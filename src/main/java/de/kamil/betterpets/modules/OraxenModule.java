package de.kamil.betterpets.modules;

import org.bukkit.Material;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;

/**
 * Optional integration with the Oraxen resource-pack plugin. When enabled (and Oraxen is installed), the
 * bundled GUI backgrounds and control icons are deployed into Oraxen so the Better Pets menus render with
 * custom pixel-art instead of vanilla items. Purely cosmetic: the plugin works identically without it.
 *
 * <p>No Oraxen class is referenced directly here or in {@link OraxenUi} except through reflection, so the
 * plugin compiles and runs on servers that do not have Oraxen.</p>
 */
public final class OraxenModule implements Module {

    public static final String ID = "oraxen";

    private final JavaPlugin plugin;
    private final File jar;

    public OraxenModule(final JavaPlugin plugin, final File jar) {
        this.plugin = plugin;
        this.jar = jar;
    }

    @Override
    public String id() {
        return ID;
    }

    @Override
    public String displayName() {
        return "Oraxen GUI";
    }

    @Override
    public String description() {
        return "Custom menu backgrounds & icons via Oraxen (cosmetic).";
    }

    @Override
    public Material iconMaterial() {
        return Material.PAINTING;
    }

    @Override
    public String requiredPluginName() {
        return "Oraxen";
    }

    @Override
    public void onEnable() {
        // Deploy the bundled item defs / textures / font into Oraxen. Best-effort; never throws.
        new OraxenAssetDeployer(plugin, jar).deploy();
    }

    @Override
    public void onDisable() {
        // Nothing to undo: the deployed assets stay in Oraxen, the menus simply stop using them because
        // OraxenUi#active() now returns false.
    }

    @Override
    public boolean isAvailable() {
        return plugin.getServer().getPluginManager().isPluginEnabled(requiredPluginName());
    }
}
