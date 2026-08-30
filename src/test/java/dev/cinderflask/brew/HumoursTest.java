package dev.cinderflask.brew;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class HumoursTest {
    private static final float EPSILON = 1.0e-4f;

    private static void assertHumours(Humours actual, float cho, float mel, float san, float phl) {
        assertEquals(cho, actual.choleric(), EPSILON, "choleric");
        assertEquals(mel, actual.melancholic(), EPSILON, "melancholic");
        assertEquals(san, actual.sanguine(), EPSILON, "sanguine");
        assertEquals(phl, actual.phlegmatic(), EPSILON, "phlegmatic");
    }

    // -------------------------------------------------------------------------------------------
    // Ageing
    // -------------------------------------------------------------------------------------------

    @Test
    @DisplayName("ageing never flattens a brew, however old it gets")
    void ageingDoesNotDiffuseTowardsUniform() {
        Humours young = Humours.of(8, 0, 0, 0);
        float sharpness = young.balance();

        // Ten full turns, sampled off the whole phases so every reading carries the most blur the
        // interpolation can produce. A single-peaked brew is split across at most two neighbours,
        // which caps balance at exactly one half; anything above that means the blur has started
        // compounding and the brew is on its way to grey.
        for (float phase = 0.25f; phase <= 40f; phase += 0.25f) {
            float aged = young.rotated(phase).balance();

            assertTrue(aged <= 0.5f + EPSILON,
                    "at phase " + phase + " the brew had spread to balance " + aged
                            + "; blur is compounding, so ageing is diffusing towards grey");
        }

        assertEquals(sharpness, young.rotated(40f).balance(), EPSILON,
                "a whole number of turns should land back on the original shape exactly");
    }

    @Test
    @DisplayName("a full turn of the wheel is the identity")
    void fullTurnReturnsTheOriginal() {
        Humours brew = new Humours(5, 3, 1, 2, 4);
        assertHumours(brew.rotated(Humours.WHEEL), 5, 3, 1, 2);
        assertHumours(brew.rotated(Humours.WHEEL * 7), 5, 3, 1, 2);
    }

    @Test
    @DisplayName("whole phases permute rather than blend")
    void wholePhasesArePermutations() {
        Humours brew = Humours.of(8, 0, 0, 0);

        assertHumours(brew.rotated(1), 0, 8, 0, 0);
        assertHumours(brew.rotated(2), 0, 0, 8, 0);
        assertHumours(brew.rotated(3), 0, 0, 0, 8);
    }

    @Test
    @DisplayName("a half phase sits evenly between the two neighbours")
    void fractionalPhasesInterpolate() {
        assertHumours(Humours.of(8, 0, 0, 0).rotated(0.5f), 4, 4, 0, 0);
        assertHumours(Humours.of(8, 0, 0, 0).rotated(1.25f), 0, 6, 2, 0);
    }

    @Test
    @DisplayName("ageing conserves the body of the brew")
    void rotationConservesMagnitude() {
        Humours brew = new Humours(5, 3, 1, 2, 4);

        for (float phase = 0; phase <= 8f; phase += 0.25f) {
            assertEquals(11f, brew.rotated(phase).magnitude(), EPSILON,
                    "magnitude drifted at phase " + phase + "; decay belongs to Brew, not the wheel");
        }
    }

    @Test
    @DisplayName("negative phases wrap backwards round the wheel")
    void negativePhasesWrap() {
        assertHumours(Humours.of(8, 0, 0, 0).rotated(-1), 0, 0, 0, 8);
    }

    @Test
    @DisplayName("quintessence sits off the wheel and never turns")
    void quintessenceDoesNotRotate() {
        Humours brew = new Humours(5, 3, 1, 2, 6);

        for (float phase = 0; phase <= 8f; phase += 0.5f) {
            assertEquals(6f, brew.rotated(phase).quintessence(), EPSILON,
                    "quintessence moved at phase " + phase);
        }
    }

    // -------------------------------------------------------------------------------------------
    // Derived quantities
    // -------------------------------------------------------------------------------------------

    @Test
    @DisplayName("force and endurance trade places across the wheel")
    void heatAndDepthInvertAsItAges() {
        Humours green = Humours.of(8, 0, 0, 0);
        assertTrue(green.heat() > green.depth() * 5, "a choleric brew should be nearly all force");

        Humours settled = green.rotated(1);
        assertTrue(settled.depth() > settled.heat() * 5, "a melancholic one nearly all endurance");

        assertTrue(settled.depth() > green.depth());
        assertTrue(settled.heat() < green.heat());
    }

    @Test
    @DisplayName("each humour has its own signature, so the wheel has four characters not two")
    void everyHumourReadsDifferently() {
        // Force and endurance were once just (choleric + sanguine) and (melancholic + phlegmatic),
        // which made sanguine indistinguishable from choleric and collapsed the wheel to two states.
        float[] force = new float[Humours.WHEEL];
        float[] endurance = new float[Humours.WHEEL];

        for (int i = 0; i < Humours.WHEEL; i++) {
            Humours pure = Humours.of(0, 0, 0, 0).plus(single(i, 8));
            force[i] = pure.heat();
            endurance[i] = pure.depth();
        }

        for (int a = 0; a < Humours.WHEEL; a++) {
            for (int b = a + 1; b < Humours.WHEEL; b++) {
                assertTrue(Math.abs(force[a] - force[b]) > 0.5f || Math.abs(endurance[a] - endurance[b]) > 0.5f,
                        "humours " + a + " and " + b + " are mechanically identical");
            }
        }
    }

    private static Humours single(int wheelIndex, float amount) {
        return switch (wheelIndex) {
            case 0 -> Humours.of(amount, 0, 0, 0);
            case 1 -> Humours.of(0, amount, 0, 0);
            case 2 -> Humours.of(0, 0, amount, 0);
            default -> Humours.of(0, 0, 0, amount);
        };
    }

    @Test
    @DisplayName("balance runs from a single humour to a level four")
    void balanceSpansItsRange() {
        assertEquals(0f, Humours.of(4, 0, 0, 0).balance(), EPSILON);
        assertEquals(1f, Humours.of(1, 1, 1, 1).balance(), EPSILON);
        assertEquals(0f, Humours.EMPTY.balance(), EPSILON);

        assertTrue(Humours.of(4, 4, 0, 0).balance() > Humours.of(6, 2, 0, 0).balance(),
                "an even pair should read as better balanced than a lopsided one");
    }

    @Test
    @DisplayName("opposed humours pass through balance rather than settling into it")
    void opposedBrewsOscillateInsteadOfCollapsing() {
        Humours opposed = Humours.of(8, 0, 8, 0);

        // Both peaks survive every whole phase, forever.
        for (int turn = 0; turn < 40; turn++) {
            Humours aged = opposed.rotated(turn);
            assertEquals(0.5f, aged.balance(), EPSILON,
                    "the two peaks had merged by phase " + turn);
        }

        assertHumours(opposed.rotated(1), 0, 8, 0, 8);

        // Halfway between, the two peaks are exactly out of phase and the brew reads as perfectly
        // even. That is intended, not a collapse: balance suppresses the comedown, so an opposed
        // brew has a recurring window where it drinks smooth. It always re-peaks afterwards.
        assertEquals(1f, opposed.rotated(2.5f).balance(), EPSILON);
        assertEquals(0.5f, opposed.rotated(3f).balance(), EPSILON);
    }

    @Test
    @DisplayName("volatility rises with heat and falls with anything patient")
    void volatilityRewardsBallast() {
        assertTrue(Humours.of(8, 0, 0, 0).volatility() > Humours.of(8, 7, 0, 0).volatility());
        assertEquals(0f, Humours.of(0, 8, 0, 0).volatility(), EPSILON);
    }

    @Test
    @DisplayName("dominant reports the leading humour")
    void dominantPicksThePeak() {
        assertEquals(0, Humours.of(5, 1, 1, 1).dominant());
        assertEquals(3, Humours.of(1, 1, 1, 5).dominant());
        assertEquals(0, Humours.EMPTY.dominant());
    }

    // -------------------------------------------------------------------------------------------
    // Arithmetic
    // -------------------------------------------------------------------------------------------

    @Test
    @DisplayName("blending is dose-weighted across every axis")
    void blendIsDoseWeighted() {
        Humours old = new Humours(8, 0, 0, 0, 4);
        Humours fresh = new Humours(0, 8, 0, 0, 0);

        Humours evens = Humours.blend(old, 1, fresh, 1);
        assertHumours(evens, 4, 4, 0, 0);
        assertEquals(2f, evens.quintessence(), EPSILON, "quintessence blends too, it just does not rotate");

        Humours mostlyOld = Humours.blend(old, 3, fresh, 1);
        assertHumours(mostlyOld, 6, 2, 0, 0);

        assertTrue(Humours.blend(Humours.EMPTY, 0, Humours.EMPTY, 0).isEmpty());
    }

    @Test
    @DisplayName("scaling thins the body but leaves reach alone")
    void scalingLeavesQuintessence() {
        Humours halved = new Humours(8, 4, 0, 0, 6).scaled(0.5f);

        assertHumours(halved, 4, 2, 0, 0);
        assertEquals(6f, halved.quintessence(), EPSILON);
    }

    @Test
    @DisplayName("negative components are clamped away at construction")
    void componentsNeverGoNegative() {
        assertHumours(new Humours(-5, 2, -1, 0, 0), 0, 2, 0, 0);
    }

    // -------------------------------------------------------------------------------------------
    // Effect proximity
    // -------------------------------------------------------------------------------------------

    @Test
    @DisplayName("similarity finds the effect a brew is nearest")
    void similarityRanksEffects() {
        Humours brew = Humours.of(7, 1, 1, 0);

        Humours kindled = Humours.of(1, 0, 0, 0);
        Humours ironhide = Humours.of(0, 1, 0, 0);

        assertTrue(brew.similarity(kindled) > brew.similarity(ironhide));
        assertEquals(1f, brew.similarity(brew), EPSILON);
        assertEquals(0f, brew.similarity(Humours.EMPTY), EPSILON);
    }

    @Test
    @DisplayName("quintessence pulls a brew towards the far-reaching effects")
    void quintessenceCountsTowardsProximity() {
        Humours mundane = new Humours(4, 0, 0, 0, 0);
        Humours aetheric = new Humours(4, 0, 0, 0, 4);
        Humours farReaching = new Humours(1, 0, 0, 0, 1);

        assertTrue(aetheric.similarity(farReaching) > mundane.similarity(farReaching));
    }
}
