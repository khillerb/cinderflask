package dev.cinderflask.test;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import dev.cinderflask.brew.Almanac;
import dev.cinderflask.brew.Humours;
import dev.cinderflask.brew.Landmarks;
import dev.cinderflask.player.Palate;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.test.GameTest;
import net.minecraft.test.GameTestException;
import net.minecraft.test.TestContext;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

/**
 * The Almanac's map, checked without opening it.
 *
 * <p>A book is the one part of a mod nobody notices is wrong: a node with no text renders as a blank
 * panel, two nodes on top of each other look like one, and an edge to nothing draws a line into
 * empty space. All three are silent in game and loud here.
 */
public class AlmanacTest implements FabricGameTest {
    private static final String LANG = "/assets/cinderflask/lang/en_us.json";

    @GameTest(templateName = EMPTY_STRUCTURE)
    public void noTwoNodesSitOnTopOfEachOther(TestContext context) {
        List<Almanac.Node> nodes = Almanac.build(context.getWorld().getRecipeManager()).nodes();

        for (int i = 0; i < nodes.size(); i++) {
            for (int j = i + 1; j < nodes.size(); j++) {
                Almanac.Node a = nodes.get(i);
                Almanac.Node b = nodes.get(j);

                int dx = Math.abs(a.x() - b.x());
                int dy = Math.abs(a.y() - b.y());

                // Square nodes, so overlap is only avoided when they clear on one axis or the other.
                if (dx < Almanac.SPACING && dy < Almanac.SPACING) {
                    throw new GameTestException(a.id() + " and " + b.id() + " overlap at ("
                            + a.x() + "," + a.y() + ") and (" + b.x() + "," + b.y() + ")");
                }
            }
        }

        context.complete();
    }

    @GameTest(templateName = EMPTY_STRUCTURE)
    public void everyEdgeJoinsTwoNodesThatExist(TestContext context) {
        Almanac.Map map = Almanac.build(context.getWorld().getRecipeManager());

        for (Almanac.Edge edge : map.edges()) {
            if (map.node(edge.from()) == null) {
                throw new GameTestException("Edge from a node that does not exist: " + edge.from());
            }
            if (map.node(edge.to()) == null) {
                throw new GameTestException("Edge to a node that does not exist: " + edge.to());
            }
        }

        context.complete();
    }

    @GameTest(templateName = EMPTY_STRUCTURE)
    public void everyNodeHasWordsToShow(TestContext context) {
        JsonObject lang = language();
        Almanac.Map map = Almanac.build(context.getWorld().getRecipeManager());

        for (Almanac.Node node : map.nodes()) {
            if (!lang.has(node.titleKey())) {
                throw new GameTestException("No title for Almanac node " + node.id());
            }
            if (!lang.has(node.bodyKey())) {
                throw new GameTestException("No body for Almanac node " + node.id());
            }
        }

        for (Almanac.Region region : map.regions()) {
            if (!lang.has(region.titleKey())) {
                throw new GameTestException("No title for Almanac region " + region.id());
            }
        }

        context.complete();
    }

    @GameTest(templateName = EMPTY_STRUCTURE)
    public void everyGateNamesSomethingYouCouldActuallyTaste(TestContext context) {
        for (Almanac.Node node : Almanac.build(context.getWorld().getRecipeManager()).nodes()) {
            Almanac.Gate gate = node.gate();
            if (gate.always()) {
                continue;
            }

            if (gate.axis() < 0 || gate.axis() > Humours.WHEEL) {
                throw new GameTestException(node.id() + " is gated on axis " + gate.axis()
                        + ", which is not a humour or reach");
            }

            if (gate.level() < 1 || gate.level() > Palate.MAX_LEVEL) {
                throw new GameTestException(node.id() + " is gated at level " + gate.level()
                        + ", which is outside 1.." + Palate.MAX_LEVEL);
            }
        }

        context.complete();
    }

    @GameTest(templateName = EMPTY_STRUCTURE)
    public void theTutorialIsReadableBeforeYouHaveDrunkAnything(TestContext context) {
        Almanac.Map map = Almanac.build(context.getWorld().getRecipeManager());
        Palate nothing = Palate.empty();

        // The path from an empty flask to a sip, and the twelve brews by name, are the tutorial.
        // A new player opening the book to a wall of locks is the failure this guards against.
        for (String id : new String[]{"flask", "mote", "temper", "base", "body", "ingredients",
                "cork", "sip", "choleric", "melancholic", "sanguine", "phlegmatic", "reach"}) {
            Almanac.Node node = map.node(id);
            if (node == null || !node.gate().isOpen(nothing)) {
                throw new GameTestException(id + " should be readable from the first minute");
            }
        }

        for (Landmarks.Landmark landmark : Landmarks.all()) {
            Almanac.Node node = map.node(landmark.id().getPath());
            if (node == null || !node.gate().isOpen(nothing)) {
                throw new GameTestException(landmark.id() + " should be named from the start");
            }
        }

        context.complete();
    }

    @GameTest(templateName = EMPTY_STRUCTURE)
    public void thereIsStillSomethingLeftToEarn(TestContext context) {
        long gated = Almanac.build(context.getWorld().getRecipeManager()).nodes().stream()
                .filter(node -> !node.gate().always())
                .count();

        if (gated < 8) {
            throw new GameTestException("Only " + gated + " nodes reveal with the palate, which is "
                    + "not enough for the book to be worth coming back to");
        }

        context.complete();
    }

    /**
     * Writes the layout beside the run directory so {@code tools/render_almanac.py} can draw it.
     *
     * <p>Not an assertion. It exists because the map is the one thing here that cannot be checked by
     * reading the code, and shipping art nobody looked at is how the last three sprites went wrong.
     */
    @GameTest(templateName = EMPTY_STRUCTURE)
    public void theLayoutIsWrittenOutToBeLookedAt(TestContext context) {
        Almanac.Map map = Almanac.build(context.getWorld().getRecipeManager());
        JsonObject json = new JsonObject();

        var nodes = new com.google.gson.JsonArray();
        for (Almanac.Node node : map.nodes()) {
            JsonObject entry = new JsonObject();
            entry.addProperty("id", node.id());
            entry.addProperty("x", node.x());
            entry.addProperty("y", node.y());
            entry.addProperty("gated", !node.gate().always());
            nodes.add(entry);
        }

        var edges = new com.google.gson.JsonArray();
        for (Almanac.Edge edge : map.edges()) {
            JsonObject entry = new JsonObject();
            entry.addProperty("from", edge.from());
            entry.addProperty("to", edge.to());
            edges.add(entry);
        }

        json.add("nodes", nodes);
        json.add("edges", edges);

        try {
            Files.writeString(Path.of("almanac.json"), new Gson().toJson(json),
                    StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new GameTestException("Could not write the layout: " + e);
        }

        context.complete();
    }

    private static JsonObject language() {
        try (InputStream stream = Almanac.class.getResourceAsStream(LANG)) {
            if (stream == null) {
                throw new GameTestException("The mod ships no " + LANG);
            }
            return new Gson().fromJson(
                    new InputStreamReader(stream, StandardCharsets.UTF_8), JsonObject.class);
        } catch (IOException e) {
            throw new GameTestException("Could not read " + LANG + ": " + e);
        }
    }
}
