package dev.cinderflask.item;

import dev.cinderflask.Cinderflask;
import dev.cinderflask.brew.Brew;
import dev.cinderflask.brew.BrewState;
import dev.cinderflask.brew.Brewing;
import dev.cinderflask.brew.Cracking;
import dev.cinderflask.brew.Dregs;
import dev.cinderflask.brew.IngredientTable;
import dev.cinderflask.brew.BrewEffects;
import dev.cinderflask.brew.BrewNbt;
import dev.cinderflask.brew.Humours;
import dev.cinderflask.brew.Temper;
import dev.cinderflask.brew.Tempering;
import dev.cinderflask.brew.Vessel;
import dev.cinderflask.brew.Readout;
import dev.cinderflask.config.CinderflaskConfig;
import dev.cinderflask.player.Palate;
import dev.cinderflask.player.PalateState;
import dev.cinderflask.player.PalateSync;
import net.minecraft.server.network.ServerPlayerEntity;
import dev.cinderflask.screen.CinderflaskScreenHandlerFactory;
import net.minecraft.client.item.TooltipContext;
import net.minecraft.entity.LivingEntity;
import net.minecraft.item.ItemUsageContext;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.registry.Registries;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Identifier;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.fluid.Fluids;
import net.minecraft.item.Items;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.MutableText;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.Hand;
import net.minecraft.util.TypedActionResult;
import net.minecraft.util.UseAction;
import net.minecraft.world.World;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.function.BooleanSupplier;

/**
 * The flask. Holds a sealed brew and gives you one dose at a time.
 *
 * <p>Nothing about the brew's state is stored except what went in, when it was sealed and what vessel
 * it is in. Everything read here is worked out from those on the spot, so a flask ages correctly
 * whether it is in your hand or forgotten in a chest, without anything having to tick.
 */
public class CinderflaskItem extends Item {
    /** Fast enough to drink mid-fight. */
    public static final int SIP_TICKS = 12;

    /** How much of this vessel body ingredients may fill. The upgrade path is more of this. */
    private final float ceiling;

    /** Reach the vessel lends on its own. Only the Aetherglass has any. */
    private final float innateQuintessence;

    /**
     * Set to {@code Screen::hasShiftDown} by the client entrypoint. Stays false on a dedicated
     * server, which has no client classes on the classpath.
     */
    public static BooleanSupplier detailModifierHeld = () -> false;

    public CinderflaskItem(Settings settings, float ceiling, float innateQuintessence) {
        super(settings);
        this.ceiling = ceiling;
        this.innateQuintessence = innateQuintessence;
    }

    public float ceiling() {
        return ceiling;
    }

    public float innateQuintessence() {
        return innateQuintessence;
    }

    // -------------------------------------------------------------------------------------------
    // Drinking
    // -------------------------------------------------------------------------------------------

    @Override
    public UseAction getUseAction(ItemStack stack) {
        return UseAction.DRINK;
    }

    @Override
    public int getMaxUseTime(ItemStack stack) {
        return SIP_TICKS;
    }

    @Override
    public TypedActionResult<ItemStack> use(World world, PlayerEntity player, Hand hand) {
        ItemStack stack = player.getStackInHand(hand);

        // Sipping is the common action so it gets the plain click; sneaking opens the intake.
        if (player.isSneaking()) {
            if (!world.isClient) {
                player.openHandledScreen(new CinderflaskScreenHandlerFactory(hand));
            }
            return TypedActionResult.success(stack, world.isClient);
        }

        BrewState state = BrewState.of(stack, world);

        // A ruined brew is poured out, or rinsed away at water. Either way the vessel survives with
        // its mote and its seasoning: those belong to the flask, not to what was in it.
        if (state == BrewState.RUINED) {
            return discard(stack, world, player);
        }

        // An empty flask held at water fills itself. Water is a base like any other, so the entry
        // comes out of the same datapack table rather than being special-cased here.
        if (state.acceptsBase() && fillFromWater(stack, world, player)) {
            return TypedActionResult.success(stack, world.isClient);
        }

        if (!state.canDrink()) {
            if (!world.isClient) {
                player.sendMessage(Text.translatable(state == BrewState.WORKING
                        ? "cinderflask.message.uncorked"
                        : "cinderflask.message.empty").formatted(Formatting.GRAY), true);
            }
            return TypedActionResult.fail(stack);
        }

        player.setCurrentHand(hand);
        return TypedActionResult.consume(stack);
    }

    private boolean fillFromWater(ItemStack stack, World world, PlayerEntity player) {
        if (!lookingAtWater(world, player)) {
            return false;
        }

        IngredientTable.Entry water = IngredientTable.lookup(new ItemStack(Items.WATER_BUCKET));
        if (water == null || !water.base()) {
            return false;
        }

        if (!world.isClient) {
            Brewing.addBase(stack, water, ceiling);
            world.playSound(null, player.getX(), player.getY(), player.getZ(),
                    SoundEvents.ITEM_BOTTLE_FILL, SoundCategory.PLAYERS, 0.8F, 1.0F);
        }

        return true;
    }

    /** Pours a ruined brew into bottles, or rinses it away if you are standing at water. */
    private TypedActionResult<ItemStack> discard(ItemStack stack, World world, PlayerEntity player) {
        boolean atWater = lookingAtWater(world, player);
        int doses = BrewNbt.doses(stack);

        if (world.isClient) {
            return TypedActionResult.success(stack, true);
        }

        BrewNbt.empty(stack);

        if (atWater) {
            world.playSound(null, player.getX(), player.getY(), player.getZ(),
                    SoundEvents.ITEM_BUCKET_EMPTY, SoundCategory.PLAYERS, 0.7F, 1.2F);
            player.sendMessage(Text.translatable("cinderflask.message.washed")
                    .formatted(Formatting.GRAY), true);
            return TypedActionResult.success(stack);
        }

        ItemStack sump = new ItemStack(Cinderflask.SUMP, Math.max(1, doses));
        if (!player.getInventory().insertStack(sump)) {
            player.dropItem(sump, false);
        }

        world.playSound(null, player.getX(), player.getY(), player.getZ(),
                SoundEvents.ITEM_BOTTLE_EMPTY, SoundCategory.PLAYERS, 0.7F, 0.7F);
        player.sendMessage(Text.translatable("cinderflask.message.poured")
                .formatted(Formatting.GRAY), true);

        return TypedActionResult.success(stack);
    }

    private boolean lookingAtWater(World world, PlayerEntity player) {
        BlockHitResult hit = raycast(world, player, net.minecraft.world.RaycastContext.FluidHandling.SOURCE_ONLY);
        return hit.getType() == HitResult.Type.BLOCK
                && world.getFluidState(hit.getBlockPos()).isOf(Fluids.WATER);
    }

    /**
     * Corking happens at a crafting bench, which has no world to read a time from, so the clock is
     * started the first time the flask is seen in one. Once, and never again.
     */
    @Override
    public void inventoryTick(ItemStack stack, World world, Entity holder, int slot, boolean selected) {
        if (world.isClient) {
            return;
        }

        BrewNbt.stampIfNeeded(stack, world);

        // A cracked flask does not wait to be drunk from.
        if (Cracking.isCracked(stack) && holder instanceof LivingEntity carrier
                && world.getTime() % Cracking.LEAK_INTERVAL == 0
                && BrewState.of(stack, world).canDrink()) {
            Cracking.leak(stack, (net.minecraft.server.world.ServerWorld) world, carrier);
        }
    }

    @Override
    public ItemStack finishUsing(ItemStack stack, World world, LivingEntity drinker) {
        if (world.isClient) {
            return stack;
        }

        Brew brew = BrewNbt.read(stack, world);
        int doses = BrewNbt.doses(stack);

        if (brew == null || doses <= 0) {
            return stack;
        }

        BrewEffects.apply(drinker, brew);

        int remaining = doses - 1;
        BrewNbt.setDoses(stack, remaining);

        if (remaining <= 0) {
            // The last dose leaves dregs, which is what carries a little of this brew into the next.
            ItemStack dregs = Dregs.from(brew);
            BrewNbt.empty(stack);

            if (!dregs.isEmpty() && drinker instanceof PlayerEntity holder
                    && !holder.getInventory().insertStack(dregs)) {
                holder.dropItem(dregs, false);
            }
        }

        if (drinker instanceof PlayerEntity player) {
            player.getItemCooldownManager().set(this, CinderflaskConfig.get().sipCooldownTicks);
        }

        if (drinker instanceof ServerPlayerEntity player) {
            // Tasting is how the palate grows, credited by each humour's share of the dose.
            Palate learned = PalateState.get(player.server).record(player, brew.current());
            PalateSync.send(player, learned);
        }

        world.playSound(null, drinker.getX(), drinker.getY(), drinker.getZ(),
                SoundEvents.ENTITY_GENERIC_DRINK, SoundCategory.PLAYERS, 0.6F, 1.1F);

        return stack;
    }

    /**
     * Takes an impression of a living thing. The creature is unharmed; the cost is that a flask holds
     * one mote for good, so the choice is permanent.
     */
    @Override
    public ActionResult useOnEntity(ItemStack stack, PlayerEntity player, LivingEntity entity, Hand hand) {
        if (Vessel.hasMote(stack)) {
            return ActionResult.PASS;
        }

        World world = player.getWorld();
        if (world.isClient) {
            return ActionResult.SUCCESS;
        }

        if (!Vessel.catchMote(stack, entity)) {
            return ActionResult.PASS;
        }

        ((ServerWorld) world).spawnParticles(ParticleTypes.END_ROD,
                entity.getX(), entity.getBodyY(0.6), entity.getZ(), 12, 0.2, 0.3, 0.2, 0.01);
        world.playSound(null, entity.getX(), entity.getY(), entity.getZ(),
                SoundEvents.BLOCK_AMETHYST_BLOCK_CHIME, SoundCategory.PLAYERS, 0.7F, 1.4F);

        return ActionResult.CONSUME;
    }

    /** Fires the flask against a heat or chill source, which sets its temper for good. */
    @Override
    public ActionResult useOnBlock(ItemUsageContext context) {
        ItemStack stack = context.getStack();
        World world = context.getWorld();

        Temper temper = Tempering.of(world.getBlockState(context.getBlockPos()));
        if (temper == null || BrewNbt.temper(stack) != Temper.UNTEMPERED) {
            return ActionResult.PASS;
        }

        if (world.isClient) {
            return ActionResult.SUCCESS;
        }

        BrewNbt.setTemper(stack, temper);

        ((ServerWorld) world).spawnParticles(ParticleTypes.SOUL_FIRE_FLAME,
                context.getHitPos().x, context.getHitPos().y, context.getHitPos().z,
                16, 0.2, 0.2, 0.2, 0.01);
        world.playSound(null, context.getBlockPos(),
                SoundEvents.BLOCK_LAVA_EXTINGUISH, SoundCategory.PLAYERS, 0.6F, 1.6F);

        return ActionResult.CONSUME;
    }

    // -------------------------------------------------------------------------------------------
    // Reading the flask
    // -------------------------------------------------------------------------------------------

    /** Packed RGB for the liquid layer, soured towards the murk by corruption. */
    public static int colourOf(ItemStack stack, @Nullable World world) {
        Brew brew = BrewNbt.read(stack, world);
        return brew == null
                ? 0xFFFFFF
                : Humours.soured(brew.current().colour(), brew.corruption());
    }

    /** How full the flask reads, from 0 to 1. Drives the fill model. */
    public static float fillOf(ItemStack stack) {
        int doses = BrewNbt.doses(stack);
        return doses <= 0 ? 0 : Math.min(1, doses / 12f);
    }

    @Override
    public Text getName(ItemStack stack) {
        MutableText earned = dev.cinderflask.brew.VesselName.of(stack);
        return earned == null ? super.getName(stack) : earned;
    }

    @Override
    public void appendTooltip(ItemStack stack, @Nullable World world, List<Text> tooltip, TooltipContext context) {
        if (Cracking.isCracked(stack)) {
            tooltip.add(Text.translatable("cinderflask.tooltip.cracked").formatted(Formatting.RED));
        }

        Temper temper = BrewNbt.temper(stack);
        tooltip.add(Text.translatable(temper == Temper.UNTEMPERED
                ? "cinderflask.tooltip.untempered"
                : temper.translationKey()).formatted(Formatting.DARK_AQUA));

        Identifier origin = Vessel.moteOrigin(stack);
        if (origin == null) {
            tooltip.add(Text.translatable("cinderflask.tooltip.no_mote").formatted(Formatting.DARK_GRAY));
        } else {
            tooltip.add(Text.translatable("cinderflask.tooltip.mote",
                    Registries.ENTITY_TYPE.get(origin).getName(),
                    Vessel.brewCount(stack)).formatted(Formatting.DARK_GRAY));
        }

        Brew brew = BrewNbt.read(stack, world);
        if (brew == null) {
            tooltip.add(Text.translatable("cinderflask.tooltip.empty").formatted(Formatting.GRAY));
            return;
        }

        tooltip.add(Text.translatable("cinderflask.tooltip.doses", BrewNbt.doses(stack))
                .formatted(Formatting.GOLD));

        if (brew.isSpoiled()) {
            tooltip.add(Text.translatable("cinderflask.tooltip.ruined").formatted(Formatting.DARK_GRAY));
            return;
        }

        tooltip.addAll(Readout.describe(brew, PalateSync.local()));

        if (detailModifierHeld.getAsBoolean()) {
            tooltip.add(Text.translatable("cinderflask.tooltip.age",
                    Readout.fmt(brew.phase()), Readout.fmt(brew.corruption()))
                    .formatted(Formatting.DARK_GRAY));
        }
    }
}
