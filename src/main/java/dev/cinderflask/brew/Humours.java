package dev.cinderflask.brew;

/**
 * The five essences a brew is made of. Pure maths, no Minecraft.
 *
 * <p>Four of them sit on a wheel and rotate as a brew ages:
 *
 * <pre>
 *   CHOLERIC ─▶ MELANCHOLIC ─▶ SANGUINE ─▶ PHLEGMATIC ─┐
 *       ▲                                              │
 *       └──────────────────────────────────────────────┘
 *     harsh   ▶   settled   ▶   mellow   ▶   sour   ▶  harsh again
 * </pre>
 *
 * <p>Quintessence does not rotate. It is reach, not character.
 */
public record Humours(float choleric, float melancholic, float sanguine, float phlegmatic,
                      float quintessence) {

    public static final Humours EMPTY = new Humours(0, 0, 0, 0, 0);

    /** Positions on the wheel, in rotation order. Quintessence is deliberately absent. */
    public static final int WHEEL = 4;

    public Humours {
        choleric = Math.max(0, choleric);
        melancholic = Math.max(0, melancholic);
        sanguine = Math.max(0, sanguine);
        phlegmatic = Math.max(0, phlegmatic);
        quintessence = Math.max(0, quintessence);
    }

    public static Humours of(float choleric, float melancholic, float sanguine, float phlegmatic) {
        return new Humours(choleric, melancholic, sanguine, phlegmatic, 0);
    }

    // ---------------------------------------------------------------------------------------
    // Wheel access
    // ---------------------------------------------------------------------------------------

    /** Reads a wheel position by index, wrapping. 0 is choleric, 3 is phlegmatic. */
    public float wheel(int index) {
        return switch (Math.floorMod(index, WHEEL)) {
            case 0 -> choleric;
            case 1 -> melancholic;
            case 2 -> sanguine;
            default -> phlegmatic;
        };
    }

    private static Humours fromWheel(float[] wheel, float quintessence) {
        return new Humours(wheel[0], wheel[1], wheel[2], wheel[3], quintessence);
    }

    // ---------------------------------------------------------------------------------------
    // Ageing
    // ---------------------------------------------------------------------------------------

    /**
     * The brew as it stands after {@code phase} steps around the wheel.
     *
     * <p>Always computed from the original vector rather than by repeatedly ageing the previous
     * result. That distinction is the whole ball game: a per-step blend is a stochastic matrix whose
     * stationary state is uniform, so iterating it drives every brew — whatever it started as —
     * towards flat grey, and ageing would destroy the variety it is supposed to create. Interpolating
     * once from the original bounds the blur at half a step no matter how old the brew gets.
     *
     * <p>Consequences worth relying on: at whole phases this is an exact permutation, so the shape
     * survives perfectly; and after a full turn of four the vector is exactly itself again.
     */
    public Humours rotated(float phase) {
        float wrapped = (float) (phase - Math.floor(phase / WHEEL) * WHEEL);
        int step = (int) Math.floor(wrapped);
        float blend = wrapped - step;

        float[] out = new float[WHEEL];
        for (int i = 0; i < WHEEL; i++) {
            // Position i now holds what used to be `step` places behind it, easing into the next.
            out[i] = wheel(i - step) * (1 - blend) + wheel(i - step - 1) * blend;
        }

        return fromWheel(out, quintessence);
    }

    // ---------------------------------------------------------------------------------------
    // Vector arithmetic
    // ---------------------------------------------------------------------------------------

    public Humours plus(Humours other) {
        return new Humours(
                choleric + other.choleric,
                melancholic + other.melancholic,
                sanguine + other.sanguine,
                phlegmatic + other.phlegmatic,
                quintessence + other.quintessence);
    }

    /** Scales the four wheel humours. Quintessence is left alone — aether does not thin. */
    public Humours scaled(float factor) {
        return new Humours(choleric * factor, melancholic * factor, sanguine * factor,
                phlegmatic * factor, quintessence);
    }

    /** Dose-weighted blend, used by solera top-ups and by pouring one flask into another. */
    public static Humours blend(Humours a, float weightA, Humours b, float weightB) {
        float total = weightA + weightB;
        if (total <= 0) {
            return EMPTY;
        }
        return a.scaledAll(weightA / total).plus(b.scaledAll(weightB / total));
    }

    private Humours scaledAll(float factor) {
        return new Humours(choleric * factor, melancholic * factor, sanguine * factor,
                phlegmatic * factor, quintessence * factor);
    }

    // ---------------------------------------------------------------------------------------
    // Derived
    // ---------------------------------------------------------------------------------------

    /** The body of the brew: the four wheel humours. Quintessence is not part of it. */
    public float magnitude() {
        return choleric + melancholic + sanguine + phlegmatic;
    }

    // How much each humour lends to force and to endurance. Deliberately not two buckets of two:
    // splitting them that way makes sanguine behave exactly like choleric and melancholic exactly
    // like phlegmatic, which collapses a four-character wheel into two and leaves duration with only
    // two values it can ever take. Weighting every humour separately gives each its own signature —
    // choleric spikes, sanguine is strong and sustained, phlegmatic is weak and long, melancholic is
    // a wall — and keeps both quantities continuous as a brew turns.
    private static final float[] FORCE = {1.00f, 0.15f, 0.60f, 0.25f};
    private static final float[] ENDURANCE = {0.10f, 1.00f, 0.45f, 0.80f};

    /** How hard the brew hits. Drives amplifier. */
    public float heat() {
        return weighted(FORCE);
    }

    /** How long it holds. Drives duration. */
    public float depth() {
        return weighted(ENDURANCE);
    }

    private float weighted(float[] weights) {
        float total = 0;
        for (int i = 0; i < WHEEL; i++) {
            total += wheel(i) * weights[i];
        }
        return total;
    }

    /** Which wheel humour leads, as an index. Returns 0 for an empty brew. */
    public int dominant() {
        int best = 0;
        for (int i = 1; i < WHEEL; i++) {
            if (wheel(i) > wheel(best)) {
                best = i;
            }
        }
        return best;
    }

    /**
     * How evenly spread the four humours are, from 0 (all one humour) to 1 (perfectly level).
     * Normalised Shannon entropy, which behaves sensibly for the two-humour brews a straight
     * variance measure would misjudge.
     */
    public float balance() {
        float total = magnitude();
        if (total <= 0) {
            return 0;
        }

        double entropy = 0;
        for (int i = 0; i < WHEEL; i++) {
            double share = wheel(i) / total;
            if (share > 0) {
                entropy -= share * Math.log(share);
            }
        }

        return (float) (entropy / Math.log(WHEEL));
    }

    /** How likely the flask is to crack: hot and unbuttressed by anything patient. */
    public float volatility() {
        return choleric / (melancholic + 1);
    }

    /**
     * Cosine similarity across all five axes, used to decide which effects a brew is near.
     * Returns 0 if either side has no length at all.
     */
    public float similarity(Humours other) {
        double dot = choleric * other.choleric
                + melancholic * other.melancholic
                + sanguine * other.sanguine
                + phlegmatic * other.phlegmatic
                + quintessence * other.quintessence;

        double lengths = length() * other.length();
        return lengths <= 0 ? 0 : (float) (dot / lengths);
    }

    private double length() {
        return Math.sqrt((double) choleric * choleric
                + (double) melancholic * melancholic
                + (double) sanguine * sanguine
                + (double) phlegmatic * phlegmatic
                + (double) quintessence * quintessence);
    }

    public boolean isEmpty() {
        return magnitude() <= 0 && quintessence <= 0;
    }
}
