package net.ppekkungz.crafops.mixin;

import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.ppekkungz.crafops.interaction.InteractionManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Partners holding hands stand close; stop the server from shoving them apart. */
@Mixin(Entity.class)
public abstract class EntityCollisionMixin {
    @Inject(method = "push(Lnet/minecraft/world/entity/Entity;)V", at = @At("HEAD"), cancellable = true)
    private void crafopsTogether$cancelConnectedPush(Entity other, CallbackInfo ci) {
        Entity self = (Entity) (Object) this;
        if (self.level().isClientSide()) return;
        if (self instanceof Player && other instanceof Player
                && InteractionManager.getInstance().isAdjacent(self.getUUID(), other.getUUID())) ci.cancel();
    }
}
