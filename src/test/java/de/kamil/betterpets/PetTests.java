package de.kamil.betterpets;

import de.kamil.betterpets.quickslots.QuickslotLogic;
import de.kamil.betterpets.quickslots.QuickslotProtocol;
import de.kamil.betterpets.quickslots.SwitchLimiter;

/**
 * Lightweight, dependency-free regression tests for the pure logic (no Bukkit server needed).
 * Run with test.ps1. Exits non-zero if any assertion fails, so it can gate a build.
 */
public final class PetTests {
    private static int passed;
    private static int failed;

    public static void main(final String[] args) {
        abilityTiers();
        alpacaSizes();
        abilityValues();
        milestones();
        expCurve();
        setExpClamp();
        maxLevel();
        versionCompare();
        textureVariants();
        variantUnlocking();
        fusionStars();
        starAbilityScaling();
        ascensionTracks();
        quickslotData();
        quickslotStepping();
        quickslotScrollDirection();
        quickslotProtocol();
        quickslotSpamGuard();

        System.out.println();
        System.out.println("Passed: " + passed + "   Failed: " + failed);
        if (failed > 0) {
            System.exit(1);
        }
    }

    private static void abilityTiers() {
        eq("tier(1)", PetAbilities.tier(1), 1);
        eq("tier(7)", PetAbilities.tier(7), 1);
        eq("tier(8)", PetAbilities.tier(8), 2);
        eq("tier(50)", PetAbilities.tier(50), 10);
        eq("tier(97)", PetAbilities.tier(97), 19);
        eq("tier(98)", PetAbilities.tier(98), 20);
        eq("tier(100)", PetAbilities.tier(100), 20);
        eq("tier(0 clamps to 1)", PetAbilities.tier(0), 1);
        eq("tier(999 clamps to 100)", PetAbilities.tier(999), 20);
    }

    private static void alpacaSizes() {
        eq("alpaca(1)", PetAbilities.alpacaStorageSize(1), 9);
        eq("alpaca(29)", PetAbilities.alpacaStorageSize(29), 9);
        eq("alpaca(30)", PetAbilities.alpacaStorageSize(30), 18);
        eq("alpaca(50)", PetAbilities.alpacaStorageSize(50), 27);
        eq("alpaca(70)", PetAbilities.alpacaStorageSize(70), 36);
        eq("alpaca(100)", PetAbilities.alpacaStorageSize(100), 54);
    }

    private static void abilityValues() {
        eq("value dog@100", PetAbilities.value("dog", 100), "100% wither chance on undead hits");
        eq("value phoenix@50", PetAbilities.value("phoenix", 50), "18h revive cooldown");
        eq("value alpaca@70", PetAbilities.value("alpaca", 70), "36 storage slots");
        eq("summary worm", PetAbilities.summary("worm"), "Mining efficiency.");
        eq("value unknown pet", PetAbilities.value("does_not_exist", 50), "Scales with its listed milestones");
        eq("summary unknown pet", PetAbilities.summary("does_not_exist"), "Pet ability.");
    }

    private static void milestones() {
        eq("phoenix@50 milestones", PetAbilities.milestones("phoenix", 50).size(), 3);
        eq("phoenix@10 milestones", PetAbilities.milestones("phoenix", 10).size(), 0);
        eq("blue_dragon@50 milestones", String.valueOf(PetAbilities.milestones("blue_dragon", 50)), "[Mount flight, Enchant trail]");
        eq("ant@50 milestones", PetAbilities.milestones("ant", 50).size(), 0);
    }

    private static void expCurve() {
        final OwnedPet pet = OwnedPet.create("dog", 1);
        eq("new pet level", pet.level(), 1);
        eq("new pet exp", pet.exp(), 0);
        eq("new pet next-exp", pet.nextLevelExp(), 26); // 2*2^2 + 4*2 + 10
        final boolean leveled = pet.addExp(26, 1.0);
        eq("leveled up", leveled, true);
        eq("level after 26 xp", pet.level(), 2);
        eq("exp reset", pet.exp(), 0);
        eq("next-exp for lvl3", pet.nextLevelExp(), 40); // 2*3^2 + 4*3 + 10
    }

    private static void setExpClamp() {
        final OwnedPet pet = OwnedPet.create("cat", 5);
        pet.recalculateNextLevelExp(1.0);
        pet.setExp(999999);
        eq("setExp clamps below next", pet.exp() < pet.nextLevelExp(), true);
        eq("setExp non-negative", pet.exp() >= 0, true);
    }

    private static void maxLevel() {
        final OwnedPet pet = OwnedPet.create("tiger", 100);
        eq("maxed level", pet.level(), 100);
        eq("maxed addExp returns false", pet.addExp(1000, 1.0), false);
        eq("maxed stays 100", pet.level(), 100);
    }

    private static void versionCompare() {
        eq("1.2.0 < 1.10.0", Updater.compareVersions("1.2.0", "1.10.0") < 0, true);
        eq("1.6.0 == 1.6.0", Updater.compareVersions("1.6.0", "1.6.0"), 0);
        eq("1.6 == 1.6.0", Updater.compareVersions("1.6", "1.6.0"), 0);
        eq("2.0.0 > 1.9.9", Updater.compareVersions("2.0.0", "1.9.9") > 0, true);
        eq("v-prefix parts ignore letters", Updater.compareVersions("1.6.0", "1.6.0-beta"), 0);
    }

    private static void textureVariants() {
        final java.util.Map<String, String> variants = java.util.Map.of("lucy", "LUCY_TEX", "wild", "WILD_TEX");
        final PetDefinition axolotl = new PetDefinition("axolotl", "Axolotl",
            net.kyori.adventure.text.format.NamedTextColor.BLUE, "Rare", 8, "BASE_TEX", null, variants, java.util.List.of(), java.util.List.of());
        eq("variant texture chosen", axolotl.textureFor(1, "wild"), "WILD_TEX");
        eq("unknown variant falls back to base", axolotl.textureFor(1, "nope"), "BASE_TEX");
        eq("no variant -> base", axolotl.textureFor(50, null), "BASE_TEX");
        eq("hasVariants true", axolotl.hasVariants(), true);
        eq("variant display caps", PetDefinition.variantDisplay("lucy"), "Lucy");
        eq("randomVariant in set", variants.containsKey(axolotl.randomVariant(new java.util.Random(1))), true);

        final PetDefinition panda = new PetDefinition("panda", "Panda",
            net.kyori.adventure.text.format.NamedTextColor.WHITE, "Epic", 5, "BASE", "MAX_TEX", java.util.Map.of(), java.util.List.of(), java.util.List.of());
        eq("panda below 100 base", panda.textureFor(99, null), "BASE");
        eq("panda at 100 max skin", panda.textureFor(100, null), "MAX_TEX");
        eq("panda no variants", panda.hasVariants(), false);
        eq("randomVariant null when none", panda.randomVariant(new java.util.Random(1)), null);
    }

    private static void variantUnlocking() {
        final OwnedPet pet = OwnedPet.create("axolotl", 1);
        eq("particles default on", pet.particlesEnabled(), true);
        pet.setParticlesEnabled(false);
        eq("particles toggled off", pet.particlesEnabled(), false);
        eq("no variant initially", pet.variant(), null);
        pet.setVariant("Wild");
        eq("variant set + lowercased", pet.variant(), "wild");

        // Unlocked variants are now tracked per-player (independent of owning the pet).
        final PlayerPetData data = new PlayerPetData();
        eq("locked before unlock", data.isVariantUnlocked("axolotl", "wild"), false);
        eq("unlock new returns true", data.unlockVariant("axolotl", "WILD"), true);
        eq("unlock again returns false", data.unlockVariant("axolotl", "wild"), false);
        eq("case-insensitive check", data.isVariantUnlocked("AXOLOTL", "Wild"), true);
        eq("other pet not unlocked", data.isVariantUnlocked("panda", "wild"), false);
        data.unlockVariant("axolotl", "gold");
        eq("axolotl unlocked count is 2", data.unlockedVariants("axolotl").size(), 2);
        eq("unknown pet has none", data.unlockedVariants("griffin").size(), 0);
    }

    private static void fusionStars() {
        final OwnedPet pet = OwnedPet.create("axolotl", 1);
        eq("no stars initially", pet.stars(), 0);
        eq("next star at 1 point", pet.pointsForNextStar(), 1);
        eq("first point -> star up", pet.addFusionPoint(), true);
        eq("now 1 star", pet.stars(), 1);
        eq("next star at 2 points", pet.pointsForNextStar(), 2);
        eq("2nd point -> star 2", pet.addFusionPoint(), true);
        eq("now 2 stars", pet.stars(), 2);
        pet.setFusionPoints(5);
        eq("5 points = 5 stars", pet.stars(), 5);
        eq("no next star at max", pet.pointsForNextStar(), -1);
        eq("addPoint at max = no star", pet.addFusionPoint(), false);
        eq("still 5 stars", pet.stars(), 5);
        eq("points capped at 5", pet.fusionPoints(), 5);
    }

    private static void starAbilityScaling() {
        // Below max level, stars still raise the ability tier and so change a scaling value.
        final String base = PetAbilities.value("reaper", 50, 0);
        final String boosted = PetAbilities.value("reaper", 50, 5);
        eq("stars change a scaling ability", base.equals(boosted), false);
        eq("bonus resets after the call", PetAbilities.value("reaper", 50), base);
        eq("tier bonus not persisted", PetAbilities.tier(100), 20);
        // Overflow guard: a maxed pet (tier 20) plus any number of stars must never exceed MAX_TIER,
        // so the ability value at max level is identical with 0 or 5 stars (no >100%/negative overflow).
        eq("max tier equals MAX_TIER", PetAbilities.tier(100), PetAbilities.MAX_TIER);
        eq("max-level + 5 stars stays capped", PetAbilities.value("reaper", 100, 5).equals(PetAbilities.value("reaper", 100, 0)), true);
        eq("mid-level + 5 stars still capped at max", PetAbilities.value("reaper", 80, 20).equals(PetAbilities.value("reaper", 80, 5)), true);
    }

    private static void ascensionTracks() {
        eq("warrior pet", Ascension.track("tiger"), Ascension.Track.WARRIOR);
        eq("guardian pet", Ascension.track("turtle"), Ascension.Track.GUARDIAN);
        eq("gatherer pet", Ascension.track("woodpecker"), Ascension.Track.GATHERER);
        eq("runner pet", Ascension.track("kangaroo"), Ascension.Track.RUNNER);
        eq("aquatic pet", Ascension.track("water_serpent"), Ascension.Track.AQUATIC);
        eq("mystic pet", Ascension.track("owl"), Ascension.Track.MYSTIC);
        eq("unknown defaults to mystic", Ascension.track("does_not_exist"), Ascension.Track.MYSTIC);
    }

    private static void quickslotData() {
        final PlayerPetData data = new PlayerPetData();
        eq("no quickslots initially", data.hasQuickslots(), false);
        eq("fresh record is empty", data.isEmpty(), true);
        data.setQuickslot(0, "Penguin");
        eq("slot stores lowercase id", data.quickslot(0), "penguin");
        eq("quickslotOf is case-insensitive", data.quickslotOf("PENGUIN"), 0);
        eq("filled slot makes the record non-empty", data.isEmpty(), false);
        // A pet lives in one slot only: parking it elsewhere moves it.
        data.setQuickslot(3, "penguin");
        eq("moved out of the old slot", data.quickslot(0), null);
        eq("moved into the new slot", data.quickslotOf("penguin"), 3);
        data.setQuickslot(1, "dragon");
        data.setQuickslot(1, "tiger");
        eq("assigning replaces the slot's pet", data.quickslot(1), "tiger");
        eq("replaced pet is in no slot", data.quickslotOf("dragon"), -1);
        data.setQuickslot(3, "  ");
        eq("blank clears the slot", data.quickslot(3), null);
        data.setQuickslot(-1, "cat");
        data.setQuickslot(PlayerPetData.MAX_QUICKSLOTS, "cat");
        eq("out-of-range writes are ignored", data.quickslotOf("cat"), -1);
        eq("out-of-range reads are null", data.quickslot(99), null);
        data.clearQuickslots();
        eq("clearQuickslots empties everything", data.hasQuickslots(), false);

        eq("scroll preference unset by default", data.quickScroll(), null);
        data.setQuickScroll(false);
        eq("a scroll choice makes the record non-empty", data.isEmpty(), false);

        // A slot names the pet type, so it finds the pet again after a convert/re-add gave it a new UUID.
        final PlayerPetData owner = new PlayerPetData();
        owner.setQuickslot(0, "dog");
        eq("slot pet not owned yet", owner.findByDefinition("dog").isPresent(), false);
        owner.pets().add(OwnedPet.create("dog", 7));
        eq("slot pet found once owned", owner.findByDefinition("DOG").map(OwnedPet::level).orElse(-1), 7);
        owner.pets().add(OwnedPet.create("dog", 30));
        eq("legacy duplicates resolve to the highest level", owner.findByDefinition("dog").map(OwnedPet::level).orElse(-1), 30);
    }

    private static void quickslotStepping() {
        final boolean[] usable = {true, false, true, false, true};
        eq("next from 0 skips the gap", QuickslotLogic.step(usable, 0, 1), 2);
        eq("next from 4 wraps to 0", QuickslotLogic.step(usable, 4, 1), 0);
        eq("prev from 0 wraps to 4", QuickslotLogic.step(usable, 0, -1), 4);
        eq("prev from 2 goes to 0", QuickslotLogic.step(usable, 2, -1), 0);
        eq("next from an unusable slot", QuickslotLogic.step(usable, 1, 1), 2);
        eq("no active slot: next starts at the first", QuickslotLogic.step(usable, -1, 1), 0);
        eq("no active slot: prev starts at the last", QuickslotLogic.step(usable, -1, -1), 4);
        eq("out-of-range current counts as none", QuickslotLogic.step(usable, 9, 1), 0);
        eq("nothing usable", QuickslotLogic.step(new boolean[]{false, false}, 0, 1), -1);
        eq("no slots at all", QuickslotLogic.step(new boolean[0], -1, 1), -1);
        eq("single usable slot returns itself", QuickslotLogic.step(new boolean[]{false, true, false}, 1, 1), 1);

        eq("parseSlot is 1-based", QuickslotLogic.parseSlot("1", 5), 0);
        eq("parseSlot upper bound", QuickslotLogic.parseSlot(" 5 ", 5), 4);
        eq("parseSlot above the slot count", QuickslotLogic.parseSlot("6", 5), -1);
        eq("parseSlot zero", QuickslotLogic.parseSlot("0", 5), -1);
        eq("parseSlot text", QuickslotLogic.parseSlot("next", 5), -1);
        eq("parseSlot null", QuickslotLogic.parseSlot(null, 5), -1);
    }

    private static void quickslotScrollDirection() {
        eq("one slot right is a forward notch", QuickslotLogic.scrollDirection(3, 4), 1);
        eq("one slot left is a backward notch", QuickslotLogic.scrollDirection(3, 2), -1);
        eq("8 -> 0 wraps forward", QuickslotLogic.scrollDirection(8, 0), 1);
        eq("0 -> 8 wraps backward", QuickslotLogic.scrollDirection(0, 8), -1);
        // Number keys must stay normal hotbar changes: only a single-notch move counts as scrolling.
        eq("a jump is not a scroll", QuickslotLogic.scrollDirection(0, 4), 0);
        eq("two slots is not a scroll", QuickslotLogic.scrollDirection(2, 4), 0);
        eq("same slot is not a scroll", QuickslotLogic.scrollDirection(5, 5), 0);
    }

    private static void quickslotProtocol() {
        eq("channel id", QuickslotProtocol.CHANNEL,
            QuickslotProtocol.CHANNEL_NAMESPACE + ":" + QuickslotProtocol.CHANNEL_PATH);
        eq("slot cap matches the data model", QuickslotProtocol.MAX_SLOTS, PlayerPetData.MAX_QUICKSLOTS);
        try {
            // Every client message survives encode -> decode unchanged (records compare by value).
            final java.util.List<QuickslotProtocol.ClientMessage> clientMessages = java.util.List.of(
                new QuickslotProtocol.Hello(7),
                new QuickslotProtocol.RequestPets(),
                new QuickslotProtocol.Assign(4, "blue_dragon"),
                new QuickslotProtocol.Assign(0, ""),
                new QuickslotProtocol.Switch(8),
                new QuickslotProtocol.Cycle(1),
                new QuickslotProtocol.Cycle(-1),
                new QuickslotProtocol.Despawn());
            for (final var message : clientMessages) {
                eq("client round-trip " + message, QuickslotProtocol.decodeClient(
                    QuickslotProtocol.encode(message)), message);
            }

            final var pet = new QuickslotProtocol.Pet("penguin", "Pingu ❄", "Penguin", "Legendary",
                0xFFAA00, 100, 5, true, "eyJ0ZXh0dXJlcyI6e319", "Treasure sense.\n+12 blocks");
            final java.util.List<QuickslotProtocol.ServerMessage> serverMessages = java.util.List.of(
                new QuickslotProtocol.State(1, true, java.util.List.of("penguin", "", "dog"), "dog", 500, true, -42, 4200),
                new QuickslotProtocol.State(1, false, java.util.List.of(), "", 0, false, 0, 0),
                new QuickslotProtocol.Pets(99, java.util.List.of(pet, pet)),
                new QuickslotProtocol.Pets(0, java.util.List.of()),
                new QuickslotProtocol.OpenScreen(),
                new QuickslotProtocol.MenuOpened());
            for (final var message : serverMessages) {
                eq("server round-trip " + message.getClass().getSimpleName(), QuickslotProtocol.decodeServer(
                    QuickslotProtocol.encode(message)), message);
            }

            // Forward compatibility: an unknown opcode is ignored, trailing bytes from a newer peer too.
            eq("unknown client opcode is ignored", QuickslotProtocol.decodeClient(new byte[]{(byte) 200}), null);
            eq("unknown server opcode is ignored", QuickslotProtocol.decodeServer(new byte[]{(byte) 200}), null);
            final byte[] switchBytes = QuickslotProtocol.encode(new QuickslotProtocol.Switch(2));
            final byte[] extended = java.util.Arrays.copyOf(switchBytes, switchBytes.length + 5);
            eq("trailing bytes are ignored", QuickslotProtocol.decodeClient(extended),
                new QuickslotProtocol.Switch(2));

            // Backward compatibility: a state from a plugin that predates the lockout field simply ends
            // four bytes earlier, and must read as "not locked" rather than fail.
            final byte[] current = QuickslotProtocol.encode(
                new QuickslotProtocol.State(1, true, java.util.List.of("penguin", ""), "penguin", 500, true, 7, 3000));
            final byte[] legacy = java.util.Arrays.copyOf(current, current.length - Integer.BYTES);
            eq("state without the lockout field still decodes", QuickslotProtocol.decodeServer(legacy),
                new QuickslotProtocol.State(1, true, java.util.List.of("penguin", ""), "penguin", 500, true, 7, 0));
        } catch (final java.io.IOException exception) {
            eq("valid messages decode without an IOException", exception.toString(), "no exception");
        }

        // Malformed input must be reported, never half-read into a message.
        boolean truncatedRejected = false;
        try {
            QuickslotProtocol.decodeClient(new byte[]{3, 1});
        } catch (final java.io.IOException expected) {
            truncatedRejected = true;
        }
        eq("truncated message is rejected", truncatedRejected, true);
        boolean emptyRejected = false;
        try {
            QuickslotProtocol.decodeServer(new byte[0]);
        } catch (final java.io.IOException expected) {
            emptyRejected = true;
        }
        eq("empty message is rejected", emptyRejected, true);
        boolean oversizedRejected = false;
        try {
            // A state claiming 200 slots: far above the cap, so it is refused rather than read.
            QuickslotProtocol.decodeServer(new byte[]{1, 0, 0, 0, 1, 1, (byte) 200});
        } catch (final java.io.IOException expected) {
            oversizedRejected = true;
        }
        eq("oversized slot count is rejected", oversizedRejected, true);
    }

    private static void quickslotSpamGuard() {
        // 500 ms between switches; more than 3 switches inside 10 s lock for 5 s.
        final SwitchLimiter limiter = new SwitchLimiter(new SwitchLimiter.Rules(500, 3, 10_000, 5_000));
        eq("first switch is allowed", limiter.check(1_000), SwitchLimiter.Verdict.ALLOWED);
        eq("check alone records nothing", limiter.check(1_000), SwitchLimiter.Verdict.ALLOWED);
        eq("1st switch does not lock", limiter.record(1_000), false);
        eq("right after a switch: cooldown", limiter.check(1_100), SwitchLimiter.Verdict.COOLDOWN);
        eq("just before the cooldown ends", limiter.check(1_499), SwitchLimiter.Verdict.COOLDOWN);
        eq("exactly at the cooldown", limiter.check(1_500), SwitchLimiter.Verdict.ALLOWED);
        eq("2nd switch does not lock", limiter.record(1_500), false);
        eq("3rd switch does not lock", limiter.record(2_000), false);
        eq("not locked yet", limiter.lockRemaining(2_000), 0L);
        eq("4th switch inside the window locks", limiter.record(2_500), true);
        eq("locked right away", limiter.check(2_500), SwitchLimiter.Verdict.LOCKED);
        eq("lock outranks the cooldown", limiter.check(2_600), SwitchLimiter.Verdict.LOCKED);
        eq("lock time left", limiter.lockRemaining(3_500), 4_000L);
        eq("still locked a millisecond before the end", limiter.check(7_499), SwitchLimiter.Verdict.LOCKED);
        eq("free again when the lock ends", limiter.check(7_500), SwitchLimiter.Verdict.ALLOWED);
        eq("no lock time left afterwards", limiter.lockRemaining(7_500), 0L);
        // The lock wipes the slate: it takes a full new burst to lock again.
        eq("after the lock: 1st", limiter.record(7_500), false);
        eq("after the lock: 2nd", limiter.record(8_000), false);
        eq("after the lock: 3rd", limiter.record(8_500), false);
        eq("after the lock: 4th locks again", limiter.record(9_000), true);

        // Switches spread out over more than the window never add up to a burst.
        final SwitchLimiter relaxed = new SwitchLimiter(new SwitchLimiter.Rules(500, 3, 10_000, 5_000));
        boolean everLocked = false;
        for (long now = 0; now <= 60_000; now += 4_000) {
            everLocked |= relaxed.record(now);
        }
        eq("a switch every 4 s never locks", everLocked, false);

        // max-switches 0 turns the burst limit off; the cooldown still applies.
        final SwitchLimiter cooldownOnly = new SwitchLimiter(new SwitchLimiter.Rules(500, 0, 10_000, 5_000));
        boolean lockedWithoutLimit = false;
        for (long now = 0; now < 20_000; now += 500) {
            lockedWithoutLimit |= cooldownOnly.record(now);
        }
        eq("no burst limit: never locks", lockedWithoutLimit, false);
        eq("no burst limit: cooldown still applies", cooldownOnly.check(19_600), SwitchLimiter.Verdict.COOLDOWN);

        // No cooldown at all: only the burst limit is left.
        final SwitchLimiter burstOnly = new SwitchLimiter(new SwitchLimiter.Rules(0, 2, 1_000, 1_000));
        burstOnly.record(0);
        eq("zero cooldown allows an immediate second switch", burstOnly.check(0), SwitchLimiter.Verdict.ALLOWED);
        burstOnly.record(0);
        eq("the third in the same instant locks", burstOnly.record(0), true);

        // Nonsense config values are clamped instead of misbehaving.
        final SwitchLimiter.Rules clamped = new SwitchLimiter.Rules(-5, -1, -1, -1);
        eq("negative cooldown clamps to 0", clamped.cooldownMillis(), 0L);
        eq("negative limit clamps to off", clamped.maxSwitches(), 0);
    }

    private static void eq(final String label, final Object got, final Object want) {
        if (String.valueOf(got).equals(String.valueOf(want))) {
            passed++;
        } else {
            failed++;
            System.out.println("FAIL " + label + ": got <" + got + "> want <" + want + ">");
        }
    }
}
