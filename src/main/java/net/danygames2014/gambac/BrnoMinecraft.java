package net.danygames2014.gambac;

import net.minecraft.client.CrashReportPanel;
import net.minecraft.client.Minecraft;
import net.minecraft.client.render.Window;
import net.minecraft.util.crash.CrashReport;
import org.lwjgl.BufferUtils;
import org.lwjgl.LWJGLException;
import org.lwjgl.opengl.Display;
import org.lwjgl.opengl.DisplayMode;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL30;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.awt.image.BufferedImage;
import java.io.InputStream;
import java.nio.ByteBuffer;

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
        Display.setResizable(true);
        super.init();
        
        // Window Title
        try {
            if (System.getProperty("org.prismlauncher.window.title") != null && System.getProperty("gambacUsePrismTitle") != null) {
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

        // Window Icon
        ByteBuffer[] icons = new ByteBuffer[2];
        try {
            icons[0] = loadIcon("/assets/gambac/icons/16.png");
            icons[1] = loadIcon("/assets/gambac/icons/32.png");
        } catch (Exception ignored) {
        }

        if (icons[0] != null && icons[1] != null) {
            Display.setIcon(icons);
        }

        // Make the display current
        try {
            Display.makeCurrent();
            Display.update();
        } catch (LWJGLException e) {
            System.err.println("Error while making the Display current");
            //noinspection CallToPrintStackTrace
            e.printStackTrace();
        }
    }


    @Override
    public void tick() {
        if (GL11.glGetString(GL11.GL_RENDERER).contains("Apple M") && !System.getProperty("os.name").toLowerCase().contains("linux")) {
            GL11.glEnable(GL30.GL_FRAMEBUFFER_SRGB);
        }

        if (Display.getWidth() != this.width || Display.getHeight() != this.height) {
            this.resize(Display.getWidth(), Display.getHeight());
        }

        super.tick();
    }

    private static ByteBuffer loadIcon(String path) {
        try {
            InputStream stream = BrnoMinecraft.class.getResourceAsStream(path);
            if (stream == null) {
                throw new RuntimeException("Icon resource not found: " + path);
            }
            BufferedImage image = ImageIO.read(stream);

            int[] pixels = new int[image.getWidth() * image.getHeight()];
            image.getRGB(0, 0, image.getWidth(), image.getHeight(), pixels, 0, image.getWidth());

            ByteBuffer buffer = BufferUtils.createByteBuffer(image.getWidth() * image.getHeight() * 4);

            for (int y = 0; y < image.getHeight(); y++) {
                for (int x = 0; x < image.getWidth(); x++) {
                    int pixel = pixels[y * image.getWidth() + x];
                    buffer.put((byte) ((pixel >> 16) & 0xFF)); // Red
                    buffer.put((byte) ((pixel >> 8) & 0xFF));  // Green
                    buffer.put((byte) (pixel & 0xFF));         // Blue
                    buffer.put((byte) ((pixel >> 24) & 0xFF)); // Alpha
                }
            }

            buffer.flip();
            return buffer;
        } catch (Exception e) {
            return null;
        }
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
