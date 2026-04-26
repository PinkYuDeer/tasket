package com.pinkyudeer.tasket.gui.widget;

import com.cleanroommc.modularui.api.drawable.IDrawable;
import com.cleanroommc.modularui.api.widget.Interactable;
import com.cleanroommc.modularui.theme.WidgetThemeEntry;
import com.cleanroommc.modularui.widgets.ButtonWidget;

import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;

@SideOnly(Side.CLIENT)
public class StyledButtonWidget extends ButtonWidget<StyledButtonWidget> {

    private IDrawable normalBackground;
    private IDrawable hoverBackground;
    private IDrawable pressedBackground;
    private boolean pressed;

    public StyledButtonWidget(IDrawable normalBackground, IDrawable hoverBackground, IDrawable pressedBackground) {
        this.normalBackground = normalBackground;
        this.hoverBackground = hoverBackground;
        this.pressedBackground = pressedBackground;
        background(normalBackground);
        hoverBackground(hoverBackground);
    }

    public StyledButtonWidget setBackgrounds(IDrawable normalBackground, IDrawable hoverBackground,
        IDrawable pressedBackground) {
        this.normalBackground = normalBackground;
        this.hoverBackground = hoverBackground;
        this.pressedBackground = pressedBackground;
        background(normalBackground);
        hoverBackground(hoverBackground);
        return this;
    }

    @Override
    public IDrawable getCurrentBackground(WidgetThemeEntry<?> widgetTheme) {
        if (pressed && pressedBackground != null) return pressedBackground;
        if (isHovering() && hoverBackground != null) return hoverBackground;
        return normalBackground;
    }

    @Override
    public Interactable.Result onMousePressed(int mouseButton) {
        if (mouseButton == 0) pressed = true;
        Interactable.Result result = super.onMousePressed(mouseButton);
        if (result == Interactable.Result.IGNORE) pressed = false;
        return result;
    }

    @Override
    public boolean onMouseRelease(int mouseButton) {
        pressed = false;
        return super.onMouseRelease(mouseButton);
    }

    @Override
    public void onMouseLeaveArea() {
        pressed = false;
        super.onMouseLeaveArea();
    }
}
