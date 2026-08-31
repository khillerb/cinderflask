package dev.cinderflask.mixin;

import dev.cinderflask.effect.CombatHooks;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.damage.DamageSource;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * The mod's only mixin.
 *
 * <p>Fabric's damage event can veto a blow but not resize one, and half the draughts are about
 * resizing blows. This is the narrowest seam that can: {@code modifyAppliedDamage} is vanilla's own
 * "last word on the number" method, the one Resistance itself uses.
 */
@Mixin(LivingEntity.class)
public abstract class LivingEntityMixin {
    @Inject(method = "modifyAppliedDamage", at = @At("RETURN"), cancellable = true)
    private void cinderflask$draughts(DamageSource source, float amount,
                                      CallbackInfoReturnable<Float> info) {
        info.setReturnValue(CombatHooks.damage(
                (LivingEntity) (Object) this, source, info.getReturnValue()));
    }
}
