package de.kamil.betterpets;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.IntFunction;

/**
 * Central registry for the per-pet descriptive data shown in the catalogue and detail GUIs:
 * the ability summary, the level-scaled value string, and the milestone unlocks.
 * <p>
 * Text is now language-driven. Each entry produces a {@link Msg} (a lang key plus the numeric
 * placeholders computed for the level); the plugin renders that through {@link LangManager} in the
 * active language, falling back to the bundled English templates kept here in {@link #EN}. The plain
 * {@code String}-returning methods ({@link #value}, {@link #summary}, {@link #milestones}) render the
 * English templates directly, so the dependency-free tests keep verifying every number computation.
 * <p>
 * The scaling helpers {@link #tier(int)} and {@link #alpacaStorageSize(int)} are the single source of
 * truth for pet level tiers and Alpaca storage size (both the plugin and {@link ActivePetManager}
 * delegate to them).
 */
public final class PetAbilities {

    /** A lang key plus flat [placeholder, value, placeholder, value, …] replacements for the numbers. */
    public record Msg(String key, List<String> repl) {
        static Msg of(final String key, final Object... repl) {
            final List<String> r = new ArrayList<>(repl.length);
            for (final Object o : repl) {
                r.add(String.valueOf(o));
            }
            return new Msg(key, List.copyOf(r));
        }
    }

    /** Displayed data for one pet: a summary, a level -> value message, and level -> milestone messages. */
    public record Info(Msg summary, IntFunction<Msg> value, IntFunction<List<Msg>> milestones) {
    }

    private static final IntFunction<List<Msg>> NONE = level -> List.of();
    // Bundled English templates (the ultimate fallback + the source rendered by the String methods/tests).
    private static final Map<String, String> EN = new HashMap<>();
    private static final Map<String, Info> REGISTRY = build();
    // Temporary tier bonus (ascension stars) applied while computing a catalogue value with stars.
    private static final ThreadLocal<Integer> STAR_TIER_BONUS = ThreadLocal.withInitial(() -> 0);

    private PetAbilities() {
    }

    /** The design ceiling every ability formula is written against; stars can never push scaling past this. */
    public static final int MAX_TIER = 20;

    /**
     * Ability tier for a pet level (1..20), plus any active ascension-star bonus, clamped to {@link #MAX_TIER}.
     */
    public static int tier(final int level) {
        return Math.min(MAX_TIER, baseTier(level) + STAR_TIER_BONUS.get());
    }

    private static int baseTier(final int level) {
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

    // ---- Message accessors (used by the plugin, rendered through lang) --------------------------

    public static Msg summaryMsg(final String id) {
        final Info info = REGISTRY.get(id);
        return info == null ? Msg.of("ability.unknown.summary") : info.summary();
    }

    public static Msg valueMsg(final String id, final int level) {
        final Info info = REGISTRY.get(id);
        return info == null ? Msg.of("ability.unknown.value") : info.value().apply(level);
    }

    /** The value message computed as if the pet had {@code starTierBonus} extra ability tiers. */
    public static Msg valueMsg(final String id, final int level, final int starTierBonus) {
        STAR_TIER_BONUS.set(Math.max(0, starTierBonus));
        try {
            return valueMsg(id, level);
        } finally {
            STAR_TIER_BONUS.set(0);
        }
    }

    public static List<Msg> milestoneMsgs(final String id, final int level) {
        final Info info = REGISTRY.get(id);
        return info == null ? List.of() : info.milestones().apply(level);
    }

    /** The bundled English template for a key (fallback when a language file has no translation). */
    public static String englishTemplate(final String key) {
        return EN.getOrDefault(key, key);
    }

    // ---- String accessors (English render; used by the dependency-free tests + as a hard fallback) ----

    public static String summary(final String id) {
        return renderEn(summaryMsg(id));
    }

    public static String value(final String id, final int level) {
        return renderEn(valueMsg(id, level));
    }

    public static String value(final String id, final int level, final int starTierBonus) {
        return renderEn(valueMsg(id, level, starTierBonus));
    }

    public static List<String> milestones(final String id, final int level) {
        final List<String> out = new ArrayList<>();
        for (final Msg msg : milestoneMsgs(id, level)) {
            out.add(renderEn(msg));
        }
        return out;
    }

    private static String renderEn(final Msg msg) {
        String template = EN.getOrDefault(msg.key(), msg.key());
        final List<String> r = msg.repl();
        for (int i = 0; i + 1 < r.size(); i += 2) {
            template = template.replace(r.get(i), r.get(i + 1));
        }
        return template;
    }

    private static String dec(final double value) {
        return String.format(Locale.ROOT, "%.3f", value);
    }

    // ---- Registration helpers ------------------------------------------------------------------

    /** Simple pet: one summary key + one value key (numbers via the lambda), no milestones. */
    private static void p(final Map<String, Info> m, final String id, final String summaryEn, final String valueEn,
                          final IntFunction<Msg> value) {
        EN.put("ability." + id + ".summary", summaryEn);
        EN.put("ability." + id + ".value", valueEn);
        m.put(id, new Info(Msg.of("ability." + id + ".summary"), value, NONE));
    }

    /** Pet with milestones (value/milestone lang keys are registered by the caller for conditional prose). */
    private static void p(final Map<String, Info> m, final String id, final String summaryEn, final String valueEn,
                          final IntFunction<Msg> value, final IntFunction<List<Msg>> milestones) {
        EN.put("ability." + id + ".summary", summaryEn);
        if (valueEn != null) {
            EN.put("ability." + id + ".value", valueEn);
        }
        m.put(id, new Info(Msg.of("ability." + id + ".summary"), value, milestones));
    }

    /** Describes the richest ore veins a Ferret reveals (mirrors its unlock tiers) as a value message. */
    private static Msg ferretOre(final int level) {
        final int idx = Math.min(5, 1 + (level / 20)) - 1;
        return Msg.of("ability.ferret.value-" + idx);
    }

    private static Map<String, Info> build() {
        final Map<String, Info> m = new HashMap<>();

        // Shared milestone lines (reused by several pets).
        EN.put("ability.ms.mount-flight", "Mount flight");
        EN.put("ability.ms.enchant-trail", "Enchant trail");
        EN.put("ability.ms.dragon-breath-trail", "Dragon Breath trail");
        EN.put("ability.ms.flame-trail", "Flame trail");
        EN.put("ability.ms.flame-flight-trail", "Flame flight trail");
        EN.put("ability.ms.shadow-flight-trail", "Shadow flight trail");
        EN.put("ability.ms.rainbow-trail", "Rainbow trail");
        // Fallback for an unknown pet id.
        EN.put("ability.unknown.summary", "Pet ability.");
        EN.put("ability.unknown.value", "Scales with its listed milestones");

        p(m, "ant", "Makes you smaller.", "%n%% scale",
            lvl -> Msg.of("ability.ant.value", "%n%", Math.round((1.0 - (tier(lvl) * 0.025)) * 100)));
        p(m, "alpaca", "Portable storage that grows with level.", "%n% storage slots",
            lvl -> Msg.of("ability.alpaca.value", "%n%", alpacaStorageSize(lvl)),
            lvl -> switch (lvl) {
                case 1 -> List.of(Msg.of("ability.alpaca.value", "%n%", 9));
                case 30 -> List.of(Msg.of("ability.alpaca.value", "%n%", 18));
                case 50 -> List.of(Msg.of("ability.alpaca.value", "%n%", 27));
                case 70 -> List.of(Msg.of("ability.alpaca.value", "%n%", 36));
                case 100 -> List.of(Msg.of("ability.alpaca.value", "%n%", 54));
                default -> List.of();
            });
        p(m, "axolotl", "Increases oxygen bonus underwater.", "+%n% oxygen",
            lvl -> Msg.of("ability.axolotl.value", "%n%", dec(tier(lvl) * 0.5)));
        p(m, "bat", "Reveals nearby hostile mobs while underground.", "%n% block underground reveal",
            lvl -> Msg.of("ability.bat.value", "%n%", Math.min(24, 8 + tier(lvl))));
        p(m, "beaver", "Faster chopping while holding an axe.", "+%n% axe mining efficiency",
            lvl -> Msg.of("ability.beaver.value", "%n%", dec(tier(lvl) * 0.35)));
        EN.put("ability.bee.value-hi", "Regen II near flowers/crops");
        EN.put("ability.bee.value-lo", "Regen I near flowers/crops");
        EN.put("ability.bee.ms", "Flower regeneration improves to level II");
        p(m, "bee", "Regeneration near flowers or crops.", null,
            lvl -> Msg.of(lvl >= 80 ? "ability.bee.value-hi" : "ability.bee.value-lo"),
            lvl -> lvl == 80 ? List.of(Msg.of("ability.bee.ms")) : List.of());
        p(m, "blue_dragon", "More End damage, absorption shield, rideable at level 50.", "+%n% damage in dimension",
            lvl -> Msg.of("ability.blue_dragon.value", "%n%", dec(tier(lvl) * 0.3)),
            lvl -> lvl == 50 ? List.of(Msg.of("ability.ms.mount-flight"), Msg.of("ability.ms.enchant-trail")) : List.of());
        EN.put("ability.capybara.value-hi", "Regen I near water, Regen II in rain");
        EN.put("ability.capybara.value-lo", "Regen I near water");
        EN.put("ability.capybara.ms", "Rain regeneration improves to level II");
        p(m, "capybara", "Regeneration near water, stronger during rain.", null,
            lvl -> Msg.of(lvl >= 80 ? "ability.capybara.value-hi" : "ability.capybara.value-lo"),
            lvl -> lvl == 80 ? List.of(Msg.of("ability.capybara.ms")) : List.of());
        p(m, "cat", "Reduces fall damage.", "%n%% fall damage",
            lvl -> Msg.of("ability.cat.value", "%n%", Math.round((1.0 - (tier(lvl) * 0.05)) * 100)));
        p(m, "chicken", "Grants slow falling pulses and lays eggs over time.", "Slow Falling; ~%n%% egg chance per cycle",
            lvl -> Msg.of("ability.chicken.value", "%n%", Math.round(Math.min(0.45, 0.08 + (tier(lvl) * 0.018)) * 100)));
        p(m, "crab", "More damage and armor near water.", "+%a% damage, +%b% armor near water",
            lvl -> Msg.of("ability.crab.value", "%a%", dec(2.0 + (tier(lvl) * 0.2)), "%b%", dec(tier(lvl) * 0.3)));
        p(m, "crystal_golem", "Chance for extra ores and crystals when mining.", "%n%% extra ore/crystal drop",
            lvl -> Msg.of("ability.crystal_golem.value", "%n%", Math.round(Math.min(0.6, 0.15 + (tier(lvl) * 0.02)) * 100)));
        p(m, "dog", "Chance to wither undead you hit.", "%n%% wither chance on undead hits",
            lvl -> Msg.of("ability.dog.value", "%n%", Math.min(100, lvl)));
        p(m, "dolphin", "Dolphin's Grace and faster water movement.", "+%n%% water movement",
            lvl -> Msg.of("ability.dolphin.value", "%n%", Math.round(tier(lvl) * 5.0)));
        p(m, "duck", "Faster swimming and brief slow falling when airborne.", "+%n%% water movement, short glide",
            lvl -> Msg.of("ability.duck.value", "%n%", Math.round(tier(lvl) * 3.5)));
        p(m, "elder_guardian", "Faster underwater mining.", "%n%% underwater mining",
            lvl -> Msg.of("ability.elder_guardian.value", "%n%", Math.round((0.2 + tier(lvl) * 0.04) * 100)));
        p(m, "ender_dragon", "More damage in every dimension, absorption shield, rideable at level 50.",
            "+%n% damage in all dimensions",
            lvl -> Msg.of("ability.ender_dragon.value", "%n%", dec(tier(lvl) * 0.35)),
            lvl -> lvl == 50 ? List.of(Msg.of("ability.ms.mount-flight"), Msg.of("ability.ms.dragon-breath-trail")) : List.of());
        p(m, "ghast", "Explosion knockback resistance and Nether kill XP.", "%n%% explosion resistance",
            lvl -> Msg.of("ability.ghast.value", "%n%", Math.round(tier(lvl) * 5.0)));
        p(m, "goblin", "Cheaper villager trades and a chance to pickpocket the sold item or some emeralds "
                + "(full refund only at level 100).", "Hero %a%, up to %b%% free-trade chance",
            lvl -> Msg.of("ability.goblin.value", "%a%", Math.min(4, 1 + (tier(lvl) / 5)),
                "%b%", Math.round(Math.min(0.20, 0.03 + (tier(lvl) * 0.009)) * 100)));
        p(m, "lich", "Steals life when you kill mobs.", "%n% health stolen per kill",
            lvl -> Msg.of("ability.lich.value", "%n%", dec(2.0 + (tier(lvl) * 0.2))));
        p(m, "moon_fox", "Speed and Strength at night.", "+%n% speed & Strength at night",
            lvl -> Msg.of("ability.moon_fox.value", "%n%", dec(tier(lvl) * 0.003)));
        p(m, "otter", "Water breathing and faster swimming.", "+%n%% water movement, water breathing",
            lvl -> Msg.of("ability.otter.value", "%n%", Math.round(tier(lvl) * 5.0)));
        p(m, "pixie", "Grants random small buffs over time.", "%n% random buff%s%%rank%",
            lvl -> Msg.of("ability.pixie.value",
                "%n%", (lvl >= 80 ? 3 : lvl >= 40 ? 2 : 1),
                "%s%", (lvl >= 40 ? "s" : ""),
                "%rank%", (lvl >= 90 ? " III" : lvl >= 50 ? " II" : "")));
        EN.put("ability.shadow_dragon.ms-6", "Aura radius grows to 6 blocks");
        EN.put("ability.shadow_dragon.ms-8", "Aura radius grows to 8 blocks");
        p(m, "shadow_dragon", "AoE burst when you attack (boss-bar cooldown), rideable at level 50.",
            "%a% AoE damage on hit, %b%s cooldown, %c% block radius",
            lvl -> Msg.of("ability.shadow_dragon.value",
                "%a%", dec(1.5 + (tier(lvl) * 0.3)),
                "%b%", (Math.max(4000L, 12000L - (lvl * 80L)) / 1000L),
                "%c%", (lvl >= 100 ? 8 : lvl >= 50 ? 6 : 4)),
            lvl -> lvl == 50 ? List.of(Msg.of("ability.ms.mount-flight"), Msg.of("ability.ms.shadow-flight-trail"), Msg.of("ability.shadow_dragon.ms-6"))
                : lvl == 100 ? List.of(Msg.of("ability.shadow_dragon.ms-8")) : List.of());
        EN.put("ability.ancient_elf.value-100", "Nullifies all debuffs");
        EN.put("ability.ancient_elf.value-50", "Debuffs capped to 2s");
        EN.put("ability.ancient_elf.value-lo", "Debuffs capped to 10s");
        EN.put("ability.ancient_elf.ms-50", "Debuffs are capped to 2 seconds");
        EN.put("ability.ancient_elf.ms-100", "All debuffs are nullified");
        p(m, "ancient_elf", "Shortens debuffs, then blocks and finally nullifies them.", null,
            lvl -> Msg.of(lvl >= 100 ? "ability.ancient_elf.value-100" : lvl >= 50 ? "ability.ancient_elf.value-50" : "ability.ancient_elf.value-lo"),
            lvl -> lvl == 50 ? List.of(Msg.of("ability.ancient_elf.ms-50"))
                : lvl == 100 ? List.of(Msg.of("ability.ancient_elf.ms-100")) : List.of());
        p(m, "hamster", "Higher step height.", "%n% step height",
            lvl -> Msg.of("ability.hamster.value", "%n%", dec(0.6 + tier(lvl) * 0.045)));
        p(m, "hedgehog", "Reflects a small amount of melee damage.", "%n% reflected damage",
            lvl -> Msg.of("ability.hedgehog.value", "%n%", dec(Math.min(3.0, 0.4 + tier(lvl) * 0.08))));
        p(m, "herobrine", "More health, longer reach, thunder aura.", "+%n% hearts/reach tier",
            lvl -> Msg.of("ability.herobrine.value", "%n%", tier(lvl)));
        p(m, "koala", "Regeneration near trees, stronger as it levels.", "%rank% near leaves/logs",
            lvl -> Msg.of("ability.koala.value", "%rank%", (lvl >= 80 ? "Regen III" : lvl >= 40 ? "Regen II" : "Regen I")));
        p(m, "mole", "Chance to break any block without spending tool durability (axe, pickaxe, shovel).",
            "%n%% no-durability chance on any block",
            lvl -> Msg.of("ability.mole.value", "%n%", Math.round(Math.min(0.6, 0.15 + (tier(lvl) * 0.025)) * 100)));
        p(m, "allay", "Pulls in nearby dropped items straight to your inventory.", "%n% block pickup radius",
            lvl -> Msg.of("ability.allay.value", "%n%", dec(Math.min(12.0, 4.0 + (tier(lvl) * 0.4)))));
        p(m, "cursed_plushie", "Distraction dummy: hostile mobs sometimes lose interest in you.", "%n%% mob distraction chance",
            lvl -> Msg.of("ability.cursed_plushie.value", "%n%", Math.round(Math.min(0.75, 0.25 + (tier(lvl) * 0.025)) * 100)));
        p(m, "owl", "Night Vision and increased luck.", "+%n% luck",
            lvl -> Msg.of("ability.owl.value", "%n%", tier(lvl) * 25));
        p(m, "firefly", "Night Vision and a hostile-mob spawn shield that grows with level.", "%n% block no-spawn radius",
            lvl -> Msg.of("ability.firefly.value", "%n%", dec(Math.min(20.0, 6.0 + (tier(lvl) * 0.5)))));
        EN.put("ability.ferret.value-0", "reveals up to coal");
        EN.put("ability.ferret.value-1", "reveals up to copper");
        EN.put("ability.ferret.value-2", "reveals up to iron/redstone");
        EN.put("ability.ferret.value-3", "reveals up to gold/quartz");
        EN.put("ability.ferret.value-4", "reveals up to diamond/emerald");
        p(m, "ferret", "While holding a pickaxe, reveals nearby ore through walls, unlocking richer veins as it "
                + "levels (coal to diamond).", null, PetAbilities::ferretOre);
        p(m, "kangaroo", "Double-jump: sneak in mid-air to launch forward and up.", "%n% leap power",
            lvl -> Msg.of("ability.kangaroo.value", "%n%", dec(0.7 + (tier(lvl) * 0.03))));
        p(m, "squirrel", "Forages bonus saplings, apples and sticks from leaves and logs.", "%n%% bonus forage chance",
            lvl -> Msg.of("ability.squirrel.value", "%n%", Math.round(Math.min(0.6, 0.12 + (tier(lvl) * 0.02)) * 100)));
        p(m, "water_serpent", "Master angler: faster bites, double catches and sea luck while holding a rod.",
            "%a%% double catch, +%b% luck",
            lvl -> Msg.of("ability.water_serpent.value", "%a%", Math.round(Math.min(0.5, 0.1 + (tier(lvl) * 0.02)) * 100), "%b%", tier(lvl) * 3));
        p(m, "scorpion", "Your melee hits poison and slow the target; venom scales with level.", "Poison %a% for %b% ticks",
            lvl -> Msg.of("ability.scorpion.value", "%a%", (1 + tier(lvl) / 8), "%b%", (60 + tier(lvl) * 6)));
        p(m, "mantis", "Longer attack reach and a chance for a heavy bonus strike.", "+%a% reach, %b%% bonus strike",
            lvl -> Msg.of("ability.mantis.value", "%a%", dec(tier(lvl) * 0.06), "%b%", Math.round(Math.min(0.5, 0.1 + tier(lvl) * 0.02) * 100)));
        p(m, "panther", "Attacking out of a sneak lands a backstab for bonus damage.", "+%n% backstab damage",
            lvl -> Msg.of("ability.panther.value", "%n%", dec(2.0 + tier(lvl) * 0.3)));
        p(m, "mimic", "A chance to ward off an incoming hit with Resistance and Absorption.", "%n%% ward chance",
            lvl -> Msg.of("ability.mimic.value", "%n%", Math.round(Math.min(0.5, 0.15 + tier(lvl) * 0.02) * 100)));
        p(m, "guardian_angel", "Periodic Absorption and Regeneration, plus a shield when death is near.",
            "Absorption %n%, low-health shield",
            lvl -> Msg.of("ability.guardian_angel.value", "%n%", (1 + tier(lvl) / 8)));
        p(m, "sugar_glider", "Glide while airborne: slow falling, and steer forward while sneaking.", "+%n% glide push",
            lvl -> Msg.of("ability.sugar_glider.value", "%n%", dec(0.08 + tier(lvl) * 0.006)));
        p(m, "lion", "Rideable ground mount. More attack damage and a roar that weakens and knocks back nearby foes.",
            "+%n% damage, periodic roar, right-click to ride",
            lvl -> Msg.of("ability.lion.value", "%n%", dec(1.0 + tier(lvl) * 0.2)));
        p(m, "cave_spider", "Face a wall to climb it (look down to descend); hold sneak to cling and turn. "
                + "Never takes fall damage.", "wall climb + safe fall",
            lvl -> Msg.of("ability.cave_spider.value"));
        p(m, "snow_golem", "Pelts nearby foes with snowballs and keeps you from freezing.", "%n% snowballs / burst, no freezing",
            lvl -> Msg.of("ability.snow_golem.value", "%n%", (1 + tier(lvl) / 5)));
        p(m, "arcane_fox", "Extra experience from the mobs you slay and the ores you mine.", "+%n% bonus XP",
            lvl -> Msg.of("ability.arcane_fox.value", "%n%", (2 + tier(lvl))));
        p(m, "mechanist", "Reveals nearby spawners through walls.", "%n% block spawner reveal",
            lvl -> Msg.of("ability.mechanist.value", "%n%", dec(Math.min(24.0, 8.0 + tier(lvl) * 0.5))));
        p(m, "kraken", "In water, drags nearby foes to you and crushes them.", "%a% block pull, %b% damage",
            lvl -> Msg.of("ability.kraken.value", "%a%", dec(4.0 + tier(lvl) * 0.3), "%b%", dec(1.0 + tier(lvl) * 0.2)));
        EN.put("ability.griffin.value-hi", "flight unlocked");
        EN.put("ability.griffin.value-lo", "flight at level 50");
        p(m, "griffin", "A rideable flying steed from level 50.", null,
            lvl -> Msg.of(lvl >= 50 ? "ability.griffin.value-hi" : "ability.griffin.value-lo"),
            lvl -> lvl == 50 ? List.of(Msg.of("ability.ms.mount-flight")) : List.of());
        p(m, "yeti", "Freezes attackers and slows them; thrives in snowy biomes.", "%n% tick freeze on attackers",
            lvl -> Msg.of("ability.yeti.value", "%n%", (140 + tier(lvl) * 6)));
        p(m, "raccoon", "Hitting a mob has a chance to pickpocket a bonus drop.", "%n%% pickpocket chance",
            lvl -> Msg.of("ability.raccoon.value", "%n%", Math.round(Math.min(0.4, 0.1 + tier(lvl) * 0.015) * 100)));
        p(m, "golem_mason", "Longer reach, and your held block stack auto-refills from your inventory.", "+%n% reach + auto-refill",
            lvl -> Msg.of("ability.golem_mason.value", "%n%", dec(tier(lvl) * 0.05)));
        p(m, "silk_moth", "A chance to mine glass, ice, ore and glowstone as if with Silk Touch.", "%n%% Silk Touch chance",
            lvl -> Msg.of("ability.silk_moth.value", "%n%", Math.round(Math.min(0.5, 0.12 + tier(lvl) * 0.02) * 100)));
        EN.put("ability.woodpecker.value-max", "whole tree, any size");
        EN.put("ability.woodpecker.value-lo", "up to %n% logs");
        p(m, "woodpecker", "Chopping one log fells the whole natural tree at once (not player-placed logs; uses axe durability).",
            null,
            lvl -> lvl >= 100 ? Msg.of("ability.woodpecker.value-max")
                : Msg.of("ability.woodpecker.value-lo", "%n%", Math.min(48, 4 + tier(lvl) * 2)));
        p(m, "badger", "Breaking one ore chases the whole vein (uses pickaxe durability).", "up to %n% ore per vein",
            lvl -> Msg.of("ability.badger.value", "%n%", Math.min(40, 3 + tier(lvl))));
        p(m, "salamander", "A chance for mined ores to drop already smelted into ingots.", "%n%% auto-smelt chance",
            lvl -> Msg.of("ability.salamander.value", "%n%", Math.round(Math.min(0.8, 0.25 + tier(lvl) * 0.03) * 100)));
        p(m, "magpie", "Draws in nearby XP orbs and grants a small luck bonus.", "+%n% luck, XP orb magnet",
            lvl -> Msg.of("ability.magpie.value", "%n%", tier(lvl) * 2));
        p(m, "scarecrow", "Harvested crops auto-replant, nearby crops grow faster, with bonus yield.", "%n%% bonus crop yield",
            lvl -> Msg.of("ability.scarecrow.value", "%n%", Math.round(Math.min(0.6, 0.15 + tier(lvl) * 0.02) * 100)));
        p(m, "panda", "More attack knockback, bamboo biome hero effect.", "+%n%% knockback",
            lvl -> Msg.of("ability.panda.value", "%n%", Math.round(tier(lvl) * 5.0)));
        p(m, "penguin", "Speed in cold biomes, frosted ice trail, and makes nearby unopened containers glow.",
            "+%a% cold speed, %b% block container glow",
            lvl -> Msg.of("ability.penguin.value", "%a%", dec(tier(lvl) * 0.00375), "%b%", Math.round(Math.min(24.0, 8.0 + (tier(lvl) * 0.5)))));
        EN.put("ability.phoenix.ms-18", "Revive cooldown reduced to 18h");
        EN.put("ability.phoenix.ms-12", "Revive cooldown reduced to 12h");
        p(m, "phoenix", "Fire Resistance, burns undead, revives you from death, rideable at level 50.", "%n%h revive cooldown",
            lvl -> Msg.of("ability.phoenix.value", "%n%", (lvl >= 100 ? 12 : lvl >= 50 ? 18 : 24)),
            lvl -> lvl == 50 ? List.of(Msg.of("ability.ms.mount-flight"), Msg.of("ability.ms.flame-flight-trail"), Msg.of("ability.phoenix.ms-18"))
                : lvl == 100 ? List.of(Msg.of("ability.phoenix.ms-12")) : List.of());
        p(m, "platypus", "Poisons melee and ranged attackers while you are wet (water or rain).",
            "%n% tick poison on attackers while wet",
            lvl -> Msg.of("ability.platypus.value", "%n%", (80 + tier(lvl) * 6)));
        p(m, "polar_bear", "Extra armor in cold biomes.", "+%n% armor in cold biomes",
            lvl -> Msg.of("ability.polar_bear.value", "%n%", dec(tier(lvl) * 0.25)));
        p(m, "pufferfish", "Wither aura against undead that grows with level.", "%a% block Wither %b% aura",
            lvl -> Msg.of("ability.pufferfish.value", "%a%", dec(5.0 + (tier(lvl) * 0.25)), "%b%", (1 + (tier(lvl) / 8))));
        p(m, "rabbit", "Higher jumps and safer falls.", "%n% jump",
            lvl -> Msg.of("ability.rabbit.value", "%n%", dec(Math.min(1.01, 0.4 + tier(lvl) * 0.03158))));
        p(m, "reaper", "Attack speed, undead aura and harvest buffs.", "%n% attack speed",
            lvl -> Msg.of("ability.reaper.value", "%n%", dec(4.0 + tier(lvl) * 0.2)));
        p(m, "red_dragon", "More Nether damage, absorption shield, rideable at level 50.", "+%n% damage in dimension",
            lvl -> Msg.of("ability.red_dragon.value", "%n%", dec(tier(lvl) * 0.3)),
            lvl -> lvl == 50 ? List.of(Msg.of("ability.ms.mount-flight"), Msg.of("ability.ms.flame-trail")) : List.of());
        p(m, "red_parrot", "Reveals nearby hostile mobs.", "%n% block reveal",
            lvl -> Msg.of("ability.red_parrot.value", "%n%", Math.min(30, 10 + (lvl / 10) * 2)));
        p(m, "red_panda", "Forest movement and faster sneaking.", "+%a% forest speed, +%b% sneak",
            lvl -> Msg.of("ability.red_panda.value", "%a%", dec(tier(lvl) * 0.002), "%b%", dec(tier(lvl) * 0.02)));
        p(m, "snail", "Faster sneaking.", "%n% sneak speed",
            lvl -> Msg.of("ability.snail.value", "%n%", dec(0.3 + tier(lvl) * 0.035)));
        p(m, "spinosaurus", "Makes you larger.", "%n%% scale",
            lvl -> Msg.of("ability.spinosaurus.value", "%n%", Math.round((1.0 + tier(lvl) * 0.025) * 100)));
        p(m, "slime", "Bouncy movement with safer landings.", "%a% safe fall, %b% jump",
            lvl -> Msg.of("ability.slime.value", "%a%", dec(5.0 + tier(lvl)), "%b%", dec(Math.min(0.85, 0.4 + tier(lvl) * 0.018))));
        p(m, "tiger", "Movement speed and sweeping damage.", "%n% speed",
            lvl -> Msg.of("ability.tiger.value", "%n%", dec(0.1 + Math.min(100, lvl) * 0.001)));
        p(m, "turtle", "Knockback resistance.", "+%n% knockback resistance",
            lvl -> Msg.of("ability.turtle.value", "%n%", tier(lvl) * 5));
        p(m, "unicorn", "Luck, light healing, and rainbow magic.", "+%n% luck",
            lvl -> Msg.of("ability.unicorn.value", "%n%", tier(lvl) * 8),
            lvl -> lvl == 50 ? List.of(Msg.of("ability.ms.rainbow-trail")) : List.of());
        p(m, "warden", "Reveals undead and wards off wardens.", "%n% block reveal",
            lvl -> Msg.of("ability.warden.value", "%n%", Math.min(50, Math.max(10, lvl))));
        p(m, "worm", "Mining efficiency.", "+%n% mining efficiency",
            lvl -> Msg.of("ability.worm.value", "%n%", dec(tier(lvl) * 0.5)));
        return m;
    }
}
