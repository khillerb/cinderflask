package dev.cinderflask.brew;

import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.entity.effect.StatusEffect;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.potion.PotionUtil;
import net.minecraft.recipe.Ingredient;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * What each ingredient writes into a brew.
 *
 * <p>Filled from {@code data/cinderflask/brewing/*.json} by {@link BrewingRecipes} and pushed to
 * clients on join. Everything else in the mod goes through {@link #lookup}, which resolves in three
 * steps: the item index, then the brew's own effect table if the stack is a potion, then
 * {@link PotionMapping}'s heuristic for effects nobody has described.
 */
public final class IngredientTable {
    /**
     * @param humours    what it writes into the vector
     * @param body       how much of the vessel's ceiling it fills — capacity, not essence
     * @param corruption filth it brings with it
     * @param base       whether it can open a brew, and only open one
     */
    public record Entry(Humours humours, float body, float corruption, boolean base) {
        public static final Entry NOTHING = new Entry(Humours.EMPTY, 0, 0, false);

        public Entry scaled(float factor) {
            return new Entry(
                    new Humours(humours.choleric() * factor, humours.melancholic() * factor,
                            humours.sanguine() * factor, humours.phlegmatic() * factor,
                            humours.quintessence() * factor),
                    body * factor, corruption * factor, base);
        }

        public Entry plus(Entry other) {
            return new Entry(humours.plus(other.humours), body + other.body,
                    corruption + other.corruption, base || other.base);
        }

        public boolean isNothing() {
            return humours.isEmpty() && body <= 0 && corruption <= 0;
        }
    }

    /** A parsed entry before its ingredient has been resolved against the tag state. */
    public record Parsed(@Nullable Ingredient ingredient, @Nullable StatusEffect effect, Entry entry) {
    }

    private static volatile List<Parsed> parsed = List.of();
    private static volatile Map<Item, Entry> itemIndex;
    private static volatile Map<StatusEffect, Entry> effectIndex;

    private IngredientTable() {
    }

    /** Replaces the whole table. Called by the datapack loader and by the client on sync. */
    public static void replace(List<Parsed> entries) {
        parsed = List.copyOf(entries);
        itemIndex = null;
        effectIndex = null;
    }

    public static List<Parsed> entries() {
        return parsed;
    }

    // -------------------------------------------------------------------------------------------
    // Resolution
    // -------------------------------------------------------------------------------------------

    private static Map<Item, Entry> items() {
        Map<Item, Entry> local = itemIndex;
        if (local != null) {
            return local;
        }

        // Built on first use rather than at load, because resolving an Ingredient walks the tag
        // state and that is not settled while the reload is still running.
        local = new HashMap<>();
        for (Parsed entry : parsed) {
            if (entry.ingredient() == null) {
                continue;
            }
            for (ItemStack stack : entry.ingredient().getMatchingStacks()) {
                local.putIfAbsent(stack.getItem(), entry.entry());
            }
        }

        itemIndex = local;
        return local;
    }

    private static Map<StatusEffect, Entry> effects() {
        Map<StatusEffect, Entry> local = effectIndex;
        if (local != null) {
            return local;
        }

        local = new HashMap<>();
        for (Parsed entry : parsed) {
            if (entry.effect() != null) {
                local.putIfAbsent(entry.effect(), entry.entry());
            }
        }

        effectIndex = local;
        return local;
    }

    @Nullable
    public static Entry lookup(ItemStack stack) {
        if (stack.isEmpty()) {
            return null;
        }

        Entry direct = items().get(stack.getItem());
        if (direct != null) {
            return direct;
        }

        return fromPotion(stack);
    }

    /**
     * A potion writes whatever its effects are worth. Strength II writes more than Strength I, and a
     * potion from another mod still maps itself through {@link PotionMapping}.
     */
    @Nullable
    private static Entry fromPotion(ItemStack stack) {
        List<StatusEffectInstance> instances = PotionUtil.getPotionEffects(stack);
        if (instances.isEmpty()) {
            return null;
        }

        Entry total = Entry.NOTHING;
        for (StatusEffectInstance instance : instances) {
            Entry described = effects().get(instance.getEffectType());
            Entry contribution = described != null
                    ? described
                    : PotionMapping.heuristic(instance.getEffectType());

            total = total.plus(contribution.scaled(instance.getAmplifier() + 1));
        }

        return total.isNothing() ? null : total;
    }

    public static boolean isIngredient(ItemStack stack) {
        Entry entry = lookup(stack);
        return entry != null && !entry.isNothing();
    }

    public static boolean isBase(ItemStack stack) {
        Entry entry = lookup(stack);
        return entry != null && entry.base();
    }

    /** Everything currently known, flattened for the sync payload. */
    public static List<Parsed> forSync() {
        return new ArrayList<>(parsed);
    }
}
