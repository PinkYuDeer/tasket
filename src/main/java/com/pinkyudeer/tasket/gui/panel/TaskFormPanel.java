package com.pinkyudeer.tasket.gui.panel;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import net.minecraft.client.Minecraft;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;

import org.lwjgl.input.Keyboard;

import com.cleanroommc.modularui.api.IPanelHandler;
import com.cleanroommc.modularui.api.drawable.IKey;
import com.cleanroommc.modularui.api.widget.IWidget;
import com.cleanroommc.modularui.screen.ModularPanel;
import com.cleanroommc.modularui.widgets.ListWidget;
import com.cleanroommc.modularui.widgets.layout.Flow;
import com.pinkyudeer.tasket.Tasket;
import com.pinkyudeer.tasket.client.TaskClientActions;
import com.pinkyudeer.tasket.client.TaskClientStore;
import com.pinkyudeer.tasket.gui.GuiStyle;
import com.pinkyudeer.tasket.gui.drawable.FrostedGlassDrawable;
import com.pinkyudeer.tasket.gui.drawable.ShaderDrawable;
import com.pinkyudeer.tasket.gui.widget.StyledButtonWidget;
import com.pinkyudeer.tasket.gui.widget.StyledMultilineTextField;
import com.pinkyudeer.tasket.gui.widget.StyledTextField;
import com.pinkyudeer.tasket.task.entity.Tag;
import com.pinkyudeer.tasket.task.entity.Task;

import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;

@SideOnly(Side.CLIENT)
public class TaskFormPanel extends AnimatedPanel {

    private static final int FORM_BG = 0x222233D8;
    private static final int LABEL_WIDTH = 70;
    private static final int PICKER_BG = 0x1E1E38F0;
    private static int nextId;

    private final StyledTextField titleField;
    private final StyledMultilineTextField descField;
    private final Runnable onSaved;
    private final String parentTaskId;
    private final String uid = String.valueOf(nextId++);
    private final Set<String> selectedTagIds = new HashSet<>();

    private Flow form;
    private int step;
    private Task.Importance selectedImportance = Task.Importance.MEDIUM;
    private Task.Urgency selectedUrgency = Task.Urgency.MEDIUM;
    private Task.PrivacyLevel selectedVisibility = Task.PrivacyLevel.PRIVATE;
    private final Set<UUID> selectedAssigneeIds = new HashSet<>();
    private long lastTagRevision = -1;

    private Flow importanceRow;
    private Flow urgencyRow;
    private Flow visibilityRow;
    private Flow assigneeRow;
    private ListWidget<IWidget, ?> tagSummaryList;
    private ListWidget<IWidget, ?> selectedTagList;
    private ListWidget<IWidget, ?> availableTagList;
    private ListWidget<IWidget, ?> assignList;
    private IPanelHandler tagPickerHandler;
    private IPanelHandler tagFormHandler;
    private IPanelHandler assigneePickerHandler;
    private IWidget pendingAnchor;

    public TaskFormPanel(Runnable onSaved) {
        this(onSaved, null);
    }

    public TaskFormPanel(Runnable onSaved, String parentTaskId) {
        this(onSaved, parentTaskId, parentTaskId != null ? "tasket_subtask_form" : "tasket_task_form");
    }

    public TaskFormPanel(Runnable onSaved, String parentTaskId, String panelName) {
        super(panelName);
        this.onSaved = onSaved;
        this.parentTaskId = parentTaskId;
        Task parent = parentTaskId == null ? null : TaskClientStore.INSTANCE.getTask(parentTaskId);
        if (parent != null && parent.getVisibility() != null) selectedVisibility = parent.getVisibility();

        size(340, GuiStyle.fitPanelHeight(300, 18, 246));
        center();
        background(FrostedGlassDrawable.create(10f));
        disableHoverBackground();
        overlay(ShaderDrawable.panel(10f, FORM_BG, GuiStyle.ACCENT));

        titleField = GuiStyle.textField();
        descField = GuiStyle.multilineField();
        descField.hintText("Enter description... (Enter for new line)");

        form = Flow.column();
        form.widthRel(1f)
            .heightRel(1f)
            .padding(12)
            .name("form/root");
        child(form);
        rebuildForm();
    }

    private String pn(String base) {
        return "task_form_" + base + "_" + uid;
    }

    @Override
    public boolean disablePanelsBelow() {
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
        long tagRevision = TaskClientStore.INSTANCE.getTagRevision();
        if (tagRevision != lastTagRevision) {
            lastTagRevision = tagRevision;
            refreshTags();
        }
    }

    private void rebuildForm() {
        form.removeAll();
        if (step == 0) buildStepOne();
        else buildStepTwo();
        form.scheduleResize();
    }

    private void buildStepOne() {
        form.child(header("Create Task  1/2"));
        form.child(GuiStyle.label("Title", 7));
        titleField.widthRel(1f)
            .height(18)
            .name("form/title_field");
        form.child(titleField);
        form.child(buildTagsRow());
        form.child(GuiStyle.label("Description", 6));
        descField.widthRel(1f)
            .height(122)
            .name("form/desc_field");
        form.child(descField);
        form.child(buildStepOneActions());
    }

    private void buildStepTwo() {
        form.child(header("Create Task  2/2"));
        visibilityRow = buildVisibilityRow();
        form.child(visibilityRow);
        assigneeRow = buildAssigneeRow();
        form.child(assigneeRow);
        importanceRow = buildImportanceRow();
        form.child(importanceRow);
        urgencyRow = buildUrgencyRow();
        form.child(urgencyRow);
        form.child(buildStepTwoActions());
    }

    private IWidget header(String text) {
        return IKey.str(parentTaskId != null ? text.replace("Task", "Subtask") : text)
            .color(GuiStyle.ACCENT)
            .shadow(true)
            .asWidget()
            .widthRel(1f)
            .height(18)
            .name("form/header_title");
    }

    private Flow buildTagsRow() {
        Flow row = Flow.row();
        row.widthRel(1f)
            .height(20)
            .marginTop(6)
            .name("form/tags_row");
        row.child(
            IKey.str("Tags")
                .color(0xAAAAAAff)
                .asWidget()
                .widthRel(0.18f)
                .heightRel(1f));
        tagSummaryList = new ListWidget<>();
        tagSummaryList.widthRel(0.66f)
            .height(16)
            .name("form/tags_summary");
        populateTagSummary();
        row.child(tagSummaryList);
        StyledButtonWidget editButton = GuiStyle.smallButton("Edit", GuiStyle.BUTTON_BG, 0xFFFFFFFF);
        editButton.widthRel(0.14f)
            .height(15)
            .marginLeft(3)
            .name("form/tags_edit")
            .onMousePressed(mouseButton -> {
                openTagPicker(tagSummaryList);
                return true;
            });
        row.child(editButton);
        return row;
    }

    private Flow buildVisibilityRow() {
        Flow row = Flow.row();
        row.widthRel(1f)
            .height(24)
            .marginTop(10)
            .name("form/visibility_row");
        row.child(
            IKey.str("Scope")
                .color(0xAAAAAAff)
                .asWidget()
                .width(LABEL_WIDTH)
                .heightRel(1f));
        addVisibilityButton(row, Task.PrivacyLevel.PRIVATE);
        addVisibilityButton(row, Task.PrivacyLevel.PUBLIC);
        if (canUseTeamScope()) addVisibilityButton(row, Task.PrivacyLevel.TEAM);
        return row;
    }

    private void addVisibilityButton(Flow row, Task.PrivacyLevel visibility) {
        boolean active = selectedVisibility == visibility;
        row.child(
            toggleButton(
                visibility.name(),
                active ? GuiStyle.TOGGLE_ACTIVE : GuiStyle.TOGGLE_INACTIVE,
                active ? 0xFFFFFFff : 0x999999ff).width(visibility == Task.PrivacyLevel.PRIVATE ? 58 : 48)
                    .height(16)
                    .marginLeft(4)
                    .name("form/scope_" + visibility.name())
                    .onMousePressed(btn -> {
                        selectedVisibility = visibility;
                        rebuildRow(visibilityRow, buildVisibilityRow());
                        return true;
                    }));
    }

    private Flow buildAssigneeRow() {
        Flow row = Flow.row();
        row.widthRel(1f)
            .height(24)
            .marginTop(5)
            .name("form/assignee_row");
        row.child(
            IKey.str("Claim")
                .color(0xAAAAAAff)
                .asWidget()
                .width(LABEL_WIDTH)
                .heightRel(1f));
        StyledButtonWidget button = GuiStyle.button(
            selectedAssigneeIds.isEmpty() ? "UnClaimed" : formatAssignees(selectedAssigneeIds),
            GuiStyle.TOGGLE_INACTIVE,
            GuiStyle.TOGGLE_HOVER,
            GuiStyle.BUTTON_PRESSED,
            selectedAssigneeIds.isEmpty() ? 0xCCCCCCff : 0x66CCFFFF,
            0.84f);
        button.widthRel(0.58f)
            .height(16)
            .marginLeft(4)
            .name("form/assignee")
            .onMousePressed(mouseButton -> {
                openAssigneePicker(button);
                return true;
            });
        row.child(button);
        return row;
    }

    private Flow buildImportanceRow() {
        Flow row = Flow.row();
        row.widthRel(1f)
            .height(24)
            .marginTop(5)
            .name("form/importance_row");
        row.child(
            IKey.str("Importance")
                .color(0xAAAAAAff)
                .asWidget()
                .width(LABEL_WIDTH)
                .heightRel(1f));

        Task.Importance[] values = { Task.Importance.LOW, Task.Importance.MEDIUM, Task.Importance.HIGH,
            Task.Importance.CRITICAL };
        for (Task.Importance imp : values) {
            boolean active = imp == selectedImportance;
            boolean crit = imp == Task.Importance.CRITICAL;
            int activeBg = crit ? 0xCC3333cc : GuiStyle.TOGGLE_ACTIVE;
            row.child(
                toggleButton(
                    crit ? "CRIT"
                        : imp.name()
                            .substring(0, 3),
                    active ? activeBg : GuiStyle.TOGGLE_INACTIVE,
                    active ? 0xFFFFFFff : (crit ? 0xFF6666ff : 0x999999ff)).width(crit ? 40 : 32)
                        .height(16)
                        .marginLeft(4)
                        .name("form/imp_" + imp.name())
                        .onMousePressed(btn -> {
                            selectedImportance = imp;
                            rebuildRow(importanceRow, buildImportanceRow());
                            return true;
                        }));
        }
        return row;
    }

    private Flow buildUrgencyRow() {
        Flow row = Flow.row();
        row.widthRel(1f)
            .height(24)
            .marginTop(3)
            .name("form/urgency_row");
        row.child(
            IKey.str("Urgency")
                .color(0xAAAAAAff)
                .asWidget()
                .width(LABEL_WIDTH)
                .heightRel(1f));

        Task.Urgency[] values = { Task.Urgency.LOW, Task.Urgency.MEDIUM, Task.Urgency.HIGH, Task.Urgency.CRITICAL };
        for (Task.Urgency urg : values) {
            boolean active = urg == selectedUrgency;
            boolean crit = urg == Task.Urgency.CRITICAL;
            int activeBg = crit ? 0xCC3333cc : GuiStyle.TOGGLE_ACTIVE;
            row.child(
                toggleButton(
                    crit ? "CRIT"
                        : urg.name()
                            .substring(0, 3),
                    active ? activeBg : GuiStyle.TOGGLE_INACTIVE,
                    active ? 0xFFFFFFff : (crit ? 0xFF6666ff : 0x999999ff)).width(crit ? 40 : 32)
                        .height(16)
                        .marginLeft(4)
                        .name("form/urg_" + urg.name())
                        .onMousePressed(btn -> {
                            selectedUrgency = urg;
                            rebuildRow(urgencyRow, buildUrgencyRow());
                            return true;
                        }));
        }
        return row;
    }

    private StyledButtonWidget toggleButton(String label, int bg, int textColor) {
        return GuiStyle.button(label, bg, GuiStyle.TOGGLE_HOVER, GuiStyle.BUTTON_PRESSED, textColor, 0.78f);
    }

    private void rebuildRow(Flow oldRow, Flow newRow) {
        int idx = form.getChildren()
            .indexOf(oldRow);
        if (idx < 0) return;
        form.remove(idx);
        form.addChild(newRow, idx);
        if (oldRow == visibilityRow) visibilityRow = newRow;
        if (oldRow == assigneeRow) assigneeRow = newRow;
        if (oldRow == importanceRow) importanceRow = newRow;
        if (oldRow == urgencyRow) urgencyRow = newRow;
        form.scheduleResize();
    }

    private void openTagPicker(IWidget anchor) {
        pendingAnchor = anchor;
        if (tagPickerHandler == null) {
            tagPickerHandler = IPanelHandler.simple(this, (p, pl) -> buildTagPicker(pendingAnchor), true);
        } else {
            if (tagPickerHandler.isPanelOpen()) return;
            tagPickerHandler.deleteCachedPanel();
        }
        tagPickerHandler.openPanel();
    }

    private ModularPanel buildTagPicker(IWidget anchor) {
        ModularPanel panel = new InputSafePanel(pn("form_tags"));
        panel.size(250, 190);
        placeBelow(panel, anchor);
        panel.background(FrostedGlassDrawable.create(6f));
        panel.disableHoverBackground();
        panel.overlay(ShaderDrawable.panel(6f, PICKER_BG, GuiStyle.ACCENT));

        Flow root = Flow.column();
        root.widthRel(1f)
            .heightRel(1f)
            .padding(6)
            .name("form/tag_picker/root");
        Flow header = Flow.row();
        header.widthRel(1f)
            .height(16);
        header.child(
            IKey.str("Tags")
                .color(GuiStyle.ACCENT)
                .scale(0.9f)
                .asWidget()
                .widthRel(0.66f)
                .heightRel(1f));
        header.child(
            GuiStyle.smallButton("+ New", GuiStyle.BUTTON_BG, 0xFFFFFFFF)
                .widthRel(0.3f)
                .height(14)
                .onMousePressed(btn -> {
                    openTagForm(panel);
                    return true;
                }));
        root.child(header);

        Flow lists = Flow.row();
        lists.widthRel(1f)
            .height(112)
            .marginTop(3);
        selectedTagList = new ListWidget<>();
        selectedTagList.widthRel(0.5f)
            .heightRel(1f)
            .paddingRight(3);
        availableTagList = new ListWidget<>();
        availableTagList.widthRel(0.5f)
            .heightRel(1f)
            .paddingLeft(3);
        populateSelectedTags();
        populateAvailableTags();
        lists.child(selectedTagList);
        lists.child(availableTagList);
        root.child(lists);
        root.child(buildTagPickerActions(panel));
        panel.child(root);
        return panel;
    }

    private Flow buildTagPickerActions(ModularPanel panel) {
        Flow row = Flow.row();
        row.widthRel(1f)
            .height(20)
            .marginTop(5)
            .name("form/tag_picker/actions");
        row.child(
            GuiStyle.dangerButton("Cancel")
                .widthRel(0.48f)
                .height(18)
                .onMousePressed(btn -> {
                    panel.closeIfOpen();
                    return true;
                }));
        row.child(
            GuiStyle.saveButton("Save")
                .widthRel(0.48f)
                .height(18)
                .marginLeft(8)
                .onMousePressed(btn -> {
                    refreshTags();
                    panel.closeIfOpen();
                    return true;
                }));
        return row;
    }

    private void openTagForm(ModularPanel parent) {
        if (tagFormHandler == null) {
            tagFormHandler = IPanelHandler
                .simple(this, (p, pl) -> new TagFormPanel(this::refreshTags, pn("tag_form")), true);
        } else {
            if (tagFormHandler.isPanelOpen()) return;
            tagFormHandler.deleteCachedPanel();
        }
        tagFormHandler.openPanel();
    }

    private void populateSelectedTags() {
        if (selectedTagList == null) return;
        if (selectedTagIds.isEmpty()) {
            selectedTagList.child(emptyText("None"));
            return;
        }
        for (String tagId : selectedTagIds) {
            NBTTagCompound tag = TaskClientStore.INSTANCE.getTag(tagId);
            if (tag != null) selectedTagList.child(tagButton("x ", tag, () -> selectedTagIds.remove(tagId)));
        }
    }

    private void populateAvailableTags() {
        if (availableTagList == null) return;
        int added = 0;
        for (NBTTagCompound tag : TaskClientStore.INSTANCE.getTagList()) {
            String id = tag.getString("id");
            if (id.isEmpty() || selectedTagIds.contains(id)) continue;
            if (!isTagAllowedForSelectedScope(tag)) continue;
            availableTagList.child(tagButton("+ ", tag, () -> selectedTagIds.add(id)));
            added++;
        }
        if (added == 0) availableTagList.child(emptyText("No available"));
    }

    private StyledButtonWidget tagButton(String prefix, NBTTagCompound tag, Runnable action) {
        int bg = GuiStyle.parseColor(tag.getString("colorCode"), GuiStyle.BUTTON_BG);
        return GuiStyle.tagChip(prefix + tag.getString("name"), bg)
            .marginTop(1)
            .onMousePressed(btn -> {
                action.run();
                refreshTags();
                return true;
            });
    }

    private void openAssigneePicker(IWidget anchor) {
        pendingAnchor = anchor;
        if (assigneePickerHandler == null) {
            assigneePickerHandler = IPanelHandler.simple(this, (p, pl) -> buildAssigneePanel(pendingAnchor), true);
        } else {
            if (assigneePickerHandler.isPanelOpen()) return;
            assigneePickerHandler.deleteCachedPanel();
        }
        assigneePickerHandler.openPanel();
    }

    private ModularPanel buildAssigneePanel(IWidget anchor) {
        ModularPanel panel = new InputSafePanel(pn("form_assignee"));
        panel.size(
            Math.max(
                150,
                anchor.getArea()
                    .w()),
            142);
        placeBelow(panel, anchor);
        panel.background(FrostedGlassDrawable.create(6f));
        panel.disableHoverBackground();
        panel.overlay(ShaderDrawable.panel(6f, PICKER_BG, GuiStyle.ACCENT));

        Flow root = Flow.column();
        root.widthRel(1f)
            .heightRel(1f)
            .padding(6);
        root.child(
            GuiStyle.smallButton("UnClaimed", GuiStyle.TOGGLE_INACTIVE, 0xCCCCCCff)
                .widthRel(0.95f)
                .height(14)
                .onMousePressed(btn -> {
                    selectedAssigneeIds.clear();
                    rebuildRow(assigneeRow, buildAssigneeRow());
                    refreshAssignees();
                    return true;
                }));
        root.child(
            GuiStyle.smallButton("Done", GuiStyle.BUTTON_BG, 0xFFFFFFFF)
                .widthRel(0.95f)
                .height(14)
                .marginTop(2)
                .onMousePressed(btn -> {
                    panel.closeIfOpen();
                    return true;
                }));
        assignList = new ListWidget<>();
        assignList.widthRel(1f)
            .height(110)
            .marginTop(3);
        populateAssigneeList(panel);
        root.child(assignList);
        panel.child(root);
        return panel;
    }

    private void populateAssigneeList(ModularPanel panel) {
        Task parent = parentTaskId == null ? null : TaskClientStore.INSTANCE.getTask(parentTaskId);
        UUID teamId = parent == null ? null : parent.getTeamId();
        if (teamId == null) {
            UUID self = currentPlayerId();
            if (self == null) {
                assignList.child(emptyText("No player"));
                return;
            }
            assignList.child(assigneeButton("Me  " + currentPlayerName(), self, panel));
            return;
        }
        NBTTagCompound team = TaskClientStore.INSTANCE.getTeam(teamId.toString());
        if (team == null) {
            assignList.child(emptyText("Team not synced"));
            return;
        }
        NBTTagList members = team.getTagList("members", 10);
        int added = 0;
        for (int i = 0; i < members.tagCount(); i++) {
            NBTTagCompound member = members.getCompoundTagAt(i);
            if (!"ACTIVE".equals(member.getString("status"))) continue;
            UUID playerId = UUID.fromString(member.getString("playerId"));
            assignList.child(assigneeButton(memberDisplayName(member), playerId, panel));
            added++;
        }
        if (added == 0) assignList.child(emptyText("No active members"));
    }

    private StyledButtonWidget assigneeButton(String label, UUID playerId, ModularPanel panel) {
        boolean active = playerId != null && selectedAssigneeIds.contains(playerId);
        return GuiStyle
            .button(
                label,
                active ? GuiStyle.TOGGLE_ACTIVE : GuiStyle.TOGGLE_INACTIVE,
                GuiStyle.TOGGLE_HOVER,
                GuiStyle.BUTTON_PRESSED,
                active ? 0xFFFFFFFF : 0xCCCCCCff,
                0.78f)
            .widthRel(0.95f)
            .height(13)
            .marginTop(1)
            .onMousePressed(btn -> {
                if (selectedAssigneeIds.contains(playerId)) selectedAssigneeIds.remove(playerId);
                else selectedAssigneeIds.add(playerId);
                rebuildRow(assigneeRow, buildAssigneeRow());
                refreshAssignees();
                return true;
            });
    }

    private void refreshAssignees() {
        if (assignList == null) return;
        assignList.removeAll();
        populateAssigneeList(null);
        assignList.scheduleResize();
    }

    private void refreshTags() {
        populateTagSummary();
        if (selectedTagList != null) {
            selectedTagList.removeAll();
            populateSelectedTags();
            selectedTagList.scheduleResize();
        }
        if (availableTagList != null) {
            availableTagList.removeAll();
            populateAvailableTags();
            availableTagList.scheduleResize();
        }
    }

    private void populateTagSummary() {
        if (tagSummaryList == null) return;
        tagSummaryList.removeAll();
        List<NBTTagCompound> tags = new ArrayList<>();
        for (String tagId : selectedTagIds) {
            NBTTagCompound tag = TaskClientStore.INSTANCE.getTag(tagId);
            if (tag != null) tags.add(tag);
        }
        if (tags.isEmpty()) {
            tagSummaryList.child(
                IKey.str("None")
                    .color(0x666666ff)
                    .scale(0.8f)
                    .asWidget()
                    .widthRel(1f)
                    .height(14));
        } else {
            Flow row = Flow.row();
            row.widthRel(1f)
                .height(GuiStyle.TAG_CHIP_HEIGHT);
            int visible = Math.min(2, tags.size());
            for (int i = 0; i < visible; i++) {
                NBTTagCompound tag = tags.get(i);
                int bg = GuiStyle.parseColor(tag.getString("colorCode"), GuiStyle.BUTTON_BG);
                StyledButtonWidget chip = GuiStyle.tagChip(tag.getString("name"), bg);
                if (i > 0) chip.marginLeft(GuiStyle.TAG_CHIP_GAP);
                row.child(chip);
            }
            if (tags.size() > visible) {
                row.child(
                    IKey.str("+" + (tags.size() - visible))
                        .color(0xAAAAAAff)
                        .shadow(true)
                        .asWidget()
                        .width(24)
                        .heightRel(1f)
                        .marginLeft(GuiStyle.TAG_CHIP_GAP));
            }
            tagSummaryList.child(row);
        }
        tagSummaryList.scheduleResize();
    }

    private IWidget emptyText(String text) {
        return IKey.str(text)
            .color(0x666666ff)
            .scale(0.82f)
            .asWidget()
            .widthRel(1f)
            .height(14);
    }

    private Flow buildStepOneActions() {
        Flow actions = Flow.row();
        actions.widthRel(1f)
            .height(24)
            .marginTop(8)
            .name("form/actions_step1");
        actions.child(
            GuiStyle.dangerButton("Cancel")
                .widthRel(0.48f)
                .height(22)
                .onMousePressed(btn -> {
                    closeIfOpen();
                    return true;
                }));
        actions.child(
            GuiStyle.saveButton("Next")
                .widthRel(0.48f)
                .height(22)
                .marginLeft(8)
                .onMousePressed(btn -> {
                    if (!canAdvance()) return true;
                    step = 1;
                    rebuildForm();
                    return true;
                }));
        return actions;
    }

    private Flow buildStepTwoActions() {
        Flow actions = Flow.row();
        actions.widthRel(1f)
            .height(24)
            .marginTop(12)
            .name("form/actions_step2");
        actions.child(
            GuiStyle.smallButton("Back", GuiStyle.TOGGLE_INACTIVE, 0xCCCCCCff)
                .widthRel(0.48f)
                .height(22)
                .onMousePressed(btn -> {
                    step = 0;
                    rebuildForm();
                    return true;
                }));
        actions.child(
            GuiStyle.saveButton("Save")
                .widthRel(0.48f)
                .height(22)
                .marginLeft(8)
                .onMousePressed(btn -> {
                    saveTask();
                    return true;
                }));
        return actions;
    }

    private boolean canAdvance() {
        String title = titleField.getText();
        return title != null && !title.trim()
            .isEmpty();
    }

    private void saveTask() {
        String title = titleField.getText();
        String desc = descField.getFullText();

        if (title == null || title.trim()
            .isEmpty()) return;
        if (desc == null) desc = "";

        try {
            TaskClientActions.createTask(
                title.trim(),
                desc.trim(),
                parentTaskId,
                selectedImportance,
                selectedUrgency,
                selectedVisibility,
                allowedSelectedTagIds(),
                "",
                "",
                "#FFFFFF",
                Tag.TagScope.PUBLIC,
                selectedAssigneeIds());
            closeIfOpen();
            if (onSaved != null) onSaved.run();
        } catch (Exception e) {
            Tasket.LOG.error("Failed to save task from GUI", e);
        }
    }

    private void placeBelow(ModularPanel panel, IWidget anchor) {
        if (anchor == null) {
            panel.center();
            return;
        }
        panel.relative(anchor)
            .left(0)
            .top(
                anchor.getArea()
                    .h() + 2);
    }

    private boolean canUseTeamScope() {
        if (parentTaskId == null) return false;
        Task parent = TaskClientStore.INSTANCE.getTask(parentTaskId);
        return parent != null && parent.getTeamId() != null;
    }

    private ArrayList<String> allowedSelectedTagIds() {
        ArrayList<String> result = new ArrayList<>();
        for (String tagId : selectedTagIds) {
            NBTTagCompound tag = TaskClientStore.INSTANCE.getTag(tagId);
            if (tag != null && isTagAllowedForSelectedScope(tag)) result.add(tagId);
        }
        return result;
    }

    private boolean isTagAllowedForSelectedScope(NBTTagCompound tag) {
        String scope = tag.getString("scope");
        if ("SYSTEM".equals(scope) || "PUBLIC".equals(scope)) return true;
        if (selectedVisibility == Task.PrivacyLevel.PRIVATE) return "PRIVATE".equals(scope);
        if (selectedVisibility == Task.PrivacyLevel.TEAM && "TEAM".equals(scope)) {
            Task parent = parentTaskId == null ? null : TaskClientStore.INSTANCE.getTask(parentTaskId);
            return parent != null && parent.getTeamId() != null
                && parent.getTeamId()
                    .toString()
                    .equals(tag.getString("ownerTeamId"));
        }
        return false;
    }

    private UUID currentPlayerId() {
        if (Minecraft.getMinecraft() == null || Minecraft.getMinecraft().thePlayer == null) return null;
        return Minecraft.getMinecraft().thePlayer.getUniqueID();
    }

    private List<String> selectedAssigneeIds() {
        List<String> result = new ArrayList<>();
        for (UUID id : selectedAssigneeIds) {
            result.add(id.toString());
        }
        return result;
    }

    private String formatAssignees(Set<UUID> assigneeIds) {
        if (assigneeIds == null || assigneeIds.isEmpty()) return "UnClaimed";
        UUID first = assigneeIds.iterator()
            .next();
        String firstName = formatAssignee(first);
        return assigneeIds.size() == 1 ? firstName : firstName + " +" + (assigneeIds.size() - 1);
    }

    private String formatAssignee(UUID assigneeId) {
        UUID self = currentPlayerId();
        if (assigneeId != null && assigneeId.equals(self)) return currentPlayerName();
        NBTTagCompound member = findSelectedTeamMember(assigneeId);
        return member == null ? "Unknown" : shorten(memberDisplayName(member), 14);
    }

    private NBTTagCompound findSelectedTeamMember(UUID playerId) {
        Task parent = parentTaskId == null ? null : TaskClientStore.INSTANCE.getTask(parentTaskId);
        UUID teamId = parent == null ? null : parent.getTeamId();
        if (playerId == null || teamId == null) return null;
        NBTTagCompound team = TaskClientStore.INSTANCE.getTeam(teamId.toString());
        if (team == null) return null;
        NBTTagList members = team.getTagList("members", 10);
        for (int i = 0; i < members.tagCount(); i++) {
            NBTTagCompound member = members.getCompoundTagAt(i);
            if (playerId.toString()
                .equals(member.getString("playerId"))) return member;
        }
        return null;
    }

    private String memberDisplayName(NBTTagCompound member) {
        String displayName = member.getString("displayName");
        if (!displayName.isEmpty()) return displayName;
        String playerName = member.getString("playerName");
        if (!playerName.isEmpty()) return playerName;
        return "Unknown";
    }

    private String currentPlayerName() {
        if (Minecraft.getMinecraft() == null || Minecraft.getMinecraft().thePlayer == null) return "Me";
        return Minecraft.getMinecraft().thePlayer.getCommandSenderName();
    }

    private static String shorten(String value, int max) {
        if (value == null) return "";
        return value.length() > max ? value.substring(0, max - 2) + ".." : value;
    }

    private static int lighten(int color) {
        return GuiStyle.lightenBackground(color);
    }

    private static int darken(int color) {
        return GuiStyle.darkenBackground(color);
    }
}
