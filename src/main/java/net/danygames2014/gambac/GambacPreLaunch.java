package net.danygames2014.gambac;

import net.fabricmc.loader.api.entrypoint.PreLaunchEntrypoint;
import org.lwjgl.system.Configuration;

public class GambacPreLaunch implements PreLaunchEntrypoint {
	@Override
	public void onPreLaunch() {
		if (!System.getProperty("os.name", "").toLowerCase().contains("mac")) {
			return;
		}

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

		System.out.println("[Gambac] macOS detected without -XstartOnFirstThread, using glfw_async");
	}
}
