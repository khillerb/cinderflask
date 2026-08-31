package dev.cinderflask.brew;

import net.minecraft.entity.EntityType;
import net.minecraft.entity.LivingEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.item.SpawnEggItem;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtElement;
import net.minecraft.nbt.NbtFloat;
import net.minecraft.nbt.NbtList;
import net.minecraft.registry.Registries;
import net.minecraft.util.Identifier;
import org.jetbrains.annotations.Nullable;

/**
 * What the flask is, as opposed to what is in it: its mote, and everything it has ever held.
 *
 * <p>Both outlive any single brew. Emptying the flask does not touch either.
 */
public final class Vessel {
    /** A flask nobody has given a mote to. Pale, and visibly waiting. */
    public static final int UNCAUGHT_MOTE = 0xC9C4B4;

    /** Brews after which seasoning stops deepening. */
    public static final int SEASONING_CAP = 30;

    /** Seasoning never fully overrules where the mote came from. */
    private static final float SEASONING_PULL = 0.6f;

    /** Below this, a mote cannot be picked out against a dark brew. */
    private static final float MINIMUM_MOTE_LUMINANCE = 0.45f;

    private static final String MOTE = "Mote";
    private static final String MOTE_ORIGIN = "MoteOrigin";
    private static final String SEASONING = "Seasoning";
    private static final String BREW_COUNT = "Brews";

    private Vessel() {
    }

    // -------------------------------------------------------------------------------------------
    // The mote
    // -------------------------------------------------------------------------------------------

    /**
     * Takes an impression of a living thing. The mob is unharmed — the cost is that a flask only
     * ever holds one mote, so which creature you choose is permanent.
     *
     * @return false if this flask already has one, or the creature has nothing to give
     */
    public static boolean catchMote(ItemStack flask, LivingEntity source) {
        if (hasMote(flask)) {
            return false;
        }

        Integer colour = colourOf(source.getType());
        if (colour == null) {
            return false;
        }

        NbtCompound nbt = flask.getOrCreateNbt();
        nbt.putInt(MOTE, colour);
        nbt.putString(MOTE_ORIGIN, Registries.ENTITY_TYPE.getId(source.getType()).toString());
        return true;
    }

    /**
     * A creature's colour, taken from its spawn egg.
     *
     * <p>Free for every mob in the game and every mob any other mod adds, and it doubles as the
     * filter for what can be caught at all: players and bosses have no spawn egg, so they have
     * nothing to give.
     */
    @Nullable
    private static Integer colourOf(EntityType<?> type) {
        SpawnEggItem egg = SpawnEggItem.forEntity(type);
        return egg == null ? null : egg.getColor(0);
    }

    public static boolean hasMote(ItemStack flask) {
        NbtCompound nbt = flask.getNbt();
        return nbt != null && nbt.contains(MOTE, NbtElement.INT_TYPE);
    }

    @Nullable
    public static Identifier moteOrigin(ItemStack flask) {
        NbtCompound nbt = flask.getNbt();
        if (nbt == null || !nbt.contains(MOTE_ORIGIN, NbtElement.STRING_TYPE)) {
            return null;
        }
        return Identifier.tryParse(nbt.getString(MOTE_ORIGIN));
    }

    /**
     * What the mote actually looks like: where it came from, dragged towards the character of
     * everything the flask has held since. A well-used flask stops looking like the creature that
     * seeded it.
     */
    public static int moteColour(ItemStack flask) {
        NbtCompound nbt = flask.getNbt();
        int origin = nbt != null && nbt.contains(MOTE, NbtElement.INT_TYPE)
                ? nbt.getInt(MOTE)
                : UNCAUGHT_MOTE;

        Humours history = seasoning(flask);
        if (history.isEmpty()) {
            return lit(origin);
        }

        float pull = Math.min(1, brewCount(flask) / (float) SEASONING_CAP) * SEASONING_PULL;
        return lit(lerp(origin, history.colour(), pull));
    }

    /**
     * Lifts a mote until it can actually be seen. Spawn-egg colours run all the way down to nearly
     * black — a warden's is 0x0F4649 — and an unlit spirit inside a dark brew is invisible.
     */
    private static int lit(int colour) {
        int red = (colour >> 16) & 0xFF;
        int green = (colour >> 8) & 0xFF;
        int blue = colour & 0xFF;

        float luminance = (0.299f * red + 0.587f * green + 0.114f * blue) / 255f;
        if (luminance >= MINIMUM_MOTE_LUMINANCE) {
            return colour;
        }

        // Towards white rather than scaling up, so a dark hue keeps its identity instead of
        // saturating into whichever channel happened to be largest.
        float t = 1 - luminance / MINIMUM_MOTE_LUMINANCE;
        return lerp(colour, 0xFFFFFF, t * 0.7f);
    }

    private static int lerp(int from, int to, float t) {
        int red = Math.round(((from >> 16) & 0xFF) * (1 - t) + ((to >> 16) & 0xFF) * t);
        int green = Math.round(((from >> 8) & 0xFF) * (1 - t) + ((to >> 8) & 0xFF) * t);
        int blue = Math.round((from & 0xFF) * (1 - t) + (to & 0xFF) * t);
        return (red << 16) | (green << 8) | blue;
    }

    // -------------------------------------------------------------------------------------------
    // Seasoning
    // -------------------------------------------------------------------------------------------

    /** The running mean of everything this flask has held. */
    public static Humours seasoning(ItemStack flask) {
        NbtCompound nbt = flask.getNbt();
        if (nbt == null || !nbt.contains(SEASONING, NbtElement.LIST_TYPE)) {
            return Humours.EMPTY;
        }

        NbtList values = nbt.getList(SEASONING, NbtElement.FLOAT_TYPE);
        if (values.size() < 5) {
            return Humours.EMPTY;
        }

        return new Humours(values.getFloat(0), values.getFloat(1), values.getFloat(2),
                values.getFloat(3), values.getFloat(4));
    }

    public static int brewCount(ItemStack flask) {
        NbtCompound nbt = flask.getNbt();
        return nbt == null ? 0 : Math.max(0, nbt.getInt(BREW_COUNT));
    }

    /** Folds one more brew into the flask's history. Called when a brew is sealed. */
    public static void record(ItemStack flask, Humours brewed) {
        int count = brewCount(flask);
        Humours mean = Humours.blend(seasoning(flask), count, brewed, 1);

        NbtList values = new NbtList();
        values.add(NbtFloat.of(mean.choleric()));
        values.add(NbtFloat.of(mean.melancholic()));
        values.add(NbtFloat.of(mean.sanguine()));
        values.add(NbtFloat.of(mean.phlegmatic()));
        values.add(NbtFloat.of(mean.quintessence()));

        NbtCompound nbt = flask.getOrCreateNbt();
        nbt.put(SEASONING, values);
        nbt.putInt(BREW_COUNT, count + 1);
    }

    /**
     * How far the vessel leans a new brew towards what it is used to. Reaches its full weight at
     * {@link #SEASONING_CAP} brews.
     */
    public static Humours drift(ItemStack flask) {
        int count = brewCount(flask);
        if (count <= 0) {
            return Humours.EMPTY;
        }

        float scale = Math.min(1, count / (float) SEASONING_CAP) * 0.25f;
        Humours mean = seasoning(flask);

        return new Humours(
                mean.choleric() * scale, mean.melancholic() * scale, mean.sanguine() * scale,
                mean.phlegmatic() * scale, mean.quintessence() * scale);
    }
}
