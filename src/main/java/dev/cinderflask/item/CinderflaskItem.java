package dev.cinderflask.item;

import dev.cinderflask.config.CinderflaskConfig;
import dev.cinderflask.screen.CinderflaskScreenHandlerFactory;
import dev.cinderflask.tag.CinderflaskTags;
import net.fabricmc.fabric.api.item.v1.FabricItem;
import net.minecraft.client.item.TooltipContext;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtElement;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.Hand;
import net.minecraft.util.TypedActionResult;
import net.minecraft.util.math.MathHelper;
import net.minecraft.world.World;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.function.BooleanSupplier;

/**
 * Stores burn ticks and gives a furnace one operation at a time.
 *
 * <p>{@link #getRecipeRemainder(ItemStack)} is what does it: the furnace consumes the flask as fuel
 * and Fabric API's stack-aware remainder puts a copy back in the slot, minus one operation.
 * {@link dev.cinderflask.mixin.AbstractFurnaceBlockEntityMixin} caps what it charges.
 */
public class CinderflaskItem extends Item implements FabricItem {
    public static final String EMBERS_KEY = "Embers";

    /**
     * Set to {@code Screen::hasShiftDown} by the client entrypoint. Stays false on a dedicated
     * server, which has no client classes on the classpath.
     */
    public static BooleanSupplier detailModifierHeld = () -> false;

    public CinderflaskItem(Settings settings) {
        super(settings);
    }

    public static int getEmbers(ItemStack stack) {
        NbtCompound nbt = stack.getNbt();
        if (nbt == null || !nbt.contains(EMBERS_KEY, NbtElement.INT_TYPE)) {
            return 0;
        }
        return Math.max(0, nbt.getInt(EMBERS_KEY));
    }

    public static void setEmbers(ItemStack stack, int embers) {
        int clamped = MathHelper.clamp(embers, 0, CinderflaskConfig.get().maxEmbers);

        if (clamped == 0) {
            // Drop the key so a spent flask stacks with a fresh one.
            NbtCompound nbt = stack.getNbt();
            if (nbt != null) {
                nbt.remove(EMBERS_KEY);
                if (nbt.isEmpty()) {
                    stack.setNbt(null);
                }
            }
        } else {
            stack.getOrCreateNbt().putInt(EMBERS_KEY, clamped);
        }
    }

    /** How many furnace operations the flask can still pay for. */
    public static int operationsRemaining(ItemStack stack) {
        return getEmbers(stack) / CinderflaskConfig.get().ticksPerOperation;
    }

    /** Takes as much of {@code fuel} as fits under the cap. Mutates both stacks. */
    public static void addFuel(ItemStack flask, ItemStack fuel) {
        int perItem = FuelTimes.of(fuel);
        if (perItem <= 0) {
            return;
        }

        int max = CinderflaskConfig.get().maxEmbers;
        int current = getEmbers(flask);
        int added = 0;

        while (!fuel.isEmpty() && current + added + perItem <= max) {
            added += perItem;
            fuel.decrement(1);
        }

        if (added > 0) {
            setEmbers(flask, current + added);
        }
    }

    /** Spends one operation's worth of embers. Returns false if the flask cannot cover one. */
    public static boolean consumeOne(ItemStack flask) {
        int perOperation = CinderflaskConfig.get().ticksPerOperation;
        int current = getEmbers(flask);

        if (current < perOperation) {
            return false;
        }

        setEmbers(flask, current - perOperation);
        return true;
    }

    /**
     * Furnace fuel, but not another flask, not anything that would hand back a container
     * (buckets, bottles), and not anything in the deny tag.
     */
    public static boolean isValidFuel(ItemStack stack) {
        return !(stack.getItem() instanceof CinderflaskItem)
                && FuelTimes.of(stack) > 0
                && stack.getRecipeRemainder().isEmpty()
                && !stack.isIn(CinderflaskTags.EMBER_DENY);
    }

    @Override
    public ItemStack getRecipeRemainder(ItemStack stack) {
        ItemStack remainder = stack.copy();
        consumeOne(remainder);
        return remainder;
    }

    @Override
    public TypedActionResult<ItemStack> use(World world, PlayerEntity player, Hand hand) {
        ItemStack stack = player.getStackInHand(hand);

        if (world.isClient) {
            return TypedActionResult.success(stack);
        }

        if (player.isSneaking() && CinderflaskConfig.get().enableShiftFill) {
            if (drainInventory(player, stack) <= 0) {
                player.sendMessage(Text.translatable("cinderflask.message.nothing_to_burn")
                        .formatted(Formatting.GRAY), true);
                return TypedActionResult.fail(stack);
            }

            world.playSound(null, player.getX(), player.getY(), player.getZ(),
                    SoundEvents.ITEM_FIRECHARGE_USE, SoundCategory.PLAYERS, 0.5F, 1.6F);
            return TypedActionResult.success(stack);
        }

        player.openHandledScreen(new CinderflaskScreenHandlerFactory(hand));
        return TypedActionResult.success(stack);
    }

    /** Empties every valid fuel out of the player's inventory. Returns the embers gained. */
    private static int drainInventory(PlayerEntity player, ItemStack flask) {
        PlayerInventory inventory = player.getInventory();
        int before = getEmbers(flask);

        for (int slot = 0; slot < inventory.size(); slot++) {
            ItemStack candidate = inventory.getStack(slot);

            if (candidate != flask && isValidFuel(candidate)) {
                addFuel(flask, candidate);

                if (candidate.isEmpty()) {
                    inventory.setStack(slot, ItemStack.EMPTY);
                }
            }
        }

        return getEmbers(flask) - before;
    }

    @Override
    public void appendTooltip(ItemStack stack, @Nullable World world, List<Text> tooltip, TooltipContext context) {
        int embers = getEmbers(stack);

        if (detailModifierHeld.getAsBoolean()) {
            tooltip.add(Text.translatable("cinderflask.tooltip.embers_ticks", format(embers))
                    .formatted(Formatting.GOLD));
        } else {
            tooltip.add(Text.translatable("cinderflask.tooltip.embers", format(operationsRemaining(stack)))
                    .formatted(Formatting.GOLD));
        }

        tooltip.add(Text.translatable(embers > 0
                ? "cinderflask.tooltip.lore.lit"
                : "cinderflask.tooltip.lore.unlit").formatted(Formatting.GRAY));
    }

    /** Thousands separators; ember counts run to seven digits. */
    public static String format(int value) {
        return String.format("%,d", value);
    }
}
