package com.periut.starac;

import net.minecraft.client.CrashReportPanel;
import net.minecraft.client.Minecraft;
import net.minecraft.client.util.ScreenScaler;
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
        this.frame.setSize(this.displayWidth, this.displayHeight);
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
    public void init() {
        super.init();
    }

    @Override
    public void tick() {
        if (Display.getWidth() != this.displayWidth || Display.getHeight() != this.displayHeight) {
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
                this.displayWidth = Display.getDisplayMode().getWidth();
                this.displayHeight = Display.getDisplayMode().getHeight();
            } else {
                this.displayWidth = this.previousWidth;
                this.displayHeight = this.previousHeight;
                Display.setDisplayMode(new DisplayMode(this.displayWidth, this.displayHeight));
            }

            if (this.displayWidth <= 0) {
                this.displayWidth = 1;
            }

            if (this.displayHeight <= 0) {
                this.displayHeight = 1;
            }

            if (this.currentScreen != null) {
                this.resize(this.displayWidth, this.displayHeight);
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

        this.displayWidth = width;
        this.displayHeight = height;
        if (this.currentScreen != null) {
            ScreenScaler scaler = new ScreenScaler(this.options, width, height);
            int scaledWidth = scaler.getScaledWidth();
            int scaledHeight = scaler.getScaledHeight();
            this.currentScreen.init(this, scaledWidth, scaledHeight);
        }
    }
}
