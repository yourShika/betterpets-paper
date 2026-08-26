package de.kamil.betterpets;

import net.kyori.adventure.text.format.TextColor;
import org.bukkit.Color;
import org.bukkit.Particle;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

/**
 * Static catalogs of buyable cosmetics: aura particle colours, movement trails, and nametag styles.
 * Players unlock these in the /pets shop (spending tokens) and then pick one per pet in the Customize menu.
 */
public final class Cosmetics {
    public static final String CAT_PARTICLE = "particle";
    public static final String CAT_TRAIL = "trail";
    public static final String CAT_NAMETAG = "nametag";

    public record ParticleColor(String id, String display, Color color) {
    }

    public record Trail(String id, String display, Particle particle) {
    }

    public record NametagStyle(String id, String display, TextColor from, TextColor to) {
    }

    private static final Map<String, ParticleColor> PARTICLE_COLORS = new LinkedHashMap<>();
    private static final Map<String, Trail> TRAILS = new LinkedHashMap<>();
    private static final Map<String, NametagStyle> NAMETAG_STYLES = new LinkedHashMap<>();

    static {
        pc("red", "Red", 255, 70, 70);
        pc("orange", "Orange", 255, 150, 40);
        pc("yellow", "Yellow", 255, 225, 70);
        pc("lime", "Lime", 130, 240, 90);
        pc("green", "Green", 60, 190, 80);
        pc("aqua", "Aqua", 80, 225, 225);
        pc("blue", "Blue", 70, 130, 255);
        pc("purple", "Purple", 160, 90, 235);
        pc("magenta", "Magenta", 235, 90, 210);
        pc("pink", "Pink", 255, 150, 200);
        pc("white", "White", 245, 245, 245);
        pc("black", "Black", 40, 40, 45);

        tr("hearts", "Hearts", Particle.HEART);
        tr("notes", "Music Notes", Particle.NOTE);
        tr("flames", "Flames", Particle.FLAME);
        tr("snow", "Snowflakes", Particle.SNOWFLAKE);
        tr("enchant", "Enchant", Particle.ENCHANT);
        tr("soul", "Souls", Particle.SOUL);
        tr("sparkle", "Sparkle", Particle.END_ROD);
        tr("smoke", "Smoke", Particle.SMOKE);
        tr("happy", "Happy", Particle.HAPPY_VILLAGER);
        tr("portal", "Portal", Particle.PORTAL);
        tr("cherry", "Petals", Particle.FALLING_SPORE_BLOSSOM);
        tr("crit", "Crits", Particle.CRIT);

        ns("fire", "Fire", 0xFF8A00, 0xFFD54F);
        ns("ocean", "Ocean", 0x00C6FF, 0x0072FF);
        ns("forest", "Forest", 0xA8FF78, 0x2F9E4F);
        ns("sunset", "Sunset", 0xFF512F, 0xF09819);
        ns("galaxy", "Galaxy", 0x7F00FF, 0xE100FF);
        ns("gold", "Gold", 0xFFD700, 0xFFA500);
        ns("mint", "Mint", 0x76FFC0, 0x2BC0E4);
        ns("rose", "Rose", 0xFF6A88, 0xFF99AC);
        ns("shadow", "Shadow", 0x5A5A6E, 0x1E1E2A);
        ns("frost", "Frost", 0xE0FFFF, 0x7FDBFF);
    }

    private Cosmetics() {
    }

    private static void pc(final String id, final String display, final int r, final int g, final int b) {
        PARTICLE_COLORS.put(id, new ParticleColor(id, display, Color.fromRGB(r, g, b)));
    }

    private static void tr(final String id, final String display, final Particle particle) {
        TRAILS.put(id, new Trail(id, display, particle));
    }

    private static void ns(final String id, final String display, final int from, final int to) {
        NAMETAG_STYLES.put(id, new NametagStyle(id, display, TextColor.color(from), TextColor.color(to)));
    }

    public static Map<String, ParticleColor> particleColors() {
        return Collections.unmodifiableMap(PARTICLE_COLORS);
    }

    public static Map<String, Trail> trails() {
        return Collections.unmodifiableMap(TRAILS);
    }

    public static Map<String, NametagStyle> nametagStyles() {
        return Collections.unmodifiableMap(NAMETAG_STYLES);
    }

    public static ParticleColor particleColor(final String id) {
        return id == null ? null : PARTICLE_COLORS.get(id.toLowerCase(Locale.ROOT));
    }

    public static Trail trail(final String id) {
        return id == null ? null : TRAILS.get(id.toLowerCase(Locale.ROOT));
    }

    public static NametagStyle nametagStyle(final String id) {
        return id == null ? null : NAMETAG_STYLES.get(id.toLowerCase(Locale.ROOT));
    }
}
