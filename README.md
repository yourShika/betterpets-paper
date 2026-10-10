# 🐾 Better Pets Paper

<!-- ai-disclaimer -->
> ⚠️ **Disclaimer:** This project — its code and its project artwork — was created with the help of AI. The plugin itself is tested by real people, and bugs and issues are actively fixed.


> A full **Paper plugin rewrite** of the original Better Pets datapack.

![Minecraft](https://img.shields.io/badge/Minecraft%20%2F%20Paper-26.2-brightgreen)
![Java](https://img.shields.io/badge/Java-21-orange)
![Platform](https://img.shields.io/badge/Platform-Paper-blue)
![Version](https://img.shields.io/badge/Version-1.34.2-blueviolet)
![Type](https://img.shields.io/badge/Type-Plugin%20Rewrite-purple)
![License](https://img.shields.io/badge/License-MIT-green)
![Languages](https://img.shields.io/badge/Languages-EN%20%2F%20DE%20%2F%20PL-yellow)

---

## ✨ About

**Better Pets Paper** is a complete **Paper plugin rewrite** of the Better Pets datapack.

It does **not** require datapacks, command functions, minecart menus, or a manual resource pack.
Everything is handled directly through the plugin. Optional animated 3D models are handled through
**BetterModel** when that plugin is installed and the (experimental) module is enabled.

---

## 🆕 What's New in v1.34.2

* 🏷️ The name above a 3D model now **glides along with the pet** instead of jumping from spot to spot.

---

## 🆕 What's New in v1.34.1

### 🧸 3D models: three corrections from the first look in the game

* 👀 A model now **looks at its owner** — it used to look where the owner looked (the heads need to be turned the other way round; models do not).
* 🏷️ The **name sits just above the model**, however tall it is and at whatever size it is shown (`model-nametag-gap`), instead of at one fixed height for all.
* 🪽 A pet that walks **comes along through the air** when its owner flies or glides, like the heads do, and is back on the ground afterwards.

---

## 🆕 What's New in v1.34.0

### 🧸 3D models: a model per skin, sizes, and animations that answer what you do

For servers that show pets as animated models through **BetterModel** (the optional, experimental module). Bring your own `.bbmodel` files — none are included.

* 🎨 **A model for every skin.** The model is found by its file name: the pet id, the skin the pet is wearing, and optionally how it moves — `cat.bbmodel`, `cat__black.bbmodel`, `allay_happy_flying.bbmodel`. Change the skin in Customize and the model changes with it; a skin without a model of its own uses the pet's plain one. Where a pet has both a `_grounded` and a `_flying` model, the new `model-flying-pets` list decides.
* 📏 **Sizes.** `model-scale` sets a size for all models, for single pets or for single model files — for the dragon that fills the room and the worm nobody can see.
* 🎬 **More than idle and walking.** Models that bring further animations now use them when the moment fits: a greeting when the pet appears, an attack or roar when you hit something, a flinch when you are hit, digging when you break blocks, swimming in water, a jump now and then when you jump, take-off and landing with the elytra, eating along with you, a little celebration on a level-up, the Phoenix's rebirth — and sitting down, then dozing off, when you stand still for a while. A model plays what it has; nothing is required. Whatever else it brings (a tail curl, a wing stretch) joins the random idle gestures.
* 🧰 **`scripts/install_models.py`** installs a folder of model packages onto a server: it picks the model for every pet and skin, leaves out what would only load twice, suggests sizes for the ones that stand out, and switches the module on.
* ⚙️ Model files are read once and remembered (`models/animations.index`) — hundreds of them no longer slow every start. Idle gestures now last exactly as long as their animation.

⚠️ Many detailed models need memory: 344 of them did not fit into 1.5 GB of heap in our test and loaded fine with 8 GB.

Checked on a real Paper 26.2 server with BetterModel 3.2.0: all 79 pets in all their skins (307 combinations) appear as models (`MODELS=… bash e2e/run-e2e.sh`). How they look and move in the game has not been looked at by the author of these lines.

---

## 🆕 What's New in v1.33.2

### 🧪 Run on a real server — and one fix that came out of it

Everything in 1.33.1 had been reasoned through, reviewed and checked against the server's own code, but not run. Now it has been: a new end-to-end test ([`e2e/`](e2e/run-e2e.sh)) starts a real Paper 26.2 server with the plugin, has real game clients with the Quickslots mod join it, and then does as a player what 1.33.1 was about — breaks blocks with each of the pets concerned (shulker boxes, cauldrons, ores with and without Silk Touch, chests with things in them, blocks with a torch on top), fishes, reopens the Alpaca storage, converts pets and takes them in again, trades, dies and gets kicked in the middle of a trade, and restarts the server in between. **50 checks, all green.**

It found one thing that reading alone had not:

* 🤝 A player who is **kicked or loses the connection during a trade** got the pets they had offered put straight back into the inventory — and dropped where they stood if it was full. Paper closes the window of such a player as "cannot be used", a moment before it treats them as gone, so the plugin took them for someone who was still there. Their pets are now put aside and handed over on the next join, which is what 1.33.1 said it did.

Nothing else changes.

---

## 🆕 What's New in v1.33.1

### 🔒 Security and bug fixes — please update

The result of going through everything that hands out items, pets or tokens. No new features.

**Duplication**

* ⛏️ **Break bonuses** — Crystal Golem, the Gatherer star bonus, Arcane Fox experience and ore tokens — are now only paid for a block that is *used up*. Before, whatever could be put down again could be broken again for another roll: an ore mined with Silk Touch, Ancient Debris, a block of diamond, a filled cauldron, and worst of all a filled shulker box (set down by a dispenser, or simply still standing after a restart), which the Gatherer bonus handed out a second time with everything in it.
  * Natural terrain and plants (logs, leaves, dirt, sand, stone, sugar cane, …) are still doubled, and a ripe crop now is even if you planted it yourself. A block that a piston pushed counts as placed.
  * ⚠️ On purpose: **Ancient Debris and ores mined with Silk Touch give no bonus any more.**
* 🛡️ **Bonuses are paid once the break, kill, trade or catch stands.** A protection plugin that cancels a break leaves the block in place — and used to leave the bonus drops behind every time, so a protected ore could be "mined" for ever. The fishing bonus is a second helping of the fish on the hook, never of what another plugin turns the catch into.
* 🦙 **Alpaca storage:** the swap-with-off-hand key put a pet item into the storage, a pet item in an open storage was handed back as a copy with every save, and opening the storage a second time while it was open brought back whatever had been taken out.
* 📖 **Menu heads are not pets.** Every Better Pets menu is closed when the plugin stops, and a head made for a menu can no longer be taken in, scrapped or traded. After a `/reload`, a catalogue that was still open let its heads be lifted out — each of them a pet.
* 🧌 The **Goblin** no longer snatches a second copy of a pet or booster bought from the Wandering Trader.
* 🎨 **Skins and name styles:** the skin a pet wears goes with the pet when it is converted, and is back in your collection when you take the pet in again — handing a pet to and fro used to copy a bought skin to everyone who held it. A name style is only kept if the new owner has it.
* 🖌️ A suspicious block gets its one pet roll for good — no longer once per server restart.

**Losing things**

* 🦋 When the **Silk Moth** kicked in on a chest, barrel or furnace, **everything inside was gone** — and so was a torch, door or rail that sat on any block it (or the **Salamander**) acted on. Both used to tell the game to drop nothing at all and then dropped their own item. They now exchange just the block's own drops, which also makes them get along with plugins that collect drops. The Silk Moth leaves blocks that hold things alone (the ender chest excepted).
* 🤝 **Trading:** when a trade window closes, both sides get their pets back at once (before, a tick later — and if the server went down in that moment, they were gone). A player who disconnects or dies in the middle gets theirs on the next join or respawn; until then they are kept in `pending-returns.yml`, where they used to live in memory only and did not survive a restart. A trade is no longer settled for fewer tokens than the window showed.
* ✋ A pet in the **off hand** was placed as a decoration head (and gone as a pet once broken), a booster there was thrown as a plain bottle. Neither happens any more.
* 🧱 The **Golem Mason** refill could overwrite whatever you had switched to in the same moment.

**Abilities and quickslots**

* 🔥 Converting a **Phoenix** to an item and back no longer resets its revive.
* 🐉 Putting a pet away and summoning it again no longer resets the **Shadow Dragon** burst or the **Kangaroo** dash.
* ⚡ Quickslots need the pet menu's permission (`betterpets.command.pets`) as well, respect the Alpaca's "empty first" rule while its storage is open, do nothing while you are dead, and save slot changes in one go instead of rewriting the data file per click. The convert confirmation now belongs to the pet it was asked for.

Goes with **[Better Pets Quickslots 1.1.2](https://github.com/yourShika/betterpets-quickslots/releases/latest)** (wrapped tooltips, tidier handling of long names); older versions of the mod keep working.

---

## 🆕 What's New in v1.33.0

### 🛡️ Spam protection for quickslots

Hammering the switch — a held key, a macro, a spammed command — can no longer keep the server busy.

* ⏱️ **Burst guard:** more than `max-switches` quick switches within `window-seconds` lock quick switching for `lockout-seconds` (out of the box: more than 8 in 10 seconds → locked for 5). The player sees the remaining time in the action bar. `switch-cooldown-ticks` stays what it was: the minimum gap between two switches.
* 🚦 **Every method counts together** — commands, sneak + mouse wheel and the mod — so changing the method does not get around the lock.
* 📦 The number of messages the mod may send per second is capped more tightly (20 instead of 60); anything beyond that is dropped unanswered.
* 🧩 **[Better Pets Quickslots 1.1.0](https://github.com/yourShika/betterpets-quickslots)**, the optional Fabric mod, is told about the cooldown and the lock and shows them as a countdown instead of sending requests the server would turn down. It also brings a **pet wheel**, a **modifier key** (hold Alt and press 1–9 or turn the mouse wheel — no numpad needed) and a **settings screen with a live preview**. Version 1.0.0 of the mod keeps working with this plugin version.

Tune it or turn it off under `quickslots.spam-protection` in `config.yml`; an existing config gets the new section added automatically.

---

## 🆕 What's New in v1.32.0

### ⚡ Quickslots — switch pets without opening the menu

Park your favourite pets in numbered slots and swap between them instantly.

* 🎛️ **Three ways to switch**, each of which a server can turn off in `config.yml`:
  * **Commands** — `/pets quick <slot>`, `next`, `prev`, `off`.
  * **Sneak + mouse wheel** — steps through your slots. Needs the **PacketEvents** plugin (2.13.0+); without it this method simply stays off.
  * **[Better Pets Quickslots](https://github.com/yourShika/betterpets-quickslots)** — an optional Fabric mod with freely bindable keys, a slot screen and a Quickslots button in the `/pets` menu.
* 📌 **Slots remember the pet type**, so a slot keeps working after the pet was converted to an item and added back.
* 🧩 Fill slots with `/pets quick set <slot> [pet]` (no pet named = the pet that is out) and list them with `/pets quick`.
* 🔒 New permission `betterpets.quickslots` (default: everyone).

See [Quickslots](#-quickslots) below for the details.

### 🛠️ Fixes

* 🎨 The **Oraxen menu skin no longer switches off after `/pets reload`**.
* 📝 Options added by an update now arrive in an existing `config.yml` **together with their explanatory comments** (they used to show up as bare keys).

---

## 🆕 What's New in v1.9.3

* 🐉 **Flying is less fiddly.** Right-clicking your pet **while flying no longer dismounts you** — hop off by **sneaking** instead. And starting flight now needs a **confirming second right-click**, so you never take off by accident.
* 👁️ **New commands:** `/pets visible` and `/pets invisible` (aliases: `show` / `hide`) show or hide your active pet from chat, without opening the menu.

---

## 🆕 What's New in v1.9.2

* 🕊️ **No more flight height cap.** Pet mounts could only reach the world's build height (~Y 319). You can now fly **well above** it into open sky — capped only by a generous, configurable safety ceiling (`flight-max-height`, default 1024).

---

## 🆕 What's New in v1.9.1

* 🐉 **Fix: flying pets no longer clip you into blocks.** The mount's collision now samples your whole body (not just the centre), and while flying you can never take **suffocation** damage from briefly touching a block.
* 🚫 **Pets no longer get in your way.** Pet hitboxes are now non-attackable and, for pets without a right-click action, tiny — so they never block your mining, attacking, or building. Pets that *do* use right-click (the **Alpaca** and the flyable dragons/Phoenix) keep a clickable hitbox; hide any pet with the visibility toggle in `/pets` if it's ever in the way.

---

## 🆕 What's New in v1.9.0

### ⬆️ Minecraft / Paper 26.2
* The plugin now targets **Paper 26.2** (`api-version: '26.2'`), built against `paper-api 26.2.build.62-beta` and **Adventure 5.2.0**.
* No gameplay changes — the entire codebase compiled against 26.2 **without a single source change**; only the build and API target moved.
* `build.ps1` now downloads the Paper API and the matching Adventure 5.x jars itself, and orders the classpath so newer Adventure wins over older server libraries.

> ⚠️ **Note:** Paper 26.2 is currently an **experimental/beta** branch (26.1.2 is the stable one). Because `api-version` is now `26.2`, this build requires a 26.2 server — if you are still on 26.1.2, stay on **v1.8.1**.

---

## 🆕 What's New in v1.8.1

* 🎰 **Fix:** the slot machine's **featured pet no longer re-rolls every time you open** `/pets slots`. It now stays the same until you actually spin, and is remembered per player.

---

## 🆕 What's New in v1.8.0

### 🎰 Slot machine, expanded
* **Way more loot** in the reels: gold nuggets, coal, iron, copper, redstone, lapis, gold ingots, XP bottles, emeralds, diamonds, netherite scrap & ingots — each with its own chance (netherite stays rare).
* **Real slot feel:** the three reels now settle **left → middle → right**.
* **A featured pet every spin** is shown above the reels — land on the pet symbol to win *that* pet, with a chat message and an optional server-wide jackpot broadcast. The pet chance was bumped up a little (still rare).
* **Spinning now costs just 1 token.**
* **Admin loot editor:** a **Configure Loot** button inside `/pets slots` opens a GUI to adjust every reward's chance live (left/right-click ±1, shift ±10, shows the %). Everything is also under `tokens:` in `config.yml`.

### 🎟️ Token commands
* `/pets tokens pass <player> <amount>` — send some of **your** tokens to another player (anyone can use it).
* `/pets tokens give|remove <player|all> <amount>` — admins hand out or take back tokens (works from console too).

---

## 🆕 What's New in v1.7.0

### 🎟️ Pet tokens & 🎰 the slot machine
* **Duplicate pets are no longer dead weight.** Turn any pet item into **pet tokens** with **`/pets scrap`** (the item in your hand) or **`/pets scrap all`** (every pet item in your inventory). Check your balance with **`/pets tokens`**.
* Tokens by rarity: **Common 1 · Rare 2 · Epic 3 · Legendary 4 · Mythical 5** (all configurable).
* **`/pets slots`** opens an animated **slot machine**: spend tokens per spin to pull **gold nuggets → iron → gold → diamonds → netherite**, or — *very, very rarely* — **another pet** (~0.5 %, the pulled pet chosen by spawn weight). Tuned to be a fun sink for duplicates, not a farm.
* Fully configurable under `tokens:` in `config.yml`; set `tokens.enabled: false` to switch the whole feature off.

---

## 🆕 What's New in v1.6.0

### 🌍 Localization — English / Deutsch / Polski
* All player-facing text now lives in **`plugins/BetterPets/lang/<code>.yml`** (bundled: `en`, `de`, `pl`). Pick one with **`language:`** in `config.yml`, or switch live with **`/pets language <en|de|pl>`**.
* **Upgrade-safe:** new message keys are merged into your language files on every start, so your custom text survives updates. A legacy `messages:` block in `config.yml` is migrated automatically.
* Messages support **MiniMessage** formatting (`<gradient:#a:#b>`, `<bold>`, colours). Substituted values are escaped, so a player-chosen pet name can never inject formatting.

### 🎨 Nicer look
* **Gradient GUI titles** and a gradient **`[BetterPets]`** broadcast prefix.
* Prettier **level-up**, **Phoenix revive**, **booster** and **convert** messages.
* **`config.yml`** reorganized into numbered, documented sections.

### 🙂 Quality of life
* **Convert To Item** now asks for a confirming second click and **keeps the pet's level *and* in-level XP** on the item, so nothing is lost on a misclick.
* **`/pets mute`** — hide discovery/booster broadcasts (text *and* sound) just for yourself; persists across relogs.
* **Middle-click** a pet in the *Spawn Chances* menu (or the *XP Multiplier*) to **type an exact value** in chat.
* A one-time **`/pets help`** hint the first time you open the menu.

### 🛠️ Fixes & performance
* Phoenix revive no longer fires (and wastes its cooldown) on a hit fully soaked by absorption hearts.
* Experience-orb XP is no longer double-counted toward the active pet.
* The auto-updater is now **config-driven and opt-in** (`update.repo` / `update.enabled`) — no more placeholder repository.
* Interactive saves are **coalesced**, the XP multiplier is cached, the Penguin container scan interval is configurable, and **`debug-logging` now defaults to off**.

### 🔒 Permissions
* The **XP Multiplier** control and the **`/pets drop`** (Pet Sources) menu now require **`betterpets.admin`** (previously `betterpets.chances`).

### 🧑‍💻 Under the hood
* Per-pet data and behaviour moved into a single **`PetAbilities` registry**, and the menu holders and updater were split out of the main class — adding a new pet is now far less work.
* Added a small **dependency-free test suite** (`test.ps1`).

---

## 🆕 What's New in v1.5.3

* 🐉 **Fix: Shadow Dragon no longer crashes the server.** Its on-hit AoE burst damaged nearby mobs as the player, which re-fired the damage event and triggered the burst again — an infinite loop that spammed the console with `StackOverflowError`. The burst now guards against re-entry, so it fires exactly once per hit.

---

## 🆕 What's New in v1.5.2

* 🐧 **Fix: Penguin container glow no longer blocks chests.** The glow used an invisible Shulker, whose hitbox intercepted your clicks (you couldn't open the container) and could even be attacked. It now uses a **BlockDisplay**, which has **no hitbox** — clicks pass straight through to the container and it cannot be attacked or killed. Same owner-only through-wall glow, now in cyan.

---

## 🆕 What's New in v1.5.1

* ⚔️ **Shadow Dragon AoE is now on-hit.** The burst only fires **when you attack** and the cooldown is ready; after firing, the boss bar counts down and the next hit triggers it again (instead of pulsing on a timer). The bar now reads *"Shadow Dragon AOE ready — attack!"* when charged.
* 👛 **Goblin steal notification.** When your Goblin snatches the emeralds back from a villager, you now get a clear **player-only chat message** (plus the action-bar flash), so you actually notice the free trade.
* 🐧 **Penguin Treasure Sense is now a glow.** Instead of particle markers, nearby containers a player has **never opened** now **glow through walls, for you only** — chests, trapped chests, **barrels** and **chest minecarts**. It also catches worlds that **pre-generate loot** (where the container looks "opened" but nobody touched it): a container stops glowing only once a player actually opens it. Reach grows with level (up to 24 blocks).

---

## 🆕 What's New in v1.5.0

### 🎨 Rarity overhaul
* **Epic is now pink** and the old **Extraordinary tier is renamed to Mythical** (dark purple). Every pet you already own updates automatically — rarity is read live from the pet list, nothing is stored per pet.
* **Goblin promoted to Mythical.** All six former Extraordinary pets (Herobrine, Phoenix, Reaper, Ender Dragon, Shadow Dragon, Ancient Elf) plus the Goblin are now **Mythical**. (`extraordinary` still works as a config/legacy alias.)

### ✨ Particle effects
* **Every Epic-and-above pet now has a fitting ambient particle aura** while it is visible — Pixie fae sparkles, Lich souls, Goblin emerald glints, Phoenix embers, Warden sculk, and more. Dragons and the Unicorn keep their existing trail/glitter.

### 🐾 Ability reworks
* **Shadow Dragon** — its aura became a **cooldown AoE burst**: a ring of shadow particles briefly appears, nearby hostiles take damage, and a **boss bar** shows *"Cooldown to AOE Damage"*. The cooldown **shrinks as it levels** (~12s → 4s).
* **Ender Dragon** — now deals **bonus damage in every dimension**, not just The End.
* **Mole** — now saves durability on **any** block you break with an **axe, pickaxe or shovel** (was dirt/sand/gravel only).
* **Goblin** — dropped the coin-on-kill perk; instead a small level-scaled chance that a **villager purchase refunds its emerald cost** (a free buy). Cheaper trades stay.
* **Penguin** — new **Treasure Sense**: client-side markers only you can see on nearby **unlooted** structure chests/barrels, radius growing with level.
* **Chicken** — keeps Slow Falling and now **lays eggs over time**, more often and more at once as it levels.
* **Pixie** — grants **more simultaneous random buffs at higher amplifiers** as it levels.
* **Pufferfish** — same undead wither aura, now **wider and stronger with level**.
* **Koala** — same tree-rest heal, **Regeneration I → III** with level.
* **Bee** — **fixed:** standing next to plain flowers (poppy, dandelion, …) now correctly grants Regeneration.

### ⚡ Performance
* Penguin's chest scan only checks **loaded chunks** via a cheap container filter; ambient particles and scans are throttled; the Shadow Dragon boss bar reuses the existing tick loop.

---

## 🆕 What's New in v1.3.2

* 📢 **Fix: pre-generated chest broadcasts.** When a chest's loot was rolled **before** it was opened (e.g. at world/chunk generation), the pet was placed but the find message never fired. Now the pet is marked at loot time and the broadcast fires when a player **opens the container** (or picks the item up), crediting whoever opened it.

---

## What's New in v1.3.1

* **`/pets xpboost give <x2-x5> <time> [player]`:** gives Pet XP Booster items with flexible times like `30m`, `1h`, `1h30m`, or `1d` (permission: `betterpets.give`).
* **Custom pet names survive item conversion:** converting a renamed pet back into an item now stores and restores the name.
* **Safer items:** Better Pets items and XP Boosters cannot be renamed through anvils.
* **Booster broadcasts and sounds:** XP Booster drops and activations now announce which booster appeared/was used.
* **Cleaner lore/help:** Booster and pet item lore plus `/pets help` now match the newer menu style.

---

## 🆕 What's New in v1.3.0

* ⚡ **Pet XP Boosters!** Hostile mobs can rarely drop a **Pet XP Booster** (x2-x5) that lasts **15/30/45/60 minutes**. Right-click to activate; it speeds up **pet leveling only** (never your own XP), does **not stack**, and its timer **only counts down while you are online**. Boosters are stackable as items but a second one won't add more effect. Drop chance is configurable in `/pets drop` and `config.yml`. The main menu shows your booster status (for admins, next to the XP Multiplier).
* ✏️ **Rename pets:** `/pets set name <name>` renames your summoned pet (keeps all level/XP/abilities), `/pets restore name` restores the default. The custom name shows on the hologram — for head pets **and** BetterModel pets.
* 📖 **Catalogue:** now shows each pet's **rarity** and **default drop weight**, plus clearer level-milestone rewards.
* 🛟 **Safer data:** corrupted `pets.yml` is quarantined (never silently wiped), one broken pet no longer drops a whole player's data, and there are **daily backups** (`storage.backup`, kept for `keep-days`).
* 🗄️ **Storage settings + SQLite seam:** new `storage` config (`type: yaml` default, plus backup options). `sqlite` is a documented opt-in for a later build; selecting it now safely stays on YAML.
* 🧰 **Config:** every option is now **commented**, and missing options are **repaired automatically** on start (your values are kept).
* 🧱 **BetterModel:** model **validation warnings** (flags synced `.bbmodel` files BetterModel didn't load, or models without animations) and clearer head-fallback reasons.

---

## 🆕 What's New in v1.2.7

* 📢 **Reliable chest broadcasts everywhere:** find notifications now fire for **all** containers, including chests in custom/data-pack structures (e.g. strongholds) where the previous opener lookup could miss. The opener is resolved via the loot event's entity, then the recorded interaction, and finally the nearest player to the container.

---

## 🆕 What's New in v1.2.6

* 🗝️ **Vault & Trial Spawner sources:** pets can now also drop from **Vaults** and **Trial Spawners** — both vanilla and data-pack ones — added straight into their dispensed loot.
* 🌀 **Ominous bonus:** when the Vault/Trial Spawner is **ominous** (any Ominous Bottle / Bad Omen level 1-5), a configurable `ominous-bonus-percent` is added on top of the base chance, so harder runs reward pets more often.
* 📢 Broadcasts: `Player found a … Pet in a Vault!` / `… in a Trial Spawner!`.
* ⚙️ Both new sources are fully configurable in `/pets drop` and `config.yml`, like every other source.
* 🪥 **Fix:** brushing now **replaces the item inside** the suspicious sand/gravel, so the pet is brushed out of the block naturally instead of popping in above it.
* 📢 **Fix:** Trial Spawner pets now broadcast — since the dispense has no triggering player, the find message fires when the pet is **picked up**.

---

## 🆕 What's New in v1.2.5

* 🎣 **New pet sources!** Pets can now also be obtained from **fishing**, **Wandering Traders**, and **brushing** suspicious sand/gravel — on top of the existing chest loot.
* 🧰 **`/pets drop` GUI:** toggle each source on/off and tune its chance, just like chest spawn chances. (Permission: `betterpets.chances`.)
* 🐟 **Fishing:** a small chance (lower than chests) to fish out a pet, with a `Player fished out a … Pet!` broadcast.
* 🧳 **Wandering Trader:** a spawning trader can carry a **one-time deal** to buy a pet; the price scales with rarity and uses emeralds **plus** other valuables (gold, diamonds, netherite). Buying it broadcasts `Player bought a … Pet from a Wandering Trader!`.
* 🪥 **Brushing:** a small chance to brush a pet out of suspicious sand/gravel, with a `Player brushed out a … Pet!` broadcast.
* ⚙️ Every source has its own configurable chance and on/off toggle, just like chests.
* 🧰 **Fix:** a **double chest** can no longer hand out **two** pets — both halves now share a single roll.

---

## 🆕 What's New in v1.2.4

* 👀 **Pets look at you again:** fixed the recurring bug where pets faced the player's gaze direction instead of looking **at** the player (a regression from 1.2.2). Documented in code so it cannot be flipped again.
* ⬇️ **`/pets update`:** downloads the latest release jar straight from GitHub into the server's update folder; it is applied automatically on the next **server restart**. Admin only.
* 📢 **Double-chest discovery broadcasts:** fixed missing find notifications when opening **double chests** (and other containers) — the opener is now matched against both halves of a double chest, so the broadcast no longer gets lost.

---

## 🆕 What's New in v1.2.3

* ⛏️ **Mending-proof XP:** pet XP now counts the **full value of every experience orb** (mining, mobs, furnaces, fishing, trading…) *before* Mending diverts any of it to tool repair — so ore XP always counts.
* ⚡ **Lighter saving:** the storage file is no longer written on every single XP gain; XP is persisted by the periodic autosave and on quit/disable.
* 🔎 **`/pets version`:** shows the plugin version, which modules are on, and checks GitHub Releases for a newer version (asynchronously).
* 🎞️ **Animation-driven movement:** a model is **grounded** or **flying** based on its own animations (`walking` vs `flying`), or forced by a model-name suffix (`ant_grounded` / `ant_flying`). The `model-movement-mode` config option was removed.
* 🚀 **Performance:** grounded models cache their ground height per block column instead of ray-casting every tick.

---

## 🎯 Target

| Requirement         | Version / Info                                            |
| ------------------- | --------------------------------------------------------- |
| Minecraft / Paper   | `26.2`                                                    |
| Java                | `21` (Java `25` recommended when using BetterModel 3.x)   |
| Optional Dependency | LuckPerms for permission assignment                       |
| Optional Dependency | BetterModel `3.x` for animated `.bbmodel` pets            |
| Optional Dependency | PacketEvents `2.13.0+` for sneak + mouse wheel quickslots |

---

## 🌟 Features

* 🐾 `/pets` inventory GUI for owned pets
* 📖 Pet catalogue with per-level milestone pages
* 🎲 Per-pet chest spawn chance GUI
* 📢 Discovery broadcast GUI by rarity, with rarity-based sounds
* ✨ Pet XP multiplier GUI
* 💾 Persistent player pet storage with backups
* 🦙 Owner-only Alpaca storage using Paper item byte serialization
* 📦 Dynamic Alpaca storage size based on level
* 🐉 Dragon mount flight unlocked from level 50
* 🏛️ Chest loot integration for vanilla **and** custom structure containers
* 🧩 Optional 3D pet rendering through BetterModel, with a clean head fallback
* ⚡ Quickslots: switch pets by command, sneak + mouse wheel, or freely bindable keys (optional Fabric mod)

---

## 💬 Commands

| Command                                  | Description                              |
| ---------------------------------------- | ---------------------------------------- |
| `/pets`                                  | Opens the main pet menu                  |
| `/pets version`                          | Shows version, active modules, updates   |
| `/pets update`                           | Downloads the latest version (admin)     |
| `/pets help`                             | Shows command help                       |
| `/pets info`                             | Opens the pet catalogue                  |
| `/pets chances`                          | Opens spawn chance settings              |
| `/pets notify`                           | Opens discovery broadcast settings       |
| `/pets drop`                             | Choose pet sources (chest/fishing/...)   |
| `/pets modules`                          | Opens optional module settings           |
| `/pets reload`                           | Reloads config, modules, models, pets    |
| `/pets give <pet\|all> [level] [player]` | Gives test pet items                     |
| `/pets xpboost give <x2-x5> <time> [player]` | Gives Pet XP Booster items          |
| `/pets quick`                            | Lists your quickslots                    |
| `/pets quick set <slot> [pet]`           | Parks a pet in a quickslot               |
| `/pets quick clear <slot\|all>`          | Empties a quickslot                      |
| `/pets quick <slot>\|next\|prev\|off`     | Switches pets / puts the pet away        |
| `/pets quick scroll [on\|off]`           | Toggles sneak + mouse wheel for yourself |

---

## 🔐 Permissions

| Permission                | Description                                                          |
| ------------------------- | ------------------------------------------------------------------- |
| `betterpets.command.pets` | Allows opening the main pet menu                                     |
| `betterpets.info`         | Allows opening the pet catalogue                                    |
| `betterpets.chances`      | Allows editing spawn chances, broadcasts, and XP multiplier         |
| `betterpets.give`         | Allows giving test pet items                                        |
| `betterpets.quickslots`   | Allows filling and using pet quickslots (default: everyone). Needs `betterpets.command.pets` as well: whoever may not open the menu cannot summon pets by quickslot either |
| `betterpets.admin`        | Grants all admin actions, including `/pets modules` and `/pets reload` |

---

## ⚡ Quickslots

Quickslots let a player park pets in numbered slots and switch between them without opening the menu.
Slots are filled with `/pets quick set <slot> [pet]` — or in the slot screen of the mod.

| Method              | How                                                      | Needs                                                                                             |
| ------------------- | -------------------------------------------------------- | ------------------------------------------------------------------------------------------------- |
| Commands            | `/pets quick <slot>`, `next`, `prev`, `off`              | nothing                                                                                           |
| Sneak + mouse wheel | hold sneak and scroll                                    | the [PacketEvents](https://github.com/retrooper/packetevents) plugin, `2.13.0+`                   |
| Keys and pet wheel  | a pet wheel, a modifier key, freely bindable keys, a slot screen, a button in `/pets` | the [Better Pets Quickslots](https://github.com/yourShika/betterpets-quickslots) Fabric mod (client) |

```yaml
quickslots:
  enabled: true
  slots: 5                    # 1 - 9 per player
  switch-cooldown-ticks: 10   # minimum gap between two switches (20 = 1 second)
  same-slot-puts-away: true   # using the slot of the pet that is out puts it away
  spam-protection:
    enabled: true
    max-switches: 8           # more than this many switches ...
    window-seconds: 10        # ... within this time ...
    lockout-seconds: 5        # ... lock quick switching for this long
  methods:                    # turn off whatever you do not want on your server
    commands: true
    sneak-scroll: true        # only ever active with PacketEvents installed
    mod: true
  sneak-scroll:
    default-on: true          # each player can toggle it: /pets quick scroll on|off
```

* A slot remembers the pet **type**, so it survives converting the pet to an item and adding it back.
* Every method follows the same rules as the menu: a disabled pet cannot be summoned, and an Alpaca that still carries items cannot be swapped out.
* Sneak + mouse wheel only ever takes over the wheel of players who filled at least one quickslot. A single wheel notch switches pets; number keys keep changing the hotbar as usual.
* The cooldown and the spam protection apply to all methods together. A switch that comes too soon is refused with a note in the action bar; too many in a short time lock quick switching for a few seconds.
* Players without the mod are not affected by it, and the mod does nothing on servers without this plugin.

---

## ⚙️ Important Config

```yaml
max-pets-per-player: 45
chest-pet-chance-percent: 2.5
pet-xp-multiplier: 1.0
dragon-flight-speed: 1.5
dragon-flight-lift: 0.55
debug-loot-rolls: false

# External modules (BetterModel) are experimental and disabled by default.
experimental-modules: false

# Model rendering (only used when the BetterModel module is enabled)
model-ground-offset: 0.05
model-nametag-height: 2.6
model-facing-yaw-offset-degrees: 0.0

discovery-broadcasts:
  common: true
  rare: true
  epic: true
  legendary: true
  mythical: true

model-overrides: {}
```

---

## 🧩 Optional Modules

> **⚠️ Experimental.** External modules (such as BetterModel) are experimental and **disabled by default**. While `experimental-modules: false` in `config.yml`, the `/pets modules` command is blocked and no external module is activated, even if it is enabled in `modules.yml`. Set `experimental-modules: true` to opt in.

Module state is stored in:

```text
plugins/BetterPets/modules.yml
```

Modules are toggled in `/pets modules` (once `experimental-modules` is enabled). A module can only be enabled when its required plugin is installed and enabled. If a module is enabled in `modules.yml` but the required plugin is missing, Better Pets keeps the flag and skips activation until the dependency becomes available.

---

## 🪄 BetterModel Module

The `bettermodel` module requires the **BetterModel** plugin. When enabled, pets render as animated 3D `.bbmodel` models instead of floating pet heads.

Model folder:

```text
plugins/BetterPets/models/
```

File naming (the name without `.bbmodel` is matched case-insensitively to the pet id/name):

```text
ant.bbmodel
bat.bbmodel
blue_dragon.bbmodel
```

Override model names per pet in `config.yml`:

```yaml
model-overrides:
  Ant: tiny_ant
  Blue Dragon: blue_dragon_variant
```

### 🎞️ Animations & movement

Better Pets reads the animations declared inside each `.bbmodel` and drives them automatically:

| Animation         | When it plays                                       |
| ----------------- | --------------------------------------------------- |
| `idle`            | The owner is standing still                         |
| `walking`         | The owner is moving and the model is **grounded**   |
| `flying`          | The owner is moving and the model is **flying**     |
| `idle2` … `idle9` | Optional random idle variants while standing still  |

Whether a model is **grounded** or **flying** is decided automatically: a `flying` animation means it flies, a `walking` animation (without `flying`) means it walks on the ground. You can also **force** it through the model name: a model ending in `_grounded` always walks, and `_flying` always flies (e.g. `ant_grounded.bbmodel`).

### 🔄 Reloading models

When the module is enabled or `/pets reload` is run, Better Pets scans `plugins/BetterPets/models/`, copies changed `.bbmodel` files into BetterModel's `models/` folder, and reloads BetterModel. It first tries the BetterModel API reload and falls back to the console command:

```text
bettermodel reload
```

If BetterModel is missing, disabled, or has no matching loaded model, Better Pets falls back to the pet-head rendering.

### 📦 Resource pack note

BetterModel generates its own resource pack from the `.bbmodel` files and embedded textures. Vanilla clients still need BetterModel's **auto-send / host** resource-pack option enabled on the server.

---

## 🦙 Alpaca Storage

Alpaca storage is **owner-only** and saved inside:

```text
plugins/BetterPets/pets.yml
```

Items are stored with `ItemStack.serializeItemsAsBytes`, preserving modern item data such as custom enchantments, PersistentDataContainer values, plugin metadata, and Paper item data.

| Alpaca Level | Storage Size |
| ------------ | ------------ |
| Level 1      | 9 slots      |
| Level 30     | 18 slots     |
| Level 50     | 27 slots     |
| Level 70     | 36 slots     |
| Level 100    | 54 slots     |

> An Alpaca with stored items cannot be switched away, despawned, or converted into an item until its storage is empty.

---

## 🛠️ Building

With Maven:

```powershell
mvn clean package
```

Local fallback build script:

```powershell
.\build.ps1
```

The output jar is created at:

```text
target/better-pets-26.2-plugin.jar
```

---

## 📦 Installation

1. Build the jar (or download it from the [latest release](https://github.com/yourShika/betterpets-paper/releases/latest)).
2. Put `better-pets-26.2-plugin.jar` into the Paper server's `plugins` folder.
3. Install **BetterModel** only if you want animated 3D models.
4. Start the server once to generate config and storage files.
5. Use LuckPerms or `paper-plugin.yml` defaults to assign permissions.
6. (Optional) Put `.bbmodel` files into `plugins/BetterPets/models/`, set `experimental-modules: true`, enable the module in `/pets modules`, then run `/pets reload`.

---

## 🏛️ Chest Loot Notes

Pet rolls happen when Minecraft generates container loot for unopened generated containers (chests, double chests, barrels, and other block containers), including custom structures from data packs.

* Existing opened containers will not reroll.
* Each generated container can get at most one Better Pets item.
* Enable `debug-loot-rolls: true` to see loot roll attempts in the console.

---

## 🐾 Pet Sources

Pets can drop from four sources, each toggleable and tunable in `/pets drop` (or `config.yml` under `pet-sources`):

| Source | How | Default chance |
| --- | --- | --- |
| **Chests** | Generated container loot (chests, double chests, barrels, custom structures) | `chest-pet-chance-percent` (2.5%) |
| **Fishing** | Fish one out (replaces the catch) | 1.0% |
| **Wandering Trader** | A spawned trader carries a one-time pet deal priced by rarity | 25% per trader |
| **Brushing** | Brush one out of suspicious sand/gravel | 1.5% |

Each find shows a broadcast (e.g. `Player fished out a Rare Pet: Axolotl!`), gated by the same per-rarity toggles as `/pets notify`.

---

## 🗒️ Previous Releases

* **v1.2.4** — Pets look at the player again (facing fix), `/pets update`, double-chest discovery broadcasts.
* **v1.2.3** — Mending-proof XP, lighter saving, `/pets version`, animation-driven model movement, ground-height caching.
* **v1.2.2** — Optional module system (experimental) + BetterModel module; particles stop when the pet is hidden.
* **v1.2.1** — Owner-only glow reveal (through walls) for Bat / Red Parrot / Warden.
* **v1.2.0** — Multiplayer fixes: personal Herobrine weather, Allay no longer takes others' items, crash-safe join cleanup, double chests + barrels.
* **v1.1.0** — Three new pets (Mole, Allay, Cursed Plushie), reworked Phoenix revive, rarity broadcast sounds, `EXP: MAXED`.

---

## 📜 Credits

This project is a Paper plugin rewrite inspired by the original **Better Pets** datapack.

🔗 [Better Pets on Modrinth](https://modrinth.com/datapack/betterpets)

All rights to the original project, name, concepts, assets, and related content belong to their respective rights holders.

---

## ⚠️ Disclaimer

This project is **not an official update**, **not an official continuation**, and **not directly affiliated with the original Better Pets project**, unless explicitly stated otherwise. It does not claim ownership of the original project. If the original author or rights holder wants specific content removed, changed, or credited differently, please contact the repository owner.

---

## 📄 License

The **source code of this Paper plugin rewrite** is released under the **MIT License** — see [`LICENSE`](LICENSE).

This covers the plugin's **own code only**. The original **Better Pets** name, concepts, and assets remain with their respective rights holders (see *Credits* and *Disclaimer* above).
