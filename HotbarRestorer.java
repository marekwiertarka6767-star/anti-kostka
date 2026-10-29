package dev.antikostka.restore;

import dev.antikostka.config.AntiKostkaConfig;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.Registries;
import net.minecraft.screen.slot.SlotActionType;
import net.minecraft.text.Text;

/**
 * Restores the saved layout using normal inventory clicks (SlotActionType.SWAP), which go
 * through the server like a player's own clicks. The local inventory is never edited directly.
 */
public final class HotbarRestorer {
    private static final int MAX_NO_PROGRESS = 3;
    private static final int WINDOW_TICKS = 400;
    private static final int MAX_SWAPS_PER_WINDOW = 16;
    private static final int PAUSE_TICKS = 200;

    private static long tickCounter;
    private static int cooldown;
    private static int grace;
    private static int pause;
    private static int lastSignature;
    private static boolean idle;
    private static int signatureBeforeSwap = Integer.MIN_VALUE;
    private static int noProgress;
    private static long windowStart;
    private static int swapsInWindow;

    private HotbarRestorer() {
    }

    public static boolean isPaused() {
        return pause > 0;
    }

    private static void reset() {
        cooldown = 0;
        grace = 0;
        pause = 0;
        idle = false;
        noProgress = 0;
        signatureBeforeSwap = Integer.MIN_VALUE;
        swapsInWindow = 0;
    }

    /** Runs every client tick. Does nothing unless the inventory (or config) changed. */
    public static void tick(MinecraftClient mc) {
        tickCounter++;
        ClientPlayerEntity player = mc.player;
        if (player == null || mc.world == null || mc.interactionManager == null) {
            reset();
            return;
        }
        if (pause > 0) {
            pause--;
        }

        AntiKostkaConfig cfg = AntiKostkaConfig.get();
        if (mc.currentScreen != null) {
            grace = cfg.graceTicks; // let the user finish rearranging, and press Save
            return;
        }
        if (grace > 0) {
            grace--;
            return;
        }
        if (!cfg.enabled || !cfg.hasLayout() || pause > 0 || !canAct(mc, player)) {
            return;
        }
        if (cooldown > 0) {
            cooldown--;
            return;
        }
        attempt(mc, player, cfg, false);
    }

    /** Manual one-step restore (Restore Now key); works even when auto-restore is disabled. */
    public static void restoreNow(MinecraftClient mc) {
        ClientPlayerEntity player = mc.player;
        if (player == null || mc.interactionManager == null) {
            return;
        }
        AntiKostkaConfig cfg = AntiKostkaConfig.get();
        if (!cfg.hasLayout()) {
            player.sendMessage(Text.translatable("message.anti_kostka.no_layout"), true);
            return;
        }
        if (!canAct(mc, player)) {
            player.sendMessage(Text.translatable("message.anti_kostka.restore_blocked"), true);
            return;
        }
        pause = 0;
        noProgress = 0;
        cooldown = 0;
        attempt(mc, player, cfg, true);
    }

    private static void attempt(MinecraftClient mc, ClientPlayerEntity player, AntiKostkaConfig cfg, boolean force) {
        int signature = signature(player, cfg);
        if (!force && idle && signature == lastSignature) {
            return; // nothing changed since we last found nothing to do
        }

        ItemStack[] inv = new ItemStack[36];
        for (int i = 0; i < 36; i++) {
            inv[i] = player.getInventory().getStack(i);
        }

        RestorePlanner.Swap swap = RestorePlanner.next(inv, cfg.layout);
        if (swap == null) {
            idle = true;
            lastSignature = signature;
            noProgress = 0;
            signatureBeforeSwap = Integer.MIN_VALUE;
            return;
        }

        // If our last click changed nothing, the server probably rejected it.
        if (signature == signatureBeforeSwap) {
            noProgress++;
            if (noProgress >= MAX_NO_PROGRESS) {
                giveUp(player, signature);
                return;
            }
        } else {
            noProgress = 0;
        }

        // Safety valve against a server that keeps undoing our moves.
        if (tickCounter - windowStart > WINDOW_TICKS) {
            windowStart = tickCounter;
            swapsInWindow = 0;
        }
        if (++swapsInWindow > MAX_SWAPS_PER_WINDOW) {
            giveUp(player, signature);
            return;
        }

        // Swap the clicked slot with hotbar slot "button". One packet, no cursor involved.
        mc.interactionManager.clickSlot(
                player.playerScreenHandler.syncId,
                toScreenSlot(swap.sourceIndex()),
                swap.targetHotbar(),
                SlotActionType.SWAP,
                player
        );
        signatureBeforeSwap = signature;
        idle = false;
        cooldown = cfg.restoreDelayTicks;
    }

    private static void giveUp(ClientPlayerEntity player, int signature) {
        pause = PAUSE_TICKS;
        idle = true;
        lastSignature = signature;
        noProgress = 0;
        signatureBeforeSwap = Integer.MIN_VALUE;
        swapsInWindow = 0;
        player.sendMessage(Text.translatable("message.anti_kostka.paused"), true);
    }

    /** Player-inventory screen slot ids: main 9-35 map to themselves, hotbar 0-8 are 36-44. */
    private static int toScreenSlot(int inventoryIndex) {
        return inventoryIndex < 9 ? 36 + inventoryIndex : inventoryIndex;
    }

    private static boolean canAct(MinecraftClient mc, ClientPlayerEntity player) {
        return mc.currentScreen == null
                && player.isAlive()
                && !player.isSpectator()
                && player.currentScreenHandler == player.playerScreenHandler
                && player.playerScreenHandler.getCursorStack().isEmpty()
                && !player.isUsingItem()
                && !mc.interactionManager.isBreakingBlock();
    }

    /**
     * Cheap change detector. It covers item identity per slot (and component changes in
     * advanced mode) but never the stack count, so counting up or down triggers no work.
     */
    private static int signature(ClientPlayerEntity player, AntiKostkaConfig cfg) {
        boolean advanced = cfg.matchMode == AntiKostkaConfig.MatchMode.ADVANCED;
        int h = cfg.revision;
        for (int i = 0; i < 36; i++) {
            ItemStack stack = player.getInventory().getStack(i);
            if (stack.isEmpty()) {
                h = 31 * h;
                continue;
            }
            h = 31 * h + Registries.ITEM.getRawId(stack.getItem()) + 1;
            if (advanced) {
                h = 31 * h + stack.getComponentChanges().hashCode();
            }
        }
        return h;
    }
}
