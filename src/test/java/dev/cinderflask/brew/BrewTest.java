package dev.cinderflask.brew;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BrewTest {
    private static final float EPSILON = 1.0e-4f;

    /** A concentrated choleric brew: the archetypal green, violent, short one. */
    private static Brew green() {
        return Brew.fresh(Humours.of(8, 0, 0, 0), 6);
    }

    // -------------------------------------------------------------------------------------------
    // The inversion the whole design rests on
    // -------------------------------------------------------------------------------------------

    @Test
    @DisplayName("a brew trades power for duration as it turns, without a band table")
    void amplifierAndDurationInvertWithAge() {
        Brew fresh = green();
        Brew settled = fresh.aged(1);

        assertTrue(fresh.amplifier() > settled.amplifier(),
                "a settled brew should hit softer than a green one");
        assertTrue(settled.durationTicks() > fresh.durationTicks() * 3,
                "and should last far longer");
    }

    @Test
    @DisplayName("green brews are ferocious and brief")
    void greenBrewsAreCombatShaped() {
        Brew fresh = green();

        assertTrue(fresh.amplifier() >= 3, "expected a heavy amplifier, got " + fresh.amplifier());
        assertTrue(fresh.durationTicks() <= 120,
                "expected a few seconds at most, got " + fresh.durationTicks() + " ticks");
    }

    @Test
    @DisplayName("deep brews are mild and long")
    void deepBrewsAreCampaignShaped() {
        Brew deep = green().aged(1);

        assertEquals(0, deep.amplifier());
        assertTrue(deep.durationTicks() > 400,
                "expected twenty seconds or more, got " + deep.durationTicks() + " ticks");
    }

    @Test
    @DisplayName("the four quarters of the wheel drink differently from one another")
    void everyQuarterOfTheWheelHasItsOwnFeel() {
        Brew choleric = Brew.fresh(Humours.of(8, 0, 0, 0), 4);
        Brew melancholic = choleric.aged(1);
        Brew sanguine = choleric.aged(2);
        Brew phlegmatic = choleric.aged(3);

        // Hardest and briefest through to softest and longest, with the other two genuinely between
        // rather than duplicating either end.
        assertTrue(choleric.amplifier() > sanguine.amplifier());
        assertTrue(sanguine.amplifier() > phlegmatic.amplifier());
        assertTrue(phlegmatic.amplifier() >= melancholic.amplifier());

        assertTrue(choleric.durationTicks() < sanguine.durationTicks());
        assertTrue(sanguine.durationTicks() < phlegmatic.durationTicks());
        assertTrue(phlegmatic.durationTicks() < melancholic.durationTicks());
    }

    // -------------------------------------------------------------------------------------------
    // Concentration
    // -------------------------------------------------------------------------------------------

    @Test
    @DisplayName("the same essence gives few strong doses or many weak ones")
    void concentrationIsTheCentralTrade() {
        Humours essence = Humours.of(8, 0, 0, 0);

        Brew concentrated = Brew.fresh(essence, 4);
        Brew dilute = Brew.fresh(essence, 12);

        assertTrue(concentrated.amplifier() > dilute.amplifier());
        assertTrue(concentrated.doses() < dilute.doses());

        assertEquals(4, concentrated.doses());
        assertEquals(12, dilute.doses());
    }

    @Test
    @DisplayName("too much essence for the vessel spoils it")
    void overfillingSpoils() {
        assertFalse(Brew.fresh(Humours.of(8, 0, 0, 0), 4).isSpoiled());
        assertTrue(Brew.fresh(Humours.of(20, 0, 0, 0), 4).isSpoiled(),
                "five times concentration should be past what the vessel can hold");
    }

    // -------------------------------------------------------------------------------------------
    // The spiral
    // -------------------------------------------------------------------------------------------

    @Test
    @DisplayName("body decays every turn and the brew eventually thins into nothing")
    void neglectEventuallyRuinsIt() {
        Brew brew = Brew.fresh(Humours.of(8, 0, 0, 0), 6);

        float previous = brew.current().magnitude();
        for (int turn = 1; turn <= 12; turn++) {
            float now = brew.aged(turn * Humours.WHEEL).current().magnitude();
            assertTrue(now < previous, "body did not decay on turn " + turn);
            previous = now;
        }

        assertTrue(brew.aged(Humours.WHEEL * 12).isSpoiled(),
                "a brew left for twelve turns should have thinned into Sump");
    }

    @Test
    @DisplayName("corruption only ever climbs, and time is the cheap way to buy it")
    void corruptionAccruesWithTurns() {
        Brew brew = Brew.fresh(Humours.of(8, 0, 0, 0), 6);

        assertEquals(0f, brew.corruption(), EPSILON);
        assertTrue(brew.aged(Humours.WHEEL).corruption() > brew.corruption());
        assertTrue(brew.aged(Humours.WHEEL * 2).corruption() > brew.aged(Humours.WHEEL).corruption());
        assertEquals(1f, brew.aged(Humours.WHEEL * 40).corruption(), EPSILON, "corruption caps at one");
    }

    @Test
    @DisplayName("a full turn restores the character but not the strength")
    void thespiralReturnsChangedNotIdentical() {
        Brew brew = Brew.fresh(Humours.of(8, 0, 0, 0), 6);
        Brew turned = brew.aged(Humours.WHEEL);

        assertEquals(brew.current().dominant(), turned.current().dominant(),
                "it should be choleric again");
        assertTrue(turned.current().magnitude() < brew.current().magnitude(),
                "but thinner");
        assertTrue(turned.corruption() > brew.corruption(),
                "and dirtier");
    }

    // -------------------------------------------------------------------------------------------
    // Comedown
    // -------------------------------------------------------------------------------------------

    @Test
    @DisplayName("balance is what spares you the comedown")
    void lopsidedBrewsPunishYou() {
        Brew lopsided = Brew.fresh(Humours.of(8, 0, 0, 0), 4);
        Brew rounded = Brew.fresh(Humours.of(2, 2, 2, 2), 4);

        assertTrue(lopsided.comedown() > rounded.comedown());
        assertEquals(0f, rounded.comedown(), EPSILON, "a perfectly even brew should drink clean");
    }

    // -------------------------------------------------------------------------------------------
    // Solera
    // -------------------------------------------------------------------------------------------

    @Test
    @DisplayName("topping up drags a tired brew back to strength")
    void toppingUpRevivesAnOldFlask() {
        Brew tired = Brew.fresh(Humours.of(8, 0, 0, 0), 6).aged(Humours.WHEEL * 3);
        Brew fresh = Brew.fresh(Humours.of(8, 0, 0, 0), 6);

        Brew revived = tired.toppedUp(fresh, 6);

        assertTrue(revived.current().magnitude() > tired.current().magnitude(),
                "the young brew should have put body back");
        assertTrue(revived.phase() < tired.phase(),
                "and pulled the age back towards the middle");
        assertTrue(revived.phase() > fresh.phase(),
                "without resetting it to new");
    }

    @Test
    @DisplayName("age and filth blend by dose, not by halves")
    void toppingUpIsDoseWeighted() {
        Brew old = new Brew(Humours.of(8, 0, 0, 0), 8, 0, 9);
        Brew young = new Brew(Humours.of(8, 0, 0, 0), 0, 0, 3);

        // Nine doses of old against three of young: the result should sit three quarters of the
        // way towards the old one.
        Brew mixed = old.toppedUp(young, 3);
        assertEquals(6f, mixed.phase(), EPSILON);
    }

    @Test
    @DisplayName("topping up an empty flask is a no-op rather than a divide by zero")
    void toppingUpDegradesGracefully() {
        Brew brew = Brew.fresh(Humours.EMPTY, 1);
        assertEquals(brew.phase(), brew.toppedUp(brew, 0).phase(), EPSILON);
    }
}
