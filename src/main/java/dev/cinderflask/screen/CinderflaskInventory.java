package dev.cinderflask.screen;

import dev.cinderflask.item.CinderflaskItem;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.inventory.Inventory;
import net.minecraft.item.ItemStack;

/**
 * The flask's single intake slot. Anything dropped in is absorbed immediately.
 *
 * <p>Absorption is server-side only; on the client this just mirrors what the server last sent.
 */
public class CinderflaskInventory implements Inventory {
    private final ItemStack flask;
    private final boolean absorbing;

    private ItemStack contents = ItemStack.EMPTY;
    private boolean reentrant;

    public CinderflaskInventory(ItemStack flask, boolean absorbing) {
        this.flask = flask;
        this.absorbing = absorbing;
    }

    @Override
    public void markDirty() {
        if (!absorbing || reentrant || flask.isEmpty() || contents.isEmpty()) {
            return;
        }

        reentrant = true;
        try {
            CinderflaskItem.addFuel(flask, contents);

            if (contents.isEmpty()) {
                contents = ItemStack.EMPTY;
            }
        } finally {
            reentrant = false;
        }
    }

    @Override
    public int size() {
        return 1;
    }

    @Override
    public boolean isEmpty() {
        return contents.isEmpty();
    }

    @Override
    public ItemStack getStack(int slot) {
        return slot == 0 ? contents : ItemStack.EMPTY;
    }

    @Override
    public ItemStack removeStack(int slot, int amount) {
        if (slot != 0 || contents.isEmpty()) {
            return ItemStack.EMPTY;
        }

        ItemStack removed = contents.split(amount);

        if (contents.isEmpty()) {
            contents = ItemStack.EMPTY;
        }

        return removed;
    }

    @Override
    public ItemStack removeStack(int slot) {
        if (slot != 0) {
            return ItemStack.EMPTY;
        }

        ItemStack removed = contents;
        contents = ItemStack.EMPTY;
        return removed;
    }

    @Override
    public void setStack(int slot, ItemStack stack) {
        if (slot != 0) {
            return;
        }

        contents = stack;
        markDirty();
    }

    @Override
    public boolean isValid(int slot, ItemStack stack) {
        return slot == 0 && CinderflaskItem.isValidFuel(stack);
    }

    @Override
    public boolean canPlayerUse(PlayerEntity player) {
        return true;
    }

    @Override
    public void clear() {
        contents = ItemStack.EMPTY;
    }
}
