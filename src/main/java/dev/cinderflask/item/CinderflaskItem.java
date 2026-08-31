package dev.cinderflask.item;

import dev.cinderflask.brew.Brew;
import dev.cinderflask.brew.BrewEffects;
import dev.cinderflask.brew.BrewNbt;
import dev.cinderflask.brew.Humours;
import dev.cinderflask.brew.Temper;
import dev.cinderflask.brew.Tempering;
import dev.cinderflask.brew.Vessel;
import dev.cinderflask.config.CinderflaskConfig;
import dev.cinderflask.screen.CinderflaskScreenHandlerFactory;
import net.minecraft.client.item.TooltipContext;
import net.minecraft.entity.LivingEntity;
import net.minecraft.item.ItemUsageContext;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.registry.Registries;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Identifier;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
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

    /**
     * Set to {@code Screen::hasShiftDown} by the client entrypoint. Stays false on a dedicated
     * server, which has no client classes on the classpath.
     */
    public static BooleanSupplier detailModifierHeld = () -> false;

    public CinderflaskItem(Settings settings, float ceiling) {
        super(settings);
        this.ceiling = ceiling;
    }

    public float ceiling() {
        return ceiling;
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

        if (BrewNbt.doses(stack) <= 0) {
            if (!world.isClient) {
                player.sendMessage(Text.translatable("cinderflask.message.empty")
                        .formatted(Formatting.GRAY), true);
            }
            return TypedActionResult.fail(stack);
        }

        player.setCurrentHand(hand);
        return TypedActionResult.consume(stack);
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
            BrewNbt.empty(stack);
        }

        if (drinker instanceof PlayerEntity player) {
            player.getItemCooldownManager().set(this, CinderflaskConfig.get().sipCooldownTicks);
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
    public void appendTooltip(ItemStack stack, @Nullable World world, List<Text> tooltip, TooltipContext context) {
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

        // Phase 7 gates this behind the Palate. Until then the numbers are simply shown on shift.
        if (detailModifierHeld.getAsBoolean()) {
            Humours now = brew.current();
            tooltip.add(Text.translatable("cinderflask.tooltip.humours",
                    fmt(now.choleric()), fmt(now.melancholic()),
                    fmt(now.sanguine()), fmt(now.phlegmatic())).formatted(Formatting.GRAY));
            tooltip.add(Text.translatable("cinderflask.tooltip.reach",
                    fmt(now.quintessence())).formatted(Formatting.GRAY));
            tooltip.add(Text.translatable("cinderflask.tooltip.age",
                    fmt(brew.phase()), fmt(brew.corruption())).formatted(Formatting.DARK_GRAY));
        } else {
            tooltip.add(Text.translatable("cinderflask.tooltip.strength",
                    brew.amplifier() + 1, fmt(brew.durationTicks() / 20f)).formatted(Formatting.GRAY));
        }
    }

    private static String fmt(float value) {
        return String.format("%.1f", value);
    }
}
