package com.pinkyudeer.tasket.gui.panel;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import net.minecraft.client.Minecraft;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;

import org.lwjgl.input.Keyboard;

import com.cleanroommc.modularui.api.IPanelHandler;
import com.cleanroommc.modularui.api.drawable.IKey;
import com.cleanroommc.modularui.api.widget.IWidget;
import com.cleanroommc.modularui.screen.ModularPanel;
import com.cleanroommc.modularui.value.StringValue;
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
import com.pinkyudeer.tasket.task.entity.Task;

import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;

@SideOnly(Side.CLIENT)
public class TaskDetailPanel extends AnimatedPanel {

    private static final int FORM_BG = 0x222233D8;
    private static final int SELECTOR_ACTIVE = 0x446688c0;
    private static final int SELECTOR_OPTION = 0x18182840;
    private static final int SELECTOR_HOVER = 0x28304860;
    private static final int SUBTASK_BG = 0x18182840;
    private static final int SUBTASK_HOVER = 0x28304860;
    private static int nextId;

    private final Task task;
    private final Runnable onChanged;
    private final String uid = String.valueOf(nextId++);
    private final StyledTextField titleField;
    private final StyledMultilineTextField descField;
    private final int detailPanelHeight;
    private final Set<String> draftTagIds = new HashSet<>();

    private Task.TaskStatus selectedStatus;
    private Task.Importance selectedImportance;
    private Task.Urgency selectedUrgency;
    private Task.PrivacyLevel selectedVisibility;
    private final Set<UUID> selectedAssigneeIds = new HashSet<>();

    private ListWidget<IWidget, ?> rightList;
    private ListWidget<IWidget, ?> tagSummaryList;
    private ListWidget<IWidget, ?> subtaskList;
    private ListWidget<IWidget, ?> tagList;
    private ListWidget<IWidget, ?> availableTagList;
    private ListWidget<IWidget, ?> assignList;

    private IPanelHandler statusPickerHandler;
    private IPanelHandler importancePickerHandler;
    private IPanelHandler urgencyPickerHandler;
    private IPanelHandler visibilityPickerHandler;
    private IPanelHandler assigneePickerHandler;
    private IPanelHandler tagPickerHandler;
    private IPanelHandler tagFormHandler;
    private IPanelHandler subtaskFormHandler;
    private IPanelHandler subtaskDetailHandler;
    private Task pendingSubtaskDetail;
    private IWidget pendingAnchor;
    private long lastTaskRevision = -1;
    private long lastTagRevision = -1;

    private static final Task.TaskStatus[] ALL_STATUSES = { Task.TaskStatus.UnClaimed, Task.TaskStatus.Claimed,
        Task.TaskStatus.InProgress, Task.TaskStatus.Blocked, Task.TaskStatus.Postponed, Task.TaskStatus.InTrialRun,
        Task.TaskStatus.Completed, Task.TaskStatus.Closed, Task.TaskStatus.Canceled, Task.TaskStatus.Rejected,
        Task.TaskStatus.Defect };

    private static final Task.Importance[] ALL_IMPORTANCES = { Task.Importance.LOW, Task.Importance.MEDIUM,
        Task.Importance.HIGH, Task.Importance.CRITICAL };

    private static final Task.Urgency[] ALL_URGENCIES = { Task.Urgency.LOW, Task.Urgency.MEDIUM, Task.Urgency.HIGH,
        Task.Urgency.CRITICAL };

    public TaskDetailPanel(Task task, Runnable onChanged) {
        this(task, onChanged, "tasket_task_detail");
    }

    public TaskDetailPanel(Task task, Runnable onChanged, String panelName) {
        super(panelName);
        this.task = task;
        this.onChanged = onChanged;
        this.selectedStatus = task.getStatus() == null ? Task.TaskStatus.UnClaimed : task.getStatus();
        this.selectedImportance = task.getImportance() == null ? Task.Importance.UNDEFINED : task.getImportance();
        this.selectedUrgency = task.getUrgency() == null ? Task.Urgency.UNDEFINED : task.getUrgency();
        this.selectedVisibility = task.getVisibility() == null ? Task.PrivacyLevel.PRIVATE : task.getVisibility();
        for (String assigneeId : TaskClientStore.INSTANCE.getTaskAssigneeIds(task.getId())) {
            selectedAssigneeIds.add(UUID.fromString(assigneeId));
        }

        this.detailPanelHeight = GuiStyle.fitPanelHeight(380, 18, 250);
        size(460, detailPanelHeight);
        center();
        background(FrostedGlassDrawable.create(10f));
        disableHoverBackground();
        overlay(ShaderDrawable.panel(10f, FORM_BG, GuiStyle.ACCENT));

        titleField = GuiStyle.textField();
        titleField.value(new StringValue(task.getTitle() != null ? task.getTitle() : ""));

        descField = GuiStyle.multilineField();
        descField.setFullText(task.getDescription() != null ? task.getDescription() : "");
        descField.hintText("Enter description... (Enter for new line)");

        child(buildContent());
    }

    private String pn(String base) {
        return "task_detail_" + base + "_" + uid;
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
        long taskRevision = TaskClientStore.INSTANCE.getTaskRevision();
        long tagRevision = TaskClientStore.INSTANCE.getTagRevision();
        if (taskRevision != lastTaskRevision || tagRevision != lastTagRevision) {
            lastTaskRevision = taskRevision;
            lastTagRevision = tagRevision;
            refreshTags();
            refreshSubtasks();
        }
    }

    private Flow buildContent() {
        Flow root = Flow.column();
        root.widthRel(1f)
            .heightRel(1f)
            .padding(8)
            .name("detail/root");

        root.child(buildHeader());

        ListWidget<IWidget, ?> scroll = new ListWidget<>();
        scroll.widthRel(1f)
            .height(Math.max(150, detailPanelHeight - 58))
            .marginTop(3)
            .name("detail/scroll");
        scroll.child(buildScrollableContent());
        root.child(scroll);

        root.child(buildActions());
        return root;
    }

    private Flow buildHeader() {
        Flow header = Flow.row();
        header.widthRel(1f)
            .height(18)
            .name("detail/header");
        header.child(
            IKey.str("Task Details")
                .color(GuiStyle.ACCENT)
                .shadow(true)
                .asWidget()
                .widthRel(0.9f)
                .heightRel(1f)
                .name("detail/header_title"));
        header.child(
            GuiStyle.smallButton("X", SELECTOR_OPTION, 0xCCCCCCff)
                .widthRel(0.08f)
                .height(16)
                .name("detail/header/close")
                .onMousePressed(btn -> {
                    closeIfOpen();
                    return true;
                }));
        return header;
    }

    private Flow buildScrollableContent() {
        Flow content = Flow.column();
        content.widthRel(1f)
            .coverChildrenHeight()
            .name("detail/content");

        Flow body = Flow.row();
        body.widthRel(1f)
            .height(170)
            .name("detail/body");
        body.child(buildLeftPane());
        body.child(buildRightPane());
        content.child(body);
        content.child(buildSubtaskSection());
        return content;
    }

    private Flow buildLeftPane() {
        Flow left = Flow.column();
        left.widthRel(0.68f)
            .heightRel(1f)
            .paddingRight(6)
            .name("detail/left_pane");

        left.child(label("Title", 0));
        titleField.widthRel(1f)
            .height(14)
            .name("detail/left/title_field");
        left.child(titleField);

        left.child(buildTagSummaryRow());

        left.child(label("Description", 2));
        descField.widthRel(1f)
            .height(94)
            .name("detail/left/desc_field");
        left.child(descField);

        return left;
    }

    private Flow buildTagSummaryRow() {
        Flow row = Flow.row();
        row.widthRel(1f)
            .height(18)
            .marginTop(3)
            .name("detail/tags_compact");
        row.child(
            IKey.str("Tags")
                .color(0xAAAAAAff)
                .scale(0.85f)
                .asWidget()
                .widthRel(0.16f)
                .heightRel(1f));
        tagSummaryList = new ListWidget<>();
        tagSummaryList.widthRel(0.66f)
            .height(16)
            .name("detail/tags_summary");
        populateTagSummary();
        row.child(tagSummaryList);
        StyledButtonWidget editButton = GuiStyle.smallButton("Edit", GuiStyle.BUTTON_BG, 0xFFFFFFFF);
        editButton.widthRel(0.16f)
            .height(15)
            .marginLeft(4)
            .name("detail/tags/edit")
            .onMousePressed(mouseButton -> {
                openTagPanel(tagSummaryList);
                return true;
            });
        row.child(editButton);
        return row;
    }

    private IWidget buildRightPane() {
        rightList = new ListWidget<>();
        rightList.widthRel(0.32f)
            .heightRel(1f)
            .paddingLeft(4)
            .name("detail/right_pane");
        rebuildRightPane();
        return rightList;
    }

    private void rebuildRightPane() {
        rightList.removeAll();

        Task.Priority prio = Task.calculatePriority(selectedImportance, selectedUrgency);
        rightList.child(label("Priority", 0));
        rightList.child(
            IKey.str(prio.name())
                .color(getPriorityColor(prio) | 0xFF000000)
                .asWidget()
                .widthRel(1f)
                .height(10)
                .name("detail/right/priority_value"));

        rightList.child(label("Scope", 2));
        rightList.child(
            fieldButton(
                selectedVisibility.name(),
                getVisibilityColor(selectedVisibility),
                "detail/right/scope_btn",
                this::openVisibilityPicker));

        rightList.child(label("Status", 2));
        rightList.child(
            fieldButton(
                selectedStatus.name(),
                getStatusTextColor(selectedStatus),
                "detail/right/status_btn",
                this::openStatusPicker));

        rightList.child(label("Assignee", 2));
        rightList.child(
            fieldButton(
                formatAssignees(selectedAssigneeIds),
                selectedAssigneeIds.isEmpty() ? 0xAAAAAAff : 0x66CCFFFF,
                "detail/right/assignee_btn",
                this::openAssigneePicker));

        rightList.child(label("Importance", 2));
        rightList.child(
            fieldButton(
                selectedImportance.name(),
                getImportanceLevelColor(selectedImportance),
                "detail/right/importance_btn",
                this::openImportancePicker));

        rightList.child(label("Urgency", 2));
        rightList.child(
            fieldButton(
                selectedUrgency.name(),
                getUrgencyLevelColor(selectedUrgency),
                "detail/right/urgency_btn",
                this::openUrgencyPicker));

        rightList.scheduleResize();
    }

    private StyledButtonWidget fieldButton(String text, int color, String widgetName,
        java.util.function.Consumer<StyledButtonWidget> onClick) {
        String display = text.length() > 12 ? text.substring(0, 12) : text;
        StyledButtonWidget button = GuiStyle
            .button(display, SELECTOR_ACTIVE, SELECTOR_HOVER, GuiStyle.BUTTON_PRESSED, color, 0.78f);
        button.widthRel(0.98f)
            .height(12)
            .name(widgetName)
            .onMousePressed(mouseButton -> {
                onClick.accept(button);
                return true;
            });
        return button;
    }

    private void openStatusPicker(StyledButtonWidget anchor) {
        pendingAnchor = anchor;
        if (statusPickerHandler == null) {
            statusPickerHandler = IPanelHandler.simple(
                this,
                (ModularPanel p, EntityPlayer pl) -> buildPickerPanel(
                    pn("pick_status"),
                    "Status",
                    ALL_STATUSES,
                    Enum::name,
                    st -> st == selectedStatus,
                    TaskDetailPanel::getStatusTextColor,
                    st -> {
                        selectedStatus = st;
                        if (st == Task.TaskStatus.UnClaimed) selectedAssigneeIds.clear();
                        rebuildRightPane();
                    },
                    pendingAnchor),
                true);
        } else {
            if (statusPickerHandler.isPanelOpen()) return;
            statusPickerHandler.deleteCachedPanel();
        }
        statusPickerHandler.openPanel();
    }

    private void openImportancePicker(StyledButtonWidget anchor) {
        pendingAnchor = anchor;
        if (importancePickerHandler == null) {
            importancePickerHandler = IPanelHandler.simple(
                this,
                (ModularPanel p, EntityPlayer pl) -> buildPickerPanel(
                    pn("pick_importance"),
                    "Importance",
                    ALL_IMPORTANCES,
                    Enum::name,
                    imp -> imp == selectedImportance,
                    TaskDetailPanel::getImportanceLevelColor,
                    imp -> {
                        selectedImportance = imp;
                        rebuildRightPane();
                    },
                    pendingAnchor),
                true);
        } else {
            if (importancePickerHandler.isPanelOpen()) return;
            importancePickerHandler.deleteCachedPanel();
        }
        importancePickerHandler.openPanel();
    }

    private void openUrgencyPicker(StyledButtonWidget anchor) {
        pendingAnchor = anchor;
        if (urgencyPickerHandler == null) {
            urgencyPickerHandler = IPanelHandler.simple(
                this,
                (ModularPanel p, EntityPlayer pl) -> buildPickerPanel(
                    pn("pick_urgency"),
                    "Urgency",
                    ALL_URGENCIES,
                    Enum::name,
                    urg -> urg == selectedUrgency,
                    TaskDetailPanel::getUrgencyLevelColor,
                    urg -> {
                        selectedUrgency = urg;
                        rebuildRightPane();
                    },
                    pendingAnchor),
                true);
        } else {
            if (urgencyPickerHandler.isPanelOpen()) return;
            urgencyPickerHandler.deleteCachedPanel();
        }
        urgencyPickerHandler.openPanel();
    }

    private void openVisibilityPicker(StyledButtonWidget anchor) {
        pendingAnchor = anchor;
        if (visibilityPickerHandler == null) {
            visibilityPickerHandler = IPanelHandler.simple(
                this,
                (ModularPanel p, EntityPlayer pl) -> buildPickerPanel(
                    pn("pick_scope"),
                    "Scope",
                    visibilityValues(),
                    Enum::name,
                    scope -> scope == selectedVisibility,
                    TaskDetailPanel::getVisibilityColor,
                    scope -> {
                        selectedVisibility = scope;
                        rebuildRightPane();
                    },
                    pendingAnchor),
                true);
        } else {
            if (visibilityPickerHandler.isPanelOpen()) return;
            visibilityPickerHandler.deleteCachedPanel();
        }
        visibilityPickerHandler.openPanel();
    }

    private <T> ModularPanel buildPickerPanel(String panelName, String title, T[] values,
        java.util.function.Function<T, String> labelFn, java.util.function.Predicate<T> isActiveFn,
        java.util.function.Function<T, Integer> colorFn, java.util.function.Consumer<T> onSelect, IWidget anchor) {

        int itemH = 13;
        int panelH = values.length * itemH + 26;
        int panelW = Math.max(
            72,
            anchor == null ? 96
                : anchor.getArea()
                    .w());
        ModularPanel picker = new InputSafePanel(panelName);
        picker.size(panelW, panelH);
        placeNear(picker, anchor);
        picker.background(FrostedGlassDrawable.create(6f));
        picker.disableHoverBackground();
        picker.overlay(ShaderDrawable.panel(6f, 0x1E1E38F0, GuiStyle.ACCENT));

        Flow col = Flow.column();
        col.widthRel(1f)
            .heightRel(1f)
            .padding(6)
            .name("picker/" + title + "/root");

        col.child(
            IKey.str(title)
                .color(GuiStyle.ACCENT)
                .scale(0.9f)
                .asWidget()
                .widthRel(1f)
                .height(12)
                .name("picker/" + title + "/header"));

        for (T val : values) {
            boolean active = isActiveFn.test(val);
            String name = labelFn.apply(val);
            col.child(
                GuiStyle
                    .button(
                        name,
                        active ? SELECTOR_ACTIVE : SELECTOR_OPTION,
                        SELECTOR_HOVER,
                        GuiStyle.BUTTON_PRESSED,
                        active ? colorFn.apply(val) : 0xAAAAAAff,
                        0.78f)
                    .widthRel(0.95f)
                    .height(11)
                    .marginTop(1)
                    .name("picker/" + title + "/opt_" + name)
                    .onMousePressed(btn -> {
                        onSelect.accept(val);
                        picker.closeIfOpen();
                        return true;
                    }));
        }

        picker.child(col);
        return picker;
    }

    private void openAssigneePicker(StyledButtonWidget anchor) {
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
        ModularPanel picker = new InputSafePanel(pn("pick_assignee"));
        picker.size(
            Math.max(
                148,
                anchor == null ? 148
                    : anchor.getArea()
                        .w()),
            166);
        placeNear(picker, anchor);
        picker.background(FrostedGlassDrawable.create(6f));
        picker.disableHoverBackground();
        picker.overlay(ShaderDrawable.panel(6f, 0x1E1E38F0, GuiStyle.ACCENT));

        Flow col = Flow.column();
        col.widthRel(1f)
            .heightRel(1f)
            .padding(6)
            .name("picker/assignee/root");
        col.child(
            IKey.str("Assignee")
                .color(GuiStyle.ACCENT)
                .scale(0.9f)
                .asWidget()
                .widthRel(1f)
                .height(12));
        col.child(
            GuiStyle.smallButton("Clear", SELECTOR_OPTION, 0xCCCCCCff)
                .widthRel(0.95f)
                .height(13)
                .marginTop(2)
                .onMousePressed(btn -> {
                    selectedAssigneeIds.clear();
                    if (selectedStatus == Task.TaskStatus.Claimed) selectedStatus = Task.TaskStatus.UnClaimed;
                    rebuildRightPane();
                    refreshAssigneeList(picker);
                    return true;
                }));
        col.child(
            GuiStyle.smallButton("Done", SELECTOR_ACTIVE, 0xFFFFFFFF)
                .widthRel(0.95f)
                .height(13)
                .marginTop(2)
                .onMousePressed(btn -> {
                    picker.closeIfOpen();
                    return true;
                }));

        assignList = new ListWidget<>();
        assignList.widthRel(1f)
            .height(106)
            .marginTop(3)
            .name("picker/assignee/list");
        populateAssigneeList(picker);
        col.child(assignList);
        picker.child(col);
        return picker;
    }

    private void populateAssigneeList(ModularPanel picker) {
        if (task.getTeamId() == null) {
            UUID self = currentPlayerId();
            if (self == null) {
                assignList.child(emptyText("No player."));
                return;
            }
            assignList.child(assigneeButton("Me  " + currentPlayerName(), self, picker));
            return;
        }
        NBTTagCompound team = TaskClientStore.INSTANCE.getTeam(
            task.getTeamId()
                .toString());
        if (team == null) {
            assignList.child(emptyText("Team not synced."));
            return;
        }
        NBTTagList members = team.getTagList("members", 10);
        int added = 0;
        for (int i = 0; i < members.tagCount(); i++) {
            NBTTagCompound member = members.getCompoundTagAt(i);
            if (!"ACTIVE".equals(member.getString("status"))) continue;
            UUID playerId = UUID.fromString(member.getString("playerId"));
            assignList.child(assigneeButton(memberDisplayName(member), playerId, picker));
            added++;
        }
        if (added == 0) assignList.child(emptyText("No active members."));
    }

    private StyledButtonWidget assigneeButton(String text, UUID assigneeId, ModularPanel picker) {
        boolean active = assigneeId != null && selectedAssigneeIds.contains(assigneeId);
        return GuiStyle
            .button(
                text,
                active ? SELECTOR_ACTIVE : SELECTOR_OPTION,
                SELECTOR_HOVER,
                GuiStyle.BUTTON_PRESSED,
                active ? 0xFFFFFFFF : 0xCCCCCCff,
                0.78f)
            .widthRel(0.95f)
            .height(13)
            .marginTop(1)
            .onMousePressed(btn -> {
                toggleAssignee(assigneeId);
                refreshAssigneeList(picker);
                return true;
            });
    }

    private void toggleAssignee(UUID assigneeId) {
        if (assigneeId == null) return;
        if (selectedAssigneeIds.contains(assigneeId)) selectedAssigneeIds.remove(assigneeId);
        else selectedAssigneeIds.add(assigneeId);
        if (selectedAssigneeIds.isEmpty() && selectedStatus == Task.TaskStatus.Claimed)
            selectedStatus = Task.TaskStatus.UnClaimed;
        if (!selectedAssigneeIds.isEmpty() && selectedStatus == Task.TaskStatus.UnClaimed)
            selectedStatus = Task.TaskStatus.Claimed;
        rebuildRightPane();
    }

    private void refreshAssigneeList(ModularPanel picker) {
        if (assignList == null) return;
        assignList.removeAll();
        populateAssigneeList(picker);
        assignList.scheduleResize();
    }

    private void placeNear(ModularPanel panel, IWidget anchor) {
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

    private Task.PrivacyLevel[] visibilityValues() {
        if (task.getTeamId() == null)
            return new Task.PrivacyLevel[] { Task.PrivacyLevel.PRIVATE, Task.PrivacyLevel.PUBLIC };
        return Task.PrivacyLevel.values();
    }

    private int getPriorityColor(Task.Priority priority) {
        return switch (priority) {
            case CRITICAL -> 0xFF4444;
            case P1, P2 -> 0xFF8844;
            case P3, P4 -> 0xFFCC44;
            case P5, P6 -> 0x44AAFF;
            case P7, P8, P9 -> 0x88CC88;
            case UNDEFINED -> 0x888888;
        };
    }

    private static int getStatusTextColor(Task.TaskStatus status) {
        return switch (status) {
            case Completed, Closed -> 0x66CC66ff;
            case Claimed -> 0x66CCFFFF;
            case InProgress -> 0x44AAFFff;
            case Blocked, Rejected, Defect -> 0xFF6666ff;
            case Postponed -> 0xFFAA44ff;
            case Canceled -> 0x888888ff;
            default -> 0xCCCCCCff;
        };
    }

    private static int getImportanceLevelColor(Task.Importance imp) {
        return switch (imp) {
            case CRITICAL -> 0xFF4444ff;
            case HIGH -> 0xFF8844ff;
            case MEDIUM -> 0xFFCC44ff;
            case LOW -> 0x88CC88ff;
            case UNDEFINED -> 0x888888ff;
        };
    }

    private static int getUrgencyLevelColor(Task.Urgency urg) {
        return switch (urg) {
            case CRITICAL -> 0xFF4444ff;
            case HIGH -> 0xFF8844ff;
            case MEDIUM -> 0xFFCC44ff;
            case LOW -> 0x88CC88ff;
            case UNDEFINED -> 0x888888ff;
        };
    }

    private static int getVisibilityColor(Task.PrivacyLevel visibility) {
        return switch (visibility) {
            case PUBLIC -> 0x88CC88ff;
            case TEAM -> 0x66CCFFFF;
            case PRIVATE -> 0xFFCC66ff;
        };
    }

    private IWidget label(String text, int topMargin) {
        return IKey.str(text)
            .color(0xAAAAAAff)
            .scale(0.85f)
            .asWidget()
            .widthRel(1f)
            .height(10)
            .marginTop(topMargin);
    }

    private IWidget emptyText(String text) {
        return IKey.str(text)
            .color(0x666666ff)
            .scale(0.82f)
            .asWidget()
            .widthRel(1f)
            .height(14);
    }

    private void openTagPanel(IWidget anchor) {
        pendingAnchor = anchor;
        if (tagPickerHandler == null) {
            tagPickerHandler = IPanelHandler.simple(this, (p, pl) -> buildTagPanel(pendingAnchor), true);
        } else {
            if (tagPickerHandler.isPanelOpen()) return;
            tagPickerHandler.deleteCachedPanel();
        }
        tagPickerHandler.openPanel();
    }

    private ModularPanel buildTagPanel(IWidget anchor) {
        ModularPanel panel = new InputSafePanel(pn("task_tags"));
        panel.size(252, 190);
        placeNear(panel, anchor);
        panel.background(FrostedGlassDrawable.create(6f));
        panel.disableHoverBackground();
        panel.overlay(ShaderDrawable.panel(6f, 0x1E1E38F0, GuiStyle.ACCENT));
        resetDraftTags();

        Flow root = Flow.column();
        root.widthRel(1f)
            .heightRel(1f)
            .padding(6)
            .name("tag_picker/root");
        root.child(
            IKey.str("Tags")
                .color(GuiStyle.ACCENT)
                .scale(0.9f)
                .asWidget()
                .widthRel(1f)
                .height(12));

        Flow lists = Flow.row();
        lists.widthRel(1f)
            .height(108)
            .marginTop(3)
            .name("tag_picker/lists");
        tagList = new ListWidget<>();
        tagList.widthRel(0.5f)
            .heightRel(1f)
            .paddingRight(3)
            .name("tag_picker/current");
        availableTagList = new ListWidget<>();
        availableTagList.widthRel(0.5f)
            .heightRel(1f)
            .paddingLeft(3)
            .name("tag_picker/available");
        populateDraftTags();
        populateDraftAvailableTags();
        lists.child(tagList);
        lists.child(availableTagList);
        root.child(lists);

        root.child(buildTagCreateRow(panel));
        root.child(buildTagActions(panel));
        panel.child(root);
        return panel;
    }

    private Flow buildTagCreateRow(ModularPanel parent) {
        Flow row = Flow.row();
        row.widthRel(1f)
            .height(18)
            .marginTop(5)
            .name("tag_picker/create");
        row.child(
            IKey.str("")
                .asWidget()
                .widthRel(0.66f)
                .heightRel(1f));
        row.child(
            GuiStyle.smallButton("+ New Tag", GuiStyle.BUTTON_BG, 0xFFFFFFFF)
                .widthRel(0.3f)
                .height(15)
                .name("tag_picker/add")
                .onMousePressed(btn -> {
                    openTagForm(parent);
                    return true;
                }));
        return row;
    }

    private Flow buildTagActions(ModularPanel panel) {
        Flow row = Flow.row();
        row.widthRel(1f)
            .height(20)
            .marginTop(5)
            .name("tag_picker/actions");
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
                    saveDraftTags(panel);
                    return true;
                }));
        return row;
    }

    private void populateTagSummary() {
        if (tagSummaryList == null) return;
        tagSummaryList.removeAll();
        List<NBTTagCompound> current = TaskClientStore.INSTANCE.getTaskTags(task.getId());
        if (current.isEmpty()) {
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
            int visible = Math.min(2, current.size());
            for (int i = 0; i < visible; i++) {
                NBTTagCompound tag = current.get(i);
                int bg = GuiStyle.parseColor(tag.getString("colorCode"), GuiStyle.BUTTON_BG);
                StyledButtonWidget chip = GuiStyle.tagChip(tag.getString("name"), bg);
                if (i > 0) chip.marginLeft(GuiStyle.TAG_CHIP_GAP);
                row.child(chip);
            }
            if (current.size() > visible) {
                row.child(
                    IKey.str("+" + (current.size() - visible))
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

    private void resetDraftTags() {
        draftTagIds.clear();
        for (NBTTagCompound tag : TaskClientStore.INSTANCE.getTaskTags(task.getId())) {
            String id = tag.getString("id");
            if (!id.isEmpty()) draftTagIds.add(id);
        }
    }

    private void populateDraftTags() {
        if (tagList == null) return;
        if (draftTagIds.isEmpty()) {
            tagList.child(emptyText("None"));
            return;
        }
        int added = 0;
        for (String tagId : draftTagIds) {
            NBTTagCompound tag = TaskClientStore.INSTANCE.getTag(tagId);
            if (tag == null) continue;
            tagList.child(buildDraftTagItem(tag));
            added++;
        }
        if (added == 0) tagList.child(emptyText("None"));
    }

    private void populateDraftAvailableTags() {
        if (availableTagList == null) return;
        int added = 0;
        for (NBTTagCompound tag : TaskClientStore.INSTANCE.getTagList()) {
            String id = tag.getString("id");
            if (id.isEmpty() || draftTagIds.contains(id) || !isTagAllowedForTask(tag)) continue;
            availableTagList.child(buildAvailableTagItem(tag));
            added++;
        }
        if (added == 0) availableTagList.child(emptyText("No available"));
    }

    private StyledButtonWidget buildDraftTagItem(NBTTagCompound tag) {
        int bg = GuiStyle.parseColor(tag.getString("colorCode"), GuiStyle.BUTTON_BG);
        String name = tag.getString("name");
        return GuiStyle.tagChip("x " + name, bg)
            .marginTop(1)
            .name("detail/tag/current_" + safeName(name))
            .onMousePressed(btn -> {
                draftTagIds.remove(tag.getString("id"));
                refreshDraftTagLists();
                return true;
            });
    }

    private StyledButtonWidget buildAvailableTagItem(NBTTagCompound tag) {
        int bg = GuiStyle.parseColor(tag.getString("colorCode"), GuiStyle.TOGGLE_INACTIVE);
        String name = tag.getString("name");
        return GuiStyle.tagChip("+ " + name, bg)
            .marginTop(1)
            .name("detail/tag/available_" + safeName(name))
            .onMousePressed(btn -> {
                draftTagIds.add(tag.getString("id"));
                refreshDraftTagLists();
                return true;
            });
    }

    private void refreshDraftTagLists() {
        if (tagList != null) {
            tagList.removeAll();
            populateDraftTags();
            tagList.scheduleResize();
        }
        if (availableTagList != null) {
            availableTagList.removeAll();
            populateDraftAvailableTags();
            availableTagList.scheduleResize();
        }
    }

    private void saveDraftTags(ModularPanel panel) {
        TaskClientActions.setTags(task.getId(), new ArrayList<>(draftTagIds));
        panel.closeIfOpen();
        if (onChanged != null) onChanged.run();
    }

    private boolean isTagAllowedForTask(NBTTagCompound tag) {
        String scope = tag.getString("scope");
        if ("SYSTEM".equals(scope) || "PUBLIC".equals(scope)) return true;
        if (selectedVisibility == Task.PrivacyLevel.PRIVATE) return "PRIVATE".equals(scope);
        if (selectedVisibility == Task.PrivacyLevel.TEAM && "TEAM".equals(scope)) {
            return task.getTeamId() != null && task.getTeamId()
                .toString()
                .equals(tag.getString("ownerTeamId"));
        }
        return false;
    }

    private void refreshTags() {
        populateTagSummary();
        if (tagList != null) {
            tagList.removeAll();
            populateDraftTags();
            tagList.scheduleResize();
        }
        if (availableTagList != null) {
            availableTagList.removeAll();
            populateDraftAvailableTags();
            availableTagList.scheduleResize();
        }
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

    private static int lighten(int color) {
        return GuiStyle.lightenBackground(color);
    }

    private static int darken(int color) {
        return GuiStyle.darkenBackground(color);
    }

    private static String safeName(String name) {
        if (name == null || name.isEmpty()) return "empty";
        String cleaned = name.replaceAll("[^a-zA-Z0-9_\\-]", "_");
        return cleaned.length() > 16 ? cleaned.substring(0, 16) : cleaned;
    }

    private Flow buildSubtaskSection() {
        Flow section = Flow.column();
        section.widthRel(1f)
            .height(112)
            .marginTop(5)
            .name("detail/subtask_section");

        Flow header = Flow.row();
        header.widthRel(1f)
            .height(14)
            .name("detail/subtask/header");
        header.child(
            IKey.str("Subtasks")
                .color(0xAAAAAAff)
                .scale(0.85f)
                .asWidget()
                .widthRel(0.82f)
                .heightRel(1f));
        header.child(
            GuiStyle.smallButton("+ Add", GuiStyle.BUTTON_BG, 0xCCCCCCff)
                .widthRel(0.16f)
                .height(12)
                .name("detail/subtask/btn_add")
                .onMousePressed(btn -> {
                    openSubtaskForm();
                    return true;
                }));
        section.child(header);

        subtaskList = new ListWidget<>();
        subtaskList.widthRel(1f)
            .height(94)
            .marginTop(2)
            .name("detail/subtask/list");
        populateSubtasks();
        section.child(subtaskList);
        return section;
    }

    private void populateSubtasks() {
        try {
            List<Task> subs = TaskClientStore.INSTANCE.getSubtasks(task.getId());
            if (subs.isEmpty()) {
                subtaskList.child(
                    IKey.str("No subtasks.")
                        .color(0x666666ff)
                        .asWidget()
                        .widthRel(1f)
                        .height(16)
                        .name("detail/subtask/empty_hint"));
            } else {
                for (Task sub : subs) {
                    subtaskList.child(buildSubtaskItem(sub));
                }
            }
        } catch (Exception e) {
            Tasket.LOG.error("Failed to load subtasks", e);
        }
    }

    private void refreshSubtasks() {
        if (subtaskList == null) return;
        subtaskList.removeAll();
        populateSubtasks();
        subtaskList.scheduleResize();
    }

    private StyledButtonWidget buildSubtaskItem(Task sub) {
        boolean done = sub.getStatus() == Task.TaskStatus.Completed || sub.getStatus() == Task.TaskStatus.Closed;
        String prefix = done ? "[/] " : "[ ] ";
        int textColor = done ? 0x888888 : 0xCCCCCC;
        String shortTitle = sub.getTitle()
            .length() > 10 ? sub.getTitle()
                .substring(0, 10) : sub.getTitle();
        StyledButtonWidget btn = GuiStyle.button(
            prefix + sub.getTitle(),
            SUBTASK_BG,
            SUBTASK_HOVER,
            GuiStyle.ITEM_PRESSED,
            textColor | 0xFF000000,
            0.85f);
        btn.widthRel(1f)
            .height(14)
            .marginTop(1)
            .name("detail/subtask/item_" + shortTitle)
            .onMousePressed(b -> {
                openSubtaskDetail(sub);
                return true;
            });
        return btn;
    }

    private void openSubtaskForm() {
        if (subtaskFormHandler == null) {
            subtaskFormHandler = IPanelHandler
                .simple(this, (ModularPanel p, EntityPlayer pl) -> new TaskFormPanel(() -> {
                    refreshSubtasks();
                    if (onChanged != null) onChanged.run();
                }, task.getId(), pn("subtask_form")), true);
        } else {
            if (subtaskFormHandler.isPanelOpen()) return;
            subtaskFormHandler.deleteCachedPanel();
        }
        subtaskFormHandler.openPanel();
    }

    private void openSubtaskDetail(Task sub) {
        this.pendingSubtaskDetail = sub;
        if (subtaskDetailHandler == null) {
            subtaskDetailHandler = IPanelHandler
                .simple(this, (ModularPanel p, EntityPlayer pl) -> new TaskDetailPanel(pendingSubtaskDetail, () -> {
                    refreshSubtasks();
                    if (onChanged != null) onChanged.run();
                }, pn("subtask_detail")), true);
        } else {
            if (subtaskDetailHandler.isPanelOpen()) return;
            subtaskDetailHandler.deleteCachedPanel();
        }
        subtaskDetailHandler.openPanel();
    }

    private Flow buildActions() {
        Flow actions = Flow.row();
        actions.widthRel(1f)
            .height(17)
            .marginTop(4)
            .name("detail/actions");

        boolean isCompleted = task.getStatus() == Task.TaskStatus.Completed
            || task.getStatus() == Task.TaskStatus.Closed;
        if (!isCompleted) {
            actions.child(
                GuiStyle.smallButton("Complete", GuiStyle.BUTTON_BG, 0x66CC66ff)
                    .widthRel(0.22f)
                    .height(14)
                    .name("detail/actions/btn_complete")
                    .onMousePressed(btn -> {
                        completeAndClose();
                        return true;
                    }));
        }

        actions.child(
            GuiStyle.dangerButton("Delete")
                .widthRel(0.18f)
                .height(14)
                .marginLeft(isCompleted ? 0 : 3)
                .name("detail/actions/btn_delete")
                .onMousePressed(btn -> {
                    deleteAndClose();
                    return true;
                }));

        actions.child(
            GuiStyle.smallButton("Share", GuiStyle.BUTTON_BG, 0x66CCFFff)
                .widthRel(0.18f)
                .height(14)
                .marginLeft(3)
                .name("detail/actions/btn_share")
                .onMousePressed(btn -> {
                    shareTask();
                    return true;
                }));

        actions.child(
            IKey.str("")
                .asWidget()
                .widthRel(isCompleted ? 0.3f : 0.14f)
                .heightRel(1f));

        actions.child(
            GuiStyle.saveButton("Save")
                .widthRel(0.2f)
                .height(14)
                .name("detail/actions/btn_save")
                .onMousePressed(btn -> {
                    saveChanges();
                    return true;
                }));

        return actions;
    }

    private void saveChanges() {
        try {
            String newTitle = titleField.getText();
            String newDesc = descField.getFullText();
            if (newTitle == null || newTitle.trim()
                .isEmpty()) return;

            if (selectedStatus == Task.TaskStatus.UnClaimed) selectedAssigneeIds.clear();
            TaskClientActions.updateTask(
                task.getId(),
                task.getVersion() == null ? 0 : task.getVersion(),
                newTitle.trim(),
                newDesc != null ? newDesc.trim() : "",
                selectedImportance,
                selectedUrgency,
                selectedStatus,
                selectedVisibility);
            TaskClientActions.assignTask(task.getId(), selectedAssigneeIdStrings());
            closeIfOpen();
            if (onChanged != null) onChanged.run();
        } catch (Exception e) {
            Tasket.LOG.error("Failed to save task changes", e);
        }
    }

    private void completeAndClose() {
        try {
            TaskClientActions.completeTask(task.getId());
            closeIfOpen();
            if (onChanged != null) onChanged.run();
        } catch (Exception e) {
            Tasket.LOG.error("Failed to complete task", e);
        }
    }

    private void deleteAndClose() {
        try {
            TaskClientActions.deleteTask(task.getId());
            closeIfOpen();
            if (onChanged != null) onChanged.run();
        } catch (Exception e) {
            Tasket.LOG.error("Failed to delete task", e);
        }
    }

    private void shareTask() {
        String title = titleField.getText();
        if (title == null || title.trim()
            .isEmpty()) title = task.getTitle();

        StringBuilder sb = new StringBuilder();
        sb.append("[Tasket] ")
            .append(title);
        sb.append(" | ")
            .append(selectedStatus.name());
        if (selectedImportance != Task.Importance.UNDEFINED) sb.append(" | Imp:")
            .append(selectedImportance.name());
        if (selectedUrgency != Task.Urgency.UNDEFINED) sb.append(" | Urg:")
            .append(selectedUrgency.name());
        if (!selectedAssigneeIds.isEmpty()) sb.append(" | -> ")
            .append(formatAssignees(selectedAssigneeIds));

        String desc = descField.getFullText();
        if (desc != null && !desc.trim()
            .isEmpty()) {
            String shortDesc = desc.trim()
                .replace('\n', ' ');
            if (shortDesc.length() > 60) shortDesc = shortDesc.substring(0, 58) + "..";
            sb.append(" | ")
                .append(shortDesc);
        }

        String message = sb.toString();
        net.minecraft.client.gui.GuiScreen.setClipboardString(message);

        if (Minecraft.getMinecraft().thePlayer != null) {
            Minecraft.getMinecraft().thePlayer
                .addChatMessage(new net.minecraft.util.ChatComponentText("§7[Tasket] Task info copied to clipboard."));
        }
    }

    private UUID currentPlayerId() {
        if (Minecraft.getMinecraft() == null || Minecraft.getMinecraft().thePlayer == null) return null;
        return Minecraft.getMinecraft().thePlayer.getUniqueID();
    }

    private String formatAssignee(UUID assigneeId) {
        if (assigneeId == null) return "None";
        UUID self = currentPlayerId();
        if (assigneeId.equals(self)) return currentPlayerName();
        NBTTagCompound member = findTeamMember(assigneeId);
        return member == null ? "Unknown" : shorten(memberDisplayName(member), 12);
    }

    private String formatAssignees(Set<UUID> assigneeIds) {
        if (assigneeIds == null || assigneeIds.isEmpty()) return "None";
        UUID first = assigneeIds.iterator()
            .next();
        String firstName = formatAssignee(first);
        return assigneeIds.size() == 1 ? firstName : firstName + " +" + (assigneeIds.size() - 1);
    }

    private List<String> selectedAssigneeIdStrings() {
        List<String> result = new ArrayList<>();
        for (UUID id : selectedAssigneeIds) {
            result.add(id.toString());
        }
        return result;
    }

    private NBTTagCompound findTeamMember(UUID playerId) {
        if (playerId == null || task.getTeamId() == null) return null;
        NBTTagCompound team = TaskClientStore.INSTANCE.getTeam(
            task.getTeamId()
                .toString());
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
}
