package net.ppekkungz.crafops.interaction;

import net.minecraft.particle.ParticleEffect;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvent;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;
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
        purgeExpired(queue, now, null);
        queue.removeIf(request -> request.sender().equals(sender.getUuid()));
        while (queue.size() >= TogetherConfig.get().maxPendingRequests) queue.removeFirst();
        long timeout = TogetherConfig.get().requestTimeoutSeconds * 1000L;
        PendingHandHoldRequest request = new PendingHandHoldRequest(UUID.randomUUID(), sender.getUuid(), recipient.getUuid(), now + timeout);
        queue.addLast(request);
        lastRequestAt.put(sender.getUuid(), now);

        if (TogetherPackets.canReceiveRequests(recipient)) {
            TogetherPackets.sendIncomingRequest(recipient, request, sender.getGameProfile().getName(), timeout);
        } else {
            recipient.sendMessage(Text.translatable("message.crafops_together.request_fallback", sender.getDisplayName())
                    .formatted(Formatting.LIGHT_PURPLE), false);
        }
        sender.sendMessage(Text.translatable("message.crafops_together.request_sent", recipient.getDisplayName())
                .formatted(Formatting.LIGHT_PURPLE), true);
        play(sender, SoundEvents.BLOCK_AMETHYST_BLOCK_CHIME, 0.35f, 1.4f);
        return true;
    }

    public boolean respond(ServerPlayerEntity recipient, UUID requestId, boolean accepted) {
        Deque<PendingHandHoldRequest> queue = requestsByRecipient.get(recipient.getUuid());
        if (queue == null) return false;
        purgeExpired(queue, System.currentTimeMillis(), recipient.getServer());
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
        purgeExpired(queue, System.currentTimeMillis(), recipient.getServer());
        Iterator<PendingHandHoldRequest> it = queue.descendingIterator();
        while (it.hasNext()) {
            PendingHandHoldRequest request = it.next();
            if (senderFilter == null || request.sender().equals(senderFilter)) return respond(recipient, request.id(), accepted);
        }
        return false;
    }

    private String validateConnection(ServerPlayerEntity sender, ServerPlayerEntity recipient) {
        if (sender == recipient || !canHoldHands(sender) || !canHoldHands(recipient)) return "message.crafops_together.invalid_target";
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

        sender.sendMessage(Text.translatable("message.crafops_together.now_holding", recipient.getDisplayName()).formatted(Formatting.LIGHT_PURPLE), true);
        recipient.sendMessage(Text.translatable("message.crafops_together.now_holding", sender.getDisplayName()).formatted(Formatting.LIGHT_PURPLE), true);
        Vec3d middle = sender.getPos().add(recipient.getPos()).multiply(0.5).add(0, 1.1, 0);
        particles(sender.getServerWorld(), ParticleTypes.HEART, middle, 5, 0.35, 0.2);
        play(sender, SoundEvents.ENTITY_EXPERIENCE_ORB_PICKUP, 0.45f, 1.5f);
        TogetherPackets.broadcastSnapshot(sender.getServer());
        return true;
    }

    /** Debug: links without a request, first placing the follower on the leader's right. Returns an error key or null. */
    public String debugConnect(ServerPlayerEntity leader, ServerPlayerEntity follower) {
        if (leader == follower) return "message.crafops_together.invalid_target";
        double yaw = Math.toRadians(leader.bodyYaw), side = TogetherConfig.get().formationSideOffset;
        follower.teleport(leader.getServerWorld(), leader.getX() - Math.cos(yaw) * side, leader.getY(),
                leader.getZ() - Math.sin(yaw) * side, leader.getYaw(), follower.getPitch());
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

    public boolean disconnectLink(ServerPlayerEntity actor, UUID other) {
        ChainState chain = chainByPlayer.get(actor.getUuid());
        if (chain == null) return false;
        int a = chain.players.indexOf(actor.getUuid()), b = chain.players.indexOf(other);
        if (Math.abs(a - b) != 1) return false;
        splitAt(chain, Math.min(a, b));
        notifyLetGo(actor, other);
        play(actor, SoundEvents.BLOCK_WOOL_PLACE, 0.4f, 0.9f);
        TogetherPackets.broadcastSnapshot(actor.getServer());
        return true;
    }

    public boolean disconnectPlayer(ServerPlayerEntity player) {
        ChainState chain = chainByPlayer.get(player.getUuid());
        if (chain == null) return false;
        int index = chain.players.indexOf(player.getUuid());
        List<UUID> neighbours = new ArrayList<>(2);
        if (index > 0) neighbours.add(chain.players.get(index - 1));
        if (index + 1 < chain.players.size()) neighbours.add(chain.players.get(index + 1));
        List<List<UUID>> pieces = ChainTopology.removePlayer(chain.players, player.getUuid());
        removeChain(chain); addIfConnected(pieces.get(0), chain.createdAtTick); addIfConnected(pieces.get(1), chain.createdAtTick);
        for (UUID neighbour : neighbours) notifyLetGo(player, neighbour);
        play(player, SoundEvents.BLOCK_WOOL_PLACE, 0.4f, 0.9f);
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
                ServerPlayerEntity leader = server.getPlayerManager().getPlayer(link.getLeaderUuid());
                ServerPlayerEntity follower = server.getPlayerManager().getPlayer(link.getFollowerUuid());
                if (leader == null || follower == null || !canHoldHands(leader) || !canHoldHands(follower)
                        || leader.getWorld() != follower.getWorld()
                        || leader.getPos().distanceTo(follower.getPos()) > TogetherConfig.get().disconnectDistance) {
                    removePairWithoutSync(link.getLeaderUuid(), link.getFollowerUuid());
                    if (leader != null && follower != null) notifyLetGo(leader, follower.getUuid());
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
            ServerPlayerEntity player = server.getPlayerManager().getPlayer(id);
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
        List<ServerPlayerEntity> members = new ArrayList<>(chain.players.size());
        for (UUID id : chain.players) {
            ServerPlayerEntity player = server.getPlayerManager().getPlayer(id);
            if (player == null || !canHoldHands(player)) return false;
            members.add(player);
        }
        if (!isSpread(members, cfg.disconnectDistance)) return false;

        ServerPlayerEntity jumper = null;
        for (ServerPlayerEntity member : members) {
            if (!jumpedThisTick(member, cfg.teleportDetectDistance)) continue;
            if (jumper != null) return false;
            jumper = member;
        }
        if (jumper == null) return false;

        ServerWorld world = jumper.getServerWorld();
        double yaw = Math.toRadians(jumper.bodyYaw);
        double rightX = -Math.cos(yaw), rightZ = -Math.sin(yaw);
        int jumperIndex = members.indexOf(jumper);
        for (int i = 0; i < members.size(); i++) {
            ServerPlayerEntity member = members.get(i);
            if (member == jumper) continue;
            double offset = (i - jumperIndex) * cfg.formationSideOffset;
            Vec3d target = new Vec3d(jumper.getX() + rightX * offset, jumper.getY(), jumper.getZ() + rightZ * offset);
            if (!world.isSpaceEmpty(member, member.getBoundingBox().offset(target.subtract(member.getPos())))) target = jumper.getPos();
            if (cfg.enableParticles) particles(member.getServerWorld(), ParticleTypes.REVERSE_PORTAL, member.getPos().add(0, 1, 0), 24, 0.3, 0.6);
            member.teleport(world, target.x, target.y, target.z, jumper.getYaw(), member.getPitch());
            member.fallDistance = 0;
            member.setVelocity(Vec3d.ZERO);
            member.velocityModified = true;
        }
        particles(world, ParticleTypes.REVERSE_PORTAL, jumper.getPos().add(0, 1, 0), 32, 0.8, 0.6);
        world.playSound(null, jumper.getBlockPos(), SoundEvents.ENTITY_ENDERMAN_TELEPORT, SoundCategory.PLAYERS, 0.35f, 1.3f);
        return true;
    }

    private static boolean isSpread(List<ServerPlayerEntity> members, double distance) {
        for (int i = 0; i + 1 < members.size(); i++) {
            ServerPlayerEntity a = members.get(i), b = members.get(i + 1);
            if (a.getWorld() != b.getWorld() || a.getPos().distanceTo(b.getPos()) > distance) return true;
        }
        return false;
    }

    private boolean jumpedThisTick(ServerPlayerEntity player, double threshold) {
        Anchor anchor = lastAnchor.get(player.getUuid());
        if (anchor == null) return false;
        return anchor.world != player.getWorld() || anchor.position.squaredDistanceTo(player.getPos()) > threshold * threshold;
    }

    private void recordAnchors(MinecraftServer server) {
        lastAnchor.keySet().retainAll(chainByPlayer.keySet());
        for (UUID id : chainByPlayer.keySet()) {
            ServerPlayerEntity player = server.getPlayerManager().getPlayer(id);
            if (player != null) lastAnchor.put(id, new Anchor(player.getWorld(), player.getPos()));
        }
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
        lastRequestAt.remove(player.getUuid());
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
                ServerPlayerEntity player = server.getPlayerManager().getPlayer(id);
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
            ServerPlayerEntity sender = server.getPlayerManager().getPlayer(request.sender());
            ServerPlayerEntity recipient = server.getPlayerManager().getPlayer(request.recipient());
            if (sender != null && recipient != null)
                sender.sendMessage(Text.translatable("message.crafops_together.request_timed_out", recipient.getDisplayName()), true);
        }
    }

    private void notifyLetGo(ServerPlayerEntity actor, UUID other) {
        ServerPlayerEntity partner = actor.getServer().getPlayerManager().getPlayer(other);
        if (partner == null) return;
        actor.sendMessage(Text.translatable("message.crafops_together.let_go", partner.getDisplayName()), true);
        partner.sendMessage(Text.translatable("message.crafops_together.let_go", actor.getDisplayName()), true);
    }

    private static boolean canHoldHands(ServerPlayerEntity player) {
        return player.isAlive() && !player.isSpectator() && !player.isSleeping() && !player.hasVehicle() && !player.isFallFlying();
    }
    private void play(ServerPlayerEntity p, SoundEvent sound, float volume, float pitch) {
        if (TogetherConfig.get().enableSoundEffects) p.getWorld().playSound(null, p.getBlockPos(), sound, SoundCategory.PLAYERS, volume, pitch);
    }
    private static void particles(ServerWorld world, ParticleEffect type, Vec3d at, int count, double spread, double speed) {
        if (TogetherConfig.get().enableParticles) world.spawnParticles(type, at.x, at.y, at.z, count, spread, spread * 0.6, spread, speed * 0.1);
    }
    private void addIfConnected(List<UUID> players, long tick) { if (players.size() >= 2) addChain(new ChainState(players, tick)); }
    private void addChain(ChainState chain) { chains.add(chain); for (UUID player : chain.players) chainByPlayer.put(player, chain); }
    private void removeChain(ChainState chain) { if (chain == null) return; chains.remove(chain); for (UUID p : chain.players) chainByPlayer.remove(p, chain); }

    private record Anchor(World world, Vec3d position) {}

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
