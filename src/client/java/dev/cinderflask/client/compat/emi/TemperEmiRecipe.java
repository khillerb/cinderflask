package dev.cinderflask.client.compat.emi;

import dev.cinderflask.Cinderflask;
import dev.cinderflask.brew.Temper;
import dev.emi.emi.api.recipe.BasicEmiRecipe;
import dev.emi.emi.api.render.EmiTexture;
import dev.emi.emi.api.stack.EmiIngredient;
import dev.emi.emi.api.stack.EmiStack;
import dev.emi.emi.api.widget.WidgetHolder;
import net.minecraft.block.Block;
import net.minecraft.item.ItemStack;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.Identifier;

import java.util.List;

/** Firing an untempered flask against a source, and what that leaves it as. */
public class TemperEmiRecipe extends BasicEmiRecipe {
    private final Temper temper;

    public TemperEmiRecipe(Identifier id, Temper temper, List<Block> sources) {
        super(CinderflaskEmiPlugin.TEMPERING, id, 130, 46);
        this.temper = temper;

        // Every block that leaves this temper, offered as one cycling slot.
        EmiIngredient anySource = EmiIngredient.of(sources.stream()
                .map(block -> (EmiIngredient) EmiStack.of(new ItemStack(block)))
                .toList());

        this.inputs = List.of(CinderflaskEmiPlugin.FLASK, anySource);
        this.outputs = List.of(EmiStack.of(Cinderflask.CINDERFLASK));
    }

    @Override
    public void addWidgets(WidgetHolder widgets) {
        widgets.addSlot(inputs.get(0), 0, 4);
        widgets.addTexture(EmiTexture.PLUS, 22, 7);
        widgets.addSlot(inputs.get(1), 39, 4);
        widgets.addTexture(EmiTexture.EMPTY_ARROW, 61, 5);
        widgets.addSlot(outputs.get(0), 90, 4).recipeContext(this);

        widgets.addText(Text.translatable(temper.translationKey()).formatted(Formatting.AQUA),
                0, 26, 0xFFAAAAAA, false);

        // The clock rate is the thing people will actually come to this page for.
        widgets.addText(Text.translatable("cinderflask.emi.clock", String.format("%.2f", temper.rate()))
                        .formatted(Formatting.GRAY),
                0, 36, 0xFFAAAAAA, false);
    }
}
