package net.ppekkungz.crafops.client.gui;

import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.text.Text;
import net.ppekkungz.crafops.client.network.TogetherClientPackets;
import org.lwjgl.glfw.GLFW;

/** Small non-pausing modal. Screen dispatch gives its keys priority over normal gameplay bindings. */
public final class HandHoldRequestScreen extends Screen {
    private final HandHoldRequestQueue.Request request;
    private int selected;
    private boolean answered;
    HandHoldRequestScreen(HandHoldRequestQueue.Request request) {
        super(Text.translatable("screen.crafops_together.request.title")); this.request = request;
    }
    @Override public boolean shouldPause() { return false; }
    @Override public void tick() {
        if (request.expiresAtMillis() <= System.currentTimeMillis()) finish(false);
    }
    @Override public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        int boxWidth = 250, left = (width - boxWidth) / 2, top = height / 2 - 55;
        context.fill(left, top, left + boxWidth, top + 110, 0xD0101010);
        context.drawCenteredTextWithShadow(textRenderer, title, width / 2, top + 12, 0xFFFFFF);
        context.drawCenteredTextWithShadow(textRenderer, Text.translatable("screen.crafops_together.request.from", request.senderName()), width / 2, top + 30, 0xDDDDDD);
        drawChoice(context, left + 16, top + 53, boxWidth - 32, Text.translatable("screen.crafops_together.request.accept"), selected == 0, 0x55AA55);
        drawChoice(context, left + 16, top + 76, boxWidth - 32, Text.translatable("screen.crafops_together.request.decline"), selected == 1, 0xCC5555);
        context.drawCenteredTextWithShadow(textRenderer, Text.translatable("screen.crafops_together.request.hint"), width / 2, top + 98, 0x999999);
    }
    private void drawChoice(DrawContext c, int x, int y, int w, Text text, boolean active, int color) {
        c.fill(x, y, x + w, y + 19, active ? 0xAA444444 : 0x55222222);
        c.drawCenteredTextWithShadow(textRenderer, text, x + w / 2, y + 5, active ? color : 0xAAAAAA);
    }
    @Override public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (keyCode == GLFW.GLFW_KEY_UP || keyCode == GLFW.GLFW_KEY_DOWN) { selected = 1 - selected; return true; }
        if (keyCode == GLFW.GLFW_KEY_ENTER || keyCode == GLFW.GLFW_KEY_KP_ENTER || keyCode == GLFW.GLFW_KEY_F) { finish(selected == 0); return true; }
        if (keyCode == GLFW.GLFW_KEY_ESCAPE) { finish(false); return true; }
        return true;
    }
    @Override public boolean mouseScrolled(double mouseX, double mouseY, double amount) { selected = amount > 0 ? 0 : 1; return true; }
    @Override public boolean mouseClicked(double mouseX, double mouseY, int button) {
        int top = height / 2 - 55;
        if (mouseY >= top + 53 && mouseY < top + 72) { selected = 0; finish(true); return true; }
        if (mouseY >= top + 76 && mouseY < top + 95) { selected = 1; finish(false); return true; }
        return true;
    }
    @Override public void close() { if (!answered) finish(false); }
    private void finish(boolean accepted) {
        if (answered) return; answered = true;
        TogetherClientPackets.respond(request.id(), accepted);
        HandHoldRequestQueue.completed(request);
        if (client != null) client.setScreen(null);
    }
}
