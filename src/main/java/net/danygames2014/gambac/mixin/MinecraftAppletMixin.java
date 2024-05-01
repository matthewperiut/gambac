package net.danygames2014.gambac.mixin;

import net.danygames2014.gambac.BrnoMinecraft;
import net.minecraft.client.Minecraft;
import net.minecraft.client.MinecraftApplet;
import net.minecraft.client.util.Session;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;

import javax.swing.*;
import java.applet.Applet;
import java.awt.*;

@SuppressWarnings("removal")
@Mixin(MinecraftApplet.class)
public class MinecraftAppletMixin extends Applet {

    @Shadow private Minecraft field_2832;

    /**
     * @author Proudly overwritten by DanyGames2014
     * @reason becaise i dont give a shit
     */
    @Overwrite(remap = false)
    public void init(){
        boolean var1 = false;
        if (this.getParameter("fullscreen") != null) {
            var1 = this.getParameter("fullscreen").equalsIgnoreCase("true");
        }

        this.field_2832 = new BrnoMinecraft(this.getWidth(), this.getHeight(), var1);
        this.field_2832.field_2810 = this.getDocumentBase().getHost();
        if (this.getDocumentBase().getPort() > 0) {
            StringBuilder var10000 = new StringBuilder();
            Minecraft var10002 = this.field_2832;
            var10002.field_2810 = var10000.append(var10002.field_2810).append(":").append(this.getDocumentBase().getPort()).toString();
        }

        if (this.getParameter("username") != null && this.getParameter("sessionid") != null) {
            this.field_2832.session = new Session(this.getParameter("username"), this.getParameter("sessionid"));
            System.out.println("Setting user: " + this.field_2832.session.username);
            if (this.getParameter("mppass") != null) {
                this.field_2832.session.mpPass = this.getParameter("mppass");
            }
        } else {
            this.field_2832.session = new Session("Payer" + System.currentTimeMillis() % 10000, "");
        }

        if (this.getParameter("server") != null && this.getParameter("port") != null) {
            this.field_2832.method_2117(this.getParameter("server"), Integer.parseInt(this.getParameter("port")));
        }

        SwingUtilities.invokeLater(() -> {
            hideThemAll(this.getParent().getParent().getParent());
            hideThemAll(this.getParent().getParent());
            hideThemAll(this.getParent());
            hideThemAll(this);
            this.method_2153();
        });
    }

    private void hideThemAll(Container container) {
        try {
            if (container instanceof Frame) {
                ((Frame) container).dispose();
            }
            for (Component component : container.getComponents()) {
                component.setVisible(false);
            }
        } catch (NullPointerException ignore) {
        }
    }

    /**
     * @author DanyGames2014
     * @reason because i dont give a shit
     */
    @Overwrite
    public void method_2153() {
        /*if (this.mcThread == null) {
            this.mcThread = new Thread(this.mc, "Minecraft main thread");
            this.mcThread.start();
        }*/
        this.field_2832.run();
    }

    /**
     * @author DanyGames2014
     * @reason because i dont give a shit
     */
    @Overwrite(remap = false)
    public void start() {
        if (this.field_2832 != null) {
            this.field_2832.paused = false;
        }
    }

    /**
     * @author DanyGames2014
     * @reason because i dont give a shit
     */
    @Overwrite(remap = false)
    public void stop() {
        if (this.field_2832 != null) {
            this.field_2832.paused = true;
        }
    }

    /**
     * @author DanyGames2014
     * @reason because i dont give a shit
     */
    @Overwrite(remap = false)
    public void destroy() {
    }

    /**
     * @author DanyGames2014
     * @reason because i dont give a shit
     */
    @Overwrite
    public void method_2154() {
        // NOP
    }

    /**
     * @author DanyGames2014
     * @reason because i dont give a shit
     */
    public void clearApplet() {
        // NOP
    }
}
