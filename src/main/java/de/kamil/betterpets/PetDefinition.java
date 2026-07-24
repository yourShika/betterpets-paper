package de.kamil.betterpets;

import net.kyori.adventure.text.format.NamedTextColor;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Random;

public record PetDefinition(
    String id,
    String name,
    NamedTextColor color,
    String rarity,
    int weight,
    String texture,
    String textureMax,
    Map<String, String> variants,
    List<String> lore
) {
    /** Resolves the head texture for a specific pet: variant skin, then level-100 skin, then base. */
    public String textureFor(final int level, final String variant) {
        if (variant != null && variants != null) {
            final String skin = variants.get(variant.toLowerCase(Locale.ROOT));
            if (skin != null && !skin.isBlank()) {
                return skin;
            }
        }
        if (level >= 100 && textureMax != null && !textureMax.isBlank()) {
            return textureMax;
        }
        return texture;
    }

    public boolean hasVariants() {
        return variants != null && !variants.isEmpty();
    }

    /** Picks a random variant key for this definition, or null when it has no variants. */
    public String randomVariant(final Random random) {
        if (!hasVariants()) {
            return null;
        }
        final List<String> keys = new ArrayList<>(variants.keySet());
        return keys.get(random.nextInt(keys.size()));
    }

    public static String variantDisplay(final String variant) {
        if (variant == null || variant.isBlank()) {
            return "";
        }
        return Character.toUpperCase(variant.charAt(0)) + variant.substring(1).toLowerCase(Locale.ROOT);
    }

    public NamedTextColor rarityColor() {
        return switch (rarity.toLowerCase(Locale.ROOT)) {
            // "extraordinary" kept as an alias so any legacy data still resolves to the Mythical colour.
            case "mythical", "extraordinary" -> NamedTextColor.DARK_PURPLE;
            case "legendary" -> NamedTextColor.GOLD;
            case "epic" -> NamedTextColor.LIGHT_PURPLE;
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
