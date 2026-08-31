package dev.cinderflask.brew;

import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtElement;
import net.minecraft.nbt.NbtFloat;
import net.minecraft.nbt.NbtList;
import net.minecraft.world.World;
import org.jetbrains.annotations.Nullable;

/**
 * The bridge between the pure brew maths and an item stack.
 *
 * <p>Phase is never stored. The flask records the world time it was corked, and the current phase is
 * worked out on read from how long ago that was and how fast the vessel turns — so a brew ages in a
 * chest, in a hopper, and on a server nobody is logged into, without anything having to tick.
 *
 * <p>An uncorked brew has no seal time and therefore no age at all. That is the whole point of the
 * working state: you can take as long as you like composing one.
 */
public final class BrewNbt {
    /** Half an in-game day. A full turn of the wheel is two days, about forty minutes of play. */
    public static final long TICKS_PER_PHASE = 12_000L;

    private static final String ROOT = "Brew";
    private static final String HUMOURS = "H";
    private static final String SEALED_AT = "Sealed";
    private static final String CORKED = "Corked";
    private static final String CARRIED_PHASE = "Carried";
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
        // No seal time means it has not been corked yet, so it is not ageing.
        if (world == null || !root.contains(SEALED_AT, NbtElement.LONG_TYPE)) {
            return 0;
        }

        long elapsed = Math.max(0, world.getTime() - root.getLong(SEALED_AT));
        return (float) elapsed / TICKS_PER_PHASE * temper(stack).rate();
    }

    /** Writes a brew without touching whether or when it was corked. */
    public static void store(ItemStack stack, Brew brew, int doses) {
        NbtCompound nbt = stack.getOrCreateNbt();
        NbtCompound root = nbt.contains(ROOT, NbtElement.COMPOUND_TYPE)
                ? nbt.getCompound(ROOT)
                : new NbtCompound();

        NbtList values = new NbtList();
        values.add(NbtFloat.of(brew.sealed().choleric()));
        values.add(NbtFloat.of(brew.sealed().melancholic()));
        values.add(NbtFloat.of(brew.sealed().sanguine()));
        values.add(NbtFloat.of(brew.sealed().phlegmatic()));
        values.add(NbtFloat.of(brew.sealed().quintessence()));

        root.put(HUMOURS, values);
        root.putFloat(CORRUPTION, brew.addedCorruption());
        root.putFloat(CAPACITY, brew.capacity());

        nbt.put(ROOT, root);
        setDoses(stack, doses);
    }

    /**
     * Writes a brew that already has an age, by backdating its seal time. Used by the tests and, from
     * Phase 4, by solera top-ups.
     */
    public static void seal(ItemStack stack, Brew brew, World world, int doses) {
        store(stack, brew, doses);
        cork(stack);

        stack.getOrCreateNbt().getCompound(ROOT)
                .putLong(SEALED_AT, world.getTime() - (long) (brew.phase() * TICKS_PER_PHASE));
    }

    /**
     * Hands the flask an age to start from once its clock does start.
     *
     * <p>A working brew has no clock, and a crafting bench has no world — but dregs and solera both
     * have to carry age forward across exactly those two gaps. The phase is parked here and cashed in
     * by {@link #stampIfNeeded}, which is the moment the clock actually begins.
     */
    public static void setCarriedPhase(ItemStack stack, float phase) {
        NbtCompound nbt = stack.getNbt();
        if (nbt == null || !nbt.contains(ROOT, NbtElement.COMPOUND_TYPE)) {
            return;
        }

        NbtCompound root = nbt.getCompound(ROOT);
        root.putFloat(CARRIED_PHASE, Math.max(0, phase));
        root.remove(SEALED_AT);
    }

    public static boolean isCorked(ItemStack stack) {
        NbtCompound nbt = stack.getNbt();
        return nbt != null && nbt.contains(ROOT, NbtElement.COMPOUND_TYPE)
                && nbt.getCompound(ROOT).getBoolean(CORKED);
    }

    /** Closes the flask. The clock does not start until {@link #stampIfNeeded} sees it in a world. */
    public static void cork(ItemStack stack) {
        NbtCompound nbt = stack.getNbt();
        if (nbt != null && nbt.contains(ROOT, NbtElement.COMPOUND_TYPE)) {
            nbt.getCompound(ROOT).putBoolean(CORKED, true);
        }
    }

    /**
     * Starts the clock on a flask that has been corked but never seen a world.
     *
     * <p>Corking happens on a crafting bench, and a crafting recipe has no world to read a time from,
     * so the stamp is deferred to the first inventory tick. It happens once and never again.
     *
     * @return true if this call was the one that started it
     */
    public static boolean stampIfNeeded(ItemStack stack, World world) {
        NbtCompound nbt = stack.getNbt();
        if (nbt == null || !nbt.contains(ROOT, NbtElement.COMPOUND_TYPE)) {
            return false;
        }

        NbtCompound root = nbt.getCompound(ROOT);
        if (!root.getBoolean(CORKED) || root.contains(SEALED_AT, NbtElement.LONG_TYPE)) {
            return false;
        }

        // Backdated by whatever age the brew arrived with, so dregs and solera get their head start
        // at the moment the clock starts rather than losing it in the gap.
        float carried = root.getFloat(CARRIED_PHASE);
        root.putLong(SEALED_AT, world.getTime() - (long) (carried * TICKS_PER_PHASE));
        root.remove(CARRIED_PHASE);
        return true;
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
