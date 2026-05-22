package com.pinkyudeer.tasket.gui.panel;

import com.cleanroommc.modularui.screen.ModularPanel;
import com.cleanroommc.modularui.screen.ModularScreen;

import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;

@SideOnly(Side.CLIENT)
public class AnimatedPanel extends ModularPanel {

    private static final long FADE_IN_MS = 250L;
    private static final long FADE_OUT_MS = 180L;

    public static float currentDrawAlpha = 1.0f;

    private long openTimeMs = -1L;
    private long closeStartMs = -1L;
    private boolean animatingClose;

    public AnimatedPanel(String name) {
        super(name);
    }

    @Override
    public void onOpen(ModularScreen screen) {
        super.onOpen(screen);
        openTimeMs = System.currentTimeMillis();
        closeStartMs = -1L;
        animatingClose = false;
    }

    @Override
    public void closeIfOpen() {
        if (!isOpen() || animatingClose) return;
        animatingClose = true;
        closeStartMs = System.currentTimeMillis();
    }

    @Override
    public void onUpdate() {
        super.onUpdate();
        if (animatingClose && closeStartMs >= 0L && System.currentTimeMillis() - closeStartMs >= FADE_OUT_MS) {
            animatingClose = false;
            super.closeIfOpen();
        }
    }

    public float getAnimationAlpha() {
        long now = System.currentTimeMillis();
        if (animatingClose) {
            float progress = (float) (now - closeStartMs) / FADE_OUT_MS;
            return 1.0f - easeOut(Math.min(1.0f, progress));
        }
        if (openTimeMs < 0L) return 1.0f;
        float progress = (float) (now - openTimeMs) / FADE_IN_MS;
        return easeOut(Math.min(1.0f, progress));
    }

    private static float easeOut(float t) {
        return 1.0f - (1.0f - t) * (1.0f - t);
    }
}
