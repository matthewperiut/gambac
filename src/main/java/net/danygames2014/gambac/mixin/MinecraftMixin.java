package net.danygames2014.gambac.mixin;

import java.awt.*;

import net.minecraft.client.Minecraft;
import org.lwjgl.glfw.GLFW;
import org.lwjgl.opengl.Display;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Minecraft.class)
public abstract class MinecraftMixin {

    @Shadow
    public Canvas canvas;

    @Shadow
    private boolean fullscreen;

    @Shadow
    public int width;

    @Shadow
    public int height;

    @Shadow
    protected abstract void resize(int width, int height);

    @Redirect(method = "<init>", at = @At(value = "FIELD", target = "Lnet/minecraft/client/Minecraft;canvas:Ljava/awt/Canvas;"))
    private void noCanvas(Minecraft instance, Canvas value) {
    }

    @Inject(method = "init", at = @At(value = "FIELD", target = "Lnet/minecraft/client/Minecraft;canvas:Ljava/awt/Canvas;", ordinal = 0))
    private void setCanvasNull(CallbackInfo ci) {
        if (canvas != null) {
            canvas.setVisible(false);
        }
        canvas = null;
    }

    // Force update screen size at end of init
    @Inject(method = "init", at = @At("TAIL"))
    private void forceUpdateScreenSize(CallbackInfo ci) {
        GLFW.glfwPollEvents();
        this.width = Display.getWidth();
        this.height = Display.getHeight();
        if (this.width <= 0) {
            this.width = 1;
        }

        if (this.height <= 0) {
            this.height = 1;
        }

        this.resize(this.width, this.height);
    }

    // Resize callback in run loop
    @Inject(method = "run", at = @At(value = "FIELD", target = "Lnet/minecraft/client/Minecraft;canvas:Ljava/awt/Canvas;", remap = false, ordinal = 1))
    private void resizeCallback(CallbackInfo ci) {
        if ((Display.getWidth() != this.width || Display.getHeight() != this.height)) {
            this.width = Display.getWidth();
            this.height = Display.getHeight();
            if (this.width <= 0) {
                this.width = 1;
            }

            if (this.height <= 0) {
                this.height = 1;
            }
            this.resize(this.width, this.height);
        }
    }

    // Prevent un-fullscreen on unfocus
    @Redirect(method = "run", at = @At(value = "INVOKE", target = "Lorg/lwjgl/opengl/Display;isActive()Z", remap = false))
    private boolean noUnFullscreenOnUnfocus() {
        return true;
    }

    // Cancel the license check thread that makes HTTP request to dead URL
    @Inject(method = "initLicenseCheckThread", at = @At("HEAD"), cancellable = true)
    private void killHttpRequestToDeadUrl(CallbackInfo ci) {
        ci.cancel();
    }
}
