package de.kamil.betterpets;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public final class PlayerPetData {
    private final List<OwnedPet> pets = new ArrayList<>();
    private UUID activePet;
    private boolean visible = true;
    // When true, this player receives no discovery/booster broadcast messages or sounds.
    private boolean broadcastsMuted;
    // Pet XP booster: tier (0 = none, else 2..5) and remaining time. Remaining time only counts down
    // while the player is online; boosterTickReference is a transient marker for that (not persisted).
    private int boosterTier;
    private long boosterRemainingMillis;
    private transient long boosterTickReference;

    public List<OwnedPet> pets() {
        return pets;
    }

    public Optional<OwnedPet> activePet() {
        if (activePet == null) {
            return Optional.empty();
        }
        return findPet(activePet);
    }

    public UUID activePetId() {
        return activePet;
    }

    public void setActivePet(final UUID activePet) {
        this.activePet = activePet;
    }

    public boolean visible() {
        return visible;
    }

    public void setVisible(final boolean visible) {
        this.visible = visible;
    }

    public boolean broadcastsMuted() {
        return broadcastsMuted;
    }

    public void setBroadcastsMuted(final boolean broadcastsMuted) {
        this.broadcastsMuted = broadcastsMuted;
    }

    public Optional<OwnedPet> findPet(final UUID uuid) {
        return pets.stream().filter(pet -> pet.uuid().equals(uuid)).findFirst();
    }

    public boolean hasDefinition(final String definitionId) {
        return pets.stream().anyMatch(pet -> pet.definitionId().equals(definitionId));
    }

    public boolean removePet(final UUID uuid) {
        if (uuid.equals(activePet)) {
            activePet = null;
        }
        return pets.removeIf(pet -> pet.uuid().equals(uuid));
    }

    public boolean hasActiveBooster() {
        return boosterTier > 1 && boosterRemainingMillis > 0L;
    }

    public int boosterTier() {
        return boosterTier;
    }

    public long boosterRemainingMillis() {
        return boosterRemainingMillis;
    }

    public void setBooster(final int tier, final long remainingMillis) {
        this.boosterTier = Math.max(0, tier);
        this.boosterRemainingMillis = Math.max(0L, remainingMillis);
    }

    public void clearBooster() {
        this.boosterTier = 0;
        this.boosterRemainingMillis = 0L;
    }

    public long boosterTickReference() {
        return boosterTickReference;
    }

    public void setBoosterTickReference(final long boosterTickReference) {
        this.boosterTickReference = boosterTickReference;
    }
}
