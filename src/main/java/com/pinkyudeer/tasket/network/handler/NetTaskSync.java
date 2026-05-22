package com.pinkyudeer.tasket.network.handler;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;

import com.pinkyudeer.tasket.client.TaskClientStore;
import com.pinkyudeer.tasket.core.ServerTaskScheduler;
import com.pinkyudeer.tasket.db.AsyncSqlExecutor;
import com.pinkyudeer.tasket.db.SQLiteManager;
import com.pinkyudeer.tasket.network.PacketIds;
import com.pinkyudeer.tasket.network.PacketSender;
import com.pinkyudeer.tasket.network.PacketTypeRegistry;
import com.pinkyudeer.tasket.task.dao.PlayerDao;
import com.pinkyudeer.tasket.task.entity.Player;
import com.pinkyudeer.tasket.task.entity.Tag;
import com.pinkyudeer.tasket.task.entity.Task;
import com.pinkyudeer.tasket.task.service.TagService;
import com.pinkyudeer.tasket.task.service.TaskService;

public final class NetTaskSync {

    private static final int DEFAULT_PAGE_SIZE = 20;

    private NetTaskSync() {}

    public static void registerHandler() {
        PacketTypeRegistry.INSTANCE.registerServerHandler(PacketIds.TASK_SYNC, NetTaskSync::onServer);
        PacketTypeRegistry.INSTANCE.registerClientHandler(PacketIds.TASK_SYNC, NetTaskSync::onClient);
    }

    public static void requestSync() {
        requestSync(0, DEFAULT_PAGE_SIZE, "priority", false, false);
    }

    public static void requestSync(int page, int pageSize, String sortMode, boolean includeCompleted,
        boolean mineOnly) {
        TaskClientStore.INSTANCE.markTaskLoading();
        NBTTagCompound payload = new NBTTagCompound();
        payload.setTag("versions", TaskClientStore.INSTANCE.getTaskVersionList());
        payload.setLong("rev", TaskClientStore.INSTANCE.getSyncGlobalRev());
        payload.setInteger("page", page);
        payload.setInteger("pageSize", pageSize);
        payload.setString("sortMode", sortMode == null ? "priority" : sortMode);
        payload.setBoolean("includeCompleted", includeCompleted);
        payload.setBoolean("mineOnly", mineOnly);
        payload.setLong("lastKnownVersion", TaskClientStore.INSTANCE.getTaskGlobalVersion());
        PacketSender.INSTANCE.sendToServer(PacketIds.TASK_SYNC, payload);
    }

    public static void sendSync(EntityPlayerMP player) {
        NetMainSync.bumpRevision();
        sendPageAsync(player, new HashMap<>(), 0, DEFAULT_PAGE_SIZE, "priority", true, false, 0L);
    }

    static void sendDelta(EntityPlayerMP player, Map<String, String> clientVersions, Map<UUID, Player> playerCache) {
        List<Task> visibleTasks = TaskService.getVisibleTasks(player.getUniqueID(), isOp(player));

        Set<String> visibleIds = new HashSet<>();
        List<Task> updated = new ArrayList<>();
        for (Task task : visibleTasks) {
            if (task == null || task.getId() == null) continue;
            visibleIds.add(task.getId());
            String serverVersion = String.valueOf(task.getVersion() == null ? 0 : task.getVersion());
            String clientVersion = clientVersions.get(task.getId());
            if (!serverVersion.equals(clientVersion)) {
                updated.add(task);
            }
        }

        NBTTagList deletedList = new NBTTagList();
        for (Map.Entry<String, String> entry : clientVersions.entrySet()) {
            if (!visibleIds.contains(entry.getKey())) {
                NBTTagCompound del = new NBTTagCompound();
                del.setString("id", entry.getKey());
                deletedList.appendTag(del);
            }
        }

        Map<String, List<UUID>> assigneesByTask = TaskService.collectAssigneesByTask(updated);
        List<String> taskIds = new ArrayList<>(updated.size());
        for (Task task : updated) taskIds.add(task.getId());
        Map<String, List<Tag>> tagsByTask = TagService.collectTagsByTask(taskIds);

        NBTTagList updatedList = new NBTTagList();
        for (Task task : updated) {
            updatedList.appendTag(
                writeTask(
                    task,
                    assigneesByTask.getOrDefault(task.getId(), Collections.emptyList()),
                    tagsByTask.getOrDefault(task.getId(), Collections.emptyList()),
                    playerCache));
        }

        NBTTagCompound payload = new NBTTagCompound();
        payload.setTag("updated", updatedList);
        payload.setTag("deleted", deletedList);
        payload.setLong("rev", NetMainSync.getRevision());
        PacketSender.INSTANCE.sendToPlayers(PacketIds.TASK_SYNC, payload, player);
    }

    static void sendEmptyDelta(EntityPlayerMP player) {
        NBTTagCompound payload = new NBTTagCompound();
        payload.setTag("updated", new NBTTagList());
        payload.setTag("deleted", new NBTTagList());
        payload.setLong("rev", NetMainSync.getRevision());
        PacketSender.INSTANCE.sendToPlayers(PacketIds.TASK_SYNC, payload, player);
    }

    private static void onServer(NBTTagCompound payload, EntityPlayerMP sender) {
        if (sender == null) return;
        Map<String, String> clientVersions = parseVersionList(payload.getTagList("versions", 10));
        int page = Math.max(0, payload.getInteger("page"));
        int pageSize = payload.hasKey("pageSize") ? payload.getInteger("pageSize") : DEFAULT_PAGE_SIZE;
        String sortMode = payload.getString("sortMode");
        boolean includeCompleted = payload.getBoolean("includeCompleted");
        boolean mineOnly = payload.getBoolean("mineOnly");
        long lastKnownVersion = payload.getLong("lastKnownVersion");
        sendPageAsync(sender, clientVersions, page, pageSize, sortMode, includeCompleted, mineOnly, lastKnownVersion);
    }

    private static void sendPageAsync(EntityPlayerMP sender, Map<String, String> clientVersions, int page, int pageSize,
        String sortMode, boolean includeCompleted, boolean mineOnly, long lastKnownVersion) {
        UUID viewerId = sender.getUniqueID();
        boolean op = isOp(sender);
        String key = "task_page:" + viewerId
            + ':'
            + op
            + ':'
            + page
            + ':'
            + pageSize
            + ':'
            + sortMode
            + ':'
            + includeCompleted
            + ':'
            + mineOnly
            + ':'
            + lastKnownVersion
            + ':'
            + NetMainSync.getRevision()
            + ':'
            + clientVersions.hashCode();
        AsyncSqlExecutor.INSTANCE.submit(
            key,
            new HashSet<>(
                java.util.Arrays.asList("tasks", "task_interactions", "tag_links", "tags", "entity_events", "players")),
            () -> buildPagePayload(
                viewerId,
                op,
                clientVersions,
                page,
                pageSize,
                sortMode,
                includeCompleted,
                mineOnly,
                lastKnownVersion,
                new HashMap<>()))
            .whenComplete((response, error) -> ServerTaskScheduler.INSTANCE.schedule(() -> {
                if (error != null) {
                    NetError.send(sender, NetError.SERVER_ERROR, "task sync failed");
                    return;
                }
                PacketSender.INSTANCE.sendToPlayers(PacketIds.TASK_SYNC, response, sender);
            }, false));
    }

    private static void onClient(NBTTagCompound payload) {
        if (payload.hasKey("page")) {
            TaskClientStore.INSTANCE.acceptTaskPage(
                payload.getTagList("updated", 10),
                payload.getTagList("deleted", 10),
                payload.getInteger("page"),
                payload.getInteger("totalCount"),
                payload.getLong("currentVersion"),
                payload.getString("mode"));
        } else {
            TaskClientStore.INSTANCE
                .acceptTaskDelta(payload.getTagList("updated", 10), payload.getTagList("deleted", 10));
        }
        TaskClientStore.INSTANCE.updateSyncRevision(payload.getLong("rev"));
    }

    private static NBTTagCompound buildPagePayload(UUID viewerId, boolean isOp, Map<String, String> clientVersions,
        int page, int pageSize, String sortMode, boolean includeCompleted, boolean mineOnly, long lastKnownVersion,
        Map<UUID, Player> playerCache) {
        TaskService.PagedTasks pageData = TaskService
            .getVisibleTasksPage(viewerId, isOp, page, pageSize, sortMode, includeCompleted, mineOnly);
        List<Task> visibleTasks = pageData.tasks();
        Set<String> pageIds = new HashSet<>();
        List<Task> changed = new ArrayList<>();
        for (Task task : visibleTasks) {
            if (task == null || task.getId() == null) continue;
            pageIds.add(task.getId());
            String serverVersion = String.valueOf(task.getVersion() == null ? 0 : task.getVersion());
            String clientVersion = clientVersions.get(task.getId());
            if (!serverVersion.equals(clientVersion)) changed.add(task);
        }

        long currentVersion = currentEntityVersion("tasks");
        int eventCount = countEventsSince("tasks", lastKnownVersion);
        boolean preferDiff = lastKnownVersion > 0 && eventCount >= 0 && eventCount < 100;

        NBTTagList fullList = writeTasks(visibleTasks, playerCache);
        NBTTagList diffList = writeTasks(changed, playerCache);
        NBTTagList deletedList = new NBTTagList();
        if (preferDiff) {
            Set<String> visibleClientIds = TaskService
                .getVisibleTaskIds(viewerId, isOp, clientVersions.keySet(), includeCompleted, mineOnly);
            for (Map.Entry<String, String> entry : clientVersions.entrySet()) {
                if (pageIds.contains(entry.getKey())) continue;
                if (!visibleClientIds.contains(entry.getKey())) {
                    NBTTagCompound del = new NBTTagCompound();
                    del.setString("id", entry.getKey());
                    deletedList.appendTag(del);
                }
            }
        }

        boolean useDiff = preferDiff && payloadSize(diffList, deletedList) < payloadSize(fullList, new NBTTagList());
        NBTTagCompound payload = new NBTTagCompound();
        payload.setString("mode", useDiff ? "diff" : "full");
        payload.setTag("updated", useDiff ? diffList : fullList);
        payload.setTag("deleted", useDiff ? deletedList : new NBTTagList());
        payload.setInteger("page", pageData.page());
        payload.setInteger("pageSize", pageData.pageSize());
        payload.setInteger("totalCount", pageData.totalCount());
        payload.setLong("currentVersion", currentVersion);
        payload.setLong("rev", NetMainSync.getRevision());
        return payload;
    }

    private static NBTTagList writeTasks(List<Task> tasks, Map<UUID, Player> playerCache) {
        Map<String, List<UUID>> assigneesByTask = TaskService.collectAssigneesByTask(tasks);
        List<String> taskIds = new ArrayList<>(tasks.size());
        for (Task task : tasks) taskIds.add(task.getId());
        Map<String, List<Tag>> tagsByTask = TagService.collectTagsByTask(taskIds);

        NBTTagList updatedList = new NBTTagList();
        for (Task task : tasks) {
            updatedList.appendTag(
                writeTask(
                    task,
                    assigneesByTask.getOrDefault(task.getId(), Collections.emptyList()),
                    tagsByTask.getOrDefault(task.getId(), Collections.emptyList()),
                    playerCache));
        }
        return updatedList;
    }

    static long currentEntityVersion(String entityType) {
        Long value = SQLiteManager.query(
            "SELECT COALESCE(MAX(COALESCE(new_version, old_version, 0)), 0) AS v FROM entity_events WHERE entity_type = ?",
            rs -> rs.next() ? rs.getLong("v") : 0L,
            entityType);
        return value == null ? 0L : value;
    }

    static int countEventsSince(String entityType, long version) {
        Integer count = SQLiteManager.query(
            "SELECT COUNT(*) AS c FROM entity_events WHERE entity_type = ? AND COALESCE(new_version, old_version, 0) > ?",
            rs -> rs.next() ? rs.getInt("c") : 0,
            entityType,
            version);
        return count == null ? -1 : count;
    }

    private static int payloadSize(NBTTagList updated, NBTTagList deleted) {
        return updated.toString()
            .length()
            + deleted.toString()
                .length();
    }

    private static NBTTagCompound writeTask(Task task, List<UUID> assigneeIds, List<Tag> taskTags,
        Map<UUID, Player> playerCache) {
        NBTTagCompound tag = new NBTTagCompound();
        tag.setString("id", task.getId());
        tag.setString("_v", String.valueOf(task.getVersion() == null ? 0 : task.getVersion()));
        tag.setString("title", safe(task.getTitle()));
        tag.setString("description", safe(task.getDescription()));
        tag.setInteger("version", task.getVersion() == null ? 0 : task.getVersion());
        tag.setString(
            "status",
            task.getStatus() == null ? ""
                : task.getStatus()
                    .name());
        tag.setString(
            "priority",
            task.getPriority() == null ? ""
                : task.getPriority()
                    .name());
        tag.setString(
            "importance",
            task.getImportance() == null ? ""
                : task.getImportance()
                    .name());
        tag.setString(
            "urgency",
            task.getUrgency() == null ? ""
                : task.getUrgency()
                    .name());
        tag.setString(
            "visibility",
            task.getVisibility() == null ? ""
                : task.getVisibility()
                    .name());
        tag.setString("parentTaskId", safe(task.getParentTaskId()));
        tag.setString(
            "teamId",
            task.getTeamId() == null ? ""
                : task.getTeamId()
                    .toString());
        tag.setString(
            "creator",
            task.getCreator() == null ? ""
                : task.getCreator()
                    .toString());
        tag.setString(
            "assigneeId",
            task.getAssigneeId() == null ? ""
                : task.getAssigneeId()
                    .toString());
        tag.setInteger("assigneeCount", task.getAssigneeCount() == null ? 0 : task.getAssigneeCount());
        NBTTagList assignees = new NBTTagList();
        for (UUID assigneeId : assigneeIds) {
            NBTTagCompound assignee = new NBTTagCompound();
            assignee.setString("playerId", assigneeId == null ? "" : assigneeId.toString());
            Player player = lookupPlayer(playerCache, assigneeId);
            assignee.setString(
                "playerName",
                player == null || player.getPlayerName() == null ? "" : player.getPlayerName());
            assignee.setString(
                "displayName",
                player == null || player.getDisplayName() == null ? "" : player.getDisplayName());
            assignees.appendTag(assignee);
        }
        tag.setTag("assignees", assignees);
        tag.setString(
            "createTime",
            task.getCreateTime() == null ? ""
                : task.getCreateTime()
                    .toString());
        tag.setString(
            "updateTime",
            task.getUpdateTime() == null ? ""
                : task.getUpdateTime()
                    .toString());
        NBTTagList tags = new NBTTagList();
        for (Tag taskTag : taskTags) {
            NBTTagCompound tagEntry = new NBTTagCompound();
            tagEntry.setString(
                "id",
                taskTag.getId() == null ? ""
                    : taskTag.getId()
                        .toString());
            tagEntry.setString("name", safe(taskTag.getName()));
            tagEntry.setString("description", safe(taskTag.getDescription()));
            tagEntry.setString("colorCode", safe(taskTag.getColorCode()));
            tagEntry.setString("fontColorCode", safe(taskTag.getFontColorCode()));
            tagEntry.setString(
                "scope",
                taskTag.getScope() == null ? ""
                    : taskTag.getScope()
                        .name());
            tags.appendTag(tagEntry);
        }
        tag.setTag("tags", tags);
        return tag;
    }

    static Player lookupPlayer(Map<UUID, Player> cache, UUID playerId) {
        if (playerId == null) return null;
        if (cache.containsKey(playerId)) return cache.get(playerId);
        Player player = PlayerDao.selectById(playerId);
        cache.put(playerId, player);
        return player;
    }

    static Map<String, String> parseVersionList(NBTTagList list) {
        Map<String, String> map = new HashMap<>();
        for (int i = 0; i < list.tagCount(); i++) {
            NBTTagCompound entry = list.getCompoundTagAt(i);
            map.put(entry.getString("id"), entry.getString("v"));
        }
        return map;
    }

    private static String safe(String value) {
        return value == null ? "" : value;
    }

    private static boolean isOp(EntityPlayerMP player) {
        return player.mcServer != null && player.mcServer.getConfigurationManager()
            .func_152596_g(player.getGameProfile());
    }
}
