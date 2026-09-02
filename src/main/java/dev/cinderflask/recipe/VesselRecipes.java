package dev.cinderflask.recipe;

import dev.cinderflask.Cinderflask;
import dev.cinderflask.brew.Brew;
import dev.cinderflask.brew.BrewNbt;
import dev.cinderflask.brew.BrewState;
import dev.cinderflask.brew.Cracking;
import dev.cinderflask.item.CinderflaskItem;
import net.minecraft.inventory.RecipeInputInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.recipe.Ingredient;
import net.minecraft.recipe.RecipeSerializer;
import net.minecraft.recipe.SpecialCraftingRecipe;
import net.minecraft.recipe.book.CraftingRecipeCategory;
import net.minecraft.registry.DynamicRegistryManager;
import net.minecraft.util.Identifier;
import net.minecraft.world.World;

import java.util.List;
import java.util.function.Predicate;

/**
 * The bench recipes that have to carry a flask's whole state across, which a shaped recipe cannot do.
 *
 * <p>All three follow the same shape: find the one flask, check what it is in, check exactly what else
 * is on the bench, and hand back a copy. The flask's mote, seasoning, temper and name survive every
 * one of them — those belong to the vessel, and re-housing a spirit is not the same as buying a bottle.
 */
public final class VesselRecipes {
    private VesselRecipes() {
    }

    /** Upgrades a vessel to a wider one, keeping everything it has become. */
    public static class Upgrade extends SpecialCraftingRecipe implements VesselOperation {
        private final CinderflaskItem from;
        private final CinderflaskItem to;
        private final Ingredient firstCost;
        private final Ingredient secondCost;
        private final RecipeSerializer<?> serializer;

        public Upgrade(Identifier id, CraftingRecipeCategory category, CinderflaskItem from,
                       CinderflaskItem to, Ingredient firstCost,
                       Ingredient secondCost, RecipeSerializer<?> serializer) {
            super(id, category);
            this.from = from;
            this.to = to;
            this.firstCost = firstCost;
            this.secondCost = secondCost;
            this.serializer = serializer;
        }

        @Override
        public boolean matches(RecipeInputInventory inventory, World world) {
            return findFlask(inventory, from) != null
                    && count(inventory, firstCost::test) == 1
                    && count(inventory, secondCost::test) == 1
                    && total(inventory) == 3;
        }

        /** The vessel that goes in, so the Almanac can chain the ladder from the recipes themselves. */
        public CinderflaskItem from() {
            return from;
        }

        /** And the one that comes out. */
        public CinderflaskItem to() {
            return to;
        }

        @Override
        public List<Ingredient> inputs() {
            return List.of(Ingredient.ofStacks(new ItemStack(from)), firstCost, secondCost);
        }

        @Override
        public ItemStack preview() {
            return new ItemStack(to);
        }

        @Override
        public String descriptionKey() {
            return "cinderflask.vessel.upgrade";
        }

        @Override
        public ItemStack craft(RecipeInputInventory inventory, DynamicRegistryManager registries) {
            ItemStack flask = findFlask(inventory, from);
            if (flask == null) {
                return ItemStack.EMPTY;
            }

            // The whole tag moves across, so the new vessel remembers everything the old one did.
            ItemStack upgraded = new ItemStack(to);
            if (flask.hasNbt()) {
                upgraded.setNbt(flask.getNbt().copy());
            }
            return upgraded;
        }

        @Override
        public boolean fits(int width, int height) {
            return width * height >= 3;
        }

        @Override
        public RecipeSerializer<?> getSerializer() {
            return serializer;
        }
    }

    /**
     * Solera. Pours a working brew into a sealed one: the vector and the age blend by dose, so a tired
     * old flask comes back up to strength without being reset to new. It is the only way to hold a
     * deep phase at full body, because decay outruns anything brewed fresh.
     */
    public static class Solera extends SpecialCraftingRecipe implements VesselOperation {
        public Solera(Identifier id, CraftingRecipeCategory category) {
            super(id, category);
        }

        @Override
        public List<Ingredient> inputs() {
            return List.of(anyVessel(), anyVessel());
        }

        @Override
        public ItemStack preview() {
            return new ItemStack(Cinderflask.CINDERFLASK);
        }

        @Override
        public String descriptionKey() {
            return "cinderflask.vessel.solera";
        }

        @Override
        public boolean matches(RecipeInputInventory inventory, World world) {
            return sealed(inventory, world) != null && working(inventory, world) != null
                    && total(inventory) == 2;
        }

        @Override
        public ItemStack craft(RecipeInputInventory inventory, DynamicRegistryManager registries) {
            ItemStack oldStack = sealed(inventory, null);
            ItemStack freshStack = working(inventory, null);

            if (oldStack == null || freshStack == null) {
                return ItemStack.EMPTY;
            }

            Brew held = BrewNbt.read(oldStack, null);
            Brew fresh = BrewNbt.read(freshStack, null);

            if (held == null || fresh == null) {
                return ItemStack.EMPTY;
            }

            ItemStack result = oldStack.copy();
            Brew blended = held.toppedUp(fresh, fresh.doses());

            // Age is blended, not restarted. A bench has no world to write a seal time against, so
            // the blended phase is parked on the flask and the clock is restamped on the next tick.
            BrewNbt.store(result, blended, held.doses() + fresh.doses());
            BrewNbt.setCarriedPhase(result, blended.phase());
            return result;
        }

        @Override
        public boolean fits(int width, int height) {
            return width * height >= 2;
        }

        @Override
        public RecipeSerializer<?> getSerializer() {
            return Cinderflask.SOLERA_RECIPE;
        }

        private static ItemStack sealed(RecipeInputInventory inventory, World world) {
            return findFlaskIn(inventory, stack -> BrewNbt.isCorked(stack)
                    && BrewState.of(stack, world) == BrewState.SEALED);
        }

        private static ItemStack working(RecipeInputInventory inventory, World world) {
            return findFlaskIn(inventory, stack -> BrewNbt.hasBrew(stack) && !BrewNbt.isCorked(stack));
        }
    }

    /** Packs a cracked flask in sand, ready for the fire that mends it. */
    public static class Sinter extends SpecialCraftingRecipe implements VesselOperation {
        public Sinter(Identifier id, CraftingRecipeCategory category) {
            super(id, category);
        }

        @Override
        public List<Ingredient> inputs() {
            return List.of(anyVessel(), Ingredient.ofItems(Items.SAND));
        }

        @Override
        public ItemStack preview() {
            return new ItemStack(Cinderflask.SINTER);
        }

        @Override
        public String descriptionKey() {
            return "cinderflask.vessel.sinter";
        }

        @Override
        public boolean matches(RecipeInputInventory inventory, World world) {
            return findFlaskIn(inventory, Cracking::isCracked) != null
                    && count(inventory, stack -> stack.isOf(Items.SAND)) == 1
                    && total(inventory) == 2;
        }

        @Override
        public ItemStack craft(RecipeInputInventory inventory, DynamicRegistryManager registries) {
            ItemStack flask = findFlaskIn(inventory, Cracking::isCracked);
            if (flask == null) {
                return ItemStack.EMPTY;
            }

            return dev.cinderflask.item.SinterItem.pack(flask);
        }

        @Override
        public boolean fits(int width, int height) {
            return width * height >= 2;
        }

        @Override
        public RecipeSerializer<?> getSerializer() {
            return Cinderflask.SINTER_RECIPE;
        }
    }

    // -------------------------------------------------------------------------------------------
    // Shared bench inspection
    // -------------------------------------------------------------------------------------------

    static ItemStack findFlask(RecipeInputInventory inventory, CinderflaskItem type) {
        return findFlaskIn(inventory, stack -> stack.isOf(type));
    }

    static ItemStack findFlaskIn(RecipeInputInventory inventory, Predicate<ItemStack> test) {
        ItemStack found = null;

        for (int slot = 0; slot < inventory.size(); slot++) {
            ItemStack stack = inventory.getStack(slot);
            if (!(stack.getItem() instanceof CinderflaskItem)) {
                continue;
            }

            // More than one flask on the bench is ambiguous, so it is simply not a recipe.
            if (found != null || !test.test(stack)) {
                return null;
            }
            found = stack;
        }

        return found;
    }

    static int count(RecipeInputInventory inventory, Predicate<ItemStack> test) {
        int found = 0;
        for (int slot = 0; slot < inventory.size(); slot++) {
            if (test.test(inventory.getStack(slot))) {
                found++;
            }
        }
        return found;
    }

    static int total(RecipeInputInventory inventory) {
        int found = 0;
        for (int slot = 0; slot < inventory.size(); slot++) {
            if (!inventory.getStack(slot).isEmpty()) {
                found++;
            }
        }
        return found;
    }

    /** Every vessel, so a page about flasks in general shows all four rather than picking one. */
    static Ingredient anyVessel() {
        ItemStack[] vessels = new ItemStack[Cinderflask.vessels().length];
        for (int i = 0; i < vessels.length; i++) {
            vessels[i] = new ItemStack(Cinderflask.vessels()[i]);
        }
        return Ingredient.ofStacks(vessels);
    }
}
