package net.ppekkungz.crafops.client.mixin;

import net.minecraft.client.network.AbstractClientPlayerEntity;
import net.minecraft.client.render.entity.model.BipedEntityModel;
import net.minecraft.client.model.ModelPart;
import net.minecraft.entity.LivingEntity;
import net.ppekkungz.crafops.client.animation.ArmAnimationState;
import net.ppekkungz.crafops.client.cache.ClientConnectionCache;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Blends only the hand(s) used by the current directed links. */
@Mixin(BipedEntityModel.class)
public abstract class BipedEntityModelMixin<T extends LivingEntity> {
    @Inject(method = "setAngles", at = @At("TAIL"))
    private void crafopsTogether$pose(T entity, float limbAngle, float limbDistance, float age, float headYaw, float headPitch, CallbackInfo ci) {
        if (!(entity instanceof AbstractClientPlayerEntity player)) return;
        BipedEntityModel<T> model = (BipedEntityModel<T>) (Object) this;
        float left = ArmAnimationState.left(player.getUuid()), right = ArmAnimationState.right(player.getUuid());
        if (left > 0) aimAtPartner(player, model.leftArm, ClientConnectionCache.incomingLeader(player.getUuid()), false, left);
        if (right > 0) aimAtPartner(player, model.rightArm, ClientConnectionCache.outgoingFollower(player.getUuid()), true, right);
    }
    private static void aimAtPartner(AbstractClientPlayerEntity player, ModelPart arm, java.util.UUID partnerId, boolean rightArm, float progress) {
        if (partnerId == null || player.getWorld() == null) return;
        var partner = player.getWorld().getPlayerByUuid(partnerId);
        if (partner == null) return;

        double dx = partner.getX() - player.getX();
        double dz = partner.getZ() - player.getZ();
        double yaw = Math.toRadians(player.bodyYaw);
        double forwardX = -Math.sin(yaw), forwardZ = Math.cos(yaw);
        double rightX = -Math.cos(yaw), rightZ = -Math.sin(yaw);

        // Aim from this shoulder to the exact midpoint between both body centres.
        double targetRight = (dx * rightX + dz * rightZ) * 0.5 - (rightArm ? 0.3125 : -0.3125);
        double targetForward = (dx * forwardX + dz * forwardZ) * 0.5;
        double verticalDrop = 0.68;
        float targetRoll = (float) Math.atan2(targetRight, verticalDrop);
        float targetPitch = (float) -Math.atan2(targetForward, Math.sqrt(verticalDrop * verticalDrop + targetRight * targetRight));
        targetRoll = clamp(targetRoll, -1.15f, 1.15f);
        targetPitch = clamp(targetPitch, -1.0f, 1.0f);
        arm.roll = lerp(arm.roll, targetRoll, progress);
        arm.pitch = lerp(arm.pitch, targetPitch, progress);
    }
    private static float clamp(float value, float min, float max) { return Math.max(min, Math.min(max, value)); }
    private static float lerp(float a, float b, float t) { return a + (b - a) * t; }
}
