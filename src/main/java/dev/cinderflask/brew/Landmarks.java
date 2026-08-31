package dev.cinderflask.brew;

import dev.cinderflask.Cinderflask;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.Registries;
import net.minecraft.util.Identifier;

import java.util.ArrayList;
import java.util.List;

/**
 * The twelve known coordinates.
 *
 * <p>Brews are emergent — any point in the space is drinkable — but a space with nothing named in it
 * is a space nobody can aim at. These are landmarks: worth reaching, worth documenting, and the
 * reference points everything between them is described against.
 */
public final class Landmarks {
    /** How close a brew has to sit before it takes a landmark's name. */
    public static final float SIMILARITY = 0.94f;

    public record Landmark(Identifier id, Humours target, String role) {
        public String translationKey() {
            return "cinderflask.landmark." + id.getPath();
        }
    }

    private static final List<Landmark> ALL = List.of(
            landmark("emberflask", 7, 1, 1, 0, 0, "alchemist"),
            landmark("deadmans_draught", 8, 0, 2, 0, 0, "berserker"),
            landmark("quickstep_draught", 6, 0, 2, 2, 0, "skirmisher"),
            landmark("ironroot_tonic", 1, 7, 1, 0, 0, "bulwark"),
            landmark("bramblewine", 3, 6, 0, 1, 0, "retaliator"),
            landmark("deepdelve", 2, 5, 2, 1, 1, "miner"),
            landmark("riposte_cordial", 2, 4, 1, 3, 0, "duelist"),
            landmark("honeyed_restorative", 0, 1, 8, 0, 2, "healer"),
            landmark("sap_sworn_mead", 3, 0, 6, 1, 0, "reaver"),
            landmark("kelpwine", 0, 2, 5, 3, 1, "diver"),
            landmark("nightcap", 1, 1, 1, 7, 0, "assassin"),
            landmark("gravemead", 0, 4, 0, 6, 2, "necromancer"));

    private Landmarks() {
    }

    private static Landmark landmark(String path, float cho, float mel, float san, float phl,
                                     float qui, String role) {
        return new Landmark(Cinderflask.id(path), new Humours(cho, mel, san, phl, qui), role);
    }

    public static List<Landmark> all() {
        return ALL;
    }

    /** The landmark a brew has landed on, if it has landed on one at all. */
    public static Landmark nearest(Humours brew) {
        Landmark best = null;
        float bestSimilarity = SIMILARITY;

        for (Landmark landmark : ALL) {
            float similarity = brew.similarity(landmark.target());
            if (similarity > bestSimilarity) {
                bestSimilarity = similarity;
                best = landmark;
            }
        }

        return best;
    }

    /**
     * One way to reach a landmark, worked out from whatever the brewing table currently holds.
     *
     * <p>Greedy: repeatedly take whichever ingredient closes the most distance, until adding anything
     * would make it worse. Not the only route and not always the cheapest, but it is derived from the
     * live table, so a datapack that retunes an ingredient retunes the suggestion with it.
     */
    public static List<Item> route(Landmark landmark, int limit) {
        List<Item> route = new ArrayList<>();
        Humours running = Humours.EMPTY;

        for (int step = 0; step < limit; step++) {
            Item best = null;
            Humours bestResult = null;
            float bestSimilarity = running.isEmpty() ? -1 : running.similarity(landmark.target());

            for (Item candidate : Registries.ITEM) {
                IngredientTable.Entry entry = IngredientTable.lookup(new ItemStack(candidate));
                if (entry == null || entry.humours().isEmpty() || entry.base()) {
                    continue;
                }

                Humours next = running.plus(entry.humours());
                float similarity = next.similarity(landmark.target());

                if (similarity > bestSimilarity) {
                    bestSimilarity = similarity;
                    best = candidate;
                    bestResult = next;
                }
            }

            if (best == null) {
                break;
            }

            route.add(best);
            running = bestResult;
        }

        return route;
    }
}
