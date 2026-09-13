package net.ppekkungz.crafops.interaction;

import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.math.Vec3d;
import net.ppekkungz.crafops.config.TogetherConfig;
import net.ppekkungz.crafops.network.TogetherPackets;

import java.util.*;

/** Server-authoritative chain topology, consent queue, and smooth movement controller. */
public final class InteractionManager {
    private static final InteractionManager INSTANCE = new InteractionManager();
    private final List<ChainState> chains = new ArrayList<>();
    private final Map<UUID, ChainState> chainByPlayer = new HashMap<>();
    private final Map<UUID, Deque<PendingHandHoldRequest>> requestsByRecipient = new HashMap<>();
    private final Map<UUID, Long> lastRequestAt = new HashMap<>();

    public static InteractionManager getInstance() { return INSTANCE; }
    private InteractionManager() {}

    public boolean isConnected(UUID player) { return chainByPlayer.containsKey(player); }
    public boolean isAdjacent(UUID first, UUID second) {
        ChainState chain = chainByPlayer.get(first);
        if (chain == null || chain != chainByPlayer.get(second)) return false;
        return Math.abs(chain.players.indexOf(first) - chain.players.indexOf(second)) == 1;
    }
    public Collection<HandHoldComponent> getAllComponents() {
        List<HandHoldComponent> result = new ArrayList<>();
        for (ChainState chain : chains) result.addAll(chain.links);
        return Collections.unmodifiableList(result);
    }

    /** Handles a right-click. The server repeats every security and topology check on acceptance. */
    public boolean requestConnection(ServerPlayerEntity sender, ServerPlayerEntity recipient) {
        String invalid = validateConnection(sender, recipient);
        if (invalid != null) { sender.sendMessage(Text.translatable(invalid), true); return false; }
        if (!TogetherConfig.get().requireConsent) return connectNow(sender, recipient);

        long now = System.currentTimeMillis();
        long last = lastRequestAt.getOrDefault(sender.getUuid(), 0L);
        if (now - last < TogetherConfig.get().requestCooldownMillis) {
            sender.sendMessage(Text.translatable("message.crafops_together.request_cooldown"), true);
            return false;
        }
        Deque<PendingHandHoldRequest> queue = requestsByRecipient.computeIfAbsent(recipient.getUuid(), key -> new ArrayDeque<>());
        purgeExpired(queue, now);
        queue.removeIf(request -> request.sender().equals(sender.getUuid()));
        while (queue.size() >= TogetherConfig.get().maxPendingRequests) queue.removeFirst();
        PendingHandHoldRequest request = new PendingHandHoldRequest(UUID.randomUUID(), sender.getUuid(), recipient.getUuid(),
                now + TogetherConfig.get().requestTimeoutSeconds * 1000L);
        queue.addLast(request);
        lastRequestAt.put(sender.getUuid(), now);
        TogetherPackets.sendIncomingRequest(recipient, request, sender.getGameProfile().getName());
        sender.sendMessage(Text.translatable("message.crafops_together.request_sent", recipient.getDisplayName()), true);
        return true;
    }

    public boolean respond(ServerPlayerEntity recipient, UUID requestId, boolean accepted) {
        Deque<PendingHandHoldRequest> queue = requestsByRecipient.get(recipient.getUuid());
        if (queue == null) return false;
        long now = System.currentTimeMillis();
        purgeExpired(queue, now);
        PendingHandHoldRequest chosen = null;
        for (PendingHandHoldRequest request : queue) if (request.id().equals(requestId)) { chosen = request; break; }
        if (chosen == null) { recipient.sendMessage(Text.translatable("message.crafops_together.request_expired"), true); return false; }
        queue.remove(chosen);
        ServerPlayerEntity sender = recipient.getServer().getPlayerManager().getPlayer(chosen.sender());
        if (!accepted) {
            if (sender != null) sender.sendMessage(Text.translatable("message.crafops_together.request_declined", recipient.getDisplayName()), true);
            return true;
        }
        if (sender == null) { recipient.sendMessage(Text.translatable("message.crafops_together.player_unavailable"), true); return false; }
        String invalid = validateConnection(sender, recipient);
        if (invalid != null) { recipient.sendMessage(Text.translatable(invalid), true); return false; }
        return connectNow(sender, recipient);
    }

    public boolean respondLatest(ServerPlayerEntity recipient, boolean accepted, UUID senderFilter) {
        Deque<PendingHandHoldRequest> queue = requestsByRecipient.get(recipient.getUuid());
        if (queue == null) return false;
        purgeExpired(queue, System.currentTimeMillis());
        Iterator<PendingHandHoldRequest> it = queue.descendingIterator();
        while (it.hasNext()) {
            PendingHandHoldRequest request = it.next();
            if (senderFilter == null || request.sender().equals(senderFilter)) return respond(recipient, request.id(), accepted);
        }
        return false;
    }

    private String validateConnection(ServerPlayerEntity sender, ServerPlayerEntity recipient) {
        if (sender == recipient || !sender.isAlive() || !recipient.isAlive()) return "message.crafops_together.invalid_target";
        if (sender.getWorld() != recipient.getWorld()) return "message.crafops_together.player_unavailable";
        if (sender.squaredDistanceTo(recipient) > TogetherConfig.get().interactionDistance * TogetherConfig.get().interactionDistance)
            return "message.crafops_together.too_far";
        ChainState a = chainByPlayer.get(sender.getUuid()), b = chainByPlayer.get(recipient.getUuid());
        if (a != null && a == b) return "message.crafops_together.already_same_chain";
        if (a != null && !a.isEndpoint(sender.getUuid())) return "message.crafops_together.not_endpoint";
        if (b != null && !b.isEndpoint(recipient.getUuid())) return "message.crafops_together.target_not_endpoint";
        int size = (a == null ? 1 : a.players.size()) + (b == null ? 1 : b.players.size());
        if (size > TogetherConfig.get().maxPlayersPerChain) return "message.crafops_together.chain_full";
        return null;
    }

    private boolean connectNow(ServerPlayerEntity sender, ServerPlayerEntity recipient) {
        String invalid = validateConnection(sender, recipient);
        if (invalid != null) { sender.sendMessage(Text.translatable(invalid), true); return false; }
        ChainState first = chainByPlayer.get(sender.getUuid()), second = chainByPlayer.get(recipient.getUuid());
        List<UUID> firstPlayers = first == null ? List.of(sender.getUuid()) : first.players;
        List<UUID> secondPlayers = second == null ? List.of(recipient.getUuid()) : second.players;
        List<UUID> merged = ChainTopology.merge(firstPlayers, sender.getUuid(), secondPlayers, recipient.getUuid(), TogetherConfig.get().maxPlayersPerChain);
        removeChain(first); if (second != first) removeChain(second);
        addChain(new ChainState(merged, sender.getWorld().getTime()));
        play(sender, SoundEvents.ENTITY_EXPERIENCE_ORB_PICKUP, 0.5f, 1.2f);
        TogetherPackets.broadcastSnapshot(sender.getServer());
        return true;
    }

    public boolean disconnectLink(ServerPlayerEntity actor, UUID other) {
        ChainState chain = chainByPlayer.get(actor.getUuid());
        if (chain == null) return false;
        int a = chain.players.indexOf(actor.getUuid()), b = chain.players.indexOf(other);
        if (Math.abs(a - b) != 1) return false;
        splitAt(chain, Math.min(a, b));
        play(actor, SoundEvents.BLOCK_WOOL_PLACE, 0.4f, 0.9f);
        TogetherPackets.broadcastSnapshot(actor.getServer());
        return true;
    }

    public boolean disconnectPlayer(ServerPlayerEntity player) {
        ChainState chain = chainByPlayer.get(player.getUuid());
        if (chain == null) return false;
        List<List<UUID>> pieces = ChainTopology.removePlayer(chain.players, player.getUuid());
        removeChain(chain); addIfConnected(pieces.get(0), player.getWorld().getTime()); addIfConnected(pieces.get(1), player.getWorld().getTime());
        play(player, SoundEvents.BLOCK_WOOL_PLACE, 0.4f, 0.9f);
        TogetherPackets.broadcastSnapshot(player.getServer());
        return true;
    }

    private void splitAt(ChainState chain, int linkIndex) {
        List<List<UUID>> pieces = ChainTopology.splitLink(chain.players, linkIndex);
        long tick = chain.createdAtTick;
        removeChain(chain); addIfConnected(pieces.get(0), tick); addIfConnected(pieces.get(1), tick);
    }

    public void onServerTick(MinecraftServer server) {
        long now = System.currentTimeMillis();
        requestsByRecipient.values().forEach(queue -> purgeExpired(queue, now));
        List<UUID[]> broken = new ArrayList<>();
        for (ChainState chain : new ArrayList<>(chains)) {
            for (HandHoldComponent link : chain.links) {
                ServerPlayerEntity leader = server.getPlayerManager().getPlayer(link.getLeaderUuid());
                ServerPlayerEntity follower = server.getPlayerManager().getPlayer(link.getFollowerUuid());
                if (leader == null || follower == null || !leader.isAlive() || !follower.isAlive() || leader.getWorld() != follower.getWorld()) {
                    broken.add(new UUID[]{link.getLeaderUuid(), link.getFollowerUuid()}); continue;
                }
                double distance = leader.getPos().distanceTo(follower.getPos());
                if (distance > TogetherConfig.get().disconnectDistance) { broken.add(new UUID[]{link.getLeaderUuid(), link.getFollowerUuid()}); continue; }
                applyHandHoldConstraint(leader, follower);
            }
        }
        if (!broken.isEmpty()) {
            for (UUID[] pair : broken) removePairWithoutSync(pair[0], pair[1]);
            TogetherPackets.broadcastSnapshot(server);
        }
    }

    private void applyHandHoldConstraint(ServerPlayerEntity leader, ServerPlayerEntity follower) {
        TogetherConfig cfg = TogetherConfig.get();
        float yawRadians = (float) Math.toRadians(leader.bodyYaw);
        double forwardX = -Math.sin(yawRadians);
        double forwardZ = Math.cos(yawRadians);
        double rightX = -Math.cos(yawRadians);
        double rightZ = -Math.sin(yawRadians);

        Vec3d goal = new Vec3d(
                leader.getX() + rightX * cfg.formationSideOffset - forwardX * cfg.formationBehindOffset,
                leader.getY(),
                leader.getZ() + rightZ * cfg.formationSideOffset - forwardZ * cfg.formationBehindOffset
        );
        Vec3d error = goal.subtract(follower.getPos());
        double horizontalError = Math.sqrt(error.x * error.x + error.z * error.z);
        Vec3d followerVelocity = follower.getVelocity();
        Vec3d leaderVelocity = leader.getVelocity();

        double correctionSpeed = horizontalError <= cfg.formationTolerance ? 0.0
                : Math.min(cfg.formationMaxCorrectionSpeed, cfg.formationSpringStrength * horizontalError);
        double correctionX = horizontalError < 0.0001 ? 0.0 : error.x / horizontalError * correctionSpeed;
        double correctionZ = horizontalError < 0.0001 ? 0.0 : error.z / horizontalError * correctionSpeed;
        double desiredX = leaderVelocity.x * cfg.leaderVelocityMatch + correctionX;
        double desiredZ = leaderVelocity.z * cfg.leaderVelocityMatch + correctionZ;
        double addX = clamp(desiredX - followerVelocity.x, -cfg.formationAcceleration, cfg.formationAcceleration);
        double addZ = clamp(desiredZ - followerVelocity.z, -cfg.formationAcceleration, cfg.formationAcceleration);
        double addY = 0;
        if (follower.isOnGround() && error.y > 0.45) addY = cfg.stepAssistVelocity;
        follower.addVelocity(addX, addY, addZ);
        follower.velocityModified = true;

        // Align only the rendered body, never the camera/head direction. This keeps both arm tips facing the same way.
        follower.bodyYaw = leader.bodyYaw;
    }

    private void removePairWithoutSync(UUID first, UUID second) {
        ChainState chain = chainByPlayer.get(first);
        if (chain == null) return;
        int a = chain.players.indexOf(first), b = chain.players.indexOf(second);
        if (Math.abs(a - b) == 1) splitAt(chain, Math.min(a, b));
    }

    public void handlePlayerLeaving(ServerPlayerEntity player) {
        requestsByRecipient.remove(player.getUuid());
        requestsByRecipient.values().forEach(q -> q.removeIf(r -> r.sender().equals(player.getUuid())));
        disconnectPlayer(player);
    }

    public void disconnectAll(MinecraftServer server) {
        chains.clear(); chainByPlayer.clear(); requestsByRecipient.clear(); lastRequestAt.clear();
        TogetherPackets.broadcastSnapshot(server);
    }

    public String getDebugInfo() {
        StringBuilder out = new StringBuilder("Chains: ").append(chains.size()).append(", links: ").append(getAllComponents().size())
                .append(", pending requests: ").append(requestsByRecipient.values().stream().mapToInt(Deque::size).sum());
        for (int i = 0; i < chains.size(); i++) out.append("\n#").append(i + 1).append(' ').append(chains.get(i).players);
        return out.toString();
    }

    private void purgeExpired(Deque<PendingHandHoldRequest> queue, long now) { queue.removeIf(r -> r.expired(now)); }
    private void play(ServerPlayerEntity p, net.minecraft.sound.SoundEvent sound, float volume, float pitch) {
        if (TogetherConfig.get().enableSoundEffects) p.getWorld().playSound(null, p.getBlockPos(), sound, SoundCategory.PLAYERS, volume, pitch);
    }
    private void addIfConnected(List<UUID> players, long tick) { if (players.size() >= 2) addChain(new ChainState(players, tick)); }
    private void addChain(ChainState chain) { chains.add(chain); for (UUID player : chain.players) chainByPlayer.put(player, chain); }
    private void removeChain(ChainState chain) { if (chain == null) return; chains.remove(chain); for (UUID p : chain.players) chainByPlayer.remove(p, chain); }
    private static double clamp(double v, double min, double max) { return Math.max(min, Math.min(max, v)); }

    private static final class ChainState {
        final List<UUID> players;
        final List<HandHoldComponent> links = new ArrayList<>();
        final long createdAtTick;
        ChainState(List<UUID> players, long tick) {
            this.players = new ArrayList<>(players); this.createdAtTick = tick;
            for (int i = 0; i + 1 < players.size(); i++) links.add(new HandHoldComponent(players.get(i), players.get(i + 1), tick));
        }
        boolean isEndpoint(UUID player) { return players.get(0).equals(player) || players.get(players.size() - 1).equals(player); }
    }
}
