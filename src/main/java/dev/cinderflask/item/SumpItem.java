package dev.cinderflask.item;

import net.minecraft.client.item.TooltipContext;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.world.World;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * What a brew becomes when it is left too long.
 *
 * <p>Not rubbish. Sump is the cheap route to the corrupt half of the effect space — the expensive one
 * is rare reagents — and it is deliberately unaimable: it raises corruption and scatters the vector,
 * so a brew built on it lands somewhere you would never have chosen.
 */
public class SumpItem extends Item {
    public SumpItem(Settings settings) {
        super(settings);
    }

    @Override
    public void appendTooltip(ItemStack stack, @Nullable World world, List<Text> tooltip, TooltipContext context) {
        tooltip.add(Text.translatable("cinderflask.tooltip.sump").formatted(Formatting.DARK_GRAY));
    }
}
