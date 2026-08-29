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

/** Leaderboard GUI (/pets top). Tracks which ranking category is being viewed. */
final class LeaderboardMenuHolder implements InventoryHolder {
    private final UUID owner;
    private String category;
    private Inventory inventory;

    LeaderboardMenuHolder(final UUID owner, final String category) {
        this.owner = owner;
        this.category = category == null ? "pets" : category;
    }

    UUID owner() {
        return owner;
    }

    String category() {
        return category;
    }

    void setCategory(final String category) {
        this.category = category == null ? "pets" : category;
    }

    void setInventory(final Inventory inventory) {
        this.inventory = inventory;
    }

    @Override
    public Inventory getInventory() {
        return inventory;
    }
}

/**
 * A two-player trade window. Both sides offer tokens; each must confirm; the deal only completes when
 * both have confirmed. The same holder instance backs both players' inventories so state stays in sync.
 */
final class TradeMenuHolder implements InventoryHolder {
    static final int MAX_OFFER_ITEMS = 6;

    private final UUID initiator;
    private final UUID partner;
    private int initiatorTokens;
    private int partnerTokens;
    private boolean initiatorConfirmed;
    private boolean partnerConfirmed;
    // Offered pet items are held here (removed from the player's inventory when offered), never in a shared
    // clickable slot, so there is no way to dupe or lose them: on complete they go to the other player, on
    // cancel/close they go back to their owner.
    private final java.util.List<org.bukkit.inventory.ItemStack> initiatorItems = new java.util.ArrayList<>();
    private final java.util.List<org.bukkit.inventory.ItemStack> partnerItems = new java.util.ArrayList<>();
    private boolean settled;
    private Inventory inventory;

    TradeMenuHolder(final UUID initiator, final UUID partner) {
        this.initiator = initiator;
        this.partner = partner;
    }

    java.util.List<org.bukkit.inventory.ItemStack> itemsOf(final UUID id) {
        return initiator.equals(id) ? initiatorItems : partnerItems;
    }

    /** True once the trade has been completed or cancelled, so close-handling only refunds once. */
    boolean settled() {
        return settled;
    }

    void markSettled() {
        settled = true;
    }

    UUID initiator() {
        return initiator;
    }

    UUID partner() {
        return partner;
    }

    boolean isInitiator(final UUID id) {
        return initiator.equals(id);
    }

    int tokensOf(final UUID id) {
        return initiator.equals(id) ? initiatorTokens : partnerTokens;
    }

    void setTokensOf(final UUID id, final int value) {
        final int clamped = Math.max(0, value);
        if (initiator.equals(id)) {
            initiatorTokens = clamped;
        } else {
            partnerTokens = clamped;
        }
        // Any change to an offer cancels both confirmations, so nobody confirms a deal that then changed.
        initiatorConfirmed = false;
        partnerConfirmed = false;
    }

    boolean confirmed(final UUID id) {
        return initiator.equals(id) ? initiatorConfirmed : partnerConfirmed;
    }

    void setConfirmed(final UUID id, final boolean value) {
        if (initiator.equals(id)) {
            initiatorConfirmed = value;
        } else {
            partnerConfirmed = value;
        }
    }

    boolean bothConfirmed() {
        return initiatorConfirmed && partnerConfirmed;
    }

    void setInventory(final Inventory inventory) {
        this.inventory = inventory;
    }

    @Override
    public Inventory getInventory() {
        return inventory;
    }
}
