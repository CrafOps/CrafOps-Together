package net.ppekkungz.crafops.client.gui;

import net.minecraft.client.MinecraftClient;
import java.util.*;

/** Client presentation queue. The server independently owns expiry and acceptance. */
public final class HandHoldRequestQueue {
    public record Request(UUID id, UUID sender, String senderName, long expiresAtMillis) {}
    private static final Deque<Request> QUEUE = new ArrayDeque<>();
    public static void enqueue(Request request) {
        QUEUE.removeIf(old -> old.sender().equals(request.sender()));
        while (QUEUE.size() >= 5) QUEUE.removeFirst();
        QUEUE.addLast(request);
    }
    public static void tick(MinecraftClient client) {
        QUEUE.removeIf(r -> r.expiresAtMillis() <= System.currentTimeMillis());
        if (client.currentScreen == null && !QUEUE.isEmpty()) client.setScreen(new HandHoldRequestScreen(QUEUE.peekFirst()));
    }
    static void completed(Request request) { QUEUE.removeIf(r -> r.id().equals(request.id())); }
    public static void clear() { QUEUE.clear(); }
    private HandHoldRequestQueue() {}
}
