package de.kamil.betterpets;

import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;

import java.util.UUID;

// InventoryHolder types that tag each Better Pets GUI so the click/drag listeners can identify which
// menu was interacted with. Pure data; extracted from BetterPetsPlugin to keep that class focused on
// behaviour. Package-private so the plugin (same package) can read/write their state.

final class PetMenuHolder implements InventoryHolder {
    private final UUID owner;
    private int page;
    private Inventory inventory;

    PetMenuHolder(final UUID owner, final int page) {
        this.owner = owner;
        this.page = Math.max(0, page);
    }

    UUID owner() {
        return owner;
    }

    int page() {
        return page;
    }

    void setPage(final int page) {
        this.page = Math.max(0, page);
    }

    void setInventory(final Inventory inventory) {
        this.inventory = inventory;
    }

    @Override
    public Inventory getInventory() {
        return inventory;
    }
}

final class InfoMenuHolder implements InventoryHolder {
    private int page;
    private Inventory inventory;

    int page() {
        return page;
    }

    void setPage(final int page) {
        this.page = Math.max(0, page);
    }

    void setInventory(final Inventory inventory) {
        this.inventory = inventory;
    }

    @Override
    public Inventory getInventory() {
        return inventory;
    }
}

final class PetDetailMenuHolder implements InventoryHolder {
    private final String petId;
    private Inventory inventory;

    PetDetailMenuHolder(final String petId) {
        this.petId = petId;
    }

    String petId() {
        return petId;
    }

    void setInventory(final Inventory inventory) {
        this.inventory = inventory;
    }

    @Override
    public Inventory getInventory() {
        return inventory;
    }
}

/** Right-click customization for one owned pet: particle toggle, skin variant picker, and cosmetics. */
final class CustomizeMenuHolder implements InventoryHolder {
    private final java.util.UUID petUuid;
    private int page;
    // Which sub-view is shown: "main", "particle", "trail" or "nametag".
    private String section = "main";
    private Inventory inventory;

    CustomizeMenuHolder(final java.util.UUID petUuid, final int page) {
        this.petUuid = petUuid;
        this.page = page;
    }

    java.util.UUID petUuid() {
        return petUuid;
    }

    int page() {
        return page;
    }

    void setPage(final int page) {
        this.page = page;
    }

    String section() {
        return section;
    }

    void setSection(final String section) {
        this.section = section == null ? "main" : section;
    }

    void setInventory(final Inventory inventory) {
        this.inventory = inventory;
    }

    @Override
    public Inventory getInventory() {
        return inventory;
    }
}

/** The galactic Ascension view for one owned pet: current stars, progress and per-star ability. */
final class AscensionMenuHolder implements InventoryHolder {
    private final java.util.UUID petUuid;
    private Inventory inventory;

    AscensionMenuHolder(final java.util.UUID petUuid) {
        this.petUuid = petUuid;
    }

    java.util.UUID petUuid() {
        return petUuid;
    }

    void setInventory(final Inventory inventory) {
        this.inventory = inventory;
    }

    @Override
    public Inventory getInventory() {
        return inventory;
    }
}

/** The token cosmetics shop, replacing the old slot machine. Category = main/particle/trail/nametag/booster. */
final class ShopMenuHolder implements InventoryHolder {
    private String category;
    private int page;
    private Inventory inventory;

    ShopMenuHolder(final String category, final int page) {
        this.category = category == null ? "main" : category;
        this.page = page;
    }

    String category() {
        return category;
    }

    void setCategory(final String category) {
        this.category = category == null ? "main" : category;
    }

    int page() {
        return page;
    }

    void setPage(final int page) {
        this.page = page;
    }

    void setInventory(final Inventory inventory) {
        this.inventory = inventory;
    }

    @Override
    public Inventory getInventory() {
        return inventory;
    }
}

/** Paginated gallery of a single pet's cosmetic variants, opened from the catalogue detail view. */
final class VariantMenuHolder implements InventoryHolder {
    private final String petId;
    private int page;
    private Inventory inventory;

    VariantMenuHolder(final String petId, final int page) {
        this.petId = petId;
        this.page = page;
    }

    String petId() {
        return petId;
    }

    int page() {
        return page;
    }

    void setPage(final int page) {
        this.page = page;
    }

    void setInventory(final Inventory inventory) {
        this.inventory = inventory;
    }

    @Override
    public Inventory getInventory() {
        return inventory;
    }
}

final class ChanceMenuHolder implements InventoryHolder {
    private final UUID owner;
    private int page;
    private Inventory inventory;

    ChanceMenuHolder(final UUID owner) {
        this.owner = owner;
    }

    UUID owner() {
        return owner;
    }

    int page() {
        return page;
    }

    void setPage(final int page) {
        this.page = Math.max(0, page);
    }

    void setInventory(final Inventory inventory) {
        this.inventory = inventory;
    }

    @Override
    public Inventory getInventory() {
        return inventory;
    }
}

final class NotifyMenuHolder implements InventoryHolder {
    private final UUID owner;
    private Inventory inventory;

    NotifyMenuHolder(final UUID owner) {
        this.owner = owner;
    }

    UUID owner() {
        return owner;
    }

    void setInventory(final Inventory inventory) {
        this.inventory = inventory;
    }

    @Override
    public Inventory getInventory() {
        return inventory;
    }
}

final class XpMenuHolder implements InventoryHolder {
    private final UUID owner;
    private Inventory inventory;

    XpMenuHolder(final UUID owner) {
        this.owner = owner;
    }

    UUID owner() {
        return owner;
    }

    void setInventory(final Inventory inventory) {
        this.inventory = inventory;
    }

    @Override
    public Inventory getInventory() {
        return inventory;
    }
}

final class DropMenuHolder implements InventoryHolder {
    private final UUID owner;
    private Inventory inventory;

    DropMenuHolder(final UUID owner) {
        this.owner = owner;
    }

    UUID owner() {
        return owner;
    }

    void setInventory(final Inventory inventory) {
        this.inventory = inventory;
    }

    @Override
    public Inventory getInventory() {
        return inventory;
    }
}

final class ModulesMenuHolder implements InventoryHolder {
    private final UUID owner;
    private Inventory inventory;

    ModulesMenuHolder(final UUID owner) {
        this.owner = owner;
    }

    UUID owner() {
        return owner;
    }

    void setInventory(final Inventory inventory) {
        this.inventory = inventory;
    }

    @Override
    public Inventory getInventory() {
        return inventory;
    }
}

final class SlotMenuHolder implements InventoryHolder {
    private final UUID owner;
    // The pet featured this spin - if the reels land on the pet symbol, this is what you win.
    private String featuredPetId;
    private Inventory inventory;

    SlotMenuHolder(final UUID owner) {
        this.owner = owner;
    }

    UUID owner() {
        return owner;
    }

    String featuredPetId() {
        return featuredPetId;
    }

    void setFeaturedPetId(final String featuredPetId) {
        this.featuredPetId = featuredPetId;
    }

    void setInventory(final Inventory inventory) {
        this.inventory = inventory;
    }

    @Override
    public Inventory getInventory() {
        return inventory;
    }
}

final class SlotConfigMenuHolder implements InventoryHolder {
    private final UUID owner;
    private Inventory inventory;

    SlotConfigMenuHolder(final UUID owner) {
        this.owner = owner;
    }

    UUID owner() {
        return owner;
    }

    void setInventory(final Inventory inventory) {
        this.inventory = inventory;
    }

    @Override
    public Inventory getInventory() {
        return inventory;
    }
}

final class AlpacaStorageHolder implements InventoryHolder {
    private final UUID owner;
    private final UUID pet;
    private final int size;
    private Inventory inventory;

    AlpacaStorageHolder(final UUID owner, final UUID pet, final int size) {
        this.owner = owner;
        this.pet = pet;
        this.size = Math.max(9, Math.min(OwnedPet.STORAGE_SIZE, size));
    }

    UUID owner() {
        return owner;
    }

    UUID pet() {
        return pet;
    }

    int size() {
        return size;
    }

    void setInventory(final Inventory inventory) {
        this.inventory = inventory;
    }

    @Override
    public Inventory getInventory() {
        return inventory;
    }
}
