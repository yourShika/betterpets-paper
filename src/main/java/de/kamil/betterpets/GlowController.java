package de.kamil.betterpets;

import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.List;

/**
 * Shows a glowing outline (visible through walls) to a single player only, by sending that player a
 * partial entity-metadata packet that flips the shared "glowing" flag. The Paper API has no per-viewer
 * glow, so this reaches into the server internals via reflection. If anything cannot be resolved on the
 * running server, {@link #isAvailable()} returns {@code false} and the caller falls back to a normal
 * server-wide glow (still through walls, but visible to everyone).
 */
final class GlowController {
    private static final byte GLOW_FLAG = 0x40;

    private final boolean available;
    private Method nmsGetId;
    private Method nmsGetEntityData;
    private Method synchedGet;
    private Object sharedFlagsAccessor;
    private Method dataValueCreate;
    private Constructor<?> setEntityDataPacket;
    private Field connectionField;
    private Method sendMethod;

    GlowController(final JavaPlugin plugin) {
        boolean ok = false;
        try {
            final Class<?> nmsEntity = Class.forName("net.minecraft.world.entity.Entity");
            final Class<?> synched = Class.forName("net.minecraft.network.syncher.SynchedEntityData");
            final Class<?> dataValue = Class.forName("net.minecraft.network.syncher.SynchedEntityData$DataValue");
            final Class<?> accessor = Class.forName("net.minecraft.network.syncher.EntityDataAccessor");
            final Class<?> packetClass = Class.forName("net.minecraft.network.protocol.Packet");
            final Class<?> setData = Class.forName("net.minecraft.network.protocol.game.ClientboundSetEntityDataPacket");
            final Class<?> serverPlayer = Class.forName("net.minecraft.server.level.ServerPlayer");

            nmsGetId = nmsEntity.getMethod("getId");
            nmsGetEntityData = nmsEntity.getMethod("getEntityData");
            synchedGet = synched.getMethod("get", accessor);

            final Field flagsField = nmsEntity.getDeclaredField("DATA_SHARED_FLAGS_ID");
            flagsField.setAccessible(true);
            sharedFlagsAccessor = flagsField.get(null);

            dataValueCreate = dataValue.getMethod("create", accessor, Object.class);
            setEntityDataPacket = setData.getConstructor(int.class, List.class);

            connectionField = findField(serverPlayer, "connection");
            connectionField.setAccessible(true);
            sendMethod = findMethod(connectionField.getType(), "send", packetClass);
            sendMethod.setAccessible(true);

            ok = true;
        } catch (final Throwable error) {
            plugin.getLogger().warning("Per-player pet glow unavailable; falling back to shared glow (" + error + ").");
        }
        available = ok;
    }

    boolean isAvailable() {
        return available;
    }

    /**
     * Sets or clears the glow outline of {@code target} for {@code viewer} only. The other shared flags
     * (on fire, sneaking, ...) are preserved. Returns {@code false} if it could not be sent.
     */
    boolean setGlow(final Player viewer, final Entity target, final boolean glowing) {
        if (!available) {
            return false;
        }
        try {
            final Object nmsTarget = invokeHandle(target);
            final int id = ((Integer) nmsGetId.invoke(nmsTarget)).intValue();
            final Object data = nmsGetEntityData.invoke(nmsTarget);
            final byte real = ((Byte) synchedGet.invoke(data, sharedFlagsAccessor)).byteValue();
            final byte flags = (byte) (glowing ? (real | GLOW_FLAG) : (real & ~GLOW_FLAG));
            final Object value = dataValueCreate.invoke(null, sharedFlagsAccessor, Byte.valueOf(flags));
            final Object packet = setEntityDataPacket.newInstance(id, List.of(value));
            final Object connection = connectionField.get(invokeHandle(viewer));
            sendMethod.invoke(connection, packet);
            return true;
        } catch (final Throwable error) {
            return false;
        }
    }

    private static Object invokeHandle(final Object craft) throws ReflectiveOperationException {
        return craft.getClass().getMethod("getHandle").invoke(craft);
    }

    private static Field findField(final Class<?> start, final String name) throws NoSuchFieldException {
        for (Class<?> type = start; type != null; type = type.getSuperclass()) {
            try {
                return type.getDeclaredField(name);
            } catch (final NoSuchFieldException ignored) {
                // keep walking up the hierarchy
            }
        }
        throw new NoSuchFieldException(name);
    }

    private static Method findMethod(final Class<?> start, final String name, final Class<?>... params) throws NoSuchMethodException {
        for (Class<?> type = start; type != null; type = type.getSuperclass()) {
            try {
                return type.getDeclaredMethod(name, params);
            } catch (final NoSuchMethodException ignored) {
                // keep walking up the hierarchy
            }
        }
        throw new NoSuchMethodException(name);
    }
}
