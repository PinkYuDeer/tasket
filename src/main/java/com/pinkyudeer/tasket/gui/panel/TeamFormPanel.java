package com.pinkyudeer.tasket.gui.panel;

import com.cleanroommc.modularui.api.drawable.IDrawable;
import com.cleanroommc.modularui.api.drawable.IKey;
import com.cleanroommc.modularui.api.widget.IWidget;
import com.cleanroommc.modularui.screen.ModularPanel;
import com.cleanroommc.modularui.widgets.layout.Flow;
import com.pinkyudeer.tasket.Tasket;
import com.pinkyudeer.tasket.client.TeamClientActions;
import com.pinkyudeer.tasket.gui.GuiStyle;
import com.pinkyudeer.tasket.gui.drawable.ShaderDrawable;
import com.pinkyudeer.tasket.gui.widget.StyledTextField;
import com.pinkyudeer.tasket.task.team.TeamProviders;

import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;

@SideOnly(Side.CLIENT)
public class TeamFormPanel extends ModularPanel {

    private final StyledTextField nameField;
    private final StyledTextField descField;
    private final Runnable onSaved;
    private String selectedSource = "LOCAL";
    private Flow sourceRow;

    public TeamFormPanel(Runnable onSaved) {
        super("tasket_team_form");
        this.onSaved = onSaved;
        size(300, externalSourceAvailable() ? 188 : 160);
        center();
        background(IDrawable.EMPTY);
        overlay(ShaderDrawable.panel(10f, 0x222233D8, GuiStyle.ACCENT));
        nameField = GuiStyle.textField();
        descField = GuiStyle.textField();
        child(buildForm());
    }

    @Override
    public boolean disablePanelsBelow() {
        return true;
    }

    @Override
    public boolean onKeyPressed(char character, int keyCode) {
        boolean handled = super.onKeyPressed(character, keyCode);
        if (handled) return true;
        return GuiStyle.shouldKeepTypingFocus(this, keyCode);
    }

    private Flow buildForm() {
        Flow form = Flow.column();
        form.widthRel(1f)
            .heightRel(1f)
            .padding(12)
            .name("team_form/root");
        form.child(
            IKey.str("Create Team")
                .color(GuiStyle.ACCENT)
                .shadow(true)
                .asWidget()
                .widthRel(1f)
                .height(18));
        form.child(GuiStyle.label("Name", 8));
        nameField.widthRel(1f)
            .height(18)
            .name("team_form/name");
        form.child(nameField);
        form.child(GuiStyle.label("Description", 6));
        descField.widthRel(1f)
            .height(18)
            .name("team_form/desc");
        form.child(descField);
        if (externalSourceAvailable()) {
            sourceRow = buildSourceRow();
            form.child(sourceRow);
        }
        form.child(buildActions());
        return form;
    }

    private Flow buildSourceRow() {
        Flow row = Flow.row();
        row.widthRel(1f)
            .height(20)
            .marginTop(8)
            .name("team_form/source");
        row.child(
            IKey.str("Source")
                .color(0xAAAAAAff)
                .scale(0.85f)
                .asWidget()
                .width(48)
                .heightRel(1f));
        row.child(sourceButton("Local", "LOCAL", 54));
        if (isBqAvailable()) row.child(sourceButton("BQ", "BETTER_QUESTING", 42));
        if (isGtnhAvailable()) row.child(sourceButton("GTNH", "GTNH_LIB", 50));
        return row;
    }

    private IWidget sourceButton(String label, String source, int width) {
        boolean active = selectedSource.equals(source);
        return GuiStyle
            .button(
                label,
                active ? GuiStyle.TOGGLE_ACTIVE : GuiStyle.TOGGLE_INACTIVE,
                GuiStyle.TOGGLE_HOVER,
                GuiStyle.BUTTON_PRESSED,
                active ? 0xFFFFFFFF : 0x999999ff,
                0.8f)
            .width(width)
            .height(15)
            .marginLeft(4)
            .onMousePressed(btn -> {
                selectedSource = source;
                rebuildSourceRow();
                return true;
            });
    }

    private void rebuildSourceRow() {
        if (sourceRow == null || getChildren().isEmpty()) return;
        Flow form = (Flow) getChildren().get(0);
        int idx = form.getChildren()
            .indexOf(sourceRow);
        if (idx < 0) return;
        form.remove(idx);
        sourceRow = buildSourceRow();
        form.addChild(sourceRow, idx);
        form.scheduleResize();
    }

    private Flow buildActions() {
        Flow row = Flow.row();
        row.widthRel(1f)
            .height(24)
            .marginTop(12)
            .name("team_form/actions");
        row.child(
            GuiStyle.saveButton("Save")
                .widthRel(0.48f)
                .height(22)
                .onMousePressed(btn -> {
                    saveTeam();
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

    private void saveTeam() {
        String name = nameField.getText();
        if (name == null || name.trim()
            .isEmpty()) return;
        try {
            TeamClientActions.createTeam(name.trim(), safe(descField.getText()).trim(), selectedSource);
            closeIfOpen();
            if (onSaved != null) onSaved.run();
        } catch (Exception e) {
            Tasket.LOG.error("Failed to create team from GUI", e);
        }
    }

    private static String safe(String value) {
        return value == null ? "" : value;
    }

    private static boolean externalSourceAvailable() {
        return isBqAvailable() || isGtnhAvailable();
    }

    private static boolean isBqAvailable() {
        try {
            return TeamProviders.betterQuesting()
                .isAvailable();
        } catch (Throwable ignored) {
            return false;
        }
    }

    private static boolean isGtnhAvailable() {
        try {
            return TeamProviders.gtnhLib()
                .isAvailable();
        } catch (Throwable ignored) {
            return false;
        }
    }
}
