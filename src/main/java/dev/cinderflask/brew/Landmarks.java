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
 *
 * <p>They are not a list somebody chose. The wheel has four positions, and each position admits three
 * readings: the humour on its own, the humour leaning into the next one round, and the humour carried
 * out to somebody else. Four times three is why there are twelve and not eleven or fifteen, and it is
 * why the four reaching brews are the four support roles — reach is the axis that decides whether an
 * effect happens to you or to the people around you.
 */
public final class Landmarks {
    /** How close a brew has to sit before it takes a landmark's name. */
    public static final float SIMILARITY = 0.94f;

    /** How far a suggested route may wander from the landmark it is aiming at. */
    private static final float ROUTE_FLOOR = 0.90f;

    /** What a unit of corruption costs an ingredient when a route is choosing between two. */
    private static final float FILTH_PENALTY = 0.15f;

    /** A humour on its own. */
    private static final float PURE = 8;

    /** A humour leaning into the next one round the wheel. */
    private static final float LEAN = 6;

    /** A humour carried outward: the same body, with reach behind it. */
    private static final float CARRIED = 8;
    private static final float REACH = 5;

    /**
     * How a landmark was built. The three readings of a humour, and the reason there are twelve.
     *
     * <p>Kept on the record rather than worked out from the vector: the Almanac lays the twelve out
     * as the wheel they actually are, and guessing the shape back from the coordinates would be a
     * second construction that could disagree with the first.
     */
    public enum Shape {
        /** The humour on its own. */
        PURE,
        /** The humour leaning into the next one round. */
        LEAN,
        /** The humour carried outward on reach. */
        CARRIED
    }

    public record Landmark(Identifier id, Humours target, String role, int humour, Shape shape) {
        public String translationKey() {
            return "cinderflask.landmark." + id.getPath();
        }
    }

    private static final List<Landmark> ALL = List.of(
            // Pure: the humour undiluted, and the four selfish roles.
            pure(0, "deadmans_draught", "berserker"),
            pure(1, "ironroot_tonic", "bulwark"),
            pure(2, "sap_sworn_mead", "reaver"),
            pure(3, "nightcap", "assassin"),

            // Leaning: half of one humour and half of the next, and the four hybrids.
            lean(0, "bramblewine", "retaliator"),
            lean(1, "deepdelve", "miner"),
            lean(2, "kelpwine", "diver"),
            lean(3, "quickstep_draught", "skirmisher"),

            // Carried: the same humour with reach behind it, and the four support roles.
            carried(0, "emberflask", "alchemist"),
            carried(1, "riposte_cordial", "duelist"),
            carried(2, "honeyed_restorative", "healer"),
            carried(3, "gravemead", "necromancer"));

    private Landmarks() {
    }

    private static Landmark pure(int humour, String path, String role) {
        return new Landmark(Cinderflask.id(path), on(humour, PURE), role, humour, Shape.PURE);
    }

    private static Landmark lean(int humour, String path, String role) {
        return new Landmark(Cinderflask.id(path),
                on(humour, LEAN).plus(on(humour + 1, LEAN)), role, humour, Shape.LEAN);
    }

    private static Landmark carried(int humour, String path, String role) {
        return new Landmark(Cinderflask.id(path),
                on(humour, CARRIED).plus(new Humours(0, 0, 0, 0, REACH)), role, humour, Shape.CARRIED);
    }

    /** A vector with {@code amount} at one wheel position and nothing anywhere else. */
    private static Humours on(int humour, float amount) {
        return switch (Math.floorMod(humour, Humours.WHEEL)) {
            case 0 -> Humours.of(amount, 0, 0, 0);
            case 1 -> Humours.of(0, amount, 0, 0);
            case 2 -> Humours.of(0, 0, amount, 0);
            default -> Humours.of(0, 0, 0, amount);
        };
    }

    public static List<Landmark> all() {
        return ALL;
    }

    /**
     * How close a brew sits to the nearest landmark, whether or not it is close enough to claim it.
     *
     * <p>{@link #nearest} answers "which one", and returns nothing below {@link #SIMILARITY}. This
     * answers "how near", which is what an inflection on precision needs.
     */
    public static float bestSimilarity(Humours brew) {
        float best = 0;

        for (Landmark landmark : ALL) {
            best = Math.max(best, brew.similarity(landmark.target()));
        }

        return best;
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
     * <p>Greedy, and it keeps going until the brew would actually fill a plain flask rather than
     * stopping the moment the direction is right — a route that says "one kelp" is pointing the right
     * way but is not a drink. Filth is scored against, so a page suggesting how to make something
     * does not casually recommend spoiling it.
     *
     * <p>Derived from the live table, so a datapack that retunes an ingredient retunes the route with
     * it. Not the only way, and rarely the cheapest.
     */
    public static List<Item> route(Landmark landmark, int limit) {
        List<Item> route = new ArrayList<>();
        Humours running = Humours.EMPTY;
        float ceiling = Cinderflask.CINDERFLASK.ceiling();

        while (route.size() < limit && running.magnitude() < ceiling) {
            Item best = null;
            Humours bestResult = null;
            float bestScore = -1;
            float bestSimilarity = 0;

            for (Item candidate : Registries.ITEM) {
                IngredientTable.Entry entry = IngredientTable.lookup(new ItemStack(candidate));
                if (entry == null || entry.humours().isEmpty() || entry.base()) {
                    continue;
                }

                Humours next = running.plus(entry.humours());
                float similarity = next.similarity(landmark.target());
                float score = similarity - FILTH_PENALTY * entry.corruption();

                if (score > bestScore) {
                    bestScore = score;
                    bestSimilarity = similarity;
                    best = candidate;
                    bestResult = next;
                }
            }

            // Better to hand back a short route than to wander away from the landmark to fill up.
            if (best == null || bestSimilarity < ROUTE_FLOOR) {
                break;
            }

            route.add(best);
            running = bestResult;
        }

        return route;
    }
}
