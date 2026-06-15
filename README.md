# 🐾 Better Pets Paper

> A full **Paper plugin rewrite** of the original Better Pets datapack.

![Minecraft](https://img.shields.io/badge/Minecraft%20%2F%20Paper-26.1.2-brightgreen)
![Java](https://img.shields.io/badge/Java-21-orange)
![Platform](https://img.shields.io/badge/Platform-Paper-blue)
![Status](https://img.shields.io/badge/Status-In%20Development-yellow)
![Type](https://img.shields.io/badge/Type-Plugin%20Rewrite-purple)

---

## ✨ About

**Better Pets Paper** is a complete **Paper plugin rewrite** of the Better Pets datapack.

It does **not** require datapacks, command functions, minecart menus, or resource pack logic.
Everything is handled directly through the plugin.

This version is designed to be easier to use on Paper servers while keeping the Better Pets experience alive in a plugin-based format.

---

## 🎯 Target

| Requirement         | Version / Info                      |
| ------------------- | ----------------------------------- |
| Minecraft / Paper   | `26.1.2`                            |
| Java                | `21`                                |
| Optional Dependency | LuckPerms for permission assignment |

---

## 🌟 Features

* 🐾 `/pets` inventory GUI for owned pets
* 📖 Pet catalogue with per-level milestone pages
* 🎲 Per-pet chest spawn chance GUI
* 📢 Discovery broadcast GUI by rarity
* ✨ Pet XP multiplier GUI
* 💾 Persistent player pet storage with backups
* 🦙 Persistent Alpaca storage using Paper item byte serialization
* 📦 Dynamic Alpaca storage size based on level
* 🐉 Dragon mount flight unlocked from level 50
* 🏛️ Chest loot integration for generated structure chest loot tables

---

## 💬 Commands

| Command                                  | Description                        |
| ---------------------------------------- | ---------------------------------- |
| `/pets`                                  | Opens the main pet menu            |
| `/pets info`                             | Opens the pet catalogue            |
| `/pets chances`                          | Opens spawn chance settings        |
| `/pets notify`                           | Opens discovery broadcast settings |
| `/pets give <pet\|all> [level] [player]` | Gives test pet items               |

---

## 🔐 Permissions

| Permission                | Description                                                 |
| ------------------------- | ----------------------------------------------------------- |
| `betterpets.command.pets` | Allows opening the main pet menu                            |
| `betterpets.info`         | Allows opening the pet catalogue                            |
| `betterpets.chances`      | Allows editing spawn chances, broadcasts, and XP multiplier |
| `betterpets.give`         | Allows giving test pet items                                |
| `betterpets.admin`        | Grants all Better Pets permissions                          |

---

## ⚙️ Important Config

```yaml
max-pets-per-player: 45
chest-pet-chance-percent: 2.5
pet-xp-multiplier: 1.0
dragon-flight-speed: 0.85
dragon-flight-lift: 0.36
debug-loot-rolls: false

discovery-broadcasts:
  common: true
  rare: true
  epic: true
  legendary: true
  extraordinary: true
```

---

## 🦙 Alpaca Storage

Alpaca storage is **owner-only** and saved inside:

```text
plugins/BetterPets/pets.yml
```

Items are stored using:

```text
ItemStack.serializeItemsAsBytes
```

This preserves modern item data such as:

* Custom enchantments
* PersistentDataContainer values
* Plugin metadata
* Modern Paper item data

### 📦 Storage Size by Alpaca Level

| Alpaca Level | Storage Size |
| ------------ | ------------ |
| Level 1      | 9 slots      |
| Level 30     | 18 slots     |
| Level 50     | 27 slots     |
| Level 70     | 36 slots     |
| Level 100    | 54 slots     |

The plugin saves open Alpaca inventories when:

* The inventory is closed
* The player logs out
* Autosave runs
* The server shuts down

> An Alpaca with stored items cannot be switched away, despawned, or converted into an item until its storage is empty.

---

## 🛠️ Building

### Build with Maven

```powershell
mvn clean package
```

### Local Fallback Build Script

```powershell
.\build.ps1
```

The output jar will be created here:

```text
target/better-pets-26.1.2-plugin.jar
```

---

## 📦 Installation

1. Build the plugin jar.
2. Put `better-pets-26.1.2-plugin.jar` into your Paper server's `plugins` folder.
3. Start the server once to generate the config and storage files.
4. Use LuckPerms or `paper-plugin.yml` defaults to assign permissions.
5. Enjoy Better Pets Paper 🐾

---

## 🏛️ Chest Loot Notes

Structure chest pet rolls happen when Minecraft generates chest loot.

This usually happens when an unopened generated chest is opened for the first time.

Important notes:

* Existing opened chests will not reroll.
* Only newly generated / unopened loot chests can roll pets.
* Enable `debug-loot-rolls: true` to see chest roll attempts in the console.

---

## 📁 Storage & Backups

Better Pets Paper stores player pet data persistently and includes backup handling for safer storage.

Main storage path:

```text
plugins/BetterPets/pets.yml
```

Alpaca inventories are also saved there using Paper's item byte serialization.

---

## 🧪 Development Status

This project is currently under development.

Features may change, commands may be adjusted, and config values may be expanded in future versions.

| Area                   | Status         |
| ---------------------- | -------------- |
| Paper Plugin Rewrite   | ✅ Implemented  |
| Pet GUI                | ✅ Implemented  |
| Pet Catalogue          | ✅ Implemented  |
| Alpaca Storage         | ✅ Implemented  |
| Dragon Flight          | ✅ Implemented  |
| Chest Loot Integration | ✅ Implemented  |
| Documentation          | 🚧 In Progress |

---

## 📜 Credits

This project is a Paper plugin rewrite inspired by the original **Better Pets** datapack.

Original project:

🔗 [Better Pets on Modrinth](https://modrinth.com/datapack/betterpets)

All rights to the original project, name, concepts, assets, and related content belong to their respective rights holders.

---

## ⚠️ Disclaimer

This project is **not an official update**, **not an official continuation**, and **not directly affiliated with the original Better Pets project**, unless explicitly stated otherwise.

This repository does **not claim ownership of the original project**.

Better Pets Paper is intended as a plugin-based rewrite for Paper servers, created to provide a server-friendly implementation with its own plugin logic.

If the original author or rights holder wants specific content removed, changed, or credited differently, please contact the repository owner.

---

## ❤️ Thank You

Thanks to the original Better Pets project for the idea and inspiration.

This rewrite was made with respect for the original concept and with the goal of making a plugin-based version for Paper servers.
