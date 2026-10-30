package net.ppekkungz.crafops.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.*;
import net.ppekkungz.crafops.CrafOpsTogether;
import net.ppekkungz.crafops.client.animation.ArmAnimationState;
import net.ppekkungz.crafops.client.cache.ClientConnectionCache;
import net.ppekkungz.crafops.client.gui.HandHoldRequestQueue;
import net.ppekkungz.crafops.client.movement.FollowerController;
import net.ppekkungz.crafops.client.network.ClientPayloadHandlers;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

/** Client-only wiring: input, ticking, rendering and the request HUD. */
@EventBusSubscriber(modid = CrafOpsTogether.MOD_ID, value = Dist.CLIENT)
public final class CrafOpsTogetherClient {

    @SubscribeEvent
    public static void onClientTick(ClientTickEvent.Post event) {
        Minecraft client = Minecraft.getInstance();
        HandHoldRequestQueue.tick(client);
        if (client.level == null) return;
        ClientConnectionCache.tickMotion(client.level);
        FollowerController.tickAfterMovement(client);
        ClientPayloadHandlers.sendOwnState(client);
        Set<UUID> present = new HashSet<>();
        for (AbstractClientPlayer player : client.level.players()) {
            UUID id = player.getUUID();
            present.add(id);
            ArmAnimationState.tick(id, ClientConnectionCache.hasIncoming(id), ClientConnectionCache.hasOutgoing(id));
        }
        ArmAnimationState.retain(present);
    }

    /** Fired right after the local player's keys are read: lets a led player walk with their partner. */
    @SubscribeEvent
    public static void onMovementInput(MovementInputUpdateEvent event) {
        FollowerController.steer(event.getInput());
    }

    /**
     * Before a follower is drawn: face it along the pair's shared heading so both arms line up. The position
     * shift itself happens in EntityRenderDispatcherMixin, so the shadow and name tag move with the model.
     *
     * A swimming body is tilted by that player's own look pitch, and a follower's camera points wherever it
     * likes, so it would dive head-first beside a level leader. While swimming, the follower is drawn with the
     * leader's pitch; the real pitch (the follower's camera) is restored in {@link #afterRenderPlayer}.
     */
    @SubscribeEvent
    public static void beforeRenderPlayer(RenderPlayerEvent.Pre event) {
        if (!(event.getEntity() instanceof AbstractClientPlayer player)) return;
        var link = ClientConnectionCache.incomingLink(player.getUUID());
        if (link == null || !(player.level().getPlayerByUUID(link.leader()) instanceof AbstractClientPlayer leader)) return;
        if (player.isSwimming()) {
            borrowedPitch = new BorrowedPitch(player, player.getXRot(), player.xRotO);
            player.setXRot(leader.getXRot());
            player.xRotO = leader.xRotO;
        } else {
            float formationYaw = ClientConnectionCache.formationYaw(link, event.getPartialTick(), leader.yBodyRot);
            player.yBodyRotO = formationYaw;
            player.yBodyRot = formationYaw;
        }
    }

    @SubscribeEvent
    public static void afterRenderPlayer(RenderPlayerEvent.Post event) {
        BorrowedPitch borrowed = borrowedPitch;
        if (borrowed == null || borrowed.player() != event.getEntity()) return;
        borrowed.player().setXRot(borrowed.pitch());
        borrowed.player().xRotO = borrowed.pitchO();
        borrowedPitch = null;
    }

    private record BorrowedPitch(AbstractClientPlayer player, float pitch, float pitchO) {}
    private static BorrowedPitch borrowedPitch;

    @SubscribeEvent
    public static void onLoggingOut(ClientPlayerNetworkEvent.LoggingOut event) {
        ClientConnectionCache.clear();
        ArmAnimationState.clear();
        HandHoldRequestQueue.clear();
    }

    @EventBusSubscriber(modid = CrafOpsTogether.MOD_ID, value = Dist.CLIENT, bus = EventBusSubscriber.Bus.MOD)
    public static final class ModEvents {
        @SubscribeEvent
        public static void registerKeys(RegisterKeyMappingsEvent event) {
            HandHoldRequestQueue.registerKeys(event);
        }

        @SubscribeEvent
        public static void registerHud(RegisterGuiLayersEvent event) {
            event.registerAboveAll(ResourceLocation.fromNamespaceAndPath(CrafOpsTogether.MOD_ID, "hand_hold_request"), HandHoldRequestQueue::render);
        }

        private ModEvents() {}
    }

    private CrafOpsTogetherClient() {}
}
