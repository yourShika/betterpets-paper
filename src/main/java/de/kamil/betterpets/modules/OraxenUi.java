package de.kamil.betterpets.modules;

import net.kyori.adventure.key.Key;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.plugin.java.JavaPlugin;

import java.lang.reflect.Method;
import java.util.List;
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
    // Reflected io.th0rgal.oraxen.api.OraxenItems#getItemById(String); resolved lazily, cached.
    private Method getItemById;
    private Method builderBuild;
    private boolean reflectionTried;
    private boolean warnedUnresolved;
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
     * Returns {@code fallback} re-skinned with the Oraxen icon {@code betterpets_<iconId>} (same name/lore),
     * or {@code fallback} unchanged when icons are off or the Oraxen item is unavailable.
     */
    public ItemStack icon(final String iconId, final ItemStack fallback) {
        if (!iconsOn() || fallback == null) {
            return fallback;
        }
        final ItemStack skinned = oraxenItem("betterpets_" + iconId);
        if (skinned == null) {
            return fallback;
        }
        final ItemMeta from = fallback.getItemMeta();
        final ItemMeta to = skinned.getItemMeta();
        if (from != null && to != null) {
            if (from.hasDisplayName()) {
                to.displayName(from.displayName());
            }
            if (from.hasLore()) {
                to.lore(from.lore());
            }
            if (from.hasEnchants() || from.getEnchantmentGlintOverride() != null) {
                to.setEnchantmentGlintOverride(from.getEnchantmentGlintOverride());
            }
            skinned.setItemMeta(to);
        }
        skinned.setAmount(Math.max(1, fallback.getAmount()));
        return skinned;
    }

    /** Builds an Oraxen item by id via reflection, or {@code null} if Oraxen/the id is unavailable. */
    private ItemStack oraxenItem(final String id) {
        try {
            if (!reflectionTried) {
                reflectionTried = true;
                final Class<?> items = Class.forName("io.th0rgal.oraxen.api.OraxenItems");
                getItemById = items.getMethod("getItemById", String.class);
            }
            if (getItemById == null) {
                return null;
            }
            final Object builder = getItemById.invoke(null, id);
            if (builder == null) {
                if (!warnedUnresolved) {
                    warnedUnresolved = true;
                    plugin.getLogger().warning("Oraxen module: item '" + id + "' is not registered in Oraxen, so"
                        + " the custom menu icons fall back to vanilla. Run \"/oraxen reload\" (or restart the"
                        + " server) after the assets were deployed so Oraxen loads betterpets_gui_icons.yml."
                        + " If it persists, check Oraxen's console for a load error on that file.");
                }
                return null;
            }
            if (builderBuild == null) {
                builderBuild = builder.getClass().getMethod("build");
            }
            final Object stack = builderBuild.invoke(builder);
            return stack instanceof ItemStack itemStack ? itemStack.clone() : null;
        } catch (final Throwable throwable) {
            if (!warnedNoApi) {
                warnedNoApi = true;
                plugin.getLogger().warning("Oraxen module: could not build icon '" + id + "' via the Oraxen API ("
                    + throwable.getClass().getSimpleName() + ": " + throwable.getMessage() + "). Menu icons stay vanilla.");
            }
            return null;
        }
    }
}
