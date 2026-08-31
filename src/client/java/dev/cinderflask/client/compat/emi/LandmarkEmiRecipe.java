package dev.cinderflask.client.compat.emi;

import dev.cinderflask.brew.Humours;
import dev.cinderflask.brew.Landmarks;
import dev.emi.emi.api.recipe.BasicEmiRecipe;
import dev.emi.emi.api.stack.EmiIngredient;
import dev.emi.emi.api.stack.EmiStack;
import dev.emi.emi.api.widget.WidgetHolder;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

import java.util.ArrayList;
import java.util.List;

/**
 * A known coordinate, and one way to reach it.
 *
 * <p>The route is solved from whatever the brewing table currently holds rather than written down, so
 * a datapack that retunes an ingredient retunes this page with it. It is one route, and rarely the
 * only one.
 */
public class LandmarkEmiRecipe extends BasicEmiRecipe {
    private static final int ROUTE_LIMIT = 5;

    private final Landmarks.Landmark landmark;

    public LandmarkEmiRecipe(Landmarks.Landmark landmark) {
        super(CinderflaskEmiPlugin.LANDMARKS, landmark.id(), 132, 50);
        this.landmark = landmark;

        List<EmiIngredient> route = new ArrayList<>();
        for (Item item : Landmarks.route(landmark, ROUTE_LIMIT)) {
            route.add(EmiStack.of(new ItemStack(item)));
        }

        this.inputs = route;
        this.outputs = List.of(CinderflaskEmiPlugin.FLASK);
    }

    @Override
    public void addWidgets(WidgetHolder widgets) {
        widgets.addText(Text.translatable(landmark.translationKey()).formatted(Formatting.GOLD),
                0, 0, 0xFFFFFFFF, false);
        widgets.addText(Text.translatable("cinderflask.role." + landmark.role())
                        .formatted(Formatting.DARK_GRAY),
                0, 10, 0xFFAAAAAA, false);

        for (int i = 0; i < inputs.size(); i++) {
            widgets.addSlot(inputs.get(i), i * 18, 22);
        }

        widgets.addText(coordinate(), 0, 42, 0xFFAAAAAA, false);
    }

    private Text coordinate() {
        Humours target = landmark.target();
        String reach = target.quintessence() > 0
                ? String.format("  +%.0f reach", target.quintessence())
                : "";

        return Text.literal(String.format("%.0f / %.0f / %.0f / %.0f%s",
                        target.choleric(), target.melancholic(),
                        target.sanguine(), target.phlegmatic(), reach))
                .formatted(Formatting.GRAY);
    }
}
