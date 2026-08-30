package dev.cinderflask.test;

import dev.cinderflask.Cinderflask;
import dev.cinderflask.config.CinderflaskConfig;
import dev.cinderflask.item.CinderflaskItem;
import dev.cinderflask.tag.CinderflaskTags;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.block.Blocks;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.block.entity.FurnaceBlockEntity;
import net.minecraft.block.entity.HopperBlockEntity;
import net.minecraft.entity.EntityType;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.test.GameTest;
import net.minecraft.test.GameTestException;
import net.minecraft.test.TestContext;
import net.minecraft.util.math.BlockPos;

/** Covers the claims in the README: exact cost per ignition, the empty gate, and the intake filter. */
public class CinderflaskGameTest implements FabricGameTest {
    private static final BlockPos FURNACE_POS = new BlockPos(1, 2, 1);

    private static final int INPUT_SLOT = 0;
    private static final int FUEL_SLOT = 1;

    /** One ignition costs exactly one operation, and the flask stays in the slot. */
    @GameTest(templateName = EMPTY_STRUCTURE)
    public void flaskSpendsExactlyOneOperationPerIgnition(TestContext context) {
        int perOperation = CinderflaskConfig.get().ticksPerOperation;
        int startingEmbers = perOperation * 10;

        FurnaceBlockEntity furnace = placeLoadedFurnace(context, startingEmbers);

        context.runAtTick(5L, () -> {
            ItemStack fuel = furnace.getStack(FUEL_SLOT);

            if (!(fuel.getItem() instanceof CinderflaskItem)) {
                throw new GameTestException("The flask left the fuel slot; it should be its own recipe remainder.");
            }

            int spent = startingEmbers - CinderflaskItem.getEmbers(fuel);

            if (spent != perOperation) {
                throw new GameTestException(
                        "Expected exactly " + perOperation + " ticks spent on ignition, got " + spent + ".");
            }

            context.complete();
        });
    }

    /** A flask that cannot cover a whole operation must not light the furnace at all. */
    @GameTest(templateName = EMPTY_STRUCTURE)
    public void underfilledFlaskDoesNotBurn(TestContext context) {
        int embers = CinderflaskConfig.get().ticksPerOperation - 1;
        FurnaceBlockEntity furnace = placeLoadedFurnace(context, embers);

        context.runAtTick(20L, () -> {
            ItemStack fuel = furnace.getStack(FUEL_SLOT);

            if (CinderflaskItem.getEmbers(fuel) != embers) {
                throw new GameTestException("An under-filled flask was drained; it should not burn at all.");
            }

            if (!furnace.getStack(INPUT_SLOT).isOf(Items.RAW_IRON)) {
                throw new GameTestException("The furnace smelted something without paying for it.");
            }

            context.complete();
        });
    }

    /** Vanilla only allows the fuel slot to be extracted when it holds a bucket. Pin that down. */
    @GameTest(templateName = EMPTY_STRUCTURE)
    public void hopperCannotDrainTheFlaskOut(TestContext context) {
        int embers = CinderflaskConfig.get().ticksPerOperation * 10;
        FurnaceBlockEntity furnace = placeLoadedFurnace(context, embers);

        BlockPos hopperPos = FURNACE_POS.down();
        context.setBlockState(hopperPos, Blocks.HOPPER.getDefaultState());
        context.setBlockState(hopperPos.down(), Blocks.CHEST.getDefaultState());

        context.runAtTick(40L, () -> {
            if (!(furnace.getStack(FUEL_SLOT).getItem() instanceof CinderflaskItem)) {
                throw new GameTestException("A hopper pulled the flask out of the fuel slot.");
            }

            BlockEntity below = context.getBlockEntity(hopperPos);

            if (below instanceof HopperBlockEntity hopper) {
                for (int slot = 0; slot < hopper.size(); slot++) {
                    if (hopper.getStack(slot).getItem() instanceof CinderflaskItem) {
                        throw new GameTestException("The flask ended up in the hopper.");
                    }
                }
            }

            context.complete();
        });
    }

    /** The intake filter: real fuel in, containers and other flasks out. */
    @GameTest(templateName = EMPTY_STRUCTURE)
    public void intakeAcceptsFuelAndRejectsContainers(TestContext context) {
        assertValid(new ItemStack(Items.COAL), true, "coal");
        assertValid(new ItemStack(Items.BLAZE_ROD), true, "a blaze rod");
        assertValid(new ItemStack(Items.LAVA_BUCKET), false, "a lava bucket");
        assertValid(new ItemStack(Items.DIAMOND), false, "a diamond");
        assertValid(new ItemStack(Cinderflask.CINDERFLASK), false, "another flask");

        ItemStack flask = new ItemStack(Cinderflask.CINDERFLASK);
        ItemStack coal = new ItemStack(Items.COAL, 64);
        CinderflaskItem.addFuel(flask, coal);

        if (!coal.isEmpty()) {
            throw new GameTestException("A stack of coal should fit under the cap in one go.");
        }

        if (CinderflaskItem.getEmbers(flask) != 64 * 1600) {
            throw new GameTestException(
                    "Expected 102,400 ticks from a stack of coal, got " + CinderflaskItem.getEmbers(flask) + ".");
        }

        context.complete();
    }

    /** The spark tag must resolve, or the empty flask is a dead end. */
    @GameTest(templateName = EMPTY_STRUCTURE)
    public void sparkSourceTagResolves(TestContext context) {
        if (!EntityType.BLAZE.isIn(CinderflaskTags.SPARK_SOURCE)) {
            throw new GameTestException("Blaze is missing from #cinderflask:spark_source.");
        }

        if (EntityType.COW.isIn(CinderflaskTags.SPARK_SOURCE)) {
            throw new GameTestException("Cow should not be a spark source.");
        }

        context.complete();
    }

    private static void assertValid(ItemStack stack, boolean expected, String description) {
        if (CinderflaskItem.isValidFuel(stack) != expected) {
            throw new GameTestException("The intake should " + (expected ? "accept " : "reject ") + description + ".");
        }
    }

    /** A lit furnace holding one raw iron and a flask with {@code embers} ticks in it. */
    private static FurnaceBlockEntity placeLoadedFurnace(TestContext context, int embers) {
        context.setBlockState(FURNACE_POS, Blocks.FURNACE.getDefaultState());
        BlockEntity blockEntity = context.getBlockEntity(FURNACE_POS);

        if (!(blockEntity instanceof FurnaceBlockEntity furnace)) {
            throw new GameTestException("Could not place a furnace for the test.");
        }

        ItemStack flask = new ItemStack(Cinderflask.CINDERFLASK);
        CinderflaskItem.setEmbers(flask, embers);

        furnace.setStack(INPUT_SLOT, new ItemStack(Items.RAW_IRON, 8));
        furnace.setStack(FUEL_SLOT, flask);
        furnace.markDirty();

        return furnace;
    }
}
