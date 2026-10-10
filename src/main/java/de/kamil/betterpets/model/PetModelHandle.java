package de.kamil.betterpets.model;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

public interface PetModelHandle extends AutoCloseable {
    @Override
    void close();

    void hide(Player player);

    void show(Player player);

    /**
     * Starts a looping animation on top of "idle", which always runs underneath: walking, flying. It
     * blends in, and blends back out to idle when it is {@link #stop stopped} - nothing restarts.
     */
    void loop(String animation);

    /** Plays an animation once over whatever is running; when it is through, that shows again. */
    void once(String animation);

    /**
     * Plays an animation once, but lets it stand still for as long as {@code held} says so - a pet that
     * lies down to sleep stays down until its owner stirs, and only then gets up.
     */
    void onceHeld(String animation, java.util.function.BooleanSupplier held);

    /** Ends an animation started by one of the above before its time, blending out. */
    void stop(String animation);

    default void hideFromAll() {
        Bukkit.getOnlinePlayers().forEach(this::hide);
    }

    default void showToAll() {
        Bukkit.getOnlinePlayers().forEach(this::show);
    }
}
