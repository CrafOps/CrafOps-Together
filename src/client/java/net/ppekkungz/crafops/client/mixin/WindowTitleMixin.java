package net.ppekkungz.crafops.client.mixin;

import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.MinecraftClient;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Dev-only: names each test client's window after its player, so several clients are easy to tell apart. */
@Mixin(MinecraftClient.class)
public abstract class WindowTitleMixin {
    @Inject(method = "getWindowTitle", at = @At("RETURN"), cancellable = true)
    private void crafopsTogether$namePlayer(CallbackInfoReturnable<String> cir) {
        if (!FabricLoader.getInstance().isDevelopmentEnvironment()) return;
        cir.setReturnValue("[" + ((MinecraftClient) (Object) this).getSession().getUsername() + "] " + cir.getReturnValue());
    }
}
