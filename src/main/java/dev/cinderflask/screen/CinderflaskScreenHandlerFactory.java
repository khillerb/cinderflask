package dev.cinderflask.screen;

import net.fabricmc.fabric.api.screenhandler.v1.ExtendedScreenHandlerFactory;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.screen.ScreenHandler;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.Hand;

/**
 * Sends the hand rather than the stack, so both sides resolve {@code player.getStackInHand(hand)}
 * and the screen reads the live flask instead of a copy taken when it opened.
 */
public record CinderflaskScreenHandlerFactory(Hand hand) implements ExtendedScreenHandlerFactory {
    @Override
    public void writeScreenOpeningData(ServerPlayerEntity player, PacketByteBuf buf) {
        buf.writeByte(hand == Hand.MAIN_HAND ? 0 : 1);
    }

    @Override
    public Text getDisplayName() {
        return Text.translatable("item.cinderflask.cinderflask");
    }

    @Override
    public ScreenHandler createMenu(int syncId, PlayerInventory playerInventory, PlayerEntity player) {
        return new CinderflaskScreenHandler(syncId, playerInventory, hand);
    }
}
