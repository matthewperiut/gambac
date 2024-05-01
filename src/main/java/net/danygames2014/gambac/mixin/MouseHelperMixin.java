package net.danygames2014.gambac.mixin;

import net.minecraft.class_596;
import org.lwjgl.input.Mouse;
import org.lwjgl.opengl.Display;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;

@Mixin(class_596.class)
public class MouseHelperMixin {

    /**
     * @author DanyGames2014
     * @reason like i really dont give a shit
     */
    @Overwrite
    public void method_1971(){
        Mouse.setCursorPosition(Display.getWidth() / 2, Display.getHeight() / 2);
        Mouse.setGrabbed(false);
    }
}
