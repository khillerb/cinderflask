package dev.cinderflask.recipe;

import dev.cinderflask.Cinderflask;
import dev.cinderflask.brew.BrewNbt;
import dev.cinderflask.brew.Brewing;
import dev.cinderflask.item.CinderflaskItem;
import net.minecraft.inventory.RecipeInputInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.recipe.Ingredient;
import net.minecraft.recipe.RecipeSerializer;
import net.minecraft.recipe.SpecialCraftingRecipe;
import net.minecraft.recipe.book.CraftingRecipeCategory;
import net.minecraft.registry.DynamicRegistryManager;
import net.minecraft.registry.tag.ItemTags;
import net.minecraft.util.Identifier;
import net.minecraft.world.World;

import java.util.List;

/**
 * Stops up a working brew, which is what starts its clock.
 *
 * <p>A special recipe rather than a shaped one because the flask's whole state has to survive the
 * bench — the same reason vanilla clones maps this way.
 *
 * <p>It cannot record a seal time: a crafting recipe has no world. It sets the cork, and
 * {@link CinderflaskItem#inventoryTick} stamps the clock the first time the flask is somewhere that
 * has one.
 */
public class CorkRecipe extends SpecialCraftingRecipe implements VesselOperation {
    public CorkRecipe(Identifier id, CraftingRecipeCategory category) {
        super(id, category);
    }

    @Override
    public boolean matches(RecipeInputInventory inventory, World world) {
        return findFlask(inventory) != null && countStoppers(inventory) == 1 && nothingElse(inventory);
    }

    @Override
    public ItemStack craft(RecipeInputInventory inventory, DynamicRegistryManager registryManager) {
        ItemStack flask = findFlask(inventory);
        if (flask == null) {
            return ItemStack.EMPTY;
        }

        // Corked on a copy, so the seasoning this records belongs to the flask that comes out.
        ItemStack corked = flask.copy();
        Brewing.cork(corked);
        return corked;
    }

    @Override
    public List<Ingredient> inputs() {
        return List.of(VesselRecipes.anyVessel(), Ingredient.fromTag(ItemTags.PLANKS));
    }

    @Override
    public ItemStack preview() {
        return new ItemStack(Cinderflask.CINDERFLASK);
    }

    @Override
    public String descriptionKey() {
        return "cinderflask.vessel.cork";
    }

    @Override
    public boolean fits(int width, int height) {
        return width * height >= 2;
    }

    @Override
    public RecipeSerializer<?> getSerializer() {
        return Cinderflask.CORK_RECIPE;
    }

    private static ItemStack findFlask(RecipeInputInventory inventory) {
        ItemStack found = null;

        for (int slot = 0; slot < inventory.size(); slot++) {
            ItemStack stack = inventory.getStack(slot);
            if (stack.getItem() instanceof CinderflaskItem) {
                // Only a working brew can be corked, and only one flask at a time.
                if (found != null || !BrewNbt.hasBrew(stack) || BrewNbt.isCorked(stack)) {
                    return null;
                }
                found = stack;
            }
        }

        return found;
    }

    private static int countStoppers(RecipeInputInventory inventory) {
        int stoppers = 0;

        for (int slot = 0; slot < inventory.size(); slot++) {
            if (inventory.getStack(slot).isIn(ItemTags.PLANKS)) {
                stoppers++;
            }
        }

        return stoppers;
    }

    private static boolean nothingElse(RecipeInputInventory inventory) {
        for (int slot = 0; slot < inventory.size(); slot++) {
            ItemStack stack = inventory.getStack(slot);
            if (!stack.isEmpty() && !stack.isIn(ItemTags.PLANKS)
                    && !(stack.getItem() instanceof CinderflaskItem)) {
                return false;
            }
        }

        return true;
    }
}
