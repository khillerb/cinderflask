package dev.cinderflask.recipe;

import dev.cinderflask.Cinderflask;
import dev.cinderflask.item.CinderflaskItem;
import net.minecraft.inventory.RecipeInputInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.recipe.Ingredient;
import net.minecraft.recipe.RecipeSerializer;
import net.minecraft.recipe.SpecialCraftingRecipe;
import net.minecraft.recipe.book.CraftingRecipeCategory;
import net.minecraft.registry.DynamicRegistryManager;
import net.minecraft.util.Identifier;
import net.minecraft.util.collection.DefaultedList;
import net.minecraft.world.World;

import java.util.List;

/**
 * A book, written against a flask.
 *
 * <p>Special rather than shapeless for one reason: the flask has to survive it. A shapeless recipe
 * would consume the vessel, along with its mote, its seasoning and its name, in exchange for a
 * tutorial — which would be a strange thing to charge somebody for reading the instructions.
 */
public class AlmanacRecipe extends SpecialCraftingRecipe implements VesselOperation {
    public AlmanacRecipe(Identifier id, CraftingRecipeCategory category) {
        super(id, category);
    }

    @Override
    public boolean matches(RecipeInputInventory inventory, World world) {
        return VesselRecipes.findFlaskIn(inventory, stack -> true) != null
                && VesselRecipes.count(inventory, stack -> stack.isOf(Items.BOOK)) == 1
                && VesselRecipes.total(inventory) == 2;
    }

    @Override
    public ItemStack craft(RecipeInputInventory inventory, DynamicRegistryManager registries) {
        return new ItemStack(Cinderflask.ALMANAC);
    }

    /** Hands the flask straight back. Only the book is spent. */
    @Override
    public DefaultedList<ItemStack> getRemainder(RecipeInputInventory inventory) {
        DefaultedList<ItemStack> remainder =
                DefaultedList.ofSize(inventory.size(), ItemStack.EMPTY);

        for (int slot = 0; slot < inventory.size(); slot++) {
            ItemStack stack = inventory.getStack(slot);
            if (stack.getItem() instanceof CinderflaskItem) {
                remainder.set(slot, stack.copy());
            }
        }

        return remainder;
    }

    @Override
    public List<Ingredient> inputs() {
        return List.of(VesselRecipes.anyVessel(), Ingredient.ofItems(Items.BOOK));
    }

    @Override
    public ItemStack preview() {
        return new ItemStack(Cinderflask.ALMANAC);
    }

    @Override
    public String descriptionKey() {
        return "cinderflask.vessel.almanac";
    }

    @Override
    public boolean fits(int width, int height) {
        return width * height >= 2;
    }

    @Override
    public RecipeSerializer<?> getSerializer() {
        return Cinderflask.ALMANAC_RECIPE;
    }
}
