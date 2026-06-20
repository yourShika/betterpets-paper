package de.kamil.betterpets.model;

import kr.toxicity.model.api.BetterModel;
import kr.toxicity.model.api.BetterModelPlatform;
import kr.toxicity.model.api.animation.AnimationModifier;
import kr.toxicity.model.api.bukkit.platform.BukkitAdapter;
import kr.toxicity.model.api.tracker.EntityTracker;
import kr.toxicity.model.api.tracker.TrackerModifier;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;

import java.io.File;
import java.util.Optional;

public final class BetterModelHook implements PetModelBridge {
    @Override
    public File dataFolder() {
        return BetterModel.platform().dataFolder();
    }

    @Override
    public boolean modelExists(final String modelName) {
        return BetterModel.model(modelName).isPresent();
    }

    @Override
    public Optional<PetModelHandle> attachModel(final String modelName, final Entity baseEntity) {
        return BetterModel.model(modelName).map(renderer -> {
            final EntityTracker tracker = renderer.getOrCreate(BukkitAdapter.adapt(baseEntity), TrackerModifier.DEFAULT);
            tracker.animate("idle", AnimationModifier.DEFAULT);
            return new BetterModelTrackerHandle(tracker);
        });
    }

    @Override
    public boolean reload() {
        final BetterModelPlatform.ReloadResult result = BetterModel.platform().reload();
        return result instanceof BetterModelPlatform.ReloadResult.Success;
    }

    private static final class BetterModelTrackerHandle implements PetModelHandle {
        private final EntityTracker tracker;

        private BetterModelTrackerHandle(final EntityTracker tracker) {
            this.tracker = tracker;
        }

        @Override
        public void close() {
            if (!tracker.isClosed()) {
                tracker.close();
            }
        }

        @Override
        public void hide(final Player player) {
            if (!tracker.isClosed()) {
                tracker.hide(BukkitAdapter.adapt(player));
            }
        }

        @Override
        public void show(final Player player) {
            if (!tracker.isClosed()) {
                tracker.show(BukkitAdapter.adapt(player));
            }
        }

        @Override
        public void play(final String animation) {
            if (!tracker.isClosed()) {
                tracker.animate(animation, AnimationModifier.DEFAULT);
            }
        }
    }
}
