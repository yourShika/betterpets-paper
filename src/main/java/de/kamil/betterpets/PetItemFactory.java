package de.kamil.betterpets;

import com.destroystokyo.paper.profile.PlayerProfile;
import com.destroystokyo.paper.profile.ProfileProperty;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.SkullMeta;
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

    public PetItemFactory(final JavaPlugin plugin) {
        this.petIdKey = new NamespacedKey(plugin, "pet_id");
        this.petUuidKey = new NamespacedKey(plugin, "pet_uuid");
        this.petLevelKey = new NamespacedKey(plugin, "pet_level");
    }

    public ItemStack discoveryItem(final PetDefinition definition) {
        return discoveryItem(definition, 1);
    }

    public ItemStack discoveryItem(final PetDefinition definition, final int level) {
        return petItem(definition, OwnedPet.create(definition.id(), level), false, true);
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
        lore.add(Component.text(definition.rarity() + " Pet", definition.rarityColor()).decorate(TextDecoration.BOLD).decoration(TextDecoration.ITALIC, false));
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
            lore.add(Component.text("Level: ", NamedTextColor.GRAY).append(Component.text(pet.level() + " / 100", NamedTextColor.AQUA)).decoration(TextDecoration.ITALIC, false));
            lore.add(Component.text("EXP: ", NamedTextColor.GRAY).append(Component.text(pet.exp() + " / " + pet.nextLevelExp(), NamedTextColor.AQUA)).decoration(TextDecoration.ITALIC, false));
            lore.add(Component.text(active ? "Currently active" : "Click to summon", active ? NamedTextColor.GREEN : NamedTextColor.YELLOW).decoration(TextDecoration.ITALIC, false));
        } else {
            lore.add(Component.empty());
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
    }

    private Component title(final PetDefinition definition, final OwnedPet pet) {
        return Component.text("[Lvl " + (pet == null ? 1 : pet.level()) + "] " + definition.name(), definition.rarityColor())
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

    private static NamedTextColor loreColor(final String line) {
        final String lower = line.toLowerCase();
        if (lower.contains("legendary")) {
            return NamedTextColor.GOLD;
        }
        if (lower.contains("extraordinary")) {
            return NamedTextColor.DARK_RED;
        }
        if (lower.contains("epic")) {
            return NamedTextColor.DARK_PURPLE;
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
