package de.kamil.betterpets;

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

    private static void eq(final String label, final Object got, final Object want) {
        if (String.valueOf(got).equals(String.valueOf(want))) {
            passed++;
        } else {
            failed++;
            System.out.println("FAIL " + label + ": got <" + got + "> want <" + want + ">");
        }
    }
}
