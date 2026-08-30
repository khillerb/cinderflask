package dev.cinderflask.item;

import dev.cinderflask.Cinderflask;
import dev.cinderflask.config.CinderflaskConfig;
import dev.cinderflask.tag.CinderflaskTags;
import net.minecraft.client.item.TooltipContext;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.registry.Registries;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.stat.Stats;
import net.minecraft.text.Text;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Formatting;
import net.minecraft.util.Hand;
import net.minecraft.world.World;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * Right-clicking a mob in {@link CinderflaskTags#SPARK_SOURCE} consumes it and turns this into a
 * {@link CinderflaskItem}.
 *
 * <p>Everything else returns {@link ActionResult#PASS}, so claim and protection mods keep their
 * veto and other mobs behave as normal.
 */
public class EmptyCinderflaskItem extends Item {
    public EmptyCinderflaskItem(Settings settings) {
        super(settings);
    }

    @Override
    public ActionResult useOnEntity(ItemStack stack, PlayerEntity player, LivingEntity entity, Hand hand) {
        if (!entity.getType().isIn(CinderflaskTags.SPARK_SOURCE)) {
            return ActionResult.PASS;
        }

        World world = player.getWorld();
        if (world.isClient) {
            return ActionResult.SUCCESS;
        }

        ServerWorld serverWorld = (ServerWorld) world;
        serverWorld.spawnParticles(ParticleTypes.FLAME,
                entity.getX(), entity.getBodyY(0.5), entity.getZ(), 24, 0.25, 0.3, 0.25, 0.02);
        serverWorld.spawnParticles(ParticleTypes.SMOKE,
                entity.getX(), entity.getBodyY(0.5), entity.getZ(), 8, 0.2, 0.2, 0.2, 0.01);
        world.playSound(null, entity.getX(), entity.getY(), entity.getZ(),
                SoundEvents.ENTITY_BLAZE_SHOOT, SoundCategory.PLAYERS, 0.7F, 1.3F);

        if (CinderflaskConfig.get().consumeSparkSource) {
            entity.discard();
        }

        stack.decrement(1);
        ItemStack sparked = new ItemStack(Cinderflask.CINDERFLASK);

        if (!player.getInventory().insertStack(sparked)) {
            player.dropItem(sparked, false);
        }

        player.incrementStat(Stats.USED.getOrCreateStat(this));
        return ActionResult.CONSUME;
    }

    @Override
    public void appendTooltip(ItemStack stack, @Nullable World world, List<Text> tooltip, TooltipContext context) {
        tooltip.add(Text.translatable("cinderflask.tooltip.lore.cold").formatted(Formatting.GRAY));

        List<Text> sources = sparkSourceNames();
        if (sources.isEmpty()) {
            tooltip.add(Text.translatable("cinderflask.tooltip.spark_sources.none").formatted(Formatting.DARK_GRAY));
            return;
        }

        tooltip.add(Text.translatable("cinderflask.tooltip.spark_sources").formatted(Formatting.DARK_GRAY));
        for (Text source : sources) {
            tooltip.add(Text.literal("  ").append(source).formatted(Formatting.GOLD));
        }
    }

    /** Read from the tag, so a datapack edit shows up in the tooltip without a code change. */
    private static List<Text> sparkSourceNames() {
        List<Text> names = new ArrayList<>();

        Registries.ENTITY_TYPE.getEntryList(CinderflaskTags.SPARK_SOURCE).ifPresent(entries -> {
            for (var entry : entries) {
                EntityType<?> type = entry.value();
                names.add(type.getName().copy());
            }
        });

        return names;
    }
}
