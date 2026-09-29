package de.kamil.betterpets.modules;

import net.kyori.adventure.key.Key;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.Map;

/**
 * Bridges the plugin's menus to the optional Oraxen resource pack, without ever hard-depending on Oraxen:
 * every Oraxen call goes through reflection, and every method degrades to the plain vanilla item/title when
 * the {@code oraxen} module is off or Oraxen is absent. So the menus keep working unchanged without Oraxen,
 * and gain the custom backgrounds/icons when it is present.
 *
 * <ul>
 *   <li>{@link #title(String, Component)} prepends a menu-background glyph (from the bundled
 *       {@code betterpets_menu} font) to an inventory title.</li>
 *   <li>{@link #icon(String, ItemStack)} re-skins a control button with the matching Oraxen icon item
 *       (keeping the button's own name and lore).</li>
 * </ul>
 */
public final class OraxenUi {

    private static final Key MENU_FONT = Key.key("minecraft", "betterpets_menu");
    private static final String LEAD = "";
    private static final String TAIL = "";
    // Menu id -> background glyph. MUST match MENU_BACKGROUNDS in generate_oraxen_assets.py.
    private static final Map<String, String> MENU_GLYPH = Map.ofEntries(
        Map.entry("main", ""),
        Map.entry("catalogue", ""),
        Map.entry("customization", ""),
        Map.entry("ascension", ""),
        Map.entry("shop", ""),
        Map.entry("shop_category", ""),
        Map.entry("leaderboard", ""),
        Map.entry("trade", ""),
        Map.entry("pet_details", ""),
        Map.entry("variants", ""));

    private final JavaPlugin plugin;
    private final ModuleManager moduleManager;
    private boolean warnedNoApi;

    public OraxenUi(final JavaPlugin plugin, final ModuleManager moduleManager) {
        this.plugin = plugin;
        this.moduleManager = moduleManager;
    }

    /** Whether the Oraxen GUI skin is currently active (module on + not disabled in config). */
    public boolean active() {
        return moduleManager != null && moduleManager.isActive(OraxenModule.ID)
            && plugin.getConfig().getBoolean("oraxen.gui-enabled", true);
    }

    private boolean backgroundsOn() {
        return active() && plugin.getConfig().getBoolean("oraxen.gui-background", true);
    }

    private boolean iconsOn() {
        return active() && plugin.getConfig().getBoolean("oraxen.gui-icons", true);
    }

    /**
     * Prepends the given menu's background glyph to a title. The glyph renders the panel PNG behind the
     * inventory; the visible title is kept as a sibling in the default font so it renders normally.
     */
    public Component title(final String menu, final Component visible) {
        if (!backgroundsOn()) {
            return visible;
        }
        final String glyph = MENU_GLYPH.get(menu);
        if (glyph == null) {
            return visible;
        }
        final Component bg = Component.text(LEAD + glyph + TAIL)
            .font(MENU_FONT)
            .color(NamedTextColor.WHITE)
            .decoration(TextDecoration.ITALIC, false);
        // Root component uses the default font, so the visible title never inherits betterpets_menu.
        return Component.text().append(bg).append(visible).build();
    }

    /**
     * Re-skins {@code fallback} with the bundled icon model, keeping its name/lore. Uses the vanilla
     * {@code item_model} component pointing at {@code minecraft:betterpets/icons/<iconId>} — a model + texture
     * that ship in the pack (copied verbatim like the working backgrounds). This deliberately does NOT rely
     * on Oraxen registering our items, so the icons render as soon as the pack is (re)built, with no full
     * server restart needed. Returns {@code fallback} unchanged when icons are off.
     */
    public ItemStack icon(final String iconId, final ItemStack fallback) {
        if (!iconsOn() || fallback == null) {
            return fallback;
        }
        final ItemStack out = fallback.clone();
        final ItemMeta meta = out.getItemMeta();
        if (meta == null) {
            return fallback;
        }
        try {
            meta.setItemModel(new org.bukkit.NamespacedKey(org.bukkit.NamespacedKey.MINECRAFT, "betterpets/icons/" + iconId));
            out.setItemMeta(meta);
            return out;
        } catch (final Throwable throwable) {
            if (!warnedNoApi) {
                warnedNoApi = true;
                plugin.getLogger().warning("Oraxen module: could not set item_model for '" + iconId + "' ("
                    + throwable.getClass().getSimpleName() + "). Menu icons stay vanilla.");
            }
            return fallback;
        }
    }
}
