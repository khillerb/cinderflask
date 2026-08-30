package dev.cinderflask.brew;

/**
 * What the vessel was fired against. Set once, permanent, and it does two things at once: leans every
 * brew put in it, and scales how fast that brew turns.
 *
 * <p>Four tempers bias a humour; the two aetheric ones buy reach instead, which is why the echo
 * temper is the cheap way to a brew that fires twice.
 */
public enum Temper {
    UNTEMPERED("untempered", 1.00f, Humours.EMPTY, 0),
    HERBAL("herbal", 1.00f, Humours.of(0, 0, 1, 0), 0),
    EMBER("ember", 2.00f, Humours.of(1, 0, 0, 0), 0),
    RIME("rime", 0.50f, Humours.of(0, 1, 0, 0), 0),
    GRAVE("grave", 1.50f, Humours.of(0, 0, 0, 1), 0.10f),
    RESONANT("resonant", 1.00f, new Humours(0, 0, 0, 0, 1), 0),
    ECHO("echo", 0.75f, new Humours(0, 0, 0, 0, 2), 0);

    private final String id;
    private final float rate;
    private final Humours bias;
    private final float corruption;

    Temper(String id, float rate, Humours bias, float corruption) {
        this.id = id;
        this.rate = rate;
        this.bias = bias;
        this.corruption = corruption;
    }

    public String id() {
        return id;
    }

    /** How fast the wheel turns in this vessel. Ember rushes a brew; rime cellars it. */
    public float rate() {
        return rate;
    }

    /** Added to whatever is sealed in. */
    public Humours bias() {
        return bias;
    }

    /** Corruption the vessel itself lends. Only the grave temper does. */
    public float corruption() {
        return corruption;
    }

    public static Temper byId(String id) {
        for (Temper temper : values()) {
            if (temper.id.equals(id)) {
                return temper;
            }
        }
        return UNTEMPERED;
    }

    public String translationKey() {
        return "cinderflask.temper." + id;
    }
}
