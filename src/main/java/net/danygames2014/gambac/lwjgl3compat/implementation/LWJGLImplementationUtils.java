package net.danygames2014.gambac.lwjgl3compat.implementation;

import net.danygames2014.gambac.lwjgl3compat.implementation.glfw.GLFWKeyboardImplementation;
import net.danygames2014.gambac.lwjgl3compat.implementation.glfw.GLFWMouseImplementation;
import net.danygames2014.gambac.lwjgl3compat.implementation.glfw.VirtualGLFWMouseImplementation;
import net.danygames2014.gambac.lwjgl3compat.implementation.input.CombinedInputImplementation;
import net.danygames2014.gambac.lwjgl3compat.implementation.input.InputImplementation;
import net.danygames2014.gambac.lwjgl3compat.implementation.input.MouseImplementation;
import org.lwjgl.glfw.GLFW;

/**
 * @author Zarzelcow
 * @created 28/09/2022 - 3:12 PM
 */
public class LWJGLImplementationUtils {
    private static final boolean allowVirtualCursor = Boolean.getBoolean("legacy_lwjgl3.allow_virtual_cursor") || System.getenv("LEGACY_LWJGL3_ALLOW_VIRTUAL_CURSOR") != null;
    private static InputImplementation _inputImplementation;

    public static InputImplementation getOrCreateInputImplementation() {
        if (_inputImplementation == null) {
            _inputImplementation = createImplementation();
        }
        return _inputImplementation;
    }

    private static InputImplementation createImplementation() {
        MouseImplementation mouse = allowVirtualCursor && GLFW.glfwGetPlatform() == GLFW.GLFW_PLATFORM_WAYLAND ?
                VirtualGLFWMouseImplementation.getInstance() :
                new GLFWMouseImplementation();
        return new CombinedInputImplementation(new GLFWKeyboardImplementation(), mouse);
    }

}
