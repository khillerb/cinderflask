package dev.cinderflask.client.compat.emi;

import dev.cinderflask.Cinderflask;
import dev.cinderflask.tag.CinderflaskTags;
import dev.emi.emi.api.EmiPlugin;
import dev.emi.emi.api.EmiRegistry;
import dev.emi.emi.api.recipe.EmiRecipe;
import dev.emi.emi.api.recipe.EmiRecipeCategory;
import dev.emi.emi.api.stack.EmiStack;
import net.minecraft.entity.EntityType;
import net.minecraft.item.SpawnEggItem;
import net.minecraft.registry.Registries;
import net.minecraft.util.Identifier;

import java.util.ArrayList;
import java.util.List;

/**
 * Sparking is an interaction, not a recipe, so nothing would show it in a recipe viewer otherwise.
 * The category is built from {@link CinderflaskTags#SPARK_SOURCE}, so a datapack that changes the
 * tag changes this too.
 *
 * <p>EMI is compile-only and only EMI requests this entrypoint, so none of it loads without EMI.
 */
public class CinderflaskEmiPlugin implements EmiPlugin {
    static final EmiStack FLASK = EmiStack.of(Cinderflask.CINDERFLASK);
    static final EmiStack EMPTY_FLASK = EmiStack.of(Cinderflask.EMPTY_CINDERFLASK);

    public static final EmiRecipeCategory SPARKING =
            new EmiRecipeCategory(Cinderflask.id("sparking"), FLASK, FLASK);

    @Override
    public void register(EmiRegistry registry) {
        List<EmiRecipe> recipes = new ArrayList<>();

        Registries.ENTITY_TYPE.getEntryList(CinderflaskTags.SPARK_SOURCE).ifPresent(entries -> {
            for (var entry : entries) {
                EntityType<?> type = entry.value();
                SpawnEggItem egg = SpawnEggItem.forEntity(type);

                // No spawn egg means nothing to put in the slot.
                if (egg == null) {
                    continue;
                }

                Identifier typeId = Registries.ENTITY_TYPE.getId(type);
                recipes.add(new SparkingEmiRecipe(
                        Cinderflask.id("sparking/" + typeId.getNamespace() + "/" + typeId.getPath()),
                        type,
                        EmiStack.of(egg)));
            }
        });

        if (recipes.isEmpty()) {
            return;
        }

        registry.addCategory(SPARKING);
        registry.addWorkstation(SPARKING, EMPTY_FLASK);

        for (EmiRecipe recipe : recipes) {
            registry.addRecipe(recipe);
        }
    }
}
