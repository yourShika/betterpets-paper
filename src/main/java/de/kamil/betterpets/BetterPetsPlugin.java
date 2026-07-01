package de.kamil.betterpets;

import de.kamil.betterpets.model.PetModelService;
import de.kamil.betterpets.modules.BetterModelModule;
import de.kamil.betterpets.modules.Module;
import de.kamil.betterpets.modules.ModuleManager;
import com.destroystokyo.paper.event.player.PlayerPickupExperienceEvent;
import io.papermc.paper.command.brigadier.BasicCommand;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.event.player.PlayerTradeEvent;
import io.papermc.paper.plugin.lifecycle.event.types.LifecycleEvents;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Sound;
import org.bukkit.block.Block;
import org.bukkit.block.BlockState;
import org.bukkit.block.Chest;
import org.bukkit.block.Container;
import org.bukkit.block.DoubleChest;
import org.bukkit.block.TileState;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Item;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.entity.WanderingTrader;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockDispenseLootEvent;
import org.bukkit.event.entity.CreatureSpawnEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.entity.EntityPickupItemEvent;
import org.bukkit.event.entity.EntityDismountEvent;
import org.bukkit.event.entity.EntityTargetLivingEntityEvent;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.event.player.PlayerExpChangeEvent;
import org.bukkit.event.player.PlayerFishEvent;
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
import org.bukkit.inventory.MerchantRecipe;
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
        Map.entry("messages.no-pet-storage", "Pet items cannot be stored inside pet storage."),
        Map.entry("messages.modules-experimental", "External modules are experimental and disabled. Set experimental-modules: true in config.yml to use /pets modules."),
        Map.entry("messages.usage-set-name", "Usage: /pets set name <name> (your pet must be summoned).")
    );

    private PetDefinitions definitions;
    private PetStorage storage;
    private PetItemFactory itemFactory;
    private ActivePetManager activePets;
    private PetModelService modelService;
    private ModuleManager moduleManager;
    private NamespacedKey generatedChestKey;
    private NamespacedKey announceOnPickupKey;
    private BukkitTask saveTask;
    private final Map<UUID, Long> menuCooldowns = new HashMap<>();
    private final Map<UUID, Long> lastOrbPickupTick = new HashMap<>();
    private final Map<UUID, Long> brushCooldowns = new HashMap<>();
    private static final String[] DROP_SOURCES = {"chest", "fishing", "wandering-trader", "brushing", "vault", "trial-spawner", "xp-booster"};
    private static final int[] BOOSTER_DURATIONS = {15, 30, 45, 60};
    private static final int[] DROP_SLOTS = {10, 11, 12, 13, 14, 15, 16};
    private final Map<String, UUID> pendingLootOpeners = new HashMap<>();

    @Override
    public void onEnable() {
        getLogger().info("Starting Better Pets as a pure Paper plugin.");
        saveDefaultConfig();
        repairConfig();
        getConfig().options().copyDefaults(true);
        saveConfig();
        announceStorageMode();

        definitions = PetDefinitions.load(this);
        getLogger().info("Loaded " + definitions.all().size() + " pet definitions.");
        ensureSpawnChanceDefaults();
        if (getServer().getPluginManager().isPluginEnabled("LuckPerms")) {
            getLogger().info("LuckPerms detected. Permission nodes: betterpets.command.pets, betterpets.give, betterpets.chances, betterpets.info, betterpets.admin.");
        }

        itemFactory = new PetItemFactory(this);
        generatedChestKey = new NamespacedKey(this, "pet_loot_generated");
        announceOnPickupKey = new NamespacedKey(this, "announce_on_pickup");
        storage = new PetStorage(this);
        storage.load();
        recalculateAllPetExp();
        getLogger().info("Loaded pet storage for " + storage.playerCount() + " player(s).");

        modelService = new PetModelService(this);
        moduleManager = new ModuleManager(this);
        moduleManager.register(new BetterModelModule(this, modelService));
        moduleManager.load();
        if (experimentalModulesEnabled()) {
            moduleManager.enablePersistedAvailable();
        } else {
            getLogger().info("External modules are experimental and disabled (experimental-modules: false). Skipping module activation.");
        }

        activePets = new ActivePetManager(this, definitions, storage, itemFactory, modelService);
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
        // Count down active Pet XP boosters once per second (only for online players).
        Bukkit.getScheduler().runTaskTimer(this, this::tickBoosters, 20L, 20L);
        getLogger().info("Better Pets enabled. Auto-save interval: " + saveInterval + " ticks.");
    }

    private void tickBoosters() {
        final long now = System.currentTimeMillis();
        for (final Player player : Bukkit.getOnlinePlayers()) {
            final PlayerPetData data = storage.data(player.getUniqueId());
            if (!data.hasActiveBooster()) {
                data.setBoosterTickReference(now);
                continue;
            }
            final long reference = data.boosterTickReference();
            final long delta = reference <= 0L ? 0L : Math.max(0L, now - reference);
            data.setBoosterTickReference(now);
            final long remaining = data.boosterRemainingMillis() - delta;
            if (remaining <= 0L) {
                data.clearBooster();
                player.sendMessage(Component.text("Your Pet XP Booster has expired.", NamedTextColor.GRAY));
            } else {
                data.setBooster(data.boosterTier(), remaining);
            }
        }
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
        if (moduleManager != null) {
            moduleManager.shutdown();
        }
        if (storage != null) {
            saveOpenAlpacaStorages();
            storage.save();
            getLogger().info("Pet storage saved.");
        }
    }

    /** Adds any config options that are missing from an older config.yml, keeping the user's values. */
    private void repairConfig() {
        final java.io.InputStream defaultStream = getResource("config.yml");
        if (defaultStream == null) {
            return;
        }
        final org.bukkit.configuration.file.YamlConfiguration defaults =
            org.bukkit.configuration.file.YamlConfiguration.loadConfiguration(
                new java.io.InputStreamReader(defaultStream, java.nio.charset.StandardCharsets.UTF_8));
        int added = 0;
        for (final String key : defaults.getKeys(true)) {
            if (defaults.isConfigurationSection(key) || getConfig().contains(key)) {
                continue;
            }
            getConfig().set(key, defaults.get(key));
            added++;
        }
        if (added > 0) {
            saveConfig();
            getLogger().info("Repaired " + added + " missing config option(s) from defaults.");
        }
    }

    private void announceStorageMode() {
        final String type = getConfig().getString("storage.type", "yaml");
        if (type != null && type.equalsIgnoreCase("sqlite")) {
            // The SQLite backend is a planned opt-in; until it ships, data stays in the safe YAML store.
            getLogger().warning("storage.type is 'sqlite', but the SQLite backend is not available yet in this build. "
                + "Using the YAML store (with backups) instead. Your data is safe.");
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
            } else if (event.getView().getTopInventory().getHolder() instanceof NotifyMenuHolder notifyHolder) {
                handleNotifyClick(event, notifyHolder);
            } else if (event.getView().getTopInventory().getHolder() instanceof XpMenuHolder xpHolder) {
                handleXpClick(event, xpHolder);
            } else if (event.getView().getTopInventory().getHolder() instanceof ModulesMenuHolder modulesHolder) {
                handleModulesClick(event, modulesHolder);
            } else if (event.getView().getTopInventory().getHolder() instanceof DropMenuHolder dropHolder) {
                handleDropClick(event, dropHolder);
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
            || event.getView().getTopInventory().getHolder() instanceof NotifyMenuHolder
            || event.getView().getTopInventory().getHolder() instanceof XpMenuHolder
            || event.getView().getTopInventory().getHolder() instanceof ModulesMenuHolder
            || event.getView().getTopInventory().getHolder() instanceof DropMenuHolder
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
        rememberLootOpener(event);

        final ItemStack item = event.getItem();
        if (itemFactory.boosterTier(item) > 0) {
            event.setCancelled(true);
            consumeBooster(event.getPlayer(), item);
            return;
        }

        final Optional<String> petId = itemFactory.petId(item);
        if (petId.isEmpty() || itemFactory.petUuid(item).isPresent()) {
            return;
        }

        event.setCancelled(true);
        addPetFromItem(event.getPlayer(), item, petId.get());
    }

    private void consumeBooster(final Player player, final ItemStack item) {
        final PlayerPetData data = storage.data(player.getUniqueId());
        if (data.hasActiveBooster()) {
            player.sendMessage(Component.text("You already have a Pet XP Booster active (x" + data.boosterTier()
                + ", " + formatDuration(data.boosterRemainingMillis()) + " left). Boosters do not stack.", NamedTextColor.RED));
            return;
        }
        final int tier = itemFactory.boosterTier(item);
        final int minutes = Math.max(1, Math.min(60, itemFactory.boosterMinutes(item)));
        data.setBooster(tier, minutes * 60_000L);
        data.setBoosterTickReference(System.currentTimeMillis());
        consumeOne(player, item);
        storage.save();
        player.sendMessage(Component.text("Pet XP Booster x" + tier + " activated for " + minutes
            + " minutes! It speeds up pet leveling only (not your own XP). The timer only runs while you are online.", NamedTextColor.LIGHT_PURPLE));
        player.playSound(player.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 0.7F, 1.3F);
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
        if (event.isPlugin() || !petSourceEnabled("chest")) {
            return;
        }
        final LootTable lootTable = event.getLootTable();
        if (lootTable == null) {
            return;
        }

        // Only inject into block containers: single chests, double chests, barrels, dispensers, ...
        // This covers both vanilla structures and custom structures added by data packs, no matter
        // which loot table namespace or path they use, as long as the loot fills a placed container.
        final InventoryHolder lootHolder = event.getInventoryHolder();
        final Container container;
        if (lootHolder instanceof Container directContainer) {
            container = directContainer;
        } else if (lootHolder instanceof DoubleChest doubleChest && doubleChest.getLeftSide() instanceof Container leftContainer) {
            container = leftContainer;
        } else {
            return;
        }

        final NamespacedKey key = lootTable.getKey();
        final Player opener = resolveLootOpener(event, lootHolder, container);

        // Dedupe across BOTH halves of a double chest: each half can generate its loot table
        // separately, so without this a double chest could hand out two pets.
        final List<TileState> dedupeStates = dedupeStatesFor(container);
        if (dedupeStates.stream().anyMatch(state -> state.getPersistentDataContainer().has(generatedChestKey, PersistentDataType.BYTE))) {
            return;
        }
        for (final TileState state : dedupeStates) {
            state.getPersistentDataContainer().set(generatedChestKey, PersistentDataType.BYTE, (byte) 1);
            state.update();
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
        if (opener != null) {
            broadcastPetDiscovery(opener, definition);
        }
        debug("Injected " + definition.name() + " into chest loot table " + key + ".");
    }

    @EventHandler
    public void onPlayerJoin(final PlayerJoinEvent event) {
        activePets.prepareJoiningPlayer(event.getPlayer());
        // Reset the booster tick reference so time spent offline is never counted against the booster.
        storage.data(event.getPlayer().getUniqueId()).setBoosterTickReference(System.currentTimeMillis());
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
        lastOrbPickupTick.remove(event.getPlayer().getUniqueId());
        storage.save();
    }

    @EventHandler
    public void onPlayerPickupExperience(final PlayerPickupExperienceEvent event) {
        final Player player = event.getPlayer();
        // Count the orb's full value, before Mending diverts any of it to tool repair.
        lastOrbPickupTick.put(player.getUniqueId(), (long) Bukkit.getCurrentTick());
        grantPetExp(player, event.getExperienceOrb().getExperience());
    }

    @EventHandler
    public void onPlayerExpChange(final PlayerExpChangeEvent event) {
        final Player player = event.getPlayer();
        // Orb pickups are handled (at full value) by onPlayerPickupExperience. Skip them here so they
        // are not counted twice; this branch only catches non-orb XP (commands, plugins, etc.).
        final Long pickupTick = lastOrbPickupTick.get(player.getUniqueId());
        if (pickupTick != null && pickupTick == (long) Bukkit.getCurrentTick()) {
            return;
        }
        grantPetExp(player, event.getAmount());
    }

    private void grantPetExp(final Player player, final int amount) {
        if (amount <= 0) {
            return;
        }
        activePets.activePet(player).ifPresent(pet -> {
            // XP is persisted by the periodic autosave and on quit/disable, so this hot path no longer
            // writes the whole storage file on every single XP gain.
            // An active Pet XP Booster multiplies only the pet's gained XP (never the player's own XP).
            final PlayerPetData boosterData = storage.data(player.getUniqueId());
            final int effectiveAmount = boosterData.hasActiveBooster() ? amount * boosterData.boosterTier() : amount;
            final boolean leveled = pet.addExp(effectiveAmount, petXpMultiplier());
            if (leveled) {
                activePets.refreshDisplay(player);
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
            maybeDropBooster(killer, event.getEntity());
        }
    }

    private void maybeDropBooster(final Player killer, final LivingEntity dead) {
        if (!petSourceEnabled("xp-booster") || !activePets.isHostile(dead)) {
            return;
        }
        final double chance = petSourceChance("xp-booster");
        if (chance <= 0.0 || ThreadLocalRandom.current().nextDouble(100.0) >= chance) {
            return;
        }
        final int tier = randomBoosterTier();
        final int minutes = BOOSTER_DURATIONS[ThreadLocalRandom.current().nextInt(BOOSTER_DURATIONS.length)];
        dead.getWorld().dropItemNaturally(dead.getLocation(), itemFactory.boosterItem(tier, minutes));
        if (getConfig().getBoolean("debug-logging", true)) {
            debug(killer.getName() + " earned a Pet XP Booster x" + tier + " (" + minutes + "m) from a " + dead.getType() + ".");
        }
    }

    private int randomBoosterTier() {
        // Higher tiers are rarer.
        final double roll = ThreadLocalRandom.current().nextDouble();
        if (roll < 0.50) {
            return 2;
        }
        if (roll < 0.80) {
            return 3;
        }
        if (roll < 0.95) {
            return 4;
        }
        return 5;
    }

    @EventHandler
    public void onEntityTarget(final EntityTargetLivingEntityEvent event) {
        activePets.handlePetTargeting(event);
    }

    @EventHandler(ignoreCancelled = true)
    public void onEntityDamage(final EntityDamageEvent event) {
        if (!(event.getEntity() instanceof Player player)) {
            return;
        }
        if (event.getCause() == EntityDamageEvent.DamageCause.VOID) {
            return;
        }
        if (player.getHealth() - event.getFinalDamage() > 0.0) {
            return;
        }
        if (activePets.tryPhoenixRevive(player)) {
            event.setCancelled(true);
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onBlockBreak(final BlockBreakEvent event) {
        activePets.handleMoleBreak(event.getPlayer(), event.getBlock());
    }

    @EventHandler(ignoreCancelled = true)
    public void onPetEquipAttempt(final InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) {
            return;
        }
        final InventoryHolder holder = event.getView().getTopInventory().getHolder();
        if (holder instanceof PetMenuHolder || holder instanceof ChanceMenuHolder || holder instanceof NotifyMenuHolder
            || holder instanceof XpMenuHolder || holder instanceof ModulesMenuHolder || holder instanceof InfoMenuHolder || holder instanceof PetDetailMenuHolder
            || holder instanceof AlpacaStorageHolder) {
            return;
        }
        if (event.getSlotType() == InventoryType.SlotType.ARMOR) {
            if (itemFactory.petId(event.getCursor()).isPresent()) {
                event.setCancelled(true);
                return;
            }
            if (event.getClick() == ClickType.NUMBER_KEY && event.getHotbarButton() >= 0
                && itemFactory.petId(player.getInventory().getItem(event.getHotbarButton())).isPresent()) {
                event.setCancelled(true);
                return;
            }
            if (event.getClick() == ClickType.SWAP_OFFHAND && itemFactory.petId(player.getInventory().getItemInOffHand()).isPresent()) {
                event.setCancelled(true);
                return;
            }
        }
        if (event.isShiftClick()
            && event.getView().getTopInventory().getType() == InventoryType.CRAFTING
            && itemFactory.petId(event.getCurrentItem()).isPresent()) {
            final ItemStack helmet = player.getInventory().getHelmet();
            if (helmet == null || helmet.getType().isAir()) {
                event.setCancelled(true);
            }
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onPetArmorDrag(final InventoryDragEvent event) {
        if (itemFactory.petId(event.getOldCursor()).isEmpty()) {
            return;
        }
        for (final int rawSlot : event.getRawSlots()) {
            if (event.getView().getSlotType(rawSlot) == InventoryType.SlotType.ARMOR) {
                event.setCancelled(true);
                return;
            }
        }
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
            Component.text("Back", holder.page() > 0 ? NamedTextColor.YELLOW : NamedTextColor.DARK_GRAY),
            List.of(Component.text("Page " + (holder.page() + 1) + " / " + pages, NamedTextColor.GRAY))
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
            // Admins keep the XP Multiplier here, with the booster status shown right below it.
            final List<Component> xpLore = new ArrayList<>();
            xpLore.add(Component.text("Current: " + formatDecimal(petXpMultiplier()) + "x", NamedTextColor.GRAY));
            xpLore.add(Component.text("0.1x to 5.0x.", NamedTextColor.DARK_GRAY));
            xpLore.add(Component.empty());
            xpLore.addAll(boosterStatusLines(data));
            inventory.setItem(48, itemFactory.control(
                Material.EXPERIENCE_BOTTLE,
                Component.text("XP Multiplier", NamedTextColor.AQUA),
                xpLore
            ));
        } else {
            // Everyone else gets a dedicated Pet XP Booster status item in the same spot.
            inventory.setItem(48, itemFactory.control(
                Material.EXPERIENCE_BOTTLE,
                Component.text("Pet XP Booster", NamedTextColor.LIGHT_PURPLE),
                boosterStatusLines(data)
            ));
        }
        inventory.setItem(50, itemFactory.control(
            Material.ENDER_EYE,
            Component.text(data.visible() ? "Pet Visible" : "Pet Hidden", data.visible() ? NamedTextColor.GREEN : NamedTextColor.YELLOW),
            List.of(Component.text("Click to toggle active pet visibility.", NamedTextColor.GRAY))
        ));
        inventory.setItem(51, itemFactory.control(
            holder.page() + 1 < pages ? Material.ARROW : Material.GRAY_STAINED_GLASS_PANE,
            Component.text("Next", holder.page() + 1 < pages ? NamedTextColor.YELLOW : NamedTextColor.DARK_GRAY),
            List.of(Component.text("Page " + (holder.page() + 1) + " / " + pages, NamedTextColor.GRAY))
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

    private List<Component> boosterStatusLines(final PlayerPetData data) {
        if (data.hasActiveBooster()) {
            return List.of(
                Component.text("Active booster: x" + data.boosterTier(), NamedTextColor.LIGHT_PURPLE),
                Component.text("Time left: " + formatDuration(data.boosterRemainingMillis()), NamedTextColor.AQUA),
                Component.text("Only speeds up pet leveling; counts down while online.", NamedTextColor.DARK_GRAY)
            );
        }
        return List.of(Component.text("No active Pet XP Booster.", NamedTextColor.GRAY));
    }

    private ItemStack petMenuItem(final PetDefinition definition, final OwnedPet pet, final boolean active) {
        final ItemStack item = itemFactory.menuItem(definition, pet, active);
        final ItemMeta meta = item.getItemMeta();
        final List<Component> lore = meta.lore() == null ? new ArrayList<>() : new ArrayList<>(meta.lore());
        lore.add(Component.empty());
        lore.add(Component.text("Current ability value", NamedTextColor.AQUA));
        lore.add(Component.text(abilityValue(definition.id(), pet.level()), NamedTextColor.GRAY));
        if (definition.id().equals("phoenix")) {
            final long remaining = ActivePetManager.phoenixCooldownMillis(pet.level()) - (System.currentTimeMillis() - pet.lastTotemMillis());
            lore.add(Component.empty());
            if (remaining <= 0) {
                lore.add(Component.text("Revive: ready", NamedTextColor.GREEN));
            } else {
                lore.add(Component.text("Revive cooldown: " + formatDuration(remaining), NamedTextColor.GRAY));
            }
        }
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

    private void rememberLootOpener(final PlayerInteractEvent event) {
        if (event.getAction() != Action.RIGHT_CLICK_BLOCK) {
            return;
        }
        final Block block = event.getClickedBlock();
        if (block == null || !(block.getState() instanceof TileState)) {
            return;
        }
        if (pendingLootOpeners.size() > 1024) {
            pendingLootOpeners.clear();
        }
        pendingLootOpeners.put(blockKey(block.getState()), event.getPlayer().getUniqueId());
    }

    private Player resolveLootOpener(final LootGenerateEvent event, final InventoryHolder lootHolder, final Container container) {
        // 1) The entity the server attributes the loot to (often the player for container loot).
        if (event.getEntity() instanceof Player direct) {
            return direct;
        }
        // 2) The player we recorded right-clicking this container (or either half of a double chest).
        final Player tracked = resolveOpener(lootHolder, container);
        if (tracked != null) {
            return tracked;
        }
        // 3) Fallback for structure chests (incl. data pack ones) where neither of the above is set:
        //    the nearest player, since opening a container requires being close to it.
        Location location = null;
        if (event.getLootContext() != null && event.getLootContext().getLocation() != null) {
            location = event.getLootContext().getLocation();
        } else if (container.getLocation().getWorld() != null) {
            location = container.getLocation().add(0.5, 0.5, 0.5);
        }
        return location == null ? null : nearestPlayer(location, 10.0);
    }

    private Player nearestPlayer(final Location location, final double radius) {
        if (location.getWorld() == null) {
            return null;
        }
        Player nearest = null;
        double bestDistanceSquared = radius * radius;
        for (final Player player : location.getWorld().getPlayers()) {
            final double distanceSquared = player.getLocation().distanceSquared(location);
            if (distanceSquared <= bestDistanceSquared) {
                bestDistanceSquared = distanceSquared;
                nearest = player;
            }
        }
        return nearest;
    }

    private Player resolveOpener(final InventoryHolder lootHolder, final Container container) {
        // A double chest is two blocks; the player may have clicked either half, so check both,
        // otherwise discovery broadcasts get lost when the loot is keyed to the other half.
        if (lootHolder instanceof DoubleChest doubleChest) {
            Player opener = null;
            if (doubleChest.getLeftSide() instanceof BlockState left) {
                opener = openerFor(left);
            }
            if (opener == null && doubleChest.getRightSide() instanceof BlockState right) {
                opener = openerFor(right);
            }
            return opener;
        }
        return openerFor(container);
    }

    private Player openerFor(final BlockState blockState) {
        final UUID playerId = pendingLootOpeners.remove(blockKey(blockState));
        return playerId == null ? null : Bukkit.getPlayer(playerId);
    }

    private List<TileState> dedupeStatesFor(final Container container) {
        if (container instanceof Chest chest && chest.getInventory().getHolder() instanceof DoubleChest doubleChest) {
            final List<TileState> states = new ArrayList<>();
            if (doubleChest.getLeftSide() instanceof TileState left) {
                states.add(left);
            }
            if (doubleChest.getRightSide() instanceof TileState right) {
                states.add(right);
            }
            if (!states.isEmpty()) {
                return states;
            }
        }
        return List.of(container);
    }

    private String blockKey(final BlockState blockState) {
        return blockState.getWorld().getUID() + ":" + blockState.getX() + ":" + blockState.getY() + ":" + blockState.getZ();
    }

    private boolean discoveryBroadcastEnabled(final String rarity) {
        return getConfig().getBoolean("discovery-broadcasts." + rarity.toLowerCase(Locale.ROOT), true);
    }

    private void setDiscoveryBroadcastEnabled(final String rarity, final boolean enabled) {
        getConfig().set("discovery-broadcasts." + rarity.toLowerCase(Locale.ROOT), enabled);
        saveConfig();
    }

    private void broadcastPetDiscovery(final Player player, final PetDefinition definition) {
        announcePet(player, definition, "found", "");
    }

    private void announcePet(final Player player, final PetDefinition definition, final String action, final String suffix) {
        if (!discoveryBroadcastEnabled(definition.rarity())) {
            return;
        }
        Bukkit.broadcast(Component.text("[BetterPets] ", NamedTextColor.GOLD)
            .append(Component.text(player.getName(), NamedTextColor.AQUA))
            .append(Component.text(" " + action + " a ", NamedTextColor.GRAY))
            .append(Component.text(definition.rarity() + " Pet", definition.rarityColor()))
            .append(Component.text(": ", NamedTextColor.GRAY))
            .append(Component.text(definition.name(), definition.rarityColor()))
            .append(Component.text(suffix.isEmpty() ? "!" : " " + suffix + "!", NamedTextColor.GRAY)));

        playDiscoverySound(definition.rarity());
    }

    private boolean petSourceEnabled(final String source) {
        return getConfig().getBoolean("pet-sources." + source + ".enabled", true);
    }

    private void setPetSourceEnabled(final String source, final boolean enabled) {
        getConfig().set("pet-sources." + source + ".enabled", enabled);
        saveConfig();
    }

    private double petSourceChance(final String source) {
        if (source.equals("chest")) {
            return Math.max(0.0, Math.min(100.0, getConfig().getDouble("chest-pet-chance-percent", 2.5)));
        }
        final double fallback = switch (source) {
            case "fishing" -> 1.0;
            case "wandering-trader" -> 25.0;
            case "brushing" -> 1.5;
            case "vault" -> 2.0;
            case "trial-spawner" -> 1.5;
            case "xp-booster" -> 1.5;
            default -> 1.0;
        };
        return Math.max(0.0, Math.min(100.0, getConfig().getDouble("pet-sources." + source + ".chance-percent", fallback)));
    }

    private boolean sourceHasOminousBonus(final String source) {
        return source.equals("vault") || source.equals("trial-spawner");
    }

    private double petSourceOminousBonus(final String source) {
        final double fallback = switch (source) {
            case "vault" -> 3.0;
            case "trial-spawner" -> 2.5;
            default -> 0.0;
        };
        return Math.max(0.0, Math.min(100.0, getConfig().getDouble("pet-sources." + source + ".ominous-bonus-percent", fallback)));
    }

    private void setPetSourceChance(final String source, final double chance) {
        final double clamped = Math.max(0.0, Math.min(100.0, chance));
        if (source.equals("chest")) {
            getConfig().set("chest-pet-chance-percent", clamped);
        } else {
            getConfig().set("pet-sources." + source + ".chance-percent", clamped);
        }
        saveConfig();
    }

    private PetDefinition rollSourcePet() {
        final double totalWeight = totalSpawnChanceWeight();
        if (totalWeight <= 0.0) {
            return null;
        }
        return randomPetBySpawnChance(ThreadLocalRandom.current(), totalWeight);
    }

    @EventHandler
    public void onPlayerFish(final PlayerFishEvent event) {
        if (event.getState() != PlayerFishEvent.State.CAUGHT_FISH || !petSourceEnabled("fishing")) {
            return;
        }
        if (!(event.getCaught() instanceof Item caught)) {
            return;
        }
        final double chance = petSourceChance("fishing");
        if (chance <= 0.0 || ThreadLocalRandom.current().nextDouble(100.0) >= chance) {
            return;
        }
        final PetDefinition definition = rollSourcePet();
        if (definition == null) {
            return;
        }
        caught.setItemStack(itemFactory.discoveryItem(definition));
        announcePet(event.getPlayer(), definition, "fished out", "");
    }

    @EventHandler
    public void onBrushSuspicious(final PlayerInteractEvent event) {
        if (event.getHand() != EquipmentSlot.HAND || event.getAction() != Action.RIGHT_CLICK_BLOCK) {
            return;
        }
        final Block block = event.getClickedBlock();
        if (block == null) {
            return;
        }
        final Material blockType = block.getType();
        if (blockType != Material.SUSPICIOUS_SAND && blockType != Material.SUSPICIOUS_GRAVEL) {
            return;
        }
        if (event.getItem() == null || event.getItem().getType() != Material.BRUSH || !petSourceEnabled("brushing")) {
            return;
        }
        final Player player = event.getPlayer();
        final long now = System.currentTimeMillis();
        if (now < brushCooldowns.getOrDefault(player.getUniqueId(), 0L)) {
            return;
        }
        brushCooldowns.put(player.getUniqueId(), now + 1500L);
        final double chance = petSourceChance("brushing");
        if (chance <= 0.0 || ThreadLocalRandom.current().nextDouble(100.0) >= chance) {
            return;
        }
        final PetDefinition definition = rollSourcePet();
        if (definition == null) {
            return;
        }
        // Replace the item buried in the block so the pet is brushed out of the block naturally,
        // instead of suddenly spawning above it.
        if (block.getState() instanceof org.bukkit.block.BrushableBlock brushable) {
            brushable.setLootTable(null);
            brushable.setItem(itemFactory.discoveryItem(definition));
            brushable.update(true);
        } else {
            block.getWorld().dropItemNaturally(block.getLocation().add(0.5, 1.0, 0.5), itemFactory.discoveryItem(definition));
        }
        announcePet(player, definition, "brushed out", "");
    }

    @EventHandler
    public void onTraderSpawn(final CreatureSpawnEvent event) {
        if (!(event.getEntity() instanceof WanderingTrader trader) || !petSourceEnabled("wandering-trader")) {
            return;
        }
        final double chance = petSourceChance("wandering-trader");
        if (chance <= 0.0 || ThreadLocalRandom.current().nextDouble(100.0) >= chance) {
            return;
        }
        final PetDefinition definition = rollSourcePet();
        if (definition == null) {
            return;
        }
        final MerchantRecipe recipe = new MerchantRecipe(itemFactory.discoveryItem(definition), 1);
        recipe.setIngredients(traderPrice(definition.rarity()));
        // Append after vanilla sets its own recipes, so our one-time deal is not overwritten.
        Bukkit.getScheduler().runTaskLater(this, () -> {
            if (!trader.isValid()) {
                return;
            }
            final List<MerchantRecipe> recipes = new ArrayList<>(trader.getRecipes());
            recipes.add(recipe);
            trader.setRecipes(recipes);
            debug("Wandering Trader offered a one-time " + definition.name() + " deal.");
        }, 1L);
    }

    @EventHandler
    public void onPlayerTrade(final PlayerTradeEvent event) {
        final ItemStack result = event.getTrade().getResult();
        itemFactory.petId(result)
            .flatMap(definitions::get)
            .ifPresent(definition -> announcePet(event.getPlayer(), definition, "bought", "from a Wandering Trader"));
    }

    @EventHandler
    public void onBlockDispenseLoot(final BlockDispenseLootEvent event) {
        final Material type = event.getBlock().getType();
        final String source;
        if (type == Material.VAULT) {
            source = "vault";
        } else if (type == Material.TRIAL_SPAWNER) {
            source = "trial-spawner";
        } else {
            return;
        }
        if (!petSourceEnabled(source)) {
            return;
        }
        final boolean ominous = isOminousBlock(event.getBlock());
        // Minecraft only exposes "ominous: yes/no" on the block, so every Ominous Bottle / Bad Omen
        // level (1-5) counts as ominous and adds the configurable ominous bonus to the base chance.
        double chance = petSourceChance(source);
        if (ominous) {
            chance += petSourceOminousBonus(source);
        }
        chance = Math.max(0.0, Math.min(100.0, chance));
        if (getConfig().getBoolean("debug-loot-rolls", false)) {
            debug(source + " loot dispense (ominous=" + ominous + ") with chance " + formatPercent(chance) + "%.");
        }
        if (chance <= 0.0 || ThreadLocalRandom.current().nextDouble(100.0) >= chance) {
            return;
        }
        final PetDefinition definition = rollSourcePet();
        if (definition == null) {
            return;
        }
        final String suffix = source.equals("vault") ? "in a Vault" : "in a Trial Spawner";
        final ItemStack petItem = itemFactory.discoveryItem(definition);
        final Player player = event.getPlayer();
        if (player != null) {
            announcePet(player, definition, "found", suffix);
        } else {
            // Trial spawners dispense their reward without a triggering player, so there is nobody to
            // credit yet. Mark the pet item so the broadcast fires when a player picks it up.
            markAnnounceOnPickup(petItem, suffix);
        }
        final List<ItemStack> loot = new ArrayList<>(event.getDispensedLoot());
        loot.add(petItem);
        event.setDispensedLoot(loot);
        debug("Injected " + definition.name() + " into " + source + " loot.");
    }

    private void markAnnounceOnPickup(final ItemStack item, final String suffix) {
        final ItemMeta meta = item.getItemMeta();
        if (meta == null) {
            return;
        }
        meta.getPersistentDataContainer().set(announceOnPickupKey, PersistentDataType.STRING, suffix);
        item.setItemMeta(meta);
    }

    @EventHandler
    public void onPetItemPickup(final EntityPickupItemEvent event) {
        if (!(event.getEntity() instanceof Player player)) {
            return;
        }
        final ItemStack stack = event.getItem().getItemStack();
        if (stack == null || !stack.hasItemMeta()) {
            return;
        }
        final ItemMeta meta = stack.getItemMeta();
        final String suffix = meta.getPersistentDataContainer().get(announceOnPickupKey, PersistentDataType.STRING);
        if (suffix == null) {
            return;
        }
        // Strip the marker so the broadcast fires exactly once and the pet item stays clean.
        meta.getPersistentDataContainer().remove(announceOnPickupKey);
        stack.setItemMeta(meta);
        event.getItem().setItemStack(stack);
        itemFactory.petId(stack)
            .flatMap(definitions::get)
            .ifPresent(definition -> announcePet(player, definition, "found", suffix));
    }

    private boolean isOminousBlock(final Block block) {
        final org.bukkit.block.data.BlockData data = block.getBlockData();
        if (data instanceof org.bukkit.block.data.type.Vault vault) {
            return vault.isOminous();
        }
        if (data instanceof org.bukkit.block.data.type.TrialSpawner trialSpawner) {
            return trialSpawner.isOminous();
        }
        return false;
    }

    private List<ItemStack> traderPrice(final String rarity) {
        return switch (rarity.toLowerCase(Locale.ROOT)) {
            case "rare" -> List.of(new ItemStack(Material.EMERALD, 24), new ItemStack(Material.GOLD_INGOT, 6));
            case "epic" -> List.of(new ItemStack(Material.EMERALD, 32), new ItemStack(Material.DIAMOND, 6));
            case "legendary" -> List.of(new ItemStack(Material.EMERALD, 48), new ItemStack(Material.DIAMOND, 16));
            case "extraordinary" -> List.of(new ItemStack(Material.EMERALD, 64), new ItemStack(Material.NETHERITE_INGOT, 1));
            default -> List.of(new ItemStack(Material.EMERALD, 16), new ItemStack(Material.IRON_INGOT, 8));
        };
    }

    void openDropMenu(final Player player) {
        if (!has(player, CHANCES_PERMISSION)) {
            player.sendMessage(message("messages.no-permission"));
            return;
        }
        final DropMenuHolder holder = new DropMenuHolder(player.getUniqueId());
        final Inventory inventory = Bukkit.createInventory(holder, 27, Component.text("Better Pets Sources", NamedTextColor.GOLD));
        holder.setInventory(inventory);
        renderDropMenu(inventory);
        player.openInventory(inventory);
    }

    private void renderDropMenu(final Inventory inventory) {
        inventory.clear();
        for (int i = 0; i < DROP_SOURCES.length; i++) {
            final String source = DROP_SOURCES[i];
            final boolean enabled = petSourceEnabled(source);
            final List<Component> lore = new ArrayList<>();
            lore.add(Component.text(enabled ? "Enabled" : "Disabled", enabled ? NamedTextColor.GREEN : NamedTextColor.RED));
            lore.add(Component.text("Chance: " + formatPercent(petSourceChance(source)) + "%", NamedTextColor.GRAY));
            if (sourceHasOminousBonus(source)) {
                lore.add(Component.text("Ominous bonus: +" + formatPercent(petSourceOminousBonus(source)) + "%", NamedTextColor.DARK_PURPLE));
            }
            lore.add(Component.empty());
            lore.add(Component.text("Left-click: toggle on/off", NamedTextColor.YELLOW));
            lore.add(Component.text("Right-click: +0.5%", NamedTextColor.GREEN));
            lore.add(Component.text("Shift + right-click: -0.5%", NamedTextColor.RED));
            inventory.setItem(DROP_SLOTS[i], itemFactory.control(
                dropMaterial(source),
                Component.text(dropName(source), enabled ? NamedTextColor.GREEN : NamedTextColor.RED),
                lore
            ));
        }
        inventory.setItem(22, itemFactory.control(
            Material.BARRIER,
            Component.text("Close", NamedTextColor.RED),
            List.of(Component.text("Changes save instantly.", NamedTextColor.GRAY))
        ));
    }

    private void handleDropClick(final InventoryClickEvent event, final DropMenuHolder holder) {
        event.setCancelled(true);
        if (!(event.getWhoClicked() instanceof Player player) || !player.getUniqueId().equals(holder.owner())) {
            return;
        }
        if (!has(player, CHANCES_PERMISSION)) {
            player.sendMessage(message("messages.no-permission"));
            return;
        }
        final int rawSlot = event.getRawSlot();
        if (rawSlot == 22) {
            player.closeInventory();
            return;
        }
        int index = -1;
        for (int i = 0; i < DROP_SLOTS.length; i++) {
            if (DROP_SLOTS[i] == rawSlot) {
                index = i;
                break;
            }
        }
        if (index < 0) {
            return;
        }
        final String source = DROP_SOURCES[index];
        if (event.isRightClick()) {
            setPetSourceChance(source, petSourceChance(source) + (event.isShiftClick() ? -0.5 : 0.5));
        } else {
            setPetSourceEnabled(source, !petSourceEnabled(source));
        }
        renderDropMenu(event.getView().getTopInventory());
    }

    private String dropName(final String source) {
        return switch (source) {
            case "chest" -> "Chests";
            case "fishing" -> "Fishing";
            case "wandering-trader" -> "Wandering Trader";
            case "brushing" -> "Brushing (Suspicious Sand/Gravel)";
            case "vault" -> "Vaults";
            case "trial-spawner" -> "Trial Spawners";
            case "xp-booster" -> "Pet XP Boosters (mob drops)";
            default -> source;
        };
    }

    private Material dropMaterial(final String source) {
        return switch (source) {
            case "chest" -> Material.CHEST;
            case "fishing" -> Material.FISHING_ROD;
            case "wandering-trader" -> Material.EMERALD;
            case "brushing" -> Material.BRUSH;
            case "vault" -> Material.VAULT;
            case "trial-spawner" -> Material.TRIAL_SPAWNER;
            case "xp-booster" -> Material.EXPERIENCE_BOTTLE;
            default -> Material.PAPER;
        };
    }

    private void playDiscoverySound(final String rarity) {
        final Sound sound;
        final float pitch;
        switch (rarity.toLowerCase(Locale.ROOT)) {
            case "rare" -> {
                sound = Sound.BLOCK_NOTE_BLOCK_BELL;
                pitch = 1.2F;
            }
            case "epic" -> {
                sound = Sound.ENTITY_PLAYER_LEVELUP;
                pitch = 1.0F;
            }
            case "legendary" -> {
                sound = Sound.UI_TOAST_CHALLENGE_COMPLETE;
                pitch = 1.0F;
            }
            case "extraordinary" -> {
                sound = Sound.ENTITY_ENDER_DRAGON_GROWL;
                pitch = 1.0F;
            }
            default -> {
                sound = Sound.BLOCK_NOTE_BLOCK_PLING;
                pitch = 1.4F;
            }
        }
        for (final Player online : Bukkit.getOnlinePlayers()) {
            online.playSound(online.getLocation(), sound, 1.0F, pitch);
        }
    }

    private List<String> rarityOrder() {
        return List.of("Common", "Rare", "Epic", "Legendary", "Extraordinary");
    }

    private NamedTextColor rarityColor(final String rarity) {
        return switch (rarity.toLowerCase(Locale.ROOT)) {
            case "rare" -> NamedTextColor.BLUE;
            case "epic" -> NamedTextColor.DARK_PURPLE;
            case "legendary" -> NamedTextColor.GOLD;
            case "extraordinary" -> NamedTextColor.DARK_RED;
            default -> NamedTextColor.GREEN;
        };
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
        inventory.setItem(45, itemFactory.control(
            Material.ARROW,
            Component.text("Back", NamedTextColor.YELLOW),
            List.of(Component.text("Return to the pet menu.", NamedTextColor.GRAY))
        ));
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
        if (event.getRawSlot() == 45) {
            openPetsMenu(player);
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
        inventory.setItem(45, itemFactory.control(
            Material.ARROW,
            Component.text("Back", NamedTextColor.YELLOW),
            List.of(Component.text("Return to the pet menu.", NamedTextColor.GRAY))
        ));
        inventory.setItem(49, itemFactory.control(
            Material.BARRIER,
            Component.text("Close", NamedTextColor.RED),
            List.of(Component.text("Changes save instantly.", NamedTextColor.GRAY))
        ));
        inventory.setItem(48, itemFactory.control(
            Material.BELL,
            Component.text("Find Broadcasts", NamedTextColor.GOLD),
            List.of(Component.text("Toggle public pet-find messages.", NamedTextColor.GRAY))
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
        if (event.getRawSlot() == 45) {
            openPetsMenu(player);
            return;
        }
        if (event.getRawSlot() == 49) {
            player.closeInventory();
            return;
        }
        if (event.getRawSlot() == 48) {
            openNotifyMenu(player);
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

    private void openNotifyMenu(final Player player) {
        if (!has(player, CHANCES_PERMISSION)) {
            player.sendMessage(message("messages.no-permission"));
            return;
        }
        final NotifyMenuHolder holder = new NotifyMenuHolder(player.getUniqueId());
        final Inventory inventory = Bukkit.createInventory(holder, 27, Component.text("Better Pets Broadcasts", NamedTextColor.GOLD));
        holder.setInventory(inventory);
        renderNotifyMenu(inventory);
        player.openInventory(inventory);
    }

    private void renderNotifyMenu(final Inventory inventory) {
        inventory.clear();
        final int[] slots = {10, 11, 12, 13, 14};
        final List<String> rarities = rarityOrder();
        for (int i = 0; i < rarities.size(); i++) {
            final String rarity = rarities.get(i);
            final boolean enabled = discoveryBroadcastEnabled(rarity);
            inventory.setItem(slots[i], itemFactory.control(
                enabled ? Material.LIME_DYE : Material.GRAY_DYE,
                Component.text(rarity + ": " + (enabled ? "ON" : "OFF"), rarityColor(rarity)),
                List.of(Component.text("Click to toggle all-chat find messages.", NamedTextColor.GRAY))
            ));
        }
        inventory.setItem(22, itemFactory.control(
            Material.ARROW,
            Component.text("Back", NamedTextColor.YELLOW),
            List.of(Component.text("Return to spawn chances.", NamedTextColor.GRAY))
        ));
    }

    private void handleNotifyClick(final InventoryClickEvent event, final NotifyMenuHolder holder) {
        event.setCancelled(true);
        if (!(event.getWhoClicked() instanceof Player player) || !player.getUniqueId().equals(holder.owner())) {
            return;
        }
        if (!has(player, CHANCES_PERMISSION)) {
            player.sendMessage(message("messages.no-permission"));
            return;
        }
        if (event.getRawSlot() == 22) {
            openChanceMenu(player);
            return;
        }
        final int[] slots = {10, 11, 12, 13, 14};
        final List<String> rarities = rarityOrder();
        for (int i = 0; i < slots.length; i++) {
            if (event.getRawSlot() == slots[i]) {
                final String rarity = rarities.get(i);
                setDiscoveryBroadcastEnabled(rarity, !discoveryBroadcastEnabled(rarity));
                renderNotifyMenu(event.getView().getTopInventory());
                return;
            }
        }
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
        inventory.setItem(18, itemFactory.control(
            Material.ARROW,
            Component.text("Back", NamedTextColor.YELLOW),
            List.of(Component.text("Return to the pet menu.", NamedTextColor.GRAY))
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
        if (event.getRawSlot() == 18) {
            openPetsMenu(player);
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

    boolean experimentalModulesEnabled() {
        return getConfig().getBoolean("experimental-modules", false);
    }

    private void openModulesMenu(final Player player) {
        if (!has(player, ADMIN_PERMISSION)) {
            player.sendMessage(message("messages.no-permission"));
            return;
        }
        if (!experimentalModulesEnabled()) {
            player.sendMessage(message("messages.modules-experimental"));
            return;
        }
        final ModulesMenuHolder holder = new ModulesMenuHolder(player.getUniqueId());
        final Inventory inventory = Bukkit.createInventory(holder, 27, Component.text("Better Pets Modules", NamedTextColor.GOLD));
        holder.setInventory(inventory);
        renderModulesMenu(inventory);
        player.openInventory(inventory);
    }

    private void renderModulesMenu(final Inventory inventory) {
        inventory.clear();
        int slot = 10;
        for (final Module module : moduleManager.modules()) {
            inventory.setItem(slot++, moduleItem(module));
        }
        inventory.setItem(22, itemFactory.control(
            Material.BARRIER,
            Component.text("Close", NamedTextColor.RED),
            List.of(Component.text("Close module settings.", NamedTextColor.GRAY))
        ));
    }

    private ItemStack moduleItem(final Module module) {
        final boolean available = module.isAvailable();
        final boolean active = moduleManager.isActive(module.id());
        final Material material = !available ? Material.BARRIER : active ? Material.LIME_DYE : Material.RED_DYE;
        final NamedTextColor color = !available ? NamedTextColor.DARK_GRAY : active ? NamedTextColor.GREEN : NamedTextColor.RED;
        final List<Component> lore = new ArrayList<>();
        lore.add(Component.text(module.description(), NamedTextColor.GRAY));
        lore.add(Component.text("Icon: " + module.iconMaterial().name().toLowerCase(Locale.ROOT), NamedTextColor.DARK_GRAY));
        lore.add(Component.empty());
        if (!available) {
            lore.add(Component.text("Benoetigt " + module.requiredPluginName() + " - nicht installiert oder deaktiviert.", NamedTextColor.DARK_GRAY));
        } else if (active) {
            lore.add(Component.text("Klick zum Deaktivieren.", NamedTextColor.GREEN));
        } else {
            lore.add(Component.text("Klick zum Aktivieren.", NamedTextColor.RED));
        }
        if (moduleManager.isRequestedEnabled(module.id()) && !active) {
            lore.add(Component.text("In modules.yml aktiv, wartet auf verfuegbares Plugin.", NamedTextColor.YELLOW));
        }
        return itemFactory.control(material, Component.text(module.displayName(), color), lore);
    }

    private void handleModulesClick(final InventoryClickEvent event, final ModulesMenuHolder holder) {
        event.setCancelled(true);
        if (!(event.getWhoClicked() instanceof Player player) || !player.getUniqueId().equals(holder.owner())) {
            return;
        }
        if (!has(player, ADMIN_PERMISSION)) {
            player.sendMessage(message("messages.no-permission"));
            return;
        }
        if (event.getRawSlot() == 22) {
            player.closeInventory();
            return;
        }
        final int index = event.getRawSlot() - 10;
        final List<Module> modules = new ArrayList<>(moduleManager.modules());
        if (index < 0 || index >= modules.size()) {
            return;
        }
        final Module module = modules.get(index);
        if (!module.isAvailable()) {
            return;
        }
        moduleManager.toggle(module.id());
        if (activePets != null) {
            activePets.respawnAllActivePets();
        }
        renderModulesMenu(event.getView().getTopInventory());
    }

    private void reloadBetterPets(final CommandSender sender) {
        if (!has(sender, ADMIN_PERMISSION)) {
            sender.sendMessage(message("messages.no-permission"));
            return;
        }
        saveOpenAlpacaStorages();
        reloadConfig();
        getConfig().options().copyDefaults(true);
        saveConfig();
        ensureSpawnChanceDefaults();
        if (moduleManager != null) {
            if (experimentalModulesEnabled()) {
                moduleManager.reload();
            } else {
                moduleManager.shutdown();
            }
        }
        if (experimentalModulesEnabled() && modelService != null && modelService.isEnabled()) {
            modelService.reloadModels();
        }
        if (activePets != null) {
            activePets.respawnAllActivePets();
        }
        if (storage != null) {
            recalculateAllPetExp();
            storage.save();
        }
        sender.sendMessage(Component.text("Better Pets reloaded config, modules, models, and active pets.", NamedTextColor.GREEN));
        getLogger().info("Better Pets reload completed.");
    }

    void handleVersionCommand(final CommandSender sender) {
        final String version = getPluginMeta().getVersion();
        sender.sendMessage(Component.text("Better Pets ", NamedTextColor.GOLD)
            .append(Component.text("v" + version, NamedTextColor.AQUA)));
        sender.sendMessage(Component.text("Modules:", NamedTextColor.GOLD));
        if (moduleManager != null) {
            for (final Module module : moduleManager.modules()) {
                final boolean active = moduleManager.isActive(module.id());
                sender.sendMessage(Component.text("  - " + module.displayName() + ": ", NamedTextColor.GRAY)
                    .append(Component.text(active ? "ON" : "OFF", active ? NamedTextColor.GREEN : NamedTextColor.RED)));
            }
        }
        if (!experimentalModulesEnabled()) {
            sender.sendMessage(Component.text("  (external modules are experimental and disabled)", NamedTextColor.DARK_GRAY));
        }
        checkLatestVersionAsync(sender, version);
    }

    private void checkLatestVersionAsync(final CommandSender sender, final String currentVersion) {
        Bukkit.getScheduler().runTaskAsynchronously(this, () -> {
            final String latest = fetchLatestReleaseTag();
            Bukkit.getScheduler().runTask(this, () -> {
                if (latest == null) {
                    sender.sendMessage(Component.text("Could not check for updates (GitHub unreachable).", NamedTextColor.DARK_GRAY));
                    return;
                }
                final String latestClean = latest.startsWith("v") ? latest.substring(1) : latest;
                final int comparison = compareVersions(currentVersion, latestClean);
                if (comparison < 0) {
                    sender.sendMessage(Component.text("A newer version is available: v" + latestClean + " (you have v" + currentVersion + ").", NamedTextColor.YELLOW));
                    sender.sendMessage(Component.text("https://github.com/yourShika/betterpets-paper/releases/latest", NamedTextColor.AQUA));
                } else if (comparison > 0) {
                    sender.sendMessage(Component.text("You are running a newer build (v" + currentVersion + ") than the latest release (v" + latestClean + ").", NamedTextColor.GRAY));
                } else {
                    sender.sendMessage(Component.text("You are on the latest version.", NamedTextColor.GREEN));
                }
            });
        });
    }

    private String fetchLatestReleaseTag() {
        final ReleaseInfo info = fetchLatestRelease();
        return info == null ? null : info.tag();
    }

    private ReleaseInfo fetchLatestRelease() {
        try {
            final java.net.http.HttpClient client = java.net.http.HttpClient.newBuilder()
                .connectTimeout(java.time.Duration.ofSeconds(5))
                .build();
            final java.net.http.HttpRequest request = java.net.http.HttpRequest.newBuilder()
                .uri(java.net.URI.create("https://api.github.com/repos/yourShika/betterpets-paper/releases/latest"))
                .header("Accept", "application/vnd.github+json")
                .header("User-Agent", "BetterPets-UpdateCheck")
                .timeout(java.time.Duration.ofSeconds(5))
                .GET()
                .build();
            final java.net.http.HttpResponse<String> response = client.send(request, java.net.http.HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() != 200) {
                return null;
            }
            final java.util.regex.Matcher tagMatcher = java.util.regex.Pattern.compile("\"tag_name\"\\s*:\\s*\"([^\"]+)\"").matcher(response.body());
            final String tag = tagMatcher.find() ? tagMatcher.group(1) : null;
            // Only accept a jar asset URL served by GitHub itself, never an arbitrary URL.
            final java.util.regex.Matcher urlMatcher = java.util.regex.Pattern.compile("\"browser_download_url\"\\s*:\\s*\"(https://[^\"]+\\.jar)\"").matcher(response.body());
            final String jarUrl = urlMatcher.find() ? urlMatcher.group(1) : null;
            return new ReleaseInfo(tag, jarUrl);
        } catch (final Exception exception) {
            return null;
        }
    }

    private record ReleaseInfo(String tag, String jarUrl) {
    }

    void handleUpdateCommand(final CommandSender sender) {
        if (!has(sender, ADMIN_PERMISSION)) {
            sender.sendMessage(message("messages.no-permission"));
            return;
        }
        final String currentVersion = getPluginMeta().getVersion();
        sender.sendMessage(Component.text("Checking for a Better Pets update...", NamedTextColor.GOLD));
        Bukkit.getScheduler().runTaskAsynchronously(this, () -> {
            final ReleaseInfo info = fetchLatestRelease();
            if (info == null || info.tag() == null) {
                runOnMain(() -> sender.sendMessage(Component.text("Could not reach GitHub to check for updates.", NamedTextColor.RED)));
                return;
            }
            final String latest = info.tag().startsWith("v") ? info.tag().substring(1) : info.tag();
            if (compareVersions(currentVersion, latest) >= 0) {
                runOnMain(() -> sender.sendMessage(Component.text("You are already on the latest version (v" + currentVersion + ").", NamedTextColor.GREEN)));
                return;
            }
            if (info.jarUrl() == null) {
                runOnMain(() -> sender.sendMessage(Component.text("Release v" + latest + " has no downloadable jar asset.", NamedTextColor.RED)));
                return;
            }
            final boolean downloaded = downloadUpdate(info.jarUrl());
            runOnMain(() -> {
                if (downloaded) {
                    sender.sendMessage(Component.text("Downloaded Better Pets v" + latest + " (you have v" + currentVersion + ").", NamedTextColor.GREEN));
                    sender.sendMessage(Component.text("Restart the server to apply the update.", NamedTextColor.YELLOW));
                } else {
                    sender.sendMessage(Component.text("Update download failed - see the console for details.", NamedTextColor.RED));
                }
            });
        });
    }

    private void runOnMain(final Runnable runnable) {
        Bukkit.getScheduler().runTask(this, runnable);
    }

    private boolean downloadUpdate(final String jarUrl) {
        try {
            final java.io.File updateFolder = Bukkit.getUpdateFolderFile();
            if (!updateFolder.exists() && !updateFolder.mkdirs()) {
                getLogger().severe("Could not create the plugin update folder: " + updateFolder.getAbsolutePath());
                return false;
            }
            // Place the new jar in the server's update folder under the current plugin's file name.
            // Bukkit applies it automatically on the next start, which avoids the locked-jar problem.
            final java.nio.file.Path target = new java.io.File(updateFolder, getFile().getName()).toPath();
            final java.net.http.HttpClient client = java.net.http.HttpClient.newBuilder()
                .followRedirects(java.net.http.HttpClient.Redirect.NORMAL)
                .connectTimeout(java.time.Duration.ofSeconds(10))
                .build();
            final java.net.http.HttpRequest request = java.net.http.HttpRequest.newBuilder()
                .uri(java.net.URI.create(jarUrl))
                .header("User-Agent", "BetterPets-UpdateCheck")
                .header("Accept", "application/octet-stream")
                .timeout(java.time.Duration.ofSeconds(120))
                .GET()
                .build();
            final java.net.http.HttpResponse<java.nio.file.Path> response =
                client.send(request, java.net.http.HttpResponse.BodyHandlers.ofFile(target));
            if (response.statusCode() != 200) {
                getLogger().severe("Update download returned HTTP " + response.statusCode() + ".");
                java.nio.file.Files.deleteIfExists(target);
                return false;
            }
            getLogger().info("Downloaded Better Pets update to " + target + "; it will be applied on the next server start.");
            return true;
        } catch (final Exception exception) {
            getLogger().severe("Update download failed: " + exception.getMessage());
            return false;
        }
    }

    private int compareVersions(final String left, final String right) {
        final String[] leftParts = left.split("\\.");
        final String[] rightParts = right.split("\\.");
        final int length = Math.max(leftParts.length, rightParts.length);
        for (int i = 0; i < length; i++) {
            final int leftValue = i < leftParts.length ? parseVersionPart(leftParts[i]) : 0;
            final int rightValue = i < rightParts.length ? parseVersionPart(rightParts[i]) : 0;
            if (leftValue != rightValue) {
                return Integer.compare(leftValue, rightValue);
            }
        }
        return 0;
    }

    private int parseVersionPart(final String value) {
        final String digits = value.replaceAll("[^0-9]", "");
        if (digits.isEmpty()) {
            return 0;
        }
        try {
            return Integer.parseInt(digits);
        } catch (final NumberFormatException exception) {
            return 0;
        }
    }

    void handleSetName(final Player player, final String name) {
        final OwnedPet pet = storage.data(player.getUniqueId()).activePet().orElse(null);
        if (pet == null) {
            player.sendMessage(message("messages.no-active-pet"));
            return;
        }
        final String clean = name.trim();
        if (clean.isEmpty() || clean.length() > 32) {
            player.sendMessage(Component.text("The pet name must be between 1 and 32 characters.", NamedTextColor.RED));
            return;
        }
        // Only the display name changes; level, XP, abilities, and Alpaca storage are untouched.
        pet.setCustomName(clean);
        activePets.refreshDisplay(player);
        storage.save();
        player.sendMessage(Component.text("Renamed your pet to '" + clean + "'.", NamedTextColor.GREEN));
    }

    void handleRestoreName(final Player player) {
        final OwnedPet pet = storage.data(player.getUniqueId()).activePet().orElse(null);
        if (pet == null) {
            player.sendMessage(message("messages.no-active-pet"));
            return;
        }
        pet.setCustomName(null);
        activePets.refreshDisplay(player);
        storage.save();
        definitions.get(pet.definitionId()).ifPresent(definition ->
            player.sendMessage(Component.text("Restored the default name (" + definition.name() + ").", NamedTextColor.GREEN)));
    }

    private void sendHelp(final CommandSender sender) {
        sender.sendMessage(Component.text("/pets - open your pet menu", NamedTextColor.GOLD));
        sender.sendMessage(Component.text("/pets set name <name> - rename your active pet", NamedTextColor.YELLOW));
        sender.sendMessage(Component.text("/pets restore name - restore your active pet's default name", NamedTextColor.YELLOW));
        sender.sendMessage(Component.text("/pets version - show version, modules, and update check", NamedTextColor.AQUA));
        if (has(sender, INFO_PERMISSION)) {
            sender.sendMessage(Component.text("/pets info - pet catalogue", NamedTextColor.YELLOW));
        }
        if (has(sender, CHANCES_PERMISSION)) {
            sender.sendMessage(Component.text("/pets chances - spawn chance GUI", NamedTextColor.YELLOW));
            sender.sendMessage(Component.text("/pets notify - discovery broadcast GUI", NamedTextColor.YELLOW));
            sender.sendMessage(Component.text("/pets drop - choose pet sources (chest/fishing/trader/brushing/vault/trial spawner)", NamedTextColor.YELLOW));
        }
        if (has(sender, GIVE_PERMISSION)) {
            sender.sendMessage(Component.text("/pets give <pet|all> [level] [player] - give test pet items", NamedTextColor.YELLOW));
        }
        if (has(sender, ADMIN_PERMISSION)) {
            sender.sendMessage(Component.text("/pets modules - optional modules GUI", NamedTextColor.AQUA));
            sender.sendMessage(Component.text("/pets reload - reload config, modules, and models", NamedTextColor.AQUA));
            sender.sendMessage(Component.text("/pets update - download the latest version (restart to apply)", NamedTextColor.AQUA));
        }
    }

    private List<Component> catalogueLore(final PetDefinition definition) {
        final List<Component> lore = new ArrayList<>();
        lore.add(Component.text(abilitySummary(definition.id()), NamedTextColor.GRAY));
        lore.add(Component.empty());
        lore.add(Component.text("Rarity: ", NamedTextColor.GRAY)
            .append(Component.text(definition.rarity(), definition.rarityColor())));
        lore.add(Component.text("Default drop weight: " + formatPercent(spawnChance(definition)) + "%", NamedTextColor.AQUA));
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
                unlocks.add("Revive cooldown reduced to 18h");
            } else if (level == 100) {
                unlocks.add("Revive cooldown reduced to 12h");
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
            case "mole" -> "Chance to mine dirt, sand, and gravel without spending tool durability.";
            case "allay" -> "Pulls in nearby dropped items straight to your inventory.";
            case "cursed_plushie" -> "Distraction dummy: hostile mobs sometimes lose interest in you.";
            case "owl" -> "Night Vision and increased luck.";
            case "panda" -> "More attack knockback, bamboo biome hero effect.";
            case "penguin" -> "Speed in cold biomes and frosted ice trail.";
            case "phoenix" -> "Fire Resistance, burns undead, revives you from death once off cooldown.";
            case "platypus" -> "Poisons melee and ranged attackers while you are wet (water or rain).";
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
            case "mole" -> Math.round(Math.min(0.6, 0.1 + (tier * 0.025)) * 100) + "% no-durability chance";
            case "allay" -> formatDecimal(Math.min(12.0, 4.0 + (tier * 0.4))) + " block pickup radius";
            case "cursed_plushie" -> Math.round(Math.min(0.75, 0.25 + (tier * 0.025)) * 100) + "% mob distraction chance";
            case "owl" -> "+" + (tier * 25) + " luck";
            case "panda" -> "+" + Math.round(tier * 5.0) + "% knockback";
            case "penguin" -> "+" + formatDecimal(tier * 0.00375) + " cold speed";
            case "phoenix" -> level >= 100 ? "12h revive cooldown" : level >= 50 ? "18h revive cooldown" : "24h revive cooldown";
            case "platypus" -> (80 + tier * 6) + " tick poison on attackers while wet";
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

    private String formatDuration(final long millis) {
        final long totalSeconds = Math.max(0L, millis / 1000L);
        final long hours = totalSeconds / 3600L;
        final long minutes = (totalSeconds % 3600L) / 60L;
        final long seconds = totalSeconds % 60L;
        if (hours > 0) {
            return hours + "h " + minutes + "m";
        }
        if (minutes > 0) {
            return minutes + "m " + seconds + "s";
        }
        return seconds + "s";
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

    private static final class NotifyMenuHolder implements InventoryHolder {
        private final UUID owner;
        private Inventory inventory;

        private NotifyMenuHolder(final UUID owner) {
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

    private static final class DropMenuHolder implements InventoryHolder {
        private final UUID owner;
        private Inventory inventory;

        private DropMenuHolder(final UUID owner) {
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

    private static final class ModulesMenuHolder implements InventoryHolder {
        private final UUID owner;
        private Inventory inventory;

        private ModulesMenuHolder(final UUID owner) {
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
            if (args.length > 0 && args[0].equalsIgnoreCase("help")) {
                plugin.sendHelp(sender);
                return;
            }
            if (args.length > 0 && args[0].equalsIgnoreCase("version")) {
                plugin.handleVersionCommand(sender);
                return;
            }
            if (args.length > 0 && args[0].equalsIgnoreCase("update")) {
                plugin.handleUpdateCommand(sender);
                return;
            }
            if (args.length > 0 && args[0].equalsIgnoreCase("reload")) {
                plugin.reloadBetterPets(sender);
                return;
            }
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
            if (args.length > 0 && args[0].equalsIgnoreCase("notify")) {
                if (sender instanceof Player player) {
                    plugin.openNotifyMenu(player);
                } else {
                    sender.sendMessage(plugin.message("messages.only-players"));
                }
                return;
            }
            if (args.length > 0 && args[0].equalsIgnoreCase("drop")) {
                if (sender instanceof Player player) {
                    plugin.openDropMenu(player);
                } else {
                    sender.sendMessage(plugin.message("messages.only-players"));
                }
                return;
            }
            if (args.length > 0 && args[0].equalsIgnoreCase("modules")) {
                if (sender instanceof Player player) {
                    plugin.openModulesMenu(player);
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

            if (args.length >= 1 && args[0].equalsIgnoreCase("set") && args.length >= 2 && args[1].equalsIgnoreCase("name")) {
                if (args.length < 3) {
                    player.sendMessage(plugin.message("messages.usage-set-name"));
                    return;
                }
                plugin.handleSetName(player, String.join(" ", java.util.Arrays.copyOfRange(args, 2, args.length)));
                return;
            }
            if (args.length >= 2 && args[0].equalsIgnoreCase("restore") && args[1].equalsIgnoreCase("name")) {
                plugin.handleRestoreName(player);
                return;
            }

            plugin.openPetsMenu(player);
        }

        @Override
        public Collection<String> suggest(final CommandSourceStack stack, final String[] args) {
            if (args.length == 1) {
                final List<String> suggestions = new ArrayList<>();
                suggestions.add("help");
                suggestions.add("version");
                suggestions.add("set");
                suggestions.add("restore");
                if (plugin.has(stack.getSender(), GIVE_PERMISSION)) {
                    suggestions.add("give");
                }
                if (plugin.has(stack.getSender(), INFO_PERMISSION)) {
                    suggestions.add("info");
                }
                if (plugin.has(stack.getSender(), CHANCES_PERMISSION)) {
                    suggestions.add("chances");
                    suggestions.add("notify");
                    suggestions.add("drop");
                }
                if (plugin.has(stack.getSender(), ADMIN_PERMISSION)) {
                    if (plugin.experimentalModulesEnabled()) {
                        suggestions.add("modules");
                    }
                    suggestions.add("reload");
                    suggestions.add("update");
                }
                return suggestions;
            }
            if (args.length == 2 && (args[0].equalsIgnoreCase("set") || args[0].equalsIgnoreCase("restore"))) {
                return List.of("name");
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
                || plugin.has(sender, INFO_PERMISSION)
                || plugin.has(sender, ADMIN_PERMISSION);
        }

        @Override
        public String permission() {
            return null;
        }
    }
}
