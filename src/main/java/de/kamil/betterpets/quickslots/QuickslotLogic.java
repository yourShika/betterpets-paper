package de.kamil.betterpets.quickslots;

/**
 * The pure decision logic behind quickslots (no Bukkit types), so it can be covered by the
 * dependency-free tests in {@code PetTests}.
 */
public final class QuickslotLogic {

    private static final int HOTBAR_SIZE = 9;

    private QuickslotLogic() {
    }

    /**
     * Interprets a held-slot change as a mouse-wheel notch.
     *
     * @return {@code +1} for one notch forward (next slot, wrapping 8 -&gt; 0), {@code -1} for one notch
     *     back, and {@code 0} for anything else - i.e. a number key or a jump across several slots, which
     *     must stay a normal hotbar change
     */
    public static int scrollDirection(final int oldSlot, final int newSlot) {
        final int diff = Math.floorMod(newSlot - oldSlot, HOTBAR_SIZE);
        if (diff == 1) {
            return 1;
        }
        return diff == HOTBAR_SIZE - 1 ? -1 : 0;
    }

    /**
     * Finds the slot a "next"/"previous" step lands on.
     *
     * @param usable    per slot: holds a pet the player owns and may summon
     * @param current   the slot of the currently summoned pet, or {@code -1} if it is in no slot
     * @param direction {@code >= 0} steps forward, negative steps back
     * @return the target slot (wrapping around), or {@code -1} when no slot is usable. With a single
     *     usable slot that is also the current one, that same slot is returned.
     */
    public static int step(final boolean[] usable, final int current, final int direction) {
        final int size = usable.length;
        final int stride = direction < 0 ? -1 : 1;
        if (current < 0 || current >= size) {
            // Nothing to step from: forward starts at the first slot, backward at the last one.
            for (int i = 0; i < size; i++) {
                final int index = stride > 0 ? i : size - 1 - i;
                if (usable[index]) {
                    return index;
                }
            }
            return -1;
        }
        for (int i = 1; i <= size; i++) {
            final int index = Math.floorMod(current + stride * i, size);
            if (usable[index]) {
                return index;
            }
        }
        return -1;
    }

    /**
     * Parses a 1-based slot number typed by a player.
     *
     * @return the 0-based slot index, or {@code -1} if the text is not a number in {@code 1..slotCount}
     */
    public static int parseSlot(final String text, final int slotCount) {
        if (text == null) {
            return -1;
        }
        try {
            final int number = Integer.parseInt(text.trim());
            return number >= 1 && number <= slotCount ? number - 1 : -1;
        } catch (final NumberFormatException notANumber) {
            return -1;
        }
    }
}
