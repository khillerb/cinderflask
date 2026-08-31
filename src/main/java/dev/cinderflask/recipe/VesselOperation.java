package dev.cinderflask.recipe;

import net.minecraft.item.ItemStack;
import net.minecraft.recipe.Ingredient;

import java.util.List;

/**
 * A bench operation on a flask, described well enough that a recipe viewer can draw it.
 *
 * <p>These recipes all have to be special ones: the flask's whole tag has to survive the bench, and a
 * shaped recipe cannot carry a tag across. The cost of being special is that a viewer sees nothing —
 * a special recipe declares no ingredients and no output, so every one of them was invisible.
 *
 * <p>This is the declaration a viewer needs, kept on the recipe itself rather than in a list beside
 * it. The EMI plugin walks the recipe manager for anything implementing this, so adding a fifth
 * vessel tier adds its page without anybody remembering to.
 */
public interface VesselOperation {
    /** Everything that has to be on the bench, in the order a viewer should show it. */
    List<Ingredient> inputs();

    /**
     * What comes off it, for display only. The real output carries the flask's tag across, which is
     * the whole reason these are special recipes, and no static stack can stand for that.
     */
    ItemStack preview();

    /** A line saying what the operation does to the flask. */
    String descriptionKey();
}
