package dev.cinderflask.client.compat.emi;

import dev.cinderflask.brew.Humours;
import dev.cinderflask.brew.IngredientTable;
import dev.cinderflask.brew.Landmarks;
import dev.emi.emi.api.recipe.BasicEmiRecipe;
import dev.emi.emi.api.render.EmiTexture;
import dev.emi.emi.api.stack.EmiStack;
import dev.emi.emi.api.widget.WidgetHolder;
import net.minecraft.item.ItemStack;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.Identifier;

import java.util.ArrayList;
import java.util.List;

/** One ingredient, what it writes into a brew, and what it is for. */
public class IngredientEmiRecipe extends BasicEmiRecipe {
    private static final String[] HUMOUR_KEYS = {
            "cinderflask.humour.choleric",
            "cinderflask.humour.melancholic",
            "cinderflask.humour.sanguine",
            "cinderflask.humour.phlegmatic",
    };

    private final IngredientTable.Entry entry;
    private final List<Text> lines;

    public IngredientEmiRecipe(Identifier id, ItemStack stack, IngredientTable.Entry entry) {
        super(CinderflaskEmiPlugin.BREWING, id, 140, 0);
        this.entry = entry;
        this.lines = describe();

        // Tall enough for everything it has to say. The old page cut itself off at four lines, which
        // silently hid the corruption on exactly the ingredients where it mattered most.
        this.height = Math.max(28, 14 + lines.size() * 10);

        this.inputs = List.of(EmiStack.of(stack));
        this.outputs = List.of(CinderflaskEmiPlugin.FLASK);
    }

    @Override
    public void addWidgets(WidgetHolder widgets) {
        widgets.addSlot(inputs.get(0), 0, 4);
        widgets.addTexture(EmiTexture.EMPTY_ARROW, 22, 5);
        widgets.addSlot(outputs.get(0), 50, 4).recipeContext(this);

        for (int line = 0; line < lines.size(); line++) {
            widgets.addText(lines.get(line), 72, 2 + line * 10, 0xFFAAAAAA, false);
        }
    }

    private List<Text> describe() {
        List<Text> out = new ArrayList<>();
        Humours humours = entry.humours();

        if (entry.base()) {
            out.add(Text.translatable("cinderflask.emi.base").formatted(Formatting.GOLD));
        }

        for (int i = 0; i < Humours.WHEEL; i++) {
            if (humours.wheel(i) > 0) {
                out.add(Text.translatable("cinderflask.emi.writes",
                        String.format("%.0f", humours.wheel(i)),
                        Text.translatable(HUMOUR_KEYS[i])));
            }
        }

        if (humours.quintessence() > 0) {
            out.add(Text.translatable("cinderflask.emi.reach",
                    String.format("%.0f", humours.quintessence())));
        }

        if (entry.body() > 0) {
            out.add(Text.translatable("cinderflask.emi.body", String.format("%.0f", entry.body())));
        }

        if (entry.corruption() > 0) {
            out.add(Text.translatable("cinderflask.emi.corruption",
                    String.format("%.2f", entry.corruption())).formatted(Formatting.DARK_PURPLE));
        }

        // The answer to "what do I actually use this for". Only the ingredients that genuinely point
        // at a known brew get this line, which is what makes it worth reading when it appears.
        Landmarks.Landmark aims = Landmarks.nearest(humours);
        if (aims != null) {
            out.add(Text.translatable("cinderflask.emi.aims",
                    Text.translatable(aims.translationKey())).formatted(Formatting.AQUA));
        }

        return out;
    }
}
