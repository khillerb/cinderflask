package dev.cinderflask.client.compat.emi;

import dev.cinderflask.recipe.VesselOperation;
import dev.emi.emi.api.recipe.BasicEmiRecipe;
import dev.emi.emi.api.render.EmiTexture;
import dev.emi.emi.api.stack.EmiIngredient;
import dev.emi.emi.api.stack.EmiStack;
import dev.emi.emi.api.widget.WidgetHolder;
import net.minecraft.recipe.Ingredient;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.Identifier;

import java.util.ArrayList;
import java.util.List;

/**
 * One bench operation on a flask.
 *
 * <p>Read entirely off the recipe object, so this class never learns which operations exist. Add a
 * fifth vessel tier and its page appears without anything here changing.
 */
public class VesselEmiRecipe extends BasicEmiRecipe {
    private final String descriptionKey;

    public VesselEmiRecipe(Identifier id, VesselOperation operation) {
        super(CinderflaskEmiPlugin.VESSEL, id, 132, 48);

        List<EmiIngredient> declared = new ArrayList<>();
        for (Ingredient ingredient : operation.inputs()) {
            declared.add(EmiIngredient.of(ingredient));
        }

        this.inputs = declared;
        this.outputs = List.of(EmiStack.of(operation.preview()));
        this.descriptionKey = operation.descriptionKey();
    }

    @Override
    public void addWidgets(WidgetHolder widgets) {
        for (int i = 0; i < inputs.size(); i++) {
            widgets.addSlot(inputs.get(i), i * 18, 4);
        }

        int afterInputs = inputs.size() * 18;
        widgets.addTexture(EmiTexture.EMPTY_ARROW, afterInputs + 4, 5);
        widgets.addSlot(outputs.get(0), afterInputs + 32, 4).recipeContext(this);

        widgets.addText(Text.translatable(descriptionKey).formatted(Formatting.GRAY),
                0, 28, 0xFFAAAAAA, false);

        // The thing that makes these special recipes in the first place, and the thing a static
        // output slot cannot show.
        widgets.addText(Text.translatable("cinderflask.vessel.carries").formatted(Formatting.DARK_GRAY),
                0, 38, 0xFF888888, false);
    }
}
