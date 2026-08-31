package dev.cinderflask.brew;

import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.block.CampfireBlock;

import java.util.Map;

/**
 * What a flask can be fired against, and what that leaves it as.
 *
 * <p>A lit campfire and an unlit one are different things, so the check is on state rather than on
 * the block alone.
 */
public final class Tempering {
    private static final Map<Block, Temper> SOURCES = Map.ofEntries(
            Map.entry(Blocks.CAMPFIRE, Temper.HERBAL),
            Map.entry(Blocks.FIRE, Temper.EMBER),
            Map.entry(Blocks.LAVA, Temper.EMBER),
            Map.entry(Blocks.MAGMA_BLOCK, Temper.EMBER),
            Map.entry(Blocks.SOUL_CAMPFIRE, Temper.GRAVE),
            Map.entry(Blocks.SOUL_FIRE, Temper.GRAVE),
            Map.entry(Blocks.SOUL_SAND, Temper.GRAVE),
            Map.entry(Blocks.SOUL_SOIL, Temper.GRAVE),
            Map.entry(Blocks.BLUE_ICE, Temper.RIME),
            Map.entry(Blocks.PACKED_ICE, Temper.RIME),
            Map.entry(Blocks.POWDER_SNOW, Temper.RIME),
            Map.entry(Blocks.AMETHYST_BLOCK, Temper.RESONANT),
            Map.entry(Blocks.BUDDING_AMETHYST, Temper.RESONANT),
            Map.entry(Blocks.AMETHYST_CLUSTER, Temper.RESONANT),
            Map.entry(Blocks.SCULK_CATALYST, Temper.ECHO),
            Map.entry(Blocks.SCULK_SHRIEKER, Temper.ECHO),
            Map.entry(Blocks.SCULK_SENSOR, Temper.ECHO));

    private Tempering() {
    }

    /** The temper this block would leave, or null if it is not something you can fire a flask on. */
    public static Temper of(BlockState state) {
        Temper temper = SOURCES.get(state.getBlock());

        if (temper == null) {
            return null;
        }

        // A cold campfire tempers nothing.
        if (state.contains(CampfireBlock.LIT) && !state.get(CampfireBlock.LIT)) {
            return null;
        }

        return temper;
    }
}
