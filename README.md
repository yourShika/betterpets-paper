# 🐾 Better Pets Paper

> A full **Paper plugin rewrite** of the original Better Pets datapack.

![Minecraft](https://img.shields.io/badge/Minecraft%20%2F%20Paper-26.1.2-brightgreen)
![Java](https://img.shields.io/badge/Java-21-orange)
![Platform](https://img.shields.io/badge/Platform-Paper-blue)
![Version](https://img.shields.io/badge/Version-1.5.0-blueviolet)
![Type](https://img.shields.io/badge/Type-Plugin%20Rewrite-purple)

---

## ✨ About

**Better Pets Paper** is a complete **Paper plugin rewrite** of the Better Pets datapack.

It does **not** require datapacks, command functions, minecart menus, or a manual resource pack.
Everything is handled directly through the plugin. Optional animated 3D models are handled through
**BetterModel** when that plugin is installed and the (experimental) module is enabled.

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
| Minecraft / Paper   | `26.1.2`                                                  |
| Java                | `21` (Java `25` recommended when using BetterModel 3.x)   |
| Optional Dependency | LuckPerms for permission assignment                       |
| Optional Dependency | BetterModel `3.x` for animated `.bbmodel` pets            |

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

---

## 🔐 Permissions

| Permission                | Description                                                          |
| ------------------------- | ------------------------------------------------------------------- |
| `betterpets.command.pets` | Allows opening the main pet menu                                     |
| `betterpets.info`         | Allows opening the pet catalogue                                    |
| `betterpets.chances`      | Allows editing spawn chances, broadcasts, and XP multiplier         |
| `betterpets.give`         | Allows giving test pet items                                        |
| `betterpets.admin`        | Grants all admin actions, including `/pets modules` and `/pets reload` |

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
target/better-pets-26.1.2-plugin.jar
```

---

## 📦 Installation

1. Build the jar (or download it from the [latest release](https://github.com/yourShika/betterpets-paper/releases/latest)).
2. Put `better-pets-26.1.2-plugin.jar` into the Paper server's `plugins` folder.
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
