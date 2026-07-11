package de.kamil.betterpets;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.TextComponent;
import net.kyori.adventure.text.format.TextColor;
import net.kyori.adventure.text.format.TextDecoration;

/**
 * Small text-styling helpers: per-character colour gradients for GUI titles and the broadcast prefix.
 * Kept out of the plugin class so the visual styling lives in one place.
 */
public final class Texts {
    // Warm gold gradient used for the "Better Pets" menu titles and broadcast prefix.
    private static final TextColor TITLE_FROM = TextColor.color(0xFFE082);
    private static final TextColor TITLE_TO = TextColor.color(0xFFA000);

    private Texts() {
    }

    /** A left-to-right colour gradient across the characters of {@code text}, non-italic. */
    public static Component gradient(final String text, final TextColor from, final TextColor to) {
        if (text == null || text.isEmpty()) {
            return Component.empty();
        }
        final int length = text.length();
        final TextComponent.Builder builder = Component.text();
        for (int i = 0; i < length; i++) {
            final float ratio = length == 1 ? 0.0F : (float) i / (length - 1);
            builder.append(Component.text(text.charAt(i)).color(TextColor.lerp(ratio, from, to)));
        }
        return builder.build().decoration(TextDecoration.ITALIC, false);
    }

    /** A bold gold-gradient GUI/menu title. */
    public static Component menuTitle(final String text) {
        return gradient(text, TITLE_FROM, TITLE_TO).decorate(TextDecoration.BOLD);
    }

    /** A bold gold-gradient title between two custom colours (e.g. rarity themed). */
    public static Component title(final String text, final TextColor from, final TextColor to) {
        return gradient(text, from, to).decorate(TextDecoration.BOLD);
    }

    /** A bold title fading from a pet's rarity colour into white. */
    public static Component rarityTitle(final String text, final TextColor rarity) {
        return gradient(text, rarity, TextColor.color(0xFFFFFF)).decorate(TextDecoration.BOLD);
    }

    /** The "[BetterPets]" chat prefix as a bold gold gradient, with a trailing space. */
    public static Component prefix() {
        return gradient("[BetterPets]", TITLE_FROM, TITLE_TO)
            .decorate(TextDecoration.BOLD)
            .append(Component.text(" ").decoration(TextDecoration.BOLD, false));
    }
}
