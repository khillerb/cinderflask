package dev.cinderflask.effect;

import dev.cinderflask.Cinderflask;
import dev.cinderflask.brew.Humours;
import dev.cinderflask.config.CinderflaskConfig;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * The four rebounds, one to a humour.
 *
 * <p>Each one is the draughts of its humour read backwards. Choleric lends you force, so its rebound
 * takes force away; melancholic lends you a wall, so its rebound takes the wall away; sanguine mends
 * you, so its rebound bleeds you; phlegmatic pays you for going unseen, so its rebound charges you
 * for being seen. Nothing here is a new mechanic — it is the same three hooks with the sign flipped,
 * which is why {@link CombatHooks} needs no knowledge of rebounds at all.
 */
public final class Rebounds {
    /** Choleric, inverted. Deadman's Vigour lent you force; this is the bill. */
    public static final ReboundEffect ASHFALL = new Ashfall();

    /** Melancholic, inverted. Ironroot took a slice off every blow; now every blow takes more. */
    public static final ReboundEffect BRITTLE = new Brittle();

    /** Sanguine, inverted. Sapsworn and Honeyed mended you; this runs the same tap backwards. */
    public static final ReboundEffect BLOODLESS = new Bloodless();

    /** Phlegmatic, inverted. The Unseen Hand paid you from behind; now behind is where it hurts. */
    public static final ReboundEffect PLAIN_SIGHT = new PlainSight();

    /** Indexed by wheel position, so the crash is chosen the same way the humour is. */
    private static final ReboundEffect[] BY_HUMOUR = {ASHFALL, BRITTLE, BLOODLESS, PLAIN_SIGHT};

    private Rebounds() {
    }

    private static CinderflaskConfig.Tuning tuning() {
        return CinderflaskConfig.get().draughts;
    }

    public static void register() {
        Registry.register(Registries.STATUS_EFFECT, Cinderflask.id("ashfall"), ASHFALL);
        Registry.register(Registries.STATUS_EFFECT, Cinderflask.id("brittle"), BRITTLE);
        Registry.register(Registries.STATUS_EFFECT, Cinderflask.id("bloodless"), BLOODLESS);
        Registry.register(Registries.STATUS_EFFECT, Cinderflask.id("plain_sight"), PLAIN_SIGHT);
    }

    /** The crash that follows a brew led by this humour. */
    public static ReboundEffect forHumour(int humour) {
        return BY_HUMOUR[Math.floorMod(humour, Humours.WHEEL)];
    }

    public static List<ReboundEffect> all() {
        return List.of(BY_HUMOUR);
    }

    // -------------------------------------------------------------------------------------------
    // The four
    // -------------------------------------------------------------------------------------------

    private static final class Ashfall extends ReboundEffect implements CombatHooks.Striking {
        /** You are spent, not harmless. */
        private static final float FLOOR = 0.25f;

        private Ashfall() {
            super(0);
        }

        @Override
        public float striking(LivingEntity bearer, LivingEntity other, DamageSource source,
                              float amount, int amplifier) {
            float softening = tuning().dial(tuning().ashfallSoftening) * (amplifier + 1);
            return Math.max(amount * FLOOR, amount * (1 - softening));
        }
    }

    private static final class Brittle extends ReboundEffect implements CombatHooks.Enduring {
        private Brittle() {
            super(1);
        }

        @Override
        public float enduring(LivingEntity bearer, @Nullable LivingEntity other, DamageSource source,
                              float amount, int amplifier) {
            return amount * (1 + tuning().dial(tuning().brittleExposure) * (amplifier + 1));
        }
    }

    private static final class Bloodless extends ReboundEffect {
        /** The same clock Honeyed mends on, which is the point. */
        private static final int INTERVAL = 50;

        private Bloodless() {
            super(2);
        }

        @Override
        public boolean canApplyUpdateEffect(int duration, int amplifier) {
            int interval = INTERVAL >> amplifier;
            return interval <= 0 || duration % interval == 0;
        }

        @Override
        public void applyUpdateEffect(LivingEntity entity, int amplifier) {
            // A crash, not an execution: like Poison, it will not take the last of you.
            if (entity.getHealth() > 1) {
                entity.damage(entity.getDamageSources().magic(),
                        tuning().dial(tuning().bloodlessDrain));
            }
        }
    }

    private static final class PlainSight extends ReboundEffect implements CombatHooks.Enduring {
        private PlainSight() {
            super(3);
        }

        @Override
        public float enduring(LivingEntity bearer, @Nullable LivingEntity other, DamageSource source,
                              float amount, int amplifier) {
            // The same geometry the Unseen Hand is paid for, charged to you instead.
            if (other == null || !CombatHooks.isBehind(other, bearer)) {
                return amount;
            }

            return amount * (1 + tuning().dial(tuning().plainSightExposure) * (amplifier + 1));
        }
    }
}
