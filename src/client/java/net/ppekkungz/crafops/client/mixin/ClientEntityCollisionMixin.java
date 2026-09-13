package net.ppekkungz.crafops.client.mixin;

import net.minecraft.entity.Entity;
import net.minecraft.entity.player.PlayerEntity;
import net.ppekkungz.crafops.client.cache.ClientConnectionCache;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Mirrors adjacent-player collision suppression for remote client prediction. */
@Mixin(Entity.class)
public abstract class ClientEntityCollisionMixin {
    @Inject(method = "pushAwayFrom", at = @At("HEAD"), cancellable = true)
    private void crafopsTogether$cancelConnectedPush(Entity other, CallbackInfo ci) {
        Entity self = (Entity) (Object) this;
        if (self instanceof PlayerEntity && other instanceof PlayerEntity &&
                ClientConnectionCache.isAdjacent(self.getUuid(), other.getUuid())) ci.cancel();
    }
}
