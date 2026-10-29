package dev.antikostka.client;

import dev.antikostka.config.AntiKostkaConfig;
import dev.antikostka.restore.HotbarRestorer;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.text.MutableText;
import net.minecraft.text.Text;

/** Small ON/OFF indicator in the top-left corner. */
public final class StatusHud {
    private StatusHud() {
    }

    public static void render(DrawContext context) {
        MinecraftClient mc = MinecraftClient.getInstance();
        AntiKostkaConfig cfg = AntiKostkaConfig.get();
        if (!cfg.showIndicator || mc.player == null || mc.options.hudHidden) {
            return;
        }

        MutableText text = Text.translatable(cfg.enabled ? "hud.anti_kostka.on" : "hud.anti_kostka.off");
        if (!cfg.hasLayout()) {
            text.append(Text.translatable("hud.anti_kostka.no_layout"));
        } else if (cfg.enabled && HotbarRestorer.isPaused()) {
            text.append(Text.translatable("hud.anti_kostka.paused"));
        }
        int color = cfg.enabled ? 0xFF55FF55 : 0xFFFF5555;
        context.drawTextWithShadow(mc.textRenderer, text, 4, 4, color);
    }
}
