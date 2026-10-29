package dev.antikostka.client;

import dev.antikostka.config.AntiKostkaConfig;
import dev.antikostka.restore.HotbarRestorer;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;
import net.minecraft.text.Text;
import org.lwjgl.glfw.GLFW;

/** All keys appear in Options > Controls under the "Anti-Kostka" category and can be rebound. */
public final class Keybinds {
    private static final String CATEGORY = "category.anti_kostka";
    private static final long CLEAR_CONFIRM_MS = 3000;

    public static KeyBinding save;
    public static KeyBinding toggle;
    public static KeyBinding clear;
    public static KeyBinding restoreNow;
    public static KeyBinding settings;

    private static long clearArmedUntil;

    private Keybinds() {
    }

    public static void register() {
        save = KeyBindingHelper.registerKeyBinding(new KeyBinding(
                "key.anti_kostka.save", InputUtil.Type.KEYSYM, GLFW.GLFW_KEY_RIGHT_BRACKET, CATEGORY));
        toggle = KeyBindingHelper.registerKeyBinding(new KeyBinding(
                "key.anti_kostka.toggle", InputUtil.Type.KEYSYM, GLFW.GLFW_KEY_LEFT_BRACKET, CATEGORY));
        settings = KeyBindingHelper.registerKeyBinding(new KeyBinding(
                "key.anti_kostka.settings", InputUtil.Type.KEYSYM, GLFW.GLFW_KEY_BACKSLASH, CATEGORY));
        // Unbound by default; bind them in Controls if you want them.
        clear = KeyBindingHelper.registerKeyBinding(new KeyBinding(
                "key.anti_kostka.clear", InputUtil.Type.KEYSYM, GLFW.GLFW_KEY_UNKNOWN, CATEGORY));
        restoreNow = KeyBindingHelper.registerKeyBinding(new KeyBinding(
                "key.anti_kostka.restore_now", InputUtil.Type.KEYSYM, GLFW.GLFW_KEY_UNKNOWN, CATEGORY));
    }

    public static void handle(MinecraftClient mc) {
        if (mc.player == null || mc.world == null) {
            return;
        }

        while (save.wasPressed()) {
            LayoutActions.saveCurrentHotbar(mc);
        }

        while (toggle.wasPressed()) {
            AntiKostkaConfig cfg = AntiKostkaConfig.get();
            cfg.enabled = !cfg.enabled;
            cfg.save();
            mc.player.sendMessage(Text.translatable(
                    cfg.enabled ? "message.anti_kostka.enabled" : "message.anti_kostka.disabled"), true);
        }

        while (clear.wasPressed()) {
            long now = System.currentTimeMillis();
            if (now <= clearArmedUntil) {
                clearArmedUntil = 0;
                LayoutActions.clear(mc);
            } else {
                // First press only arms the action, the second press within 3 s confirms.
                clearArmedUntil = now + CLEAR_CONFIRM_MS;
                mc.player.sendMessage(Text.translatable("message.anti_kostka.clear_confirm"), true);
            }
        }

        while (restoreNow.wasPressed()) {
            HotbarRestorer.restoreNow(mc);
        }

        while (settings.wasPressed()) {
            mc.setScreen(new LayoutScreen(null));
        }
    }
}
