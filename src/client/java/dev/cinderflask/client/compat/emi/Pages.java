package dev.cinderflask.client.compat.emi;

import dev.emi.emi.api.widget.WidgetHolder;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.text.OrderedText;
import net.minecraft.text.Text;

import java.util.List;

/**
 * Text that stays inside its page.
 *
 * <p>{@code WidgetHolder#addText} draws one unwrapped line wherever it is told, and nothing checks it
 * against the page width. Three of this mod's four pages were running their text off the edge — one
 * of them by 234 pixels — which is a large part of why they read as unfinished.
 */
final class Pages {
    /** Vertical pitch of wrapped text, matching the spacing the pages already use. */
    static final int LINE = 10;

    private Pages() {
    }

    private static TextRenderer font() {
        return MinecraftClient.getInstance().textRenderer;
    }

    static List<OrderedText> wrap(Text text, int width) {
        return font().wrapLines(text, width);
    }

    /**
     * Draws {@code text} wrapped to {@code width}, and reports the y the next thing may start at.
     */
    static int paragraph(WidgetHolder widgets, Text text, int x, int y, int width, int colour) {
        for (OrderedText line : wrap(text, width)) {
            widgets.addText(line, x, y, colour, false);
            y += LINE;
        }

        return y;
    }

    /** How tall a paragraph will be, so a page can size itself before it draws anything. */
    static int height(Text text, int width) {
        return wrap(text, width).size() * LINE;
    }
}
