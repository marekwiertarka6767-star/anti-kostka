package dev.antikostka.restore;

import dev.antikostka.match.Fingerprint;
import dev.antikostka.match.ItemMatcher;
import net.minecraft.item.ItemStack;

/**
 * Chooses the next swap needed to move the hotbar towards the saved layout.
 * <p>
 * Only swaps are planned, so no item is ever deleted, duplicated, merged, split or dropped.
 * Inventory indices: 0-8 = hotbar, 9-35 = main inventory.
 * <p>
 * Termination: a swap only ever involves unsatisfied hotbar slots and never touches a
 * satisfied one, so the number of satisfied slots strictly grows.
 */
public final class RestorePlanner {
    private RestorePlanner() {
    }

    /** Swap the stack at {@code sourceIndex} with hotbar slot {@code targetHotbar}. */
    public record Swap(int targetHotbar, int sourceIndex) {
    }

    public static Swap next(ItemStack[] inv, Fingerprint[] layout) {
        boolean[] satisfied = new boolean[9];
        for (int h = 0; h < 9; h++) {
            satisfied[h] = layout[h] != null && ItemMatcher.matches(layout[h], inv[h]);
        }

        for (int h = 0; h < 9; h++) {
            if (layout[h] == null || satisfied[h]) {
                continue; // empty-saved slots carry no requirement
            }

            int best = -1;
            int bestRank = Integer.MAX_VALUE;
            for (int s = 0; s < 36; s++) {
                if (s == h || (s < 9 && satisfied[s])) {
                    continue; // never take from a slot that is already correct
                }
                if (!ItemMatcher.matches(layout[h], inv[s])) {
                    continue;
                }
                int rank;
                if (s >= 9) {
                    rank = 2; // main inventory: last choice
                } else if (layout[s] != null && ItemMatcher.matches(layout[s], inv[h])) {
                    rank = 0; // two-way swap fixes both slots
                } else {
                    rank = 1; // other hotbar slot
                }
                if (rank < bestRank) { // ascending scan, so the lowest index wins ties
                    best = s;
                    bestRank = rank;
                }
            }
            if (best >= 0) {
                return new Swap(h, best);
            }
            // No matching stack anywhere: saved item is missing, leave this slot alone.
        }
        return null;
    }
}
