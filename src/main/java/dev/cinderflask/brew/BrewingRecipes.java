package dev.cinderflask.brew;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import dev.cinderflask.Cinderflask;
import net.fabricmc.fabric.api.resource.IdentifiableResourceReloadListener;
import net.minecraft.entity.effect.StatusEffect;
import net.minecraft.recipe.Ingredient;
import net.minecraft.registry.Registries;
import net.minecraft.resource.JsonDataLoader;
import net.minecraft.resource.ResourceManager;
import net.minecraft.util.Identifier;
import net.minecraft.util.JsonHelper;
import net.minecraft.util.profiler.Profiler;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Reads {@code data/cinderflask/brewing/*.json} into {@link IngredientTable}.
 *
 * <p>An entry describes either an item — anything the vanilla {@code Ingredient} codec accepts, so
 * tags work — or a status effect, which is how vanilla potions get their humours.
 */
public class BrewingRecipes extends JsonDataLoader implements IdentifiableResourceReloadListener {
    public static final String DIRECTORY = "brewing";

    private static final Gson GSON = new Gson();

    public BrewingRecipes() {
        super(GSON, DIRECTORY);
    }

    @Override
    public Identifier getFabricId() {
        return Cinderflask.id(DIRECTORY);
    }

    @Override
    protected void apply(Map<Identifier, JsonElement> prepared, ResourceManager manager, Profiler profiler) {
        List<IngredientTable.Parsed> entries = new ArrayList<>();

        for (Map.Entry<Identifier, JsonElement> file : prepared.entrySet()) {
            try {
                entries.add(parse(JsonHelper.asObject(file.getValue(), "brewing entry")));
            } catch (RuntimeException e) {
                Cinderflask.LOGGER.warn("Skipping brewing entry {}: {}", file.getKey(), e.getMessage());
            }
        }

        IngredientTable.replace(entries);
        Cinderflask.LOGGER.info("Loaded {} brewing entries.", entries.size());
    }

    private static IngredientTable.Parsed parse(JsonObject json) {
        Ingredient ingredient = json.has("ingredient")
                ? Ingredient.fromJson(json.get("ingredient"))
                : null;

        StatusEffect effect = null;
        if (json.has("effect")) {
            Identifier id = new Identifier(JsonHelper.getString(json, "effect"));
            effect = Registries.STATUS_EFFECT.getOrEmpty(id)
                    .orElseThrow(() -> new IllegalArgumentException("unknown effect " + id));
        }

        if (ingredient == null && effect == null) {
            throw new IllegalArgumentException("needs either an ingredient or an effect");
        }

        return new IngredientTable.Parsed(ingredient, effect, new IngredientTable.Entry(
                humours(JsonHelper.getObject(json, "humours", new JsonObject())),
                JsonHelper.getFloat(json, "body", 0),
                JsonHelper.getFloat(json, "corruption", 0),
                JsonHelper.getBoolean(json, "base", false)));
    }

    private static Humours humours(JsonObject json) {
        return new Humours(
                JsonHelper.getFloat(json, "choleric", 0),
                JsonHelper.getFloat(json, "melancholic", 0),
                JsonHelper.getFloat(json, "sanguine", 0),
                JsonHelper.getFloat(json, "phlegmatic", 0),
                JsonHelper.getFloat(json, "quintessence", 0));
    }
}
