package dev.cinderflask.brew;

import dev.cinderflask.Cinderflask;
import dev.cinderflask.player.Palate;
import dev.cinderflask.recipe.VesselRecipes;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.recipe.Recipe;
import net.minecraft.recipe.RecipeManager;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * The map the Almanac draws.
 *
 * <p>Pure data — no screen, no rendering — so what the book claims can be tested without opening it.
 *
 * <p>The twelve known brews are not placed by hand. They are laid out as the wheel they actually
 * are: angle from which humour leads them, radius from whether reach carries them outward. Retune a
 * landmark and its node moves with it, which is the only way a map of a system stays true to it.
 * The vessel ladder comes from the upgrade recipes for the same reason.
 */
public final class Almanac {
    /** Half the width of a node, in map units. Nodes are square. */
    public static final int NODE = 20;

    /** The closest two node centres may sit. Enforced by test, because overlap is unreadable. */
    public static final int SPACING = 30;

    // Concentric, so the wheel reads outward: the turning centre, the four humours, the brews
    // that are one humour or a lean between two, and beyond them the four that reach.
    private static final int AXIS_RING = 80;
    private static final int INNER_RING = 160;
    private static final int OUTER_RING = 250;

    /** Palate axis 4 is quintessence; 0..3 are the wheel humours. */
    private static final int REACH_AXIS = 4;

    /**
     * What a reader has to have tasted before a node opens.
     *
     * <p>Reuses {@link Palate} exactly as the tooltip does. Knowledge is the progression; nothing is
     * locked behind it, so a gated node hides an explanation and never a capability.
     */
    public record Gate(int axis, int level) {
        public static final Gate OPEN = new Gate(-1, 0);

        public boolean isOpen(Palate palate) {
            return axis < 0 || palate.level(axis) >= level;
        }

        public boolean always() {
            return axis < 0;
        }
    }

    public record Node(String id, ItemStack icon, int x, int y, Gate gate) {
        public String titleKey() {
            return "cinderflask.almanac." + id + ".title";
        }

        public String bodyKey() {
            return "cinderflask.almanac." + id + ".body";
        }
    }

    public record Edge(String from, String to) {
    }

    /**
     * A heading over a cluster. One map rather than five chapters, but a map with nothing written on
     * it is a constellation of unlabelled boxes.
     */
    public record Region(String id, int x, int y) {
        public String titleKey() {
            return "cinderflask.almanac.region." + id;
        }
    }

    public record Map(List<Node> nodes, List<Edge> edges, List<Region> regions) {
        @Nullable
        public Node node(String id) {
            for (Node node : nodes) {
                if (node.id().equals(id)) {
                    return node;
                }
            }
            return null;
        }
    }

    private Almanac() {
    }

    /**
     * Builds the map. Pass the recipe manager where one is available and the vessel ladder is read
     * from the registered upgrades; pass null and it falls back to the declared vessel order.
     */
    public static Map build(@Nullable RecipeManager recipes) {
        List<Node> nodes = new ArrayList<>();
        List<Edge> edges = new ArrayList<>();

        vessel(nodes, edges, recipes);
        brewing(nodes, edges);
        axes(nodes, edges);
        wheel(nodes, edges);
        derivations(nodes, edges);
        endings(nodes, edges);

        return new Map(List.copyOf(nodes), List.copyOf(edges), List.of(
                new Region("vessel", -640, -500),
                new Region("brewing", -640, -210),
                new Region("endings", -620, 45),
                new Region("wheel", 0, -300),
                new Region("numbers", 560, -470)));
    }

    // -------------------------------------------------------------------------------------------
    // The vessel, upper left
    // -------------------------------------------------------------------------------------------

    private static void vessel(List<Node> nodes, List<Edge> edges, @Nullable RecipeManager recipes) {
        nodes.add(open("flask", Cinderflask.CINDERFLASK, -820, -300));
        nodes.add(open("mote", Items.PIG_SPAWN_EGG, -700, -370));
        nodes.add(open("temper", Items.MAGMA_BLOCK, -700, -230));
        nodes.add(gated("motes_unique", Items.ALLAY_SPAWN_EGG, -820, -440, REACH_AXIS, 2));

        edges.add(new Edge("flask", "mote"));
        edges.add(new Edge("flask", "temper"));
        edges.add(new Edge("mote", "motes_unique"));

        // The ladder, from the upgrade recipes themselves, so a fifth tier would appear here.
        List<VesselRecipes.Upgrade> upgrades = upgrades(recipes);
        int x = -580;

        if (upgrades.isEmpty()) {
            String previous = "flask";
            for (int tier = 1; tier < Cinderflask.vessels().length; tier++) {
                String id = "vessel_" + tier;
                nodes.add(open(id, Cinderflask.vessels()[tier], x, -300));
                edges.add(new Edge(previous, id));
                previous = id;
                x += 120;
            }
            return;
        }

        for (VesselRecipes.Upgrade upgrade : upgrades) {
            String from = vesselId(upgrade.from());
            String to = vesselId(upgrade.to());

            if (nodes.stream().noneMatch(node -> node.id().equals(to))) {
                nodes.add(open(to, upgrade.to(), x, -300));
                x += 120;
            }

            edges.add(new Edge(from, to));
        }
    }

    /** Ordered narrowest first, so the ladder is drawn in the order it is climbed. */
    private static List<VesselRecipes.Upgrade> upgrades(@Nullable RecipeManager recipes) {
        List<VesselRecipes.Upgrade> found = new ArrayList<>();
        if (recipes == null) {
            return found;
        }

        for (Recipe<?> recipe : recipes.values()) {
            if (recipe instanceof VesselRecipes.Upgrade upgrade) {
                found.add(upgrade);
            }
        }

        found.sort((a, b) -> Float.compare(a.from().ceiling(), b.from().ceiling()));
        return found;
    }

    private static String vesselId(net.minecraft.item.Item vessel) {
        return vessel == Cinderflask.CINDERFLASK ? "flask"
                : net.minecraft.registry.Registries.ITEM.getId(vessel).getPath();
    }

    // -------------------------------------------------------------------------------------------
    // Brewing, lower left. All of it open: this is the tutorial.
    // -------------------------------------------------------------------------------------------

    private static void brewing(List<Node> nodes, List<Edge> edges) {
        nodes.add(open("base", Items.WATER_BUCKET, -820, -60));
        nodes.add(open("body", Items.NETHER_WART, -700, -130));
        nodes.add(open("ingredients", Items.BLAZE_POWDER, -700, 10));
        nodes.add(open("cork", Items.OAK_PLANKS, -580, -60));
        nodes.add(open("sip", Cinderflask.CINDERFLASK, -460, -60));

        edges.add(new Edge("flask", "base"));
        edges.add(new Edge("base", "body"));
        edges.add(new Edge("base", "ingredients"));
        edges.add(new Edge("body", "cork"));
        edges.add(new Edge("ingredients", "cork"));
        edges.add(new Edge("cork", "sip"));
    }

    // -------------------------------------------------------------------------------------------
    // The five axes, the column between the loop and the wheel
    // -------------------------------------------------------------------------------------------

    private static void axes(List<Node> nodes, List<Edge> edges) {
        String[] names = {"choleric", "melancholic", "sanguine", "phlegmatic"};
        ItemStack[] icons = {
                new ItemStack(Items.BLAZE_POWDER), new ItemStack(Items.IRON_NUGGET),
                new ItemStack(Items.GLOW_BERRIES), new ItemStack(Items.FERMENTED_SPIDER_EYE),
        };

        // On their own spokes, so every brew sits radially outward from the humour that leads it and
        // the whole wheel is one short step wide. Laid out as a column beside the wheel instead, the
        // twelve edges to the brews crossed the entire map and made it unreadable.
        for (int humour = 0; humour < names.length; humour++) {
            int[] at = onWheel(humour, 0, AXIS_RING);
            nodes.add(new Node(names[humour], icons[humour], at[0], at[1], Gate.OPEN));
            edges.add(new Edge("wheel", names[humour]));
        }

        // Reach and corruption are axes too, but neither turns, so neither sits on a spoke.
        nodes.add(open("reach", Items.ECHO_SHARD, 190, -340));
        nodes.add(gated("corruption", Items.ROTTEN_FLESH, -190, 340, 3, 3));
        edges.add(new Edge("wheel", "reach"));
        edges.add(new Edge("wheel", "corruption"));

        // One bridge from the brewing loop into the wheel, rather than six.
        edges.add(new Edge("ingredients", "wheel"));
    }

    /** Where something sits on the wheel: {@code humour} quarters, plus an offset, at a radius. */
    private static int[] onWheel(int humour, int offsetDegrees, int radius) {
        double radians = Math.toRadians(humour * 90 + offsetDegrees);
        return new int[]{
                (int) Math.round(Math.cos(radians) * radius),
                (int) Math.round(Math.sin(radians) * radius),
        };
    }

    // -------------------------------------------------------------------------------------------
    // The wheel. Twelve nodes that place themselves.
    // -------------------------------------------------------------------------------------------

    private static void wheel(List<Node> nodes, List<Edge> edges) {
        nodes.add(gated("wheel", Items.CLOCK, 0, 0, 1, 2));

        for (Landmarks.Landmark landmark : Landmarks.all()) {
            boolean leaning = landmark.shape() == Landmarks.Shape.LEAN;
            int radius = landmark.shape() == Landmarks.Shape.CARRIED ? OUTER_RING : INNER_RING;
            int[] at = onWheel(landmark.humour(), leaning ? 45 : 0, radius);

            String id = landmark.id().getPath();
            nodes.add(new Node(id, signature(landmark), at[0], at[1], Gate.OPEN));

            // Straight out from the humour that leads it, so the spoke is short and says why.
            edges.add(new Edge(axisOf(landmark.humour()), id));
        }
    }

    /**
     * The ingredient that means this brew, found the same way the routes find it.
     *
     * <p>Twelve identical flasks told a reader nothing and made the wheel a row of bottles. The
     * signature is the thing you would actually reach for, so the wheel shows kelp on Kelpwine.
     */
    private static ItemStack signature(Landmarks.Landmark landmark) {
        for (net.minecraft.item.Item candidate : net.minecraft.registry.Registries.ITEM) {
            IngredientTable.Entry entry = IngredientTable.lookup(new ItemStack(candidate));

            if (entry == null || entry.humours().isEmpty() || entry.base()) {
                continue;
            }

            Landmarks.Landmark means = Landmarks.nearest(entry.humours());
            if (means != null && means.id().equals(landmark.id())) {
                return new ItemStack(candidate);
            }
        }

        return new ItemStack(Cinderflask.CINDERFLASK);
    }

    private static String axisOf(int humour) {
        return switch (Math.floorMod(humour, Humours.WHEEL)) {
            case 0 -> "choleric";
            case 1 -> "melancholic";
            case 2 -> "sanguine";
            default -> "phlegmatic";
        };
    }

    // -------------------------------------------------------------------------------------------
    // What the numbers mean, right. Earned rather than given.
    // -------------------------------------------------------------------------------------------

    private static void derivations(List<Node> nodes, List<Edge> edges) {
        // A column, chained, entered once. Four separate edges back to the middle of the wheel
        // fanned straight across the spokes and buried them.
        String[] chain = {"amplifier", "duration", "concentration", "balance", "rebound"};
        nodes.add(gated("amplifier", Items.GLOWSTONE_DUST, 560, -380, 0, 2));
        nodes.add(gated("duration", Items.REDSTONE, 560, -260, 1, 2));
        nodes.add(gated("concentration", Items.PITCHER_PLANT, 560, -140, 2, 2));
        nodes.add(gated("balance", Items.AMETHYST_SHARD, 560, -20, 3, 2));
        nodes.add(gated("rebound", Items.POISONOUS_POTATO, 560, 100, 0, 3));

        // The four new nodes hang off the numbers, because that is what they are: what a brew's
        // own quantities do once they pass a point.
        nodes.add(gated("inflections", Items.AMETHYST_CLUSTER, 700, -380, 1, 3));
        nodes.add(gated("delivery", Items.ECHO_SHARD, 700, -260, REACH_AXIS, 3));
        nodes.add(gated("corrupt", Items.WITHER_ROSE, 700, -140, 3, 4));
        nodes.add(gated("capstone", Items.NETHER_STAR, 700, -20, 0, 5));

        edges.add(new Edge("amplifier", "inflections"));
        edges.add(new Edge("inflections", "delivery"));
        edges.add(new Edge("inflections", "corrupt"));
        edges.add(new Edge("corrupt", "capstone"));

        edges.add(new Edge("wheel", "amplifier"));
        for (int i = 1; i < chain.length; i++) {
            edges.add(new Edge(chain[i - 1], chain[i]));
        }
    }

    // -------------------------------------------------------------------------------------------
    // How a brew ends, along the bottom
    // -------------------------------------------------------------------------------------------

    private static void endings(List<Node> nodes, List<Edge> edges) {
        // Directly under the brewing loop, so every ending is a short step from the sip that
        // leads to it rather than a line drawn across the whole map.
        nodes.add(gated("ageing", Items.CLOCK, -460, 90, 1, 3));
        nodes.add(gated("cracking", Items.GLASS_BOTTLE, -460, 230, 0, 3));
        nodes.add(gated("sinter", Cinderflask.SINTER, -460, 370, 0, 4));
        nodes.add(gated("dregs", Cinderflask.DREGS, -620, 90, 2, 3));
        nodes.add(gated("solera", Cinderflask.BOUND_CINDERFLASK, -780, 90, 2, 4));
        nodes.add(gated("sump", Cinderflask.SUMP, -620, 230, 3, 4));
        nodes.add(gated("names", Items.NAME_TAG, -780, 230, 1, 4));

        edges.add(new Edge("sip", "ageing"));
        edges.add(new Edge("ageing", "cracking"));
        edges.add(new Edge("cracking", "sinter"));
        edges.add(new Edge("sip", "dregs"));
        edges.add(new Edge("dregs", "solera"));
        edges.add(new Edge("ageing", "sump"));
        edges.add(new Edge("solera", "names"));
    }

    // -------------------------------------------------------------------------------------------
    // Shorthand
    // -------------------------------------------------------------------------------------------

    private static Node open(String id, net.minecraft.item.ItemConvertible icon, int x, int y) {
        return new Node(id, new ItemStack(icon), x, y, Gate.OPEN);
    }

    private static Node gated(String id, net.minecraft.item.ItemConvertible icon, int x, int y,
                              int axis, int level) {
        return new Node(id, new ItemStack(icon), x, y, new Gate(axis, level));
    }
}
