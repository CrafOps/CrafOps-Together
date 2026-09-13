package net.ppekkungz.crafops.interaction;
import java.util.UUID;
public record PendingHandHoldRequest(UUID id, UUID sender, UUID recipient, long expiresAtMillis) {
    public boolean expired(long now) { return now >= expiresAtMillis; }
}
