package dev.cinderflask.item;

import dev.cinderflask.brew.Cracking;
import net.minecraft.client.item.TooltipContext;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtElement;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.world.World;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * A cracked flask packed in sand, waiting for the fire.
 *
 * <p>The whole flask travels inside this — mote, seasoning, temper, name — so smelting it hands back
 * the same vessel rather than a replacement. Repairing an heirloom should not cost you the heirloom.
 */
public class SinterItem extends Item {
    private static final String FLASK = "Flask";

    public SinterItem(Settings settings) {
        super(settings);
    }

    /** Packs a flask away, mended, ready to come back out of a furnace. */
    public static ItemStack pack(ItemStack flask) {
        ItemStack mended = flask.copy();
        Cracking.mend(mended);

        ItemStack sinter = new ItemStack(dev.cinderflask.Cinderflask.SINTER);
        sinter.getOrCreateNbt().put(FLASK, mended.writeNbt(new NbtCompound()));
        return sinter;
    }

    /** The flask that was packed in, or an empty stack if this one is somehow blank. */
    public static ItemStack unpack(ItemStack sinter) {
        NbtCompound nbt = sinter.getNbt();
        if (nbt == null || !nbt.contains(FLASK, NbtElement.COMPOUND_TYPE)) {
            return ItemStack.EMPTY;
        }
        return ItemStack.fromNbt(nbt.getCompound(FLASK));
    }

    @Override
    public void appendTooltip(ItemStack stack, @Nullable World world, List<Text> tooltip, TooltipContext context) {
        ItemStack packed = unpack(stack);

        tooltip.add(Text.translatable("cinderflask.tooltip.sinter").formatted(Formatting.DARK_GRAY));

        if (!packed.isEmpty()) {
            tooltip.add(Text.translatable("cinderflask.tooltip.sinter_holds",
                    packed.getName()).formatted(Formatting.DARK_GRAY));
        }
    }
}
