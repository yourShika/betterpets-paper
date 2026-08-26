package de.kamil.betterpets;

import java.util.HashMap;
import java.util.Map;

/**
 * Ascension archetypes. Each pet belongs to one track, so its stars grant a thematic bonus that differs
 * from pet to pet (on top of its own ability growing stronger and the flat XP bonus). Effects are all
 * event- or potion-based (no attribute modifiers) so they never clash with a pet's own passive buffs.
 */
public final class Ascension {
    public enum Track {
        WARRIOR("Warrior", "+3% damage dealt per star"),
        GUARDIAN("Guardian", "-3% damage taken per star"),
        GATHERER("Gatherer", "+6% double-drop chance per star"),
        RUNNER("Runner", "Less fall damage; Speed from ★★★"),
        MYSTIC("Mystic", "Luck; Regeneration from ★★★★"),
        AQUATIC("Aquatic", "Water breathing from ★★★; +8% double catch per star");

        private final String display;
        private final String perk;

        Track(final String display, final String perk) {
            this.display = display;
            this.perk = perk;
        }

        public String display() {
            return display;
        }

        public String perk() {
            return perk;
        }
    }

    private static final Map<String, Track> TRACKS = new HashMap<>();

    static {
        put(Track.WARRIOR, "blue_dragon", "dog", "ender_dragon", "herobrine", "kraken", "lich", "lion",
            "mantis", "panda", "panther", "pufferfish", "reaper", "red_dragon", "scorpion", "shadow_dragon",
            "snow_golem", "spinosaurus", "tiger");
        put(Track.GUARDIAN, "ancient_elf", "cat", "crab", "cursed_plushie", "ghast", "guardian_angel",
            "hedgehog", "mimic", "phoenix", "platypus", "polar_bear", "turtle", "warden", "yeti");
        put(Track.GATHERER, "allay", "badger", "beaver", "crystal_golem", "ferret", "golem_mason", "magpie",
            "mechanist", "mole", "raccoon", "salamander", "scarecrow", "silk_moth", "squirrel", "woodpecker", "worm");
        put(Track.RUNNER, "cave_spider", "griffin", "hamster", "kangaroo", "moon_fox", "rabbit", "red_panda",
            "slime", "snail", "sugar_glider");
        put(Track.MYSTIC, "alpaca", "ant", "arcane_fox", "bat", "bee", "capybara", "chicken", "firefly",
            "goblin", "koala", "owl", "penguin", "pixie", "red_parrot", "unicorn");
        put(Track.AQUATIC, "axolotl", "dolphin", "duck", "elder_guardian", "otter", "water_serpent");
    }

    private Ascension() {
    }

    private static void put(final Track track, final String... ids) {
        for (final String id : ids) {
            TRACKS.put(id, track);
        }
    }

    /** The ascension track for a pet id (defaults to MYSTIC for anything unmapped). */
    public static Track track(final String petId) {
        return petId == null ? Track.MYSTIC : TRACKS.getOrDefault(petId, Track.MYSTIC);
    }
}
