package de.kamil.betterpets;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.IOException;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.Base64;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

public final class PetStorage {
    private final JavaPlugin plugin;
    private final File file;
    private final Map<UUID, PlayerPetData> players = new HashMap<>();

    public PetStorage(final JavaPlugin plugin) {
        this.plugin = plugin;
        this.file = new File(plugin.getDataFolder(), "pets.yml");
    }

    public void load() {
        players.clear();

        final YamlConfiguration config = loadConfiguration(file);
        final ConfigurationSection root = config.getConfigurationSection("players");
        if (root == null) {
            return;
        }

        int skipped = 0;
        for (final String uuidText : root.getKeys(false)) {
            try {
                final UUID playerId = UUID.fromString(uuidText);
                final ConfigurationSection section = root.getConfigurationSection(uuidText);
                if (section == null) {
                    continue;
                }

                final PlayerPetData data = new PlayerPetData();
                data.setVisible(section.getBoolean("visible", true));
                data.setBroadcastsMuted(section.getBoolean("broadcasts-muted", false));
                data.setTokens(section.getInt("tokens", 0));
                data.setSlotFeaturedPet(section.getString("slot-featured-pet", null));
                data.setBooster(section.getInt("booster-tier", 0), section.getLong("booster-remaining-millis", 0L));
                final String activeText = section.getString("active");
                if (activeText != null && !activeText.isBlank()) {
                    data.setActivePet(UUID.fromString(activeText));
                }

                final ConfigurationSection pets = section.getConfigurationSection("pets");
                if (pets != null) {
                    for (final String petUuidText : pets.getKeys(false)) {
                        try {
                            final ConfigurationSection pet = pets.getConfigurationSection(petUuidText);
                            if (pet == null) {
                                continue;
                            }
                            final String definition = pet.getString("id", "");
                            if (definition.isBlank()) {
                                continue;
                            }
                            final OwnedPet owned = new OwnedPet(
                                UUID.fromString(petUuidText),
                                definition,
                                pet.getInt("level", 1),
                                pet.getInt("exp", 0),
                                pet.getInt("next-exp", 16),
                                pet.getLong("last-totem", 0L),
                                storageContents(pet)
                            );
                            owned.setCustomName(pet.getString("name", null));
                            data.pets().add(owned);
                        } catch (final RuntimeException petException) {
                            // One broken pet must not drop the whole player's data.
                            plugin.getLogger().warning("Skipping corrupted pet " + petUuidText + " for " + uuidText + ": " + petException.getMessage());
                        }
                    }
                }

                if (data.activePet().isEmpty()) {
                    data.setActivePet(null);
                }
                players.put(playerId, data);
            } catch (final RuntimeException exception) {
                skipped++;
                plugin.getLogger().warning("Skipping corrupted Better Pets entry " + uuidText + ": " + exception.getMessage());
            }
        }
        if (skipped > 0) {
            plugin.getLogger().warning("Skipped " + skipped + " corrupted player entr(ies) while loading pet data.");
        }
    }

    public PlayerPetData data(final UUID playerId) {
        return players.computeIfAbsent(playerId, ignored -> new PlayerPetData());
    }

    public Collection<Map.Entry<UUID, PlayerPetData>> entries() {
        return players.entrySet();
    }

    public int playerCount() {
        return players.size();
    }

    public Optional<OwnedPet> activePet(final UUID playerId) {
        return Optional.ofNullable(players.get(playerId)).flatMap(PlayerPetData::activePet);
    }

    public void save() {
        if (!plugin.getDataFolder().exists() && !plugin.getDataFolder().mkdirs()) {
            plugin.getLogger().warning("Could not create Better Pets data folder.");
            return;
        }

        final YamlConfiguration config = new YamlConfiguration();
        for (final Map.Entry<UUID, PlayerPetData> entry : players.entrySet()) {
            final String base = "players." + entry.getKey();
            final PlayerPetData data = entry.getValue();
            config.set(base + ".visible", data.visible());
            config.set(base + ".broadcasts-muted", data.broadcastsMuted());
            config.set(base + ".tokens", data.tokens());
            config.set(base + ".slot-featured-pet", data.slotFeaturedPet());
            config.set(base + ".active", data.activePetId() == null ? null : data.activePetId().toString());
            config.set(base + ".booster-tier", data.boosterTier());
            config.set(base + ".booster-remaining-millis", data.boosterRemainingMillis());

            for (final OwnedPet pet : data.pets()) {
                final String petPath = base + ".pets." + pet.uuid();
                config.set(petPath + ".id", pet.definitionId());
                config.set(petPath + ".level", pet.level());
                config.set(petPath + ".exp", pet.exp());
                config.set(petPath + ".next-exp", pet.nextLevelExp());
                config.set(petPath + ".last-totem", pet.lastTotemMillis());
                config.set(petPath + ".name", pet.hasCustomName() ? pet.customName() : null);
                config.set(petPath + ".storage-bytes", Base64.getEncoder().encodeToString(ItemStack.serializeItemsAsBytes(serializableStorageContents(pet))));
                config.set(petPath + ".storage", null);
            }
        }

        final File tmp = new File(file.getParentFile(), file.getName() + ".tmp");
        final File backup = new File(file.getParentFile(), file.getName() + ".bak");
        try {
            config.save(tmp);
            if (file.exists()) {
                Files.copy(file.toPath(), backup.toPath(), StandardCopyOption.REPLACE_EXISTING);
            }
            try {
                Files.move(tmp.toPath(), file.toPath(), StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
            } catch (final AtomicMoveNotSupportedException ignored) {
                Files.move(tmp.toPath(), file.toPath(), StandardCopyOption.REPLACE_EXISTING);
            }
            dailyBackup();
        } catch (final IOException exception) {
            plugin.getLogger().severe("Could not save Better Pets data: " + exception.getMessage());
        }
    }

    /**
     * Keeps one dated copy of pets.yml per day under plugins/BetterPets/backups, pruning old ones.
     * Controlled by storage.backup.enabled and storage.backup.daily in config.yml.
     */
    private void dailyBackup() {
        if (!plugin.getConfig().getBoolean("storage.backup.enabled", true)
            || !plugin.getConfig().getBoolean("storage.backup.daily", true)
            || !file.exists()) {
            return;
        }
        try {
            final File backupDir = new File(plugin.getDataFolder(), "backups");
            if (!backupDir.exists() && !backupDir.mkdirs()) {
                return;
            }
            final File target = new File(backupDir, "pets-" + java.time.LocalDate.now() + ".yml");
            if (target.exists()) {
                return;
            }
            Files.copy(file.toPath(), target.toPath(), StandardCopyOption.REPLACE_EXISTING);
            plugin.getLogger().info("Created daily Better Pets backup: " + target.getName());
            pruneOldBackups(backupDir);
        } catch (final IOException ignored) {
            // A failed backup must never block saving.
        }
    }

    private void pruneOldBackups(final File backupDir) {
        final int keep = Math.max(1, plugin.getConfig().getInt("storage.backup.keep-days", 14));
        final File[] backups = backupDir.listFiles((dir, name) -> name.startsWith("pets-") && name.endsWith(".yml"));
        if (backups == null || backups.length <= keep) {
            return;
        }
        java.util.Arrays.sort(backups, java.util.Comparator.comparing(File::getName));
        for (int i = 0; i < backups.length - keep; i++) {
            if (!backups[i].delete()) {
                plugin.getLogger().warning("Could not delete old backup " + backups[i].getName());
            }
        }
    }

    private YamlConfiguration loadConfiguration(final File source) {
        if (source.exists()) {
            try {
                final YamlConfiguration config = new YamlConfiguration();
                config.load(source);
                return config;
            } catch (final IOException | org.bukkit.configuration.InvalidConfigurationException exception) {
                plugin.getLogger().severe("pets.yml is corrupted (" + exception.getMessage() + "); quarantining it and trying the backup.");
                quarantine(source);
            }
        }

        final File backup = new File(source.getParentFile(), source.getName() + ".bak");
        if (backup.exists()) {
            try {
                final YamlConfiguration config = new YamlConfiguration();
                config.load(backup);
                plugin.getLogger().warning("Loaded Better Pets data from backup pets.yml.bak.");
                return config;
            } catch (final IOException | org.bukkit.configuration.InvalidConfigurationException exception) {
                plugin.getLogger().severe("Backup pets.yml.bak is also corrupted: " + exception.getMessage());
            }
        }

        return new YamlConfiguration();
    }

    private void quarantine(final File source) {
        try {
            final File quarantined = new File(source.getParentFile(), source.getName() + ".corrupt-" + System.currentTimeMillis());
            Files.copy(source.toPath(), quarantined.toPath(), StandardCopyOption.REPLACE_EXISTING);
            plugin.getLogger().severe("A copy of the corrupted data was kept as " + quarantined.getName() + " so nothing is lost.");
        } catch (final IOException ignored) {
            // best effort
        }
    }

    private ItemStack[] storageContents(final ConfigurationSection pet) {
        final String encoded = pet.getString("storage-bytes");
        if (encoded != null && !encoded.isBlank()) {
            try {
                final ItemStack[] stored = ItemStack.deserializeItemsFromBytes(Base64.getDecoder().decode(encoded));
                final ItemStack[] contents = new ItemStack[OwnedPet.STORAGE_SIZE];
                for (int i = 0; i < Math.min(contents.length, stored.length); i++) {
                    if (stored[i] != null && !stored[i].getType().isAir()) {
                        contents[i] = stored[i].clone();
                    }
                }
                return contents;
            } catch (final IllegalArgumentException exception) {
                plugin.getLogger().warning("Could not read Alpaca storage bytes for " + pet.getCurrentPath() + ", trying legacy storage list.");
            }
        }

        final ItemStack[] contents = new ItemStack[OwnedPet.STORAGE_SIZE];
        final List<?> storedItems = pet.getList("storage", List.of());
        for (int i = 0; i < Math.min(contents.length, storedItems.size()); i++) {
            final Object item = storedItems.get(i);
            if (item instanceof ItemStack stack) {
                contents[i] = stack.clone();
            }
        }
        return contents;
    }

    private ItemStack[] serializableStorageContents(final OwnedPet pet) {
        final ItemStack[] contents = pet.storageContents();
        for (int i = 0; i < contents.length; i++) {
            if (contents[i] == null || contents[i].getType().isAir()) {
                contents[i] = ItemStack.empty();
            }
        }
        return contents;
    }
}
