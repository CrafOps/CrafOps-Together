package net.ppekkungz.crafops.client.gui;

import net.minecraft.client.Minecraft;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.network.chat.Component;
import net.minecraft.ChatFormatting;
import net.ppekkungz.crafops.client.cache.ClientConnectionCache;
import net.ppekkungz.crafops.client.network.ClientPayloadHandlers;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;
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

    private static KeyMapping acceptKey;
    private static KeyMapping declineKey;
    private static UUID visibleRequestId;
    private static long visibleSinceMillis;
    private static Leaving leaving;

    public static void registerKeys(RegisterKeyMappingsEvent event) {
        acceptKey = new KeyMapping(
                "key.crafops_together.accept_request", InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_H,
                "key.categories.crafops_together");
        declineKey = new KeyMapping(
                "key.crafops_together.decline_request", InputConstants.Type.KEYSYM, InputConstants.UNKNOWN.getValue(),
                "key.categories.crafops_together");
        event.register(acceptKey);
        event.register(declineKey);
    }

    public static void enqueue(UUID id, UUID sender, String senderName, long lifetimeMillis) {
        QUEUE.removeIf(old -> old.sender().equals(sender));
        while (QUEUE.size() >= MAX_REQUESTS) QUEUE.removeFirst();
        QUEUE.addLast(new Request(id, sender, senderName, System.currentTimeMillis() + lifetimeMillis, lifetimeMillis));
        refreshVisibleRequest();
    }

    public static void tick(Minecraft client) {
        long now = System.currentTimeMillis();
        Request head = QUEUE.peekFirst();
        if (head != null && head.expiresAtMillis() <= now) leaving = new Leaving(head, now, false);
        QUEUE.removeIf(request -> request.expiresAtMillis() <= now);
        if (client.player != null) removeConnected(client.player.getUUID(), ClientConnectionCache.getLinks());
        refreshVisibleRequest();

        if (acceptKey == null || declineKey == null) return;
        if (client.screen != null) {
            while (acceptKey.consumeClick()) { /* consume presses made while a screen is open */ }
            while (declineKey.consumeClick()) { /* consume presses made while a screen is open */ }
            return;
        }
        if (acceptKey.consumeClick()) answerVisible(true);
        if (declineKey.consumeClick()) answerVisible(false);
    }

    public static void render(GuiGraphics context, DeltaTracker delta) {
        Minecraft client = Minecraft.getInstance();
        if (client.options.hideGui || acceptKey == null) return;
        long now = System.currentTimeMillis();
        int baseY = context.guiHeight() - 72;

        if (leaving != null) {
            float t = (now - leaving.since()) / (float) FADE_OUT_MILLIS;
            if (t >= 1f) leaving = null;
            else drawPrompt(context, client.font, leaving.request(), now, 1f - easeOut(t),
                    baseY - Math.round((leaving.accepted() ? -RISE_PIXELS : RISE_PIXELS) * easeOut(t)), 0);
        }

        Request request = QUEUE.peekFirst();
        if (request == null) return;
        float in = easeOut(Math.min(1f, (now - visibleSinceMillis) / (float) FADE_IN_MILLIS));
        drawPrompt(context, client.font, request, now, in, baseY + Math.round(RISE_PIXELS * (1f - in)), QUEUE.size() - 1);
    }

    private static void drawPrompt(GuiGraphics context, Font font, Request request, long now, float alpha, int y, int more) {
        int a = Math.round(alpha * 255f);
        if (a < 8) return;
        long remaining = Math.max(0L, request.expiresAtMillis() - now);
        int seconds = (int) Math.ceil(remaining / 1000.0);
        Component line = Component.translatable("hud.crafops_together.request",
                Component.literal(request.senderName()).withStyle(s -> s.withColor(ACCENT)),
                Component.literal("[").append(acceptKey.getTranslatedKeyMessage()).append("]").withStyle(ChatFormatting.YELLOW),
                seconds);
        int width = font.width(line);
        int x = (context.guiWidth() - width) / 2;

        int backdrop = Math.round(alpha * 0x70) << 24;
        context.fill(x - 6, y - 4, x + width + 6, y + font.lineHeight + 4, backdrop);
        float life = request.lifetimeMillis() <= 0 ? 0f : Math.min(1f, remaining / (float) request.lifetimeMillis());
        int barWidth = Math.round((width + 12) * life);
        context.fill(x - 6, y + font.lineHeight + 3, x - 6 + barWidth, y + font.lineHeight + 4, (a << 24) | ACCENT);
        context.drawString(font, line, x, y, (a << 24) | 0xFFFFFF, true);

        if (more > 0) {
            Component extra = Component.translatable("hud.crafops_together.request.more", more);
            context.drawString(font, extra, (context.guiWidth() - font.width(extra)) / 2,
                    y + font.lineHeight + 8, (Math.round(alpha * 0xA0) << 24) | 0xBBBBBB, true);
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
        ClientPayloadHandlers.respond(request.id(), accepted);
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
