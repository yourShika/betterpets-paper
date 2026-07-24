package de.kamil.betterpets;

import de.kamil.betterpets.model.PetModelHandle;
import de.kamil.betterpets.model.PetModelService;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.EntityEffect;
import org.bukkit.GameMode;
import org.bukkit.Input;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.WeatherType;
import org.bukkit.World;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.attribute.AttributeModifier;
import org.bukkit.block.Block;
import org.bukkit.block.BlockState;
import org.bukkit.boss.BarColor;
import org.bukkit.boss.BarStyle;
import org.bukkit.boss.BossBar;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Interaction;
import org.bukkit.entity.Item;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.entity.TextDisplay;
import org.bukkit.event.entity.EntityTargetLivingEntityEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.Damageable;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.util.Vector;

import java.util.Collection;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

public final class ActivePetManager {
    private static final Set<EntityType> UNDEAD = EnumSet.of(
        EntityType.ZOMBIE,
        EntityType.ZOMBIE_VILLAGER,
        EntityType.HUSK,
        EntityType.DROWNED,
        EntityType.SKELETON,
        EntityType.STRAY,
        EntityType.WITHER_SKELETON,
        EntityType.WITHER,
        EntityType.ZOMBIFIED_PIGLIN,
        EntityType.PHANTOM,
        EntityType.ZOGLIN,
        EntityType.BOGGED
    );

    private static final Set<EntityType> HOSTILE = EnumSet.of(
        EntityType.BLAZE,
        EntityType.BREEZE,
        EntityType.CAVE_SPIDER,
        EntityType.CREEPER,
        EntityType.ELDER_GUARDIAN,
        EntityType.ENDERMAN,
        EntityType.ENDERMITE,
        EntityType.EVOKER,
        EntityType.GHAST,
        EntityType.GUARDIAN,
        EntityType.HOGLIN,
        EntityType.ILLUSIONER,
        EntityType.MAGMA_CUBE,
        EntityType.PIGLIN,
        EntityType.PIGLIN_BRUTE,
        EntityType.PILLAGER,
        EntityType.RAVAGER,
        EntityType.SHULKER,
        EntityType.SILVERFISH,
        EntityType.SLIME,
        EntityType.SPIDER,
        EntityType.VEX,
        EntityType.VINDICATOR,
        EntityType.WARDEN,
        EntityType.WITCH,
        EntityType.ZOGLIN
    );

    private static final Set<String> COLD_BIOMES = Set.of(
        "snowy_taiga",
        "snowy_beach",
        "snowy_plains",
        "snowy_slopes",
        "cold_ocean",
        "frozen_ocean",
        "frozen_peaks",
        "frozen_river",
        "deep_cold_ocean",
        "deep_frozen_ocean",
        "ice_spikes"
    );

    private static final List<Attribute> MODIFIED_ATTRIBUTES = List.of(
        Attribute.ARMOR,
        Attribute.SCALE,
        Attribute.OXYGEN_BONUS,
        Attribute.ATTACK_DAMAGE,
        Attribute.FALL_DAMAGE_MULTIPLIER,
        Attribute.WATER_MOVEMENT_EFFICIENCY,
        Attribute.SUBMERGED_MINING_SPEED,
        Attribute.EXPLOSION_KNOCKBACK_RESISTANCE,
        Attribute.STEP_HEIGHT,
        Attribute.MAX_HEALTH,
        Attribute.ENTITY_INTERACTION_RANGE,
        Attribute.LUCK,
        Attribute.ATTACK_KNOCKBACK,
        Attribute.MOVEMENT_SPEED,
        Attribute.SAFE_FALL_DISTANCE,
        Attribute.JUMP_STRENGTH,
        Attribute.ATTACK_SPEED,
        Attribute.SNEAKING_SPEED,
        Attribute.SWEEPING_DAMAGE_RATIO,
        Attribute.KNOCKBACK_RESISTANCE,
        Attribute.MINING_EFFICIENCY
    );

    private static final Set<Material> CRYSTAL_GOLEM_ORES = EnumSet.of(
        Material.COAL_ORE,
        Material.DEEPSLATE_COAL_ORE,
        Material.IRON_ORE,
        Material.DEEPSLATE_IRON_ORE,
        Material.COPPER_ORE,
        Material.DEEPSLATE_COPPER_ORE,
        Material.GOLD_ORE,
        Material.DEEPSLATE_GOLD_ORE,
        Material.NETHER_GOLD_ORE,
        Material.REDSTONE_ORE,
        Material.DEEPSLATE_REDSTONE_ORE,
        Material.LAPIS_ORE,
        Material.DEEPSLATE_LAPIS_ORE,
        Material.DIAMOND_ORE,
        Material.DEEPSLATE_DIAMOND_ORE,
        Material.EMERALD_ORE,
        Material.DEEPSLATE_EMERALD_ORE,
        Material.NETHER_QUARTZ_ORE,
        Material.ANCIENT_DEBRIS,
        Material.AMETHYST_CLUSTER
    );

    // Container types the Penguin's Treasure Sense scans for. A still-present loot table means the
    // container was generated by a structure and nobody has looted it yet.
    private static final Set<Material> LOOT_CONTAINER_TYPES = EnumSet.of(
        Material.CHEST,
        Material.TRAPPED_CHEST,
        Material.BARREL
    );

    private static final String CHEST_GLOW_TAG = "BetterPets.ChestGlow";

    private static final List<PotionEffectType> PIXIE_BUFFS = List.of(
        PotionEffectType.SPEED,
        PotionEffectType.JUMP_BOOST,
        PotionEffectType.REGENERATION,
        PotionEffectType.HASTE,
        PotionEffectType.RESISTANCE,
        PotionEffectType.LUCK
    );

    private static final List<PotionEffectType> NEGATIVE_EFFECTS = List.of(
        PotionEffectType.POISON,
        PotionEffectType.WITHER,
        PotionEffectType.SLOWNESS,
        PotionEffectType.WEAKNESS,
        PotionEffectType.MINING_FATIGUE,
        PotionEffectType.NAUSEA,
        PotionEffectType.BLINDNESS,
        PotionEffectType.HUNGER,
        PotionEffectType.UNLUCK,
        PotionEffectType.DARKNESS,
        PotionEffectType.LEVITATION
    );

    private final JavaPlugin plugin;
    private final PetDefinitions definitions;
    private final PetStorage storage;
    private final PetItemFactory itemFactory;
    private final PetModelService modelService;
    private final LangManager lang;
    private final NamespacedKey ownerKey;
    private final NamespacedKey petUuidKey;
    private final NamespacedKey generatedChestKey;
    private final NamespacedKey containerOpenedKey;
    private final Map<UUID, ActivePet> activePets = new HashMap<>();
    private final Map<UUID, RideState> rides = new HashMap<>();
    // Pending mount confirmations: flight only starts on a confirming second right-click within this window.
    private final Map<UUID, Long> mountConfirms = new HashMap<>();
    private static final long MOUNT_CONFIRM_MILLIS = 2000L;
    private final Map<UUID, Set<PotionEffectType>> petBuffs = new HashMap<>();
    private final Set<UUID> herobrineWeather = new HashSet<>();
    private final Map<UUID, Set<UUID>> revealedMobs = new HashMap<>();
    // Penguin container reveal: block containers glow via owner-only BlockDisplay proxies (which have no
    // hitbox, so they never block opening the chest and cannot be attacked), chest minecarts (real
    // entities) glow via the shared per-viewer GlowController like revealed mobs.
    private final Map<UUID, Map<Long, org.bukkit.entity.BlockDisplay>> chestGlows = new HashMap<>();
    private final Map<UUID, Set<UUID>> minecartGlows = new HashMap<>();
    // Ferret ore sense: unlocked ores glow through walls via the same owner-only BlockDisplay proxies.
    private final Map<UUID, Map<Long, org.bukkit.entity.BlockDisplay>> oreGlows = new HashMap<>();
    // Kangaroo double-jump cooldown: one mid-air dash per short window per owner.
    private final Map<UUID, Long> kangarooCooldowns = new HashMap<>();
    // Ferret ore tiers, unlocked progressively by level (coal at level 1, netherite at 100).
    private static final List<Set<Material>> FERRET_ORE_TIERS = List.of(
        Set.of(Material.COAL_ORE, Material.DEEPSLATE_COAL_ORE),
        Set.of(Material.COPPER_ORE, Material.DEEPSLATE_COPPER_ORE),
        Set.of(Material.IRON_ORE, Material.DEEPSLATE_IRON_ORE, Material.LAPIS_ORE, Material.DEEPSLATE_LAPIS_ORE,
            Material.REDSTONE_ORE, Material.DEEPSLATE_REDSTONE_ORE),
        Set.of(Material.GOLD_ORE, Material.DEEPSLATE_GOLD_ORE, Material.NETHER_GOLD_ORE, Material.NETHER_QUARTZ_ORE),
        Set.of(Material.DIAMOND_ORE, Material.DEEPSLATE_DIAMOND_ORE, Material.EMERALD_ORE, Material.DEEPSLATE_EMERALD_ORE),
        Set.of(Material.ANCIENT_DEBRIS)
    );
    // Squirrel forage pool for leaves; logs always drop a stick.
    private static final List<Material> SQUIRREL_LEAF_DROPS = List.of(
        Material.OAK_SAPLING, Material.BIRCH_SAPLING, Material.SPRUCE_SAPLING,
        Material.APPLE, Material.STICK, Material.OAK_SAPLING
    );
    private final Map<UUID, BossBar> shadowBars = new HashMap<>();
    private final Map<UUID, Long> shadowAoeReadyAt = new HashMap<>();
    // Guards against infinite recursion: the AoE burst damages mobs as the player, which re-fires the
    // damage event (and applyHitAbility) — without this, that would trigger the burst again forever.
    private final Set<UUID> shadowAoeBursting = new HashSet<>();
    private final GlowController glow;
    // Per-pet behaviour registry: each pet registers only the hooks it uses. Adding a pet's behaviour
    // is a few put() calls in registerAbilities() instead of edits across six switch statements.
    private final Map<String, Behavior> passiveBehaviors = new HashMap<>();
    private final Map<String, Behavior> periodicBehaviors = new HashMap<>();
    private final Map<String, Hit> hitBehaviors = new HashMap<>();
    private final Map<String, Hit> killBehaviors = new HashMap<>();
    private final Map<String, Defense> defenseBehaviors = new HashMap<>();
    private final Map<String, Target> targetBehaviors = new HashMap<>();
    private BukkitTask task;
    private BukkitTask rideTask;
    private long tick;

    public ActivePetManager(final JavaPlugin plugin, final PetDefinitions definitions, final PetStorage storage, final PetItemFactory itemFactory, final PetModelService modelService, final LangManager lang) {
        this.plugin = plugin;
        this.definitions = definitions;
        this.storage = storage;
        this.itemFactory = itemFactory;
        this.modelService = modelService;
        this.lang = lang;
        this.ownerKey = new NamespacedKey(plugin, "active_owner");
        this.petUuidKey = new NamespacedKey(plugin, "active_pet");
        // Same keys BetterPetsPlugin uses on containers, so we can tell an unlooted container from one
        // a player has already opened (pre-generated loot clears the loot table but sets no such flag).
        this.generatedChestKey = new NamespacedKey(plugin, "pet_loot_generated");
        this.containerOpenedKey = new NamespacedKey(plugin, "container_opened");
        this.glow = new GlowController(plugin);
        registerAbilities();
    }

    public void start() {
        final int removed = cleanupStaleDisplays();
        final int interval = Math.max(1, plugin.getConfig().getInt("follow-update-ticks", 3));
        task = Bukkit.getScheduler().runTaskTimer(plugin, this::tick, interval, interval);
        // Flight is driven every tick (not every follow interval) so steering stays lag-free.
        rideTask = Bukkit.getScheduler().runTaskTimer(plugin, this::rideTick, 1L, 1L);
        Bukkit.getScheduler().runTaskLater(plugin, () -> Bukkit.getOnlinePlayers().forEach(this::spawnSavedActivePet), 20L);
        plugin.getLogger().info("Active pet manager started. Removed " + removed + " stale display(s). Follow interval: " + interval + " ticks.");
    }

    public void stop() {
        if (task != null) {
            task.cancel();
            task = null;
        }
        if (rideTask != null) {
            rideTask.cancel();
            rideTask = null;
        }
        Bukkit.getOnlinePlayers().forEach(player -> stopRide(player, false));
        activePets.values().forEach(active -> {
            active.closeModel();
            active.display().remove();
            active.hitbox().remove();
        });
        activePets.clear();
        Bukkit.getOnlinePlayers().forEach(this::clearReveal);
        Bukkit.getOnlinePlayers().forEach(this::clearChestGlow);
        Bukkit.getOnlinePlayers().forEach(this::clearOreGlow);
        Bukkit.getOnlinePlayers().forEach(this::resetPlayerState);
        shadowBars.values().forEach(BossBar::removeAll);
        shadowBars.clear();
        shadowAoeReadyAt.clear();
    }

    public void spawnSavedActivePet(final Player player) {
        storage.activePet(player.getUniqueId()).ifPresent(pet -> spawn(player, pet));
    }

    public void spawn(final Player player, final OwnedPet pet) {
        despawn(player, false);
        final PetDefinition definition = definitions.get(pet.definitionId()).orElse(null);
        if (definition == null) {
            return;
        }

        final boolean modelCandidate = modelService.canRender(definition);
        final Location location = initialPetLocation(player, definition, modelCandidate);
        final ItemDisplay display = player.getWorld().spawn(location, ItemDisplay.class, entity -> {
            entity.setItemStack(modelCandidate ? ItemStack.empty() : itemFactory.menuItem(definition, pet, true));
            entity.setItemDisplayTransform(ItemDisplay.ItemDisplayTransform.GROUND);
            entity.setBillboard(org.bukkit.entity.Display.Billboard.FIXED);
            entity.setTeleportDuration(Math.max(1, plugin.getConfig().getInt("follow-teleport-duration-ticks", 8)));
            entity.setShadowRadius(0.15F);
            entity.setViewRange(storage.data(player.getUniqueId()).visible() ? 32.0F : 0.0F);
            entity.customName(petNickname(definition, pet));
            entity.setCustomNameVisible(!modelCandidate);
            entity.setPersistent(false);
            entity.addScoreboardTag("BetterPets.Pet");
            entity.getPersistentDataContainer().set(ownerKey, PersistentDataType.STRING, player.getUniqueId().toString());
            entity.getPersistentDataContainer().set(petUuidKey, PersistentDataType.STRING, pet.uuid().toString());
        });
        final boolean visible = storage.data(player.getUniqueId()).visible();
        final PetModelHandle modelHandle = modelCandidate ? modelService.render(definition, display).orElse(null) : null;
        final String modelName = modelHandle == null ? null : modelService.modelName(definition).orElse(null);
        if (modelCandidate && modelHandle == null) {
            display.setItemStack(itemFactory.menuItem(definition, pet, true));
            display.setCustomNameVisible(true);
        }
        final TextDisplay nametag = modelHandle == null ? null : spawnModelNametag(location, definition, pet, visible, player.getUniqueId());
        if (modelHandle != null && !visible) {
            modelHandle.hideFromAll();
        }
        // Only pets with a right-click action (Alpaca storage, flyable mounts) keep a full clickable
        // hitbox; every other pet gets a tiny, non-responsive one so it never blocks mining, attacking
        // or building near it, and can never be attacked.
        final boolean interactive = isInteractivePet(pet.definitionId());
        final Interaction hitbox = player.getWorld().spawn(location, Interaction.class, entity -> {
            entity.setInteractionWidth(visible && interactive ? 1.2F : 0.1F);
            entity.setInteractionHeight(visible && interactive ? 1.8F : 0.1F);
            entity.setResponsive(false);
            entity.setPersistent(false);
            entity.addScoreboardTag("BetterPets.Pet");
            entity.getPersistentDataContainer().set(ownerKey, PersistentDataType.STRING, player.getUniqueId().toString());
            entity.getPersistentDataContainer().set(petUuidKey, PersistentDataType.STRING, pet.uuid().toString());
        });

        activePets.put(player.getUniqueId(), new ActivePet(pet, display, hitbox, player.getLocation().getYaw(), modelHandle, modelName, nametag));
        applyPassive(player, pet);
        if (plugin.getConfig().getBoolean("debug-logging", false)) {
            plugin.getLogger().info("[Debug] Spawned active pet " + definition.id() + " for " + player.getName() + (modelHandle == null ? " with head fallback." : " with BetterModel model " + modelName + "."));
        }
    }

    public void despawn(final Player player, final boolean clearActive) {
        mountConfirms.remove(player.getUniqueId());
        stopRide(player, false);
        clearReveal(player);
        clearChestGlow(player);
        clearOreGlow(player);
        clearShadowBar(player);
        final ActivePet active = activePets.remove(player.getUniqueId());
        if (active != null) {
            active.closeModel();
            active.display().remove();
            active.hitbox().remove();
        }
        if (clearActive) {
            storage.data(player.getUniqueId()).setActivePet(null);
        }
        resetPlayerState(player);
    }

    public Optional<OwnedPet> activePet(final Player player) {
        final ActivePet active = activePets.get(player.getUniqueId());
        if (active != null) {
            return Optional.of(active.pet());
        }
        return storage.activePet(player.getUniqueId());
    }

    public void refreshDisplay(final Player player) {
        final ActivePet active = activePets.get(player.getUniqueId());
        if (active == null) {
            return;
        }
        definitions.get(active.pet().definitionId()).ifPresent(definition -> {
            active.display().customName(petNickname(definition, active.pet()));
            active.updateNametag(petNickname(definition, active.pet()));
            updatePetVisual(player, active, definition);
        });
        applyPassive(player, active.pet());
    }

    public void refreshAllDisplays() {
        for (final Player player : Bukkit.getOnlinePlayers()) {
            refreshDisplay(player);
        }
    }

    /**
     * Fully rebuilds every active pet (despawn + spawn). Used when a module is toggled or on reload, so
     * a disabled model module cleanly removes its trackers and falls back to heads with no leftovers.
     */
    public void respawnAllActivePets() {
        for (final Player player : Bukkit.getOnlinePlayers()) {
            final OwnedPet pet = storage.data(player.getUniqueId()).activePet().orElse(null);
            if (pet != null) {
                spawn(player, pet);
            } else {
                despawn(player, false);
            }
        }
    }

    public void setVisible(final Player player, final boolean visible) {
        final ActivePet active = activePets.get(player.getUniqueId());
        if (active != null) {
            final boolean interactive = isInteractivePet(active.pet().definitionId());
            active.display().setViewRange(visible ? 32.0F : 0.0F);
            active.hitbox().setInteractionWidth(visible && interactive ? 1.2F : 0.1F);
            active.hitbox().setInteractionHeight(visible && interactive ? 1.8F : 0.1F);
            active.hitbox().setResponsive(false);
            active.setNametagVisible(visible);
            if (active.modelHandle() != null) {
                if (visible) {
                    active.modelHandle().showToAll();
                } else {
                    active.modelHandle().hideFromAll();
                }
            }
        }
    }

    private void updatePetVisual(final Player owner, final ActivePet active, final PetDefinition definition) {
        final boolean visible = storage.data(owner.getUniqueId()).visible();
        final String wantedModel = modelService.modelName(definition).orElse(null);
        if (modelService.canRender(definition)) {
            if (active.modelHandle() == null || !active.modelNameMatches(wantedModel)) {
                active.closeModel();
                final PetModelHandle handle = modelService.render(definition, active.display()).orElse(null);
                if (handle != null) {
                    final TextDisplay nametag = spawnModelNametag(active.display().getLocation(), definition, active.pet(), visible, owner.getUniqueId());
                    active.model(handle, wantedModel, nametag);
                    active.display().setItemStack(ItemStack.empty());
                    active.display().setCustomNameVisible(false);
                    if (visible) {
                        handle.showToAll();
                    } else {
                        handle.hideFromAll();
                    }
                    return;
                }
            } else {
                active.display().setItemStack(ItemStack.empty());
                active.display().setCustomNameVisible(false);
                ensureModelNametag(active, definition, visible);
                return;
            }
        }
        active.closeModel();
        active.display().setItemStack(itemFactory.menuItem(definition, active.pet(), true));
        active.display().setCustomNameVisible(true);
    }

    private void ensureModelNametag(final ActivePet active, final PetDefinition definition, final boolean visible) {
        if (active.nametagMissing()) {
            active.nametag(spawnModelNametag(active.display().getLocation(), definition, active.pet(), visible, ownerId(active)));
        } else {
            active.updateNametag(petNickname(definition, active.pet()));
            active.setNametagVisible(visible);
            active.teleportNametag(modelNametagLocation(active.display().getLocation()));
        }
    }

    private Location initialPetLocation(final Player player, final PetDefinition definition, final boolean modelCandidate) {
        Location location = player.getLocation().clone();
        final boolean grounded = modelCandidate && modelService.modelName(definition).map(this::groundedByModelName).orElse(false);
        if (grounded) {
            location = groundModelLocation(player, location, null);
        } else {
            location.add(0, plugin.getConfig().getDouble("follow-height", 1.2), 0);
        }
        faceTargetAtPlayer(location, player, modelCandidate);
        return location;
    }

    /**
     * Whether a model should walk on the ground or fly. Decided by the model's own animations:
     * a "flying" animation forces flying, a "walking" animation (without flying) forces grounded,
     * and a model that declares neither falls back to the model-movement-mode config value.
     */
    private boolean groundedByModelName(final String modelName) {
        if (modelName == null) {
            return false;
        }
        // Force by model name suffix (e.g. ant_grounded / ant_flying), otherwise decide by animations.
        final String lower = modelName.toLowerCase(Locale.ROOT);
        if (lower.endsWith("_grounded") || lower.endsWith("_ground")) {
            return true;
        }
        if (lower.endsWith("_flying") || lower.endsWith("_fly")) {
            return false;
        }
        final java.util.Set<String> anims = modelService.animations(modelName);
        if (anims.contains("flying")) {
            return false;
        }
        if (anims.contains("walking")) {
            return true;
        }
        return false;
    }

    private boolean isModelGrounded(final ActivePet active) {
        return groundedByModelName(active.modelName());
    }

    /**
     * Drives idle / walking / flying animations from player movement, and occasionally plays a random
     * idle2-9 variant while standing still. All driven by animations present in the .bbmodel.
     */
    private void updateModelAnimation(final Player player, final ActivePet active) {
        final PetModelHandle handle = active.modelHandle();
        if (handle == null) {
            return;
        }
        final java.util.Set<String> anims = modelService.animations(active.modelName());
        if (anims.isEmpty()) {
            return;
        }

        final Location now = player.getLocation();
        final Location last = active.lastLocation();
        final boolean moving = last != null && last.getWorld() != null && last.getWorld().equals(now.getWorld())
            && last.distanceSquared(now) > 0.0025;
        active.lastLocation(now.clone());

        if (tick < active.tempAnimationUntil()) {
            return;
        }

        final String desired;
        if (moving) {
            final boolean grounded = isModelGrounded(active);
            if (grounded && anims.contains("walking")) {
                desired = "walking";
            } else if (!grounded && anims.contains("flying")) {
                desired = "flying";
            } else {
                desired = "idle";
            }
        } else {
            if (tick % 40L == 0L && ThreadLocalRandom.current().nextDouble() < 0.2) {
                final String variant = randomIdleVariant(anims);
                if (variant != null) {
                    playModelAnimation(active, variant);
                    active.tempAnimationUntil(tick + 60L);
                    return;
                }
            }
            desired = "idle";
        }
        if (!desired.equals(active.currentAnimation())) {
            playModelAnimation(active, desired);
        }
    }

    private void playModelAnimation(final ActivePet active, final String animation) {
        try {
            active.modelHandle().play(animation);
            active.currentAnimation(animation);
        } catch (final RuntimeException | LinkageError ignored) {
            // BetterModel rejected the animation name; keep the current animation.
        }
    }

    private String randomIdleVariant(final java.util.Set<String> anims) {
        final java.util.List<String> variants = new java.util.ArrayList<>();
        for (int i = 2; i <= 9; i++) {
            if (anims.contains("idle" + i)) {
                variants.add("idle" + i);
            }
        }
        return variants.isEmpty() ? null : variants.get(ThreadLocalRandom.current().nextInt(variants.size()));
    }

    private TextDisplay spawnModelNametag(final Location base, final PetDefinition definition, final OwnedPet pet, final boolean visible, final UUID owner) {
        final Location location = modelNametagLocation(base);
        return location.getWorld().spawn(location, TextDisplay.class, entity -> {
            entity.text(petNickname(definition, pet));
            entity.setBillboard(org.bukkit.entity.Display.Billboard.CENTER);
            entity.setShadowed(true);
            entity.setSeeThrough(false);
            entity.setViewRange(visible ? 32.0F : 0.0F);
            entity.setPersistent(false);
            entity.addScoreboardTag("BetterPets.Pet");
            entity.addScoreboardTag("BetterPets.Nametag");
            entity.getPersistentDataContainer().set(ownerKey, PersistentDataType.STRING, owner.toString());
            entity.getPersistentDataContainer().set(petUuidKey, PersistentDataType.STRING, pet.uuid().toString());
        });
    }

    private UUID ownerId(final ActivePet active) {
        final String ownerText = active.display().getPersistentDataContainer().get(ownerKey, PersistentDataType.STRING);
        if (ownerText != null) {
            try {
                return UUID.fromString(ownerText);
            } catch (final IllegalArgumentException ignored) {
            }
        }
        return new UUID(0L, 0L);
    }

    public boolean handlePetInteraction(final Player player, final Entity clicked) {
        final ActivePet active = clickedActive(player, clicked).orElse(null);
        if (active == null) {
            return false;
        }
        if (!isFlyablePet(active.pet().definitionId())) {
            return true;
        }
        // While already flying, right-click does nothing - dismounting is sneak-only now.
        if (rides.containsKey(player.getUniqueId())) {
            return true;
        }
        if (active.pet().level() < 50) {
            player.sendMessage(lang.colored("flight.level-required", net.kyori.adventure.text.format.NamedTextColor.YELLOW));
            return true;
        }
        // Require a confirming second right-click so you never take off by accident.
        final long now = System.currentTimeMillis();
        final Long armed = mountConfirms.get(player.getUniqueId());
        if (armed == null || now > armed) {
            mountConfirms.put(player.getUniqueId(), now + MOUNT_CONFIRM_MILLIS);
            final String name = definitions.get(active.pet().definitionId()).map(PetDefinition::name).orElse("pet");
            player.sendMessage(lang.colored("flight.confirm-mount", net.kyori.adventure.text.format.NamedTextColor.GOLD, "%pet%", name));
            player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_HAT, 0.8F, 1.2F);
            return true;
        }
        mountConfirms.remove(player.getUniqueId());
        startRide(player, active);
        return true;
    }

    /**
     * Seats the player on their pet and lets them steer it like a real flying mount. An invisible
     * armor stand carries the player (so they sit on the pet), and it is moved every tick by velocity
     * (see {@link #driveRide}) rather than teleported. Velocity keeps the movement smooth and, crucially,
     * still collides with blocks, so the rider can no longer clip inside them. Steer by looking where you
     * want to go and holding forward; jump to climb, look down to dive, sneak to dismount.
     */
    private void startRide(final Player player, final ActivePet active) {
        if (player.getGameMode() == GameMode.SPECTATOR) {
            return;
        }
        if (player.isInsideVehicle()) {
            player.leaveVehicle();
        }
        final Location spawnAt = active.display().getLocation().clone();
        final ArmorStand mount = player.getWorld().spawn(spawnAt, ArmorStand.class, entity -> {
            entity.setVisible(false);
            entity.setGravity(false);
            entity.setInvulnerable(true);
            entity.setCollidable(false);
            entity.setSmall(true);
            entity.setBasePlate(false);
            entity.setPersistent(false);
            entity.addScoreboardTag("BetterPets.Ride");
            entity.getPersistentDataContainer().set(ownerKey, PersistentDataType.STRING, player.getUniqueId().toString());
            entity.getPersistentDataContainer().set(petUuidKey, PersistentDataType.STRING, active.pet().uuid().toString());
        });
        if (!mount.addPassenger(player)) {
            mount.remove();
            player.sendMessage(Component.text("Flight could not start here.", net.kyori.adventure.text.format.NamedTextColor.RED));
            return;
        }
        rides.put(player.getUniqueId(), new RideState(mount));
        // Teleport the pet body tightly (1-tick interpolation) so it tracks the mount without lag.
        active.display().setTeleportDuration(1);
        player.sendMessage(Component.text("Flight enabled. Look to steer, forward to fly, jump to climb, sneak to dismount.", net.kyori.adventure.text.format.NamedTextColor.GOLD));
        plugin.getLogger().info("[Debug] " + player.getName() + " started flight with " + active.pet().definitionId() + ".");
    }

    public Optional<OwnedPet> clickedActivePet(final Player player, final Entity clicked) {
        return clickedActive(player, clicked).map(ActivePet::pet);
    }

    private Optional<ActivePet> clickedActive(final Player player, final Entity clicked) {
        final ActivePet active = activePets.get(player.getUniqueId());
        if (active == null || (!clicked.equals(active.display()) && !clicked.equals(active.hitbox()))) {
            return Optional.empty();
        }
        return Optional.of(active);
    }

    public void handleRideInput(final Player player, final Input input) {
        final RideState state = rides.get(player.getUniqueId());
        if (state == null) {
            return;
        }
        if (input.isSneak()) {
            stopRide(player, true);
            return;
        }
        state.input(input);
    }

    public boolean stopRideIfSneaking(final Player player) {
        if (!rides.containsKey(player.getUniqueId())) {
            return false;
        }
        stopRide(player, true);
        return true;
    }

    public void stopRide(final Player player, final boolean notify) {
        final RideState state = rides.remove(player.getUniqueId());
        if (state == null) {
            return;
        }
        if (player.isInsideVehicle() && player.getVehicle() != null && player.getVehicle().equals(state.mount())) {
            player.leaveVehicle();
        }
        state.mount().remove();
        // The player was carried; keep them from taking fall damage on the way down.
        player.setFallDistance(0.0F);
        player.addPotionEffect(new PotionEffect(PotionEffectType.SLOW_FALLING, 100, 0, true, false, true));
        final ActivePet active = activePets.get(player.getUniqueId());
        if (active != null && !active.display().isDead()) {
            active.display().setTeleportDuration(Math.max(1, plugin.getConfig().getInt("follow-teleport-duration-ticks", 8)));
        }
        if (notify) {
            player.sendMessage(Component.text("Flight disabled.", net.kyori.adventure.text.format.NamedTextColor.GRAY));
        }
    }

    public boolean isRideMount(final Entity entity) {
        return entity != null && entity.getScoreboardTags().contains("BetterPets.Ride");
    }

    private boolean isDragon(final String id) {
        return id.equals("blue_dragon") || id.equals("red_dragon") || id.equals("ender_dragon");
    }

    private boolean isFlyablePet(final String id) {
        return isDragon(id) || id.equals("phoenix") || id.equals("shadow_dragon");
    }

    /** Pets that respond to right-click (Alpaca storage, flyable mounts) and thus keep a clickable hitbox. */
    private boolean isInteractivePet(final String id) {
        return id.equals("alpaca") || isFlyablePet(id);
    }

    /** Whether the player is currently seated on (flying) their pet. */
    public boolean isRiding(final Player player) {
        return rides.containsKey(player.getUniqueId());
    }

    public boolean isUndead(final Entity entity) {
        return UNDEAD.contains(entity.getType());
    }

    public boolean isHostile(final Entity entity) {
        return HOSTILE.contains(entity.getType()) || isUndead(entity);
    }

    public void handlePetTargeting(final EntityTargetLivingEntityEvent event) {
        if (!(event.getTarget() instanceof Player player)) {
            return;
        }
        final OwnedPet pet = activePet(player).orElse(null);
        if (pet == null) {
            return;
        }
        final Target target = targetBehaviors.get(pet.definitionId());
        if (target != null) {
            target.run(ctx(player, pet), event);
        }
    }

    private void spawnPlushieDistraction(final Player player) {
        final ActivePet active = activePets.get(player.getUniqueId());
        final Location location = (active != null ? active.display().getLocation() : player.getLocation()).clone().add(0, 0.4, 0);
        player.getWorld().spawnParticle(Particle.SOUL, location, 8, 0.2, 0.3, 0.2, 0.01);
        player.getWorld().playSound(location, Sound.ENTITY_VEX_AMBIENT, 0.7F, 0.6F);
    }

    public void applyHitAbility(final Player player, final LivingEntity victim) {
        final OwnedPet pet = activePet(player).orElse(null);
        if (pet == null) {
            return;
        }

        final Hit hit = hitBehaviors.get(pet.definitionId());
        if (hit != null) {
            hit.run(ctx(player, pet), victim);
        }
    }

    public void applyDefenseAbility(final Player player, final Entity damager) {
        final OwnedPet pet = activePet(player).orElse(null);
        if (pet == null) {
            return;
        }

        final Defense defense = defenseBehaviors.get(pet.definitionId());
        if (defense != null) {
            defense.run(ctx(player, pet), damager);
        }
    }

    private LivingEntity resolveAttacker(final Player player, final Entity damager) {
        if (damager instanceof Projectile projectile && projectile.getShooter() instanceof LivingEntity shooter) {
            return shooter.equals(player) ? null : shooter;
        }
        if (damager instanceof LivingEntity living && !living.equals(player)) {
            return living;
        }
        return null;
    }

    public void applyKillAbility(final Player player, final LivingEntity killed) {
        final OwnedPet pet = activePet(player).orElse(null);
        if (pet == null) {
            return;
        }

        final Hit kill = killBehaviors.get(pet.definitionId());
        if (kill != null) {
            kill.run(ctx(player, pet), killed);
        }
    }

    /**
     * Very low chance that an active Goblin "steals back" the emeralds a player just spent on a villager
     * trade, effectively making that purchase free. The chance rises slightly with level. The refund
     * itself is handled by the caller so it can read the trade's real emerald cost.
     */
    public boolean tryGoblinTradeSave(final Player player) {
        final OwnedPet pet = activePet(player).orElse(null);
        if (pet == null || !pet.definitionId().equals("goblin")) {
            return false;
        }
        final double chance = Math.min(0.20, 0.03 + (abilityTier(pet.level()) * 0.009));
        return ThreadLocalRandom.current().nextDouble() < chance;
    }

    private void tick() {
        tick++;
        final int abilityInterval = Math.max(20, plugin.getConfig().getInt("ability-update-ticks", 100));
        final int penguinInterval = Math.max(20, plugin.getConfig().getInt("penguin-glow-update-ticks", 40));

        for (final Player player : Bukkit.getOnlinePlayers()) {
            final PlayerPetData data = storage.data(player.getUniqueId());
            final OwnedPet pet = data.activePet().orElse(null);
            if (pet == null) {
                despawn(player, false);
                continue;
            }

            ActivePet active = activePets.get(player.getUniqueId());
            if (active == null || active.display().isDead() || active.hitbox().isDead() || active.modelNametagDead() || !active.pet().uuid().equals(pet.uuid())) {
                spawn(player, pet);
                active = activePets.get(player.getUniqueId());
            }
            if (active == null) {
                continue;
            }

            final RideState ride = rides.get(player.getUniqueId());
            if (ride == null) {
                // While riding, the per-tick rideTick() keeps the pet pinned to the mount instead.
                follow(player, active);
            }
            final boolean petVisible = data.visible();
            if (petVisible && ride != null && tick % 4L == 0L) {
                spawnDragonTrail(player, pet);
            } else if (petVisible && ride == null && isDragon(pet.definitionId()) && tick % 6L == 0L) {
                spawnPetWalkTrail(player, pet, active);
            }
            if (petVisible && pet.definitionId().equals("unicorn") && pet.level() >= 50 && tick % 6L == 0L) {
                spawnUnicornGlitter(player);
            }
            if (petVisible && ride == null && tick % 8L == 0L) {
                spawnAmbientPetParticle(pet, active);
            }
            if (active.modelHandle() != null) {
                updateModelAnimation(player, active);
            }
            if (tick % 2L == 0L) {
                updateReveals(player, pet);
            }
            if (pet.definitionId().equals("allay") && tick % 10L == 0L) {
                collectAllayItems(player, Math.min(12.0, 4.0 + (abilityTier(pet.level()) * 0.4)));
            }
            if (pet.definitionId().equals("penguin")) {
                if (tick % penguinInterval == 0L) {
                    updatePenguinChestGlow(player, pet);
                }
            } else if (chestGlows.containsKey(player.getUniqueId()) || minecartGlows.containsKey(player.getUniqueId())) {
                clearChestGlow(player);
            }
            if (pet.definitionId().equals("ferret")) {
                if (tick % penguinInterval == 0L) {
                    updateFerretOreGlow(player, pet);
                }
            } else if (oreGlows.containsKey(player.getUniqueId())) {
                clearOreGlow(player);
            }
            if (pet.definitionId().equals("shadow_dragon")) {
                updateShadowBar(player, pet);
            } else {
                clearShadowBar(player);
            }
            if (pet.definitionId().equals("ancient_elf")) {
                cleanseDebuffs(player, pet.level());
            }
            if (tick % abilityInterval == 0L) {
                applyPassive(player, pet);
                applyPeriodicAbilities(player, pet);
            }
        }
    }

    private void follow(final Player player, final ActivePet active) {
        final ItemDisplay display = active.display();
        final PlayerPetData data = storage.data(player.getUniqueId());
        display.setViewRange(data.visible() ? 32.0F : 0.0F);

        final Location target = followLocation(player, active, tick);
        final double teleportDistance = Math.max(4.0, plugin.getConfig().getDouble("follow-teleport-distance", 24.0));
        if (!display.getWorld().equals(player.getWorld()) || display.getLocation().distanceSquared(target) > teleportDistance * teleportDistance) {
            active.followYaw(player.getLocation().getYaw());
            teleportActive(active, target);
            return;
        }
        if (display.getLocation().distanceSquared(target) > 0.10) {
            teleportActive(active, target);
        }
    }

    private void teleportActive(final ActivePet active, final Location target) {
        active.display().teleport(target);
        active.hitbox().teleport(target);
        active.teleportNametag(modelNametagLocation(target));
    }

    /** Runs every tick: drives each rider's mount and keeps the pet body pinned to it. */
    private void rideTick() {
        if (rides.isEmpty()) {
            return;
        }
        for (final Map.Entry<UUID, RideState> entry : new java.util.ArrayList<>(rides.entrySet())) {
            final Player player = Bukkit.getPlayer(entry.getKey());
            if (player == null || !player.isOnline()) {
                continue;
            }
            final ActivePet active = activePets.get(entry.getKey());
            if (active == null) {
                stopRide(player, false);
                continue;
            }
            driveRide(player, active, entry.getValue());
            if (rides.containsKey(entry.getKey())) {
                repositionRidePet(active, entry.getValue());
            }
        }
    }

    /**
     * Moves the mount from the rider's held inputs and look direction. Armor stands ignore
     * {@link org.bukkit.entity.Entity#setVelocity} (especially with gravity off), so the mount is moved
     * by a small teleport every tick instead. Because this runs every tick (not every follow interval)
     * the motion stays smooth, and each step is checked against block collision first so the rider slides
     * along walls instead of clipping into them. No input means the mount simply hovers in place.
     */
    private void driveRide(final Player player, final ActivePet active, final RideState ride) {
        final ArmorStand mount = ride.mount();
        if (mount.isDead() || !mount.getPassengers().contains(player)
            || player.getGameMode() == GameMode.SPECTATOR || !mount.getWorld().equals(player.getWorld())) {
            stopRide(player, false);
            return;
        }
        final Location eye = player.getLocation();
        final Vector look = eye.getDirection();
        final Vector move = new Vector();
        if (ride.forward()) {
            move.add(look);
        }
        if (ride.backward()) {
            move.subtract(look);
        }
        Vector flat = new Vector(look.getX(), 0, look.getZ());
        if (flat.lengthSquared() < 1.0e-4) {
            flat = new Vector(0, 0, 1);
        }
        flat.normalize();
        final Vector right = new Vector(-flat.getZ(), 0, flat.getX());
        if (ride.right()) {
            move.add(right);
        }
        if (ride.left()) {
            move.subtract(right);
        }
        final double speed = Math.max(0.05, plugin.getConfig().getDouble("flight-speed", 0.6));
        if (move.lengthSquared() > 1.0e-4) {
            move.normalize().multiply(speed);
        }
        if (ride.jump()) {
            move.setY(move.getY() + Math.max(0.1, plugin.getConfig().getDouble("flight-lift", 0.5)));
        }
        final float yaw = eye.getYaw();
        final Location base = mount.getLocation();
        if (move.lengthSquared() < 1.0e-6) {
            // Hovering: keep the mount in place but still face the player's heading.
            mount.setRotation(yaw, 0.0F);
            return;
        }
        // Try the full step, then slide along walls (horizontal-only, then vertical-only) so a blocked
        // direction never stops the whole movement and never teleports the rider into a solid block.
        if (!tryMoveMount(mount, base, move.getX(), move.getY(), move.getZ(), yaw)
            && !tryMoveMount(mount, base, move.getX(), 0.0, move.getZ(), yaw)
            && !tryMoveMount(mount, base, 0.0, move.getY(), 0.0, yaw)) {
            mount.setRotation(yaw, 0.0F);
        }
    }

    private boolean tryMoveMount(final ArmorStand mount, final Location base, final double dx, final double dy, final double dz, final float yaw) {
        if (dx == 0.0 && dy == 0.0 && dz == 0.0) {
            return false;
        }
        final Location target = base.clone().add(dx, dy, dz);
        target.setYaw(yaw);
        target.setPitch(0.0F);
        if (!isRideLocationSafe(target)) {
            return false;
        }
        mount.teleport(target);
        return true;
    }

    private boolean isRideLocationSafe(final Location location) {
        final World world = location.getWorld();
        if (world == null || location.getY() <= world.getMinHeight() + 1) {
            return false;
        }
        // No build-height cap: you can fly well above the world's max height (empty air up there).
        // A generous configurable ceiling only guards against absurd Y values.
        if (location.getY() >= plugin.getConfig().getDouble("flight-max-height", 1024.0)) {
            return false;
        }
        // Above the build limit there are no blocks, so the bounding-box block checks below are skipped.
        if (location.getY() >= world.getMaxHeight()) {
            return true;
        }
        // Sample the rider's rough bounding box (centre + edges, at foot and body height) so you can't
        // clip a shoulder or head into a wall and get stuck.
        final double radius = 0.35;
        final double[][] offsets = {{0, 0}, {radius, 0}, {-radius, 0}, {0, radius}, {0, -radius}};
        for (final double[] offset : offsets) {
            final Location foot = location.clone().add(offset[0], 0, offset[1]);
            if (!foot.getBlock().isPassable() || !foot.clone().add(0, 1, 0).getBlock().isPassable()) {
                return false;
            }
        }
        return true;
    }

    private void repositionRidePet(final ActivePet active, final RideState ride) {
        final ArmorStand mount = ride.mount();
        if (mount.isDead() || active.display().isDead()) {
            return;
        }
        final Location target = mount.getLocation().clone().add(0, plugin.getConfig().getDouble("flight-pet-offset", -0.4), 0);
        target.setYaw(mount.getYaw());
        target.setPitch(0.0F);
        active.display().teleport(target);
        active.hitbox().teleport(target);
        active.teleportNametag(modelNametagLocation(target));
    }

    private Location followLocation(final Player player, final ActivePet active, final long tick) {
        final Location base = player.getLocation().clone();
        final Location displayLocation = active.display().getLocation();
        final double desiredDistance = Math.max(1.0, plugin.getConfig().getDouble("follow-distance", 2.4));
        Vector offset = displayLocation.toVector().subtract(base.toVector());
        offset.setY(0);

        if (!displayLocation.getWorld().equals(base.getWorld()) || offset.lengthSquared() < 0.16) {
            final Vector direction = base.getDirection().clone();
            direction.setY(0);
            if (direction.lengthSquared() < 0.01) {
                direction.setZ(1);
            }
            direction.normalize();
            final Vector side = new Vector(-direction.getZ(), 0, direction.getX())
                .normalize()
                .multiply(plugin.getConfig().getDouble("follow-side-offset", 0.75));
            offset = direction.multiply(-desiredDistance).add(side);
        } else {
            final double distance = offset.length();
            if (distance > desiredDistance * 1.35 || distance < desiredDistance * 0.55) {
                offset.normalize().multiply(desiredDistance);
            }
        }

        final double bob = Math.sin(tick / 8.0) * 0.12;
        Location target = base.add(offset);
        final boolean modelGrounded = active.modelHandle() != null && isModelGrounded(active);
        if (modelGrounded) {
            target = groundModelLocation(player, target, active);
        } else {
            target.add(0, plugin.getConfig().getDouble("follow-height", 1.2) + bob, 0);
        }
        faceTargetAtPlayer(target, player, active.modelHandle() != null);
        return target;
    }

    private Location groundModelLocation(final Player player, final Location target, final ActivePet active) {
        final int blockX = target.getBlockX();
        final int blockZ = target.getBlockZ();
        // Reuse the last ground height while the pet stays in the same block column, so a standing pet
        // does not re-scan the ground every follow tick. The scan only runs when the column changes.
        if (active != null) {
            final Double cached = active.cachedGroundY(blockX, blockZ);
            if (cached != null) {
                target.setY(cached);
                return target;
            }
        }
        final World world = target.getWorld();
        final int startY = Math.min(world.getMaxHeight() - 2, Math.max(world.getMinHeight() + 2, player.getLocation().getBlockY() + 3));
        final int minY = Math.max(world.getMinHeight() + 1, player.getLocation().getBlockY() - 12);
        for (int y = startY; y >= minY; y--) {
            final Block feet = world.getBlockAt(blockX, y, blockZ);
            final Block head = world.getBlockAt(blockX, y + 1, blockZ);
            final Block below = world.getBlockAt(blockX, y - 1, blockZ);
            if (feet.isPassable() && head.isPassable() && !below.isPassable()) {
                final double groundY = y + Math.max(0.0, plugin.getConfig().getDouble("model-ground-offset", 0.05));
                if (active != null) {
                    active.cacheGroundY(blockX, blockZ, groundY);
                }
                target.setY(groundY);
                return target;
            }
        }
        target.add(0, plugin.getConfig().getDouble("follow-height", 1.2), 0);
        return target;
    }

    private void faceTargetAtPlayer(final Location target, final Player player, final boolean model) {
        // IMPORTANT: do not "simplify" this to playerEye - target.
        // The floating pet head renders its face on the side OPPOSITE the entity's facing direction,
        // so to make the pet look AT the player we must point the entity AWAY from the player (its face
        // then turns toward the player). Using playerEye - target makes pets stare where the player
        // looks instead of at the player (regression that happened in 1.2.2, fixed again in 1.2.4).
        final Vector awayFromPlayer = target.toVector().subtract(player.getEyeLocation().toVector());
        if (awayFromPlayer.lengthSquared() < 0.01) {
            awayFromPlayer.setZ(1);
        }
        target.setDirection(awayFromPlayer);
        if (model) {
            target.setYaw(target.getYaw() + (float) plugin.getConfig().getDouble("model-facing-yaw-offset-degrees", 0.0));
        }
    }

    private Location modelNametagLocation(final Location base) {
        return base.clone().add(0, Math.max(0.5, plugin.getConfig().getDouble("model-nametag-height", 2.2)), 0);
    }

    private void spawnUnicornGlitter(final Player player) {
        final ThreadLocalRandom random = ThreadLocalRandom.current();
        final Color color = Color.fromRGB(random.nextInt(80, 256), random.nextInt(80, 256), random.nextInt(80, 256));
        player.getWorld().spawnParticle(
            Particle.DUST,
            player.getLocation().add(0, 0.5, 0),
            10,
            0.5,
            0.3,
            0.5,
            0.0,
            new Particle.DustOptions(color, 1.1F)
        );
    }

    /**
     * A cosmetic ambient particle aura for every Epic-and-above pet, themed to fit the pet, shown while
     * the pet is visible. Dragons and the Unicorn are handled by their own trail/glitter effects and are
     * intentionally left out here so effects never stack.
     */
    private void spawnAmbientPetParticle(final OwnedPet pet, final ActivePet active) {
        final Location loc = active.display().getLocation().clone().add(0, 0.45, 0);
        final World world = loc.getWorld();
        if (world == null) {
            return;
        }
        switch (pet.definitionId()) {
            // Epic
            case "dolphin" -> world.spawnParticle(Particle.BUBBLE_POP, loc, 6, 0.25, 0.3, 0.25, 0.0);
            case "otter" -> world.spawnParticle(Particle.SPLASH, loc, 6, 0.25, 0.2, 0.25, 0.0);
            case "ghast" -> world.spawnParticle(Particle.LARGE_SMOKE, loc, 3, 0.2, 0.2, 0.2, 0.0);
            case "owl" -> world.spawnParticle(Particle.END_ROD, loc, 4, 0.2, 0.3, 0.2, 0.0);
            case "panda" -> world.spawnParticle(Particle.HAPPY_VILLAGER, loc, 4, 0.25, 0.3, 0.25, 0.0);
            case "rabbit" -> world.spawnParticle(Particle.CLOUD, loc, 3, 0.2, 0.1, 0.2, 0.0);
            case "tiger" -> world.spawnParticle(Particle.CRIT, loc, 5, 0.25, 0.3, 0.25, 0.0);
            case "red_panda" -> world.spawnParticle(Particle.FALLING_SPORE_BLOSSOM, loc, 4, 0.3, 0.3, 0.3, 0.0);
            case "allay" -> world.spawnParticle(Particle.GLOW, loc, 4, 0.25, 0.3, 0.25, 0.0);
            case "crystal_golem" -> world.spawnParticle(Particle.END_ROD, loc, 3, 0.2, 0.3, 0.2, 0.0);
            case "moon_fox" -> world.spawnParticle(Particle.SOUL_FIRE_FLAME, loc, 3, 0.2, 0.3, 0.2, 0.0);
            case "goblin" -> {
                world.spawnParticle(Particle.HAPPY_VILLAGER, loc, 3, 0.25, 0.3, 0.25, 0.0);
                world.spawnParticle(Particle.WAX_ON, loc, 3, 0.2, 0.25, 0.2, 0.0);
            }
            case "pixie" -> {
                world.spawnParticle(Particle.WAX_OFF, loc, 6, 0.3, 0.35, 0.3, 0.0);
                world.spawnParticle(Particle.END_ROD, loc, 2, 0.2, 0.3, 0.2, 0.0);
            }
            // Legendary
            case "penguin" -> world.spawnParticle(Particle.SNOWFLAKE, loc, 6, 0.25, 0.25, 0.25, 0.0);
            case "warden" -> world.spawnParticle(Particle.SCULK_SOUL, loc, 3, 0.2, 0.3, 0.2, 0.0);
            case "unicorn" -> world.spawnParticle(Particle.END_ROD, loc, 3, 0.25, 0.3, 0.25, 0.0);
            case "cursed_plushie" -> world.spawnParticle(Particle.SOUL, loc, 3, 0.2, 0.3, 0.2, 0.0);
            case "lich" -> {
                world.spawnParticle(Particle.SOUL, loc, 4, 0.25, 0.35, 0.25, 0.01);
                world.spawnParticle(Particle.SOUL_FIRE_FLAME, loc, 2, 0.2, 0.3, 0.2, 0.0);
            }
            // Mythical
            case "phoenix" -> {
                world.spawnParticle(Particle.FLAME, loc, 4, 0.2, 0.3, 0.2, 0.01);
                world.spawnParticle(Particle.SMALL_FLAME, loc, 3, 0.2, 0.25, 0.2, 0.0);
            }
            case "shadow_dragon" -> world.spawnParticle(Particle.SMOKE, loc, 5, 0.25, 0.3, 0.25, 0.0);
            case "herobrine" -> world.spawnParticle(Particle.SOUL_FIRE_FLAME, loc, 3, 0.2, 0.3, 0.2, 0.0);
            case "reaper" -> world.spawnParticle(Particle.SOUL, loc, 4, 0.25, 0.3, 0.25, 0.0);
            case "ancient_elf" -> world.spawnParticle(Particle.ENCHANT, loc, 6, 0.3, 0.35, 0.3, 0.0);
            default -> {
            }
        }
    }

    private void spawnDragonTrail(final Player player, final OwnedPet pet) {
        final Location location = player.getLocation().add(0, 0.8, 0);
        try {
            switch (pet.definitionId()) {
                case "ender_dragon" -> player.getWorld().spawnParticle(Particle.DRAGON_BREATH, location, 14, 0.55, 0.35, 0.55, 0.01, 1.0F);
                case "blue_dragon" -> player.getWorld().spawnParticle(Particle.ENCHANT, location, 22, 0.65, 0.45, 0.65, 0.0);
                case "red_dragon" -> player.getWorld().spawnParticle(Particle.FLAME, location, 14, 0.45, 0.3, 0.45, 0.01);
                case "phoenix" -> {
                    player.getWorld().spawnParticle(Particle.FLAME, location, 16, 0.5, 0.35, 0.5, 0.01);
                    player.getWorld().spawnParticle(Particle.LAVA, location, 2, 0.4, 0.2, 0.4, 0.0);
                }
                case "shadow_dragon" -> {
                    player.getWorld().spawnParticle(Particle.SMOKE, location, 18, 0.6, 0.4, 0.6, 0.01);
                    player.getWorld().spawnParticle(Particle.WITCH, location, 10, 0.5, 0.35, 0.5, 0.0);
                }
                default -> {
                }
            }
        } catch (final IllegalArgumentException exception) {
            if (pet.definitionId().equals("ender_dragon")) {
                player.getWorld().spawnParticle(Particle.PORTAL, location, 16, 0.55, 0.35, 0.55, 0.0);
            }
        }
    }

    private long shadowAoeCooldownMillis(final int level) {
        // ~12s at low level down to 4s at level 100 — a higher level shortens the cooldown.
        return Math.max(4000L, 12000L - (level * 80L));
    }

    /**
     * Keeps the Shadow Dragon's boss bar in sync each tick. The bar only *shows* the cooldown state — it
     * never triggers the AoE. When ready it invites the player to attack; while on cooldown it fills up
     * again. The burst itself is fired from {@link #tryShadowAoeOnAttack} when the player lands a hit.
     */
    private void updateShadowBar(final Player player, final OwnedPet pet) {
        final UUID id = player.getUniqueId();
        final long now = System.currentTimeMillis();
        final long cooldown = shadowAoeCooldownMillis(pet.level());
        final long readyAt = shadowAoeReadyAt.getOrDefault(id, 0L);

        final BossBar bar = shadowBars.computeIfAbsent(id, ignored -> {
            final BossBar created = Bukkit.createBossBar("Shadow Dragon AOE ready — attack!", BarColor.PURPLE, BarStyle.SEGMENTED_10);
            created.addPlayer(player);
            return created;
        });
        if (!bar.getPlayers().contains(player)) {
            bar.addPlayer(player);
        }

        if (now >= readyAt) {
            bar.setTitle("Shadow Dragon AOE ready — attack!");
            bar.setProgress(1.0);
        } else {
            bar.setTitle("Cooldown to AOE Damage");
            bar.setProgress(Math.max(0.0, Math.min(1.0, 1.0 - ((readyAt - now) / (double) cooldown))));
        }
    }

    /**
     * Fires the Shadow Dragon's AoE burst when the owner lands a melee hit, but only if the cooldown has
     * elapsed. After firing, the cooldown runs until the player attacks again. The cooldown shrinks with
     * level (see {@link #shadowAoeCooldownMillis}).
     */
    private void tryShadowAoeOnAttack(final Player player, final OwnedPet pet) {
        final UUID id = player.getUniqueId();
        final long now = System.currentTimeMillis();
        // The burst itself deals damage as the player, which re-enters this method; skip while bursting.
        if (shadowAoeBursting.contains(id) || now < shadowAoeReadyAt.getOrDefault(id, 0L)) {
            return;
        }
        // Set the cooldown before firing so the re-entrant damage events see it as on cooldown too.
        shadowAoeReadyAt.put(id, now + shadowAoeCooldownMillis(pet.level()));
        shadowAoeBursting.add(id);
        try {
            triggerShadowAoe(player, pet, storage.data(id).visible());
        } finally {
            shadowAoeBursting.remove(id);
        }
    }

    private void triggerShadowAoe(final Player player, final OwnedPet pet, final boolean visible) {
        final int level = pet.level();
        final double radius = level >= 100 ? 8.0 : level >= 50 ? 6.0 : 4.0;
        final double damage = 1.5 + (abilityTier(level) * 0.3);
        for (final Entity entity : player.getNearbyEntities(radius, radius, radius)) {
            if (entity instanceof LivingEntity living && !living.equals(player) && isHostile(living)) {
                living.damage(damage, player);
            }
        }
        if (visible) {
            spawnShadowCircle(player, radius);
        }
        player.getWorld().playSound(player.getLocation(), Sound.ENTITY_WITHER_SHOOT, 0.6F, 1.2F);
    }

    /** A short-lived ring of shadow particles at the player's feet, marking the AoE reach. */
    private void spawnShadowCircle(final Player player, final double radius) {
        final Location base = player.getLocation();
        final World world = player.getWorld();
        final int points = (int) Math.max(20, radius * 8);
        for (int i = 0; i < points; i++) {
            final double angle = (2 * Math.PI * i) / points;
            final Location at = base.clone().add(Math.cos(angle) * radius, 0.2, Math.sin(angle) * radius);
            world.spawnParticle(Particle.SMOKE, at, 1, 0.0, 0.02, 0.0, 0.0);
            world.spawnParticle(Particle.WITCH, at, 1, 0.0, 0.05, 0.0, 0.0);
        }
    }

    private void clearShadowBar(final Player player) {
        final BossBar bar = shadowBars.remove(player.getUniqueId());
        if (bar != null) {
            bar.removeAll();
        }
        shadowAoeReadyAt.remove(player.getUniqueId());
    }

    private Component petNickname(final PetDefinition definition, final OwnedPet pet) {
        final String name = pet.hasCustomName() ? pet.customName() : definition.name();
        return Component.text("[Lvl " + pet.level() + "] " + name, definition.rarityColor())
            .decoration(TextDecoration.ITALIC, false);
    }

    private void applyPeriodicAbilities(final Player player, final OwnedPet pet) {
        // shadow_dragon AoE is driven by the cooldown/boss-bar system, not a periodic behaviour here.
        final Behavior behavior = periodicBehaviors.get(pet.definitionId());
        if (behavior != null) {
            behavior.run(ctx(player, pet));
        }
    }

    public static long phoenixCooldownMillis(final int level) {
        if (level >= 100) {
            return 43_200_000L; // 12 hours
        }
        if (level >= 50) {
            return 64_800_000L; // 18 hours
        }
        return 86_400_000L; // 24 hours
    }

    /**
     * Saves the player from a lethal hit, exactly as if they had been holding a Totem of Undying.
     * This is on-demand (triggered by the damage event), so it works even if the server was idle or
     * asleep, unlike a real-time timer. Returns true if the player was revived.
     */
    public boolean tryPhoenixRevive(final Player player) {
        final OwnedPet pet = activePet(player).orElse(null);
        if (pet == null || !pet.definitionId().equals("phoenix")) {
            return false;
        }
        final long now = System.currentTimeMillis();
        if (now - pet.lastTotemMillis() < phoenixCooldownMillis(pet.level())) {
            return false;
        }
        pet.setLastTotemMillis(now);

        final AttributeInstance maxHealth = player.getAttribute(Attribute.MAX_HEALTH);
        player.setHealth(Math.min(maxHealth == null ? 1.0 : maxHealth.getValue(), 1.0));
        player.setFireTicks(0);
        player.setFreezeTicks(0);
        for (final PotionEffectType negative : NEGATIVE_EFFECTS) {
            player.removePotionEffect(negative);
        }
        player.addPotionEffect(new PotionEffect(PotionEffectType.REGENERATION, 900, 1, true, true, true));
        player.addPotionEffect(new PotionEffect(PotionEffectType.ABSORPTION, 100, 1, true, true, true));
        player.addPotionEffect(new PotionEffect(PotionEffectType.FIRE_RESISTANCE, 800, 0, true, true, true));
        player.playEffect(EntityEffect.TOTEM_RESURRECT);
        player.getWorld().playSound(player.getLocation(), Sound.ITEM_TOTEM_USE, 1.0F, 1.0F);
        player.sendMessage(lang.colored("pet.phoenix-saved", net.kyori.adventure.text.format.NamedTextColor.GOLD));
        storage.save();
        return true;
    }

    public void handleMoleBreak(final Player player, final Block block) {
        final OwnedPet pet = activePet(player).orElse(null);
        if (pet == null || !pet.definitionId().equals("mole")) {
            return;
        }
        final ItemStack tool = player.getInventory().getItemInMainHand();
        // Works for any block broken with an axe, pickaxe or shovel — saves durability generally.
        if (!isDurabilityTool(tool.getType())) {
            return;
        }
        final double chance = Math.min(0.6, 0.15 + (abilityTier(pet.level()) * 0.025));
        if (ThreadLocalRandom.current().nextDouble() >= chance) {
            return;
        }
        if (!(tool.getItemMeta() instanceof Damageable)) {
            return;
        }
        final Material toolType = tool.getType();
        final int before = ((Damageable) tool.getItemMeta()).getDamage();
        // The tool's durability is consumed right after this break, so refund the lost point next tick.
        Bukkit.getScheduler().runTask(plugin, () -> {
            final ItemStack current = player.getInventory().getItemInMainHand();
            if (current.getType() != toolType || !(current.getItemMeta() instanceof Damageable currentDamageable)) {
                return;
            }
            if (currentDamageable.getDamage() > before) {
                currentDamageable.setDamage(before);
                current.setItemMeta(currentDamageable);
            }
        });
    }

    public void handleOreBonus(final Player player, final Block block) {
        final OwnedPet pet = activePet(player).orElse(null);
        if (pet == null || !pet.definitionId().equals("crystal_golem") || !CRYSTAL_GOLEM_ORES.contains(block.getType())) {
            return;
        }
        final double chance = Math.min(0.6, 0.15 + (abilityTier(pet.level()) * 0.02));
        if (ThreadLocalRandom.current().nextDouble() >= chance) {
            return;
        }
        final ItemStack tool = player.getInventory().getItemInMainHand();
        final Collection<ItemStack> drops = block.getDrops(tool, player);
        if (drops.isEmpty()) {
            return;
        }
        final Location center = block.getLocation().add(0.5, 0.5, 0.5);
        for (final ItemStack drop : drops) {
            block.getWorld().dropItemNaturally(center, drop.clone());
        }
        block.getWorld().spawnParticle(Particle.HAPPY_VILLAGER, center, 10, 0.3, 0.3, 0.3, 0.0);
        block.getWorld().playSound(center, Sound.BLOCK_AMETHYST_BLOCK_CHIME, 0.6F, 1.4F);
    }

    private void collectAllayItems(final Player player, final double radius) {
        final UUID ownerId = player.getUniqueId();
        for (final Entity entity : player.getNearbyEntities(radius, radius, radius)) {
            if (!(entity instanceof Item itemEntity) || itemEntity.getPickupDelay() > 0 || !itemEntity.canPlayerPickup()) {
                continue;
            }
            // Never pull items that another player dropped or that are reserved for someone else.
            if (itemEntity.getThrower() != null && !itemEntity.getThrower().equals(ownerId)) {
                continue;
            }
            if (itemEntity.getOwner() != null && !itemEntity.getOwner().equals(ownerId)) {
                continue;
            }
            final ItemStack stack = itemEntity.getItemStack();
            final Map<Integer, ItemStack> leftover = player.getInventory().addItem(stack);
            if (leftover.isEmpty()) {
                itemEntity.remove();
                player.playSound(player.getLocation(), Sound.ENTITY_ITEM_PICKUP, 0.6F, 1.6F);
            } else {
                itemEntity.setItemStack(leftover.values().iterator().next());
            }
        }
    }

    /**
     * The Penguin's Treasure Sense: makes every nearby container a player has never opened glow through
     * walls, for the owner only. Block containers (chests, trapped chests, barrels) glow via an invisible
     * glowing Shulker shown only to the owner; chest minecarts are entities and glow via the per-viewer
     * GlowController like revealed mobs. A container counts as "unopened" while its loot table is still
     * unrolled, or — for worlds that pre-generate loot at chunk generation, which clears the loot table
     * and would otherwise look "opened" — while our loot marker is set but no player has opened it yet.
     * Reach grows with level; only already-loaded chunks are scanned.
     */
    private void updatePenguinChestGlow(final Player player, final OwnedPet pet) {
        final World world = player.getWorld();
        final Location center = player.getLocation();
        final double radius = Math.min(24.0, 8.0 + (abilityTier(pet.level()) * 0.5));
        final double radiusSq = radius * radius;

        // Block containers: keep one owner-only glowing BlockDisplay per unopened container in range.
        final Set<Long> desiredBlocks = new HashSet<>();
        final Map<Long, org.bukkit.entity.BlockDisplay> playerGlows =
            chestGlows.computeIfAbsent(player.getUniqueId(), ignored -> new HashMap<>());
        final int chunkRadius = (int) Math.ceil(radius / 16.0);
        final int baseX = center.getBlockX() >> 4;
        final int baseZ = center.getBlockZ() >> 4;
        for (int cx = baseX - chunkRadius; cx <= baseX + chunkRadius; cx++) {
            for (int cz = baseZ - chunkRadius; cz <= baseZ + chunkRadius; cz++) {
                if (!world.isChunkLoaded(cx, cz)) {
                    continue;
                }
                for (final BlockState state : world.getChunkAt(cx, cz)
                    .getTileEntities(block -> LOOT_CONTAINER_TYPES.contains(block.getType()), false)) {
                    if (state.getLocation().distanceSquared(center) > radiusSq || !isUnopenedBlockContainer(state)) {
                        continue;
                    }
                    final long key = blockKey(state);
                    desiredBlocks.add(key);
                    final org.bukkit.entity.BlockDisplay existing = playerGlows.get(key);
                    if (existing == null || existing.isDead()) {
                        final org.bukkit.entity.BlockDisplay spawned = spawnChestGlow(player, state);
                        if (spawned != null) {
                            playerGlows.put(key, spawned);
                        }
                    }
                }
            }
        }
        playerGlows.entrySet().removeIf(entry -> {
            if (!desiredBlocks.contains(entry.getKey())) {
                if (entry.getValue() != null && !entry.getValue().isDead()) {
                    entry.getValue().remove();
                }
                return true;
            }
            return false;
        });
        if (playerGlows.isEmpty()) {
            chestGlows.remove(player.getUniqueId());
        }

        // Chest minecarts are entities, so glow them directly for the owner like revealed mobs.
        final Set<UUID> desiredCarts = new HashSet<>();
        for (final Entity entity : player.getNearbyEntities(radius, radius, radius)) {
            if (entity instanceof org.bukkit.entity.minecart.StorageMinecart cart && isUnopenedMinecart(cart)) {
                desiredCarts.add(cart.getUniqueId());
            }
        }
        reconcileMinecartGlow(player, desiredCarts);
    }

    private boolean isUnopenedBlockContainer(final BlockState state) {
        if (state instanceof org.bukkit.persistence.PersistentDataHolder holder) {
            final org.bukkit.persistence.PersistentDataContainer pdc = holder.getPersistentDataContainer();
            if (pdc.has(containerOpenedKey, PersistentDataType.BYTE)) {
                return false;
            }
            if (state instanceof org.bukkit.loot.Lootable lootable && lootable.hasLootTable()) {
                return true;
            }
            return pdc.has(generatedChestKey, PersistentDataType.BYTE);
        }
        return state instanceof org.bukkit.loot.Lootable lootable && lootable.hasLootTable();
    }

    private boolean isUnopenedMinecart(final org.bukkit.entity.minecart.StorageMinecart cart) {
        if (cart.getPersistentDataContainer().has(containerOpenedKey, PersistentDataType.BYTE)) {
            return false;
        }
        return cart instanceof org.bukkit.loot.Lootable lootable && lootable.hasLootTable();
    }

    private org.bukkit.entity.BlockDisplay spawnChestGlow(final Player player, final BlockState state) {
        // A BlockDisplay has no hitbox: it cannot be clicked, so it never blocks opening the container,
        // and it cannot be attacked or killed. It renders the real block and glows through walls.
        final Location loc = state.getLocation();
        final org.bukkit.block.data.BlockData blockData = state.getBlockData();
        try {
            final org.bukkit.entity.BlockDisplay display = loc.getWorld().spawn(loc, org.bukkit.entity.BlockDisplay.class, entity -> {
                entity.setBlock(blockData);
                entity.setGlowing(true);
                entity.setGlowColorOverride(Color.AQUA);
                entity.setBrightness(new org.bukkit.entity.Display.Brightness(15, 15));
                entity.setPersistent(false);
                entity.setVisibleByDefault(false);
                entity.addScoreboardTag(CHEST_GLOW_TAG);
            });
            player.showEntity(plugin, display);
            return display;
        } catch (final RuntimeException | LinkageError error) {
            return null;
        }
    }

    private void reconcileMinecartGlow(final Player player, final Set<UUID> desired) {
        if (!glow.isAvailable()) {
            return;
        }
        final Set<UUID> current = minecartGlows.computeIfAbsent(player.getUniqueId(), ignored -> new HashSet<>());
        for (final UUID id : Set.copyOf(current)) {
            if (!desired.contains(id)) {
                final Entity entity = Bukkit.getEntity(id);
                if (entity != null) {
                    glow.setGlow(player, entity, false);
                }
                current.remove(id);
            }
        }
        for (final UUID id : desired) {
            final Entity entity = Bukkit.getEntity(id);
            if (entity != null && glow.setGlow(player, entity, true)) {
                current.add(id);
            }
        }
        if (current.isEmpty()) {
            minecartGlows.remove(player.getUniqueId());
        }
    }

    private void clearChestGlow(final Player player) {
        final Map<Long, org.bukkit.entity.BlockDisplay> glows = chestGlows.remove(player.getUniqueId());
        if (glows != null) {
            for (final org.bukkit.entity.BlockDisplay display : glows.values()) {
                if (display != null && !display.isDead()) {
                    display.remove();
                }
            }
        }
        final Set<UUID> carts = minecartGlows.remove(player.getUniqueId());
        if (carts != null && glow.isAvailable()) {
            for (final UUID id : carts) {
                final Entity entity = Bukkit.getEntity(id);
                if (entity != null) {
                    glow.setGlow(player, entity, false);
                }
            }
        }
    }

    private static long blockKey(final BlockState state) {
        return ((long) (state.getX() & 0x3FFFFFF) << 38) | ((long) (state.getZ() & 0x3FFFFFF) << 12) | (state.getY() & 0xFFFL);
    }

    private static long blockKey(final int x, final int y, final int z) {
        return ((long) (x & 0x3FFFFFF) << 38) | ((long) (z & 0x3FFFFFF) << 12) | (y & 0xFFFL);
    }

    // ---- Firefly: hostile-mob spawn shield -----------------------------------------------------

    /** True when a natural hostile spawn at {@code loc} falls inside an active Firefly owner's shield. */
    public boolean suppressesHostileSpawn(final Location loc) {
        if (loc == null || loc.getWorld() == null) {
            return false;
        }
        for (final Map.Entry<UUID, ActivePet> entry : activePets.entrySet()) {
            final ActivePet active = entry.getValue();
            if (active == null || !active.pet().definitionId().equals("firefly")) {
                continue;
            }
            final Player owner = Bukkit.getPlayer(entry.getKey());
            if (owner == null || !owner.getWorld().equals(loc.getWorld())) {
                continue;
            }
            final double radius = fireflyRadius(active.pet().level());
            if (owner.getLocation().distanceSquared(loc) <= radius * radius) {
                return true;
            }
        }
        return false;
    }

    private double fireflyRadius(final int level) {
        final double base = plugin.getConfig().getDouble("firefly.spawn-protection-radius", 6.0);
        final double max = plugin.getConfig().getDouble("firefly.spawn-protection-max-radius", 20.0);
        return Math.min(max, base + (abilityTier(level) * 0.5));
    }

    // ---- Ferret: ore sense ----------------------------------------------------------------------

    private Set<Material> ferretOreSet(final int level) {
        final int tiers = Math.min(FERRET_ORE_TIERS.size(), 1 + (level / 20));
        final Set<Material> set = EnumSet.noneOf(Material.class);
        for (int i = 0; i < tiers; i++) {
            set.addAll(FERRET_ORE_TIERS.get(i));
        }
        return set;
    }

    private void updateFerretOreGlow(final Player player, final OwnedPet pet) {
        final World world = player.getWorld();
        final Location center = player.getLocation();
        final double radius = Math.min(12.0, 5.0 + (abilityTier(pet.level()) * 0.25));
        final int r = (int) Math.ceil(radius);
        final double radiusSq = radius * radius;
        final Set<Material> ores = ferretOreSet(pet.level());
        final int maxGlows = Math.max(16, plugin.getConfig().getInt("ferret.max-glowing-ores", 80));
        final int cx = center.getBlockX();
        final int cy = center.getBlockY();
        final int cz = center.getBlockZ();
        final int minY = world.getMinHeight();
        final int maxY = world.getMaxHeight() - 1;

        final Set<Long> desired = new HashSet<>();
        final Map<Long, org.bukkit.entity.BlockDisplay> playerGlows =
            oreGlows.computeIfAbsent(player.getUniqueId(), ignored -> new HashMap<>());
        outer:
        for (int dx = -r; dx <= r; dx++) {
            for (int dz = -r; dz <= r; dz++) {
                final int bx = cx + dx;
                final int bz = cz + dz;
                if (!world.isChunkLoaded(bx >> 4, bz >> 4)) {
                    continue;
                }
                for (int dy = -r; dy <= r; dy++) {
                    if ((dx * dx) + (dy * dy) + (dz * dz) > radiusSq) {
                        continue;
                    }
                    final int by = cy + dy;
                    if (by < minY || by > maxY) {
                        continue;
                    }
                    final Block block = world.getBlockAt(bx, by, bz);
                    if (!ores.contains(block.getType())) {
                        continue;
                    }
                    final long key = blockKey(bx, by, bz);
                    desired.add(key);
                    final org.bukkit.entity.BlockDisplay existing = playerGlows.get(key);
                    if (existing == null || existing.isDead()) {
                        final org.bukkit.entity.BlockDisplay spawned = spawnOreGlow(player, block);
                        if (spawned != null) {
                            playerGlows.put(key, spawned);
                        }
                    }
                    if (desired.size() >= maxGlows) {
                        break outer;
                    }
                }
            }
        }
        playerGlows.entrySet().removeIf(entry -> {
            if (!desired.contains(entry.getKey())) {
                if (entry.getValue() != null && !entry.getValue().isDead()) {
                    entry.getValue().remove();
                }
                return true;
            }
            return false;
        });
        if (playerGlows.isEmpty()) {
            oreGlows.remove(player.getUniqueId());
        }
    }

    private org.bukkit.entity.BlockDisplay spawnOreGlow(final Player player, final Block block) {
        final Location loc = block.getLocation();
        final org.bukkit.block.data.BlockData blockData = block.getBlockData();
        final Color color = oreGlowColor(block.getType());
        try {
            final org.bukkit.entity.BlockDisplay display = loc.getWorld().spawn(loc, org.bukkit.entity.BlockDisplay.class, entity -> {
                entity.setBlock(blockData);
                entity.setGlowing(true);
                entity.setGlowColorOverride(color);
                entity.setBrightness(new org.bukkit.entity.Display.Brightness(15, 15));
                entity.setPersistent(false);
                entity.setVisibleByDefault(false);
                entity.addScoreboardTag(CHEST_GLOW_TAG);
            });
            player.showEntity(plugin, display);
            return display;
        } catch (final RuntimeException | LinkageError error) {
            return null;
        }
    }

    private Color oreGlowColor(final Material type) {
        return switch (type) {
            case COAL_ORE, DEEPSLATE_COAL_ORE -> Color.GRAY;
            case COPPER_ORE, DEEPSLATE_COPPER_ORE -> Color.ORANGE;
            case IRON_ORE, DEEPSLATE_IRON_ORE -> Color.WHITE;
            case GOLD_ORE, DEEPSLATE_GOLD_ORE, NETHER_GOLD_ORE, NETHER_QUARTZ_ORE -> Color.YELLOW;
            case REDSTONE_ORE, DEEPSLATE_REDSTONE_ORE -> Color.RED;
            case LAPIS_ORE, DEEPSLATE_LAPIS_ORE -> Color.BLUE;
            case DIAMOND_ORE, DEEPSLATE_DIAMOND_ORE -> Color.AQUA;
            case EMERALD_ORE, DEEPSLATE_EMERALD_ORE -> Color.GREEN;
            case ANCIENT_DEBRIS -> Color.FUCHSIA;
            default -> Color.ORANGE;
        };
    }

    private void clearOreGlow(final Player player) {
        final Map<Long, org.bukkit.entity.BlockDisplay> glows = oreGlows.remove(player.getUniqueId());
        if (glows != null) {
            for (final org.bukkit.entity.BlockDisplay display : glows.values()) {
                if (display != null && !display.isDead()) {
                    display.remove();
                }
            }
        }
    }

    // ---- Kangaroo: mid-air double jump ----------------------------------------------------------

    /** Launches an airborne Kangaroo owner forward and up when they sneak, once per short cooldown. */
    public void handleKangarooJump(final Player player) {
        final OwnedPet pet = storage.data(player.getUniqueId()).activePet().orElse(null);
        if (pet == null || !pet.definitionId().equals("kangaroo")) {
            return;
        }
        if (player.isOnGround() || player.isFlying() || player.isGliding() || isRiding(player)) {
            return;
        }
        final long now = System.currentTimeMillis();
        final Long until = kangarooCooldowns.get(player.getUniqueId());
        if (until != null && now < until) {
            return;
        }
        final double power = 0.7 + (abilityTier(pet.level()) * 0.03);
        final Vector dir = player.getLocation().getDirection().normalize().multiply(power);
        dir.setY(Math.max(0.5, dir.getY() + 0.5));
        player.setVelocity(dir);
        kangarooCooldowns.put(player.getUniqueId(), now + 1500L);
        player.getWorld().spawnParticle(Particle.CLOUD, player.getLocation(), 8, 0.2, 0.05, 0.2, 0.02);
        player.playSound(player.getLocation(), Sound.ENTITY_RABBIT_JUMP, 0.7F, 1.2F);
    }

    // ---- Squirrel: forage bonus -----------------------------------------------------------------

    /** Drops bonus forage when a Squirrel owner breaks leaves or logs. */
    public void handleSquirrelForage(final Player player, final Block block) {
        final OwnedPet pet = storage.data(player.getUniqueId()).activePet().orElse(null);
        if (pet == null || !pet.definitionId().equals("squirrel")) {
            return;
        }
        final Material type = block.getType();
        final boolean leaves = org.bukkit.Tag.LEAVES.isTagged(type);
        final boolean logs = org.bukkit.Tag.LOGS.isTagged(type);
        if (!leaves && !logs) {
            return;
        }
        final double chance = Math.min(0.6, 0.12 + (abilityTier(pet.level()) * 0.02));
        if (ThreadLocalRandom.current().nextDouble() >= chance) {
            return;
        }
        final Material drop = leaves
            ? SQUIRREL_LEAF_DROPS.get(ThreadLocalRandom.current().nextInt(SQUIRREL_LEAF_DROPS.size()))
            : Material.STICK;
        block.getWorld().dropItemNaturally(block.getLocation().add(0.5, 0.5, 0.5), new ItemStack(drop));
    }

    // ---- Water Serpent: master angler -----------------------------------------------------------

    /** Speeds up bites while casting and rolls a bonus catch when a Water Serpent owner reels one in. */
    public void handleWaterSerpentFish(final org.bukkit.event.player.PlayerFishEvent event) {
        final Player player = event.getPlayer();
        final OwnedPet pet = storage.data(player.getUniqueId()).activePet().orElse(null);
        if (pet == null || !pet.definitionId().equals("water_serpent")) {
            return;
        }
        final int tier = abilityTier(pet.level());
        if (event.getState() == org.bukkit.event.player.PlayerFishEvent.State.FISHING) {
            final org.bukkit.entity.FishHook hook = event.getHook();
            final int minWait = Math.max(20, 100 - (tier * 3));
            final int maxWait = Math.max(minWait + 20, 300 - (tier * 6));
            hook.setMinWaitTime(minWait);
            hook.setMaxWaitTime(maxWait);
            return;
        }
        if (event.getState() == org.bukkit.event.player.PlayerFishEvent.State.CAUGHT_FISH
            && event.getCaught() instanceof Item caught) {
            final double chance = Math.min(0.5, 0.1 + (tier * 0.02));
            if (ThreadLocalRandom.current().nextDouble() < chance) {
                player.getWorld().dropItemNaturally(player.getLocation(), caught.getItemStack().clone());
            }
        }
    }

    /** Per-pet dispatch context: the owner, the pet, and its cached level/ability tier. */
    private record AbilityCtx(Player player, OwnedPet pet, int level, int tier) {
    }

    @FunctionalInterface
    private interface Behavior {
        void run(AbilityCtx c);
    }

    @FunctionalInterface
    private interface Hit {
        void run(AbilityCtx c, LivingEntity target);
    }

    @FunctionalInterface
    private interface Defense {
        void run(AbilityCtx c, Entity damager);
    }

    @FunctionalInterface
    private interface Target {
        void run(AbilityCtx c, EntityTargetLivingEntityEvent event);
    }

    private AbilityCtx ctx(final Player player, final OwnedPet pet) {
        final int level = pet.level();
        return new AbilityCtx(player, pet, level, PetAbilities.tier(level));
    }

    /**
     * Registers each pet's behaviour hooks. Lambdas close over this manager, so they reach the same
     * private helpers (setTarget, applyPetBuff, biome/water checks, ...) the old switch statements used.
     * Formulas are unchanged from the previous inline versions.
     */
    private void registerAbilities() {
        // Passive attribute / potion effects, reapplied while the pet is active.
        passiveBehaviors.put("ant", c -> setTarget(c.player(), Attribute.SCALE, Math.max(0.5, 1.0 - (c.tier() * 0.025))));
        passiveBehaviors.put("axolotl", c -> setTarget(c.player(), Attribute.OXYGEN_BONUS, c.tier() * 0.5));
        passiveBehaviors.put("alpaca", c -> {
            if (usedInventorySlots(c.player()) >= 27) {
                setTarget(c.player(), Attribute.MOVEMENT_SPEED, 0.1 + (c.tier() * 0.0015));
            }
        });
        passiveBehaviors.put("beaver", c -> {
            if (isHoldingTool(c.player(), "_AXE")) {
                setTarget(c.player(), Attribute.MINING_EFFICIENCY, c.tier() * 0.35);
            }
        });
        passiveBehaviors.put("blue_dragon", c -> {
            if (c.player().getWorld().getEnvironment() == World.Environment.THE_END) {
                setTarget(c.player(), Attribute.ATTACK_DAMAGE, 2.0 + (c.tier() * 0.3));
            }
        });
        passiveBehaviors.put("cat", c -> setTarget(c.player(), Attribute.FALL_DAMAGE_MULTIPLIER, Math.max(0.0, 1.0 - (c.tier() * 0.05))));
        passiveBehaviors.put("crab", c -> {
            if (isWetOrNearWater(c.player())) {
                setTarget(c.player(), Attribute.ATTACK_DAMAGE, 2.0 + (c.tier() * 0.2));
                setTarget(c.player(), Attribute.ARMOR, c.tier() * 0.3);
            }
        });
        passiveBehaviors.put("goblin", c -> applyPetBuff(c.player(), PotionEffectType.HERO_OF_THE_VILLAGE, Math.min(4, 1 + (c.tier() / 5))));
        passiveBehaviors.put("moon_fox", c -> {
            if (isNight(c.player())) {
                setTarget(c.player(), Attribute.MOVEMENT_SPEED, 0.1 + (c.tier() * 0.003));
                applyPetBuff(c.player(), PotionEffectType.STRENGTH, 0);
            }
        });
        passiveBehaviors.put("otter", c -> {
            setTarget(c.player(), Attribute.WATER_MOVEMENT_EFFICIENCY, c.tier() * 0.05);
            setTarget(c.player(), Attribute.OXYGEN_BONUS, c.tier() * 0.5);
            applyPetBuff(c.player(), PotionEffectType.WATER_BREATHING, 0);
            applyPetBuff(c.player(), PotionEffectType.DOLPHINS_GRACE, 1);
        });
        passiveBehaviors.put("dolphin", c -> {
            applyPetBuff(c.player(), PotionEffectType.DOLPHINS_GRACE, 1);
            setTarget(c.player(), Attribute.WATER_MOVEMENT_EFFICIENCY, c.tier() * 0.05);
        });
        passiveBehaviors.put("elder_guardian", c -> setTarget(c.player(), Attribute.SUBMERGED_MINING_SPEED, Math.min(1.0, 0.2 + (c.tier() * 0.04))));
        // The Ender Dragon now empowers you in every dimension, not just The End.
        passiveBehaviors.put("ender_dragon", c -> setTarget(c.player(), Attribute.ATTACK_DAMAGE, 2.0 + (c.tier() * 0.35)));
        passiveBehaviors.put("ghast", c -> setTarget(c.player(), Attribute.EXPLOSION_KNOCKBACK_RESISTANCE, c.tier() * 0.05));
        passiveBehaviors.put("hamster", c -> setTarget(c.player(), Attribute.STEP_HEIGHT, 0.6 + (c.tier() * 0.045)));
        passiveBehaviors.put("herobrine", c -> {
            setTarget(c.player(), Attribute.MAX_HEALTH, 20.0 + c.tier());
            setTarget(c.player(), Attribute.ENTITY_INTERACTION_RANGE, 3.0 + (c.tier() * 0.1125));
        });
        passiveBehaviors.put("owl", c -> {
            applyPetBuff(c.player(), PotionEffectType.NIGHT_VISION, 1);
            setTarget(c.player(), Attribute.LUCK, c.tier() * 25.0);
        });
        passiveBehaviors.put("panda", c -> setTarget(c.player(), Attribute.ATTACK_KNOCKBACK, c.tier() * 0.05));
        passiveBehaviors.put("penguin", c -> {
            if (isColdBiome(c.player())) {
                setTarget(c.player(), Attribute.MOVEMENT_SPEED, 0.1 + (c.tier() * 0.00375));
            }
        });
        passiveBehaviors.put("polar_bear", c -> {
            if (isColdBiome(c.player())) {
                setTarget(c.player(), Attribute.ARMOR, c.tier() * 0.25);
            }
        });
        passiveBehaviors.put("phoenix", c -> applyPetBuff(c.player(), PotionEffectType.FIRE_RESISTANCE, 1));
        passiveBehaviors.put("red_panda", c -> {
            if (isForestBiome(c.player())) {
                setTarget(c.player(), Attribute.MOVEMENT_SPEED, 0.1 + (c.tier() * 0.002));
            }
            setTarget(c.player(), Attribute.SNEAKING_SPEED, 0.3 + (c.tier() * 0.02));
        });
        passiveBehaviors.put("rabbit", c -> {
            setTarget(c.player(), Attribute.SAFE_FALL_DISTANCE, 10.0);
            setTarget(c.player(), Attribute.JUMP_STRENGTH, Math.min(1.01, 0.4 + (c.tier() * 0.03158)));
        });
        passiveBehaviors.put("reaper", c -> setTarget(c.player(), Attribute.ATTACK_SPEED, 4.0 + (c.tier() * 0.2)));
        passiveBehaviors.put("red_dragon", c -> {
            if (c.player().getWorld().getEnvironment() == World.Environment.NETHER) {
                setTarget(c.player(), Attribute.ATTACK_DAMAGE, 2.0 + (c.tier() * 0.3));
            }
        });
        passiveBehaviors.put("snail", c -> setTarget(c.player(), Attribute.SNEAKING_SPEED, 0.3 + (c.tier() * 0.035)));
        passiveBehaviors.put("spinosaurus", c -> setTarget(c.player(), Attribute.SCALE, 1.0 + (c.tier() * 0.025)));
        passiveBehaviors.put("slime", c -> {
            setTarget(c.player(), Attribute.SAFE_FALL_DISTANCE, 5.0 + c.tier());
            setTarget(c.player(), Attribute.JUMP_STRENGTH, Math.min(0.85, 0.4 + (c.tier() * 0.018)));
        });
        passiveBehaviors.put("tiger", c -> {
            setTarget(c.player(), Attribute.SWEEPING_DAMAGE_RATIO, 1.0);
            setTarget(c.player(), Attribute.MOVEMENT_SPEED, 0.1 + (Math.min(100, c.level()) * 0.001));
        });
        passiveBehaviors.put("turtle", c -> setTarget(c.player(), Attribute.KNOCKBACK_RESISTANCE, c.tier() * 5.0));
        passiveBehaviors.put("duck", c -> setTarget(c.player(), Attribute.WATER_MOVEMENT_EFFICIENCY, c.tier() * 0.035));
        passiveBehaviors.put("unicorn", c -> setTarget(c.player(), Attribute.LUCK, c.tier() * 8.0));
        passiveBehaviors.put("worm", c -> setTarget(c.player(), Attribute.MINING_EFFICIENCY, c.tier() * 0.5));
        passiveBehaviors.put("firefly", c -> applyPetBuff(c.player(), PotionEffectType.NIGHT_VISION, 0));
        passiveBehaviors.put("water_serpent", c -> {
            if (isHoldingTool(c.player(), "FISHING_ROD")) {
                setTarget(c.player(), Attribute.LUCK, c.tier() * 3.0);
                setTarget(c.player(), Attribute.WATER_MOVEMENT_EFFICIENCY, c.tier() * 0.05);
            }
        });

        // Periodic effects, applied every ability interval.
        final Behavior dragonAbsorption = c -> applyPetBuff(c.player(), PotionEffectType.ABSORPTION, 3, 220);
        periodicBehaviors.put("blue_dragon", dragonAbsorption);
        periodicBehaviors.put("red_dragon", dragonAbsorption);
        periodicBehaviors.put("ender_dragon", c -> applyPetBuff(c.player(), PotionEffectType.ABSORPTION, 2, 220));
        periodicBehaviors.put("capybara", c -> {
            if (isWetOrNearWater(c.player())) {
                applyPetBuff(c.player(), PotionEffectType.REGENERATION, c.player().getWorld().hasStorm() && c.level() >= 80 ? 1 : 0);
            }
        });
        periodicBehaviors.put("bee", c -> {
            if (nearFlowersOrCrops(c.player())) {
                applyPetBuff(c.player(), PotionEffectType.REGENERATION, c.level() >= 80 ? 1 : 0);
            }
        });
        periodicBehaviors.put("chicken", c -> {
            applyPetBuff(c.player(), PotionEffectType.SLOW_FALLING, 1);
            layChickenEgg(c.player(), c.level());
        });
        periodicBehaviors.put("pixie", c -> {
            // Higher level = more simultaneous buffs and stronger amplifiers.
            final int lvl = c.level();
            final int count = lvl >= 80 ? 3 : lvl >= 40 ? 2 : 1;
            final int amplifier = lvl >= 90 ? 2 : lvl >= 50 ? 1 : 0;
            final List<PotionEffectType> pool = new java.util.ArrayList<>(PIXIE_BUFFS);
            java.util.Collections.shuffle(pool, ThreadLocalRandom.current());
            for (int i = 0; i < count && i < pool.size(); i++) {
                applyPetBuff(c.player(), pool.get(i), amplifier);
            }
        });
        periodicBehaviors.put("duck", c -> {
            if (isAirborne(c.player())) {
                applyPetBuff(c.player(), PotionEffectType.SLOW_FALLING, 0);
            }
        });
        periodicBehaviors.put("koala", c -> {
            if (nearTree(c.player())) {
                final int lvl = c.level();
                applyPetBuff(c.player(), PotionEffectType.REGENERATION, lvl >= 80 ? 2 : lvl >= 40 ? 1 : 0);
            }
        });
        periodicBehaviors.put("panda", c -> {
            if (biomeKey(c.player()).contains("bamboo_jungle")) {
                applyPetBuff(c.player(), PotionEffectType.HERO_OF_THE_VILLAGE, 1);
            }
        });
        periodicBehaviors.put("penguin", c -> freezeWaterNear(c.player()));
        periodicBehaviors.put("pufferfish", c -> {
            // Same aura at every level, but its reach and wither strength scale up with tier.
            final int t = c.tier();
            final double radius = 5.0 + (t * 0.25);
            final int amplifier = 1 + (t / 8);
            affectNearby(c.player(), radius, true, entity ->
                entity.addPotionEffect(new PotionEffect(PotionEffectType.WITHER, 80 + (t * 8), amplifier, true, false, true)));
        });
        periodicBehaviors.put("reaper", c -> {
            final int radius = c.level() >= 100 ? 25 : c.level() >= 50 ? 20 : 15;
            affectNearby(c.player(), radius, true, entity -> {
                entity.addPotionEffect(new PotionEffect(PotionEffectType.WITHER, 160, 1, true, false, true));
                entity.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, 160, 1, true, false, true));
            });
        });
        periodicBehaviors.put("warden", c -> Bukkit.getWorlds().forEach(world -> world.getEntitiesByClass(org.bukkit.entity.Warden.class).forEach(warden -> {
            if (warden.getTarget() != null && warden.getTarget().equals(c.player())) {
                warden.setTarget(null);
            }
        })));
        periodicBehaviors.put("herobrine", c -> {
            // Personal, client-side weather only, so other players and the world are unaffected.
            herobrineWeather.add(c.player().getUniqueId());
            c.player().setPlayerWeather(WeatherType.DOWNFALL);
        });
        periodicBehaviors.put("unicorn", c -> applyPetBuff(c.player(), PotionEffectType.REGENERATION, c.level() >= 80 ? 1 : 0));

        // On-hit effects (owner lands a melee/projectile hit on a victim).
        hitBehaviors.put("dog", (c, victim) -> {
            if (isUndead(victim) && Math.random() <= Math.min(1.0, c.level() / 100.0)) {
                victim.addPotionEffect(new PotionEffect(PotionEffectType.WITHER, 40, 1, true, false, true));
            }
        });
        hitBehaviors.put("phoenix", (c, victim) -> {
            if (isUndead(victim)) {
                victim.setFireTicks(Math.max(victim.getFireTicks(), c.level() * 2));
            }
        });
        hitBehaviors.put("reaper", (c, victim) -> {
            c.player().addPotionEffect(new PotionEffect(PotionEffectType.REGENERATION, 100, 1, true, false, true));
            c.player().addPotionEffect(new PotionEffect(PotionEffectType.STRENGTH, 100, 1, true, false, true));
        });
        hitBehaviors.put("shadow_dragon", (c, victim) -> tryShadowAoeOnAttack(c.player(), c.pet()));

        // On-kill effects.
        killBehaviors.put("ghast", (c, killed) -> {
            if (c.player().getWorld().getEnvironment() == World.Environment.NETHER) {
                c.player().giveExp(10);
            }
        });
        killBehaviors.put("lich", (c, killed) -> {
            final AttributeInstance maxHealth = c.player().getAttribute(Attribute.MAX_HEALTH);
            final double max = maxHealth == null ? 20.0 : maxHealth.getValue();
            final double heal = 2.0 + (c.tier() * 0.2);
            c.player().setHealth(Math.max(0.0, Math.min(max, c.player().getHealth() + heal)));
            c.player().getWorld().spawnParticle(Particle.HEART, c.player().getLocation().add(0, 1.0, 0), 3, 0.3, 0.3, 0.3, 0.0);
        });

        // On-defense effects (owner is hit by a damager).
        defenseBehaviors.put("hedgehog", (c, damager) -> {
            if (damager instanceof LivingEntity living && !living.equals(c.player())) {
                living.damage(Math.min(3.0, 0.4 + (c.tier() * 0.08)));
            }
        });
        defenseBehaviors.put("platypus", (c, damager) -> {
            final LivingEntity attacker = resolveAttacker(c.player(), damager);
            if (attacker != null && isWetOrNearWater(c.player())) {
                // Undead mobs are immune to Poison, so wither them instead for a visible effect.
                final PotionEffectType effect = isUndead(attacker) ? PotionEffectType.WITHER : PotionEffectType.POISON;
                attacker.addPotionEffect(new PotionEffect(effect, 80 + (c.tier() * 6), 0, true, false, true));
            }
        });

        // Mob-targeting overrides.
        targetBehaviors.put("warden", (c, event) -> {
            if (event.getEntityType() == EntityType.WARDEN) {
                event.setCancelled(true);
            }
        });
        targetBehaviors.put("cursed_plushie", (c, event) -> {
            final double chance = Math.min(0.75, 0.25 + (c.tier() * 0.025));
            if (isHostile(event.getEntity()) && ThreadLocalRandom.current().nextDouble() < chance) {
                event.setCancelled(true);
                spawnPlushieDistraction(c.player());
            }
        });
    }

    private void applyPassive(final Player player, final OwnedPet pet) {
        resetPlayerState(player);
        final Behavior behavior = passiveBehaviors.get(pet.definitionId());
        if (behavior != null) {
            behavior.run(ctx(player, pet));
        }
    }

    private int abilityTier(final int level) {
        return PetAbilities.tier(level);
    }

    private void resetPlayerState(final Player player) {
        for (final Attribute attribute : MODIFIED_ATTRIBUTES) {
            final AttributeInstance instance = player.getAttribute(attribute);
            if (instance == null) {
                continue;
            }
            final NamespacedKey key = modifierKey(attribute);
            instance.getModifiers().stream()
                .filter(modifier -> key.equals(modifier.getKey()))
                .findFirst()
                .ifPresent(instance::removeModifier);
        }

        final Set<PotionEffectType> tracked = petBuffs.remove(player.getUniqueId());
        if (tracked != null) {
            for (final PotionEffectType type : tracked) {
                player.removePotionEffect(type);
            }
        }

        if (herobrineWeather.remove(player.getUniqueId())) {
            player.resetPlayerWeather();
        }
    }

    /**
     * Strips any leftover pet attribute modifiers from a joining player. After an unclean shutdown
     * (a crash with no onDisable) the keyed modifiers can persist in player data; calling this on join
     * removes them so a player never keeps a pet stat without an active pet.
     */
    public void prepareJoiningPlayer(final Player player) {
        resetPlayerState(player);
    }

    /**
     * Applies a potion effect that belongs to the active pet. The effect is given an infinite
     * duration so it never blinks or expires while the pet is active, and only effects that the
     * pet itself applied are ever removed again (see {@link #resetPlayerState(Player)}). Effects the
     * player already has from another source are left untouched so pets never overwrite them.
     */
    private void applyPetBuff(final Player player, final PotionEffectType type, final int amplifier) {
        applyPetBuff(player, type, amplifier, PotionEffect.INFINITE_DURATION);
    }

    private void applyPetBuff(final Player player, final PotionEffectType type, final int amplifier, final int duration) {
        final Set<PotionEffectType> tracked = petBuffs.computeIfAbsent(player.getUniqueId(), ignored -> new HashSet<>());
        if (!tracked.contains(type) && player.hasPotionEffect(type)) {
            return;
        }
        tracked.add(type);
        player.addPotionEffect(new PotionEffect(type, duration, amplifier, true, false, true));
    }

    private void setTarget(final Player player, final Attribute attribute, final double targetValue) {
        final AttributeInstance instance = player.getAttribute(attribute);
        if (instance == null) {
            if (plugin.getConfig().getBoolean("debug-logging", false)) {
                plugin.getLogger().info("[Debug] Missing player attribute " + attribute.getKey() + " for " + player.getName() + ".");
            }
            return;
        }

        final NamespacedKey key = modifierKey(attribute);
        instance.getModifiers().stream()
            .filter(modifier -> key.equals(modifier.getKey()))
            .findFirst()
            .ifPresent(instance::removeModifier);

        final double amount = targetValue - instance.getBaseValue();
        if (Math.abs(amount) > 0.0001) {
            instance.addModifier(new AttributeModifier(key, amount, AttributeModifier.Operation.ADD_NUMBER));
        }
    }

    private NamespacedKey modifierKey(final Attribute attribute) {
        return new NamespacedKey(plugin, "pet_" + attribute.getKey().getKey().toLowerCase(Locale.ROOT).replace('/', '_'));
    }

    private void updateReveals(final Player player, final OwnedPet pet) {
        final Set<UUID> desired = new HashSet<>();
        switch (pet.definitionId()) {
            case "bat" -> {
                if (player.getLocation().getY() < 50.0) {
                    collectRevealTargets(player, Math.min(24, 8 + abilityTier(pet.level())), false, desired);
                }
            }
            case "red_parrot" -> {
                if (player.getLocation().getBlock().getLightFromSky() > 0) {
                    collectRevealTargets(player, Math.min(30, 10 + (pet.level() / 10) * 2), false, desired);
                }
            }
            case "warden" -> collectRevealTargets(player, Math.min(50, Math.max(10, pet.level())), true, desired);
            default -> {
            }
        }
        reconcileReveal(player, desired);
    }

    private void collectRevealTargets(final Player player, final double radius, final boolean undeadOnly, final Set<UUID> out) {
        for (final Entity entity : player.getNearbyEntities(radius, radius, radius)) {
            if (!(entity instanceof LivingEntity living) || entity.equals(player)) {
                continue;
            }
            if (undeadOnly ? !isUndead(living) : !isHostile(living)) {
                continue;
            }
            if (glow.isAvailable()) {
                out.add(living.getUniqueId());
            } else {
                // No per-viewer glow on this server: fall back to a normal glow (through walls, seen by all).
                living.addPotionEffect(new PotionEffect(PotionEffectType.GLOWING, 15, 0, true, false, false));
            }
        }
    }

    /**
     * Drives a glowing outline that only the pet owner sees. Newly revealed mobs are turned on, mobs
     * that left the radius are turned off again, so the reveal follows the mobs and stays owner-only.
     */
    private void reconcileReveal(final Player player, final Set<UUID> desired) {
        if (!glow.isAvailable()) {
            return;
        }
        final Set<UUID> current = revealedMobs.computeIfAbsent(player.getUniqueId(), ignored -> new HashSet<>());
        for (final UUID id : Set.copyOf(current)) {
            if (!desired.contains(id)) {
                final Entity entity = Bukkit.getEntity(id);
                if (entity != null) {
                    glow.setGlow(player, entity, false);
                }
                current.remove(id);
            }
        }
        for (final UUID id : desired) {
            final Entity entity = Bukkit.getEntity(id);
            if (entity != null && glow.setGlow(player, entity, true)) {
                current.add(id);
            }
        }
        if (current.isEmpty()) {
            revealedMobs.remove(player.getUniqueId());
        }
    }

    private void clearReveal(final Player player) {
        final Set<UUID> current = revealedMobs.remove(player.getUniqueId());
        if (current == null || !glow.isAvailable()) {
            return;
        }
        for (final UUID id : current) {
            final Entity entity = Bukkit.getEntity(id);
            if (entity != null) {
                glow.setGlow(player, entity, false);
            }
        }
    }

    private void spawnPetWalkTrail(final Player player, final OwnedPet pet, final ActivePet active) {
        final Location location = active.display().getLocation().clone().add(0, 0.3, 0);
        try {
            switch (pet.definitionId()) {
                case "ender_dragon" -> player.getWorld().spawnParticle(Particle.DRAGON_BREATH, location, 4, 0.25, 0.18, 0.25, 0.0, 1.0F);
                case "blue_dragon" -> player.getWorld().spawnParticle(Particle.ENCHANT, location, 8, 0.3, 0.25, 0.3, 0.0);
                case "red_dragon" -> player.getWorld().spawnParticle(Particle.FLAME, location, 4, 0.22, 0.15, 0.22, 0.0);
                default -> {
                }
            }
        } catch (final IllegalArgumentException exception) {
            if (pet.definitionId().equals("ender_dragon")) {
                player.getWorld().spawnParticle(Particle.PORTAL, location, 6, 0.25, 0.18, 0.25, 0.0);
            }
        }
    }

    private void affectNearby(final Player player, final double radius, final boolean undeadOnly, final java.util.function.Consumer<LivingEntity> action) {
        for (final Entity entity : player.getNearbyEntities(radius, radius, radius)) {
            if (!(entity instanceof LivingEntity living) || entity.equals(player)) {
                continue;
            }
            if (undeadOnly && !isUndead(living)) {
                continue;
            }
            action.accept(living);
        }
    }

    private void freezeWaterNear(final Player player) {
        final Location location = player.getLocation();
        for (int x = -1; x <= 1; x++) {
            for (int z = -1; z <= 1; z++) {
                final Block block = location.clone().add(x, -1, z).getBlock();
                if (block.getType() == Material.WATER && block.getRelative(0, 1, 0).isPassable()) {
                    block.setType(Material.FROSTED_ICE, false);
                }
            }
        }
    }

    private boolean isColdBiome(final Player player) {
        return COLD_BIOMES.contains(biomeKey(player));
    }

    private boolean isForestBiome(final Player player) {
        final String key = biomeKey(player);
        return key.contains("forest") || key.contains("taiga") || key.contains("jungle") || key.contains("grove");
    }

    private boolean isWetOrNearWater(final Player player) {
        if (player.getLocation().getBlock().getType() == Material.WATER || player.getEyeLocation().getBlock().getType() == Material.WATER) {
            return true;
        }
        final Location location = player.getLocation();
        for (int x = -1; x <= 1; x++) {
            for (int y = -1; y <= 0; y++) {
                for (int z = -1; z <= 1; z++) {
                    if (location.clone().add(x, y, z).getBlock().getType() == Material.WATER) {
                        return true;
                    }
                }
            }
        }
        return player.getWorld().hasStorm()
            && player.getLocation().getBlockY() >= player.getWorld().getHighestBlockYAt(player.getLocation());
    }

    private boolean isAirborne(final Player player) {
        return player.getLocation().clone().subtract(0, 0.1, 0).getBlock().isPassable();
    }

    private boolean nearTree(final Player player) {
        final Location location = player.getLocation();
        for (int x = -2; x <= 2; x++) {
            for (int y = -1; y <= 2; y++) {
                for (int z = -2; z <= 2; z++) {
                    final String material = location.clone().add(x, y, z).getBlock().getType().name();
                    if (material.endsWith("_LEAVES") || material.endsWith("_LOG") || material.endsWith("_STEM")) {
                        return true;
                    }
                }
            }
        }
        return false;
    }

    private boolean isNight(final Player player) {
        final long time = player.getWorld().getTime();
        return time >= 13000L && time <= 23000L;
    }

    private boolean nearFlowersOrCrops(final Player player) {
        final Location location = player.getLocation();
        for (int x = -2; x <= 2; x++) {
            for (int y = -1; y <= 1; y++) {
                for (int z = -2; z <= 2; z++) {
                    if (isFlowerOrCrop(location.clone().add(x, y, z).getBlock().getType())) {
                        return true;
                    }
                }
            }
        }
        return false;
    }

    private boolean isFlowerOrCrop(final Material material) {
        // Tag.FLOWERS on its own misses the common small flowers (poppy, dandelion, ...) on some
        // versions, which is why standing next to a plain flower gave the Bee no Regeneration.
        // Checking SMALL_FLOWERS explicitly (plus a name fallback) makes every flower count.
        if (org.bukkit.Tag.FLOWERS.isTagged(material)
            || org.bukkit.Tag.SMALL_FLOWERS.isTagged(material)
            || org.bukkit.Tag.CROPS.isTagged(material)
            || org.bukkit.Tag.SAPLINGS.isTagged(material)) {
            return true;
        }
        if (material.name().endsWith("_FLOWER") || material.name().endsWith("_TULIP")) {
            return true;
        }
        return switch (material) {
            case FARMLAND, PUMPKIN, MELON, SWEET_BERRY_BUSH, SUGAR_CANE, PINK_PETALS,
                 SPORE_BLOSSOM, FLOWERING_AZALEA, FLOWERING_AZALEA_LEAVES, BEE_NEST, BEEHIVE,
                 SUNFLOWER, LILAC, ROSE_BUSH, PEONY, POPPY, DANDELION, BLUE_ORCHID, ALLIUM,
                 AZURE_BLUET, OXEYE_DAISY, CORNFLOWER, LILY_OF_THE_VALLEY, WITHER_ROSE,
                 TORCHFLOWER, PITCHER_PLANT, PITCHER_CROP, CHORUS_FLOWER -> true;
            default -> false;
        };
    }

    /**
     * The Ancient Elf's warding: shortens debuff durations early, strongly caps them past level 50, and
     * removes them outright at level 100. Runs every follow tick so protection feels near-instant.
     */
    private void cleanseDebuffs(final Player player, final int level) {
        for (final PotionEffectType negative : NEGATIVE_EFFECTS) {
            final PotionEffect effect = player.getPotionEffect(negative);
            if (effect == null || effect.getDuration() == PotionEffect.INFINITE_DURATION) {
                continue;
            }
            if (level >= 100) {
                player.removePotionEffect(negative);
                continue;
            }
            final int cap = level >= 50 ? 40 : (int) (effect.getDuration() * 0.6);
            if (cap < effect.getDuration()) {
                player.removePotionEffect(negative);
                if (cap > 0) {
                    player.addPotionEffect(new PotionEffect(negative, cap, effect.getAmplifier(),
                        effect.isAmbient(), effect.hasParticles(), effect.hasIcon()));
                }
            }
        }
    }

    private boolean isHoldingTool(final Player player, final String suffix) {
        return player.getInventory().getItemInMainHand().getType().name().endsWith(suffix);
    }

    private boolean isDurabilityTool(final Material material) {
        final String name = material.name();
        return name.endsWith("_PICKAXE") || name.endsWith("_AXE") || name.endsWith("_SHOVEL");
    }

    /**
     * The Chicken periodically lays an egg straight into the owner's inventory. Both the chance to lay
     * and the number of eggs rise with the pet's level, so a higher-level Chicken is meaningfully better.
     */
    private void layChickenEgg(final Player player, final int level) {
        final int tier = abilityTier(level);
        if (ThreadLocalRandom.current().nextDouble() >= Math.min(0.45, 0.08 + (tier * 0.018))) {
            return;
        }
        final int eggs = 1 + (tier / 12);
        final Location at = player.getLocation();
        final Map<Integer, ItemStack> leftover = player.getInventory().addItem(new ItemStack(Material.EGG, eggs));
        leftover.values().forEach(stack -> player.getWorld().dropItemNaturally(at, stack));
        player.getWorld().spawnParticle(Particle.CLOUD, at.clone().add(0, 0.3, 0), 4, 0.2, 0.1, 0.2, 0.0);
        player.getWorld().playSound(at, Sound.ENTITY_CHICKEN_EGG, 0.5F, 1.0F);
    }

    private int usedInventorySlots(final Player player) {
        int used = 0;
        for (final org.bukkit.inventory.ItemStack item : player.getInventory().getStorageContents()) {
            if (item != null && item.getType() != Material.AIR) {
                used++;
            }
        }
        return used;
    }

    private String biomeKey(final Player player) {
        return player.getWorld().getBiome(player.getLocation()).getKey().getKey();
    }

    private int cleanupStaleDisplays() {
        int removed = 0;
        for (final World world : Bukkit.getWorlds()) {
            final List<ItemDisplay> stale = world.getEntitiesByClass(ItemDisplay.class).stream()
                .filter(entity -> entity.getScoreboardTags().contains("BetterPets.Pet"))
                .toList();
            removed += stale.size();
            stale.forEach(Entity::remove);
            final List<Interaction> staleHitboxes = world.getEntitiesByClass(Interaction.class).stream()
                .filter(entity -> entity.getScoreboardTags().contains("BetterPets.Pet"))
                .toList();
            removed += staleHitboxes.size();
            staleHitboxes.forEach(Entity::remove);
            final List<TextDisplay> staleNametags = world.getEntitiesByClass(TextDisplay.class).stream()
                .filter(entity -> entity.getScoreboardTags().contains("BetterPets.Pet"))
                .toList();
            removed += staleNametags.size();
            staleNametags.forEach(Entity::remove);
            final List<ArmorStand> staleMounts = world.getEntitiesByClass(ArmorStand.class).stream()
                .filter(entity -> entity.getScoreboardTags().contains("BetterPets.Ride"))
                .toList();
            removed += staleMounts.size();
            staleMounts.forEach(Entity::remove);
            final List<org.bukkit.entity.BlockDisplay> staleGlows = world.getEntitiesByClass(org.bukkit.entity.BlockDisplay.class).stream()
                .filter(entity -> entity.getScoreboardTags().contains(CHEST_GLOW_TAG))
                .toList();
            removed += staleGlows.size();
            staleGlows.forEach(Entity::remove);
        }
        return removed;
    }

    private static final class ActivePet {
        private final OwnedPet pet;
        private final ItemDisplay display;
        private final Interaction hitbox;
        private float followYaw;
        private PetModelHandle modelHandle;
        private String modelName;
        private TextDisplay nametag;
        private String currentAnimation;
        private Location lastLocation;
        private long tempAnimationUntil;
        private int groundCacheX = Integer.MIN_VALUE;
        private int groundCacheZ = Integer.MIN_VALUE;
        private double groundCacheY;

        private ActivePet(final OwnedPet pet, final ItemDisplay display, final Interaction hitbox, final float followYaw, final PetModelHandle modelHandle, final String modelName, final TextDisplay nametag) {
            this.pet = pet;
            this.display = display;
            this.hitbox = hitbox;
            this.followYaw = followYaw;
            this.modelHandle = modelHandle;
            this.modelName = modelName;
            this.nametag = nametag;
        }

        private OwnedPet pet() {
            return pet;
        }

        private ItemDisplay display() {
            return display;
        }

        private Interaction hitbox() {
            return hitbox;
        }

        private float followYaw() {
            return followYaw;
        }

        private void followYaw(final float followYaw) {
            this.followYaw = followYaw;
        }

        private PetModelHandle modelHandle() {
            return modelHandle;
        }

        private String modelName() {
            return modelName;
        }

        private boolean modelNameMatches(final String modelName) {
            return this.modelName != null && this.modelName.equals(modelName);
        }

        private String currentAnimation() {
            return currentAnimation;
        }

        private void currentAnimation(final String currentAnimation) {
            this.currentAnimation = currentAnimation;
        }

        private Location lastLocation() {
            return lastLocation;
        }

        private void lastLocation(final Location lastLocation) {
            this.lastLocation = lastLocation;
        }

        private long tempAnimationUntil() {
            return tempAnimationUntil;
        }

        private void tempAnimationUntil(final long tempAnimationUntil) {
            this.tempAnimationUntil = tempAnimationUntil;
        }

        private Double cachedGroundY(final int blockX, final int blockZ) {
            return (blockX == groundCacheX && blockZ == groundCacheZ) ? groundCacheY : null;
        }

        private void cacheGroundY(final int blockX, final int blockZ, final double y) {
            this.groundCacheX = blockX;
            this.groundCacheZ = blockZ;
            this.groundCacheY = y;
        }

        private void model(final PetModelHandle modelHandle, final String modelName, final TextDisplay nametag) {
            this.modelHandle = modelHandle;
            this.modelName = modelName;
            this.nametag = nametag;
        }

        private void nametag(final TextDisplay nametag) {
            this.nametag = nametag;
        }

        private void updateNametag(final Component text) {
            if (nametag != null && !nametag.isDead()) {
                nametag.text(text);
            }
        }

        private void setNametagVisible(final boolean visible) {
            if (nametag != null && !nametag.isDead()) {
                nametag.setViewRange(visible ? 32.0F : 0.0F);
            }
        }

        private void teleportNametag(final Location location) {
            if (nametag != null && !nametag.isDead()) {
                nametag.teleport(location);
            }
        }

        private boolean modelNametagDead() {
            return modelHandle != null && (nametag == null || nametag.isDead());
        }

        private boolean nametagMissing() {
            return nametag == null || nametag.isDead();
        }

        private void closeModel() {
            if (modelHandle != null) {
                modelHandle.close();
                modelHandle = null;
                modelName = null;
                currentAnimation = null;
            }
            if (nametag != null) {
                nametag.remove();
                nametag = null;
            }
        }
    }

    private static final class RideState {
        private final ArmorStand mount;
        private boolean forward;
        private boolean backward;
        private boolean left;
        private boolean right;
        private boolean jump;

        private RideState(final ArmorStand mount) {
            this.mount = mount;
        }

        private ArmorStand mount() {
            return mount;
        }

        private void input(final Input input) {
            this.forward = input.isForward();
            this.backward = input.isBackward();
            this.left = input.isLeft();
            this.right = input.isRight();
            this.jump = input.isJump();
        }

        private boolean forward() {
            return forward;
        }

        private boolean backward() {
            return backward;
        }

        private boolean left() {
            return left;
        }

        private boolean right() {
            return right;
        }

        private boolean jump() {
            return jump;
        }
    }
}
