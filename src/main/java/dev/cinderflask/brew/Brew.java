package dev.cinderflask.brew;

/**
 * A sealed brew: what went in, how far round the wheel it has turned, and how filthy it has become.
 *
 * <p>Everything the game reads off a brew — amplifier, duration, dose count, colour, whether it has
 * spoiled — is derived here rather than stored, so ageing changes all of it at once and none of it
 * can drift out of agreement.
 *
 * <p>Still pure maths. Nothing in this file knows what a world is.
 */
public record Brew(Humours sealed, float phase, float addedCorruption, float capacity) {

    /** Body lost per full turn of the wheel. What eventually thins a neglected flask into Sump. */
    public static final float DECAY_PER_TURN = 0.18f;

    /** Filth gained per full turn. Roughly seven turns from clean to entirely corrupt. */
    public static final float CORRUPTION_PER_TURN = 0.15f;

    /** Below this much body the brew is no longer a brew. */
    public static final float SPOIL_FLOOR = 1.0f;

    /** Essence per unit of capacity beyond which the vessel cannot hold it at all. */
    public static final float MAX_CONCENTRATION = 2.5f;

    private static final float AMPLIFIER_SCALE = 2.5f;
    private static final int MAX_AMPLIFIER = 4;
    private static final int BASE_DURATION_TICKS = 60;
    private static final int DEPTH_DURATION_TICKS = 400;

    public Brew {
        phase = Math.max(0, phase);
        addedCorruption = Math.min(1, Math.max(0, addedCorruption));
        capacity = Math.max(1, capacity);
    }

    public static Brew fresh(Humours humours, float capacity) {
        return new Brew(humours, 0, 0, capacity);
    }

    // -------------------------------------------------------------------------------------------
    // Ageing
    // -------------------------------------------------------------------------------------------

    public Brew aged(float additionalPhase) {
        return new Brew(sealed, phase + Math.max(0, additionalPhase), addedCorruption, capacity);
    }

    /** Full turns completed. Only whole turns accrue corruption. */
    public int turns() {
        return (int) (phase / Humours.WHEEL);
    }

    private float bodyRemaining() {
        return (float) Math.pow(1 - DECAY_PER_TURN, phase / Humours.WHEEL);
    }

    /** What is actually in the flask right now: turned round the wheel and thinned by age. */
    public Humours current() {
        return sealed.rotated(phase).scaled(bodyRemaining());
    }

    /**
     * How filthy the brew is, from 0 to 1. Reagents can push it, but mostly it is what time does:
     * letting a brew rot is the cheap route to the dark half of the effect space.
     */
    public float corruption() {
        return Math.min(1, addedCorruption + phase / Humours.WHEEL * CORRUPTION_PER_TURN);
    }

    // -------------------------------------------------------------------------------------------
    // What the game reads
    // -------------------------------------------------------------------------------------------

    /**
     * Essence per unit of vessel. The central trade: cram a lot into a small body and you get a few
     * ferocious doses, spread it through a large one and you get many mild ones.
     */
    public float concentration() {
        return current().magnitude() / capacity;
    }

    /** Doses are the vessel's volume, not the strength of what is in it. */
    public int doses() {
        return Math.round(capacity);
    }

    /** Driven by how concentrated the hot humours are, so it falls away as the brew settles. */
    public int amplifier() {
        float hot = current().heat() / capacity;
        return Math.min(MAX_AMPLIFIER, (int) (hot * AMPLIFIER_SCALE));
    }

    /**
     * Driven by the *share* of the brew that is patient rather than its absolute amount, which is
     * what makes duration climb as the amplifier drops instead of both rising together.
     */
    public int durationTicks() {
        Humours now = current();
        float body = now.magnitude();

        if (body <= 0) {
            return BASE_DURATION_TICKS;
        }

        return BASE_DURATION_TICKS + Math.round(now.depth() / body * DEPTH_DURATION_TICKS);
    }

    /** A lopsided, concentrated brew punishes you. A level one barely does. */
    public float comedown() {
        return (1 - current().balance()) * Math.min(1, concentration() / MAX_CONCENTRATION);
    }

    public boolean isSpoiled() {
        return current().magnitude() < SPOIL_FLOOR || concentration() > MAX_CONCENTRATION;
    }

    // -------------------------------------------------------------------------------------------
    // Vessel operations
    // -------------------------------------------------------------------------------------------

    /**
     * Solera: pour fresh brew onto what is already in the flask. Vector and age both blend by dose,
     * so topping up drags a tired old brew back up to strength without resetting what it has become.
     */
    public Brew toppedUp(Brew fresh, float freshDoses) {
        float held = doses();
        float total = held + freshDoses;

        if (total <= 0) {
            return this;
        }

        return new Brew(
                Humours.blend(current(), held, fresh.current(), freshDoses),
                (phase * held + fresh.phase * freshDoses) / total,
                (corruption() * held + fresh.corruption() * freshDoses) / total,
                Math.max(capacity, fresh.capacity));
    }

    public Brew withAddedCorruption(float amount) {
        return new Brew(sealed, phase, addedCorruption + amount, capacity);
    }
}
