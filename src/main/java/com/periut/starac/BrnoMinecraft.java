package com.periut.starac;

import net.minecraft.client.CrashReportPanel;
import net.minecraft.client.Minecraft;
import net.minecraft.client.render.Window;
import net.minecraft.util.crash.CrashReport;
import org.lwjgl.LWJGLException;
import org.lwjgl.opengl.Display;
import org.lwjgl.opengl.DisplayMode;

import java.awt.*;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;

public class BrnoMinecraft extends Minecraft {

    private final Frame frame;
    private int previousWidth;
    private int previousHeight;

    public BrnoMinecraft(int width, int height, boolean fullscreen) {
        super(null, null, null, width, height, fullscreen);
        this.previousWidth = width;
        this.previousHeight = height;
        this.frame = new Frame("Minecraft");
    }

    @Override
    public void handleCrash(CrashReport throwable) { // displayUnexpectedThrowable(UnexpectedThrowable)
        this.frame.removeAll();
        this.frame.add(new CrashReportPanel(throwable), "Center");
        this.frame.validate();
        this.frame.setSize(this.width, this.height);
        this.frame.setLocationRelativeTo(null);
        this.frame.setAutoRequestFocus(true);
        this.frame.addWindowListener(new WindowAdapter() {
                                         public void windowClosing(WindowEvent we) {
                                             frame.dispose();
                                             System.exit(1);
                                         }
                                     }
        );
        this.frame.setVisible(true);
        Display.destroy();
    }

    @Override
    public void init() throws LWJGLException {
        super.init();

        // Window Title
        try {
            if (System.getProperty("org.prismlauncher.window.title") != null && System.getProperty("staracUsePrismTitle") != null) {
                // PrismLauncher Window Title
                Display.setTitle(System.getProperty("org.prismlauncher.window.title"));
            } else {
                // Fallback
                Display.setTitle("Minecraft Beta 1.7.3");
            }
        } catch (Exception ignored) {
            // If something happens, fallback to the default title
            Display.setTitle("Minecraft Beta 1.7.3");
        }

    }

    @Override
    public void tick() {
        if (Display.getWidth() != this.width || Display.getHeight() != this.height) {
            this.resize(Display.getWidth(), Display.getHeight());
        }

        super.tick();
    }

    @Override
    public void toggleFullscreen() {
        try {
            this.fullscreen = !this.fullscreen;

            if (this.fullscreen) {
                this.previousWidth = Display.getWidth();
                this.previousHeight = Display.getHeight();

                Display.setDisplayMode(Display.getDesktopDisplayMode());
                this.width = Display.getDisplayMode().getWidth();
                this.height = Display.getDisplayMode().getHeight();
            } else {
                this.width = this.previousWidth;
                this.height = this.previousHeight;
                Display.setDisplayMode(new DisplayMode(this.width, this.height));
            }

            if (this.width <= 0) {
                this.width = 1;
            }

            if (this.height <= 0) {
                this.height = 1;
            }

            if (this.screen != null) {
                this.resize(this.width, this.height);
            }

            Display.setFullscreen(this.fullscreen);
            Display.update();
        } catch (Exception e) {
            //noinspection CallToPrintStackTrace
            e.printStackTrace();
        }
    }

    private void resize(int width, int height) {
        if (width <= 0) {
            width = 1;
        }

        if (height <= 0) {
            height = 1;
        }

        this.width = width;
        this.height = height;
        if (this.screen != null) {
            Window scaler = new Window(this.options, width, height);
            int scaledWidth = scaler.getWidth();
            int scaledHeight = scaler.getHeight();
            this.screen.init(this, scaledWidth, scaledHeight);
        }
    }
}
