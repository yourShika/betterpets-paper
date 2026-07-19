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
