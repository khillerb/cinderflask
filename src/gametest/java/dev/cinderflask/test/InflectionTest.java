package dev.cinderflask.test;

import dev.cinderflask.Cinderflask;
import dev.cinderflask.brew.Brew;
import dev.cinderflask.brew.BrewEffects;
import dev.cinderflask.brew.BrewNbt;
import dev.cinderflask.brew.Delivery;
import dev.cinderflask.brew.Humours;
import dev.cinderflask.brew.Inflection;
import dev.cinderflask.brew.Landmarks;
import dev.cinderflask.brew.Vessel;
import dev.cinderflask.brew.VesselName;
import dev.cinderflask.effect.CorruptDraughts;
import dev.cinderflask.effect.DraughtEffect;
import dev.cinderflask.effect.Draughts;
import dev.cinderflask.effect.Unspent;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.entity.AreaEffectCloudEntity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.passive.PigEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.test.GameTest;
import net.minecraft.test.GameTestException;
import net.minecraft.test.TestContext;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;

import java.util.EnumSet;
import java.util.List;

/**
 * The second half of the wheel, and the thresholds that reach it.
 *
 * <p>The point of an inflection is that several of them can be true at once, so most of what is
 * worth protecting here is that they stay independently reachable and that crossing one does not
 * quietly cross another.
 */
public class InflectionTest implements FabricGameTest {
    private static ItemStack flask() {
        return new ItemStack(Cinderflask.CINDERFLASK);
    }

    private static Brew foul(Humours humours, float capacity) {
        return new Brew(humours, 0, 0.9f, capacity);
    }

    // -------------------------------------------------------------------------------------------
    // The corrupt half
    // -------------------------------------------------------------------------------------------

    @GameTest(templateName = EMPTY_STRUCTURE)
    public void everyLandmarkHasATwinWearingItsOwnColour(TestContext context) {
        for (Landmarks.Landmark landmark : Landmarks.all()) {
            DraughtEffect twin = CorruptDraughts.of(landmark);

            if (twin == null) {
                throw new GameTestException("No corrupt twin for " + landmark.id());
            }

            if (!twin.landmark().id().equals(landmark.id())) {
                throw new GameTestException(landmark.id() + " has a twin that belongs elsewhere.");
            }

            // Soured, so the icon reads as its counterpart gone bad rather than as something new.
            int clean = landmark.target().colour();
            if (twin.getColor() == clean) {
                throw new GameTestException("The twin of " + landmark.id() + " is not soured at all.");
            }
        }

        if (CorruptDraughts.all().size() != Landmarks.all().size()) {
            throw new GameTestException("There should be exactly one twin to a landmark.");
        }

        context.complete();
    }

    @GameTest(templateName = EMPTY_STRUCTURE)
    public void theSameCoordinateGivesCleanOrFoulDependingOnlyOnFilth(TestContext context) {
        Humours choleric = Humours.of(8, 0, 0, 0);

        List<StatusEffectInstance> clean = BrewEffects.of(Brew.fresh(choleric, 6));
        List<StatusEffectInstance> rotten = BrewEffects.of(foul(choleric, 6));

        if (!holds(clean, Draughts.DEADMANS_VIGOUR)) {
            throw new GameTestException("A clean choleric brew should give Deadman's Vigour.");
        }

        if (holds(rotten, Draughts.DEADMANS_VIGOUR)) {
            throw new GameTestException("A foul brew should not hand back the clean draught.");
        }

        if (!holds(rotten, CorruptDraughts.DEADMANS_HUNGER)) {
            throw new GameTestException("A foul choleric brew should give Deadman's Hunger, got "
                    + rotten);
        }

        context.complete();
    }

    private static boolean holds(List<StatusEffectInstance> effects,
                                 net.minecraft.entity.effect.StatusEffect wanted) {
        return effects.stream().anyMatch(effect -> effect.getEffectType() == wanted);
    }

    // -------------------------------------------------------------------------------------------
    // The thresholds
    // -------------------------------------------------------------------------------------------

    @GameTest(templateName = EMPTY_STRUCTURE)
    public void everyInflectionCanBeReached(TestContext context) {
        EnumSet<Inflection> seen = EnumSet.noneOf(Inflection.class);

        for (Object[] attempt : reachable(context)) {
            seen.addAll(Inflection.of((ItemStack) attempt[0], (Brew) attempt[1]));
        }

        EnumSet<Inflection> missing = EnumSet.allOf(Inflection.class);
        missing.removeAll(seen);

        if (!missing.isEmpty()) {
            throw new GameTestException("No brew in this test reaches " + missing
                    + ", so those thresholds are unreachable or wrongly tuned");
        }

        context.complete();
    }

    /** One flask-and-brew pair per thing worth crossing. */
    private static List<Object[]> reachable(TestContext context) {
        ItemStack aetherglass = new ItemStack(Cinderflask.AETHERGLASS_CINDERFLASK);

        ItemStack storied = flask();
        for (int i = 0; i < VesselName.THRESHOLD; i++) {
            Vessel.record(storied, Humours.of(8, 0, 0, 0));
        }

        ItemStack omened = flask();
        Vessel.catchMote(omened, context.spawnMob(EntityType.ALLAY, new BlockPos(1, 2, 1)));

        return List.of(
                // CONCENTRATED and VOLATILE and ACRID: a lot of choleric in a small vessel.
                new Object[]{flask(), Brew.fresh(Humours.of(20, 0, 0, 0), 4)},
                // LEVEL: even across the four.
                new Object[]{flask(), Brew.fresh(Humours.of(6, 6, 6, 6), 24)},
                // EXACT: sitting on a landmark.
                new Object[]{flask(), Brew.fresh(Humours.of(8, 0, 0, 0), 8)},
                // FAR: reach past the threshold.
                new Object[]{flask(), Brew.fresh(new Humours(4, 0, 0, 0, 12), 8)},
                // FOUL and DEEP: filthy and old.
                new Object[]{flask(), new Brew(Humours.of(8, 0, 0, 0), 12, 0.9f, 8)},
                // LEADEN, LUSH, BRACKISH: steeped in each of the other three.
                new Object[]{flask(), Brew.fresh(Humours.of(0, 20, 0, 0), 12)},
                new Object[]{flask(), Brew.fresh(Humours.of(0, 0, 20, 0), 12)},
                new Object[]{flask(), Brew.fresh(Humours.of(0, 0, 0, 20), 12)},
                // AETHERIC, STORIED, OMENED: what the vessel brings.
                new Object[]{aetherglass, Brew.fresh(Humours.of(8, 0, 0, 0), 8)},
                new Object[]{storied, Brew.fresh(Humours.of(8, 0, 0, 0), 8)},
                new Object[]{omened, Brew.fresh(Humours.of(8, 0, 0, 0), 8)});
    }

    @GameTest(templateName = EMPTY_STRUCTURE)
    public void levelAndExactPullAgainstEachOther(TestContext context) {
        // The point of having both: they cannot be had together, so the heavy brews come from
        // different routes rather than one optimal one.
        for (Landmarks.Landmark landmark : Landmarks.all()) {
            EnumSet<Inflection> crossed =
                    Inflection.of(null, Brew.fresh(landmark.target(), 8));

            if (crossed.contains(Inflection.LEVEL) && crossed.contains(Inflection.EXACT)) {
                throw new GameTestException(landmark.id()
                        + " is both level and exact, which was supposed to be a trade");
            }
        }

        context.complete();
    }

    @GameTest(templateName = EMPTY_STRUCTURE)
    public void theVesselAndTheMoteOnlySpeakThroughAFlask(TestContext context) {
        Brew brew = Brew.fresh(Humours.of(8, 0, 0, 0), 8);
        ItemStack aetherglass = new ItemStack(Cinderflask.AETHERGLASS_CINDERFLASK);

        if (!Inflection.of(aetherglass, brew).contains(Inflection.AETHERIC)) {
            throw new GameTestException("An Aetherglass brew should be aetheric.");
        }

        // The same brew with no flask at all: the vessel inflections have nothing to read.
        if (Inflection.of(null, brew).contains(Inflection.AETHERIC)) {
            throw new GameTestException("A brew with no vessel cannot be aetheric.");
        }

        context.complete();
    }

    // -------------------------------------------------------------------------------------------
    // Who it lands on
    // -------------------------------------------------------------------------------------------

    @GameTest(templateName = EMPTY_STRUCTURE)
    public void reachDecidesWhoGetsDosed(TestContext context) {
        ServerWorld world = context.getWorld();

        // No reach: the bystander is untouched.
        PigEntity drinker = context.spawnMob(EntityType.PIG, new BlockPos(1, 2, 1));
        PigEntity bystander = context.spawnMob(EntityType.PIG, new BlockPos(2, 2, 1));

        Delivery.serve(world, drinker, flask(), Brew.fresh(Humours.of(8, 0, 0, 0), 8));

        if (drinker.getStatusEffects().isEmpty()) {
            throw new GameTestException("The drinker should always be dosed.");
        }

        if (!bystander.getStatusEffects().isEmpty()) {
            throw new GameTestException("A brew with no reach should not touch anybody else.");
        }

        // Enough reach to burst: the bystander is caught.
        PigEntity carrier = context.spawnMob(EntityType.PIG, new BlockPos(4, 2, 1));
        PigEntity near = context.spawnMob(EntityType.PIG, new BlockPos(5, 2, 1));

        Delivery.serve(world, carrier, flask(),
                Brew.fresh(new Humours(8, 0, 0, 0, 6), 8));

        if (near.getStatusEffects().isEmpty()) {
            throw new GameTestException("A reaching brew should catch whoever is standing there.");
        }

        context.complete();
    }

    @GameTest(templateName = EMPTY_STRUCTURE)
    public void enoughReachHangsInTheAirInstead(TestContext context) {
        ServerWorld world = context.getWorld();
        PigEntity drinker = context.spawnMob(EntityType.PIG, new BlockPos(1, 2, 1));

        Box around = drinker.getBoundingBox().expand(16);
        if (!world.getEntitiesByClass(AreaEffectCloudEntity.class, around, cloud -> true).isEmpty()) {
            throw new GameTestException("Something left a cloud here before the test started.");
        }

        Delivery.serve(world, drinker, flask(), Brew.fresh(new Humours(8, 0, 0, 0, 20), 8));

        if (world.getEntitiesByClass(AreaEffectCloudEntity.class, around, cloud -> true).isEmpty()) {
            throw new GameTestException("A brew with that much reach should linger, not land.");
        }

        context.complete();
    }

    @GameTest(templateName = EMPTY_STRUCTURE)
    public void theShapeFollowsTheReach(TestContext context) {
        if (Delivery.shapeOf(0) != Delivery.Shape.DRINKER
                || Delivery.shapeOf(6) != Delivery.Shape.BURST
                || Delivery.shapeOf(20) != Delivery.Shape.CLOUD_SHAPE) {
            throw new GameTestException("The three shapes should follow reach in order.");
        }

        context.complete();
    }

    // -------------------------------------------------------------------------------------------
    // The capstone
    // -------------------------------------------------------------------------------------------

    @GameTest(templateName = EMPTY_STRUCTURE)
    public void enoughAtOnceGrantsSomethingNoLandmarkCan(TestContext context) {
        ItemStack aetherglass = new ItemStack(Cinderflask.AETHERGLASS_CINDERFLASK);
        for (int i = 0; i < VesselName.THRESHOLD; i++) {
            Vessel.record(aetherglass, Humours.of(8, 0, 0, 0));
        }

        // Concentrated, acrid, volatile, exact, aetheric, storied: comfortably past the capstone.
        Brew heavy = Brew.fresh(new Humours(24, 0, 0, 0, 12), 6);
        EnumSet<Inflection> crossed = Inflection.of(aetherglass, heavy);

        if (!Inflection.capstoned(crossed)) {
            throw new GameTestException("That brew should be past the capstone; crossed " + crossed);
        }

        if (!holds(BrewEffects.of(aetherglass, heavy), Unspent.EFFECT)) {
            throw new GameTestException("A capstoned dose should carry Unspent.");
        }

        // And an ordinary one should not, or the capstone is not one.
        if (holds(BrewEffects.of(flask(), Brew.fresh(Humours.of(4, 3, 0, 0), 12)), Unspent.EFFECT)) {
            throw new GameTestException("An ordinary brew should not carry Unspent.");
        }

        context.complete();
    }

    @GameTest(templateName = EMPTY_STRUCTURE)
    public void aHeavyBrewIsActuallyServable(TestContext context) {
        ServerWorld world = context.getWorld();
        PigEntity drinker = context.spawnMob(EntityType.PIG, new BlockPos(1, 2, 1));

        ItemStack aetherglass = new ItemStack(Cinderflask.AETHERGLASS_CINDERFLASK);
        for (int i = 0; i < VesselName.THRESHOLD; i++) {
            Vessel.record(aetherglass, Humours.of(8, 0, 0, 0));
        }

        // Past the capstone and far enough to burst, so the whole path runs: corrupt lookup,
        // delivery, the particle burst and the capstone sound. Every other test here stops short
        // of two inflections, which meant the spectacle was never executed at all.
        Brew heavy = new Brew(new Humours(24, 0, 0, 0, 10), 12, 0.9f, 6);

        if (!Inflection.capstoned(Inflection.of(aetherglass, heavy))) {
            throw new GameTestException("This test is meant to exercise a capstoned brew.");
        }

        Delivery.serve(world, drinker, aetherglass, heavy);

        if (!drinker.hasStatusEffect(Unspent.EFFECT)) {
            throw new GameTestException("Serving a capstoned brew should have granted Unspent.");
        }

        context.complete();
    }

    @GameTest(templateName = EMPTY_STRUCTURE)
    public void unspentRefusesOneDeathAndOnlyOne(TestContext context) {
        ServerWorld world = context.getWorld();
        PigEntity pig = context.spawnMob(EntityType.PIG, new BlockPos(1, 2, 1));
        pig.addStatusEffect(new StatusEffectInstance(Unspent.EFFECT, 400, 0, false, false, false));

        pig.damage(world.getDamageSources().generic(), 1000);

        if (!pig.isAlive()) {
            throw new GameTestException("Unspent should have refused that.");
        }

        if (pig.hasStatusEffect(Unspent.EFFECT)) {
            throw new GameTestException("Unspent should be spent once it has been used.");
        }

        // Invulnerability frames would otherwise swallow the second blow.
        pig.timeUntilRegen = 0;
        pig.damage(world.getDamageSources().generic(), 1000);

        if (pig.isAlive()) {
            throw new GameTestException("Unspent should only refuse once.");
        }

        context.complete();
    }
}
