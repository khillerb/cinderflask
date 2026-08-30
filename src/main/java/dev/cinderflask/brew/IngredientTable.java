package dev.cinderflask.brew;

import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.Map;

/**
 * What each ingredient writes into a brew.
 *
 * <p>Hardcoded for now against vanilla stand-ins, so the loop is playable before the herbs exist.
 * Phase 3 replaces the contents with datapack JSON loaded through {@code ResourceManagerHelper} and
 * synced to clients; everything else in the mod goes through {@link #lookup} and will not notice.
 */
public final class IngredientTable {
    /**
     * @param humours what it writes into the vector
     * @param body    how much of the vessel's ceiling it fills — capacity, not essence
     */
    public record Entry(Humours humours, float body) {
        public static Entry humour(float cho, float mel, float san, float phl) {
            return new Entry(Humours.of(cho, mel, san, phl), 0);
        }

        public static Entry aether(float quintessence) {
            return new Entry(new Humours(0, 0, 0, 0, quintessence), 0);
        }

        public static Entry body(float body) {
            return new Entry(Humours.EMPTY, body);
        }
    }

    private static final Map<Item, Entry> ENTRIES = new HashMap<>();

    static {
        // Body: how much of the vessel you actually fill. More body means more doses and a milder
        // brew, because the same essence is spread through more of it.
        ENTRIES.put(Items.HONEYCOMB, Entry.body(2));
        ENTRIES.put(Items.NETHER_WART, Entry.body(4));
        ENTRIES.put(Items.PITCHER_PLANT, Entry.body(6));

        // Choleric: hot and quick.
        ENTRIES.put(Items.BLAZE_POWDER, Entry.humour(3, 0, 0, 0));
        ENTRIES.put(Items.MAGMA_CREAM, Entry.humour(2, 0, 1, 0));
        ENTRIES.put(Items.SUGAR, Entry.humour(1, 0, 1, 0));

        // Melancholic: cold and patient.
        ENTRIES.put(Items.IRON_NUGGET, Entry.humour(0, 2, 0, 0));
        ENTRIES.put(Items.AZALEA, Entry.humour(0, 3, 0, 0));
        ENTRIES.put(Items.PRISMARINE_CRYSTALS, Entry.humour(0, 2, 0, 1));

        // Sanguine: sweet and vital.
        ENTRIES.put(Items.GLOW_BERRIES, Entry.humour(0, 0, 3, 0));
        ENTRIES.put(Items.HONEY_BOTTLE, Entry.humour(0, 0, 2, 0));
        ENTRIES.put(Items.SWEET_BERRIES, Entry.humour(0, 0, 2, 0));

        // Phlegmatic: dull and strange.
        ENTRIES.put(Items.FERMENTED_SPIDER_EYE, Entry.humour(0, 0, 0, 3));
        ENTRIES.put(Items.INK_SAC, Entry.humour(0, 1, 0, 2));
        ENTRIES.put(Items.SPORE_BLOSSOM, Entry.humour(0, 0, 1, 2));

        // Reach. The shard is what does the echoing.
        ENTRIES.put(Items.AMETHYST_SHARD, Entry.aether(1));
        ENTRIES.put(Items.GHAST_TEAR, Entry.aether(2));
        ENTRIES.put(Items.ECHO_SHARD, Entry.aether(5));
        ENTRIES.put(Items.NETHER_STAR, Entry.aether(5));
    }

    private IngredientTable() {
    }

    @Nullable
    public static Entry lookup(ItemStack stack) {
        return stack.isEmpty() ? null : ENTRIES.get(stack.getItem());
    }

    public static boolean isIngredient(ItemStack stack) {
        return lookup(stack) != null;
    }
}
