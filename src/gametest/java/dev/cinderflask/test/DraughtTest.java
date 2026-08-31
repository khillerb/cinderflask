package dev.cinderflask.test;

import dev.cinderflask.brew.Brew;
import dev.cinderflask.brew.BrewEffects;
import dev.cinderflask.brew.Humours;
import dev.cinderflask.brew.Landmarks;
import dev.cinderflask.effect.DraughtEffect;
import dev.cinderflask.effect.Draughts;
import dev.cinderflask.effect.ReboundEffect;
import dev.cinderflask.effect.Rebounds;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.effect.StatusEffect;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.passive.PigEntity;
import net.minecraft.entity.projectile.ArrowEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.test.GameTest;
import net.minecraft.test.GameTestException;
import net.minecraft.test.TestContext;
import net.minecraft.util.math.BlockPos;

import java.util.List;

/**
 * The twelve draughts and the four rebounds: which ones a brew lands you, and what they do to a blow.
 *
 * <p>Everything here goes through a live hit rather than calling the hook directly, because the thing
 * worth protecting is that the mixin, the hook and the effect still agree with one another.
 */
public class DraughtTest implements FabricGameTest {
    private static final float EPSILON = 0.05f;

    private static PigEntity pig(TestContext context, int x, int z) {
        return context.spawnMob(EntityType.PIG, new BlockPos(x, 2, z));
    }

    private static StatusEffectInstance dose(StatusEffect effect) {
        return new StatusEffectInstance(effect, 400, 0, false, false, false);
    }

    /** A fresh victim, hit once, and what it cost them. */
    private static float taken(TestContext context, int x, int z, DamageSource source, float amount,
                               StatusEffect... carrying) {
        PigEntity victim = pig(context, x, z);
        for (StatusEffect effect : carrying) {
            victim.addStatusEffect(dose(effect));
        }

        float before = victim.getHealth();
        victim.damage(source, amount);
        return before - victim.getHealth();
    }

    /**
     * Turns one entity to face another, or to put its back to it.
     *
     * <p>Worked out from where the two actually are rather than from a hardcoded yaw: the draught
     * reads real positions, and a mob that wandered a block would otherwise quietly stop testing the
     * thing the test is named after.
     */
    private static void face(LivingEntity victim, LivingEntity target, boolean towards) {
        double dx = target.getX() - victim.getX();
        double dz = target.getZ() - victim.getZ();

        float yaw = (float) (Math.toDegrees(Math.atan2(dz, dx)) - 90) + (towards ? 0 : 180);
        victim.setYaw(yaw);
        victim.setHeadYaw(yaw);
        victim.setPitch(0);
    }

    // -------------------------------------------------------------------------------------------
    // Which draughts a brew lands you
    // -------------------------------------------------------------------------------------------

    @GameTest(templateName = EMPTY_STRUCTURE)
    public void everyLandmarkHasADraught(TestContext context) {
        for (Landmarks.Landmark landmark : Landmarks.all()) {
            DraughtEffect draught = Draughts.of(landmark);

            if (draught == null) {
                throw new GameTestException("No draught registered for " + landmark.id());
            }

            if (draught.getColor() != landmark.target().colour()) {
                throw new GameTestException("The icon for " + landmark.id()
                        + " is not the colour of the brew that makes it.");
            }
        }

        if (Draughts.all().size() != Landmarks.all().size()) {
            throw new GameTestException("There should be exactly one draught to a landmark.");
        }

        if (Rebounds.all().size() != Humours.WHEEL) {
            throw new GameTestException("There should be exactly one rebound to a humour.");
        }

        context.complete();
    }

    @GameTest(templateName = EMPTY_STRUCTURE)
    public void hittingALandmarkSquarelyGivesAlmostAllOfOneDraught(TestContext context) {
        Landmarks.Landmark deadmans = named("deadmans_draught");
        List<BrewEffects.Share> shares = BrewEffects.shares(deadmans.target());

        if (shares.isEmpty() || !shares.get(0).landmark().equals(deadmans)) {
            throw new GameTestException("A brew standing on a landmark should lead with it, got " + shares);
        }

        if (shares.get(0).weight() < 0.8f) {
            throw new GameTestException("Aiming well should be worth something; got only "
                    + shares.get(0).weight());
        }

        context.complete();
    }

    @GameTest(templateName = EMPTY_STRUCTURE)
    public void sittingBetweenTwoLandmarksGivesBoth(TestContext context) {
        // Part way from pure choleric towards choleric-and-melancholic.
        List<BrewEffects.Share> shares = BrewEffects.shares(Humours.of(7, 3, 0, 0));

        boolean deadmans = false;
        boolean bramble = false;

        for (BrewEffects.Share share : shares) {
            deadmans |= share.landmark().id().getPath().equals("deadmans_draught");
            bramble |= share.landmark().id().getPath().equals("bramblewine");

            if (share.weight() > 0.8f) {
                throw new GameTestException("Neither should dominate in between; got " + shares);
            }
        }

        if (!deadmans || !bramble) {
            throw new GameTestException("The ground between two landmarks should give both, got " + shares);
        }

        context.complete();
    }

    @GameTest(templateName = EMPTY_STRUCTURE)
    public void alevelBrewTradesStrengthForBreadthAndPaysNoRebound(TestContext context) {
        List<BrewEffects.Share> shares = BrewEffects.shares(Humours.of(5, 5, 5, 5));

        if (shares.size() < 2) {
            throw new GameTestException("A level brew is near several landmarks, not one; got " + shares);
        }

        for (BrewEffects.Share share : shares) {
            if (share.weight() > 0.5f) {
                throw new GameTestException("Nothing should dominate a level brew; got " + shares);
            }
        }

        // And the whole point of levelling one out: no crash afterwards.
        for (StatusEffectInstance effect : BrewEffects.of(Brew.fresh(Humours.of(5, 5, 5, 5), 8))) {
            if (effect.getEffectType() instanceof ReboundEffect) {
                throw new GameTestException("A level brew should not rebound, got " + effect);
            }
        }

        context.complete();
    }

    @GameTest(templateName = EMPTY_STRUCTURE)
    public void alopsidedBrewReboundsIntoItsOpposite(TestContext context) {
        Humours choleric = Humours.of(8, 0, 0, 0);
        ReboundEffect rebound = null;

        for (StatusEffectInstance effect : BrewEffects.of(Brew.fresh(choleric, 4))) {
            if (effect.getEffectType() instanceof ReboundEffect found) {
                rebound = found;
            }
        }

        if (rebound == null) {
            throw new GameTestException("A single-humour brew should rebound.");
        }

        // The crash belongs to the humour that caused it, not to some fifth thing.
        if (rebound.humour() != choleric.dominant()) {
            throw new GameTestException("A choleric brew should rebound as choleric, got humour "
                    + rebound.humour());
        }

        if (rebound != Rebounds.ASHFALL) {
            throw new GameTestException("Expected Ashfall, got " + rebound.getName().getString());
        }

        context.complete();
    }

    // -------------------------------------------------------------------------------------------
    // What a draught does to a blow
    // -------------------------------------------------------------------------------------------

    @GameTest(templateName = EMPTY_STRUCTURE)
    public void ironrootTakesAFlatAmountOffRatherThanAShare(TestContext context) {
        DamageSource generic = context.getWorld().getDamageSources().generic();

        float small = taken(context, 1, 1, generic, 3, Draughts.IRONROOT);
        float large = taken(context, 3, 1, generic, 6, Draughts.IRONROOT);

        // A share would take twice as much off the bigger hit. A flat amount takes the same off both,
        // which is why Ironroot beats a swarm and Resistance does not.
        float offSmall = 3 - small;
        float offLarge = 6 - large;

        if (Math.abs(offSmall - offLarge) > EPSILON) {
            throw new GameTestException("Ironroot should subtract, not scale; took off "
                    + offSmall + " and " + offLarge);
        }

        if (offSmall <= 0) {
            throw new GameTestException("Ironroot took nothing off at all.");
        }

        context.complete();
    }

    @GameTest(templateName = EMPTY_STRUCTURE)
    public void deepdelveIgnoresEverythingTheGroundDoes(TestContext context) {
        ServerWorld world = context.getWorld();

        if (taken(context, 1, 1, world.getDamageSources().fall(), 8, Draughts.DEEPDELVE) > 0) {
            throw new GameTestException("Deepdelve should have swallowed the fall.");
        }

        // But it is not armour. An ordinary blow still lands.
        if (taken(context, 3, 1, world.getDamageSources().generic(), 4, Draughts.DEEPDELVE) <= 0) {
            throw new GameTestException("Deepdelve should not stop a blow that is not the ground.");
        }

        context.complete();
    }

    @GameTest(templateName = EMPTY_STRUCTURE)
    public void deadmansVigourHitsHarderTheLessIsLeftOfYou(TestContext context) {
        ServerWorld world = context.getWorld();

        PigEntity whole = pig(context, 1, 1);
        PigEntity spent = pig(context, 3, 1);

        whole.addStatusEffect(dose(Draughts.DEADMANS_VIGOUR));
        spent.addStatusEffect(dose(Draughts.DEADMANS_VIGOUR));
        spent.setHealth(1);

        float fromWhole = taken(context, 1, 3, world.getDamageSources().mobAttack(whole), 4);
        float fromSpent = taken(context, 3, 3, world.getDamageSources().mobAttack(spent), 4);

        if (fromSpent <= fromWhole) {
            throw new GameTestException("A dying berserker should hit harder, not the same; "
                    + fromWhole + " against " + fromSpent);
        }

        context.complete();
    }

    @GameTest(templateName = EMPTY_STRUCTURE)
    public void sapswornPutsBackSomeOfWhatItTakesOut(TestContext context) {
        ServerWorld world = context.getWorld();

        PigEntity reaver = pig(context, 1, 1);
        reaver.addStatusEffect(dose(Draughts.SAPSWORN));
        reaver.setHealth(4);

        float before = reaver.getHealth();
        taken(context, 3, 1, world.getDamageSources().mobAttack(reaver), 6);

        if (reaver.getHealth() <= before) {
            throw new GameTestException("Sapsworn should have fed off that.");
        }

        context.complete();
    }

    @GameTest(templateName = EMPTY_STRUCTURE)
    public void bramblewineAnswersAHandAndNotAnArrow(TestContext context) {
        ServerWorld world = context.getWorld();

        PigEntity hand = pig(context, 1, 1);
        float handBefore = hand.getHealth();
        taken(context, 3, 1, world.getDamageSources().mobAttack(hand), 4, Draughts.BRAMBLE);

        if (hand.getHealth() >= handBefore) {
            throw new GameTestException("Bramblewine should have answered a blow within reach.");
        }

        // With no reach behind it, it cannot answer something thrown from across the room.
        PigEntity archer = pig(context, 1, 3);
        float archerBefore = archer.getHealth();
        ArrowEntity arrow = new ArrowEntity(world, archer);
        taken(context, 3, 3, world.getDamageSources().arrow(arrow, archer), 4, Draughts.BRAMBLE);

        if (archer.getHealth() < archerBefore) {
            throw new GameTestException("Bramblewine reached an archer it should not have.");
        }

        context.complete();
    }

    @GameTest(templateName = EMPTY_STRUCTURE)
    public void twoBramblesDoNotTradeTheSameHitForever(TestContext context) {
        ServerWorld world = context.getWorld();

        PigEntity first = pig(context, 1, 1);
        PigEntity second = pig(context, 3, 1);

        first.addStatusEffect(dose(Draughts.BRAMBLE));
        second.addStatusEffect(dose(Draughts.BRAMBLE));

        // The assertion is that this returns at all: without the reentrancy guard the answer to an
        // answer is another answer, and the stack runs out before the argument does.
        second.damage(world.getDamageSources().mobAttack(first), 4);

        if (!first.isAlive() || !second.isAlive()) {
            throw new GameTestException("One blow between two hedges should not kill either of them.");
        }

        context.complete();
    }

    @GameTest(templateName = EMPTY_STRUCTURE)
    public void emberbloodSetsWhatItTouchesAlight(TestContext context) {
        ServerWorld world = context.getWorld();

        PigEntity alchemist = pig(context, 1, 1);
        alchemist.addStatusEffect(dose(Draughts.EMBERBLOOD));

        PigEntity victim = pig(context, 3, 1);
        victim.damage(world.getDamageSources().mobAttack(alchemist), 2);

        if (victim.getFireTicks() <= 0) {
            throw new GameTestException("Emberblood should have lit them up.");
        }

        context.complete();
    }

    @GameTest(templateName = EMPTY_STRUCTURE)
    public void honeyedDoesNotStopAtYourOwnSkin(TestContext context) {
        PigEntity healer = pig(context, 1, 1);
        PigEntity ally = pig(context, 2, 1);
        ally.setHealth(2);

        float before = ally.getHealth();
        Draughts.HONEYED.applyUpdateEffect(healer, 0);

        if (ally.getHealth() <= before) {
            throw new GameTestException("Reach is the point of Honeyed; the ally was not mended.");
        }

        context.complete();
    }

    @GameTest(templateName = EMPTY_STRUCTURE)
    public void graveboundIsMendedByAKillRatherThanByABlow(TestContext context) {
        ServerWorld world = context.getWorld();

        PigEntity necromancer = pig(context, 1, 1);
        necromancer.addStatusEffect(dose(Draughts.GRAVEBOUND));
        necromancer.setHealth(2);

        float before = necromancer.getHealth();
        pig(context, 3, 1).damage(world.getDamageSources().mobAttack(necromancer), 100);

        if (necromancer.getHealth() <= before) {
            throw new GameTestException("A kill should have knitted the necromancer back together.");
        }

        context.complete();
    }

    // -------------------------------------------------------------------------------------------
    // The rebounds, which are the same rules read backwards
    // -------------------------------------------------------------------------------------------

    @GameTest(templateName = EMPTY_STRUCTURE)
    public void ashfallTakesBackTheForceItsHumourLent(TestContext context) {
        ServerWorld world = context.getWorld();

        PigEntity fresh = pig(context, 1, 1);
        PigEntity spent = pig(context, 3, 1);
        spent.addStatusEffect(dose(Rebounds.ASHFALL));

        float ordinary = taken(context, 1, 3, world.getDamageSources().mobAttack(fresh), 4);
        float ashen = taken(context, 3, 3, world.getDamageSources().mobAttack(spent), 4);

        if (ashen >= ordinary) {
            throw new GameTestException("Ashfall should soften a blow, not leave it alone; "
                    + ordinary + " against " + ashen);
        }

        context.complete();
    }

    @GameTest(templateName = EMPTY_STRUCTURE)
    public void plainSightChargesForExactlyWhatTheUnseenHandPays(TestContext context) {
        ServerWorld world = context.getWorld();

        PigEntity assassin = pig(context, 1, 1);
        assassin.addStatusEffect(dose(Draughts.UNSEEN_HAND));

        float paidFromBehind = struck(context, assassin, 3, 1, true);
        float paidFacing = struck(context, assassin, 5, 1, false);

        if (paidFromBehind <= paidFacing) {
            throw new GameTestException("The Unseen Hand should pay from behind; "
                    + paidFacing + " facing against " + paidFromBehind + " behind");
        }

        // The rebound reads the same geometry and charges the victim for it instead.
        PigEntity plain = pig(context, 1, 4);
        float chargedFromBehind = struck(context, plain, 3, 4, true, Rebounds.PLAIN_SIGHT);
        float chargedFacing = struck(context, plain, 5, 4, false, Rebounds.PLAIN_SIGHT);

        if (chargedFromBehind <= chargedFacing) {
            throw new GameTestException("Plain Sight should cost you from behind; "
                    + chargedFacing + " facing against " + chargedFromBehind + " behind");
        }

        context.complete();
    }

    @GameTest(templateName = EMPTY_STRUCTURE)
    public void bloodlessDrainsYouButWillNotTakeTheLast(TestContext context) {
        PigEntity bleeding = pig(context, 1, 1);
        float before = bleeding.getHealth();
        Rebounds.BLOODLESS.applyUpdateEffect(bleeding, 0);

        if (bleeding.getHealth() >= before) {
            throw new GameTestException("Bloodless should run Honeyed's tap backwards.");
        }

        // A crash, not an execution.
        PigEntity nearlyGone = pig(context, 3, 1);
        nearlyGone.setHealth(1);
        Rebounds.BLOODLESS.applyUpdateEffect(nearlyGone, 0);

        if (!nearlyGone.isAlive()) {
            throw new GameTestException("A rebound should not be able to kill you.");
        }

        context.complete();
    }

    /**
     * Hits a fresh victim placed at (x, z), turned either to face the attacker or to show their back,
     * and reports what it cost them.
     */
    private static float struck(TestContext context, LivingEntity attacker, int x, int z,
                                boolean fromBehind, StatusEffect... carrying) {
        PigEntity victim = pig(context, x, z);
        for (StatusEffect effect : carrying) {
            victim.addStatusEffect(dose(effect));
        }

        face(victim, attacker, !fromBehind);

        float before = victim.getHealth();
        victim.damage(context.getWorld().getDamageSources().mobAttack(attacker), 3);
        return before - victim.getHealth();
    }

    private static Landmarks.Landmark named(String path) {
        for (Landmarks.Landmark landmark : Landmarks.all()) {
            if (landmark.id().getPath().equals(path)) {
                return landmark;
            }
        }

        throw new GameTestException("No landmark called " + path);
    }
}
