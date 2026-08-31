package dev.cinderflask.player;

import dev.cinderflask.Cinderflask;
import net.fabricmc.fabric.api.networking.v1.PacketByteBufs;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.Identifier;

/**
 * Pushes a player's own palate to their client, because the tooltip and the intake are drawn there
 * and the counts live on the server.
 *
 * <p>Only ever your own. Nobody needs to know what anyone else can taste.
 */
public final class PalateSync {
    public static final Identifier CHANNEL = Cinderflask.id("palate");

    /** What the client believes about its own palate. Replaced wholesale on every update. */
    private static volatile Palate local = Palate.empty();

    private PalateSync() {
    }

    public static Palate local() {
        return local;
    }

    public static void setLocal(Palate palate) {
        local = palate;
    }

    public static void register() {
        ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> {
            if (ServerPlayNetworking.canSend(handler, CHANNEL)) {
                sender.sendPacket(CHANNEL, write(PalateState.get(server).of(handler.player.getUuid())));
            }
        });
    }

    public static void send(ServerPlayerEntity player, Palate palate) {
        if (ServerPlayNetworking.canSend(player, CHANNEL)) {
            ServerPlayNetworking.send(player, CHANNEL, write(palate));
        }
    }

    public static PacketByteBuf write(Palate palate) {
        PacketByteBuf buf = PacketByteBufs.create();
        for (int i = 0; i < 5; i++) {
            buf.writeFloat(palate.tasted(i));
        }
        return buf;
    }

    public static Palate read(PacketByteBuf buf) {
        float[] tasted = new float[5];
        for (int i = 0; i < 5; i++) {
            tasted[i] = buf.readFloat();
        }
        return new Palate(tasted);
    }
}
