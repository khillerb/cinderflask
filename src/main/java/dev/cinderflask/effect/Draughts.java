package dev.cinderflask.effect;

import dev.cinderflask.brew.Landmarks;
import dev.cinderflask.config.CinderflaskConfig;
import net.fabricmc.fabric.api.entity.event.v1.ServerEntityCombatEvents;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.attribute.EntityAttributeModifier;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.damage.DamageTypes;
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
 * The twelve draughts, one to a landmark.
 *
 * <p>They are deliberately not twelve flavours of one idea. Four are what a humour does on its own,
 * four are what two neighbouring humours do together, and four are what a humour does once reach
 * carries it out of your own body and into somebody else's — which is why the four reaching draughts
 * are the four that touch other people.
 *
 * <p>Every number the combat hook reads comes from {@link CinderflaskConfig.Tuning}. The three that
 * do not — Ironroot's knockback resistance, Quickstep's speed, Deepdelve's armour — are attribute
 * modifiers, which are baked into the effect object at construction and so stay constants here.
 */
public final class Draughts {
    private static final List<DraughtEffect> ALL = new ArrayList<>(Landmarks.all().size());
    private static final Map<Identifier, DraughtEffect> BY_LANDMARK = new HashMap<>();

    private Draughts() {
    }

    private static CinderflaskConfig.Tuning tuning() {
        return CinderflaskConfig.get().draughts;
    }

    // -------------------------------------------------------------------------------------------
    // Pure — one humour undiluted, and it happens entirely to you
    // -------------------------------------------------------------------------------------------

    /** Choleric. The emptier you are the harder you swing, and the less you can afford to be hit. */
    public static final DraughtEffect DEADMANS_VIGOUR = new DeadmansVigour();

    /** Melancholic. A flat subtraction, so it beats a swarm of small hits where Resistance does not. */
    public static final DraughtEffect IRONROOT = new Ironroot();

    /** Sanguine. What you take out of somebody comes back into you. */
    public static final DraughtEffect SAPSWORN = new Sapsworn();

    /** Phlegmatic. Hitting somebody who is not looking at you is a different thing entirely. */
    public static final DraughtEffect UNSEEN_HAND = new UnseenHand();

    // -------------------------------------------------------------------------------------------
    // Leaning — two neighbouring humours, and a hybrid to match
    // -------------------------------------------------------------------------------------------

    /** Choleric into melancholic. A wall that bites. */
    public static final DraughtEffect BRAMBLE = new Bramble();

    /** Melancholic into sanguine. Everything the ground can do to you, it cannot. */
    public static final DraughtEffect DEEPDELVE = new Deepdelve();

    /** Sanguine into phlegmatic. You stop needing the surface. */
    public static final DraughtEffect KELPSWORN = new Kelpsworn();

    /** Phlegmatic into choleric. Fast, and only worth anything while you keep moving. */
    public static final DraughtEffect QUICKSTEP = new Quickstep();

    // -------------------------------------------------------------------------------------------
    // Carried — the same humour with reach behind it, so it lands on somebody else
    // -------------------------------------------------------------------------------------------

    /** Choleric, carried. Your blows set what they touch alight, and the fire is not yours to fear. */
    public static final DraughtEffect EMBERBLOOD = new Emberblood();

    /** Melancholic, carried. Being hit staggers whoever did it, however far off they were standing. */
    public static final DraughtEffect RIPOSTE = new Riposte();

    /** Sanguine, carried. The mending does not stop at your own skin. */
    public static final DraughtEffect HONEYED = new Honeyed();

    /** Phlegmatic, carried. The dead are poor company, but they are not much of a threat either. */
    public static final DraughtEffect GRAVEBOUND = new Gravebound();

    // -------------------------------------------------------------------------------------------
    // Registry
    // -------------------------------------------------------------------------------------------

    public static void register() {
        register(DEADMANS_VIGOUR, IRONROOT, SAPSWORN, UNSEEN_HAND,
                BRAMBLE, DEEPDELVE, KELPSWORN, QUICKSTEP,
                EMBERBLOOD, RIPOSTE, HONEYED, GRAVEBOUND);

        // Gravebound's other half. A kill is not damage, so it cannot come through the combat hook.
        ServerEntityCombatEvents.AFTER_KILLED_OTHER_ENTITY.register((world, killer, killed) -> {
            if (!(killer instanceof LivingEntity living)) {
                return;
            }

            StatusEffectInstance held = living.getStatusEffect(GRAVEBOUND);
            if (held != null) {
                living.heal(tuning().dial(tuning().graveboundKillHeal) * (held.getAmplifier() + 1));
            }
        });
    }

    private static void register(DraughtEffect... draughts) {
        for (DraughtEffect draught : draughts) {
            Identifier id = draught.landmark().id();
            Registry.register(Registries.STATUS_EFFECT, id, draught);
            ALL.add(draught);
            BY_LANDMARK.put(id, draught);
        }
    }

    /** Every draught, in landmark order. Empty until {@link #register} has run. */
    public static List<DraughtEffect> all() {
        return ALL;
    }

    /** The draught a landmark produces, or null before registration. */
    @Nullable
    public static DraughtEffect of(Landmarks.Landmark landmark) {
        return BY_LANDMARK.get(landmark.id());
    }

    /** Named {@code at} rather than {@code landmark} so the draughts' own accessor stays visible. */
    private static Landmarks.Landmark at(String path) {
        for (Landmarks.Landmark candidate : Landmarks.all()) {
            if (candidate.id().getPath().equals(path)) {
                return candidate;
            }
        }

        throw new IllegalStateException("No landmark called " + path);
    }

    // -------------------------------------------------------------------------------------------
    // The twelve
    // -------------------------------------------------------------------------------------------

    private static final class DeadmansVigour extends DraughtEffect
            implements CombatHooks.Striking, CombatHooks.Enduring {
        /** However empty you get, there is a limit to what desperation is worth. */
        private static final float CEILING = 2.5f;

        private DeadmansVigour() {
            super(at("deadmans_draught"));
        }

        @Override
        public float striking(LivingEntity bearer, LivingEntity other, DamageSource source,
                              float amount, int amplifier) {
            float missing = 1 - bearer.getHealth() / Math.max(1, bearer.getMaxHealth());
            float scale = 1 + missing * tuning().dial(tuning().berserkFromMissingHealth) * (amplifier + 1);
            return amount * Math.min(CEILING, scale);
        }

        @Override
        public float enduring(LivingEntity bearer, @Nullable LivingEntity other, DamageSource source,
                              float amount, int amplifier) {
            // What it costs: everything hits you harder while you are running on it.
            return amount * (1 + tuning().dial(tuning().berserkExposure) * (amplifier + 1));
        }
    }

    private static final class Ironroot extends DraughtEffect implements CombatHooks.Enduring {
        /** However much it takes off, something always gets through. */
        private static final float FLOOR = 0.2f;

        private Ironroot() {
            super(at("ironroot_tonic"));
            addAttributeModifier(EntityAttributes.GENERIC_KNOCKBACK_RESISTANCE,
                    "9a1c7f0e-6d2b-4a53-9f47-2c1e5b8d3a60", 0.2,
                    EntityAttributeModifier.Operation.ADDITION);
        }

        @Override
        public float enduring(LivingEntity bearer, @Nullable LivingEntity other, DamageSource source,
                              float amount, int amplifier) {
            float flat = tuning().dial(tuning().ironrootFlatReduction) * (amplifier + 1);
            return Math.max(amount * FLOOR, amount - flat);
        }
    }

    private static final class Sapsworn extends DraughtEffect implements CombatHooks.Answering {
        private Sapsworn() {
            super(at("sap_sworn_mead"));
        }

        @Override
        public void answer(LivingEntity bearer, @Nullable LivingEntity other, DamageSource source,
                           float amount, int amplifier) {
            // Only off a blow the bearer struck themselves.
            if (other != null && source.getAttacker() == bearer) {
                bearer.heal(amount * tuning().dial(tuning().sapswornLifestealShare) * (amplifier + 1));
            }
        }
    }

    private static final class UnseenHand extends DraughtEffect implements CombatHooks.Striking {
        private UnseenHand() {
            super(at("nightcap"));
        }

        @Override
        public float striking(LivingEntity bearer, LivingEntity other, DamageSource source,
                              float amount, int amplifier) {
            if (!CombatHooks.isBehind(bearer, other)) {
                return amount;
            }

            return amount * (1 + tuning().dial(tuning().unseenHandBonus) * (amplifier + 1));
        }
    }

    private static final class Bramble extends DraughtEffect implements CombatHooks.Answering {
        /** Contact only. With no reach behind it, it cannot answer an arrow. */
        private static final double RANGE = 5;

        private Bramble() {
            super(at("bramblewine"));
        }

        @Override
        public void answer(LivingEntity bearer, @Nullable LivingEntity other, DamageSource source,
                           float amount, int amplifier) {
            if (other == null || other == bearer || source.isIn(DamageTypeTags.IS_PROJECTILE)
                    || bearer.squaredDistanceTo(other) > RANGE * RANGE) {
                return;
            }

            other.damage(bearer.getDamageSources().thorns(bearer),
                    amount * tuning().dial(tuning().brambleReflectShare) * (amplifier + 1));
        }
    }

    private static final class Deepdelve extends DraughtEffect implements CombatHooks.Enduring {
        private Deepdelve() {
            super(at("deepdelve"));
            addAttributeModifier(EntityAttributes.GENERIC_ARMOR,
                    "3f8e2d41-5b76-4c09-8a2e-71d4f6c0b985", 2,
                    EntityAttributeModifier.Operation.ADDITION);
        }

        @Override
        public float enduring(LivingEntity bearer, @Nullable LivingEntity other, DamageSource source,
                              float amount, int amplifier) {
            return crushing(source) ? 0 : amount;
        }

        /** Everything the ground itself does to somebody working in it. */
        private static boolean crushing(DamageSource source) {
            // is_fall already covers stalagmites, so falling onto one is in here twice over.
            return source.isIn(DamageTypeTags.IS_FALL)
                    || source.isOf(DamageTypes.IN_WALL)
                    || source.isOf(DamageTypes.FALLING_BLOCK)
                    || source.isOf(DamageTypes.FALLING_ANVIL)
                    || source.isOf(DamageTypes.FALLING_STALACTITE)
                    || source.isOf(DamageTypes.CACTUS);
        }
    }

    private static final class Kelpsworn extends DraughtEffect implements CombatHooks.Enduring {
        private static final float FLOOR = 0.4f;

        private Kelpsworn() {
            super(at("kelpwine"));
        }

        @Override
        public boolean canApplyUpdateEffect(int duration, int amplifier) {
            return true;
        }

        @Override
        public void applyUpdateEffect(LivingEntity entity, int amplifier) {
            entity.setAir(entity.getMaxAir());
        }

        @Override
        public float enduring(LivingEntity bearer, @Nullable LivingEntity other, DamageSource source,
                              float amount, int amplifier) {
            if (source.isIn(DamageTypeTags.IS_DROWNING)) {
                return 0;
            }

            if (!bearer.isTouchingWater()) {
                return amount;
            }

            float shelter = tuning().dial(tuning().kelpwineWaterReduction) * (amplifier + 1);
            return Math.max(amount * FLOOR, amount * (1 - shelter));
        }
    }

    private static final class Quickstep extends DraughtEffect implements CombatHooks.Enduring {
        private static final float FLOOR = 0.2f;

        private Quickstep() {
            super(at("quickstep_draught"));
            addAttributeModifier(EntityAttributes.GENERIC_MOVEMENT_SPEED,
                    "c6b0a935-2e18-4d7f-b3a1-8e40597c2df6", 0.10,
                    EntityAttributeModifier.Operation.MULTIPLY_TOTAL);
            addAttributeModifier(EntityAttributes.GENERIC_ATTACK_SPEED,
                    "5d2f81ba-70c4-4e36-9018-b7a3e6459c2d", 0.15,
                    EntityAttributeModifier.Operation.MULTIPLY_TOTAL);
        }

        @Override
        public float enduring(LivingEntity bearer, @Nullable LivingEntity other, DamageSource source,
                              float amount, int amplifier) {
            // Only while you are actually moving. Standing still with it on is worth nothing.
            if (!bearer.isSprinting()) {
                return amount;
            }

            float shelter = tuning().dial(tuning().quickstepSprintReduction) * (amplifier + 1);
            return Math.max(amount * FLOOR, amount * (1 - shelter));
        }
    }

    private static final class Emberblood extends DraughtEffect
            implements CombatHooks.Answering, CombatHooks.Enduring {
        private Emberblood() {
            super(at("emberflask"));
        }

        @Override
        public void answer(LivingEntity bearer, @Nullable LivingEntity other, DamageSource source,
                           float amount, int amplifier) {
            if (other != null && source.getAttacker() == bearer) {
                other.setOnFireFor(tuning().emberbloodBurnSeconds + amplifier);
            }
        }

        @Override
        public float enduring(LivingEntity bearer, @Nullable LivingEntity other, DamageSource source,
                              float amount, int amplifier) {
            return source.isIn(DamageTypeTags.IS_FIRE) ? 0 : amount;
        }
    }

    private static final class Riposte extends DraughtEffect
            implements CombatHooks.Answering, CombatHooks.Striking {
        private static final int STAGGER_TICKS = 40;

        private Riposte() {
            super(at("riposte_cordial"));
        }

        @Override
        public void answer(LivingEntity bearer, @Nullable LivingEntity other, DamageSource source,
                           float amount, int amplifier) {
            // Reach is what makes this land on the archer rather than on the arrow.
            if (other == null || other == bearer || source.getAttacker() != other) {
                return;
            }

            other.takeKnockback(0.5 + 0.2 * amplifier,
                    bearer.getX() - other.getX(), bearer.getZ() - other.getZ());
            other.addStatusEffect(new StatusEffectInstance(
                    StatusEffects.SLOWNESS, STAGGER_TICKS, amplifier, false, true, true));
        }

        @Override
        public float striking(LivingEntity bearer, LivingEntity other, DamageSource source,
                              float amount, int amplifier) {
            // hurtTime is vanilla's own record of having just been hit, so the window costs no
            // bookkeeping: answer inside it and the blow lands heavier.
            if (bearer.hurtTime <= 0) {
                return amount;
            }

            return amount * (1 + tuning().dial(tuning().riposteAnswerBonus) * (amplifier + 1));
        }
    }

    private static final class Honeyed extends DraughtEffect {
        private static final int INTERVAL = 50;

        /** What the bearer gets, matching vanilla Regeneration. The reach is the configurable part. */
        private static final float SELF = 1;

        private static final double RADIUS = 4;

        private Honeyed() {
            super(at("honeyed_restorative"));
        }

        @Override
        public boolean canApplyUpdateEffect(int duration, int amplifier) {
            int interval = INTERVAL >> amplifier;
            return interval <= 0 || duration % interval == 0;
        }

        @Override
        public void applyUpdateEffect(LivingEntity entity, int amplifier) {
            entity.heal(SELF);

            if (entity.getWorld().isClient) {
                return;
            }

            // Reach is the whole point. Without it this would only be a slower Regeneration.
            Box nearby = entity.getBoundingBox().expand(RADIUS + amplifier);
            float mending = tuning().dial(tuning().honeyedAllyHeal);

            for (LivingEntity ally : entity.getWorld().getEntitiesByClass(LivingEntity.class, nearby,
                    candidate -> candidate != entity && candidate.isAlive()
                            && !(candidate instanceof HostileEntity))) {
                ally.heal(mending);
            }
        }
    }

    private static final class Gravebound extends DraughtEffect implements CombatHooks.Enduring {
        private static final float FLOOR = 0.3f;

        private Gravebound() {
            super(at("gravemead"));
        }

        @Override
        public float enduring(LivingEntity bearer, @Nullable LivingEntity other, DamageSource source,
                              float amount, int amplifier) {
            if (other == null || !other.isUndead()) {
                return amount;
            }

            float shelter = tuning().dial(tuning().graveboundUndeadReduction) * (amplifier + 1);
            return Math.max(amount * FLOOR, amount * (1 - shelter));
        }
    }
}
