package org.lwjgl.opengl;

import java.nio.ByteBuffer;

import org.lwjgl.glfw.GLFWNativeNSGL;
import org.lwjgl.system.JNI;
import org.lwjgl.system.MemoryUtil;
import org.lwjgl.system.macosx.ObjCRuntime;

/**
 * macOS-specific display helpers. This class is only loaded on macOS,
 * so it's safe to reference macOS-only LWJGL classes directly.
 */
final class MacOSDisplayHelper {
	private static long cglContext;

	private MacOSDisplayHelper() {}

	/**
	 * Opt the NSApplication into system appearance inheritance by calling
	 * [[NSApplication sharedApplication] setAppearance:nil].
	 * This must be called after glfwInit() (which creates NSApp) but before
	 * any windows are created, so there are no threading concerns.
	 */
	static void initAppAppearance() {
		try {
			long objc_msgSend = ObjCRuntime.getLibrary().getFunctionAddress("objc_msgSend");

			long nsAppClass = ObjCRuntime.objc_getClass("NSApplication");
			long selSharedApp = ObjCRuntime.sel_getUid("sharedApplication");
			long nsApp = JNI.invokePPP(nsAppClass, selSharedApp, objc_msgSend);
			if (nsApp == 0L) return;

			// [NSApp setAppearance:nil] — nil means inherit system appearance
			long selSetAppearance = ObjCRuntime.sel_getUid("setAppearance:");
			JNI.invokePPPP(nsApp, selSetAppearance, 0L, objc_msgSend);
		} catch (Exception e) {
			System.err.println("[Display] Failed to init macOS app appearance: " + e.getMessage());
		}
	}

	/**
	 * Get the CGL context from a GLFW window and lock it for the calling thread.
	 * This prevents the macOS compositor from accessing the GL surface concurrently,
	 * avoiding SIGSEGV in AppleMetalOpenGLRenderer during window resize.
	 */
	static void lockCGLContext(long windowHandle) {
		try {
			long nsgl = GLFWNativeNSGL.glfwGetNSGLContext(windowHandle);
			if (nsgl == 0L) return;

			long objc_msgSend = ObjCRuntime.getLibrary().getFunctionAddress("objc_msgSend");
			long selCGLContextObj = ObjCRuntime.sel_getUid("CGLContextObj");
			cglContext = JNI.invokePPP(nsgl, selCGLContextObj, objc_msgSend);

			if (cglContext != 0L) {
				CGL.CGLLockContext(cglContext);
			}
		} catch (Exception e) {
			System.err.println("[Display] Failed to lock CGL context: " + e.getMessage());
		}
	}

	static void unlockCGLContext() {
		if (cglContext != 0L) {
			CGL.CGLUnlockContext(cglContext);
		}
	}

	static void relockCGLContext() {
		if (cglContext != 0L) {
			CGL.CGLLockContext(cglContext);
		}
	}

	/**
	 * Sets the Dock icon via NSApplication from PNG-encoded bytes.
	 * glfwSetWindowIcon is a no-op on macOS, so this is the only way
	 * to set the app icon at runtime.
	 */
	static void setDockIcon(byte[] pngBytes) {
		try {
			// Write PNG to a temp file so we can use [NSImage initWithContentsOfFile:]
			java.nio.file.Path tmpIcon = java.nio.file.Files.createTempFile("starac-icon", ".png");
			java.nio.file.Files.write(tmpIcon, pngBytes);
			tmpIcon.toFile().deleteOnExit();

			long objc_msgSend = ObjCRuntime.getLibrary().getFunctionAddress("objc_msgSend");
			long selAlloc = ObjCRuntime.sel_getUid("alloc");

			// Create NSString for the file path
			long nsStringClass = ObjCRuntime.objc_getClass("NSString");
			long selStringWithUTF8 = ObjCRuntime.sel_getUid("stringWithUTF8String:");
			ByteBuffer pathBuf = MemoryUtil.memASCII(tmpIcon.toAbsolutePath().toString(), true);
			long nsPath = JNI.invokePPPP(nsStringClass, selStringWithUTF8, MemoryUtil.memAddress(pathBuf), objc_msgSend);
			MemoryUtil.memFree(pathBuf);
			if (nsPath == 0L) return;

			// [[NSImage alloc] initWithContentsOfFile:nsPath]
			long nsImageClass = ObjCRuntime.objc_getClass("NSImage");
			long nsImage = JNI.invokePPP(nsImageClass, selAlloc, objc_msgSend);
			long selInitWithFile = ObjCRuntime.sel_getUid("initWithContentsOfFile:");
			nsImage = JNI.invokePPPP(nsImage, selInitWithFile, nsPath, objc_msgSend);
			if (nsImage == 0L) return;

			// [[NSApplication sharedApplication] setApplicationIconImage:nsImage]
			long nsAppClass = ObjCRuntime.objc_getClass("NSApplication");
			long selSharedApp = ObjCRuntime.sel_getUid("sharedApplication");
			long nsApp = JNI.invokePPP(nsAppClass, selSharedApp, objc_msgSend);
			long selSetIcon = ObjCRuntime.sel_getUid("setApplicationIconImage:");
			JNI.invokePPPP(nsApp, selSetIcon, nsImage, objc_msgSend);

			System.out.println("[Starac] Set macOS Dock icon");
		} catch (Exception e) {
			System.err.println("[Display] Failed to set macOS Dock icon: " + e.getMessage());
		}
	}
}
