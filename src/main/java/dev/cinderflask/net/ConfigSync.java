package dev.cinderflask.net;

import dev.cinderflask.Cinderflask;
import dev.cinderflask.config.CinderflaskConfig;
import net.fabricmc.fabric.api.networking.v1.PacketByteBufs;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.util.Identifier;

/**
 * Sends the two numbers that change what a brew does to joining clients, so a server that has
 * retuned the clock does not leave players reading wrong ages.
 */
public final class ConfigSync {
    public static final Identifier CHANNEL = Cinderflask.id("config");

    private ConfigSync() {
    }

    public static void register() {
        ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> {
            if (!ServerPlayNetworking.canSend(handler, CHANNEL)) {
                return;
            }

            CinderflaskConfig config = CinderflaskConfig.get();
            PacketByteBuf buf = PacketByteBufs.create();
            buf.writeVarInt(config.sipCooldownTicks);
            buf.writeVarInt(config.ticksPerPhase);
            sender.sendPacket(CHANNEL, buf);
        });
    }
}
