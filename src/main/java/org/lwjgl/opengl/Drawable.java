package org.lwjgl.opengl;

import org.lwjgl.LWJGLException;

public interface Drawable {
	void makeCurrent() throws LWJGLException;
	void releaseContext() throws LWJGLException;
	boolean isCurrent() throws LWJGLException;
	void destroy();
}
