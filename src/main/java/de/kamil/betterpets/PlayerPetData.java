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
    // Per-player unlocked cosmetics, keyed by category (particle / trail / nametag) -> set of ids.
    private final Map<String, Set<String>> unlockedCosmetics = new LinkedHashMap<>();
    private UUID activePet;
    private boolean visible = true;
    // When true, this player receives no discovery/booster broadcast messages or sounds.
    private boolean broadcastsMuted;
    // Pet XP booster: tier (0 = none, else 2..5) and remaining time. Remaining time only counts down
    // while the player is online; boosterTickReference is a transient marker for that (not persisted).
    private int boosterTier;
    private long boosterRemainingMillis;
    private transient long boosterTickReference;
    // Pet tokens: currency earned by scrapping (duplicate) pets, spent in the /pets shop.
    private int tokens;
    // Last known player name, refreshed on join, so leaderboards can show names without blocking UUID lookups.
    private String playerName;
    // Quickslots: the pet (by definition id, lowercase) parked in each slot, null = empty. Keyed by the
    // definition rather than the pet's UUID because a player owns at most one pet per definition and a
    // pet gets a new UUID when it is converted to an item and added back - so a slot survives that trip.
    private final String[] quickslots = new String[MAX_QUICKSLOTS];
    // The player's own choice for the sneak+scroll quick switch; null = follow the server default.
    private Boolean quickScroll;

    /** Upper bound of quickslots a player can have (the server may allow fewer). */
    public static final int MAX_QUICKSLOTS = 9;

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
        // Saturating: never overflow int (which would wrap negative and wipe the balance to 0).
        this.tokens = (int) Math.max(0L, Math.min(Integer.MAX_VALUE, (long) this.tokens + amount));
    }

    /** True when this record holds no meaningful state (used to avoid persisting ghost/read-only lookups). */
    public boolean isEmpty() {
        return pets.isEmpty() && tokens == 0 && activePet == null && boosterTier == 0
            && playerName == null && visible && !broadcastsMuted
            && quickScroll == null && !hasQuickslots()
            && unlockedVariants.values().stream().allMatch(java.util.Set::isEmpty)
            && unlockedCosmetics.values().stream().allMatch(java.util.Set::isEmpty);
    }

    /** The pet definition id parked in a quickslot (0-based), or null if the slot is empty or out of range. */
    public String quickslot(final int index) {
        return index >= 0 && index < MAX_QUICKSLOTS ? quickslots[index] : null;
    }

    /**
     * Parks a pet definition in a quickslot ({@code null}/blank clears it). A pet lives in at most one
     * slot, so assigning it somewhere else moves it there.
     */
    public void setQuickslot(final int index, final String petId) {
        if (index < 0 || index >= MAX_QUICKSLOTS) {
            return;
        }
        final String normalized = petId == null || petId.isBlank() ? null : petId.toLowerCase(Locale.ROOT);
        if (normalized != null) {
            for (int i = 0; i < MAX_QUICKSLOTS; i++) {
                if (normalized.equals(quickslots[i])) {
                    quickslots[i] = null;
                }
            }
        }
        quickslots[index] = normalized;
    }

    /** The quickslot (0-based) holding the given pet definition, or -1. */
    public int quickslotOf(final String petId) {
        if (petId == null) {
            return -1;
        }
        final String normalized = petId.toLowerCase(Locale.ROOT);
        for (int i = 0; i < MAX_QUICKSLOTS; i++) {
            if (normalized.equals(quickslots[i])) {
                return i;
            }
        }
        return -1;
    }

    public boolean hasQuickslots() {
        for (final String slot : quickslots) {
            if (slot != null) {
                return true;
            }
        }
        return false;
    }

    public void clearQuickslots() {
        java.util.Arrays.fill(quickslots, null);
    }

    /** The player's sneak+scroll preference, or null if they never chose (server default applies). */
    public Boolean quickScroll() {
        return quickScroll;
    }

    public void setQuickScroll(final Boolean quickScroll) {
        this.quickScroll = quickScroll;
    }

    /** The owned pet of a definition (a player owns at most one), preferring the highest level if legacy data has several. */
    public Optional<OwnedPet> findByDefinition(final String definitionId) {
        if (definitionId == null) {
            return Optional.empty();
        }
        OwnedPet best = null;
        for (final OwnedPet pet : pets) {
            if (pet.definitionId().equalsIgnoreCase(definitionId) && (best == null || pet.level() > best.level())) {
                best = pet;
            }
        }
        return Optional.ofNullable(best);
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

    /**
     * Takes a variant out of the player's collection again - for the skin a pet is wearing when it leaves
     * its owner. The skin goes with the pet (the item says so) and comes back with it.
     */
    public void lockVariant(final String petId, final String variant) {
        if (petId == null || variant == null) {
            return;
        }
        final Set<String> set = unlockedVariants.get(petId.toLowerCase(Locale.ROOT));
        if (set != null) {
            set.remove(variant.toLowerCase(Locale.ROOT));
        }
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

    public boolean unlockCosmetic(final String category, final String id) {
        if (category == null || id == null || id.isBlank()) {
            return false;
        }
        return unlockedCosmetics
            .computeIfAbsent(category.toLowerCase(Locale.ROOT), ignored -> new LinkedHashSet<>())
            .add(id.toLowerCase(Locale.ROOT));
    }

    public boolean hasCosmetic(final String category, final String id) {
        if (category == null || id == null) {
            return false;
        }
        final Set<String> set = unlockedCosmetics.get(category.toLowerCase(Locale.ROOT));
        return set != null && set.contains(id.toLowerCase(Locale.ROOT));
    }

    public Set<String> cosmetics(final String category) {
        if (category == null) {
            return Set.of();
        }
        return Collections.unmodifiableSet(unlockedCosmetics.getOrDefault(category.toLowerCase(Locale.ROOT), Set.of()));
    }

    /** All unlocked cosmetics keyed by category (for persistence). */
    public Map<String, Set<String>> unlockedCosmeticsByCategory() {
        return unlockedCosmetics;
    }

    public String playerName() {
        return playerName;
    }

    public void setPlayerName(final String playerName) {
        this.playerName = playerName == null || playerName.isBlank() ? null : playerName;
    }

    /** Total ascension stars across all owned pets (for leaderboards). */
    public int totalStars() {
        int total = 0;
        for (final OwnedPet pet : pets()) {
            total += pet.stars();
        }
        return total;
    }
}
