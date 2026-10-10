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
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Stream;

public final class PetModelService {
    private final JavaPlugin plugin;
    private final File localModelFolder;
    private final Map<String, Path> localModels = new HashMap<>();
    // model name -> its animations and how long each runs, in ticks
    private final Map<String, Map<String, Integer>> modelAnimations = new HashMap<>();
    private final Map<String, Optional<String>> resolved = new HashMap<>();
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
            validateModels();
            return true;
        }
        plugin.getLogger().warning("BetterModel API reload failed; falling back to console command 'bettermodel reload'.");
        Bukkit.dispatchCommand(Bukkit.getConsoleSender(), "bettermodel reload");
        // Validate a tick later so BetterModel has time to process the console reload.
        Bukkit.getScheduler().runTaskLater(plugin, this::validateModels, 40L);
        return false;
    }

    /**
     * Warns about local .bbmodel files that BetterModel did not actually load (e.g. invalid models or
     * ones missing the required animations), so the reason for a head fallback is visible in the console.
     */
    private void validateModels() {
        if (bridge == null) {
            return;
        }
        for (final Map.Entry<String, Map<String, Integer>> entry : modelAnimations.entrySet()) {
            final String name = entry.getKey();
            try {
                if (!bridge.modelExists(name)) {
                    plugin.getLogger().warning("Model '" + name + "' was synced to BetterModel but is not loaded. "
                        + "Pets using it will fall back to heads. Check that " + name + ".bbmodel is a valid Blockbench model.");
                    continue;
                }
            } catch (final RuntimeException | LinkageError exception) {
                plugin.getLogger().warning("Could not validate model '" + name + "': " + exception.getMessage());
                continue;
            }
            final Set<String> anims = entry.getValue().keySet();
            if (!anims.contains("idle") && !anims.contains("walking") && !anims.contains("flying")) {
                plugin.getLogger().warning("Model '" + name + "' has no idle/walking/flying animation; "
                    + "it will render but stay static.");
            }
        }
    }

    /**
     * The model for a pet wearing a skin. An entry in model-overrides wins. Otherwise the file is found
     * by name: the pet id, the skin joined with one or two underscores, and optionally the way the model
     * moves at the end - cat__black, allay_happy_flying, tiger__white_grounded. If there is no model for
     * the skin, the pet's plain model stands in; if both a _grounded and a _flying one exist, the
     * model-flying-pets list in config.yml decides.
     */
    public Optional<String> modelName(final PetDefinition definition, final String variant) {
        final Optional<String> override = overrideModelName(definition);
        if (override.isPresent()) {
            return override;
        }
        final String id = normalizeModelName(definition.id());
        final String skin = variant == null || variant.isBlank() ? "" : normalizeModelName(variant);
        return resolved.computeIfAbsent(id + "|" + skin, key -> {
            final boolean flies = plugin.getConfig().getStringList("model-flying-pets").stream()
                .anyMatch(entry -> normalizeModelName(entry).equals(id));
            final String[] endings = flies ? new String[]{"_flying", "", "_grounded"} : new String[]{"_grounded", "", "_flying"};
            if (!skin.isEmpty()) {
                for (final String ending : endings) {
                    for (final String joint : new String[]{"__", "_"}) {
                        if (localModels.containsKey(id + joint + skin + ending)) {
                            return Optional.of(id + joint + skin + ending);
                        }
                    }
                }
            }
            for (final String ending : endings) {
                if (localModels.containsKey(id + ending)) {
                    return Optional.of(id + ending);
                }
            }
            return Optional.empty();
        });
    }

    public boolean canRender(final PetDefinition definition, final String variant) {
        if (bridge == null) {
            return false;
        }
        try {
            return modelName(definition, variant)
                .filter(name -> bridge.modelExists(name))
                .isPresent();
        } catch (final RuntimeException | LinkageError exception) {
            plugin.getLogger().warning("BetterModel model lookup failed: " + exception.getMessage());
            return false;
        }
    }

    public Optional<PetModelHandle> render(final PetDefinition definition, final String variant, final Entity baseEntity) {
        if (bridge == null) {
            return Optional.empty();
        }
        final Optional<String> modelName = modelName(definition, variant);
        if (modelName.isEmpty()) {
            return Optional.empty();
        }
        try {
            if (!bridge.modelExists(modelName.get())) {
                return Optional.empty();
            }
            return bridge.attachModel(modelName.get(), baseEntity, scale(definition, modelName.get()));
        } catch (final RuntimeException | LinkageError exception) {
            plugin.getLogger().warning("BetterModel tracker creation failed for " + modelName.get() + ": " + exception.getMessage());
            return Optional.empty();
        }
    }

    /** model-scale in config.yml: a size for one model, else for the pet, else the default. */
    private float scale(final PetDefinition definition, final String modelName) {
        final double fallback = plugin.getConfig().getDouble("model-scale.default", 1.0);
        final double forPet = plugin.getConfig().getDouble("model-scale.pets." + normalizeModelName(definition.id()), fallback);
        final double value = plugin.getConfig().getDouble("model-scale.models." + modelName, forPet);
        return (float) Math.max(0.1, Math.min(5.0, value));
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
        resolved.clear();
        if (!localModelFolder.exists() && !localModelFolder.mkdirs()) {
            plugin.getLogger().warning("Could not create model folder: " + localModelFolder.getAbsolutePath());
            return;
        }
        final Map<String, String> index = readIndex();
        try (Stream<Path> stream = Files.walk(localModelFolder.toPath(), 1)) {
            stream
                .filter(Files::isRegularFile)
                .filter(path -> path.getFileName().toString().toLowerCase(Locale.ROOT).endsWith(".bbmodel"))
                .sorted(Comparator.comparing(path -> path.getFileName().toString()))
                .forEach(path -> {
                    final String name = modelNameFromFile(path);
                    localModels.put(name, path);
                    modelAnimations.put(name, readAnimations(path, index));
                });
            writeIndex();
        } catch (final IOException exception) {
            plugin.getLogger().warning("Could not scan Better Pets model folder: " + exception.getMessage());
        }
        plugin.getLogger().info("Scanned " + localModels.size() + " Better Pets model file(s).");
    }

    // A model file runs to several megabytes (texture and keyframes), and there may be hundreds. What is
    // needed of each - its animations and their lengths - is kept in a small index next to the models,
    // so that a file is only read again when it has changed.
    private final Map<String, String> indexLines = new HashMap<>();

    private File indexFile() {
        return new File(localModelFolder, "animations.index");
    }

    private Map<String, String> readIndex() {
        final Map<String, String> index = new HashMap<>();
        indexLines.clear();
        try {
            if (indexFile().isFile()) {
                for (final String line : Files.readAllLines(indexFile().toPath(), StandardCharsets.UTF_8)) {
                    final int cut = line.indexOf('=');
                    if (cut > 0) {
                        index.put(line.substring(0, cut), line.substring(cut + 1));
                    }
                }
            }
        } catch (final IOException | RuntimeException exception) {
            plugin.getLogger().warning("Could not read the model index, reading the models themselves: " + exception.getMessage());
        }
        return index;
    }

    private void writeIndex() {
        try {
            final java.util.List<String> lines = new java.util.ArrayList<>();
            indexLines.entrySet().stream().sorted(Map.Entry.comparingByKey())
                .forEach(entry -> lines.add(entry.getKey() + "=" + entry.getValue()));
            Files.write(indexFile().toPath(), lines, StandardCharsets.UTF_8);
        } catch (final IOException | RuntimeException exception) {
            plugin.getLogger().warning("Could not write the model index: " + exception.getMessage());
        }
    }

    /**
     * The animations of a .bbmodel and their lengths in ticks - idle, walking, flying, idle2-9 and whatever
     * gestures the model brings along. From the index if the file is unchanged, else from the file.
     */
    private Map<String, Integer> readAnimations(final Path path, final Map<String, String> index) {
        final String key = path.getFileName().toString();
        String stamp = "";
        try {
            stamp = Files.size(path) + ":" + Files.getLastModifiedTime(path).toMillis();
        } catch (final IOException ignored) {
            // read the file below
        }
        final String known = index.get(key);
        if (known != null && known.startsWith(stamp + ";")) {
            final Map<String, Integer> animations = new HashMap<>();
            for (final String part : known.substring(stamp.length() + 1).split(",")) {
                final int cut = part.lastIndexOf(':');
                if (cut > 0) {
                    try {
                        animations.put(part.substring(0, cut), Integer.parseInt(part.substring(cut + 1)));
                    } catch (final NumberFormatException ignored) {
                        // a damaged entry: left out
                    }
                }
            }
            indexLines.put(key, known);
            return animations;
        }
        final Map<String, Integer> animations = parseAnimations(path);
        final StringBuilder line = new StringBuilder(stamp).append(';');
        animations.forEach((name, ticks) -> line.append(name).append(':').append(ticks).append(','));
        indexLines.put(key, line.toString());
        return animations;
    }

    private Map<String, Integer> parseAnimations(final Path path) {
        final Map<String, Integer> animations = new HashMap<>();
        try (com.google.gson.stream.JsonReader reader = new com.google.gson.stream.JsonReader(Files.newBufferedReader(path, StandardCharsets.UTF_8))) {
            reader.beginObject();
            while (reader.hasNext()) {
                if (!reader.nextName().equals("animations") || reader.peek() != com.google.gson.stream.JsonToken.BEGIN_ARRAY) {
                    reader.skipValue();
                    continue;
                }
                reader.beginArray();
                while (reader.hasNext()) {
                    String name = null;
                    double seconds = 3.0;
                    reader.beginObject();
                    while (reader.hasNext()) {
                        final String field = reader.nextName();
                        if (field.equals("name") && reader.peek() == com.google.gson.stream.JsonToken.STRING) {
                            name = reader.nextString();
                        } else if (field.equals("length") && reader.peek() == com.google.gson.stream.JsonToken.NUMBER) {
                            seconds = reader.nextDouble();
                        } else {
                            reader.skipValue();
                        }
                    }
                    reader.endObject();
                    if (name != null && !name.isBlank()) {
                        animations.put(name.toLowerCase(Locale.ROOT), Math.max(1, (int) Math.round(seconds * 20.0)));
                    }
                }
                reader.endArray();
            }
        } catch (final IOException | RuntimeException exception) {
            plugin.getLogger().warning("Could not read animations from " + path.getFileName() + ": " + exception.getMessage());
        }
        return animations;
    }

    /** Animation names available for the given (already normalized) model name. */
    public Set<String> animations(final String modelName) {
        if (modelName == null) {
            return Set.of();
        }
        final Map<String, Integer> animations = modelAnimations.get(modelName);
        return animations == null ? Set.of() : animations.keySet();
    }

    /** How long an animation of a model runs, in ticks; three seconds if that is not known. */
    public int animationTicks(final String modelName, final String animation) {
        final Map<String, Integer> animations = modelName == null ? null : modelAnimations.get(modelName);
        return animations == null ? 60 : animations.getOrDefault(animation, 60);
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
