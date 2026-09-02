package dev.cinderflask.effect;

import dev.cinderflask.Cinderflask;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.minecraft.entity.effect.StatusEffect;
import net.minecraft.entity.effect.StatusEffectCategory;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;

/**
 * What a brew gives when enough of it is true at once.
 *
 * <p>No landmark grants this. It is the capstone: cross enough inflections in one flask and the dose
 * carries a refusal — the next blow that would finish you leaves you at a sliver instead, once, and
 * then it is gone.
 *
 * <p>Deliberately the only thing in the mod that is not reachable by aiming at a coordinate. You get
 * it by making a brew that is several things at the same time, which is a different kind of skill
 * from hitting a point.
 */
public final class Unspent {
    /** What you are left standing on. Half a heart, so it is a reprieve and not a win. */
    private static final float SLIVER = 1;

    public static final StatusEffect EFFECT =
            new StatusEffect(StatusEffectCategory.BENEFICIAL, 0xF3CC66) {
            };

    private Unspent() {
    }

    public static void register() {
        Registry.register(Registries.STATUS_EFFECT, Cinderflask.id("unspent"), EFFECT);

        ServerLivingEntityEvents.ALLOW_DEATH.register((entity, source, amount) -> {
            StatusEffectInstance held = entity.getStatusEffect(EFFECT);
            if (held == null) {
                return true;
            }

            // Spent on the way out. Vanilla checks health every tick, so it has to be set here or
            // the entity simply dies again next tick.
            entity.removeStatusEffect(EFFECT);
            entity.setHealth(SLIVER);
            entity.clearStatusEffects();
            entity.addStatusEffect(new StatusEffectInstance(
                    net.minecraft.entity.effect.StatusEffects.REGENERATION, 200, 1));

            entity.getWorld().playSound(null, entity.getBlockPos(), SoundEvents.ITEM_TOTEM_USE,
                    SoundCategory.PLAYERS, 1.0F, 1.4F);

            return false;
        });
    }
}
