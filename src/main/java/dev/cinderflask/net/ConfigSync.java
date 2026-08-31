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
 * Sends the numbers that change what a brew does to joining clients, so a server that has retuned
 * the clock or the draughts does not leave players reading wrong ages or wrong strengths.
 *
 * <p>{@link #write} and {@link #read} sit beside each other on purpose: they are the two halves of
 * one wire format, and a knob added to one and forgotten in the other would desynchronise silently.
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

    public static void write(PacketByteBuf buf, CinderflaskConfig config) {
        buf.writeVarInt(config.sipCooldownTicks);
        buf.writeVarInt(config.ticksPerPhase);
        buf.writeVarInt(config.maxDraughtsPerDose);
        buf.writeBoolean(config.draughtsAffectPvp);

        CinderflaskConfig.Tuning tuning = config.draughts;
        buf.writeFloat(tuning.potency);
        buf.writeFloat(tuning.comedownSeverity);
        buf.writeFloat(tuning.berserkFromMissingHealth);
        buf.writeFloat(tuning.berserkExposure);
        buf.writeFloat(tuning.ironrootFlatReduction);
        buf.writeFloat(tuning.sapswornLifestealShare);
        buf.writeFloat(tuning.brambleReflectShare);
        buf.writeFloat(tuning.unseenHandBonus);
        buf.writeFloat(tuning.riposteAnswerBonus);
        buf.writeFloat(tuning.quickstepSprintReduction);
        buf.writeFloat(tuning.kelpwineWaterReduction);
        buf.writeFloat(tuning.graveboundUndeadReduction);
        buf.writeFloat(tuning.honeyedAllyHeal);
        buf.writeFloat(tuning.graveboundKillHeal);
        buf.writeFloat(tuning.ashfallSoftening);
        buf.writeFloat(tuning.brittleExposure);
        buf.writeFloat(tuning.bloodlessDrain);
        buf.writeFloat(tuning.plainSightExposure);
        buf.writeVarInt(tuning.emberbloodBurnSeconds);
    }

    /** The other half of {@link #write}. Read in exactly the order it was written. */
    public static CinderflaskConfig read(PacketByteBuf buf) {
        CinderflaskConfig config = new CinderflaskConfig();
        config.sipCooldownTicks = buf.readVarInt();
        config.ticksPerPhase = buf.readVarInt();
        config.maxDraughtsPerDose = buf.readVarInt();
        config.draughtsAffectPvp = buf.readBoolean();

        CinderflaskConfig.Tuning tuning = config.draughts;
        tuning.potency = buf.readFloat();
        tuning.comedownSeverity = buf.readFloat();
        tuning.berserkFromMissingHealth = buf.readFloat();
        tuning.berserkExposure = buf.readFloat();
        tuning.ironrootFlatReduction = buf.readFloat();
        tuning.sapswornLifestealShare = buf.readFloat();
        tuning.brambleReflectShare = buf.readFloat();
        tuning.unseenHandBonus = buf.readFloat();
        tuning.riposteAnswerBonus = buf.readFloat();
        tuning.quickstepSprintReduction = buf.readFloat();
        tuning.kelpwineWaterReduction = buf.readFloat();
        tuning.graveboundUndeadReduction = buf.readFloat();
        tuning.honeyedAllyHeal = buf.readFloat();
        tuning.graveboundKillHeal = buf.readFloat();
        tuning.ashfallSoftening = buf.readFloat();
        tuning.brittleExposure = buf.readFloat();
        tuning.bloodlessDrain = buf.readFloat();
        tuning.plainSightExposure = buf.readFloat();
        tuning.emberbloodBurnSeconds = buf.readVarInt();

        return config;
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

            PacketByteBuf buf = PacketByteBufs.create();
            write(buf, CinderflaskConfig.get());
            sender.sendPacket(CHANNEL, buf);

            if (ServerPlayNetworking.canSend(handler, TABLE_CHANNEL)) {
                sender.sendPacket(TABLE_CHANNEL, table());
            }
        });
    }
}
