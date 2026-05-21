package com.pinkyudeer.tasket.gui.panel;

import org.lwjgl.input.Keyboard;

import com.cleanroommc.modularui.screen.ModularPanel;
import com.pinkyudeer.tasket.gui.GuiStyle;

import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;

@SideOnly(Side.CLIENT)
public class InputSafePanel extends ModularPanel {

    public InputSafePanel(String name) {
        super(name);
    }

    @Override
    public boolean onKeyPressed(char character, int keyCode) {
        boolean handled = super.onKeyPressed(character, keyCode);
        if (handled) return true;
        if (keyCode == Keyboard.KEY_ESCAPE) {
            closeIfOpen();
            return true;
        }
        return GuiStyle.shouldKeepTypingFocus(this, keyCode);
    }

    @Override
    public boolean closeOnOutOfBoundsClick() {
        return true;
    }
}
