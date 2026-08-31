package dev.cinderflask.brew;

import net.minecraft.entity.effect.StatusEffect;
import net.minecraft.entity.effect.StatusEffectCategory;

/**
 * What to do with an effect nobody has written an entry for.
 *
 * <p>The vanilla effects are described properly in {@code data/cinderflask/brewing}, because their
 * colours are decorative rather than meaningful — Strength is a dark red that would read as sanguine.
 * Anything a mod added falls through to here, which guesses from the two things every effect has: a
 * colour, and whether it is doing you a favour.
 */
public final class PotionMapping {
    /** How much essence a guessed effect is worth. Deliberately less than a described one. */
    private static final float GUESSED_STRENGTH = 1.5f;

    /** Filth a harmful effect brings with it. */
    private static final float HARMFUL_CORRUPTION = 0.08f;

    private PotionMapping() {
    }

    public static IngredientTable.Entry heuristic(StatusEffect effect) {
        int humour = nearestHumour(effect.getColor());

        float[] weights = new float[Humours.WHEEL];
        weights[humour] = GUESSED_STRENGTH;

        Humours humours = Humours.of(weights[0], weights[1], weights[2], weights[3]);
        float corruption = effect.getCategory() == StatusEffectCategory.HARMFUL ? HARMFUL_CORRUPTION : 0;

        return new IngredientTable.Entry(humours, 0, corruption, false);
    }

    /** Which humour's colour the effect's own colour sits closest to, in plain RGB distance. */
    static int nearestHumour(int colour) {
        int best = 0;
        double bestDistance = Double.MAX_VALUE;

        for (int i = 0; i < Humours.WHEEL; i++) {
            double distance = distance(colour, Humours.humourColour(i));
            if (distance < bestDistance) {
                bestDistance = distance;
                best = i;
            }
        }

        return best;
    }

    private static double distance(int a, int b) {
        int dr = ((a >> 16) & 0xFF) - ((b >> 16) & 0xFF);
        int dg = ((a >> 8) & 0xFF) - ((b >> 8) & 0xFF);
        int db = (a & 0xFF) - (b & 0xFF);
        return (double) dr * dr + (double) dg * dg + (double) db * db;
    }
}
