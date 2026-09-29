#!/usr/bin/env python3
"""
Generates the bundled Oraxen assets for Better Pets from the prepared GUI/icon textures.

It reads the raw art under paper-plugin/GUI/... and writes a self-contained "oraxen/" bundle into
src/main/resources/oraxen/ that the OraxenAssetDeployer copies into an installed Oraxen at runtime:

  oraxen/items/betterpets_gui_icons.yml   - one PAPER item per icon (generated model + custom_model_data)
  oraxen/pack/textures/betterpets/icons/  - the icon PNGs
  oraxen/pack/textures/betterpets/gui/    - the 256x256 menu background PNGs
  oraxen/pack/font/betterpets_menu.json   - a bitmap font: one glyph per menu background + space shifts
  oraxen/asset-manifest.properties        - version + sha256 of every bundled file (change detection)

Only textures the user prepared are copied - nothing is drawn procedurally. Re-run after adding art.

The font overlay offsets (ASCENT / LEAD / TAIL) reproduce the backpack's proven 256-wide setup; the
panel art still usually needs a little in-game fine-tuning (see README), which is done here + /oraxen reload.
"""
import json
import hashlib
import shutil
from pathlib import Path

# --- paths -----------------------------------------------------------------------------------------
SCRIPT = Path(__file__).resolve()
PLUGIN = SCRIPT.parent.parent                      # paper-plugin/
GUI = PLUGIN / "GUI"
OUT = PLUGIN / "src/main/resources/oraxen"
TEX_ICONS = OUT / "pack/textures/betterpets/icons"
TEX_GUI = OUT / "pack/textures/betterpets/gui"
MODELS_ICONS = OUT / "pack/models/betterpets/icons"
ITEMDEFS_ICONS = OUT / "pack/items/betterpets/icons"   # 1.21.4+ item_model definitions
FONT_DIR = OUT / "pack/font"
ITEMS_DIR = OUT / "items"

# Raw asset roots (the user's prepared packages).
CORE = GUI / "BetterPets_UI_Assets (1)"
MENUS5 = GUI / "BetterPets_5_Menus"
EXTRA = GUI / "BetterPets_Extra_Icons"
# Corrected backgrounds: exact 176x222 panel at (40,17), all 10 menus in one flat folder.
EXACT_GUI = GUI / "BetterPets_Exact_GUI"

# --- tunables --------------------------------------------------------------------------------------
ASSET_VERSION = 1
CMD_START = 3000                 # custom_model_data base for icons (kept clear of the backpack's 2300s)
ICON_SIZE = "icons_32x32"        # source icon resolution to bundle (32x32 = crisp)
GUI_VARIANT = "gui_256"          # background variant to bundle (256x256, panel origin 40,17)
# Panel placement. Derived from the exact art geometry (panel 176x222 at image (40,17), overlay
# displacement (-40,-17)): LEAD -48 lands the panel's left edge on the container, ascent ~30 lands its
# top. Tune in-game if needed: raise FONT_ASCENT to move the panel UP, lower to move DOWN; make
# SPACE_LEAD_ADV more negative to move LEFT (keep LEAD + 257 + TAIL == 0 so the title stays put).
FONT_ASCENT = 30
FONT_HEIGHT = 256
SPACE_LEAD = ""            # cursor shift BEFORE drawing the background
SPACE_TAIL = ""            # cursor shift AFTER drawing the background
SPACE_LEAD_ADV = -48             # horizontal lead (more negative = panel further left)
SPACE_TAIL_ADV = -209            # horizontal tail (LEAD + 257 + TAIL == 0)

# Menu backgrounds -> private-use glyph char. MUST match OraxenUi.MENU_GLYPH in Java.
MENU_BACKGROUNDS = {
    "main": "",
    "catalogue": "",
    "customization": "",
    "ascension": "",
    "shop": "",
    "shop_category": "",
    "leaderboard": "",
    "trade": "",
    "pet_details": "",
    "variants": "",
}

# Icons to bundle. Missing files are skipped with a warning (so partial art still generates).
ICONS = [
    # original approved set
    "ascension_info", "ascension_level", "back", "catalogue", "close", "despawn",
    "nametag_style", "next_page", "particle_color", "particles_off", "particles_on",
    "pet_to_item", "previous_page", "spawn_chances_admin", "trail", "visibility_off",
    "visibility_on", "xp_booster",
    # extra icons
    "filter_sort", "token_balance", "shop_skins", "shop_buy",
    "leaderboard_pets", "leaderboard_stars", "leaderboard_tokens", "your_rank",
    "trade_offer_pet", "trade_confirm", "trade_clear", "trade_cancel",
    "trade_token_plus", "trade_token_minus", "trade_empty_offer",
    "ascension_current", "ascension_locked", "ascension_reached",
    "admin_toggle_on", "admin_toggle_off", "admin_xp_plus", "admin_xp_minus",
]

# Where to look for each icon file, in order.
ICON_SOURCES = [CORE / ICON_SIZE, EXTRA / ICON_SIZE]
# Where to look for each menu background, in order. The corrected exact-geometry pack wins; the older
# gui_256 variants stay as a fallback for any menu it does not include.
GUI_SOURCES = [EXACT_GUI, CORE / GUI_VARIANT, MENUS5 / GUI_VARIANT]


def find(sources, name):
    for base in sources:
        p = base / (name + ".png")
        if p.exists():
            return p
    return None


def sha256(path: Path) -> str:
    h = hashlib.sha256()
    h.update(path.read_bytes())
    return h.hexdigest()


def main():
    for d in (TEX_ICONS, TEX_GUI, MODELS_ICONS, ITEMDEFS_ICONS, FONT_DIR, ITEMS_DIR):
        d.mkdir(parents=True, exist_ok=True)

    warnings = []

    # 1) icons -> textures + item YAML ---------------------------------------------------------------
    lines = [
        "# Better Pets GUI icons - generated by scripts/generate_oraxen_assets.py.",
        "# Pure texture/model carriers: the plugin only shows them inside its menus (never as real items).",
        "",
    ]
    cmd = CMD_START
    for name in ICONS:
        src = find(ICON_SOURCES, name)
        if src is None:
            warnings.append("icon missing: " + name)
            continue
        shutil.copyfile(src, TEX_ICONS / (name + ".png"))
        # Ship the flat item model...
        (MODELS_ICONS / (name + ".json")).write_text(json.dumps({
            "parent": "minecraft:item/generated",
            "textures": {"layer0": "minecraft:betterpets/icons/" + name},
        }, indent=2), encoding="utf-8")
        # ...and the 1.21.4+ item definition that the item_model component resolves to
        # (minecraft:betterpets/icons/<name> -> assets/minecraft/items/betterpets/icons/<name>.json).
        # Together these render the icon from the pack alone, with no Oraxen item registration needed.
        (ITEMDEFS_ICONS / (name + ".json")).write_text(json.dumps({
            "model": {"type": "minecraft:model", "model": "minecraft:betterpets/icons/" + name},
        }, indent=2), encoding="utf-8")
        lines += [
            "betterpets_" + name + ":",
            '  displayname: "<gray>' + name.replace("_", " ").title() + '"',
            "  material: PAPER",
            "  Pack:",
            "    generate_model: true",
            '    parent_model: "item/generated"',
            "    textures:",
            "      - betterpets/icons/" + name + ".png",
            "    custom_model_data: " + str(cmd),
            "",
        ]
        cmd += 1
    (ITEMS_DIR / "betterpets_gui_icons.yml").write_text("\n".join(lines), encoding="utf-8")

    # 2) menu backgrounds -> textures + font ---------------------------------------------------------
    providers = [{
        "type": "space",
        "advances": {SPACE_LEAD: SPACE_LEAD_ADV, SPACE_TAIL: SPACE_TAIL_ADV},
    }]
    for menu, glyph in MENU_BACKGROUNDS.items():
        src = find(GUI_SOURCES, menu)
        if src is None:
            warnings.append("background missing: " + menu)
            continue
        shutil.copyfile(src, TEX_GUI / (menu + ".png"))
        providers.append({
            "type": "bitmap",
            "file": "minecraft:betterpets/gui/" + menu + ".png",
            "ascent": FONT_ASCENT,
            "height": FONT_HEIGHT,
            "chars": [glyph],
        })
    (FONT_DIR / "betterpets_menu.json").write_text(
        json.dumps({"providers": providers}, indent=2, ensure_ascii=False), encoding="utf-8")

    # 3) manifest ------------------------------------------------------------------------------------
    manifest = ["# Generated by scripts/generate_oraxen_assets.py", "asset-version=" + str(ASSET_VERSION)]
    for f in sorted(OUT.rglob("*")):
        if f.is_file() and f.name != "asset-manifest.properties":
            rel = "oraxen/" + f.relative_to(OUT).as_posix()
            manifest.append("sha256." + rel + "=" + sha256(f))
    (OUT / "asset-manifest.properties").write_text("\n".join(manifest) + "\n", encoding="utf-8")

    icons_done = cmd - CMD_START
    bgs_done = len(providers) - 1
    print(f"Generated {icons_done} icons, {bgs_done} menu backgrounds, asset-version {ASSET_VERSION}.")
    for w in warnings:
        print("  WARN:", w)


if __name__ == "__main__":
    main()
