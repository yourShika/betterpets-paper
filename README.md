# Better Pets Paper

Better Pets Paper is a full Paper plugin rewrite of the Better Pets datapack.
It does not require datapacks, command functions, minecart menus, or resource pack logic.

## Target

- Minecraft / Paper: `26.1.2`
- Java: `21`
- Optional: LuckPerms for permission assignment

## Features

- `/pets` inventory GUI for owned pets
- Pet catalogue with per-level milestone pages
- Per-pet chest spawn chance GUI
- Discovery broadcast GUI by rarity
- Pet XP multiplier GUI
- Persistent player pet storage with backups
- Persistent Alpaca storage with Paper item byte serialization
- Dynamic Alpaca storage size by level
- Dragon mount flight from level 50
- Chest loot integration for generated structure chest loot tables

## Commands

- `/pets` opens the main menu
- `/pets info` opens the pet catalogue
- `/pets chances` opens spawn chance settings
- `/pets notify` opens discovery broadcast settings
- `/pets give <pet|all> [level] [player]` gives test pet items

## Permissions

- `betterpets.command.pets` opens the main menu
- `betterpets.info` opens the catalogue
- `betterpets.chances` edits spawn chances, broadcasts, and XP multiplier
- `betterpets.give` gives test pet items
- `betterpets.admin` grants all Better Pets permissions

## Important Config

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

## Alpaca Storage

Alpaca storage is owner-only and saved inside `plugins/BetterPets/pets.yml`.
Items are stored with `ItemStack.serializeItemsAsBytes`, which preserves modern item data such as custom enchantments, PersistentDataContainer values, and plugin metadata.

Storage size by Alpaca level:

- Level 1: 9 slots
- Level 30: 18 slots
- Level 50: 27 slots
- Level 70: 36 slots
- Level 100: 54 slots

The plugin saves open Alpaca inventories on close, logout, autosave, and shutdown.
An Alpaca with stored items cannot be switched away, despawned, or converted into an item until the storage is empty.

## Building

With Maven:

```powershell
mvn clean package
```

Local fallback build script:

```powershell
.\build.ps1
```

The output jar is:

```text
target/better-pets-26.1.2-plugin.jar
```

## Installation

1. Build the jar.
2. Put `better-pets-26.1.2-plugin.jar` into the Paper server `plugins` folder.
3. Start the server once to generate config and storage files.
4. Use LuckPerms or `paper-plugin.yml` defaults to assign permissions.

## Notes

- Structure chest pet rolls happen when Minecraft generates chest loot, usually when an unopened generated chest is first opened.
- Existing opened chests will not reroll.
- Enable `debug-loot-rolls: true` to see chest roll attempts in the console.
