package dev.cinderflask.brew;

import net.minecraft.entity.AreaEffectCloudEntity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.mob.HostileEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.math.Box;
import org.jetbrains.annotations.Nullable;

import java.util.EnumSet;
import java.util.List;

/**
 * Who a dose lands on.
 *
 * <p>Reach is a property of delivery, not of the effect. A {@code StatusEffectInstance} carries only
 * an effect, a duration and an amplifier — there is nowhere on it to write "this brew had reach
 * twelve" — so quintessence decides who gets dosed rather than trying to change what they get.
 *
 * <p>Low reach is you. Middling reach is you and whoever is standing with you. High reach is a cloud
 * that catches whoever walks into it, which is what makes a corrupt reaching brew area denial rather
 * than a mistake.
 */
public final class Delivery {
    /** Below this, a brew stays in the drinker. */
    private static final float SELF = 2;

    /** Above this, it stops being a burst and starts being weather. */
    private static final float CLOUD = 12;

    private static final float BLOCKS_PER_REACH = 0.6f;

    /** A cloud lasts a share of what the dose itself would have, so reach is not free duration. */
    private static final float CLOUD_DURATION = 0.5f;

    private Delivery() {
    }

    /** What shape a given amount of reach makes. Public so the Almanac and tests can say so too. */
    public enum Shape {
        DRINKER, BURST, CLOUD_SHAPE
    }

    public static Shape shapeOf(float reach) {
        if (reach < SELF) {
            return Shape.DRINKER;
        }
        return reach < CLOUD ? Shape.BURST : Shape.CLOUD_SHAPE;
    }

    public static float radiusOf(float reach) {
        return reach * BLOCKS_PER_REACH;
    }

    /**
     * Serves one dose. The flask is read for the vessel and mote inflections and is otherwise
     * untouched — spending the dose is the caller's business.
     */
    public static void serve(ServerWorld world, LivingEntity drinker, @Nullable ItemStack flask,
                             Brew brew) {
        List<StatusEffectInstance> dose = BrewEffects.of(flask, brew);
        if (dose.isEmpty()) {
            return;
        }

        float reach = brew.current().quintessence();
        EnumSet<Inflection> crossed = Inflection.of(flask, brew);
        int colour = Humours.soured(brew.current().colour(), brew.corruption());

        give(drinker, dose);

        switch (shapeOf(reach)) {
            case DRINKER -> spectacle(world, drinker, crossed, colour, 0);
            case BURST -> {
                float radius = radiusOf(reach);
                Box near = drinker.getBoundingBox().expand(radius);

                for (LivingEntity caught : world.getEntitiesByClass(LivingEntity.class, near,
                        other -> other != drinker && other.isAlive()
                                && !(other instanceof HostileEntity))) {
                    give(caught, dose);
                }

                spectacle(world, drinker, crossed, colour, radius);
            }
            case CLOUD_SHAPE -> {
                linger(world, drinker, dose, radiusOf(reach), colour);
                spectacle(world, drinker, crossed, colour, radiusOf(reach));
            }
        }
    }

    /** Each drinker gets its own instances; a shared one would tick down on both at once. */
    private static void give(LivingEntity target, List<StatusEffectInstance> dose) {
        for (StatusEffectInstance effect : dose) {
            target.addStatusEffect(new StatusEffectInstance(effect));
        }
    }

    private static void linger(ServerWorld world, LivingEntity drinker,
                               List<StatusEffectInstance> dose, float radius, int colour) {
        AreaEffectCloudEntity cloud = new AreaEffectCloudEntity(
                world, drinker.getX(), drinker.getY(), drinker.getZ());

        cloud.setOwner(drinker);
        cloud.setRadius(radius);
        cloud.setColor(colour);
        cloud.setWaitTime(10);
        cloud.setRadiusOnUse(-0.5F);
        cloud.setRadiusGrowth(-radius / cloud.getDuration());

        int longest = 0;
        for (StatusEffectInstance effect : dose) {
            cloud.addEffect(new StatusEffectInstance(effect));
            longest = Math.max(longest, effect.getDuration());
        }

        cloud.setDuration(Math.max(40, Math.round(longest * CLOUD_DURATION)));
        world.spawnEntity(cloud);
    }

    /**
     * What it looks like. Scales with how many inflections the brew crossed, and is thrown outward
     * to the radius it actually covered, so the animation says who it just hit.
     */
    private static void spectacle(ServerWorld world, LivingEntity drinker,
                                  EnumSet<Inflection> crossed, int colour, float radius) {
        int marks = crossed.size();
        if (marks < 2) {
            return;
        }

        // ENTITY_EFFECT takes its colour through the velocity arguments, which is the trick vanilla
        // potions use. Count zero is what switches that on.
        double red = ((colour >> 16) & 0xFF) / 255.0;
        double green = ((colour >> 8) & 0xFF) / 255.0;
        double blue = (colour & 0xFF) / 255.0;

        int puffs = 12 * marks;
        double spread = Math.max(0.6, radius);

        for (int i = 0; i < puffs; i++) {
            double angle = world.getRandom().nextDouble() * Math.PI * 2;
            double distance = world.getRandom().nextDouble() * spread;

            world.spawnParticles(ParticleTypes.ENTITY_EFFECT,
                    drinker.getX() + Math.cos(angle) * distance,
                    drinker.getY() + 0.4 + world.getRandom().nextDouble() * 1.2,
                    drinker.getZ() + Math.sin(angle) * distance,
                    0, red, green, blue, 1);
        }

        if (Inflection.capstoned(crossed)) {
            world.spawnParticles(ParticleTypes.END_ROD, drinker.getX(), drinker.getY() + 1,
                    drinker.getZ(), 24, 0.3, 0.6, 0.3, 0.04);
        }

        world.playSound(null, drinker.getBlockPos(),
                Inflection.capstoned(crossed)
                        ? SoundEvents.BLOCK_BEACON_ACTIVATE
                        : SoundEvents.BLOCK_AMETHYST_BLOCK_CHIME,
                SoundCategory.PLAYERS, 0.7F, 0.8F + marks * 0.08F);
    }
}
