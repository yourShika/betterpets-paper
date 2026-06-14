package de.kamil.betterpets;

import net.kyori.adventure.text.format.NamedTextColor;

import java.util.List;
import java.util.Locale;

public record PetDefinition(
    String id,
    String name,
    NamedTextColor color,
    String rarity,
    int weight,
    String texture,
    List<String> lore
) {
    public NamedTextColor rarityColor() {
        return switch (rarity.toLowerCase(Locale.ROOT)) {
            case "extraordinary" -> NamedTextColor.DARK_RED;
            case "legendary" -> NamedTextColor.GOLD;
            case "epic" -> NamedTextColor.DARK_PURPLE;
            case "rare" -> NamedTextColor.BLUE;
            case "common" -> NamedTextColor.GREEN;
            default -> color;
        };
    }

    public String profileName() {
        final String sanitized = id.replaceAll("[^A-Za-z0-9_]", "_");
        if (sanitized.isBlank()) {
            return "better_pet";
        }
        return sanitized.length() <= 16 ? sanitized : sanitized.substring(0, 16);
    }
}
