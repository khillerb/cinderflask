package dev.cinderflask.effect;

import dev.cinderflask.brew.Landmarks;
import net.minecraft.entity.effect.StatusEffect;
import net.minecraft.entity.effect.StatusEffectCategory;

/**
 * An effect that lives at a coordinate.
 *
 * <p>Every draught is the effect of one landmark, and takes its colour from that landmark's own brew,
 * so an icon can never disagree with the liquid that produces it.
 *
 * <p>Combat behaviour is declared by implementing {@link CombatHooks.Striking},
 * {@link CombatHooks.Enduring} or {@link CombatHooks.Answering} rather than by being listed anywhere.
 */
public class DraughtEffect extends StatusEffect {
    private final Landmarks.Landmark landmark;

    protected DraughtEffect(Landmarks.Landmark landmark) {
        this(landmark, landmark.target().colour());
    }

    /** For the corrupt twins, which are the same landmark wearing a fouled colour. */
    protected DraughtEffect(Landmarks.Landmark landmark, int colour) {
        super(StatusEffectCategory.BENEFICIAL, colour);
        this.landmark = landmark;
    }

    public Landmarks.Landmark landmark() {
        return landmark;
    }
}
