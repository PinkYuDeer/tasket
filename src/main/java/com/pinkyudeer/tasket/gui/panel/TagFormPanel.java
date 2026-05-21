package com.pinkyudeer.tasket.gui.panel;

import java.util.List;

import net.minecraft.nbt.NBTTagCompound;

import org.lwjgl.input.Keyboard;

import com.cleanroommc.modularui.api.GuiAxis;
import com.cleanroommc.modularui.api.drawable.IDrawable;
import com.cleanroommc.modularui.api.drawable.IKey;
import com.cleanroommc.modularui.api.widget.IWidget;
import com.cleanroommc.modularui.screen.ModularPanel;
import com.cleanroommc.modularui.value.IntValue;
import com.cleanroommc.modularui.widgets.SliderWidget;
import com.cleanroommc.modularui.widgets.layout.Flow;
import com.pinkyudeer.tasket.Tasket;
import com.pinkyudeer.tasket.client.TagClientActions;
import com.pinkyudeer.tasket.client.TaskClientStore;
import com.pinkyudeer.tasket.gui.GuiStyle;
import com.pinkyudeer.tasket.gui.drawable.ShaderDrawable;
import com.pinkyudeer.tasket.gui.widget.StyledButtonWidget;
import com.pinkyudeer.tasket.gui.widget.StyledTextField;
import com.pinkyudeer.tasket.network.handler.NetTagSync;
import com.pinkyudeer.tasket.task.entity.Tag;

import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;

@SideOnly(Side.CLIENT)
public class TagFormPanel extends ModularPanel {

    private final StyledTextField nameField;
    private final StyledTextField descField;
    private final StyledTextField redField;
    private final StyledTextField greenField;
    private final StyledTextField blueField;
    private final IntValue redValue = new IntValue(255);
    private final IntValue greenValue = new IntValue(255);
    private final IntValue blueValue = new IntValue(255);
    private final Runnable onSaved;
    private final String editingTagId;
    private final String editingTeamName;

    private Tag.TagScope selectedScope = Tag.TagScope.PUBLIC;
    private Flow scopeRow;
    private Flow teamRow;
    private StyledButtonWidget previewButton;
    private SliderWidget redSlider;
    private SliderWidget greenSlider;
    private SliderWidget blueSlider;
    private int selectedTeamIndex;
    private int lastPreviewColor;
    private String lastPreviewText = "";

    public TagFormPanel(Runnable onSaved) {
        this(null, onSaved);
    }

    public TagFormPanel(NBTTagCompound editingTag, Runnable onSaved) {
        super("tasket_tag_form");
        this.onSaved = onSaved;
        size(340, GuiStyle.fitPanelHeight(286, 18, 248));
        center();
        background(IDrawable.EMPTY);
        overlay(ShaderDrawable.panel(10f, 0x222233D8, GuiStyle.ACCENT));
        nameField = GuiStyle.textField();
        descField = GuiStyle.textField();
        redField = colorField(redValue);
        greenField = colorField(greenValue);
        blueField = colorField(blueValue);
        if (editingTag != null) {
            this.editingTagId = editingTag.getString("id");
            applyEditingTag(editingTag);
            this.editingTeamName = resolveTeamName(editingTag.getString("ownerTeamId"));
        } else {
            this.editingTagId = null;
            this.editingTeamName = null;
        }
        child(buildForm());
    }

    private boolean isEditing() {
        return editingTagId != null && !editingTagId.isEmpty();
    }

    private void applyEditingTag(NBTTagCompound tag) {
        nameField.setText(tag.getString("name"));
        descField.setText(tag.getString("description"));
        String scope = tag.getString("scope");
        if (scope != null && !scope.isEmpty()) {
            try {
                selectedScope = Tag.TagScope.valueOf(scope);
            } catch (IllegalArgumentException ignore) {
                selectedScope = Tag.TagScope.PUBLIC;
            }
        }
        String colorHex = tag.getString("colorCode");
        if (colorHex != null && colorHex.matches("^#[0-9a-fA-F]{6}$")) {
            int rgb = Integer.parseInt(colorHex.substring(1), 16);
            redValue.setIntValue((rgb >> 16) & 0xFF);
            greenValue.setIntValue((rgb >> 8) & 0xFF);
            blueValue.setIntValue(rgb & 0xFF);
        }
    }

    private String resolveTeamName(String teamId) {
        if (teamId == null || teamId.isEmpty()) return null;
        for (NBTTagCompound team : TaskClientStore.INSTANCE.getTeamList()) {
            if (teamId.equals(team.getString("id"))) return team.getString("name");
        }
        return teamId;
    }

    @Override
    public boolean disablePanelsBelow() {
        return true;
    }

    @Override
    public boolean closeOnOutOfBoundsClick() {
        return true;
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
    public void onUpdate() {
        super.onUpdate();
        updatePreview();
    }

    private Flow buildForm() {
        Flow form = Flow.column();
        form.widthRel(1f)
            .heightRel(1f)
            .padding(12)
            .name("tag_form/root");
        form.child(
            IKey.str(isEditing() ? "Edit Tag" : "Create Tag")
                .color(GuiStyle.ACCENT)
                .shadow(true)
                .asWidget()
                .widthRel(1f)
                .height(18));
        form.child(GuiStyle.label("Name", 7));
        nameField.widthRel(1f)
            .height(18)
            .name("tag_form/name");
        form.child(nameField);
        form.child(GuiStyle.label("Description", 5));
        descField.widthRel(1f)
            .height(18)
            .name("tag_form/desc");
        form.child(descField);
        form.child(buildColorEditor());
        scopeRow = buildScopeRow();
        form.child(scopeRow);
        teamRow = buildTeamRow();
        form.child(teamRow);
        form.child(buildActions());
        return form;
    }

    private Flow buildColorEditor() {
        Flow row = Flow.row();
        row.widthRel(1f)
            .height(62)
            .marginTop(7)
            .name("tag_form/color_editor");

        Flow preview = Flow.column();
        preview.widthRel(0.32f)
            .heightRel(1f)
            .paddingRight(6);
        preview.child(
            IKey.str("Preview")
                .color(0xAAAAAAff)
                .scale(0.8f)
                .asWidget()
                .widthRel(1f)
                .height(11));
        previewButton = GuiStyle.tagChip(previewText(), currentColor());
        previewButton.marginTop(3)
            .name("tag_form/preview");
        preview.child(previewButton);
        lastPreviewColor = currentColor();
        row.child(preview);

        Flow controls = Flow.column();
        controls.widthRel(0.64f)
            .heightRel(1f)
            .marginLeft(8)
            .name("tag_form/color_controls");
        controls.child(buildColorRow("R", redValue, redField, 0xD84C4CFF));
        controls.child(buildColorRow("G", greenValue, greenField, 0x55C774FF));
        controls.child(buildColorRow("B", blueValue, blueField, 0x5598E8FF));
        row.child(controls);
        return row;
    }

    private Flow buildColorRow(String label, IntValue value, StyledTextField field, int color) {
        Flow row = Flow.row();
        row.widthRel(1f)
            .height(18)
            .marginTop(1)
            .name("tag_form/color_" + label);
        row.child(
            IKey.str(label)
                .color(GuiStyle.rgbaToArgb(color))
                .shadow(true)
                .asWidget()
                .width(14)
                .heightRel(1f));
        SliderWidget slider = new SliderWidget().value(value)
            .bounds(0, 255)
            .setAxis(GuiAxis.X)
            .background(sliderTrack(value, color, false))
            .hoverBackground(sliderTrack(value, color, true))
            .sliderTexture(IDrawable.EMPTY)
            .sliderSize(6, 12)
            .widthRel(0.56f)
            .height(12)
            .name("tag_form/slider_" + label);
        if ("R".equals(label)) redSlider = slider;
        if ("G".equals(label)) greenSlider = slider;
        if ("B".equals(label)) blueSlider = slider;
        row.child(slider);
        field.widthRel(0.22f)
            .height(14)
            .marginLeft(4)
            .name("tag_form/input_" + label);
        row.child(field);
        return row;
    }

    private Flow buildScopeRow() {
        Flow row = Flow.row();
        row.widthRel(1f)
            .height(18)
            .marginTop(7)
            .name("tag_form/scope");
        row.child(
            IKey.str("Scope")
                .color(0xAAAAAAff)
                .scale(0.85f)
                .asWidget()
                .width(46)
                .heightRel(1f));
        addScope(row, Tag.TagScope.PUBLIC);
        addScope(row, Tag.TagScope.PRIVATE);
        if (!TaskClientStore.INSTANCE.getTeamList()
            .isEmpty()) addScope(row, Tag.TagScope.TEAM);
        return row;
    }

    private void addScope(Flow row, Tag.TagScope scope) {
        boolean active = selectedScope == scope;
        row.child(
            GuiStyle
                .button(
                    scope.name(),
                    active ? GuiStyle.TOGGLE_ACTIVE : GuiStyle.TOGGLE_INACTIVE,
                    GuiStyle.TOGGLE_HOVER,
                    GuiStyle.BUTTON_PRESSED,
                    active ? 0xFFFFFFFF : 0x999999ff,
                    0.78f)
                .width(scope == Tag.TagScope.PRIVATE ? 58 : 48)
                .height(14)
                .marginLeft(4)
                .onMousePressed(btn -> {
                    selectedScope = scope;
                    rebuildScopeAndTeam();
                    return true;
                }));
    }

    private Flow buildTeamRow() {
        Flow row = Flow.row();
        row.widthRel(1f)
            .height(18)
            .marginTop(4)
            .name("tag_form/team");
        if (selectedScope != Tag.TagScope.TEAM) {
            row.child(
                IKey.str("Team scope not selected.")
                    .color(0x666666ff)
                    .scale(0.82f)
                    .asWidget()
                    .widthRel(1f)
                    .heightRel(1f));
            return row;
        }
        List<NBTTagCompound> teams = TaskClientStore.INSTANCE.getTeamList();
        if (teams.isEmpty()) {
            row.child(
                IKey.str("No team available.")
                    .color(0xFF7777ff)
                    .scale(0.82f)
                    .asWidget()
                    .widthRel(1f)
                    .heightRel(1f));
            return row;
        }
        selectedTeamIndex = Math.max(0, Math.min(selectedTeamIndex, teams.size() - 1));
        NBTTagCompound team = teams.get(selectedTeamIndex);
        row.child(
            IKey.str("Team")
                .color(0xAAAAAAff)
                .scale(0.85f)
                .asWidget()
                .width(36)
                .heightRel(1f));
        row.child(
            GuiStyle.smallButton("<", GuiStyle.TOGGLE_INACTIVE, 0xCCCCCCff)
                .width(20)
                .height(14)
                .onMousePressed(btn -> {
                    selectedTeamIndex = (selectedTeamIndex + teams.size() - 1) % teams.size();
                    rebuildScopeAndTeam();
                    return true;
                }));
        row.child(
            IKey.str(shorten(team.getString("name"), 22))
                .color(0xDDDDDDff)
                .scale(0.85f)
                .asWidget()
                .widthRel(0.64f)
                .heightRel(1f)
                .marginLeft(4));
        row.child(
            GuiStyle.smallButton(">", GuiStyle.TOGGLE_INACTIVE, 0xCCCCCCff)
                .width(20)
                .height(14)
                .marginLeft(4)
                .onMousePressed(btn -> {
                    selectedTeamIndex = (selectedTeamIndex + 1) % teams.size();
                    rebuildScopeAndTeam();
                    return true;
                }));
        return row;
    }

    private void rebuildScopeAndTeam() {
        Flow form = (Flow) getChildren().get(0);
        replaceChild(form, scopeRow, buildScopeRow());
        replaceChild(form, teamRow, buildTeamRow());
        form.scheduleResize();
    }

    private void replaceChild(Flow form, IWidget oldChild, Flow newChild) {
        int idx = form.getChildren()
            .indexOf(oldChild);
        if (idx >= 0) {
            form.remove(idx);
            form.addChild(newChild, idx);
            if (oldChild == scopeRow) scopeRow = newChild;
            if (oldChild == teamRow) teamRow = newChild;
        }
    }

    private Flow buildActions() {
        Flow row = Flow.row();
        row.widthRel(1f)
            .height(24)
            .marginTop(8);
        row.child(
            GuiStyle.saveButton(isEditing() ? "Save" : "Add")
                .widthRel(0.48f)
                .height(22)
                .onMousePressed(btn -> {
                    saveTag();
                    return true;
                }));
        row.child(
            GuiStyle.dangerButton("Cancel")
                .widthRel(0.48f)
                .height(22)
                .marginLeft(8)
                .onMousePressed(btn -> {
                    closeIfOpen();
                    return true;
                }));
        return row;
    }

    private StyledTextField colorField(IntValue value) {
        StyledTextField field = GuiStyle.textField();
        field.value(value);
        field.setNumbers(0, 255);
        field.setMaxLength(3);
        return field;
    }

    private void updatePreview() {
        int color = currentColor();
        syncSlider(redSlider, redValue);
        syncSlider(greenSlider, greenValue);
        syncSlider(blueSlider, blueValue);
        String text = previewText();
        if (previewButton != null && (color != lastPreviewColor || !text.equals(lastPreviewText))) {
            previewButton.setBackgrounds(
                GuiStyle.tagChipDrawable(color),
                GuiStyle.tagChipDrawable(lighten(color)),
                GuiStyle.tagChipDrawable(darken(color)));
            previewButton.overlay(
                IKey.str(GuiStyle.shortTagLabel(text))
                    .color(GuiStyle.readableTextColor(color))
                    .shadow(false)
                    .scale(0.72f));
            lastPreviewColor = color;
            lastPreviewText = text;
        }
    }

    private void saveTag() {
        String name = nameField.getText();
        if (name == null || name.trim()
            .isEmpty()) return;
        String ownerTeamId = "";
        if (selectedScope == Tag.TagScope.TEAM) {
            List<NBTTagCompound> teams = TaskClientStore.INSTANCE.getTeamList();
            if (teams.isEmpty()) return;
            selectedTeamIndex = Math.max(0, Math.min(selectedTeamIndex, teams.size() - 1));
            ownerTeamId = teams.get(selectedTeamIndex)
                .getString("id");
        }
        try {
            if (isEditing()) {
                TagClientActions.updateTag(editingTagId, name.trim(), safe(descField.getText()).trim(), colorHex());
            } else {
                TagClientActions
                    .createTag(name.trim(), safe(descField.getText()).trim(), colorHex(), selectedScope, ownerTeamId);
            }
            NetTagSync.requestSync();
            closeIfOpen();
            if (onSaved != null) onSaved.run();
        } catch (Exception e) {
            Tasket.LOG.error("Failed to create tag from GUI", e);
        }
    }

    private String previewText() {
        String name = nameField.getText();
        if (name != null && !name.trim()
            .isEmpty()) return name.trim();
        return "Tag name";
    }

    private void syncSlider(SliderWidget slider, IntValue value) {
        if (slider != null && !slider.isDragging()) {
            slider.setValue(clamp(value.getIntValue()), false);
        }
    }

    private static IDrawable sliderTrack(IntValue value, int color, boolean hover) {
        IDrawable track = ShaderDrawable.roundedRect(3f, hover ? 0x34476ACC : 0x25334FAA);
        IDrawable handle = ShaderDrawable.roundedRect(4f, color);
        return (context, x, y, width, height, widgetTheme) -> {
            track.draw(context, x, y, width, height, widgetTheme);
            int handleW = 6;
            int handleH = 12;
            int usable = Math.max(1, width - handleW);
            int handleX = x + Math.round(usable * (clamp(value.getIntValue()) / 255f));
            int handleY = y + (height - handleH) / 2;
            handle.draw(context, handleX, handleY, handleW, handleH, widgetTheme);
        };
    }

    private int currentColor() {
        return GuiStyle.backgroundColor(redValue.getIntValue(), greenValue.getIntValue(), blueValue.getIntValue());
    }

    private String colorHex() {
        return String.format(
            "#%02X%02X%02X",
            clamp(redValue.getIntValue()),
            clamp(greenValue.getIntValue()),
            clamp(blueValue.getIntValue()));
    }

    private static int clamp(int value) {
        return Math.max(0, Math.min(255, value));
    }

    private static int lighten(int color) {
        return GuiStyle.lightenBackground(color);
    }

    private static int darken(int color) {
        return GuiStyle.darkenBackground(color);
    }

    private static String safe(String value) {
        return value == null ? "" : value;
    }

    private static String shorten(String value, int max) {
        if (value == null) return "";
        return value.length() > max ? value.substring(0, max - 2) + ".." : value;
    }
}
