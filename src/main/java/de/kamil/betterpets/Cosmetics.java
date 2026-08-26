package de.kamil.betterpets;

import net.kyori.adventure.text.format.TextColor;
import org.bukkit.Color;
import org.bukkit.Particle;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

/**
 * Static catalogs of buyable cosmetics: aura particle colours (solid, gradient or rainbow), movement
 * trails, and nametag styles (gradient or rainbow). Players unlock these in the /pets shop and pick one
 * per pet in the Customize menu.
 */
public final class Cosmetics {
    public static final String CAT_PARTICLE = "particle";
    public static final String CAT_TRAIL = "trail";
    public static final String CAT_NAMETAG = "nametag";

    /** An aura colour: SOLID (one dust colour), TRANSITION (fades from->to), or RAINBOW (cycles hues). */
    public record ParticleColor(String id, String display, Kind kind, Color from, Color to) {
        public enum Kind { SOLID, TRANSITION, RAINBOW }
    }

    public record Trail(String id, String display, Particle particle) {
    }

    /** A nametag colour: a two-colour gradient across the name, or RAINBOW (per-character hues). */
    public record NametagStyle(String id, String display, TextColor from, TextColor to, boolean rainbow) {
    }

    private static final Map<String, ParticleColor> PARTICLE_COLORS = new LinkedHashMap<>();
    private static final Map<String, Trail> TRAILS = new LinkedHashMap<>();
    private static final Map<String, NametagStyle> NAMETAG_STYLES = new LinkedHashMap<>();

    static {
        // ---- Solid aura colours ----
        solid("red", "Red", 0xFF4646);
        solid("crimson", "Crimson", 0xDC143C);
        solid("orange", "Orange", 0xFF9628);
        solid("amber", "Amber", 0xFFBF00);
        solid("yellow", "Yellow", 0xFFE146);
        solid("gold", "Gold", 0xFFD700);
        solid("lime", "Lime", 0x82F05A);
        solid("green", "Green", 0x3CBE50);
        solid("emerald", "Emerald", 0x2ECC71);
        solid("mint", "Mint", 0x98FF98);
        solid("teal", "Teal", 0x0FB5A5);
        solid("aqua", "Aqua", 0x50E1E1);
        solid("cyan", "Cyan", 0x00E5FF);
        solid("sky", "Sky Blue", 0x87CEEB);
        solid("blue", "Blue", 0x4682FF);
        solid("indigo", "Indigo", 0x4B0082);
        solid("purple", "Purple", 0xA05AEB);
        solid("violet", "Violet", 0x8A2BE2);
        solid("magenta", "Magenta", 0xEB5AD2);
        solid("pink", "Pink", 0xFF96C8);
        solid("rose", "Rose", 0xFF6A88);
        solid("brown", "Brown", 0x8B5A2B);
        solid("gray", "Gray", 0x9A9AA0);
        solid("white", "White", 0xF5F5F5);
        solid("black", "Black", 0x28282D);

        // ---- Gradient (transition) aura colours ----
        gradient("white_black", "White to Black", 0xFFFFFF, 0x101010);
        gradient("black_white", "Black to White", 0x101010, 0xFFFFFF);
        gradient("fire", "Fire", 0xFF3B00, 0xFFE100);
        gradient("ember", "Ember", 0xFF5A00, 0x2A0000);
        gradient("ocean", "Ocean", 0x00E5FF, 0x0040FF);
        gradient("toxic", "Toxic", 0x39FF14, 0x0B6B00);
        gradient("sunset", "Sunset", 0xFF7E5F, 0xFEB47B);
        gradient("galaxy", "Galaxy", 0x7F00FF, 0xE100FF);
        gradient("frost", "Frost", 0xFFFFFF, 0x66E0FF);
        gradient("void", "Void", 0x8A2BE2, 0x0A0A14);
        gradient("aurora", "Aurora", 0x39FF14, 0x00E5FF);
        gradient("candy", "Candy", 0xFF6EC7, 0x8A5CFF);
        gradient("lava", "Lava", 0xFFD700, 0xB22222);
        gradient("neon", "Neon", 0xFF00E5, 0x00E5FF);

        // ---- Rainbow aura ----
        rainbowAura("rainbow", "Rainbow");

        // ---- Trails ----
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
        tr("dragon", "Dragon Breath", Particle.DRAGON_BREATH);
        tr("lava", "Lava", Particle.LAVA);
        tr("witch", "Witch", Particle.WITCH);
        tr("glow", "Glow", Particle.GLOW);

        // ---- Nametag gradients ----
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
        ns("crimson", "Crimson", 0xFF4646, 0x8B0000);
        ns("lava", "Lava", 0xFFC300, 0xC70039);
        ns("toxic", "Toxic", 0x39FF14, 0x0B6B00);
        ns("ice", "Ice", 0xFFFFFF, 0x66E0FF);
        ns("royal", "Royal", 0x4682FF, 0x8A2BE2);
        ns("candy", "Candy", 0xFF6EC7, 0x8A5CFF);
        ns("emerald", "Emerald", 0x2ECC71, 0x0FB5A5);
        ns("bubblegum", "Bubblegum", 0xFF6EC7, 0x00E5FF);
        ns("midnight", "Midnight", 0x4682FF, 0x0A0A14);
        ns("white_black", "White to Black", 0xFFFFFF, 0x101010);
        ns("silver", "Silver", 0xFFFFFF, 0x9A9AA0);
        ns("neon", "Neon", 0xFF00E5, 0x00E5FF);
        ns("sunrise", "Sunrise", 0xFFE146, 0xFF7E00);
        ns("tropical", "Tropical", 0x82F05A, 0x00E5FF);
        // ---- Nametag solids ----
        nsSolid("red", "Red", 0xFF4646);
        nsSolid("blue", "Blue", 0x4682FF);
        nsSolid("green", "Green", 0x3CBE50);
        nsSolid("purple", "Purple", 0xA05AEB);
        nsSolid("aqua", "Aqua", 0x50E1E1);
        nsSolid("pink", "Pink", 0xFF96C8);
        nsSolid("white", "White", 0xF5F5F5);
        // ---- Nametag rainbow ----
        NAMETAG_STYLES.put("rainbow", new NametagStyle("rainbow", "Rainbow", TextColor.color(0xFF0000), TextColor.color(0x8A2BE2), true));
    }

    private Cosmetics() {
    }

    private static void solid(final String id, final String display, final int rgb) {
        PARTICLE_COLORS.put(id, new ParticleColor(id, display, ParticleColor.Kind.SOLID, Color.fromRGB(rgb & 0xFFFFFF), null));
    }

    private static void gradient(final String id, final String display, final int from, final int to) {
        PARTICLE_COLORS.put(id, new ParticleColor(id, display, ParticleColor.Kind.TRANSITION,
            Color.fromRGB(from & 0xFFFFFF), Color.fromRGB(to & 0xFFFFFF)));
    }

    private static void rainbowAura(final String id, final String display) {
        PARTICLE_COLORS.put(id, new ParticleColor(id, display, ParticleColor.Kind.RAINBOW, Color.WHITE, null));
    }

    private static void tr(final String id, final String display, final Particle particle) {
        TRAILS.put(id, new Trail(id, display, particle));
    }

    private static void ns(final String id, final String display, final int from, final int to) {
        NAMETAG_STYLES.put(id, new NametagStyle(id, display, TextColor.color(from), TextColor.color(to), false));
    }

    private static void nsSolid(final String id, final String display, final int rgb) {
        NAMETAG_STYLES.put(id, new NametagStyle(id, display, TextColor.color(rgb), TextColor.color(rgb), false));
    }

    /** An org.bukkit.Color for a hue (0..1), used by rainbow auras. */
    public static Color hueColor(final float hue) {
        return Color.fromRGB(java.awt.Color.HSBtoRGB(hue, 0.85F, 1.0F) & 0xFFFFFF);
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
