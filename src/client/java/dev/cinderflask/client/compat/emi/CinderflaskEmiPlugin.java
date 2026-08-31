package dev.cinderflask.client.compat.emi;

import dev.cinderflask.Cinderflask;
import dev.cinderflask.brew.IngredientTable;
import dev.cinderflask.brew.Landmarks;
import dev.cinderflask.brew.Temper;
import dev.cinderflask.brew.Tempering;
import dev.emi.emi.api.EmiPlugin;
import dev.emi.emi.api.EmiRegistry;
import dev.emi.emi.api.recipe.EmiRecipeCategory;
import dev.emi.emi.api.stack.EmiStack;
import net.minecraft.block.Block;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.Registries;
import net.minecraft.util.Identifier;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * Three things a recipe viewer cannot work out on its own.
 *
 * <p>Brewing is not a recipe — it is an item written into a vector — so nothing about it would show up
 * without saying so explicitly. Tempering is an interaction with a block. And the landmarks are
 * coordinates rather than outputs, which is why their routes are solved from the live table rather
 * than authored: retune an ingredient in a datapack and the suggestion retunes with it.
 */
public class CinderflaskEmiPlugin implements EmiPlugin {
    static final EmiStack FLASK = EmiStack.of(Cinderflask.CINDERFLASK);

    public static final EmiRecipeCategory BREWING =
            new EmiRecipeCategory(Cinderflask.id("brewing"), FLASK, FLASK);

    public static final EmiRecipeCategory TEMPERING =
            new EmiRecipeCategory(Cinderflask.id("tempering"), FLASK, FLASK);

    public static final EmiRecipeCategory LANDMARKS =
            new EmiRecipeCategory(Cinderflask.id("landmarks"), FLASK, FLASK);

    @Override
    public void register(EmiRegistry registry) {
        registry.addCategory(BREWING);
        registry.addCategory(TEMPERING);
        registry.addCategory(LANDMARKS);

        for (var vessel : Cinderflask.vessels()) {
            registry.addWorkstation(BREWING, EmiStack.of(vessel));
            registry.addWorkstation(TEMPERING, EmiStack.of(vessel));
            registry.addWorkstation(LANDMARKS, EmiStack.of(vessel));
        }

        // Every ingredient the table currently knows, and what it writes.
        for (Item item : Registries.ITEM) {
            ItemStack stack = new ItemStack(item);
            IngredientTable.Entry entry = IngredientTable.lookup(stack);

            if (entry != null && !entry.isNothing()) {
                registry.addRecipe(new IngredientEmiRecipe(
                        Cinderflask.id("brewing/" + Registries.ITEM.getId(item).toUnderscoreSeparatedString()),
                        stack, entry));
            }
        }

        for (Map.Entry<Temper, List<Block>> temper : temperSources().entrySet()) {
            registry.addRecipe(new TemperEmiRecipe(
                    Cinderflask.id("tempering/" + temper.getKey().id()),
                    temper.getKey(), temper.getValue()));
        }

        for (Landmarks.Landmark landmark : Landmarks.all()) {
            registry.addRecipe(new LandmarkEmiRecipe(landmark));
        }
    }

    /** Walks the block registry rather than duplicating the list, so the two cannot drift apart. */
    private static Map<Temper, List<Block>> temperSources() {
        Map<Temper, List<Block>> sources = new EnumMap<>(Temper.class);

        for (Block block : Registries.BLOCK) {
            Temper temper = Tempering.of(block.getDefaultState());
            if (temper != null) {
                sources.computeIfAbsent(temper, key -> new ArrayList<>()).add(block);
            }
        }

        return sources;
    }

    static Identifier idOf(String path) {
        return Cinderflask.id(path);
    }
}
