package de.kamil.betterpets.modules;

import de.kamil.betterpets.quickslots.HeldSlotInterceptor;
import org.bukkit.Material;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * Optional integration with the PacketEvents plugin: it is what makes the "sneak + mouse wheel" quickslot
 * switch possible. Swallowing a wheel notch means cancelling the client's hotbar-change packet before the
 * server (and every other plugin) acts on it, which the Bukkit API cannot do - so without PacketEvents
 * this way of switching simply stays off, and everything else works as usual.
 *
 * <p>PacketEvents types are only referenced inside {@link PacketEventsScrollHook}, which this class
 * creates after checking that PacketEvents is installed - so nothing here breaks on servers without it.</p>
 */
public final class PacketEventsModule implements Module {

    public static final String ID = "packetevents";

    private final JavaPlugin plugin;
    private final HeldSlotInterceptor interceptor;
    // Held as a plain Runnable so this class itself never names a PacketEvents-dependent type.
    private Runnable unregister;

    public PacketEventsModule(final JavaPlugin plugin, final HeldSlotInterceptor interceptor) {
        this.plugin = plugin;
        this.interceptor = interceptor;
    }

    @Override
    public String id() {
        return ID;
    }

    @Override
    public String displayName() {
        return "PacketEvents Quickslots";
    }

    @Override
    public String description() {
        return "Sneak + mouse wheel switches between quickslot pets.";
    }

    @Override
    public Material iconMaterial() {
        return Material.COMPARATOR;
    }

    @Override
    public String requiredPluginName() {
        return "packetevents";
    }

    @Override
    public void onEnable() {
        try {
            unregister = PacketEventsScrollHook.register(interceptor);
        } catch (final LinkageError incompatible) {
            // A PacketEvents build whose API no longer matches must not take the plugin down with it.
            throw new IllegalStateException("incompatible PacketEvents version (" + incompatible + ")");
        }
        interceptor.setScrollHookActive(true);
    }

    @Override
    public void onDisable() {
        interceptor.setScrollHookActive(false);
        if (unregister != null) {
            try {
                unregister.run();
            } catch (final RuntimeException | LinkageError exception) {
                plugin.getLogger().warning("Could not unregister the PacketEvents quickslot hook: " + exception.getMessage());
            }
            unregister = null;
        }
    }

    @Override
    public boolean isAvailable() {
        return plugin.getServer().getPluginManager().isPluginEnabled(requiredPluginName());
    }
}
