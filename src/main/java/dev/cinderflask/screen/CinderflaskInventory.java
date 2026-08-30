package dev.cinderflask.screen;

import dev.cinderflask.brew.IngredientTable;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.inventory.Inventory;
import net.minecraft.item.ItemStack;

/**
 * The flask's single intake slot. Anything dropped in is folded into the brew immediately.
 *
 * <p>Server-side only; on the client this just mirrors what the server last sent.
 */
public class CinderflaskInventory implements Inventory {
    private final ItemStack flask;
    private final boolean absorbing;

    private ItemStack contents = ItemStack.EMPTY;
    private boolean reentrant;

    private java.util.function.BiConsumer<ItemStack, ItemStack> onAdded = (flask, ingredient) -> { };

    public CinderflaskInventory(ItemStack flask, boolean absorbing) {
        this.flask = flask;
        this.absorbing = absorbing;
    }

    /** How a dropped-in ingredient actually reaches the brew. Set by the screen handler. */
    public void onAdded(java.util.function.BiConsumer<ItemStack, ItemStack> handler) {
        this.onAdded = handler;
    }

    @Override
    public void markDirty() {
        if (!absorbing || reentrant || flask.isEmpty() || contents.isEmpty()) {
            return;
        }

        reentrant = true;
        try {
            onAdded.accept(flask, contents);

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
        return slot == 0 && IngredientTable.isIngredient(stack);
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
