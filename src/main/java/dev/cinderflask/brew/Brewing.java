package dev.cinderflask.brew;

import net.minecraft.item.ItemStack;
import net.minecraft.world.World;

/**
 * Adding things to a flask.
 *
 * <p>The clock starts on the first ingredient and is not reset by later ones, so a brew you keep
 * working on keeps ageing while you work on it. Phase 3 puts an explicit seal step in front of this
 * and moves the table to datapack JSON; the arithmetic here does not change.
 */
public final class Brewing {
    /** A flask with no body ingredient in it still holds a little. */
    public static final float MINIMUM_CAPACITY = 1;

    private Brewing() {
    }

    /**
     * Folds one ingredient into whatever the flask already holds.
     *
     * @param ceiling how much capacity this vessel will allow body ingredients to reach
     * @return false if the brew spoiled, in which case the flask now holds Sump
     */
    public static boolean add(ItemStack flask, IngredientTable.Entry entry, World world, float ceiling) {
        Brew existing = BrewNbt.read(flask, world);

        Humours humours = existing == null ? Humours.EMPTY : existing.sealed();
        float capacity = existing == null ? MINIMUM_CAPACITY : existing.capacity();
        float corruption = existing == null ? 0 : existing.addedCorruption();
        float phase = existing == null ? 0 : existing.phase();

        boolean starting = existing == null;
        if (starting) {
            // Whatever the vessel was fired against leans the brew and lends its own filth, and so
            // does everything it has held before — a flask used only for one humour starts pulling
            // every new brew that way.
            Temper temper = BrewNbt.temper(flask);
            humours = humours.plus(temper.bias()).plus(Vessel.drift(flask));
            corruption += temper.corruption();
        }

        humours = humours.plus(entry.humours());
        capacity = Math.min(ceiling, capacity + entry.body());

        Brew brewed = new Brew(humours, phase, corruption, capacity);
        BrewNbt.seal(flask, brewed, world, brewed.doses());

        if (starting) {
            Vessel.record(flask, humours);
        }

        return !brewed.isSpoiled();
    }

    /** Whether the flask could take this ingredient without overflowing the vessel. */
    public static boolean wouldSpoil(ItemStack flask, IngredientTable.Entry entry, World world, float ceiling) {
        Brew existing = BrewNbt.read(flask, world);

        Humours humours = (existing == null ? Humours.EMPTY : existing.sealed()).plus(entry.humours());
        float capacity = Math.min(ceiling,
                (existing == null ? MINIMUM_CAPACITY : existing.capacity()) + entry.body());

        return new Brew(humours, 0, 0, capacity).isSpoiled();
    }
}
