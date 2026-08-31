package dev.cinderflask.brew;

import dev.cinderflask.item.CinderflaskItem;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.world.World;

/**
 * Glass breaks.
 *
 * <p>A heavy hit while you are carrying something volatile cracks the flask. What leaks out afterwards
 * needs no separate rule: a crack breathes off the hot humours first, so a cracked flask drifts
 * colder and slower as it empties, and by the end it is giving you long weak doses of something quite
 * unlike what you brewed.
 *
 * <p>Cracked is a state, not a death sentence. Some people keep one on purpose.
 */
public final class Cracking {
    private static final String CRACKED = "Cracked";

    /** Below this, a brew is too well-buttressed to shatter whatever you take. */
    private static final float VOLATILITY_THRESHOLD = 3.0f;

    /** Damage below this never breaks anything. */
    private static final float DAMAGE_THRESHOLD = 6.0f;

    /** Ticks between one leak and the next. */
    public static final int LEAK_INTERVAL = 200;

    /** How much of what is left escapes each time, before the volatility weighting. */
    private static final float LEAK_FRACTION = 0.12f;

    private Cracking() {
    }

    public static boolean isCracked(ItemStack flask) {
        NbtCompound nbt = flask.getNbt();
        return nbt != null && nbt.getBoolean(CRACKED);
    }

    public static void crack(ItemStack flask) {
        flask.getOrCreateNbt().putBoolean(CRACKED, true);
    }

    public static void mend(ItemStack flask) {
        NbtCompound nbt = flask.getNbt();
        if (nbt != null) {
            nbt.remove(CRACKED);
        }
    }

    public static void register() {
        ServerLivingEntityEvents.ALLOW_DAMAGE.register((entity, source, amount) -> {
            if (amount >= DAMAGE_THRESHOLD && entity instanceof PlayerEntity player) {
                crackSomethingVolatile(player, amount);
            }
            return true;
        });
    }

    /** Breaks at most one flask per hit, the most volatile thing being carried. */
    private static void crackSomethingVolatile(PlayerEntity player, float damage) {
        PlayerInventory inventory = player.getInventory();
        World world = player.getWorld();

        ItemStack worst = null;
        float worstVolatility = VOLATILITY_THRESHOLD;

        for (int slot = 0; slot < inventory.size(); slot++) {
            ItemStack stack = inventory.getStack(slot);

            if (!(stack.getItem() instanceof CinderflaskItem) || isCracked(stack)
                    || BrewState.of(stack, world) != BrewState.SEALED) {
                continue;
            }

            Brew brew = BrewNbt.read(stack, world);
            if (brew == null) {
                continue;
            }

            float volatility = brew.current().volatility();
            if (volatility > worstVolatility) {
                worst = stack;
                worstVolatility = volatility;
            }
        }

        if (worst == null) {
            return;
        }

        // Harder hits and hotter brews break more readily; a ballasted one mostly survives.
        float chance = Math.min(0.9f, (worstVolatility - VOLATILITY_THRESHOLD) / 8f
                * (damage / DAMAGE_THRESHOLD));

        if (world.getRandom().nextFloat() >= chance) {
            return;
        }

        crack(worst);
        world.playSound(null, player.getBlockPos(), SoundEvents.BLOCK_GLASS_BREAK,
                SoundCategory.PLAYERS, 0.8F, 1.4F);
        player.sendMessage(Text.translatable("cinderflask.message.cracked")
                .formatted(Formatting.RED), true);
    }

    /**
     * One leak. Applies a weakened dose to whoever is carrying it and breathes off the volatile part
     * of what is left.
     */
    public static void leak(ItemStack flask, ServerWorld world, net.minecraft.entity.LivingEntity holder) {
        Brew brew = BrewNbt.read(flask, world);
        int doses = BrewNbt.doses(flask);

        if (brew == null || doses <= 0) {
            return;
        }

        BrewEffects.apply(holder, brew);

        Humours remaining = brew.sealed().vented(LEAK_FRACTION);
        BrewNbt.store(flask, new Brew(remaining, brew.phase(), brew.addedCorruption(), brew.capacity()),
                doses - 1);

        if (doses - 1 <= 0) {
            BrewNbt.empty(flask);
        }

        world.spawnParticles(net.minecraft.particle.ParticleTypes.DRIPPING_HONEY,
                holder.getX(), holder.getY() + 0.4, holder.getZ(), 3, 0.2, 0.1, 0.2, 0);
    }

}
