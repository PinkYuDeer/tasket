package com.pinkyudeer.tasket.gui;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.ScaledResolution;

import com.cleanroommc.modularui.api.drawable.IDrawable;
import com.cleanroommc.modularui.api.drawable.IKey;
import com.cleanroommc.modularui.api.widget.IWidget;
import com.cleanroommc.modularui.screen.ModularPanel;
import com.cleanroommc.modularui.screen.viewport.GuiContext;
import com.cleanroommc.modularui.theme.WidgetTheme;
import com.cleanroommc.modularui.widgets.textfield.BaseTextFieldWidget;
import com.cleanroommc.modularui.widgets.textfield.TextFieldWidget;
import com.pinkyudeer.tasket.gui.drawable.ShaderDrawable;
import com.pinkyudeer.tasket.gui.widget.StyledButtonWidget;
import com.pinkyudeer.tasket.gui.widget.StyledMultilineTextField;
import com.pinkyudeer.tasket.gui.widget.StyledTextField;

import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;

@SideOnly(Side.CLIENT)
public final class GuiStyle {

    public static final int ACCENT = 0x99ccffff;
    public static final int PANEL_BG = 0x181820C0;
    public static final int PANEL_DARK = 0x141828B0;
    public static final int ITEM_BG = 0x20203040;
    public static final int ITEM_HOVER = 0x30406060;
    public static final int ITEM_PRESSED = 0x40507888;
    public static final int BUTTON_BG = 0x335588a0;
    public static final int BUTTON_HOVER = 0x4477aacc;
    public static final int BUTTON_PRESSED = 0x22507799;
    public static final int SAVE_BG = 0x336633c0;
    public static final int SAVE_HOVER = 0x448844d0;
    public static final int SAVE_PRESSED = 0x225522c0;
    public static final int DANGER_BG = 0x663333c0;
    public static final int DANGER_HOVER = 0x884444d0;
    public static final int DANGER_PRESSED = 0x552222c0;
    public static final int INPUT_BG = 0x15152070;
    public static final int INPUT_HOVER = 0x20243890;
    public static final int INPUT_FOCUS = 0x273650B0;
    public static final int TOGGLE_ACTIVE = 0x446688c0;
    public static final int TOGGLE_INACTIVE = 0x22334470;
    public static final int TOGGLE_HOVER = 0x334766a0;
    public static final int TAG_CHIP_WIDTH = 56;
    public static final int TAG_CHIP_HEIGHT = 12;
    public static final int TAG_CHIP_GAP = 4;
    public static final float TAG_CHIP_RADIUS = 3f;

    private GuiStyle() {}

    public static StyledButtonWidget button(String label, int bg, int hover, int pressed, int textColor, float scale) {
        return new StyledButtonWidget(
            ShaderDrawable.roundedRect(5f, bg),
            ShaderDrawable.roundedRect(5f, hover),
            ShaderDrawable.roundedRect(5f, pressed)).overlay(
                IKey.str(label)
                    .color(textColor)
                    .shadow(true)
                    .scale(scale));
    }

    public static StyledButtonWidget button(String label) {
        return button(label, BUTTON_BG, BUTTON_HOVER, BUTTON_PRESSED, 0xFFFFFFFF, 1f);
    }

    public static StyledButtonWidget smallButton(String label, int bg, int textColor) {
        return button(label, bg, BUTTON_HOVER, BUTTON_PRESSED, textColor, 0.85f);
    }

    public static StyledButtonWidget saveButton(String label) {
        return button(label, SAVE_BG, SAVE_HOVER, SAVE_PRESSED, 0xFFFFFFFF, 0.95f);
    }

    public static StyledButtonWidget dangerButton(String label) {
        return button(label, DANGER_BG, DANGER_HOVER, DANGER_PRESSED, 0xFFFF7777, 0.95f);
    }

    public static StyledButtonWidget tagChip(String label, int bg) {
        return new StyledButtonWidget(
            tagChipDrawable(bg),
            tagChipDrawable(lightenBackground(bg)),
            tagChipDrawable(darkenBackground(bg))).overlay(
                IKey.str(shortTagLabel(label))
                    .color(readableTextColor(bg))
                    .shadow(false)
                    .scale(0.72f))
                .width(TAG_CHIP_WIDTH)
                .height(TAG_CHIP_HEIGHT);
    }

    public static IDrawable tagChipDrawable(int bg) {
        return new InsetDrawable(
            ShaderDrawable.builder()
                .round(TAG_CHIP_RADIUS)
                .rectColor(bg)
                .rectEdgeSoftness(0.35f)
                .border(0.75f, 0.25f, 0.0f, ACCENT)
                .build(),
            1);
    }

    public static int rgbaToArgb(int rgba) {
        return ((rgba & 0xFF) << 24) | ((rgba >>> 8) & 0xFFFFFF);
    }

    public static String shortTagLabel(String label) {
        if (label == null) return "";
        return label.length() > 10 ? label.substring(0, 8) + ".." : label;
    }

    public static StyledTextField textField() {
        return new StyledTextField(
            ShaderDrawable.roundedRect(4f, INPUT_BG),
            ShaderDrawable.roundedRect(4f, INPUT_HOVER),
            ShaderDrawable.roundedRect(4f, INPUT_FOCUS));
    }

    public static StyledMultilineTextField multilineField() {
        return new StyledMultilineTextField(
            ShaderDrawable.roundedRect(4f, INPUT_BG),
            ShaderDrawable.roundedRect(4f, INPUT_HOVER),
            ShaderDrawable.roundedRect(4f, INPUT_FOCUS));
    }

    public static void applyTextFieldStyle(TextFieldWidget field) {
        field.background(ShaderDrawable.roundedRect(4f, INPUT_BG));
        field.hoverBackground(ShaderDrawable.roundedRect(4f, INPUT_HOVER));
        field.setTextColor(0xFFFFFFFF);
        field.setMarkedColor(0xFF2F72A8);
        field.hintColor(0xFF777777);
    }

    public static IWidget label(String text, int topMargin) {
        return IKey.str(text)
            .color(0xAAAAAAff)
            .scale(0.85f)
            .asWidget()
            .widthRel(1f)
            .height(10)
            .marginTop(topMargin);
    }

    public static int parseColor(String value, int fallback) {
        if (value == null || !value.matches("^#[0-9a-fA-F]{6}$")) return fallback;
        return rgbToBackground(Integer.parseInt(value.substring(1), 16));
    }

    public static String normalizeColor(String value) {
        if (value == null || !value.matches("^#[0-9a-fA-F]{6}$")) return "#FFFFFF";
        return value.toUpperCase();
    }

    public static int readableTextColor(int backgroundColor) {
        int r = (backgroundColor >> 24) & 0xFF;
        int g = (backgroundColor >> 16) & 0xFF;
        int b = (backgroundColor >> 8) & 0xFF;
        int brightness = (r * 299 + g * 587 + b * 114) / 1000;
        return brightness > 128 ? 0xFF111111 : 0xFFFFFFFF;
    }

    public static int rgbToBackground(int rgb) {
        return ((rgb & 0xFFFFFF) << 8) | 0xFF;
    }

    public static int backgroundColor(int red, int green, int blue) {
        return (clampColor(red) << 24) | (clampColor(green) << 16) | (clampColor(blue) << 8) | 0xFF;
    }

    public static int lightenBackground(int color) {
        return adjustBackground(color, 24);
    }

    public static int darkenBackground(int color) {
        return adjustBackground(color, -24);
    }

    public static int shiftBackground(int color, int redDelta, int greenDelta, int blueDelta) {
        int r = clampColor(((color >> 24) & 0xFF) + redDelta);
        int g = clampColor(((color >> 16) & 0xFF) + greenDelta);
        int b = clampColor(((color >> 8) & 0xFF) + blueDelta);
        return (r << 24) | (g << 16) | (b << 8) | (color & 0xFF);
    }

    private static int adjustBackground(int color, int delta) {
        return shiftBackground(color, delta, delta, delta);
    }

    private static int clampColor(int value) {
        return Math.max(0, Math.min(255, value));
    }

    public static int fitPanelHeight(int desired, int margin, int min) {
        Minecraft mc = Minecraft.getMinecraft();
        if (mc == null) return desired;
        ScaledResolution scaled = new ScaledResolution(mc, mc.displayWidth, mc.displayHeight);
        int available = Math.max(min, scaled.getScaledHeight() - margin);
        return Math.min(desired, available);
    }

    public static boolean shouldKeepTypingFocus(ModularPanel panel, int keyCode) {
        if (keyCode == org.lwjgl.input.Keyboard.KEY_ESCAPE) return false;
        return hasFocusedTextField(panel);
    }

    private static boolean hasFocusedTextField(IWidget widget) {
        if (widget instanceof BaseTextFieldWidget<?>field && field.isFocused()) return true;
        for (IWidget child : widget.getChildren()) {
            if (hasFocusedTextField(child)) return true;
        }
        return false;
    }

    private static final class InsetDrawable implements IDrawable {

        private final IDrawable delegate;
        private final int inset;

        private InsetDrawable(IDrawable delegate, int inset) {
            this.delegate = delegate;
            this.inset = inset;
        }

        @Override
        public void draw(GuiContext context, int x, int y, int width, int height, WidgetTheme widgetTheme) {
            int innerW = Math.max(1, width - inset * 2);
            int innerH = Math.max(1, height - inset * 2);
            delegate.draw(context, x + inset, y + inset, innerW, innerH, widgetTheme);
        }
    }
}
