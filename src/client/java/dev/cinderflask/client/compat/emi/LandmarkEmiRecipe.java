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

    /** Six route slots, an arrow and an output slot come to 158 before any margin. */
    private static final int WIDTH = 190;

    private static final int ROUTE_TOP = 24;
    private static final int WORDS_TOP = 48;

    private final Landmarks.Landmark landmark;
    private final Text words;

    public LandmarkEmiRecipe(Landmarks.Landmark landmark) {
        super(CinderflaskEmiPlugin.LANDMARKS,
                CinderflaskEmiPlugin.synthetic("landmark/" + landmark.id().getPath()), WIDTH, 0);
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

        // The same words the flask's own tooltip uses, rather than four bare numbers.
        this.words = Readout.inWords(landmark.target()).formatted(Formatting.GRAY);
        this.height = WORDS_TOP + Pages.height(words, WIDTH) + 4;
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
            widgets.addSlot(inputs.get(i), i * 18, ROUTE_TOP);
        }

        int afterRoute = inputs.size() * 18;
        widgets.addTexture(EmiTexture.EMPTY_ARROW, afterRoute + 4, ROUTE_TOP + 1);
        widgets.addSlot(outputs.get(0), afterRoute + 32, ROUTE_TOP).recipeContext(this);

        Pages.paragraph(widgets, words, 0, WORDS_TOP, WIDTH, 0xFFAAAAAA);
    }
}
