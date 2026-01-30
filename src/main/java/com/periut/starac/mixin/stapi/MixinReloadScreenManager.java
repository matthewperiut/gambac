package com.periut.starac.mixin.stapi;

import net.modificationstation.stationapi.api.client.resource.ReloadScreenManager;
import org.lwjgl.LWJGLException;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(value = ReloadScreenManager.class, remap = false)
public class MixinReloadScreenManager {
    @Inject(method = "openEarly", at = @At("HEAD"), cancellable = true)
    private static void starac_noopOpenEarly(CallbackInfo ci) throws LWJGLException {
        ci.cancel();
    }

    @Inject(method = "isReloadComplete", at = @At("HEAD"), cancellable = true)
    private static void starac_alwaysComplete(CallbackInfoReturnable<Boolean> cir) {
        cir.setReturnValue(true);
    }
}
