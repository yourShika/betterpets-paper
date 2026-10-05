package de.kamil.betterpets;

import de.kamil.betterpets.quickslots.HeldSlotInterceptor;
import de.kamil.betterpets.quickslots.QuickslotLogic;
import de.kamil.betterpets.quickslots.QuickslotProtocol;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Bukkit;
import org.bukkit.Sound;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerToggleSneakEvent;
import org.bukkit.plugin.messaging.PluginMessageListener;
import org.bukkit.scheduler.BukkitTask;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Quickslots: numbered slots a player parks pets in, to switch between them without opening the menu.
 *
 * <p>There are three ways to switch, each of which the server can turn off in config.yml
 * ({@code quickslots.methods}):</p>
 * <ul>
 *   <li><b>commands</b> - {@code /pets quick <slot> | next | prev | off};</li>
 *   <li><b>sneak-scroll</b> - sneak and turn the mouse wheel; needs the PacketEvents plugin (see
 *       {@code PacketEventsModule}), without it this method simply never activates;</li>
 *   <li><b>mod</b> - the "Better Pets Quickslots" Fabric mod: real key bindings and a slot screen,
 *       talking to this class over the {@value QuickslotProtocol#CHANNEL} plugin-message channel.</li>
 * </ul>
 *
 * <p>Whatever the method, the switch itself goes through {@link BetterPetsPlugin#summonPet}, so every
 * equip rule (disabled pets, the Alpaca storage lock) applies exactly as in the menu.</p>
 */
final class QuickslotManager implements Listener, PluginMessageListener, HeldSlotInterceptor {

    static final String PERMISSION = "betterpets.quickslots";

    /** A mod may ask for the pet list at most this often; the list is pushed on its own whenever it changes. */
    private static final long REQUEST_MIN_INTERVAL_MILLIS = 500L;
    /** Messages a single client may send per second before the rest are dropped. */
    private static final int MAX_MESSAGES_PER_SECOND = 60;
    /** Hotbar changes this soon after a swallowed scroll notch belong to the same wheel gesture. */
    private static final long SCROLL_GESTURE_MILLIS = 250L;

    /** How a quick switch was triggered; decides which config toggle gates it and how feedback is shown. */
    enum Source {
        COMMAND, SCROLL, MOD
    }

    /** What we last told one mod-running client, so it is only sent again when something changed. */
    private static final class ModClient {
        boolean stateSent;
        int stateHash;
        boolean petsSent;
        int petsRevision;
        long lastRequestMillis;
        long windowStartMillis;
        int windowCount;
    }

    private final BetterPetsPlugin plugin;

    // --- config snapshot (refreshed on enable and on /pets reload) ---
    private boolean enabled;
    private int slotCount;
    private boolean commandsEnabled;
    private boolean sneakScrollEnabled;
    private boolean modEnabled;
    private long cooldownMillis;
    private boolean sameSlotPutsAway;
    private boolean scrollDefaultOn;

    // --- runtime ---
    private final Map<UUID, Long> lastSwitch = new HashMap<>();
    private final Map<UUID, ModClient> modClients = new HashMap<>();
    private BukkitTask syncTask;
    // Sneak+scroll state. Written on the main thread, read by the network thread, hence concurrent.
    // armedSlots: sneaking players for whom a wheel notch switches pets -> the hotbar slot they are on.
    private final Map<UUID, Integer> armedSlots = new ConcurrentHashMap<>();
    private final Map<UUID, Long> lastIntercept = new ConcurrentHashMap<>();
    private volatile boolean scrollHookActive;

    QuickslotManager(final BetterPetsPlugin plugin) {
        this.plugin = plugin;
        refresh();
    }

    /** Re-reads the quickslot options from config.yml. */
    void refresh() {
        final var config = plugin.getConfig();
        enabled = config.getBoolean("quickslots.enabled", true);
        slotCount = Math.max(1, Math.min(PlayerPetData.MAX_QUICKSLOTS, config.getInt("quickslots.slots", 5)));
        commandsEnabled = config.getBoolean("quickslots.methods.commands", true);
        sneakScrollEnabled = config.getBoolean("quickslots.methods.sneak-scroll", true);
        modEnabled = config.getBoolean("quickslots.methods.mod", true);
        cooldownMillis = Math.max(0L, config.getLong("quickslots.switch-cooldown-ticks", 10L)) * 50L;
        sameSlotPutsAway = config.getBoolean("quickslots.same-slot-puts-away", true);
        scrollDefaultOn = config.getBoolean("quickslots.sneak-scroll.default-on", true);
        // Anyone currently armed was armed under the old settings; they re-arm on their next sneak.
        armedSlots.clear();
    }

    void start() {
        Bukkit.getPluginManager().registerEvents(this, plugin);
        Bukkit.getMessenger().registerOutgoingPluginChannel(plugin, QuickslotProtocol.CHANNEL);
        Bukkit.getMessenger().registerIncomingPluginChannel(plugin, QuickslotProtocol.CHANNEL, this);
        // Catches every change made outside this class (menu summon, level-ups, new pets, ...) without
        // having to hook each of those code paths: once a second, mod clients are sent whatever changed.
        syncTask = Bukkit.getScheduler().runTaskTimer(plugin, this::syncAll, 20L, 20L);
    }

    void stop() {
        if (syncTask != null) {
            syncTask.cancel();
            syncTask = null;
        }
        HandlerList.unregisterAll(this);
        Bukkit.getMessenger().unregisterIncomingPluginChannel(plugin, QuickslotProtocol.CHANNEL, this);
        Bukkit.getMessenger().unregisterOutgoingPluginChannel(plugin, QuickslotProtocol.CHANNEL);
        modClients.clear();
        armedSlots.clear();
        lastIntercept.clear();
        lastSwitch.clear();
    }

    /** Whether the server wants the sneak+scroll method (the PacketEvents module is enabled for it). */
    boolean sneakScrollWanted() {
        return enabled && sneakScrollEnabled;
    }

    // ------------------------------------------------------------------------------------------------
    // Switching
    // ------------------------------------------------------------------------------------------------

    /** Summons the pet parked in {@code slot} (0-based), or puts it away if it is already out. */
    boolean switchTo(final Player player, final int slot, final Source source) {
        if (!allowed(player, source)) {
            return false;
        }
        if (slot < 0 || slot >= slotCount) {
            actionBar(player, "quickslots.invalid-slot", NamedTextColor.RED, "%max%", Integer.toString(slotCount));
            return false;
        }
        final PlayerPetData data = data(player);
        final String petId = data.quickslot(slot);
        if (petId == null) {
            actionBar(player, "quickslots.empty-slot", NamedTextColor.YELLOW, "%slot%", Integer.toString(slot + 1));
            return false;
        }
        final OwnedPet pet = data.findByDefinition(petId).orElse(null);
        if (pet == null) {
            actionBar(player, "quickslots.missing-pet", NamedTextColor.YELLOW,
                "%slot%", Integer.toString(slot + 1), "%pet%", typeName(petId));
            return false;
        }
        if (pet.uuid().equals(data.activePetId())) {
            if (sameSlotPutsAway) {
                return putAway(player, source);
            }
            actionBar(player, "quickslots.already-out", NamedTextColor.GRAY, "%pet%", displayName(pet));
            return false;
        }
        if (onCooldown(player)) {
            // A wheel naturally produces several notches; staying quiet there avoids action-bar spam.
            if (source != Source.SCROLL) {
                actionBar(player, "quickslots.cooldown", NamedTextColor.GRAY);
            }
            return false;
        }
        switch (plugin.summonPet(player, data, pet)) {
            case DISABLED -> {
                actionBar(player, "messages.pet-disabled", NamedTextColor.RED, "%pet%", typeName(petId));
                return false;
            }
            case STORAGE_LOCKED -> {
                actionBar(player, "messages.alpaca-storage-not-empty", NamedTextColor.RED);
                return false;
            }
            case SUMMONED -> {
                lastSwitch.put(player.getUniqueId(), System.currentTimeMillis());
                player.playSound(player.getLocation(), Sound.ENTITY_ITEM_PICKUP, 0.6F, 1.5F);
                // The mod draws its own slot bar when the state changes, so its key presses need no text.
                if (source != Source.MOD) {
                    actionBar(player, "quickslots.switched", NamedTextColor.GREEN,
                        "%slot%", Integer.toString(slot + 1), "%pet%", displayName(pet), "%level%", Integer.toString(pet.level()));
                }
                pushState(player);
                return true;
            }
        }
        return false;
    }

    /** Steps to the next ({@code direction >= 0}) or previous filled quickslot, wrapping around. */
    boolean cycle(final Player player, final int direction, final Source source) {
        if (!allowed(player, source)) {
            return false;
        }
        final PlayerPetData data = data(player);
        final boolean[] usable = new boolean[slotCount];
        boolean anyAssigned = false;
        for (int slot = 0; slot < slotCount; slot++) {
            final String petId = data.quickslot(slot);
            if (petId == null) {
                continue;
            }
            anyAssigned = true;
            usable[slot] = !plugin.isPetDisabled(petId) && data.findByDefinition(petId).isPresent();
        }
        final int current = data.activePet().map(active -> data.quickslotOf(active.definitionId())).orElse(-1);
        final int target = QuickslotLogic.step(usable, current < slotCount ? current : -1, direction);
        if (target < 0) {
            actionBar(player, anyAssigned ? "quickslots.none-usable" : "quickslots.none-assigned", NamedTextColor.YELLOW);
            return false;
        }
        if (target == current) {
            // The only usable slot is the pet that is already out: stepping must not put it away.
            actionBar(player, "quickslots.only-one", NamedTextColor.GRAY);
            return false;
        }
        return switchTo(player, target, source);
    }

    /** Puts the active pet away (same rules as the menu's despawn button). */
    boolean putAway(final Player player, final Source source) {
        if (!allowed(player, source)) {
            return false;
        }
        final PlayerPetData data = data(player);
        if (data.activePet().isEmpty()) {
            actionBar(player, "messages.no-active-pet", NamedTextColor.GRAY);
            return false;
        }
        if (plugin.alpacaStorageLocked(data)) {
            actionBar(player, "messages.alpaca-storage-not-empty", NamedTextColor.RED);
            return false;
        }
        if (onCooldown(player)) {
            if (source != Source.SCROLL) {
                actionBar(player, "quickslots.cooldown", NamedTextColor.GRAY);
            }
            return false;
        }
        // Like a quick switch, putting a pet away is not saved on the spot: the plugin's save rewrites
        // the whole data file, which is too heavy for something a key can trigger twice a second. The
        // auto-save and the save on quit pick it up.
        plugin.activePetManager().despawn(player, true);
        lastSwitch.put(player.getUniqueId(), System.currentTimeMillis());
        player.playSound(player.getLocation(), Sound.ENTITY_ITEM_PICKUP, 0.6F, 0.9F);
        if (source != Source.MOD) {
            actionBar(player, "quickslots.put-away", NamedTextColor.GRAY);
        }
        pushState(player);
        return true;
    }

    /** Feature on, permission held, and this way of switching allowed on the server. */
    private boolean allowed(final Player player, final Source source) {
        if (!enabled) {
            actionBar(player, "quickslots.disabled", NamedTextColor.RED);
            return false;
        }
        if (!plugin.has(player, PERMISSION)) {
            actionBar(player, "messages.no-permission", NamedTextColor.RED);
            return false;
        }
        final boolean methodOn = switch (source) {
            case COMMAND -> commandsEnabled;
            case SCROLL -> sneakScrollEnabled && scrollHookActive;
            case MOD -> modEnabled;
        };
        if (!methodOn) {
            actionBar(player, "quickslots.method-off", NamedTextColor.RED);
        }
        return methodOn;
    }

    private boolean onCooldown(final Player player) {
        final Long last = lastSwitch.get(player.getUniqueId());
        return last != null && System.currentTimeMillis() - last < cooldownMillis;
    }

    // ------------------------------------------------------------------------------------------------
    // Slot management
    // ------------------------------------------------------------------------------------------------

    /**
     * Parks a pet in a slot.
     *
     * @return the lang key describing why it was refused, or {@code null} on success
     */
    private String assign(final Player player, final PlayerPetData data, final int slot, final String petId) {
        if (slot < 0 || slot >= slotCount) {
            return "quickslots.invalid-slot";
        }
        if (data.findByDefinition(petId).isEmpty()) {
            return "quickslots.not-owned";
        }
        if (plugin.isPetDisabled(petId)) {
            return "messages.pet-disabled";
        }
        data.setQuickslot(slot, petId);
        plugin.requestSave();
        refreshArming(player);
        return null;
    }

    private void clear(final Player player, final PlayerPetData data, final int slot) {
        data.setQuickslot(slot, null);
        plugin.requestSave();
        refreshArming(player);
    }

    // ------------------------------------------------------------------------------------------------
    // /pets quick ...
    // ------------------------------------------------------------------------------------------------

    /** Handles {@code /pets quick ...}; {@code args[0]} is the sub-command name itself. */
    void handleCommand(final Player player, final String[] args) {
        if (!enabled) {
            chat(player, "quickslots.disabled", NamedTextColor.RED);
            return;
        }
        if (!plugin.has(player, PERMISSION)) {
            player.sendMessage(plugin.lang().component("messages.no-permission"));
            return;
        }
        final PlayerPetData data = data(player);
        final String sub = args.length > 1 ? args[1].toLowerCase(Locale.ROOT) : "";
        switch (sub) {
            case "" -> {
                // With the mod installed the bare command opens its screen; otherwise it lists the slots.
                if (modEnabled && modClients.containsKey(player.getUniqueId())) {
                    send(player, new QuickslotProtocol.OpenScreen());
                } else {
                    sendOverview(player, data);
                }
            }
            case "list", "help" -> sendOverview(player, data);
            case "open" -> {
                if (modEnabled && modClients.containsKey(player.getUniqueId())) {
                    send(player, new QuickslotProtocol.OpenScreen());
                } else {
                    chat(player, "quickslots.mod-required", NamedTextColor.YELLOW);
                }
            }
            case "set" -> commandSet(player, data, args);
            case "clear" -> commandClear(player, data, args);
            case "use" -> {
                final int slot = args.length > 2 ? QuickslotLogic.parseSlot(args[2], slotCount) : -1;
                if (slot < 0) {
                    chat(player, "quickslots.invalid-slot", NamedTextColor.RED, "%max%", Integer.toString(slotCount));
                } else {
                    switchTo(player, slot, Source.COMMAND);
                }
            }
            case "next" -> cycle(player, 1, Source.COMMAND);
            case "prev", "previous" -> cycle(player, -1, Source.COMMAND);
            case "off", "away" -> putAway(player, Source.COMMAND);
            case "scroll" -> commandScroll(player, data, args);
            default -> {
                final int slot = QuickslotLogic.parseSlot(sub, slotCount);
                if (slot >= 0) {
                    switchTo(player, slot, Source.COMMAND);
                } else {
                    sendOverview(player, data);
                }
            }
        }
    }

    private void commandSet(final Player player, final PlayerPetData data, final String[] args) {
        if (args.length < 3) {
            chat(player, "quickslots.usage-set", NamedTextColor.YELLOW);
            return;
        }
        final int slot = QuickslotLogic.parseSlot(args[2], slotCount);
        if (slot < 0) {
            chat(player, "quickslots.invalid-slot", NamedTextColor.RED, "%max%", Integer.toString(slotCount));
            return;
        }
        final String petId;
        if (args.length > 3) {
            final String query = String.join(" ", Arrays.copyOfRange(args, 3, args.length));
            petId = resolveOwnedPet(data, query);
            if (petId == null) {
                chat(player, "quickslots.not-owned", NamedTextColor.RED, "%pet%", query);
                return;
            }
        } else {
            // No pet named: take the one that is out right now.
            final OwnedPet active = data.activePet().orElse(null);
            if (active == null) {
                chat(player, "quickslots.usage-set", NamedTextColor.YELLOW);
                return;
            }
            petId = active.definitionId();
        }
        final String refusal = assign(player, data, slot, petId);
        if (refusal != null) {
            chat(player, refusal, NamedTextColor.RED, "%pet%", typeName(petId), "%max%", Integer.toString(slotCount));
            return;
        }
        chat(player, "quickslots.set", NamedTextColor.GREEN, "%slot%", Integer.toString(slot + 1), "%pet%", typeName(petId));
        pushState(player);
    }

    private void commandClear(final Player player, final PlayerPetData data, final String[] args) {
        if (args.length < 3) {
            chat(player, "quickslots.usage-clear", NamedTextColor.YELLOW);
            return;
        }
        if (args[2].equalsIgnoreCase("all")) {
            data.clearQuickslots();
            plugin.requestSave();
            refreshArming(player);
            chat(player, "quickslots.cleared-all", NamedTextColor.GREEN);
            pushState(player);
            return;
        }
        final int slot = QuickslotLogic.parseSlot(args[2], slotCount);
        if (slot < 0) {
            chat(player, "quickslots.invalid-slot", NamedTextColor.RED, "%max%", Integer.toString(slotCount));
            return;
        }
        clear(player, data, slot);
        chat(player, "quickslots.cleared", NamedTextColor.GREEN, "%slot%", Integer.toString(slot + 1));
        pushState(player);
    }

    private void commandScroll(final Player player, final PlayerPetData data, final String[] args) {
        if (!sneakScrollEnabled || !scrollHookActive) {
            chat(player, "quickslots.scroll-unavailable", NamedTextColor.RED);
            return;
        }
        final boolean target;
        if (args.length > 2) {
            final String value = args[2].toLowerCase(Locale.ROOT);
            if (!value.equals("on") && !value.equals("off")) {
                chat(player, "quickslots.usage-scroll", NamedTextColor.YELLOW);
                return;
            }
            target = value.equals("on");
        } else {
            target = !scrollOn(data);
        }
        data.setQuickScroll(target);
        plugin.requestSave();
        refreshArming(player);
        chat(player, target ? "quickslots.scroll-on" : "quickslots.scroll-off", target ? NamedTextColor.GREEN : NamedTextColor.YELLOW);
    }

    /** Resolves what a player typed to the definition id of a pet they own: type name/id/alias, then nickname. */
    private String resolveOwnedPet(final PlayerPetData data, final String query) {
        final String byType = plugin.petDefinitions().find(query).map(PetDefinition::id).orElse(null);
        if (byType != null && data.findByDefinition(byType).isPresent()) {
            return byType;
        }
        for (final OwnedPet pet : data.pets()) {
            if (pet.hasCustomName() && pet.customName().equalsIgnoreCase(query)) {
                return pet.definitionId();
            }
        }
        return null;
    }

    private void sendOverview(final Player player, final PlayerPetData data) {
        final LangManager lang = plugin.lang();
        player.sendMessage(Component.empty());
        player.sendMessage(lang.colored("quickslots.list-header", NamedTextColor.GOLD).decorate(TextDecoration.BOLD));
        for (int slot = 0; slot < slotCount; slot++) {
            Component line = Component.text("  " + (slot + 1) + "  ", NamedTextColor.YELLOW);
            final String petId = data.quickslot(slot);
            if (petId == null) {
                line = line.append(lang.colored("quickslots.list-empty", NamedTextColor.DARK_GRAY));
            } else {
                final PetDefinition definition = plugin.petDefinitions().get(petId).orElse(null);
                final OwnedPet pet = data.findByDefinition(petId).orElse(null);
                if (pet == null) {
                    line = line.append(Component.text(typeName(petId) + " ", NamedTextColor.DARK_GRAY))
                        .append(lang.colored("quickslots.list-missing", NamedTextColor.DARK_GRAY));
                } else {
                    line = line.append(Component.text(displayName(pet) + " ",
                            definition == null ? NamedTextColor.WHITE : definition.rarityColor()))
                        .append(Component.text("[Lvl " + pet.level() + "]", NamedTextColor.GRAY));
                    if (plugin.isPetDisabled(petId)) {
                        line = line.append(Component.text(" ")).append(lang.colored("quickslots.list-disabled", NamedTextColor.RED));
                    } else if (pet.uuid().equals(data.activePetId())) {
                        line = line.append(Component.text(" ")).append(lang.colored("quickslots.list-active", NamedTextColor.GREEN));
                    }
                }
            }
            player.sendMessage(line);
        }
        usageLine(player, "/pets quick set <slot> [pet]", "quickslots.help-set");
        usageLine(player, "/pets quick clear <slot|all>", "quickslots.help-clear");
        if (commandsEnabled) {
            usageLine(player, "/pets quick <slot> | next | prev | off", "quickslots.help-switch");
        }
        if (sneakScrollEnabled && scrollHookActive) {
            usageLine(player, "/pets quick scroll [on|off]",
                scrollOn(data) ? "quickslots.help-scroll-on" : "quickslots.help-scroll-off");
        }
        if (modEnabled && !modClients.containsKey(player.getUniqueId())) {
            player.sendMessage(lang.colored("quickslots.mod-hint", NamedTextColor.DARK_GRAY));
        }
        player.sendMessage(Component.empty());
    }

    private void usageLine(final Player player, final String command, final String descriptionKey) {
        player.sendMessage(Component.text("  ", NamedTextColor.DARK_GRAY)
            .append(Component.text(command, NamedTextColor.YELLOW))
            .append(Component.text("  -  ", NamedTextColor.DARK_GRAY))
            .append(plugin.lang().colored(descriptionKey, NamedTextColor.GRAY)));
    }

    /** Tab completion for {@code /pets quick ...}; {@code args[0]} is the sub-command name itself. */
    List<String> suggest(final CommandSender sender, final String[] args) {
        if (!enabled || !plugin.has(sender, PERMISSION)) {
            return List.of();
        }
        if (args.length == 2) {
            final List<String> out = new ArrayList<>(List.of("list", "set", "clear"));
            if (commandsEnabled) {
                out.addAll(List.of("next", "prev", "off"));
                out.addAll(slotNumbers());
            }
            if (sneakScrollEnabled && scrollHookActive) {
                out.add("scroll");
            }
            return out;
        }
        final String sub = args[1].toLowerCase(Locale.ROOT);
        if (args.length == 3) {
            return switch (sub) {
                case "set", "use" -> slotNumbers();
                case "clear" -> {
                    final List<String> out = new ArrayList<>(slotNumbers());
                    out.add("all");
                    yield out;
                }
                case "scroll" -> List.of("on", "off");
                default -> List.of();
            };
        }
        if (args.length == 4 && sub.equals("set") && sender instanceof Player player) {
            final List<String> out = new ArrayList<>();
            for (final OwnedPet pet : data(player).pets()) {
                if (!plugin.isPetDisabled(pet.definitionId()) && !out.contains(pet.definitionId())) {
                    out.add(pet.definitionId());
                }
            }
            return out;
        }
        return List.of();
    }

    private List<String> slotNumbers() {
        final List<String> numbers = new ArrayList<>(slotCount);
        for (int slot = 1; slot <= slotCount; slot++) {
            numbers.add(Integer.toString(slot));
        }
        return numbers;
    }

    // ------------------------------------------------------------------------------------------------
    // Sneak + scroll (needs the PacketEvents hook to see hotbar-change packets)
    // ------------------------------------------------------------------------------------------------

    @Override
    public void setScrollHookActive(final boolean active) {
        scrollHookActive = active;
        if (!active) {
            armedSlots.clear();
            lastIntercept.clear();
        }
    }

    // MONITOR + ignoreCancelled: the plugin itself cancels the sneak that dismounts a ridden pet, and that
    // sneak must not arm the wheel.
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onSneak(final PlayerToggleSneakEvent event) {
        final Player player = event.getPlayer();
        if (event.isSneaking() && scrollUsableBy(player)) {
            armedSlots.put(player.getUniqueId(), player.getInventory().getHeldItemSlot());
        } else {
            armedSlots.remove(player.getUniqueId());
        }
    }

    @EventHandler
    public void onQuit(final PlayerQuitEvent event) {
        final UUID id = event.getPlayer().getUniqueId();
        armedSlots.remove(id);
        lastIntercept.remove(id);
        lastSwitch.remove(id);
        modClients.remove(id);
    }

    /** Re-evaluates arming for a player who is sneaking right now (after their slots or preference changed). */
    private void refreshArming(final Player player) {
        if (player.isSneaking() && scrollUsableBy(player)) {
            armedSlots.putIfAbsent(player.getUniqueId(), player.getInventory().getHeldItemSlot());
        } else {
            armedSlots.remove(player.getUniqueId());
        }
    }

    /**
     * Whether the wheel should switch pets for this player while they sneak. Only players who filled at
     * least one quickslot are ever affected, so nobody loses sneak+scroll on the hotbar by surprise.
     */
    private boolean scrollUsableBy(final Player player) {
        if (!enabled || !sneakScrollEnabled || !scrollHookActive || !plugin.has(player, PERMISSION)) {
            return false;
        }
        final PlayerPetData data = data(player);
        if (!scrollOn(data)) {
            return false;
        }
        for (int slot = 0; slot < slotCount; slot++) {
            if (data.quickslot(slot) != null) {
                return true;
            }
        }
        return false;
    }

    private boolean scrollOn(final PlayerPetData data) {
        return data.quickScroll() == null ? scrollDefaultOn : data.quickScroll();
    }

    @Override
    public int interceptHeldSlot(final UUID playerId, final int newSlot) {
        final Integer heldSlot = armedSlots.get(playerId);
        if (heldSlot == null) {
            return -1;
        }
        final long now = System.currentTimeMillis();
        final int direction = QuickslotLogic.scrollDirection(heldSlot, newSlot);
        if (direction == 0) {
            // Several notches inside one client tick arrive as a single bigger jump. Right after a
            // swallowed notch that is still the same wheel gesture, so it is swallowed too (without
            // switching again) instead of suddenly moving the hotbar.
            final Long last = lastIntercept.get(playerId);
            if (newSlot != heldSlot && last != null && now - last < SCROLL_GESTURE_MILLIS) {
                return heldSlot;
            }
            // A number key or a real jump: a normal hotbar change. Remember where the player is now.
            armedSlots.replace(playerId, newSlot);
            return -1;
        }
        lastIntercept.put(playerId, now);
        try {
            Bukkit.getScheduler().runTask(plugin, () -> {
                final Player player = Bukkit.getPlayer(playerId);
                if (player != null) {
                    cycle(player, direction, Source.SCROLL);
                }
            });
        } catch (final RuntimeException pluginDisabling) {
            return -1;
        }
        return heldSlot;
    }

    // ------------------------------------------------------------------------------------------------
    // Fabric mod (plugin-message channel)
    // ------------------------------------------------------------------------------------------------

    @Override
    public void onPluginMessageReceived(final String channel, final Player player, final byte[] message) {
        if (!QuickslotProtocol.CHANNEL.equals(channel)) {
            return;
        }
        final long now = System.currentTimeMillis();
        final ModClient client = modClients.computeIfAbsent(player.getUniqueId(), id -> new ModClient());
        if (now - client.windowStartMillis >= 1000L) {
            client.windowStartMillis = now;
            client.windowCount = 0;
        }
        if (++client.windowCount > MAX_MESSAGES_PER_SECOND) {
            return;
        }
        final QuickslotProtocol.ClientMessage decoded;
        try {
            decoded = QuickslotProtocol.decodeClient(message);
        } catch (final IOException | RuntimeException malformed) {
            plugin.debug("Ignored a malformed quickslot message from " + player.getName() + ": " + malformed.getMessage());
            return;
        }
        if (decoded == null) {
            return;
        }
        final boolean usable = enabled && modEnabled && plugin.has(player, PERMISSION);
        switch (decoded) {
            case QuickslotProtocol.Hello hello ->
                plugin.debug(player.getName() + " joined with the quickslot mod (protocol " + hello.version() + ").");
            case QuickslotProtocol.RequestPets ignored -> {
                if (now - client.lastRequestMillis < REQUEST_MIN_INTERVAL_MILLIS) {
                    return;
                }
                client.lastRequestMillis = now;
                client.petsSent = false;
            }
            case QuickslotProtocol.Assign assign -> {
                if (usable) {
                    final PlayerPetData data = data(player);
                    if (assign.petId().isBlank()) {
                        if (assign.slot() >= 0 && assign.slot() < slotCount) {
                            clear(player, data, assign.slot());
                        }
                    } else {
                        final String refusal = assign(player, data, assign.slot(), assign.petId());
                        if (refusal != null) {
                            actionBar(player, refusal, NamedTextColor.RED,
                                "%pet%", typeName(assign.petId()), "%max%", Integer.toString(slotCount));
                        }
                    }
                }
            }
            case QuickslotProtocol.Switch target -> switchTo(player, target.slot(), Source.MOD);
            case QuickslotProtocol.Cycle step -> cycle(player, step.direction(), Source.MOD);
            case QuickslotProtocol.Despawn ignored -> putAway(player, Source.MOD);
        }
        // Always answer with the authoritative state, so the client corrects itself after a refusal too.
        sync(player, client, true);
    }

    /** Tells a mod client that the main chest menu is about to open, so it can add its button to it. */
    void notifyMenuOpened(final Player player) {
        if (enabled && modEnabled && modClients.containsKey(player.getUniqueId()) && plugin.has(player, PERMISSION)) {
            send(player, new QuickslotProtocol.MenuOpened());
        }
    }

    private void pushState(final Player player) {
        final ModClient client = modClients.get(player.getUniqueId());
        if (client != null) {
            sync(player, client, true);
        }
    }

    private void syncAll() {
        modClients.entrySet().removeIf(entry -> Bukkit.getPlayer(entry.getKey()) == null);
        for (final Map.Entry<UUID, ModClient> entry : modClients.entrySet()) {
            final Player player = Bukkit.getPlayer(entry.getKey());
            if (player != null) {
                sync(player, entry.getValue(), false);
            }
        }
    }

    /** Sends the pet list and/or state to a mod client - only what changed, unless {@code force}d. */
    private void sync(final Player player, final ModClient client, final boolean force) {
        // Until the client has opened the channel, plugin messages to it are dropped; try again later.
        if (!player.getListeningPluginChannels().contains(QuickslotProtocol.CHANNEL)) {
            return;
        }
        final PlayerPetData data = data(player);
        final List<QuickslotProtocol.Pet> pets = buildPets(data);
        // The list's hash stands in for a revision counter: it changes exactly when the list would look
        // different to the client (level-up, rename, new pet, language switch, ...).
        final int revision = pets.hashCode();
        if (!client.petsSent || client.petsRevision != revision) {
            send(player, new QuickslotProtocol.Pets(revision, pets));
            client.petsSent = true;
            client.petsRevision = revision;
        }
        final QuickslotProtocol.State state = buildState(player, data, revision);
        final int hash = state.hashCode();
        if (force || !client.stateSent || client.stateHash != hash) {
            send(player, state);
            client.stateSent = true;
            client.stateHash = hash;
        }
    }

    private QuickslotProtocol.State buildState(final Player player, final PlayerPetData data, final int petsRevision) {
        final List<String> slots = new ArrayList<>(slotCount);
        for (int slot = 0; slot < slotCount; slot++) {
            final String petId = data.quickslot(slot);
            slots.add(petId == null ? "" : petId);
        }
        return new QuickslotProtocol.State(
            QuickslotProtocol.VERSION,
            enabled && modEnabled && plugin.has(player, PERMISSION),
            slots,
            data.activePet().map(OwnedPet::definitionId).orElse(""),
            (int) Math.min(Integer.MAX_VALUE, cooldownMillis),
            sameSlotPutsAway,
            petsRevision);
    }

    private List<QuickslotProtocol.Pet> buildPets(final PlayerPetData data) {
        final List<QuickslotProtocol.Pet> pets = new ArrayList<>(data.pets().size());
        for (final OwnedPet pet : data.pets()) {
            final PetDefinition definition = plugin.petDefinitions().get(pet.definitionId()).orElse(null);
            if (definition == null) {
                continue;
            }
            final String texture = definition.textureFor(pet.level(), pet.variant());
            pets.add(new QuickslotProtocol.Pet(
                definition.id(),
                displayName(pet),
                definition.name(),
                definition.rarity(),
                definition.rarityColor().value(),
                pet.level(),
                pet.stars(),
                plugin.isPetDisabled(definition.id()),
                texture == null ? "" : texture,
                plugin.abilitySummary(definition.id()) + "\n" + plugin.abilityValue(definition.id(), pet.level())));
        }
        return pets;
    }

    private void send(final Player player, final QuickslotProtocol.ServerMessage message) {
        try {
            player.sendPluginMessage(plugin, QuickslotProtocol.CHANNEL, QuickslotProtocol.encode(message));
        } catch (final RuntimeException exception) {
            plugin.debug("Could not send a quickslot message to " + player.getName() + ": " + exception.getMessage());
        }
    }

    // ------------------------------------------------------------------------------------------------

    private PlayerPetData data(final Player player) {
        return plugin.petStorage().data(player.getUniqueId());
    }

    /** The pet's own name: its nickname if the player gave it one, else the type name. */
    private String displayName(final OwnedPet pet) {
        return pet.hasCustomName() ? pet.customName() : typeName(pet.definitionId());
    }

    private String typeName(final String petId) {
        return plugin.petDefinitions().get(petId).map(PetDefinition::name).orElse(petId);
    }

    private void actionBar(final Player player, final String key, final NamedTextColor color, final String... replacements) {
        player.sendActionBar(plugin.lang().colored(key, color, replacements));
    }

    private void chat(final Player player, final String key, final NamedTextColor color, final String... replacements) {
        player.sendMessage(plugin.lang().colored(key, color, replacements));
    }
}
