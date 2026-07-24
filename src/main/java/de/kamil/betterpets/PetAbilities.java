package de.kamil.betterpets;

import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.IntFunction;

/**
 * Central registry for the per-pet descriptive data shown in the catalogue and detail GUIs:
 * the ability summary, the level-scaled value string, and the milestone unlocks. Previously these
 * lived in three separate {@code switch} statements in {@link BetterPetsPlugin}; consolidating them
 * here means a new pet's displayed data is defined in exactly one place.
 * <p>
 * The scaling helpers {@link #tier(int)} and {@link #alpacaStorageSize(int)} are the single source of
 * truth for pet level tiers and Alpaca storage size (both the plugin and {@link ActivePetManager}
 * delegate to them). All formulas are byte-for-byte the same as the previous inline versions.
 */
public final class PetAbilities {

    /** Displayed data for one pet: a summary line, a level -> value string, and level -> milestone list. */
    public record Info(String summary, IntFunction<String> value, IntFunction<List<String>> milestones) {
    }

    private static final IntFunction<List<String>> NONE = level -> List.of();
    private static final Map<String, Info> REGISTRY = build();

    private PetAbilities() {
    }

    /** Ability tier (1..20) for a pet level. Single source of truth for all pet scaling. */
    public static int tier(final int level) {
        final int capped = Math.max(1, Math.min(100, level));
        if (capped <= 7) {
            return 1;
        }
        if (capped >= 98) {
            return 20;
        }
        return Math.min(20, 2 + ((capped - 8) / 5));
    }

    /** Alpaca storage slot count for a pet level. */
    public static int alpacaStorageSize(final int level) {
        if (level >= 100) {
            return 54;
        }
        if (level >= 70) {
            return 36;
        }
        if (level >= 50) {
            return 27;
        }
        if (level >= 30) {
            return 18;
        }
        return 9;
    }

    public static String summary(final String id) {
        final Info info = REGISTRY.get(id);
        return info == null ? "Pet ability." : info.summary();
    }

    public static String value(final String id, final int level) {
        final Info info = REGISTRY.get(id);
        return info == null ? "Scales with its listed milestones" : info.value().apply(level);
    }

    public static List<String> milestones(final String id, final int level) {
        final Info info = REGISTRY.get(id);
        return info == null ? List.of() : info.milestones().apply(level);
    }

    private static String dec(final double value) {
        return String.format(Locale.ROOT, "%.3f", value);
    }

    /** Describes the richest ore veins a Ferret reveals at the given level (mirrors its unlock tiers). */
    private static String ferretOreLabel(final int level) {
        final String[] names = {"coal", "copper", "iron/redstone", "gold/quartz", "diamond/emerald"};
        final int idx = Math.min(names.length, 1 + (level / 20)) - 1;
        return "reveals up to " + names[idx];
    }

    private static void put(final Map<String, Info> map, final String id, final String summary,
                            final IntFunction<String> value, final IntFunction<List<String>> milestones) {
        map.put(id, new Info(summary, value, milestones));
    }

    private static Map<String, Info> build() {
        final Map<String, Info> m = new HashMap<>();
        put(m, "ant", "Makes you smaller.",
            lvl -> Math.round((1.0 - (tier(lvl) * 0.025)) * 100) + "% scale", NONE);
        put(m, "alpaca", "Portable storage that grows with level.",
            lvl -> alpacaStorageSize(lvl) + " storage slots",
            lvl -> switch (lvl) {
                case 1 -> List.of("9 storage slots");
                case 30 -> List.of("18 storage slots");
                case 50 -> List.of("27 storage slots");
                case 70 -> List.of("36 storage slots");
                case 100 -> List.of("54 storage slots");
                default -> List.of();
            });
        put(m, "axolotl", "Increases oxygen bonus underwater.",
            lvl -> "+" + dec(tier(lvl) * 0.5) + " oxygen", NONE);
        put(m, "bat", "Reveals nearby hostile mobs while underground.",
            lvl -> Math.min(24, 8 + tier(lvl)) + " block underground reveal", NONE);
        put(m, "beaver", "Faster chopping while holding an axe.",
            lvl -> "+" + dec(tier(lvl) * 0.35) + " axe mining efficiency", NONE);
        put(m, "bee", "Regeneration near flowers or crops.",
            lvl -> lvl >= 80 ? "Regen II near flowers/crops" : "Regen I near flowers/crops",
            lvl -> lvl == 80 ? List.of("Flower regeneration improves to level II") : List.of());
        put(m, "blue_dragon", "More End damage, absorption shield, rideable at level 50.",
            lvl -> "+" + dec(tier(lvl) * 0.3) + " damage in dimension",
            lvl -> lvl == 50 ? List.of("Mount flight", "Enchant trail") : List.of());
        put(m, "capybara", "Regeneration near water, stronger during rain.",
            lvl -> lvl >= 80 ? "Regen I near water, Regen II in rain" : "Regen I near water",
            lvl -> lvl == 80 ? List.of("Rain regeneration improves to level II") : List.of());
        put(m, "cat", "Reduces fall damage.",
            lvl -> Math.round((1.0 - (tier(lvl) * 0.05)) * 100) + "% fall damage", NONE);
        put(m, "chicken", "Grants slow falling pulses and lays eggs over time.",
            lvl -> "Slow Falling; ~" + Math.round(Math.min(0.45, 0.08 + (tier(lvl) * 0.018)) * 100) + "% egg chance per cycle", NONE);
        put(m, "crab", "More damage and armor near water.",
            lvl -> "+" + dec(2.0 + (tier(lvl) * 0.2)) + " damage, +" + dec(tier(lvl) * 0.3) + " armor near water", NONE);
        put(m, "crystal_golem", "Chance for extra ores and crystals when mining.",
            lvl -> Math.round(Math.min(0.6, 0.15 + (tier(lvl) * 0.02)) * 100) + "% extra ore/crystal drop", NONE);
        put(m, "dog", "Chance to wither undead you hit.",
            lvl -> Math.min(100, lvl) + "% wither chance on undead hits", NONE);
        put(m, "dolphin", "Dolphin's Grace and faster water movement.",
            lvl -> "+" + Math.round(tier(lvl) * 5.0) + "% water movement", NONE);
        put(m, "duck", "Faster swimming and brief slow falling when airborne.",
            lvl -> "+" + Math.round(tier(lvl) * 3.5) + "% water movement, short glide", NONE);
        put(m, "elder_guardian", "Faster underwater mining.",
            lvl -> Math.round((0.2 + tier(lvl) * 0.04) * 100) + "% underwater mining", NONE);
        put(m, "ender_dragon", "More damage in every dimension, absorption shield, rideable at level 50.",
            lvl -> "+" + dec(tier(lvl) * 0.35) + " damage in all dimensions",
            lvl -> lvl == 50 ? List.of("Mount flight", "Dragon Breath trail") : List.of());
        put(m, "ghast", "Explosion knockback resistance and Nether kill XP.",
            lvl -> Math.round(tier(lvl) * 5.0) + "% explosion resistance", NONE);
        put(m, "goblin", "Cheaper villager trades and a small chance to buy from villagers for free.",
            lvl -> "Hero " + Math.min(4, 1 + (tier(lvl) / 5)) + ", up to " + Math.round(Math.min(0.20, 0.03 + (tier(lvl) * 0.009)) * 100) + "% free-trade chance", NONE);
        put(m, "lich", "Steals life when you kill mobs.",
            lvl -> dec(2.0 + (tier(lvl) * 0.2)) + " health stolen per kill", NONE);
        put(m, "moon_fox", "Speed and Strength at night.",
            lvl -> "+" + dec(tier(lvl) * 0.003) + " speed & Strength at night", NONE);
        put(m, "otter", "Water breathing and faster swimming.",
            lvl -> "+" + Math.round(tier(lvl) * 5.0) + "% water movement, water breathing", NONE);
        put(m, "pixie", "Grants random small buffs over time.",
            lvl -> (lvl >= 80 ? 3 : lvl >= 40 ? 2 : 1) + " random buff" + (lvl >= 40 ? "s" : "") + (lvl >= 90 ? " III" : lvl >= 50 ? " II" : ""), NONE);
        put(m, "shadow_dragon", "AoE burst when you attack (boss-bar cooldown), rideable at level 50.",
            lvl -> dec(1.5 + (tier(lvl) * 0.3)) + " AoE damage on hit, " + (Math.max(4000L, 12000L - (lvl * 80L)) / 1000L) + "s cooldown, " + (lvl >= 100 ? 8 : lvl >= 50 ? 6 : 4) + " block radius",
            lvl -> lvl == 50 ? List.of("Mount flight", "Shadow flight trail", "Aura radius grows to 6 blocks")
                : lvl == 100 ? List.of("Aura radius grows to 8 blocks") : List.of());
        put(m, "ancient_elf", "Shortens debuffs, then blocks and finally nullifies them.",
            lvl -> lvl >= 100 ? "Nullifies all debuffs" : lvl >= 50 ? "Debuffs capped to 2s" : "Debuff duration cut by 40%",
            lvl -> lvl == 50 ? List.of("Debuffs are capped to 2 seconds")
                : lvl == 100 ? List.of("All debuffs are nullified") : List.of());
        put(m, "hamster", "Higher step height.",
            lvl -> dec(0.6 + tier(lvl) * 0.045) + " step height", NONE);
        put(m, "hedgehog", "Reflects a small amount of melee damage.",
            lvl -> dec(Math.min(3.0, 0.4 + tier(lvl) * 0.08)) + " reflected damage", NONE);
        put(m, "herobrine", "More health, longer reach, thunder aura.",
            lvl -> "+" + tier(lvl) + " hearts/reach tier", NONE);
        put(m, "koala", "Regeneration near trees, stronger as it levels.",
            lvl -> (lvl >= 80 ? "Regen III" : lvl >= 40 ? "Regen II" : "Regen I") + " near leaves/logs", NONE);
        put(m, "mole", "Chance to break any block without spending tool durability (axe, pickaxe, shovel).",
            lvl -> Math.round(Math.min(0.6, 0.15 + (tier(lvl) * 0.025)) * 100) + "% no-durability chance on any block", NONE);
        put(m, "allay", "Pulls in nearby dropped items straight to your inventory.",
            lvl -> dec(Math.min(12.0, 4.0 + (tier(lvl) * 0.4))) + " block pickup radius", NONE);
        put(m, "cursed_plushie", "Distraction dummy: hostile mobs sometimes lose interest in you.",
            lvl -> Math.round(Math.min(0.75, 0.25 + (tier(lvl) * 0.025)) * 100) + "% mob distraction chance", NONE);
        put(m, "owl", "Night Vision and increased luck.",
            lvl -> "+" + (tier(lvl) * 25) + " luck", NONE);
        put(m, "firefly", "Night Vision and a hostile-mob spawn shield that grows with level.",
            lvl -> dec(Math.min(20.0, 6.0 + (tier(lvl) * 0.5))) + " block no-spawn radius", NONE);
        put(m, "ferret", "While holding a pickaxe, reveals nearby ore through walls, unlocking richer veins as it levels (coal to diamond).",
            PetAbilities::ferretOreLabel, NONE);
        put(m, "kangaroo", "Double-jump: sneak in mid-air to launch forward and up.",
            lvl -> dec(0.7 + (tier(lvl) * 0.03)) + " leap power", NONE);
        put(m, "squirrel", "Forages bonus saplings, apples and sticks from leaves and logs.",
            lvl -> Math.round(Math.min(0.6, 0.12 + (tier(lvl) * 0.02)) * 100) + "% bonus forage chance", NONE);
        put(m, "water_serpent", "Master angler: faster bites, double catches and sea luck while holding a rod.",
            lvl -> Math.round(Math.min(0.5, 0.1 + (tier(lvl) * 0.02)) * 100) + "% double catch, +" + (tier(lvl) * 3) + " luck", NONE);
        put(m, "panda", "More attack knockback, bamboo biome hero effect. Enchanted skin at level 100.",
            lvl -> "+" + Math.round(tier(lvl) * 5.0) + "% knockback",
            lvl -> lvl == 100 ? List.of("Enchanted Panda skin") : List.of());
        put(m, "penguin", "Speed in cold biomes, frosted ice trail, and makes nearby unopened containers glow.",
            lvl -> "+" + dec(tier(lvl) * 0.00375) + " cold speed, " + Math.round(Math.min(24.0, 8.0 + (tier(lvl) * 0.5))) + " block container glow", NONE);
        put(m, "phoenix", "Fire Resistance, burns undead, revives you from death, rideable at level 50.",
            lvl -> lvl >= 100 ? "12h revive cooldown" : lvl >= 50 ? "18h revive cooldown" : "24h revive cooldown",
            lvl -> lvl == 50 ? List.of("Mount flight", "Flame flight trail", "Revive cooldown reduced to 18h")
                : lvl == 100 ? List.of("Revive cooldown reduced to 12h") : List.of());
        put(m, "platypus", "Poisons melee and ranged attackers while you are wet (water or rain).",
            lvl -> (80 + tier(lvl) * 6) + " tick poison on attackers while wet", NONE);
        put(m, "polar_bear", "Extra armor in cold biomes.",
            lvl -> "+" + dec(tier(lvl) * 0.25) + " armor in cold biomes", NONE);
        put(m, "pufferfish", "Wither aura against undead that grows with level.",
            lvl -> dec(5.0 + (tier(lvl) * 0.25)) + " block Wither " + (1 + (tier(lvl) / 8)) + " aura", NONE);
        put(m, "rabbit", "Higher jumps and safer falls.",
            lvl -> dec(Math.min(1.01, 0.4 + tier(lvl) * 0.03158)) + " jump", NONE);
        put(m, "reaper", "Attack speed, undead aura and harvest buffs.",
            lvl -> dec(4.0 + tier(lvl) * 0.2) + " attack speed", NONE);
        put(m, "red_dragon", "More Nether damage, absorption shield, rideable at level 50.",
            lvl -> "+" + dec(tier(lvl) * 0.3) + " damage in dimension",
            lvl -> lvl == 50 ? List.of("Mount flight", "Flame trail") : List.of());
        put(m, "red_parrot", "Reveals nearby hostile mobs.",
            lvl -> Math.min(30, 10 + (lvl / 10) * 2) + " block reveal", NONE);
        put(m, "red_panda", "Forest movement and faster sneaking.",
            lvl -> "+" + dec(tier(lvl) * 0.002) + " forest speed, +" + dec(tier(lvl) * 0.02) + " sneak", NONE);
        put(m, "snail", "Faster sneaking.",
            lvl -> dec(0.3 + tier(lvl) * 0.035) + " sneak speed", NONE);
        put(m, "spinosaurus", "Makes you larger.",
            lvl -> Math.round((1.0 + tier(lvl) * 0.025) * 100) + "% scale", NONE);
        put(m, "slime", "Bouncy movement with safer landings.",
            lvl -> dec(5.0 + tier(lvl)) + " safe fall, " + dec(Math.min(0.85, 0.4 + tier(lvl) * 0.018)) + " jump", NONE);
        put(m, "tiger", "Movement speed and sweeping damage.",
            lvl -> dec(0.1 + Math.min(100, lvl) * 0.001) + " speed", NONE);
        put(m, "turtle", "Knockback resistance.",
            lvl -> "+" + (tier(lvl) * 5) + " knockback resistance", NONE);
        put(m, "unicorn", "Luck, light healing, and rainbow magic.",
            lvl -> "+" + (tier(lvl) * 8) + " luck",
            lvl -> lvl == 50 ? List.of("Rainbow trail") : List.of());
        put(m, "warden", "Reveals undead and wards off wardens.",
            lvl -> Math.min(50, Math.max(10, lvl)) + " block reveal", NONE);
        put(m, "worm", "Mining efficiency.",
            lvl -> "+" + dec(tier(lvl) * 0.5) + " mining efficiency", NONE);
        return m;
    }
}
