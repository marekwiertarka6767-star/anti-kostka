package dev.antikostka;

import dev.antikostka.client.Keybinds;
import dev.antikostka.client.StatusHud;
import dev.antikostka.config.AntiKostkaConfig;
import dev.antikostka.restore.HotbarRestorer;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;

public class AntiKostkaClient implements ClientModInitializer {
    @Override
    public void onInitializeClient() {
        AntiKostkaConfig.get(); // load the saved layout at startup
        Keybinds.register();

        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            Keybinds.handle(client);
            HotbarRestorer.tick(client);
        });

        HudRenderCallback.EVENT.register((context, tickCounter) -> StatusHud.render(context));
    }
}
