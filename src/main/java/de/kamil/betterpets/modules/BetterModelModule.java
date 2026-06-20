package de.kamil.betterpets.modules;

import de.kamil.betterpets.model.PetModelService;
import org.bukkit.Material;
import org.bukkit.plugin.java.JavaPlugin;

public final class BetterModelModule implements Module {
    public static final String ID = "bettermodel";

    private final JavaPlugin plugin;
    private final PetModelService modelService;

    public BetterModelModule(final JavaPlugin plugin, final PetModelService modelService) {
        this.plugin = plugin;
        this.modelService = modelService;
    }

    @Override
    public String id() {
        return ID;
    }

    @Override
    public String displayName() {
        return "BetterModel";
    }

    @Override
    public String description() {
        return "Animated 3D .bbmodel pets with head fallback.";
    }

    @Override
    public Material iconMaterial() {
        return Material.ARMOR_STAND;
    }

    @Override
    public String requiredPluginName() {
        return "BetterModel";
    }

    @Override
    public void onEnable() {
        if (!modelService.enable()) {
            throw new IllegalStateException("BetterModel service could not be enabled.");
        }
    }

    @Override
    public void onDisable() {
        modelService.disable();
    }

    @Override
    public boolean isAvailable() {
        return plugin.getServer().getPluginManager().isPluginEnabled(requiredPluginName());
    }
}
