package de.kamil.betterpets;

import com.destroystokyo.paper.profile.PlayerProfile;
import com.destroystokyo.paper.profile.ProfileProperty;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.SkullMeta;
import org.bukkit.inventory.meta.components.EquippableComponent;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;

public final class PetItemFactory {
    private final NamespacedKey petIdKey;
    private final NamespacedKey petUuidKey;
    private final NamespacedKey petLevelKey;
    private final NamespacedKey petExpKey;
    private final NamespacedKey petNameKey;
    private final NamespacedKey petVariantKey;
    private final NamespacedKey petFusionKey;
    private final NamespacedKey petNametagKey;
    private final NamespacedKey boosterTierKey;
    private final NamespacedKey boosterMinutesKey;
    private final java.util.Random random = new java.util.Random();
    private final LangManager lang;

    public PetItemFactory(final JavaPlugin plugin, final LangManager lang) {
        this.lang = lang;
        this.petIdKey = new NamespacedKey(plugin, "pet_id");
        this.petUuidKey = new NamespacedKey(plugin, "pet_uuid");
        this.petLevelKey = new NamespacedKey(plugin, "pet_level");
        this.petExpKey = new NamespacedKey(plugin, "pet_exp");
        this.petNameKey = new NamespacedKey(plugin, "pet_name");
        this.petVariantKey = new NamespacedKey(plugin, "pet_variant");
        this.petFusionKey = new NamespacedKey(plugin, "pet_fusion");
        this.petNametagKey = new NamespacedKey(plugin, "pet_nametag");
        this.boosterTierKey = new NamespacedKey(plugin, "booster_tier");
        this.boosterMinutesKey = new NamespacedKey(plugin, "booster_minutes");
    }

    /** Translated menu component (italics stripped, since these items don't pass through control()). */
    private Component ml(final String key, final NamedTextColor color, final String... repl) {
        return lang.colored(key, color, repl).decoration(TextDecoration.ITALIC, false);
    }

    private Component mg(final String key, final String... repl) {
        return lang.component(key, repl).decoration(TextDecoration.ITALIC, false);
    }

    private String mt(final String key, final String... repl) {
        return lang.raw(key, repl);
    }

    public ItemStack boosterItem(final int tier, final int minutes) {
        final ItemStack item = new ItemStack(Material.EXPERIENCE_BOTTLE);
        final ItemMeta meta = item.getItemMeta();
        meta.displayName(ml("item.booster.name", NamedTextColor.LIGHT_PURPLE, "%tier%", Integer.toString(tier)));
        meta.lore(List.of(
            ml("item.booster.header", NamedTextColor.GOLD).decorate(TextDecoration.BOLD),
            ml("item.booster.multiplier", NamedTextColor.GRAY, "%tier%", Integer.toString(tier)),
            ml("item.booster.duration", NamedTextColor.GRAY, "%time%", formatMinutes(minutes)),
            Component.empty().decoration(TextDecoration.ITALIC, false),
            ml("item.booster.use", NamedTextColor.GOLD).decorate(TextDecoration.BOLD),
            ml("item.booster.activate", NamedTextColor.YELLOW),
            ml("item.booster.no-stack", NamedTextColor.DARK_GRAY)
        ));
        meta.getPersistentDataContainer().set(boosterTierKey, PersistentDataType.INTEGER, tier);
        meta.getPersistentDataContainer().set(boosterMinutesKey, PersistentDataType.INTEGER, minutes);
        meta.setEnchantmentGlintOverride(true);
        item.setItemMeta(meta);
        return item;
    }

    /** Booster tier of a booster item (2..5), or 0 if the item is not a booster. */
    public int boosterTier(final ItemStack item) {
        if (item == null || !item.hasItemMeta()) {
            return 0;
        }
        final Integer tier = item.getItemMeta().getPersistentDataContainer().get(boosterTierKey, PersistentDataType.INTEGER);
        return tier == null ? 0 : tier;
    }

    public int boosterMinutes(final ItemStack item) {
        if (item == null || !item.hasItemMeta()) {
            return 0;
        }
        final Integer minutes = item.getItemMeta().getPersistentDataContainer().get(boosterMinutesKey, PersistentDataType.INTEGER);
        return minutes == null ? 0 : minutes;
    }

    public ItemStack discoveryItem(final PetDefinition definition) {
        return discoveryItem(definition, 1);
    }

    public ItemStack discoveryItem(final PetDefinition definition, final int level) {
        // Roll a cosmetic variant so the loot/discovery item already shows (and later grants) the exact skin.
        return discoveryItem(definition, level, definition.randomVariant(random));
    }

    /** Builds a discovery item for a specific variant (null = definition has none / roll skipped). */
    public ItemStack discoveryItem(final PetDefinition definition, final int level, final String variant) {
        final OwnedPet pet = OwnedPet.create(definition.id(), level);
        if (variant != null) {
            pet.setVariant(variant);
        }
        return petItem(definition, pet, false, true);
    }

    public ItemStack discoveryItem(final PetDefinition definition, final OwnedPet pet) {
        return petItem(definition, pet, false, true);
    }

    public ItemStack menuItem(final PetDefinition definition, final OwnedPet pet, final boolean active) {
        return petItem(definition, pet, active, false);
    }

    public ItemStack chanceItem(final PetDefinition definition, final double chance) {
        return customPetItem(
            definition,
            Component.text(definition.name() + " - " + formatPercent(chance) + "%", definition.rarityColor())
                .decoration(TextDecoration.ITALIC, false),
            List.of(
                ml("menu.detail.rarity-pet", definition.rarityColor(), "%rarity%", definition.rarity()).decorate(TextDecoration.BOLD),
                Component.empty(),
                ml("item.chance.weight", NamedTextColor.GRAY, "%n%", formatPercent(chance)),
                Component.empty(),
                ml("item.chance.plus", NamedTextColor.GREEN),
                ml("item.chance.minus", NamedTextColor.RED),
                ml("item.chance.shift", NamedTextColor.YELLOW),
                ml("item.chance.exact", NamedTextColor.AQUA),
                ml("item.chance.range", NamedTextColor.DARK_GRAY)
            )
        );
    }

    public ItemStack infoItem(final PetDefinition definition, final List<Component> abilityLore) {
        final List<Component> lore = new ArrayList<>();
        lore.add(Component.text(definition.rarity() + " Pet", definition.rarityColor()).decorate(TextDecoration.BOLD));
        lore.add(Component.empty());
        lore.addAll(abilityLore);
        return customPetItem(
            definition,
            Component.text(definition.name(), definition.rarityColor())
                .decoration(TextDecoration.ITALIC, false),
            lore
        );
    }

    public Optional<String> petId(final ItemStack item) {
        if (item == null || !item.hasItemMeta()) {
            return Optional.empty();
        }
        final String id = item.getItemMeta().getPersistentDataContainer().get(petIdKey, PersistentDataType.STRING);
        return id == null || id.isBlank() ? Optional.empty() : Optional.of(id);
    }

    public Optional<UUID> petUuid(final ItemStack item) {
        if (item == null || !item.hasItemMeta()) {
            return Optional.empty();
        }
        final String value = item.getItemMeta().getPersistentDataContainer().get(petUuidKey, PersistentDataType.STRING);
        if (value == null || value.isBlank()) {
            return Optional.empty();
        }
        try {
            return Optional.of(UUID.fromString(value));
        } catch (final IllegalArgumentException ignored) {
            return Optional.empty();
        }
    }

    public int petLevel(final ItemStack item) {
        if (item == null || !item.hasItemMeta()) {
            return 1;
        }
        final Integer level = item.getItemMeta().getPersistentDataContainer().get(petLevelKey, PersistentDataType.INTEGER);
        return level == null ? 1 : Math.max(1, Math.min(100, level));
    }

    /** Saved in-level XP stored on a converted pet item, or 0 if none. */
    public int petExp(final ItemStack item) {
        if (item == null || !item.hasItemMeta()) {
            return 0;
        }
        final Integer exp = item.getItemMeta().getPersistentDataContainer().get(petExpKey, PersistentDataType.INTEGER);
        return exp == null ? 0 : Math.max(0, exp);
    }

    /** The cosmetic variant stored on a discovery/give pet item, or empty if none. */
    public Optional<String> petVariant(final ItemStack item) {
        if (item == null || !item.hasItemMeta()) {
            return Optional.empty();
        }
        final String variant = item.getItemMeta().getPersistentDataContainer().get(petVariantKey, PersistentDataType.STRING);
        return variant == null || variant.isBlank() ? Optional.empty() : Optional.of(variant);
    }

    /** Ascension fusion points stored on a pet item (0 if none), so stars survive convert/trade. */
    public int petFusionPoints(final ItemStack item) {
        if (item == null || !item.hasItemMeta()) {
            return 0;
        }
        final Integer points = item.getItemMeta().getPersistentDataContainer().get(petFusionKey, PersistentDataType.INTEGER);
        return points == null ? 0 : Math.max(0, points);
    }

    /** The chosen nametag-style cosmetic stored on a pet item, or empty if none. */
    public Optional<String> petNametagStyle(final ItemStack item) {
        if (item == null || !item.hasItemMeta()) {
            return Optional.empty();
        }
        final String style = item.getItemMeta().getPersistentDataContainer().get(petNametagKey, PersistentDataType.STRING);
        return style == null || style.isBlank() ? Optional.empty() : Optional.of(style);
    }

    /**
     * Resolves a pet item's variant: first the stored tag, then (fallback) by matching the head's texture
     * against the definition's variant textures. The fallback recovers the skin from older items that carry
     * the variant head but not the tag, so scrapping them still collects the right skin.
     */
    public Optional<String> resolveVariant(final ItemStack item, final PetDefinition definition) {
        final Optional<String> tagged = petVariant(item);
        if (tagged.isPresent() || definition == null || !definition.hasVariants()
            || item == null || !(item.getItemMeta() instanceof SkullMeta skull)) {
            return tagged;
        }
        final PlayerProfile profile = skull.getPlayerProfile();
        if (profile == null) {
            return Optional.empty();
        }
        for (final ProfileProperty property : profile.getProperties()) {
            if (!"textures".equals(property.getName())) {
                continue;
            }
            final String texture = property.getValue();
            for (final java.util.Map.Entry<String, String> entry : definition.variants().entrySet()) {
                if (entry.getValue().equals(texture)) {
                    return Optional.of(entry.getKey());
                }
            }
        }
        return Optional.empty();
    }

    public Optional<String> petCustomName(final ItemStack item) {
        if (item == null || !item.hasItemMeta()) {
            return Optional.empty();
        }
        final String name = item.getItemMeta().getPersistentDataContainer().get(petNameKey, PersistentDataType.STRING);
        return name == null || name.isBlank() ? Optional.empty() : Optional.of(name);
    }

    public ItemStack control(final Material material, final Component name, final List<Component> lore) {
        final ItemStack item = new ItemStack(material);
        final ItemMeta meta = item.getItemMeta();
        meta.displayName(name.decoration(TextDecoration.ITALIC, false));
        meta.lore(lore.stream().map(component -> component.decoration(TextDecoration.ITALIC, false)).toList());
        item.setItemMeta(meta);
        return item;
    }

    /** A display-only head showing one specific cosmetic variant of a pet (used in the variant gallery). */
    public ItemStack variantIcon(final PetDefinition definition, final String variant) {
        final ItemStack item = new ItemStack(Material.PLAYER_HEAD);
        final SkullMeta meta = (SkullMeta) item.getItemMeta();
        meta.displayName(Component.text(PetDefinition.variantDisplay(variant), definition.rarityColor())
            .decoration(TextDecoration.ITALIC, false));
        meta.lore(List.of(
            Component.text(definition.name() + " variant", NamedTextColor.GRAY).decoration(TextDecoration.ITALIC, false)
        ));
        applyPetIdentity(meta, definition, 1, variant);
        item.setItemMeta(meta);
        return item;
    }

    private ItemStack petItem(final PetDefinition definition, final OwnedPet pet, final boolean active, final boolean discovery) {
        final ItemStack item = new ItemStack(Material.PLAYER_HEAD);
        final SkullMeta meta = (SkullMeta) item.getItemMeta();
        meta.displayName(title(definition, pet));

        final List<Component> lore = new ArrayList<>();
        lore.add(ml("item.pet.rarity", NamedTextColor.GRAY).append(Component.text(definition.rarity(), definition.rarityColor()).decoration(TextDecoration.ITALIC, false)));
        if (pet != null && pet.variant() != null && definition.hasVariants()) {
            lore.add(ml("item.pet.variant", NamedTextColor.GRAY)
                .append(Component.text(PetDefinition.variantDisplay(pet.variant()), NamedTextColor.AQUA).decoration(TextDecoration.ITALIC, false)));
        }
        lore.add(Component.empty());
        lore.add(ml("item.pet.abilities", NamedTextColor.GOLD).decorate(TextDecoration.BOLD));
        definition.lore().stream()
            .filter(line -> !line.isBlank())
            .filter(line -> !line.equalsIgnoreCase("A loyal companion that offers"))
            .filter(line -> !line.equalsIgnoreCase("unique abilities to its owner."))
            .filter(line -> !line.toLowerCase(Locale.ROOT).endsWith(" pet"))
            .filter(line -> !line.toLowerCase(Locale.ROOT).contains("click to"))
            .forEach(line -> lore.add(Component.text("- " + line, loreColor(line)).decoration(TextDecoration.ITALIC, false)));

        if (pet != null && !discovery) {
            lore.add(Component.empty());
            lore.add(ml("item.pet.progress", NamedTextColor.GOLD).decorate(TextDecoration.BOLD));
            lore.add(ml("item.pet.level", NamedTextColor.GRAY).append(Component.text(pet.level() + " / 100", NamedTextColor.AQUA).decoration(TextDecoration.ITALIC, false)));
            lore.add(ml("item.pet.exp", NamedTextColor.GRAY).append(Component.text(pet.level() >= 100 ? mt("item.pet.maxed") : pet.exp() + " / " + pet.nextLevelExp(), NamedTextColor.AQUA).decoration(TextDecoration.ITALIC, false)));
            final Component starLine = ml("item.pet.stars", NamedTextColor.GRAY)
                .append(pet.stars() >= OwnedPet.MAX_STARS
                    ? Component.text("★★★★★ " + mt("item.pet.stars-max"), NamedTextColor.WHITE).decoration(TextDecoration.ITALIC, false)
                    : Component.text("★".repeat(pet.stars()) + "☆".repeat(OwnedPet.MAX_STARS - pet.stars())
                        + "  " + pet.fusionPoints() + " / " + pet.pointsForNextStar(), NamedTextColor.GOLD).decoration(TextDecoration.ITALIC, false));
            lore.add(starLine);
            lore.add(Component.empty());
            lore.add(ml(active ? "item.pet.active" : "item.pet.summon", active ? NamedTextColor.GREEN : NamedTextColor.YELLOW));
        } else {
            lore.add(Component.empty());
            lore.add(ml("item.pet.claim", NamedTextColor.GOLD).decorate(TextDecoration.BOLD));
            if (pet != null && pet.level() > 1) {
                lore.add(ml("item.pet.starts-at", NamedTextColor.GRAY).append(Component.text(pet.level(), NamedTextColor.AQUA).decoration(TextDecoration.ITALIC, false)));
            }
            lore.add(ml("item.pet.add", NamedTextColor.YELLOW));
        }

        meta.lore(lore.stream().map(component -> component.decoration(TextDecoration.ITALIC, false)).toList());
        applyPetIdentity(meta, definition, pet == null ? 1 : pet.level(), pet == null ? null : pet.variant());
        if (pet != null) {
            if (discovery) {
                meta.getPersistentDataContainer().set(petLevelKey, PersistentDataType.INTEGER, pet.level());
                meta.getPersistentDataContainer().set(petExpKey, PersistentDataType.INTEGER, pet.exp());
                if (pet.variant() != null) {
                    meta.getPersistentDataContainer().set(petVariantKey, PersistentDataType.STRING, pet.variant());
                }
                if (pet.hasCustomName()) {
                    meta.getPersistentDataContainer().set(petNameKey, PersistentDataType.STRING, pet.customName());
                }
                // Preserve ascension progress and the chosen nametag style across convert/trade round-trips.
                if (pet.fusionPoints() > 0) {
                    meta.getPersistentDataContainer().set(petFusionKey, PersistentDataType.INTEGER, pet.fusionPoints());
                }
                if (pet.nametagStyle() != null) {
                    meta.getPersistentDataContainer().set(petNametagKey, PersistentDataType.STRING, pet.nametagStyle());
                }
            } else {
                meta.getPersistentDataContainer().set(petUuidKey, PersistentDataType.STRING, pet.uuid().toString());
            }
        }

        item.setItemMeta(meta);
        return item;
    }

    private ItemStack customPetItem(final PetDefinition definition, final Component title, final List<Component> lore) {
        final ItemStack item = new ItemStack(Material.PLAYER_HEAD);
        final SkullMeta meta = (SkullMeta) item.getItemMeta();
        meta.displayName(title.decoration(TextDecoration.ITALIC, false));
        meta.lore(lore.stream().map(component -> component.decoration(TextDecoration.ITALIC, false)).toList());
        applyPetIdentity(meta, definition, 1, null);
        item.setItemMeta(meta);
        return item;
    }

    private void applyPetIdentity(final SkullMeta meta, final PetDefinition definition, final int level, final String variant) {
        final String texture = definition.textureFor(level, variant);
        if (texture != null && !texture.isBlank()) {
            // Fold the texture into the profile UUID so a variant/level skin swap yields a distinct
            // profile identity; otherwise the client caches the first skin it saw for that UUID.
            final PlayerProfile profile = Bukkit.createProfile(
                UUID.nameUUIDFromBytes(("betterpets:" + definition.id() + ':' + texture).getBytes(StandardCharsets.UTF_8)),
                definition.profileName()
            );
            profile.setProperty(new ProfileProperty("textures", texture));
            meta.setPlayerProfile(profile);
        }

        final PersistentDataContainer data = meta.getPersistentDataContainer();
        data.set(petIdKey, PersistentDataType.STRING, definition.id());

        // Pet heads must never be worn: player heads auto-equip to the helmet slot on right-click
        // (1.21.2+ equippable component), which races the "claim pet" logic and can eat the item.
        // Forcing the equip slot to the hand disables both the right-click auto-equip and manual
        // placement into the helmet slot.
        final EquippableComponent equippable = meta.getEquippable();
        equippable.setSlot(EquipmentSlot.HAND);
        meta.setEquippable(equippable);
    }

    private Component title(final PetDefinition definition, final OwnedPet pet) {
        final String name = pet != null && pet.hasCustomName() ? pet.customName() : definition.name();
        final Cosmetics.NametagStyle style = pet == null ? null : Cosmetics.nametagStyle(pet.nametagStyle());
        final Component namePart = style == null
            ? Component.text(name, definition.rarityColor()).decoration(TextDecoration.ITALIC, false)
            : style.rainbow() ? Texts.rainbow(name) : Texts.gradient(name, style.from(), style.to());
        return Component.text("[Lvl " + (pet == null ? 1 : pet.level()) + "] ", definition.rarityColor())
            .decoration(TextDecoration.ITALIC, false)
            .append(namePart)
            .append(starSuffix(pet));
    }

    /** The ★ star suffix for an ascended pet: gold for ★1-4, white at max (★5), empty when unascended. */
    public Component starSuffix(final OwnedPet pet) {
        final int stars = pet == null ? 0 : pet.stars();
        if (stars <= 0) {
            return Component.empty();
        }
        final NamedTextColor color = stars >= OwnedPet.MAX_STARS ? NamedTextColor.WHITE : NamedTextColor.GOLD;
        return Component.text(" " + "★".repeat(stars), color).decoration(TextDecoration.ITALIC, false);
    }

    private static String formatPercent(final double value) {
        if (value >= 10.0) {
            return String.format(Locale.ROOT, "%.0f", value);
        }
        if (value >= 1.0) {
            return String.format(Locale.ROOT, "%.2f", value);
        }
        return String.format(Locale.ROOT, "%.3f", value);
    }

    private static String formatMinutes(final int minutes) {
        if (minutes >= 1440 && minutes % 1440 == 0) {
            return (minutes / 1440) + "d";
        }
        if (minutes >= 60 && minutes % 60 == 0) {
            return (minutes / 60) + "h";
        }
        if (minutes >= 60) {
            return (minutes / 60) + "h " + (minutes % 60) + "m";
        }
        return Math.max(1, minutes) + "m";
    }

    private static NamedTextColor loreColor(final String line) {
        final String lower = line.toLowerCase();
        if (lower.contains("legendary")) {
            return NamedTextColor.GOLD;
        }
        if (lower.contains("mythical") || lower.contains("extraordinary")) {
            return NamedTextColor.DARK_PURPLE;
        }
        if (lower.contains("epic")) {
            return NamedTextColor.LIGHT_PURPLE;
        }
        if (lower.contains("rare")) {
            return NamedTextColor.BLUE;
        }
        if (lower.contains("common")) {
            return NamedTextColor.GREEN;
        }
        if (lower.contains("click")) {
            return NamedTextColor.YELLOW;
        }
        if (lower.contains("blessing") || lower.contains("dragon") || lower.contains("aura") || lower.contains("guardian")) {
            return NamedTextColor.GOLD;
        }
        return NamedTextColor.GRAY;
    }
}
