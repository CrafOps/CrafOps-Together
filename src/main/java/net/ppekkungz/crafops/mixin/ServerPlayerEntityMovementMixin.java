package net.ppekkungz.crafops.mixin;

import net.minecraft.network.packet.c2s.play.PlayerMoveC2SPacket;
import net.minecraft.server.network.ServerPlayNetworkHandler;
import net.minecraft.server.network.ServerPlayerEntity;
import net.ppekkungz.crafops.interaction.InteractionManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Blocks follower from sending position updates to the server.
 * Rotation packets (look) are still allowed — follower can look around.
 * Position is fully controlled by InteractionManager.applyPull().
 */
@Mixin(ServerPlayNetworkHandler.class)
public class ServerPlayerEntityMovementMixin {

    @Shadow public ServerPlayerEntity player;

    @Inject(
            method = "onPlayerMove",
            at = @At("HEAD"),
            cancellable = true
    )
    private void onPlayerMove(PlayerMoveC2SPacket packet, CallbackInfo ci) {
        InteractionManager manager = InteractionManager.getInstance();

        if (!manager.isConnected(player.getUuid())) return;
        if (manager.isInitiator(player.getUuid())) return;

        // Block ALL movement packets from follower
        // Server controls both position AND rotation
        ci.cancel();
    }
}