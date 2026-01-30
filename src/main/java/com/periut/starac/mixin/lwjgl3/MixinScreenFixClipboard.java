package com.periut.starac.mixin.lwjgl3;

import java.util.Objects;
import net.minecraft.client.gui.screens.Screen;
import com.periut.starac.lwjgl3compat.annotations.Public;
import org.lwjgl.glfw.GLFW;
import org.lwjgl.opengl.Display;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;

@Mixin(Screen.class)
public class MixinScreenFixClipboard {

	/**
	 * @author moehreag
	 * @reason Fix clipboard access with GLFW
	 */
	@Overwrite
	public static String getClipboard(){
        return Objects.requireNonNullElse(GLFW.glfwGetClipboardString(Display.getHandle()), "");
	}

	/**
	 * @author moehreag
	 * @reason Fix clipboard access with GLFW
	 */
	//@Overwrite
	@Public
	private static void setClipboard(String string){
		GLFW.glfwSetClipboardString(Display.getHandle(), string);
	}
}
