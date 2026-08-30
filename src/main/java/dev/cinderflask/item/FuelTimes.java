package dev.cinderflask.item;

import net.minecraft.block.entity.AbstractFurnaceBlockEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;

import java.util.Map;

/**
 * Smelting burn times for arbitrary stacks.
 *
 * <p>{@link AbstractFurnaceBlockEntity#createFuelTimeMap()} is authoritative (Fabric API appends the
 * whole {@code FuelRegistry} to it at RETURN) but allocates a fresh map every call, so it is cached
 * here and invalidated from {@code CommonLifecycleEvents.TAGS_LOADED}.
 */
public final class FuelTimes {
    private static volatile Map<Item, Integer> cache;

    private FuelTimes() {
    }

    public static void invalidate() {
        cache = null;
    }

    private static Map<Item, Integer> map() {
        Map<Item, Integer> local = cache;
        if (local == null) {
            local = AbstractFurnaceBlockEntity.createFuelTimeMap();
            cache = local;
        }
        return local;
    }

    /** Burn ticks this stack is worth in a furnace, or 0 if it is not fuel. */
    public static int of(ItemStack stack) {
        if (stack.isEmpty()) {
            return 0;
        }
        return map().getOrDefault(stack.getItem(), 0);
    }
}
