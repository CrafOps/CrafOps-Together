package net.ppekkungz.crafops.client.mixin;

import net.minecraft.client.network.AbstractClientPlayerEntity;
import net.minecraft.client.render.entity.model.BipedEntityModel;
import net.minecraft.entity.LivingEntity;
import net.ppekkungz.crafops.client.animation.ArmAnimationState;
import net.ppekkungz.crafops.client.cache.ClientConnectionCache;
import net.ppekkungz.crafops.interaction.HeadPoseGeometry;
import net.ppekkungz.crafops.interaction.HandPoseGeometry;
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
        ClientConnectionCache.Link incoming = ClientConnectionCache.incomingLink(player.getUuid());
        ClientConnectionCache.Link outgoing = ClientConnectionCache.outgoingLink(player.getUuid());

        if (incoming != null) {
            float safeHeadYaw = HeadPoseGeometry.clampRelativeYaw(headYaw, 80.0f);
            model.head.yaw = (float) Math.toRadians(safeHeadYaw);
        }

        HandPoseGeometry.ArmAngles leftPose = HandPoseGeometry.angles(ClientConnectionCache.formationSideOffset(),
                incoming == null ? ClientConnectionCache.formationBehindOffset() : ClientConnectionCache.effectiveBehindOffset(incoming),
                ClientConnectionCache.visualHandGap(), false,
                incoming == null ? 0.0 : ClientConnectionCache.followerArmLift(incoming));
        HandPoseGeometry.ArmAngles rightPose = HandPoseGeometry.angles(ClientConnectionCache.formationSideOffset(),
                outgoing == null ? ClientConnectionCache.formationBehindOffset() : ClientConnectionCache.effectiveBehindOffset(outgoing),
                ClientConnectionCache.visualHandGap(), true,
                outgoing == null ? 0.0 : ClientConnectionCache.leaderArmLift(outgoing));
        model.leftArm.pitch = lerp(model.leftArm.pitch, leftPose.pitch(), left);
        model.leftArm.roll = lerp(model.leftArm.roll, leftPose.roll(), left);
        model.rightArm.pitch = lerp(model.rightArm.pitch, rightPose.pitch(), right);
        model.rightArm.roll = lerp(model.rightArm.roll, rightPose.roll(), right);
    }
    private static float lerp(float a, float b, float t) { return a + (b - a) * t; }
}
