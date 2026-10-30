package net.ppekkungz.crafops.client.gui;

import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.ppekkungz.crafops.client.cache.ClientConnectionCache;
import net.ppekkungz.crafops.client.network.TogetherClientPackets;
import org.lwjgl.glfw.GLFW;

import java.util.*;

/**
 * Non-modal "X reached out a hand to you! Press [H] to hold it (9s)" prompt above the hotbar.
 * One key takes the hand; letting the countdown run out (or the optional decline key) leaves it.
 */
public final class HandHoldRequestQueue {
    private static final int MAX_REQUESTS = 5;
    private static final long FADE_IN_MILLIS = 180L;
    private static final long FADE_OUT_MILLIS = 220L;
    private static final int RISE_PIXELS = 6;
    private static final int ACCENT = 0xF29BD9;
    private static final Deque<Request> QUEUE = new ArrayDeque<>();

    private static KeyBinding acceptKey;
    private static KeyBinding declineKey;
    private static UUID visibleRequestId;
    private static long visibleSinceMillis;
    private static Leaving leaving;

    public static void registerKeys() {
        acceptKey = KeyBindingHelper.registerKeyBinding(new KeyBinding(
                "key.crafops_together.accept_request", InputUtil.Type.KEYSYM, GLFW.GLFW_KEY_H,
                "key.categories.crafops_together"));
        declineKey = KeyBindingHelper.registerKeyBinding(new KeyBinding(
                "key.crafops_together.decline_request", InputUtil.Type.KEYSYM, GLFW.GLFW_KEY_UNKNOWN,
                "key.categories.crafops_together"));
    }

    public static void enqueue(UUID id, UUID sender, String senderName, long lifetimeMillis) {
        QUEUE.removeIf(old -> old.sender().equals(sender));
        while (QUEUE.size() >= MAX_REQUESTS) QUEUE.removeFirst();
        QUEUE.addLast(new Request(id, sender, senderName, System.currentTimeMillis() + lifetimeMillis, lifetimeMillis));
        refreshVisibleRequest();
    }

    public static void tick(MinecraftClient client) {
        long now = System.currentTimeMillis();
        Request head = QUEUE.peekFirst();
        if (head != null && head.expiresAtMillis() <= now) leaving = new Leaving(head, now, false);
        QUEUE.removeIf(request -> request.expiresAtMillis() <= now);
        if (client.player != null) removeConnected(client.player.getUuid(), ClientConnectionCache.getLinks());
        refreshVisibleRequest();

        if (acceptKey == null || declineKey == null) return;
        if (client.currentScreen != null) {
            while (acceptKey.wasPressed()) { /* consume presses made while a screen is open */ }
            while (declineKey.wasPressed()) { /* consume presses made while a screen is open */ }
            return;
        }
        if (acceptKey.wasPressed()) answerVisible(true);
        if (declineKey.wasPressed()) answerVisible(false);
    }

    public static void render(DrawContext context, float tickDelta) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.options.hudHidden || acceptKey == null) return;
        long now = System.currentTimeMillis();
        int baseY = context.getScaledWindowHeight() - 72;

        if (leaving != null) {
            float t = (now - leaving.since()) / (float) FADE_OUT_MILLIS;
            if (t >= 1f) leaving = null;
            else drawPrompt(context, client.textRenderer, leaving.request(), now, 1f - easeOut(t),
                    baseY - Math.round((leaving.accepted() ? -RISE_PIXELS : RISE_PIXELS) * easeOut(t)), 0);
        }

        Request request = QUEUE.peekFirst();
        if (request == null) return;
        float in = easeOut(Math.min(1f, (now - visibleSinceMillis) / (float) FADE_IN_MILLIS));
        drawPrompt(context, client.textRenderer, request, now, in, baseY + Math.round(RISE_PIXELS * (1f - in)), QUEUE.size() - 1);
    }

    private static void drawPrompt(DrawContext context, TextRenderer font, Request request, long now, float alpha, int y, int more) {
        int a = Math.round(alpha * 255f);
        if (a < 8) return;
        long remaining = Math.max(0L, request.expiresAtMillis() - now);
        int seconds = (int) Math.ceil(remaining / 1000.0);
        Text line = Text.translatable("hud.crafops_together.request",
                Text.literal(request.senderName()).styled(s -> s.withColor(ACCENT)),
                Text.literal("[").append(acceptKey.getBoundKeyLocalizedText()).append("]").formatted(Formatting.YELLOW),
                seconds);
        int width = font.getWidth(line);
        int x = (context.getScaledWindowWidth() - width) / 2;

        int backdrop = Math.round(alpha * 0x70) << 24;
        context.fill(x - 6, y - 4, x + width + 6, y + font.fontHeight + 4, backdrop);
        float life = request.lifetimeMillis() <= 0 ? 0f : Math.min(1f, remaining / (float) request.lifetimeMillis());
        int barWidth = Math.round((width + 12) * life);
        context.fill(x - 6, y + font.fontHeight + 3, x - 6 + barWidth, y + font.fontHeight + 4, (a << 24) | ACCENT);
        context.drawTextWithShadow(font, line, x, y, (a << 24) | 0xFFFFFF);

        if (more > 0) {
            Text extra = Text.translatable("hud.crafops_together.request.more", more);
            context.drawTextWithShadow(font, extra, (context.getScaledWindowWidth() - font.getWidth(extra)) / 2,
                    y + font.fontHeight + 8, (Math.round(alpha * 0xA0) << 24) | 0xBBBBBB);
        }
    }

    public static void removeConnected(UUID localPlayer, Collection<ClientConnectionCache.Link> links) {
        QUEUE.removeIf(request -> links.stream().anyMatch(link ->
                link.leader().equals(request.sender()) && link.follower().equals(localPlayer)
                        || link.follower().equals(request.sender()) && link.leader().equals(localPlayer)));
        refreshVisibleRequest();
    }

    public static void clear() {
        QUEUE.clear();
        visibleRequestId = null;
        visibleSinceMillis = 0L;
        leaving = null;
    }

    private static void answerVisible(boolean accepted) {
        Request request = QUEUE.pollFirst();
        if (request == null) return;
        TogetherClientPackets.respond(request.id(), accepted);
        leaving = new Leaving(request, System.currentTimeMillis(), accepted);
        refreshVisibleRequest();
    }

    private static void refreshVisibleRequest() {
        Request visible = QUEUE.peekFirst();
        UUID nextId = visible == null ? null : visible.id();
        if (!Objects.equals(visibleRequestId, nextId)) {
            visibleRequestId = nextId;
            visibleSinceMillis = System.currentTimeMillis();
        }
    }

    private static float easeOut(float t) { return 1f - (1f - t) * (1f - t) * (1f - t); }

    public record Request(UUID id, UUID sender, String senderName, long expiresAtMillis, long lifetimeMillis) {}
    private record Leaving(Request request, long since, boolean accepted) {}
    private HandHoldRequestQueue() {}
}
