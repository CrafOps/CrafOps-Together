package net.ppekkungz.crafops.client.mixin;

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

/** Makes every client render the follower from the same canonical leader-relative position. */
@Mixin(PlayerEntityRenderer.class)
public abstract class PlayerEntityRendererMixin {
    @Inject(method = "render(Lnet/minecraft/client/network/AbstractClientPlayerEntity;FFLnet/minecraft/client/util/math/MatrixStack;Lnet/minecraft/client/render/VertexConsumerProvider;I)V",
            at = @At("HEAD"))
    private void crafopsTogether$alignRender(AbstractClientPlayerEntity player, float yaw, float tickDelta,
                                              MatrixStack matrices, VertexConsumerProvider vertices, int light,
                                              CallbackInfo ci) {
        var link = ClientConnectionCache.incomingLink(player.getUuid());
        if (link == null || player.getWorld() == null) return;
        var rawLeader = player.getWorld().getPlayerByUuid(link.leader());
        if (!(rawLeader instanceof AbstractClientPlayerEntity leader)) return;

        float progress = ArmAnimationState.left(player.getUuid());
        Vec3d expected = HandHoldVisuals.expectedFollowerPosition(leader, tickDelta, link);
        Vec3d actual = HandHoldVisuals.interpolatedPosition(player, tickDelta);
        Vec3d correction = expected.subtract(actual);
        double horizontal = Math.sqrt(correction.x * correction.x + correction.z * correction.z);
        double maximum = ClientConnectionCache.maxVisualPositionCorrection();
        if (horizontal > maximum && horizontal > 0.0001) correction = correction.multiply(maximum / horizontal);
        matrices.translate(correction.x * progress, 0, correction.z * progress);

        player.prevBodyYaw = leader.prevBodyYaw;
        player.bodyYaw = leader.bodyYaw;
    }
}
