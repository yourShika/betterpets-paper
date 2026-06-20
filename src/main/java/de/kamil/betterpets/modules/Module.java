package de.kamil.betterpets.modules;

import org.bukkit.Material;

public interface Module {
    String id();

    String displayName();

    String description();

    Material iconMaterial();

    String requiredPluginName();

    void onEnable();

    void onDisable();

    boolean isAvailable();
}
