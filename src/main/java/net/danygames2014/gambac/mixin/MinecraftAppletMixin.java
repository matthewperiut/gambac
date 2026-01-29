package net.danygames2014.gambac.mixin;

import net.danygames2014.gambac.BrnoMinecraft;
import net.minecraft.client.Minecraft;
import net.minecraft.client.MinecraftApplet;
import net.minecraft.client.Session;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import javax.swing.*;
import java.applet.Applet;
import java.awt.*;

@SuppressWarnings({"removal", "StringBufferReplaceableByString"})
@Mixin(MinecraftApplet.class)
public class MinecraftAppletMixin extends Applet {

    @Shadow
    private Minecraft minecraft;

    /**
     * @author Proudly overwritten by DanyGames2014
     * @reason because i don't give a shit
     */
    @Overwrite(remap = false)
    public void init() {
        //System.out.println("Properties:");
        //System.getProperties().forEach( (k, v) -> System.out.println(k + " : " + v));

        // PrismLauncher Window Size
        if (System.getProperty("org.prismlauncher.window.dimensions") != null) {
            String[] dimensions = System.getProperty("org.prismlauncher.window.dimensions").split("x");
            if (dimensions.length == 2) {
                try {
                    int prismWidth = Integer.parseInt(dimensions[0]);
                    int prismHeight = Integer.parseInt(dimensions[1]);
                    this.setSize(prismWidth, prismHeight);
                } catch (NumberFormatException ignored) {

                }
            }
        }

        boolean fullscreen = false;
        if (this.getParameter("fullscreen") != null) {
            fullscreen = this.getParameter("fullscreen").equalsIgnoreCase("true");
        }

        this.minecraft = new BrnoMinecraft(this.getWidth(), this.getHeight(), fullscreen);

        this.minecraft.hostAddress = this.getDocumentBase().getHost();
        if (this.getDocumentBase().getPort() > 0) {
            StringBuilder hostAdressBuilder = new StringBuilder();
            Minecraft mc = this.minecraft;
            mc.hostAddress = hostAdressBuilder.append(mc.hostAddress).append(":").append(this.getDocumentBase().getPort()).toString();
        }

        if (this.getParameter("username") != null && this.getParameter("sessionid") != null) {
            this.minecraft.session = new Session(this.getParameter("username"), this.getParameter("sessionid"));
            System.out.println("Setting user: " + this.minecraft.session.username);
            if (this.getParameter("mppass") != null) {
                this.minecraft.session.password = this.getParameter("mppass");
            }
        } else {
            this.minecraft.session = new Session("Player" + System.currentTimeMillis() % 10000, "");
        }

        if (this.getParameter("server") != null && this.getParameter("port") != null) {
            this.minecraft.setStartupServer(this.getParameter("server"), Integer.parseInt(this.getParameter("port")));
        }

        this.startThread();

        SwingUtilities.invokeLater(() -> {
            hideThemAll(this.getParent().getParent().getParent());
            hideThemAll(this.getParent().getParent());
            hideThemAll(this.getParent());
            hideThemAll(this);
        });
    }

    @Unique
    private void hideThemAll(Container container) {
        try {
            if (container instanceof Frame) {
                ((Frame) container).dispose();
            }
            for (Component component : container.getComponents()) {
                component.setVisible(false);
            }
        } catch (NullPointerException ignored) {
        }
    }

    /**
     * @author DanyGames2014
     * @reason because i don't give a shit
     */
    @Overwrite
    public void startThread() { // startMainThread
        this.minecraft.run();
    }

    @Inject(method = "destroy", at = @At(value = "HEAD"), remap = false, cancellable = true)
    public void destroy(CallbackInfo ci) {
        ci.cancel();
    }

    @Inject(method = "stopThread", at = @At(value = "HEAD"), cancellable = true)
    public void stopThread(CallbackInfo ci) {
        ci.cancel();
    }

    @Inject(method = "clearMemory", at = @At(value = "HEAD"), cancellable = true)
    public void clearMemory(CallbackInfo ci) {
        ci.cancel();
    }
}
