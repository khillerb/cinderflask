package dev.cinderflask.client.compat.emi;

import dev.emi.emi.api.recipe.BasicEmiRecipe;
import dev.emi.emi.api.render.EmiTexture;
import dev.emi.emi.api.stack.EmiIngredient;
import dev.emi.emi.api.stack.EmiStack;
import dev.emi.emi.api.widget.WidgetHolder;
import net.minecraft.entity.EntityType;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.Identifier;

import java.util.List;

/** Empty Cinderflask + one living spark source, right-clicked, becomes a Cinderflask. */
public class SparkingEmiRecipe extends BasicEmiRecipe {
    private static final int WIDTH = 108;
    private static final int HEIGHT = 26;

    private final EntityType<?> source;

    public SparkingEmiRecipe(Identifier id, EntityType<?> source, EmiStack spawnEgg) {
        super(CinderflaskEmiPlugin.SPARKING, id, WIDTH, HEIGHT);

        this.source = source;
        this.inputs = List.of(CinderflaskEmiPlugin.EMPTY_FLASK, spawnEgg);
        this.outputs = List.of(CinderflaskEmiPlugin.FLASK);
    }

    @Override
    public void addWidgets(WidgetHolder widgets) {
        EmiIngredient flask = inputs.get(0);
        EmiIngredient egg = inputs.get(1);

        widgets.addSlot(flask, 0, 4);
        widgets.addTexture(EmiTexture.PLUS, 22, 7);
        widgets.addSlot(egg, 39, 4);

        // The egg is a stand-in for the mob, so spell out the actual interaction.
        widgets.addTooltipText(
                List.of(Text.translatable("cinderflask.emi.sparking.hint", source.getName())
                        .formatted(Formatting.GRAY)),
                39, 4, 18, 18);

        widgets.addTexture(EmiTexture.EMPTY_ARROW, 61, 5);
        widgets.addSlot(outputs.get(0), 90, 4).recipeContext(this);
    }
}
