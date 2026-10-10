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
    // A few ticks of blending in and out, so that one animation flows into the next instead of the model
    // snapping to a pose.
    private static final AnimationModifier LOOP = AnimationModifier.builder()
        .start(5).end(6).type(kr.toxicity.model.api.animation.AnimationIterator.Type.LOOP).build();
    private static final AnimationModifier ONCE = AnimationModifier.builder()
        .start(4).end(6).type(kr.toxicity.model.api.animation.AnimationIterator.Type.PLAY_ONCE).build();

    @Override
    public File dataFolder() {
        return BetterModel.platform().dataFolder();
    }

    @Override
    public boolean modelExists(final String modelName) {
        return BetterModel.model(modelName).isPresent();
    }

    @Override
    public Optional<PetModelHandle> attachModel(final String modelName, final Entity baseEntity, final float scale) {
        return BetterModel.model(modelName).map(renderer -> {
            final EntityTracker tracker = renderer.getOrCreate(BukkitAdapter.adapt(baseEntity), TrackerModifier.DEFAULT);
            if (Math.abs(scale - 1.0F) > 0.001F) {
                tracker.scaler(kr.toxicity.model.api.tracker.ModelScaler.value(scale));
            }
            tracker.animate("idle", LOOP);
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
        public void loop(final String animation) {
            if (!tracker.isClosed()) {
                tracker.animate(animation, LOOP);
            }
        }

        @Override
        public void once(final String animation) {
            if (!tracker.isClosed()) {
                tracker.animate(animation, ONCE);
            }
        }

        @Override
        public void onceHeld(final String animation, final java.util.function.BooleanSupplier held) {
            if (!tracker.isClosed()) {
                // Held = time all but stands still for this animation; let go, it runs on to its end.
                tracker.animate(animation, ONCE.toBuilder().speed(() -> held.getAsBoolean() ? 0.0005F : 1.0F).build());
            }
        }

        @Override
        public void stop(final String animation) {
            if (!tracker.isClosed()) {
                tracker.stopAnimation(animation);
            }
        }
    }
}
