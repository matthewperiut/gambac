package net.danygames2014.gambac.mixin;

import net.minecraft.client.Minecraft;
import org.lwjgl.LWJGLException;
import org.lwjgl.opengl.Display;
import org.lwjgl.opengl.PixelFormat;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.awt.*;

@Mixin(Minecraft.class)
public class MinecraftMixin {

//    @Unique
//    private long window;

//    @Redirect(
//            method = "init",
//            at = @At(
//                    value = "INVOKE",
//                    target = "Lorg/lwjgl/opengl/Display;create()V",
//                    remap = false,
//                    ordinal = 0))
//    private void init_disableOriginalDisplay() throws LWJGLException {
//        try {
//            Display.create(new PixelFormat().withDepthBits(24));
//        } catch (LWJGLException ex) {
//            Display.create();
//        }
//    }

//    @Inject(method = "init", at = @At(value = "INVOKE", target = "Lorg/lwjgl/opengl/Display;create()V", shift = At.Shift.AFTER))
//    public void createWindow(CallbackInfo ci){
//        System.out.println("Hello LWJGL " + Version.getVersion() + "!");
//        if ( !glfwInit() )
//            throw new IllegalStateException("Unable to initialize GLFW");
//
//        glfwDefaultWindowHints(); // optional, the current window hints are already the default
//        glfwWindowHint(GLFW_VISIBLE, GLFW_FALSE); // the window will stay hidden after creation
//        glfwWindowHint(GLFW_RESIZABLE, GLFW_TRUE); // the window will be resizable
//
//        window = GLFW.glfwCreateWindow(1280,720,"test", 0, 0);
//
//        try ( MemoryStack stack = stackPush() ) {
//            IntBuffer pWidth = stack.mallocInt(1); // int*
//            IntBuffer pHeight = stack.mallocInt(1); // int*
//
//            glfwGetWindowSize(window, pWidth, pHeight);
//
//            GLFWVidMode vidmode = glfwGetVideoMode(glfwGetPrimaryMonitor());
//
//            glfwSetWindowPos(
//                    window,
//                    (vidmode.width() - pWidth.get(0)) / 2,
//                    (vidmode.height() - pHeight.get(0)) / 2
//            );
//        }
//
//        glfwMakeContextCurrent(window);
//
//        GLFW.glfwShowWindow(window);
//    }


//    @Redirect(method = "init", at = @At(value = "INVOKE", target = "Lorg/lwjgl/opengl/Display;setParent(Ljava/awt/Canvas;)V"))
//    public void cancelParent(Canvas canvas){
//
//    }
}
