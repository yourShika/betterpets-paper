# Better Pets Paper

Better Pets Paper is a full Paper plugin rewrite of the Better Pets datapack for Minecraft / Paper `26.1.2`.

No datapack, command-function menu, minecart menu, or external manual resource pack is required by Better Pets itself. Optional 3D pet models are handled through BetterModel when that plugin is installed and the module is enabled.

## Requirements

| Requirement | Version / Info |
| --- | --- |
| Minecraft / Paper | `26.1.2` |
| Java | `21` for Better Pets, Java `25` recommended when using BetterModel 3.x |
| Optional | LuckPerms for permissions |
| Optional | BetterModel `3.x` for animated `.bbmodel` pets |

## Commands

| Command | Description |
| --- | --- |
| `/pets` | Opens the main pet menu |
| `/pets help` | Shows command help |
| `/pets info` | Opens the pet catalogue |
| `/pets chances` | Opens spawn chance settings |
| `/pets notify` | Opens discovery broadcast settings |
| `/pets modules` | Opens optional module settings |
| `/pets reload` | Reloads config, modules, models, and active pets |
| `/pets give <pet\|all> [level] [player]` | Gives test pet items |

## Permissions

| Permission | Description |
| --- | --- |
| `betterpets.command.pets` | Allows opening the main pet menu |
| `betterpets.info` | Allows opening the pet catalogue |
| `betterpets.chances` | Allows editing spawn chances, discovery broadcasts, and XP multiplier |
| `betterpets.give` | Allows giving test pet items |
| `betterpets.admin` | Grants all Better Pets admin actions, including `/pets modules` and `/pets reload` |

## Optional Modules

> **Experimental.** External modules (such as BetterModel) are experimental and **disabled by default**. While `experimental-modules: false` in `config.yml`, the `/pets modules` command is blocked and no external module is activated, even if it is enabled in `modules.yml`. Set `experimental-modules: true` to use them.

Better Pets stores module state in:

```text
plugins/BetterPets/modules.yml
```

Modules can be toggled in `/pets modules` (once `experimental-modules` is enabled). A module can only be enabled when its required plugin is installed and enabled. If a module is enabled in `modules.yml` but the required plugin is missing, Better Pets keeps the flag and skips activation until the dependency becomes available.

## BetterModel Module

The `bettermodel` module requires the `BetterModel` plugin. When enabled, pets can render as animated 3D `.bbmodel` models instead of floating pet heads.

Model folder:

```text
plugins/BetterPets/models/
```

File naming convention:

```text
ant.bbmodel
bat.bbmodel
blue_dragon.bbmodel
```

The file name without `.bbmodel` is matched case-insensitively against the pet id/name. You can override model names in `config.yml`:

```yaml
model-overrides:
  Ant: tiny_ant
  Blue Dragon: blue_dragon_variant
```

Model movement can be tuned globally:

```yaml
model-movement-mode: "flying" # flying, ground, grounded, or floor
model-ground-offset: 0.05
model-nametag-height: 2.2
model-facing-yaw-offset-degrees: 0.0
```

Use `ground`/`grounded` if your models should walk on the terrain instead of floating. If a specific `.bbmodel` was built with its front side rotated differently, adjust `model-facing-yaw-offset-degrees` in steps like `90`, `180`, or `-90`.

When the module is enabled or `/pets reload` is run, Better Pets scans `plugins/BetterPets/models/`, copies changed `.bbmodel` files into BetterModel's `models/` folder, and reloads BetterModel. It first tries the BetterModel API reload and falls back to the console command:

```text
bettermodel reload
```

If BetterModel is missing, disabled, or does not contain a matching loaded model, Better Pets falls back to the existing pet-head rendering.

### Resource Pack Note

BetterModel generates its own resource pack from the `.bbmodel` files and embedded textures. Vanilla clients still need BetterModel's auto-send/host resource-pack option enabled on the server.

## Important Config

```yaml
max-pets-per-player: 45
chest-pet-chance-percent: 2.5
pet-xp-multiplier: 1.0
dragon-flight-speed: 0.85
dragon-flight-lift: 0.36
debug-loot-rolls: false
model-movement-mode: "flying"
model-ground-offset: 0.05
model-nametag-height: 2.2
model-facing-yaw-offset-degrees: 0.0

discovery-broadcasts:
  common: true
  rare: true
  epic: true
  legendary: true
  extraordinary: true

model-overrides: {}
```

## Features

- `/pets` inventory GUI for owned pets
- Pet catalogue with level milestone detail pages
- Spawn chance GUI
- Discovery broadcast GUI by rarity
- Pet XP multiplier GUI
- Persistent player pet storage with backups
- Owner-only Alpaca storage using Paper item byte serialization
- Dynamic Alpaca storage size by level
- Dragon mount flight from level 50
- Generated chest loot integration for vanilla and custom structure containers
- Optional BetterModel 3D pet rendering with head fallback

## Alpaca Storage

Alpaca storage is owner-only and saved in:

```text
plugins/BetterPets/pets.yml
```

Items are stored with:

```text
ItemStack.serializeItemsAsBytes
```

That preserves modern item data such as custom enchantments, PersistentDataContainer values, plugin metadata, and Paper item data.

| Alpaca Level | Storage Size |
| --- | --- |
| Level 1 | 9 slots |
| Level 30 | 18 slots |
| Level 50 | 27 slots |
| Level 70 | 36 slots |
| Level 100 | 54 slots |

An Alpaca with stored items cannot be switched away, despawned, or converted into an item until its storage is empty.

## Building

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

## Installation

1. Build the plugin jar.
2. Put `better-pets-26.1.2-plugin.jar` into the Paper server `plugins` folder.
3. Install BetterModel only if you want animated 3D models.
4. Start the server once to generate config and storage files.
5. Use LuckPerms or `paper-plugin.yml` defaults to assign permissions.
6. Put optional `.bbmodel` files into `plugins/BetterPets/models/`.
7. Run `/pets reload`.

## Chest Loot Notes

Pet rolls happen when Minecraft generates container loot for unopened generated containers.

- Existing opened containers will not reroll.
- Each generated container can get at most one Better Pets item.
- Enable `debug-loot-rolls: true` to see loot roll attempts in the console.

## Credits

This project is a Paper plugin rewrite inspired by the original Better Pets datapack. All rights to the original project, name, concepts, assets, and related content belong to their respective rights holders.
