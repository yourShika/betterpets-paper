package de.kamil.betterpets;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.List;

/**
 * Loads player-facing text from per-language files in plugins/BetterPets/lang/.
 * <p>
 * Upgrade safety: the bundled en/de/pl files are the source of truth for which keys exist. On every
 * load any key that is missing from a server's on-disk language file is merged in from the bundled
 * copy (never overwriting lines the admin already edited), exactly like the config repair does. So a
 * new plugin version that adds strings never leaves a blank message, and a server's translations are
 * preserved across updates. Missing keys ultimately fall back to the bundled English text, then to the
 * raw key, so a message can never come out empty.
 */
public final class LangManager {
    private static final String[] BUNDLED = {"en", "de", "pl"};
    private static final MiniMessage MINI = MiniMessage.miniMessage();

    private final JavaPlugin plugin;
    private YamlConfiguration active;
    private YamlConfiguration englishFallback;
    private String activeCode = "en";

    public LangManager(final JavaPlugin plugin) {
        this.plugin = plugin;
    }

    public String activeCode() {
        return activeCode;
    }

    /** Saves/merges the bundled language files, migrates any old config messages, then loads the active one. */
    public void load() {
        final File langFolder = new File(plugin.getDataFolder(), "lang");
        if (!langFolder.exists() && !langFolder.mkdirs()) {
            plugin.getLogger().warning("Could not create the lang folder: " + langFolder.getAbsolutePath());
        }
        for (final String code : BUNDLED) {
            writeAndMerge(langFolder, code);
        }

        englishFallback = loadBundled("en");
        migrateLegacyConfigMessages(langFolder);

        String requested = plugin.getConfig().getString("language", "en");
        requested = requested == null ? "en" : requested.toLowerCase(java.util.Locale.ROOT).trim();
        final File requestedFile = new File(langFolder, requested + ".yml");
        if (!requestedFile.exists()) {
            plugin.getLogger().warning("Language '" + requested + "' has no lang/" + requested + ".yml; using English.");
            requested = "en";
        }
        activeCode = requested;
        active = YamlConfiguration.loadConfiguration(new File(langFolder, requested + ".yml"));
        plugin.getLogger().info("Loaded language '" + activeCode + "'.");
    }

    /** Writes the bundled file if absent, then merges any keys the on-disk copy is missing. */
    private void writeAndMerge(final File langFolder, final String code) {
        final File target = new File(langFolder, code + ".yml");
        final YamlConfiguration defaults = loadBundled(code);
        if (defaults == null) {
            return;
        }
        if (!target.exists()) {
            try {
                defaults.save(target);
            } catch (final Exception exception) {
                plugin.getLogger().warning("Could not write lang/" + code + ".yml: " + exception.getMessage());
            }
            return;
        }
        final YamlConfiguration current = YamlConfiguration.loadConfiguration(target);
        int added = 0;
        for (final String key : defaults.getKeys(true)) {
            if (defaults.isConfigurationSection(key) || current.contains(key)) {
                continue;
            }
            current.set(key, defaults.get(key));
            added++;
        }
        if (added > 0) {
            try {
                current.save(target);
                plugin.getLogger().info("Merged " + added + " new message(s) into lang/" + code + ".yml.");
            } catch (final Exception exception) {
                plugin.getLogger().warning("Could not update lang/" + code + ".yml: " + exception.getMessage());
            }
        }
    }

    /**
     * One-time move of a pre-i18n config.yml "messages:" block into the language files. Only values that
     * the admin actually customized (i.e. differ from the bundled English default) are carried over, so
     * shipped translations are never clobbered. The block is then removed from config.yml.
     */
    private void migrateLegacyConfigMessages(final File langFolder) {
        final ConfigurationSection legacy = plugin.getConfig().getConfigurationSection("messages");
        if (legacy == null) {
            return;
        }
        int migrated = 0;
        for (final String code : BUNDLED) {
            final File target = new File(langFolder, code + ".yml");
            final YamlConfiguration langFile = YamlConfiguration.loadConfiguration(target);
            boolean changed = false;
            for (final String leaf : legacy.getKeys(false)) {
                final String value = legacy.getString(leaf);
                if (value == null) {
                    continue;
                }
                final String path = "messages." + leaf;
                final String english = englishFallback == null ? null : englishFallback.getString(path);
                // Only migrate genuine customizations (different from the shipped English default).
                if (english != null && english.equals(value)) {
                    continue;
                }
                langFile.set(path, value);
                changed = true;
                migrated++;
            }
            if (changed) {
                try {
                    langFile.save(target);
                } catch (final Exception exception) {
                    plugin.getLogger().warning("Could not migrate messages into lang/" + code + ".yml: " + exception.getMessage());
                }
            }
        }
        plugin.getConfig().set("messages", null);
        plugin.saveConfig();
        if (migrated > 0) {
            plugin.getLogger().info("Migrated " + migrated + " custom message(s) from config.yml into the language files.");
        }
    }

    private YamlConfiguration loadBundled(final String code) {
        final InputStream stream = plugin.getResource("lang/" + code + ".yml");
        if (stream == null) {
            return null;
        }
        return YamlConfiguration.loadConfiguration(new InputStreamReader(stream, StandardCharsets.UTF_8));
    }

    /** Raw translated string for a key, with active -> bundled English -> key fallback. */
    public String raw(final String key) {
        if (active != null) {
            final String value = active.getString(key);
            if (value != null && !value.isBlank()) {
                return value;
            }
        }
        if (englishFallback != null) {
            final String value = englishFallback.getString(key);
            if (value != null && !value.isBlank()) {
                return value;
            }
        }
        return key;
    }

    /** Raw string with %placeholder% pairs substituted. */
    public String raw(final String key, final String... replacements) {
        String value = raw(key);
        for (int i = 0; i + 1 < replacements.length; i += 2) {
            value = value.replace(replacements[i], replacements[i + 1]);
        }
        return value;
    }

    /**
     * Renders a language key through MiniMessage, so language files may use tags like
     * {@code <gradient:#a:#b>}, {@code <bold>} or named colours. Plain strings render with the given
     * fallback colour (via {@code colorIfAbsent}), so untagged messages look exactly as before.
     * Substituted %placeholder% values are escaped, so a player-chosen pet name can never inject tags.
     */
    private Component render(final String key, final NamedTextColor fallback, final String... replacements) {
        String template = raw(key);
        for (int i = 0; i + 1 < replacements.length; i += 2) {
            template = template.replace(replacements[i], MINI.escapeTags(replacements[i + 1]));
        }
        try {
            return MINI.deserialize(template).colorIfAbsent(fallback);
        } catch (final RuntimeException exception) {
            // A malformed MiniMessage tag (e.g. from a hand-edited lang file) must never break a message.
            plugin.getLogger().warning("Invalid message formatting for '" + key + "': " + exception.getMessage());
            return Component.text(template.replaceAll("<[^>]+>", ""), fallback);
        }
    }

    /** A gray, non-italic component for a key (the default style for command/menu feedback). */
    public Component component(final String key) {
        return render(key, NamedTextColor.GRAY);
    }

    /** A gray component with %placeholder% pairs substituted. */
    public Component component(final String key, final String... replacements) {
        return render(key, NamedTextColor.GRAY, replacements);
    }

    /** A component in a chosen fallback color with %placeholder% pairs substituted. */
    public Component colored(final String key, final NamedTextColor color, final String... replacements) {
        return render(key, color, replacements);
    }

    public List<String> languageCodes() {
        return List.of(BUNDLED);
    }
}
