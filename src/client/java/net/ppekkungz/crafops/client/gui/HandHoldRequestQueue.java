package net.ppekkungz.crafops.client.gui;

import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;
import net.minecraft.text.Text;
import net.ppekkungz.crafops.client.cache.ClientConnectionCache;
import net.ppekkungz.crafops.client.network.TogetherClientPackets;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayDeque;
import java.util.Collection;
import java.util.Deque;
import java.util.Objects;
import java.util.UUID;

/** Non-modal request queue rendered as a compact, configurable HUD toast. */
public final class HandHoldRequestQueue {
    private static final int MAX_REQUESTS = 5;
    private static final int TOAST_WIDTH = 184;
    private static final int TOAST_HEIGHT = 45;
    private static final long SLIDE_DURATION_MILLIS = 220L;
    private static final Deque<Request> QUEUE = new ArrayDeque<>();

    private static KeyBinding acceptKey;
    private static KeyBinding declineKey;
    private static UUID visibleRequestId;
    private static long visibleSinceMillis;

    public static void registerKeys() {
        acceptKey = KeyBindingHelper.registerKeyBinding(new KeyBinding(
                "key.crafops_together.accept_request",
                InputUtil.Type.KEYSYM,
                GLFW.GLFW_KEY_G,
                "key.categories.crafops_together"));
        declineKey = KeyBindingHelper.registerKeyBinding(new KeyBinding(
                "key.crafops_together.decline_request",
                InputUtil.Type.KEYSYM,
                GLFW.GLFW_KEY_H,
                "key.categories.crafops_together"));
    }

    public static void enqueue(Request request) {
        QUEUE.removeIf(old -> old.sender().equals(request.sender()));

        while (QUEUE.size() >= MAX_REQUESTS) {
            QUEUE.removeFirst();
        }

        QUEUE.addLast(request);
        refreshVisibleRequest();
    }

    public static void tick(MinecraftClient client) {
        QUEUE.removeIf(request -> request.expiresAtMillis() <= System.currentTimeMillis());
        removeConnected(client);
        refreshVisibleRequest();

        if (acceptKey == null || declineKey == null) {
            return;
        }

        if (client.currentScreen != null) {
            drainKeyPresses();
            return;
        }

        if (acceptKey.wasPressed()) {
            answerVisible(true);
        }

        if (declineKey.wasPressed()) {
            answerVisible(false);
        }
    }

    public static void render(DrawContext context, float tickDelta) {
        MinecraftClient client = MinecraftClient.getInstance();
        Request request = QUEUE.peekFirst();

        if (request == null || client.options.hudHidden || acceptKey == null || declineKey == null) {
            return;
        }

        long now = System.currentTimeMillis();
        float slideProgress = clamp(
                (now - visibleSinceMillis) / (float) SLIDE_DURATION_MILLIS,
                0.0f,
                1.0f);
        float easedProgress = 1.0f - (float) Math.pow(1.0f - slideProgress, 3.0f);
        int screenWidth = context.getScaledWindowWidth();
        int screenHeight = context.getScaledWindowHeight();
        int finalLeft = screenWidth - TOAST_WIDTH - 8;
        int left = finalLeft + Math.round((TOAST_WIDTH + 12) * (1.0f - easedProgress));
        int top = (screenHeight - TOAST_HEIGHT) / 2;
        int right = left + TOAST_WIDTH;
        int bottom = top + TOAST_HEIGHT;

        context.fill(left, top, right, bottom, 0xB0181818);
        context.fill(left, top, left + 2, bottom, 0xFF5FCF9B);
        context.drawTextWithShadow(
                client.textRenderer,
                Text.translatable("hud.crafops_together.request", request.senderName()),
                left + 8,
                top + 8,
                0xFFF3F3F3);

        Text hint = Text.translatable(
                "hud.crafops_together.request.keys",
                acceptKey.getBoundKeyLocalizedText(),
                declineKey.getBoundKeyLocalizedText());
        context.drawTextWithShadow(
                client.textRenderer,
                hint,
                left + 8,
                top + 24,
                0xFFB8B8B8);

        long remaining = Math.max(0L, request.expiresAtMillis() - now);
        float remainingProgress = clamp(remaining / 15000.0f, 0.0f, 1.0f);
        int progressWidth = Math.round((TOAST_WIDTH - 2) * remainingProgress);
        context.fill(left + 2, bottom - 2, left + 2 + progressWidth, bottom, 0xFF5FCF9B);
    }

    public static void removeConnected(
            UUID localPlayer,
            Collection<ClientConnectionCache.Link> links) {
        QUEUE.removeIf(request -> links.stream().anyMatch(link ->
                link.leader().equals(request.sender()) && link.follower().equals(localPlayer)
                        || link.follower().equals(request.sender()) && link.leader().equals(localPlayer)));
        refreshVisibleRequest();
    }

    public static void clear() {
        QUEUE.clear();
        visibleRequestId = null;
        visibleSinceMillis = 0L;
    }

    private static void answerVisible(boolean accepted) {
        Request request = QUEUE.pollFirst();

        if (request == null) {
            return;
        }

        TogetherClientPackets.respond(request.id(), accepted);
        refreshVisibleRequest();
    }

    private static void removeConnected(MinecraftClient client) {
        if (client.player == null) {
            return;
        }

        removeConnected(client.player.getUuid(), ClientConnectionCache.getLinks());
    }

    private static void refreshVisibleRequest() {
        Request visible = QUEUE.peekFirst();
        UUID nextId = visible == null ? null : visible.id();

        if (!Objects.equals(visibleRequestId, nextId)) {
            visibleRequestId = nextId;
            visibleSinceMillis = System.currentTimeMillis();
        }
    }

    private static void drainKeyPresses() {
        while (acceptKey.wasPressed()) {
            // Consume presses made while chat, inventory, or another screen is open.
        }

        while (declineKey.wasPressed()) {
            // Consume presses made while chat, inventory, or another screen is open.
        }
    }

    private static float clamp(float value, float minimum, float maximum) {
        return Math.max(minimum, Math.min(maximum, value));
    }

    public record Request(UUID id, UUID sender, String senderName, long expiresAtMillis) {
    }

    private HandHoldRequestQueue() {
    }
}
