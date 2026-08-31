package dev.cinderflask.effect;

import dev.cinderflask.brew.Humours;
import net.minecraft.entity.effect.StatusEffect;
import net.minecraft.entity.effect.StatusEffectCategory;

/**
 * The crash after a lopsided brew.
 *
 * <p>A rebound is not a separate idea from a draught — it is the same one running backwards. Drink
 * something that is all one humour and the body answers by taking away exactly what that humour lent
 * you, which is why there are four of these and not twelve: the wheel already says what the opposite
 * of a humour is, and a brew only has one humour leading it.
 *
 * <p>They take the colour of the humour that caused them, dragged towards the murk — so a rebound
 * looks like the brew it came from, gone bad.
 */
public class ReboundEffect extends StatusEffect {
    /** How far towards the murk a rebound's colour sits. Enough to read as spoiled at icon size. */
    private static final float SOURED = 0.5f;

    private final int humour;

    protected ReboundEffect(int humour) {
        super(StatusEffectCategory.HARMFUL,
                Humours.soured(Humours.humourColour(humour), SOURED));
        this.humour = humour;
    }

    /** Which wheel position this is the crash from. */
    public int humour() {
        return humour;
    }
}
