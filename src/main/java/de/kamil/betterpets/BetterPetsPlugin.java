package de.kamil.betterpets;

import io.papermc.paper.command.brigadier.BasicCommand;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.plugin.lifecycle.event.types.LifecycleEvents;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Sound;
import org.bukkit.block.TileState;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.entity.EntityDismountEvent;
import org.bukkit.event.entity.EntityTargetLivingEntityEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.player.PlayerExpChangeEvent;
import org.bukkit.event.player.PlayerInputEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.event.player.PlayerToggleSneakEvent;
import org.bukkit.event.world.LootGenerateEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.loot.LootTable;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.projectiles.ProjectileSource;
import org.bukkit.scheduler.BukkitTask;

import java.util.Collection;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Random;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

public final class BetterPetsPlugin extends JavaPlugin implements Listener {
    private static final String USE_PERMISSION = "betterpets.command.pets";
    private static final String GIVE_PERMISSION = "betterpets.give";
    private static final String CHANCES_PERMISSION = "betterpets.chances";
    private static final String INFO_PERMISSION = "betterpets.info";
    private static final String ADMIN_PERMISSION = "betterpets.admin";
    private static final int PET_SLOT_LIMIT = 45;
    private static final Map<String, String> DEFAULT_MESSAGES = Map.ofEntries(
        Map.entry("messages.only-players", "Only players can use /pets."),
        Map.entry("messages.no-permission", "You do not have permission to use /pets."),
        Map.entry("messages.cooldown", "Please wait a moment before opening the pet menu again."),
        Map.entry("messages.pet-added", "Added %pet% to your pet list."),
        Map.entry("messages.duplicate-pet", "You already have this pet."),
        Map.entry("messages.pet-limit", "You have reached the maximum amount of pets."),
        Map.entry("messages.active-pet", "You summoned %pet%."),
        Map.entry("messages.despawned", "Your active pet has been despawned."),
        Map.entry("messages.converted", "Converted %pet% back to an item."),
        Map.entry("messages.no-active-pet", "You do not have an active pet."),
        Map.entry("messages.no-space", "Your inventory is full, so the item was dropped."),
        Map.entry("messages.usage-give", "Usage: /pets give <pet|all> [level] [player]"),
        Map.entry("messages.unknown-pet", "Unknown pet: %pet%"),
        Map.entry("messages.player-not-found", "Player not found: %player%"),
        Map.entry("messages.test-give", "Gave %pet% level %level% to %player%."),
        Map.entry("messages.chances-saved", "Spawn chance for %pet% is now %chance%%."),
        Map.entry("messages.info-opened", "Opening pet catalogue."),
        Map.entry("messages.alpaca-storage-not-empty", "Empty the Alpaca storage before switching, despawning, or converting this pet."),
        Map.entry("messages.no-pet-storage", "Pet items cannot be stored inside pet storage.")
    );

    private PetDefinitions definitions;
    private PetStorage storage;
    private PetItemFactory itemFactory;
    private ActivePetManager activePets;
    private NamespacedKey generatedChestKey;
    private BukkitTask saveTask;
    private final Map<UUID, Long> menuCooldowns = new HashMap<>();

    @Override
    public void onEnable() {
        getLogger().info("Starting Better Pets as a pure Paper plugin.");
        saveDefaultConfig();
        getConfig().options().copyDefaults(true);
        saveConfig();

        definitions = PetDefinitions.load(this);
        getLogger().info("Loaded " + definitions.all().size() + " pet definitions.");
        ensureSpawnChanceDefaults();
        if (getServer().getPluginManager().isPluginEnabled("LuckPerms")) {
            getLogger().info("LuckPerms detected. Permission nodes: betterpets.command.pets, betterpets.give, betterpets.chances, betterpets.info, betterpets.admin.");
        }

        itemFactory = new PetItemFactory(this);
        generatedChestKey = new NamespacedKey(this, "pet_loot_generated");
        storage = new PetStorage(this);
        storage.load();
        recalculateAllPetExp();
        getLogger().info("Loaded pet storage for " + storage.playerCount() + " player(s).");

        activePets = new ActivePetManager(this, definitions, storage, itemFactory);
        activePets.start();
        getLogger().info("Registered Java abilities for " + definitions.all().size() + " pet(s).");

        getServer().getPluginManager().registerEvents(this, this);
        getLifecycleManager().registerEventHandler(LifecycleEvents.COMMANDS, event ->
            event.registrar().register(
                "pets",
                "Open the Better Pets menu or run Better Pets admin tools.",
                List.of("pet", "betterpets"),
                new PetsCommand(this)
            )
        );

        final int saveInterval = Math.max(1200, getConfig().getInt("save-interval-ticks", 6000));
        saveTask = Bukkit.getScheduler().runTaskTimer(this, () -> {
            saveOpenAlpacaStorages();
            storage.save();
        }, saveInterval, saveInterval);
        getLogger().info("Better Pets enabled. Auto-save interval: " + saveInterval + " ticks.");
    }

    @Override
    public void onDisable() {
        getLogger().info("Stopping Better Pets.");
        if (saveTask != null) {
            saveTask.cancel();
            saveTask = null;
        }
        if (activePets != null) {
            activePets.stop();
        }
        if (storage != null) {
            saveOpenAlpacaStorages();
            storage.save();
            getLogger().info("Pet storage saved.");
        }
    }

    void openPetsMenu(final Player player) {
        if (!has(player, USE_PERMISSION)) {
            player.sendMessage(message("messages.no-permission"));
            return;
        }

        final long now = System.currentTimeMillis();
        final long cooldownMillis = Math.max(0L, getConfig().getLong("open-cooldown-ticks", 20L) * 50L);
        if (now < menuCooldowns.getOrDefault(player.getUniqueId(), 0L)) {
            player.sendMessage(message("messages.cooldown"));
            return;
        }
        menuCooldowns.put(player.getUniqueId(), now + cooldownMillis);

        final PetMenuHolder holder = new PetMenuHolder(player.getUniqueId(), 0);
        final Inventory inventory = Bukkit.createInventory(holder, 54, Component.text("Better Pets", NamedTextColor.GOLD));
        holder.setInventory(inventory);
        renderMenu(player, inventory);
        player.openInventory(inventory);
    }

    @EventHandler
    public void onInventoryClick(final InventoryClickEvent event) {
        if (!(event.getView().getTopInventory().getHolder() instanceof PetMenuHolder holder)) {
            if (event.getView().getTopInventory().getHolder() instanceof ChanceMenuHolder chanceHolder) {
                handleChanceClick(event, chanceHolder);
            } else if (event.getView().getTopInventory().getHolder() instanceof XpMenuHolder xpHolder) {
                handleXpClick(event, xpHolder);
            } else if (event.getView().getTopInventory().getHolder() instanceof AlpacaStorageHolder alpacaHolder) {
                handleAlpacaStorageClick(event, alpacaHolder);
            } else if (event.getView().getTopInventory().getHolder() instanceof InfoMenuHolder) {
                handleInfoClick(event);
            } else if (event.getView().getTopInventory().getHolder() instanceof PetDetailMenuHolder detailHolder) {
                handlePetDetailClick(event, detailHolder);
            }
            return;
        }

        event.setCancelled(true);
        if (!(event.getWhoClicked() instanceof Player player) || !player.getUniqueId().equals(holder.owner())) {
            return;
        }

        final int slot = event.getRawSlot();
        if (slot < 0 || slot >= event.getView().getTopInventory().getSize()) {
            return;
        }

        final PlayerPetData data = storage.data(player.getUniqueId());
        if (slot >= 0 && slot < PET_SLOT_LIMIT) {
            final int petIndex = (holder.page() * PET_SLOT_LIMIT) + slot;
            if (petIndex < 0 || petIndex >= data.pets().size()) {
                return;
            }
            final OwnedPet selectedPet = data.pets().get(petIndex);
            itemFactory.petUuid(event.getCurrentItem()).flatMap(data::findPet).filter(pet -> pet.uuid().equals(selectedPet.uuid())).ifPresent(pet -> {
                if (!pet.uuid().equals(data.activePetId()) && activeAlpacaStorageLocked(player, data)) {
                    return;
                }
                data.setActivePet(pet.uuid());
                activePets.spawn(player, pet);
                storage.save();
                definitions.get(pet.definitionId()).ifPresent(definition ->
                    player.sendMessage(message("messages.active-pet").replaceText(builder -> builder.matchLiteral("%pet%").replacement(definition.name())))
                );
                player.playSound(player.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 0.7F, 1.6F);
                renderMenu(player, event.getView().getTopInventory());
            });
            return;
        }

        switch (slot) {
            case 45 -> {
                if (holder.page() > 0) {
                    holder.setPage(holder.page() - 1);
                    renderMenu(player, event.getView().getTopInventory());
                }
            }
            case 46 -> openInfoMenu(player);
            case 47 -> {
                if (has(player, CHANCES_PERMISSION)) {
                    openChanceMenu(player);
                }
            }
            case 48 -> {
                if (has(player, CHANCES_PERMISSION)) {
                    openXpMenu(player);
                }
            }
            case 49 -> player.closeInventory();
            case 50 -> {
                data.setVisible(!data.visible());
                activePets.setVisible(player, data.visible());
                storage.save();
                renderMenu(player, event.getView().getTopInventory());
            }
            case 51 -> {
                if (holder.page() + 1 < pageCount(data.pets().size())) {
                    holder.setPage(holder.page() + 1);
                    renderMenu(player, event.getView().getTopInventory());
                }
            }
            case 52 -> {
                if (data.activePet().isEmpty()) {
                    player.sendMessage(message("messages.no-active-pet"));
                    return;
                }
                if (activeAlpacaStorageLocked(player, data)) {
                    return;
                }
                activePets.despawn(player, true);
                storage.save();
                player.sendMessage(message("messages.despawned"));
                renderMenu(player, event.getView().getTopInventory());
            }
            case 53 -> convertActivePet(player, data, event.getView().getTopInventory());
            default -> {
            }
        }
    }

    @EventHandler
    public void onInventoryDrag(final InventoryDragEvent event) {
        if (event.getView().getTopInventory().getHolder() instanceof PetMenuHolder
            || event.getView().getTopInventory().getHolder() instanceof ChanceMenuHolder
            || event.getView().getTopInventory().getHolder() instanceof XpMenuHolder
            || event.getView().getTopInventory().getHolder() instanceof InfoMenuHolder
            || event.getView().getTopInventory().getHolder() instanceof PetDetailMenuHolder) {
            event.setCancelled(true);
            return;
        }
        if (event.getView().getTopInventory().getHolder() instanceof AlpacaStorageHolder
            && event.getRawSlots().stream().anyMatch(slot -> slot < event.getView().getTopInventory().getSize())
            && itemFactory.petId(event.getOldCursor()).isPresent()) {
            event.setCancelled(true);
            if (event.getWhoClicked() instanceof Player player) {
                player.sendMessage(message("messages.no-pet-storage"));
            }
        }
    }

    @EventHandler
    public void onInventoryClose(final InventoryCloseEvent event) {
        if (!(event.getInventory().getHolder() instanceof AlpacaStorageHolder holder) || !(event.getPlayer() instanceof Player player)) {
            return;
        }
        saveAlpacaStorage(player, holder, event.getInventory());
        storage.save();
    }

    @EventHandler
    public void onPlayerInteract(final PlayerInteractEvent event) {
        if (event.getHand() != EquipmentSlot.HAND) {
            return;
        }
        if (event.getAction() != Action.RIGHT_CLICK_AIR && event.getAction() != Action.RIGHT_CLICK_BLOCK) {
            return;
        }

        final ItemStack item = event.getItem();
        final Optional<String> petId = itemFactory.petId(item);
        if (petId.isEmpty() || itemFactory.petUuid(item).isPresent()) {
            return;
        }

        event.setCancelled(true);
        addPetFromItem(event.getPlayer(), item, petId.get());
    }

    @EventHandler
    public void onPlayerInteractEntity(final PlayerInteractEntityEvent event) {
        if (event.getHand() != EquipmentSlot.HAND) {
            return;
        }
        final Optional<OwnedPet> clickedPet = activePets.clickedActivePet(event.getPlayer(), event.getRightClicked());
        if (clickedPet.isPresent() && clickedPet.get().definitionId().equals("alpaca")) {
            event.setCancelled(true);
            openAlpacaStorage(event.getPlayer(), clickedPet.get());
            return;
        }
        if (activePets.handlePetInteraction(event.getPlayer(), event.getRightClicked())) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onPlayerToggleSneak(final PlayerToggleSneakEvent event) {
        if (event.isSneaking() && activePets.stopRideIfSneaking(event.getPlayer())) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onPlayerInput(final PlayerInputEvent event) {
        activePets.handleRideInput(event.getPlayer(), event.getInput());
    }

    @EventHandler
    public void onEntityDismount(final EntityDismountEvent event) {
        if (event.getEntity() instanceof Player player && activePets.isRideMount(event.getDismounted())) {
            activePets.stopRide(player, false);
        }
    }

    @EventHandler
    public void onLootGenerate(final LootGenerateEvent event) {
        if (event.isPlugin()) {
            return;
        }
        final LootTable lootTable = event.getLootTable();
        if (lootTable == null) {
            return;
        }

        final NamespacedKey key = lootTable.getKey();
        if (key == null) {
            return;
        }
        if (!key.getKey().startsWith("chests/")) {
            return;
        }

        if (event.getInventoryHolder() instanceof TileState tileState) {
            if (tileState.getPersistentDataContainer().has(generatedChestKey, PersistentDataType.BYTE)) {
                return;
            }
            tileState.getPersistentDataContainer().set(generatedChestKey, PersistentDataType.BYTE, (byte) 1);
            tileState.update();
        }

        final boolean alreadyHasPet = event.getLoot().stream().anyMatch(item -> itemFactory.petId(item).isPresent());
        if (alreadyHasPet) {
            return;
        }

        final double chestChance = Math.max(0.0, Math.min(100.0, getConfig().getDouble("chest-pet-chance-percent", 2.5)));
        final Random random = ThreadLocalRandom.current();
        if (getConfig().getBoolean("debug-loot-rolls", false)) {
            debug("Chest loot roll " + key + " in " + event.getWorld().getName() + " with chance " + formatPercent(chestChance) + "%.");
        }
        if (chestChance <= 0.0 || random.nextDouble(100.0) >= chestChance) {
            return;
        }

        final double totalWeight = totalSpawnChanceWeight();
        if (totalWeight <= 0.0) {
            return;
        }

        final PetDefinition definition = randomPetBySpawnChance(random, totalWeight);
        event.getLoot().add(itemFactory.discoveryItem(definition));
        debug("Injected " + definition.name() + " into chest loot table " + key + ".");
    }

    @EventHandler
    public void onPlayerJoin(final PlayerJoinEvent event) {
        Bukkit.getScheduler().runTaskLater(this, () -> activePets.spawnSavedActivePet(event.getPlayer()), 20L);
    }

    @EventHandler
    public void onPlayerRespawn(final PlayerRespawnEvent event) {
        Bukkit.getScheduler().runTaskLater(this, () -> activePets.spawnSavedActivePet(event.getPlayer()), 20L);
    }

    @EventHandler
    public void onPlayerQuit(final PlayerQuitEvent event) {
        saveOpenAlpacaStorage(event.getPlayer());
        activePets.despawn(event.getPlayer(), false);
        menuCooldowns.remove(event.getPlayer().getUniqueId());
        storage.save();
    }

    @EventHandler
    public void onPlayerExpChange(final PlayerExpChangeEvent event) {
        final Player player = event.getPlayer();
        activePets.activePet(player).ifPresent(pet -> {
            final boolean leveled = pet.addExp(event.getAmount(), petXpMultiplier());
            activePets.refreshDisplay(player);
            storage.save();
            if (leveled) {
                definitions.get(pet.definitionId()).ifPresent(definition ->
                    player.sendMessage(Component.text("Your " + definition.name() + " reached level " + pet.level() + ".", NamedTextColor.GREEN))
                );
                player.playSound(player.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 0.8F, 1.5F);
            }
        });
    }

    @EventHandler
    public void onEntityDamageByEntity(final EntityDamageByEntityEvent event) {
        if (!(event.getEntity() instanceof LivingEntity victim)) {
            return;
        }

        final Player player = damagingPlayer(event.getDamager());
        if (player != null) {
            activePets.applyHitAbility(player, victim);
        }
        if (event.getEntity() instanceof Player damagedPlayer) {
            activePets.applyDefenseAbility(damagedPlayer, event.getDamager());
        }
    }

    @EventHandler
    public void onEntityDeath(final EntityDeathEvent event) {
        final Player killer = event.getEntity().getKiller();
        if (killer != null) {
            activePets.applyKillAbility(killer, event.getEntity());
        }
    }

    @EventHandler
    public void onEntityTarget(final EntityTargetLivingEntityEvent event) {
        activePets.handleWardenTarget(event);
    }

    private void renderMenu(final Player player, final Inventory inventory) {
        inventory.clear();
        final PlayerPetData data = storage.data(player.getUniqueId());
        final List<OwnedPet> pets = data.pets();
        final PetMenuHolder holder = inventory.getHolder() instanceof PetMenuHolder menuHolder ? menuHolder : new PetMenuHolder(player.getUniqueId(), 0);
        final int pages = pageCount(pets.size());
        if (holder.page() >= pages) {
            holder.setPage(Math.max(0, pages - 1));
        }
        final int start = holder.page() * PET_SLOT_LIMIT;
        final int visiblePets = Math.max(0, Math.min(PET_SLOT_LIMIT, pets.size() - start));

        for (int slot = 0; slot < visiblePets; slot++) {
            final int petSlot = slot;
            final OwnedPet pet = pets.get(start + slot);
            definitions.get(pet.definitionId()).ifPresent(definition ->
                inventory.setItem(petSlot, petMenuItem(definition, pet, pet.uuid().equals(data.activePetId())))
            );
        }
        for (int slot = visiblePets; slot < PET_SLOT_LIMIT; slot++) {
            inventory.setItem(slot, itemFactory.control(
                Material.GRAY_STAINED_GLASS_PANE,
                Component.text("Empty Pet Slot", NamedTextColor.DARK_GRAY),
                List.of(Component.text("Find pets in generated structure chests.", NamedTextColor.GRAY))
            ));
        }

        inventory.setItem(45, itemFactory.control(
            holder.page() > 0 ? Material.ARROW : Material.GRAY_STAINED_GLASS_PANE,
            Component.text("Zurueck", holder.page() > 0 ? NamedTextColor.YELLOW : NamedTextColor.DARK_GRAY),
            List.of(Component.text("Seite " + (holder.page() + 1) + " / " + pages, NamedTextColor.GRAY))
        ));
        inventory.setItem(46, itemFactory.control(
            Material.KNOWLEDGE_BOOK,
            Component.text("Pet Catalogue", NamedTextColor.GOLD),
            List.of(
                Component.text("View every pet and its ability scaling.", NamedTextColor.GRAY),
                Component.text("Pets: " + pets.size() + " / " + Math.max(1, getConfig().getInt("max-pets-per-player", PET_SLOT_LIMIT)), NamedTextColor.DARK_GRAY)
            )
        ));
        if (has(player, CHANCES_PERMISSION)) {
            inventory.setItem(47, itemFactory.control(
                Material.COMPARATOR,
                Component.text("Spawn Chances", NamedTextColor.YELLOW),
                List.of(Component.text("Adjust chest spawn weights.", NamedTextColor.GRAY))
            ));
            inventory.setItem(48, itemFactory.control(
                Material.EXPERIENCE_BOTTLE,
                Component.text("XP Multiplier", NamedTextColor.AQUA),
                List.of(
                    Component.text("Current: " + formatDecimal(petXpMultiplier()) + "x", NamedTextColor.GRAY),
                    Component.text("0.1x to 5.0x.", NamedTextColor.DARK_GRAY)
                )
            ));
        }
        inventory.setItem(50, itemFactory.control(
            Material.ENDER_EYE,
            Component.text(data.visible() ? "Pet Visible" : "Pet Hidden", data.visible() ? NamedTextColor.GREEN : NamedTextColor.YELLOW),
            List.of(Component.text("Click to toggle active pet visibility.", NamedTextColor.GRAY))
        ));
        inventory.setItem(51, itemFactory.control(
            holder.page() + 1 < pages ? Material.ARROW : Material.GRAY_STAINED_GLASS_PANE,
            Component.text("Weiter", holder.page() + 1 < pages ? NamedTextColor.YELLOW : NamedTextColor.DARK_GRAY),
            List.of(Component.text("Seite " + (holder.page() + 1) + " / " + pages, NamedTextColor.GRAY))
        ));
        inventory.setItem(52, itemFactory.control(
            Material.PURPLE_DYE,
            Component.text("Despawn Pet", NamedTextColor.DARK_RED),
            List.of(Component.text("Keeps the pet in your list.", NamedTextColor.GRAY))
        ));
        inventory.setItem(53, itemFactory.control(
            Material.GRAY_DYE,
            Component.text("Convert To Item", NamedTextColor.RED),
            List.of(Component.text("Removes the active pet from your list.", NamedTextColor.GRAY))
        ));
        inventory.setItem(49, itemFactory.control(
            Material.BARRIER,
            Component.text("Close", NamedTextColor.RED),
            List.of(Component.text("Close the menu.", NamedTextColor.GRAY))
        ));
    }

    private ItemStack petMenuItem(final PetDefinition definition, final OwnedPet pet, final boolean active) {
        final ItemStack item = itemFactory.menuItem(definition, pet, active);
        final ItemMeta meta = item.getItemMeta();
        final List<Component> lore = meta.lore() == null ? new ArrayList<>() : new ArrayList<>(meta.lore());
        lore.add(Component.empty());
        lore.add(Component.text("Current ability value", NamedTextColor.AQUA));
        lore.add(Component.text(abilityValue(definition.id(), pet.level()), NamedTextColor.GRAY));
        if (isDragonPet(definition.id()) && pet.level() >= 50) {
            lore.add(Component.text("Right-click the active pet to fly.", NamedTextColor.GOLD));
        }
        meta.lore(lore.stream().map(component -> component.decoration(TextDecoration.ITALIC, false)).toList());
        item.setItemMeta(meta);
        return item;
    }

    private void openAlpacaStorage(final Player player, final OwnedPet pet) {
        final PlayerPetData data = storage.data(player.getUniqueId());
        final OwnedPet ownedPet = data.findPet(pet.uuid()).orElse(null);
        if (ownedPet == null || !ownedPet.definitionId().equals("alpaca")) {
            player.sendMessage(message("messages.no-permission"));
            return;
        }
        final int size = alpacaStorageSize(ownedPet.level());
        final AlpacaStorageHolder holder = new AlpacaStorageHolder(player.getUniqueId(), pet.uuid(), size);
        final Inventory inventory = Bukkit.createInventory(holder, size, Component.text("Alpaca Storage", NamedTextColor.GOLD));
        holder.setInventory(inventory);
        inventory.setContents(ownedPet.storageContents(size));
        player.openInventory(inventory);
    }

    private void handleAlpacaStorageClick(final InventoryClickEvent event, final AlpacaStorageHolder holder) {
        if (!(event.getWhoClicked() instanceof Player player) || !player.getUniqueId().equals(holder.owner())) {
            event.setCancelled(true);
            return;
        }
        final int topSize = event.getView().getTopInventory().getSize();
        if (event.getRawSlot() < topSize && itemFactory.petId(event.getCursor()).isPresent()) {
            event.setCancelled(true);
            player.sendMessage(message("messages.no-pet-storage"));
            return;
        }
        if (event.getRawSlot() < topSize && event.getHotbarButton() >= 0
            && itemFactory.petId(player.getInventory().getItem(event.getHotbarButton())).isPresent()) {
            event.setCancelled(true);
            player.sendMessage(message("messages.no-pet-storage"));
            return;
        }
        if (event.isShiftClick() && event.getRawSlot() >= topSize && itemFactory.petId(event.getCurrentItem()).isPresent()) {
            event.setCancelled(true);
            player.sendMessage(message("messages.no-pet-storage"));
        }
    }

    private ItemStack[] sanitizeAlpacaContents(final Player player, final ItemStack[] contents, final int size) {
        final ItemStack[] sanitized = new ItemStack[Math.max(0, Math.min(OwnedPet.STORAGE_SIZE, size))];
        for (int i = 0; i < Math.min(sanitized.length, contents.length); i++) {
            final ItemStack item = contents[i];
            if (item == null || item.getType().isAir()) {
                continue;
            }
            if (itemFactory.petId(item).isPresent()) {
                giveOrDrop(player, item);
                player.sendMessage(message("messages.no-pet-storage"));
                continue;
            }
            sanitized[i] = item.clone();
        }
        return sanitized;
    }

    private void saveOpenAlpacaStorages() {
        for (final Player player : Bukkit.getOnlinePlayers()) {
            saveOpenAlpacaStorage(player);
        }
    }

    private void saveOpenAlpacaStorage(final Player player) {
        if (player.getOpenInventory().getTopInventory().getHolder() instanceof AlpacaStorageHolder holder) {
            saveAlpacaStorage(player, holder, player.getOpenInventory().getTopInventory());
        }
    }

    private void saveAlpacaStorage(final Player player, final AlpacaStorageHolder holder, final Inventory inventory) {
        if (!player.getUniqueId().equals(holder.owner())) {
            debug("Ignored Alpaca storage save from non-owner " + player.getName() + ".");
            return;
        }
        final PlayerPetData data = storage.data(holder.owner());
        data.findPet(holder.pet()).ifPresent(pet -> {
            if (!pet.definitionId().equals("alpaca")) {
                return;
            }
            final ItemStack[] merged = pet.storageContents();
            final ItemStack[] visible = sanitizeAlpacaContents(player, inventory.getContents(), holder.size());
            for (int i = 0; i < visible.length; i++) {
                merged[i] = visible[i];
            }
            pet.setStorageContents(merged);
            debug("Saved Alpaca storage for " + player.getName() + ".");
        });
    }

    private int alpacaStorageSize(final int level) {
        if (level >= 100) {
            return 54;
        }
        if (level >= 70) {
            return 36;
        }
        if (level >= 50) {
            return 27;
        }
        if (level >= 30) {
            return 18;
        }
        return 9;
    }

    private boolean activeAlpacaStorageLocked(final Player player, final PlayerPetData data) {
        final OwnedPet active = data.activePet().orElse(null);
        if (active == null || !active.definitionId().equals("alpaca") || !active.hasStoredItems()) {
            return false;
        }
        player.sendMessage(message("messages.alpaca-storage-not-empty"));
        return true;
    }

    private void addPetFromItem(final Player player, final ItemStack item, final String petId) {
        final PetDefinition definition = definitions.get(petId).orElse(null);
        if (definition == null) {
            return;
        }

        final PlayerPetData data = storage.data(player.getUniqueId());
        if (data.hasDefinition(petId)) {
            player.sendMessage(message("messages.duplicate-pet"));
            return;
        }
        final int limit = Math.max(1, getConfig().getInt("max-pets-per-player", PET_SLOT_LIMIT));
        if (data.pets().size() >= limit) {
            player.sendMessage(message("messages.pet-limit"));
            return;
        }

        final int level = itemFactory.petLevel(item);
        final OwnedPet pet = OwnedPet.create(petId, level);
        pet.recalculateNextLevelExp(petXpMultiplier());
        data.pets().add(pet);
        consumeOne(player, item);
        storage.save();
        player.sendMessage(message("messages.pet-added").replaceText(builder -> builder.matchLiteral("%pet%").replacement(definition.name())));
        player.playSound(player.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 0.8F, 1.8F);
        debug(player.getName() + " added pet " + definition.id() + " level " + pet.level() + " from item.");
    }

    private void convertActivePet(final Player player, final PlayerPetData data, final Inventory inventory) {
        final OwnedPet pet = data.activePet().orElse(null);
        if (pet == null) {
            player.sendMessage(message("messages.no-active-pet"));
            return;
        }

        final PetDefinition definition = definitions.get(pet.definitionId()).orElse(null);
        if (definition == null) {
            return;
        }
        if (pet.definitionId().equals("alpaca") && pet.hasStoredItems()) {
            player.sendMessage(message("messages.alpaca-storage-not-empty"));
            return;
        }

        data.removePet(pet.uuid());
        activePets.despawn(player, false);
        giveOrDrop(player, itemFactory.discoveryItem(definition, pet.level()));
        storage.save();
        player.sendMessage(message("messages.converted").replaceText(builder -> builder.matchLiteral("%pet%").replacement(definition.name())));
        renderMenu(player, inventory);
    }

    private void consumeOne(final Player player, final ItemStack item) {
        if (item.getAmount() <= 1) {
            player.getInventory().setItemInMainHand(null);
        } else {
            item.setAmount(item.getAmount() - 1);
        }
    }

    private void giveOrDrop(final Player player, final ItemStack item) {
        final var leftover = player.getInventory().addItem(item);
        if (!leftover.isEmpty()) {
            leftover.values().forEach(stack -> player.getWorld().dropItemNaturally(player.getLocation(), stack));
            player.sendMessage(message("messages.no-space"));
        }
    }

    private void handleGiveCommand(final CommandSender sender, final String[] args) {
        if (!has(sender, GIVE_PERMISSION)) {
            sender.sendMessage(message("messages.no-permission"));
            return;
        }
        if (args.length < 2) {
            sender.sendMessage(message("messages.usage-give"));
            return;
        }

        int level = 1;
        boolean explicitLevel = false;
        String targetName = null;
        for (int i = 2; i < args.length; i++) {
            final Optional<Integer> parsedLevel = parseLevel(args[i]);
            if (parsedLevel.isPresent() && !explicitLevel) {
                level = parsedLevel.get();
                explicitLevel = true;
                continue;
            }
            if (targetName == null) {
                targetName = args[i];
                continue;
            }
            sender.sendMessage(message("messages.usage-give"));
            return;
        }

        final Player target;
        if (targetName != null) {
            target = Bukkit.getPlayerExact(targetName);
            if (target == null) {
                final String missingTargetName = targetName;
                sender.sendMessage(message("messages.player-not-found").replaceText(builder -> builder.matchLiteral("%player%").replacement(missingTargetName)));
                return;
            }
        } else if (sender instanceof Player player) {
            target = player;
        } else {
            sender.sendMessage(message("messages.usage-give"));
            return;
        }

        final int giftLevel = level;
        final String petId = args[1].toLowerCase(Locale.ROOT);
        if (petId.equals("all")) {
            for (final PetDefinition definition : definitions.ordered()) {
                giveOrDrop(target, itemFactory.discoveryItem(definition, giftLevel));
            }
            sender.sendMessage(message("messages.test-give")
                .replaceText(builder -> builder.matchLiteral("%pet%").replacement("all pets"))
                .replaceText(builder -> builder.matchLiteral("%level%").replacement(Integer.toString(giftLevel)))
                .replaceText(builder -> builder.matchLiteral("%player%").replacement(target.getName())));
            debug(sender.getName() + " gave all test pet items level " + giftLevel + " to " + target.getName() + ".");
            return;
        }

        final PetDefinition definition = definitions.find(petId).orElse(null);
        if (definition == null) {
            sender.sendMessage(message("messages.unknown-pet").replaceText(builder -> builder.matchLiteral("%pet%").replacement(petId)));
            return;
        }

        giveOrDrop(target, itemFactory.discoveryItem(definition, giftLevel));
        sender.sendMessage(message("messages.test-give")
            .replaceText(builder -> builder.matchLiteral("%pet%").replacement(definition.name()))
            .replaceText(builder -> builder.matchLiteral("%level%").replacement(Integer.toString(giftLevel)))
            .replaceText(builder -> builder.matchLiteral("%player%").replacement(target.getName())));
        debug(sender.getName() + " gave test pet item " + definition.id() + " level " + giftLevel + " to " + target.getName() + ".");
    }

    private Optional<Integer> parseLevel(final String value) {
        try {
            return Optional.of(Math.max(1, Math.min(100, Integer.parseInt(value))));
        } catch (final NumberFormatException ignored) {
            return Optional.empty();
        }
    }

    private boolean has(final CommandSender sender, final String permission) {
        return sender.hasPermission(permission) || sender.hasPermission(ADMIN_PERMISSION);
    }

    private boolean isDragonPet(final String id) {
        return id.equals("blue_dragon") || id.equals("red_dragon") || id.equals("ender_dragon");
    }

    private Player damagingPlayer(final Entity damager) {
        if (damager instanceof Player player) {
            return player;
        }
        if (damager instanceof Projectile projectile) {
            final ProjectileSource shooter = projectile.getShooter();
            if (shooter instanceof Player player) {
                return player;
            }
        }
        return null;
    }

    private void openInfoMenu(final Player player) {
        if (!has(player, INFO_PERMISSION)) {
            player.sendMessage(message("messages.no-permission"));
            return;
        }

        final InfoMenuHolder holder = new InfoMenuHolder();
        final Inventory inventory = Bukkit.createInventory(holder, 54, Component.text("Better Pets Catalogue", NamedTextColor.GOLD));
        holder.setInventory(inventory);

        int slot = 0;
        for (final PetDefinition definition : definitions.ordered()) {
            inventory.setItem(slot++, itemFactory.infoItem(definition, catalogueLore(definition)));
        }
        inventory.setItem(49, itemFactory.control(
            Material.BARRIER,
            Component.text("Close", NamedTextColor.RED),
            List.of(Component.text("Close this catalogue.", NamedTextColor.GRAY))
        ));
        player.openInventory(inventory);
    }

    private void handleInfoClick(final InventoryClickEvent event) {
        event.setCancelled(true);
        if (!(event.getWhoClicked() instanceof Player player)) {
            return;
        }
        if (event.getRawSlot() == 49) {
            player.closeInventory();
            return;
        }
        itemFactory.petId(event.getCurrentItem())
            .flatMap(definitions::get)
            .ifPresent(definition -> openPetDetailMenu(player, definition));
    }

    private void openPetDetailMenu(final Player player, final PetDefinition definition) {
        final PetDetailMenuHolder holder = new PetDetailMenuHolder(definition.id());
        final Inventory inventory = Bukkit.createInventory(holder, 54, Component.text(definition.name() + " Details", definition.rarityColor()));
        holder.setInventory(inventory);

        final ItemStack filler = itemFactory.control(Material.BLACK_STAINED_GLASS_PANE, Component.text(" ", NamedTextColor.DARK_GRAY), List.of());
        for (int i = 0; i < inventory.getSize(); i++) {
            inventory.setItem(i, filler);
        }

        inventory.setItem(4, itemFactory.infoItem(definition, List.of(
            Component.text(abilitySummary(definition.id()), NamedTextColor.GRAY),
            Component.text(definition.rarity() + " Pet", definition.rarityColor())
        )));

        final int[] slots = {10, 11, 12, 13, 14, 15, 16, 19, 20, 21, 22};
        final int[] levels = {1, 10, 20, 30, 40, 50, 60, 70, 80, 90, 100};
        for (int i = 0; i < levels.length; i++) {
            final int level = levels[i];
            inventory.setItem(slots[i], itemFactory.control(
                level >= 50 ? Material.LIGHT_BLUE_STAINED_GLASS_PANE : Material.LIME_STAINED_GLASS_PANE,
                Component.text("Level " + level, level >= 50 ? NamedTextColor.AQUA : NamedTextColor.GREEN),
                detailLore(definition, level)
            ));
        }

        inventory.setItem(49, itemFactory.control(
            Material.ARROW,
            Component.text("Back", NamedTextColor.YELLOW),
            List.of(Component.text("Return to the pet catalogue.", NamedTextColor.GRAY))
        ));
        inventory.setItem(53, itemFactory.control(
            Material.BARRIER,
            Component.text("Close", NamedTextColor.RED),
            List.of()
        ));
        player.openInventory(inventory);
    }

    private void handlePetDetailClick(final InventoryClickEvent event, final PetDetailMenuHolder holder) {
        event.setCancelled(true);
        if (!(event.getWhoClicked() instanceof Player player)) {
            return;
        }
        if (event.getRawSlot() == 49) {
            openInfoMenu(player);
        } else if (event.getRawSlot() == 53) {
            player.closeInventory();
        }
    }

    private void openChanceMenu(final Player player) {
        if (!has(player, CHANCES_PERMISSION)) {
            player.sendMessage(message("messages.no-permission"));
            return;
        }

        final ChanceMenuHolder holder = new ChanceMenuHolder(player.getUniqueId());
        final Inventory inventory = Bukkit.createInventory(holder, 54, Component.text("Better Pets Spawn Chances", NamedTextColor.YELLOW));
        holder.setInventory(inventory);
        renderChanceMenu(inventory);
        player.openInventory(inventory);
    }

    private void renderChanceMenu(final Inventory inventory) {
        inventory.clear();
        int slot = 0;
        for (final PetDefinition definition : definitions.ordered()) {
            inventory.setItem(slot++, itemFactory.chanceItem(definition, spawnChance(definition)));
        }
        inventory.setItem(49, itemFactory.control(
            Material.BARRIER,
            Component.text("Close", NamedTextColor.RED),
            List.of(Component.text("Changes save instantly.", NamedTextColor.GRAY))
        ));
    }

    private void handleChanceClick(final InventoryClickEvent event, final ChanceMenuHolder holder) {
        event.setCancelled(true);
        if (!(event.getWhoClicked() instanceof Player player) || !player.getUniqueId().equals(holder.owner())) {
            return;
        }
        if (event.getRawSlot() < 0 || event.getRawSlot() >= event.getView().getTopInventory().getSize()) {
            return;
        }
        if (!has(player, CHANCES_PERMISSION)) {
            player.sendMessage(message("messages.no-permission"));
            return;
        }
        if (event.getRawSlot() == 49) {
            player.closeInventory();
            return;
        }

        final Optional<String> petId = itemFactory.petId(event.getCurrentItem());
        if (petId.isEmpty()) {
            return;
        }
        final PetDefinition definition = definitions.get(petId.get()).orElse(null);
        if (definition == null) {
            return;
        }

        final double step = event.isShiftClick() ? 10.0 : 1.0;
        double value = spawnChance(definition);
        if (event.isLeftClick()) {
            value += step;
        } else if (event.isRightClick()) {
            value -= step;
        } else {
            value = definition.weight();
        }
        value = clampChance(value);
        setSpawnChance(definition, value);
        renderChanceMenu(event.getView().getTopInventory());
        final String formattedChance = formatPercent(value);
        player.sendMessage(message("messages.chances-saved")
            .replaceText(builder -> builder.matchLiteral("%pet%").replacement(definition.name()))
            .replaceText(builder -> builder.matchLiteral("%chance%").replacement(formattedChance)));
    }

    private void openXpMenu(final Player player) {
        if (!has(player, CHANCES_PERMISSION)) {
            player.sendMessage(message("messages.no-permission"));
            return;
        }

        final XpMenuHolder holder = new XpMenuHolder(player.getUniqueId());
        final Inventory inventory = Bukkit.createInventory(holder, 27, Component.text("Better Pets XP Multiplier", NamedTextColor.AQUA));
        holder.setInventory(inventory);
        renderXpMenu(inventory);
        player.openInventory(inventory);
    }

    private void renderXpMenu(final Inventory inventory) {
        inventory.clear();
        final double multiplier = petXpMultiplier();
        inventory.setItem(11, itemFactory.control(
            Material.RED_STAINED_GLASS_PANE,
            Component.text("-0.1x", NamedTextColor.RED),
            List.of(Component.text("Right-click or click this side.", NamedTextColor.GRAY))
        ));
        inventory.setItem(13, itemFactory.control(
            Material.EXPERIENCE_BOTTLE,
            Component.text("XP Multiplier: " + formatDecimal(multiplier) + "x", NamedTextColor.AQUA),
            List.of(
                Component.text("1.0x is normal.", NamedTextColor.GRAY),
                Component.text("2.0x means pets need half the XP.", NamedTextColor.GRAY),
                Component.text("0.5x means pets need double XP.", NamedTextColor.GRAY),
                Component.text("Range: 0.1x - 5.0x.", NamedTextColor.DARK_GRAY)
            )
        ));
        inventory.setItem(15, itemFactory.control(
            Material.LIME_STAINED_GLASS_PANE,
            Component.text("+0.1x", NamedTextColor.GREEN),
            List.of(Component.text("Left-click or click this side.", NamedTextColor.GRAY))
        ));
        inventory.setItem(22, itemFactory.control(
            Material.BARRIER,
            Component.text("Close", NamedTextColor.RED),
            List.of(Component.text("Shift-click changes by 1.0x.", NamedTextColor.GRAY))
        ));
    }

    private void handleXpClick(final InventoryClickEvent event, final XpMenuHolder holder) {
        event.setCancelled(true);
        if (!(event.getWhoClicked() instanceof Player player) || !player.getUniqueId().equals(holder.owner())) {
            return;
        }
        if (!has(player, CHANCES_PERMISSION)) {
            player.sendMessage(message("messages.no-permission"));
            return;
        }
        if (event.getRawSlot() == 22) {
            player.closeInventory();
            return;
        }

        double value = petXpMultiplier();
        final double step = event.isShiftClick() ? 1.0 : 0.1;
        if (event.getRawSlot() == 11 || event.isRightClick()) {
            value -= step;
        } else if (event.getRawSlot() == 15 || event.isLeftClick()) {
            value += step;
        } else {
            value = 1.0;
        }
        setPetXpMultiplier(value);
        renderXpMenu(event.getView().getTopInventory());
        player.sendMessage(Component.text("Pet XP multiplier is now " + formatDecimal(petXpMultiplier()) + "x.", NamedTextColor.AQUA));
    }

    private List<Component> catalogueLore(final PetDefinition definition) {
        final List<Component> lore = new ArrayList<>();
        lore.add(Component.text(abilitySummary(definition.id()), NamedTextColor.GRAY));
        lore.add(Component.empty());
        lore.add(Component.text("Click to view level milestones.", NamedTextColor.YELLOW));
        return lore;
    }

    private List<Component> detailLore(final PetDefinition definition, final int level) {
        final List<Component> lore = new ArrayList<>();
        lore.add(Component.text("Current value", NamedTextColor.AQUA));
        lore.add(Component.text(abilityValue(definition.id(), level), NamedTextColor.GRAY));
        final List<String> unlocks = milestoneUnlocks(definition.id(), level);
        if (!unlocks.isEmpty()) {
            lore.add(Component.empty());
            lore.add(Component.text("Unlocks", NamedTextColor.GOLD));
            unlocks.forEach(unlock -> lore.add(Component.text("+ " + unlock, NamedTextColor.YELLOW)));
        }
        if (level == 100) {
            lore.add(Component.empty());
            lore.add(Component.text("Maximum level", NamedTextColor.GREEN));
        }
        return lore;
    }

    private List<String> milestoneUnlocks(final String id, final int level) {
        final List<String> unlocks = new ArrayList<>();
        if (isDragonPet(id) && level == 50) {
            unlocks.add("Mount flight");
            unlocks.add(id.equals("ender_dragon") ? "Dragon Breath trail" : id.equals("red_dragon") ? "Flame trail" : "Enchant trail");
        }
        if (id.equals("unicorn") && level == 50) {
            unlocks.add("Rainbow trail");
        }
        if (id.equals("alpaca")) {
            if (level == 1) {
                unlocks.add("9 storage slots");
            } else if (level == 30) {
                unlocks.add("18 storage slots");
            } else if (level == 50) {
                unlocks.add("27 storage slots");
            } else if (level == 70) {
                unlocks.add("36 storage slots");
            } else if (level == 100) {
                unlocks.add("54 storage slots");
            }
        }
        if (id.equals("phoenix")) {
            if (level == 50) {
                unlocks.add("Totem cooldown reduced to 18h");
            } else if (level == 100) {
                unlocks.add("Totem cooldown reduced to 12h");
            }
        }
        if (id.equals("capybara") && level == 80) {
            unlocks.add("Rain regeneration improves to level II");
        }
        return unlocks;
    }

    private String abilitySummary(final String id) {
        return switch (id) {
            case "ant" -> "Makes you smaller.";
            case "alpaca" -> "Portable storage that grows with level.";
            case "axolotl" -> "Increases oxygen bonus underwater.";
            case "bat" -> "Reveals nearby hostile mobs while underground.";
            case "beaver" -> "Faster chopping while holding an axe.";
            case "blue_dragon" -> "More End damage, absorption shield, rideable at level 50.";
            case "capybara" -> "Regeneration near water, stronger during rain.";
            case "cat" -> "Reduces fall damage.";
            case "chicken" -> "Grants slow falling pulses.";
            case "dog" -> "Chance to wither undead you hit.";
            case "dolphin" -> "Dolphin's Grace and faster water movement.";
            case "duck" -> "Faster swimming and brief slow falling when airborne.";
            case "elder_guardian" -> "Faster underwater mining.";
            case "ender_dragon" -> "More End damage, absorption shield, rideable at level 50.";
            case "ghast" -> "Explosion knockback resistance and Nether kill XP.";
            case "hamster" -> "Higher step height.";
            case "hedgehog" -> "Reflects a small amount of melee damage.";
            case "herobrine" -> "More health, longer reach, thunder aura.";
            case "koala" -> "Regeneration when resting near trees.";
            case "owl" -> "Night Vision and increased luck.";
            case "panda" -> "More attack knockback, bamboo biome hero effect.";
            case "penguin" -> "Speed in cold biomes and frosted ice trail.";
            case "phoenix" -> "Fire Resistance, burns undead, grants totems.";
            case "platypus" -> "Poisons attackers while you are wet.";
            case "polar_bear" -> "Extra armor in cold biomes.";
            case "pufferfish" -> "Wither aura against undead.";
            case "rabbit" -> "Higher jumps and safer falls.";
            case "reaper" -> "Attack speed, undead aura and harvest buffs.";
            case "red_dragon" -> "More Nether damage, absorption shield, rideable at level 50.";
            case "red_parrot" -> "Reveals nearby hostile mobs.";
            case "red_panda" -> "Forest movement and faster sneaking.";
            case "snail" -> "Faster sneaking.";
            case "spinosaurus" -> "Makes you larger.";
            case "slime" -> "Bouncy movement with safer landings.";
            case "tiger" -> "Movement speed and sweeping damage.";
            case "turtle" -> "Knockback resistance.";
            case "unicorn" -> "Luck, light healing, and rainbow magic.";
            case "warden" -> "Reveals undead and wards off wardens.";
            case "worm" -> "Mining efficiency.";
            default -> "Pet ability.";
        };
    }

    private String abilityValue(final String id, final int level) {
        final int tier = abilityTier(level);
        return switch (id) {
            case "ant" -> Math.round((1.0 - (tier * 0.025)) * 100) + "% scale";
            case "alpaca" -> alpacaStorageSize(level) + " storage slots";
            case "axolotl" -> "+" + formatDecimal(tier * 0.5) + " oxygen";
            case "bat" -> Math.min(24, 8 + tier) + " block underground reveal";
            case "beaver" -> "+" + formatDecimal(tier * 0.35) + " axe mining efficiency";
            case "blue_dragon", "red_dragon" -> "+" + formatDecimal(tier * 0.3) + " damage in dimension";
            case "capybara" -> level >= 80 ? "Regen I near water, Regen II in rain" : "Regen I near water";
            case "cat" -> Math.round((1.0 - (tier * 0.05)) * 100) + "% fall damage";
            case "chicken" -> "Slow Falling pulse every " + Math.max(1, getConfig().getInt("ability-update-ticks", 100) / 20) + "s";
            case "dog" -> Math.min(100, level) + "% wither chance on undead hits";
            case "dolphin" -> "+" + Math.round(tier * 5.0) + "% water movement";
            case "duck" -> "+" + Math.round(tier * 3.5) + "% water movement, short glide";
            case "elder_guardian" -> Math.round((0.2 + tier * 0.04) * 100) + "% underwater mining";
            case "ender_dragon" -> "+" + formatDecimal(tier * 0.35) + " End damage";
            case "ghast" -> Math.round(tier * 5.0) + "% explosion resistance";
            case "hamster" -> formatDecimal(0.6 + tier * 0.045) + " step height";
            case "hedgehog" -> formatDecimal(Math.min(3.0, 0.4 + tier * 0.08)) + " reflected damage";
            case "herobrine" -> "+" + tier + " hearts/reach tier";
            case "koala" -> "Regen I near leaves/logs";
            case "owl" -> "+" + (tier * 25) + " luck";
            case "panda" -> "+" + Math.round(tier * 5.0) + "% knockback";
            case "penguin" -> "+" + formatDecimal(tier * 0.00375) + " cold speed";
            case "phoenix" -> level >= 100 ? "12h totem cooldown" : level >= 50 ? "18h totem cooldown" : "24h totem cooldown";
            case "platypus" -> (60 + tier * 4) + " tick poison on attackers while wet";
            case "polar_bear" -> "+" + formatDecimal(tier * 0.25) + " armor in cold biomes";
            case "pufferfish" -> "7 block wither aura";
            case "rabbit" -> formatDecimal(Math.min(1.01, 0.4 + tier * 0.03158)) + " jump";
            case "reaper" -> formatDecimal(4.0 + tier * 0.2) + " attack speed";
            case "red_parrot" -> Math.min(30, 10 + (level / 10) * 2) + " block reveal";
            case "red_panda" -> "+" + formatDecimal(tier * 0.002) + " forest speed, +" + formatDecimal(tier * 0.02) + " sneak";
            case "snail" -> formatDecimal(0.3 + tier * 0.035) + " sneak speed";
            case "spinosaurus" -> Math.round((1.0 + tier * 0.025) * 100) + "% scale";
            case "slime" -> formatDecimal(5.0 + tier) + " safe fall, " + formatDecimal(Math.min(0.85, 0.4 + tier * 0.018)) + " jump";
            case "tiger" -> formatDecimal(0.1 + Math.min(100, level) * 0.001) + " speed";
            case "turtle" -> "+" + (tier * 5) + " knockback resistance";
            case "unicorn" -> "+" + (tier * 8) + " luck";
            case "warden" -> Math.min(50, Math.max(10, level)) + " block reveal";
            case "worm" -> "+" + formatDecimal(tier * 0.5) + " mining efficiency";
            default -> "Scales with its listed milestones";
        };
    }

    private int abilityTier(final int level) {
        final int capped = Math.max(1, Math.min(100, level));
        if (capped <= 7) {
            return 1;
        }
        if (capped >= 98) {
            return 20;
        }
        return Math.min(20, 2 + ((capped - 8) / 5));
    }

    private int pageCount(final int itemCount) {
        return Math.max(1, (int) Math.ceil(itemCount / (double) PET_SLOT_LIMIT));
    }

    private void ensureSpawnChanceDefaults() {
        boolean changed = false;
        for (final PetDefinition definition : definitions.all()) {
            final String path = "spawn-chances." + definition.id();
            if (!getConfig().isSet(path)) {
                getConfig().set(path, (double) definition.weight());
                changed = true;
            }
        }
        if (changed) {
            saveConfig();
            getLogger().info("Created default spawn chance settings for pets.");
        }
    }

    private double spawnChance(final PetDefinition definition) {
        return clampChance(getConfig().getDouble("spawn-chances." + definition.id(), definition.weight()));
    }

    private void setSpawnChance(final PetDefinition definition, final double chance) {
        getConfig().set("spawn-chances." + definition.id(), clampChance(chance));
        saveConfig();
    }

    private double totalSpawnChanceWeight() {
        return definitions.all().stream().mapToDouble(this::spawnChance).sum();
    }

    private double petXpMultiplier() {
        return clampXpMultiplier(getConfig().getDouble("pet-xp-multiplier", 1.0));
    }

    private void setPetXpMultiplier(final double value) {
        final double multiplier = clampXpMultiplier(value);
        getConfig().set("pet-xp-multiplier", multiplier);
        recalculateAllPetExp();
        saveConfig();
        storage.save();
    }

    private void recalculateAllPetExp() {
        final double multiplier = petXpMultiplier();
        for (final Map.Entry<UUID, PlayerPetData> entry : storage.entries()) {
            entry.getValue().pets().forEach(pet -> pet.recalculateNextLevelExp(multiplier));
        }
    }

    private PetDefinition randomPetBySpawnChance(final Random random, final double totalWeight) {
        double roll = random.nextDouble(totalWeight);
        for (final PetDefinition definition : definitions.ordered()) {
            roll -= spawnChance(definition);
            if (roll <= 0.0) {
                return definition;
            }
        }
        return definitions.ordered().getFirst();
    }

    private double clampChance(final double value) {
        return Math.max(0.001, Math.min(100.0, value));
    }

    private double clampXpMultiplier(final double value) {
        return Math.max(0.1, Math.min(5.0, Math.round(value * 10.0) / 10.0));
    }

    private String formatPercent(final double value) {
        if (value >= 10.0) {
            return String.format(Locale.ROOT, "%.0f", value);
        }
        if (value >= 1.0) {
            return String.format(Locale.ROOT, "%.2f", value);
        }
        return String.format(Locale.ROOT, "%.3f", value);
    }

    private String formatDecimal(final double value) {
        return String.format(Locale.ROOT, "%.3f", value);
    }

    private Component message(final String path) {
        return Component.text(messageText(path), NamedTextColor.GRAY);
    }

    private String messageText(final String path) {
        final String value = getConfig().getString(path);
        if (value != null && !value.isBlank()) {
            return value;
        }
        return DEFAULT_MESSAGES.getOrDefault(path, path);
    }

    void debug(final String message) {
        if (getConfig().getBoolean("debug-logging", true)) {
            getLogger().info("[Debug] " + message);
        }
    }

    private static final class PetMenuHolder implements InventoryHolder {
        private final UUID owner;
        private int page;
        private Inventory inventory;

        private PetMenuHolder(final UUID owner, final int page) {
            this.owner = owner;
            this.page = Math.max(0, page);
        }

        private UUID owner() {
            return owner;
        }

        private int page() {
            return page;
        }

        private void setPage(final int page) {
            this.page = Math.max(0, page);
        }

        private void setInventory(final Inventory inventory) {
            this.inventory = inventory;
        }

        @Override
        public Inventory getInventory() {
            return inventory;
        }
    }

    private static final class InfoMenuHolder implements InventoryHolder {
        private Inventory inventory;

        private void setInventory(final Inventory inventory) {
            this.inventory = inventory;
        }

        @Override
        public Inventory getInventory() {
            return inventory;
        }
    }

    private static final class PetDetailMenuHolder implements InventoryHolder {
        private final String petId;
        private Inventory inventory;

        private PetDetailMenuHolder(final String petId) {
            this.petId = petId;
        }

        private String petId() {
            return petId;
        }

        private void setInventory(final Inventory inventory) {
            this.inventory = inventory;
        }

        @Override
        public Inventory getInventory() {
            return inventory;
        }
    }

    private static final class ChanceMenuHolder implements InventoryHolder {
        private final UUID owner;
        private Inventory inventory;

        private ChanceMenuHolder(final UUID owner) {
            this.owner = owner;
        }

        private UUID owner() {
            return owner;
        }

        private void setInventory(final Inventory inventory) {
            this.inventory = inventory;
        }

        @Override
        public Inventory getInventory() {
            return inventory;
        }
    }

    private static final class XpMenuHolder implements InventoryHolder {
        private final UUID owner;
        private Inventory inventory;

        private XpMenuHolder(final UUID owner) {
            this.owner = owner;
        }

        private UUID owner() {
            return owner;
        }

        private void setInventory(final Inventory inventory) {
            this.inventory = inventory;
        }

        @Override
        public Inventory getInventory() {
            return inventory;
        }
    }

    private static final class AlpacaStorageHolder implements InventoryHolder {
        private final UUID owner;
        private final UUID pet;
        private final int size;
        private Inventory inventory;

        private AlpacaStorageHolder(final UUID owner, final UUID pet, final int size) {
            this.owner = owner;
            this.pet = pet;
            this.size = Math.max(9, Math.min(OwnedPet.STORAGE_SIZE, size));
        }

        private UUID owner() {
            return owner;
        }

        private UUID pet() {
            return pet;
        }

        private int size() {
            return size;
        }

        private void setInventory(final Inventory inventory) {
            this.inventory = inventory;
        }

        @Override
        public Inventory getInventory() {
            return inventory;
        }
    }

    private static final class PetsCommand implements BasicCommand {
        private final BetterPetsPlugin plugin;

        private PetsCommand(final BetterPetsPlugin plugin) {
            this.plugin = plugin;
        }

        @Override
        public void execute(final CommandSourceStack stack, final String[] args) {
            final CommandSender sender = stack.getSender();
            if (args.length > 0 && args[0].equalsIgnoreCase("give")) {
                plugin.handleGiveCommand(sender, args);
                return;
            }
            if (args.length > 0 && args[0].equalsIgnoreCase("chances")) {
                if (sender instanceof Player player) {
                    plugin.openChanceMenu(player);
                } else {
                    sender.sendMessage(plugin.message("messages.only-players"));
                }
                return;
            }

            final Entity executor = stack.getExecutor();
            final Player player = executor instanceof Player executingPlayer
                ? executingPlayer
                : sender instanceof Player sendingPlayer ? sendingPlayer : null;

            if (player == null) {
                sender.sendMessage(plugin.message("messages.only-players"));
                return;
            }

            if (args.length > 0 && args[0].equalsIgnoreCase("info")) {
                plugin.openInfoMenu(player);
                return;
            }

            plugin.openPetsMenu(player);
        }

        @Override
        public Collection<String> suggest(final CommandSourceStack stack, final String[] args) {
            if (args.length == 1) {
                final List<String> suggestions = new ArrayList<>();
                if (plugin.has(stack.getSender(), GIVE_PERMISSION)) {
                    suggestions.add("give");
                }
                if (plugin.has(stack.getSender(), INFO_PERMISSION)) {
                    suggestions.add("info");
                }
                if (plugin.has(stack.getSender(), CHANCES_PERMISSION)) {
                    suggestions.add("chances");
                }
                return suggestions;
            }
            if (args.length == 2 && args[0].equalsIgnoreCase("give")) {
                final List<String> suggestions = new java.util.ArrayList<>();
                suggestions.add("all");
                suggestions.addAll(plugin.definitions.ordered().stream().map(PetDefinition::id).toList());
                return suggestions;
            }
            if (args.length == 3 && args[0].equalsIgnoreCase("give")) {
                final List<String> suggestions = new ArrayList<>(List.of("1", "10", "50", "100"));
                suggestions.addAll(Bukkit.getOnlinePlayers().stream().map(Player::getName).toList());
                return suggestions;
            }
            if (args.length == 4 && args[0].equalsIgnoreCase("give")) {
                return Bukkit.getOnlinePlayers().stream().map(Player::getName).toList();
            }
            return List.of();
        }

        @Override
        public boolean canUse(final CommandSender sender) {
            return plugin.has(sender, USE_PERMISSION)
                || plugin.has(sender, GIVE_PERMISSION)
                || plugin.has(sender, CHANCES_PERMISSION)
                || plugin.has(sender, INFO_PERMISSION);
        }

        @Override
        public String permission() {
            return null;
        }
    }
}
