package dev.cinderflask.effect;

import dev.cinderflask.config.CinderflaskConfig;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.registry.tag.DamageTypeTags;
import net.minecraft.util.math.Vec3d;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * The one place an effect of this mod can touch a blow.
 *
 * <p>Everything combat-shaped comes through here, and nothing here knows which effects exist. It
 * reads whatever the two fighters are carrying, asks anything implementing one of the interfaces
 * below what it wants to do, and applies the answer — so a new draught or rebound is a new class and
 * no edit to this file.
 *
 * <p>Called from {@code LivingEntity#modifyAppliedDamage}, which is late enough that armour and
 * Resistance have already had their say and early enough that the number still matters. Absorption is
 * subtracted afterwards, so an effect sees the blow before a golden apple eats part of it.
 */
public final class CombatHooks {
    /**
     * True while a reaction is running. Bramblewine answers a blow with a blow, and two people
     * wearing it would otherwise trade one hit back and forth until the stack ran out.
     */
    private static boolean answering;

    private CombatHooks() {
    }

    // -------------------------------------------------------------------------------------------
    // What an effect may ask for
    // -------------------------------------------------------------------------------------------

    /**
     * Changes a blow the bearer is about to land.
     *
     * @param bearer the fighter carrying the effect, always the attacker here
     * @param other  whoever is being hit
     */
    public interface Striking {
        float striking(LivingEntity bearer, LivingEntity other, DamageSource source, float amount,
                       int amplifier);
    }

    /**
     * Changes a blow the bearer is about to take.
     *
     * @param bearer the fighter carrying the effect, always the victim here
     * @param other  whoever is hitting them, or null for the world itself
     */
    public interface Enduring {
        float enduring(LivingEntity bearer, @Nullable LivingEntity other, DamageSource source,
                       float amount, int amplifier);
    }

    /**
     * Does something once the blow has landed. Called for both fighters, so {@code bearer} is
     * whichever of them carries the effect and {@code other} is the one across from them.
     *
     * <p>Anything done here that deals damage will not itself provoke further answers: the guard
     * below stops two people wearing Bramblewine bouncing a hit between them forever.
     */
    public interface Answering {
        void answer(LivingEntity bearer, @Nullable LivingEntity other, DamageSource source,
                    float amount, int amplifier);
    }

    // -------------------------------------------------------------------------------------------
    // Shared geometry
    // -------------------------------------------------------------------------------------------

    /**
     * Whether {@code attacker} is standing behind {@code victim}.
     *
     * <p>Shared rather than written twice: the Unseen Hand pays out on this being true and Plain
     * Sight punishes it, and a rebound that disagreed with the draught it inverts would be a bug
     * nobody could see.
     */
    public static boolean isBehind(LivingEntity attacker, LivingEntity victim) {
        Vec3d facing = victim.getRotationVec(1).multiply(1, 0, 1).normalize();
        Vec3d toAttacker = attacker.getPos().subtract(victim.getPos()).multiply(1, 0, 1).normalize();

        return facing.dotProduct(toAttacker) < 0;
    }

    // -------------------------------------------------------------------------------------------
    // The hook
    // -------------------------------------------------------------------------------------------

    public static float damage(LivingEntity victim, DamageSource source, float amount) {
        // Effects are server business, and damage that bypasses effects bypasses these too.
        if (victim.getWorld().isClient || source.isIn(DamageTypeTags.BYPASSES_EFFECTS)) {
            return amount;
        }

        LivingEntity attacker = source.getAttacker() instanceof LivingEntity living ? living : null;

        // A server that wants brews to be a world mechanic rather than a duelling one can say so.
        if (!CinderflaskConfig.get().draughtsAffectPvp
                && victim instanceof PlayerEntity && attacker instanceof PlayerEntity) {
            return amount;
        }

        if (attacker != null) {
            for (StatusEffectInstance held : held(attacker)) {
                if (held.getEffectType() instanceof Striking striking) {
                    amount = striking.striking(attacker, victim, source, amount, held.getAmplifier());
                }
            }
        }

        for (StatusEffectInstance held : held(victim)) {
            if (held.getEffectType() instanceof Enduring enduring) {
                amount = enduring.enduring(victim, attacker, source, amount, held.getAmplifier());
            }
        }

        amount = Math.max(0, amount);
        answer(victim, attacker, source, amount);
        return amount;
    }

    private static void answer(LivingEntity victim, @Nullable LivingEntity attacker,
                               DamageSource source, float amount) {
        if (answering || amount <= 0) {
            return;
        }

        answering = true;
        try {
            answer(victim, attacker, source, amount, held(victim));

            if (attacker != null) {
                answer(attacker, victim, source, amount, held(attacker));
            }
        } finally {
            answering = false;
        }
    }

    private static void answer(LivingEntity bearer, @Nullable LivingEntity other, DamageSource source,
                               float amount, List<StatusEffectInstance> carrying) {
        for (StatusEffectInstance held : carrying) {
            if (held.getEffectType() instanceof Answering answering) {
                answering.answer(bearer, other, source, amount, held.getAmplifier());
            }
        }
    }

    /** A copy, because an answer is allowed to hand somebody a new effect mid-walk. */
    private static List<StatusEffectInstance> held(LivingEntity entity) {
        return List.copyOf(entity.getStatusEffects());
    }
}
