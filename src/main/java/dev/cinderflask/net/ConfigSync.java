package dev.cinderflask.net;

import dev.cinderflask.Cinderflask;
import dev.cinderflask.brew.IngredientTable;
import dev.cinderflask.config.CinderflaskConfig;
import net.minecraft.entity.effect.StatusEffect;
import net.minecraft.item.ItemStack;
import net.minecraft.recipe.Ingredient;
import net.minecraft.registry.Registries;

import java.util.ArrayList;
import java.util.List;
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

    /**
     * The brewing table, resolved down to plain items and effects. Datapacks are server-side, and
     * without this the intake on a client would not know what it is allowed to take.
     */
    public static final Identifier TABLE_CHANNEL = Cinderflask.id("brewing_table");

    private ConfigSync() {
    }

    /** Flattens the table into item and effect ids the client can rebuild without a datapack. */
    public static PacketByteBuf table() {
        PacketByteBuf buf = PacketByteBufs.create();
        List<IngredientTable.Parsed> entries = IngredientTable.forSync();

        List<IngredientTable.Parsed> items = new ArrayList<>();
        List<IngredientTable.Parsed> effects = new ArrayList<>();

        for (IngredientTable.Parsed entry : entries) {
            (entry.effect() != null ? effects : items).add(entry);
        }

        buf.writeVarInt(items.size());
        for (IngredientTable.Parsed entry : items) {
            // Ingredients are resolved here rather than shipped as tags, so the client does not have
            // to agree about tag contents to agree about the table.
            ItemStack[] matching = entry.ingredient().getMatchingStacks();
            buf.writeVarInt(matching.length);
            for (ItemStack stack : matching) {
                buf.writeVarInt(Registries.ITEM.getRawId(stack.getItem()));
            }
            writeEntry(buf, entry.entry());
        }

        buf.writeVarInt(effects.size());
        for (IngredientTable.Parsed entry : effects) {
            buf.writeVarInt(Registries.STATUS_EFFECT.getRawId(entry.effect()));
            writeEntry(buf, entry.entry());
        }

        return buf;
    }

    private static void writeEntry(PacketByteBuf buf, IngredientTable.Entry entry) {
        buf.writeFloat(entry.humours().choleric());
        buf.writeFloat(entry.humours().melancholic());
        buf.writeFloat(entry.humours().sanguine());
        buf.writeFloat(entry.humours().phlegmatic());
        buf.writeFloat(entry.humours().quintessence());
        buf.writeFloat(entry.body());
        buf.writeFloat(entry.corruption());
        buf.writeBoolean(entry.base());
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

            if (ServerPlayNetworking.canSend(handler, TABLE_CHANNEL)) {
                sender.sendPacket(TABLE_CHANNEL, table());
            }
        });
    }
}
