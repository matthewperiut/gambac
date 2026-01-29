package org.lwjgl.opengl;

import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Arrays;
import java.util.Comparator;

import net.danygames2014.gambac.lwjgl3compat.DesktopFileInjector;
import net.danygames2014.gambac.lwjgl3compat.wayland.WaylandCenterCursor;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.lwjgl.LWJGLException;
import org.lwjgl.glfw.*;
import org.lwjgl.glfw.GLFWImage.Buffer;
import org.lwjgl.input.Keyboard;
import org.lwjgl.input.Mouse;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.system.MemoryUtil;
import java.lang.reflect.Method;

public final class Display {
	@NotNull
	private static String title = "";
	private static long handle = -1L;
	private static boolean resizable;
	@NotNull
	private static DisplayMode displayMode = new DisplayMode(640, 480, 24, 60);
	private static int width;
	private static int height;
	private static int xPos;
	private static int yPos;
	private static boolean window_resized = true;
	@Nullable
	private static GLFWWindowSizeCallback sizeCallback;
	@Nullable
	private static ByteBuffer[] cached_icons = null;
	private static boolean focused;
	private static boolean glfwInitialized = false;
	private static boolean usingGlfwAsync = false;

	private Display() {
	}

	private static boolean isGnome() {
		String desktop = System.getenv("XDG_CURRENT_DESKTOP");
		return desktop != null && desktop.toUpperCase().contains("GNOME");
	}

	/**
	 * Sets XCURSOR_THEME and XCURSOR_SIZE from gsettings so GLFW uses the
	 * user's configured cursor theme on Wayland. GNOME (and some other DEs)
	 * set the cursor via gsettings but don't export the env vars.
	 */
	private static void setupCursorTheme() {
		try {
			// Skip if already set
			if (System.getenv("XCURSOR_THEME") != null) {
				return;
			}

			org.lwjgl.system.SharedLibrary libc = org.lwjgl.system.APIUtil.apiCreateLibrary("libc.so.6");
			long setenv = libc.getFunctionAddress("setenv");
			if (setenv == 0) return;

			// Read cursor theme from gsettings
			try {
				Process p = new ProcessBuilder("gsettings", "get", "org.gnome.desktop.interface", "cursor-theme").start();
				String output = new String(p.getInputStream().readAllBytes()).trim();
				p.waitFor();
				// gsettings wraps in single quotes: 'Adwaita'
				if (output.contains("'")) {
					String theme = output.split("'")[1];
					nativeSetenv(setenv, "XCURSOR_THEME", theme);
					System.out.println("[Gambac] Set XCURSOR_THEME=" + theme);
				}
			} catch (Exception ignored) {}

			// Read cursor size
			if (System.getenv("XCURSOR_SIZE") == null) {
				try {
					Process p = new ProcessBuilder("gsettings", "get", "org.gnome.desktop.interface", "cursor-size").start();
					String output = new String(p.getInputStream().readAllBytes()).trim();
					p.waitFor();
					if (!output.isEmpty()) {
						nativeSetenv(setenv, "XCURSOR_SIZE", output);
						System.out.println("[Gambac] Set XCURSOR_SIZE=" + output);
					}
				} catch (Exception ignored) {}
			}
		} catch (Exception e) {
			System.out.println("[Gambac] Could not setup cursor theme: " + e.getMessage());
		}
	}

	private static void nativeSetenv(long setenvAddr, String name, String value) {
		ByteBuffer nameBuf = MemoryUtil.memASCII(name, true);
		ByteBuffer valueBuf = MemoryUtil.memASCII(value, true);
		org.lwjgl.system.JNI.invokePPI(MemoryUtil.memAddress(nameBuf), MemoryUtil.memAddress(valueBuf), 1, setenvAddr);
		MemoryUtil.memFree(nameBuf);
		MemoryUtil.memFree(valueBuf);
	}

	/**
	 * Sets up native Wayland decorations by:
	 * 1. Extracting a patched libdecor-gtk plugin (thread check removed)
	 * 2. Setting GDK_BACKEND=wayland so GTK initializes correctly
	 * 3. Setting LIBDECOR_PLUGIN_DIR to point to our patched plugin
	 */
	private static void setupLibdecorPlugin() {
		try {
			org.lwjgl.system.SharedLibrary libc = org.lwjgl.system.APIUtil.apiCreateLibrary("libc.so.6");
			long setenv = libc.getFunctionAddress("setenv");
			if (setenv == 0) {
				System.out.println("[Gambac] Could not find setenv in libc");
				return;
			}

			// Force GTK to use Wayland backend, otherwise gtk_init fails
			nativeSetenv(setenv, "GDK_BACKEND", "wayland");
			System.out.println("[Gambac] Set GDK_BACKEND=wayland");

			// Extract patched libdecor-gtk plugin
			String arch = System.getProperty("os.arch", "");
			String nativeDir;
			if (arch.contains("aarch64") || arch.contains("arm64")) {
				nativeDir = "natives/linux-aarch64";
			} else {
				nativeDir = "natives/linux-x86_64";
			}

			String resource = "/" + nativeDir + "/libdecor-gtk.so";
			try (InputStream in = Display.class.getResourceAsStream(resource)) {
				if (in == null) {
					System.out.println("[Gambac] Patched libdecor-gtk plugin not found for " + arch);
					return;
				}

				Path pluginDir = Files.createTempDirectory("gambac-libdecor");
				Path pluginFile = pluginDir.resolve("libdecor-gtk.so");
				Files.copy(in, pluginFile, StandardCopyOption.REPLACE_EXISTING);
				pluginFile.toFile().setExecutable(true);
				pluginDir.toFile().deleteOnExit();
				pluginFile.toFile().deleteOnExit();

				// Copy the system cairo plugin as fallback (e.g. if GTK3 is not installed)
				Path systemCairo = Path.of("/usr/lib/libdecor/plugins-1/libdecor-cairo.so");
				if (Files.exists(systemCairo)) {
					Path cairoCopy = pluginDir.resolve("libdecor-cairo.so");
					Files.copy(systemCairo, cairoCopy, StandardCopyOption.REPLACE_EXISTING);
					cairoCopy.toFile().deleteOnExit();
				}

				nativeSetenv(setenv, "LIBDECOR_PLUGIN_DIR", pluginDir.toAbsolutePath().toString());
				System.out.println("[Gambac] Set LIBDECOR_PLUGIN_DIR=" + pluginDir.toAbsolutePath());
			}
		} catch (Exception e) {
			System.out.println("[Gambac] Could not setup libdecor plugin: " + e.getMessage());
		}
	}

	public static void ensureInitialized() {
		if (glfwInitialized) return;
		glfwInitialized = true;
		usingGlfwAsync = "glfw_async".equals(org.lwjgl.system.Configuration.GLFW_LIBRARY_NAME.get());
		GLFWErrorCallback.createPrint(System.err).set();
		if (GLFW.glfwPlatformSupported(GLFW.GLFW_PLATFORM_WAYLAND)) {
			setupCursorTheme();
			// GNOME is the only major compositor lacking xdg-decoration (server-side
			// decorations), so it needs a patched libdecor-gtk plugin. Other compositors
			// (KDE, Sway, etc.) handle decorations natively.
			if (isGnome()) {
				setupLibdecorPlugin();
			}
			GLFW.glfwInitHint(GLFW.GLFW_PLATFORM, GLFW.GLFW_PLATFORM_WAYLAND);
			GLFW.glfwInitHint(GLFW.GLFW_WAYLAND_LIBDECOR, GLFW.GLFW_WAYLAND_PREFER_LIBDECOR);
		}
		if (!GLFW.glfwInit()) {
			throw new IllegalStateException("Unable to initialize GLFW");
		}
		if (GLFW.glfwGetPlatform() == GLFW.GLFW_PLATFORM_WAYLAND) {
			WaylandCenterCursor.init();
		}
		if (GLFW.glfwGetPlatform() == GLFW.GLFW_PLATFORM_COCOA) {
			MacOSDisplayHelper.initAppAppearance();
		}
	}

	@NotNull
	public static String getTitle() {
		return title;
	}

	public static void setTitle(@NotNull String title) {
		Display.title = title;
		if (isCreated()) {
			GLFW.glfwSetWindowTitle(handle, title);
		}
	}

	public static long getHandle() {
		return handle;
	}

	public static void setHandle(long handle) {
		Display.handle = handle;
	}

	@NotNull
	public static DisplayMode getDisplayMode() {
		return displayMode;
	}

	public static void setDisplayMode(@NotNull DisplayMode mode) {
		displayMode = mode;
	}

	public static int getWidth() {
		if (handle != -1L) {
			int[] w = new int[1];
			GLFW.glfwGetFramebufferSize(handle, w, null);
			return w[0];
		}
		return width;
	}

	public static void setWidth(int width) {
		Display.width = width;
	}

	public static int getHeight() {
		if (handle != -1L) {
			int[] h = new int[1];
			GLFW.glfwGetFramebufferSize(handle, null, h);
			return h[0];
		}
		return height;
	}

	public static void setHeight(int height) {
		Display.height = height;
	}

	public static int getXPos() {
		return xPos;
	}

	public static void setXPos(int XPos) {
		xPos = XPos;
	}

	public static int getYPos() {
		return yPos;
	}

	public static void setYPos(int YPos) {
		yPos = YPos;
	}

	@Nullable
	public static DisplayMode getDesktopDisplayMode() {
		ensureInitialized();
		long mon = GLFW.glfwGetPrimaryMonitor();
		GLFWVidMode mode = GLFW.glfwGetVideoMode(mon);
		if (mode == null) {
			return Arrays.stream(getAvailableDisplayModes()).max(Comparator.comparingInt(d -> d.getWidth() * d.getHeight())).orElse(null);
		}
		return new DisplayMode(mode.width(), mode.height(), mode.redBits() + mode.greenBits() + mode.blueBits(),
				mode.refreshRate());
	}


	public static int setIcon(@NotNull ByteBuffer[] icons) {

		if (GLFW.glfwGetPlatform() == GLFW.GLFW_PLATFORM_WAYLAND) {
			// Wayland does not have a standardised way of setting window icons, see
			// https://www.glfw.org/docs/latest/group__window.html#gadd7ccd39fe7a7d1f0904666ae5932dc5
			// for more information.
			return DesktopFileInjector.setIcon(icons);
		}

		// LWJGL2 doesn't enforce this to be called after window creation,
		// meaning you have to keep hold the icons to use them when the window is created
		if (!Arrays.equals(cached_icons, icons)) {
			// you have to also clone the byte buffers to avoid seg faults from them being freed
			cached_icons = Arrays.stream(icons).map(buf -> {
				ByteBuffer copy = ByteBuffer.allocate(buf.capacity());
				int old_pos = buf.position();
				copy.put(buf);
				buf.position(old_pos);
				copy.flip();
				return copy;
			}).toArray(ByteBuffer[]::new);
		}

		if (isCreated() && GLFW.glfwGetPlatform() != GLFW.GLFW_PLATFORM_COCOA) {
			try (MemoryStack memoryStack = MemoryStack.stackPush()) {
				Buffer buffer = GLFWImage.malloc(icons.length, memoryStack);

				Arrays.stream(icons).forEach(buf -> {
					GLFWImage image = GLFWImage.malloc();
					int size = buf.limit() / 4;
					int dimension = (int) Math.sqrt(size);

					// Minecraft provides ARGB pixels; GLFW expects RGBA bytes.
					// Convert each pixel from ARGB to RGBA byte order.
					ByteBuffer rgba = ByteBuffer.allocateDirect(buf.limit());
					int oldPos = buf.position();
					for (int i = 0; i < size; i++) {
						int offset = i * 4;
						byte a = buf.get(offset);
						byte r = buf.get(offset + 1);
						byte g = buf.get(offset + 2);
						byte b = buf.get(offset + 3);
						rgba.put(r).put(g).put(b).put(a);
					}
					buf.position(oldPos);
					rgba.flip();

					buffer.put(image.set(dimension, dimension, rgba));
				});

				GLFW.glfwSetWindowIcon(handle, buffer);
			}
			return 1;
		} else {
			return 0;
		}
	}

	public static void update() {
		window_resized = false;
		if (usingGlfwAsync) {
			// Unlock the CGL context so the macOS compositor can safely access
			// the GL surface during event processing (e.g. window resize).
			// Without this, the compositor and game thread race on the GL
			// surface, causing SIGSEGV in AppleMetalOpenGLRenderer.
			GL11.glFinish();
			MacOSDisplayHelper.unlockCGLContext();
			GLFW.glfwPollEvents();
			MacOSDisplayHelper.relockCGLContext();
		} else {
			GLFW.glfwPollEvents();
		}
		if (Mouse.isCreated()) {
			Mouse.poll();
		}

		if (Keyboard.isCreated()) {
			Keyboard.poll();
		}

		GLFW.glfwSwapBuffers(handle);
		// After GLFW commits the surface via swapBuffers, dispatch any
		// pending cursor warp so the lock callback fires.
		WaylandCenterCursor.finishWarp();
	}

	public static void create() {
		try {
			create(new PixelFormat());
		} catch (LWJGLException e) {
			throw new RuntimeException(e);
		}
	}

	public static void create(@NotNull PixelFormat pixelFormat) throws LWJGLException {
		ensureInitialized();
		// Configure GLFW
		GLFW.glfwDefaultWindowHints();

		if (GLFW.glfwGetPlatform() == GLFW.GLFW_PLATFORM_WAYLAND) {
			DesktopFileInjector.inject();
			GLFW.glfwWindowHintString(GLFW.GLFW_WAYLAND_APP_ID, DesktopFileInjector.APP_ID);
		}

		GLFW.glfwWindowHint(GLFW.GLFW_CLIENT_API, GLFW.GLFW_OPENGL_API);
		GLFW.glfwWindowHint(GLFW.GLFW_CONTEXT_CREATION_API, GLFW.GLFW_NATIVE_CONTEXT_API);
		if (GLFW.glfwGetPlatform() != GLFW.GLFW_PLATFORM_COCOA) { // macOS does not support the compat profile
			GLFW.glfwWindowHint(GLFW.GLFW_CONTEXT_VERSION_MAJOR, 3);
			GLFW.glfwWindowHint(GLFW.GLFW_CONTEXT_VERSION_MINOR, 2);
			GLFW.glfwWindowHint(GLFW.GLFW_OPENGL_PROFILE, GLFW.GLFW_OPENGL_COMPAT_PROFILE);
		}
		GLFW.glfwWindowHint(GLFW.GLFW_ALPHA_BITS, pixelFormat.getAlphaBits());
		GLFW.glfwWindowHint(GLFW.GLFW_DEPTH_BITS, pixelFormat.getDepthBits());
		GLFW.glfwWindowHint(GLFW.GLFW_STENCIL_BITS, pixelFormat.getStencilBits());
		GLFW.glfwWindowHint(GLFW.GLFW_STEREO, pixelFormat.isStereo() ? GLFW.GLFW_TRUE : GLFW.GLFW_FALSE);

		GLFW.glfwWindowHint(GLFW.GLFW_DECORATED, GLFW.GLFW_TRUE);
		GLFW.glfwWindowHint(GLFW.GLFW_VISIBLE, 0);
		GLFW.glfwWindowHint(GLFW.GLFW_RESIZABLE, 1);
		handle =
				GLFW.glfwCreateWindow(displayMode.getWidth(), displayMode.getHeight(), title, MemoryUtil.NULL, MemoryUtil.NULL);
		width = displayMode.getWidth();
		height = displayMode.getHeight();
		GLFW.glfwMakeContextCurrent(handle);
		GL.createCapabilities();
		GLFW.glfwSwapInterval(0); // disable vsync by default
		// create general callbacks
		sizeCallback = GLFWWindowSizeCallback.create(Display::resizeCallback);
		GLFW.glfwSetWindowSizeCallback(handle, sizeCallback);
		GLFW.glfwSetWindowFocusCallback(handle, (window, focused1) -> {
			if (window == handle) {
				focused = focused1;
			}
		});
		Mouse.create();
		Keyboard.create();
		GLFW.glfwShowWindow(handle);
		if (cached_icons != null) {
			setIcon(cached_icons);
		}
		if (usingGlfwAsync && GLFW.glfwGetPlatform() == GLFW.GLFW_PLATFORM_COCOA) {
			// Lock the CGL context for the game thread. This is unlocked
			// around glfwPollEvents in update() so the compositor can
			// safely access the GL surface during resize.
			MacOSDisplayHelper.lockCGLContext(handle);
		}
	}

	public static void setFullscreen(boolean fullscreen) {

		try {
			resizeCallback(handle, displayMode.getWidth(), displayMode.getHeight());

			if (fullscreen) {
				long monitor = GLFW.glfwGetWindowMonitor(handle);
				if (monitor == 0L) {
					monitor = GLFW.glfwGetPrimaryMonitor();
				}
				GLFW.glfwSetWindowMonitor(getHandle(),
						monitor,
						0,
						0,
						getWidth(),
						getHeight(),
						getDisplayMode().getFrequency());
				setXPos(getDisplayMode().getWidth() / 2);
				setYPos(getDisplayMode().getHeight() / 2);
			} else {
				setXPos(getXPos() - getWidth() / 2);
				setYPos(getYPos() - getHeight() / 2);
				GLFW.glfwSetWindowMonitor(getHandle(),
						0L,
						getXPos(), // need a xPos
						getYPos(), // need a yPos
						getWidth(),
						getHeight(),
						-1);
			}

		} catch (Throwable t) {
			t.printStackTrace();
		}
	}

	@NotNull
	public static DisplayMode[] getAvailableDisplayModes() {
		ensureInitialized();
		long primaryMonitor = GLFW.glfwGetPrimaryMonitor();
		if (primaryMonitor == MemoryUtil.NULL) {
			return new DisplayMode[0];
		} else {
			GLFWVidMode.Buffer videoModes = GLFW.glfwGetVideoModes(primaryMonitor);
			if (videoModes == null) {
				throw new IllegalStateException("No video modes found");
			} else {
				return videoModes.stream().map(mode -> new DisplayMode(mode.width(),
						mode.height(), mode.redBits() + mode.blueBits() + mode.greenBits(),
						mode.refreshRate())).toArray(DisplayMode[]::new);
			}
		}
	}

	public static void destroy() {
		if (usingGlfwAsync && GLFW.glfwGetPlatform() == GLFW.GLFW_PLATFORM_COCOA) {
			MacOSDisplayHelper.unlockCGLContext();
		}
		WaylandCenterCursor.destroy();
		// free callbacks
		assert sizeCallback != null;
		sizeCallback.free();
		Mouse.destroy();
		Keyboard.destroy();
		GLFWErrorCallback callback = GLFW.glfwSetErrorCallback(null);
		if (callback != null) {
			callback.free();
		}
		// Destroy the window
		GLFW.glfwDestroyWindow(handle);
		GLFW.glfwTerminate();
	}

	public static boolean isCreated() {
		return handle != -1L;
	}

	public static boolean isCloseRequested() {
		return GLFW.glfwWindowShouldClose(handle);
	}

	public static boolean isActive() {
		return focused;
	}

	public static void setResizable(boolean isResizable) {
		resizable = isResizable;
		if (isCreated()) {
			GLFW.glfwWindowHint(GLFW.GLFW_RESIZABLE, resizable ? 1 : 0);
		}
	}

	public static void sync(int fps) {
		Sync.sync(fps);
	}


	public static void setVSyncEnabled(boolean enabled) {
		if (GLFW.glfwGetCurrentContext() != 0) {
			GLFW.glfwSwapInterval(enabled ? 1 : 0);
		}
	}

	public static boolean wasResized() {
		return window_resized;
	}

	public static boolean isVisible() {
		return GLFW.glfwGetWindowAttrib(handle, GLFW.GLFW_VISIBLE) != 0;
	}

	public static float getContentScaleX() {
		if (handle == -1L) return 1.0f;
		int[] fbW = new int[1], winW = new int[1];
		GLFW.glfwGetFramebufferSize(handle, fbW, null);
		GLFW.glfwGetWindowSize(handle, winW, null);
		return winW[0] > 0 ? (float) fbW[0] / winW[0] : 1.0f;
	}

	public static float getContentScaleY() {
		if (handle == -1L) return 1.0f;
		int[] fbH = new int[1], winH = new int[1];
		GLFW.glfwGetFramebufferSize(handle, null, fbH);
		GLFW.glfwGetWindowSize(handle, null, winH);
		return winH[0] > 0 ? (float) fbH[0] / winH[0] : 1.0f;
	}

	private static void resizeCallback(long window, int width, int height) {
		if (window == handle) {
			window_resized = true;
			Display.width = width;
			Display.height = height;
		}
	}

	public static void swapBuffers() {
		GLFW.glfwSwapBuffers(handle);
	}
}
