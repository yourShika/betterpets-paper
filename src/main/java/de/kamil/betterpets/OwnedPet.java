package de.kamil.betterpets;

import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;

import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

public final class OwnedPet {
    public static final int STORAGE_SIZE = 54;
    private final UUID uuid;
    private final String definitionId;
    private int level;
    private int exp;
    private int nextLevelExp;
    private long lastTotemMillis;
    private String customName;
    private String variant;
    private final Set<String> unlockedVariants = new LinkedHashSet<>();
    private boolean particlesEnabled = true;
    private ItemStack[] storageContents;

    public OwnedPet(final UUID uuid, final String definitionId, final int level, final int exp, final int nextLevelExp, final long lastTotemMillis) {
        this(uuid, definitionId, level, exp, nextLevelExp, lastTotemMillis, new ItemStack[STORAGE_SIZE]);
    }

    public OwnedPet(final UUID uuid, final String definitionId, final int level, final int exp, final int nextLevelExp, final long lastTotemMillis, final ItemStack[] storageContents) {
        this.uuid = uuid;
        this.definitionId = definitionId;
        this.level = Math.max(1, Math.min(100, level));
        this.exp = Math.max(0, exp);
        this.nextLevelExp = Math.max(1, nextLevelExp);
        this.lastTotemMillis = Math.max(0L, lastTotemMillis);
        setStorageContents(storageContents);
    }

    public static OwnedPet create(final String definitionId, final int level) {
        final int safeLevel = Math.max(1, Math.min(100, level));
        return new OwnedPet(UUID.randomUUID(), definitionId, safeLevel, 0, expForNextLevel(safeLevel + 1), 0L);
    }

    public static int scaledExpForNextLevel(final int nextLevel, final double multiplier) {
        final double safeMultiplier = Math.max(0.1, Math.min(5.0, multiplier));
        return Math.max(1, (int) Math.ceil(expForNextLevel(nextLevel) / safeMultiplier));
    }

    public UUID uuid() {
        return uuid;
    }

    public String definitionId() {
        return definitionId;
    }

    public int level() {
        return level;
    }

    public int exp() {
        return exp;
    }

    public int nextLevelExp() {
        return nextLevelExp;
    }

    public long lastTotemMillis() {
        return lastTotemMillis;
    }

    public void setLastTotemMillis(final long lastTotemMillis) {
        this.lastTotemMillis = Math.max(0L, lastTotemMillis);
    }

    /** The player-chosen display name, or null when the pet uses its default definition name. */
    public String customName() {
        return customName;
    }

    public boolean hasCustomName() {
        return customName != null && !customName.isBlank();
    }

    public void setCustomName(final String customName) {
        this.customName = customName == null || customName.isBlank() ? null : customName;
    }

    /** The rolled cosmetic variant (e.g. an Axolotl style), or null when the pet has none. */
    public String variant() {
        return variant;
    }

    /** Sets the active cosmetic variant and unlocks it (the worn skin is always unlocked). */
    public void setVariant(final String variant) {
        if (variant == null || variant.isBlank()) {
            this.variant = null;
            return;
        }
        this.variant = variant.toLowerCase(Locale.ROOT);
        unlockedVariants.add(this.variant);
    }

    /** The set of cosmetic variants this pet has unlocked (via rolls or scrapping duplicates). */
    public Set<String> unlockedVariants() {
        return Collections.unmodifiableSet(unlockedVariants);
    }

    public boolean isVariantUnlocked(final String key) {
        return key != null && unlockedVariants.contains(key.toLowerCase(Locale.ROOT));
    }

    /** Unlocks a variant without changing the active skin. Returns true if it was newly added. */
    public boolean unlockVariant(final String key) {
        if (key == null || key.isBlank()) {
            return false;
        }
        return unlockedVariants.add(key.toLowerCase(Locale.ROOT));
    }

    /** Bulk restore of unlocked variants from storage. */
    public void setUnlockedVariants(final Iterable<String> keys) {
        unlockedVariants.clear();
        if (keys != null) {
            for (final String key : keys) {
                if (key != null && !key.isBlank()) {
                    unlockedVariants.add(key.toLowerCase(Locale.ROOT));
                }
            }
        }
        if (variant != null) {
            unlockedVariants.add(variant);
        }
    }

    public boolean particlesEnabled() {
        return particlesEnabled;
    }

    public void setParticlesEnabled(final boolean particlesEnabled) {
        this.particlesEnabled = particlesEnabled;
    }

    public ItemStack[] storageContents() {
        return Arrays.copyOf(storageContents, STORAGE_SIZE);
    }

    public ItemStack[] storageContents(final int size) {
        return Arrays.copyOf(storageContents, Math.max(0, Math.min(STORAGE_SIZE, size)));
    }

    public void setStorageContents(final ItemStack[] contents) {
        storageContents = new ItemStack[STORAGE_SIZE];
        if (contents == null) {
            return;
        }
        for (int i = 0; i < Math.min(STORAGE_SIZE, contents.length); i++) {
            final ItemStack item = contents[i];
            if (item != null && item.getType() != Material.AIR) {
                storageContents[i] = item.clone();
            }
        }
    }

    public boolean hasStoredItems() {
        return Arrays.stream(storageContents).anyMatch(item -> item != null && item.getType() != Material.AIR);
    }

    public boolean addExp(final int amount, final double multiplier) {
        if (amount <= 0 || level >= 100) {
            return false;
        }

        boolean leveled = false;
        nextLevelExp = scaledExpForNextLevel(level + 1, multiplier);
        exp += amount;
        while (level < 100 && exp >= nextLevelExp) {
            exp -= nextLevelExp;
            level++;
            nextLevelExp = scaledExpForNextLevel(level + 1, multiplier);
            leveled = true;
        }

        if (level >= 100) {
            level = 100;
            exp = 0;
            nextLevelExp = scaledExpForNextLevel(101, multiplier);
        }

        return leveled;
    }

    /** Restores saved in-level progress (used when a converted pet item is turned back into a pet). */
    public void setExp(final int exp) {
        this.exp = Math.max(0, exp);
        if (level >= 100) {
            this.exp = 0;
        } else if (this.exp >= nextLevelExp) {
            this.exp = Math.max(0, nextLevelExp - 1);
        }
    }

    public void recalculateNextLevelExp(final double multiplier) {
        nextLevelExp = scaledExpForNextLevel(level + 1, multiplier);
        if (level >= 100) {
            exp = 0;
        } else if (exp >= nextLevelExp) {
            exp = Math.max(0, nextLevelExp - 1);
        }
    }

    private static int expForNextLevel(final int nextLevel) {
        return (2 * nextLevel * nextLevel) + (4 * nextLevel) + 10;
    }
}
