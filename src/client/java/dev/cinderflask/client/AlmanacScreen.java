package dev.cinderflask.client;

import com.mojang.blaze3d.systems.RenderSystem;
import dev.cinderflask.brew.Almanac;
import dev.cinderflask.brew.Humours;
import dev.cinderflask.player.Palate;
import dev.cinderflask.player.PalateSync;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.render.BufferBuilder;
import net.minecraft.client.render.GameRenderer;
import net.minecraft.client.render.Tessellator;
import net.minecraft.client.render.VertexFormat;
import net.minecraft.client.render.VertexFormats;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.math.MathHelper;
import org.jetbrains.annotations.Nullable;
import org.joml.Matrix4f;

import java.util.ArrayList;
import java.util.List;

/**
 * The Almanac: one map you drag around, rather than a book you page through.
 *
 * <p>What is readable is decided by {@link Palate}, the same thing that decides how much a flask's
 * tooltip gives away. A locked node still occupies its place on the map, so the shape of the system
 * is legible from the first minute and there is visibly something left to earn.
 */
public class AlmanacScreen extends Screen {
    private static final int HALF = Almanac.NODE / 2;
    // A wide range on purpose: the map is far larger than a GUI-scaled screen, so it has to go from
    // "the whole thing at once" to "read one corner comfortably".
    private static final float MIN_ZOOM = 0.25f;
    private static final float MAX_ZOOM = 3.0f;
    private static final float DEFAULT_ZOOM = 0.55f;

    /** Multiplicative, so a notch feels the same at either end of that range. */
    private static final float ZOOM_STEP = 1.18f;

    private static final int PANEL_MAX_WIDTH = 200;
    private static final int PANEL_PAD = 8;

    private static final int EDGE_COLOUR = 0xFF4A4A5C;
    private static final int OPEN_FRAME = 0xFFE08A2A;
    private static final int LOCKED_FRAME = 0xFF4A4A5C;
    private static final int NODE_BACK = 0xFF1F1F29;
    private static final int LOCKED_BACK = 0xC8181820;
    private static final int BACKDROP = 0xFF14121A;

    private static final int TOOLTIP_WIDTH = 220;
    private static final int TOOLTIP_LINES = 3;

    private Almanac.Map map = new Almanac.Map(List.of(), List.of(), List.of());
    private Palate palate = Palate.empty();

    private double panX;
    private double panY;
    private float zoom = DEFAULT_ZOOM;

    @Nullable
    private Almanac.Node selected;

    public AlmanacScreen() {
        super(Text.translatable("item.cinderflask.almanac"));
    }

    @Override
    protected void init() {
        MinecraftClient client = MinecraftClient.getInstance();
        map = Almanac.build(client.world == null ? null : client.world.getRecipeManager());
        palate = PalateSync.local();
    }

    @Override
    public boolean shouldPause() {
        return false;
    }

    // -------------------------------------------------------------------------------------------
    // Where things are
    // -------------------------------------------------------------------------------------------

    private double originX() {
        return width / 2.0 + panX;
    }

    private double originY() {
        return height / 2.0 + panY;
    }

    private double screenX(int mapX) {
        return originX() + mapX * zoom;
    }

    private double screenY(int mapY) {
        return originY() + mapY * zoom;
    }

    @Nullable
    private Almanac.Node nodeAt(double mouseX, double mouseY) {
        double x = (mouseX - originX()) / zoom;
        double y = (mouseY - originY()) / zoom;

        for (Almanac.Node node : map.nodes()) {
            if (Math.abs(x - node.x()) <= HALF && Math.abs(y - node.y()) <= HALF) {
                return node;
            }
        }

        return null;
    }

    // -------------------------------------------------------------------------------------------
    // Input
    // -------------------------------------------------------------------------------------------

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double dx, double dy) {
        panX += dx;
        panY += dy;
        return true;
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double amount) {
        float previous = zoom;
        zoom = MathHelper.clamp(zoom * (float) Math.pow(ZOOM_STEP, amount), MIN_ZOOM, MAX_ZOOM);

        // Zoom towards the cursor, so the thing you are looking at stays where you are looking.
        double scale = zoom / previous;
        panX = mouseX - (mouseX - panX - width / 2.0) * scale - width / 2.0;
        panY = mouseY - (mouseY - panY - height / 2.0) * scale - height / 2.0;

        return true;
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (keyCode == org.lwjgl.glfw.GLFW.GLFW_KEY_SPACE) {
            panX = 0;
            panY = 0;
            zoom = DEFAULT_ZOOM;
            return true;
        }

        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        Almanac.Node clicked = nodeAt(mouseX, mouseY);
        if (clicked != null) {
            selected = clicked.gate().isOpen(palate) ? clicked : null;
            return true;
        }

        selected = null;
        return super.mouseClicked(mouseX, mouseY, button);
    }

    // -------------------------------------------------------------------------------------------
    // Drawing
    // -------------------------------------------------------------------------------------------

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        // Opaque, not the usual translucent dim. This screen is a page, and the world showing
        // through a page of diagrams makes both of them unreadable.
        context.fill(0, 0, width, height, BACKDROP);
        context.fillGradient(0, 0, width, height, 0x00000000, 0x40000000);

        for (Almanac.Region region : map.regions()) {
            drawRegion(context, region);
        }

        drawEdges(context);
        for (Almanac.Node node : map.nodes()) {
            drawNode(context, node);
        }


        if (selected != null) {
            drawPanel(context, selected);
        }

        context.drawText(textRenderer, Text.translatable("cinderflask.almanac.hint")
                .formatted(Formatting.DARK_GRAY), PANEL_PAD, height - 14, 0xFF6A6A78, false);

        Almanac.Node hovered = nodeAt(mouseX, mouseY);
        if (hovered != null) {
            context.drawTooltip(textRenderer, describe(hovered), mouseX, mouseY);
        }
    }

    /** One pass of quads, so a map with a hundred edges still costs one draw call. */
    private void drawEdges(DrawContext context) {
        Matrix4f matrix = context.getMatrices().peek().getPositionMatrix();
        Tessellator tessellator = Tessellator.getInstance();
        BufferBuilder buffer = tessellator.getBuffer();

        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.setShader(GameRenderer::getPositionColorProgram);
        buffer.begin(VertexFormat.DrawMode.QUADS, VertexFormats.POSITION_COLOR);

        float alpha = ((EDGE_COLOUR >> 24) & 0xFF) / 255f;
        float red = ((EDGE_COLOUR >> 16) & 0xFF) / 255f;
        float green = ((EDGE_COLOUR >> 8) & 0xFF) / 255f;
        float blue = (EDGE_COLOUR & 0xFF) / 255f;

        for (Almanac.Edge edge : map.edges()) {
            Almanac.Node from = map.node(edge.from());
            Almanac.Node to = map.node(edge.to());

            if (from == null || to == null) {
                continue;
            }

            float x1 = (float) screenX(from.x());
            float y1 = (float) screenY(from.y());
            float x2 = (float) screenX(to.x());
            float y2 = (float) screenY(to.y());

            float dx = x2 - x1;
            float dy = y2 - y1;
            float length = MathHelper.sqrt(dx * dx + dy * dy);

            if (length < 0.001f) {
                continue;
            }

            // A quad two pixels wide, laid along the edge.
            float nx = -dy / length * 1.0f;
            float ny = dx / length * 1.0f;

            buffer.vertex(matrix, x1 - nx, y1 - ny, 0).color(red, green, blue, alpha).next();
            buffer.vertex(matrix, x1 + nx, y1 + ny, 0).color(red, green, blue, alpha).next();
            buffer.vertex(matrix, x2 + nx, y2 + ny, 0).color(red, green, blue, alpha).next();
            buffer.vertex(matrix, x2 - nx, y2 - ny, 0).color(red, green, blue, alpha).next();
        }

        tessellator.draw();
        RenderSystem.disableBlend();
    }

    private void drawNode(DrawContext context, Almanac.Node node) {
        boolean open = node.gate().isOpen(palate);

        int half = Math.max(6, Math.round(HALF * zoom));
        int x = (int) Math.round(screenX(node.x()));
        int y = (int) Math.round(screenY(node.y()));

        // Off-screen nodes still cost a fill each otherwise, and the map is wider than any window.
        if (x + half < 0 || x - half > width || y + half < 0 || y - half > height) {
            return;
        }

        context.fill(x - half, y - half, x + half, y + half, NODE_BACK);
        drawFrame(context, x - half, y - half, x + half, y + half,
                open ? OPEN_FRAME : LOCKED_FRAME);

        MatrixStack matrices = context.getMatrices();
        matrices.push();
        matrices.translate(x - 8.0 * zoom, y - 8.0 * zoom, 0);
        matrices.scale(zoom, zoom, 1);
        context.drawItem(node.icon(), 0, 0);
        matrices.pop();

        if (!open) {
            // Dimmed rather than hidden: the shape of the map should read before you have earned it.
            context.fill(x - half, y - half, x + half, y + half, LOCKED_BACK);
        }

        if (selected == node) {
            drawFrame(context, x - half - 2, y - half - 2, x + half + 2, y + half + 2, 0xFFF3CC66);
        }
    }

    private void drawRegion(DrawContext context, Almanac.Region region) {
        int x = (int) Math.round(screenX(region.x()));
        int y = (int) Math.round(screenY(region.y()));

        Text title = Text.translatable(region.titleKey());
        int half = textRenderer.getWidth(title) / 2;

        if (x + half < 0 || x - half > width || y < -20 || y > height) {
            return;
        }

        context.drawText(textRenderer, title, x - half, y, 0xFF6E5A3C, false);
    }

    private static void drawFrame(DrawContext context, int left, int top, int right, int bottom,
                                  int colour) {
        context.fill(left, top, right, top + 1, colour);
        context.fill(left, bottom - 1, right, bottom, colour);
        context.fill(left, top, left + 1, bottom, colour);
        context.fill(right - 1, top, right, bottom, colour);
    }

    /** The panel a clicked node opens. Screen space, so panning does not carry it off the edge. */
    private void drawPanel(DrawContext context, Almanac.Node node) {
        // Never more than a third of the screen: at GUI scale 2 a fixed 200 covered half the map.
        int panel = Math.min(PANEL_MAX_WIDTH, width / 3);
        int left = width - panel;

        context.fill(left, 0, width, height, 0xF50E0D14);
        context.fill(left, 0, left + 1, height, 0xFFE08A2A);

        int y = PANEL_PAD;
        context.drawText(textRenderer, Text.translatable(node.titleKey()).formatted(Formatting.GOLD),
                left + PANEL_PAD, y, 0xFFF3CC66, false);
        y += 14;

        for (var line : textRenderer.wrapLines(Text.translatable(node.bodyKey()),
                panel - PANEL_PAD * 2)) {
            context.drawText(textRenderer, line, left + PANEL_PAD, y, 0xFFB9B4C0, false);
            y += 10;
        }
    }

    /**
     * What hovering tells you. The map deliberately carries no labels — on the wheel the nodes sit
     * closer together than their names are wide — so this is where a node says what it is.
     */
    private List<Text> describe(Almanac.Node node) {
        List<Text> lines = new ArrayList<>(4);

        if (!node.gate().isOpen(palate)) {
            lines.add(Text.translatable(node.titleKey()).formatted(Formatting.DARK_GRAY));
            lines.add(Text.translatable("cinderflask.almanac.locked",
                    Text.translatable(axisName(node.gate().axis()))).formatted(Formatting.DARK_RED));
            return lines;
        }

        lines.add(Text.translatable(node.titleKey()).formatted(Formatting.GOLD));

        // The opening of the entry, so hovering is worth something on its own and clicking is a
        // choice rather than the only way to find out what a node is.
        List<net.minecraft.text.OrderedText> body =
                textRenderer.wrapLines(Text.translatable(node.bodyKey()), TOOLTIP_WIDTH);

        for (int i = 0; i < Math.min(TOOLTIP_LINES, body.size()); i++) {
            lines.add(net.minecraft.text.Text.of(flatten(body.get(i))
                    + (i == TOOLTIP_LINES - 1 && body.size() > TOOLTIP_LINES ? "..." : "")));
        }

        if (body.size() > TOOLTIP_LINES) {
            lines.add(Text.translatable("cinderflask.almanac.more").formatted(Formatting.DARK_GRAY));
        }

        return lines;
    }

    /** {@link net.minecraft.text.OrderedText} has no plain-string accessor of its own. */
    private static String flatten(net.minecraft.text.OrderedText ordered) {
        StringBuilder out = new StringBuilder();
        ordered.accept((index, style, codePoint) -> {
            out.appendCodePoint(codePoint);
            return true;
        });
        return out.toString();
    }

    private static String axisName(int axis) {
        return axis >= Humours.WHEEL
                ? "cinderflask.humour.quintessence"
                : switch (axis) {
            case 0 -> "cinderflask.humour.choleric";
            case 1 -> "cinderflask.humour.melancholic";
            case 2 -> "cinderflask.humour.sanguine";
            default -> "cinderflask.humour.phlegmatic";
        };
    }
}
