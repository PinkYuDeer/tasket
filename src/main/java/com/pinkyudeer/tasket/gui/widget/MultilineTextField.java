package com.pinkyudeer.tasket.gui.widget;

import java.util.Collections;

import com.cleanroommc.modularui.screen.viewport.ModularGuiContext;
import com.cleanroommc.modularui.theme.TextFieldTheme;
import com.cleanroommc.modularui.utils.Alignment;
import com.cleanroommc.modularui.widget.scroll.VerticalScrollData;
import com.cleanroommc.modularui.widgets.textfield.BaseTextFieldWidget;

import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;

/**
 * Multiline text field supporting Enter for line breaks and vertical scrolling.
 * Unlike {@link com.cleanroommc.modularui.widgets.textfield.TextFieldWidget}, this widget
 * does not enforce single-line mode and supports vertical scroll.
 */
@SideOnly(Side.CLIENT)
public class MultilineTextField extends BaseTextFieldWidget<MultilineTextField> {

    public MultilineTextField() {
        this.handler.setMaxLines(10000);
        this.textAlignment = Alignment.TopLeft;
        padding(2, 6);
        getScrollArea().setScrollDataY(new VerticalScrollData(false, 4));
        ensureTextLine();
    }

    @Override
    protected void drawText(ModularGuiContext context, TextFieldTheme widgetTheme) {
        ensureTextLine();
        try {
            if (this.handler.isTextEmpty() && this.hintText != null) {
                int c = this.renderer.getColor();
                int hintColor = this.hintTextColor != null ? this.hintTextColor : widgetTheme.getHintColor();
                this.renderer.setColor(hintColor);
                this.renderer.draw(Collections.singletonList(this.hintText));
                this.renderer.setColor(c);
            } else {
                this.renderer.draw(this.handler.getText());
            }
        } catch (IndexOutOfBoundsException ignored) {
            try {
                this.renderer.draw(Collections.singletonList(" "));
            } catch (IndexOutOfBoundsException ignoredAgain) {
                // ModularUI2 can briefly report no measured line while resizing/focusing an empty field.
            }
        }
        if (getScrollArea().getScrollX() != null) {
            getScrollArea().getScrollX()
                .setScrollSize(Math.max(0, (int) (this.renderer.getLastActualWidth() + 0.5f)));
        }
        if (getScrollArea().getScrollY() != null) {
            getScrollArea().getScrollY()
                .setScrollSize(Math.max(0, (int) (this.renderer.getLastActualHeight() + 0.5f)));
        }
    }

    public String getFullText() {
        ensureTextLine();
        return String.join("\n", this.handler.getText());
    }

    public void setFullText(String text) {
        this.handler.getText()
            .clear();
        if (text != null && !text.isEmpty()) {
            for (String line : text.split("\n", -1)) {
                this.handler.getText()
                    .add(line);
            }
        } else {
            this.handler.getText()
                .add("");
        }
    }

    private void ensureTextLine() {
        if (this.handler.getText()
            .isEmpty()) {
            this.handler.getText()
                .add("");
        }
    }
}
