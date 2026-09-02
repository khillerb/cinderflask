package dev.cinderflask.item;

import net.minecraft.client.item.TooltipContext;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.Hand;
import net.minecraft.util.TypedActionResult;
import net.minecraft.world.World;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * The book. Opens a map of everything the glass can do.
 *
 * <p>Which parts of that map you can read is decided by what you have drunk, the same way the
 * flask's own tooltip is. Nothing on it is a capability — a page you cannot read yet is an
 * explanation you have not earned, never a thing you are forbidden to do.
 */
public class AlmanacItem extends Item {
    /**
     * Set by the client entrypoint. A screen cannot be named from the common source set, which is
     * compiled into the dedicated-server path, so the item asks for one through here.
     */
    public static Runnable opener = () -> {
    };

    public AlmanacItem(Settings settings) {
        super(settings);
    }

    @Override
    public TypedActionResult<ItemStack> use(World world, PlayerEntity user, Hand hand) {
        ItemStack held = user.getStackInHand(hand);

        if (world.isClient) {
            opener.run();
        }

        return TypedActionResult.success(held, world.isClient);
    }

    @Override
    public void appendTooltip(ItemStack stack, @Nullable World world, List<Text> tooltip,
                              TooltipContext context) {
        tooltip.add(Text.translatable("cinderflask.tooltip.almanac").formatted(Formatting.DARK_GRAY));
    }
}
