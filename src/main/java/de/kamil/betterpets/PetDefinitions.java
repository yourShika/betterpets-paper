package de.kamil.betterpets;

import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Random;

public final class PetDefinitions {
    private final Map<String, PetDefinition> definitions;
    private final int totalWeight;

    private PetDefinitions(final Map<String, PetDefinition> definitions) {
        this.definitions = definitions;
        this.totalWeight = definitions.values().stream().mapToInt(PetDefinition::weight).sum();
    }

    public static PetDefinitions load(final JavaPlugin plugin) {
        final InputStream stream = plugin.getResource("pets.yml");
        if (stream == null) {
            throw new IllegalStateException("Missing pets.yml resource.");
        }

        final YamlConfiguration config = YamlConfiguration.loadConfiguration(new InputStreamReader(stream, StandardCharsets.UTF_8));
        final ConfigurationSection section = config.getConfigurationSection("pets");
        if (section == null) {
            throw new IllegalStateException("pets.yml does not contain a pets section.");
        }

        final Map<String, PetDefinition> loaded = new LinkedHashMap<>();
        for (final String id : section.getKeys(false)) {
            final ConfigurationSection pet = section.getConfigurationSection(id);
            if (pet == null) {
                continue;
            }

            loaded.put(id, new PetDefinition(
                id,
                pet.getString("name", id),
                color(pet.getString("color", "white")),
                pet.getString("rarity", "Common"),
                Math.max(1, pet.getInt("weight", 1)),
                pet.getString("texture", ""),
                List.copyOf(pet.getStringList("lore"))
            ));
        }

        if (loaded.isEmpty()) {
            throw new IllegalStateException("No pets were loaded from pets.yml.");
        }

        return new PetDefinitions(loaded);
    }

    public Collection<PetDefinition> all() {
        return definitions.values();
    }

    public List<PetDefinition> ordered() {
        return new ArrayList<>(definitions.values());
    }

    public Optional<PetDefinition> get(final String id) {
        return Optional.ofNullable(definitions.get(id));
    }

    public Optional<PetDefinition> find(final String input) {
        final String normalized = normalize(input);
        return definitions.values().stream()
            .filter(definition -> normalize(definition.id()).equals(normalized) || normalize(definition.name()).equals(normalized))
            .findFirst();
    }

    public PetDefinition randomWeighted(final Random random) {
        int roll = random.nextInt(Math.max(1, totalWeight));
        for (final PetDefinition definition : definitions.values()) {
            roll -= definition.weight();
            if (roll < 0) {
                return definition;
            }
        }
        return definitions.values().iterator().next();
    }

    public int totalWeight() {
        return totalWeight;
    }

    private static NamedTextColor color(final String color) {
        final String normalized = color.toLowerCase(Locale.ROOT).replace('_', '-');
        final NamedTextColor named = NamedTextColor.NAMES.value(normalized);
        return named == null ? NamedTextColor.WHITE : named;
    }

    private static String normalize(final String value) {
        return value.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]", "");
    }
}
