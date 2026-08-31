package dev.cinderflask.brew;

import net.minecraft.item.ItemStack;
import net.minecraft.world.World;
import org.jetbrains.annotations.Nullable;

/**
 * What stage a flask is at.
 *
 * <p>The distinction that matters is {@link #WORKING} against {@link #SEALED}: a working brew has no
 * age and cannot be drunk, which is what gives you room to compose one. Corking is what starts the
 * clock, and it is also when the flask records what it held into its seasoning.
 */
public enum BrewState {
    /** Nothing in it. Only a base will go in. */
    EMPTY,
    /** Has a base and possibly ingredients. Not ageing, not drinkable, still open to additions. */
    WORKING,
    /** Corked. Ageing, drinkable, closed. */
    SEALED,
    /** Aged past the point of being a brew. Pour it out, wash it out, or regret drinking it. */
    RUINED;

    public static BrewState of(ItemStack flask, @Nullable World world) {
        if (!BrewNbt.hasBrew(flask)) {
            return EMPTY;
        }

        if (!BrewNbt.isCorked(flask)) {
            return WORKING;
        }

        Brew brew = BrewNbt.read(flask, world);
        return brew != null && brew.isSpoiled() ? RUINED : SEALED;
    }

    public boolean canDrink() {
        return this == SEALED;
    }

    public boolean acceptsIngredients() {
        return this == WORKING;
    }

    public boolean acceptsBase() {
        return this == EMPTY;
    }

    public String translationKey() {
        return "cinderflask.state." + name().toLowerCase(java.util.Locale.ROOT);
    }
}
