package de.kamil.betterpets.modules;

import org.bukkit.Bukkit;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.Enumeration;
import java.util.HexFormat;
import java.util.Properties;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

/**
 * Copies the bundled Oraxen assets ({@code oraxen/**} inside the plugin JAR) into an installed Oraxen at
 * runtime, so the icon items, textures and the menu-background font ship with Better Pets rather than
 * having to be pasted into Oraxen by hand.
 *
 * <p>Item and font files are always refreshed (they carry the ids/offsets the plugin relies on). Textures
 * are only (re)written when they are missing or when the bundled version changed since the last deploy, so
 * a server's own edited textures are preserved. A small state file in the plugin folder tracks the last
 * deployed hashes. Everything is best-effort: any failure is logged and never breaks plugin start.</p>
 */
public final class OraxenAssetDeployer {

    private static final String BUNDLE = "oraxen/";
    private static final String ITEMS = "oraxen/items/";
    private static final String PACK = "oraxen/pack/";
    private static final String TEX = "oraxen/pack/textures/";
    private static final String MANIFEST = "oraxen/asset-manifest.properties";
    private static final String STATE_FILE = ".oraxen-asset-state.properties";

    private final JavaPlugin plugin;
    private final File jar;

    public OraxenAssetDeployer(final JavaPlugin plugin, final File jar) {
        this.plugin = plugin;
        this.jar = jar;
    }

    /** Extracts/updates the bundled assets into Oraxen. No-op (logged) if Oraxen is not installed. */
    public void deploy() {
        final Plugin oraxen = Bukkit.getPluginManager().getPlugin("Oraxen");
        if (oraxen == null) {
            plugin.getLogger().info("Oraxen module: Oraxen not installed, skipping asset deploy.");
            return;
        }
        final File data = oraxen.getDataFolder();
        final File statePath = new File(plugin.getDataFolder(), STATE_FILE);
        final Properties state = load(statePath);

        int written = 0;
        int preserved = 0;
        try (ZipFile zip = new ZipFile(jar)) {
            final Enumeration<? extends ZipEntry> entries = zip.entries();
            while (entries.hasMoreElements()) {
                final ZipEntry entry = entries.nextElement();
                final String name = entry.getName();
                if (entry.isDirectory() || !name.startsWith(BUNDLE) || name.equals(MANIFEST)) {
                    continue;
                }
                final File target = targetFor(data, name);
                if (target == null) {
                    continue;
                }
                final byte[] bundled = readAll(zip, entry);
                final String bundledHash = sha256(bundled);
                final boolean isTexture = name.startsWith(TEX);
                if (target.exists()) {
                    final String currentHash = sha256(Files.readAllBytes(target.toPath()));
                    if (currentHash.equals(bundledHash)) {
                        continue; // already up to date
                    }
                    // Items and font are plugin-owned: always refresh. Textures: only if the admin has not
                    // edited them (i.e. the on-disk copy still matches what we last deployed).
                    if (isTexture) {
                        final String lastDeployed = state.getProperty(name);
                        if (lastDeployed != null && !lastDeployed.equals(currentHash)) {
                            preserved++;
                            continue; // admin changed this texture — keep it
                        }
                    }
                }
                target.getParentFile().mkdirs();
                Files.write(target.toPath(), bundled);
                state.setProperty(name, bundledHash);
                written++;
            }
        } catch (final Exception exception) {
            plugin.getLogger().warning("Oraxen module: asset deploy failed: " + exception.getMessage());
            return;
        }
        save(statePath, state);
        final File itemsFile = new File(data, "items/betterpets_gui_icons.yml");
        plugin.getLogger().info("Oraxen module: deployed " + written + " asset file(s)"
            + (preserved > 0 ? ", preserved " + preserved + " edited texture(s)" : "")
            + " into " + data.getName() + ".");
        plugin.getLogger().info("Oraxen module: icon item file "
            + (itemsFile.exists() ? "present (" + itemsFile.length() + " bytes) at " + itemsFile.getPath()
                : "MISSING at " + itemsFile.getPath()) + ".");
        plugin.getLogger().info("Oraxen module: if the menu ICONS still show vanilla items, do a FULL server "
            + "restart (not just /oraxen reload) so Oraxen scans the new items file, then /oraxen reload once.");
    }

    private static File targetFor(final File oraxenData, final String bundleName) {
        // Oraxen item configs go to items/; everything under oraxen/pack/** (textures, models, font, and the
        // 1.21.4+ item definitions) is copied verbatim into Oraxen's pack/ so it lands in the built pack.
        if (bundleName.startsWith(ITEMS)) {
            return new File(oraxenData, "items/" + bundleName.substring(ITEMS.length()));
        }
        if (bundleName.startsWith(PACK)) {
            return new File(oraxenData, "pack/" + bundleName.substring(PACK.length()));
        }
        return null;
    }

    private static byte[] readAll(final ZipFile zip, final ZipEntry entry) throws Exception {
        try (InputStream in = zip.getInputStream(entry)) {
            return in.readAllBytes();
        }
    }

    private static String sha256(final byte[] data) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(data));
        } catch (final Exception exception) {
            return Integer.toHexString(java.util.Arrays.hashCode(data));
        }
    }

    private Properties load(final File file) {
        final Properties p = new Properties();
        if (file.exists()) {
            try (InputStream in = Files.newInputStream(file.toPath())) {
                p.load(in);
            } catch (final Exception exception) {
                plugin.getLogger().warning("Oraxen module: could not read asset state: " + exception.getMessage());
            }
        }
        return p;
    }

    private void save(final File file, final Properties state) {
        try {
            final Path dir = file.getParentFile().toPath();
            Files.createDirectories(dir);
            try (var out = Files.newOutputStream(file.toPath())) {
                state.store(out, "Better Pets Oraxen asset state (last deployed hashes)");
            }
        } catch (final Exception exception) {
            plugin.getLogger().warning("Oraxen module: could not save asset state: " + exception.getMessage());
        }
    }
}
