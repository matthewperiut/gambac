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
 * Java wrapper around libcenter.so which provides cursor warping
 * on Wayland via the zwp_pointer_constraints_v1 protocol.
 *
 * Usage is two-phase:
 * 1. Call {@link #setupWarp} to create the lock request (before GLFW commits)
 * 2. Call {@link #finishWarp} after GLFW has committed the surface
 *    (e.g. after glfwSwapBuffers or glfwPollEvents)
 */
public final class WaylandCenterCursor {
    private static SharedLibrary lib;
    private static long fn_setup_warp;
    private static long fn_finish_warp;
    private static boolean initialized;
    private static boolean available;
    private static boolean pendingWarp;

    private WaylandCenterCursor() {}

    public static void init() {
        if (initialized) return;
        initialized = true;

        try {
            Path soPath = extractNativeLibrary();
            if (soPath == null) {
                System.out.println("[Gambac] libcenter.so not found for this platform");
                return;
            }

            lib = org.lwjgl.system.APIUtil.apiCreateLibrary(soPath.toAbsolutePath().toString());
            fn_setup_warp = lib.getFunctionAddress("wl_setup_warp");
            fn_finish_warp = lib.getFunctionAddress("wl_finish_warp");

            if (fn_setup_warp == 0 || fn_finish_warp == 0) {
                System.out.println("[Gambac] libcenter.so missing expected symbols");
                return;
            }

            available = true;
            System.out.println("[Gambac] Wayland cursor warp available via libcenter.so");
        } catch (Exception e) {
            System.out.println("[Gambac] Failed to initialize cursor warp: " + e.getMessage());
        }
    }

    public static boolean isAvailable() {
        return available;
    }

    /**
     * Phase 1: Set up a pointer lock with a cursor position hint.
     * Must be called before GLFW's next surface commit.
     */
    public static void setupWarp(int x, int y) {
        if (!available) return;

        long wlDisplay = GLFWNativeWayland.glfwGetWaylandDisplay();
        long wlSurface = GLFWNativeWayland.glfwGetWaylandWindow(Display.getHandle());
        if (wlDisplay == MemoryUtil.NULL || wlSurface == MemoryUtil.NULL) return;

        JNI.invokePPV(wlDisplay, wlSurface, x, y, fn_setup_warp);
        pendingWarp = true;
    }

    /**
     * Phase 2: Dispatch the private queue after GLFW has committed
     * the surface. Call this after glfwSwapBuffers or glfwPollEvents.
     */
    public static void finishWarp() {
        if (!available || !pendingWarp) return;
        pendingWarp = false;

        long wlDisplay = GLFWNativeWayland.glfwGetWaylandDisplay();
        if (wlDisplay == MemoryUtil.NULL) return;

        JNI.invokePV(wlDisplay, fn_finish_warp);
    }

    public static boolean hasPendingWarp() {
        return pendingWarp;
    }

    public static void destroy() {
        if (lib != null) {
            lib.free();
            lib = null;
        }
        available = false;
        initialized = false;
        pendingWarp = false;
    }

    private static Path extractNativeLibrary() {
        String arch = System.getProperty("os.arch", "");
        String nativeDir;
        if (arch.contains("aarch64") || arch.contains("arm64")) {
            nativeDir = "natives/linux-aarch64";
        } else {
            nativeDir = "natives/linux-x86_64";
        }

        String resource = "/" + nativeDir + "/libcenter.so";
        try (InputStream in = WaylandCenterCursor.class.getResourceAsStream(resource)) {
            if (in == null) return null;

            Path tmpDir = Files.createTempDirectory("gambac-center");
            Path tmpFile = tmpDir.resolve("libcenter.so");
            Files.copy(in, tmpFile, StandardCopyOption.REPLACE_EXISTING);
            tmpFile.toFile().setExecutable(true);
            tmpDir.toFile().deleteOnExit();
            tmpFile.toFile().deleteOnExit();
            return tmpFile;
        } catch (Exception e) {
            System.out.println("[Gambac] Could not extract libcenter.so: " + e.getMessage());
            return null;
        }
    }
}
