package dev.cinderflask.brew;

import dev.cinderflask.Cinderflask;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtElement;
import net.minecraft.nbt.NbtFloat;
import net.minecraft.nbt.NbtList;

/**
 * What settles at the bottom of a flask you drank dry.
 *
 * <p>Opening a new brew on dregs starts it part-aged and part-flavoured — the cheapest way to reach a
 * deep phase without waiting for one, and the reason emptying a flask is not quite the same as
 * throwing its contents away.
 */
public final class Dregs {
    /** How much of the old brew survives into the dregs. */
    private static final float CARRIED = 0.35f;

    /** And how much of its age. Less than the vector, so dregs are a head start, not a shortcut. */
    private static final float CARRIED_AGE = 0.5f;

    private static final String HUMOURS = "Dregs";
    private static final String PHASE = "DregsPhase";

    private Dregs() {
    }

    /** The dregs a brew leaves behind, or an empty stack if there was nothing worth keeping. */
    public static ItemStack from(Brew brew) {
        Humours left = brew.current();
        if (left.magnitude() <= 0) {
            return ItemStack.EMPTY;
        }

        ItemStack dregs = new ItemStack(Cinderflask.DREGS);
        NbtCompound nbt = dregs.getOrCreateNbt();

        NbtList values = new NbtList();
        values.add(NbtFloat.of(left.choleric() * CARRIED));
        values.add(NbtFloat.of(left.melancholic() * CARRIED));
        values.add(NbtFloat.of(left.sanguine() * CARRIED));
        values.add(NbtFloat.of(left.phlegmatic() * CARRIED));
        values.add(NbtFloat.of(left.quintessence() * CARRIED));

        nbt.put(HUMOURS, values);
        nbt.putFloat(PHASE, brew.phase() * CARRIED_AGE);

        return dregs;
    }

    public static Humours humours(ItemStack dregs) {
        NbtCompound nbt = dregs.getNbt();
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

    public static float phase(ItemStack dregs) {
        NbtCompound nbt = dregs.getNbt();
        return nbt == null ? 0 : Math.max(0, nbt.getFloat(PHASE));
    }
}
