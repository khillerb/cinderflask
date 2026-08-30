package dev.cinderflask.net;

import dev.cinderflask.Cinderflask;
import dev.cinderflask.config.CinderflaskConfig;
import net.fabricmc.fabric.api.networking.v1.PacketByteBufs;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.util.Identifier;

/**
 * Sends the two numbers that drive tooltip maths to joining clients, so a server that has retuned
 * {@code ticksPerOperation} does not leave players reading wrong numbers.
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
            buf.writeVarInt(config.ticksPerOperation);
            buf.writeVarInt(config.maxEmbers);
            sender.sendPacket(CHANNEL, buf);
        });
    }
}
