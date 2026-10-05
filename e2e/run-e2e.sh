#!/usr/bin/env bash
# Runs Better Pets on a real Paper server and plays through what the unit tests cannot reach.
#
# Builds the test plugin in src/ (see E2EPlugin), sets up a throw-away server in ../target/e2e/server
# with the Better Pets jar from ../target and that test plugin, starts it, and has a game client join
# it - the real game with the Quickslots mod, on a Windows desktop of its own, so no window appears.
# The test plugin then breaks blocks as that player, opens menus, trades, dies in the middle of a trade,
# and writes ../target/e2e/server/e2e-report.txt. Exits non-zero if a check failed.
#
#   powershell ../build.ps1 && E2E_ACCEPT_EULA=1 bash run-e2e.sh
#
# The server is the Minecraft server software: starting it means accepting the Minecraft EULA
# (https://aka.ms/MinecraftEULA). This script only writes eula=true into the throw-away folder when it
# is told to with E2E_ACCEPT_EULA=1 - by someone who has read it and agrees.
#
# Nothing is downloaded: the server jar and its libraries are copied from an existing Paper server
# (SERVER_SOURCE), the game comes from the launcher directory the mod is built against.
#
#   SERVER_SOURCE   a Paper server folder to copy paper-*.jar, cache/, versions/ and libraries/ from
#   MOD_DIR         the Better Pets Quickslots checkout (its jar, classpath helper and hidden launcher)
#   PORT            port of the test server (default 25599)
#   SECOND_PLAYER_SECONDS   how long to wait for a second player before the trade checks are skipped;
#                   anyone may be that player by joining localhost:PORT while the run waits
#   CLIENTS=2       start a second test client (E2E_B) as well, so the trade checks have their partner
#   TRADES_ONLY=true   skip the checks that need one player only
#   HOST=1          no checks at all: just the server, for trying the Quickslots mod against the real
#                   plugin with your own game and keyboard (whoever joins gets pets in their quickslots)
#   CLIENTS=0       start no client: join localhost:PORT as E2E_A yourself
#   VISIBLE=1       show the test client's window instead of hiding it
set -euo pipefail

# Windows-style paths (C:/...): they go into argument files that Java reads, not only onto command lines.
HERE="$(cd "$(dirname "$0")" && pwd -W)"
PLUGIN_DIR="$(cd "$HERE/.." && pwd -W)"
SERVER_SOURCE="${SERVER_SOURCE:-C:/Users/Kamil Bura/Desktop/Neuer Ordner (6)/UPDATE}"
MOD_DIR="${MOD_DIR:-C:/Users/Kamil Bura/Projekten/BetterPetsQuickslots}"
MCROOT="${MCROOT:-D:/.minecraftx}"
JDK="${JDK:-C:/Program Files/Java/jdk-25}"
PYTHON="${PYTHON:-python}"
PORT="${PORT:-25599}"
MC_VERSION="${MC_VERSION:-26.2}"
PLAYER="E2E_A"
SECOND_PLAYER_SECONDS="${SECOND_PLAYER_SECONDS:-240}"

WORK="$PLUGIN_DIR/target/e2e"
# Java opens a small socket file for its network selectors, by default right in the temp directory -
# which fails ("Unable to establish loopback connection") where that directory is locked down, as it
# is for sandboxed shells. A folder of our own works everywhere.
SOCKETS="$WORK/tmp"
SERVER="$WORK/server"
PETS_JAR="$PLUGIN_DIR/target/better-pets-$MC_VERSION-plugin.jar"
PAPER_JAR="$(ls "$SERVER_SOURCE"/paper-*.jar 2>/dev/null | sort -V | tail -1)"

[ -f "$PETS_JAR" ] || { echo "$PETS_JAR not found -- run build.ps1 first"; exit 1; }
[ -f "$PAPER_JAR" ] || { echo "No paper-*.jar in $SERVER_SOURCE (set SERVER_SOURCE)"; exit 1; }

# ---- the test plugin --------------------------------------------------------------------------------
echo "Building the test plugin ..."
rm -rf "$WORK/classes"
mkdir -p "$WORK/classes" "$SOCKETS"
{
  ls "$PLUGIN_DIR"/lib/paper-api-"$MC_VERSION"*.jar | sort -V | tail -1
  ls "$PLUGIN_DIR"/target/compile-libs/adventure-*.jar
  find "$SERVER_SOURCE/libraries" -name '*.jar'
} | tr -d '\r' | paste -sd ';' > "$WORK/classpath.txt"
printf -- '-cp "%s"\n' "$(cat "$WORK/classpath.txt")" > "$WORK/javac-args.txt"
find "$HERE/src" -name '*.java' | tr -d '\r' | sed 's/.*/"&"/' > "$WORK/sources.txt"
"$JDK/bin/javac" -nowarn -proc:none --release 21 -encoding UTF-8 -d "$WORK/classes" \
  "@$WORK/javac-args.txt" "@$WORK/sources.txt"
cp "$HERE/plugin.yml" "$WORK/classes/"
"$JDK/bin/jar" --create --file "$WORK/BetterPetsE2E.jar" -C "$WORK/classes" .

# ---- the server --------------------------------------------------------------------------------------
if [ ! -f "$SERVER/eula.txt" ]; then
  if [ "${E2E_ACCEPT_EULA:-0}" != "1" ]; then
    echo "Starting the server means accepting the Minecraft EULA (https://aka.ms/MinecraftEULA)."
    echo "Run again with E2E_ACCEPT_EULA=1 if you agree to it."
    exit 1
  fi
  mkdir -p "$SERVER"
  echo "eula=true" > "$SERVER/eula.txt"
fi
if [ ! -d "$SERVER/libraries" ]; then
  echo "Copying the server from $SERVER_SOURCE ..."
  cp "$PAPER_JAR" "$SERVER/"
  cp -r "$SERVER_SOURCE/cache" "$SERVER_SOURCE/versions" "$SERVER_SOURCE/libraries" "$SERVER/"
fi
# A clean slate every time: world, player data and reports of the last run go.
rm -rf "$SERVER"/world* "$SERVER/plugins" "$SERVER/logs" "$SERVER/e2e-report.txt" "$SERVER"/server*.log
mkdir -p "$SERVER/plugins/bStats"
cp "$PETS_JAR" "$WORK/BetterPetsE2E.jar" "$SERVER/plugins/"
# No statistics are to leave this machine from a test server.
printf 'enabled: false\nserverUuid: 00000000-0000-0000-0000-000000000000\nlogFailedRequests: false\n' \
  > "$SERVER/plugins/bStats/config.yml"
cat > "$SERVER/server.properties" <<EOF
server-port=$PORT
server-ip=127.0.0.1
online-mode=false
enforce-secure-profile=false
level-type=minecraft\:flat
generate-structures=false
spawn-protection=0
difficulty=peaceful
gamemode=survival
view-distance=4
simulation-distance=4
max-players=4
motd=Better Pets test server
enable-rcon=false
enable-query=false
sync-chunk-writes=false
EOF

server_pid=""
start_server() {
  ( cd "$SERVER" && exec "$JDK/bin/java" -Xms512M -Xmx1536M "-Djdk.net.unixdomain.tmpdir=$SOCKETS" \
      -De2e.player="$PLAYER" -De2e.secondPlayerSeconds="$SECOND_PLAYER_SECONDS" \
      -De2e.tradesOnly="${TRADES_ONLY:-false}" -De2e.host="$([ "${HOST:-0}" = "1" ] && echo true || echo false)" \
      -jar "$(basename "$PAPER_JAR")" --nogui >> server.log 2>&1 ) &
  server_pid=$!
  echo "Starting the server (pid $server_pid) ..."
  for _ in $(seq 1 180); do
    grep -q 'Done (' "$SERVER/server.log" 2>/dev/null && { echo "The server is up on port $PORT."; return 0; }
    kill -0 "$server_pid" 2>/dev/null || { echo "The server stopped while starting -- see $SERVER/server.log"; return 1; }
    sleep 1
  done
  echo "The server did not come up within three minutes -- see $SERVER/server.log"
  return 1
}

# ---- the client --------------------------------------------------------------------------------------
MOD_JAR="$(ls "$MOD_DIR"/betterpets-quickslots-*.jar 2>/dev/null | sort -V | tail -1)"
FABRIC_API="${FABRIC_API:-$(ls "$MCROOT"/instances/*/mods/fabric-api-*+"$MC_VERSION".jar 2>/dev/null | sort -V | tail -1)}"
PROFILE="${PROFILE:-$(ls -d "$MCROOT"/versions/"$MC_VERSION"-fabric* | sort -V | tail -1 | xargs basename)}"
ASSET_INDEX="$(sed -n 's/.*"assets": *"\([^"]*\)".*/\1/p' "$MCROOT/versions/$MC_VERSION/$MC_VERSION.json" | head -1)"

# start_client <player name>
start_client() {
  local name="$1"
  local CLIENT="$WORK/client-$name"
  [ "${CLIENTS:-1}" != "0" ] || { echo "No client started: join localhost:$PORT as $name."; return 0; }
  [ -f "$MOD_JAR" ] || { echo "No mod jar in $MOD_DIR (set MOD_DIR)"; return 1; }
  rm -rf "$CLIENT/mods"
  mkdir -p "$CLIENT/mods" "$CLIENT/natives"
  cp "$MOD_JAR" "$FABRIC_API" "$CLIENT/mods/"
  cat > "$CLIENT/options.txt" <<EOF
onboardAccessibility:false
skipMultiplayerWarning:true
joinedFirstServer:true
tutorialStep:none
narrator:0
fullscreen:false
pauseOnLostFocus:false
soundCategory_master:0.0
guiScale:2
lang:en_us
EOF
  {
    "$PYTHON" "$MOD_DIR/tools/classpath.py" "$MCROOT" "$PROFILE"
    printf '%s\n' "$MCROOT/versions/$MC_VERSION/$MC_VERSION.jar"
  } | tr -d '\r' > "$CLIENT/classpath.txt"
  printf -- '-cp "%s"\n' "$(paste -sd ';' "$CLIENT/classpath.txt")" > "$CLIENT/java-args.txt"
  local args=(-Xmx2G "-Djdk.net.unixdomain.tmpdir=$SOCKETS"
    --sun-misc-unsafe-memory-access=allow --enable-native-access=ALL-UNNAMED
    -Djava.library.path=natives/java -Djna.tmpdir=natives/jna
    -Dorg.lwjgl.system.SharedLibraryExtractPath=natives/lwjgl -Dio.netty.native.workdir=natives/netty
    @java-args.txt net.fabricmc.loader.impl.launch.knot.KnotClient
    --username "$name" --version "$PROFILE" --gameDir .
    --assetsDir "$MCROOT/assets" --assetIndex "$ASSET_INDEX"
    --uuid 00000000-0000-0000-0000-00000000e2e0 --accessToken 0 --clientId 0 --xuid 0
    --versionType release --width 854 --height 480
    --quickPlayMultiplayer "localhost:$PORT")
  echo "Starting the game as $name ..."
  if [ "${VISIBLE:-0}" = "1" ]; then
    ( cd "$CLIENT" && exec "$JDK/bin/java" "${args[@]}" > game.log 2>&1 ) &
  else
    {
      printf 'cmd.exe /c ""%s"' "$(cygpath -w "$JDK/bin/java.exe")"
      for arg in "${args[@]}"; do
        case "$arg" in *' '*) printf ' "%s"' "$arg" ;; *) printf ' %s' "$arg" ;; esac
      done
      printf ' > game.log 2>&1"'
    } > "$CLIENT/hidden-command.txt"
    ( cd "$CLIENT" && powershell -NoProfile -ExecutionPolicy Bypass \
        -File "$(cygpath -w "$MOD_DIR/tools/run-hidden.ps1")" \
        -WorkDir "$(cd "$CLIENT" && pwd -W)" -CommandFile hidden-command.txt -TimeoutSeconds 1500 > /dev/null 2>&1 || true ) &
  fi
}

stop_client() {
  # The game was started through cmd.exe on another desktop; its user name on the command line finds it.
  powershell -NoProfile -Command "Get-CimInstance Win32_Process -Filter \"Name='java.exe'\" | Where-Object { \$_.CommandLine -match '--username E2E_[AB] ' } | ForEach-Object { Stop-Process -Id \$_.ProcessId -Force }" 2>/dev/null || true
}

wait_for_server_to_stop() {
  for _ in $(seq 1 "$1"); do
    kill -0 "$server_pid" 2>/dev/null || return 0
    sleep 1
  done
  echo "The server is still running after $1 seconds -- stopping it."
  kill "$server_pid" 2>/dev/null || true
  return 1
}

trap 'stop_client; [ -n "$server_pid" ] && kill "$server_pid" 2>/dev/null || true' EXIT

if [ "${HOST:-0}" = "1" ]; then
  start_server
  echo "Nothing is checked in host mode. Join localhost:$PORT with your own game: you get a few pets in"
  echo "your quickslots, and $SERVER/server.log shows every switch. Stop this script to stop the server."
  wait "$server_pid" || true
  exit 0
fi

start_server
start_client "$PLAYER"
if [ "${CLIENTS:-1}" = "2" ]; then
  start_client E2E_B
fi
wait_for_server_to_stop $((SECOND_PLAYER_SECONDS + 900)) || true
stop_client

# The test player was thrown out in the middle of a trade on purpose: the pets that are waiting for it
# have to survive a restart of the server and arrive on the next join.
if [ -f "$SERVER/plugins/BetterPetsE2E/rejoin-pending" ]; then
  echo "Second start: the kicked player comes back."
  mv "$SERVER/server.log" "$SERVER/server-first-start.log"
  start_server
  start_client "$PLAYER"
  wait_for_server_to_stop 600 || true
  stop_client
fi

echo
if [ ! -f "$SERVER/e2e-report.txt" ]; then
  echo "No report was written -- see $SERVER/server.log"
  exit 1
fi
cat "$SERVER/e2e-report.txt"
! grep -q '^FAIL' "$SERVER/e2e-report.txt"
