package dev.antikostka.client;

import dev.antikostka.config.AntiKostkaConfig;
import dev.antikostka.match.Fingerprint;
import net.minecraft.client.MinecraftClient;
import net.minecraft.item.ItemStack;
import net.minecraft.text.Text;

public final class LayoutActions {
    private LayoutActions() {
    }

    /** Saves the current hotbar. Only item identity is stored, never the stack count. */
    public static void saveCurrentHotbar(MinecraftClient mc) {
        if (mc.player == null || mc.world == null) {
            return;
        }
        Fingerprint[] captured = new Fingerprint[AntiKostkaConfig.HOTBAR_SIZE];
        boolean any = false;
        for (int i = 0; i < AntiKostkaConfig.HOTBAR_SIZE; i++) {
            ItemStack stack = mc.player.getInventory().getStack(i);
            if (!stack.isEmpty()) {
                captured[i] = Fingerprint.capture(stack);
                any = true;
            }
        }
        if (!any) {
            // Protects an existing layout from being wiped by accident.
            mc.player.sendMessage(Text.translatable("message.anti_kostka.empty_hotbar"), true);
            return;
        }
        AntiKostkaConfig cfg = AntiKostkaConfig.get();
        cfg.layout = captured;
        cfg.save();
        mc.player.sendMessage(Text.translatable("message.anti_kostka.saved"), true);
    }

    public static void clear(MinecraftClient mc) {
        AntiKostkaConfig.get().clearLayout();
        if (mc.player != null) {
            mc.player.sendMessage(Text.translatable("message.anti_kostka.cleared"), true);
        }
    }
}
