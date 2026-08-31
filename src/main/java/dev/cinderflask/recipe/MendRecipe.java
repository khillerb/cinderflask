package dev.cinderflask.recipe;

import dev.cinderflask.Cinderflask;
import dev.cinderflask.item.SinterItem;
import net.minecraft.inventory.Inventory;
import net.minecraft.item.ItemStack;
import net.minecraft.recipe.AbstractCookingRecipe;
import net.minecraft.recipe.Ingredient;
import net.minecraft.recipe.RecipeSerializer;
import net.minecraft.recipe.RecipeType;
import net.minecraft.recipe.book.CookingRecipeCategory;
import net.minecraft.registry.DynamicRegistryManager;
import net.minecraft.util.Identifier;

/**
 * Fires a sintered flask back into a whole one.
 *
 * <p>A smelting recipe normally hands back a fixed stack, which would quietly destroy the vessel's
 * mote, seasoning and name. This one reads the flask out of what went in, so the thing you get back
 * is the thing you put in.
 */
public class MendRecipe extends AbstractCookingRecipe {
    public MendRecipe(Identifier id, String group, CookingRecipeCategory category,
                      Ingredient input, ItemStack output, float experience, int cookingTime) {
        super(RecipeType.SMELTING, id, group, category, input, output, experience, cookingTime);
    }

    @Override
    public ItemStack craft(Inventory inventory, DynamicRegistryManager registries) {
        ItemStack packed = SinterItem.unpack(inventory.getStack(0));
        return packed.isEmpty() ? super.craft(inventory, registries) : packed;
    }

    @Override
    public RecipeSerializer<?> getSerializer() {
        return Cinderflask.MEND_RECIPE;
    }
}
