package dev.cinderflask.player;

import dev.cinderflask.brew.Humours;

/**
 * How much of each humour a drinker has tasted.
 *
 * <p>Gates legibility and nothing else — a brew you cannot read still does exactly what it does.
 * Knowledge is the progression; nothing is locked behind it.
 */
public record Palate(float[] tasted) {
    /** Levels beyond this add nothing; five is already "reads the figures". */
    public static final int MAX_LEVEL = 5;

    /** Doses of a humour behind each level, so the first step is quick and the last is earned. */
    private static final float PER_LEVEL = 4;

    public static Palate empty() {
        return new Palate(new float[5]);
    }

    public Palate {
        if (tasted.length != 5) {
            throw new IllegalArgumentException("a palate has five axes");
        }
    }

    /**
     * Records a dose. Each humour is credited by its share of what was drunk, so a brew that is
     * mostly choleric teaches you about choleric and barely anything about the rest.
     */
    public Palate tasting(Humours brew) {
        float total = brew.magnitude() + brew.quintessence();
        if (total <= 0) {
            return this;
        }

        float[] next = tasted.clone();
        for (int i = 0; i < Humours.WHEEL; i++) {
            next[i] += brew.wheel(i) / total;
        }
        next[4] += brew.quintessence() / total;

        return new Palate(next);
    }

    public int level(int axis) {
        return Math.min(MAX_LEVEL, (int) (tasted[axis] / PER_LEVEL));
    }

    /** How well a particular brew can be read: whatever you know about the humour that leads it. */
    public int levelFor(Humours brew) {
        return level(brew.dominant());
    }

    public float tasted(int axis) {
        return tasted[axis];
    }
}
