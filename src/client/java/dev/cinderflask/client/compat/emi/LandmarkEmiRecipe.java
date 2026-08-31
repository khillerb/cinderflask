package dev.cinderflask.client.compat.emi;

import dev.cinderflask.brew.Landmarks;
import dev.cinderflask.brew.Readout;
import dev.cinderflask.effect.DraughtEffect;
import dev.cinderflask.effect.Draughts;
import dev.emi.emi.api.recipe.BasicEmiRecipe;
import dev.emi.emi.api.render.EmiTexture;
import dev.emi.emi.api.stack.EmiIngredient;
import dev.emi.emi.api.stack.EmiStack;
import dev.emi.emi.api.widget.WidgetHolder;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * A known coordinate: what it gives you, and one way to reach it.
 *
 * <p>The route is solved from whatever the brewing table currently holds rather than written down, so
 * a datapack that retunes an ingredient retunes this page with it. It aims to fill a plain flask
 * rather than merely to point the right way, because a route that says "one kelp" is a direction and
 * not a drink. It is one route, and rarely the only one.
 */
public class LandmarkEmiRecipe extends BasicEmiRecipe {
    private static final int ROUTE_LIMIT = 6;

    private final Landmarks.Landmark landmark;

    public LandmarkEmiRecipe(Landmarks.Landmark landmark) {
        super(CinderflaskEmiPlugin.LANDMARKS,
                CinderflaskEmiPlugin.synthetic("landmark/" + landmark.id().getPath()), 152, 60);
        this.landmark = landmark;

        // Grouped, so three blaze powder reads as one stack of three rather than three slots.
        Map<Item, Integer> tally = new LinkedHashMap<>();
        for (Item item : Landmarks.route(landmark, ROUTE_LIMIT)) {
            tally.merge(item, 1, Integer::sum);
        }

        List<EmiIngredient> route = new ArrayList<>();
        for (Map.Entry<Item, Integer> step : tally.entrySet()) {
            route.add(EmiStack.of(new ItemStack(step.getKey(), step.getValue())));
        }

        this.inputs = route;
        this.outputs = List.of(CinderflaskEmiPlugin.FLASK);
    }

    @Override
    public void addWidgets(WidgetHolder widgets) {
        widgets.addText(Text.translatable(landmark.translationKey()).formatted(Formatting.GOLD),
                0, 0, 0xFFFFFFFF, false);

        DraughtEffect draught = Draughts.of(landmark);
        if (draught != null) {
            widgets.addText(Text.translatable("cinderflask.emi.gives",
                            draught.getName(), Text.translatable("cinderflask.role." + landmark.role()))
                    .formatted(Formatting.AQUA), 0, 11, 0xFFAAAAAA, false);
        }

        for (int i = 0; i < inputs.size(); i++) {
            widgets.addSlot(inputs.get(i), i * 18, 24);
        }

        int afterRoute = inputs.size() * 18;
        widgets.addTexture(EmiTexture.EMPTY_ARROW, afterRoute + 4, 25);
        widgets.addSlot(outputs.get(0), afterRoute + 32, 24).recipeContext(this);

        // The same words the flask's own tooltip uses, rather than four bare numbers.
        widgets.addText(Readout.inWords(landmark.target()).formatted(Formatting.GRAY),
                0, 48, 0xFFAAAAAA, false);
    }
}
