package de.kamil.betterpets.modules;

import com.github.retrooper.packetevents.PacketEvents;
import com.github.retrooper.packetevents.event.PacketListener;
import com.github.retrooper.packetevents.event.PacketListenerCommon;
import com.github.retrooper.packetevents.event.PacketListenerPriority;
import com.github.retrooper.packetevents.event.PacketReceiveEvent;
import com.github.retrooper.packetevents.protocol.packettype.PacketType;
import com.github.retrooper.packetevents.protocol.player.User;
import com.github.retrooper.packetevents.wrapper.play.client.WrapperPlayClientHeldItemChange;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerHeldItemChange;
import de.kamil.betterpets.quickslots.HeldSlotInterceptor;

import java.util.UUID;

/**
 * Watches the clients' hotbar-change packets through PacketEvents and lets the quickslot logic swallow
 * the ones that are a sneak+scroll pet switch. The only class that touches PacketEvents types: it is
 * loaded solely by {@link PacketEventsModule}, and only once PacketEvents is known to be installed.
 */
final class PacketEventsScrollHook implements PacketListener {

    private final HeldSlotInterceptor interceptor;

    private PacketEventsScrollHook(final HeldSlotInterceptor interceptor) {
        this.interceptor = interceptor;
    }

    /** Starts listening. Returns the action that stops it again. */
    static Runnable register(final HeldSlotInterceptor interceptor) {
        final PacketListenerCommon registered = PacketEvents.getAPI().getEventManager()
            .registerListener(new PacketEventsScrollHook(interceptor), PacketListenerPriority.NORMAL);
        return () -> PacketEvents.getAPI().getEventManager().unregisterListener(registered);
    }

    @Override
    public void onPacketReceive(final PacketReceiveEvent event) {
        if (event.getPacketType() != PacketType.Play.Client.HELD_ITEM_CHANGE) {
            return;
        }
        final User user = event.getUser();
        final UUID playerId = user == null ? null : user.getUUID();
        if (playerId == null) {
            return;
        }
        final int snapBack = interceptor.interceptHeldSlot(playerId, new WrapperPlayClientHeldItemChange(event).getSlot());
        if (snapBack < 0) {
            return;
        }
        // The server never sees the change; the client already moved its selection, so move it back.
        event.setCancelled(true);
        user.sendPacket(new WrapperPlayServerHeldItemChange(snapBack));
    }
}
