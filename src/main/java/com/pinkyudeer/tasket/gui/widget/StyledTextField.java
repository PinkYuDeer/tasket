package com.pinkyudeer.tasket.gui.widget;

import com.cleanroommc.modularui.api.drawable.IDrawable;
import com.cleanroommc.modularui.theme.WidgetThemeEntry;
import com.cleanroommc.modularui.widgets.textfield.TextFieldWidget;

import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;

@SideOnly(Side.CLIENT)
public class StyledTextField extends TextFieldWidget {

    private final IDrawable normalBackground;
    private final IDrawable hoverBackground;
    private final IDrawable focusBackground;

    public StyledTextField(IDrawable normalBackground, IDrawable hoverBackground, IDrawable focusBackground) {
        this.normalBackground = normalBackground;
        this.hoverBackground = hoverBackground;
        this.focusBackground = focusBackground;
        background(normalBackground);
        hoverBackground(hoverBackground);
        setTextColor(0xFFFFFFFF);
        setMarkedColor(0xFF2F72A8);
        hintColor(0xFF777777);
    }

    @Override
    public IDrawable getCurrentBackground(WidgetThemeEntry<?> widgetTheme) {
        if (isFocused() && focusBackground != null) return focusBackground;
        if (isHovering() && hoverBackground != null) return hoverBackground;
        return normalBackground;
    }
}
