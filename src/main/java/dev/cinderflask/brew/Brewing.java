package dev.cinderflask.brew;

import net.minecraft.item.ItemStack;
import net.minecraft.world.World;
import org.jetbrains.annotations.Nullable;

/**
 * Composing a brew: the base, then the ingredients, then the cork.
 *
 * <p>Nothing here ages. A working brew has no clock at all, which is what lets you take as long as
 * you like over it — the clock starts when the flask is corked.
 */
public final class Brewing {
    /** A flask with no body ingredient in it still holds a little. */
    public static final float MINIMUM_CAPACITY = 1;

    private Brewing() {
    }

    /**
     * Puts a base in an empty flask. Whatever the vessel was fired against leans it from the start,
     * and so does everything the flask has held before.
     *
     * @return false if this flask already holds something
     */
    public static boolean addBase(ItemStack flask, IngredientTable.Entry base, float ceiling) {
        if (BrewNbt.hasBrew(flask)) {
            return false;
        }

        Temper temper = BrewNbt.temper(flask);
        Humours humours = base.humours().plus(temper.bias()).plus(Vessel.drift(flask))
                .plus(innateReach(flask));
        float capacity = Math.min(ceiling, MINIMUM_CAPACITY + base.body());

        Brew brewed = new Brew(humours, 0, temper.corruption() + base.corruption(), capacity);
        BrewNbt.store(flask, brewed, brewed.doses());
        return true;
    }

    /**
     * Opens a brew on the dregs of an old one, which is what carries a fraction of its character and
     * its age forward. The clock does not start until the new brew is corked, but it starts partway
     * along — the only way to reach a deep phase without having waited for one.
     */
    public static boolean openWithDregs(ItemStack flask, ItemStack dregs, float ceiling) {
        if (BrewNbt.hasBrew(flask)) {
            return false;
        }

        Humours carried = Dregs.humours(dregs);
        if (carried.isEmpty()) {
            return false;
        }

        Temper temper = BrewNbt.temper(flask);
        Humours humours = carried.plus(temper.bias()).plus(Vessel.drift(flask))
                .plus(innateReach(flask));

        Brew brewed = new Brew(humours, Dregs.phase(dregs), temper.corruption(),
                Math.min(ceiling, MINIMUM_CAPACITY + 1));
        BrewNbt.store(flask, brewed, brewed.doses());
        BrewNbt.setCarriedPhase(flask, Dregs.phase(dregs));
        return true;
    }

    /**
     * Folds one ingredient into a working brew.
     *
     * @return false if the flask has no base yet, is already corked, or the brew spoiled
     */
    public static boolean add(ItemStack flask, IngredientTable.Entry entry, @Nullable World world, float ceiling) {
        Brew existing = BrewNbt.read(flask, world);
        if (existing == null || BrewNbt.isCorked(flask)) {
            return false;
        }

        Humours humours = existing.sealed().plus(entry.humours());
        float capacity = Math.min(ceiling, existing.capacity() + entry.body());
        float corruption = existing.addedCorruption() + entry.corruption();

        Brew brewed = new Brew(humours, 0, corruption, capacity);
        BrewNbt.store(flask, brewed, brewed.doses());

        return !brewed.isSpoiled();
    }

    /**
     * Corks a working brew, which is what starts its clock and what the flask remembers it by.
     *
     * <p>Seasoning is recorded here rather than when the first ingredient went in, so the flask learns
     * the composition you actually finished with.
     *
     * @return false if there was nothing to cork
     */
    public static boolean cork(ItemStack flask) {
        if (!BrewNbt.hasBrew(flask) || BrewNbt.isCorked(flask)) {
            return false;
        }

        Brew brew = BrewNbt.read(flask, null);
        if (brew == null) {
            return false;
        }

        Vessel.record(flask, brew.sealed());
        BrewNbt.cork(flask);
        return true;
    }

    /** What the vessel itself lends. Only the Aetherglass lends anything. */
    private static Humours innateReach(ItemStack flask) {
        return flask.getItem() instanceof dev.cinderflask.item.CinderflaskItem vessel
                ? new Humours(0, 0, 0, 0, vessel.innateQuintessence())
                : Humours.EMPTY;
    }

    /** Whether the flask could take this ingredient without overflowing the vessel. */
    public static boolean wouldSpoil(ItemStack flask, IngredientTable.Entry entry, @Nullable World world, float ceiling) {
        Brew existing = BrewNbt.read(flask, world);
        if (existing == null) {
            return false;
        }

        Humours humours = existing.sealed().plus(entry.humours());
        float capacity = Math.min(ceiling, existing.capacity() + entry.body());

        return new Brew(humours, 0, 0, capacity).isSpoiled();
    }
}
