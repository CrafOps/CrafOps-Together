package net.ppekkungz.crafops.interaction;

import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundSource;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.network.chat.Component;
import net.minecraft.ChatFormatting;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.level.Level;
import net.ppekkungz.crafops.config.TogetherConfig;
import net.ppekkungz.crafops.network.TogetherPackets;

import java.util.*;

/**
 * Server-authoritative chain topology, consent queue and "travel together" rules.
 *
 * Movement itself is driven by the follower's own client (see the client FollowerController): pushing
 * a player's velocity from the server fights client prediction and is the main source of jitter.
 * The server only checks distance, keeps chains together through teleports, and plays feedback.
 */
public final class InteractionManager {
    private static final InteractionManager INSTANCE = new InteractionManager();
    private final List<ChainState> chains = new ArrayList<>();
    private final Map<UUID, ChainState> chainByPlayer = new HashMap<>();
    private final Map<UUID, Deque<PendingHandHoldRequest>> requestsByRecipient = new HashMap<>();
    private final Map<UUID, Long> lastRequestAt = new HashMap<>();
    private final Map<UUID, Anchor> lastAnchor = new HashMap<>();
    private final Set<UUID> flying = new HashSet<>();

    public static InteractionManager getInstance() { return INSTANCE; }
    private InteractionManager() {}

    public boolean isConnected(UUID player) { return chainByPlayer.containsKey(player); }
    public Set<UUID> getConnectedPlayers() { return Collections.unmodifiableSet(chainByPlayer.keySet()); }
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
    public boolean requestConnection(ServerPlayer sender, ServerPlayer recipient) {
        String invalid = validateConnection(sender, recipient);
        if (invalid != null) { sender.displayClientMessage(Component.translatable(invalid), true); return false; }
        if (!TogetherConfig.get().requireConsent) return connectNow(sender, recipient);

        long now = System.currentTimeMillis();
        long last = lastRequestAt.getOrDefault(sender.getUUID(), 0L);
        if (now - last < TogetherConfig.get().requestCooldownMillis) {
            sender.displayClientMessage(Component.translatable("message.crafops_together.request_cooldown"), true);
            return false;
        }
        Deque<PendingHandHoldRequest> queue = requestsByRecipient.computeIfAbsent(recipient.getUUID(), key -> new ArrayDeque<>());
        purgeExpired(queue, now, null);
        queue.removeIf(request -> request.sender().equals(sender.getUUID()));
        while (queue.size() >= TogetherConfig.get().maxPendingRequests) queue.removeFirst();
        long timeout = TogetherConfig.get().requestTimeoutSeconds * 1000L;
        PendingHandHoldRequest request = new PendingHandHoldRequest(UUID.randomUUID(), sender.getUUID(), recipient.getUUID(), now + timeout);
        queue.addLast(request);
        lastRequestAt.put(sender.getUUID(), now);

        if (TogetherPackets.canReceiveRequests(recipient)) {
            TogetherPackets.sendIncomingRequest(recipient, request, sender.getGameProfile().getName(), timeout);
        } else {
            recipient.displayClientMessage(Component.translatable("message.crafops_together.request_fallback", sender.getDisplayName())
                    .withStyle(ChatFormatting.LIGHT_PURPLE), false);
        }
        sender.displayClientMessage(Component.translatable("message.crafops_together.request_sent", recipient.getDisplayName())
                .withStyle(ChatFormatting.LIGHT_PURPLE), true);
        play(sender, SoundEvents.AMETHYST_BLOCK_CHIME, 0.35f, 1.4f);
        return true;
    }

    public boolean respond(ServerPlayer recipient, UUID requestId, boolean accepted) {
        Deque<PendingHandHoldRequest> queue = requestsByRecipient.get(recipient.getUUID());
        if (queue == null) return false;
        purgeExpired(queue, System.currentTimeMillis(), recipient.getServer());
        PendingHandHoldRequest chosen = null;
        for (PendingHandHoldRequest request : queue) if (request.id().equals(requestId)) { chosen = request; break; }
        if (chosen == null) { recipient.displayClientMessage(Component.translatable("message.crafops_together.request_expired"), true); return false; }
        queue.remove(chosen);
        ServerPlayer sender = recipient.getServer().getPlayerList().getPlayer(chosen.sender());
        if (!accepted) {
            if (sender != null) sender.displayClientMessage(Component.translatable("message.crafops_together.request_declined", recipient.getDisplayName()), true);
            return true;
        }
        if (sender == null) { recipient.displayClientMessage(Component.translatable("message.crafops_together.player_unavailable"), true); return false; }
        String invalid = validateConnection(sender, recipient);
        if (invalid != null) { recipient.displayClientMessage(Component.translatable(invalid), true); return false; }
        return connectNow(sender, recipient);
    }

    public boolean respondLatest(ServerPlayer recipient, boolean accepted, UUID senderFilter) {
        Deque<PendingHandHoldRequest> queue = requestsByRecipient.get(recipient.getUUID());
        if (queue == null) return false;
        purgeExpired(queue, System.currentTimeMillis(), recipient.getServer());
        Iterator<PendingHandHoldRequest> it = queue.descendingIterator();
        while (it.hasNext()) {
            PendingHandHoldRequest request = it.next();
            if (senderFilter == null || request.sender().equals(senderFilter)) return respond(recipient, request.id(), accepted);
        }
        return false;
    }

    private String validateConnection(ServerPlayer sender, ServerPlayer recipient) {
        if (sender == recipient || !canHoldHands(sender) || !canHoldHands(recipient)) return "message.crafops_together.invalid_target";
        if (sender.level() != recipient.level()) return "message.crafops_together.player_unavailable";
        if (sender.distanceToSqr(recipient) > TogetherConfig.get().interactionDistance * TogetherConfig.get().interactionDistance)
            return "message.crafops_together.too_far";
        ChainState a = chainByPlayer.get(sender.getUUID()), b = chainByPlayer.get(recipient.getUUID());
        if (a != null && a == b) return "message.crafops_together.already_same_chain";
        if (a != null && !a.isEndpoint(sender.getUUID())) return "message.crafops_together.not_endpoint";
        if (b != null && !b.isEndpoint(recipient.getUUID())) return "message.crafops_together.target_not_endpoint";
        int size = (a == null ? 1 : a.players.size()) + (b == null ? 1 : b.players.size());
        if (size > TogetherConfig.get().maxPlayersPerChain) return "message.crafops_together.chain_full";
        return null;
    }

    private boolean connectNow(ServerPlayer sender, ServerPlayer recipient) {
        String invalid = validateConnection(sender, recipient);
        if (invalid != null) { sender.displayClientMessage(Component.translatable(invalid), true); return false; }
        ChainState first = chainByPlayer.get(sender.getUUID()), second = chainByPlayer.get(recipient.getUUID());
        List<UUID> firstPlayers = first == null ? List.of(sender.getUUID()) : first.players;
        List<UUID> secondPlayers = second == null ? List.of(recipient.getUUID()) : second.players;
        List<UUID> merged = ChainTopology.merge(firstPlayers, sender.getUUID(), secondPlayers, recipient.getUUID(), TogetherConfig.get().maxPlayersPerChain);
        removeChain(first); if (second != first) removeChain(second);
        addChain(new ChainState(merged, sender.level().getGameTime()));

        sender.displayClientMessage(Component.translatable("message.crafops_together.now_holding", recipient.getDisplayName()).withStyle(ChatFormatting.LIGHT_PURPLE), true);
        recipient.displayClientMessage(Component.translatable("message.crafops_together.now_holding", sender.getDisplayName()).withStyle(ChatFormatting.LIGHT_PURPLE), true);
        Vec3 middle = sender.position().add(recipient.position()).scale(0.5).add(0, 1.1, 0);
        particles(sender.serverLevel(), ParticleTypes.HEART, middle, 5, 0.35, 0.2);
        play(sender, SoundEvents.EXPERIENCE_ORB_PICKUP, 0.45f, 1.5f);
        TogetherPackets.broadcastSnapshot(sender.getServer());
        return true;
    }

    /** Debug: links without a request, first placing the follower on the leader's right. Returns an error key or null. */
    public String debugConnect(ServerPlayer leader, ServerPlayer follower) {
        if (leader == follower) return "message.crafops_together.invalid_target";
        double yaw = Math.toRadians(leader.yBodyRot), side = TogetherConfig.get().formationSideOffset;
        follower.teleportTo(leader.serverLevel(), leader.getX() - Math.cos(yaw) * side, leader.getY(),
                leader.getZ() - Math.sin(yaw) * side, leader.getYRot(), follower.getXRot());
        String invalid = validateConnection(leader, follower);
        if (invalid != null) return invalid;
        connectNow(leader, follower);
        return null;
    }

    /** The link a player follows in, or else the one they lead. */
    public HandHoldComponent findLink(UUID player) {
        ChainState chain = chainByPlayer.get(player);
        if (chain == null) return null;
        HandHoldComponent leading = null;
        for (HandHoldComponent link : chain.links) {
            if (link.getFollowerUuid().equals(player)) return link;
            if (link.getLeaderUuid().equals(player)) leading = link;
        }
        return leading;
    }

    public boolean disconnectLink(ServerPlayer actor, UUID other) {
        ChainState chain = chainByPlayer.get(actor.getUUID());
        if (chain == null) return false;
        int a = chain.players.indexOf(actor.getUUID()), b = chain.players.indexOf(other);
        if (Math.abs(a - b) != 1) return false;
        splitAt(chain, Math.min(a, b));
        notifyLetGo(actor, other);
        play(actor, SoundEvents.WOOL_PLACE, 0.4f, 0.9f);
        TogetherPackets.broadcastSnapshot(actor.getServer());
        return true;
    }

    public boolean disconnectPlayer(ServerPlayer player) {
        ChainState chain = chainByPlayer.get(player.getUUID());
        if (chain == null) return false;
        int index = chain.players.indexOf(player.getUUID());
        List<UUID> neighbours = new ArrayList<>(2);
        if (index > 0) neighbours.add(chain.players.get(index - 1));
        if (index + 1 < chain.players.size()) neighbours.add(chain.players.get(index + 1));
        List<List<UUID>> pieces = ChainTopology.removePlayer(chain.players, player.getUUID());
        removeChain(chain); addIfConnected(pieces.get(0), chain.createdAtTick); addIfConnected(pieces.get(1), chain.createdAtTick);
        for (UUID neighbour : neighbours) notifyLetGo(player, neighbour);
        play(player, SoundEvents.WOOL_PLACE, 0.4f, 0.9f);
        TogetherPackets.broadcastSnapshot(player.getServer());
        return true;
    }

    private void splitAt(ChainState chain, int linkIndex) {
        List<List<UUID>> pieces = ChainTopology.splitLink(chain.players, linkIndex);
        removeChain(chain); addIfConnected(pieces.get(0), chain.createdAtTick); addIfConnected(pieces.get(1), chain.createdAtTick);
    }

    public void onServerTick(MinecraftServer server) {
        long now = System.currentTimeMillis();
        requestsByRecipient.values().forEach(queue -> purgeExpired(queue, now, server));

        boolean changed = false;
        for (ChainState chain : new ArrayList<>(chains)) {
            if (!chains.contains(chain)) continue;
            if (regroupAfterTeleport(server, chain)) continue;
            for (HandHoldComponent link : new ArrayList<>(chain.links)) {
                ServerPlayer leader = server.getPlayerList().getPlayer(link.getLeaderUuid());
                ServerPlayer follower = server.getPlayerList().getPlayer(link.getFollowerUuid());
                if (leader == null || follower == null || !canHoldHands(leader) || !canHoldHands(follower)
                        || leader.level() != follower.level()
                        || leader.position().distanceTo(follower.position()) > TogetherConfig.get().disconnectDistance) {
                    removePairWithoutSync(link.getLeaderUuid(), link.getFollowerUuid());
                    if (leader != null && follower != null) notifyLetGo(leader, follower.getUUID());
                    changed = true;
                }
            }
        }
        if (refreshFlying(server)) changed = true;
        if (changed) TogetherPackets.broadcastSnapshot(server);
        recordAnchors(server);
    }

    public Collection<UUID> getFlyingPlayers() { return Collections.unmodifiableSet(flying); }

    /** Tracks creative/ability flight of chained players; returns true when anyone took off or landed. */
    private boolean refreshFlying(MinecraftServer server) {
        Set<UUID> now = new HashSet<>();
        for (UUID id : chainByPlayer.keySet()) {
            ServerPlayer player = server.getPlayerList().getPlayer(id);
            if (player != null && player.getAbilities().flying) now.add(id);
        }
        if (now.equals(flying)) return false;
        flying.clear(); flying.addAll(now);
        return true;
    }

    /**
     * Waystones, /tp, ender pearls, portals: if exactly one member jumped away this tick, bring the
     * whole chain to them instead of breaking it, laid out in chain order along the jumper's right.
     */
    private boolean regroupAfterTeleport(MinecraftServer server, ChainState chain) {
        TogetherConfig cfg = TogetherConfig.get();
        if (!cfg.teleportTogether) return false;
        List<ServerPlayer> members = new ArrayList<>(chain.players.size());
        for (UUID id : chain.players) {
            ServerPlayer player = server.getPlayerList().getPlayer(id);
            if (player == null || !canHoldHands(player)) return false;
            members.add(player);
        }
        if (!isSpread(members, cfg.disconnectDistance)) return false;

        ServerPlayer jumper = null;
        for (ServerPlayer member : members) {
            if (!jumpedThisTick(member, cfg.teleportDetectDistance)) continue;
            if (jumper != null) return false;
            jumper = member;
        }
        if (jumper == null) return false;

        ServerLevel world = jumper.serverLevel();
        double yaw = Math.toRadians(jumper.yBodyRot);
        double rightX = -Math.cos(yaw), rightZ = -Math.sin(yaw);
        int jumperIndex = members.indexOf(jumper);
        for (int i = 0; i < members.size(); i++) {
            ServerPlayer member = members.get(i);
            if (member == jumper) continue;
            double offset = (i - jumperIndex) * cfg.formationSideOffset;
            Vec3 target = new Vec3(jumper.getX() + rightX * offset, jumper.getY(), jumper.getZ() + rightZ * offset);
            if (!world.noCollision(member, member.getBoundingBox().move(target.subtract(member.position())))) target = jumper.position();
            if (cfg.enableParticles) particles(member.serverLevel(), ParticleTypes.REVERSE_PORTAL, member.position().add(0, 1, 0), 24, 0.3, 0.6);
            member.teleportTo(world, target.x, target.y, target.z, jumper.getYRot(), member.getXRot());
            member.fallDistance = 0;
            member.setDeltaMovement(Vec3.ZERO);
            member.hurtMarked = true;
        }
        particles(world, ParticleTypes.REVERSE_PORTAL, jumper.position().add(0, 1, 0), 32, 0.8, 0.6);
        world.playSound(null, jumper.blockPosition(), SoundEvents.ENDERMAN_TELEPORT, SoundSource.PLAYERS, 0.35f, 1.3f);
        return true;
    }

    private static boolean isSpread(List<ServerPlayer> members, double distance) {
        for (int i = 0; i + 1 < members.size(); i++) {
            ServerPlayer a = members.get(i), b = members.get(i + 1);
            if (a.level() != b.level() || a.position().distanceTo(b.position()) > distance) return true;
        }
        return false;
    }

    private boolean jumpedThisTick(ServerPlayer player, double threshold) {
        Anchor anchor = lastAnchor.get(player.getUUID());
        if (anchor == null) return false;
        return anchor.world != player.level() || anchor.position.distanceToSqr(player.position()) > threshold * threshold;
    }

    private void recordAnchors(MinecraftServer server) {
        lastAnchor.keySet().retainAll(chainByPlayer.keySet());
        for (UUID id : chainByPlayer.keySet()) {
            ServerPlayer player = server.getPlayerList().getPlayer(id);
            if (player != null) lastAnchor.put(id, new Anchor(player.level(), player.position()));
        }
    }

    private void removePairWithoutSync(UUID first, UUID second) {
        ChainState chain = chainByPlayer.get(first);
        if (chain == null) return;
        int a = chain.players.indexOf(first), b = chain.players.indexOf(second);
        if (Math.abs(a - b) == 1) splitAt(chain, Math.min(a, b));
    }

    public void handlePlayerLeaving(ServerPlayer player) {
        requestsByRecipient.remove(player.getUUID());
        requestsByRecipient.values().forEach(q -> q.removeIf(r -> r.sender().equals(player.getUUID())));
        lastRequestAt.remove(player.getUUID());
        disconnectPlayer(player);
    }

    public void disconnectAll(MinecraftServer server) {
        chains.clear(); chainByPlayer.clear(); requestsByRecipient.clear(); lastRequestAt.clear(); lastAnchor.clear(); flying.clear();
        TogetherPackets.broadcastSnapshot(server);
    }

    public String getDebugInfo(MinecraftServer server) {
        StringBuilder out = new StringBuilder("Chains: ").append(chains.size()).append(", links: ").append(getAllComponents().size())
                .append(", pending requests: ").append(requestsByRecipient.values().stream().mapToInt(Deque::size).sum());
        for (int i = 0; i < chains.size(); i++) {
            List<String> names = new ArrayList<>();
            for (UUID id : chains.get(i).players) {
                ServerPlayer player = server.getPlayerList().getPlayer(id);
                String name = player == null ? id.toString() : player.getGameProfile().getName();
                names.add(flying.contains(id) ? name + " (flying)" : name);
            }
            out.append("\n#").append(i + 1).append(' ').append(String.join(" -> ", names));
        }
        return out.toString();
    }

    /** Drops expired requests, telling the sender their hand went untaken when a server is available. */
    private void purgeExpired(Deque<PendingHandHoldRequest> queue, long now, MinecraftServer server) {
        Iterator<PendingHandHoldRequest> it = queue.iterator();
        while (it.hasNext()) {
            PendingHandHoldRequest request = it.next();
            if (!request.expired(now)) continue;
            it.remove();
            if (server == null) continue;
            ServerPlayer sender = server.getPlayerList().getPlayer(request.sender());
            ServerPlayer recipient = server.getPlayerList().getPlayer(request.recipient());
            if (sender != null && recipient != null)
                sender.displayClientMessage(Component.translatable("message.crafops_together.request_timed_out", recipient.getDisplayName()), true);
        }
    }

    private void notifyLetGo(ServerPlayer actor, UUID other) {
        ServerPlayer partner = actor.getServer().getPlayerList().getPlayer(other);
        if (partner == null) return;
        actor.displayClientMessage(Component.translatable("message.crafops_together.let_go", partner.getDisplayName()), true);
        partner.displayClientMessage(Component.translatable("message.crafops_together.let_go", actor.getDisplayName()), true);
    }

    private static boolean canHoldHands(ServerPlayer player) {
        return player.isAlive() && !player.isSpectator() && !player.isSleeping() && !player.isPassenger() && !player.isFallFlying();
    }
    private void play(ServerPlayer p, SoundEvent sound, float volume, float pitch) {
        if (TogetherConfig.get().enableSoundEffects) p.level().playSound(null, p.blockPosition(), sound, SoundSource.PLAYERS, volume, pitch);
    }
    private static void particles(ServerLevel world, ParticleOptions type, Vec3 at, int count, double spread, double speed) {
        if (TogetherConfig.get().enableParticles) world.sendParticles(type, at.x, at.y, at.z, count, spread, spread * 0.6, spread, speed * 0.1);
    }
    private void addIfConnected(List<UUID> players, long tick) { if (players.size() >= 2) addChain(new ChainState(players, tick)); }
    private void addChain(ChainState chain) { chains.add(chain); for (UUID player : chain.players) chainByPlayer.put(player, chain); }
    private void removeChain(ChainState chain) { if (chain == null) return; chains.remove(chain); for (UUID p : chain.players) chainByPlayer.remove(p, chain); }

    private record Anchor(Level world, Vec3 position) {}

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
