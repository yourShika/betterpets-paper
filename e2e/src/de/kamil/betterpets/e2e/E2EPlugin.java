package de.kamil.betterpets.e2e;

import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.BrushableBlock;
import org.bukkit.block.Chest;
import org.bukkit.block.ShulkerBox;
import org.bukkit.block.data.Ageable;
import org.bukkit.block.data.Directional;
import org.bukkit.block.data.Levelled;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Entity;
import org.bukkit.entity.ExperienceOrb;
import org.bukkit.entity.FishHook;
import org.bukkit.entity.Interaction;
import org.bukkit.entity.Item;
import org.bukkit.entity.Player;
import org.bukkit.entity.WanderingTrader;
import org.bukkit.event.Event;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryAction;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.event.player.PlayerFishEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.MerchantRecipe;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.java.JavaPlugin;

import io.papermc.paper.event.player.PlayerTradeEvent;

import java.io.File;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.Callable;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

/**
 * End-to-end checks for Better Pets on a real Paper server.
 *
 * <p>Lives only on the throw-away test server (see run-e2e.sh). Once the test player has joined, it plays
 * through the situations the unit tests cannot reach - breaking blocks as that player, opening menus,
 * trading, dying in the middle of a trade - against the real, unmodified Better Pets jar, and writes what
 * it found to e2e-report.txt. Better Pets is only ever touched the way a player, the console or another
 * plugin would touch it; where its inner state has to be read or set up (stars, the token balance), that
 * goes through reflection, so nothing in Better Pets exists for the sake of this test.</p>
 */
public final class E2EPlugin extends JavaPlugin implements Listener {

    private static final String NS = "betterpets";

    private final List<String> lines = new ArrayList<>();
    private int passed;
    private int failed;
    private int skipped;

    private Plugin pets;
    private World world;
    private Location spot;
    private String firstName;

    // What a "protection plugin" and a "fishing plugin" on this server do, switched by the scenarios.
    private volatile boolean cancelBreaks;
    private volatile boolean cancelCatch;
    private volatile boolean replaceCatch;
    private boolean hosting;

    @Override
    public void onEnable() {
        pets = getServer().getPluginManager().getPlugin("BetterPets");
        firstName = System.getProperty("e2e.player", "Tester");
        getServer().getPluginManager().registerEvents(this, this);
        if (pets == null || !pets.isEnabled()) {
            getLogger().severe("Better Pets is not running - nothing to test.");
            return;
        }
        // Settings the scenarios rely on. Read live by Better Pets, so no config file has to be prepared.
        pets.getConfig().set("debug-logging", true);
        pets.getConfig().set("tokens.ore-tokens.chance-percent", 100.0);
        pets.getConfig().set("tokens.ore-tokens.broadcast", false);
        pets.getConfig().set("pet-sources.brushing.chance-percent", 0.0);
        pets.getConfig().set("pet-sources.fishing.chance-percent", 0.0);
        if (Boolean.getBoolean("e2e.host")) {
            hosting = true;
            getLogger().info("Host mode: no checks. Whoever joins gets a few pets in their quickslots; the server stays up.");
            return;
        }
        final Thread runner = new Thread(this::run, "betterpets-e2e");
        runner.setDaemon(true);
        runner.start();
    }

    // ------------------------------------------------------------------------------------------------
    // Host mode: a server to try the mod on by hand
    // ------------------------------------------------------------------------------------------------

    private static final String[] HOST_PETS = {"penguin", "unicorn", "ghast", "cat", "axolotl"};

    /**
     * With -De2e.host=true nothing is checked. Whoever joins is handed a few pets, each parked in a
     * quickslot, and the server simply stays up - for trying the Quickslots mod against the real plugin
     * with a real keyboard. The server log shows every switch ("Spawned active pet ...").
     */
    @EventHandler
    public void welcome(final PlayerJoinEvent event) {
        if (!hosting) {
            return;
        }
        final Player player = event.getPlayer();
        Bukkit.getScheduler().runTaskLater(this, () -> {
            if (!player.isOnline()) {
                return;
            }
            try {
                for (int slot = 0; slot < HOST_PETS.length; slot++) {
                    final String petId = HOST_PETS[slot];
                    if (pet(player, petId) == null) {
                        final ItemStack held = player.getInventory().getItemInMainHand().clone();
                        Bukkit.dispatchCommand(Bukkit.getConsoleSender(), "pets give " + petId + " " + (10 + slot * 20) + " " + player.getName());
                        final ItemStack item = takePetItem(player, petId);
                        if (item != null) {
                            player.getInventory().setItemInMainHand(item);
                            rightClickAir(player);
                            player.getInventory().setItemInMainHand(held);
                        }
                    }
                    player.performCommand("pets quick set " + (slot + 1) + " " + petId);
                }
                getLogger().info("Host mode: " + player.getName() + " has " + HOST_PETS.length + " pets in quickslots 1-" + HOST_PETS.length + ".");
            } catch (final Exception problem) {
                getLogger().warning("Host mode: could not set " + player.getName() + " up: " + describe(problem));
            }
        }, 60L);
    }

    // ------------------------------------------------------------------------------------------------
    // The stand-ins for other plugins
    // ------------------------------------------------------------------------------------------------

    @EventHandler(priority = EventPriority.HIGHEST)
    public void protect(final BlockBreakEvent event) {
        if (cancelBreaks) {
            event.setCancelled(true);
        }
    }

    /** For whoever reads the server log: why a trade window closed, as the server saw it. */
    @EventHandler(priority = EventPriority.LOWEST)
    public void watchTradeWindows(final InventoryCloseEvent event) {
        final InventoryHolder holder = event.getInventory().getHolder();
        if (holder != null && holder.getClass().getSimpleName().equals("TradeMenuHolder")) {
            getLogger().info("(the trade window of " + event.getPlayer().getName() + " closes: " + event.getReason() + ")");
        }
    }

    @EventHandler(priority = EventPriority.NORMAL)
    public void customFishing(final PlayerFishEvent event) {
        if (cancelCatch) {
            event.setCancelled(true);
        }
        if (replaceCatch && event.getCaught() instanceof Item caught) {
            caught.setItemStack(new ItemStack(Material.DIAMOND));
        }
    }

    // ------------------------------------------------------------------------------------------------
    // The run
    // ------------------------------------------------------------------------------------------------

    private void run() {
        final File rejoinMarker = new File(getDataFolder(), "rejoin-pending");
        try {
            final Player first = waitForPlayer(firstName, 900);
            if (first == null) {
                note("FAIL", "the test player " + firstName + " never joined", "");
                return;
            }
            Thread.sleep(4000);
            if (rejoinMarker.isFile()) {
                // Nothing is tidied up first here: what the player carries on joining is the very thing to look at.
                Files.delete(rejoinMarker.toPath());
                world = first.getWorld();
                afterRestart(first);
            } else {
                sync(() -> {
                    prepare(first);
                    return null;
                });
                getDataFolder().mkdirs();
                if (!Boolean.getBoolean("e2e.tradesOnly")) {
                    singlePlayer(first);
                }
                final boolean leftForRestart = twoPlayers(first, rejoinMarker);
                if (!leftForRestart) {
                    shutdownOfThePlugin(first);
                }
            }
        } catch (final Throwable problem) {
            note("FAIL", "the run itself broke down", describe(problem));
        } finally {
            writeReport();
            if (!Boolean.getBoolean("e2e.keepRunning")) {
                Bukkit.getScheduler().runTask(this, Bukkit::shutdown);
            }
        }
    }

    private void prepare(final Player player) {
        world = player.getWorld();
        world.setTime(6000L);
        final Location spawn = world.getSpawnLocation();
        final int x = spawn.getBlockX() + 12;
        final int z = spawn.getBlockZ();
        spot = new Location(world, x, world.getHighestBlockYAt(x, z) + 1, z);
        setUp(player);
    }

    private void setUp(final Player player) {
        final Location home = world.getSpawnLocation();
        home.setY(world.getHighestBlockYAt(home.getBlockX(), home.getBlockZ()) + 1);
        player.teleport(home.add(0.5, 0, 0.5));
        player.setGameMode(GameMode.SURVIVAL);
        player.setCanPickupItems(false);
        player.setOp(true);
        player.getInventory().clear();
        player.setHealth(20.0);
        player.setFoodLevel(20);
    }

    // ------------------------------------------------------------------------------------------------
    // One player
    // ------------------------------------------------------------------------------------------------

    private void singlePlayer(final Player a) throws Exception {
        final ItemStack pickaxe = tool(Material.DIAMOND_PICKAXE, false);
        final ItemStack silkPickaxe = tool(Material.DIAMOND_PICKAXE, true);
        final ItemStack axe = tool(Material.DIAMOND_AXE, false);

        scenario("the mod and the plugin find each other", () -> {
            final boolean listening = sync(() -> a.getListeningPluginChannels().contains("betterpets:quickslots"));
            check("the client listens on betterpets:quickslots", listening,
                "channels: " + sync(a::getListeningPluginChannels));
        });

        scenario("breaking a block as the player is counted correctly", () -> {
            final Map<Material, Integer> drops = breakOnce(a, Material.DIRT, null, null);
            check("a plain block of dirt drops exactly one dirt", amount(drops, Material.DIRT) == 1, drops.toString());
        });

        // ---- Crystal Golem ----------------------------------------------------------------------
        scenario("Crystal Golem", () -> {
            claim(a, "crystal_golem", 100);
            summon(a, "crystal_golem");
            final int rounds = 60;
            int diamonds = 0;
            int oreBack = 0;
            for (int i = 0; i < rounds; i++) {
                final Map<Material, Integer> drops = breakOnce(a, Material.DIAMOND_ORE, pickaxe, null);
                diamonds += amount(drops, Material.DIAMOND);
                oreBack += amount(drops, Material.DIAMOND_ORE);
            }
            check("ore mined out: the bonus is paid (more diamonds than ores)", diamonds > rounds && oreBack == 0,
                diamonds + " diamonds from " + rounds + " ores, " + oreBack + " ore blocks");
            int silkOre = 0;
            int silkOther = 0;
            for (int i = 0; i < rounds; i++) {
                final Map<Material, Integer> drops = breakOnce(a, Material.DIAMOND_ORE, silkPickaxe, null);
                silkOre += amount(drops, Material.DIAMOND_ORE);
                silkOther += total(drops) - amount(drops, Material.DIAMOND_ORE);
            }
            check("ore mined with Silk Touch: exactly the ore, never a second one", silkOre == rounds && silkOther == 0,
                silkOre + " ore blocks and " + silkOther + " other items from " + rounds + " ores");
            int debris = 0;
            for (int i = 0; i < rounds; i++) {
                debris += total(breakOnce(a, Material.ANCIENT_DEBRIS, pickaxe, null));
            }
            check("ancient debris: exactly one each, never a second one", debris == rounds, debris + " from " + rounds);

            cancelBreaks = true;
            int leaked = 0;
            int stillThere = 0;
            for (int i = 0; i < 40; i++) {
                final Map<Material, Integer> drops = breakOnce(a, Material.DIAMOND_ORE, pickaxe, null);
                leaked += total(drops);
                stillThere += drops.getOrDefault(Material.BARRIER, 0);
            }
            cancelBreaks = false;
            check("a break another plugin cancels leaves nothing behind", leaked == 0 && stillThere == 40,
                leaked + " items from 40 cancelled breaks, block still there " + stillThere + " times");
        });

        // ---- Ore tokens and Arcane Fox ------------------------------------------------------------
        scenario("ore tokens", () -> {
            final int before = tokens(a);
            for (int i = 0; i < 10; i++) {
                breakOnce(a, Material.IRON_ORE, pickaxe, null);
            }
            final int mined = tokens(a);
            for (int i = 0; i < 10; i++) {
                breakOnce(a, Material.IRON_ORE, silkPickaxe, null);
            }
            for (int i = 0; i < 10; i++) {
                breakOnce(a, Material.ANCIENT_DEBRIS, pickaxe, null);
            }
            final int after = tokens(a);
            check("an ore that is mined out earns tokens (chance set to 100%)", mined >= before + 10, before + " -> " + mined);
            check("Silk Touch and ancient debris earn none", after == mined, mined + " -> " + after);
        });

        scenario("Arcane Fox", () -> {
            claim(a, "arcane_fox", 100);
            summon(a, "arcane_fox");
            final int start = sync(a::getTotalExperience);
            breakOnce(a, Material.COAL_ORE, pickaxe, null);
            final int mined = sync(a::getTotalExperience);
            breakOnce(a, Material.COAL_ORE, silkPickaxe, null);
            breakOnce(a, Material.ANCIENT_DEBRIS, pickaxe, null);
            final int after = sync(a::getTotalExperience);
            check("experience for an ore that is mined out", mined - start == 11, "gained " + (mined - start) + " (expected 11)");
            check("none for Silk Touch or ancient debris", after == mined, "gained another " + (after - mined));
        });

        // ---- Gatherer stars -----------------------------------------------------------------------
        scenario("Gatherer bonus (mole, five stars)", () -> {
            claim(a, "mole", 1);
            setStars(a, "mole", 5);
            summon(a, "mole");
            final int rounds = 80;

            int boxes = 0;
            int contents = 0;
            for (int i = 0; i < rounds; i++) {
                final Map<Material, Integer> drops = breakOnce(a, Material.SHULKER_BOX, pickaxe, block -> {
                    final ShulkerBox box = (ShulkerBox) block.getState();
                    box.getInventory().setItem(0, new ItemStack(Material.EMERALD, 7));
                });
                boxes += amount(drops, Material.SHULKER_BOX);
                contents += amount(drops, Material.EMERALD);
            }
            check("a filled shulker box: one box each, never a second one", boxes == rounds && contents == 0,
                boxes + " boxes and " + contents + " loose emeralds from " + rounds);

            int cauldrons = 0;
            for (int i = 0; i < rounds; i++) {
                cauldrons += amount(breakOnce(a, Material.WATER_CAULDRON, pickaxe, block -> {
                    final Levelled water = (Levelled) block.getBlockData();
                    water.setLevel(water.getMaximumLevel());
                    block.setBlockData(water, false);
                }), Material.CAULDRON);
            }
            check("a filled cauldron: one cauldron each, never a second one", cauldrons == rounds, cauldrons + " from " + rounds);

            int diamondBlocks = 0;
            for (int i = 0; i < rounds; i++) {
                diamondBlocks += total(breakOnce(a, Material.DIAMOND_BLOCK, pickaxe, null));
            }
            check("a block of diamond: one each", diamondBlocks == rounds, diamondBlocks + " from " + rounds);

            int logs = 0;
            for (int i = 0; i < rounds; i++) {
                logs += amount(breakOnce(a, Material.OAK_LOG, axe, null), Material.OAK_LOG);
            }
            check("logs are still doubled now and then", logs > rounds && logs <= 2 * rounds, logs + " from " + rounds);

            int cobble = 0;
            for (int i = 0; i < rounds; i++) {
                cobble += amount(breakOnce(a, Material.STONE, pickaxe, null), Material.COBBLESTONE);
            }
            check("stone still gives extra cobblestone now and then", cobble > rounds && cobble <= 2 * rounds, cobble + " from " + rounds);

            int wheat = 0;
            for (int i = 0; i < rounds; i++) {
                wheat += amount(breakOnce(a, Material.WHEAT, null, block -> {
                    block.getRelative(BlockFace.DOWN).setType(Material.FARMLAND, false);
                    // Planted by the player: a seed put down by hand, grown since.
                    Bukkit.getPluginManager().callEvent(new BlockPlaceEvent(block, block.getState(),
                        block.getRelative(BlockFace.DOWN), new ItemStack(Material.WHEAT_SEEDS), a, true, EquipmentSlot.HAND));
                    final Ageable crop = (Ageable) block.getBlockData();
                    crop.setAge(crop.getMaximumAge());
                    block.setBlockData(crop, false);
                }), Material.WHEAT);
            }
            check("ripe wheat the player planted is doubled now and then", wheat > rounds, wheat + " from " + rounds);

            int placedLogs = 0;
            for (int i = 0; i < rounds; i++) {
                placedLogs += amount(breakOnce(a, Material.OAK_LOG, axe, block ->
                    Bukkit.getPluginManager().callEvent(new BlockPlaceEvent(block, block.getState(),
                        block.getRelative(BlockFace.DOWN), new ItemStack(Material.OAK_LOG), a, true, EquipmentSlot.HAND))),
                    Material.OAK_LOG);
            }
            check("a log the player put down is never doubled", placedLogs == rounds, placedLogs + " from " + rounds);
        });

        scenario("a block pushed by a piston counts as placed", () -> {
            int pushed = 0;
            int logs = 0;
            for (int i = 0; i < 25; i++) {
                final Boolean moved = pushLogWithPiston();
                if (!Boolean.TRUE.equals(moved)) {
                    continue;
                }
                pushed++;
                logs += sync(() -> {
                    final Block target = spot.getBlock().getRelative(BlockFace.SOUTH, 2);
                    a.getInventory().setItemInMainHand(axe.clone());
                    a.breakBlock(target);
                    final int found = amount(itemsAround(), Material.OAK_LOG);
                    clearPistonRow();
                    return found;
                });
            }
            sync(() -> {
                clearPistonRow();
                return null;
            });
            if (pushed < 15) {
                skip("piston check", "the piston only moved the log " + pushed + " times of 25");
            } else {
                check("pushed logs are never doubled", logs == pushed, logs + " logs from " + pushed + " pushed ones");
            }
        });

        // ---- Silk Moth and Salamander ---------------------------------------------------------------
        scenario("Silk Moth", () -> {
            claim(a, "silk_moth", 100);
            summon(a, "silk_moth");
            int chests = 0;
            int gold = 0;
            int iron = 0;
            for (int i = 0; i < 40; i++) {
                final Map<Material, Integer> drops = breakOnce(a, Material.CHEST, axe, block -> {
                    final Chest chest = (Chest) block.getState();
                    chest.getBlockInventory().setItem(0, new ItemStack(Material.GOLD_INGOT, 5));
                    chest.getBlockInventory().setItem(13, new ItemStack(Material.IRON_INGOT, 3));
                });
                chests += amount(drops, Material.CHEST);
                gold += amount(drops, Material.GOLD_INGOT);
                iron += amount(drops, Material.IRON_INGOT);
            }
            check("a filled chest keeps everything that was in it", chests == 40 && gold == 200 && iron == 120,
                chests + " chests, " + gold + "/200 gold, " + iron + "/120 iron");

            int torches = 0;
            int stone = 0;
            int cobble = 0;
            for (int i = 0; i < 60; i++) {
                final Map<Material, Integer> drops = breakOnce(a, Material.STONE, pickaxe,
                    block -> block.getRelative(BlockFace.UP).setType(Material.TORCH, false));
                torches += amount(drops, Material.TORCH);
                stone += amount(drops, Material.STONE);
                cobble += amount(drops, Material.COBBLESTONE);
            }
            check("the torch on a broken block is never lost", torches == 60, torches + " torches from 60");
            check("stone comes back whole now and then, and never twice", stone > 5 && stone + cobble == 60,
                stone + " stone + " + cobble + " cobblestone from 60");

            int glass = 0;
            for (int i = 0; i < 60; i++) {
                glass += total(breakOnce(a, Material.GLASS, null, null));
            }
            check("glass broken by hand comes back now and then", glass > 5 && glass < 55, glass + " from 60");

            int both = 0;
            int whole = 0;
            for (int i = 0; i < 60; i++) {
                final Map<Material, Integer> drops = breakOnce(a, Material.DIAMOND_ORE, pickaxe, null);
                final int ore = amount(drops, Material.DIAMOND_ORE);
                final int gems = amount(drops, Material.DIAMOND);
                whole += ore;
                if ((ore > 0 && gems > 0) || ore > 1 || (ore == 0 && gems == 0)) {
                    both++;
                }
            }
            check("an ore comes back whole or as diamonds, never as both", both == 0 && whole > 5, whole + " whole, " + both + " wrong");
        });

        scenario("Salamander", () -> {
            claim(a, "salamander", 100);
            summon(a, "salamander");
            int ingots = 0;
            int raw = 0;
            int torches = 0;
            for (int i = 0; i < 40; i++) {
                final Map<Material, Integer> drops = breakOnce(a, Material.IRON_ORE, pickaxe,
                    block -> block.getRelative(BlockFace.UP).setType(Material.TORCH, false));
                ingots += amount(drops, Material.IRON_INGOT);
                raw += amount(drops, Material.RAW_IRON);
                torches += amount(drops, Material.TORCH);
            }
            check("iron ore is smelted now and then, one piece per ore", ingots > 5 && ingots + raw == 40, ingots + " ingots + " + raw + " raw from 40");
            check("the torch on the ore is never lost", torches == 40, torches + " torches from 40");
        });

        // ---- Fishing ------------------------------------------------------------------------------
        scenario("Water Serpent", () -> {
            claim(a, "water_serpent", 100);
            summon(a, "water_serpent");
            final int[] plain = fish(a, 40);
            check("a catch earns a second fish now and then", plain[0] > 5 && plain[0] < 35 && plain[1] == 0, plain[0] + " extra cod from 40");
            cancelCatch = true;
            final int[] cancelled = fish(a, 40);
            cancelCatch = false;
            check("a catch another plugin cancels earns nothing", cancelled[0] == 0 && cancelled[1] == 0, cancelled[0] + " extra");
            replaceCatch = true;
            final int[] replaced = fish(a, 40);
            replaceCatch = false;
            check("the bonus is the fish on the hook, not what another plugin made of the catch",
                replaced[0] > 5 && replaced[1] == 0, replaced[0] + " cod, " + replaced[1] + " diamonds as bonus");
        });

        // ---- Pet items ------------------------------------------------------------------------------
        scenario("a head out of a menu is not a pet", () -> {
            final int petsBefore = petCount(a);
            final int tokensBefore = tokens(a);
            final String outcome = sync(() -> {
                a.performCommand("pets info");
                final ItemStack icon = a.getOpenInventory().getTopInventory().getItem(0);
                if (icon == null || tag(icon, "pet_id") == null) {
                    return "no pet head in the first slot of the catalogue";
                }
                final ItemStack taken = icon.clone();
                a.closeInventory();
                hold(a, taken);
                rightClickAir(a);
                final boolean stillHeld = tag(a.getInventory().getItemInMainHand(), "pet_id") != null;
                a.performCommand("pets scrap");
                final boolean stillHeldAfterScrap = tag(a.getInventory().getItemInMainHand(), "pet_id") != null;
                a.getInventory().setItemInMainHand(null);
                return stillHeld && stillHeldAfterScrap ? "" : "the head was used up";
            });
            check("it can be neither taken in nor scrapped", outcome.isEmpty() && petCount(a) == petsBefore && tokens(a) == tokensBefore,
                outcome + " pets " + petsBefore + " -> " + petCount(a) + ", tokens " + tokensBefore + " -> " + tokens(a));
        });

        scenario("a pet in the off hand", () -> {
            final String outcome = sync(() -> {
                Bukkit.dispatchCommand(Bukkit.getConsoleSender(), "pets give cat 1 " + a.getName());
                final ItemStack cat = takePetItem(a, "cat");
                if (cat == null) {
                    return "no cat item arrived";
                }
                a.getInventory().setItemInOffHand(cat);
                final Block ground = a.getLocation().getBlock().getRelative(BlockFace.DOWN);
                final PlayerInteractEvent use = new PlayerInteractEvent(a, Action.RIGHT_CLICK_BLOCK,
                    a.getInventory().getItemInOffHand(), ground, BlockFace.UP, EquipmentSlot.OFF_HAND);
                Bukkit.getPluginManager().callEvent(use);
                final Block above = ground.getRelative(BlockFace.UP);
                final BlockPlaceEvent place = new BlockPlaceEvent(above, above.getState(), ground,
                    a.getInventory().getItemInOffHand(), a, true, EquipmentSlot.OFF_HAND);
                Bukkit.getPluginManager().callEvent(place);
                final boolean kept = tag(a.getInventory().getItemInOffHand(), "pet_id") != null;
                a.getInventory().setItemInOffHand(null);
                if (use.useItemInHand() != Event.Result.DENY) {
                    return "using it from the off hand was not refused";
                }
                if (!place.isCancelled()) {
                    return "placing the head was not refused";
                }
                return kept ? "" : "the item is gone";
            });
            check("is neither used nor placed", outcome.isEmpty(), outcome);
        });

        scenario("converting a pet and taking it in again", () -> {
            claim(a, "axolotl", 1);
            summon(a, "axolotl");
            final String skin = (String) call(pet(a, "axolotl"), "variant");
            final boolean hadSkin = skin != null && (Boolean) call(data(a), "isVariantUnlocked", "axolotl", skin);
            convertActivePet(a);
            final boolean gone = pet(a, "axolotl") == null;
            final boolean skinLeft = skin != null && !(Boolean) call(data(a), "isVariantUnlocked", "axolotl", skin);
            final String itemSkin = sync(() -> {
                final ItemStack item = takePetItem(a, "axolotl");
                if (item == null) {
                    return null;
                }
                hold(a, item);
                final String carried = tag(item, "pet_variant");
                rightClickAir(a);
                return carried == null ? "" : carried;
            });
            final Object back = pet(a, "axolotl");
            check("the pet becomes an item that carries its skin", hadSkin && gone && skin.equalsIgnoreCase(itemSkin),
                "skin " + skin + ", on the item " + itemSkin + ", pet gone " + gone);
            check("the skin leaves the collection with the pet and comes back with it",
                skinLeft && back != null && skin.equalsIgnoreCase((String) call(back, "variant"))
                    && (Boolean) call(data(a), "isVariantUnlocked", "axolotl", skin),
                "left " + skinLeft + ", back " + (back != null));

            claim(a, "phoenix", 1);
            summon(a, "phoenix");
            final long revived = System.currentTimeMillis() - 60_000L;
            call(pet(a, "phoenix"), "setLastTotemMillis", revived);
            convertActivePet(a);
            final Long onItem = sync(() -> {
                final ItemStack item = takePetItem(a, "phoenix");
                if (item == null) {
                    return null;
                }
                hold(a, item);
                final Long stamp = item.getItemMeta().getPersistentDataContainer()
                    .get(new NamespacedKey(NS, "pet_totem"), PersistentDataType.LONG);
                rightClickAir(a);
                return stamp == null ? -1L : stamp;
            });
            final Object phoenix = pet(a, "phoenix");
            check("a Phoenix keeps its revive time through the item", onItem != null && onItem == revived && phoenix != null
                && (Long) call(phoenix, "lastTotemMillis") == revived, "item " + onItem + ", expected " + revived);
        });

        scenario("Alpaca storage", () -> {
            claim(a, "alpaca", 1);
            summon(a, "alpaca");
            Thread.sleep(500);
            final String opened = sync(() -> openAlpaca(a) ? "" : "the storage did not open (" + holder(a) + ")");
            if (!opened.isEmpty()) {
                skip("Alpaca storage", opened);
                return;
            }
            final String outcome = sync(() -> {
                a.getOpenInventory().getTopInventory().setItem(0, new ItemStack(Material.DIAMOND, 5));
                a.closeInventory();
                if (!openAlpaca(a) || amount(a.getOpenInventory().getTopInventory().getItem(0)) != 5) {
                    return "what was put in is not there after reopening";
                }
                // Take it out, and "right-click the Alpaca" again without closing the window first.
                a.getOpenInventory().getTopInventory().setItem(0, null);
                a.getInventory().addItem(new ItemStack(Material.DIAMOND, 5));
                openAlpaca(a);
                final int inStorage = count(a.getOpenInventory().getTopInventory().getContents(), Material.DIAMOND);
                final int carried = count(a.getInventory().getContents(), Material.DIAMOND);
                a.closeInventory();
                a.getInventory().remove(Material.DIAMOND);
                return inStorage == 0 && carried == 5 ? "" : "in the storage " + inStorage + ", carried " + carried;
            });
            check("opening it a second time does not bring back what was taken out", outcome.isEmpty(), outcome);
        });

        scenario("Golem Mason refill", () -> {
            claim(a, "golem_mason", 1);
            summon(a, "golem_mason");
            sync(() -> {
                layOutForRefill(a);
                // The last block is placed, and in the same moment the player scrolls on to the sword.
                a.getInventory().setItem(0, null);
                a.getInventory().setHeldItemSlot(1);
                return null;
            });
            Thread.sleep(250);
            final String switched = sync(() -> describeHotbar(a));
            check("it never overwrites what the player switched to", switched.equals("AIR/DIAMOND_SWORD/STONEx32"), switched);
            sync(() -> {
                layOutForRefill(a);
                a.getInventory().setItem(0, null);
                return null;
            });
            Thread.sleep(250);
            final String refilled = sync(() -> describeHotbar(a));
            check("it still refills the hand that ran empty", refilled.equals("STONEx32/DIAMOND_SWORD/AIR"), refilled);
            sync(() -> {
                a.getInventory().clear();
                return null;
            });
        });

        scenario("a suspicious block keeps its loot when it is rolled for a pet", () -> {
            final String outcome = sync(() -> {
                final Block block = spot.getBlock();
                clearSpot();
                block.setType(Material.SUSPICIOUS_SAND, false);
                final BrushableBlock buried = (BrushableBlock) block.getState();
                buried.setLootTable(Bukkit.getLootTable(NamespacedKey.minecraft("archaeology/desert_pyramid")));
                buried.update(true, false);
                hold(a, new ItemStack(Material.BRUSH));
                Bukkit.getPluginManager().callEvent(new PlayerInteractEvent(a, Action.RIGHT_CLICK_BLOCK,
                    a.getInventory().getItemInMainHand(), block, BlockFace.UP, EquipmentSlot.HAND));
                final BrushableBlock after = (BrushableBlock) block.getState();
                final boolean marked = after.getPersistentDataContainer().has(new NamespacedKey(NS, "brush_rolled"), PersistentDataType.BYTE);
                final boolean loot = after.getLootTable() != null;
                clearSpot();
                a.getInventory().clear();
                return marked && loot ? "" : "marked " + marked + ", loot table still there " + loot;
            });
            check("the roll is noted in the block and the loot table stays", outcome.isEmpty(), outcome);
        });

        scenario("Goblin", () -> {
            claim(a, "goblin", 100);
            summon(a, "goblin");
            sync(() -> {
                a.getInventory().clear();
                Bukkit.dispatchCommand(Bukkit.getConsoleSender(), "pets give cat 1 " + a.getName());
                final ItemStack cat = takePetItem(a, "cat");
                final WanderingTrader trader = world.spawn(a.getLocation().add(2, 0, 0), WanderingTrader.class);
                trader.setAI(false);
                // The trader's one-time deal: a pet for emeralds. Bought eighty times over.
                final MerchantRecipe deal = new MerchantRecipe(cat, 999);
                deal.addIngredient(new ItemStack(Material.EMERALD, 16));
                for (int i = 0; i < 80; i++) {
                    Bukkit.getPluginManager().callEvent(new PlayerTradeEvent(a, trader, deal, false, false));
                }
                trader.remove();
                return null;
            });
            Thread.sleep(300);
            final int[] found = sync(() -> {
                int cats = 0;
                for (final ItemStack item : a.getInventory().getContents()) {
                    if ("cat".equals(tag(item, "pet_id"))) {
                        cats += item.getAmount();
                    }
                }
                final int emeralds = count(a.getInventory().getContents(), Material.EMERALD);
                a.getInventory().clear();
                return new int[]{cats, emeralds};
            });
            check("it snatches emeralds, never a second pet", found[0] == 0 && found[1] > 0,
                found[0] + " pets and " + found[1] + " emeralds from 80 purchases");
        });

        scenario("quickslot commands", () -> {
            final String outcome = sync(() -> {
                a.performCommand("pets quick set 1 mole");
                a.performCommand("pets quick set 2 silk_moth");
                a.performCommand("pets quick 1");
                return "";
            });
            Thread.sleep(800);
            final String first = activePet(a);
            sync(() -> {
                a.performCommand("pets quick next");
                return null;
            });
            Thread.sleep(800);
            final String second = activePet(a);
            check("a slot summons its pet, and next steps on", "mole".equals(first) && "silk_moth".equals(second),
                outcome + first + " then " + second);
        });

        scenario("quickslot spam protection", () -> {
            int went = 0;
            String last = activePet(a);
            for (int i = 0; i < 14; i++) {
                final String command = i % 2 == 0 ? "pets quick 1" : "pets quick 2";
                sync(() -> {
                    a.performCommand(command);
                    return null;
                });
                Thread.sleep(600);
                final String now = activePet(a);
                if (!now.equals(last)) {
                    went++;
                }
                last = now;
            }
            check("too many switches in a row are locked out for a while", went >= 4 && went <= 10,
                went + " of 14 switches in 8 seconds went through");
        });
    }

    // ------------------------------------------------------------------------------------------------
    // Two players
    // ------------------------------------------------------------------------------------------------

    /** @return whether the first player was kicked on purpose and the server is to come up again for them */
    private boolean twoPlayers(final Player a, final File rejoinMarker) throws Exception {
        final int patience = Integer.getInteger("e2e.secondPlayerSeconds", 240);
        getLogger().info("Waiting up to " + patience + " s for a second player for the trade checks ...");
        Player other = null;
        for (int waited = 0; waited < patience && other == null; waited++) {
            other = sync(() -> Bukkit.getOnlinePlayers().stream().filter(p -> !p.equals(a)).findFirst().orElse(null));
            if (other == null) {
                Thread.sleep(1000);
            }
        }
        if (other == null) {
            skip("trading", "no second player joined within " + patience + " seconds");
            return false;
        }
        final Player b = other;
        Thread.sleep(3000);
        sync(() -> {
            b.setCanPickupItems(false);
            b.setGameMode(GameMode.SURVIVAL);
            return null;
        });
        scenario("the second client", () -> {
            final boolean mod = sync(() -> b.getListeningPluginChannels().contains("betterpets:quickslots"));
            note(mod ? "PASS" : "INFO", b.getName() + " joined" + (mod ? " with the quickslot mod" : " (without the quickslot mod)"), "");
            if (mod) {
                passed++;
            }
        });

        scenario("closing a trade", () -> {
            if (!openTrade(a, b)) {
                return;
            }
            final String outcome = sync(() -> {
                final String offered = offerPet(a, "cat", 28);
                if (!offered.isEmpty()) {
                    return offered;
                }
                a.closeInventory();
                return hasPetItem(a, "cat") ? "" : "the pet was not back the moment the window closed";
            });
            Thread.sleep(300);
            final String partner = sync(() -> holder(b));
            check("gives the pets back at once", outcome.isEmpty(), outcome);
            check("and closes the other player's window", !partner.equals("TradeMenuHolder"), "still open: " + partner);
            sync(() -> {
                takePetItem(a, "cat");
                return null;
            });
        });

        scenario("a trade for fewer tokens than shown", () -> {
            sync(() -> Bukkit.dispatchCommand(Bukkit.getConsoleSender(), "pets tokens give " + a.getName() + " 10"));
            final int had = tokens(a);
            if (!openTrade(a, b)) {
                return;
            }
            sync(() -> {
                for (int i = 0; i < 5; i++) {
                    click(a, 29, ClickType.LEFT);
                }
                // The tokens go elsewhere while the offer stands.
                Bukkit.dispatchCommand(Bukkit.getConsoleSender(), "pets tokens remove " + a.getName() + " " + (had - 2));
                click(a, 45, ClickType.LEFT);
                click(b, 53, ClickType.LEFT);
                return null;
            });
            Thread.sleep(300);
            final String open = sync(() -> holder(a) + "/" + holder(b));
            final int left = tokens(a);
            check("is not settled: both still have the window, nobody was paid",
                open.equals("TradeMenuHolder/TradeMenuHolder") && left == 2, "windows " + open + ", tokens " + left);
            sync(() -> {
                a.closeInventory();
                return null;
            });
            Thread.sleep(300);
        });

        scenario("a trade that goes through", () -> {
            if (!openTrade(a, b)) {
                return;
            }
            final String outcome = sync(() -> {
                final String offered = offerPet(a, "cat", 28);
                if (!offered.isEmpty()) {
                    return offered;
                }
                click(a, 45, ClickType.LEFT);
                click(b, 53, ClickType.LEFT);
                return "";
            });
            Thread.sleep(300);
            final String result = sync(() -> {
                final boolean arrived = hasPetItem(b, "cat");
                final boolean left = !hasPetItem(a, "cat");
                takePetItem(b, "cat");
                return arrived && left ? "" : "arrived " + arrived + ", left the giver " + left;
            });
            check("hands the pet to the other player", outcome.isEmpty() && result.isEmpty(), outcome + result);
        });

        scenario("dying in the middle of a trade", () -> {
            if (!openTrade(a, b)) {
                return;
            }
            final File waiting = new File(pets.getDataFolder(), "pending-returns.yml");
            final String outcome = sync(() -> {
                final String offered = offerPet(a, "cat", 28);
                if (!offered.isEmpty()) {
                    return offered;
                }
                a.setHealth(0.0);
                final boolean carried = hasPetItem(a, "cat");
                boolean onTheGround = false;
                for (final Entity entity : world.getNearbyEntities(a.getLocation(), 12, 12, 12)) {
                    if (entity instanceof Item item && tag(item.getItemStack(), "pet_id") != null) {
                        onTheGround = true;
                    }
                }
                return !carried && !onTheGround && waiting.isFile() ? ""
                    : "carried " + carried + ", on the ground " + onTheGround + ", kept in the file " + waiting.isFile();
            });
            check("the pet is neither wiped with the inventory nor dropped, but kept", outcome.isEmpty(), outcome);
            sync(() -> {
                a.spigot().respawn();
                return null;
            });
            Thread.sleep(600);
            final String back = sync(() -> hasPetItem(a, "cat") && !waiting.isFile() ? ""
                : "has it " + hasPetItem(a, "cat") + ", file still there " + waiting.isFile());
            check("and is back after the respawn", back.isEmpty(), back);
            sync(() -> {
                setUp(a);
                clearSpot();
                return null;
            });
        });

        scenario("losing the connection in the middle of a trade", () -> {
            if (!openTrade(a, b)) {
                return;
            }
            final File waiting = new File(pets.getDataFolder(), "pending-returns.yml");
            final String offered = sync(() -> offerPet(a, "cat", 28));
            if (!offered.isEmpty()) {
                check("the pet could be offered", false, offered);
                return;
            }
            Files.writeString(rejoinMarker.toPath(), "the test player was kicked mid-trade", StandardCharsets.UTF_8);
            sync(() -> {
                a.kick();
                return null;
            });
            Thread.sleep(600);
            final String outcome = sync(() -> {
                boolean onTheGround = false;
                for (final Entity entity : world.getEntitiesByClass(Item.class)) {
                    if (tag(((Item) entity).getItemStack(), "pet_id") != null) {
                        onTheGround = true;
                    }
                }
                return waiting.isFile() && !onTheGround && !holder(b).equals("TradeMenuHolder") ? ""
                    : "kept in the file " + waiting.isFile() + ", on the ground " + onTheGround + ", partner window " + holder(b);
            });
            check("the pet waits in pending-returns.yml and the partner's window closes", outcome.isEmpty(), outcome);
        });
        return rejoinMarker.isFile();
    }

    /** Second start of the server: the pets that waited for the kicked player have to arrive. */
    private void afterRestart(final Player a) throws Exception {
        scenario("after a restart", () -> {
            final File waiting = new File(pets.getDataFolder(), "pending-returns.yml");
            final String outcome = sync(() -> hasPetItem(a, "cat") && !waiting.isFile() ? ""
                : "has it " + hasPetItem(a, "cat") + ", file still there " + waiting.isFile());
            check("the pet from the interrupted trade is handed over on the next join", outcome.isEmpty(), outcome);
        });
        shutdownOfThePlugin(a);
    }

    private void shutdownOfThePlugin(final Player a) throws Exception {
        scenario("stopping the plugin", () -> {
            final String outcome = sync(() -> {
                if (!a.isOnline()) {
                    return "SKIP";
                }
                a.performCommand("pets info");
                final String before = holder(a);
                Bukkit.getPluginManager().disablePlugin(pets);
                final String after = holder(a);
                return before.equals("InfoMenuHolder") && after.isEmpty() ? "" : "before " + before + ", after " + after;
            });
            if (outcome.equals("SKIP")) {
                skip("stopping the plugin", "the test player is not online");
            } else {
                check("closes the menus that are open", outcome.isEmpty(), outcome);
            }
        });
    }

    // ------------------------------------------------------------------------------------------------
    // Doing what a player does
    // ------------------------------------------------------------------------------------------------

    private ItemStack tool(final Material type, final boolean silk) {
        final ItemStack item = new ItemStack(type);
        final ItemMeta meta = item.getItemMeta();
        meta.setUnbreakable(true);
        item.setItemMeta(meta);
        if (silk) {
            item.addEnchantment(Enchantment.SILK_TOUCH, 1);
        }
        return item;
    }

    /**
     * Puts a block at the test spot, has the player break it, and returns what lay around afterwards.
     * The entry for BARRIER says whether the block was still standing (1) after the break.
     */
    private Map<Material, Integer> breakOnce(final Player player, final Material type, final ItemStack tool,
                                             final Consumer<Block> prepare) throws Exception {
        return sync(() -> {
            final Block block = spot.getBlock();
            final Block below = block.getRelative(BlockFace.DOWN);
            final Material ground = below.getType();
            clearSpot();
            block.setType(type, false);
            if (prepare != null) {
                prepare.accept(block);
            }
            player.getInventory().setHeldItemSlot(0);
            player.getInventory().setItemInMainHand(tool == null ? null : tool.clone());
            player.breakBlock(block);
            final Map<Material, Integer> found = itemsAround();
            found.put(Material.BARRIER, block.getType() == type ? 1 : 0);
            clearSpot();
            below.setType(ground, false);
            return found;
        });
    }

    private Map<Material, Integer> itemsAround() {
        final Map<Material, Integer> found = new EnumMap<>(Material.class);
        for (final Entity entity : world.getNearbyEntities(spot.clone().add(0.5, 0.5, 0.5), 8, 8, 8)) {
            if (entity instanceof Item item) {
                final ItemStack stack = item.getItemStack();
                found.merge(stack.getType(), stack.getAmount(), Integer::sum);
            }
        }
        return found;
    }

    private void clearSpot() {
        final Block block = spot.getBlock();
        block.getRelative(BlockFace.UP).setType(Material.AIR, false);
        block.setType(Material.AIR, false);
        for (final Entity entity : world.getNearbyEntities(spot.clone().add(0.5, 0.5, 0.5), 12, 12, 12)) {
            if (entity instanceof Item || entity instanceof ExperienceOrb) {
                entity.remove();
            }
        }
    }

    private static int amount(final Map<Material, Integer> drops, final Material type) {
        return drops.getOrDefault(type, 0);
    }

    private static int amount(final ItemStack item) {
        return item == null ? 0 : item.getAmount();
    }

    private static int total(final Map<Material, Integer> drops) {
        int sum = 0;
        for (final Map.Entry<Material, Integer> entry : drops.entrySet()) {
            if (entry.getKey() != Material.BARRIER) {
                sum += entry.getValue();
            }
        }
        return sum;
    }

    private static int count(final ItemStack[] contents, final Material type) {
        int sum = 0;
        for (final ItemStack item : contents) {
            if (item != null && item.getType() == type) {
                sum += item.getAmount();
            }
        }
        return sum;
    }

    /** A piston at the test spot pushes a log one block south. Null if the row could not be set up. */
    private Boolean pushLogWithPiston() throws Exception {
        sync(() -> {
            clearPistonRow();
            final Block piston = spot.getBlock();
            piston.setType(Material.PISTON, false);
            final Directional facing = (Directional) piston.getBlockData();
            facing.setFacing(BlockFace.SOUTH);
            piston.setBlockData(facing, false);
            piston.getRelative(BlockFace.SOUTH).setType(Material.OAK_LOG, false);
            piston.getRelative(BlockFace.NORTH).setType(Material.REDSTONE_BLOCK, true);
            return null;
        });
        Thread.sleep(400);
        return sync(() -> spot.getBlock().getRelative(BlockFace.SOUTH, 2).getType() == Material.OAK_LOG);
    }

    private void clearPistonRow() {
        for (int step = 3; step >= -1; step--) {
            final Block block = step >= 0 ? spot.getBlock().getRelative(BlockFace.SOUTH, step) : spot.getBlock().getRelative(BlockFace.NORTH);
            block.setType(Material.AIR, false);
        }
        clearSpot();
    }

    /** Reels in 'rounds' cod. Returns the cod and the diamonds that turned up besides the catches themselves. */
    private int[] fish(final Player player, final int rounds) throws Exception {
        int extraCod = 0;
        int extraDiamonds = 0;
        for (int i = 0; i < rounds; i++) {
            final int[] extra = sync(() -> {
                for (final Entity entity : world.getNearbyEntities(player.getLocation(), 10, 10, 10)) {
                    if (entity instanceof Item) {
                        entity.remove();
                    }
                }
                final FishHook hook = player.launchProjectile(FishHook.class);
                final Item caught = world.dropItem(player.getLocation().add(3, 0, 0), new ItemStack(Material.COD));
                Bukkit.getPluginManager().callEvent(new PlayerFishEvent(player, caught, hook, PlayerFishEvent.State.CAUGHT_FISH));
                int cod = 0;
                int diamonds = 0;
                for (final Entity entity : world.getNearbyEntities(player.getLocation(), 10, 10, 10)) {
                    if (entity instanceof Item item && !item.equals(caught)) {
                        if (item.getItemStack().getType() == Material.COD) {
                            cod += item.getItemStack().getAmount();
                        } else if (item.getItemStack().getType() == Material.DIAMOND) {
                            diamonds += item.getItemStack().getAmount();
                        }
                    }
                    if (entity instanceof Item) {
                        entity.remove();
                    }
                }
                hook.remove();
                return new int[]{cod, diamonds};
            });
            extraCod += extra[0];
            extraDiamonds += extra[1];
        }
        return new int[]{extraCod, extraDiamonds};
    }

    private void hold(final Player player, final ItemStack item) {
        player.getInventory().setHeldItemSlot(0);
        player.getInventory().setItemInMainHand(item);
    }

    private void rightClickAir(final Player player) {
        Bukkit.getPluginManager().callEvent(new PlayerInteractEvent(player, Action.RIGHT_CLICK_AIR,
            player.getInventory().getItemInMainHand(), null, BlockFace.SELF, EquipmentSlot.HAND));
    }

    private void click(final Player player, final int slot, final ClickType type) {
        Bukkit.getPluginManager().callEvent(new InventoryClickEvent(player.getOpenInventory(),
            InventoryType.SlotType.CONTAINER, slot, type, InventoryAction.PICKUP_ALL));
    }

    private static String holder(final Player player) {
        final InventoryHolder holder = player.getOpenInventory().getTopInventory().getHolder();
        return holder == null || holder == player ? "" : holder.getClass().getSimpleName();
    }

    private static String tag(final ItemStack item, final String key) {
        if (item == null || !item.hasItemMeta()) {
            return null;
        }
        final PersistentDataContainer data = item.getItemMeta().getPersistentDataContainer();
        return data.get(new NamespacedKey(NS, key), PersistentDataType.STRING);
    }

    private static boolean hasPetItem(final Player player, final String petId) {
        for (final ItemStack item : player.getInventory().getContents()) {
            if (petId.equals(tag(item, "pet_id"))) {
                return true;
            }
        }
        return false;
    }

    /** Takes the first item of that pet out of the player's inventory. */
    private static ItemStack takePetItem(final Player player, final String petId) {
        final ItemStack[] contents = player.getInventory().getContents();
        for (int slot = 0; slot < contents.length; slot++) {
            if (petId.equals(tag(contents[slot], "pet_id"))) {
                final ItemStack item = contents[slot].clone();
                player.getInventory().setItem(slot, null);
                return item;
            }
        }
        return null;
    }

    /** The console hands out the pet as an item, the player takes it in with a right-click. */
    private void claim(final Player player, final String petId, final int level) throws Exception {
        final String problem = sync(() -> {
            Bukkit.dispatchCommand(Bukkit.getConsoleSender(), "pets give " + petId + " " + level + " " + player.getName());
            final ItemStack item = takePetItem(player, petId);
            if (item == null) {
                return "the " + petId + " item did not arrive";
            }
            hold(player, item);
            rightClickAir(player);
            return "";
        });
        if (!problem.isEmpty() || pet(player, petId) == null) {
            throw new IllegalStateException("could not take in a " + petId + ": " + problem);
        }
    }

    private void summon(final Player player, final String petId) throws Exception {
        final Object owned = pet(player, petId);
        final Object result = sync(() -> call(pets, "summonPet", player, data(player), owned));
        if (!"SUMMONED".equals(String.valueOf(result))) {
            throw new IllegalStateException("could not summon the " + petId + ": " + result);
        }
        Thread.sleep(150);
    }

    private void setStars(final Player player, final String petId, final int stars) throws Exception {
        call(pet(player, petId), "setFusionPoints", stars);
    }

    /** Two clicks on the convert button of the pet menu, as the confirmation asks for. */
    private void convertActivePet(final Player player) throws Exception {
        sync(() -> {
            player.performCommand("pets");
            return null;
        });
        Thread.sleep(300);
        sync(() -> {
            click(player, 53, ClickType.LEFT);
            return null;
        });
        Thread.sleep(300);
        sync(() -> {
            click(player, 53, ClickType.LEFT);
            player.closeInventory();
            return null;
        });
        Thread.sleep(200);
    }

    private boolean openAlpaca(final Player player) {
        for (final Entity entity : player.getNearbyEntities(8, 8, 8)) {
            if (entity instanceof Interaction) {
                Bukkit.getPluginManager().callEvent(new PlayerInteractEntityEvent(player, entity, EquipmentSlot.HAND));
                if (holder(player).equals("AlpacaStorageHolder")) {
                    return true;
                }
            }
        }
        return holder(player).equals("AlpacaStorageHolder");
    }

    private void layOutForRefill(final Player player) {
        player.getInventory().clear();
        player.getInventory().setItem(0, new ItemStack(Material.STONE, 1));
        player.getInventory().setItem(1, new ItemStack(Material.DIAMOND_SWORD));
        player.getInventory().setItem(2, new ItemStack(Material.STONE, 32));
        player.getInventory().setHeldItemSlot(0);
        final Block block = spot.getBlock();
        Bukkit.getPluginManager().callEvent(new BlockPlaceEvent(block, block.getState(), block.getRelative(BlockFace.DOWN),
            player.getInventory().getItemInMainHand(), player, true, EquipmentSlot.HAND));
    }

    private static String describeHotbar(final Player player) {
        final StringBuilder text = new StringBuilder();
        for (int slot = 0; slot < 3; slot++) {
            final ItemStack item = player.getInventory().getItem(slot);
            if (slot > 0) {
                text.append('/');
            }
            text.append(item == null ? "AIR" : item.getType() + (item.getAmount() > 1 ? "x" + item.getAmount() : ""));
        }
        return text.toString();
    }

    private boolean openTrade(final Player a, final Player b) throws Exception {
        sync(() -> {
            a.closeInventory();
            b.closeInventory();
            a.performCommand("pets trade " + b.getName());
            b.performCommand("pets trade accept");
            return null;
        });
        Thread.sleep(300);
        final String open = sync(() -> holder(a) + "/" + holder(b));
        if (!open.equals("TradeMenuHolder/TradeMenuHolder")) {
            check("the trade window opens for both", false, open);
            return false;
        }
        return true;
    }

    /** Gives the player a pet item, puts it into their hand and has them click "offer". */
    private String offerPet(final Player player, final String petId, final int addSlot) {
        if (!hasPetItem(player, petId)) {
            Bukkit.dispatchCommand(Bukkit.getConsoleSender(), "pets give " + petId + " 1 " + player.getName());
        }
        final ItemStack item = takePetItem(player, petId);
        if (item == null) {
            return "no " + petId + " item to offer";
        }
        hold(player, item);
        click(player, addSlot, ClickType.LEFT);
        return hasPetItem(player, petId) ? "the pet did not leave the hand when it was offered" : "";
    }

    // ------------------------------------------------------------------------------------------------
    // Looking into Better Pets
    // ------------------------------------------------------------------------------------------------

    private Object data(final Player player) throws Exception {
        return call(call(pets, "petStorage"), "data", player.getUniqueId());
    }

    private Object pet(final Player player, final String petId) throws Exception {
        return ((Optional<?>) call(data(player), "findByDefinition", petId)).orElse(null);
    }

    private int tokens(final Player player) throws Exception {
        return (Integer) call(data(player), "tokens");
    }

    private int petCount(final Player player) throws Exception {
        return ((List<?>) call(data(player), "pets")).size();
    }

    private String activePet(final Player player) throws Exception {
        final Optional<?> active = (Optional<?>) call(data(player), "activePet");
        return active.isPresent() ? (String) call(active.get(), "definitionId") : "";
    }

    private static Object call(final Object target, final String name, final Object... args) throws Exception {
        for (Class<?> type = target.getClass(); type != null; type = type.getSuperclass()) {
            for (final Method method : type.getDeclaredMethods()) {
                if (method.getName().equals(name) && method.getParameterCount() == args.length) {
                    method.setAccessible(true);
                    return method.invoke(target, args);
                }
            }
        }
        throw new NoSuchMethodException(target.getClass().getSimpleName() + "." + name + "/" + args.length);
    }

    // ------------------------------------------------------------------------------------------------
    // Plumbing
    // ------------------------------------------------------------------------------------------------

    private interface Body {
        void run() throws Exception;
    }

    private void scenario(final String name, final Body body) {
        note("----", name, "");
        try {
            body.run();
        } catch (final Throwable problem) {
            note("FAIL", name + ": did not get through", describe(problem));
            failed++;
            cancelBreaks = false;
            cancelCatch = false;
            replaceCatch = false;
        }
    }

    private void check(final String what, final boolean ok, final String detail) {
        if (ok) {
            passed++;
        } else {
            failed++;
        }
        note(ok ? "PASS" : "FAIL", what, detail);
    }

    private void skip(final String what, final String why) {
        skipped++;
        note("SKIP", what, why);
    }

    private synchronized void note(final String kind, final String what, final String detail) {
        final String line = kind + "  " + what + (detail == null || detail.isBlank() ? "" : "   [" + detail.trim() + "]");
        lines.add(line);
        getLogger().info(line);
    }

    private static String describe(final Throwable problem) {
        Throwable cause = problem;
        while (cause.getCause() != null && (cause instanceof java.lang.reflect.InvocationTargetException
            || cause instanceof java.util.concurrent.ExecutionException)) {
            cause = cause.getCause();
        }
        final StackTraceElement[] trace = cause.getStackTrace();
        return cause + (trace.length > 0 ? " at " + trace[0] : "");
    }

    private <T> T sync(final Callable<T> work) throws Exception {
        return Bukkit.getScheduler().callSyncMethod(this, work).get(60, TimeUnit.SECONDS);
    }

    private Player waitForPlayer(final String name, final int seconds) throws Exception {
        for (int waited = 0; waited < seconds; waited++) {
            final Player player = sync(() -> Bukkit.getPlayerExact(name));
            if (player != null) {
                return player;
            }
            Thread.sleep(1000);
        }
        return null;
    }

    private synchronized void writeReport() {
        final List<String> out = new ArrayList<>(lines);
        out.add("");
        out.add("Passed: " + passed + "   Failed: " + failed + "   Skipped: " + skipped);
        try {
            final File report = new File(getServer().getWorldContainer(), "e2e-report.txt");
            final List<String> all = new ArrayList<>();
            if (report.isFile()) {
                all.addAll(Files.readAllLines(report.toPath(), StandardCharsets.UTF_8));
                all.add("");
            }
            all.addAll(out);
            Files.write(report.toPath(), all, StandardCharsets.UTF_8);
        } catch (final Exception problem) {
            getLogger().severe("Could not write the report: " + problem);
        }
        getLogger().info("Passed: " + passed + "   Failed: " + failed + "   Skipped: " + skipped);
    }
}
