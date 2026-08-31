package dev.cinderflask.client.compat.emi;

import dev.cinderflask.brew.Humours;
import dev.cinderflask.brew.IngredientTable;
import dev.emi.emi.api.recipe.BasicEmiRecipe;
import dev.emi.emi.api.stack.EmiStack;
import dev.emi.emi.api.widget.WidgetHolder;
import net.minecraft.item.ItemStack;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.Identifier;

import java.util.ArrayList;
import java.util.List;

/** One ingredient, and what it writes into a brew. */
public class IngredientEmiRecipe extends BasicEmiRecipe {
    private static final int MAX_LINES = 4;

    private static final String[] HUMOUR_KEYS = {
            "cinderflask.humour.choleric",
            "cinderflask.humour.melancholic",
            "cinderflask.humour.sanguine",
            "cinderflask.humour.phlegmatic",
    };

    private final IngredientTable.Entry entry;

    public IngredientEmiRecipe(Identifier id, ItemStack stack, IngredientTable.Entry entry) {
        super(CinderflaskEmiPlugin.BREWING, id, 130, 44);
        this.entry = entry;
        this.inputs = List.of(EmiStack.of(stack));
        this.outputs = List.of();
    }

    @Override
    public void addWidgets(WidgetHolder widgets) {
        widgets.addSlot(inputs.get(0), 0, 4);

        List<Text> lines = describe();
        for (int line = 0; line < lines.size(); line++) {
            widgets.addText(lines.get(line), 24, 2 + line * 10, 0xFFAAAAAA, false);
        }
    }

    private List<Text> describe() {
        List<Text> lines = new ArrayList<>(MAX_LINES);
        Humours humours = entry.humours();

        if (entry.base()) {
            lines.add(Text.translatable("cinderflask.emi.base").formatted(Formatting.GOLD));
        }

        for (int i = 0; i < Humours.WHEEL; i++) {
            if (humours.wheel(i) > 0) {
                lines.add(Text.translatable("cinderflask.emi.writes",
                        String.format("%.0f", humours.wheel(i)),
                        Text.translatable(HUMOUR_KEYS[i])));
            }
        }

        if (humours.quintessence() > 0) {
            lines.add(Text.translatable("cinderflask.emi.reach",
                    String.format("%.0f", humours.quintessence())));
        }

        if (entry.body() > 0) {
            lines.add(Text.translatable("cinderflask.emi.body", String.format("%.0f", entry.body())));
        }

        if (entry.corruption() > 0) {
            lines.add(Text.translatable("cinderflask.emi.corruption",
                    String.format("%.2f", entry.corruption())).formatted(Formatting.DARK_PURPLE));
        }

        return lines.subList(0, Math.min(MAX_LINES, lines.size()));
    }
}
