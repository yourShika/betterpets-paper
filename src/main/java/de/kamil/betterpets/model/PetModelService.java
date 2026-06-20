package de.kamil.betterpets.model;

import de.kamil.betterpets.PetDefinition;
import org.bukkit.Bukkit;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Entity;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.IOException;
import java.lang.reflect.Constructor;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

public final class PetModelService {
    private static final Pattern ANIMATION_NAME = Pattern.compile("\"name\"\\s*:\\s*\"([^\"]+)\"");

    private final JavaPlugin plugin;
    private final File localModelFolder;
    private final Map<String, Path> localModels = new HashMap<>();
    private final Map<String, Set<String>> modelAnimations = new HashMap<>();
    private PetModelBridge bridge;

    public PetModelService(final JavaPlugin plugin) {
        this.plugin = plugin;
        this.localModelFolder = new File(plugin.getDataFolder(), "models");
    }

    public boolean enable() {
        if (!plugin.getServer().getPluginManager().isPluginEnabled("BetterModel")) {
            bridge = null;
            return false;
        }
        bridge = createBridge().orElse(null);
        if (bridge == null) {
            return false;
        }
        reloadModels();
        return true;
    }

    public void disable() {
        bridge = null;
        localModels.clear();
    }

    public boolean isEnabled() {
        return bridge != null;
    }

    public boolean reloadModels() {
        scanLocalModels();
        if (bridge == null) {
            return false;
        }
        // BetterModel builds the client resource pack from these .bbmodel files.
        // Vanilla clients still need BetterModel's auto-send/host setting enabled.
        copyModelsToBetterModel();
        boolean apiReloaded = false;
        try {
            apiReloaded = bridge.reload();
        } catch (final RuntimeException | LinkageError exception) {
            plugin.getLogger().warning("BetterModel API reload threw an exception: " + exception.getMessage());
        }
        if (apiReloaded) {
            plugin.getLogger().info("BetterModel reloaded through API.");
            return true;
        }
        plugin.getLogger().warning("BetterModel API reload failed; falling back to console command 'bettermodel reload'.");
        Bukkit.dispatchCommand(Bukkit.getConsoleSender(), "bettermodel reload");
        return false;
    }

    public Optional<String> modelName(final PetDefinition definition) {
        final Optional<String> override = overrideModelName(definition);
        if (override.isPresent()) {
            return override;
        }
        return Optional.of(normalizeModelName(definition.id()));
    }

    public boolean canRender(final PetDefinition definition) {
        if (bridge == null) {
            return false;
        }
        try {
            return modelName(definition)
                .filter(name -> bridge.modelExists(name))
                .isPresent();
        } catch (final RuntimeException | LinkageError exception) {
            plugin.getLogger().warning("BetterModel model lookup failed: " + exception.getMessage());
            return false;
        }
    }

    public Optional<PetModelHandle> render(final PetDefinition definition, final Entity baseEntity) {
        if (bridge == null) {
            return Optional.empty();
        }
        final Optional<String> modelName = modelName(definition);
        if (modelName.isEmpty()) {
            return Optional.empty();
        }
        try {
            if (!bridge.modelExists(modelName.get())) {
                return Optional.empty();
            }
            return bridge.attachModel(modelName.get(), baseEntity);
        } catch (final RuntimeException | LinkageError exception) {
            plugin.getLogger().warning("BetterModel tracker creation failed for " + modelName.get() + ": " + exception.getMessage());
            return Optional.empty();
        }
    }

    private Optional<PetModelBridge> createBridge() {
        try {
            final Class<?> hookClass = Class.forName("de.kamil.betterpets.model.BetterModelHook", true, getClass().getClassLoader());
            final Constructor<?> constructor = hookClass.getDeclaredConstructor();
            final Object instance = constructor.newInstance();
            if (instance instanceof PetModelBridge modelBridge) {
                return Optional.of(modelBridge);
            }
        } catch (final ReflectiveOperationException | LinkageError exception) {
            plugin.getLogger().severe("BetterModel hook could not be loaded: " + exception.getMessage());
        }
        return Optional.empty();
    }

    private void scanLocalModels() {
        localModels.clear();
        modelAnimations.clear();
        if (!localModelFolder.exists() && !localModelFolder.mkdirs()) {
            plugin.getLogger().warning("Could not create model folder: " + localModelFolder.getAbsolutePath());
            return;
        }
        try (Stream<Path> stream = Files.walk(localModelFolder.toPath(), 1)) {
            stream
                .filter(Files::isRegularFile)
                .filter(path -> path.getFileName().toString().toLowerCase(Locale.ROOT).endsWith(".bbmodel"))
                .sorted(Comparator.comparing(path -> path.getFileName().toString()))
                .forEach(path -> {
                    final String name = modelNameFromFile(path);
                    localModels.put(name, path);
                    modelAnimations.put(name, parseAnimationNames(path));
                });
        } catch (final IOException exception) {
            plugin.getLogger().warning("Could not scan Better Pets model folder: " + exception.getMessage());
        }
        plugin.getLogger().info("Scanned " + localModels.size() + " Better Pets model file(s).");
    }

    /**
     * Reads the animation names declared inside a .bbmodel (the file is JSON). Used to decide whether
     * a pet model is grounded (has a "walking" animation) or flying (has a "flying" animation), and to
     * pick idle / idle2-9 variants. Done by scanning the local file so it needs no BetterModel API.
     */
    private Set<String> parseAnimationNames(final Path path) {
        final Set<String> names = new HashSet<>();
        try {
            final String text = Files.readString(path, StandardCharsets.UTF_8);
            final int index = text.indexOf("\"animations\"");
            if (index < 0) {
                return names;
            }
            final Matcher matcher = ANIMATION_NAME.matcher(text.substring(index));
            while (matcher.find()) {
                names.add(matcher.group(1).toLowerCase(Locale.ROOT));
            }
        } catch (final IOException | RuntimeException exception) {
            plugin.getLogger().warning("Could not read animations from " + path.getFileName() + ": " + exception.getMessage());
        }
        return names;
    }

    /** Animation names available for the given (already normalized) model name. */
    public Set<String> animations(final String modelName) {
        if (modelName == null) {
            return Set.of();
        }
        return modelAnimations.getOrDefault(modelName, Set.of());
    }

    /** A model is grounded when it ships a "walking" animation but no "flying" animation. */
    public boolean isGroundedModel(final String modelName) {
        final Set<String> available = animations(modelName);
        return available.contains("walking") && !available.contains("flying");
    }

    private void copyModelsToBetterModel() {
        if (bridge == null) {
            return;
        }
        final Path targetFolder = bridge.dataFolder().toPath().resolve("models");
        try {
            Files.createDirectories(targetFolder);
            int copied = 0;
            for (final Map.Entry<String, Path> entry : localModels.entrySet()) {
                final Path target = targetFolder.resolve(entry.getKey() + ".bbmodel");
                if (shouldCopy(entry.getValue(), target)) {
                    Files.copy(entry.getValue(), target, StandardCopyOption.REPLACE_EXISTING);
                    copied++;
                }
            }
            plugin.getLogger().info("Synced " + copied + " Better Pets .bbmodel file(s) into BetterModel.");
        } catch (final IOException exception) {
            plugin.getLogger().severe("Could not sync Better Pets models into BetterModel: " + exception.getMessage());
        }
    }

    private boolean shouldCopy(final Path source, final Path target) throws IOException {
        if (!Files.exists(target)) {
            return true;
        }
        return Files.size(source) != Files.size(target)
            || Files.getLastModifiedTime(source).toMillis() > Files.getLastModifiedTime(target).toMillis();
    }

    private Optional<String> overrideModelName(final PetDefinition definition) {
        final ConfigurationSection section = plugin.getConfig().getConfigurationSection("model-overrides");
        if (section == null) {
            return Optional.empty();
        }
        final String petId = normalizeLookupKey(definition.id());
        final String petName = normalizeLookupKey(definition.name());
        for (final String key : section.getKeys(false)) {
            final String normalizedKey = normalizeLookupKey(key);
            if (normalizedKey.equals(petId) || normalizedKey.equals(petName)) {
                final String value = section.getString(key, "");
                if (value != null && !value.isBlank()) {
                    return Optional.of(normalizeModelName(value));
                }
            }
        }
        return Optional.empty();
    }

    private String modelNameFromFile(final Path path) {
        final String fileName = path.getFileName().toString();
        return normalizeModelName(fileName.substring(0, fileName.length() - ".bbmodel".length()));
    }

    private String normalizeModelName(final String value) {
        String normalized = value.toLowerCase(Locale.ROOT).trim();
        if (normalized.endsWith(".bbmodel")) {
            normalized = normalized.substring(0, normalized.length() - ".bbmodel".length());
        }
        normalized = normalized.replaceAll("[^a-z0-9_.-]", "_");
        return normalized.isBlank() ? "pet_model" : normalized;
    }

    private String normalizeLookupKey(final String value) {
        return value.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]", "");
    }
}
