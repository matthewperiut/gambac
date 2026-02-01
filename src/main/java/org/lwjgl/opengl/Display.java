package org.lwjgl.opengl;

import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Arrays;
import java.util.Comparator;

import com.periut.starac.lwjgl3compat.DesktopFileInjector;
import com.periut.starac.lwjgl3compat.wayland.WaylandCenterCursor;
import com.periut.starac.lwjgl3compat.util.OS;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.lwjgl.LWJGLException;
import org.lwjgl.glfw.*;
import org.lwjgl.glfw.GLFWImage.Buffer;
import org.lwjgl.input.Keyboard;
import org.lwjgl.input.Mouse;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.system.MemoryUtil;

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
	private static boolean cinnamonSizeLimitActive = false;
	private static boolean forceX11Fallback = false;

	private Display() {
	}

	private static boolean needsLibdecor() {
		String desktop = System.getenv("XDG_CURRENT_DESKTOP");
		if (desktop == null) return false;
		String upper = desktop.toUpperCase();
		return upper.contains("GNOME") || upper.contains("CINNAMON");
	}

	private static boolean isCinnamonWayland() {
		String desktop = System.getenv("XDG_CURRENT_DESKTOP");
		return desktop != null && desktop.toUpperCase().contains("CINNAMON")
				&& GLFW.glfwGetPlatform() == GLFW.GLFW_PLATFORM_WAYLAND;
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
					System.out.println("[Starac] Set XCURSOR_THEME=" + theme);
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
						System.out.println("[Starac] Set XCURSOR_SIZE=" + output);
					}
				} catch (Exception ignored) {}
			}
		} catch (Exception e) {
			System.out.println("[Starac] Could not setup cursor theme: " + e.getMessage());
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
				System.out.println("[Starac] Could not find setenv in libc");
				return;
			}

			// Force GTK to use Wayland backend, otherwise gtk_init fails
			nativeSetenv(setenv, "GDK_BACKEND", "wayland");
			System.out.println("[Starac] Set GDK_BACKEND=wayland");

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
					System.out.println("[Starac] Patched libdecor-gtk plugin not found for " + arch);
					return;
				}

				Path pluginDir = Files.createTempDirectory("starac-libdecor");
				Path pluginFile = pluginDir.resolve("libdecor-gtk.so");
				Files.copy(in, pluginFile, StandardCopyOption.REPLACE_EXISTING);
				pluginFile.toFile().setExecutable(true);
				pluginDir.toFile().deleteOnExit();
				pluginFile.toFile().deleteOnExit();

				// Copy the system cairo plugin as fallback (e.g. if GTK3 is not installed)
				// Try arch-appropriate lib dir first (lib64 on Fedora/RHEL, then lib)
				String[] cairoCandidates = {
					"/usr/lib64/libdecor/plugins-1/libdecor-cairo.so",
					"/usr/lib/x86_64-linux-gnu/libdecor/plugins-1/libdecor-cairo.so",
					"/usr/lib/libdecor/plugins-1/libdecor-cairo.so",
				};
				Path systemCairo = null;
				for (String c : cairoCandidates) {
					Path p = Path.of(c);
					if (Files.exists(p)) {
						systemCairo = p;
						break;
					}
				}
				if (systemCairo != null) {
					Path cairoCopy = pluginDir.resolve("libdecor-cairo.so");
					Files.copy(systemCairo, cairoCopy, StandardCopyOption.REPLACE_EXISTING);
					cairoCopy.toFile().deleteOnExit();
				}

				nativeSetenv(setenv, "LIBDECOR_PLUGIN_DIR", pluginDir.toAbsolutePath().toString());
				System.out.println("[Starac] Set LIBDECOR_PLUGIN_DIR=" + pluginDir.toAbsolutePath());
			}
		} catch (Exception e) {
			System.out.println("[Starac] Could not setup libdecor plugin: " + e.getMessage());
		}
	}

	public static void ensureInitialized() {
		if (glfwInitialized) return;
		glfwInitialized = true;
		usingGlfwAsync = "glfw_async".equals(org.lwjgl.system.Configuration.GLFW_LIBRARY_NAME.get());
		GLFWErrorCallback.createPrint(System.err).set();

		if (!forceX11Fallback && System.getenv("WAYLAND_DISPLAY") != null && GLFW.glfwPlatformSupported(GLFW.GLFW_PLATFORM_WAYLAND)) {
			setupCursorTheme();
			// Compositors without xdg-decoration (e.g. GNOME, Cinnamon) need
			// a patched libdecor-gtk plugin for window decorations.
			if (needsLibdecor()) {
				setupLibdecorPlugin();
			}
			GLFW.glfwInitHint(GLFW.GLFW_PLATFORM, GLFW.GLFW_PLATFORM_WAYLAND);
			GLFW.glfwInitHint(GLFW.GLFW_WAYLAND_LIBDECOR, GLFW.GLFW_WAYLAND_PREFER_LIBDECOR);
		} else if (forceX11Fallback) {
			GLFW.glfwInitHint(GLFW.GLFW_PLATFORM, GLFW.GLFW_PLATFORM_X11);
		}

		if (!GLFW.glfwInit()) {
			if (!forceX11Fallback && System.getenv("WAYLAND_DISPLAY") != null) {
				System.out.println("[Starac] Wayland GLFW init failed, falling back to X11/XWayland");
				forceX11Fallback = true;
				glfwInitialized = false;
				ensureInitialized();
				return;
			}
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
		if (GLFW.glfwGetPlatform() == GLFW.GLFW_PLATFORM_WAYLAND) {
			DesktopFileInjector.updateTitle(title);
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
			return DesktopFileInjector.setIcon(icons);
		}

		if (!isCreated()) {
			// Cache icons for when the window is created
			cached_icons = Arrays.stream(icons).map(buf -> {
				ByteBuffer copy = ByteBuffer.allocateDirect(buf.remaining());
				int old_pos = buf.position();
				copy.put(buf);
				buf.position(old_pos);
				copy.flip();
				return copy;
			}).toArray(ByteBuffer[]::new);
			return 0;
		}

		if (GLFW.glfwGetPlatform() == GLFW.GLFW_PLATFORM_COCOA) {
			// glfwSetWindowIcon is a no-op on macOS; use NSApplication API instead.
			ByteBuffer largest = icons[0];
			for (ByteBuffer buf : icons) {
				if (buf.limit() > largest.limit()) largest = buf;
			}
			int size = largest.limit() / 4;
			int dim = (int) Math.sqrt(size);
			java.awt.image.BufferedImage img = new java.awt.image.BufferedImage(dim, dim, java.awt.image.BufferedImage.TYPE_INT_ARGB);
			int oldPos = largest.position();
			for (int i = 0; i < size; i++) {
				int a = largest.get(oldPos + i * 4) & 0xFF;
				int r = largest.get(oldPos + i * 4 + 1) & 0xFF;
				int g = largest.get(oldPos + i * 4 + 2) & 0xFF;
				int b = largest.get(oldPos + i * 4 + 3) & 0xFF;
				img.setRGB(i % dim, i / dim, (a << 24) | (r << 16) | (g << 8) | b);
			}
			try {
				java.io.ByteArrayOutputStream baos = new java.io.ByteArrayOutputStream();
				javax.imageio.ImageIO.write(img, "png", baos);
				MacOSDisplayHelper.setDockIcon(baos.toByteArray());
			} catch (java.io.IOException e) {
				System.err.println("[Display] Failed to encode icon as PNG: " + e.getMessage());
			}
			return 1;
		}

		// X11 / Windows: convert ARGB to RGBA and pass to GLFW
		try (MemoryStack memoryStack = MemoryStack.stackPush()) {
			Buffer glfwBuffer = GLFWImage.malloc(icons.length, memoryStack);

			for (ByteBuffer buf : icons) {
				int pixelCount = buf.remaining() / 4;
				int dimension = (int) Math.sqrt(pixelCount);

				ByteBuffer rgba = MemoryUtil.memAlloc(pixelCount * 4);
				for (int i = 0; i < pixelCount; i++) {
					int base = buf.position() + i * 4;
					byte a = buf.get(base);
					byte r = buf.get(base + 1);
					byte g = buf.get(base + 2);
					byte b = buf.get(base + 3);
					rgba.put(r).put(g).put(b).put(a);
				}
				rgba.flip();

				GLFWImage image = GLFWImage.malloc(memoryStack);
				image.set(dimension, dimension, rgba);
				glfwBuffer.put(image);
			}
			glfwBuffer.flip();

			System.out.println("[Starac] Setting window icon (" + icons.length + " sizes, handle=" + handle + ")");
			GLFW.glfwSetWindowIcon(handle, glfwBuffer);
		}
		return 1;
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
		if (OS.current() == OS.WINDOWS) {
			WindowsDisplayHelper.pollThemeChange();
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
		if (handle == MemoryUtil.NULL && !forceX11Fallback && GLFW.glfwGetPlatform() == GLFW.GLFW_PLATFORM_WAYLAND) {
			System.out.println("[Starac] Wayland window creation failed, falling back to X11/XWayland");
			GLFW.glfwTerminate();
			forceX11Fallback = true;
			glfwInitialized = false;
			ensureInitialized();
			create(pixelFormat);
			return;
		}
		if (handle == MemoryUtil.NULL) {
			throw new LWJGLException("Failed to create GLFW window");
		}
		width = displayMode.getWidth();
		height = displayMode.getHeight();
		// Cinnamon's Muffin attaches libdecor decorations late, sending a
		// configure that shrinks the window. Lock the minimum size until
		// the first resize callback fires.
		if (isCinnamonWayland()) {
			GLFW.glfwSetWindowSizeLimits(handle, displayMode.getWidth(), displayMode.getHeight(), GLFW.GLFW_DONT_CARE, GLFW.GLFW_DONT_CARE);
			cinnamonSizeLimitActive = true;
		}
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
		// Center window on primary monitor (not supported on Wayland)
		if (GLFW.glfwGetPlatform() != GLFW.GLFW_PLATFORM_WAYLAND) {
			long primaryMonitor = GLFW.glfwGetPrimaryMonitor();
			if (primaryMonitor != MemoryUtil.NULL) {
				GLFWVidMode vidMode = GLFW.glfwGetVideoMode(primaryMonitor);
				if (vidMode != null) {
					GLFW.glfwSetWindowPos(handle,
							(vidMode.width() - displayMode.getWidth()) / 2,
							(vidMode.height() - displayMode.getHeight()) / 2);
				}
			}
		}
		// Enable dark titlebar on Windows 10/11
		if (OS.current() == OS.WINDOWS) {
			WindowsDisplayHelper.init(handle);
		}
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

	private static int windowedX, windowedY, windowedWidth, windowedHeight;

	public static void setFullscreen(boolean fullscreen) {
		boolean isWayland = GLFW.glfwGetPlatform() == GLFW.GLFW_PLATFORM_WAYLAND;
		try {
			if (fullscreen) {
				// Save windowed position and size for restoration
				if (!isWayland) {
					int[] wx = new int[1], wy = new int[1];
					GLFW.glfwGetWindowPos(handle, wx, wy);
					windowedX = wx[0];
					windowedY = wy[0];
				}
				int[] ww = new int[1], wh = new int[1];
				GLFW.glfwGetWindowSize(handle, ww, wh);
				windowedWidth = ww[0];
				windowedHeight = wh[0];

				// Use the monitor's native resolution — no display mode change
				long monitor = GLFW.glfwGetPrimaryMonitor();
				GLFWVidMode vidMode = GLFW.glfwGetVideoMode(monitor);
				if (vidMode != null) {
					GLFW.glfwSetWindowMonitor(handle, monitor,
							0, 0,
							vidMode.width(), vidMode.height(),
							vidMode.refreshRate());
					resizeCallback(handle, vidMode.width(), vidMode.height());
				}
			} else {
				// Restore windowed mode with saved position and size
				GLFW.glfwSetWindowMonitor(handle, MemoryUtil.NULL,
						isWayland ? 0 : windowedX,
						isWayland ? 0 : windowedY,
						windowedWidth, windowedHeight,
						-1);
				resizeCallback(handle, windowedWidth, windowedHeight);
			}

			// Surface may have been recreated — reset native cursor warp state
			if (isWayland) {
				WaylandCenterCursor.reset();
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
			if (cinnamonSizeLimitActive) {
				cinnamonSizeLimitActive = false;
				GLFW.glfwSetWindowSizeLimits(handle, GLFW.GLFW_DONT_CARE, GLFW.GLFW_DONT_CARE, GLFW.GLFW_DONT_CARE, GLFW.GLFW_DONT_CARE);
			}
			window_resized = true;
			Display.width = width;
			Display.height = height;
		}
	}

	public static void swapBuffers() {
		GLFW.glfwSwapBuffers(handle);
	}

	public static Drawable getDrawable() {
		return new Drawable() {
			@Override
			public void makeCurrent() throws LWJGLException {
				GLFW.glfwMakeContextCurrent(handle);
			}

			@Override
			public void releaseContext() throws LWJGLException {
				GLFW.glfwMakeContextCurrent(MemoryUtil.NULL);
			}

			@Override
			public boolean isCurrent() throws LWJGLException {
				return GLFW.glfwGetCurrentContext() == handle;
			}

			@Override
			public void destroy() {
			}
		};
	}
}
