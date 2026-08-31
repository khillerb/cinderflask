package dev.cinderflask.brew;

import dev.cinderflask.config.CinderflaskConfig;
import dev.cinderflask.effect.DraughtEffect;
import dev.cinderflask.effect.Draughts;
import dev.cinderflask.effect.Rebounds;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.effect.StatusEffectInstance;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * What a brew does when you drink it.
 *
 * <p>Nothing is looked up. A brew is a point, the twelve landmarks are points, and what you get is
 * whichever landmarks you are standing near, in proportion to how near. Everything people will say
 * about this system falls out of that one rule:
 *
 * <ul>
 *   <li>Hit a landmark squarely and you get almost all of one draught, because nothing else is close.
 *   <li>Sit between two and you get both, split by which you are nearer — so the ground between two
 *       named brews is continuous rather than empty.
 *   <li>Brew something perfectly even and you get three weak draughts and no comedown at all, which
 *       is the trade balance is for.
 * </ul>
 *
 * <p>The crash afterwards is the same idea running backwards: a brew that was all one humour
 * rebounds by taking away exactly what that humour lent you. See {@link Rebounds}.
 */
public final class BrewEffects {
    /**
     * How hard proximity is favoured. Cosine similarity between two vectors that are both all
     * positive is never small, so raw similarity barely separates anything — everything scores 0.7.
     * Raising it to a power stretches the small differences that actually matter back out.
     */
    private static final float SHARPNESS = 12;

    /** Below this share a draught would last a few ticks and read as noise. */
    private static final float MIN_SHARE = 0.08f;

    /** The comedown never lasts longer than this share of what you drank it for. */
    private static final float COMEDOWN_DURATION = 0.6f;

    /** And an almost-level brew does not have one at all. */
    private static final float COMEDOWN_THRESHOLD = 0.15f;

    /** One landmark and how much of this brew is it. */
    public record Share(Landmarks.Landmark landmark, float weight) {
    }

    private BrewEffects() {
    }

    /**
     * Which landmarks a point in the space is near, and by how much. Weights always sum to 1.
     *
     * <p>Pure maths over the landmark table, so it can be reasoned about without a world.
     */
    public static List<Share> shares(Humours humours) {
        if (humours.isEmpty()) {
            return List.of();
        }

        List<Share> shares = new ArrayList<>(Landmarks.all().size());
        for (Landmarks.Landmark landmark : Landmarks.all()) {
            float similarity = Math.max(0, humours.similarity(landmark.target()));
            shares.add(new Share(landmark, (float) Math.pow(similarity, SHARPNESS)));
        }

        shares.sort(Comparator.comparingDouble(Share::weight).reversed());

        int cap = Math.max(1, CinderflaskConfig.get().maxDraughtsPerDose);
        return normalised(shares.subList(0, Math.min(cap, shares.size())));
    }

    /** Rescales to sum to 1, drops anything too faint to be worth an icon, and rescales again. */
    private static List<Share> normalised(List<Share> shares) {
        float total = 0;
        for (Share share : shares) {
            total += share.weight();
        }

        if (total <= 0) {
            return List.of();
        }

        List<Share> kept = new ArrayList<>(shares.size());
        float keptTotal = 0;

        for (Share share : shares) {
            if (share.weight() / total >= MIN_SHARE) {
                kept.add(share);
                keptTotal += share.weight();
            }
        }

        List<Share> out = new ArrayList<>(kept.size());
        for (Share share : kept) {
            out.add(new Share(share.landmark(), share.weight() / keptTotal));
        }

        return out;
    }

    public static List<StatusEffectInstance> of(Brew brew) {
        List<StatusEffectInstance> effects = new ArrayList<>(4);

        Humours now = brew.current();
        if (now.magnitude() <= 0) {
            return effects;
        }

        int duration = brew.durationTicks();
        int amplifier = brew.amplifier();

        for (Share share : shares(now)) {
            DraughtEffect draught = Draughts.of(share.landmark());
            int ticks = Math.round(duration * share.weight());

            if (draught == null || ticks <= 0) {
                continue;
            }

            effects.add(new StatusEffectInstance(draught, ticks,
                    Math.round(amplifier * share.weight()), false, true, true));
        }

        // The crash scales with how lopsided and how concentrated the brew is, so levelling one out
        // is what spares you it — no separate rule, and nothing extra to author.
        float severity = brew.comedown() * CinderflaskConfig.get().draughts.comedownSeverity;
        if (severity > COMEDOWN_THRESHOLD) {
            int ticks = Math.round(duration * COMEDOWN_DURATION * severity);

            if (ticks > 0) {
                effects.add(new StatusEffectInstance(
                        Rebounds.forHumour(now.dominant()), ticks, 0, false, true, true));
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
