package de.kamil.betterpets;

import de.kamil.betterpets.model.PetModelService;
import de.kamil.betterpets.modules.BetterModelModule;
import de.kamil.betterpets.modules.Module;
import de.kamil.betterpets.modules.ModuleManager;
import com.destroystokyo.paper.event.player.PlayerPickupExperienceEvent;
import io.papermc.paper.command.brigadier.BasicCommand;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.event.player.AsyncChatEvent;
import io.papermc.paper.event.player.PlayerTradeEvent;
import io.papermc.paper.plugin.lifecycle.event.types.LifecycleEvents;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.NamespacedKey;
import org.bukkit.OfflinePlayer;
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
import org.bukkit.event.inventory.InventoryOpenEvent;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.event.inventory.PrepareAnvilEvent;
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
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;

import java.util.Collection;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Random;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

public final class BetterPetsPlugin extends JavaPlugin implements Listener {
    // Singleton reference so the public de.kamil.betterpets.api.BetterPetsApi (called by other plugins,
    // typically via reflection with no hard dependency) can delegate to the running plugin instance.
    private static BetterPetsPlugin instance;

    public static BetterPetsPlugin getInstance() {
        return instance;
    }

    private static final String USE_PERMISSION = "betterpets.command.pets";
    private static final String GIVE_PERMISSION = "betterpets.give";
    private static final String CHANCES_PERMISSION = "betterpets.chances";
    private static final String INFO_PERMISSION = "betterpets.info";
    private static final String ADMIN_PERMISSION = "betterpets.admin";
    private static final int PET_SLOT_LIMIT = 45;

    private PetDefinitions definitions;
    private PetStorage storage;
    private PetItemFactory itemFactory;
    private ActivePetManager activePets;
    private PetModelService modelService;
    private ModuleManager moduleManager;
    private LangManager lang;
    private Updater updater;
    private NamespacedKey generatedChestKey;
    private NamespacedKey containerOpenedKey;
    private NamespacedKey announceOnPickupKey;
    private BukkitTask saveTask;
    private boolean savePending;
    // Cached copy of pet-xp-multiplier; refreshed on enable/reload/change so the XP hot path (every XP
    // gain) does not re-parse the config each time.
    private double cachedXpMultiplier = 1.0;
    private final Map<UUID, Long> menuCooldowns = new HashMap<>();
    // Per-tick accounting of XP already credited to the pet from experience orbs, so plain
    // (non-orb) XP gained in the same tick is not dropped when we de-duplicate orb pickups.
    private final Map<UUID, long[]> orbXpThisTick = new HashMap<>();
    private final Map<UUID, Long> brushCooldowns = new HashMap<>();
    // Suspicious blocks (world:x:y:z) already rolled for a pet, so right-click spam can't re-roll one block.
    private final Set<String> brushedBlocks = new HashSet<>();
    // "Convert To Item" needs a second confirming click within this window (see Q1 in review).
    private final Map<UUID, Long> convertConfirms = new HashMap<>();
    private static final long CONVERT_CONFIRM_MILLIS = 5000L;
    // Players currently typing a numeric value in chat (spawn chance / XP multiplier). Read from the
    // async chat thread and written on the main thread, so it must be concurrent.
    private final Map<UUID, PendingInput> pendingInputs = new java.util.concurrent.ConcurrentHashMap<>();
    // Players already shown the one-time "/pets help" hint this session.
    private final Set<UUID> menuHintShown = new HashSet<>();
    // Pending /pets trade requests: target player id -> who asked + when it expires.
    private final Map<UUID, TradeRequest> tradeRequests = new HashMap<>();
    // Pets held for players who disconnected mid-trade; handed back on their next join so nothing is ever lost.
    private final Map<UUID, List<ItemStack>> pendingTradeReturns = new HashMap<>();
    private static final long TRADE_REQUEST_TIMEOUT_MILLIS = 60_000L;
    // Extra pet XP per ascension star (0.06 = +6%/star, +30% at ★5). Capped against the booster by xp-max-multiplier.
    private static final double ASCENSION_XP_PER_STAR = 0.06;
    private static final String[] DROP_SOURCES = {"chest", "fishing", "wandering-trader", "brushing", "vault", "trial-spawner", "xp-booster"};
    private static final int[] BOOSTER_DURATIONS = {15, 30, 45, 60};
    private static final int[] DROP_SLOTS = {10, 11, 12, 13, 14, 15, 16};
    private final Map<String, UUID> pendingLootOpeners = new HashMap<>();

    @Override
    public void onEnable() {
        instance = this;
        getLogger().info("Starting Better Pets as a pure Paper plugin.");
        saveDefaultConfig();
        // Keep the documented comments in config.yml intact across the saves this plugin performs.
        getConfig().options().parseComments(true);
        repairConfig();
        getConfig().options().copyDefaults(true);
        saveConfig();
        announceStorageMode();

        lang = new LangManager(this);
        lang.load();
        updater = new Updater(this);
        refreshXpMultiplierCache();

        definitions = PetDefinitions.load(this);
        getLogger().info("Loaded " + definitions.all().size() + " pet definitions.");
        ensureSpawnChanceDefaults();
        if (getServer().getPluginManager().isPluginEnabled("LuckPerms")) {
            getLogger().info("LuckPerms detected. Permission nodes: betterpets.command.pets, betterpets.give, betterpets.chances, betterpets.info, betterpets.admin.");
        }

        itemFactory = new PetItemFactory(this, lang);
        generatedChestKey = new NamespacedKey(this, "pet_loot_generated");
        containerOpenedKey = new NamespacedKey(this, "container_opened");
        announceOnPickupKey = new NamespacedKey(this, "announce_on_pickup");
        storage = new PetStorage(this);
        storage.load();
        recalculateAllPetExp();
        assignMissingVariants();
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

        activePets = new ActivePetManager(this, definitions, storage, itemFactory, modelService, lang);
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
                player.sendMessage(lang.colored("booster.expired", NamedTextColor.GRAY));
            } else {
                data.setBooster(data.boosterTier(), remaining);
            }
        }
    }

    @Override
    public void onDisable() {
        getLogger().info("Stopping Better Pets.");
        // Return any pets still parked in open trade windows to their owners (their inventories are saved
        // by vanilla on shutdown) so a restart/reload never destroys offered pets.
        for (final Player online : Bukkit.getOnlinePlayers()) {
            if (online.getOpenInventory().getTopInventory().getHolder() instanceof TradeMenuHolder holder) {
                cancelTrade(holder);
            }
        }
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
        instance = null;
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

        if (menuHintShown.add(player.getUniqueId())) {
            player.sendMessage(lang.colored("hint.help", NamedTextColor.DARK_GRAY));
        }

        final PetMenuHolder holder = new PetMenuHolder(player.getUniqueId(), 0);
        // Show the token balance right in the title so players always see what they have to spend.
        final String title = tokensEnabled()
            ? "Better Pets   ✦ " + storage.data(player.getUniqueId()).tokens()
            : "Better Pets";
        final Inventory inventory = Bukkit.createInventory(holder, 54, Texts.menuTitle(title));
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
            } else if (event.getView().getTopInventory().getHolder() instanceof VariantMenuHolder variantHolder) {
                handleVariantClick(event, variantHolder);
            } else if (event.getView().getTopInventory().getHolder() instanceof CustomizeMenuHolder customizeHolder) {
                handleCustomizeClick(event, customizeHolder);
            } else if (event.getView().getTopInventory().getHolder() instanceof ShopMenuHolder shopHolder) {
                handleShopClick(event, shopHolder);
            } else if (event.getView().getTopInventory().getHolder() instanceof AscensionMenuHolder ascensionHolder) {
                handleAscensionClick(event, ascensionHolder);
            } else if (event.getView().getTopInventory().getHolder() instanceof LeaderboardMenuHolder leaderboardHolder) {
                handleLeaderboardClick(event, leaderboardHolder);
            } else if (event.getView().getTopInventory().getHolder() instanceof TradeMenuHolder tradeHolder) {
                handleTradeClick(event, tradeHolder);
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
                ensureVariant(data, pet);
                data.setActivePet(pet.uuid());
                activePets.spawn(player, pet);
                requestSave();
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
                if (has(player, ADMIN_PERMISSION)) {
                    openXpMenu(player);
                }
            }
            case 49 -> player.closeInventory();
            case 50 -> {
                data.setVisible(!data.visible());
                activePets.setVisible(player, data.visible());
                requestSave();
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
                requestSave();
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
            || event.getView().getTopInventory().getHolder() instanceof PetDetailMenuHolder
            || event.getView().getTopInventory().getHolder() instanceof LeaderboardMenuHolder
            || event.getView().getTopInventory().getHolder() instanceof TradeMenuHolder
            || event.getView().getTopInventory().getHolder() instanceof ShopMenuHolder
            || event.getView().getTopInventory().getHolder() instanceof AscensionMenuHolder
            || event.getView().getTopInventory().getHolder() instanceof CustomizeMenuHolder
            || event.getView().getTopInventory().getHolder() instanceof VariantMenuHolder) {
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
        // Closing a trade window (manually, or because a player disconnected) cancels the deal and returns
        // everyone's offered pets. completeTrade marks the holder settled first, so a successful trade's own
        // closeInventory() calls here are a no-op.
        if (event.getInventory().getHolder() instanceof TradeMenuHolder tradeHolder) {
            if (!tradeHolder.settled()) {
                // Defer one tick: closing this viewer's window while completeTrade is closing the other's must
                // not re-enter mid-loop, and getPlayer lookups stay valid for returning items.
                Bukkit.getScheduler().runTask(this, () -> cancelTrade(tradeHolder));
            }
            return;
        }
        if (!(event.getInventory().getHolder() instanceof AlpacaStorageHolder holder) || !(event.getPlayer() instanceof Player player)) {
            return;
        }
        saveAlpacaStorage(player, holder, event.getInventory());
        requestSave();
    }

    @EventHandler
    public void onProtectedItemAnvilPrepare(final PrepareAnvilEvent event) {
        if (isProtectedBetterPetsItem(event.getInventory().getItem(0))
            || isProtectedBetterPetsItem(event.getInventory().getItem(1))
            || isProtectedBetterPetsItem(event.getResult())) {
            event.setResult(null);
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onProtectedItemAnvilClick(final InventoryClickEvent event) {
        if (event.getView().getTopInventory().getType() != InventoryType.ANVIL) {
            return;
        }
        final Inventory top = event.getView().getTopInventory();
        final int rawSlot = event.getRawSlot();
        boolean protectedMove = rawSlot >= 0 && rawSlot < top.getSize()
            && (isProtectedBetterPetsItem(event.getCursor()) || isProtectedBetterPetsItem(event.getCurrentItem()));
        protectedMove |= event.isShiftClick() && isProtectedBetterPetsItem(event.getCurrentItem());
        protectedMove |= event.getClick() == ClickType.NUMBER_KEY && event.getHotbarButton() >= 0
            && event.getWhoClicked() instanceof Player hotbarPlayer
            && isProtectedBetterPetsItem(hotbarPlayer.getInventory().getItem(event.getHotbarButton()));
        protectedMove |= event.getClick() == ClickType.SWAP_OFFHAND
            && event.getWhoClicked() instanceof Player offhandPlayer
            && isProtectedBetterPetsItem(offhandPlayer.getInventory().getItemInOffHand());
        if (!protectedMove) {
            return;
        }

        event.setCancelled(true);
        if (event.getWhoClicked() instanceof Player player) {
            player.sendMessage(message("messages.protected-anvil"));
            player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_BASS, 0.8F, 0.7F);
        }
    }

    private boolean isProtectedBetterPetsItem(final ItemStack item) {
        return itemFactory.petId(item).isPresent() || itemFactory.boosterTier(item) > 0;
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
        // Activate from the held item, then consume exactly one on success. All activation logic lives in
        // activateBooster so the public API can reuse it without requiring a physical item.
        if (activateBooster(player, itemFactory.boosterTier(item), itemFactory.boosterMinutes(item))) {
            consumeOne(player, item);
        }
    }

    /**
     * Activates a Pet XP Booster for the player, exactly like using a booster item but WITHOUT requiring or
     * consuming one (the caller manages the item). Boosters never stack: if one is already running, the
     * player gets the usual "already active" feedback and this returns false. {@code minutes} is clamped to
     * {@link #maxBoosterMinutes()}. Public entry point for {@link de.kamil.betterpets.api.BetterPetsApi}.
     *
     * @return true if a booster was activated, false if one was already active or the tier is invalid (not 2..5).
     */
    public boolean activateBooster(final Player player, final int tier, final int minutes) {
        final PlayerPetData data = storage.data(player.getUniqueId());
        if (data.hasActiveBooster()) {
            player.sendMessage(lang.colored("booster.already-active", NamedTextColor.RED,
                "%tier%", Integer.toString(data.boosterTier()),
                "%time%", formatDuration(data.boosterRemainingMillis())));
            player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_BASS, 0.8F, 0.8F);
            return false;
        }
        if (tier < 2 || tier > 5) {
            return false;
        }
        final int clampedMinutes = Math.max(1, Math.min(maxBoosterMinutes(), minutes));
        data.setBooster(tier, clampedMinutes * 60_000L);
        data.setBoosterTickReference(System.currentTimeMillis());
        requestSave();
        player.sendMessage(lang.colored("booster.activated", NamedTextColor.LIGHT_PURPLE,
            "%tier%", Integer.toString(tier), "%time%", formatBoosterMinutes(clampedMinutes)));
        player.playSound(player.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 0.7F, 1.3F);
        if (getConfig().getBoolean("xp-booster.broadcast-activations", true)) {
            broadcastBoosterActivation(player, tier, clampedMinutes);
        }
        return true;
    }

    // ---- Public accessors for de.kamil.betterpets.api.BetterPetsApi -----------------------------------

    /** Booster tier (2..5) of an item, or 0 if it is not a Pet XP Booster. */
    public int boosterTier(final ItemStack item) {
        return itemFactory.boosterTier(item);
    }

    /** Configured booster minutes stored on an item, or 0 if it is not a booster. */
    public int boosterMinutes(final ItemStack item) {
        return itemFactory.boosterMinutes(item);
    }

    /** Whether the player currently has an active Pet XP Booster running. */
    public boolean hasActiveBooster(final Player player) {
        return storage.data(player.getUniqueId()).hasActiveBooster();
    }

    @EventHandler
    public void onPlayerInteractEntity(final PlayerInteractEntityEvent event) {
        if (event.getHand() != EquipmentSlot.HAND) {
            return;
        }
        final Player player = event.getPlayer();
        final Optional<OwnedPet> clickedPet = activePets.clickedActivePet(player, event.getRightClicked());
        if (clickedPet.isPresent()) {
            final OwnedPet pet = clickedPet.get();
            // Sneak + right-click always opens Customize; a plain right-click also opens it for pets with
            // no other right-click action (the Alpaca opens storage, flyable pets start their mount).
            final boolean special = pet.definitionId().equals("alpaca")
                || activePets.isFlyable(pet.definitionId())
                || activePets.isGroundRideable(pet.definitionId());
            if (player.isSneaking() || !special) {
                event.setCancelled(true);
                openCustomizeMenu(player, pet);
                return;
            }
            if (pet.definitionId().equals("alpaca")) {
                event.setCancelled(true);
                openAlpacaStorage(player, pet);
                return;
            }
        }
        if (activePets.handlePetInteraction(player, event.getRightClicked())) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onPlayerToggleSneak(final PlayerToggleSneakEvent event) {
        if (!event.isSneaking()) {
            return;
        }
        if (activePets.stopRideIfSneaking(event.getPlayer())) {
            event.setCancelled(true);
            return;
        }
        activePets.handleKangarooJump(event.getPlayer());
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
        final ItemStack petItem = itemFactory.discoveryItem(definition);
        if (opener != null) {
            broadcastPetDiscovery(opener, definition);
        } else {
            // Loot was generated before any player opened the container (e.g. at world/chunk generation),
            // so there is nobody to credit yet. Mark the pet so the broadcast fires when a player opens
            // the container (see onContainerOpen) or picks the item up.
            markAnnounceOnPickup(petItem, "");
        }
        event.getLoot().add(petItem);
        debug("Injected " + definition.name() + " into chest loot table " + key + ".");
    }

    @EventHandler
    public void onContainerOpen(final InventoryOpenEvent event) {
        if (!(event.getPlayer() instanceof Player player)) {
            return;
        }
        // Remember that a player has opened this container, so the Penguin's Treasure Sense stops
        // glowing it. This is the only reliable "opened" signal for worlds that pre-generate loot,
        // where the loot table is already cleared and the container would otherwise look untouched.
        markContainerOpened(event.getInventory());
        // Announce any pre-generated pet that was waiting in this container (marker set at loot time).
        final Inventory inventory = event.getInventory();
        final ItemStack[] contents = inventory.getContents();
        for (int slot = 0; slot < contents.length; slot++) {
            final ItemStack stack = contents[slot];
            if (stack == null || !stack.hasItemMeta()) {
                continue;
            }
            final ItemMeta meta = stack.getItemMeta();
            final String suffix = meta.getPersistentDataContainer().get(announceOnPickupKey, PersistentDataType.STRING);
            if (suffix == null) {
                continue;
            }
            meta.getPersistentDataContainer().remove(announceOnPickupKey);
            stack.setItemMeta(meta);
            inventory.setItem(slot, stack);
            itemFactory.petId(stack)
                .flatMap(definitions::get)
                .ifPresent(definition -> announcePet(player, definition, "found", suffix));
        }
    }

    private void markContainerOpened(final Inventory inventory) {
        final InventoryHolder holder = inventory.getHolder();
        if (holder instanceof DoubleChest doubleChest) {
            markOpened(doubleChest.getLeftSide());
            markOpened(doubleChest.getRightSide());
        } else {
            markOpened(holder);
        }
    }

    private void markOpened(final Object holder) {
        if (holder instanceof TileState tile) {
            // Block containers only persist PDC changes once the captured state is written back.
            if (tile.getPersistentDataContainer().has(containerOpenedKey, PersistentDataType.BYTE)) {
                return;
            }
            tile.getPersistentDataContainer().set(containerOpenedKey, PersistentDataType.BYTE, (byte) 1);
            tile.update();
        } else if (holder instanceof org.bukkit.persistence.PersistentDataHolder entityHolder) {
            // Chest / hopper minecarts and other entity-backed containers persist their PDC directly.
            entityHolder.getPersistentDataContainer().set(containerOpenedKey, PersistentDataType.BYTE, (byte) 1);
        }
    }

    @EventHandler
    public void onPlayerJoin(final PlayerJoinEvent event) {
        activePets.prepareJoiningPlayer(event.getPlayer());
        // Remember the name so /pets top can show it without a blocking offline-UUID lookup.
        storage.data(event.getPlayer().getUniqueId()).setPlayerName(event.getPlayer().getName());
        // Hand back any pets held from a trade the player disconnected out of.
        deliverPendingTradeReturns(event.getPlayer());
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
        brushCooldowns.remove(event.getPlayer().getUniqueId());
        orbXpThisTick.remove(event.getPlayer().getUniqueId());
        convertConfirms.remove(event.getPlayer().getUniqueId());
        pendingInputs.remove(event.getPlayer().getUniqueId());
        menuHintShown.remove(event.getPlayer().getUniqueId());
        // Drop any pending trade requests involving this player (as target or requester) so they don't linger.
        final UUID quitId = event.getPlayer().getUniqueId();
        tradeRequests.remove(quitId);
        tradeRequests.values().removeIf(request -> request.requester().equals(quitId));
        // A player leaving is a natural, infrequent save point, so persist immediately for durability.
        storage.save();
    }

    @EventHandler
    public void onPlayerPickupExperience(final PlayerPickupExperienceEvent event) {
        final Player player = event.getPlayer();
        // Count the orb's full value, before Mending diverts any of it to tool repair, and remember how
        // much orb XP we already credited this tick so onPlayerExpChange does not count it again.
        final int value = event.getExperienceOrb().getExperience();
        addOrbXpThisTick(player.getUniqueId(), (long) Bukkit.getCurrentTick(), value);
        grantPetExp(player, value);
    }

    @EventHandler
    public void onPlayerExpChange(final PlayerExpChangeEvent event) {
        final Player player = event.getPlayer();
        final long tick = Bukkit.getCurrentTick();
        final long[] orb = orbXpThisTick.get(player.getUniqueId());
        // Orb pickups are already credited (at full value) by onPlayerPickupExperience. If this change is
        // in the same tick, only credit the part that exceeds what orbs already gave, so simultaneous
        // non-orb XP (commands, plugins) is not lost while orb XP is never double counted.
        if (orb != null && orb[0] == tick) {
            final int extra = (int) (event.getAmount() - orb[1]);
            if (extra > 0) {
                grantPetExp(player, extra);
                orb[1] += extra;
            }
            return;
        }
        grantPetExp(player, event.getAmount());
    }

    private void addOrbXpThisTick(final UUID playerId, final long tick, final int amount) {
        final long[] current = orbXpThisTick.get(playerId);
        if (current == null || current[0] != tick) {
            orbXpThisTick.put(playerId, new long[]{tick, amount});
        } else {
            current[1] += amount;
        }
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
            // The pet-XP speed-up is booster x ascension, but capped so the two never stack into an absurd
            // rate (e.g. x5 booster x +50% would be 7.5x). Ascension adds +6% per star (+30% at max).
            final double boosterFactor = boosterData.hasActiveBooster() ? boosterData.boosterTier() : 1.0;
            final double ascensionFactor = 1.0 + pet.stars() * ASCENSION_XP_PER_STAR;
            final double cap = Math.max(1.0, getConfig().getDouble("xp-max-multiplier", 6.0));
            final double combined = Math.min(cap, boosterFactor * ascensionFactor);
            final int effectiveAmount = Math.max(1, (int) Math.round(amount * combined));
            final boolean leveled = pet.addExp(effectiveAmount, petXpMultiplier());
            if (leveled) {
                activePets.refreshDisplay(player);
                definitions.get(pet.definitionId()).ifPresent(definition ->
                    player.sendMessage(lang.colored("pet.leveled", NamedTextColor.GREEN,
                        "%pet%", definition.name(), "%level%", Integer.toString(pet.level())))
                );
                player.playSound(player.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 0.8F, 1.5F);
            }
        });
    }

    @EventHandler(ignoreCancelled = true)
    public void onEntityDamageByEntity(final EntityDamageByEntityEvent event) {
        if (!(event.getEntity() instanceof LivingEntity victim)) {
            return;
        }

        final Player player = damagingPlayer(event.getDamager());
        if (player != null) {
            activePets.applyHitAbility(player, victim);
            // Warrior-track ascension: +3% outgoing damage per star.
            final int stars = activePets.activeStars(player);
            if (stars > 0 && activePets.activeTrack(player) == Ascension.Track.WARRIOR) {
                event.setDamage(event.getDamage() * (1.0 + stars * 0.03));
            }
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
        broadcastBoosterDrop(killer, dead, tier, minutes);
        if (getConfig().getBoolean("debug-logging", false)) {
            debug(killer.getName() + " earned a Pet XP Booster x" + tier + " (" + minutes + "m) from a " + dead.getType() + ".");
        }
    }

    private void broadcastBoosterDrop(final Player player, final LivingEntity source, final int tier, final int minutes) {
        if (!getConfig().getBoolean("xp-booster.broadcast-drops", true)) {
            return;
        }
        broadcastToUnmuted(Texts.prefix().append(lang.colored("broadcast.booster-drop", NamedTextColor.GRAY,
            "%player%", player.getName(), "%tier%", Integer.toString(tier),
            "%time%", formatBoosterMinutes(minutes), "%source%", friendlyEntityName(source))));
        playBoosterSound(1.35F);
    }

    private void broadcastBoosterActivation(final Player player, final int tier, final int minutes) {
        broadcastToUnmuted(Texts.prefix().append(lang.colored("broadcast.booster-activate", NamedTextColor.GRAY,
            "%player%", player.getName(), "%tier%", Integer.toString(tier), "%time%", formatBoosterMinutes(minutes))));
        playBoosterSound(1.6F);
    }

    private void playBoosterSound(final float pitch) {
        for (final Player online : Bukkit.getOnlinePlayers()) {
            if (storage.data(online.getUniqueId()).broadcastsMuted()) {
                continue;
            }
            online.playSound(online.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 0.8F, pitch);
        }
    }

    private String friendlyEntityName(final LivingEntity entity) {
        return entity.getType().name().toLowerCase(Locale.ROOT).replace('_', ' ');
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
        // While flying on a pet you can briefly clip into a block; never let that suffocate the rider.
        if ((event.getCause() == EntityDamageEvent.DamageCause.SUFFOCATION
            || event.getCause() == EntityDamageEvent.DamageCause.CRAMMING)
            && activePets.isRiding(player)) {
            event.setCancelled(true);
            return;
        }
        if (event.getCause() == EntityDamageEvent.DamageCause.VOID) {
            return;
        }
        // Ascension: Guardian pets soften all incoming damage; Runner pets soften fall damage.
        final int defenseStars = activePets.activeStars(player);
        if (defenseStars > 0) {
            final Ascension.Track track = activePets.activeTrack(player);
            if (track == Ascension.Track.GUARDIAN) {
                event.setDamage(event.getDamage() * Math.max(0.5, 1.0 - defenseStars * 0.03));
            } else if (track == Ascension.Track.RUNNER && event.getCause() == EntityDamageEvent.DamageCause.FALL) {
                event.setDamage(event.getDamage() * Math.max(0.0, 1.0 - defenseStars * 0.18));
            }
        }
        // Absorption soaks damage before health does, so a hit fully covered by absorption is not lethal
        // and must not trigger (and waste) the Phoenix revive.
        if (player.getHealth() + player.getAbsorptionAmount() - event.getFinalDamage() > 0.0) {
            return;
        }
        if (activePets.tryPhoenixRevive(player)) {
            event.setCancelled(true);
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onBlockBreak(final BlockBreakEvent event) {
        // Re-entry guard: our own Woodpecker/Badger chain-break fires BlockBreakEvents for protection checks.
        if (activePets.isChainBreaking()) {
            return;
        }
        activePets.handleMoleBreak(event.getPlayer(), event.getBlock());
        activePets.handleOreBonus(event.getPlayer(), event.getBlock());
        activePets.handleSquirrelForage(event.getPlayer(), event.getBlock());
        activePets.handleArcaneFoxMine(event.getPlayer(), event.getBlock());
        activePets.handleMechanistBlockBreak(event.getPlayer(), event.getBlock());
        activePets.handleSilkMoth(event);
        activePets.handleSalamanderSmelt(event);
        activePets.handleWoodpecker(event.getPlayer(), event.getBlock());
        activePets.handleBadgerVein(event.getPlayer(), event.getBlock());
        activePets.handleScarecrow(event.getPlayer(), event.getBlock());
        activePets.handleGathererBonus(event.getPlayer(), event.getBlock());
        maybeOreTokenDrop(event.getPlayer(), event.getBlock());
        // Now that all break bonuses have run, forget this position so the placed-block set stays bounded.
        activePets.forgetPlacedBlock(event.getBlock());
    }

    private boolean isOre(final Material type) {
        return type.name().endsWith("_ORE") || type == Material.ANCIENT_DEBRIS;
    }

    /** A small chance for a mined ore to drop 1..max tokens directly, with an optional chat broadcast. */
    private void maybeOreTokenDrop(final Player player, final Block block) {
        if (!tokensEnabled() || !getConfig().getBoolean("tokens.ore-tokens.enabled", true)) {
            return;
        }
        if (!isOre(block.getType()) || activePets.isPlayerPlaced(block)) {
            return;
        }
        final org.bukkit.GameMode mode = player.getGameMode();
        if (mode != org.bukkit.GameMode.SURVIVAL && mode != org.bukkit.GameMode.ADVENTURE) {
            return;
        }
        final double chance = getConfig().getDouble("tokens.ore-tokens.chance-percent", 3.0);
        if (chance <= 0.0 || ThreadLocalRandom.current().nextDouble(100.0) >= chance) {
            return;
        }
        final int min = Math.max(1, getConfig().getInt("tokens.ore-tokens.min", 1));
        final int max = Math.max(min, getConfig().getInt("tokens.ore-tokens.max", 5));
        final int amount = min + ThreadLocalRandom.current().nextInt(max - min + 1);
        final PlayerPetData data = storage.data(player.getUniqueId());
        data.addTokens(amount);
        requestSave();
        player.playSound(player.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 0.7F, 1.6F);
        final String ore = friendlyMaterialName(block.getType());
        if (getConfig().getBoolean("tokens.ore-tokens.broadcast", true)) {
            broadcastToUnmuted(Texts.prefix().append(lang.colored("broadcast.ore-tokens", NamedTextColor.GRAY,
                "%player%", player.getName(), "%tokens%", Integer.toString(amount), "%ore%", ore)));
        } else {
            player.sendMessage(lang.component("tokens.ore-found", "%tokens%", Integer.toString(amount), "%ore%", ore));
        }
    }

    private String friendlyMaterialName(final Material material) {
        return material.name().toLowerCase(Locale.ROOT).replace('_', ' ');
    }

    @EventHandler(ignoreCancelled = true)
    public void onGolemMasonPlace(final org.bukkit.event.block.BlockPlaceEvent event) {
        // Track player-placed blocks so gatherer/chain bonuses never duplicate them.
        activePets.notePlacedBlock(event.getBlockPlaced());
        activePets.handleGolemRefill(event);
    }

    @EventHandler(ignoreCancelled = true)
    public void onPetEquipAttempt(final InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) {
            return;
        }
        final InventoryHolder holder = event.getView().getTopInventory().getHolder();
        if (holder instanceof PetMenuHolder || holder instanceof ChanceMenuHolder || holder instanceof NotifyMenuHolder
            || holder instanceof XpMenuHolder || holder instanceof ModulesMenuHolder || holder instanceof InfoMenuHolder || holder instanceof PetDetailMenuHolder
            || holder instanceof AlpacaStorageHolder
            || holder instanceof VariantMenuHolder || holder instanceof CustomizeMenuHolder
            || holder instanceof ShopMenuHolder || holder instanceof AscensionMenuHolder
            || holder instanceof LeaderboardMenuHolder || holder instanceof TradeMenuHolder) {
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
                ml("menu.main.empty-slot", NamedTextColor.DARK_GRAY),
                List.of(mg("menu.main.empty-slot-lore"))
            ));
        }

        inventory.setItem(45, itemFactory.control(
            holder.page() > 0 ? Material.ARROW : Material.GRAY_STAINED_GLASS_PANE,
            ml("menu.common.back", holder.page() > 0 ? NamedTextColor.YELLOW : NamedTextColor.DARK_GRAY),
            List.of(ml("menu.common.page", NamedTextColor.GRAY, "%page%", Integer.toString(holder.page() + 1), "%pages%", Integer.toString(pages)))
        ));
        inventory.setItem(46, itemFactory.control(
            Material.KNOWLEDGE_BOOK,
            ml("menu.main.catalogue", NamedTextColor.GOLD),
            List.of(
                mg("menu.main.catalogue-lore"),
                ml("menu.main.pets-count", NamedTextColor.DARK_GRAY, "%have%", Integer.toString(pets.size()), "%max%", Integer.toString(Math.max(1, getConfig().getInt("max-pets-per-player", PET_SLOT_LIMIT))))
            )
        ));
        if (has(player, CHANCES_PERMISSION)) {
            inventory.setItem(47, itemFactory.control(
                Material.COMPARATOR,
                ml("menu.main.spawn-chances", NamedTextColor.YELLOW),
                List.of(mg("menu.main.spawn-chances-lore"))
            ));
        }
        if (has(player, ADMIN_PERMISSION)) {
            // The server-wide XP Multiplier is an admin control; the booster status is shown below it.
            final List<Component> xpLore = new ArrayList<>();
            xpLore.add(ml("menu.main.xp-current", NamedTextColor.GRAY, "%n%", formatDecimal(petXpMultiplier())));
            xpLore.add(mg("menu.main.xp-range"));
            xpLore.add(Component.empty());
            xpLore.addAll(boosterStatusLines(data));
            inventory.setItem(48, itemFactory.control(
                Material.EXPERIENCE_BOTTLE,
                ml("menu.main.xp-multiplier", NamedTextColor.AQUA),
                xpLore
            ));
        } else {
            // Everyone else gets a dedicated Pet XP Booster status item in the same spot.
            inventory.setItem(48, itemFactory.control(
                Material.EXPERIENCE_BOTTLE,
                ml("menu.main.xp-booster", NamedTextColor.LIGHT_PURPLE),
                boosterStatusLines(data)
            ));
        }
        inventory.setItem(50, itemFactory.control(
            Material.ENDER_EYE,
            ml(data.visible() ? "menu.main.pet-visible" : "menu.main.pet-hidden", data.visible() ? NamedTextColor.GREEN : NamedTextColor.YELLOW),
            List.of(mg("menu.main.visibility-lore"))
        ));
        inventory.setItem(51, itemFactory.control(
            holder.page() + 1 < pages ? Material.ARROW : Material.GRAY_STAINED_GLASS_PANE,
            ml("menu.main.next", holder.page() + 1 < pages ? NamedTextColor.YELLOW : NamedTextColor.DARK_GRAY),
            List.of(ml("menu.common.page", NamedTextColor.GRAY, "%page%", Integer.toString(holder.page() + 1), "%pages%", Integer.toString(pages)))
        ));
        inventory.setItem(52, itemFactory.control(
            Material.PURPLE_DYE,
            ml("menu.main.despawn", NamedTextColor.DARK_RED),
            List.of(mg("menu.main.despawn-lore"))
        ));
        inventory.setItem(53, itemFactory.control(
            Material.GRAY_DYE,
            ml("menu.main.convert", NamedTextColor.RED),
            List.of(mg("menu.main.convert-lore"))
        ));
        inventory.setItem(49, itemFactory.control(
            Material.BARRIER,
            ml("menu.common.close", NamedTextColor.RED),
            List.of(mg("menu.main.close-lore"))
        ));
    }

    private List<Component> boosterStatusLines(final PlayerPetData data) {
        if (data.hasActiveBooster()) {
            return List.of(
                lang.colored("booster.active-line", NamedTextColor.LIGHT_PURPLE, "%tier%", Integer.toString(data.boosterTier())),
                lang.colored("booster.time-left", NamedTextColor.AQUA, "%time%", formatDuration(data.boosterRemainingMillis())),
                lang.colored("booster.only-leveling", NamedTextColor.DARK_GRAY)
            );
        }
        return List.of(lang.colored("booster.no-active", NamedTextColor.GRAY));
    }

    private ItemStack petMenuItem(final PetDefinition definition, final OwnedPet pet, final boolean active) {
        final ItemStack item = itemFactory.menuItem(definition, pet, active);
        final ItemMeta meta = item.getItemMeta();
        final List<Component> lore = meta.lore() == null ? new ArrayList<>() : new ArrayList<>(meta.lore());
        lore.add(Component.empty());
        lore.add(ml("menu.pet.current-ability", NamedTextColor.AQUA));
        lore.add(Component.text(abilityValue(definition.id(), pet.level()), NamedTextColor.GRAY));
        if (definition.id().equals("phoenix")) {
            final long remaining = ActivePetManager.phoenixCooldownMillis(pet.level()) - (System.currentTimeMillis() - pet.lastTotemMillis());
            lore.add(Component.empty());
            if (remaining <= 0) {
                lore.add(ml("menu.pet.revive-ready", NamedTextColor.GREEN));
            } else {
                lore.add(ml("menu.pet.revive-cooldown", NamedTextColor.GRAY, "%time%", formatDuration(remaining)));
            }
        }
        if (activePets.isFlyable(definition.id()) && pet.level() >= 50) {
            lore.add(ml("menu.pet.fly-hint", NamedTextColor.GOLD));
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
        final Inventory inventory = Bukkit.createInventory(holder, size, Texts.menuTitle(mt("menu.title.alpaca-storage")));
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
        return PetAbilities.alpacaStorageSize(level);
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
        itemFactory.petCustomName(item).ifPresent(pet::setCustomName);
        pet.recalculateNextLevelExp(petXpMultiplier());
        pet.setExp(itemFactory.petExp(item));
        // Restore ascension progress and the nametag style so convert/trade never wipes them.
        pet.setFusionPoints(itemFactory.petFusionPoints(item));
        itemFactory.petNametagStyle(item).ifPresent(pet::setNametagStyle);
        // Keep the exact variant the loot/give item advertised; otherwise roll a fresh one.
        final String itemVariant = itemFactory.petVariant(item).orElse(null);
        if (itemVariant != null && definition.variants().containsKey(itemVariant.toLowerCase(Locale.ROOT))) {
            pet.setVariant(itemVariant);
            data.unlockVariant(pet.definitionId(), itemVariant);
        } else {
            ensureVariant(data, pet);
        }
        data.pets().add(pet);
        consumeOne(player, item);
        requestSave();
        player.sendMessage(message("messages.pet-added").replaceText(builder -> builder.matchLiteral("%pet%").replacement(definition.name())));
        player.playSound(player.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 0.8F, 1.8F);
        debug(player.getName() + " added pet " + definition.id() + " level " + pet.level() + " from item.");
    }

    /** Rolls a cosmetic variant for a pet that supports them but has none yet, unlocking it for the player. */
    private void ensureVariant(final PlayerPetData data, final OwnedPet pet) {
        if (pet == null || pet.variant() != null) {
            return;
        }
        definitions.get(pet.definitionId()).ifPresent(definition -> {
            if (definition.hasVariants()) {
                final String rolled = definition.randomVariant(ThreadLocalRandom.current());
                pet.setVariant(rolled);
                data.unlockVariant(pet.definitionId(), rolled);
            }
        });
    }

    /** Assigns variants to every stored pet that supports them but is missing one (e.g. pre-existing Axolotls). */
    private void assignMissingVariants() {
        boolean changed = false;
        for (final Map.Entry<UUID, PlayerPetData> entry : storage.entries()) {
            for (final OwnedPet pet : entry.getValue().pets()) {
                if (pet.variant() == null) {
                    ensureVariant(entry.getValue(), pet);
                    changed = changed || pet.variant() != null;
                } else {
                    // Make sure the worn skin is always in the per-player unlocked collection.
                    entry.getValue().unlockVariant(pet.definitionId(), pet.variant());
                }
            }
        }
        if (changed) {
            requestSave();
        }
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
        return location == null ? null : soleNearbyPlayer(location, 10.0);
    }

    /**
     * The single player within {@code radius} of {@code location}, or {@code null} if there are zero or
     * more than one. Refusing to guess when several players are close avoids crediting the wrong player
     * with a structure-chest pet discovery.
     */
    private Player soleNearbyPlayer(final Location location, final double radius) {
        if (location.getWorld() == null) {
            return null;
        }
        final double radiusSquared = radius * radius;
        Player found = null;
        for (final Player player : location.getWorld().getPlayers()) {
            if (player.getLocation().distanceSquared(location) <= radiusSquared) {
                if (found != null) {
                    return null;
                }
                found = player;
            }
        }
        return found;
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

    /** Sends a broadcast to every online player except those who muted broadcasts via /pets mute. */
    private void broadcastToUnmuted(final Component message) {
        for (final Player online : Bukkit.getOnlinePlayers()) {
            if (!storage.data(online.getUniqueId()).broadcastsMuted()) {
                online.sendMessage(message);
            }
        }
    }

    private void announcePet(final Player player, final PetDefinition definition, final String actionKey, final String suffixKey) {
        if (!discoveryBroadcastEnabled(definition.rarity())) {
            return;
        }
        // Full-sentence templates (word order differs per language), with the rarity colour injected as a
        // %rc% hex so the pet/rarity stay coloured. %suffix% is a translated " in a Vault"-style phrase or "".
        final String suffix = (suffixKey == null || suffixKey.isEmpty()) ? "" : mt("broadcast.suffix." + suffixKey);
        broadcastToUnmuted(Texts.prefix().append(lang.colored("broadcast." + actionKey, NamedTextColor.GRAY,
            "%player%", player.getName(),
            "%rarity%", definition.rarity(),
            "%pet%", definition.name(),
            "%suffix%", suffix,
            "%rc%", definition.rarityColor().asHexString())));
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
        activePets.handleWaterSerpentFish(event);
        activePets.handleAquaticFishBonus(event);
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
        announcePet(event.getPlayer(), definition, "fished", "");
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
        // A suspicious block may be rolled for a pet ONLY ONCE. Previously each right-click re-rolled the
        // same block (throttled only by a per-player cooldown), so tapping a single block eventually
        // guaranteed a pet. Require a pristine block (loot table still intact) and remember which blocks
        // we already decided, so every block gets exactly one roll.
        if (!(block.getState() instanceof org.bukkit.block.BrushableBlock brushable) || !brushable.hasLootTable()) {
            return;
        }
        final String blockKey = block.getWorld().getUID() + ":" + block.getX() + ":" + block.getY() + ":" + block.getZ();
        if (brushedBlocks.contains(blockKey)) {
            return;
        }
        final long now = System.currentTimeMillis();
        if (now < brushCooldowns.getOrDefault(player.getUniqueId(), 0L)) {
            return;
        }
        brushCooldowns.put(player.getUniqueId(), now + 1500L);
        // This block is now decided (win or lose) and can never roll again.
        if (brushedBlocks.size() > 100_000) {
            brushedBlocks.clear();
        }
        brushedBlocks.add(blockKey);
        final double chance = petSourceChance("brushing");
        if (chance <= 0.0 || ThreadLocalRandom.current().nextDouble(100.0) >= chance) {
            return;
        }
        final PetDefinition definition = rollSourcePet();
        if (definition == null) {
            return;
        }
        // Replace the item buried in the block so the pet is brushed out of the block naturally.
        brushable.setLootTable(null);
        brushable.setItem(itemFactory.discoveryItem(definition));
        brushable.update(true);
        announcePet(player, definition, "brushed", "");
    }

    @EventHandler(ignoreCancelled = true)
    public void onFireflySpawnShield(final CreatureSpawnEvent event) {
        if (event.getSpawnReason() != CreatureSpawnEvent.SpawnReason.NATURAL
            || !(event.getEntity() instanceof org.bukkit.entity.Monster)
            || !getConfig().getBoolean("firefly.enabled", true)) {
            return;
        }
        if (activePets.suppressesHostileSpawn(event.getLocation())) {
            event.setCancelled(true);
        }
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
        if (event.isCancelled()) {
            return;
        }
        final ItemStack result = event.getTrade().getResult();
        itemFactory.petId(result)
            .flatMap(definitions::get)
            .ifPresent(definition -> announcePet(event.getPlayer(), definition, "bought", "trader"));
        maybeGoblinTradeRefund(event);
    }

    /**
     * If the buyer has an active Goblin, there is a small chance it pickpockets the villager: it snatches
     * either a random few of the item the villager just sold, or a random number of emeralds. The emerald
     * grab scales with level and only a level-100 Goblin can grab the full price back (so trades are not
     * free — and never profitable — at lower levels).
     */
    private void maybeGoblinTradeRefund(final PlayerTradeEvent event) {
        if (event.isCancelled()) {
            return;
        }
        final Player player = event.getPlayer();
        final int level = activePets.goblinLevel(player);
        if (level <= 0 || !activePets.tryGoblinTradeSave(player)) {
            return;
        }
        final org.bukkit.inventory.MerchantRecipe recipe = event.getTrade();
        final ItemStack result = recipe.getResult();
        int paidEmeralds = 0;
        for (final ItemStack ingredient : recipe.getIngredients()) {
            if (ingredient != null && ingredient.getType() == Material.EMERALD) {
                paidEmeralds += ingredient.getAmount();
            }
        }
        final boolean canStealItem = result != null && !result.getType().isAir() && result.getType() != Material.EMERALD;

        final ItemStack loot;
        final String desc;
        if (canStealItem && (paidEmeralds <= 0 || ThreadLocalRandom.current().nextBoolean())) {
            // Snatch a small random stack of exactly what the villager just sold (book, tool, ...).
            final int max = Math.max(1, Math.min(result.getMaxStackSize(), 1 + (level / 34)));
            final int amount = 1 + ThreadLocalRandom.current().nextInt(max);
            loot = result.clone();
            loot.setAmount(amount);
            desc = amount + "x " + result.getType().name().toLowerCase(Locale.ROOT).replace('_', ' ');
        } else if (paidEmeralds > 0) {
            // Random emeralds; only a level-100 Goblin can grab the whole price, lower levels a fraction.
            final int cap = level >= 100 ? paidEmeralds : Math.max(1, (int) Math.round(paidEmeralds * (level / 100.0)));
            final int amount = 1 + ThreadLocalRandom.current().nextInt(Math.max(1, cap));
            loot = new ItemStack(Material.EMERALD, amount);
            desc = amount + " emerald" + (amount == 1 ? "" : "s");
        } else {
            return;
        }

        // Deferred a tick so the loot lands after the trade has settled the player's inventory.
        Bukkit.getScheduler().runTask(this, () -> {
            player.getInventory().addItem(loot).values()
                .forEach(stack -> player.getWorld().dropItemNaturally(player.getLocation(), stack));
            player.spawnParticle(Particle.HAPPY_VILLAGER, player.getLocation().add(0, 1.0, 0), 8, 0.3, 0.4, 0.3, 0.0);
            player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, 0.6F, 1.4F);
            player.sendMessage(lang.colored("broadcast.goblin", NamedTextColor.GREEN, "%item%", desc));
            player.sendActionBar(lang.colored("broadcast.goblin-actionbar", NamedTextColor.GREEN, "%item%", desc));
        });
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
        final String suffix = source.equals("vault") ? "vault" : "trial";
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
            case "mythical", "extraordinary" -> List.of(new ItemStack(Material.EMERALD, 64), new ItemStack(Material.NETHERITE_INGOT, 1));
            default -> List.of(new ItemStack(Material.EMERALD, 16), new ItemStack(Material.IRON_INGOT, 8));
        };
    }

    void openDropMenu(final Player player) {
        if (!has(player, ADMIN_PERMISSION)) {
            player.sendMessage(message("messages.no-permission"));
            return;
        }
        final DropMenuHolder holder = new DropMenuHolder(player.getUniqueId());
        final Inventory inventory = Bukkit.createInventory(holder, 27, Texts.menuTitle(mt("menu.title.sources")));
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
            lore.add(ml(enabled ? "menu.common.enabled" : "menu.common.disabled", enabled ? NamedTextColor.GREEN : NamedTextColor.RED));
            lore.add(ml("menu.sources.chance", NamedTextColor.GRAY, "%n%", formatPercent(petSourceChance(source))));
            if (sourceHasOminousBonus(source)) {
                lore.add(ml("menu.sources.ominous", NamedTextColor.DARK_PURPLE, "%n%", formatPercent(petSourceOminousBonus(source))));
            }
            lore.add(Component.empty());
            lore.add(ml("menu.sources.toggle", NamedTextColor.YELLOW));
            lore.add(ml("menu.sources.plus", NamedTextColor.GREEN));
            lore.add(ml("menu.sources.minus", NamedTextColor.RED));
            inventory.setItem(DROP_SLOTS[i], itemFactory.control(
                dropMaterial(source),
                Component.text(dropName(source), enabled ? NamedTextColor.GREEN : NamedTextColor.RED),
                lore
            ));
        }
        inventory.setItem(22, itemFactory.control(
            Material.BARRIER,
            ml("menu.common.close", NamedTextColor.RED),
            List.of(mg("menu.common.saves-instantly"))
        ));
    }

    private void handleDropClick(final InventoryClickEvent event, final DropMenuHolder holder) {
        event.setCancelled(true);
        if (!(event.getWhoClicked() instanceof Player player) || !player.getUniqueId().equals(holder.owner())) {
            return;
        }
        if (!has(player, ADMIN_PERMISSION)) {
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
            case "mythical", "extraordinary" -> {
                sound = Sound.ENTITY_ENDER_DRAGON_GROWL;
                pitch = 1.0F;
            }
            default -> {
                sound = Sound.BLOCK_NOTE_BLOCK_PLING;
                pitch = 1.4F;
            }
        }
        for (final Player online : Bukkit.getOnlinePlayers()) {
            if (storage.data(online.getUniqueId()).broadcastsMuted()) {
                continue;
            }
            online.playSound(online.getLocation(), sound, 1.0F, pitch);
        }
    }

    private List<String> rarityOrder() {
        return List.of("Common", "Rare", "Epic", "Legendary", "Mythical");
    }

    private NamedTextColor rarityColor(final String rarity) {
        return switch (rarity.toLowerCase(Locale.ROOT)) {
            case "rare" -> NamedTextColor.BLUE;
            case "epic" -> NamedTextColor.LIGHT_PURPLE;
            case "legendary" -> NamedTextColor.GOLD;
            case "mythical", "extraordinary" -> NamedTextColor.DARK_PURPLE;
            default -> NamedTextColor.GREEN;
        };
    }

    // ---------------------------------------------------------------------------
    //  Pet tokens (earned by scrapping, spent in the cosmetics shop)
    // ---------------------------------------------------------------------------

    private boolean tokensEnabled() {
        return getConfig().getBoolean("tokens.enabled", true);
    }

    /** Tokens a pet of the given rarity is worth when scrapped. */
    private int tokenValueForRarity(final String rarity) {
        final String key = rarity == null ? "common" : rarity.toLowerCase(Locale.ROOT);
        final String configKey = key.equals("extraordinary") ? "mythical" : key;
        final int fallback = switch (configKey) {
            case "rare" -> 2;
            case "epic" -> 3;
            case "legendary" -> 4;
            case "mythical" -> 5;
            default -> 1;
        };
        return Math.max(0, getConfig().getInt("tokens.per-rarity." + configKey, fallback));
    }

    /** Token value of a single unit of a pet item, or 0 if it is not a scrappable pet item. */
    private int scrapValue(final ItemStack item) {
        return itemFactory.petId(item).flatMap(definitions::get).map(def -> tokenValueForRarity(def.rarity())).orElse(0);
    }

    void handleScrap(final Player player, final String[] args) {
        if (!tokensEnabled()) {
            player.sendMessage(message("tokens.disabled"));
            return;
        }
        final PlayerPetData data = storage.data(player.getUniqueId());

        if (args.length >= 2 && args[1].equalsIgnoreCase("all")) {
            int count = 0;
            int gained = 0;
            final java.util.LinkedHashSet<String> unlocked = new java.util.LinkedHashSet<>();
            final java.util.LinkedHashSet<String> starUps = new java.util.LinkedHashSet<>();
            final ItemStack[] contents = player.getInventory().getStorageContents();
            for (int i = 0; i < contents.length; i++) {
                final ItemStack item = contents[i];
                final int value = scrapValue(item);
                if (item == null || value <= 0) {
                    continue;
                }
                final String unlockedName = tryUnlockScrappedVariant(data, item);
                if (unlockedName != null) {
                    unlocked.add(unlockedName);
                }
                final String itemPetId = itemFactory.petId(item).orElse(null);
                if (fuseDuplicates(data, itemPetId, item.getAmount())) {
                    definitions.get(itemPetId).ifPresent(def -> starUps.add(def.name()));
                }
                gained += value * item.getAmount();
                count += item.getAmount();
                player.getInventory().setItem(i, null);
            }
            if (count == 0) {
                player.sendMessage(message("tokens.scrapped-none"));
                return;
            }
            data.addTokens(gained);
            requestSave();
            player.playSound(player.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 0.8F, 1.2F);
            player.sendMessage(lang.component("tokens.scrapped-all",
                "%count%", Integer.toString(count), "%tokens%", Integer.toString(gained), "%total%", Integer.toString(data.tokens())));
            if (!unlocked.isEmpty()) {
                player.sendMessage(lang.component("customize.unlocked", "%variant%", String.join(", ", unlocked)));
            }
            if (!starUps.isEmpty()) {
                activePets.refreshDisplay(player);
                player.playSound(player.getLocation(), Sound.BLOCK_AMETHYST_BLOCK_CHIME, 1.0F, 1.2F);
                player.sendMessage(lang.component("fusion.star-up-multi", "%pets%", String.join(", ", starUps)));
            }
            return;
        }

        final ItemStack held = player.getInventory().getItemInMainHand();
        final int value = scrapValue(held);
        if (value <= 0) {
            player.sendMessage(message("tokens.scrapped-none"));
            return;
        }
        final int gained = value * held.getAmount();
        final String name = itemFactory.petId(held).flatMap(definitions::get).map(PetDefinition::name).orElse("pet");
        final String heldPetId = itemFactory.petId(held).orElse(null);
        final boolean ownsPet = heldPetId != null && data.pets().stream().anyMatch(p -> p.definitionId().equals(heldPetId));
        final Component unlockMessage = scrapUnlockMessage(data, held);
        final Component fusionMessage = fusionMessage(player, data, held, held.getAmount());
        player.getInventory().setItemInMainHand(null);
        data.addTokens(gained);
        requestSave();
        player.playSound(player.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 0.8F, 1.4F);
        player.sendMessage(lang.component("tokens.scrapped",
            "%pet%", name, "%tokens%", Integer.toString(gained), "%total%", Integer.toString(data.tokens())));
        if (unlockMessage != null) {
            player.sendMessage(unlockMessage);
        }
        if (fusionMessage != null) {
            player.sendMessage(fusionMessage);
        } else if (!ownsPet) {
            // Scrapping a species you don't own grants tokens only — warn that no ascension progress was earned.
            player.sendMessage(lang.component("tokens.scrapped-no-fusion", "%pet%", name));
        }
    }

    /**
     * Builds the skin-collection feedback for a single scrapped pet item: unlocks the variant for the player
     * (regardless of ownership) and returns "unlocked" if it was new, "already have it" otherwise, or null
     * when the pet has no variants / the item carries no variant tag (logged for diagnosis).
     */
    private Component scrapUnlockMessage(final PlayerPetData data, final ItemStack item) {
        final String petId = itemFactory.petId(item).orElse(null);
        final PetDefinition definition = petId == null ? null : definitions.get(petId).orElse(null);
        if (definition == null || !definition.hasVariants()) {
            return null;
        }
        final String variant = itemFactory.resolveVariant(item, definition).orElse(null);
        if (variant == null || !definition.variants().containsKey(variant.toLowerCase(Locale.ROOT))) {
            debug("Scrap: " + petId + " item had no resolvable variant (no tag and no matching head texture).");
            return null;
        }
        final String display = PetDefinition.variantDisplay(variant) + " " + definition.name();
        final boolean isNew = data.unlockVariant(petId, variant);
        debug("Scrap: " + petId + " variant " + variant + " -> " + (isNew ? "unlocked" : "already owned"));
        return lang.component(isNew ? "customize.unlocked" : "customize.already", "%variant%", display);
    }

    /** Adds {@code count} fusion points to the player's owned pet of that type. Returns true if a star was gained. */
    private boolean fuseDuplicates(final PlayerPetData data, final String petId, final int count) {
        if (petId == null || count <= 0) {
            return false;
        }
        final OwnedPet owned = data.pets().stream().filter(pet -> pet.definitionId().equals(petId)).findFirst().orElse(null);
        if (owned == null) {
            return false;
        }
        boolean starUp = false;
        for (int i = 0; i < count; i++) {
            if (owned.addFusionPoint()) {
                starUp = true;
            }
        }
        return starUp;
    }

    /** Applies fusion for a single scrapped stack and returns the star-up or progress message (or null). */
    private Component fusionMessage(final Player player, final PlayerPetData data, final ItemStack item, final int count) {
        final String petId = itemFactory.petId(item).orElse(null);
        if (petId == null) {
            return null;
        }
        final OwnedPet owned = data.pets().stream().filter(pet -> pet.definitionId().equals(petId)).findFirst().orElse(null);
        if (owned == null) {
            return null;
        }
        boolean starUp = false;
        for (int i = 0; i < Math.max(1, count); i++) {
            if (owned.addFusionPoint()) {
                starUp = true;
            }
        }
        final String name = definitions.get(petId).map(PetDefinition::name).orElse(petId);
        if (owned.uuid().equals(data.activePetId())) {
            activePets.refreshDisplay(player);
        }
        if (starUp) {
            player.playSound(player.getLocation(), Sound.BLOCK_AMETHYST_BLOCK_CHIME, 1.0F, 1.2F);
            return lang.component("fusion.star-up", "%pet%", name, "%stars%", "★".repeat(owned.stars()));
        }
        if (owned.pointsForNextStar() > 0) {
            return lang.component("fusion.progress", "%pet%", name,
                "%points%", Integer.toString(owned.fusionPoints()), "%next%", Integer.toString(owned.pointsForNextStar()));
        }
        return null;
    }

    /** Unlocks the scrapped item's variant for the player (regardless of ownership); returns the display name if new. */
    private String tryUnlockScrappedVariant(final PlayerPetData data, final ItemStack item) {
        final String petId = itemFactory.petId(item).orElse(null);
        final PetDefinition definition = petId == null ? null : definitions.get(petId).orElse(null);
        if (definition == null) {
            return null;
        }
        final String variant = itemFactory.resolveVariant(item, definition).orElse(null);
        if (variant == null || !definition.variants().containsKey(variant.toLowerCase(Locale.ROOT))) {
            return null;
        }
        return data.unlockVariant(petId, variant) ? PetDefinition.variantDisplay(variant) + " " + definition.name() : null;
    }

    void handleTokensCommand(final CommandSender sender, final String[] args) {
        if (!tokensEnabled()) {
            sender.sendMessage(message("tokens.disabled"));
            return;
        }
        if (args.length < 2) {
            if (sender instanceof Player player) {
                player.sendMessage(lang.component("tokens.balance", "%total%", Integer.toString(storage.data(player.getUniqueId()).tokens())));
            } else {
                sender.sendMessage(message("messages.only-players"));
            }
            return;
        }
        switch (args[1].toLowerCase(Locale.ROOT)) {
            case "give" -> adminAdjustTokens(sender, args, true);
            case "remove" -> adminAdjustTokens(sender, args, false);
            case "pass" -> passTokens(sender, args);
            default -> sender.sendMessage(lang.colored("tokens.usage", NamedTextColor.YELLOW));
        }
    }

    /** /pets tokens give|remove <player|all> <amount> - admin only. */
    private void adminAdjustTokens(final CommandSender sender, final String[] args, final boolean give) {
        if (!has(sender, ADMIN_PERMISSION)) {
            sender.sendMessage(message("messages.no-permission"));
            return;
        }
        if (args.length < 4) {
            sender.sendMessage(lang.colored("tokens.usage", NamedTextColor.YELLOW));
            return;
        }
        final Optional<Integer> amount = parsePositiveInt(args[3]);
        if (amount.isEmpty()) {
            sender.sendMessage(lang.colored("tokens.invalid-amount", NamedTextColor.RED));
            return;
        }
        final int delta = give ? amount.get() : -amount.get();
        if (args[2].equalsIgnoreCase("all")) {
            for (final Player online : Bukkit.getOnlinePlayers()) {
                storage.data(online.getUniqueId()).addTokens(delta);
            }
            requestSave();
            sender.sendMessage(lang.colored(give ? "tokens.given-all" : "tokens.removed-all", NamedTextColor.GREEN,
                "%amount%", Integer.toString(amount.get())));
            return;
        }
        final OfflinePlayer target = resolveTarget(args[2]);
        if (target == null || target.getName() == null) {
            sender.sendMessage(lang.colored("tokens.player-not-found", NamedTextColor.RED, "%player%", args[2]));
            return;
        }
        final PlayerPetData data = storage.data(target.getUniqueId());
        data.addTokens(delta);
        requestSave();
        sender.sendMessage(lang.colored(give ? "tokens.given" : "tokens.removed", NamedTextColor.GREEN,
            "%amount%", Integer.toString(amount.get()), "%player%", target.getName(), "%total%", Integer.toString(data.tokens())));
        getLogger().info(sender.getName() + (give ? " gave " : " removed ") + amount.get() + " tokens " + (give ? "to " : "from ") + target.getName() + ".");
        if (give && target.getPlayer() != null) {
            target.getPlayer().sendMessage(lang.component("tokens.received-admin", "%amount%", Integer.toString(amount.get())));
        }
    }

    /** /pets tokens pass <player> <amount> - any player can transfer their own tokens. */
    private void passTokens(final CommandSender sender, final String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(message("messages.only-players"));
            return;
        }
        if (args.length < 4) {
            player.sendMessage(lang.colored("tokens.usage", NamedTextColor.YELLOW));
            return;
        }
        final Optional<Integer> amount = parsePositiveInt(args[3]);
        if (amount.isEmpty()) {
            player.sendMessage(lang.colored("tokens.invalid-amount", NamedTextColor.RED));
            return;
        }
        final OfflinePlayer target = resolveTarget(args[2]);
        if (target == null || target.getName() == null) {
            player.sendMessage(lang.colored("tokens.player-not-found", NamedTextColor.RED, "%player%", args[2]));
            return;
        }
        if (target.getUniqueId().equals(player.getUniqueId())) {
            player.sendMessage(lang.colored("tokens.pass-self", NamedTextColor.RED));
            return;
        }
        final PlayerPetData mine = storage.data(player.getUniqueId());
        if (mine.tokens() < amount.get()) {
            player.sendMessage(lang.colored("tokens.pass-not-enough", NamedTextColor.RED, "%total%", Integer.toString(mine.tokens())));
            return;
        }
        mine.addTokens(-amount.get());
        storage.data(target.getUniqueId()).addTokens(amount.get());
        requestSave();
        player.sendMessage(lang.colored("tokens.pass-sent", NamedTextColor.GREEN,
            "%amount%", Integer.toString(amount.get()), "%player%", target.getName(), "%total%", Integer.toString(mine.tokens())));
        if (target.getPlayer() != null) {
            target.getPlayer().sendMessage(lang.component("tokens.pass-received",
                "%amount%", Integer.toString(amount.get()), "%player%", player.getName()));
        }
    }

    private OfflinePlayer resolveTarget(final String name) {
        final Player online = Bukkit.getPlayerExact(name);
        return online != null ? online : Bukkit.getOfflinePlayerIfCached(name);
    }

    private Optional<Integer> parsePositiveInt(final String value) {
        try {
            final int parsed = Integer.parseInt(value.trim());
            return parsed > 0 ? Optional.of(parsed) : Optional.empty();
        } catch (final NumberFormatException ignored) {
            return Optional.empty();
        }
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

        // Require a confirming second click within a few seconds, so a misclick never deletes a pet.
        final long now = System.currentTimeMillis();
        final Long armedUntil = convertConfirms.get(player.getUniqueId());
        if (armedUntil == null || now > armedUntil) {
            convertConfirms.put(player.getUniqueId(), now + CONVERT_CONFIRM_MILLIS);
            player.sendMessage(lang.colored("convert.confirm", NamedTextColor.GOLD, "%pet%", definition.name()));
            player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_HAT, 0.8F, 1.2F);
            return;
        }
        convertConfirms.remove(player.getUniqueId());

        data.removePet(pet.uuid());
        activePets.despawn(player, false);
        giveOrDrop(player, itemFactory.discoveryItem(definition, pet));
        requestSave();
        player.sendMessage(message("messages.converted").replaceText(builder -> builder.matchLiteral("%pet%").replacement(definition.name())));
        player.playSound(player.getLocation(), Sound.ENTITY_ITEM_PICKUP, 0.7F, 1.3F);
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
        final java.util.List<String> stringArgs = new java.util.ArrayList<>();
        for (int i = 2; i < args.length; i++) {
            final Optional<Integer> parsedLevel = parseLevel(args[i]);
            if (parsedLevel.isPresent() && !explicitLevel) {
                level = parsedLevel.get();
                explicitLevel = true;
                continue;
            }
            stringArgs.add(args[i]);
        }
        if (stringArgs.size() > 2) {
            sender.sendMessage(message("messages.usage-give"));
            return;
        }

        // Resolve the definition early so a string arg matching one of its variants is treated as the
        // requested variant; the other string arg (if any) is the target player.
        final String petIdArg = args[1].toLowerCase(Locale.ROOT);
        final PetDefinition variantDefinition = petIdArg.equals("all") ? null : definitions.find(petIdArg).orElse(null);
        String targetName = null;
        String variantArg = null;
        for (final String token : stringArgs) {
            if (variantArg == null && variantDefinition != null
                && variantDefinition.variants().containsKey(token.toLowerCase(Locale.ROOT))) {
                variantArg = token.toLowerCase(Locale.ROOT);
            } else if (targetName == null) {
                targetName = token;
            } else {
                sender.sendMessage(message("messages.usage-give"));
                return;
            }
        }
        final String giftVariant = variantArg;

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
            getLogger().info(sender.getName() + " gave all test pet items level " + giftLevel + " to " + target.getName() + ".");
            return;
        }

        final PetDefinition definition = definitions.find(petId).orElse(null);
        if (definition == null) {
            sender.sendMessage(message("messages.unknown-pet").replaceText(builder -> builder.matchLiteral("%pet%").replacement(petId)));
            return;
        }

        giveOrDrop(target, itemFactory.discoveryItem(definition, giftLevel, giftVariant));
        final String givenName = giftVariant != null
            ? PetDefinition.variantDisplay(giftVariant) + " " + definition.name()
            : definition.name();
        sender.sendMessage(message("messages.test-give")
            .replaceText(builder -> builder.matchLiteral("%pet%").replacement(givenName))
            .replaceText(builder -> builder.matchLiteral("%level%").replacement(Integer.toString(giftLevel)))
            .replaceText(builder -> builder.matchLiteral("%player%").replacement(target.getName())));
        getLogger().info(sender.getName() + " gave test pet item " + definition.id()
            + (giftVariant != null ? " variant " + giftVariant : "") + " level " + giftLevel + " to " + target.getName() + ".");
    }

    private void handleXpBoostCommand(final CommandSender sender, final String[] args) {
        if (!has(sender, GIVE_PERMISSION)) {
            sender.sendMessage(message("messages.no-permission"));
            return;
        }
        if (args.length < 4 || !args[1].equalsIgnoreCase("give")) {
            sender.sendMessage(message("messages.usage-xpboost"));
            return;
        }

        final Optional<Integer> parsedTier = parseBoosterTier(args[2]);
        final Optional<Integer> parsedMinutes = parseDurationMinutes(args[3]);
        if (parsedTier.isEmpty() || parsedMinutes.isEmpty()) {
            sender.sendMessage(message("messages.usage-xpboost"));
            return;
        }

        final int maxMinutes = maxBoosterMinutes();
        final int minutes = parsedMinutes.get();
        if (minutes > maxMinutes) {
            sender.sendMessage(lang.component("messages.booster-capped", "%time%", formatBoosterMinutes(maxMinutes)));
            return;
        }

        final Player target;
        if (args.length >= 5) {
            target = Bukkit.getPlayerExact(args[4]);
            if (target == null) {
                sender.sendMessage(message("messages.player-not-found").replaceText(builder -> builder.matchLiteral("%player%").replacement(args[4])));
                return;
            }
        } else if (sender instanceof Player player) {
            target = player;
        } else {
            sender.sendMessage(message("messages.usage-xpboost"));
            return;
        }

        final int tier = parsedTier.get();
        giveOrDrop(target, itemFactory.boosterItem(tier, minutes));
        sender.sendMessage(message("messages.booster-give")
            .replaceText(builder -> builder.matchLiteral("%tier%").replacement("x" + tier))
            .replaceText(builder -> builder.matchLiteral("%time%").replacement(formatBoosterMinutes(minutes)))
            .replaceText(builder -> builder.matchLiteral("%player%").replacement(target.getName())));
        if (!sender.equals(target)) {
            target.sendMessage(lang.colored("booster.received", NamedTextColor.LIGHT_PURPLE,
                "%tier%", Integer.toString(tier), "%time%", formatBoosterMinutes(minutes)));
        }
        target.playSound(target.getLocation(), Sound.ENTITY_ITEM_PICKUP, 0.8F, 1.4F);
        getLogger().info(sender.getName() + " gave Pet XP Booster x" + tier + " (" + formatBoosterMinutes(minutes) + ") to " + target.getName() + ".");
    }

    private Optional<Integer> parseLevel(final String value) {
        try {
            return Optional.of(Math.max(1, Math.min(100, Integer.parseInt(value))));
        } catch (final NumberFormatException ignored) {
            return Optional.empty();
        }
    }

    private Optional<Integer> parseBoosterTier(final String value) {
        final String normalized = value.toLowerCase(Locale.ROOT).replace("x", "").trim();
        try {
            final int tier = Integer.parseInt(normalized);
            if (tier >= 2 && tier <= 5) {
                return Optional.of(tier);
            }
        } catch (final NumberFormatException ignored) {
        }
        return Optional.empty();
    }

    private Optional<Integer> parseDurationMinutes(final String value) {
        final String normalized = value.toLowerCase(Locale.ROOT).replace(" ", "");
        if (normalized.isBlank()) {
            return Optional.empty();
        }
        if (normalized.matches("\\d+")) {
            try {
                return Optional.of(Math.max(1, Integer.parseInt(normalized)));
            } catch (final NumberFormatException ignored) {
                return Optional.empty();
            }
        }

        final java.util.regex.Matcher matcher = java.util.regex.Pattern.compile("(\\d+)([smhd])").matcher(normalized);
        int consumed = 0;
        long totalSeconds = 0L;
        while (matcher.find()) {
            if (matcher.start() != consumed) {
                return Optional.empty();
            }
            consumed = matcher.end();
            final long amount;
            try {
                amount = Long.parseLong(matcher.group(1));
            } catch (final NumberFormatException ignored) {
                return Optional.empty();
            }
            totalSeconds += switch (matcher.group(2)) {
                case "s" -> amount;
                case "m" -> amount * 60L;
                case "h" -> amount * 3600L;
                case "d" -> amount * 86400L;
                default -> 0L;
            };
            if (totalSeconds > 10080L * 60L) {
                return Optional.empty();
            }
        }
        if (consumed != normalized.length() || totalSeconds <= 0L) {
            return Optional.empty();
        }
        return Optional.of((int) Math.max(1L, (totalSeconds + 59L) / 60L));
    }

    private boolean has(final CommandSender sender, final String permission) {
        return sender.hasPermission(permission) || sender.hasPermission(ADMIN_PERMISSION);
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
        final Inventory inventory = Bukkit.createInventory(holder, 54, Texts.menuTitle(mt("menu.title.catalogue")));
        holder.setInventory(inventory);
        renderInfoMenu(inventory, holder);
        player.openInventory(inventory);
    }

    private void renderInfoMenu(final Inventory inventory, final InfoMenuHolder holder) {
        inventory.clear();
        final List<PetDefinition> all = definitions.ordered();
        final int pages = pageCount(all.size());
        if (holder.page() >= pages) {
            holder.setPage(Math.max(0, pages - 1));
        }
        final int start = holder.page() * PET_SLOT_LIMIT;
        final int visible = Math.max(0, Math.min(PET_SLOT_LIMIT, all.size() - start));
        for (int i = 0; i < visible; i++) {
            final PetDefinition definition = all.get(start + i);
            inventory.setItem(i, itemFactory.infoItem(definition, catalogueLore(definition)));
        }
        inventory.setItem(45, itemFactory.control(
            Material.ARROW,
            ml("menu.common.back", NamedTextColor.YELLOW),
            List.of(mg("menu.catalogue.back-lore"))
        ));
        if (holder.page() > 0) {
            inventory.setItem(48, itemFactory.control(
                Material.SPECTRAL_ARROW,
                ml("menu.common.prev", NamedTextColor.YELLOW),
                List.of(ml("menu.common.page", NamedTextColor.GRAY, "%page%", Integer.toString(holder.page() + 1), "%pages%", Integer.toString(pages)))
            ));
        }
        if (holder.page() + 1 < pages) {
            inventory.setItem(50, itemFactory.control(
                Material.SPECTRAL_ARROW,
                ml("menu.common.next", NamedTextColor.YELLOW),
                List.of(ml("menu.common.page", NamedTextColor.GRAY, "%page%", Integer.toString(holder.page() + 1), "%pages%", Integer.toString(pages)))
            ));
        }
        inventory.setItem(49, itemFactory.control(
            Material.BARRIER,
            ml("menu.common.close", NamedTextColor.RED),
            List.of(mg("menu.catalogue.close-lore"))
        ));
    }

    private void handleInfoClick(final InventoryClickEvent event) {
        event.setCancelled(true);
        if (!(event.getWhoClicked() instanceof Player player)) {
            return;
        }
        final InfoMenuHolder holder = event.getView().getTopInventory().getHolder() instanceof InfoMenuHolder infoHolder ? infoHolder : null;
        final int slot = event.getRawSlot();
        if (slot == 45) {
            openPetsMenu(player);
            return;
        }
        if (slot == 49) {
            player.closeInventory();
            return;
        }
        if (holder != null && (slot == 48 || slot == 50)) {
            final int pages = pageCount(definitions.ordered().size());
            final int target = slot == 48 ? holder.page() - 1 : holder.page() + 1;
            if (target >= 0 && target < pages) {
                holder.setPage(target);
                renderInfoMenu(event.getView().getTopInventory(), holder);
            }
            return;
        }
        itemFactory.petId(event.getCurrentItem())
            .flatMap(definitions::get)
            .ifPresent(definition -> openPetDetailMenu(player, definition));
    }

    private void openPetDetailMenu(final Player player, final PetDefinition definition) {
        final PetDetailMenuHolder holder = new PetDetailMenuHolder(definition.id());
        final Inventory inventory = Bukkit.createInventory(holder, 54, Texts.rarityTitle(definition.name() + " " + mt("menu.title.details-suffix"), definition.rarityColor()));
        holder.setInventory(inventory);

        final ItemStack filler = itemFactory.control(Material.BLACK_STAINED_GLASS_PANE, Component.text(" ", NamedTextColor.DARK_GRAY), List.of());
        for (int i = 0; i < inventory.getSize(); i++) {
            inventory.setItem(i, filler);
        }

        inventory.setItem(4, itemFactory.infoItem(definition, List.of(
            Component.text(abilitySummary(definition.id()), NamedTextColor.GRAY),
            ml("menu.detail.rarity-pet", definition.rarityColor(), "%rarity%", definition.rarity())
        )));

        final int[] slots = {10, 11, 12, 13, 14, 15, 16, 19, 20, 21, 22};
        final int[] levels = {1, 10, 20, 30, 40, 50, 60, 70, 80, 90, 100};
        for (int i = 0; i < levels.length; i++) {
            final int level = levels[i];
            inventory.setItem(slots[i], itemFactory.control(
                level >= 50 ? Material.LIGHT_BLUE_STAINED_GLASS_PANE : Material.LIME_STAINED_GLASS_PANE,
                ml("menu.detail.level", level >= 50 ? NamedTextColor.AQUA : NamedTextColor.GREEN, "%n%", Integer.toString(level)),
                detailLore(definition, level)
            ));
        }

        final Ascension.Track ascTrack = Ascension.track(definition.id());
        final List<Component> ascLore = new ArrayList<>(List.of(
            ml("menu.asc.track-label", NamedTextColor.GRAY).append(Component.text(ascTrack.display(), NamedTextColor.AQUA)),
            Component.text(trackDescription(ascTrack), NamedTextColor.GRAY),
            Component.empty(),
            ml("menu.detail.at-max", NamedTextColor.DARK_GRAY),
            Component.text("• " + trackPerkAtStar(ascTrack, OwnedPet.MAX_STARS), NamedTextColor.AQUA),
            ml("menu.asc.xp", NamedTextColor.GRAY, "%n%", Integer.toString((int) Math.round(OwnedPet.MAX_STARS * ASCENSION_XP_PER_STAR * 100))),
            ml("menu.detail.stronger-ability", NamedTextColor.GRAY)));
        if (activePets.isFlyable(definition.id())) {
            ascLore.add(ml("menu.asc.flight", NamedTextColor.AQUA, "%n%", Integer.toString((int) Math.round(OwnedPet.MAX_STARS * getConfig().getDouble("flight-speed-per-star", 0.08) * 100))));
        }
        ascLore.add(Component.empty());
        ascLore.add(ml("menu.detail.reach-max", NamedTextColor.DARK_GRAY));
        ascLore.add(ml("menu.detail.rightclick-asc", NamedTextColor.LIGHT_PURPLE));
        inventory.setItem(8, itemFactory.control(Material.NETHER_STAR,
            ml("menu.asc.title-item", NamedTextColor.GOLD), ascLore));

        if (definition.hasVariants()) {
            inventory.setItem(45, itemFactory.control(
                Material.ITEM_FRAME,
                ml("menu.detail.view-variants", NamedTextColor.LIGHT_PURPLE, "%n%", Integer.toString(definition.variants().size())),
                List.of(mg("menu.detail.view-variants-lore"))
            ));
        }
        inventory.setItem(49, itemFactory.control(
            Material.ARROW,
            ml("menu.common.back", NamedTextColor.YELLOW),
            List.of(mg("menu.detail.back-lore"))
        ));
        inventory.setItem(53, itemFactory.control(
            Material.BARRIER,
            ml("menu.common.close", NamedTextColor.RED),
            List.of()
        ));
        player.openInventory(inventory);
    }

    private void handlePetDetailClick(final InventoryClickEvent event, final PetDetailMenuHolder holder) {
        event.setCancelled(true);
        if (!(event.getWhoClicked() instanceof Player player)) {
            return;
        }
        if (event.getRawSlot() == 45) {
            definitions.get(holder.petId()).filter(PetDefinition::hasVariants)
                .ifPresent(definition -> openVariantMenu(player, definition, 0));
        } else if (event.getRawSlot() == 49) {
            openInfoMenu(player);
        } else if (event.getRawSlot() == 53) {
            player.closeInventory();
        }
    }

    private void openVariantMenu(final Player player, final PetDefinition definition, final int page) {
        final VariantMenuHolder holder = new VariantMenuHolder(definition.id(), Math.max(0, page));
        final Inventory inventory = Bukkit.createInventory(holder, 54, Texts.rarityTitle(definition.name() + " " + mt("menu.title.variants-suffix"), definition.rarityColor()));
        holder.setInventory(inventory);
        renderVariantMenu(inventory, holder, player);
        player.openInventory(inventory);
    }

    private void renderVariantMenu(final Inventory inventory, final VariantMenuHolder holder, final Player player) {
        inventory.clear();
        final PetDefinition definition = definitions.get(holder.petId()).orElse(null);
        if (definition == null) {
            return;
        }
        final PlayerPetData data = storage.data(player.getUniqueId());
        final List<String> keys = new ArrayList<>(definition.variants().keySet());
        final int perPage = 45;
        final int pages = Math.max(1, (keys.size() + perPage - 1) / perPage);
        if (holder.page() >= pages) {
            holder.setPage(pages - 1);
        }
        final int start = holder.page() * perPage;
        final int visible = Math.max(0, Math.min(perPage, keys.size() - start));
        for (int i = 0; i < visible; i++) {
            final String key = keys.get(start + i);
            final ItemStack icon = itemFactory.variantIcon(definition, key);
            final boolean collected = data.isVariantUnlocked(definition.id(), key);
            icon.editMeta(meta -> {
                if (collected) {
                    meta.setEnchantmentGlintOverride(true);
                }
                final List<Component> lore = new ArrayList<>(meta.lore() == null ? List.of() : meta.lore());
                lore.add((collected
                    ? ml("menu.variants.collected", NamedTextColor.GREEN)
                    : ml("menu.variants.not-collected", NamedTextColor.DARK_GRAY)).decoration(TextDecoration.ITALIC, false));
                meta.lore(lore);
            });
            inventory.setItem(i, icon);
        }
        inventory.setItem(45, itemFactory.control(
            Material.ARROW,
            ml("menu.common.back", NamedTextColor.YELLOW),
            List.of(mg("menu.variants.back-lore"))
        ));
        if (holder.page() > 0) {
            inventory.setItem(48, itemFactory.control(Material.SPECTRAL_ARROW,
                ml("menu.common.prev", NamedTextColor.YELLOW),
                List.of(ml("menu.common.page", NamedTextColor.GRAY, "%page%", Integer.toString(holder.page() + 1), "%pages%", Integer.toString(pages)))));
        }
        if (holder.page() + 1 < pages) {
            inventory.setItem(50, itemFactory.control(Material.SPECTRAL_ARROW,
                ml("menu.common.next", NamedTextColor.YELLOW),
                List.of(ml("menu.common.page", NamedTextColor.GRAY, "%page%", Integer.toString(holder.page() + 1), "%pages%", Integer.toString(pages)))));
        }
        inventory.setItem(49, itemFactory.control(Material.BARRIER, ml("menu.common.close", NamedTextColor.RED), List.of()));
    }

    private void handleVariantClick(final InventoryClickEvent event, final VariantMenuHolder holder) {
        event.setCancelled(true);
        if (!(event.getWhoClicked() instanceof Player player)) {
            return;
        }
        final int slot = event.getRawSlot();
        if (slot == 45) {
            definitions.get(holder.petId()).ifPresent(definition -> openPetDetailMenu(player, definition));
            return;
        }
        if (slot == 49) {
            player.closeInventory();
            return;
        }
        if (slot == 48 || slot == 50) {
            final PetDefinition definition = definitions.get(holder.petId()).orElse(null);
            if (definition == null) {
                return;
            }
            final int pages = Math.max(1, (definition.variants().size() + 44) / 45);
            final int target = slot == 48 ? holder.page() - 1 : holder.page() + 1;
            if (target >= 0 && target < pages) {
                holder.setPage(target);
                renderVariantMenu(event.getView().getTopInventory(), holder, player);
            }
        }
    }

    // ===== Cosmetics Shop (replaces the slot machine) ==========================================

    private static final int SHOP_PER_PAGE = 36;

    private int shopPrice(final String key, final int def) {
        return Math.max(1, getConfig().getInt("shop.price." + key, def));
    }

    public void openShopMenu(final Player player, final String category, final int page) {
        if (!tokensEnabled()) {
            player.sendMessage(message("tokens.disabled"));
            return;
        }
        final ShopMenuHolder holder = new ShopMenuHolder(category, Math.max(0, page));
        final Inventory inventory = Bukkit.createInventory(holder, 54, Texts.menuTitle(mt("menu.title.shop")));
        holder.setInventory(inventory);
        renderShopMenu(inventory, holder, player);
        player.openInventory(inventory);
    }

    private void renderShopMenu(final Inventory inventory, final ShopMenuHolder holder, final Player player) {
        inventory.clear();
        final PlayerPetData data = storage.data(player.getUniqueId());
        inventory.setItem(4, itemFactory.control(Material.GOLD_INGOT,
            ml("menu.shop.tokens", NamedTextColor.GOLD, "%n%", Integer.toString(data.tokens())),
            List.of(mg("menu.shop.tokens-lore"))));

        switch (holder.category()) {
            case "particle" -> renderShopList(inventory, holder, player, "particle");
            case "trail" -> renderShopList(inventory, holder, player, "trail");
            case "nametag" -> renderShopList(inventory, holder, player, "nametag");
            case "booster" -> renderShopBoosters(inventory);
            default -> {
                inventory.setItem(20, itemFactory.control(shopDye("aqua"),
                    ml("menu.shop.particles", NamedTextColor.AQUA),
                    List.of(mg("menu.shop.particles-lore"),
                        ml("menu.shop.price-each", NamedTextColor.GOLD, "%n%", Integer.toString(shopPrice("particle", 10))))));
                inventory.setItem(22, itemFactory.control(Material.FIREWORK_STAR,
                    ml("menu.shop.trails", NamedTextColor.LIGHT_PURPLE),
                    List.of(mg("menu.shop.trails-lore"),
                        ml("menu.shop.price-each", NamedTextColor.GOLD, "%n%", Integer.toString(shopPrice("trail", 15))))));
                inventory.setItem(24, itemFactory.control(Material.NAME_TAG,
                    ml("menu.shop.nametags", NamedTextColor.YELLOW),
                    List.of(mg("menu.shop.nametags-lore"),
                        ml("menu.shop.price-each", NamedTextColor.GOLD, "%n%", Integer.toString(shopPrice("nametag", 20))))));
                inventory.setItem(30, itemFactory.control(Material.EXPERIENCE_BOTTLE,
                    ml("menu.shop.boosters", NamedTextColor.GREEN),
                    List.of(mg("menu.shop.boosters-lore"))));
                inventory.setItem(32, itemFactory.control(Material.PLAYER_HEAD,
                    ml("menu.shop.skins", NamedTextColor.DARK_AQUA),
                    List.of(mg("menu.shop.skins-lore1"), mg("menu.shop.skins-lore2"))));
            }
        }
        if (!holder.category().equals("main")) {
            final int pages = shopPageCount(holder.category());
            inventory.setItem(45, itemFactory.control(Material.ARROW,
                ml("menu.common.back", NamedTextColor.YELLOW), List.of(mg("menu.shop.back-lore"))));
            if (holder.page() > 0) {
                inventory.setItem(48, itemFactory.control(Material.SPECTRAL_ARROW,
                    ml("menu.common.prev", NamedTextColor.YELLOW),
                    List.of(ml("menu.common.page", NamedTextColor.GRAY, "%page%", Integer.toString(holder.page() + 1), "%pages%", Integer.toString(pages)))));
            }
            if (holder.page() + 1 < pages) {
                inventory.setItem(50, itemFactory.control(Material.SPECTRAL_ARROW,
                    ml("menu.common.next", NamedTextColor.YELLOW),
                    List.of(ml("menu.common.page", NamedTextColor.GRAY, "%page%", Integer.toString(holder.page() + 1), "%pages%", Integer.toString(pages)))));
            }
        }
        inventory.setItem(49, itemFactory.control(Material.BARRIER, ml("menu.common.close", NamedTextColor.RED), List.of()));
    }

    private int shopPageCount(final String category) {
        final int size = switch (category) {
            case "particle" -> Cosmetics.particleColors().size();
            case "trail" -> Cosmetics.trails().size();
            case "nametag" -> Cosmetics.nametagStyles().size();
            default -> 0;
        };
        return Math.max(1, (size + SHOP_PER_PAGE - 1) / SHOP_PER_PAGE);
    }

    private List<String> shopIds(final String category) {
        return switch (category) {
            case "particle" -> new ArrayList<>(Cosmetics.particleColors().keySet());
            case "trail" -> new ArrayList<>(Cosmetics.trails().keySet());
            case "nametag" -> new ArrayList<>(Cosmetics.nametagStyles().keySet());
            default -> List.of();
        };
    }

    private void renderShopList(final Inventory inventory, final ShopMenuHolder holder, final Player player, final String category) {
        final PlayerPetData data = storage.data(player.getUniqueId());
        final List<String> ids = shopIds(category);
        final int price = shopPrice(category, category.equals("particle") ? 10 : category.equals("trail") ? 15 : 20);
        final int start = holder.page() * SHOP_PER_PAGE;
        for (int i = 0; i < SHOP_PER_PAGE && start + i < ids.size(); i++) {
            final String id = ids.get(start + i);
            final boolean owned = data.hasCosmetic(category, id);
            inventory.setItem(9 + i, shopIcon(category, id, price, owned));
        }
    }

    private void renderShopBoosters(final Inventory inventory) {
        final int[] tiers = {2, 3, 4, 5};
        final int[] slots = {19, 21, 23, 25};
        for (int i = 0; i < tiers.length; i++) {
            final int tier = tiers[i];
            final int price = shopPrice("booster-x" + tier, 15 * (tier - 1));
            inventory.setItem(slots[i], itemFactory.control(Material.EXPERIENCE_BOTTLE,
                ml("menu.shop.booster-name", NamedTextColor.LIGHT_PURPLE, "%tier%", Integer.toString(tier)),
                List.of(ml("menu.shop.booster-duration", NamedTextColor.GRAY, "%min%", Integer.toString(getConfig().getInt("shop.booster-minutes", 30))),
                    ml("menu.shop.booster-buy", NamedTextColor.GOLD, "%price%", Integer.toString(price)))));
        }
    }

    private ItemStack shopIcon(final String category, final String id, final int price, final boolean owned) {
        final Component name;
        final Material material;
        if (category.equals("particle")) {
            final Cosmetics.ParticleColor pc = Cosmetics.particleColor(id);
            if (pc != null && pc.kind() == Cosmetics.ParticleColor.Kind.RAINBOW) {
                material = Material.NETHER_STAR;
                name = Texts.rainbow(pc.display());
            } else if (pc != null && pc.kind() == Cosmetics.ParticleColor.Kind.TRANSITION) {
                material = Material.FIREWORK_STAR;
                name = Component.text(pc.display(), NamedTextColor.AQUA);
            } else {
                material = shopDye(id);
                name = Component.text(pc == null ? id : pc.display(), NamedTextColor.AQUA);
            }
        } else if (category.equals("trail")) {
            final Cosmetics.Trail t = Cosmetics.trail(id);
            material = Material.FIREWORK_STAR;
            name = Component.text(t == null ? id : t.display(), NamedTextColor.LIGHT_PURPLE);
        } else {
            final Cosmetics.NametagStyle ns = Cosmetics.nametagStyle(id);
            material = ns != null && ns.rainbow() ? Material.NETHER_STAR : Material.NAME_TAG;
            name = ns == null ? Component.text(id)
                : ns.rainbow() ? Texts.rainbow(ns.display()) : Texts.gradient(ns.display(), ns.from(), ns.to());
        }
        final List<Component> lore = new ArrayList<>();
        if (owned) {
            lore.add(ml("menu.shop.owned", NamedTextColor.GREEN));
            lore.add(mg("menu.shop.owned-lore"));
        } else {
            lore.add(ml("menu.shop.buy", NamedTextColor.GOLD, "%price%", Integer.toString(price)));
        }
        final ItemStack icon = itemFactory.control(material, name, lore);
        if (owned) {
            icon.editMeta(meta -> meta.setEnchantmentGlintOverride(true));
        }
        return icon;
    }

    private Material shopDye(final String colorId) {
        return switch (colorId) {
            case "red", "crimson" -> Material.RED_DYE;
            case "orange", "amber" -> Material.ORANGE_DYE;
            case "yellow", "gold" -> Material.YELLOW_DYE;
            case "lime", "mint", "emerald" -> Material.LIME_DYE;
            case "green" -> Material.GREEN_DYE;
            case "aqua", "teal" -> Material.CYAN_DYE;
            case "cyan", "sky" -> Material.LIGHT_BLUE_DYE;
            case "blue" -> Material.BLUE_DYE;
            case "purple", "indigo", "violet" -> Material.PURPLE_DYE;
            case "magenta" -> Material.MAGENTA_DYE;
            case "pink", "rose" -> Material.PINK_DYE;
            case "brown" -> Material.BROWN_DYE;
            case "gray" -> Material.GRAY_DYE;
            case "black" -> Material.BLACK_DYE;
            case "white" -> Material.WHITE_DYE;
            default -> Material.GLOWSTONE_DUST;
        };
    }

    private void handleShopClick(final InventoryClickEvent event, final ShopMenuHolder holder) {
        event.setCancelled(true);
        if (!(event.getWhoClicked() instanceof Player player)) {
            return;
        }
        final int slot = event.getRawSlot();
        if (slot == 49) {
            player.closeInventory();
            return;
        }
        if (holder.category().equals("main")) {
            final String target = switch (slot) {
                case 20 -> "particle";
                case 22 -> "trail";
                case 24 -> "nametag";
                case 30 -> "booster";
                default -> null;
            };
            if (target != null) {
                holder.setCategory(target);
                holder.setPage(0);
                renderShopMenu(event.getView().getTopInventory(), holder, player);
            }
            return;
        }
        if (slot == 45) {
            holder.setCategory("main");
            holder.setPage(0);
            renderShopMenu(event.getView().getTopInventory(), holder, player);
            return;
        }
        if (slot == 48 || slot == 50) {
            final int pages = shopPageCount(holder.category());
            final int targetPage = slot == 48 ? holder.page() - 1 : holder.page() + 1;
            if (targetPage >= 0 && targetPage < pages) {
                holder.setPage(targetPage);
                renderShopMenu(event.getView().getTopInventory(), holder, player);
            }
            return;
        }
        if (holder.category().equals("booster")) {
            final int idx = switch (slot) {
                case 19 -> 0;
                case 21 -> 1;
                case 23 -> 2;
                case 25 -> 3;
                default -> -1;
            };
            if (idx >= 0) {
                buyBooster(player, idx + 2);
                renderShopMenu(event.getView().getTopInventory(), holder, player);
            }
            return;
        }
        if (slot >= 9 && slot < 45) {
            final List<String> ids = shopIds(holder.category());
            final int idx = holder.page() * SHOP_PER_PAGE + (slot - 9);
            if (idx >= 0 && idx < ids.size()) {
                buyCosmetic(player, holder.category(), ids.get(idx));
                renderShopMenu(event.getView().getTopInventory(), holder, player);
            }
        }
    }

    private void buyCosmetic(final Player player, final String category, final String id) {
        final PlayerPetData data = storage.data(player.getUniqueId());
        final String display = switch (category) {
            case "particle" -> Cosmetics.particleColor(id) != null ? Cosmetics.particleColor(id).display() : id;
            case "trail" -> Cosmetics.trail(id) != null ? Cosmetics.trail(id).display() : id;
            case "nametag" -> Cosmetics.nametagStyle(id) != null ? Cosmetics.nametagStyle(id).display() : id;
            default -> id;
        };
        if (data.hasCosmetic(category, id)) {
            player.sendMessage(lang.component("shop.already-owned", "%item%", display));
            return;
        }
        final int price = shopPrice(category, category.equals("particle") ? 10 : category.equals("trail") ? 15 : 20);
        if (data.tokens() < price) {
            player.sendMessage(lang.component("shop.not-enough", "%price%", Integer.toString(price), "%total%", Integer.toString(data.tokens())));
            player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_BASS, 0.7F, 0.8F);
            return;
        }
        data.setTokens(data.tokens() - price);
        data.unlockCosmetic(category, id);
        requestSave();
        player.playSound(player.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 0.7F, 1.4F);
        player.sendMessage(lang.component("shop.bought", "%item%", display, "%price%", Integer.toString(price)));
    }

    private void buyBooster(final Player player, final int tier) {
        final PlayerPetData data = storage.data(player.getUniqueId());
        final int price = shopPrice("booster-x" + tier, 15 * (tier - 1));
        if (data.tokens() < price) {
            player.sendMessage(lang.component("shop.not-enough", "%price%", Integer.toString(price), "%total%", Integer.toString(data.tokens())));
            player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_BASS, 0.7F, 0.8F);
            return;
        }
        data.setTokens(data.tokens() - price);
        requestSave();
        giveOrDrop(player, itemFactory.boosterItem(tier, getConfig().getInt("shop.booster-minutes", 30)));
        player.playSound(player.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 0.7F, 1.4F);
        player.sendMessage(lang.component("shop.bought", "%item%", "XP Booster x" + tier, "%price%", Integer.toString(price)));
    }

    private void openAscensionMenu(final Player player, final OwnedPet pet) {
        final PetDefinition definition = definitions.get(pet.definitionId()).orElse(null);
        if (definition == null) {
            return;
        }
        final AscensionMenuHolder holder = new AscensionMenuHolder(pet.uuid());
        final Inventory inventory = Bukkit.createInventory(holder, 54, Texts.title("✦ " + definition.name() + " " + mt("menu.title.ascension-suffix") + " ✦",
            net.kyori.adventure.text.format.TextColor.color(0x9B5CFF), net.kyori.adventure.text.format.TextColor.color(0xE100FF)));
        holder.setInventory(inventory);
        renderAscensionMenu(inventory, holder, player);
        player.openInventory(inventory);
        // Galactic "whoosh" when the ascension GUI opens.
        player.playSound(player.getLocation(), Sound.BLOCK_BEACON_ACTIVATE, 0.5F, 1.6F);
        player.playSound(player.getLocation(), Sound.BLOCK_AMETHYST_BLOCK_CHIME, 0.4F, 0.8F);
    }

    private void renderAscensionMenu(final Inventory inventory, final AscensionMenuHolder holder, final Player player) {
        inventory.clear();
        final PlayerPetData data = storage.data(player.getUniqueId());
        final OwnedPet pet = data.findPet(holder.petUuid()).orElse(null);
        if (pet == null) {
            return;
        }
        final PetDefinition definition = definitions.get(pet.definitionId()).orElse(null);
        if (definition == null) {
            return;
        }
        // Galactic checkered backdrop (purple/black glass "starfield").
        final ItemStack purple = itemFactory.control(Material.PURPLE_STAINED_GLASS_PANE, Component.text(" ", NamedTextColor.DARK_GRAY), List.of());
        final ItemStack black = itemFactory.control(Material.BLACK_STAINED_GLASS_PANE, Component.text(" ", NamedTextColor.DARK_GRAY), List.of());
        for (int i = 0; i < inventory.getSize(); i++) {
            inventory.setItem(i, ((i + i / 9) % 2 == 0) ? black : purple);
        }
        final Ascension.Track track = Ascension.track(definition.id());
        inventory.setItem(13, itemFactory.menuItem(definition, pet, pet.uuid().equals(data.activePetId())));
        // Five star stages, centred in the row.
        final int[] slots = {29, 30, 31, 32, 33};
        // Base ability tier at this level (no star bonus); stars raise it but never past MAX_TIER.
        final int baseTier = PetAbilities.tier(pet.level());
        for (int n = 1; n <= OwnedPet.MAX_STARS; n++) {
            final boolean reached = pet.stars() >= n;
            final boolean current = pet.stars() == n;
            final NamedTextColor titleColor = n >= OwnedPet.MAX_STARS ? NamedTextColor.WHITE : reached ? NamedTextColor.GOLD : NamedTextColor.DARK_GRAY;
            final List<Component> lore = new ArrayList<>();
            lore.add(current ? ml("menu.asc.here", NamedTextColor.WHITE)
                : reached ? ml("menu.asc.reached", NamedTextColor.GREEN)
                : ml("menu.asc.locked", NamedTextColor.GRAY, "%n%", Integer.toString(n)));
            lore.add(Component.empty());
            lore.add(ml("menu.asc.bonus-header", NamedTextColor.DARK_GRAY));
            lore.add(Component.text("• " + trackPerkAtStar(track, n), NamedTextColor.AQUA));
            if (activePets.isFlyable(definition.id())) {
                lore.add(ml("menu.asc.flight", NamedTextColor.AQUA, "%n%", Integer.toString((int) Math.round(n * getConfig().getDouble("flight-speed-per-star", 0.08) * 100))));
            }
            lore.add(ml("menu.asc.xp", NamedTextColor.GRAY, "%n%", Integer.toString((int) Math.round(n * ASCENSION_XP_PER_STAR * 100))));
            final int abilityGain = Math.min(PetAbilities.MAX_TIER, baseTier + n) - baseTier;
            lore.add(abilityGain > 0
                ? ml("menu.asc.ability", NamedTextColor.GRAY, "%n%", Integer.toString(abilityGain))
                : ml("menu.asc.ability-capped", NamedTextColor.DARK_GRAY));
            final ItemStack node = itemFactory.control(reached ? Material.NETHER_STAR : Material.GRAY_STAINED_GLASS_PANE,
                ml("menu.asc.star", titleColor, "%stars%", "★".repeat(n) + "☆".repeat(OwnedPet.MAX_STARS - n), "%n%", Integer.toString(n)), lore);
            if (current) {
                node.editMeta(meta -> meta.setEnchantmentGlintOverride(true));
            }
            inventory.setItem(slots[n - 1], node);
        }
        final Component progress = pet.stars() >= OwnedPet.MAX_STARS
            ? ml("menu.asc.fully", NamedTextColor.GRAY)
            : ml("menu.asc.progress", NamedTextColor.GRAY, "%next%", Integer.toString(pet.pointsForNextStar()), "%done%", Integer.toString(pet.fusionPoints()));
        final List<Component> headerLore = new ArrayList<>(List.of(
            ml("menu.asc.current", NamedTextColor.GRAY).append(Component.text("★".repeat(pet.stars()) + "☆".repeat(OwnedPet.MAX_STARS - pet.stars()),
                pet.stars() >= OwnedPet.MAX_STARS ? NamedTextColor.WHITE : NamedTextColor.GOLD)),
            progress,
            Component.empty(),
            ml("menu.asc.track-label", NamedTextColor.GRAY).append(Component.text(track.display(), NamedTextColor.AQUA)),
            ml("menu.track.desc." + track.name().toLowerCase(java.util.Locale.ROOT), NamedTextColor.DARK_GRAY),
            ml("menu.asc.ability-now", NamedTextColor.GRAY, "%stars%", Integer.toString(pet.stars()))
                .append(Component.text(PetAbilities.value(definition.id(), pet.level(), pet.stars()), NamedTextColor.YELLOW))));
        if (activePets.isFlyable(definition.id())) {
            final int flightPct = (int) Math.round(pet.stars() * getConfig().getDouble("flight-speed-per-star", 0.08) * 100);
            headerLore.add(ml("menu.asc.flight-header", NamedTextColor.GRAY)
                .append(Component.text("+" + flightPct + "%", NamedTextColor.AQUA)).append(ml("menu.asc.flight-note", NamedTextColor.DARK_GRAY)));
        }
        headerLore.add(Component.empty());
        headerLore.add(ml("menu.asc.footer", NamedTextColor.DARK_GRAY));
        inventory.setItem(4, itemFactory.control(Material.NETHER_STAR,
            Texts.gradient(mt("menu.asc.title-item"), net.kyori.adventure.text.format.TextColor.color(0xFFD54F), net.kyori.adventure.text.format.TextColor.color(0xFFFFFF)),
            headerLore));
        inventory.setItem(49, itemFactory.control(Material.BARRIER,
            ml("menu.common.back", NamedTextColor.RED), List.of(mg("menu.picker.back-lore"))));
    }

    /** One-line plain-language description of what an ascension track does, for menu tooltips. */
    private String trackDescription(final Ascension.Track track) {
        return mt("menu.track.desc." + track.name().toLowerCase(java.util.Locale.ROOT));
    }

    /** The concrete, cumulative track perk a pet has at a given star count — shown per stage node so the scaling is obvious. */
    private String trackPerkAtStar(final Ascension.Track track, final int n) {
        return switch (track) {
            case WARRIOR -> mt("menu.track.perk.warrior", "%n%", Integer.toString(n * 3));
            case GUARDIAN -> mt("menu.track.perk.guardian", "%n%", Integer.toString(n * 3));
            case GATHERER -> mt("menu.track.perk.gatherer", "%n%", Integer.toString(n * 6));
            case RUNNER -> mt("menu.track.perk.runner", "%n%", Integer.toString(Math.min(90, n * 18)))
                + (n >= 3 ? mt("menu.track.extra.speed") : "");
            case MYSTIC -> mt("menu.track.perk.mystic", "%level%", new String[]{"I", "II", "III"}[Math.min(2, (n - 1) / 2)])
                + (n >= 4 ? mt("menu.track.extra.regen") : "");
            case AQUATIC -> mt("menu.track.perk.aquatic", "%n%", Integer.toString(n * 8))
                + (n >= 3 ? mt("menu.track.extra.water") : "");
        };
    }

    private void handleAscensionClick(final InventoryClickEvent event, final AscensionMenuHolder holder) {
        event.setCancelled(true);
        if (!(event.getWhoClicked() instanceof Player player)) {
            return;
        }
        if (event.getRawSlot() == 49) {
            storage.data(player.getUniqueId()).findPet(holder.petUuid())
                .ifPresentOrElse(pet -> openCustomizeMenu(player, pet), player::closeInventory);
        }
    }

    // ===== Leaderboard (/pets top) ==============================================================

    private static final int[] LEADERBOARD_SLOTS = {10, 11, 12, 13, 14, 19, 20, 21, 22, 23};

    void openLeaderboardMenu(final Player player, final String category) {
        final LeaderboardMenuHolder holder = new LeaderboardMenuHolder(player.getUniqueId(), category);
        final Inventory inventory = Bukkit.createInventory(holder, 54, Texts.menuTitle(mt("menu.title.leaderboard")));
        holder.setInventory(inventory);
        renderLeaderboardMenu(inventory, holder, player);
        player.openInventory(inventory);
        player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 0.6F, 1.2F);
    }

    private record LeaderRow(UUID id, String name, int value) {
    }

    /** All known players ranked (descending) by the given category, skipping zero-value entries. */
    private List<LeaderRow> leaderboard(final String category) {
        final List<LeaderRow> rows = new ArrayList<>();
        for (final Map.Entry<UUID, PlayerPetData> entry : storage.entries()) {
            final PlayerPetData data = entry.getValue();
            final int value = switch (category) {
                case "stars" -> data.totalStars();
                case "tokens" -> data.tokens();
                default -> data.pets().size();
            };
            if (value <= 0) {
                continue;
            }
            final String name = data.playerName() != null ? data.playerName()
                : java.util.Optional.ofNullable(Bukkit.getOfflinePlayer(entry.getKey()).getName())
                    .orElse(entry.getKey().toString().substring(0, 8));
            rows.add(new LeaderRow(entry.getKey(), name, value));
        }
        rows.sort((a, b) -> Integer.compare(b.value(), a.value()));
        return rows;
    }

    private String leaderboardUnit(final String category) {
        return switch (category) {
            case "stars" -> mt("menu.top.unit.stars");
            case "tokens" -> mt("menu.top.unit.tokens");
            default -> mt("menu.top.unit.pets");
        };
    }

    private void renderLeaderboardMenu(final Inventory inventory, final LeaderboardMenuHolder holder, final Player viewer) {
        final ItemStack filler = itemFactory.control(Material.BLACK_STAINED_GLASS_PANE, Component.text(" ", NamedTextColor.DARK_GRAY), List.of());
        for (int i = 0; i < inventory.getSize(); i++) {
            inventory.setItem(i, filler);
        }
        final String category = holder.category();
        inventory.setItem(2, leaderboardCategoryButton("pets", "menu.top.cat.pets", Material.BONE, category));
        inventory.setItem(4, leaderboardCategoryButton("stars", "menu.top.cat.stars", Material.NETHER_STAR, category));
        inventory.setItem(6, leaderboardCategoryButton("tokens", "menu.top.cat.tokens", Material.SUNFLOWER, category));

        final List<LeaderRow> rows = leaderboard(category);
        final String unit = leaderboardUnit(category);
        for (int i = 0; i < LEADERBOARD_SLOTS.length; i++) {
            if (i >= rows.size()) {
                inventory.setItem(LEADERBOARD_SLOTS[i], filler);
                continue;
            }
            final LeaderRow row = rows.get(i);
            inventory.setItem(LEADERBOARD_SLOTS[i], leaderboardHead(i + 1, row, unit, row.id().equals(viewer.getUniqueId())));
        }

        int myRank = -1;
        int myValue = 0;
        for (int i = 0; i < rows.size(); i++) {
            if (rows.get(i).id().equals(viewer.getUniqueId())) {
                myRank = i + 1;
                myValue = rows.get(i).value();
                break;
            }
        }
        inventory.setItem(40, itemFactory.control(Material.NAME_TAG,
            ml("menu.top.your-rank", NamedTextColor.AQUA),
            List.of(myRank > 0
                ? ml("menu.top.rank-line", NamedTextColor.GOLD, "%rank%", Integer.toString(myRank), "%value%", Integer.toString(myValue), "%unit%", unit)
                : ml("menu.top.unranked", NamedTextColor.GRAY))));
        inventory.setItem(49, itemFactory.control(Material.BARRIER, ml("menu.common.close", NamedTextColor.RED), List.of()));
    }

    private ItemStack leaderboardCategoryButton(final String key, final String labelKey, final Material icon, final String active) {
        final boolean on = key.equals(active);
        final ItemStack item = itemFactory.control(icon, ml(labelKey, on ? NamedTextColor.GREEN : NamedTextColor.GRAY),
            List.of(on ? ml("menu.top.showing", NamedTextColor.DARK_GRAY)
                : ml("menu.top.click-view", NamedTextColor.DARK_GRAY)));
        if (on) {
            item.editMeta(meta -> meta.setEnchantmentGlintOverride(true));
        }
        return item;
    }

    private ItemStack leaderboardHead(final int rank, final LeaderRow row, final String unit, final boolean isViewer) {
        final ItemStack head = new ItemStack(Material.PLAYER_HEAD);
        final NamedTextColor color = switch (rank) {
            case 1 -> NamedTextColor.GOLD;
            case 2 -> NamedTextColor.WHITE;
            case 3 -> NamedTextColor.YELLOW;
            default -> NamedTextColor.GRAY;
        };
        head.editMeta(org.bukkit.inventory.meta.SkullMeta.class, meta -> {
            meta.setOwningPlayer(Bukkit.getOfflinePlayer(row.id()));
            meta.displayName(Component.text("#" + rank + "  " + row.name() + (isViewer ? mt("menu.top.you") : ""), color)
                .decoration(TextDecoration.ITALIC, false));
            meta.lore(List.of(Component.text(row.value() + " " + unit, NamedTextColor.AQUA).decoration(TextDecoration.ITALIC, false)));
        });
        return head;
    }

    private void handleLeaderboardClick(final InventoryClickEvent event, final LeaderboardMenuHolder holder) {
        event.setCancelled(true);
        if (!(event.getWhoClicked() instanceof Player player)) {
            return;
        }
        final int slot = event.getRawSlot();
        if (slot == 49) {
            player.closeInventory();
            return;
        }
        final String newCategory = switch (slot) {
            case 2 -> "pets";
            case 4 -> "stars";
            case 6 -> "tokens";
            default -> null;
        };
        if (newCategory != null && !newCategory.equals(holder.category())) {
            holder.setCategory(newCategory);
            renderLeaderboardMenu(event.getView().getTopInventory(), holder, player);
            player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 0.6F, 1.4F);
        }
    }

    // ===== Player-to-player trading (/pets trade) ===============================================

    private record TradeRequest(UUID requester, long expiresAt) {
    }

    // Item display slots per side, and the control/confirm slots.
    private static final int[] TRADE_INIT_ITEM_SLOTS = {10, 11, 12, 19, 20, 21};
    private static final int[] TRADE_PART_ITEM_SLOTS = {14, 15, 16, 23, 24, 25};
    private static final int TRADE_INIT_ADD = 28;
    private static final int TRADE_INIT_TOKENS = 29;
    private static final int TRADE_INIT_CLEAR = 30;
    private static final int TRADE_INIT_CONFIRM = 45;
    private static final int TRADE_PART_ADD = 34;
    private static final int TRADE_PART_TOKENS = 33;
    private static final int TRADE_PART_CLEAR = 32;
    private static final int TRADE_PART_CONFIRM = 53;
    private static final int TRADE_CLOSE = 49;

    void handleTradeCommand(final Player player, final String[] args) {
        if (!tokensEnabled()) {
            player.sendMessage(message("tokens.disabled"));
            return;
        }
        if (args.length < 2) {
            player.sendMessage(lang.colored("trade.usage", NamedTextColor.GRAY));
            return;
        }
        final String sub = args[1].toLowerCase(java.util.Locale.ROOT);
        if (sub.equals("accept")) {
            final TradeRequest request = tradeRequests.remove(player.getUniqueId());
            if (request == null || request.expiresAt() < System.currentTimeMillis()) {
                player.sendMessage(lang.colored("trade.none-pending", NamedTextColor.RED));
                return;
            }
            final Player requester = Bukkit.getPlayer(request.requester());
            if (requester == null) {
                player.sendMessage(lang.colored("trade.partner-offline", NamedTextColor.RED));
                return;
            }
            openTradeMenu(requester, player);
            return;
        }
        if (sub.equals("deny") || sub.equals("decline")) {
            final TradeRequest request = tradeRequests.remove(player.getUniqueId());
            if (request != null) {
                final Player requester = Bukkit.getPlayer(request.requester());
                if (requester != null) {
                    requester.sendMessage(lang.colored("trade.denied", NamedTextColor.YELLOW, "%player%", player.getName()));
                }
            }
            player.sendMessage(lang.colored("trade.deny-done", NamedTextColor.GRAY));
            return;
        }
        final Player target = Bukkit.getPlayerExact(args[1]);
        if (target == null) {
            player.sendMessage(lang.colored("tokens.player-not-found", NamedTextColor.RED, "%player%", args[1]));
            return;
        }
        if (target.getUniqueId().equals(player.getUniqueId())) {
            player.sendMessage(lang.colored("trade.self", NamedTextColor.RED));
            return;
        }
        tradeRequests.put(target.getUniqueId(), new TradeRequest(player.getUniqueId(), System.currentTimeMillis() + TRADE_REQUEST_TIMEOUT_MILLIS));
        player.sendMessage(lang.colored("trade.sent", NamedTextColor.GREEN, "%player%", target.getName()));
        target.sendMessage(lang.colored("trade.received", NamedTextColor.AQUA, "%player%", player.getName()));
        target.sendMessage(lang.colored("trade.received-hint", NamedTextColor.GRAY, "%player%", player.getName()));
        target.playSound(target.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 0.7F, 1.6F);
    }

    private void openTradeMenu(final Player initiator, final Player partner) {
        final TradeMenuHolder holder = new TradeMenuHolder(initiator.getUniqueId(), partner.getUniqueId());
        final Inventory inventory = Bukkit.createInventory(holder, 54, Texts.menuTitle(mt("menu.title.trade")));
        holder.setInventory(inventory);
        renderTradeMenu(holder);
        // Both players view the SAME inventory instance, so every change stays in sync between them.
        initiator.openInventory(inventory);
        partner.openInventory(inventory);
        initiator.playSound(initiator.getLocation(), Sound.UI_BUTTON_CLICK, 0.6F, 1.2F);
        partner.playSound(partner.getLocation(), Sound.UI_BUTTON_CLICK, 0.6F, 1.2F);
    }

    private void renderTradeMenu(final TradeMenuHolder holder) {
        final Inventory inv = holder.getInventory();
        final ItemStack filler = itemFactory.control(Material.BLACK_STAINED_GLASS_PANE, Component.text(" ", NamedTextColor.DARK_GRAY), List.of());
        final ItemStack divider = itemFactory.control(Material.GRAY_STAINED_GLASS_PANE, Component.text(" ", NamedTextColor.DARK_GRAY), List.of());
        for (int i = 0; i < inv.getSize(); i++) {
            inv.setItem(i, filler);
        }
        for (final int d : new int[]{4, 13, 22, 31, 40, 49}) {
            inv.setItem(d, divider);
        }
        final String initName = nameOf(holder.initiator());
        final String partName = nameOf(holder.partner());
        inv.setItem(0, tradeHeader(initName, holder.confirmed(holder.initiator())));
        inv.setItem(8, tradeHeader(partName, holder.confirmed(holder.partner())));

        final List<ItemStack> initItems = holder.itemsOf(holder.initiator());
        final List<ItemStack> partItems = holder.itemsOf(holder.partner());
        for (int i = 0; i < TRADE_INIT_ITEM_SLOTS.length; i++) {
            inv.setItem(TRADE_INIT_ITEM_SLOTS[i], i < initItems.size() ? initItems.get(i).clone() : emptyOfferSlot());
            inv.setItem(TRADE_PART_ITEM_SLOTS[i], i < partItems.size() ? partItems.get(i).clone() : emptyOfferSlot());
        }

        inv.setItem(TRADE_INIT_ADD, tradeButton(Material.LIME_DYE, "menu.trade.offer-pet", "menu.trade.offer-pet-hint"));
        inv.setItem(TRADE_INIT_CLEAR, tradeButton(Material.CAULDRON, "menu.trade.clear", "menu.trade.clear-hint"));
        inv.setItem(TRADE_INIT_TOKENS, tokenOfferItem(holder.tokensOf(holder.initiator())));
        inv.setItem(TRADE_PART_ADD, tradeButton(Material.LIME_DYE, "menu.trade.offer-pet", "menu.trade.offer-pet-hint"));
        inv.setItem(TRADE_PART_CLEAR, tradeButton(Material.CAULDRON, "menu.trade.clear", "menu.trade.clear-hint"));
        inv.setItem(TRADE_PART_TOKENS, tokenOfferItem(holder.tokensOf(holder.partner())));

        inv.setItem(TRADE_INIT_CONFIRM, confirmButton(holder.confirmed(holder.initiator())));
        inv.setItem(TRADE_PART_CONFIRM, confirmButton(holder.confirmed(holder.partner())));
        inv.setItem(TRADE_CLOSE, itemFactory.control(Material.BARRIER, ml("menu.trade.cancel", NamedTextColor.RED),
            List.of(mg("menu.trade.cancel-lore"))));
    }

    private ItemStack tradeHeader(final String name, final boolean confirmed) {
        return itemFactory.control(Material.PLAYER_HEAD, Component.text(name, NamedTextColor.AQUA),
            List.of(confirmed ? ml("menu.trade.confirmed", NamedTextColor.GREEN) : ml("menu.trade.deciding", NamedTextColor.GRAY)));
    }

    private ItemStack emptyOfferSlot() {
        return itemFactory.control(Material.LIGHT_GRAY_STAINED_GLASS_PANE, ml("menu.trade.empty-slot", NamedTextColor.DARK_GRAY), List.of());
    }

    private ItemStack tradeButton(final Material icon, final String labelKey, final String hintKey) {
        return itemFactory.control(icon, ml(labelKey, NamedTextColor.YELLOW), List.of(mg(hintKey)));
    }

    private ItemStack tokenOfferItem(final int amount) {
        return itemFactory.control(Material.SUNFLOWER, ml("menu.trade.token-offer", NamedTextColor.GOLD, "%n%", Integer.toString(amount)),
            List.of(ml("menu.trade.token-plus", NamedTextColor.GREEN),
                ml("menu.trade.token-minus", NamedTextColor.RED)));
    }

    private ItemStack confirmButton(final boolean confirmed) {
        return itemFactory.control(confirmed ? Material.EMERALD_BLOCK : Material.EMERALD,
            ml(confirmed ? "menu.trade.confirmed-btn" : "menu.trade.confirm-btn", confirmed ? NamedTextColor.GREEN : NamedTextColor.YELLOW),
            List.of(mg("menu.trade.confirm-lore")));
    }

    private String nameOf(final UUID id) {
        final Player online = Bukkit.getPlayer(id);
        if (online != null) {
            return online.getName();
        }
        final String stored = storage.data(id).playerName();
        return stored != null ? stored : id.toString().substring(0, 8);
    }

    private void handleTradeClick(final InventoryClickEvent event, final TradeMenuHolder holder) {
        event.setCancelled(true);
        if (!(event.getWhoClicked() instanceof Player player)) {
            return;
        }
        final UUID id = player.getUniqueId();
        if (!holder.isInitiator(id) && !holder.partner().equals(id)) {
            return;
        }
        final int slot = event.getRawSlot();
        if (slot == TRADE_CLOSE) {
            player.closeInventory();
            return;
        }
        final boolean isInit = holder.isInitiator(id);
        final int addSlot = isInit ? TRADE_INIT_ADD : TRADE_PART_ADD;
        final int clearSlot = isInit ? TRADE_INIT_CLEAR : TRADE_PART_CLEAR;
        final int tokenSlot = isInit ? TRADE_INIT_TOKENS : TRADE_PART_TOKENS;
        final int confirmSlot = isInit ? TRADE_INIT_CONFIRM : TRADE_PART_CONFIRM;

        if (slot == addSlot) {
            offerHeldPet(player, holder);
        } else if (slot == clearSlot) {
            clearTradeOffer(player, holder);
        } else if (slot == tokenSlot) {
            final int step = (event.isShiftClick() ? 10 : 1) * (event.isRightClick() ? -1 : 1);
            final int max = storage.data(id).tokens();
            holder.setTokensOf(id, Math.max(0, Math.min(max, holder.tokensOf(id) + step)));
            resetTradeConfirmations(holder);
            player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 0.4F, step > 0 ? 1.4F : 0.9F);
            renderTradeMenu(holder);
        } else if (slot == confirmSlot) {
            holder.setConfirmed(id, !holder.confirmed(id));
            player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 0.6F, 1.2F);
            if (holder.bothConfirmed()) {
                completeTrade(holder);
            } else {
                renderTradeMenu(holder);
            }
        }
    }

    private void offerHeldPet(final Player player, final TradeMenuHolder holder) {
        final List<ItemStack> items = holder.itemsOf(player.getUniqueId());
        if (items.size() >= TradeMenuHolder.MAX_OFFER_ITEMS) {
            player.sendMessage(lang.colored("trade.offer-full", NamedTextColor.RED));
            return;
        }
        final ItemStack held = player.getInventory().getItemInMainHand();
        if (held.getType().isAir() || itemFactory.petId(held).isEmpty()) {
            player.sendMessage(lang.colored("trade.not-a-pet", NamedTextColor.RED));
            return;
        }
        final ItemStack one = held.clone();
        one.setAmount(1);
        items.add(one);
        held.setAmount(held.getAmount() - 1);
        resetTradeConfirmations(holder);
        player.playSound(player.getLocation(), Sound.ENTITY_ITEM_PICKUP, 0.7F, 1.2F);
        renderTradeMenu(holder);
    }

    private void clearTradeOffer(final Player player, final TradeMenuHolder holder) {
        final List<ItemStack> items = holder.itemsOf(player.getUniqueId());
        for (final ItemStack item : items) {
            giveOrDrop(player, item);
        }
        items.clear();
        holder.setTokensOf(player.getUniqueId(), 0);
        resetTradeConfirmations(holder);
        renderTradeMenu(holder);
    }

    /** Any change to an offer must clear both confirmations, so nobody can confirm and then alter the deal. */
    private void resetTradeConfirmations(final TradeMenuHolder holder) {
        holder.setConfirmed(holder.initiator(), false);
        holder.setConfirmed(holder.partner(), false);
    }

    /** Atomically swaps both sides' offered items and tokens, then closes the window for both. */
    private void completeTrade(final TradeMenuHolder holder) {
        if (holder.settled()) {
            return;
        }
        final Player initiator = Bukkit.getPlayer(holder.initiator());
        final Player partner = Bukkit.getPlayer(holder.partner());
        if (initiator == null || partner == null) {
            cancelTrade(holder);
            return;
        }
        final PlayerPetData initData = storage.data(holder.initiator());
        final PlayerPetData partData = storage.data(holder.partner());
        final int initTokens = Math.min(holder.tokensOf(holder.initiator()), initData.tokens());
        final int partTokens = Math.min(holder.tokensOf(holder.partner()), partData.tokens());
        // Move tokens.
        initData.addTokens(-initTokens);
        partData.addTokens(-partTokens);
        initData.addTokens(partTokens);
        partData.addTokens(initTokens);
        // Move items to the OTHER player.
        for (final ItemStack item : holder.itemsOf(holder.initiator())) {
            giveOrDrop(partner, item);
        }
        for (final ItemStack item : holder.itemsOf(holder.partner())) {
            giveOrDrop(initiator, item);
        }
        holder.itemsOf(holder.initiator()).clear();
        holder.itemsOf(holder.partner()).clear();
        holder.markSettled();
        requestSave();
        for (final Player p : new Player[]{initiator, partner}) {
            p.sendMessage(lang.colored("trade.complete", NamedTextColor.GREEN));
            p.playSound(p.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 0.8F, 1.3F);
            p.closeInventory();
        }
    }

    /** Returns each side's offered items to its owner (tokens were never deducted) and marks the trade done. */
    private void cancelTrade(final TradeMenuHolder holder) {
        if (holder.settled()) {
            return;
        }
        holder.markSettled();
        returnOfferedItems(holder, holder.initiator());
        returnOfferedItems(holder, holder.partner());
        for (final UUID id : new UUID[]{holder.initiator(), holder.partner()}) {
            final Player p = Bukkit.getPlayer(id);
            if (p != null) {
                p.sendMessage(lang.colored("trade.cancelled", NamedTextColor.YELLOW));
                if (p.getOpenInventory().getTopInventory().getHolder() instanceof TradeMenuHolder) {
                    p.closeInventory();
                }
            }
        }
    }

    private void returnOfferedItems(final TradeMenuHolder holder, final UUID id) {
        final List<ItemStack> items = holder.itemsOf(id);
        if (items.isEmpty()) {
            return;
        }
        final Player owner = Bukkit.getPlayer(id);
        for (final ItemStack item : items) {
            if (owner != null) {
                giveOrDrop(owner, item);
            } else {
                // Owner went offline mid-trade: hold their pet(s) and hand them back on next join. Never lost.
                pendingTradeReturns.computeIfAbsent(id, k -> new ArrayList<>()).add(item);
            }
        }
        items.clear();
    }

    /** Gives back any pets that were held for a player who disconnected mid-trade. Called on join. */
    private void deliverPendingTradeReturns(final Player player) {
        final List<ItemStack> pending = pendingTradeReturns.remove(player.getUniqueId());
        if (pending == null || pending.isEmpty()) {
            return;
        }
        for (final ItemStack item : pending) {
            giveOrDrop(player, item);
        }
        player.sendMessage(lang.colored("trade.returned", NamedTextColor.YELLOW));
    }

    private void openCustomizeMenu(final Player player, final OwnedPet pet) {
        final PetDefinition definition = definitions.get(pet.definitionId()).orElse(null);
        if (definition == null) {
            return;
        }
        final CustomizeMenuHolder holder = new CustomizeMenuHolder(pet.uuid(), 0);
        final Inventory inventory = Bukkit.createInventory(holder, 54, Texts.rarityTitle(mt("menu.title.customize-prefix") + " " + definition.name(), definition.rarityColor()));
        holder.setInventory(inventory);
        renderCustomizeMenu(inventory, holder, player);
        player.openInventory(inventory);
    }

    private static final int CUSTOMIZE_PER_PAGE = 36;

    private void fillCustomizeBorder(final Inventory inventory) {
        final ItemStack filler = itemFactory.control(Material.BLACK_STAINED_GLASS_PANE, Component.text(" ", NamedTextColor.DARK_GRAY), List.of());
        for (int i = 0; i < 9; i++) {
            inventory.setItem(i, filler);
        }
        for (int i = 45; i < 54; i++) {
            inventory.setItem(i, filler);
        }
    }

    private void renderCustomizeMenu(final Inventory inventory, final CustomizeMenuHolder holder, final Player player) {
        inventory.clear();
        final PlayerPetData data = storage.data(player.getUniqueId());
        final OwnedPet pet = data.findPet(holder.petUuid()).orElse(null);
        if (pet == null) {
            return;
        }
        final PetDefinition definition = definitions.get(pet.definitionId()).orElse(null);
        if (definition == null) {
            return;
        }
        if (!holder.section().equals("main")) {
            renderCosmeticPicker(inventory, holder, player, pet, holder.section());
            return;
        }
        // Safety: the currently-worn skin is always part of the player's unlocked collection.
        if (pet.variant() != null) {
            data.unlockVariant(pet.definitionId(), pet.variant());
        }
        fillCustomizeBorder(inventory);
        inventory.setItem(4, itemFactory.menuItem(definition, pet, pet.uuid().equals(data.activePetId())));
        inventory.setItem(0, itemFactory.control(
            pet.particlesEnabled() ? Material.LIME_DYE : Material.GRAY_DYE,
            ml(pet.particlesEnabled() ? "menu.customize.particles-on" : "menu.customize.particles-off",
                pet.particlesEnabled() ? NamedTextColor.GREEN : NamedTextColor.RED),
            List.of(mg("menu.customize.particles-lore"))));
        inventory.setItem(2, itemFactory.control(shopDye(pet.particleColor() == null ? "aqua" : pet.particleColor()),
            ml("menu.customize.particle-colour", NamedTextColor.AQUA),
            List.of(ml("menu.customize.current", NamedTextColor.GRAY).append(Component.text(cosmeticDisplay(Cosmetics.CAT_PARTICLE, pet.particleColor()), NamedTextColor.WHITE)),
                ml("menu.customize.pick-aura", NamedTextColor.YELLOW))));
        inventory.setItem(6, itemFactory.control(Material.FIREWORK_STAR,
            ml("menu.customize.trail", NamedTextColor.LIGHT_PURPLE),
            List.of(ml("menu.customize.current", NamedTextColor.GRAY).append(Component.text(cosmeticDisplay(Cosmetics.CAT_TRAIL, pet.trail()), NamedTextColor.WHITE)),
                ml("menu.customize.pick-trail", NamedTextColor.YELLOW))));
        inventory.setItem(8, itemFactory.control(Material.NAME_TAG,
            ml("menu.customize.nametag", NamedTextColor.YELLOW),
            List.of(ml("menu.customize.current", NamedTextColor.GRAY).append(Component.text(cosmeticDisplay(Cosmetics.CAT_NAMETAG, pet.nametagStyle()), NamedTextColor.WHITE)),
                ml("menu.customize.pick-nametag", NamedTextColor.YELLOW))));
        inventory.setItem(45, itemFactory.control(Material.NETHER_STAR,
            ml("menu.customize.ascension", NamedTextColor.LIGHT_PURPLE),
            List.of(ml("menu.customize.stars", NamedTextColor.GOLD, "%stars%", "★".repeat(pet.stars()) + "☆".repeat(OwnedPet.MAX_STARS - pet.stars())),
                ml("menu.customize.ascension-lore", NamedTextColor.YELLOW))));

        final List<String> keys = new ArrayList<>(definition.variants().keySet());
        final int pages = Math.max(1, (keys.size() + CUSTOMIZE_PER_PAGE - 1) / CUSTOMIZE_PER_PAGE);
        if (holder.page() >= pages) {
            holder.setPage(pages - 1);
        }
        final int skinPrice = shopPrice("skin", 12);
        final int start = holder.page() * CUSTOMIZE_PER_PAGE;
        for (int i = 0; i < CUSTOMIZE_PER_PAGE && start + i < keys.size(); i++) {
            final String key = keys.get(start + i);
            final int slot = 9 + i;
            if (data.isVariantUnlocked(pet.definitionId(), key)) {
                final ItemStack icon = itemFactory.variantIcon(definition, key);
                final boolean active = key.equalsIgnoreCase(pet.variant());
                icon.editMeta(meta -> {
                    if (active) {
                        meta.setEnchantmentGlintOverride(true);
                    }
                    final List<Component> lore = new ArrayList<>(meta.lore() == null ? List.of() : meta.lore());
                    lore.add((active
                        ? ml("menu.customize.active-skin", NamedTextColor.GREEN)
                        : ml("menu.customize.wear-skin", NamedTextColor.YELLOW)).decoration(TextDecoration.ITALIC, false));
                    meta.lore(lore);
                });
                inventory.setItem(slot, icon);
            } else {
                inventory.setItem(slot, itemFactory.control(Material.GRAY_STAINED_GLASS_PANE,
                    ml("menu.customize.locked", NamedTextColor.DARK_GRAY, "%skin%", PetDefinition.variantDisplay(key)),
                    List.of(mg("menu.customize.locked-lore1"),
                        ml("menu.customize.locked-lore2", NamedTextColor.GOLD, "%price%", Integer.toString(skinPrice)))));
            }
        }
        if (holder.page() > 0) {
            inventory.setItem(48, itemFactory.control(Material.SPECTRAL_ARROW,
                ml("menu.common.prev", NamedTextColor.YELLOW),
                List.of(ml("menu.common.page", NamedTextColor.GRAY, "%page%", Integer.toString(holder.page() + 1), "%pages%", Integer.toString(pages)))));
        }
        if (holder.page() + 1 < pages) {
            inventory.setItem(50, itemFactory.control(Material.SPECTRAL_ARROW,
                ml("menu.common.next", NamedTextColor.YELLOW),
                List.of(ml("menu.common.page", NamedTextColor.GRAY, "%page%", Integer.toString(holder.page() + 1), "%pages%", Integer.toString(pages)))));
        }
        inventory.setItem(49, itemFactory.control(Material.BARRIER, ml("menu.common.close", NamedTextColor.RED), List.of()));
    }

    private String cosmeticDisplay(final String category, final String id) {
        if (id == null) {
            return mt("menu.customize.none");
        }
        return switch (category) {
            case "particle" -> Cosmetics.particleColor(id) != null ? Cosmetics.particleColor(id).display() : id;
            case "trail" -> Cosmetics.trail(id) != null ? Cosmetics.trail(id).display() : id;
            case "nametag" -> Cosmetics.nametagStyle(id) != null ? Cosmetics.nametagStyle(id).display() : id;
            default -> id;
        };
    }

    /** Renders a per-pet cosmetic picker (particle / trail / nametag) showing owned selectable and locked options. */
    private void renderCosmeticPicker(final Inventory inventory, final CustomizeMenuHolder holder, final Player player,
                                      final OwnedPet pet, final String category) {
        final PlayerPetData data = storage.data(player.getUniqueId());
        final String current = switch (category) {
            case "particle" -> pet.particleColor();
            case "trail" -> pet.trail();
            default -> pet.nametagStyle();
        };
        fillCustomizeBorder(inventory);
        final String title = mt(category.equals("particle") ? "menu.customize.particle-colour"
            : category.equals("trail") ? "menu.customize.trail" : "menu.customize.nametag");
        inventory.setItem(4, itemFactory.control(
            category.equals("particle") ? Material.BLAZE_POWDER : category.equals("trail") ? Material.FIREWORK_STAR : Material.NAME_TAG,
            ml("menu.picker.choose", NamedTextColor.AQUA, "%what%", title),
            List.of(mg("menu.picker.owned-hint1"), mg("menu.picker.owned-hint2"))));
        inventory.setItem(0, itemFactory.control(current == null ? Material.LIME_DYE : Material.GRAY_DYE,
            ml("menu.picker.none", current == null ? NamedTextColor.GREEN : NamedTextColor.GRAY),
            List.of(mg("menu.picker.none-lore"))));
        final List<String> ids = shopIds(category);
        final int start = holder.page() * CUSTOMIZE_PER_PAGE;
        for (int i = 0; i < CUSTOMIZE_PER_PAGE && start + i < ids.size(); i++) {
            final String id = ids.get(start + i);
            final int slot = 9 + i;
            final boolean owned = data.hasCosmetic(category, id);
            if (owned) {
                final boolean active = id.equalsIgnoreCase(current);
                final ItemStack icon = shopIcon(category, id, 0, true);
                icon.editMeta(meta -> {
                    meta.setEnchantmentGlintOverride(active);
                    meta.lore(List.of((active ? ml("menu.picker.selected", NamedTextColor.GREEN)
                        : ml("menu.picker.apply", NamedTextColor.YELLOW)).decoration(TextDecoration.ITALIC, false)));
                });
                inventory.setItem(slot, icon);
            } else {
                inventory.setItem(slot, itemFactory.control(Material.GRAY_STAINED_GLASS_PANE,
                    ml("menu.picker.locked", NamedTextColor.DARK_GRAY, "%name%", cosmeticDisplay(category, id)),
                    List.of(mg("menu.picker.locked-lore"))));
            }
        }
        inventory.setItem(45, itemFactory.control(Material.ARROW,
            ml("menu.common.back", NamedTextColor.YELLOW), List.of(mg("menu.picker.back-lore"))));
        final int pages = Math.max(1, (ids.size() + CUSTOMIZE_PER_PAGE - 1) / CUSTOMIZE_PER_PAGE);
        if (holder.page() > 0) {
            inventory.setItem(48, itemFactory.control(Material.SPECTRAL_ARROW, ml("menu.common.prev", NamedTextColor.YELLOW),
                List.of(ml("menu.common.page", NamedTextColor.GRAY, "%page%", Integer.toString(holder.page() + 1), "%pages%", Integer.toString(pages)))));
        }
        if (holder.page() + 1 < pages) {
            inventory.setItem(50, itemFactory.control(Material.SPECTRAL_ARROW, ml("menu.common.next", NamedTextColor.YELLOW),
                List.of(ml("menu.common.page", NamedTextColor.GRAY, "%page%", Integer.toString(holder.page() + 1), "%pages%", Integer.toString(pages)))));
        }
        inventory.setItem(49, itemFactory.control(Material.BARRIER, ml("menu.common.close", NamedTextColor.RED), List.of()));
    }

    private void handleCustomizeClick(final InventoryClickEvent event, final CustomizeMenuHolder holder) {
        event.setCancelled(true);
        if (!(event.getWhoClicked() instanceof Player player)) {
            return;
        }
        final PlayerPetData data = storage.data(player.getUniqueId());
        final OwnedPet pet = data.findPet(holder.petUuid()).orElse(null);
        if (pet == null) {
            player.closeInventory();
            return;
        }
        final PetDefinition definition = definitions.get(pet.definitionId()).orElse(null);
        if (definition == null) {
            return;
        }
        final int slot = event.getRawSlot();
        if (slot == 49) {
            player.closeInventory();
            return;
        }
        if (!holder.section().equals("main")) {
            handleCosmeticPickerClick(event, holder, player, pet, holder.section());
            return;
        }
        if (slot == 0) {
            pet.setParticlesEnabled(!pet.particlesEnabled());
            requestSave();
            renderCustomizeMenu(event.getView().getTopInventory(), holder, player);
            return;
        }
        if (slot == 2 || slot == 6 || slot == 8) {
            holder.setSection(slot == 2 ? "particle" : slot == 6 ? "trail" : "nametag");
            holder.setPage(0);
            renderCustomizeMenu(event.getView().getTopInventory(), holder, player);
            return;
        }
        if (slot == 45) {
            openAscensionMenu(player, pet);
            return;
        }
        if (slot == 48 || slot == 50) {
            final int pages = Math.max(1, (definition.variants().size() + CUSTOMIZE_PER_PAGE - 1) / CUSTOMIZE_PER_PAGE);
            final int target = slot == 48 ? holder.page() - 1 : holder.page() + 1;
            if (target >= 0 && target < pages) {
                holder.setPage(target);
                renderCustomizeMenu(event.getView().getTopInventory(), holder, player);
            }
            return;
        }
        if (slot >= 9 && slot < 45) {
            final List<String> keys = new ArrayList<>(definition.variants().keySet());
            final int idx = (holder.page() * CUSTOMIZE_PER_PAGE) + (slot - 9);
            if (idx < 0 || idx >= keys.size()) {
                return;
            }
            final String key = keys.get(idx);
            if (!data.isVariantUnlocked(pet.definitionId(), key)) {
                // Buy the locked skin with tokens.
                final int skinPrice = shopPrice("skin", 12);
                if (!tokensEnabled() || data.tokens() < skinPrice) {
                    player.sendMessage(lang.component("shop.not-enough", "%price%", Integer.toString(skinPrice), "%total%", Integer.toString(data.tokens())));
                    player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_BASS, 0.7F, 0.8F);
                    return;
                }
                data.setTokens(data.tokens() - skinPrice);
                data.unlockVariant(pet.definitionId(), key);
                player.sendMessage(lang.component("shop.bought", "%item%", PetDefinition.variantDisplay(key) + " " + definition.name(), "%price%", Integer.toString(skinPrice)));
            }
            pet.setVariant(key);
            requestSave();
            if (pet.uuid().equals(data.activePetId())) {
                activePets.refreshDisplay(player);
            }
            player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 0.7F, 1.2F);
            player.sendMessage(message("customize.applied").replaceText(builder -> builder.matchLiteral("%variant%").replacement(PetDefinition.variantDisplay(key))));
            renderCustomizeMenu(event.getView().getTopInventory(), holder, player);
        }
    }

    private void handleCosmeticPickerClick(final InventoryClickEvent event, final CustomizeMenuHolder holder, final Player player,
                                           final OwnedPet pet, final String category) {
        final PlayerPetData data = storage.data(player.getUniqueId());
        final int slot = event.getRawSlot();
        if (slot == 45) {
            holder.setSection("main");
            holder.setPage(0);
            renderCustomizeMenu(event.getView().getTopInventory(), holder, player);
            return;
        }
        final List<String> ids = shopIds(category);
        if (slot == 48 || slot == 50) {
            final int pages = Math.max(1, (ids.size() + CUSTOMIZE_PER_PAGE - 1) / CUSTOMIZE_PER_PAGE);
            final int target = slot == 48 ? holder.page() - 1 : holder.page() + 1;
            if (target >= 0 && target < pages) {
                holder.setPage(target);
                renderCustomizeMenu(event.getView().getTopInventory(), holder, player);
            }
            return;
        }
        String selected = null;
        if (slot == 0) {
            selected = null;
        } else if (slot >= 9 && slot < 45) {
            final int idx = (holder.page() * CUSTOMIZE_PER_PAGE) + (slot - 9);
            if (idx < 0 || idx >= ids.size()) {
                return;
            }
            final String id = ids.get(idx);
            if (!data.hasCosmetic(category, id)) {
                player.sendMessage(message("customize.locked"));
                player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_BASS, 0.7F, 0.8F);
                return;
            }
            selected = id;
        } else {
            return;
        }
        switch (category) {
            case "particle" -> pet.setParticleColor(selected);
            case "trail" -> pet.setTrail(selected);
            case "nametag" -> pet.setNametagStyle(selected);
            default -> {
            }
        }
        requestSave();
        if (pet.uuid().equals(data.activePetId())) {
            activePets.refreshDisplay(player);
        }
        player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 0.7F, 1.2F);
        renderCustomizeMenu(event.getView().getTopInventory(), holder, player);
    }

    private void openChanceMenu(final Player player) {
        if (!has(player, CHANCES_PERMISSION)) {
            player.sendMessage(message("messages.no-permission"));
            return;
        }

        final ChanceMenuHolder holder = new ChanceMenuHolder(player.getUniqueId());
        final Inventory inventory = Bukkit.createInventory(holder, 54, Texts.menuTitle(mt("menu.title.chances")));
        holder.setInventory(inventory);
        renderChanceMenu(inventory, holder);
        player.openInventory(inventory);
    }

    private void renderChanceMenu(final Inventory inventory, final ChanceMenuHolder holder) {
        inventory.clear();
        final List<PetDefinition> all = definitions.ordered();
        final int pages = pageCount(all.size());
        if (holder.page() >= pages) {
            holder.setPage(Math.max(0, pages - 1));
        }
        final int start = holder.page() * PET_SLOT_LIMIT;
        final int visible = Math.max(0, Math.min(PET_SLOT_LIMIT, all.size() - start));
        for (int i = 0; i < visible; i++) {
            final PetDefinition definition = all.get(start + i);
            inventory.setItem(i, itemFactory.chanceItem(definition, spawnChance(definition)));
        }
        inventory.setItem(45, itemFactory.control(
            Material.ARROW,
            ml("menu.common.back", NamedTextColor.YELLOW),
            List.of(mg("menu.catalogue.back-lore"))
        ));
        if (holder.page() > 0) {
            inventory.setItem(46, itemFactory.control(
                Material.SPECTRAL_ARROW,
                ml("menu.common.prev", NamedTextColor.YELLOW),
                List.of(ml("menu.common.page", NamedTextColor.GRAY, "%page%", Integer.toString(holder.page() + 1), "%pages%", Integer.toString(pages)))
            ));
        }
        if (holder.page() + 1 < pages) {
            inventory.setItem(47, itemFactory.control(
                Material.SPECTRAL_ARROW,
                ml("menu.common.next", NamedTextColor.YELLOW),
                List.of(ml("menu.common.page", NamedTextColor.GRAY, "%page%", Integer.toString(holder.page() + 1), "%pages%", Integer.toString(pages)))
            ));
        }
        inventory.setItem(49, itemFactory.control(
            Material.BARRIER,
            ml("menu.common.close", NamedTextColor.RED),
            List.of(mg("menu.common.saves-instantly"))
        ));
        inventory.setItem(48, itemFactory.control(
            Material.BELL,
            ml("menu.chances.broadcasts", NamedTextColor.GOLD),
            List.of(mg("menu.chances.broadcasts-lore"))
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
        if (event.getRawSlot() == 46 || event.getRawSlot() == 47) {
            final int pages = pageCount(definitions.ordered().size());
            final int target = event.getRawSlot() == 46 ? holder.page() - 1 : holder.page() + 1;
            if (target >= 0 && target < pages) {
                holder.setPage(target);
                renderChanceMenu(event.getView().getTopInventory(), holder);
            }
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

        if (event.getClick() == ClickType.MIDDLE) {
            startChanceInput(player, definition);
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
        renderChanceMenu(event.getView().getTopInventory(), holder);
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
        final Inventory inventory = Bukkit.createInventory(holder, 27, Texts.menuTitle(mt("menu.title.broadcasts")));
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
                Component.text(rarity + ": " + mt(enabled ? "menu.common.on-label" : "menu.common.off-label"), rarityColor(rarity)),
                List.of(mg("menu.notify.toggle-lore"))
            ));
        }
        inventory.setItem(22, itemFactory.control(
            Material.ARROW,
            ml("menu.common.back", NamedTextColor.YELLOW),
            List.of(mg("menu.notify.back-lore"))
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
        if (!has(player, ADMIN_PERMISSION)) {
            player.sendMessage(message("messages.no-permission"));
            return;
        }

        final XpMenuHolder holder = new XpMenuHolder(player.getUniqueId());
        final Inventory inventory = Bukkit.createInventory(holder, 27, Texts.menuTitle(mt("menu.title.xp")));
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
            List.of(mg("menu.xp.minus-lore"))
        ));
        inventory.setItem(13, itemFactory.control(
            Material.EXPERIENCE_BOTTLE,
            ml("menu.xp.multiplier", NamedTextColor.AQUA, "%n%", formatDecimal(multiplier)),
            List.of(
                mg("menu.xp.info1"),
                mg("menu.xp.info2"),
                mg("menu.xp.info3"),
                ml("menu.xp.info-exact", NamedTextColor.AQUA),
                mg("menu.xp.info-range")
            )
        ));
        inventory.setItem(15, itemFactory.control(
            Material.LIME_STAINED_GLASS_PANE,
            Component.text("+0.1x", NamedTextColor.GREEN),
            List.of(mg("menu.xp.plus-lore"))
        ));
        inventory.setItem(18, itemFactory.control(
            Material.ARROW,
            ml("menu.common.back", NamedTextColor.YELLOW),
            List.of(mg("menu.catalogue.back-lore"))
        ));
        inventory.setItem(22, itemFactory.control(
            Material.BARRIER,
            ml("menu.common.close", NamedTextColor.RED),
            List.of(mg("menu.xp.close-lore"))
        ));
    }

    private void handleXpClick(final InventoryClickEvent event, final XpMenuHolder holder) {
        event.setCancelled(true);
        if (!(event.getWhoClicked() instanceof Player player) || !player.getUniqueId().equals(holder.owner())) {
            return;
        }
        if (!has(player, ADMIN_PERMISSION)) {
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
        if (event.getClick() == ClickType.MIDDLE) {
            startXpInput(player);
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
        player.sendMessage(lang.component("messages.xp-multiplier-set", "%value%", formatDecimal(petXpMultiplier())));
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
        final Inventory inventory = Bukkit.createInventory(holder, 27, Texts.menuTitle(mt("menu.title.modules")));
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
            ml("menu.common.close", NamedTextColor.RED),
            List.of(mg("menu.modules.close-lore"))
        ));
    }

    private ItemStack moduleItem(final Module module) {
        final boolean available = module.isAvailable();
        final boolean active = moduleManager.isActive(module.id());
        final Material material = !available ? Material.BARRIER : active ? Material.LIME_DYE : Material.RED_DYE;
        final NamedTextColor color = !available ? NamedTextColor.DARK_GRAY : active ? NamedTextColor.GREEN : NamedTextColor.RED;
        final List<Component> lore = new ArrayList<>();
        lore.add(Component.text(module.description(), NamedTextColor.GRAY));
        lore.add(lang.colored("module.icon", NamedTextColor.DARK_GRAY,
            "%material%", module.iconMaterial().name().toLowerCase(Locale.ROOT)));
        lore.add(Component.empty());
        if (!available) {
            lore.add(lang.colored("module.needs-plugin", NamedTextColor.DARK_GRAY, "%plugin%", module.requiredPluginName()));
        } else if (active) {
            lore.add(lang.colored("module.click-disable", NamedTextColor.GREEN));
        } else {
            lore.add(lang.colored("module.click-enable", NamedTextColor.RED));
        }
        if (moduleManager.isRequestedEnabled(module.id()) && !active) {
            lore.add(lang.colored("module.waiting-plugin", NamedTextColor.YELLOW));
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
        lang.load();
        refreshXpMultiplierCache();
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

    void handleLanguageCommand(final CommandSender sender, final String[] args) {
        if (!has(sender, ADMIN_PERMISSION)) {
            sender.sendMessage(message("messages.no-permission"));
            return;
        }
        if (args.length < 2) {
            sender.sendMessage(Component.text("Current language: " + lang.activeCode()
                + ". Usage: /pets language <" + String.join("|", lang.languageCodes()) + ">", NamedTextColor.YELLOW));
            return;
        }
        final String code = args[1].toLowerCase(Locale.ROOT);
        if (!lang.languageCodes().contains(code)) {
            sender.sendMessage(Component.text("Unknown language '" + code + "'. Available: "
                + String.join(", ", lang.languageCodes()), NamedTextColor.RED));
            return;
        }
        getConfig().set("language", code);
        saveConfig();
        lang.load();
        sender.sendMessage(Component.text("Language set to '" + code + "'. Menus reopen in the new language.", NamedTextColor.GREEN));
        getLogger().info(sender.getName() + " changed the language to '" + code + "'.");
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
        updater.checkLatestVersion(sender, version);
    }

    void handleUpdateCommand(final CommandSender sender) {
        if (!has(sender, ADMIN_PERMISSION)) {
            sender.sendMessage(message("messages.no-permission"));
            return;
        }
        if (updater.repo().isEmpty()) {
            sender.sendMessage(Component.text("The auto-updater is disabled. Set update.repo in config.yml to enable it.", NamedTextColor.RED));
            return;
        }
        final String currentVersion = getPluginMeta().getVersion();
        if (!updater.autoEnabled()) {
            sender.sendMessage(Component.text("Auto-download is off (update.enabled: false). Only the version check ran; download it yourself from the release page.", NamedTextColor.YELLOW));
            updater.checkLatestVersion(sender, currentVersion);
            return;
        }
        sender.sendMessage(Component.text("Checking for a Better Pets update...", NamedTextColor.GOLD));
        updater.downloadLatest(sender, currentVersion);
    }

    /** The jar file this plugin was loaded from (used by the updater to stage a replacement). */
    java.io.File currentJar() {
        return getFile();
    }

    @EventHandler
    public void onChatInput(final AsyncChatEvent event) {
        final Player player = event.getPlayer();
        final PendingInput pending = pendingInputs.get(player.getUniqueId());
        if (pending == null) {
            return;
        }
        // The player is typing a value for a menu prompt; swallow the chat and handle it on the main thread.
        event.setCancelled(true);
        final String raw = PlainTextComponentSerializer.plainText().serialize(event.message()).trim();
        Bukkit.getScheduler().runTask(this, () -> applyPendingInput(player, pending, raw));
    }

    private void applyPendingInput(final Player player, final PendingInput pending, final String raw) {
        if (!player.isOnline() || !pending.equals(pendingInputs.get(player.getUniqueId()))) {
            return;
        }
        if (raw.equalsIgnoreCase("cancel")) {
            pendingInputs.remove(player.getUniqueId());
            player.sendMessage(lang.colored("input.cancelled", NamedTextColor.GRAY));
            return;
        }
        final double value;
        try {
            value = Double.parseDouble(raw.replace(',', '.'));
        } catch (final NumberFormatException exception) {
            // Keep the prompt open so the player can try again.
            player.sendMessage(lang.colored("input.invalid", NamedTextColor.RED));
            return;
        }
        pendingInputs.remove(player.getUniqueId());
        if (pending.type().equals("chance")) {
            final PetDefinition definition = definitions.get(pending.petId()).orElse(null);
            if (definition == null) {
                return;
            }
            final double clamped = clampChance(value);
            setSpawnChance(definition, clamped);
            player.sendMessage(lang.colored("input.applied", NamedTextColor.GREEN, "%value%", formatPercent(clamped) + "%"));
        } else if (pending.type().equals("xp")) {
            final double clamped = clampXpMultiplier(value);
            setPetXpMultiplier(clamped);
            player.sendMessage(lang.colored("input.applied", NamedTextColor.GREEN, "%value%", formatDecimal(clamped) + "x"));
        }
    }

    private void startChanceInput(final Player player, final PetDefinition definition) {
        pendingInputs.put(player.getUniqueId(), new PendingInput("chance", definition.id()));
        player.closeInventory();
        player.sendMessage(lang.colored("input.prompt-chance", NamedTextColor.AQUA, "%pet%", definition.name()));
    }

    private void startXpInput(final Player player) {
        pendingInputs.put(player.getUniqueId(), new PendingInput("xp", null));
        player.closeInventory();
        player.sendMessage(lang.colored("input.prompt-xp", NamedTextColor.AQUA));
    }

    void handleMute(final Player player) {
        final PlayerPetData data = storage.data(player.getUniqueId());
        final boolean nowMuted = !data.broadcastsMuted();
        data.setBroadcastsMuted(nowMuted);
        requestSave();
        player.sendMessage(lang.colored(nowMuted ? "broadcast.muted" : "broadcast.unmuted",
            nowMuted ? NamedTextColor.YELLOW : NamedTextColor.GREEN));
    }

    void handleVisibility(final Player player, final boolean visible) {
        final PlayerPetData data = storage.data(player.getUniqueId());
        data.setVisible(visible);
        activePets.setVisible(player, visible);
        requestSave();
        player.sendMessage(lang.colored(visible ? "visibility.shown" : "visibility.hidden",
            visible ? NamedTextColor.GREEN : NamedTextColor.YELLOW));
    }

    void handleSetName(final Player player, final String name) {
        final OwnedPet pet = storage.data(player.getUniqueId()).activePet().orElse(null);
        if (pet == null) {
            player.sendMessage(message("messages.no-active-pet"));
            return;
        }
        final String clean = name.trim();
        if (clean.isEmpty() || clean.length() > 32) {
            player.sendMessage(lang.colored("rename.length", NamedTextColor.RED));
            return;
        }
        // Only the display name changes; level, XP, abilities, and Alpaca storage are untouched.
        pet.setCustomName(clean);
        activePets.refreshDisplay(player);
        requestSave();
        player.sendMessage(lang.colored("rename.success", NamedTextColor.GREEN, "%name%", clean));
    }

    void handleRestoreName(final Player player) {
        final OwnedPet pet = storage.data(player.getUniqueId()).activePet().orElse(null);
        if (pet == null) {
            player.sendMessage(message("messages.no-active-pet"));
            return;
        }
        pet.setCustomName(null);
        activePets.refreshDisplay(player);
        requestSave();
        definitions.get(pet.definitionId()).ifPresent(definition ->
            player.sendMessage(lang.colored("rename.restored", NamedTextColor.GREEN, "%pet%", definition.name())));
    }

    private void sendHelp(final CommandSender sender) {
        sender.sendMessage(Component.empty());
        sender.sendMessage(Component.text("Better Pets Commands", NamedTextColor.GOLD).decorate(TextDecoration.BOLD));
        helpLine(sender, "/pets", "Open your pet menu", NamedTextColor.GOLD);
        helpLine(sender, "/pets scrap [all]", "Turn held (or all) duplicate pets into tokens", NamedTextColor.YELLOW);
        helpLine(sender, "/pets shop", "Spend tokens on skins, auras, trails and boosters", NamedTextColor.YELLOW);
        helpLine(sender, "/pets top", "Leaderboards: most pets, total stars, tokens", NamedTextColor.YELLOW);
        helpLine(sender, "/pets trade <player>", "Trade pets and tokens with another player", NamedTextColor.YELLOW);
        helpLine(sender, "/pets tokens", "Show your pet token balance", NamedTextColor.YELLOW);
        helpLine(sender, "/pets tokens pass <player> <amount>", "Send tokens to another player", NamedTextColor.YELLOW);
        helpLine(sender, "/pets visible | invisible", "Show or hide your active pet", NamedTextColor.YELLOW);
        helpLine(sender, "/pets mute", "Toggle pet find/booster broadcasts for yourself", NamedTextColor.YELLOW);
        helpLine(sender, "/pets set name <name>", "Rename your active pet", NamedTextColor.YELLOW);
        helpLine(sender, "/pets restore name", "Restore the default pet name", NamedTextColor.YELLOW);
        helpLine(sender, "/pets version", "Show version, modules, and update check", NamedTextColor.AQUA);
        if (has(sender, INFO_PERMISSION)) {
            helpLine(sender, "/pets info", "Open the pet catalogue", NamedTextColor.YELLOW);
        }
        if (has(sender, CHANCES_PERMISSION)) {
            helpLine(sender, "/pets chances", "Spawn chance GUI", NamedTextColor.YELLOW);
            helpLine(sender, "/pets notify", "Discovery broadcast GUI", NamedTextColor.YELLOW);
        }
        if (has(sender, GIVE_PERMISSION)) {
            helpLine(sender, "/pets give <pet|all> [level] [player]", "Give test pet items", NamedTextColor.YELLOW);
            helpLine(sender, "/pets xpboost give <x2-x5> <time> [player]", "Give Pet XP Boosters", NamedTextColor.LIGHT_PURPLE);
        }
        if (has(sender, ADMIN_PERMISSION)) {
            helpLine(sender, "/pets tokens give|remove <player|all> <amount>", "Manage pet tokens", NamedTextColor.AQUA);
            helpLine(sender, "/pets drop", "Pet source and booster drop GUI", NamedTextColor.AQUA);
            helpLine(sender, "/pets modules", "Optional modules GUI", NamedTextColor.AQUA);
            helpLine(sender, "/pets reload", "Reload config, modules, and models", NamedTextColor.AQUA);
            helpLine(sender, "/pets language <en|de|pl>", "Switch the plugin language", NamedTextColor.AQUA);
            helpLine(sender, "/pets update", "Version check / download (see config)", NamedTextColor.AQUA);
        }
        sender.sendMessage(Component.empty());
    }

    private void helpLine(final CommandSender sender, final String command, final String description, final NamedTextColor commandColor) {
        sender.sendMessage(Component.text("  ", NamedTextColor.DARK_GRAY)
            .append(Component.text(command, commandColor))
            .append(Component.text("  -  ", NamedTextColor.DARK_GRAY))
            .append(Component.text(description, NamedTextColor.GRAY)));
    }

    private List<Component> catalogueLore(final PetDefinition definition) {
        final List<Component> lore = new ArrayList<>();
        lore.add(Component.text(abilitySummary(definition.id()), NamedTextColor.GRAY));
        lore.add(Component.empty());
        lore.add(ml("menu.detail.rarity-label", NamedTextColor.GRAY)
            .append(Component.text(definition.rarity(), definition.rarityColor())));
        lore.add(ml("menu.detail.drop-weight", NamedTextColor.AQUA, "%n%", formatPercent(spawnChance(definition))));
        lore.add(Component.empty());
        lore.add(ml("menu.detail.click-milestones", NamedTextColor.YELLOW));
        return lore;
    }

    private List<Component> detailLore(final PetDefinition definition, final int level) {
        final List<Component> lore = new ArrayList<>();
        lore.add(ml("menu.detail.current-value", NamedTextColor.AQUA));
        lore.add(Component.text(abilityValue(definition.id(), level), NamedTextColor.GRAY));
        final List<String> unlocks = milestoneUnlocks(definition.id(), level);
        if (!unlocks.isEmpty()) {
            lore.add(Component.empty());
            lore.add(ml("menu.detail.unlocks", NamedTextColor.GOLD));
            unlocks.forEach(unlock -> lore.add(Component.text("+ " + unlock, NamedTextColor.YELLOW)));
        }
        if (level == 100) {
            lore.add(Component.empty());
            lore.add(ml("menu.detail.max-level", NamedTextColor.GREEN));
        }
        return lore;
    }

    private List<String> milestoneUnlocks(final String id, final int level) {
        return PetAbilities.milestones(id, level);
    }

    private String abilitySummary(final String id) {
        return PetAbilities.summary(id);
    }

    private String abilityValue(final String id, final int level) {
        return PetAbilities.value(id, level);
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
        return cachedXpMultiplier;
    }

    void refreshXpMultiplierCache() {
        cachedXpMultiplier = clampXpMultiplier(getConfig().getDouble("pet-xp-multiplier", 1.0));
    }

    private void setPetXpMultiplier(final double value) {
        final double multiplier = clampXpMultiplier(value);
        getConfig().set("pet-xp-multiplier", multiplier);
        cachedXpMultiplier = multiplier;
        recalculateAllPetExp();
        saveConfig();
        requestSave();
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

    private String formatBoosterMinutes(final int minutes) {
        final int safeMinutes = Math.max(1, minutes);
        if (safeMinutes >= 1440 && safeMinutes % 1440 == 0) {
            return (safeMinutes / 1440) + "d";
        }
        if (safeMinutes >= 60 && safeMinutes % 60 == 0) {
            return (safeMinutes / 60) + "h";
        }
        if (safeMinutes >= 60) {
            return (safeMinutes / 60) + "h " + (safeMinutes % 60) + "m";
        }
        return safeMinutes + "m";
    }

    private int maxBoosterMinutes() {
        return Math.max(1, Math.min(10080, getConfig().getInt("xp-booster.max-minutes", 1440)));
    }

    private Component message(final String path) {
        return lang.component(path);
    }

    /** Menu label in a given colour (control() removes italics). Shorthand for lang lookups in GUIs. */
    private Component ml(final String key, final NamedTextColor color, final String... repl) {
        return lang.colored(key, color, repl);
    }

    /** Gray menu lore line from lang. */
    private Component mg(final String key, final String... repl) {
        return lang.component(key, repl);
    }

    /** A plain translated menu string (for titles etc.), placeholders substituted, no styling. */
    private String mt(final String key, final String... repl) {
        return lang.raw(key, repl);
    }

    LangManager lang() {
        return lang;
    }

    void debug(final String message) {
        if (getConfig().getBoolean("debug-logging", false)) {
            getLogger().info("[Debug] " + message);
        }
    }

    /**
     * Requests a pet-data save on the next tick, coalescing bursts of interactive changes (menu clicks,
     * toggles, ...) into a single write. This keeps data durable (saved within a tick) while avoiding
     * many full-file rewrites of every player's data on the main thread in the same tick.
     */
    void requestSave() {
        if (savePending || storage == null) {
            return;
        }
        savePending = true;
        Bukkit.getScheduler().runTask(this, () -> {
            savePending = false;
            saveOpenAlpacaStorages();
            storage.save();
        });
    }

    private record PendingInput(String type, String petId) {
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
            if (args.length > 0 && (args[0].equalsIgnoreCase("language") || args[0].equalsIgnoreCase("lang"))) {
                plugin.handleLanguageCommand(sender, args);
                return;
            }
            if (args.length > 0 && args[0].equalsIgnoreCase("tokens")) {
                plugin.handleTokensCommand(sender, args);
                return;
            }
            if (args.length > 0 && args[0].equalsIgnoreCase("give")) {
                plugin.handleGiveCommand(sender, args);
                return;
            }
            if (args.length > 0 && args[0].equalsIgnoreCase("xpboost")) {
                plugin.handleXpBoostCommand(sender, args);
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

            if (args.length > 0 && args[0].equalsIgnoreCase("mute")) {
                plugin.handleMute(player);
                return;
            }
            if (args.length > 0 && (args[0].equalsIgnoreCase("visible") || args[0].equalsIgnoreCase("show"))) {
                plugin.handleVisibility(player, true);
                return;
            }
            if (args.length > 0 && (args[0].equalsIgnoreCase("invisible") || args[0].equalsIgnoreCase("unvisible") || args[0].equalsIgnoreCase("hide"))) {
                plugin.handleVisibility(player, false);
                return;
            }

            if (args.length > 0 && args[0].equalsIgnoreCase("scrap")) {
                plugin.handleScrap(player, args);
                return;
            }
            if (args.length > 0 && (args[0].equalsIgnoreCase("shop")
                || args[0].equalsIgnoreCase("slots") || args[0].equalsIgnoreCase("slot"))) {
                plugin.openShopMenu(player, "main", 0);
                return;
            }
            if (args.length > 0 && (args[0].equalsIgnoreCase("top") || args[0].equalsIgnoreCase("leaderboard"))) {
                plugin.openLeaderboardMenu(player, args.length > 1 ? args[1].toLowerCase(java.util.Locale.ROOT) : "pets");
                return;
            }
            if (args.length > 0 && (args[0].equalsIgnoreCase("trade") || args[0].equalsIgnoreCase("trades"))) {
                plugin.handleTradeCommand(player, args);
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
                suggestions.add("mute");
                suggestions.add("visible");
                suggestions.add("invisible");
                suggestions.add("tokens");
                suggestions.add("scrap");
                suggestions.add("shop");
                suggestions.add("top");
                suggestions.add("trade");
                suggestions.add("set");
                suggestions.add("restore");
                if (plugin.has(stack.getSender(), GIVE_PERMISSION)) {
                    suggestions.add("give");
                    suggestions.add("xpboost");
                }
                if (plugin.has(stack.getSender(), INFO_PERMISSION)) {
                    suggestions.add("info");
                }
                if (plugin.has(stack.getSender(), CHANCES_PERMISSION)) {
                    suggestions.add("chances");
                    suggestions.add("notify");
                }
                if (plugin.has(stack.getSender(), ADMIN_PERMISSION)) {
                    suggestions.add("drop");
                    if (plugin.experimentalModulesEnabled()) {
                        suggestions.add("modules");
                    }
                    suggestions.add("reload");
                    suggestions.add("language");
                    suggestions.add("update");
                }
                return suggestions;
            }
            if (args.length == 2 && (args[0].equalsIgnoreCase("set") || args[0].equalsIgnoreCase("restore"))) {
                return List.of("name");
            }
            if (args.length == 2 && (args[0].equalsIgnoreCase("language") || args[0].equalsIgnoreCase("lang"))) {
                return plugin.lang().languageCodes();
            }
            if (args.length == 2 && args[0].equalsIgnoreCase("scrap")) {
                return List.of("all");
            }
            if (args.length == 2 && (args[0].equalsIgnoreCase("top") || args[0].equalsIgnoreCase("leaderboard"))) {
                return List.of("pets", "stars", "tokens");
            }
            if (args.length == 2 && (args[0].equalsIgnoreCase("trade") || args[0].equalsIgnoreCase("trades"))) {
                final List<String> suggestions = new ArrayList<>(List.of("accept", "deny"));
                suggestions.addAll(Bukkit.getOnlinePlayers().stream().map(Player::getName).toList());
                return suggestions;
            }
            if (args.length == 2 && args[0].equalsIgnoreCase("tokens")) {
                final List<String> suggestions = new ArrayList<>(List.of("pass"));
                if (plugin.has(stack.getSender(), ADMIN_PERMISSION)) {
                    suggestions.add("give");
                    suggestions.add("remove");
                }
                return suggestions;
            }
            if (args.length == 3 && args[0].equalsIgnoreCase("tokens")) {
                final List<String> suggestions = new ArrayList<>();
                if (args[1].equalsIgnoreCase("give") || args[1].equalsIgnoreCase("remove")) {
                    suggestions.add("all");
                }
                suggestions.addAll(Bukkit.getOnlinePlayers().stream().map(Player::getName).toList());
                return suggestions;
            }
            if (args.length == 2 && args[0].equalsIgnoreCase("give")) {
                final List<String> suggestions = new java.util.ArrayList<>();
                suggestions.add("all");
                suggestions.addAll(plugin.definitions.ordered().stream().map(PetDefinition::id).toList());
                return suggestions;
            }
            if (args.length == 2 && args[0].equalsIgnoreCase("xpboost")) {
                return List.of("give");
            }
            if (args.length == 3 && args[0].equalsIgnoreCase("give")) {
                final List<String> suggestions = new ArrayList<>(List.of("1", "10", "50", "100"));
                suggestions.addAll(Bukkit.getOnlinePlayers().stream().map(Player::getName).toList());
                plugin.definitions.find(args[1]).ifPresent(def -> suggestions.addAll(def.variants().keySet()));
                return suggestions;
            }
            if (args.length == 3 && args[0].equalsIgnoreCase("xpboost") && args[1].equalsIgnoreCase("give")) {
                return List.of("x2", "x3", "x4", "x5");
            }
            if ((args.length == 4 || args.length == 5) && args[0].equalsIgnoreCase("give")) {
                final List<String> suggestions = new ArrayList<>(Bukkit.getOnlinePlayers().stream().map(Player::getName).toList());
                plugin.definitions.find(args[1]).ifPresent(def -> suggestions.addAll(def.variants().keySet()));
                return suggestions;
            }
            if (args.length == 4 && args[0].equalsIgnoreCase("xpboost") && args[1].equalsIgnoreCase("give")) {
                return List.of("15m", "30m", "1h", "2h", "1d");
            }
            if (args.length == 5 && args[0].equalsIgnoreCase("xpboost") && args[1].equalsIgnoreCase("give")) {
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
