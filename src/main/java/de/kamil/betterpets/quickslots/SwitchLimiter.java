package de.kamil.betterpets.quickslots;

import java.util.ArrayDeque;

/**
 * One player's guard against hammering the quick switch - a held key, a macro, a spammed command.
 *
 * <p>Two layers. The <em>cooldown</em> is the minimum gap between two switches. On top of it the
 * <em>burst limit</em> counts the switches that did go through: more than {@code maxSwitches} inside the
 * window and quick switching is locked for a moment. Summoning and dismissing a pet is not free - entities
 * are spawned, sounds and particles reach everyone nearby - so the cooldown alone would still allow doing
 * that twice a second for as long as someone likes.</p>
 *
 * <p>Pure logic on caller-supplied timestamps (milliseconds), so it is covered by the dependency-free
 * tests. Not thread-safe; used from the server thread only.</p>
 */
public final class SwitchLimiter {

    /** The limits, shared by every player. A {@code maxSwitches} of 0 turns the burst limit off. */
    public record Rules(long cooldownMillis, int maxSwitches, long windowMillis, long lockoutMillis) {
        public Rules {
            cooldownMillis = Math.max(0L, cooldownMillis);
            maxSwitches = Math.max(0, maxSwitches);
            windowMillis = Math.max(0L, windowMillis);
            lockoutMillis = Math.max(0L, lockoutMillis);
        }
    }

    public enum Verdict {
        /** Go ahead. */
        ALLOWED,
        /** The previous switch was only a moment ago. */
        COOLDOWN,
        /** Too many switches lately; locked for a while. */
        LOCKED
    }

    private final Rules rules;
    private final ArrayDeque<Long> recent = new ArrayDeque<>();
    private boolean switched;
    private long lastSwitch;
    private long lockedUntil;

    public SwitchLimiter(final Rules rules) {
        this.rules = rules;
    }

    /** Whether a switch may happen now. Changes nothing; call {@link #record} once it did happen. */
    public Verdict check(final long now) {
        if (now < lockedUntil) {
            return Verdict.LOCKED;
        }
        if (switched && now - lastSwitch < rules.cooldownMillis()) {
            return Verdict.COOLDOWN;
        }
        return Verdict.ALLOWED;
    }

    /**
     * Notes a switch that was carried out.
     *
     * @return {@code true} if this one was one too many and the lock starts now
     */
    public boolean record(final long now) {
        switched = true;
        lastSwitch = now;
        if (rules.maxSwitches() <= 0) {
            return false;
        }
        while (!recent.isEmpty() && now - recent.peekFirst() >= rules.windowMillis()) {
            recent.removeFirst();
        }
        recent.addLast(now);
        if (recent.size() <= rules.maxSwitches()) {
            return false;
        }
        // The slate is clean again once the lock is over.
        recent.clear();
        lockedUntil = now + rules.lockoutMillis();
        return rules.lockoutMillis() > 0L;
    }

    /** Milliseconds until the lock ends, 0 when not locked. */
    public long lockRemaining(final long now) {
        return Math.max(0L, lockedUntil - now);
    }
}
