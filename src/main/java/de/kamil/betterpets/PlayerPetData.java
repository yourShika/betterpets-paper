package de.kamil.betterpets;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

public final class PlayerPetData {
    private final List<OwnedPet> pets = new ArrayList<>();
    // Per-player collection of unlocked cosmetic variants, keyed by pet definition id. Kept at the player
    // level (not per owned pet) so a scrapped/collected skin stays yours even if you do not own the pet.
    private final Map<String, Set<String>> unlockedVariants = new LinkedHashMap<>();
    private UUID activePet;
    private boolean visible = true;
    // When true, this player receives no discovery/booster broadcast messages or sounds.
    private boolean broadcastsMuted;
    // Pet XP booster: tier (0 = none, else 2..5) and remaining time. Remaining time only counts down
    // while the player is online; boosterTickReference is a transient marker for that (not persisted).
    private int boosterTier;
    private long boosterRemainingMillis;
    private transient long boosterTickReference;
    // Pet tokens: currency earned by scrapping (duplicate) pets, spent in the /pets slots machine.
    private int tokens;
    // The pet currently featured in the slot machine; only re-rolled after a spin, not on each open.
    private String slotFeaturedPet;

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

    public int tokens() {
        return tokens;
    }

    public void setTokens(final int tokens) {
        this.tokens = Math.max(0, tokens);
    }

    public void addTokens(final int amount) {
        this.tokens = Math.max(0, this.tokens + amount);
    }

    /** Unlocks a cosmetic variant for a pet definition (per player). Returns true if newly added. */
    public boolean unlockVariant(final String petId, final String variant) {
        if (petId == null || variant == null || variant.isBlank()) {
            return false;
        }
        return unlockedVariants
            .computeIfAbsent(petId.toLowerCase(Locale.ROOT), ignored -> new LinkedHashSet<>())
            .add(variant.toLowerCase(Locale.ROOT));
    }

    public boolean isVariantUnlocked(final String petId, final String variant) {
        if (petId == null || variant == null) {
            return false;
        }
        final Set<String> set = unlockedVariants.get(petId.toLowerCase(Locale.ROOT));
        return set != null && set.contains(variant.toLowerCase(Locale.ROOT));
    }

    public Set<String> unlockedVariants(final String petId) {
        if (petId == null) {
            return Set.of();
        }
        return Collections.unmodifiableSet(unlockedVariants.getOrDefault(petId.toLowerCase(Locale.ROOT), Set.of()));
    }

    /** All unlocked variants keyed by pet id (for persistence). */
    public Map<String, Set<String>> unlockedVariantsByPet() {
        return unlockedVariants;
    }

    public String slotFeaturedPet() {
        return slotFeaturedPet;
    }

    public void setSlotFeaturedPet(final String slotFeaturedPet) {
        this.slotFeaturedPet = slotFeaturedPet == null || slotFeaturedPet.isBlank() ? null : slotFeaturedPet;
    }
}
