package dev.cinderflask.mixin;

import dev.cinderflask.config.CinderflaskConfig;
import dev.cinderflask.item.CinderflaskItem;
import net.minecraft.block.entity.AbstractFurnaceBlockEntity;
import net.minecraft.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Makes burn time stack-aware for the flask, and only for the flask.
 *
 * <p>Vanilla looks burn time up in an {@code Item}-keyed map and Fabric's {@code FuelRegistry} is
 * item-keyed too, so registering there alone would let an empty flask burn forever.
 *
 * <p>HEAD does not conflict with Fabric API's own {@code @Redirect} on this method, which swaps the
 * map lookup further in. {@code getFuelTime} runs on ignition, not per tick.
 */
@Mixin(AbstractFurnaceBlockEntity.class)
public class AbstractFurnaceBlockEntityMixin {
    @Inject(method = "getFuelTime", at = @At("HEAD"), cancellable = true)
    private void cinderflask$stackAwareFuelTime(ItemStack fuel, CallbackInfoReturnable<Integer> cir) {
        if (!(fuel.getItem() instanceof CinderflaskItem)) {
            return;
        }

        int perOperation = CinderflaskConfig.get().ticksPerOperation;
        cir.setReturnValue(CinderflaskItem.getEmbers(fuel) >= perOperation ? perOperation : 0);
    }
}
