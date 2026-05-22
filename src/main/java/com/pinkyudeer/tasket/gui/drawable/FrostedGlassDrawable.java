package com.pinkyudeer.tasket.gui.drawable;

import java.nio.FloatBuffer;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.ScaledResolution;
import net.minecraft.client.renderer.OpenGlHelper;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.client.shader.Framebuffer;
import net.minecraft.util.ResourceLocation;

import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.ARBShaderObjects;
import org.lwjgl.opengl.Display;
import org.lwjgl.opengl.EXTFramebufferObject;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL12;

import com.cleanroommc.modularui.api.drawable.IDrawable;
import com.cleanroommc.modularui.screen.viewport.GuiContext;
import com.cleanroommc.modularui.theme.WidgetTheme;
import com.cleanroommc.modularui.utils.GlStateManager;
import com.github.bsideup.jabel.Desugar;
import com.pinkyudeer.tasket.Tasket;
import com.pinkyudeer.tasket.gui.panel.AnimatedPanel;
import com.pinkyudeer.tasket.render.RenderHelper;
import com.pinkyudeer.tasket.render.ShaderHelper;

import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;

@SideOnly(Side.CLIENT)
public class FrostedGlassDrawable implements IDrawable {

    private static final ResourceLocation NORMAL_VERT = new ResourceLocation("tasket", "shaders/normal.vert");
    private static final ResourceLocation BLUR_FRAG = new ResourceLocation("tasket", "shaders/blur.frag");
    private static final ResourceLocation FROSTED_GLASS_FRAG = new ResourceLocation(
        "tasket",
        "shaders/frosted_glass.frag");

    private static final int MAX_DOWNSCALE_LEVELS = 3;

    private static final Minecraft MC = Minecraft.getMinecraft();

    private static Framebuffer captureBuffer;
    private static Framebuffer[] downscaleBuffers;
    private static Framebuffer blurBufferH;
    private static Framebuffer blurBufferV;
    private static int bufferWidth;
    private static int bufferHeight;
    private static int bufferDownscaleLevels;
    private static int blurShaderProgram;
    private static int frostedGlassShaderProgram;
    private static boolean shaderLoadFailed;
    private static final FloatBuffer MODEL_VIEW_BUFFER = BufferUtils.createFloatBuffer(16);

    private final float cornerRadius;
    private final int downscaleLevels;
    private final float blurRadius;

    private FrostedGlassDrawable(float cornerRadius, int downscaleLevels, float blurRadius) {
        this.cornerRadius = cornerRadius;
        this.downscaleLevels = Math.max(1, Math.min(MAX_DOWNSCALE_LEVELS, downscaleLevels));
        this.blurRadius = Math.max(0.1f, blurRadius);
    }

    public static FrostedGlassDrawable create(float cornerRadius) {
        return create(cornerRadius, 1, 12.0f);
    }

    public static FrostedGlassDrawable create(float cornerRadius, int downscaleLevels, float blurRadius) {
        return new FrostedGlassDrawable(cornerRadius, downscaleLevels, blurRadius);
    }

    @Override
    public void draw(GuiContext context, int x, int y, int width, int height, WidgetTheme widgetTheme) {
        if (width <= 0 || height <= 0) return;
        if (!canRender()) return;
        if (!ensureShaders() || !ensureFramebuffers(downscaleLevels)) return;

        PanelBounds bounds = readPanelBounds(x, y, width, height);
        if (bounds.pixelWidth <= 0.0f || bounds.pixelHeight <= 0.0f) return;

        int savedFbo = GL11.glGetInteger(EXTFramebufferObject.GL_FRAMEBUFFER_BINDING_EXT);
        boolean lightingEnabled = GL11.glIsEnabled(GL11.GL_LIGHTING);
        boolean depthEnabled = GL11.glIsEnabled(GL11.GL_DEPTH_TEST);
        boolean alphaEnabled = GL11.glIsEnabled(GL11.GL_ALPHA_TEST);
        boolean textureEnabled = GL11.glIsEnabled(GL11.GL_TEXTURE_2D);
        boolean blendEnabled = GL11.glIsEnabled(GL11.GL_BLEND);
        try {
            GlStateManager.disableLighting();
            GlStateManager.disableDepth();
            GlStateManager.disableAlpha();
            GlStateManager.enableTexture2D();
            GlStateManager.disableBlend();
            GlStateManager.color(1.0f, 1.0f, 1.0f, 1.0f);

            pushMatrices();
            try {
                copyTextureTo(captureBuffer, MC.getFramebuffer().framebufferTexture);
                performDownSampling();
                blur(downscaleBuffers[downscaleLevels - 1], blurBufferH, 1.0f, 0.0f);
                blur(blurBufferH, blurBufferV, 0.0f, 1.0f);
                performUpSampling();
            } finally {
                popMatrices();
            }

            restoreFramebuffer(savedFbo);
            composite(bounds, x, y, width, height);
        } finally {
            ARBShaderObjects.glUseProgramObjectARB(0);
            restoreFramebuffer(savedFbo);
            restoreLighting(lightingEnabled);
            restoreDepth(depthEnabled);
            restoreAlpha(alphaEnabled);
            restoreTexture(textureEnabled);
            restoreBlend(blendEnabled);
            GlStateManager.color(1.0f, 1.0f, 1.0f, 1.0f);
        }
    }

    private static boolean canRender() {
        return OpenGlHelper.isFramebufferEnabled() && OpenGlHelper.shadersSupported
            && Display.isVisible()
            && MC != null
            && MC.getFramebuffer() != null;
    }

    private static boolean ensureShaders() {
        if (shaderLoadFailed) return false;
        try {
            if (blurShaderProgram == 0) {
                blurShaderProgram = ShaderHelper.createProgram(NORMAL_VERT, BLUR_FRAG);
            }
            if (frostedGlassShaderProgram == 0) {
                frostedGlassShaderProgram = ShaderHelper.createProgram(NORMAL_VERT, FROSTED_GLASS_FRAG);
            }
        } catch (RuntimeException e) {
            shaderLoadFailed = true;
            Tasket.LOG.warn("Failed to initialize frosted glass shaders", e);
            return false;
        }
        return blurShaderProgram != 0 && frostedGlassShaderProgram != 0;
    }

    private static boolean ensureFramebuffers(int downscaleLevels) {
        if (captureBuffer != null && bufferWidth == MC.displayWidth
            && bufferHeight == MC.displayHeight
            && bufferDownscaleLevels == downscaleLevels) {
            return buffersValid();
        }

        cleanupFramebuffers();
        bufferWidth = MC.displayWidth;
        bufferHeight = MC.displayHeight;
        bufferDownscaleLevels = downscaleLevels;

        captureBuffer = createFramebuffer(bufferWidth, bufferHeight);

        downscaleBuffers = new Framebuffer[downscaleLevels];
        int downscaleWidth = bufferWidth;
        int downscaleHeight = bufferHeight;
        for (int i = 0; i < downscaleLevels; i++) {
            downscaleWidth = Math.max(1, downscaleWidth / 2);
            downscaleHeight = Math.max(1, downscaleHeight / 2);
            downscaleBuffers[i] = createFramebuffer(downscaleWidth, downscaleHeight);
        }

        blurBufferH = createFramebuffer(downscaleWidth, downscaleHeight);
        blurBufferV = createFramebuffer(downscaleWidth, downscaleHeight);
        return buffersValid();
    }

    private static Framebuffer createFramebuffer(int width, int height) {
        Framebuffer framebuffer = new Framebuffer(width, height, false);
        framebuffer.setFramebufferFilter(GL11.GL_LINEAR);
        configureFramebufferTexture(framebuffer);
        return framebuffer;
    }

    private static boolean buffersValid() {
        if (!isFramebufferValid(captureBuffer) || downscaleBuffers == null
            || downscaleBuffers.length != bufferDownscaleLevels
            || !isFramebufferValid(blurBufferH)
            || !isFramebufferValid(blurBufferV)) {
            return false;
        }
        for (Framebuffer downscaleBuffer : downscaleBuffers) {
            if (!isFramebufferValid(downscaleBuffer)) return false;
        }
        return true;
    }

    private static boolean isFramebufferValid(Framebuffer framebuffer) {
        return framebuffer != null && framebuffer.framebufferObject > 0;
    }

    private static void cleanupFramebuffers() {
        deleteFramebuffer(captureBuffer);
        if (downscaleBuffers != null) {
            for (Framebuffer downscaleBuffer : downscaleBuffers) {
                deleteFramebuffer(downscaleBuffer);
            }
        }
        deleteFramebuffer(blurBufferH);
        deleteFramebuffer(blurBufferV);
        captureBuffer = null;
        downscaleBuffers = null;
        blurBufferH = null;
        blurBufferV = null;
    }

    private static void deleteFramebuffer(Framebuffer framebuffer) {
        if (framebuffer != null) framebuffer.deleteFramebuffer();
    }

    private static void configureFramebufferTexture(Framebuffer framebuffer) {
        GlStateManager.bindTexture(framebuffer.framebufferTexture);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_S, GL12.GL_CLAMP_TO_EDGE);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_T, GL12.GL_CLAMP_TO_EDGE);
        GlStateManager.bindTexture(0);
    }

    private static void pushMatrices() {
        GlStateManager.matrixMode(GL11.GL_PROJECTION);
        GlStateManager.pushMatrix();
        GlStateManager.matrixMode(GL11.GL_MODELVIEW);
        GlStateManager.pushMatrix();
    }

    private static void popMatrices() {
        GlStateManager.matrixMode(GL11.GL_PROJECTION);
        GlStateManager.popMatrix();
        GlStateManager.matrixMode(GL11.GL_MODELVIEW);
        GlStateManager.popMatrix();
    }

    private void blur(Framebuffer input, Framebuffer output, float directionX, float directionY) {
        if (!isFramebufferValid(input) || !isFramebufferValid(output)) return;
        output.framebufferClear();
        output.bindFramebuffer(false);
        GlStateManager.viewport(0, 0, output.framebufferWidth, output.framebufferHeight);

        ARBShaderObjects.glUseProgramObjectARB(blurShaderProgram);
        ShaderHelper.setUniform1i(blurShaderProgram, "texture", 0);
        ShaderHelper.setUniform2f(
            blurShaderProgram,
            "texelSize",
            1.0f / (float) input.framebufferWidth,
            1.0f / (float) input.framebufferHeight);
        ShaderHelper.setUniform2f(blurShaderProgram, "direction", directionX, directionY);
        ShaderHelper.setUniform1f(blurShaderProgram, "radius", blurRadius);
        drawFramebuffer(input.framebufferTexture);
        ARBShaderObjects.glUseProgramObjectARB(0);
    }

    private static void performDownSampling() {
        Framebuffer input = captureBuffer;
        for (Framebuffer output : downscaleBuffers) {
            copyTextureTo(output, input.framebufferTexture);
            input = output;
        }
    }

    private static void performUpSampling() {
        Framebuffer input = blurBufferV;
        for (int i = downscaleBuffers.length - 2; i >= 0; i--) {
            Framebuffer output = downscaleBuffers[i];
            copyTextureTo(output, input.framebufferTexture);
            input = output;
        }
        copyTextureTo(captureBuffer, input.framebufferTexture);
    }

    private static void copyTextureTo(Framebuffer output, int texture) {
        if (!isFramebufferValid(output)) return;
        output.framebufferClear();
        output.bindFramebuffer(false);
        GlStateManager.viewport(0, 0, output.framebufferWidth, output.framebufferHeight);
        drawFramebuffer(texture);
    }

    private static void drawFramebuffer(int texture) {
        GlStateManager.bindTexture(texture);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_S, GL12.GL_CLAMP_TO_EDGE);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_T, GL12.GL_CLAMP_TO_EDGE);

        GlStateManager.matrixMode(GL11.GL_PROJECTION);
        GlStateManager.loadIdentity();
        GL11.glOrtho(0.0D, 1.0D, 1.0D, 0.0D, -1.0D, 1.0D);
        GlStateManager.matrixMode(GL11.GL_MODELVIEW);
        GlStateManager.loadIdentity();

        Tessellator tessellator = Tessellator.instance;
        tessellator.startDrawingQuads();
        tessellator.addVertexWithUV(0, 1, 0, 0, 0);
        tessellator.addVertexWithUV(1, 1, 0, 1, 0);
        tessellator.addVertexWithUV(1, 0, 0, 1, 1);
        tessellator.addVertexWithUV(0, 0, 0, 0, 1);
        tessellator.draw();
    }

    private PanelBounds readPanelBounds(int x, int y, int width, int height) {
        MODEL_VIEW_BUFFER.clear();
        GL11.glGetFloat(GL11.GL_MODELVIEW_MATRIX, MODEL_VIEW_BUFFER);
        FloatBuffer modelView = MODEL_VIEW_BUFFER;

        float m0 = modelView.get(0);
        float m1 = modelView.get(1);
        float m4 = modelView.get(4);
        float m5 = modelView.get(5);
        float m12 = modelView.get(12);
        float m13 = modelView.get(13);

        float guiX = m0 * x + m4 * y + m12;
        float guiY = m1 * x + m5 * y + m13;
        float guiWidth = width * (float) Math.sqrt(m0 * m0 + m1 * m1);
        float guiHeight = height * (float) Math.sqrt(m4 * m4 + m5 * m5);

        ScaledResolution scaledResolution = new ScaledResolution(MC, MC.displayWidth, MC.displayHeight);
        int scale = scaledResolution.getScaleFactor();

        float px = guiX * scale;
        float py = guiY * scale;
        float pw = guiWidth * scale;
        float ph = guiHeight * scale;

        float uvOffsetX = px / (float) MC.displayWidth;
        float uvOffsetY = 1.0f - (py + ph) / (float) MC.displayHeight;
        float uvScaleX = pw / (float) MC.displayWidth;
        float uvScaleY = ph / (float) MC.displayHeight;
        return new PanelBounds(pw, ph, uvOffsetX, uvOffsetY, uvScaleX, uvScaleY);
    }

    private void composite(PanelBounds bounds, int x, int y, int width, int height) {
        GlStateManager.viewport(0, 0, MC.displayWidth, MC.displayHeight);
        GlStateManager.enableBlend();
        GlStateManager.blendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
        GlStateManager.enableTexture2D();
        GlStateManager.bindTexture(captureBuffer.framebufferTexture);

        ARBShaderObjects.glUseProgramObjectARB(frostedGlassShaderProgram);
        ShaderHelper.setUniform1i(frostedGlassShaderProgram, "u_blurredTex", 0);
        ShaderHelper.setUniform2f(frostedGlassShaderProgram, "iResolution", width, height);
        ShaderHelper.setUniform2f(frostedGlassShaderProgram, "u_screenUVOffset", bounds.uvOffsetX, bounds.uvOffsetY);
        ShaderHelper.setUniform2f(frostedGlassShaderProgram, "u_screenUVScale", bounds.uvScaleX, bounds.uvScaleY);
        ShaderHelper.setUniform1f(frostedGlassShaderProgram, "u_alpha", AnimatedPanel.currentDrawAlpha);
        ShaderHelper.setUniform1f(frostedGlassShaderProgram, "u_continuityIndex", 3.0f);
        ShaderHelper.setUniform2f(frostedGlassShaderProgram, "u_rectSize", 1.0f, 1.0f);
        ShaderHelper.setUniform2f(frostedGlassShaderProgram, "u_rectCenter", 0.5f, 0.5f);
        ShaderHelper.setUniform1f(frostedGlassShaderProgram, "u_rectEdgeSoftness", 1.0f);

        float normalizedRadius = normalizeRadius(cornerRadius, width, height);
        ShaderHelper.setUniform4f(
            frostedGlassShaderProgram,
            "u_cornerRadiuses",
            normalizedRadius,
            normalizedRadius,
            normalizedRadius,
            normalizedRadius);

        boolean translate = x != 0 || y != 0;
        if (translate) {
            GlStateManager.pushMatrix();
            GlStateManager.translate(x, y, 0.0f);
        }
        RenderHelper.drawRelativeRect(width, height, true);
        if (translate) {
            GlStateManager.popMatrix();
        }
        ARBShaderObjects.glUseProgramObjectARB(0);
    }

    private static void restoreFramebuffer(int framebufferObject) {
        OpenGlHelper.func_153171_g(OpenGlHelper.field_153198_e, framebufferObject);
    }

    private static void restoreLighting(boolean enabled) {
        if (enabled) GlStateManager.enableLighting();
        else GlStateManager.disableLighting();
    }

    private static void restoreDepth(boolean enabled) {
        if (enabled) GlStateManager.enableDepth();
        else GlStateManager.disableDepth();
    }

    private static void restoreAlpha(boolean enabled) {
        if (enabled) GlStateManager.enableAlpha();
        else GlStateManager.disableAlpha();
    }

    private static void restoreTexture(boolean enabled) {
        if (enabled) GlStateManager.enableTexture2D();
        else GlStateManager.disableTexture2D();
    }

    private static void restoreBlend(boolean enabled) {
        if (enabled) GlStateManager.enableBlend();
        else GlStateManager.disableBlend();
    }

    private static float normalizeRadius(float radius, int width, int height) {
        float minSize = Math.max(1.0f, Math.min(width, height));
        return Math.abs(radius) > 0.5f ? radius / minSize : radius;
    }

    @Desugar
    private record PanelBounds(float pixelWidth, float pixelHeight, float uvOffsetX, float uvOffsetY, float uvScaleX,
        float uvScaleY) {}
}
