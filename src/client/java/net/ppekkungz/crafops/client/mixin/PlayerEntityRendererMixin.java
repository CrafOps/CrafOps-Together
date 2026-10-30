package net.ppekkungz.crafops.client.mixin;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.AbstractClientPlayerEntity;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.entity.PlayerEntityRenderer;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.math.Vec3d;
import net.ppekkungz.crafops.client.animation.ArmAnimationState;
import net.ppekkungz.crafops.client.cache.ClientConnectionCache;
import net.ppekkungz.crafops.client.renderer.HandHoldVisuals;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Hides network lag between partners: remote followers are drawn at the canonical leader-relative spot
 * (fading the correction out when it is too large to be lag, e.g. mid-teleport), and every follower's body
 * faces the shared formation yaw so both arms line up.
 */
@Mixin(PlayerEntityRenderer.class)
public abstract class PlayerEntityRendererMixin {
    @Inject(method = "render(Lnet/minecraft/client/network/AbstractClientPlayerEntity;FFLnet/minecraft/client/util/math/MatrixStack;Lnet/minecraft/client/render/VertexConsumerProvider;I)V",
            at = @At("HEAD"))
    private void crafopsTogether$alignRender(AbstractClientPlayerEntity player, float yaw, float tickDelta,
                                              MatrixStack matrices, VertexConsumerProvider vertices, int light,
                                              CallbackInfo ci) {
        if (player.getWorld() == null) return;
        var link = ClientConnectionCache.incomingLink(player.getUuid());
        if (link != null && player.getWorld().getPlayerByUuid(link.leader()) instanceof AbstractClientPlayerEntity leader && !player.isSwimming()) {
            float formationYaw = ClientConnectionCache.formationYaw(link, tickDelta, leader.bodyYaw);
            player.prevBodyYaw = formationYaw;
            player.bodyYaw = formationYaw;
        }
        Vec3d correction = HandHoldVisuals.renderCorrection(player, tickDelta);
        if (correction != Vec3d.ZERO) matrices.translate(correction.x, correction.y, correction.z);
    }
}
