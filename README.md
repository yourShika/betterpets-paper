# 🐾 Better Pets Paper

> A full **Paper plugin rewrite** of the original Better Pets datapack.

![Minecraft](https://img.shields.io/badge/Minecraft%20%2F%20Paper-26.1.2-brightgreen)
![Java](https://img.shields.io/badge/Java-21-orange)
![Platform](https://img.shields.io/badge/Platform-Paper-blue)
![Version](https://img.shields.io/badge/Version-1.2.5-blueviolet)
![Type](https://img.shields.io/badge/Type-Plugin%20Rewrite-purple)

---

## ✨ About

**Better Pets Paper** is a complete **Paper plugin rewrite** of the Better Pets datapack.

It does **not** require datapacks, command functions, minecart menus, or a manual resource pack.
Everything is handled directly through the plugin. Optional animated 3D models are handled through
**BetterModel** when that plugin is installed and the (experimental) module is enabled.

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
  extraordinary: true

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
