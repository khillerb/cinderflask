package dev.cinderflask.test;

import dev.cinderflask.Cinderflask;
import dev.cinderflask.brew.Brew;
import dev.cinderflask.brew.BrewNbt;
import dev.cinderflask.brew.BrewEffects;
import dev.cinderflask.brew.Brewing;
import dev.cinderflask.brew.Humours;
import dev.cinderflask.brew.IngredientTable;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.passive.PigEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.test.GameTest;
import net.minecraft.test.GameTestException;
import net.minecraft.test.TestContext;
import net.minecraft.util.math.BlockPos;

import java.util.List;

/** Covers the parts of the brew that need a world: the clock, the intake, and spending a dose. */
public class CinderflaskGameTest implements FabricGameTest {
    private static final float EPSILON = 0.05f;

    private static ItemStack flask() {
        return new ItemStack(Cinderflask.CINDERFLASK);
    }

    // -------------------------------------------------------------------------------------------
    // The intake
    // -------------------------------------------------------------------------------------------

    @GameTest(templateName = EMPTY_STRUCTURE)
    public void ingredientsFoldIntoTheBrew(TestContext context) {
        ServerWorld world = context.getWorld();
        ItemStack flask = flask();

        Brewing.add(flask, IngredientTable.lookup(new ItemStack(Items.HONEYCOMB)), world, 8);
        Brewing.add(flask, IngredientTable.lookup(new ItemStack(Items.BLAZE_POWDER)), world, 8);

        Brew brew = BrewNbt.read(flask, world);
        if (brew == null) {
            throw new GameTestException("Nothing was folded into the flask.");
        }

        if (Math.abs(brew.sealed().choleric() - 3) > EPSILON) {
            throw new GameTestException("Blaze powder should have written 3 choleric, wrote "
                    + brew.sealed().choleric());
        }

        // Minimum capacity of 1 plus the honeycomb's 2.
        if (Math.abs(brew.capacity() - 3) > EPSILON) {
            throw new GameTestException("Expected capacity 3, got " + brew.capacity());
        }

        context.complete();
    }

    @GameTest(templateName = EMPTY_STRUCTURE)
    public void bodyIsCappedByTheVesselCeiling(TestContext context) {
        ServerWorld world = context.getWorld();
        ItemStack flask = flask();

        // Three pitcher plants is 18 body against a ceiling of 8.
        for (int i = 0; i < 3; i++) {
            Brewing.add(flask, IngredientTable.lookup(new ItemStack(Items.PITCHER_PLANT)), world, 8);
        }

        Brew brew = BrewNbt.read(flask, world);
        if (brew == null || brew.capacity() > 8 + EPSILON) {
            throw new GameTestException("The vessel ceiling did not hold; capacity reached "
                    + (brew == null ? "nothing" : brew.capacity()));
        }

        context.complete();
    }

    @GameTest(templateName = EMPTY_STRUCTURE)
    public void crammingTooMuchEssenceSpoilsIt(TestContext context) {
        ServerWorld world = context.getWorld();
        ItemStack flask = flask();

        // No body at all, so capacity stays at the minimum while the essence keeps climbing.
        for (int i = 0; i < 4; i++) {
            Brewing.add(flask, IngredientTable.lookup(new ItemStack(Items.BLAZE_POWDER)), world, 8);
        }

        Brew brew = BrewNbt.read(flask, world);
        if (brew == null || !brew.isSpoiled()) {
            throw new GameTestException("Twelve choleric in a vessel of one should have spoiled.");
        }

        context.complete();
    }

    // -------------------------------------------------------------------------------------------
    // The clock
    // -------------------------------------------------------------------------------------------

    @GameTest(templateName = EMPTY_STRUCTURE)
    public void ageIsRecoveredFromTheSealTimeRatherThanTicked(TestContext context) {
        ServerWorld world = context.getWorld();
        ItemStack flask = flask();

        // Sealing a brew that is already two phases old backdates the seal time, which is the same
        // mechanism that makes a flask age untouched in a chest.
        Brew aged = new Brew(Humours.of(8, 0, 0, 0), 2, 0, 6);
        BrewNbt.seal(flask, aged, world, aged.doses());

        Brew read = BrewNbt.read(flask, world);
        if (read == null || Math.abs(read.phase() - 2) > EPSILON) {
            throw new GameTestException("Expected phase 2 back out, got "
                    + (read == null ? "nothing" : read.phase()));
        }

        // Two phases on, a choleric brew has turned sanguine.
        if (read.current().dominant() != 2) {
            throw new GameTestException("Two phases should have carried choleric round to sanguine.");
        }

        context.complete();
    }

    // -------------------------------------------------------------------------------------------
    // Drinking
    // -------------------------------------------------------------------------------------------

    @GameTest(templateName = EMPTY_STRUCTURE)
    public void sippingSpendsOneDoseAndKeepsTheFlask(TestContext context) {
        ServerWorld world = context.getWorld();
        ItemStack flask = flask();

        BrewNbt.seal(flask, Brew.fresh(Humours.of(6, 0, 0, 0), 6), world, 6);

        PigEntity drinker = context.spawnMob(EntityType.PIG, new BlockPos(1, 2, 1));
        flask.finishUsing(world, drinker);

        if (BrewNbt.doses(flask) != 5) {
            throw new GameTestException("Expected 5 doses left, got " + BrewNbt.doses(flask));
        }

        if (!flask.isOf(Cinderflask.CINDERFLASK)) {
            throw new GameTestException("The flask should survive being drunk from.");
        }

        if (drinker.getStatusEffects().isEmpty()) {
            throw new GameTestException("Drinking applied no effect at all.");
        }

        context.complete();
    }

    @GameTest(templateName = EMPTY_STRUCTURE)
    public void theLastDoseEmptiesTheFlask(TestContext context) {
        ServerWorld world = context.getWorld();
        ItemStack flask = flask();

        BrewNbt.seal(flask, Brew.fresh(Humours.of(6, 0, 0, 0), 6), world, 1);

        PigEntity drinker = context.spawnMob(EntityType.PIG, new BlockPos(1, 2, 1));
        flask.finishUsing(world, drinker);

        if (BrewNbt.hasBrew(flask) || BrewNbt.doses(flask) != 0) {
            throw new GameTestException("The flask should be empty after its last dose.");
        }

        context.complete();
    }

    @GameTest(templateName = EMPTY_STRUCTURE)
    public void aLopsidedBrewLeavesAComedownAndABalancedOneDoesNot(TestContext context) {
        ServerWorld world = context.getWorld();

        ItemStack lopsided = flask();
        BrewNbt.seal(lopsided, Brew.fresh(Humours.of(8, 0, 0, 0), 4), world, 4);

        ItemStack rounded = flask();
        BrewNbt.seal(rounded, Brew.fresh(Humours.of(2, 2, 2, 2), 4), world, 4);

        PigEntity a = context.spawnMob(EntityType.PIG, new BlockPos(1, 2, 1));
        PigEntity b = context.spawnMob(EntityType.PIG, new BlockPos(3, 2, 1));

        lopsided.finishUsing(world, a);
        rounded.finishUsing(world, b);

        // Asserted on what the brew produces rather than on what the drinker kept: entities are
        // allowed to refuse effects (undead turn down regeneration), and that is not this rule.
        Brew sharp = BrewNbt.read(lopsided, world);
        Brew even = BrewNbt.read(rounded, world);

        if (sharp == null || even == null) {
            throw new GameTestException("Both flasks should still hold a brew.");
        }

        List<StatusEffectInstance> sharpEffects = BrewEffects.of(sharp);
        if (sharpEffects.size() < 2) {
            throw new GameTestException("A single-humour brew should carry its opposite as a comedown, got "
                    + sharpEffects);
        }

        if (!BrewEffects.of(even).isEmpty() && BrewEffects.of(even).size() != 1) {
            throw new GameTestException("An even brew has no opposite, so it should drink clean, got "
                    + BrewEffects.of(even));
        }

        // And the dose really did reach the drinkers.
        if (a.getStatusEffects().isEmpty() || b.getStatusEffects().isEmpty()) {
            throw new GameTestException("A sip applied nothing at all.");
        }

        context.complete();
    }
}
