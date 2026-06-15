package de.kamil.betterpets;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.EntityEffect;
import org.bukkit.Input;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.attribute.AttributeModifier;
import org.bukkit.block.Block;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Interaction;
import org.bukkit.entity.Item;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.event.entity.EntityTargetLivingEntityEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.Damageable;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.util.Vector;

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

    private static final Set<Material> MOLE_BLOCKS = EnumSet.of(
        Material.DIRT,
        Material.COARSE_DIRT,
        Material.ROOTED_DIRT,
        Material.GRASS_BLOCK,
        Material.PODZOL,
        Material.MYCELIUM,
        Material.DIRT_PATH,
        Material.FARMLAND,
        Material.MUD,
        Material.CLAY,
        Material.GRAVEL,
        Material.SAND,
        Material.RED_SAND,
        Material.SUSPICIOUS_SAND,
        Material.SUSPICIOUS_GRAVEL,
        Material.SOUL_SAND,
        Material.SOUL_SOIL
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
    private final NamespacedKey ownerKey;
    private final NamespacedKey petUuidKey;
    private final Map<UUID, ActivePet> activePets = new HashMap<>();
    private final Map<UUID, RideState> rides = new HashMap<>();
    private final Map<UUID, Set<PotionEffectType>> petBuffs = new HashMap<>();
    private BukkitTask task;
    private long tick;

    public ActivePetManager(final JavaPlugin plugin, final PetDefinitions definitions, final PetStorage storage, final PetItemFactory itemFactory) {
        this.plugin = plugin;
        this.definitions = definitions;
        this.storage = storage;
        this.itemFactory = itemFactory;
        this.ownerKey = new NamespacedKey(plugin, "active_owner");
        this.petUuidKey = new NamespacedKey(plugin, "active_pet");
    }

    public void start() {
        final int removed = cleanupStaleDisplays();
        final int interval = Math.max(1, plugin.getConfig().getInt("follow-update-ticks", 3));
        task = Bukkit.getScheduler().runTaskTimer(plugin, this::tick, interval, interval);
        Bukkit.getScheduler().runTaskLater(plugin, () -> Bukkit.getOnlinePlayers().forEach(this::spawnSavedActivePet), 20L);
        plugin.getLogger().info("Active pet manager started. Removed " + removed + " stale display(s). Follow interval: " + interval + " ticks.");
    }

    public void stop() {
        if (task != null) {
            task.cancel();
            task = null;
        }
        Bukkit.getOnlinePlayers().forEach(player -> stopRide(player, false));
        activePets.values().forEach(active -> {
            active.display().remove();
            active.hitbox().remove();
        });
        activePets.clear();
        Bukkit.getOnlinePlayers().forEach(this::resetPlayerState);
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

        final Location location = player.getLocation().clone().add(0, plugin.getConfig().getDouble("follow-height", 1.2), 0);
        final ItemDisplay display = player.getWorld().spawn(location, ItemDisplay.class, entity -> {
            entity.setItemStack(itemFactory.menuItem(definition, pet, true));
            entity.setItemDisplayTransform(ItemDisplay.ItemDisplayTransform.GROUND);
            entity.setBillboard(org.bukkit.entity.Display.Billboard.FIXED);
            entity.setTeleportDuration(Math.max(1, plugin.getConfig().getInt("follow-teleport-duration-ticks", 8)));
            entity.setShadowRadius(0.15F);
            entity.setViewRange(storage.data(player.getUniqueId()).visible() ? 32.0F : 0.0F);
            entity.customName(petNickname(definition, pet));
            entity.setCustomNameVisible(true);
            entity.setPersistent(false);
            entity.addScoreboardTag("BetterPets.Pet");
            entity.getPersistentDataContainer().set(ownerKey, PersistentDataType.STRING, player.getUniqueId().toString());
            entity.getPersistentDataContainer().set(petUuidKey, PersistentDataType.STRING, pet.uuid().toString());
        });
        final boolean visible = storage.data(player.getUniqueId()).visible();
        final Interaction hitbox = player.getWorld().spawn(location, Interaction.class, entity -> {
            entity.setInteractionWidth(visible ? 1.2F : 0.1F);
            entity.setInteractionHeight(visible ? 1.8F : 0.1F);
            entity.setResponsive(visible);
            entity.setPersistent(false);
            entity.addScoreboardTag("BetterPets.Pet");
            entity.getPersistentDataContainer().set(ownerKey, PersistentDataType.STRING, player.getUniqueId().toString());
            entity.getPersistentDataContainer().set(petUuidKey, PersistentDataType.STRING, pet.uuid().toString());
        });

        activePets.put(player.getUniqueId(), new ActivePet(pet, display, hitbox, player.getLocation().getYaw()));
        applyPassive(player, pet);
        if (plugin.getConfig().getBoolean("debug-logging", true)) {
            plugin.getLogger().info("[Debug] Spawned active pet " + definition.id() + " for " + player.getName() + ".");
        }
    }

    public void despawn(final Player player, final boolean clearActive) {
        stopRide(player, false);
        final ActivePet active = activePets.remove(player.getUniqueId());
        if (active != null) {
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
            active.display().setItemStack(itemFactory.menuItem(definition, active.pet(), true));
            active.display().customName(petNickname(definition, active.pet()));
        });
        applyPassive(player, active.pet());
    }

    public void setVisible(final Player player, final boolean visible) {
        final ActivePet active = activePets.get(player.getUniqueId());
        if (active != null) {
            active.display().setViewRange(visible ? 32.0F : 0.0F);
            active.hitbox().setInteractionWidth(visible ? 1.2F : 0.1F);
            active.hitbox().setInteractionHeight(visible ? 1.8F : 0.1F);
            active.hitbox().setResponsive(visible);
        }
    }

    public boolean handlePetInteraction(final Player player, final Entity clicked) {
        final ActivePet active = clickedActive(player, clicked).orElse(null);
        if (active == null) {
            return false;
        }
        if (!isDragon(active.pet().definitionId())) {
            return true;
        }
        if (active.pet().level() < 50) {
            player.sendMessage(Component.text("This dragon can be flown from level 50.", net.kyori.adventure.text.format.NamedTextColor.YELLOW));
            return true;
        }
        if (rides.containsKey(player.getUniqueId())) {
            stopRide(player, true);
            return true;
        }
        final ArmorStand mount = player.getWorld().spawn(active.display().getLocation(), ArmorStand.class, entity -> {
            entity.setVisible(false);
            entity.setGravity(false);
            entity.setInvulnerable(true);
            entity.setCollidable(false);
            entity.setSmall(true);
            entity.setPersistent(false);
            entity.addScoreboardTag("BetterPets.Ride");
            entity.getPersistentDataContainer().set(ownerKey, PersistentDataType.STRING, player.getUniqueId().toString());
            entity.getPersistentDataContainer().set(petUuidKey, PersistentDataType.STRING, active.pet().uuid().toString());
        });
        if (player.isInsideVehicle()) {
            player.leaveVehicle();
        }
        if (!mount.addPassenger(player)) {
            mount.remove();
            player.sendMessage(Component.text("Dragon flight could not start here.", net.kyori.adventure.text.format.NamedTextColor.RED));
            return true;
        }
        rides.put(player.getUniqueId(), new RideState(mount));
        player.sendMessage(Component.text("Dragon flight enabled. Sneak to dismount.", net.kyori.adventure.text.format.NamedTextColor.GOLD));
        plugin.getLogger().info("[Debug] " + player.getName() + " started dragon flight with " + active.pet().definitionId() + ".");
        return true;
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
        state.input(input);
        if (input.isSneak()) {
            stopRide(player, true);
        }
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
        if (notify) {
            player.sendMessage(Component.text("Dragon flight disabled.", net.kyori.adventure.text.format.NamedTextColor.GRAY));
        }
    }

    public boolean isRideMount(final Entity entity) {
        return entity != null && entity.getScoreboardTags().contains("BetterPets.Ride");
    }

    private boolean isDragon(final String id) {
        return id.equals("blue_dragon") || id.equals("red_dragon") || id.equals("ender_dragon");
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
        switch (pet.definitionId()) {
            case "warden" -> {
                if (event.getEntityType() == EntityType.WARDEN) {
                    event.setCancelled(true);
                }
            }
            case "cursed_plushie" -> {
                final double chance = Math.min(0.75, 0.25 + (abilityTier(pet.level()) * 0.025));
                if (isHostile(event.getEntity()) && ThreadLocalRandom.current().nextDouble() < chance) {
                    event.setCancelled(true);
                    spawnPlushieDistraction(player);
                }
            }
            default -> {
            }
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

        final int level = pet.level();
        switch (pet.definitionId()) {
            case "dog" -> {
                if (isUndead(victim) && Math.random() <= Math.min(1.0, level / 100.0)) {
                    victim.addPotionEffect(new PotionEffect(PotionEffectType.WITHER, 40, 1, true, false, true));
                }
            }
            case "phoenix" -> {
                if (isUndead(victim)) {
                    victim.setFireTicks(Math.max(victim.getFireTicks(), level * 2));
                }
            }
            case "reaper" -> {
                player.addPotionEffect(new PotionEffect(PotionEffectType.REGENERATION, 100, 1, true, false, true));
                player.addPotionEffect(new PotionEffect(PotionEffectType.STRENGTH, 100, 1, true, false, true));
            }
            default -> {
            }
        }
    }

    public void applyDefenseAbility(final Player player, final Entity damager) {
        final OwnedPet pet = activePet(player).orElse(null);
        if (pet == null) {
            return;
        }

        final int tier = abilityTier(pet.level());
        switch (pet.definitionId()) {
            case "hedgehog" -> {
                if (damager instanceof LivingEntity living && !living.equals(player)) {
                    living.damage(Math.min(3.0, 0.4 + (tier * 0.08)));
                }
            }
            case "platypus" -> {
                final LivingEntity attacker = resolveAttacker(player, damager);
                if (attacker != null && isWetOrNearWater(player)) {
                    // Undead mobs are immune to Poison, so wither them instead for a visible effect.
                    final PotionEffectType effect = isUndead(attacker) ? PotionEffectType.WITHER : PotionEffectType.POISON;
                    attacker.addPotionEffect(new PotionEffect(effect, 80 + (tier * 6), 0, true, false, true));
                }
            }
            default -> {
            }
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

        if (pet.definitionId().equals("ghast") && player.getWorld().getEnvironment() == World.Environment.NETHER) {
            player.giveExp(10);
        }
    }

    private void tick() {
        tick++;
        final int abilityInterval = Math.max(20, plugin.getConfig().getInt("ability-update-ticks", 100));

        for (final Player player : Bukkit.getOnlinePlayers()) {
            final PlayerPetData data = storage.data(player.getUniqueId());
            final OwnedPet pet = data.activePet().orElse(null);
            if (pet == null) {
                despawn(player, false);
                continue;
            }

            ActivePet active = activePets.get(player.getUniqueId());
            if (active == null || active.display().isDead() || active.hitbox().isDead() || !active.pet().uuid().equals(pet.uuid())) {
                spawn(player, pet);
                active = activePets.get(player.getUniqueId());
            }
            if (active == null) {
                continue;
            }

            final RideState ride = rides.get(player.getUniqueId());
            if (ride != null) {
                updateRide(player, active, ride);
            }
            follow(player, active);
            if (ride != null && tick % 4L == 0L) {
                spawnDragonTrail(player, pet);
            } else if (ride == null && isDragon(pet.definitionId()) && tick % 6L == 0L) {
                spawnPetWalkTrail(player, pet, active);
            }
            if (pet.definitionId().equals("unicorn") && pet.level() >= 50 && tick % 6L == 0L) {
                spawnUnicornGlitter(player);
            }
            updateReveals(player, pet);
            if (pet.definitionId().equals("allay") && tick % 10L == 0L) {
                collectAllayItems(player, Math.min(12.0, 4.0 + (abilityTier(pet.level()) * 0.4)));
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

        final RideState ride = rides.get(player.getUniqueId());
        final Location target = ride == null ? followLocation(player, active, tick) : rideLocation(player, ride, tick);
        final double teleportDistance = Math.max(4.0, plugin.getConfig().getDouble("follow-teleport-distance", 24.0));
        if (!display.getWorld().equals(player.getWorld()) || display.getLocation().distanceSquared(target) > teleportDistance * teleportDistance) {
            active.followYaw(player.getLocation().getYaw());
            display.teleport(target);
            active.hitbox().teleport(target);
            return;
        }
        if (ride != null || display.getLocation().distanceSquared(target) > 0.10) {
            display.teleport(target);
            active.hitbox().teleport(target);
        }
    }

    private void updateRide(final Player player, final ActivePet active, final RideState ride) {
        if (ride.mount().isDead() || !ride.mount().getPassengers().contains(player)) {
            stopRide(player, false);
            return;
        }
        if (!ride.mount().getWorld().equals(player.getWorld())) {
            ride.mount().teleport(player.getLocation());
        }

        Vector forward = player.getLocation().getDirection().clone();
        if (forward.lengthSquared() < 0.01) {
            forward = new Vector(0, 0, 1);
        }
        forward.normalize();
        Vector flatForward = player.getLocation().getDirection().clone();
        flatForward.setY(0);
        if (flatForward.lengthSquared() < 0.01) {
            flatForward = new Vector(0, 0, 1);
        }
        flatForward.normalize();
        final Vector right = new Vector(-flatForward.getZ(), 0, flatForward.getX()).normalize();
        final Vector motion = new Vector();
        if (ride.forward()) {
            motion.add(forward);
        }
        if (ride.backward()) {
            motion.subtract(forward);
        }
        if (ride.right()) {
            motion.add(right);
        }
        if (ride.left()) {
            motion.subtract(right);
        }
        if (motion.lengthSquared() > 0.01) {
            motion.normalize().multiply(Math.max(0.1, plugin.getConfig().getDouble("dragon-flight-speed", 1.5)));
        }
        if (ride.jump()) {
            motion.setY(Math.max(motion.getY(), Math.max(0.28, plugin.getConfig().getDouble("dragon-flight-lift", 0.55))));
        } else if (motion.lengthSquared() <= 0.01) {
            motion.setY(Math.sin(tick / 12.0) * 0.01);
        }

        final Location target = ride.mount().getLocation().clone().add(motion);
        target.setDirection(player.getLocation().getDirection());
        if (isRideLocationSafe(target)) {
            ride.mount().teleport(target);
        } else {
            final Location verticalOnly = ride.mount().getLocation().clone().add(0, motion.getY(), 0);
            verticalOnly.setDirection(player.getLocation().getDirection());
            if (isRideLocationSafe(verticalOnly)) {
                ride.mount().teleport(verticalOnly);
            }
        }
    }

    private boolean isRideLocationSafe(final Location location) {
        if (location.getY() <= location.getWorld().getMinHeight() + 1 || location.getY() >= location.getWorld().getMaxHeight() - 1) {
            return false;
        }
        return location.getBlock().isPassable() && location.clone().add(0, 1, 0).getBlock().isPassable();
    }

    private Location rideLocation(final Player player, final RideState ride, final long tick) {
        final Location base = ride.mount().getLocation().clone();
        Vector direction = base.getDirection().clone();
        if (direction.lengthSquared() < 0.01) {
            direction = player.getLocation().getDirection().clone();
        }
        direction.setY(0);
        if (direction.lengthSquared() < 0.01) {
            direction = new Vector(0, 0, 1);
        }
        direction.normalize();
        final double bob = Math.sin(tick / 6.0) * 0.08;
        final Location target = base.add(0, -0.35 + bob, 0);
        target.setDirection(player.getLocation().getDirection());
        return target;
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
        final Location target = base.add(offset).add(0, plugin.getConfig().getDouble("follow-height", 1.2) + bob, 0);
        target.setDirection(target.toVector().subtract(player.getEyeLocation().toVector()));
        return target;
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

    private void spawnDragonTrail(final Player player, final OwnedPet pet) {
        final Location location = player.getLocation().add(0, 0.8, 0);
        try {
            switch (pet.definitionId()) {
                case "ender_dragon" -> player.getWorld().spawnParticle(Particle.DRAGON_BREATH, location, 14, 0.55, 0.35, 0.55, 0.01, 1.0F);
                case "blue_dragon" -> player.getWorld().spawnParticle(Particle.ENCHANT, location, 22, 0.65, 0.45, 0.65, 0.0);
                case "red_dragon" -> player.getWorld().spawnParticle(Particle.FLAME, location, 14, 0.45, 0.3, 0.45, 0.01);
                default -> {
                }
            }
        } catch (final IllegalArgumentException exception) {
            if (pet.definitionId().equals("ender_dragon")) {
                player.getWorld().spawnParticle(Particle.PORTAL, location, 16, 0.55, 0.35, 0.55, 0.0);
            }
        }
    }

    private Component petNickname(final PetDefinition definition, final OwnedPet pet) {
        return Component.text("[Lvl " + pet.level() + "] " + definition.name(), definition.rarityColor())
            .decoration(TextDecoration.ITALIC, false);
    }

    private void applyPeriodicAbilities(final Player player, final OwnedPet pet) {
        switch (pet.definitionId()) {
            case "blue_dragon", "red_dragon" -> applyPetBuff(player, PotionEffectType.ABSORPTION, 3, 220);
            case "ender_dragon" -> applyPetBuff(player, PotionEffectType.ABSORPTION, 2, 220);
            case "capybara" -> {
                if (isWetOrNearWater(player)) {
                    applyPetBuff(player, PotionEffectType.REGENERATION, player.getWorld().hasStorm() && pet.level() >= 80 ? 1 : 0);
                }
            }
            case "chicken" -> applyPetBuff(player, PotionEffectType.SLOW_FALLING, 1);
            case "duck" -> {
                if (isAirborne(player)) {
                    applyPetBuff(player, PotionEffectType.SLOW_FALLING, 0);
                }
            }
            case "koala" -> {
                if (nearTree(player)) {
                    applyPetBuff(player, PotionEffectType.REGENERATION, 0);
                }
            }
            case "panda" -> {
                if (biomeKey(player).contains("bamboo_jungle")) {
                    applyPetBuff(player, PotionEffectType.HERO_OF_THE_VILLAGE, 1);
                }
            }
            case "penguin" -> freezeWaterNear(player);
            case "pufferfish" -> affectNearby(player, 7, true, entity -> entity.addPotionEffect(new PotionEffect(PotionEffectType.WITHER, 100, 1, true, false, true)));
            case "reaper" -> {
                final int radius = pet.level() >= 100 ? 25 : pet.level() >= 50 ? 20 : 15;
                affectNearby(player, radius, true, entity -> {
                    entity.addPotionEffect(new PotionEffect(PotionEffectType.WITHER, 160, 1, true, false, true));
                    entity.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, 160, 1, true, false, true));
                });
            }
            case "warden" -> Bukkit.getWorlds().forEach(world -> world.getEntitiesByClass(org.bukkit.entity.Warden.class).forEach(warden -> {
                if (warden.getTarget() != null && warden.getTarget().equals(player)) {
                    warden.setTarget(null);
                }
            }));
            case "herobrine" -> {
                player.getWorld().setStorm(true);
                player.getWorld().setThundering(true);
            }
            case "unicorn" -> applyPetBuff(player, PotionEffectType.REGENERATION, pet.level() >= 80 ? 1 : 0);
            default -> {
            }
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
        player.sendMessage(Component.text("Your Phoenix saved you from death!", net.kyori.adventure.text.format.NamedTextColor.GOLD));
        storage.save();
        return true;
    }

    public void handleMoleBreak(final Player player, final Block block) {
        final OwnedPet pet = activePet(player).orElse(null);
        if (pet == null || !pet.definitionId().equals("mole") || !MOLE_BLOCKS.contains(block.getType())) {
            return;
        }
        final double chance = Math.min(0.6, 0.1 + (abilityTier(pet.level()) * 0.025));
        if (ThreadLocalRandom.current().nextDouble() >= chance) {
            return;
        }
        final ItemStack tool = player.getInventory().getItemInMainHand();
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

    private void collectAllayItems(final Player player, final double radius) {
        for (final Entity entity : player.getNearbyEntities(radius, radius, radius)) {
            if (!(entity instanceof Item itemEntity) || itemEntity.getPickupDelay() > 0 || !itemEntity.canPlayerPickup()) {
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

    private void applyPassive(final Player player, final OwnedPet pet) {
        resetPlayerState(player);
        final int level = pet.level();
        final int tier = abilityTier(level);
        switch (pet.definitionId()) {
            case "ant" -> setTarget(player, Attribute.SCALE, Math.max(0.5, 1.0 - (tier * 0.025)));
            case "axolotl" -> setTarget(player, Attribute.OXYGEN_BONUS, tier * 0.5);
            case "alpaca" -> {
                if (usedInventorySlots(player) >= 27) {
                    setTarget(player, Attribute.MOVEMENT_SPEED, 0.1 + (tier * 0.0015));
                }
            }
            case "beaver" -> {
                if (isHoldingTool(player, "_AXE")) {
                    setTarget(player, Attribute.MINING_EFFICIENCY, tier * 0.35);
                }
            }
            case "blue_dragon" -> {
                if (player.getWorld().getEnvironment() == World.Environment.THE_END) {
                    setTarget(player, Attribute.ATTACK_DAMAGE, 2.0 + (tier * 0.3));
                }
            }
            case "cat" -> setTarget(player, Attribute.FALL_DAMAGE_MULTIPLIER, Math.max(0.0, 1.0 - (tier * 0.05)));
            case "dolphin" -> {
                applyPetBuff(player, PotionEffectType.DOLPHINS_GRACE, 1);
                setTarget(player, Attribute.WATER_MOVEMENT_EFFICIENCY, tier * 0.05);
            }
            case "elder_guardian" -> setTarget(player, Attribute.SUBMERGED_MINING_SPEED, Math.min(1.0, 0.2 + (tier * 0.04)));
            case "ender_dragon" -> {
                if (player.getWorld().getEnvironment() == World.Environment.THE_END) {
                    setTarget(player, Attribute.ATTACK_DAMAGE, 2.0 + (tier * 0.35));
                }
            }
            case "ghast" -> setTarget(player, Attribute.EXPLOSION_KNOCKBACK_RESISTANCE, tier * 0.05);
            case "hamster" -> setTarget(player, Attribute.STEP_HEIGHT, 0.6 + (tier * 0.045));
            case "herobrine" -> {
                setTarget(player, Attribute.MAX_HEALTH, 20.0 + tier);
                setTarget(player, Attribute.ENTITY_INTERACTION_RANGE, 3.0 + (tier * 0.1125));
            }
            case "owl" -> {
                applyPetBuff(player, PotionEffectType.NIGHT_VISION, 1);
                setTarget(player, Attribute.LUCK, tier * 25.0);
            }
            case "panda" -> setTarget(player, Attribute.ATTACK_KNOCKBACK, tier * 0.05);
            case "penguin" -> {
                if (isColdBiome(player)) {
                    setTarget(player, Attribute.MOVEMENT_SPEED, 0.1 + (tier * 0.00375));
                }
            }
            case "polar_bear" -> {
                if (isColdBiome(player)) {
                    setTarget(player, Attribute.ARMOR, tier * 0.25);
                }
            }
            case "phoenix" -> applyPetBuff(player, PotionEffectType.FIRE_RESISTANCE, 1);
            case "red_panda" -> {
                if (isForestBiome(player)) {
                    setTarget(player, Attribute.MOVEMENT_SPEED, 0.1 + (tier * 0.002));
                }
                setTarget(player, Attribute.SNEAKING_SPEED, 0.3 + (tier * 0.02));
            }
            case "rabbit" -> {
                setTarget(player, Attribute.SAFE_FALL_DISTANCE, 10.0);
                setTarget(player, Attribute.JUMP_STRENGTH, Math.min(1.01, 0.4 + (tier * 0.03158)));
            }
            case "reaper" -> setTarget(player, Attribute.ATTACK_SPEED, 4.0 + (tier * 0.2));
            case "red_dragon" -> {
                if (player.getWorld().getEnvironment() == World.Environment.NETHER) {
                    setTarget(player, Attribute.ATTACK_DAMAGE, 2.0 + (tier * 0.3));
                }
            }
            case "snail" -> setTarget(player, Attribute.SNEAKING_SPEED, 0.3 + (tier * 0.035));
            case "spinosaurus" -> setTarget(player, Attribute.SCALE, 1.0 + (tier * 0.025));
            case "slime" -> {
                setTarget(player, Attribute.SAFE_FALL_DISTANCE, 5.0 + tier);
                setTarget(player, Attribute.JUMP_STRENGTH, Math.min(0.85, 0.4 + (tier * 0.018)));
            }
            case "tiger" -> {
                setTarget(player, Attribute.SWEEPING_DAMAGE_RATIO, 1.0);
                setTarget(player, Attribute.MOVEMENT_SPEED, 0.1 + (Math.min(100, level) * 0.001));
            }
            case "turtle" -> setTarget(player, Attribute.KNOCKBACK_RESISTANCE, tier * 5.0);
            case "duck" -> setTarget(player, Attribute.WATER_MOVEMENT_EFFICIENCY, tier * 0.035);
            case "unicorn" -> setTarget(player, Attribute.LUCK, tier * 8.0);
            case "worm" -> setTarget(player, Attribute.MINING_EFFICIENCY, tier * 0.5);
            default -> {
            }
        }
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
            if (plugin.getConfig().getBoolean("debug-logging", true)) {
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
        switch (pet.definitionId()) {
            case "bat" -> {
                if (player.getLocation().getY() < 50.0) {
                    revealMobs(player, Math.min(24, 8 + abilityTier(pet.level())), false);
                }
            }
            case "red_parrot" -> {
                if (player.getLocation().getBlock().getLightFromSky() > 0) {
                    revealMobs(player, Math.min(30, 10 + (pet.level() / 10) * 2), false);
                }
            }
            case "warden" -> revealMobs(player, Math.min(50, Math.max(10, pet.level())), true);
            default -> {
            }
        }
    }

    /**
     * Keeps every matching mob inside the radius highlighted continuously. The glow is given a short
     * duration and refreshed every tick run, so a mob stops glowing shortly after it leaves the radius.
     */
    private void revealMobs(final Player player, final double radius, final boolean undeadOnly) {
        for (final Entity entity : player.getNearbyEntities(radius, radius, radius)) {
            if (!(entity instanceof LivingEntity living) || entity.equals(player)) {
                continue;
            }
            if (undeadOnly ? !isUndead(living) : !isHostile(living)) {
                continue;
            }
            living.addPotionEffect(new PotionEffect(PotionEffectType.GLOWING, 15, 0, true, false, false));
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

    private boolean isHoldingTool(final Player player, final String suffix) {
        return player.getInventory().getItemInMainHand().getType().name().endsWith(suffix);
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
            final List<ArmorStand> staleMounts = world.getEntitiesByClass(ArmorStand.class).stream()
                .filter(entity -> entity.getScoreboardTags().contains("BetterPets.Ride"))
                .toList();
            removed += staleMounts.size();
            staleMounts.forEach(Entity::remove);
        }
        return removed;
    }

    private static final class ActivePet {
        private final OwnedPet pet;
        private final ItemDisplay display;
        private final Interaction hitbox;
        private float followYaw;

        private ActivePet(final OwnedPet pet, final ItemDisplay display, final Interaction hitbox, final float followYaw) {
            this.pet = pet;
            this.display = display;
            this.hitbox = hitbox;
            this.followYaw = followYaw;
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
