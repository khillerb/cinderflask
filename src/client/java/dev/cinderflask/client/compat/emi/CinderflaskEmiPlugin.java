package dev.cinderflask.client.compat.emi;

import dev.cinderflask.Cinderflask;
import dev.cinderflask.brew.IngredientTable;
import dev.cinderflask.brew.Landmarks;
import dev.cinderflask.brew.Temper;
import dev.cinderflask.brew.Tempering;
import dev.cinderflask.recipe.VesselOperation;
import dev.emi.emi.api.EmiPlugin;
import dev.emi.emi.api.EmiRegistry;
import dev.emi.emi.api.recipe.EmiInfoRecipe;
import dev.emi.emi.api.recipe.EmiRecipeCategory;
import dev.emi.emi.api.stack.EmiIngredient;
import dev.emi.emi.api.stack.EmiStack;
import net.minecraft.block.Block;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.recipe.Recipe;
import net.minecraft.registry.Registries;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * The parts of this mod a recipe viewer cannot work out on its own.
 *
 * <p>Brewing is not a recipe — it is an item written into a vector — so nothing about it would show up
 * without saying so explicitly. Tempering is an interaction with a block. The landmarks are
 * coordinates rather than outputs. And the bench operations are all special recipes, which declare no
 * ingredients and no output, so every one of them was invisible until they were taught to describe
 * themselves.
 *
 * <p>Nothing here is a list. Ingredients come from the live table, temper sources from the block
 * registry, vessel operations from the recipe manager, and landmark routes are solved. Retune any of
 * it in a datapack and these pages retune with it.
 */
public class CinderflaskEmiPlugin implements EmiPlugin {
    static final EmiStack FLASK = EmiStack.of(Cinderflask.CINDERFLASK);

    public static final EmiRecipeCategory BREWING =
            new EmiRecipeCategory(Cinderflask.id("brewing"), FLASK, FLASK);

    public static final EmiRecipeCategory TEMPERING =
            new EmiRecipeCategory(Cinderflask.id("tempering"), FLASK, FLASK);

    public static final EmiRecipeCategory LANDMARKS =
            new EmiRecipeCategory(Cinderflask.id("landmarks"), FLASK, FLASK);

    public static final EmiRecipeCategory VESSEL =
            new EmiRecipeCategory(Cinderflask.id("vessel"), FLASK, FLASK);

    @Override
    public void register(EmiRegistry registry) {
        registry.addCategory(BREWING);
        registry.addCategory(TEMPERING);
        registry.addCategory(LANDMARKS);
        registry.addCategory(VESSEL);

        for (var vessel : Cinderflask.vessels()) {
            registry.addWorkstation(BREWING, EmiStack.of(vessel));
            registry.addWorkstation(TEMPERING, EmiStack.of(vessel));
            registry.addWorkstation(LANDMARKS, EmiStack.of(vessel));
        }

        registry.addWorkstation(VESSEL, EmiStack.of(net.minecraft.item.Items.CRAFTING_TABLE));

        ingredients(registry);
        tempering(registry);
        landmarks(registry);
        vesselOperations(registry);
        vesselLife(registry);
    }

    /** Every ingredient the table currently knows, and what it writes. */
    private static void ingredients(EmiRegistry registry) {
        for (Item item : Registries.ITEM) {
            ItemStack stack = new ItemStack(item);
            IngredientTable.Entry entry = IngredientTable.lookup(stack);

            if (entry != null && !entry.isNothing()) {
                registry.addRecipe(new IngredientEmiRecipe(
                        synthetic("brewing/" + Registries.ITEM.getId(item).toUnderscoreSeparatedString()),
                        stack, entry));
            }
        }
    }

    private static void tempering(EmiRegistry registry) {
        for (Map.Entry<Temper, List<Block>> temper : temperSources().entrySet()) {
            registry.addRecipe(new TemperEmiRecipe(
                    synthetic("tempering/" + temper.getKey().id()),
                    temper.getKey(), temper.getValue()));
        }
    }

    private static void landmarks(EmiRegistry registry) {
        for (Landmarks.Landmark landmark : Landmarks.all()) {
            registry.addRecipe(new LandmarkEmiRecipe(landmark));
        }
    }

    /**
     * Every bench operation that describes itself, straight out of the recipe manager. Nothing here
     * knows how many there are, so a new tier or a new operation shows up on its own.
     */
    private static void vesselOperations(EmiRegistry registry) {
        for (Recipe<?> recipe : registry.getRecipeManager().values()) {
            if (recipe instanceof VesselOperation operation) {
                registry.addRecipe(new VesselEmiRecipe(
                        synthetic("vessel/" + recipe.getId().toUnderscoreSeparatedString()),
                        operation));
            }
        }
    }

    /** The parts of a flask's life that are not recipes at all, and so have nowhere else to be said. */
    private static void vesselLife(EmiRegistry registry) {
        info(registry, "dregs", EmiStack.of(Cinderflask.DREGS));
        info(registry, "sump", EmiStack.of(Cinderflask.SUMP));
        info(registry, "cracked", FLASK);
        info(registry, "ageing", FLASK);
    }

    private static void info(EmiRegistry registry, String name, EmiIngredient subject) {
        registry.addRecipe(new EmiInfoRecipe(List.of(subject),
                List.of(Text.translatable("cinderflask.info." + name)),
                synthetic("info/" + name)));
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

    /**
     * An id for a page that has no recipe behind it.
     *
     * <p>EMI requires the leading slash and complains about every page that omits it, because
     * anything without one it expects to find in the recipe manager. Almost nothing this mod shows
     * is a recipe, so almost everything here needs it.
     */
    static Identifier synthetic(String path) {
        return Cinderflask.id("/" + path);
    }
}
