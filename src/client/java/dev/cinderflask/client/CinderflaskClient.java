package dev.cinderflask.client;

import dev.cinderflask.Cinderflask;
import dev.cinderflask.config.CinderflaskConfig;
import dev.cinderflask.item.CinderflaskItem;
import dev.cinderflask.net.ConfigSync;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.screen.ingame.HandledScreens;
import net.minecraft.client.item.ModelPredicateProviderRegistry;
import net.minecraft.util.math.MathHelper;

public final class CinderflaskClient implements ClientModInitializer {
    @Override
    public void onInitializeClient() {
        HandledScreens.register(Cinderflask.SCREEN_HANDLER, CinderflaskScreen::new);

        // The item class compiles into the dedicated-server path, so it cannot reference Screen
        // itself.
        CinderflaskItem.detailModifierHeld = Screen::hasShiftDown;

        ModelPredicateProviderRegistry.register(
                Cinderflask.CINDERFLASK,
                Cinderflask.id("embers"),
                (stack, world, entity, seed) -> {
                    int embers = CinderflaskItem.getEmbers(stack);
                    int max = CinderflaskConfig.get().maxEmbers;

                    if (embers <= 0 || max <= 0) {
                        return 0.0F;
                    }

                    // The cap is ~97 stacks of coal, so a few items round to zero. Floor a
                    // non-empty flask at the first override so it never looks empty.
                    return MathHelper.clamp((float) embers / max, 0.01F, 1.0F);
                });

        ClientPlayNetworking.registerGlobalReceiver(ConfigSync.CHANNEL, (client, handler, buf, sender) -> {
            int ticksPerOperation = buf.readVarInt();
            int maxEmbers = buf.readVarInt();
            client.execute(() -> CinderflaskConfig.applyServerValues(ticksPerOperation, maxEmbers));
        });

        // Leaving a server that had retuned the numbers must not leave them stuck on the client.
        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> CinderflaskConfig.restoreLocalValues());
    }
}
