package com.periut.starac.mixin.lwjgl3;

import java.nio.IntBuffer;

import com.periut.starac.lwjgl3compat.annotations.CreateStub;
import org.lwjgl.opengl.ARBOcclusionQuery;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;

@Mixin(value = ARBOcclusionQuery.class, remap = false)
public abstract class ARBOcclusionQueryMixin {

	@CreateStub("glGetQueryObjectuARB")
	@Shadow
	public static void glGetQueryObjectuivARB(int id, int pname, IntBuffer params) {
	}

}
