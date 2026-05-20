package com.pinkyudeer.tasket.gui.widget;

import com.cleanroommc.modularui.api.GuiAxis;
import com.cleanroommc.modularui.widgets.layout.Flow;

import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;

/**
 * A vertical Flow that blocks click-through, preventing mouse events from reaching widgets
 * below it in the render order. Used for dropdown/overlay menus.
 */
@SideOnly(Side.CLIENT)
public class BlockingColumn extends Flow {

    public BlockingColumn() {
        super(GuiAxis.Y);
    }

    @Override
    public boolean canClickThrough() {
        return false;
    }

    @Override
    public boolean canHoverThrough() {
        return false;
    }
}
