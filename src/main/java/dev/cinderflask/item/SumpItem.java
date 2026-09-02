package dev.cinderflask.item;

import dev.cinderflask.brew.Humours;
import net.minecraft.client.item.TooltipContext;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtElement;
import net.minecraft.nbt.NbtFloat;
import net.minecraft.nbt.NbtList;
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
 *
 * <p>It remembers the brew it came from. Nothing reads that yet beyond its colour, but a sump that
 * knows what it used to be is what the corrupt half will be built on, and it is the difference
 * between an item that looks dead and one that looks like something died in it.
 */
public class SumpItem extends Item {
    private static final String HUMOURS = "Sump";

    /** How far towards the murk the remembered colour is dragged. Spoiled, but still recognisable. */
    private static final float SOURED = 0.35f;

    public SumpItem(Settings settings) {
        super(settings);
    }

    /** A stack of sump that remembers what it used to be. */
    public static ItemStack of(Humours left, int count) {
        ItemStack sump = new ItemStack(dev.cinderflask.Cinderflask.SUMP, Math.max(1, count));

        NbtList values = new NbtList();
        values.add(NbtFloat.of(left.choleric()));
        values.add(NbtFloat.of(left.melancholic()));
        values.add(NbtFloat.of(left.sanguine()));
        values.add(NbtFloat.of(left.phlegmatic()));
        values.add(NbtFloat.of(left.quintessence()));

        sump.getOrCreateNbt().put(HUMOURS, values);
        return sump;
    }

    public static Humours humours(ItemStack sump) {
        NbtCompound nbt = sump.getNbt();
        if (nbt == null || !nbt.contains(HUMOURS, NbtElement.LIST_TYPE)) {
            return Humours.EMPTY;
        }

        NbtList values = nbt.getList(HUMOURS, NbtElement.FLOAT_TYPE);
        if (values.size() < 5) {
            return Humours.EMPTY;
        }

        return new Humours(values.getFloat(0), values.getFloat(1), values.getFloat(2),
                values.getFloat(3), values.getFloat(4));
    }

    /** The colour of what it used to be, gone off. */
    public static int colour(ItemStack sump) {
        return Humours.soured(humours(sump).colour(), SOURED);
    }

    @Override
    public void appendTooltip(ItemStack stack, @Nullable World world, List<Text> tooltip, TooltipContext context) {
        tooltip.add(Text.translatable("cinderflask.tooltip.sump").formatted(Formatting.DARK_GRAY));
    }
}
