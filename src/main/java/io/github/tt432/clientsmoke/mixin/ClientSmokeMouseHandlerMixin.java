package io.github.tt432.clientsmoke.mixin;

import io.github.tt432.clientsmoke.config.ClientSmokeConfig;
import net.minecraft.client.MouseHandler;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@OnlyIn(Dist.CLIENT)
@Mixin(MouseHandler.class)
public class ClientSmokeMouseHandlerMixin {

    @Inject(method = "grabMouse", at = @At("HEAD"), cancellable = true)
    private void clientsmoke$preventMouseGrab(CallbackInfo ci) {
        if (ClientSmokeConfig.isEnabled() || ClientSmokeConfig.isPreventMouseGrab()) {
            ci.cancel();
        }
    }
}
