package dev.cinderflask.brew;

import dev.cinderflask.Cinderflask;
import dev.cinderflask.config.CinderflaskConfig;
import dev.cinderflask.item.CinderflaskItem;
import net.minecraft.entity.EntityType;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.Registries;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.registry.tag.TagKey;
import net.minecraft.util.Identifier;
import org.jetbrains.annotations.Nullable;

import java.util.EnumSet;

/**
 * A threshold a brew has crossed.
 *
 * <p>Crossing one is a fact about the brew and changes the dose a little. Crossing several compounds:
 * the count scales potency, and past {@link CinderflaskConfig.Inflections#capstone} the dose grants
 * something no landmark can give. That is where the memorable brews come from — earned by geometry
 * rather than by a recipe, and reachable by genuinely different routes, because {@link #LEVEL} and
 * {@link #EXACT} pull against one another.
 *
 * <p>Four sources: what the brew is like, where it sits, what it was brewed in, and what the vessel
 * caught. The last two live on the stack rather than in the {@link Brew}, which is why this reads
 * both.
 */
public enum Inflection {
    // -- what the brew is like -------------------------------------------------------------------

    /** Packed tight. More force out of the same vessel. */
    CONCENTRATED((flask, brew) -> brew.concentration() >= marks().concentrated),

    /** Level across all four. Nothing dominates, and nothing rebounds. */
    LEVEL((flask, brew) -> brew.current().balance() >= marks().level),

    /** Sitting squarely on a known brew rather than between several. */
    EXACT((flask, brew) -> Landmarks.bestSimilarity(brew.current()) >= marks().exact),

    /** Enough reach to leave the drinker. */
    FAR((flask, brew) -> brew.current().quintessence() >= marks().far),

    /** Far enough gone that every draught turns. */
    FOUL((flask, brew) -> brew.corruption() >= marks().foul),

    /** Old. It has been round the wheel and back. */
    DEEP((flask, brew) -> brew.phase() / Humours.WHEEL >= marks().deep),

    /** Hot and unbuttressed. Drinking it is its own risk. */
    VOLATILE((flask, brew) -> brew.current().volatility() >= marks().volatile_),

    // -- where it sits ---------------------------------------------------------------------------
    // Depth is not exactness. Exactness is direction: a tiny brew can sit precisely on a landmark.
    // Depth is quantity: a large brew can be steeped in choleric and still be muddled.

    /** Steeped in choleric. */
    ACRID(depth(0)),

    /** Steeped in melancholic. */
    LEADEN(depth(1)),

    /** Steeped in sanguine. */
    LUSH(depth(2)),

    /** Steeped in phlegmatic. */
    BRACKISH(depth(3)),

    // -- what it was brewed in -------------------------------------------------------------------

    /** Brewed in a vessel that lends reach of its own. */
    AETHERIC((flask, brew) -> flask != null
            && flask.getItem() instanceof CinderflaskItem vessel
            && vessel.innateQuintessence() > 0),

    /** Brewed in a flask that has held enough to have earned a name. */
    STORIED((flask, brew) -> flask != null && VesselName.of(flask) != null),

    // -- what the vessel caught ------------------------------------------------------------------

    /** The mote came from something worth remarking on. Which creatures those are is a tag. */
    OMENED((flask, brew) -> flask != null && storied(Vessel.moteOrigin(flask)));

    /** Creatures whose motes count. A tag, so a pack decides what is rare enough to matter. */
    public static final TagKey<EntityType<?>> STORIED_MOTES =
            TagKey.of(RegistryKeys.ENTITY_TYPE, Cinderflask.id("storied_motes"));

    @FunctionalInterface
    private interface Test {
        boolean crossed(@Nullable ItemStack flask, Brew brew);
    }

    private final Test test;

    Inflection(Test test) {
        this.test = test;
    }

    public String translationKey() {
        return "cinderflask.inflection." + name().toLowerCase(java.util.Locale.ROOT);
    }

    private static CinderflaskConfig.Inflections marks() {
        return CinderflaskConfig.get().inflections;
    }

    private static Test depth(int humour) {
        return (flask, brew) -> brew.current().wheel(humour) >= marks().humourDepth;
    }

    private static boolean storied(@Nullable Identifier origin) {
        if (origin == null) {
            return false;
        }

        EntityType<?> type = Registries.ENTITY_TYPE.get(origin);
        return Registries.ENTITY_TYPE.getEntry(type).isIn(STORIED_MOTES);
    }

    /**
     * Everything this brew has crossed.
     *
     * <p>The flask may be null where only the brew is known — the pure tests, and anywhere a brew is
     * being reasoned about rather than drunk. The vessel and mote inflections simply do not fire.
     */
    public static EnumSet<Inflection> of(@Nullable ItemStack flask, Brew brew) {
        EnumSet<Inflection> crossed = EnumSet.noneOf(Inflection.class);

        for (Inflection inflection : values()) {
            if (inflection.test.crossed(flask, brew)) {
                crossed.add(inflection);
            }
        }

        return crossed;
    }

    /** How far past the capstone a dose is, from 0 upward. */
    public static int beyondCapstone(EnumSet<Inflection> crossed) {
        return Math.max(0, crossed.size() - marks().capstone);
    }

    public static boolean capstoned(EnumSet<Inflection> crossed) {
        return crossed.size() >= marks().capstone;
    }
}
