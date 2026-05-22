package com.pinkyudeer.tasket.gui.screen;

import javax.annotation.Nonnull;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.OpenGlHelper;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.client.shader.Framebuffer;

import org.lwjgl.input.Keyboard;
import org.lwjgl.opengl.EXTFramebufferObject;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL12;

import com.cleanroommc.modularui.api.MCHelper;
import com.cleanroommc.modularui.screen.CustomModularScreen;
import com.cleanroommc.modularui.screen.ModularPanel;
import com.cleanroommc.modularui.screen.viewport.ModularGuiContext;
import com.cleanroommc.modularui.utils.GlStateManager;
import com.cleanroommc.modularui.widget.WidgetTree;
import com.pinkyudeer.tasket.Tasket;
import com.pinkyudeer.tasket.gui.panel.AnimatedPanel;
import com.pinkyudeer.tasket.gui.panel.MainPanel;
import com.pinkyudeer.tasket.render.BlurHandler;

import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;
import lombok.Getter;

@SideOnly(Side.CLIENT)
public class TaskScreen extends CustomModularScreen {

    private static final long FADE_IN_MS = 300L;
    private static final long FADE_OUT_MS = 200L;
    private static final Minecraft MC = Minecraft.getMinecraft();
    private static Framebuffer panelFbo;

    private long openTime = -1;
    @Getter
    private boolean closing = false;
    private long closeStartTime = -1;
    private Runnable pendingClose;

    public TaskScreen() {
        super(Tasket.MODID);
    }

    @Override
    public @Nonnull ModularPanel buildUI(ModularGuiContext context) {
        return new MainPanel(this);
    }

    @Override
    public void drawScreen() {
        float alpha = calculateAlpha();
        BlurHandler.renderBlurredBackground(alpha);

        GlStateManager.disableRescaleNormal();
        net.minecraft.client.renderer.RenderHelper.disableStandardItemLighting();
        GlStateManager.disableLighting();
        GlStateManager.disableDepth();
        GlStateManager.disableAlpha();

        ModularGuiContext ctx = getContext();
        ctx.reset();
        ctx.pushViewport(null, ctx.getScreenArea());
        for (ModularPanel panel : getPanelManager().getReverseOpenPanels()) {
            ctx.updateZ(0);
            float panelAlpha = 1.0f;
            if (panel instanceof AnimatedPanel animatedPanel) {
                panelAlpha = animatedPanel.getAnimationAlpha();
                if (panelAlpha <= 0.0f) {
                    AnimatedPanel.currentDrawAlpha = 1.0f;
                    continue;
                }
            }

            boolean useFbo = panelAlpha < 0.999f && ensurePanelFbo();
            if (useFbo) {
                panelFbo.framebufferClear();
                panelFbo.bindFramebuffer(false);
                GlStateManager.viewport(0, 0, panelFbo.framebufferWidth, panelFbo.framebufferHeight);
            }

            AnimatedPanel.currentDrawAlpha = useFbo ? 1.0f : panelAlpha;
            try {
                WidgetTree.drawTree(panel, ctx);
            } finally {
                AnimatedPanel.currentDrawAlpha = 1.0f;
            }

            if (useFbo) {
                MC.getFramebuffer()
                    .bindFramebuffer(false);
                GlStateManager.viewport(0, 0, MC.displayWidth, MC.displayHeight);
                GlStateManager.enableBlend();
                GlStateManager.blendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
                GlStateManager.color(1.0f, 1.0f, 1.0f, panelAlpha);
                drawFboTexture(panelFbo);
                GlStateManager.color(1.0f, 1.0f, 1.0f, 1.0f);
            }

            GlStateManager.clearDepth(1);
            GlStateManager.clear(GL11.GL_DEPTH_BUFFER_BIT);
        }
        ctx.updateZ(0);
        ctx.popViewport(null);

        ctx.postRenderCallbacks.forEach(element -> element.accept(ctx));
        GlStateManager.enableRescaleNormal();
        net.minecraft.client.renderer.RenderHelper.enableStandardItemLighting();
        GlStateManager.enableAlpha();
    }

    private float calculateAlpha() {
        long now = System.currentTimeMillis();
        if (openTime < 0) openTime = now;

        if (closing) {
            float progress = (float) (now - closeStartTime) / FADE_OUT_MS;
            if (progress >= 1.0f) {
                closing = false;
                if (pendingClose != null) {
                    pendingClose.run();
                } else {
                    MCHelper.closeScreen();
                }
                return 0f;
            }
            return 1f - easeOut(progress);
        }

        float progress = (float) (now - openTime) / FADE_IN_MS;
        return easeOut(Math.min(1f, progress));
    }

    private static boolean ensurePanelFbo() {
        if (!OpenGlHelper.isFramebufferEnabled() || MC == null) return false;
        if (panelFbo != null && panelFbo.framebufferWidth == MC.displayWidth
            && panelFbo.framebufferHeight == MC.displayHeight
            && panelFbo.framebufferObject > 0) {
            return true;
        }
        int savedFbo = GL11.glGetInteger(EXTFramebufferObject.GL_FRAMEBUFFER_BINDING_EXT);
        if (panelFbo != null) {
            panelFbo.deleteFramebuffer();
            panelFbo = null;
        }
        panelFbo = new Framebuffer(MC.displayWidth, MC.displayHeight, false);
        panelFbo.setFramebufferFilter(GL11.GL_LINEAR);
        configureFramebufferTexture(panelFbo);
        OpenGlHelper.func_153171_g(OpenGlHelper.field_153198_e, savedFbo);
        return panelFbo.framebufferObject > 0;
    }

    private static void configureFramebufferTexture(Framebuffer framebuffer) {
        GlStateManager.bindTexture(framebuffer.framebufferTexture);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_S, GL12.GL_CLAMP_TO_EDGE);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_T, GL12.GL_CLAMP_TO_EDGE);
        GlStateManager.bindTexture(0);
    }

    private static void drawFboTexture(Framebuffer framebuffer) {
        GlStateManager.enableTexture2D();
        GlStateManager.bindTexture(framebuffer.framebufferTexture);

        GlStateManager.matrixMode(GL11.GL_PROJECTION);
        GlStateManager.pushMatrix();
        GlStateManager.loadIdentity();
        GL11.glOrtho(0.0D, 1.0D, 1.0D, 0.0D, -1.0D, 1.0D);
        GlStateManager.matrixMode(GL11.GL_MODELVIEW);
        GlStateManager.pushMatrix();
        GlStateManager.loadIdentity();
        try {
            Tessellator tessellator = Tessellator.instance;
            tessellator.startDrawingQuads();
            tessellator.addVertexWithUV(0, 1, 0, 0, 0);
            tessellator.addVertexWithUV(1, 1, 0, 1, 0);
            tessellator.addVertexWithUV(1, 0, 0, 1, 1);
            tessellator.addVertexWithUV(0, 0, 0, 0, 1);
            tessellator.draw();
        } finally {
            GlStateManager.matrixMode(GL11.GL_PROJECTION);
            GlStateManager.popMatrix();
            GlStateManager.matrixMode(GL11.GL_MODELVIEW);
            GlStateManager.popMatrix();
        }
    }

    private static float easeOut(float t) {
        return 1f - (1f - t) * (1f - t);
    }

    public void startClosing(Runnable onComplete) {
        if (!closing) {
            closing = true;
            closeStartTime = System.currentTimeMillis();
            pendingClose = onComplete;
        }
    }

    @Override
    public boolean onKeyPressed(char typedChar, int keyCode) {
        if (keyCode == Keyboard.KEY_ESCAPE) {
            ModularPanel topPanel = getPanelManager().getTopMostPanel();
            if (!getPanelManager().isMainPanel(topPanel)) {
                topPanel.closeIfOpen();
                return true;
            }
        }
        return super.onKeyPressed(typedChar, keyCode);
    }

    @Override
    public boolean doesPauseGame() {
        return false;
    }
}
