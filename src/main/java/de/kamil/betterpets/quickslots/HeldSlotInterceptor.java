package de.kamil.betterpets.quickslots;

import java.util.UUID;

/**
 * The seam between the quickslot logic and whatever watches hotbar-change packets (the optional
 * PacketEvents hook). Kept as a tiny interface so the hook class - the only one that touches PacketEvents
 * types - can live apart from the rest and is never loaded on servers without PacketEvents.
 */
public interface HeldSlotInterceptor {

    /**
     * Decides what to do with a hotbar change sent by a client. Called on the network thread, so it must
     * only read thread-safe state.
     *
     * @return the slot to snap the client back to when the change is swallowed as a quickslot scroll, or
     *     {@code -1} to let it through untouched
     */
    int interceptHeldSlot(UUID playerId, int newSlot);

    /** Told by the hook whether it is currently listening (i.e. sneak+scroll can work at all). */
    void setScrollHookActive(boolean active);
}
