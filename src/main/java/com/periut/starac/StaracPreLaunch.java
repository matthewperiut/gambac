package com.periut.starac;

import net.fabricmc.loader.api.entrypoint.PreLaunchEntrypoint;
import org.lwjgl.system.Configuration;

public class StaracPreLaunch implements PreLaunchEntrypoint {
	@Override
	public void onPreLaunch() {
		String os = System.getProperty("os.name", "").toLowerCase();

		if (os.contains("linux")) {
			setupLinux();
		} else if (os.contains("mac")) {
			setupMacOS();
		}

	}

	private void setupLinux() {
		// GNOME lacks server-side decorations, so GLFW relies on libdecor's GTK
		// plugin for client-side decorations. The LWJGL-bundled GLFW has a
		// different libdecor build, so use the system GLFW on GNOME Wayland.
		String waylandDisplay = System.getenv("WAYLAND_DISPLAY");
		if (waylandDisplay == null || waylandDisplay.isEmpty()) {
			return;
		}

		String desktop = System.getenv("XDG_CURRENT_DESKTOP");
		if (desktop == null || !desktop.toUpperCase().contains("GNOME")) {
			return;
		}

		String arch = System.getProperty("os.arch", "");

		// Try common system GLFW paths, preferring arch-appropriate directories
		String[] candidates;
		if (arch.contains("aarch64")) {
			candidates = new String[]{
				"/usr/lib/aarch64-linux-gnu/libglfw.so.3",
				"/usr/lib/aarch64-linux-gnu/libglfw.so",
				"/usr/lib64/libglfw.so.3",
				"/usr/lib64/libglfw.so",
				"/usr/lib/libglfw.so.3",
				"/usr/lib/libglfw.so",
			};
		} else if (arch.contains("64")) {
			candidates = new String[]{
				"/usr/lib/x86_64-linux-gnu/libglfw.so.3",
				"/usr/lib/x86_64-linux-gnu/libglfw.so",
				"/usr/lib64/libglfw.so.3",
				"/usr/lib64/libglfw.so",
				"/usr/lib/libglfw.so.3",
				"/usr/lib/libglfw.so",
			};
		} else {
			candidates = new String[]{
				"/usr/lib/libglfw.so.3",
				"/usr/lib/libglfw.so",
			};
		}

		for (String path : candidates) {
			if (new java.io.File(path).exists()) {
				Configuration.GLFW_LIBRARY_NAME.set(path);
				System.out.println("[Starac] GNOME Wayland detected, using system GLFW: " + path);
				return;
			}
		}
	}

	private void setupMacOS() {
		// -XstartOnFirstThread deadlocks this mod on macOS: fabric-loader's
		// applet wrapper creates a java.awt.Frame, and AWT's Metal pipeline
		// init blocks forever waiting on the main-thread run loop, which
		// nobody pumps once main() returns. glfw_async has the same problem
		// (it dispatches to the unpumped main queue), so the only fix is to
		// relaunch the JVM without the flag.
		long pid = ProcessHandle.current().pid();
		if ("1".equals(System.getenv("JAVA_STARTED_ON_FIRST_THREAD_" + pid))) {
			relaunchWithoutStartOnFirstThread();
			return;
		}

		// On macOS without -XstartOnFirstThread, use LWJGL's glfw_async library
		// which dispatches Cocoa calls to the main thread internally.
		// This avoids the need for -XstartOnFirstThread entirely.
		Configuration.GLFW_CHECK_THREAD0.set(false);
		Configuration.GLFW_LIBRARY_NAME.set("glfw_async");

		System.out.println("[Starac] macOS detected without -XstartOnFirstThread, using glfw_async");
	}

	/**
	 * Respawns the JVM without -XstartOnFirstThread, pipes its output through
	 * this process (so launchers stay attached to their child and keep showing
	 * logs), and exits with the game's exit code.
	 *
	 * The command is rebuilt from the running JVM (input args + classpath +
	 * Knot + the game's launch args) rather than the original argv: launcher
	 * wrappers like Prism's EntryPoint read their config from stdin, which is
	 * already consumed, so re-running the original command would hang.
	 */
	private void relaunchWithoutStartOnFirstThread() {
		if (System.getProperty("starac.relaunched") != null) {
			// Relaunch guard: something re-added the flag; don't loop forever.
			System.err.println("[Starac] -XstartOnFirstThread still present after relaunch, giving up."
					+ " Remove it from your launcher's Java arguments (Prism: the FirstThreadOnMacOS trait).");
			return;
		}

		try {
			String javaBin = ProcessHandle.current().info().command()
					.orElse(System.getProperty("java.home") + "/bin/java");

			java.util.List<String> command = new java.util.ArrayList<>();
			command.add(javaBin);
			command.add("-Dstarac.relaunched=true");
			// -XstartOnFirstThread is consumed by the java launcher and not
			// reported here, but filter defensively in case a JVM passes it.
			for (String arg : java.lang.management.ManagementFactory.getRuntimeMXBean().getInputArguments()) {
				if (!"-XstartOnFirstThread".equals(arg)) {
					command.add(arg);
				}
			}
			command.add("-cp");
			command.add(System.getProperty("java.class.path"));

			boolean client = net.fabricmc.loader.api.FabricLoader.getInstance().getEnvironmentType()
					== net.fabricmc.api.EnvType.CLIENT;
			command.add(client
					? "net.fabricmc.loader.impl.launch.knot.KnotClient"
					: "net.fabricmc.loader.impl.launch.knot.KnotServer");
			command.addAll(java.util.Arrays.asList(
					net.fabricmc.loader.impl.FabricLoaderImpl.INSTANCE.getLaunchArguments(false)));

			System.out.println("[Starac] -XstartOnFirstThread deadlocks AWT on macOS; relaunching without it...");
			Process child = new ProcessBuilder(command).inheritIO().start();
			Runtime.getRuntime().addShutdownHook(new Thread(child::destroy));
			System.exit(child.waitFor());
		} catch (Exception e) {
			System.err.println("[Starac] Relaunch failed: " + e.getMessage()
					+ " — remove -XstartOnFirstThread from your launcher's Java arguments manually"
					+ " (Prism: the FirstThreadOnMacOS trait).");
		}
	}
}
