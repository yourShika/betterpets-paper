package de.kamil.betterpets;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;

import java.io.File;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * GitHub release check and (opt-in) jar download for Better Pets. Extracted from the main plugin so the
 * networking and version-comparison logic lives on its own. All GitHub access is gated behind
 * {@code update.repo}/{@code update.enabled} in config.yml (see {@link #repo()} / {@link #autoEnabled()}).
 */
final class Updater {
    private static final Pattern TAG = Pattern.compile("\"tag_name\"\\s*:\\s*\"([^\"]+)\"");
    private static final Pattern JAR_URL = Pattern.compile("\"browser_download_url\"\\s*:\\s*\"(https://[^\"]+\\.jar)\"");

    private final BetterPetsPlugin plugin;

    Updater(final BetterPetsPlugin plugin) {
        this.plugin = plugin;
    }

    /** GitHub "owner/name" the updater pulls from; blank disables all update network calls. */
    String repo() {
        final String repo = plugin.getConfig().getString("update.repo", "");
        return repo == null ? "" : repo.trim();
    }

    boolean autoEnabled() {
        return plugin.getConfig().getBoolean("update.enabled", false) && !repo().isEmpty();
    }

    /** Reports (chat) whether a newer release exists. Does not download anything. */
    void checkLatestVersion(final CommandSender sender, final String currentVersion) {
        final String repo = repo();
        if (repo.isEmpty()) {
            sender.sendMessage(Component.text("Update check is disabled (set update.repo in config.yml to enable it).", NamedTextColor.DARK_GRAY));
            return;
        }
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            final ReleaseInfo info = fetchLatestRelease();
            final String latest = info == null ? null : info.tag();
            Bukkit.getScheduler().runTask(plugin, () -> {
                if (latest == null) {
                    sender.sendMessage(Component.text("Could not check for updates (GitHub unreachable).", NamedTextColor.DARK_GRAY));
                    return;
                }
                final String latestClean = latest.startsWith("v") ? latest.substring(1) : latest;
                final int comparison = compareVersions(currentVersion, latestClean);
                if (comparison < 0) {
                    sender.sendMessage(Component.text("A newer version is available: v" + latestClean + " (you have v" + currentVersion + ").", NamedTextColor.YELLOW));
                    sender.sendMessage(Component.text("https://github.com/" + repo + "/releases/latest", NamedTextColor.AQUA));
                } else if (comparison > 0) {
                    sender.sendMessage(Component.text("You are running a newer build (v" + currentVersion + ") than the latest release (v" + latestClean + ").", NamedTextColor.GRAY));
                } else {
                    sender.sendMessage(Component.text("You are on the latest version.", NamedTextColor.GREEN));
                }
            });
        });
    }

    /** Downloads the latest release jar into the server update folder if newer (auto-updater must be enabled). */
    void downloadLatest(final CommandSender sender, final String currentVersion) {
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            final ReleaseInfo info = fetchLatestRelease();
            if (info == null || info.tag() == null) {
                runOnMain(() -> sender.sendMessage(Component.text("Could not reach GitHub to check for updates.", NamedTextColor.RED)));
                return;
            }
            final String latest = info.tag().startsWith("v") ? info.tag().substring(1) : info.tag();
            if (compareVersions(currentVersion, latest) >= 0) {
                runOnMain(() -> sender.sendMessage(Component.text("You are already on the latest version (v" + currentVersion + ").", NamedTextColor.GREEN)));
                return;
            }
            if (info.jarUrl() == null) {
                runOnMain(() -> sender.sendMessage(Component.text("Release v" + latest + " has no downloadable jar asset.", NamedTextColor.RED)));
                return;
            }
            final boolean downloaded = download(info.jarUrl());
            runOnMain(() -> {
                if (downloaded) {
                    sender.sendMessage(Component.text("Downloaded Better Pets v" + latest + " (you have v" + currentVersion + ").", NamedTextColor.GREEN));
                    sender.sendMessage(Component.text("Restart the server to apply the update.", NamedTextColor.YELLOW));
                } else {
                    sender.sendMessage(Component.text("Update download failed - see the console for details.", NamedTextColor.RED));
                }
            });
        });
    }

    private ReleaseInfo fetchLatestRelease() {
        final String repo = repo();
        if (repo.isEmpty()) {
            return null;
        }
        try {
            final HttpClient client = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(5))
                .build();
            final HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create("https://api.github.com/repos/" + repo + "/releases/latest"))
                .header("Accept", "application/vnd.github+json")
                .header("User-Agent", "BetterPets-UpdateCheck")
                .timeout(Duration.ofSeconds(5))
                .GET()
                .build();
            final HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() != 200) {
                return null;
            }
            final Matcher tagMatcher = TAG.matcher(response.body());
            final String tag = tagMatcher.find() ? tagMatcher.group(1) : null;
            // Only accept a jar asset URL served by GitHub itself, never an arbitrary URL.
            final Matcher urlMatcher = JAR_URL.matcher(response.body());
            final String jarUrl = urlMatcher.find() ? urlMatcher.group(1) : null;
            return new ReleaseInfo(tag, jarUrl);
        } catch (final Exception exception) {
            return null;
        }
    }

    private boolean download(final String jarUrl) {
        try {
            final File updateFolder = Bukkit.getUpdateFolderFile();
            if (!updateFolder.exists() && !updateFolder.mkdirs()) {
                plugin.getLogger().severe("Could not create the plugin update folder: " + updateFolder.getAbsolutePath());
                return false;
            }
            // Place the new jar in the server's update folder under the current plugin's file name.
            // Bukkit applies it automatically on the next start, which avoids the locked-jar problem.
            final Path target = new File(updateFolder, plugin.currentJar().getName()).toPath();
            final HttpClient client = HttpClient.newBuilder()
                .followRedirects(HttpClient.Redirect.NORMAL)
                .connectTimeout(Duration.ofSeconds(10))
                .build();
            final HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(jarUrl))
                .header("User-Agent", "BetterPets-UpdateCheck")
                .header("Accept", "application/octet-stream")
                .timeout(Duration.ofSeconds(120))
                .GET()
                .build();
            final HttpResponse<Path> response = client.send(request, HttpResponse.BodyHandlers.ofFile(target));
            if (response.statusCode() != 200) {
                plugin.getLogger().severe("Update download returned HTTP " + response.statusCode() + ".");
                Files.deleteIfExists(target);
                return false;
            }
            plugin.getLogger().info("Downloaded Better Pets update to " + target + "; it will be applied on the next server start.");
            return true;
        } catch (final Exception exception) {
            plugin.getLogger().severe("Update download failed: " + exception.getMessage());
            return false;
        }
    }

    private void runOnMain(final Runnable runnable) {
        Bukkit.getScheduler().runTask(plugin, runnable);
    }

    static int compareVersions(final String left, final String right) {
        final String[] leftParts = left.split("\\.");
        final String[] rightParts = right.split("\\.");
        final int length = Math.max(leftParts.length, rightParts.length);
        for (int i = 0; i < length; i++) {
            final int leftValue = i < leftParts.length ? parseVersionPart(leftParts[i]) : 0;
            final int rightValue = i < rightParts.length ? parseVersionPart(rightParts[i]) : 0;
            if (leftValue != rightValue) {
                return Integer.compare(leftValue, rightValue);
            }
        }
        return 0;
    }

    private static int parseVersionPart(final String value) {
        final String digits = value.replaceAll("[^0-9]", "");
        if (digits.isEmpty()) {
            return 0;
        }
        try {
            return Integer.parseInt(digits);
        } catch (final NumberFormatException exception) {
            return 0;
        }
    }

    private record ReleaseInfo(String tag, String jarUrl) {
    }
}
