package dev.cinderflask.client;

import dev.cinderflask.Cinderflask;
import dev.cinderflask.brew.Brew;
import dev.cinderflask.brew.BrewNbt;
import dev.cinderflask.brew.IngredientTable;
import dev.cinderflask.item.CinderflaskItem;
import dev.cinderflask.screen.CinderflaskScreenHandler;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.ingame.HandledScreen;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.MathHelper;

import java.util.List;

public class CinderflaskScreen extends HandledScreen<CinderflaskScreenHandler> {
    private static final Identifier TEXTURE = Cinderflask.id("textures/gui/cinderflask.png");

    private static final int BAR_X = 36;
    private static final int BAR_Y = 48;
    private static final int BAR_WIDTH = 104;
    private static final int BAR_HEIGHT = 6;
    private static final int BAR_V_EMPTY = 166;
    private static final int BAR_V_FULL = 172;

    public CinderflaskScreen(CinderflaskScreenHandler handler, PlayerInventory inventory, Text title) {
        super(handler, inventory, title);
        this.backgroundHeight = 166;
        this.playerInventoryTitleY = this.backgroundHeight - 94;
    }

    @Override
    protected void drawBackground(DrawContext context, float delta, int mouseX, int mouseY) {
        context.drawTexture(TEXTURE, x, y, 0, 0, backgroundWidth, backgroundHeight);

        ItemStack flask = handler.getFlask();
        Brew brew = BrewNbt.read(flask, client == null ? null : client.world);

        context.drawTexture(TEXTURE, x + BAR_X, y + BAR_Y, 0, BAR_V_EMPTY, BAR_WIDTH, BAR_HEIGHT);

        if (brew == null) {
            return;
        }

        // The bar shows how full the vessel is, so an over-concentrated brew visibly overruns it.
        float fullness = MathHelper.clamp(brew.concentration() / Brew.MAX_CONCENTRATION, 0, 1);
        int filled = MathHelper.ceil(BAR_WIDTH * fullness);

        if (filled > 0) {
            context.drawTexture(TEXTURE, x + BAR_X, y + BAR_Y, 0, BAR_V_FULL, filled, BAR_HEIGHT);
        }
    }

    @Override
    protected void drawForeground(DrawContext context, int mouseX, int mouseY) {
        drawCentered(context, title, 6);

        ItemStack flask = handler.getFlask();
        Brew brew = BrewNbt.read(flask, client == null ? null : client.world);

        if (brew == null) {
            drawCentered(context, Text.translatable("cinderflask.gui.nothing"), 58);
        } else {
            drawCentered(context, Text.translatable("cinderflask.gui.doses", BrewNbt.doses(flask)), 20);
            drawCentered(context, Text.translatable("cinderflask.gui.strength",
                    brew.amplifier() + 1, String.format("%.1f", brew.durationTicks() / 20f)), 58);
        }

        context.drawText(textRenderer, playerInventoryTitle, playerInventoryTitleX, playerInventoryTitleY,
                0xFF4B4B57, false);
    }

    private void drawCentered(DrawContext context, Text text, int y) {
        context.drawText(textRenderer, text, (backgroundWidth - textRenderer.getWidth(text)) / 2, y,
                0xFFD9D2C4, false);
    }

    /** Shows what a hovered ingredient would write, so you can weigh it before dropping it in. */
    @Override
    protected void drawMouseoverTooltip(DrawContext context, int x, int y) {
        if (!handler.getCursorStack().isEmpty() || focusedSlot == null || !focusedSlot.hasStack()) {
            super.drawMouseoverTooltip(context, x, y);
            return;
        }

        ItemStack hovered = focusedSlot.getStack();
        List<Text> lines = getTooltipFromItem(client, hovered);
        IngredientTable.Entry entry = IngredientTable.lookup(hovered);

        if (entry != null) {
            if (entry.body() > 0) {
                lines.add(Text.translatable("cinderflask.tooltip.body",
                        String.format("%.0f", entry.body())).formatted(Formatting.GOLD));
            }
            if (!entry.humours().isEmpty()) {
                lines.add(Text.translatable("cinderflask.tooltip.writes",
                        describe(entry)).formatted(Formatting.GOLD));
            }
        }

        context.drawTooltip(textRenderer, lines, hovered.getTooltipData(), x, y);
    }

    private static String describe(IngredientTable.Entry entry) {
        StringBuilder out = new StringBuilder();
        String[] names = {"choleric", "melancholic", "sanguine", "phlegmatic"};

        for (int i = 0; i < names.length; i++) {
            float amount = entry.humours().wheel(i);
            if (amount > 0) {
                out.append(out.isEmpty() ? "" : ", ").append(String.format("%.0f ", amount)).append(names[i]);
            }
        }

        if (entry.humours().quintessence() > 0) {
            out.append(out.isEmpty() ? "" : ", ")
                    .append(String.format("%.0f reach", entry.humours().quintessence()));
        }

        return out.toString();
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        renderBackground(context);
        super.render(context, mouseX, mouseY, delta);
        drawMouseoverTooltip(context, mouseX, mouseY);
    }
}
