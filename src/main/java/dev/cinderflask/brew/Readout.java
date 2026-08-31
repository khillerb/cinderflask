package dev.cinderflask.brew;

import dev.cinderflask.player.Palate;
import net.minecraft.text.MutableText;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

import java.util.ArrayList;
import java.util.List;

/**
 * The one place a brew is turned into words or figures.
 *
 * <p>Both the tooltip and the intake go through here, so what a brew reads like is decided once. How
 * much it gives away depends on how much of that humour the drinker has actually tasted — the brew
 * does the same thing either way, you just cannot see what it is yet.
 */
public final class Readout {
    /** Adjectives for how much of a humour is present, from a trace to overwhelming. */
    private static final String[] STRENGTHS = {"faint", "some", "strong", "overwhelming"};

    private static final String[] HUMOUR_KEYS = {
            "cinderflask.humour.choleric",
            "cinderflask.humour.melancholic",
            "cinderflask.humour.sanguine",
            "cinderflask.humour.phlegmatic",
    };

    private Readout() {
    }

    public static List<Text> describe(Brew brew, Palate palate) {
        List<Text> lines = new ArrayList<>(3);
        Humours now = brew.current();

        if (now.magnitude() <= 0) {
            return lines;
        }

        int level = palate.levelFor(now);

        if (level <= 0) {
            lines.add(Text.translatable(impression(now)).formatted(Formatting.GRAY));
            return lines;
        }

        if (level < 3) {
            lines.add(named(now).formatted(Formatting.GRAY));
            lines.add(Text.translatable("cinderflask.readout.rough",
                    Text.translatable(bodyWord(brew))).formatted(Formatting.DARK_GRAY));
            return lines;
        }

        lines.add(Text.translatable("cinderflask.tooltip.humours",
                fmt(now.choleric()), fmt(now.melancholic()),
                fmt(now.sanguine()), fmt(now.phlegmatic())).formatted(Formatting.GRAY));

        if (now.quintessence() > 0) {
            lines.add(Text.translatable("cinderflask.tooltip.reach",
                    fmt(now.quintessence())).formatted(Formatting.GRAY));
        }

        lines.add(Text.translatable("cinderflask.tooltip.strength",
                brew.amplifier() + 1, fmt(brew.durationTicks() / 20f)).formatted(Formatting.DARK_GRAY));

        return lines;
    }

    /** What it smells like, when you have no idea what you are smelling. */
    private static String impression(Humours now) {
        return switch (now.dominant()) {
            case 0 -> "cinderflask.impression.choleric";
            case 1 -> "cinderflask.impression.melancholic";
            case 2 -> "cinderflask.impression.sanguine";
            default -> "cinderflask.impression.phlegmatic";
        };
    }

    /** The humours by name, with a sense of how much, but no numbers. */
    private static MutableText named(Humours now) {
        float total = now.magnitude();
        List<Text> parts = new ArrayList<>(Humours.WHEEL);

        for (int i = 0; i < Humours.WHEEL; i++) {
            float share = now.wheel(i) / total;
            if (share < 0.1f) {
                continue;
            }

            int strength = Math.min(STRENGTHS.length - 1, (int) (share * STRENGTHS.length));
            parts.add(Text.translatable("cinderflask.readout.part",
                    Text.translatable("cinderflask.strength." + STRENGTHS[strength]),
                    Text.translatable(HUMOUR_KEYS[i])));
        }

        MutableText joined = parts.isEmpty() ? Text.empty() : parts.get(0).copy();
        for (int i = 1; i < parts.size(); i++) {
            joined = joined.append(", ").append(parts.get(i));
        }

        return joined;
    }

    private static String bodyWord(Brew brew) {
        float concentration = brew.concentration();
        if (concentration < 0.75f) {
            return "cinderflask.body.thin";
        }
        return concentration > 1.75f ? "cinderflask.body.thick" : "cinderflask.body.even";
    }

    public static String fmt(float value) {
        return String.format("%.1f", value);
    }
}
