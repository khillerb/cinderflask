package dev.cinderflask.client;

import dev.cinderflask.Cinderflask;
import dev.cinderflask.config.CinderflaskConfig;
import dev.cinderflask.brew.Humours;
import dev.cinderflask.brew.IngredientTable;
import dev.cinderflask.brew.Dregs;
import dev.cinderflask.brew.Vessel;
import dev.cinderflask.item.AlmanacItem;
import dev.cinderflask.item.CinderflaskItem;
import dev.cinderflask.item.SumpItem;
import dev.cinderflask.net.ConfigSync;
import dev.cinderflask.player.Palate;
import dev.cinderflask.player.PalateSync;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.rendering.v1.ColorProviderRegistry;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.screen.ingame.HandledScreens;
import net.minecraft.client.item.ModelPredicateProviderRegistry;
import net.minecraft.item.ItemStack;
import net.minecraft.recipe.Ingredient;
import net.minecraft.registry.Registries;

import java.util.ArrayList;
import java.util.List;

public final class CinderflaskClient implements ClientModInitializer {
    // A generated model gives layer N tint index N, and every flask model carries all three layers
    // so these never shift about: shell, liquid, mote.
    private static final int LIQUID_TINT = 1;
    private static final int MOTE_TINT = 2;

    /** Dregs and sump are two-layer: an untinted shape, then the settled brew over it. */
    private static final int SETTLED_TINT = 1;

    @Override
    public void onInitializeClient() {
        HandledScreens.register(Cinderflask.SCREEN_HANDLER, CinderflaskScreen::new);

        // The item class compiles into the dedicated-server path, so it cannot reference Screen
        // itself.
        CinderflaskItem.detailModifierHeld = Screen::hasShiftDown;

        // Same reason: the item lives in the common source set and cannot name a screen.
        AlmanacItem.opener = () -> MinecraftClient.getInstance().setScreen(new AlmanacScreen());

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

        // Dregs and sump both remember a brew, and both used to throw that away at render time and
        // come out as the same olive blob. Same mechanism as the flask's liquid: an untinted shape
        // over a greyscale layer the brew colours.
        ColorProviderRegistry.ITEM.register(
                (stack, tintIndex) -> tintIndex == SETTLED_TINT
                        ? 0xFF000000 | Dregs.humours(stack).colour()
                        : 0xFFFFFFFF,
                Cinderflask.DREGS);

        ColorProviderRegistry.ITEM.register(
                (stack, tintIndex) -> tintIndex == SETTLED_TINT
                        ? 0xFF000000 | SumpItem.colour(stack)
                        : 0xFFFFFFFF,
                Cinderflask.SUMP);

        ClientPlayNetworking.registerGlobalReceiver(ConfigSync.CHANNEL, (client, handler, buf, sender) -> {
            CinderflaskConfig fromServer = ConfigSync.read(buf);
            client.execute(() -> CinderflaskConfig.applyServerValues(fromServer));
        });

        // Datapacks are server-side, so the table arrives over the wire already resolved. Without
        // this the intake would not know what it is allowed to take.
        ClientPlayNetworking.registerGlobalReceiver(ConfigSync.TABLE_CHANNEL, (client, handler, buf, sender) -> {
            List<IngredientTable.Parsed> entries = new ArrayList<>();

            int items = buf.readVarInt();
            for (int i = 0; i < items; i++) {
                ItemStack[] matching = new ItemStack[buf.readVarInt()];
                for (int m = 0; m < matching.length; m++) {
                    matching[m] = new ItemStack(Registries.ITEM.get(buf.readVarInt()));
                }
                entries.add(new IngredientTable.Parsed(Ingredient.ofStacks(matching), null, readEntry(buf)));
            }

            int effects = buf.readVarInt();
            for (int i = 0; i < effects; i++) {
                entries.add(new IngredientTable.Parsed(
                        null, Registries.STATUS_EFFECT.get(buf.readVarInt()), readEntry(buf)));
            }

            client.execute(() -> IngredientTable.replace(entries));
        });

        ClientPlayNetworking.registerGlobalReceiver(PalateSync.CHANNEL, (client, handler, buf, sender) -> {
            Palate palate = PalateSync.read(buf);
            client.execute(() -> PalateSync.setLocal(palate));
        });

        // Leaving a server that had retuned the numbers must not leave them stuck on the client.
        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> {
            CinderflaskConfig.restoreLocalValues();
            PalateSync.setLocal(Palate.empty());
        });
    }

    private static IngredientTable.Entry readEntry(net.minecraft.network.PacketByteBuf buf) {
        Humours humours = new Humours(buf.readFloat(), buf.readFloat(), buf.readFloat(),
                buf.readFloat(), buf.readFloat());
        return new IngredientTable.Entry(humours, buf.readFloat(), buf.readFloat(), buf.readBoolean());
    }
}
