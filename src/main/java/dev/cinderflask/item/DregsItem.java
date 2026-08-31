package dev.cinderflask.item;

import dev.cinderflask.brew.Dregs;
import dev.cinderflask.brew.Humours;
import net.minecraft.client.item.TooltipContext;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.world.World;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/** What settles in a flask you drank dry. Opening a brew on it starts partway along. */
public class DregsItem extends Item {
    public DregsItem(Settings settings) {
        super(settings);
    }

    @Override
    public void appendTooltip(ItemStack stack, @Nullable World world, List<Text> tooltip, TooltipContext context) {
        tooltip.add(Text.translatable("cinderflask.tooltip.dregs").formatted(Formatting.DARK_GRAY));

        Humours carried = Dregs.humours(stack);
        if (!carried.isEmpty()) {
            tooltip.add(Text.translatable("cinderflask.tooltip.dregs_age",
                    String.format("%.1f", Dregs.phase(stack))).formatted(Formatting.DARK_GRAY));
        }
    }
}
