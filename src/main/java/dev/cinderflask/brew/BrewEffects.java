package dev.cinderflask.brew;

import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.effect.StatusEffect;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;

import java.util.ArrayList;
import java.util.List;

/**
 * What a brew does when you drink it.
 *
 * <p>A placeholder: the dominant humour picks one vanilla effect, and the comedown is whatever sits
 * across the wheel from it. Phase 5 replaces the lookup with the real thing — up to three of the
 * mod's own effects, chosen by proximity in the five-axis space — but the shape of this class and the
 * comedown rule are already what they will be.
 */
public final class BrewEffects {
    /** One per wheel position, in rotation order. */
    private static final StatusEffect[] BY_HUMOUR = {
            StatusEffects.STRENGTH,      // choleric
            StatusEffects.RESISTANCE,    // melancholic
            StatusEffects.REGENERATION,  // sanguine
            StatusEffects.INVISIBILITY,  // phlegmatic
    };

    /** The comedown never lasts longer than this share of what you drank it for. */
    private static final float COMEDOWN_DURATION = 0.6f;

    private BrewEffects() {
    }

    public static List<StatusEffectInstance> of(Brew brew) {
        List<StatusEffectInstance> effects = new ArrayList<>(2);

        Humours now = brew.current();
        if (now.magnitude() <= 0) {
            return effects;
        }

        int dominant = now.dominant();
        effects.add(new StatusEffectInstance(
                BY_HUMOUR[dominant], brew.durationTicks(), brew.amplifier(), false, true, true));

        // The shadow is always the far side of the wheel, and it scales with how lopsided the brew
        // is — so balancing a brew is what spares you the comedown, without anything extra authored.
        float severity = brew.comedown();
        if (severity > 0.15f) {
            int opposite = (dominant + Humours.WHEEL / 2) % Humours.WHEEL;
            int duration = Math.round(brew.durationTicks() * COMEDOWN_DURATION * severity);

            if (duration > 0) {
                effects.add(new StatusEffectInstance(
                        BY_HUMOUR[opposite], duration, 0, false, true, true));
            }
        }

        return effects;
    }

    public static void apply(LivingEntity drinker, Brew brew) {
        for (StatusEffectInstance effect : of(brew)) {
            drinker.addStatusEffect(effect);
        }
    }
}
