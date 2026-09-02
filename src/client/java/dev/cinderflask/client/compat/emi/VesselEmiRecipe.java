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
    private static final int WIDTH = 176;
    private static final int TEXT_TOP = 28;

    private final Text description;
    private final Text carries;

    public VesselEmiRecipe(Identifier id, VesselOperation operation) {
        super(CinderflaskEmiPlugin.VESSEL, id, WIDTH, 0);

        List<EmiIngredient> declared = new ArrayList<>();
        for (Ingredient ingredient : operation.inputs()) {
            declared.add(EmiIngredient.of(ingredient));
        }

        this.inputs = declared;
        this.outputs = List.of(EmiStack.of(operation.preview()));

        this.description = Text.translatable(operation.descriptionKey()).formatted(Formatting.GRAY);
        this.carries = Text.translatable("cinderflask.vessel.carries").formatted(Formatting.DARK_GRAY);

        // Sized from the text rather than guessed at. The old fixed 48 was too short for a sentence
        // and the old fixed width sent it 234 pixels off the side of the page.
        this.height = TEXT_TOP + Pages.height(description, WIDTH) + Pages.height(carries, WIDTH) + 4;
    }

    @Override
    public void addWidgets(WidgetHolder widgets) {
        for (int i = 0; i < inputs.size(); i++) {
            widgets.addSlot(inputs.get(i), i * 18, 4);
        }

        int afterInputs = inputs.size() * 18;
        widgets.addTexture(EmiTexture.EMPTY_ARROW, afterInputs + 4, 5);
        widgets.addSlot(outputs.get(0), afterInputs + 32, 4).recipeContext(this);

        int y = Pages.paragraph(widgets, description, 0, TEXT_TOP, WIDTH, 0xFFAAAAAA);

        // The thing that makes these special recipes in the first place, and the thing a static
        // output slot cannot show.
        Pages.paragraph(widgets, carries, 0, y, WIDTH, 0xFF888888);
    }
}
