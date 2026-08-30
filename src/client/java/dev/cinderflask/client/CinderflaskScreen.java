package dev.cinderflask.client;

import dev.cinderflask.Cinderflask;
import dev.cinderflask.config.CinderflaskConfig;
import dev.cinderflask.item.CinderflaskItem;
import dev.cinderflask.item.FuelTimes;
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

        int max = CinderflaskConfig.get().maxEmbers;
        int embers = CinderflaskItem.getEmbers(handler.getFlask());
        int filled = max <= 0 ? 0 : MathHelper.ceil(BAR_WIDTH * MathHelper.clamp((float) embers / max, 0.0F, 1.0F));

        context.drawTexture(TEXTURE, x + BAR_X, y + BAR_Y, 0, BAR_V_EMPTY, BAR_WIDTH, BAR_HEIGHT);

        if (filled > 0) {
            context.drawTexture(TEXTURE, x + BAR_X, y + BAR_Y, 0, BAR_V_FULL, filled, BAR_HEIGHT);
        }
    }

    @Override
    protected void drawForeground(DrawContext context, int mouseX, int mouseY) {
        ItemStack flask = handler.getFlask();
        int embers = CinderflaskItem.getEmbers(flask);

        drawCentered(context, title, 6);

        // One readout, not two: smelts by default, raw ticks while the detail modifier is held.
        Text readout = CinderflaskItem.detailModifierHeld.getAsBoolean()
                ? Text.translatable("cinderflask.gui.embers_ticks", CinderflaskItem.format(embers))
                : Text.translatable("cinderflask.gui.embers",
                        CinderflaskItem.format(CinderflaskItem.operationsRemaining(flask)));
        drawCentered(context, readout, 58);

        context.drawText(textRenderer, playerInventoryTitle, playerInventoryTitleX, playerInventoryTitleY,
                0xFF4B4B57, false);
    }

    private void drawCentered(DrawContext context, Text text, int y) {
        context.drawText(textRenderer, text, (backgroundWidth - textRenderer.getWidth(text)) / 2, y,
                0xFFD9D2C4, false);
    }

    /**
     * Adds what a hovered fuel is worth to its own tooltip, so you can weigh a stack before feeding
     * it in. Mirrors the original canister screen.
     */
    @Override
    protected void drawMouseoverTooltip(DrawContext context, int x, int y) {
        if (!handler.getCursorStack().isEmpty() || focusedSlot == null || !focusedSlot.hasStack()) {
            super.drawMouseoverTooltip(context, x, y);
            return;
        }

        ItemStack hovered = focusedSlot.getStack();
        List<Text> lines = getTooltipFromItem(client, hovered);

        if (CinderflaskItem.isValidFuel(hovered)) {
            int perItem = FuelTimes.of(hovered);
            int perStack = perItem * hovered.getCount();
            int perOperation = CinderflaskConfig.get().ticksPerOperation;

            if (CinderflaskItem.detailModifierHeld.getAsBoolean()) {
                lines.add(Text.translatable("cinderflask.tooltip.worth_ticks",
                        CinderflaskItem.format(perItem)).formatted(Formatting.GOLD));
                lines.add(Text.translatable("cinderflask.tooltip.worth_ticks_stack",
                        CinderflaskItem.format(perStack)).formatted(Formatting.GOLD));
            } else {
                lines.add(Text.translatable("cinderflask.tooltip.worth",
                        CinderflaskItem.format(perItem / perOperation)).formatted(Formatting.GOLD));
                lines.add(Text.translatable("cinderflask.tooltip.worth_stack",
                        CinderflaskItem.format(perStack / perOperation)).formatted(Formatting.GOLD));
            }
        }

        context.drawTooltip(textRenderer, lines, hovered.getTooltipData(), x, y);
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        renderBackground(context);
        super.render(context, mouseX, mouseY, delta);
        drawMouseoverTooltip(context, mouseX, mouseY);
    }
}
