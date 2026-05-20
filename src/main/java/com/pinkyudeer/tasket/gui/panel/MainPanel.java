package com.pinkyudeer.tasket.gui.panel;

import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.FontRenderer;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.nbt.NBTTagCompound;

import org.lwjgl.input.Keyboard;
import org.lwjgl.opengl.GL11;

import com.cleanroommc.modularui.api.IPanelHandler;
import com.cleanroommc.modularui.api.drawable.IDrawable;
import com.cleanroommc.modularui.api.drawable.IKey;
import com.cleanroommc.modularui.api.widget.IWidget;
import com.cleanroommc.modularui.screen.ModularPanel;
import com.cleanroommc.modularui.widgets.ListWidget;
import com.cleanroommc.modularui.widgets.layout.Flow;
import com.pinkyudeer.tasket.client.TaskClientStore;
import com.pinkyudeer.tasket.gui.GuiStyle;
import com.pinkyudeer.tasket.gui.drawable.ShaderDrawable;
import com.pinkyudeer.tasket.gui.screen.TaskScreen;
import com.pinkyudeer.tasket.gui.widget.StyledButtonWidget;
import com.pinkyudeer.tasket.network.handler.NetMainSync;
import com.pinkyudeer.tasket.network.handler.NetTagSync;
import com.pinkyudeer.tasket.network.handler.NetTeamSync;
import com.pinkyudeer.tasket.task.entity.Task;

import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;

@SideOnly(Side.CLIENT)
public class MainPanel extends ModularPanel {

    private static final int ACCENT = 0x99ccffff;
    private static final int PANEL_BG = 0x181820C0;
    private static final int SIDEBAR_BG = 0x141828B0;
    private static final int ITEM_BG = 0x20203040;
    private static final int ITEM_HOVER = 0x30406060;
    private static final int BTN_BG = 0x335588a0;
    private static final int BTN_HOVER = 0x4477aacc;
    private static final int SORT_ACTIVE = 0x3366aa99;
    private static final int SORT_INACTIVE = 0x1a223344;
    private static final int FILTER_ON = 0x336644aa;
    private static final int FILTER_OFF = 0x1a223344;
    private static final int SUB_INDENT = 16;

    private static final DateTimeFormatter TIME_FMT = DateTimeFormatter.ofPattern("MM-dd HH:mm");

    private enum SortMode {
        PRIORITY,
        TIME_DESC,
        TIME_ASC
    }

    private enum ViewMode {
        TASKS,
        TEAMS,
        TAGS
    }

    private final TaskScreen taskScreen;
    private Flow contentArea;
    private ListWidget<IWidget, ?> taskListWidget;
    private ListWidget<IWidget, ?> teamListWidget;
    private ListWidget<IWidget, ?> tagListWidget;
    private IPanelHandler formHandler;
    private IPanelHandler detailHandler;
    private IPanelHandler teamFormHandler;
    private IPanelHandler teamDetailHandler;
    private IPanelHandler tagFormHandler;
    private String lastDetailTaskId;
    private SortMode currentSort = SortMode.PRIORITY;
    private ViewMode currentView = ViewMode.TASKS;
    private boolean showCompleted = false;
    private boolean showMineOnly = false;
    private StyledButtonWidget sortPrioBtn;
    private StyledButtonWidget sortTimeBtn;
    private StyledButtonWidget filterDoneBtn;
    private StyledButtonWidget filterMineBtn;
    private StyledButtonWidget navTasksBtn;
    private StyledButtonWidget navTeamsBtn;
    private StyledButtonWidget navTagsBtn;
    private final Set<String> expandedTasks = new HashSet<>();
    private NBTTagCompound pendingTeamDetail;

    private List<Task> cachedTasks = new ArrayList<>();
    private final Map<String, List<Task>> cachedSubtasks = new HashMap<>();
    private final Map<String, Integer> cachedSubCounts = new HashMap<>();
    private long seenTaskRevision = -1;
    private long seenTeamRevision = -1;
    private long seenTagRevision = -1;
    private int lastPanelWidth = -1;
    private int lastPanelHeight = -1;

    public MainPanel(TaskScreen taskScreen) {
        super("tasket_main_panel");
        this.taskScreen = taskScreen;
        sizeRel(0.88f, 0.84f);
        center();
        background(IDrawable.EMPTY);
        overlay(ShaderDrawable.panel(12f, PANEL_BG, ACCENT));
        NetMainSync.requestSync();
        child(buildLayout());
    }

    @Override
    public void closeIfOpen() {
        if (!isOpen()) return;
        if (taskScreen.isClosing()) {
            super.closeIfOpen();
            return;
        }
        taskScreen.startClosing(super::closeIfOpen);
    }

    @Override
    public void onUpdate() {
        super.onUpdate();
        int panelWidth = getArea().w();
        int panelHeight = getArea().h();
        if (panelWidth != lastPanelWidth || panelHeight != lastPanelHeight) {
            lastPanelWidth = panelWidth;
            lastPanelHeight = panelHeight;
            scheduleResize();
            if (contentArea != null) contentArea.scheduleResize();
            if (taskListWidget != null) taskListWidget.scheduleResize();
            if (teamListWidget != null) teamListWidget.scheduleResize();
            if (tagListWidget != null) tagListWidget.scheduleResize();
        }

        long taskRevision = TaskClientStore.INSTANCE.getTaskRevision();
        long teamRevision = TaskClientStore.INSTANCE.getTeamRevision();
        long tagRevision = TaskClientStore.INSTANCE.getTagRevision();
        if (taskRevision != seenTaskRevision) {
            seenTaskRevision = taskRevision;
            if (currentView == ViewMode.TASKS) refreshTaskList();
        }
        if (teamRevision != seenTeamRevision) {
            seenTeamRevision = teamRevision;
            if (currentView == ViewMode.TEAMS) refreshTeamList();
        }
        if (tagRevision != seenTagRevision) {
            seenTagRevision = tagRevision;
            if (currentView == ViewMode.TAGS) refreshTagList();
        }
    }

    // --- Cache management ---

    private void reloadCache() {
        cachedSubtasks.clear();
        cachedSubCounts.clear();
        try {
            cachedTasks = TaskClientStore.INSTANCE.getTaskList(showCompleted);
        } catch (Exception e) {
            cachedTasks = new ArrayList<>();
        }
        if (showMineOnly) {
            UUID self = currentPlayerId();
            cachedTasks.removeIf(task -> self == null || !isAssignedTo(task, self));
        }
        for (Task t : cachedTasks) {
            if (t.getParentTaskId() != null) continue;
            ensureSubtasksCached(t.getId());
        }
    }

    private void ensureSubtasksCached(String taskId) {
        if (cachedSubCounts.containsKey(taskId)) return;
        List<Task> subs = TaskClientStore.INSTANCE.getSubtasks(taskId);
        cachedSubtasks.put(taskId, subs);
        cachedSubCounts.put(taskId, subs.size());
        if (expandedTasks.contains(taskId)) {
            for (Task sub : subs) {
                ensureSubtasksCached(sub.getId());
            }
        }
    }

    private int getCachedSubCount(String taskId) {
        if (!cachedSubCounts.containsKey(taskId)) {
            ensureSubtasksCached(taskId);
        }
        return cachedSubCounts.getOrDefault(taskId, 0);
    }

    private List<Task> getCachedSubtasks(String taskId) {
        if (!cachedSubCounts.containsKey(taskId)) {
            ensureSubtasksCached(taskId);
        }
        return cachedSubtasks.getOrDefault(taskId, new ArrayList<>());
    }

    // --- Layout ---

    private Flow buildLayout() {
        Flow layout = Flow.row();
        layout.widthRel(1f)
            .heightRel(1f)
            .padding(6)
            .name("main/layout");
        layout.child(buildSidebar());
        layout.child(buildContentArea());
        return layout;
    }

    private Flow buildSidebar() {
        Flow sidebar = Flow.column();
        sidebar.widthRel(0.18f)
            .heightRel(1f)
            .name("main/sidebar");
        sidebar.background(ShaderDrawable.roundedRect(8f, SIDEBAR_BG));
        sidebar.padding(8);
        sidebar.child(
            IKey.str("TASKET")
                .color(ACCENT)
                .shadow(true)
                .asWidget()
                .heightRel(0.08f)
                .widthRel(1f)
                .name("sidebar/logo"));
        navTasksBtn = navButton("Tasks", ViewMode.TASKS, "sidebar/nav_tasks");
        navTeamsBtn = navButton("Teams", ViewMode.TEAMS, "sidebar/nav_teams");
        navTagsBtn = navButton("Tags", ViewMode.TAGS, "sidebar/nav_tags");
        sidebar.child(navTasksBtn);
        sidebar.child(navTeamsBtn);
        sidebar.child(navTagsBtn);
        return sidebar;
    }

    private StyledButtonWidget navButton(String label, ViewMode mode, String widgetName) {
        boolean active = currentView == mode;
        int bg = active ? BTN_BG : 0x00000000;
        return GuiStyle.button(label, bg, BTN_HOVER, GuiStyle.BUTTON_PRESSED, active ? 0xFFFFFFff : 0xAAAAAAff, 1f)
            .widthRel(0.9f)
            .height(22)
            .marginTop(4)
            .name(widgetName)
            .onMousePressed(btn -> {
                switchView(mode);
                return true;
            });
    }

    private void switchView(ViewMode view) {
        if (currentView == view) return;
        currentView = view;
        refreshNavButtons();
        rebuildContentArea();
    }

    private void refreshNavButtons() {
        updateNavButton(navTasksBtn, "Tasks", ViewMode.TASKS);
        updateNavButton(navTeamsBtn, "Teams", ViewMode.TEAMS);
        updateNavButton(navTagsBtn, "Tags", ViewMode.TAGS);
    }

    private void updateNavButton(StyledButtonWidget button, String label, ViewMode mode) {
        if (button == null) return;
        boolean active = currentView == mode;
        int bg = active ? BTN_BG : 0x00000000;
        button.setBackgrounds(
            ShaderDrawable.roundedRect(6f, bg),
            ShaderDrawable.roundedRect(6f, BTN_HOVER),
            ShaderDrawable.roundedRect(6f, GuiStyle.BUTTON_PRESSED));
        button.overlay(
            IKey.str(label)
                .color(active ? 0xFFFFFFff : 0xAAAAAAff)
                .shadow(active));
    }

    private Flow buildContentArea() {
        contentArea = Flow.column();
        contentArea.widthRel(0.82f)
            .heightRel(1f)
            .name("main/content");
        contentArea.paddingLeft(8);
        rebuildContentArea();
        return contentArea;
    }

    private void rebuildContentArea() {
        if (contentArea == null) return;
        contentArea.removeAll();
        taskListWidget = null;
        teamListWidget = null;
        tagListWidget = null;
        if (currentView == ViewMode.TASKS) {
            contentArea.child(buildHeader());
            contentArea.child(buildSortBar());
            taskListWidget = new ListWidget<>();
            taskListWidget.widthRel(1f)
                .heightRel(0.82f)
                .name("content/task_list");
            populateTaskList(true);
            contentArea.child(taskListWidget);
        } else if (currentView == ViewMode.TEAMS) {
            contentArea.child(buildTeamHeader());
            teamListWidget = new ListWidget<>();
            teamListWidget.widthRel(1f)
                .heightRel(0.9f)
                .name("content/team_list");
            populateTeamList();
            contentArea.child(teamListWidget);
        } else {
            contentArea.child(buildTagHeader());
            tagListWidget = new ListWidget<>();
            tagListWidget.widthRel(1f)
                .heightRel(0.9f)
                .name("content/tag_list");
            populateTagList();
            contentArea.child(tagListWidget);
        }
        contentArea.scheduleResize();
    }

    private Flow buildHeader() {
        Flow header = Flow.row();
        header.widthRel(1f)
            .height(32)
            .marginBottom(2)
            .name("content/header");
        header.child(
            IKey.str("Active Tasks")
                .color(0xEEEEEEff)
                .shadow(true)
                .asWidget()
                .widthRel(0.7f)
                .heightRel(1f)
                .name("header/title"));
        header.child(
            GuiStyle.button("+ New Task")
                .widthRel(0.25f)
                .height(24)
                .topRel(0.5f)
                .anchorTop(0.5f)
                .name("header/btn_new_task")
                .onMousePressed(btn -> {
                    openTaskForm();
                    return true;
                }));
        return header;
    }

    private Flow buildTeamHeader() {
        Flow header = Flow.row();
        header.widthRel(1f)
            .height(34)
            .marginBottom(4)
            .name("teams/header");
        header.child(
            IKey.str("Teams")
                .color(0xEEEEEEff)
                .shadow(true)
                .asWidget()
                .widthRel(0.7f)
                .heightRel(1f));
        header.child(
            GuiStyle.button("+ New Team")
                .widthRel(0.25f)
                .height(24)
                .topRel(0.5f)
                .anchorTop(0.5f)
                .name("teams/header/new")
                .onMousePressed(btn -> {
                    openTeamForm();
                    return true;
                }));
        return header;
    }

    private Flow buildTagHeader() {
        Flow header = Flow.row();
        header.widthRel(1f)
            .height(34)
            .marginBottom(4)
            .name("tags/header");
        header.child(
            IKey.str("Tags")
                .color(0xEEEEEEff)
                .shadow(true)
                .asWidget()
                .widthRel(0.7f)
                .heightRel(1f));
        header.child(
            GuiStyle.button("+ New Tag")
                .widthRel(0.25f)
                .height(24)
                .topRel(0.5f)
                .anchorTop(0.5f)
                .name("tags/header/new")
                .onMousePressed(btn -> {
                    openTagForm();
                    return true;
                }));
        return header;
    }

    private Flow buildSortBar() {
        Flow bar = Flow.row();
        bar.widthRel(1f)
            .height(16)
            .marginBottom(4)
            .name("content/sort_bar");
        bar.child(
            IKey.str("Sort:")
                .color(0x777777ff)
                .asWidget()
                .width(30)
                .heightRel(1f));

        sortPrioBtn = buildSortButton("Priority", SortMode.PRIORITY);
        sortTimeBtn = buildSortButton("Time", SortMode.TIME_DESC);
        bar.child(sortPrioBtn);
        bar.child(sortTimeBtn);
        bar.child(
            IKey.str("  ")
                .asWidget()
                .width(10)
                .heightRel(1f));

        filterDoneBtn = buildFilterDoneButton();
        bar.child(filterDoneBtn);
        filterMineBtn = buildFilterMineButton();
        bar.child(filterMineBtn);
        return bar;
    }

    private StyledButtonWidget buildFilterDoneButton() {
        int bg = showCompleted ? FILTER_ON : FILTER_OFF;
        StyledButtonWidget btn = GuiStyle.button(
            showCompleted ? "Done: ON" : "Done: OFF",
            bg,
            BTN_HOVER,
            GuiStyle.BUTTON_PRESSED,
            showCompleted ? 0x88FF88ff : 0x777777ff,
            0.85f);
        btn.width(52)
            .height(14)
            .topRel(0.5f)
            .anchorTop(0.5f)
            .marginLeft(3)
            .name("sort_bar/filter_done")
            .onMousePressed(b -> {
                showCompleted = !showCompleted;
                refreshFilterButton();
                refreshTaskList();
                return true;
            });
        return btn;
    }

    private void refreshFilterButton() {
        if (filterDoneBtn == null) return;
        updateFilterButton(
            filterDoneBtn,
            showCompleted ? "Done: ON" : "Done: OFF",
            showCompleted,
            showCompleted ? 0x88FF88ff : 0x777777ff);
        if (filterMineBtn != null) {
            updateFilterButton(
                filterMineBtn,
                showMineOnly ? "Mine: ON" : "Mine: OFF",
                showMineOnly,
                showMineOnly ? 0x99CCFFff : 0x777777ff);
        }
    }

    private StyledButtonWidget buildFilterMineButton() {
        int bg = showMineOnly ? FILTER_ON : FILTER_OFF;
        StyledButtonWidget btn = GuiStyle.button(
            showMineOnly ? "Mine: ON" : "Mine: OFF",
            bg,
            BTN_HOVER,
            GuiStyle.BUTTON_PRESSED,
            showMineOnly ? 0x99CCFFff : 0x777777ff,
            0.85f);
        btn.width(54)
            .height(14)
            .topRel(0.5f)
            .anchorTop(0.5f)
            .marginLeft(3)
            .name("sort_bar/filter_mine")
            .onMousePressed(b -> {
                showMineOnly = !showMineOnly;
                refreshFilterButton();
                refreshTaskList();
                return true;
            });
        return btn;
    }

    private void updateFilterButton(StyledButtonWidget button, String text, boolean active, int textColor) {
        int bg = active ? FILTER_ON : FILTER_OFF;
        button.setBackgrounds(
            ShaderDrawable.roundedRect(3f, bg),
            ShaderDrawable.roundedRect(3f, BTN_HOVER),
            ShaderDrawable.roundedRect(3f, GuiStyle.BUTTON_PRESSED));
        button.overlay(
            IKey.str(text)
                .color(textColor)
                .shadow(active)
                .scale(0.85f));
    }

    private StyledButtonWidget buildSortButton(String label, SortMode mode) {
        boolean active = (currentSort == mode) || (mode == SortMode.TIME_DESC && currentSort == SortMode.TIME_ASC);
        String arrow = "";
        if (mode == SortMode.TIME_DESC && (currentSort == SortMode.TIME_DESC || currentSort == SortMode.TIME_ASC)) {
            arrow = currentSort == SortMode.TIME_ASC ? " ^" : " v";
        }
        int bg = active ? SORT_ACTIVE : SORT_INACTIVE;
        StyledButtonWidget btn = GuiStyle
            .button(label + arrow, bg, BTN_HOVER, GuiStyle.BUTTON_PRESSED, active ? 0xFFFFFFff : 0x999999ff, 0.85f);
        btn.width(48)
            .height(14)
            .topRel(0.5f)
            .anchorTop(0.5f)
            .marginLeft(3)
            .name("sort_bar/sort_" + label.toLowerCase())
            .onMousePressed(b -> {
                if (mode == SortMode.PRIORITY) {
                    currentSort = SortMode.PRIORITY;
                } else {
                    currentSort = (currentSort == SortMode.TIME_DESC) ? SortMode.TIME_ASC : SortMode.TIME_DESC;
                }
                refreshTaskList();
                refreshSortButtons();
                return true;
            });
        return btn;
    }

    private void refreshSortButtons() {
        if (sortPrioBtn == null || sortTimeBtn == null) return;
        boolean prioActive = currentSort == SortMode.PRIORITY;
        boolean timeActive = currentSort == SortMode.TIME_DESC || currentSort == SortMode.TIME_ASC;
        String timeArrow = currentSort == SortMode.TIME_ASC ? " ^" : (timeActive ? " v" : "");
        updateSortButton(sortPrioBtn, "Priority", prioActive);
        updateSortButton(sortTimeBtn, "Time" + timeArrow, timeActive);
    }

    private void updateSortButton(StyledButtonWidget button, String text, boolean active) {
        int bg = active ? SORT_ACTIVE : SORT_INACTIVE;
        button.setBackgrounds(
            ShaderDrawable.roundedRect(3f, bg),
            ShaderDrawable.roundedRect(3f, BTN_HOVER),
            ShaderDrawable.roundedRect(3f, GuiStyle.BUTTON_PRESSED));
        button.overlay(
            IKey.str(text)
                .color(active ? 0xFFFFFFff : 0x999999ff)
                .shadow(active)
                .scale(0.85f));
    }

    // --- Panel open ---

    private void openTaskForm() {
        if (formHandler == null) {
            formHandler = IPanelHandler.simple(
                this,
                (ModularPanel parentPanel, EntityPlayer player) -> new TaskFormPanel(this::refreshTaskList),
                true);
        }
        if (!formHandler.isPanelOpen()) {
            formHandler.openPanel();
        }
    }

    private Task pendingDetailTask;

    private void openTaskDetail(Task task) {
        this.pendingDetailTask = task;
        if (detailHandler == null) {
            lastDetailTaskId = task.getId();
            detailHandler = IPanelHandler.simple(
                this,
                (ModularPanel parentPanel,
                    EntityPlayer player) -> new TaskDetailPanel(pendingDetailTask, this::refreshTaskList),
                true);
        } else {
            if (detailHandler.isPanelOpen()) return;
            if (!task.getId()
                .equals(lastDetailTaskId)) {
                detailHandler.deleteCachedPanel();
                lastDetailTaskId = task.getId();
            }
        }
        detailHandler.openPanel();
    }

    private void openTeamForm() {
        if (teamFormHandler == null) {
            teamFormHandler = IPanelHandler
                .simple(this, (ModularPanel parentPanel, EntityPlayer player) -> new TeamFormPanel(() -> {
                    NetTeamSync.requestSync();
                    refreshTeamList();
                }), true);
        } else if (!teamFormHandler.isPanelOpen()) {
            teamFormHandler.deleteCachedPanel();
        }
        teamFormHandler.openPanel();
    }

    private void openTagForm() {
        if (tagFormHandler == null) {
            tagFormHandler = IPanelHandler
                .simple(this, (ModularPanel parentPanel, EntityPlayer player) -> new TagFormPanel(() -> {
                    NetTagSync.requestSync();
                    refreshTagList();
                }), true);
        } else if (!tagFormHandler.isPanelOpen()) {
            tagFormHandler.deleteCachedPanel();
        }
        tagFormHandler.openPanel();
    }

    private void openTeamDetail(NBTTagCompound team) {
        pendingTeamDetail = team;
        if (teamDetailHandler == null) {
            teamDetailHandler = IPanelHandler.simple(
                this,
                (ModularPanel parentPanel, EntityPlayer player) -> new TeamDetailPanel(pendingTeamDetail, () -> {
                    NetTeamSync.requestSync();
                    refreshTeamList();
                }),
                true);
        } else {
            if (teamDetailHandler.isPanelOpen()) return;
            teamDetailHandler.deleteCachedPanel();
        }
        teamDetailHandler.openPanel();
    }

    // --- Team / tag list populate ---

    private void populateTeamList() {
        if (teamListWidget == null) return;
        List<NBTTagCompound> teams = TaskClientStore.INSTANCE.getTeamList();
        teams.sort(Comparator.comparing(t -> t.getString("name"), String.CASE_INSENSITIVE_ORDER));
        if (teams.isEmpty()) {
            teamListWidget.child(
                IKey.str("No teams yet. Click '+ New Team' to create one.")
                    .color(0x888888ff)
                    .asWidget()
                    .widthRel(1f)
                    .height(30)
                    .marginTop(20));
            return;
        }
        for (NBTTagCompound team : teams) {
            teamListWidget.child(buildTeamItem(team));
        }
    }

    private void populateTagList() {
        if (tagListWidget == null) return;
        List<NBTTagCompound> tags = TaskClientStore.INSTANCE.getTagList();
        tags.sort(Comparator.comparing(t -> t.getString("name"), String.CASE_INSENSITIVE_ORDER));
        if (tags.isEmpty()) {
            tagListWidget.child(
                IKey.str("No tags yet. Click '+ New Tag' to create one.")
                    .color(0x888888ff)
                    .asWidget()
                    .widthRel(1f)
                    .height(30)
                    .marginTop(20));
            return;
        }
        populateTagCategory("Public", "PUBLIC", tags);
        populateTagCategory("Private", "PRIVATE", tags);
        populateTagCategory("Team", "TEAM", tags);
    }

    private void populateTagCategory(String title, String scope, List<NBTTagCompound> allTags) {
        tagListWidget.child(
            IKey.str(title)
                .color(GuiStyle.ACCENT)
                .shadow(true)
                .asWidget()
                .widthRel(1f)
                .height(14)
                .marginTop(6)
                .name("tag_list/category_" + scope));

        List<NBTTagCompound> scoped = new ArrayList<>();
        for (NBTTagCompound tag : allTags) {
            if (scope.equals(emptyAs(tag.getString("scope"), "PUBLIC"))) scoped.add(tag);
        }
        if (scoped.isEmpty()) {
            tagListWidget.child(
                IKey.str("暂无")
                    .color(0x666666ff)
                    .scale(0.85f)
                    .asWidget()
                    .widthRel(1f)
                    .height(16)
                    .marginTop(1));
            return;
        }
        addTagRows(scoped);
    }

    private void addTagRows(List<NBTTagCompound> tags) {
        Flow row = null;
        int col = 0;
        int columns = tagColumns();
        for (NBTTagCompound tag : tags) {
            if (col == 0) {
                row = Flow.row();
                row.widthRel(1f)
                    .height(GuiStyle.TAG_CHIP_HEIGHT + 6)
                    .marginTop(3)
                    .name("tag_list/row");
                tagListWidget.child(row);
            }
            StyledButtonWidget item = buildTagItem(tag);
            if (col > 0) item.marginLeft(GuiStyle.TAG_CHIP_GAP);
            row.child(item);
            col = (col + 1) % columns;
        }
    }

    private int tagColumns() {
        int available = tagListWidget == null ? 0
            : tagListWidget.getArea()
                .w();
        if (available <= 0) available = Math.max(160, (int) (getArea().w() * 0.72f));
        int unit = GuiStyle.TAG_CHIP_WIDTH + GuiStyle.TAG_CHIP_GAP;
        return Math.max(1, Math.min(8, (available + GuiStyle.TAG_CHIP_GAP) / unit));
    }

    private StyledButtonWidget buildTeamItem(NBTTagCompound team) {
        String name = team.getString("name");
        String line = name + "   members " + team.getInteger("totalMembers");
        StyledButtonWidget button = GuiStyle
            .button(line, ITEM_BG, ITEM_HOVER, GuiStyle.ITEM_PRESSED, 0xDDDDDDff, 0.85f);
        button.widthRel(1f)
            .height(24)
            .marginTop(2)
            .name("team_list/item_" + safeName(name))
            .onMousePressed(btn -> {
                openTeamDetail(team);
                return true;
            });
        button.tooltip(tip -> {
            tip.textShadow(true);
            tip.add(
                IKey.str(name)
                    .color(0xFFFFFF))
                .newLine();
            tip.add(
                IKey.str("ID: " + team.getString("id"))
                    .color(0x999999))
                .newLine();
            String desc = team.getString("description");
            if (!desc.isEmpty()) {
                tip.spaceLine(2);
                tip.add(
                    IKey.str(desc)
                        .color(0xBBBBBB))
                    .newLine();
            }
        });
        return button;
    }

    private StyledButtonWidget buildTagItem(NBTTagCompound tag) {
        String name = tag.getString("name");
        int bg = GuiStyle.parseColor(tag.getString("colorCode"), GuiStyle.BUTTON_BG);
        String scope = emptyAs(tag.getString("scope"), "PUBLIC");
        int linkedTaskCount = tag.getInteger("linkedTaskCount");
        StyledButtonWidget button = GuiStyle.tagChip(tagListLabel(name, linkedTaskCount), bg);
        button.name("tag_list/item_" + safeName(name));
        button.tooltip(tip -> {
            tip.textShadow(true);
            tip.add(
                IKey.str(name)
                    .color(GuiStyle.readableTextColor(bg)))
                .newLine();
            tip.add(
                IKey.str("Scope: " + scope)
                    .color(0xBBBBBB))
                .newLine();
            tip.add(
                IKey.str("Tasks: " + linkedTaskCount)
                    .color(0xBBBBBB))
                .newLine();
            String desc = tag.getString("description");
            if (!desc.isEmpty()) {
                tip.spaceLine(2);
                tip.add(
                    IKey.str(desc)
                        .color(0xBBBBBB))
                    .newLine();
            }
        });
        return button;
    }

    private void refreshTeamList() {
        if (teamListWidget == null) return;
        teamListWidget.removeAll();
        populateTeamList();
        teamListWidget.scheduleResize();
    }

    private void refreshTagList() {
        if (tagListWidget == null) return;
        tagListWidget.removeAll();
        populateTagList();
        tagListWidget.scheduleResize();
    }

    // --- Task list populate ---

    private void populateTaskList(boolean fromDb) {
        try {
            if (fromDb) {
                reloadCache();
            }
            List<Task> topLevel = new ArrayList<>();
            for (Task t : cachedTasks) {
                if (t.getParentTaskId() == null) topLevel.add(t);
            }
            if (topLevel.isEmpty()) {
                taskListWidget.child(
                    IKey.str("No tasks yet. Click '+ New Task' to create one.")
                        .color(0x888888ff)
                        .asWidget()
                        .widthRel(1f)
                        .height(30)
                        .marginTop(20)
                        .name("task_list/empty_hint"));
            } else {
                sortTasks(topLevel);
                for (Task task : topLevel) {
                    addTaskAndChildren(task, 0);
                }
            }
        } catch (Exception e) {
            taskListWidget.child(
                IKey.str("Failed to load tasks.")
                    .color(0xFF4444ff)
                    .asWidget()
                    .widthRel(1f)
                    .height(30)
                    .marginTop(20)
                    .name("task_list/error_hint"));
        }
    }

    private void addTaskAndChildren(Task task, int depth) {
        taskListWidget.child(buildTaskItem(task, depth));
        if (expandedTasks.contains(task.getId())) {
            List<Task> subs = getCachedSubtasks(task.getId());
            sortTasks(subs);
            for (Task sub : subs) {
                addTaskAndChildren(sub, depth + 1);
            }
        }
    }

    private void toggleExpand(Task task, boolean recursive) {
        String id = task.getId();
        if (expandedTasks.contains(id)) {
            if (recursive) {
                collapseRecursive(id);
            } else {
                expandedTasks.remove(id);
            }
        } else {
            expandedTasks.add(id);
            if (recursive) {
                expandRecursive(id);
            }
        }
        rebuildFromCache();
    }

    private void expandRecursive(String taskId) {
        expandedTasks.add(taskId);
        for (Task sub : getCachedSubtasks(taskId)) {
            expandRecursive(sub.getId());
        }
    }

    private void collapseRecursive(String taskId) {
        expandedTasks.remove(taskId);
        for (Task sub : getCachedSubtasks(taskId)) {
            collapseRecursive(sub.getId());
        }
    }

    private void sortTasks(List<Task> tasks) {
        Comparator<Task> cmp = switch (currentSort) {
            case TIME_ASC -> Comparator.comparing(Task::getCreateTime, Comparator.nullsLast(Comparator.naturalOrder()));
            case TIME_DESC -> Comparator
                .comparing(Task::getCreateTime, Comparator.nullsLast(Comparator.reverseOrder()));
            default -> Comparator.comparing(
                t -> t.getPriority()
                    .ordinal());
        };
        tasks.sort(cmp);
    }

    /** Rebuild UI from cached data only, no DB queries. */
    private void rebuildFromCache() {
        if (taskListWidget == null) return;
        taskListWidget.removeAll();
        populateTaskList(false);
        taskListWidget.scheduleResize();
    }

    /** Reload from DB and rebuild the UI. Called when data changes. */
    public void refreshTaskList() {
        if (taskListWidget == null) return;
        if (detailHandler != null && !detailHandler.isPanelOpen()) {
            detailHandler.deleteCachedPanel();
            lastDetailTaskId = null;
        }
        taskListWidget.removeAll();
        populateTaskList(true);
        taskListWidget.scheduleResize();
    }

    // --- Status icon mapping ---

    private static String getStatusIcon(Task.TaskStatus status) {
        return switch (status) {
            case Completed -> "[/] ";
            case Closed -> "[-] ";
            case Claimed -> "[+] ";
            case InProgress -> "[~] ";
            case Blocked -> "[!] ";
            case Postponed -> "[>] ";
            case Canceled, Rejected -> "[x] ";
            case InTrialRun -> "[?] ";
            default -> "[ ] ";
        };
    }

    private static int getStatusIconColor(Task.TaskStatus status) {
        return switch (status) {
            case Completed -> 0x66CC66;
            case Closed -> 0x666666;
            case Claimed -> 0x66CCFF;
            case InProgress -> 0x44AAFF;
            case Blocked -> 0xFF8844;
            case Postponed -> 0xAAAA44;
            case Canceled, Rejected -> 0xCC4444;
            case InTrialRun -> 0x88CCAA;
            default -> 0xAAAAAA;
        };
    }

    // --- Task item builder ---

    private StyledButtonWidget buildTaskItem(Task task, int depth) {
        Task.TaskStatus status = task.getStatus();
        String prioLabel = formatPriority(task.getPriority());
        int prioColor = getPriorityColor(task.getPriority());
        String statusLabel = statusDisplay(task);
        int statusColor = getStatusColor(status);
        int subCount = getCachedSubCount(task.getId());
        boolean expanded = expandedTasks.contains(task.getId());
        int indent = depth * SUB_INDENT;
        String statusIcon = getStatusIcon(status);
        int iconColor = getStatusIconColor(status);
        List<NBTTagCompound> taskTags = TaskClientStore.INSTANCE.getTaskTags(task.getId());

        FontRenderer fr = Minecraft.getMinecraft().fontRenderer;
        int arrowWidth = subCount > 0 ? fr.getStringWidth(expanded ? "v " : "> ") : 0;

        IDrawable itemOverlay = (context, x, y, width, height, widgetTheme) -> {
            GL11.glPushMatrix();
            GL11.glEnable(GL11.GL_TEXTURE_2D);
            GL11.glEnable(GL11.GL_BLEND);

            int textY = y + (height - fr.FONT_HEIGHT) / 2;
            int pad = 6;
            int cx = x + pad + indent;

            if (subCount > 0) {
                String arrow = expanded ? "v " : "> ";
                fr.drawStringWithShadow(arrow, cx, textY, 0x7799AA);
                cx += fr.getStringWidth(arrow);
            }

            String prioText = "[" + prioLabel + "] ";
            fr.drawStringWithShadow(prioText, cx, textY, prioColor);
            cx += fr.getStringWidth(prioText);

            fr.drawStringWithShadow(statusIcon, cx, textY, iconColor);
            cx += fr.getStringWidth(statusIcon);

            int rightW = fr.getStringWidth(statusLabel);
            if (subCount > 0) rightW += fr.getStringWidth(" (" + subCount + ")") + 4;
            int availableMainW = width - (cx - x) - pad - rightW - 12;
            int visibleTags = Math.min(2, taskTags.size());
            while (visibleTags > 0 && taskTagAreaWidth(fr, taskTags.size(), visibleTags) > availableMainW - 40) {
                visibleTags--;
            }
            int tagAreaW = taskTagAreaWidth(fr, taskTags.size(), visibleTags);
            int maxTitleW = availableMainW - tagAreaW - (visibleTags > 0 ? 8 : 0);

            String title = fitText(fr, task.getTitle(), maxTitleW);
            boolean done = status == Task.TaskStatus.Completed || status == Task.TaskStatus.Closed;
            fr.drawStringWithShadow(title, cx, textY, done ? 0x888888 : 0xDDDDDD);
            cx += fr.getStringWidth(title);

            if (visibleTags > 0) {
                cx += 6;
                int textCenterY = textY + fr.FONT_HEIGHT / 2;
                for (int i = 0; i < visibleTags; i++) {
                    drawTaskTagChip(fr, taskTags.get(i), cx, textCenterY);
                    cx += GuiStyle.TAG_CHIP_WIDTH + GuiStyle.TAG_CHIP_GAP;
                }
                if (taskTags.size() > visibleTags) {
                    fr.drawStringWithShadow("+" + (taskTags.size() - visibleTags), cx, textY, 0xAAAAAA);
                }
            }

            int rx = x + width - pad;
            if (subCount > 0) {
                String subStr = "(" + subCount + ")";
                rx -= fr.getStringWidth(subStr);
                fr.drawStringWithShadow(subStr, rx, textY, 0x7799AA);
                rx -= 4;
            }
            rx -= fr.getStringWidth(statusLabel);
            fr.drawStringWithShadow(statusLabel, rx, textY, statusColor);

            GL11.glPopMatrix();
        };

        int itemBg = depth > 0 ? blendColor(ITEM_BG, depth) : ITEM_BG;
        int itemHover = depth > 0 ? blendColor(ITEM_HOVER, depth) : ITEM_HOVER;
        int itemPressed = depth > 0 ? blendColor(GuiStyle.ITEM_PRESSED, depth) : GuiStyle.ITEM_PRESSED;
        String shortTitle = task.getTitle()
            .length() > 12 ? task.getTitle()
                .substring(0, 12) : task.getTitle();
        StyledButtonWidget btn = new StyledButtonWidget(
            ShaderDrawable.roundedRect(4f, itemBg),
            ShaderDrawable.roundedRect(4f, itemHover),
            ShaderDrawable.roundedRect(4f, itemPressed));
        btn.widthRel(1f)
            .height(24)
            .marginTop(1)
            .name("task_list/item_" + shortTitle + "_d" + depth)
            .overlay(itemOverlay)
            .onMousePressed(b -> {
                if (subCount > 0) {
                    int mx = btn.getContext()
                        .getAbsMouseX();
                    int arrowStartX = btn.getArea().x + 6 + indent;
                    int arrowEndX = arrowStartX + arrowWidth;
                    if (mx >= arrowStartX && mx < arrowEndX) {
                        boolean shift = Keyboard.isKeyDown(Keyboard.KEY_LSHIFT)
                            || Keyboard.isKeyDown(Keyboard.KEY_RSHIFT);
                        toggleExpand(task, shift);
                        return true;
                    }
                }
                openTaskDetail(task);
                return true;
            });

        btn.tooltip(tip -> {
            tip.textShadow(true);
            tip.add(
                IKey.str(task.getTitle())
                    .color(0xFFFFFF))
                .newLine();
            tip.spaceLine(2);
            String desc = task.getDescription();
            if (desc != null && !desc.isEmpty()) {
                tip.add(
                    IKey.str(desc)
                        .color(0xBBBBBB))
                    .newLine();
                tip.spaceLine(2);
            }
            tip.add(
                IKey.str("Priority: ")
                    .color(0x999999))
                .add(
                    IKey.str(
                        task.getPriority()
                            .name())
                        .color(prioColor))
                .newLine();
            tip.add(
                IKey.str("Status:   ")
                    .color(0x999999))
                .add(
                    IKey.str(statusLabel)
                        .color(statusColor))
                .newLine();
            tip.add(
                IKey.str("Importance: ")
                    .color(0x999999))
                .add(
                    IKey.str(
                        task.getImportance()
                            .name())
                        .color(0xBBBBBB))
                .newLine();
            tip.add(
                IKey.str("Urgency:    ")
                    .color(0x999999))
                .add(
                    IKey.str(
                        task.getUrgency()
                            .name())
                        .color(0xBBBBBB))
                .newLine();
            if (subCount > 0) {
                tip.add(
                    IKey.str("Subtasks:   ")
                        .color(0x999999))
                    .add(
                        IKey.str(String.valueOf(subCount))
                            .color(0x7799AA))
                    .newLine();
            }
            if (task.getCreateTime() != null) {
                tip.spaceLine(2);
                tip.add(
                    IKey.str(
                        "Created: " + task.getCreateTime()
                            .format(TIME_FMT))
                        .color(0x777777))
                    .newLine();
            }
        });
        return btn;
    }

    // --- Utilities ---

    private static int blendColor(int base, int depth) {
        return GuiStyle.shiftBackground(base, depth * 8, depth * 8, depth * 12);
    }

    private static int lighten(int color) {
        return GuiStyle.lightenBackground(color);
    }

    private static int darken(int color) {
        return GuiStyle.darkenBackground(color);
    }

    private static String emptyAs(String value, String fallback) {
        return value == null || value.isEmpty() ? fallback : value;
    }

    private static String safeName(String name) {
        if (name == null || name.isEmpty()) return "empty";
        String cleaned = name.replaceAll("[^a-zA-Z0-9_\\-]", "_");
        return cleaned.length() > 18 ? cleaned.substring(0, 18) : cleaned;
    }

    private static String tagListLabel(String name, int linkedTaskCount) {
        String count = " " + linkedTaskCount;
        String safeName = name == null ? "" : name;
        int maxNameLen = Math.max(1, 10 - count.length());
        if (safeName.length() > maxNameLen) safeName = safeName.substring(0, Math.max(1, maxNameLen - 2)) + "..";
        return safeName + count;
    }

    private static String fitText(FontRenderer fr, String text, int maxWidth) {
        if (text == null || maxWidth <= 0) return "";
        if (fr.getStringWidth(text) <= maxWidth) return text;
        if (maxWidth <= fr.getStringWidth("..")) return "";
        String result = text;
        while (fr.getStringWidth(result + "..") > maxWidth && result.length() > 1) {
            result = result.substring(0, result.length() - 1);
        }
        return result + "..";
    }

    private static void drawTaskTagChip(FontRenderer fr, NBTTagCompound tag, int x, int textCenterY) {
        int bg = GuiStyle.parseColor(tag.getString("colorCode"), GuiStyle.BUTTON_BG);
        int chipW = GuiStyle.TAG_CHIP_WIDTH - 4;
        int chipH = GuiStyle.TAG_CHIP_HEIGHT - 2;
        int chipX = x + 2;
        int chipY = textCenterY - chipH / 2;
        GuiStyle.tagChipDrawable(bg)
            .draw(null, chipX, chipY, chipW, chipH, null);
        String label = fitText(fr, GuiStyle.shortTagLabel(tag.getString("name")), chipW - 6);
        int textX = chipX + (chipW - fr.getStringWidth(label)) / 2;
        int textY = textCenterY - fr.FONT_HEIGHT / 2;
        fr.drawString(label, textX, textY, GuiStyle.readableTextColor(bg));
    }

    private static int taskTagAreaWidth(FontRenderer fr, int totalTags, int visibleTags) {
        if (visibleTags <= 0) return 0;
        int width = visibleTags * GuiStyle.TAG_CHIP_WIDTH + Math.max(0, visibleTags - 1) * GuiStyle.TAG_CHIP_GAP;
        if (totalTags > visibleTags)
            width += GuiStyle.TAG_CHIP_GAP + fr.getStringWidth("+" + (totalTags - visibleTags));
        return width;
    }

    private String statusDisplay(Task task) {
        String label = task.getStatus()
            .name();
        List<NBTTagCompound> assignees = TaskClientStore.INSTANCE.getTaskAssignees(task.getId());
        if (assignees.isEmpty()) {
            return task.getAssigneeId() == null ? label : label + " - " + formatPlayerName(task.getAssigneeId());
        }
        if (assignees.size() == 1) return label + " - " + assigneeName(assignees.get(0));
        return label + " - " + assigneeName(assignees.get(0)) + " +" + (assignees.size() - 1);
    }

    private String assigneeName(NBTTagCompound assignee) {
        String display = assignee.getString("displayName");
        if (!display.isEmpty()) return display;
        String name = assignee.getString("playerName");
        if (!name.isEmpty()) return name;
        String id = assignee.getString("playerId");
        return id.isEmpty() ? "Unknown" : formatPlayerName(UUID.fromString(id));
    }

    private String formatPlayerName(UUID playerId) {
        UUID self = currentPlayerId();
        if (playerId != null && playerId.equals(self)
            && Minecraft.getMinecraft() != null
            && Minecraft.getMinecraft().thePlayer != null) {
            return Minecraft.getMinecraft().thePlayer.getCommandSenderName();
        }
        return "Unknown";
    }

    private UUID currentPlayerId() {
        if (Minecraft.getMinecraft() == null || Minecraft.getMinecraft().thePlayer == null) return null;
        return Minecraft.getMinecraft().thePlayer.getUniqueID();
    }

    private boolean isAssignedTo(Task task, UUID playerId) {
        if (task.getAssigneeId() != null && task.getAssigneeId()
            .equals(playerId)) return true;
        for (NBTTagCompound assignee : TaskClientStore.INSTANCE.getTaskAssignees(task.getId())) {
            if (playerId.toString()
                .equals(assignee.getString("playerId"))) return true;
        }
        return false;
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

    private int getStatusColor(Task.TaskStatus status) {
        return switch (status) {
            case InProgress -> 0x44AAFF;
            case Claimed -> 0x66CCFF;
            case Completed -> 0x66CC66;
            case Closed -> 0x666666;
            case Canceled, Rejected -> 0xCC4444;
            case Blocked -> 0xFF8844;
            case Postponed -> 0xAAAA44;
            case Defect -> 0xFF6644;
            case InTrialRun -> 0x88CCAA;
            default -> 0xAAAAAA;
        };
    }

    private String formatPriority(Task.Priority priority) {
        return switch (priority) {
            case CRITICAL -> "CRIT";
            case UNDEFINED -> "---";
            default -> priority.name();
        };
    }
}
