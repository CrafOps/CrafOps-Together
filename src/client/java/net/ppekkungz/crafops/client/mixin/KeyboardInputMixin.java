package net.ppekkungz.crafops.client.mixin;

import net.minecraft.client.input.Input;
import net.minecraft.client.input.KeyboardInput;
import net.ppekkungz.crafops.client.movement.FollowerController;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Lets a led player walk with their partner through the normal movement pipeline. */
@Mixin(KeyboardInput.class)
public abstract class KeyboardInputMixin {
    @Inject(method = "tick", at = @At("TAIL"))
    private void crafopsTogether$followLeader(boolean slowDown, float slowDownFactor, CallbackInfo ci) {
        FollowerController.steer((Input) (Object) this, slowDown, slowDownFactor);
    }
}
