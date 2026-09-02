package dev.cinderflask.test;

import dev.cinderflask.Cinderflask;
import dev.cinderflask.config.CinderflaskConfig;
import dev.cinderflask.net.ConfigSync;
import dev.cinderflask.brew.Brew;
import dev.cinderflask.brew.BrewNbt;
import dev.cinderflask.brew.Brewing;
import dev.cinderflask.brew.Humours;
import dev.cinderflask.brew.IngredientTable;
import dev.cinderflask.brew.Temper;
import dev.cinderflask.brew.Tempering;
import dev.cinderflask.brew.Vessel;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.fabricmc.fabric.api.networking.v1.PacketByteBufs;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.passive.PigEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.block.Blocks;
import net.minecraft.item.Items;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.test.GameTest;
import net.minecraft.test.GameTestException;
import net.minecraft.test.TestContext;
import net.minecraft.util.math.BlockPos;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
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

        Brewing.addBase(flask, IngredientTable.lookup(new ItemStack(Items.WATER_BUCKET)), 8);
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

        // Minimum capacity of 1, plus water's 1 and the honeycomb's 2.
        if (Math.abs(brew.capacity() - 4) > EPSILON) {
            throw new GameTestException("Expected capacity 4, got " + brew.capacity());
        }

        context.complete();
    }

    @GameTest(templateName = EMPTY_STRUCTURE)
    public void bodyIsCappedByTheVesselCeiling(TestContext context) {
        ServerWorld world = context.getWorld();
        ItemStack flask = flask();

        Brewing.addBase(flask, IngredientTable.lookup(new ItemStack(Items.WATER_BUCKET)), 8);

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

        Brewing.addBase(flask, IngredientTable.lookup(new ItemStack(Items.WATER_BUCKET)), 8);

        // No body beyond the base, so capacity stays low while the essence keeps climbing.
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
    // The vessel
    // -------------------------------------------------------------------------------------------

    @GameTest(templateName = EMPTY_STRUCTURE)
    public void aMoteTakesTheCreaturesColourAndOnlyOnce(TestContext context) {
        ItemStack flask = flask();
        PigEntity pig = context.spawnMob(EntityType.PIG, new BlockPos(1, 2, 1));

        if (Vessel.hasMote(flask)) {
            throw new GameTestException("A fresh flask should have no mote.");
        }

        if (!Vessel.catchMote(flask, pig)) {
            throw new GameTestException("A pig has a spawn egg, so it should have a mote to give.");
        }

        if (!"minecraft:pig".equals(String.valueOf(Vessel.moteOrigin(flask)))) {
            throw new GameTestException("The origin should have been recorded, got "
                    + Vessel.moteOrigin(flask));
        }

        if (Vessel.moteColour(flask) == Vessel.UNCAUGHT_MOTE) {
            throw new GameTestException("The mote should have taken the pig's colour.");
        }

        // The choice is permanent, which is the whole cost of it.
        if (Vessel.catchMote(flask, context.spawnMob(EntityType.COW, new BlockPos(3, 2, 1)))) {
            throw new GameTestException("A flask should only ever hold one mote.");
        }

        context.complete();
    }

    @GameTest(templateName = EMPTY_STRUCTURE)
    public void everyMoteIsBrightEnoughToSee(TestContext context) {
        // A warden's spawn egg is nearly black, and an unlit spirit inside a dark brew is invisible.
        ItemStack flask = flask();
        Vessel.catchMote(flask, context.spawnMob(EntityType.WARDEN, new BlockPos(1, 2, 1)));

        int colour = Vessel.moteColour(flask);
        float luminance = (0.299f * ((colour >> 16) & 0xFF)
                + 0.587f * ((colour >> 8) & 0xFF)
                + 0.114f * (colour & 0xFF)) / 255f;

        if (luminance < 0.4f) {
            throw new GameTestException("A warden's mote came out at luminance " + luminance
                    + "; it would vanish inside a dark brew.");
        }

        context.complete();
    }

    @GameTest(templateName = EMPTY_STRUCTURE)
    public void temperingReadsTheBlockAndSticks(TestContext context) {
        if (Tempering.of(Blocks.LAVA.getDefaultState()) != Temper.EMBER) {
            throw new GameTestException("Lava should leave an ember temper.");
        }

        if (Tempering.of(Blocks.BLUE_ICE.getDefaultState()) != Temper.RIME) {
            throw new GameTestException("Blue ice should leave a rime temper.");
        }

        if (Tempering.of(Blocks.DIRT.getDefaultState()) != null) {
            throw new GameTestException("Dirt is not something you fire a flask against.");
        }

        // A campfire that is not lit tempers nothing.
        if (Tempering.of(Blocks.CAMPFIRE.getDefaultState().with(
                net.minecraft.block.CampfireBlock.LIT, false)) != null) {
            throw new GameTestException("An unlit campfire should not temper.");
        }

        context.complete();
    }

    @GameTest(templateName = EMPTY_STRUCTURE)
    public void aFlaskLeansTowardsWhatItHasHeld(TestContext context) {
        ItemStack flask = flask();

        if (!Vessel.drift(flask).isEmpty()) {
            throw new GameTestException("A new flask should lean nowhere.");
        }

        for (int i = 0; i < Vessel.SEASONING_CAP; i++) {
            Vessel.record(flask, Humours.of(8, 0, 0, 0));
        }

        Humours drift = Vessel.drift(flask);
        if (drift.dominant() != 0 || drift.choleric() <= 0) {
            throw new GameTestException("Thirty choleric brews should have seasoned the flask choleric, got "
                    + drift);
        }

        if (drift.choleric() > 4) {
            throw new GameTestException("Seasoning should nudge a brew, not dictate it; drift was "
                    + drift.choleric());
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
    public void aSipReachesTheDrinker(TestContext context) {
        ServerWorld world = context.getWorld();

        ItemStack flask = flask();
        BrewNbt.seal(flask, Brew.fresh(Humours.of(8, 0, 0, 0), 4), world, 4);

        PigEntity drinker = context.spawnMob(EntityType.PIG, new BlockPos(1, 2, 1));
        flask.finishUsing(world, drinker);

        // Which effects a brew produces is DraughtTest's business. All this asks is that the intake,
        // the effect list and a real drinker are still joined up to one another.
        if (drinker.getStatusEffects().isEmpty()) {
            throw new GameTestException("A sip applied nothing at all.");
        }

        context.complete();
    }
    @GameTest(templateName = EMPTY_STRUCTURE)
    public void theConfigSurvivesTheRoundTripToAClient(TestContext context) {
        CinderflaskConfig sent = new CinderflaskConfig();
        sent.sipCooldownTicks = 37;
        sent.ticksPerPhase = 4321;
        sent.maxDraughtsPerDose = 2;
        sent.draughtsAffectPvp = false;

        // A distinct value in every tuning field, so a knob the packet forgot to write comes back as
        // its default and fails below, rather than matching by coincidence.
        List<Field> knobs = knobs();
        try {
            float seed = 0.11f;
            for (Field knob : knobs) {
                if (knob.getType() == float.class) {
                    knob.setFloat(blockOf(sent, knob), seed += 0.07f);
                } else if (knob.getType() == int.class) {
                    knob.setInt(blockOf(sent, knob), 9);
                }
            }
        } catch (IllegalAccessException e) {
            throw new GameTestException("Could not seed the tuning block: " + e);
        }

        PacketByteBuf buf = PacketByteBufs.create();
        ConfigSync.write(buf, sent);
        CinderflaskConfig got = ConfigSync.read(buf);

        if (buf.readableBytes() != 0) {
            throw new GameTestException("The reader left " + buf.readableBytes()
                    + " bytes on the wire, so the two halves disagree.");
        }

        if (got.sipCooldownTicks != sent.sipCooldownTicks || got.ticksPerPhase != sent.ticksPerPhase
                || got.maxDraughtsPerDose != sent.maxDraughtsPerDose
                || got.draughtsAffectPvp != sent.draughtsAffectPvp) {
            throw new GameTestException("The top-level config did not survive the round trip.");
        }

        try {
            for (Field knob : knobs) {
                if (!knob.get(blockOf(sent, knob)).equals(knob.get(blockOf(got, knob)))) {
                    throw new GameTestException(knob.getName()
                            + " did not survive the round trip: sent " + knob.get(blockOf(sent, knob))
                            + ", got " + knob.get(blockOf(got, knob)));
                }
            }
        } catch (IllegalAccessException e) {
            throw new GameTestException("Could not read the tuning block back: " + e);
        }

        context.complete();
    }

    /** Every tunable field, so adding one to the config and not to the packet fails this test. */
    private static List<Field> knobs() {
        List<Field> fields = new ArrayList<>();

        for (Class<?> block : new Class<?>[]{
                CinderflaskConfig.Tuning.class, CinderflaskConfig.Inflections.class}) {
            for (Field field : block.getDeclaredFields()) {
                if (!field.isSynthetic() && !Modifier.isStatic(field.getModifiers())) {
                    fields.add(field);
                }
            }
        }

        return fields;
    }

    /** Which block a knob belongs to, so the round trip can seed and compare it. */
    private static Object blockOf(CinderflaskConfig config, Field knob) {
        return knob.getDeclaringClass() == CinderflaskConfig.Tuning.class
                ? config.draughts
                : config.inflections;
    }
}
