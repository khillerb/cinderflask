package dev.cinderflask.effect;

import dev.cinderflask.Cinderflask;
import dev.cinderflask.brew.Humours;
import dev.cinderflask.brew.Landmarks;
import dev.cinderflask.config.CinderflaskConfig;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.attribute.EntityAttributeModifier;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.entity.mob.HostileEntity;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.registry.tag.DamageTypeTags;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.Box;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * The other half of the wheel.
 *
 * <p>Twelve twins, one to a landmark. A brew that has crossed {@code FOUL} draws from here instead of
 * from {@link Draughts} — all of it, not some of it, so corruption is a place you end up rather than
 * a penalty you accrue.
 *
 * <p>Every one is the same role turned malignant: the thing the clean draught did, still done, and
 * now taking something back. They wear their landmark's colour dragged towards the murk, so a twin
 * reads as its counterpart gone bad rather than as a separate effect.
 */
public final class CorruptDraughts {
    /** How far towards the murk a twin's colour sits. Matches the rebounds, for the same reason. */
    private static final float SOURED = 0.5f;

    /** What the corrupt version is worth against the clean one. Power, at a price. */
    private static final float GREED = 1.6f;

    private static final List<DraughtEffect> ALL = new ArrayList<>(Landmarks.all().size());
    private static final Map<Identifier, DraughtEffect> BY_LANDMARK = new HashMap<>();

    private CorruptDraughts() {
    }

    private static CinderflaskConfig.Tuning tuning() {
        return CinderflaskConfig.get().draughts;
    }

    public static final DraughtEffect DEADMANS_HUNGER = new DeadmansHunger();
    public static final DraughtEffect IRONROT = new Ironrot();
    public static final DraughtEffect SAPLEECH = new Sapleech();
    public static final DraughtEffect STRANGLEHOLD = new Stranglehold();
    public static final DraughtEffect WRACKTHORN = new Wrackthorn();
    public static final DraughtEffect GRAVEDELVE = new Gravedelve();
    public static final DraughtEffect DROWNED = new Drowned();
    public static final DraughtEffect SPRINTWRACK = new Sprintwrack();
    public static final DraughtEffect PYRE = new Pyre();
    public static final DraughtEffect REPRISAL = new Reprisal();
    public static final DraughtEffect CLOYING = new Cloying();
    public static final DraughtEffect GRAVE_CALLED = new GraveCalled();

    public static void register() {
        register(DEADMANS_HUNGER, IRONROT, SAPLEECH, STRANGLEHOLD,
                WRACKTHORN, GRAVEDELVE, DROWNED, SPRINTWRACK,
                PYRE, REPRISAL, CLOYING, GRAVE_CALLED);
    }

    private static void register(DraughtEffect... twins) {
        for (DraughtEffect twin : twins) {
            Identifier landmark = twin.landmark().id();
            Registry.register(Registries.STATUS_EFFECT, idOf(landmark), twin);
            ALL.add(twin);
            BY_LANDMARK.put(landmark, twin);
        }
    }

    /** Registered under the landmark it corrupts, prefixed, so the pairing is readable in a log. */
    public static Identifier idOf(Identifier landmark) {
        return Cinderflask.id("foul_" + landmark.getPath());
    }

    public static List<DraughtEffect> all() {
        return ALL;
    }

    @Nullable
    public static DraughtEffect of(Landmarks.Landmark landmark) {
        return BY_LANDMARK.get(landmark.id());
    }

    private static Landmarks.Landmark at(String path) {
        for (Landmarks.Landmark candidate : Landmarks.all()) {
            if (candidate.id().getPath().equals(path)) {
                return candidate;
            }
        }

        throw new IllegalStateException("No landmark called " + path);
    }

    private static int soured(Landmarks.Landmark landmark) {
        return Humours.soured(landmark.target().colour(), SOURED);
    }

    // -------------------------------------------------------------------------------------------
    // The twelve
    // -------------------------------------------------------------------------------------------

    /** Deadman's Vigour, but the strength is drawn out of you rather than found. */
    private static final class DeadmansHunger extends DraughtEffect
            implements CombatHooks.Striking, CombatHooks.Answering {
        private static final float CEILING = 3.0f;
        private static final float TITHE = 1.0f;

        private DeadmansHunger() {
            super(at("deadmans_draught"), soured(at("deadmans_draught")));
        }

        @Override
        public float striking(LivingEntity bearer, LivingEntity other, DamageSource source,
                              float amount, int amplifier) {
            float missing = 1 - bearer.getHealth() / Math.max(1, bearer.getMaxHealth());
            float scale = 1 + missing * tuning().dial(tuning().berserkFromMissingHealth)
                    * GREED * (amplifier + 1);
            return amount * Math.min(CEILING, scale);
        }

        @Override
        public void answer(LivingEntity bearer, @Nullable LivingEntity other, DamageSource source,
                           float amount, int amplifier) {
            // Every swing is paid for, which is what makes the scaling a spiral rather than a gift.
            if (other != null && source.getAttacker() == bearer && bearer.getHealth() > TITHE) {
                bearer.damage(bearer.getDamageSources().magic(), TITHE);
            }
        }
    }

    /** Ironroot, but the rot does not stop at your own skin. */
    private static final class Ironrot extends DraughtEffect implements CombatHooks.Enduring {
        private static final float FLOOR = 0.2f;
        private static final int INTERVAL = 40;
        private static final double REACH = 3;
        private static final float BITE = 1;

        private Ironrot() {
            super(at("ironroot_tonic"), soured(at("ironroot_tonic")));
            addAttributeModifier(EntityAttributes.GENERIC_KNOCKBACK_RESISTANCE,
                    "1d4f6c2a-8e30-4b71-9c58-2fa07d61e934", 0.3,
                    EntityAttributeModifier.Operation.ADDITION);
        }

        @Override
        public float enduring(LivingEntity bearer, @Nullable LivingEntity other, DamageSource source,
                              float amount, int amplifier) {
            float flat = tuning().dial(tuning().ironrootFlatReduction) * GREED * (amplifier + 1);
            return Math.max(amount * FLOOR, amount - flat);
        }

        @Override
        public boolean canApplyUpdateEffect(int duration, int amplifier) {
            return duration % INTERVAL == 0;
        }

        @Override
        public void applyUpdateEffect(LivingEntity entity, int amplifier) {
            if (entity.getWorld().isClient) {
                return;
            }

            Box near = entity.getBoundingBox().expand(REACH);
            for (LivingEntity caught : entity.getWorld().getEntitiesByClass(
                    LivingEntity.class, near, other -> other != entity && other.isAlive())) {
                caught.damage(entity.getDamageSources().magic(), BITE * (amplifier + 1));
            }
        }
    }

    /** Sapsworn, but it draws from whatever is near rather than from what you hit. */
    private static final class Sapleech extends DraughtEffect {
        private static final int INTERVAL = 30;
        private static final double REACH = 4;
        private static final float DRAW = 1;

        private Sapleech() {
            super(at("sap_sworn_mead"), soured(at("sap_sworn_mead")));
        }

        @Override
        public boolean canApplyUpdateEffect(int duration, int amplifier) {
            return duration % INTERVAL == 0;
        }

        @Override
        public void applyUpdateEffect(LivingEntity entity, int amplifier) {
            if (entity.getWorld().isClient) {
                return;
            }

            Box near = entity.getBoundingBox().expand(REACH);
            float drawn = 0;

            for (LivingEntity victim : entity.getWorld().getEntitiesByClass(
                    LivingEntity.class, near, other -> other != entity && other.isAlive())) {
                victim.damage(entity.getDamageSources().magic(), DRAW);
                drawn += DRAW;
            }

            entity.heal(drawn * tuning().dial(tuning().sapswornLifestealShare) * GREED
                    * (amplifier + 1));
        }
    }

    /** The Unseen Hand, but everything you are not facing has the same advantage over you. */
    private static final class Stranglehold extends DraughtEffect
            implements CombatHooks.Striking, CombatHooks.Enduring {
        private Stranglehold() {
            super(at("nightcap"), soured(at("nightcap")));
        }

        @Override
        public float striking(LivingEntity bearer, LivingEntity other, DamageSource source,
                              float amount, int amplifier) {
            if (!CombatHooks.isBehind(bearer, other)) {
                return amount;
            }

            return amount * (1 + tuning().dial(tuning().unseenHandBonus) * GREED * (amplifier + 1));
        }

        @Override
        public float enduring(LivingEntity bearer, @Nullable LivingEntity other, DamageSource source,
                              float amount, int amplifier) {
            if (other == null || !CombatHooks.isBehind(other, bearer)) {
                return amount;
            }

            return amount * (1 + tuning().dial(tuning().plainSightExposure) * GREED * (amplifier + 1));
        }
    }

    /** Bramblewine, but the thorns do not care who is standing there. */
    private static final class Wrackthorn extends DraughtEffect implements CombatHooks.Answering {
        private static final double REACH = 4;

        private Wrackthorn() {
            super(at("bramblewine"), soured(at("bramblewine")));
        }

        @Override
        public void answer(LivingEntity bearer, @Nullable LivingEntity other, DamageSource source,
                           float amount, int amplifier) {
            if (bearer.getWorld().isClient) {
                return;
            }

            float back = amount * tuning().dial(tuning().brambleReflectShare) * GREED * (amplifier + 1);
            Box near = bearer.getBoundingBox().expand(REACH);

            for (LivingEntity caught : bearer.getWorld().getEntitiesByClass(
                    LivingEntity.class, near, target -> target != bearer && target.isAlive())) {
                caught.damage(bearer.getDamageSources().thorns(bearer), back);
            }
        }
    }

    /** Deepdelve, but the ground that cannot hurt you will not let you go either. */
    private static final class Gravedelve extends DraughtEffect implements CombatHooks.Enduring {
        private Gravedelve() {
            super(at("deepdelve"), soured(at("deepdelve")));
            addAttributeModifier(EntityAttributes.GENERIC_MOVEMENT_SPEED,
                    "7c2e91b5-4a08-4d63-b1f7-3e0965c8a2d1", -0.15,
                    EntityAttributeModifier.Operation.MULTIPLY_TOTAL);
            addAttributeModifier(EntityAttributes.GENERIC_KNOCKBACK_RESISTANCE,
                    "b83d0f27-91c4-4e5a-8206-d7fa14e3b6c0", 0.6,
                    EntityAttributeModifier.Operation.ADDITION);
        }

        @Override
        public float enduring(LivingEntity bearer, @Nullable LivingEntity other, DamageSource source,
                              float amount, int amplifier) {
            return source.isIn(DamageTypeTags.IS_FALL) ? 0 : amount;
        }
    }

    /** Kelpwine, but you have stopped being something that belongs in the air. */
    private static final class Drowned extends DraughtEffect implements CombatHooks.Enduring {
        private static final int INTERVAL = 40;
        private static final float PARCH = 1;

        private Drowned() {
            super(at("kelpwine"), soured(at("kelpwine")));
        }

        @Override
        public boolean canApplyUpdateEffect(int duration, int amplifier) {
            return duration % INTERVAL == 0;
        }

        @Override
        public void applyUpdateEffect(LivingEntity entity, int amplifier) {
            entity.setAir(entity.getMaxAir());

            // Out of water it dries you out, which is the price of never drowning.
            if (!entity.isTouchingWater() && entity.getHealth() > PARCH) {
                entity.damage(entity.getDamageSources().dryOut(), PARCH);
            }
        }

        @Override
        public float enduring(LivingEntity bearer, @Nullable LivingEntity other, DamageSource source,
                              float amount, int amplifier) {
            if (source.isIn(DamageTypeTags.IS_DROWNING)) {
                return 0;
            }

            float shelter = tuning().dial(tuning().kelpwineWaterReduction) * GREED * (amplifier + 1);
            return bearer.isTouchingWater() ? Math.max(0, amount * (1 - shelter)) : amount;
        }
    }

    /** Quickstep, but stopping is what hurts. */
    private static final class Sprintwrack extends DraughtEffect implements CombatHooks.Enduring {
        private static final int INTERVAL = 30;
        private static final float SEIZE = 1;

        private Sprintwrack() {
            super(at("quickstep_draught"), soured(at("quickstep_draught")));
            addAttributeModifier(EntityAttributes.GENERIC_MOVEMENT_SPEED,
                    "e5170a3c-62d8-4b19-9f04-8ac2735de6b1", 0.20,
                    EntityAttributeModifier.Operation.MULTIPLY_TOTAL);
            addAttributeModifier(EntityAttributes.GENERIC_ATTACK_SPEED,
                    "4b9c8e02-1d75-42a6-b8f3-06e59a1c7d24", 0.25,
                    EntityAttributeModifier.Operation.MULTIPLY_TOTAL);
        }

        @Override
        public boolean canApplyUpdateEffect(int duration, int amplifier) {
            return duration % INTERVAL == 0;
        }

        @Override
        public void applyUpdateEffect(LivingEntity entity, int amplifier) {
            if (!entity.isSprinting() && entity.getHealth() > SEIZE) {
                entity.damage(entity.getDamageSources().magic(), SEIZE);
            }
        }

        @Override
        public float enduring(LivingEntity bearer, @Nullable LivingEntity other, DamageSource source,
                              float amount, int amplifier) {
            float shelter = tuning().dial(tuning().quickstepSprintReduction) * GREED * (amplifier + 1);
            return bearer.isSprinting() ? Math.max(0, amount * (1 - shelter)) : amount;
        }
    }

    /** Emberblood, but the fire does not know whose side it is on. */
    private static final class Pyre extends DraughtEffect implements CombatHooks.Answering {
        private Pyre() {
            super(at("emberflask"), soured(at("emberflask")));
        }

        @Override
        public void answer(LivingEntity bearer, @Nullable LivingEntity other, DamageSource source,
                           float amount, int amplifier) {
            if (other == null || source.getAttacker() != bearer) {
                return;
            }

            int seconds = (int) (tuning().emberbloodBurnSeconds * GREED) + amplifier;
            other.setOnFireFor(seconds);
            bearer.setOnFireFor(Math.max(1, seconds / 2));
        }
    }

    /** Riposte, but answering costs you the guard you had. */
    private static final class Reprisal extends DraughtEffect
            implements CombatHooks.Answering, CombatHooks.Enduring {
        private static final int STAGGER_TICKS = 60;

        private Reprisal() {
            super(at("riposte_cordial"), soured(at("riposte_cordial")));
        }

        @Override
        public void answer(LivingEntity bearer, @Nullable LivingEntity other, DamageSource source,
                           float amount, int amplifier) {
            if (other == null || other == bearer || source.getAttacker() != other) {
                return;
            }

            other.takeKnockback(0.8 + 0.2 * amplifier,
                    bearer.getX() - other.getX(), bearer.getZ() - other.getZ());
            other.addStatusEffect(new StatusEffectInstance(
                    StatusEffects.SLOWNESS, STAGGER_TICKS, amplifier + 1, false, true, true));
            other.addStatusEffect(new StatusEffectInstance(
                    StatusEffects.BLINDNESS, STAGGER_TICKS, 0, false, true, true));
        }

        @Override
        public float enduring(LivingEntity bearer, @Nullable LivingEntity other, DamageSource source,
                              float amount, int amplifier) {
            // The guard is spent on answering, so what lands while you are answering lands harder.
            return bearer.hurtTime > 0 ? amount * (1 + 0.25f * (amplifier + 1)) : amount;
        }
    }

    /** Honeyed, but what it mends it also holds still. */
    private static final class Cloying extends DraughtEffect {
        private static final int INTERVAL = 40;
        private static final double RADIUS = 5;
        private static final int BIND_TICKS = 60;

        private Cloying() {
            super(at("honeyed_restorative"), soured(at("honeyed_restorative")));
        }

        @Override
        public boolean canApplyUpdateEffect(int duration, int amplifier) {
            return duration % INTERVAL == 0;
        }

        @Override
        public void applyUpdateEffect(LivingEntity entity, int amplifier) {
            entity.heal(1);

            if (entity.getWorld().isClient) {
                return;
            }

            float mending = tuning().dial(tuning().honeyedAllyHeal) * GREED;
            Box nearby = entity.getBoundingBox().expand(RADIUS + amplifier);

            for (LivingEntity ally : entity.getWorld().getEntitiesByClass(LivingEntity.class, nearby,
                    candidate -> candidate != entity && candidate.isAlive()
                            && !(candidate instanceof HostileEntity))) {
                ally.heal(mending);
                ally.addStatusEffect(new StatusEffectInstance(
                        StatusEffects.SLOWNESS, BIND_TICKS, 2, false, false, true));
            }
        }
    }

    /** Gravebound, but the dead take you for one of their own and the living notice. */
    private static final class GraveCalled extends DraughtEffect implements CombatHooks.Enduring {
        private GraveCalled() {
            super(at("gravemead"), soured(at("gravemead")));
        }

        @Override
        public float enduring(LivingEntity bearer, @Nullable LivingEntity other, DamageSource source,
                              float amount, int amplifier) {
            if (other == null) {
                return amount;
            }

            if (other.isUndead()) {
                float shelter = tuning().dial(tuning().graveboundUndeadReduction)
                        * GREED * (amplifier + 1);
                return Math.max(0, amount * (1 - shelter));
            }

            return amount * (1 + 0.15f * (amplifier + 1));
        }
    }
}
