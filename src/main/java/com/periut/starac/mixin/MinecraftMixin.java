package com.periut.starac.mixin;

import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.InputStream;
import java.nio.ByteBuffer;

import net.minecraft.client.Minecraft;
import org.lwjgl.BufferUtils;
import com.periut.starac.Starac;
import org.lwjgl.glfw.GLFW;
import org.lwjgl.opengl.Display;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import javax.imageio.ImageIO;

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

    // Force update screen size right after Display is created, so the Mojang
    // loading screen renders at the correct scale on HiDPI/Retina displays
    @Inject(method = "init", at = @At(value = "INVOKE", target = "Lorg/lwjgl/opengl/Display;create()V", shift = At.Shift.AFTER, remap = false))
    private void forceUpdateScreenSizeEarly(CallbackInfo ci) {
        // Set window title immediately — before any events are polled
        try {
            if (System.getProperty("org.prismlauncher.window.title") != null && System.getProperty("staracUsePrismTitle") != null) {
                Display.setTitle(System.getProperty("org.prismlauncher.window.title"));
            } else {
                Display.setTitle(Starac.WINDOW_TITLE);
            }
        } catch (Exception ignored) {
            Display.setTitle(Starac.WINDOW_TITLE);
        }

        // Set window icon — original Minecraft used AWT Frame icons which no longer apply
        ByteBuffer[] icons = new ByteBuffer[4];
        icons[0] = starac$loadIcon("/assets/starac/icons/16.png");
        icons[1] = starac$loadIcon("/assets/starac/icons/32.png");
        icons[2] = starac$loadIcon("/assets/starac/icons/64.png");
        icons[3] = starac$loadIcon("/assets/starac/icons/256.png");
        if (icons[0] != null && icons[1] != null && icons[2] != null && icons[3] != null) {
            Display.setIcon(icons);
        }

        GLFW.glfwPollEvents();
        this.width = Display.getWidth();
        this.height = Display.getHeight();
        if (this.width <= 0) this.width = 1;
        if (this.height <= 0) this.height = 1;
    }

    private static ByteBuffer starac$loadIcon(String path) {
        try {
            InputStream stream = MinecraftMixin.class.getResourceAsStream(path);
            if (stream == null) return null;
            BufferedImage image = ImageIO.read(stream);
            int w = image.getWidth(), h = image.getHeight();
            int[] pixels = new int[w * h];
            image.getRGB(0, 0, w, h, pixels, 0, w);
            ByteBuffer buffer = BufferUtils.createByteBuffer(w * h * 4);
            for (int pixel : pixels) {
                buffer.put((byte) ((pixel >> 24) & 0xFF)); // A
                buffer.put((byte) ((pixel >> 16) & 0xFF)); // R
                buffer.put((byte) ((pixel >> 8) & 0xFF));  // G
                buffer.put((byte) (pixel & 0xFF));         // B
            }
            buffer.flip();
            return buffer;
        } catch (Exception e) {
            return null;
        }
    }

    // Also force update at end of init for good measure
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
