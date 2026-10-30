package net.ppekkungz.crafops.mixin.client;

import net.minecraft.client.Minecraft;
import net.neoforged.fml.loading.FMLLoader;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Dev-only: names each test client's window after its player, so several clients are easy to tell apart. */
@Mixin(Minecraft.class)
public abstract class WindowTitleMixin {
    @Inject(method = "createTitle", at = @At("RETURN"), cancellable = true)
    private void crafopsTogether$namePlayer(CallbackInfoReturnable<String> cir) {
        if (FMLLoader.isProduction()) return;
        cir.setReturnValue("[" + ((Minecraft) (Object) this).getUser().getName() + "] " + cir.getReturnValue());
    }
}
