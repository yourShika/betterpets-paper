package de.kamil.betterpets.model;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

public interface PetModelHandle extends AutoCloseable {
    @Override
    void close();

    void hide(Player player);

    void show(Player player);

    /** Plays the given animation on the model (idle / walking / flying / idleN). */
    void play(String animation);

    default void hideFromAll() {
        Bukkit.getOnlinePlayers().forEach(this::hide);
    }

    default void showToAll() {
        Bukkit.getOnlinePlayers().forEach(this::show);
    }
}
