package dev.cinderflask.brew;

import net.minecraft.item.ItemStack;
import net.minecraft.text.MutableText;
import net.minecraft.text.Text;
import org.jetbrains.annotations.Nullable;

/**
 * A flask earns a name once it has held enough to have a character.
 *
 * <p>Composed from two small pools rather than written out, so every vessel gets one, and drawn from
 * what the flask actually is — what it is used for, and how filthy it has become. Both halves are
 * translation keys, so the name survives being played in another language.
 */
public final class VesselName {
    /** Brews before a flask is worth naming. Below this it is just a flask. */
    public static final int THRESHOLD = 20;

    /** Indexed by the seasoning's dominant humour. */
    private static final String[] BY_HUMOUR = {"ember", "patient", "sweet", "sour"};

    /** Chosen by how much filth the vessel has taken on over its life. */
    private static final String[] BY_CHARACTER = {"cup", "flask", "widow", "mother"};

    private VesselName() {
    }

    @Nullable
    public static MutableText of(ItemStack flask) {
        int brews = Vessel.brewCount(flask);
        if (brews < THRESHOLD) {
            return null;
        }

        Humours seasoning = Vessel.seasoning(flask);
        if (seasoning.isEmpty()) {
            return null;
        }

        // Seeded from the mote so a given flask keeps its name for good, rather than renaming
        // itself every time its seasoning shifts a little.
        int seed = Vessel.moteColour(flask) * 31 + seasoning.dominant();
        String noun = BY_CHARACTER[Math.floorMod(seed / 7 + brews / THRESHOLD, BY_CHARACTER.length)];

        return Text.translatable("cinderflask.name.format",
                Text.translatable("cinderflask.name.adjective." + BY_HUMOUR[seasoning.dominant()]),
                Text.translatable("cinderflask.name.noun." + noun));
    }
}
