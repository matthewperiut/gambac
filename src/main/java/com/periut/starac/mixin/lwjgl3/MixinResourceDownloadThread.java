package com.periut.starac.mixin.lwjgl3;

import net.minecraft.client.BackgroundDownloader;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(BackgroundDownloader.class)
public abstract class MixinResourceDownloadThread {

	@Shadow public abstract void forceReload();

	@Inject(method = "run", at = @At("HEAD"), cancellable = true)
	private void noResourceLoading(CallbackInfo ci){
		ci.cancel();
		forceReload();
	}
}