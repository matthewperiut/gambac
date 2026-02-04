package org.lwjgl.opengl;

import org.lwjgl.glfw.GLFW;
import org.lwjgl.input.Keyboard;
import org.lwjgl.input.Mouse;

import java.util.function.BooleanSupplier;

/**
 * Provides a hook for running an early render loop before the main game loop starts.
 * This allows mods like StationAPI to show loading screens during startup without
 * the complexity of multi-threaded context management.
 *
 * Usage:
 * 1. Detect this class exists (indicates starac/LWJGL3 compatibility layer)
 * 2. Call runLoop() with your render callback and completion condition
 * 3. The loop handles event polling, input, and buffer swapping properly
 */
public final class EarlyRenderLoop {

    private EarlyRenderLoop() {}

    /**
     * Runs an early render loop on the current thread.
     * This properly handles GLFW event polling and input while rendering.
     *
     * @param shouldContinue Returns true while the loop should keep running, false to exit
     * @param render Called each frame to perform rendering
     * @param targetFps Target frames per second (0 for unlimited)
     */
    public static void runLoop(BooleanSupplier shouldContinue, Runnable render, int targetFps) {
        long handle = Display.getHandle();
        if (handle == -1L) {
            throw new IllegalStateException("Display not created yet");
        }

        long frameTimeNanos = targetFps > 0 ? 1_000_000_000L / targetFps : 0;
        long lastFrameTime = System.nanoTime();

        while (shouldContinue.getAsBoolean()) {
            // Proper event polling
            GLFW.glfwPollEvents();

            // Poll input devices
            if (Mouse.isCreated()) {
                Mouse.poll();
            }
            if (Keyboard.isCreated()) {
                Keyboard.poll();
            }

            // Drain input event queues to prevent buildup
            while (Mouse.next()) { /* drain */ }
            while (Keyboard.next()) { /* drain */ }

            // Call the render callback
            render.run();

            // Swap buffers
            GLFW.glfwSwapBuffers(handle);

            // Frame rate limiting
            if (frameTimeNanos > 0) {
                long now = System.nanoTime();
                long elapsed = now - lastFrameTime;
                long sleepTime = frameTimeNanos - elapsed;
                if (sleepTime > 0) {
                    try {
                        Thread.sleep(sleepTime / 1_000_000, (int) (sleepTime % 1_000_000));
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                        break;
                    }
                }
                lastFrameTime = System.nanoTime();
            } else {
                lastFrameTime = System.nanoTime();
            }

            // Check if window should close
            if (GLFW.glfwWindowShouldClose(handle)) {
                break;
            }
        }
    }

    /**
     * Simplified version with default 60 FPS target.
     */
    public static void runLoop(BooleanSupplier shouldContinue, Runnable render) {
        runLoop(shouldContinue, render, 60);
    }
}
