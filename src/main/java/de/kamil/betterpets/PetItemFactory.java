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
    private final NamespacedKey petNameKey;
    private final NamespacedKey boosterTierKey;
    private final NamespacedKey boosterMinutesKey;

    public PetItemFactory(final JavaPlugin plugin) {
        this.petIdKey = new NamespacedKey(plugin, "pet_id");
        this.petUuidKey = new NamespacedKey(plugin, "pet_uuid");
        this.petLevelKey = new NamespacedKey(plugin, "pet_level");
        this.petNameKey = new NamespacedKey(plugin, "pet_name");
        this.boosterTierKey = new NamespacedKey(plugin, "booster_tier");
        this.boosterMinutesKey = new NamespacedKey(plugin, "booster_minutes");
    }

    public ItemStack boosterItem(final int tier, final int minutes) {
        final ItemStack item = new ItemStack(Material.EXPERIENCE_BOTTLE);
        final ItemMeta meta = item.getItemMeta();
        meta.displayName(Component.text("Pet XP Booster x" + tier, NamedTextColor.LIGHT_PURPLE)
            .decoration(TextDecoration.ITALIC, false));
        meta.lore(List.of(
            Component.text("Booster", NamedTextColor.GOLD).decorate(TextDecoration.BOLD).decoration(TextDecoration.ITALIC, false),
            Component.text("Multiplier: ", NamedTextColor.GRAY).append(Component.text("x" + tier, NamedTextColor.LIGHT_PURPLE)).decoration(TextDecoration.ITALIC, false),
            Component.text("Duration: ", NamedTextColor.GRAY).append(Component.text(formatMinutes(minutes), NamedTextColor.AQUA)).decoration(TextDecoration.ITALIC, false),
            Component.empty().decoration(TextDecoration.ITALIC, false),
            Component.text("Use", NamedTextColor.GOLD).decorate(TextDecoration.BOLD).decoration(TextDecoration.ITALIC, false),
            Component.text("Right-click to activate.", NamedTextColor.YELLOW).decoration(TextDecoration.ITALIC, false),
            Component.text("Pet XP only. Boosters do not stack.", NamedTextColor.DARK_GRAY).decoration(TextDecoration.ITALIC, false)
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
        return petItem(definition, OwnedPet.create(definition.id(), level), false, true);
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
                Component.text(definition.rarity() + " Pet", definition.rarityColor()).decorate(TextDecoration.BOLD),
                Component.empty(),
                Component.text("Current spawn weight: ", NamedTextColor.GRAY).append(Component.text(formatPercent(chance) + "%", NamedTextColor.AQUA)),
                Component.empty(),
                Component.text("Left-click: +1%", NamedTextColor.GREEN),
                Component.text("Right-click: -1%", NamedTextColor.RED),
                Component.text("Shift-click: +/-10%", NamedTextColor.YELLOW),
                Component.text("Range: 0.001% - 100%", NamedTextColor.DARK_GRAY)
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

    private ItemStack petItem(final PetDefinition definition, final OwnedPet pet, final boolean active, final boolean discovery) {
        final ItemStack item = new ItemStack(Material.PLAYER_HEAD);
        final SkullMeta meta = (SkullMeta) item.getItemMeta();
        meta.displayName(title(definition, pet));

        final List<Component> lore = new ArrayList<>();
        lore.add(Component.text("Rarity: ", NamedTextColor.GRAY).append(Component.text(definition.rarity(), definition.rarityColor())).decoration(TextDecoration.ITALIC, false));
        lore.add(Component.empty());
        lore.add(Component.text("Abilities", NamedTextColor.GOLD).decorate(TextDecoration.BOLD).decoration(TextDecoration.ITALIC, false));
        definition.lore().stream()
            .filter(line -> !line.isBlank())
            .filter(line -> !line.equalsIgnoreCase("A loyal companion that offers"))
            .filter(line -> !line.equalsIgnoreCase("unique abilities to its owner."))
            .filter(line -> !line.toLowerCase(Locale.ROOT).endsWith(" pet"))
            .filter(line -> !line.toLowerCase(Locale.ROOT).contains("click to"))
            .forEach(line -> lore.add(Component.text("- " + line, loreColor(line)).decoration(TextDecoration.ITALIC, false)));

        if (pet != null && !discovery) {
            lore.add(Component.empty());
            lore.add(Component.text("Progress", NamedTextColor.GOLD).decorate(TextDecoration.BOLD).decoration(TextDecoration.ITALIC, false));
            lore.add(Component.text("Level: ", NamedTextColor.GRAY).append(Component.text(pet.level() + " / 100", NamedTextColor.AQUA)).decoration(TextDecoration.ITALIC, false));
            lore.add(Component.text("EXP: ", NamedTextColor.GRAY).append(Component.text(pet.level() >= 100 ? "MAXED" : pet.exp() + " / " + pet.nextLevelExp(), NamedTextColor.AQUA)).decoration(TextDecoration.ITALIC, false));
            lore.add(Component.empty());
            lore.add(Component.text(active ? "Currently active" : "Click to summon", active ? NamedTextColor.GREEN : NamedTextColor.YELLOW).decoration(TextDecoration.ITALIC, false));
        } else {
            lore.add(Component.empty());
            lore.add(Component.text("Claim", NamedTextColor.GOLD).decorate(TextDecoration.BOLD).decoration(TextDecoration.ITALIC, false));
            if (pet != null && pet.level() > 1) {
                lore.add(Component.text("Starts at level ", NamedTextColor.GRAY).append(Component.text(pet.level(), NamedTextColor.AQUA)).decoration(TextDecoration.ITALIC, false));
            }
            lore.add(Component.text("Right-click to add this pet.", NamedTextColor.YELLOW).decoration(TextDecoration.ITALIC, false));
        }

        meta.lore(lore.stream().map(component -> component.decoration(TextDecoration.ITALIC, false)).toList());
        applyPetIdentity(meta, definition);
        if (pet != null) {
            if (discovery) {
                meta.getPersistentDataContainer().set(petLevelKey, PersistentDataType.INTEGER, pet.level());
                if (pet.hasCustomName()) {
                    meta.getPersistentDataContainer().set(petNameKey, PersistentDataType.STRING, pet.customName());
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
        applyPetIdentity(meta, definition);
        item.setItemMeta(meta);
        return item;
    }

    private void applyPetIdentity(final SkullMeta meta, final PetDefinition definition) {
        if (!definition.texture().isBlank()) {
            final PlayerProfile profile = Bukkit.createProfile(
                UUID.nameUUIDFromBytes(("betterpets:" + definition.id()).getBytes(StandardCharsets.UTF_8)),
                definition.profileName()
            );
            profile.setProperty(new ProfileProperty("textures", definition.texture()));
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
        return Component.text("[Lvl " + (pet == null ? 1 : pet.level()) + "] " + name, definition.rarityColor())
            .decoration(TextDecoration.ITALIC, false);
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
