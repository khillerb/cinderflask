package dev.cinderflask.brew;

import dev.cinderflask.config.CinderflaskConfig;
import dev.cinderflask.effect.CorruptDraughts;
import dev.cinderflask.effect.DraughtEffect;
import dev.cinderflask.effect.Draughts;
import dev.cinderflask.effect.Rebounds;
import dev.cinderflask.effect.Unspent;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.item.ItemStack;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.List;

/**
 * What a brew does when you drink it.
 *
 * <p>Nothing is looked up. A brew is a point, the twelve landmarks are points, and what you get is
 * whichever landmarks you are standing near, in proportion to how near. Everything people say about
 * this system falls out of that one rule:
 *
 * <ul>
 *   <li>Hit a landmark squarely and you get almost all of one draught, because nothing else is close.
 *   <li>Sit between two and you get both, split by which you are nearer — so the ground between two
 *       named brews is continuous rather than empty.
 *   <li>Brew something perfectly even and you get three weak draughts and no comedown at all, which
 *       is the trade balance is for.
 * </ul>
 *
 * <p>On top of that sit the {@link Inflection}s: thresholds the brew has crossed, each bending the
 * dose a little and compounding when several are true at once. Corruption is one of them, and past
 * it every draught comes from {@link CorruptDraughts} instead.
 *
 * <p>The crash afterwards is the same idea running backwards — see {@link Rebounds}.
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

    /** What each inflection past the first is worth to duration. */
    private static final float PER_INFLECTION = 0.12f;

    /** What DEEP alone is worth to duration, on top of that. */
    private static final float DEEP_STRETCH = 0.5f;

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

    /** What a brew alone produces. The vessel and mote inflections cannot fire without a flask. */
    public static List<StatusEffectInstance> of(Brew brew) {
        return of(null, brew);
    }

    public static List<StatusEffectInstance> of(@Nullable ItemStack flask, Brew brew) {
        List<StatusEffectInstance> effects = new ArrayList<>(5);

        Humours now = brew.current();
        if (now.magnitude() <= 0) {
            return effects;
        }

        EnumSet<Inflection> crossed = Inflection.of(flask, brew);
        boolean foul = crossed.contains(Inflection.FOUL);

        int duration = Math.round(brew.durationTicks() * stretch(crossed));
        int amplifier = brew.amplifier() + (crossed.contains(Inflection.CONCENTRATED) ? 1 : 0);

        List<Share> shares = shares(now);

        // Sitting squarely on a landmark means the whole dose is that draught, not a blend that
        // happens to lean towards it.
        if (crossed.contains(Inflection.EXACT) && !shares.isEmpty()) {
            shares = List.of(new Share(shares.get(0).landmark(), 1));
        }

        for (Share share : shares) {
            DraughtEffect draught = foul
                    ? CorruptDraughts.of(share.landmark())
                    : Draughts.of(share.landmark());

            int ticks = Math.round(duration * share.weight());

            if (draught == null || ticks <= 0) {
                continue;
            }

            effects.add(new StatusEffectInstance(draught, ticks,
                    Math.round(amplifier * share.weight()), false, true, true));
        }

        // A level brew has nothing to swing back from; everything else pays for how lopsided and how
        // concentrated it is. No separate rule, and nothing extra to author.
        if (!crossed.contains(Inflection.LEVEL)) {
            float severity = brew.comedown() * CinderflaskConfig.get().draughts.comedownSeverity;

            if (severity > COMEDOWN_THRESHOLD) {
                int ticks = Math.round(duration * COMEDOWN_DURATION * severity);

                if (ticks > 0) {
                    effects.add(new StatusEffectInstance(
                            Rebounds.forHumour(now.dominant()), ticks, 0, false, true, true));
                }
            }
        }

        // And the capstone, which no coordinate can reach on its own.
        if (Inflection.capstoned(crossed)) {
            effects.add(new StatusEffectInstance(Unspent.EFFECT, duration, 0, false, true, true));
        }

        return effects;
    }

    /** Every inflection lengthens a dose a little; age lengthens it a lot. */
    private static float stretch(EnumSet<Inflection> crossed) {
        float extra = Math.max(0, crossed.size() - 1) * PER_INFLECTION;
        return 1 + extra + (crossed.contains(Inflection.DEEP) ? DEEP_STRETCH : 0);
    }

    public static void apply(LivingEntity drinker, Brew brew) {
        apply(drinker, null, brew);
    }

    public static void apply(LivingEntity drinker, @Nullable ItemStack flask, Brew brew) {
        for (StatusEffectInstance effect : of(flask, brew)) {
            drinker.addStatusEffect(effect);
        }
    }
}
