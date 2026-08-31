package dev.cinderflask.test;

import dev.cinderflask.Cinderflask;
import dev.cinderflask.brew.Brew;
import dev.cinderflask.brew.BrewNbt;
import dev.cinderflask.brew.Brewing;
import dev.cinderflask.brew.Cracking;
import dev.cinderflask.brew.Dregs;
import dev.cinderflask.brew.Humours;
import dev.cinderflask.brew.IngredientTable;
import dev.cinderflask.brew.Landmarks;
import dev.cinderflask.brew.Vessel;
import dev.cinderflask.brew.VesselName;
import dev.cinderflask.item.CinderflaskItem;
import dev.cinderflask.recipe.VesselOperation;
import dev.cinderflask.item.SinterItem;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.item.ItemStack;
import net.minecraft.inventory.SimpleInventory;
import net.minecraft.item.Items;
import net.minecraft.recipe.Recipe;
import net.minecraft.recipe.RecipeType;
import net.minecraft.recipe.SpecialCraftingRecipe;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.test.GameTest;
import net.minecraft.test.GameTestException;
import net.minecraft.test.TestContext;

/** What happens to the vessel over its life: cracking, dregs, solera, mending, and its name. */
public class VesselLifeTest implements FabricGameTest {
    private static final float EPSILON = 0.05f;

    private static IngredientTable.Entry of(net.minecraft.item.Item item) {
        IngredientTable.Entry entry = IngredientTable.lookup(new ItemStack(item));
        if (entry == null) {
            throw new GameTestException("No brewing entry for " + item);
        }
        return entry;
    }

    /** A corked, choleric brew in a plain flask. */
    private static ItemStack brewed(ServerWorld world, int blazePowder) {
        ItemStack flask = new ItemStack(Cinderflask.CINDERFLASK);
        Brewing.addBase(flask, of(Items.WATER_BUCKET), 8);
        Brewing.add(flask, of(Items.HONEYCOMB), world, 8);

        for (int i = 0; i < blazePowder; i++) {
            Brewing.add(flask, of(Items.BLAZE_POWDER), world, 8);
        }

        Brewing.cork(flask);
        BrewNbt.stampIfNeeded(flask, world);
        return flask;
    }

    // -------------------------------------------------------------------------------------------
    // Cracking
    // -------------------------------------------------------------------------------------------

    @GameTest(templateName = EMPTY_STRUCTURE)
    public void aCrackVentsTheVolatileHumoursFirst(TestContext context) {
        // Choleric is the volatile one, so a crack should carry it off faster than anything else.
        Humours hot = Humours.of(8, 8, 0, 0);
        Humours vented = hot.vented(0.5f);

        float cholericLost = hot.choleric() - vented.choleric();
        float melancholicLost = hot.melancholic() - vented.melancholic();

        if (cholericLost <= melancholicLost) {
            throw new GameTestException("A crack should breathe off choleric fastest; lost "
                    + cholericLost + " against " + melancholicLost);
        }

        // Which is why a cracked flask ends up colder and slower than what went into it.
        if (vented.depth() / vented.magnitude() <= hot.depth() / hot.magnitude()) {
            throw new GameTestException("What is left should be more patient than what went in.");
        }

        context.complete();
    }

    @GameTest(templateName = EMPTY_STRUCTURE)
    public void leakingSpendsDosesAndCoolsTheBrew(TestContext context) {
        ServerWorld world = context.getWorld();
        ItemStack flask = brewed(world, 2);
        Cracking.crack(flask);

        Brew before = BrewNbt.read(flask, world);
        int doses = BrewNbt.doses(flask);

        var drinker = context.spawnMob(net.minecraft.entity.EntityType.PIG,
                new net.minecraft.util.math.BlockPos(1, 2, 1));
        Cracking.leak(flask, world, drinker);

        Brew after = BrewNbt.read(flask, world);
        if (before == null || after == null) {
            throw new GameTestException("The flask should still hold something after one leak.");
        }

        if (BrewNbt.doses(flask) != doses - 1) {
            throw new GameTestException("A leak should cost one dose.");
        }

        if (after.sealed().choleric() >= before.sealed().choleric()) {
            throw new GameTestException("A leak should have carried some choleric off.");
        }

        if (drinker.getStatusEffects().isEmpty()) {
            throw new GameTestException("A leak should still dose whoever is carrying it.");
        }

        context.complete();
    }

    @GameTest(templateName = EMPTY_STRUCTURE)
    public void sinteringCarriesTheWholeVesselThroughTheFire(TestContext context) {
        ServerWorld world = context.getWorld();
        ItemStack flask = brewed(world, 1);

        Vessel.catchMote(flask, context.spawnMob(net.minecraft.entity.EntityType.PIG,
                new net.minecraft.util.math.BlockPos(1, 2, 1)));
        Cracking.crack(flask);

        int mote = Vessel.moteColour(flask);
        int brews = Vessel.brewCount(flask);

        // Through the recipe manager rather than through SinterItem, because the helper working is
        // not the same thing as a furnace being able to reach it. It was not, for a while.
        SimpleInventory furnace = new SimpleInventory(SinterItem.pack(flask));
        var recipe = world.getRecipeManager().getFirstMatch(RecipeType.SMELTING, furnace, world);

        if (recipe.isEmpty()) {
            throw new GameTestException("No smelting recipe matches a sintered flask.");
        }

        ItemStack mended = recipe.get().craft(furnace, world.getRegistryManager());

        if (mended.isEmpty() || !(mended.getItem() instanceof CinderflaskItem)) {
            throw new GameTestException("Nothing came back out of the sinter.");
        }

        if (Cracking.isCracked(mended)) {
            throw new GameTestException("The fire should have mended the crack.");
        }

        // Repairing an heirloom should not cost you the heirloom.
        if (Vessel.moteColour(mended) != mote || Vessel.brewCount(mended) != brews) {
            throw new GameTestException("The mote and seasoning should have survived the fire.");
        }

        context.complete();
    }

    // -------------------------------------------------------------------------------------------
    // Dregs and solera
    // -------------------------------------------------------------------------------------------

    @GameTest(templateName = EMPTY_STRUCTURE)
    public void dregsCarryCharacterAndAgeForward(TestContext context) {
        ServerWorld world = context.getWorld();

        Brew old = new Brew(Humours.of(8, 0, 0, 0), 6, 0, 6);
        ItemStack dregs = Dregs.from(old);

        // What settles is what was actually in the flask, not what was sealed into it. Six phases
        // on, a choleric brew has turned sanguine, and that is what the dregs remember.
        if (dregs.isEmpty() || Dregs.humours(dregs).dominant() != old.current().dominant()) {
            throw new GameTestException("Dregs should remember the brew as it stood, expected "
                    + old.current().dominant() + " got " + Dregs.humours(dregs).dominant());
        }

        if (Dregs.phase(dregs) <= 0 || Dregs.phase(dregs) >= old.phase()) {
            throw new GameTestException("Dregs should carry some of the age, not all of it; got "
                    + Dregs.phase(dregs));
        }

        ItemStack flask = new ItemStack(Cinderflask.CINDERFLASK);
        if (!Brewing.openWithDregs(flask, dregs, 8)) {
            throw new GameTestException("A flask should open on dregs.");
        }

        // A working brew has no clock at all, so the head start is parked until the cork.
        Brew working = BrewNbt.read(flask, world);
        if (working == null || working.phase() != 0) {
            throw new GameTestException("A working brew has no age, even one opened on dregs.");
        }

        Brewing.cork(flask);
        BrewNbt.stampIfNeeded(flask, world);

        Brew opened = BrewNbt.read(flask, world);
        if (opened == null || Math.abs(opened.phase() - Dregs.phase(dregs)) > EPSILON) {
            throw new GameTestException("Corking should cash in the head start; got "
                    + (opened == null ? "nothing" : opened.phase()));
        }

        context.complete();
    }

    @GameTest(templateName = EMPTY_STRUCTURE)
    public void toppingUpHoldsADeepPhaseAtFullBody(TestContext context) {
        // The whole point of a solera: decay outruns anything brewed fresh, so a deep phase at full
        // strength is only reachable by keeping one running.
        Brew tired = new Brew(Humours.of(8, 0, 0, 0), 12, 0, 8);
        Brew fresh = new Brew(Humours.of(8, 0, 0, 0), 0, 0, 8);

        Brew revived = tired.toppedUp(fresh, 8);

        if (revived.current().magnitude() <= tired.current().magnitude()) {
            throw new GameTestException("Topping up should put body back.");
        }

        if (revived.phase() <= fresh.phase() || revived.phase() >= tired.phase()) {
            throw new GameTestException("Age should blend, not reset; got " + revived.phase());
        }

        context.complete();
    }

    // -------------------------------------------------------------------------------------------
    // Tiers and names
    // -------------------------------------------------------------------------------------------

    @GameTest(templateName = EMPTY_STRUCTURE)
    public void widerVesselsGiveMoreDosesNotStrongerBrews(TestContext context) {
        ServerWorld world = context.getWorld();

        ItemStack narrow = new ItemStack(Cinderflask.CINDERFLASK);
        ItemStack wide = new ItemStack(Cinderflask.AETHERGLASS_CINDERFLASK);

        for (ItemStack flask : new ItemStack[]{narrow, wide}) {
            float ceiling = ((dev.cinderflask.item.CinderflaskItem) flask.getItem()).ceiling();
            Brewing.addBase(flask, of(Items.WATER_BUCKET), ceiling);
            for (int i = 0; i < 4; i++) {
                Brewing.add(flask, of(Items.PITCHER_PLANT), world, ceiling);
            }
            Brewing.cork(flask);
            BrewNbt.stampIfNeeded(flask, world);
        }

        Brew small = BrewNbt.read(narrow, world);
        Brew large = BrewNbt.read(wide, world);

        if (small == null || large == null) {
            throw new GameTestException("Both vessels should hold a brew.");
        }

        if (large.doses() <= small.doses()) {
            throw new GameTestException("A wider vessel should hold more doses.");
        }

        context.complete();
    }

    @GameTest(templateName = EMPTY_STRUCTURE)
    public void theAetherglassLendsReachOnItsOwn(TestContext context) {
        ItemStack plain = new ItemStack(Cinderflask.CINDERFLASK);
        ItemStack aetherglass = new ItemStack(Cinderflask.AETHERGLASS_CINDERFLASK);

        Brewing.addBase(plain, of(Items.WATER_BUCKET), 8);
        Brewing.addBase(aetherglass, of(Items.WATER_BUCKET), 22);

        Brew mundane = BrewNbt.read(plain, null);
        Brew aetheric = BrewNbt.read(aetherglass, null);

        if (mundane == null || aetheric == null) {
            throw new GameTestException("Both should have opened.");
        }

        if (aetheric.sealed().quintessence() <= mundane.sealed().quintessence()) {
            throw new GameTestException("The Aetherglass should lend reach without a reagent.");
        }

        context.complete();
    }

    @GameTest(templateName = EMPTY_STRUCTURE)
    public void aWellUsedFlaskEarnsAName(TestContext context) {
        ItemStack flask = new ItemStack(Cinderflask.CINDERFLASK);

        if (VesselName.of(flask) != null) {
            throw new GameTestException("A new flask has not earned anything.");
        }

        for (int i = 0; i < VesselName.THRESHOLD; i++) {
            Vessel.record(flask, Humours.of(8, 0, 0, 0));
        }

        if (VesselName.of(flask) == null) {
            throw new GameTestException("Twenty brews in, it should have a name.");
        }

        context.complete();
    }

    // -------------------------------------------------------------------------------------------
    // Landmarks
    // -------------------------------------------------------------------------------------------

    @GameTest(templateName = EMPTY_STRUCTURE)
    public void everyLandmarkHasAReachableRoute(TestContext context) {
        float ceiling = Cinderflask.CINDERFLASK.ceiling();

        for (Landmarks.Landmark landmark : Landmarks.all()) {
            var route = Landmarks.route(landmark, 6);

            if (route.isEmpty()) {
                throw new GameTestException("No route found to " + landmark.id());
            }

            Humours reached = Humours.EMPTY;
            for (var item : route) {
                IngredientTable.Entry step = IngredientTable.lookup(new ItemStack(item));
                reached = reached.plus(step.humours());

                // A page telling you how to make something should not casually tell you to spoil it.
                if (step.corruption() > 0) {
                    throw new GameTestException("The route to " + landmark.id() + " recommends "
                            + item + ", which corrupts the brew.");
                }
            }

            // The solver should end up somewhere the landmark would actually claim.
            if (reached.similarity(landmark.target()) < 0.8f) {
                throw new GameTestException("The route to " + landmark.id() + " only reaches "
                        + reached.similarity(landmark.target()));
            }

            // And it should be a drink, not a direction. Half a flask is the least that is worth it.
            if (reached.magnitude() < ceiling / 2) {
                throw new GameTestException("The route to " + landmark.id() + " only fills "
                        + reached.magnitude() + " of a " + ceiling + " flask.");
            }
        }

        context.complete();
    }

    @GameTest(templateName = EMPTY_STRUCTURE)
    public void everyLandmarkHasAnIngredientThatObviouslyMeansIt(TestContext context) {
        for (Landmarks.Landmark landmark : Landmarks.all()) {
            var route = Landmarks.route(landmark, 6);
            IngredientTable.Entry opener = IngredientTable.lookup(new ItemStack(route.get(0)));

            // Wanting Kelpwine should send you looking for kelp. If the shortest way in does not
            // itself point at the landmark, the table has no obvious thing to reach for.
            Landmarks.Landmark means = Landmarks.nearest(opener.humours());

            if (means == null || !means.id().equals(landmark.id())) {
                throw new GameTestException("The route to " + landmark.id() + " opens with "
                        + route.get(0) + ", which points at "
                        + (means == null ? "nothing in particular" : means.id()));
            }
        }

        context.complete();
    }

    // -------------------------------------------------------------------------------------------
    // What a recipe viewer can see
    // -------------------------------------------------------------------------------------------

    @GameTest(templateName = EMPTY_STRUCTURE)
    public void everyBenchOperationDescribesItself(TestContext context) {
        int described = 0;

        for (Recipe<?> recipe : context.getWorld().getRecipeManager().values()) {
            if (!recipe.getId().getNamespace().equals(Cinderflask.MOD_ID)
                    || !(recipe instanceof SpecialCraftingRecipe)) {
                continue;
            }

            // A special recipe declares no ingredients and no output, so a viewer shows nothing at
            // all unless the recipe says what it wants. This is the guard against adding a bench
            // operation and quietly shipping it invisible.
            if (!(recipe instanceof VesselOperation operation)) {
                throw new GameTestException(recipe.getId()
                        + " is a special recipe that no recipe viewer could draw.");
            }

            if (operation.inputs().isEmpty() || operation.preview().isEmpty()) {
                throw new GameTestException(recipe.getId() + " describes itself as nothing.");
            }

            described++;
        }

        // Cork, solera, sinter and the three upgrades.
        if (described < 6) {
            throw new GameTestException("Expected six bench operations, found " + described);
        }

        context.complete();
    }
}
