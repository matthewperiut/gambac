package net.danygames2014.gambac.lwjgl3compat.wayland;

import org.lwjgl.glfw.GLFWNativeWayland;
import org.lwjgl.opengl.Display;
import org.lwjgl.system.JNI;
import org.lwjgl.system.MemoryUtil;
import org.lwjgl.system.SharedLibrary;

import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

/**
 * Java wrapper around libgambac-warp.so which provides cursor warping
 * on Wayland via the wp_pointer_warp_v1 protocol.
 */
public final class WaylandPointerWarp {
    private static SharedLibrary lib;
    private static long fn_init;
    private static long fn_supported;
    private static long fn_warp_cursor;
    private static long fn_destroy;
    private static boolean initialized;
    private static boolean available;

    private WaylandPointerWarp() {}

    /**
     * Load the native library and probe the compositor for
     * wp_pointer_warp_v1 support.  Safe to call on any platform;
     * does nothing if not on Wayland or if the library is missing.
     */
    public static void init() {
        if (initialized) return;
        initialized = true;

        try {
            Path soPath = extractNativeLibrary();
            if (soPath == null) {
                System.out.println("[Gambac] libgambac-warp.so not found for this platform");
                return;
            }

            lib = org.lwjgl.system.APIUtil.apiCreateLibrary(soPath.toAbsolutePath().toString());
            fn_init         = lib.getFunctionAddress("gambac_warp_init");
            fn_supported    = lib.getFunctionAddress("gambac_warp_supported");
            fn_warp_cursor  = lib.getFunctionAddress("gambac_warp_cursor");
            fn_destroy      = lib.getFunctionAddress("gambac_warp_destroy");

            if (fn_init == 0 || fn_supported == 0 || fn_warp_cursor == 0 || fn_destroy == 0) {
                System.out.println("[Gambac] libgambac-warp.so missing expected symbols");
                return;
            }

            long wlDisplay = GLFWNativeWayland.glfwGetWaylandDisplay();
            if (wlDisplay == MemoryUtil.NULL) {
                System.out.println("[Gambac] Could not get Wayland display for pointer warp");
                return;
            }

            int result = JNI.invokePI(wlDisplay, fn_init);
            available = result != 0;
        } catch (Exception e) {
            System.out.println("[Gambac] Failed to initialize pointer warp: " + e.getMessage());
        }
    }

    /**
     * @return true if wp_pointer_warp_v1 is available on the current compositor
     */
    public static boolean isSupported() {
        return available;
    }

    /**
     * Warp the cursor to the given position in screen (window) coordinates.
     * Converts to wl_fixed_t internally.
     *
     * @param screenX x in window coordinates (not framebuffer)
     * @param screenY y in window coordinates (not framebuffer), top-left origin
     */
    public static void warpCursor(double screenX, double screenY) {
        if (!available) return;

        long wlSurface = GLFWNativeWayland.glfwGetWaylandWindow(Display.getHandle());
        if (wlSurface == MemoryUtil.NULL) return;

        // Convert double to wl_fixed_t (24.8 fixed-point, stored as int32)
        int fixedX = (int) (screenX * 256.0);
        int fixedY = (int) (screenY * 256.0);

        JNI.invokePV(wlSurface, fixedX, fixedY, fn_warp_cursor);
    }

    /**
     * Clean up native resources.
     */
    public static void destroy() {
        if (fn_destroy != 0) {
            JNI.invokeV(fn_destroy);
        }
        if (lib != null) {
            lib.free();
            lib = null;
        }
        available = false;
        initialized = false;
    }

    private static Path extractNativeLibrary() {
        String arch = System.getProperty("os.arch", "");
        String nativeDir;
        if (arch.contains("aarch64") || arch.contains("arm64")) {
            nativeDir = "natives/linux-aarch64";
        } else {
            nativeDir = "natives/linux-x86_64";
        }

        String resource = "/" + nativeDir + "/libgambac-warp.so";
        try (InputStream in = WaylandPointerWarp.class.getResourceAsStream(resource)) {
            if (in == null) return null;

            Path tmpDir = Files.createTempDirectory("gambac-warp");
            Path tmpFile = tmpDir.resolve("libgambac-warp.so");
            Files.copy(in, tmpFile, StandardCopyOption.REPLACE_EXISTING);
            tmpFile.toFile().setExecutable(true);
            tmpDir.toFile().deleteOnExit();
            tmpFile.toFile().deleteOnExit();
            return tmpFile;
        } catch (Exception e) {
            System.out.println("[Gambac] Could not extract libgambac-warp.so: " + e.getMessage());
            return null;
        }
    }
}
