package net.fabricmc.loader.impl.game.minecraft.applet;

import java.io.File;

/**
 * Replacement for Fabric Loader's AppletMain that avoids AWT on macOS.
 * On macOS with -XstartOnFirstThread, AWT's native Cocoa initialization
 * deadlocks because AWT's event thread is not the main/AppKit thread.
 * This replacement launches the applet frame directly on the main thread
 * when running on macOS, bypassing EventQueue.invokeLater().
 */
public final class AppletMain implements Runnable {
	final String[] args;

	private AppletMain(String[] args) {
		this.args = args;
	}

	public static File hookGameDir(File file) {
		File proposed = AppletLauncher.gameDir;

		if (proposed != null) {
			return proposed;
		} else {
			return file;
		}
	}

	public static void main(String[] args) {
		AppletMain instance = new AppletMain(args);

		if (System.getProperty("os.name", "").toLowerCase().contains("mac")) {
			// On macOS with -XstartOnFirstThread, we must run on the main thread
			// (current thread) instead of the AWT EventQueue thread, because AWT's
			// Cocoa backend requires the AppKit main thread for native calls.
			instance.run();
		} else {
			java.awt.EventQueue.invokeLater(instance);
		}
	}

	@Override
	public void run() {
		AppletFrame me = new AppletFrame("Minecraft", null);
		me.launch(args);
	}
}
