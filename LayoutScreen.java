package dev.antikostka.client;

import dev.antikostka.config.AntiKostkaConfig;
import dev.antikostka.match.Fingerprint;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.Registries;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;

/** Shows the saved layout and lets the user change the settings. Opened with the Settings key. */
public class LayoutScreen extends Screen {
    private static final int SLOT = 20;

    private final Screen parent;
    private long clearArmedUntil;

    private ButtonWidget enabledButton;
    private ButtonWidget modeButton;
    private ButtonWidget indicatorButton;
    private ButtonWidget clearButton;
    private ButtonWidget enchantButton;
    private ButtonWidget nameButton;
    private ButtonWidget durabilityButton;
    private ButtonWidget componentsButton;

    public LayoutScreen(Screen parent) {
        super(Text.translatable("screen.anti_kostka.title"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        AntiKostkaConfig cfg = AntiKostkaConfig.get();
        int left = width / 2 - 155;
        int right = width / 2 + 5;
        int w = 150;
        int y = 90;
        int step = 24;

        enabledButton = addDrawableChild(ButtonWidget.builder(Text.empty(), b -> {
            cfg.enabled = !cfg.enabled;
            cfg.save();
            refresh();
        }).dimensions(left, y, w, 20).build());

        modeButton = addDrawableChild(ButtonWidget.builder(Text.empty(), b -> {
            cfg.matchMode = cfg.matchMode == AntiKostkaConfig.MatchMode.BASIC
                    ? AntiKostkaConfig.MatchMode.ADVANCED : AntiKostkaConfig.MatchMode.BASIC;
            cfg.save();
            refresh();
        }).dimensions(left, y + step, w, 20).build());

        indicatorButton = addDrawableChild(ButtonWidget.builder(Text.empty(), b -> {
            cfg.showIndicator = !cfg.showIndicator;
            cfg.save();
            refresh();
        }).dimensions(left, y + step * 2, w, 20).build());

        addDrawableChild(ButtonWidget.builder(Text.translatable("screen.anti_kostka.save"),
                b -> LayoutActions.saveCurrentHotbar(client)
        ).dimensions(left, y + step * 3, w, 20).build());

        clearButton = addDrawableChild(ButtonWidget.builder(Text.empty(), b -> {
            long now = System.currentTimeMillis();
            if (now <= clearArmedUntil) {
                clearArmedUntil = 0;
                LayoutActions.clear(client);
            } else {
                clearArmedUntil = now + 3000; // second click within 3 s confirms
            }
        }).dimensions(left, y + step * 4, w, 20).build());

        enchantButton = addDrawableChild(ButtonWidget.builder(Text.empty(), b -> {
            cfg.matchEnchantments = !cfg.matchEnchantments;
            cfg.save();
            refresh();
        }).dimensions(right, y, w, 20).build());

        nameButton = addDrawableChild(ButtonWidget.builder(Text.empty(), b -> {
            cfg.matchCustomName = !cfg.matchCustomName;
            cfg.save();
            refresh();
        }).dimensions(right, y + step, w, 20).build());

        durabilityButton = addDrawableChild(ButtonWidget.builder(Text.empty(), b -> {
            cfg.matchDurability = !cfg.matchDurability;
            cfg.save();
            refresh();
        }).dimensions(right, y + step * 2, w, 20).build());

        componentsButton = addDrawableChild(ButtonWidget.builder(Text.empty(), b -> {
            cfg.matchAllComponents = !cfg.matchAllComponents;
            cfg.save();
            refresh();
        }).dimensions(right, y + step * 3, w, 20).build());

        addDrawableChild(ButtonWidget.builder(Text.translatable("screen.anti_kostka.done"), b -> close())
                .dimensions(right, y + step * 4, w, 20).build());

        refresh();
    }

    private static Text onOff(boolean value) {
        return Text.translatable(value ? "options.on" : "options.off");
    }

    private void refresh() {
        AntiKostkaConfig cfg = AntiKostkaConfig.get();
        boolean advanced = cfg.matchMode == AntiKostkaConfig.MatchMode.ADVANCED;
        enabledButton.setMessage(Text.translatable("screen.anti_kostka.enabled", onOff(cfg.enabled)));
        modeButton.setMessage(Text.translatable("screen.anti_kostka.mode", Text.translatable(
                advanced ? "screen.anti_kostka.mode.advanced" : "screen.anti_kostka.mode.basic")));
        indicatorButton.setMessage(Text.translatable("screen.anti_kostka.indicator", onOff(cfg.showIndicator)));
        enchantButton.setMessage(Text.translatable("screen.anti_kostka.enchantments", onOff(cfg.matchEnchantments)));
        nameButton.setMessage(Text.translatable("screen.anti_kostka.name", onOff(cfg.matchCustomName)));
        durabilityButton.setMessage(Text.translatable("screen.anti_kostka.durability", onOff(cfg.matchDurability)));
        componentsButton.setMessage(Text.translatable("screen.anti_kostka.components", onOff(cfg.matchAllComponents)));
        enchantButton.active = advanced;
        nameButton.active = advanced;
        durabilityButton.active = advanced;
        componentsButton.active = advanced;
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        // Screen.render already draws the background in 1.21.x, do not call renderBackground again.
        clearButton.setMessage(Text.translatable(System.currentTimeMillis() <= clearArmedUntil
                ? "screen.anti_kostka.clear_confirm" : "screen.anti_kostka.clear"));
        super.render(context, mouseX, mouseY, delta);

        context.drawCenteredTextWithShadow(textRenderer, title, width / 2, 12, 0xFFFFFFFF);

        AntiKostkaConfig cfg = AntiKostkaConfig.get();
        int startX = width / 2 - (9 * SLOT) / 2;
        int slotY = 36;
        Text hoverText = null;
        for (int i = 0; i < 9; i++) {
            int x = startX + i * SLOT;
            context.fill(x, slotY, x + SLOT - 2, slotY + SLOT - 2, 0x80000000);
            Fingerprint fp = cfg.layout[i];
            if (fp != null) {
                Identifier id = Identifier.tryParse(fp.id);
                if (id != null && Registries.ITEM.containsId(id)) {
                    Item item = Registries.ITEM.get(id);
                    context.drawItem(new ItemStack(item), x + 1, slotY + 1);
                } else {
                    context.drawCenteredTextWithShadow(textRenderer, Text.literal("?"), x + 9, slotY + 5, 0xFFFF5555);
                }
            }
            context.drawCenteredTextWithShadow(textRenderer, Text.literal(String.valueOf(i + 1)),
                    x + 9, slotY + SLOT + 2, 0xFFAAAAAA);

            if (mouseX >= x && mouseX < x + SLOT - 2 && mouseY >= slotY && mouseY < slotY + SLOT - 2) {
                hoverText = fp == null ? Text.translatable("screen.anti_kostka.empty_slot") : Text.literal(fp.id);
            }
        }
        if (hoverText != null) {
            context.drawTooltip(textRenderer, hoverText, mouseX, mouseY);
        }
    }

    @Override
    public void close() {
        client.setScreen(parent);
    }
}
