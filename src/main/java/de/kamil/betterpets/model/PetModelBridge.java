package de.kamil.betterpets.model;

import org.bukkit.entity.Entity;

import java.io.File;
import java.util.Optional;

public interface PetModelBridge {
    File dataFolder();

    boolean modelExists(String modelName);

    Optional<PetModelHandle> attachModel(String modelName, Entity baseEntity);

    boolean reload();
}
