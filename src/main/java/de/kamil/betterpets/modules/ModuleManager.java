package de.kamil.betterpets.modules;

import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.IOException;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

public final class ModuleManager {
    private final JavaPlugin plugin;
    private final File file;
    private final Map<String, Module> modules = new LinkedHashMap<>();
    private final Map<String, Boolean> requestedEnabled = new HashMap<>();
    private final Set<String> active = new HashSet<>();

    public ModuleManager(final JavaPlugin plugin) {
        this.plugin = plugin;
        this.file = new File(plugin.getDataFolder(), "modules.yml");
    }

    public void register(final Module module) {
        modules.put(module.id(), module);
        requestedEnabled.putIfAbsent(module.id(), false);
    }

    public Collection<Module> modules() {
        return modules.values();
    }

    public Optional<Module> module(final String id) {
        return Optional.ofNullable(modules.get(id));
    }

    public void load() {
        if (!plugin.getDataFolder().exists() && !plugin.getDataFolder().mkdirs()) {
            plugin.getLogger().warning("Could not create Better Pets data folder for modules.yml.");
        }
        final YamlConfiguration config = file.exists() ? YamlConfiguration.loadConfiguration(file) : new YamlConfiguration();
        modules.keySet().forEach(id -> requestedEnabled.put(id, config.getBoolean("modules." + id + ".enabled", false)));
        save();
    }

    public void reload() {
        load();
        for (final Module module : modules.values()) {
            final boolean requested = isRequestedEnabled(module.id());
            if (!requested || !module.isAvailable()) {
                if (isActive(module.id())) {
                    deactivate(module, false);
                }
                continue;
            }
            if (!isActive(module.id())) {
                activate(module, false);
            }
        }
    }

    public void enablePersistedAvailable() {
        for (final Module module : modules.values()) {
            if (isRequestedEnabled(module.id())) {
                if (module.isAvailable()) {
                    activate(module, false);
                } else {
                    plugin.getLogger().info("Module " + module.id() + " is enabled in modules.yml but unavailable because " + module.requiredPluginName() + " is missing or disabled.");
                }
            }
        }
    }

    public boolean toggle(final String id) {
        final Module module = modules.get(id);
        if (module == null) {
            return false;
        }
        if (isActive(id)) {
            return setEnabled(id, false);
        }
        return setEnabled(id, true);
    }

    public boolean setEnabled(final String id, final boolean enabled) {
        final Module module = modules.get(id);
        if (module == null) {
            return false;
        }
        requestedEnabled.put(id, enabled);
        save();
        if (enabled) {
            return activate(module, true);
        }
        deactivate(module, true);
        return true;
    }

    public boolean isRequestedEnabled(final String id) {
        return requestedEnabled.getOrDefault(id, false);
    }

    public boolean isActive(final String id) {
        return active.contains(id);
    }

    public void shutdown() {
        modules.values().stream()
            .filter(module -> isActive(module.id()))
            .forEach(module -> deactivate(module, false));
    }

    private boolean activate(final Module module, final boolean userAction) {
        if (!module.isAvailable()) {
            if (userAction) {
                plugin.getLogger().warning("Cannot enable module " + module.id() + " because " + module.requiredPluginName() + " is missing or disabled.");
            }
            return false;
        }
        if (isActive(module.id())) {
            return true;
        }
        try {
            module.onEnable();
            active.add(module.id());
            plugin.getLogger().info("Module enabled: " + module.id());
            return true;
        } catch (final RuntimeException exception) {
            plugin.getLogger().severe("Could not enable module " + module.id() + ": " + exception.getMessage());
            return false;
        }
    }

    private void deactivate(final Module module, final boolean userAction) {
        if (!active.remove(module.id())) {
            return;
        }
        try {
            module.onDisable();
            plugin.getLogger().info("Module disabled: " + module.id());
        } catch (final RuntimeException exception) {
            plugin.getLogger().severe("Could not disable module " + module.id() + ": " + exception.getMessage());
        }
    }

    private void save() {
        final YamlConfiguration config = new YamlConfiguration();
        modules.keySet().forEach(id -> config.set("modules." + id + ".enabled", requestedEnabled.getOrDefault(id, false)));
        try {
            config.save(file);
        } catch (final IOException exception) {
            plugin.getLogger().severe("Could not save modules.yml: " + exception.getMessage());
        }
    }
}
