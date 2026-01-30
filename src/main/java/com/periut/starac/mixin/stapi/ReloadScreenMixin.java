package com.periut.starac.mixin.stapi;

import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.renderer.Tesselator;
import net.modificationstation.stationapi.api.client.resource.ReloadScreenManager;
import net.modificationstation.stationapi.api.resource.ResourceReload;
import org.lwjgl.opengl.GL11;
import org.spongepowered.asm.mixin.*;

import java.awt.*;
import java.text.NumberFormat;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletionException;

@Mixin(targets = "net.modificationstation.stationapi.api.client.resource.ReloadScreen", remap = false)
public abstract class ReloadScreenMixin extends Screen {

    @Shadow
    protected abstract void fill(int startX, int startY, int endX, int endY, int color);

    @Shadow
    private boolean exceptionThrown;

    @Shadow
    private boolean finished;

    @Shadow
    private Exception exception;

    @Shadow
    @Final
    private Runnable done;

    @Shadow
    @Final
    private Screen parent;

    @Shadow
    private float progress;

    @Shadow
    @Final
    private Tesselator tessellator;

    @Unique
    private static final NumberFormat NUMBER_FORMAT = NumberFormat.getNumberInstance();

    static {
        NUMBER_FORMAT.setMinimumFractionDigits(2);
        NUMBER_FORMAT.setMaximumFractionDigits(2);
    }

    @Override
    public void render(int mouseX, int mouseY, float delta) {
        super.render(mouseX, mouseY, delta);
        if (parent == null) renderEarly();
        else renderNormal(delta);

        Optional<ResourceReload> reload;
        float target = (starac_isReloadStarted() && (reload = ReloadScreenManager.getCurrentReload()).isPresent())
                ? reload.orElse(null).getProgress() : 0;
        progress = Math.max(0, Math.min(progress * .95F + target * .05F, 1));
        if (Float.isNaN(progress)) progress = 0;
        if (!exceptionThrown && !finished && ReloadScreenManager.isReloadComplete()) {
            try {
                ReloadScreenManager.getCurrentReload().stream().peek(ResourceReload::throwException);
                finished = true;
            } catch (CompletionException e) {
                exceptionThrown = true;
                exception = e;
                System.err.println("[Starac] An exception occurred during resource loading");
                e.printStackTrace();
            }
        }
        if (finished) {
            ReloadScreenManagerAccessor.onFinish();
            done.run();
        }
    }

    @Unique
    private boolean starac_isReloadStarted() {
        return true;
    }

    @Unique
    private void renderEarly() {
        GL11.glBindTexture(3553, minecraft.textures.loadTexture("/title/mojang.png"));
        fill(0, 0, width, height, 0xFFFFFFFF);
        drawMojangLogoQuad((width - 256) / 2, (height - 256) / 2);
        GL11.glEnable(GL11.GL_BLEND);
        renderText(Color.BLACK, false);
        GL11.glDisable(GL11.GL_BLEND);
    }

    @Unique
    private void drawMojangLogoQuad(int i, int j) {
        float f = 0.00390625f;
        float f2 = 0.00390625f;
        tessellator.begin();
        tessellator.vertexUV(i, j + 256, 0.0, 0, 256 * f2);
        tessellator.vertexUV(i + 256, j + 256, 0.0, 256 * f, 256 * f2);
        tessellator.vertexUV(i + 256, j, 0.0, 256 * f, 0);
        tessellator.vertexUV(i, j, 0.0, 0, 0);
        tessellator.end();
    }

    @Unique
    private void renderNormal(float delta) {
        parent.render(-1, -1, delta);
        this.fillGradient(0, 0, this.width, this.height, -1072689136, -804253680);
        renderText(Color.WHITE, true);
    }

    @Unique
    private void renderText(Color textColor, boolean shadow) {
        if (exceptionThrown) font.draw("Oh noes! An error occurred, check your logs.", 0, 0, textColor.getRGB(), shadow);
        else font.draw("Loading resources...", 0, 0, textColor.getRGB(), shadow);
        List<String> locations = ReloadScreenManagerAccessor.getLocations();
        String s = locations.isEmpty() ? "Doing the do" : locations.get(locations.size() - 1);
        font.draw(s, 5, height - 10, textColor.getRGB(), shadow);
        String text = NUMBER_FORMAT.format(progress * 100f) + "%";
        int textRendererWidth = font.width(text);
        font.draw(text, width - textRendererWidth - 5, height - 10, textColor.getRGB(), shadow);
    }

    /**
     * @author Starac
     * @reason no animation so no need to wait
     */
    @Overwrite
    public boolean isReloadStarted() {
        return true;
    }
}
