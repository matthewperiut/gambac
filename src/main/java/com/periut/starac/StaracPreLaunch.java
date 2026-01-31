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
		// Check if -XstartOnFirstThread is already active
		long pid = ProcessHandle.current().pid();
		if ("1".equals(System.getenv("JAVA_STARTED_ON_FIRST_THREAD_" + pid))) {
			return;
		}

		// On macOS without -XstartOnFirstThread, use LWJGL's glfw_async library
		// which dispatches Cocoa calls to the main thread internally.
		// This avoids the need for -XstartOnFirstThread entirely.
		Configuration.GLFW_CHECK_THREAD0.set(false);
		Configuration.GLFW_LIBRARY_NAME.set("glfw_async");

		System.out.println("[Starac] macOS detected without -XstartOnFirstThread, using glfw_async");
	}
}
