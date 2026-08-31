package dev.cinderflask.test;

import dev.cinderflask.Cinderflask;
import dev.cinderflask.brew.Brew;
import dev.cinderflask.brew.BrewNbt;
import dev.cinderflask.brew.BrewState;
import dev.cinderflask.brew.Brewing;
import dev.cinderflask.brew.Humours;
import dev.cinderflask.brew.IngredientTable;
import dev.cinderflask.brew.Vessel;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.potion.PotionUtil;
import net.minecraft.potion.Potions;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.test.GameTest;
import net.minecraft.test.GameTestException;
import net.minecraft.test.TestContext;

/** The composing loop: base, ingredients, cork, and what happens when one is left too long. */
public class BrewingLoopTest implements FabricGameTest {
    private static final float EPSILON = 0.05f;

    private static ItemStack flask() {
        return new ItemStack(Cinderflask.CINDERFLASK);
    }

    private static IngredientTable.Entry of(net.minecraft.item.Item item) {
        IngredientTable.Entry entry = IngredientTable.lookup(new ItemStack(item));
        if (entry == null) {
            throw new GameTestException("No brewing entry for " + item
                    + " — the datapack table did not load.");
        }
        return entry;
    }

    private static ItemStack based(ServerWorld world) {
        ItemStack flask = flask();
        Brewing.addBase(flask, of(Items.WATER_BUCKET), 8);
        return flask;
    }

    // -------------------------------------------------------------------------------------------
    // The base
    // -------------------------------------------------------------------------------------------

    @GameTest(templateName = EMPTY_STRUCTURE)
    public void nothingGoesInWithoutABase(TestContext context) {
        ItemStack flask = flask();

        if (Brewing.add(flask, of(Items.BLAZE_POWDER), context.getWorld(), 8)) {
            throw new GameTestException("An ingredient went into a flask with no base in it.");
        }

        if (BrewState.of(flask, context.getWorld()) != BrewState.EMPTY) {
            throw new GameTestException("The flask should still be empty.");
        }

        context.complete();
    }

    @GameTest(templateName = EMPTY_STRUCTURE)
    public void honeyMakesAMilderLongerBrewThanWater(TestContext context) {
        ServerWorld world = context.getWorld();

        ItemStack watered = flask();
        Brewing.addBase(watered, of(Items.WATER_BUCKET), 8);

        ItemStack meaded = flask();
        Brewing.addBase(meaded, of(Items.HONEY_BOTTLE), 8);

        for (ItemStack flask : new ItemStack[]{watered, meaded}) {
            Brewing.add(flask, of(Items.BLAZE_POWDER), world, 8);
            Brewing.cork(flask);
            BrewNbt.stampIfNeeded(flask, world);
        }

        Brew lean = BrewNbt.read(watered, world);
        Brew mead = BrewNbt.read(meaded, world);

        if (lean == null || mead == null) {
            throw new GameTestException("Both flasks should hold a brew.");
        }

        // Honey is not special-cased anywhere. It writes sanguine and melancholic, and this is
        // simply what those two do.
        if (mead.doses() <= lean.doses()) {
            throw new GameTestException("Honey should give more doses: " + mead.doses()
                    + " against " + lean.doses());
        }

        if (mead.durationTicks() <= lean.durationTicks()) {
            throw new GameTestException("Honey should last longer.");
        }

        if (mead.comedown() >= lean.comedown()) {
            throw new GameTestException("Honey should drink softer: comedown " + mead.comedown()
                    + " against " + lean.comedown());
        }

        context.complete();
    }

    // -------------------------------------------------------------------------------------------
    // Corking
    // -------------------------------------------------------------------------------------------

    @GameTest(templateName = EMPTY_STRUCTURE)
    public void aWorkingBrewHasNoAgeAndCannotBeDrunk(TestContext context) {
        ServerWorld world = context.getWorld();
        ItemStack flask = based(world);
        Brewing.add(flask, of(Items.BLAZE_POWDER), world, 8);

        if (BrewState.of(flask, world) != BrewState.WORKING) {
            throw new GameTestException("A flask with a base and an ingredient is working.");
        }

        if (BrewState.of(flask, world).canDrink()) {
            throw new GameTestException("A working brew should not be drinkable.");
        }

        Brew brew = BrewNbt.read(flask, world);
        if (brew == null || brew.phase() != 0) {
            throw new GameTestException("A working brew has no age; got phase "
                    + (brew == null ? "nothing" : brew.phase()));
        }

        context.complete();
    }

    @GameTest(templateName = EMPTY_STRUCTURE)
    public void corkingStartsTheClockExactlyOnce(TestContext context) {
        ServerWorld world = context.getWorld();
        ItemStack flask = based(world);
        Brewing.add(flask, of(Items.BLAZE_POWDER), world, 8);

        Brewing.cork(flask);

        if (!BrewNbt.stampIfNeeded(flask, world)) {
            throw new GameTestException("The first tick after corking should start the clock.");
        }

        if (BrewNbt.stampIfNeeded(flask, world)) {
            throw new GameTestException("The clock restarted; it must only ever be stamped once.");
        }

        if (!BrewState.of(flask, world).canDrink()) {
            throw new GameTestException("A corked brew should be drinkable.");
        }

        context.complete();
    }

    @GameTest(templateName = EMPTY_STRUCTURE)
    public void seasoningRecordsWhatWasFinishedNotWhatWasStarted(TestContext context) {
        ServerWorld world = context.getWorld();

        // The flask is opened with water, which writes nothing, and then filled with choleric. If
        // seasoning were recorded when the brew started it would learn nothing at all.
        ItemStack flask = based(world);
        Brewing.add(flask, of(Items.BLAZE_POWDER), world, 8);
        Brewing.add(flask, of(Items.BLAZE_POWDER), world, 8);
        Brewing.cork(flask);

        Humours learned = Vessel.seasoning(flask);
        if (learned.dominant() != 0 || learned.choleric() < 5) {
            throw new GameTestException("The flask should have learned choleric, got " + learned);
        }

        if (Vessel.brewCount(flask) != 1) {
            throw new GameTestException("One brew should have been recorded, got "
                    + Vessel.brewCount(flask));
        }

        context.complete();
    }

    // -------------------------------------------------------------------------------------------
    // Potions and the table
    // -------------------------------------------------------------------------------------------

    @GameTest(templateName = EMPTY_STRUCTURE)
    public void potionsWriteWhatTheirEffectsAreWorth(TestContext context) {
        IngredientTable.Entry strength = IngredientTable.lookup(
                PotionUtil.setPotion(new ItemStack(Items.POTION), Potions.STRENGTH));

        if (strength == null || strength.humours().dominant() != 0) {
            throw new GameTestException("A potion of Strength should read as choleric, got " + strength);
        }

        IngredientTable.Entry strong = IngredientTable.lookup(
                PotionUtil.setPotion(new ItemStack(Items.POTION), Potions.STRONG_STRENGTH));

        if (strong == null || strong.humours().choleric() <= strength.humours().choleric()) {
            throw new GameTestException("Strength II should write more than Strength I.");
        }

        IngredientTable.Entry poison = IngredientTable.lookup(
                PotionUtil.setPotion(new ItemStack(Items.POTION), Potions.POISON));

        if (poison == null || poison.corruption() <= 0) {
            throw new GameTestException("A harmful potion should bring filth with it.");
        }

        context.complete();
    }

    // -------------------------------------------------------------------------------------------
    // Ruin
    // -------------------------------------------------------------------------------------------

    @GameTest(templateName = EMPTY_STRUCTURE)
    public void aBrewLeftTooLongReadsAsRuined(TestContext context) {
        ServerWorld world = context.getWorld();
        ItemStack flask = flask();

        BrewNbt.seal(flask, new Brew(Humours.of(8, 0, 0, 0), Humours.WHEEL * 12, 0, 6), world, 6);

        if (BrewState.of(flask, world) != BrewState.RUINED) {
            throw new GameTestException("Twelve turns should have thinned it into Sump.");
        }

        if (BrewState.of(flask, world).canDrink()) {
            throw new GameTestException("A ruined brew should not read as drinkable.");
        }

        context.complete();
    }

    @GameTest(templateName = EMPTY_STRUCTURE)
    public void washingKeepsTheVesselAndLosesOnlyTheBrew(TestContext context) {
        ServerWorld world = context.getWorld();
        ItemStack flask = based(world);

        Brewing.add(flask, of(Items.BLAZE_POWDER), world, 8);
        Brewing.cork(flask);

        int brews = Vessel.brewCount(flask);
        int mote = Vessel.moteColour(flask);

        BrewNbt.empty(flask);

        if (BrewState.of(flask, world) != BrewState.EMPTY) {
            throw new GameTestException("The flask should be empty again.");
        }

        if (Vessel.brewCount(flask) != brews || Vessel.moteColour(flask) != mote) {
            throw new GameTestException("The mote and the seasoning belong to the flask, not the brew.");
        }

        context.complete();
    }

    @GameTest(templateName = EMPTY_STRUCTURE)
    public void theVesselCeilingStillHolds(TestContext context) {
        ServerWorld world = context.getWorld();
        ItemStack flask = based(world);

        for (int i = 0; i < 4; i++) {
            Brewing.add(flask, of(Items.PITCHER_PLANT), world, 8);
        }

        Brew brew = BrewNbt.read(flask, world);
        if (brew == null || brew.capacity() > 8 + EPSILON) {
            throw new GameTestException("Capacity ran past the vessel ceiling: "
                    + (brew == null ? "nothing" : brew.capacity()));
        }

        context.complete();
    }
}
