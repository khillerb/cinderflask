package dev.cinderflask.brew;

import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtElement;
import net.minecraft.nbt.NbtList;
import net.minecraft.nbt.NbtFloat;
import net.minecraft.world.World;
import org.jetbrains.annotations.Nullable;

/**
 * The bridge between the pure brew maths and an item stack.
 *
 * <p>Phase is never stored. The flask records the world time it was sealed, and the current phase is
 * worked out on read from how long ago that was and how fast the vessel turns — so a brew ages in a
 * chest, in a hopper, and on a server nobody is logged into, without anything having to tick.
 */
public final class BrewNbt {
    /** Half an in-game day. A full turn of the wheel is two days, about forty minutes of play. */
    public static final long TICKS_PER_PHASE = 12_000L;

    private static final String ROOT = "Brew";
    private static final String HUMOURS = "H";
    private static final String SEALED_AT = "Sealed";
    private static final String CORRUPTION = "Corruption";
    private static final String CAPACITY = "Capacity";
    private static final String DOSES = "Doses";
    private static final String TEMPER = "Temper";

    private BrewNbt() {
    }

    public static boolean hasBrew(ItemStack stack) {
        NbtCompound nbt = stack.getNbt();
        return nbt != null && nbt.contains(ROOT, NbtElement.COMPOUND_TYPE);
    }

    /** The brew as it stands right now, or null if the flask is empty. */
    @Nullable
    public static Brew read(ItemStack stack, @Nullable World world) {
        NbtCompound nbt = stack.getNbt();
        if (nbt == null || !nbt.contains(ROOT, NbtElement.COMPOUND_TYPE)) {
            return null;
        }

        NbtCompound root = nbt.getCompound(ROOT);
        NbtList values = root.getList(HUMOURS, NbtElement.FLOAT_TYPE);
        if (values.size() < 5) {
            return null;
        }

        Humours humours = new Humours(
                values.getFloat(0), values.getFloat(1), values.getFloat(2),
                values.getFloat(3), values.getFloat(4));

        return new Brew(humours, phaseOf(stack, root, world),
                root.getFloat(CORRUPTION), root.getFloat(CAPACITY));
    }

    private static float phaseOf(ItemStack stack, NbtCompound root, @Nullable World world) {
        if (world == null) {
            return 0;
        }

        long elapsed = Math.max(0, world.getTime() - root.getLong(SEALED_AT));
        return (float) elapsed / TICKS_PER_PHASE * temper(stack).rate();
    }

    /** Seals a brew into the flask, starting its clock from now. */
    public static void seal(ItemStack stack, Brew brew, World world, int doses) {
        NbtCompound root = new NbtCompound();

        NbtList values = new NbtList();
        values.add(NbtFloat.of(brew.sealed().choleric()));
        values.add(NbtFloat.of(brew.sealed().melancholic()));
        values.add(NbtFloat.of(brew.sealed().sanguine()));
        values.add(NbtFloat.of(brew.sealed().phlegmatic()));
        values.add(NbtFloat.of(brew.sealed().quintessence()));

        root.put(HUMOURS, values);
        root.putLong(SEALED_AT, world.getTime() - (long) (brew.phase() * TICKS_PER_PHASE));
        root.putFloat(CORRUPTION, brew.addedCorruption());
        root.putFloat(CAPACITY, brew.capacity());

        stack.getOrCreateNbt().put(ROOT, root);
        setDoses(stack, doses);
    }

    public static void empty(ItemStack stack) {
        NbtCompound nbt = stack.getNbt();
        if (nbt != null) {
            nbt.remove(ROOT);
            nbt.remove(DOSES);
        }
    }

    public static int doses(ItemStack stack) {
        NbtCompound nbt = stack.getNbt();
        return nbt == null ? 0 : Math.max(0, nbt.getInt(DOSES));
    }

    public static void setDoses(ItemStack stack, int doses) {
        stack.getOrCreateNbt().putInt(DOSES, Math.max(0, doses));
    }

    public static Temper temper(ItemStack stack) {
        NbtCompound nbt = stack.getNbt();
        return nbt == null ? Temper.UNTEMPERED : Temper.byId(nbt.getString(TEMPER));
    }

    public static void setTemper(ItemStack stack, Temper temper) {
        stack.getOrCreateNbt().putString(TEMPER, temper.id());
    }
}
