package de.kamil.betterpets.api;

import de.kamil.betterpets.BetterPetsPlugin;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

/**
 * Small, stable public API for other plugins (e.g. yourShika Backpacks). All methods are static so a
 * foreign plugin can call them by reflection with no hard dependency on BetterPets — and every method
 * degrades safely (returns {@code false}/{@code 0}) when BetterPets is not loaded/enabled.
 */
public final class BetterPetsApi {

    private BetterPetsApi() {
    }

    /** true if the item is a Pet XP Booster (PDC booster_tier &gt; 0). */
    public static boolean isBoosterItem(final ItemStack item) {
        final BetterPetsPlugin plugin = BetterPetsPlugin.getInstance();
        return plugin != null && plugin.boosterTier(item) > 0;
    }

    /** Booster tier 2..5, or 0 if the item is not a booster. */
    public static int boosterTier(final ItemStack item) {
        final BetterPetsPlugin plugin = BetterPetsPlugin.getInstance();
        return plugin == null ? 0 : plugin.boosterTier(item);
    }

    /** Configured booster minutes stored on the item, or 0 if it is not a booster. */
    public static int boosterMinutes(final ItemStack item) {
        final BetterPetsPlugin plugin = BetterPetsPlugin.getInstance();
        return plugin == null ? 0 : plugin.boosterMinutes(item);
    }

    /** Whether the player currently has an active Pet XP Booster running. */
    public static boolean hasActiveBooster(final Player player) {
        final BetterPetsPlugin plugin = BetterPetsPlugin.getInstance();
        return plugin != null && plugin.hasActiveBooster(player);
    }

    /**
     * Activates a booster (tier 2..5, minutes) for the player exactly like consuming a booster item, but
     * WITHOUT requiring or consuming a physical item — the calling plugin removes the item from its own
     * storage. Boosters never stack: if one is already active, nothing changes and this returns false.
     * On success the player gets the usual activation message, sound and (config-gated) broadcast.
     *
     * @return true on success, false if a booster is already active, the tier is invalid, or BetterPets is not loaded.
     */
    public static boolean activateBooster(final Player player, final int tier, final int minutes) {
        final BetterPetsPlugin plugin = BetterPetsPlugin.getInstance();
        return plugin != null && plugin.activateBooster(player, tier, minutes);
    }
}
