package dev.cinderflask.client;

import dev.cinderflask.Cinderflask;
import dev.cinderflask.config.CinderflaskConfig;
import dev.cinderflask.brew.Vessel;
import dev.cinderflask.item.CinderflaskItem;
import dev.cinderflask.net.ConfigSync;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.rendering.v1.ColorProviderRegistry;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.screen.ingame.HandledScreens;
import net.minecraft.client.item.ModelPredicateProviderRegistry;

public final class CinderflaskClient implements ClientModInitializer {
    // A generated model gives layer N tint index N, and every flask model carries all three layers
    // so these never shift about: shell, liquid, mote.
    private static final int LIQUID_TINT = 1;
    private static final int MOTE_TINT = 2;

    @Override
    public void onInitializeClient() {
        HandledScreens.register(Cinderflask.SCREEN_HANDLER, CinderflaskScreen::new);

        // The item class compiles into the dedicated-server path, so it cannot reference Screen
        // itself.
        CinderflaskItem.detailModifierHeld = Screen::hasShiftDown;

        ModelPredicateProviderRegistry.register(
                Cinderflask.CINDERFLASK,
                Cinderflask.id("fill"),
                (stack, world, entity, seed) -> CinderflaskItem.fillOf(stack));

        // One greyscale liquid sprite serves every brew; the colour comes from the humours, the way
        // vanilla tints potions.
        ColorProviderRegistry.ITEM.register(
                (stack, tintIndex) -> switch (tintIndex) {
                    case LIQUID_TINT ->
                            0xFF000000 | CinderflaskItem.colourOf(stack, MinecraftClient.getInstance().world);
                    case MOTE_TINT -> 0xFF000000 | Vessel.moteColour(stack);
                    default -> 0xFFFFFFFF;
                },
                Cinderflask.CINDERFLASK);

        ClientPlayNetworking.registerGlobalReceiver(ConfigSync.CHANNEL, (client, handler, buf, sender) -> {
            int sipCooldownTicks = buf.readVarInt();
            int ticksPerPhase = buf.readVarInt();
            client.execute(() -> CinderflaskConfig.applyServerValues(sipCooldownTicks, ticksPerPhase));
        });

        // Leaving a server that had retuned the numbers must not leave them stuck on the client.
        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> CinderflaskConfig.restoreLocalValues());
    }
}
