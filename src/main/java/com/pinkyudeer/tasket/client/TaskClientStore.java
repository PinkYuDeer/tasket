package com.pinkyudeer.tasket.client;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;

import com.pinkyudeer.tasket.task.entity.Task;

public final class TaskClientStore {

    public static final TaskClientStore INSTANCE = new TaskClientStore();

    private final Map<String, NBTTagCompound> tasks = new HashMap<>();
    private final Map<String, NBTTagCompound> teams = new HashMap<>();
    private final Map<String, NBTTagCompound> tags = new HashMap<>();
    private final Map<String, String> taskVersions = new HashMap<>();
    private final Map<String, String> teamVersions = new HashMap<>();
    private final Map<String, String> tagVersions = new HashMap<>();
    private long syncGlobalRev = -1;
    private NBTTagList invites = new NBTTagList();
    private String lastErrorCode = "";
    private String lastErrorMessage = "";
    private long taskRevision;
    private long teamRevision;
    private long tagRevision;
    private long inviteRevision;
    private long errorRevision;
    private boolean taskLoading;
    private boolean teamLoading;
    private boolean tagLoading;
    private long taskRequestTime;
    private long teamRequestTime;
    private long tagRequestTime;
    private int taskTotalCount;
    private int highestTaskPage = -1;
    private long taskGlobalVersion;

    private TaskClientStore() {}

    public synchronized void reset() {
        tasks.clear();
        teams.clear();
        tags.clear();
        taskVersions.clear();
        teamVersions.clear();
        tagVersions.clear();
        syncGlobalRev = -1;
        invites = new NBTTagList();
        lastErrorCode = "";
        lastErrorMessage = "";
        taskLoading = false;
        teamLoading = false;
        tagLoading = false;
        taskTotalCount = 0;
        highestTaskPage = -1;
        taskGlobalVersion = 0;
        taskRevision++;
        teamRevision++;
        tagRevision++;
        inviteRevision++;
        errorRevision++;
    }

    public synchronized void acceptTaskDelta(NBTTagList updated, NBTTagList deleted) {
        taskLoading = false;
        if (updated.tagCount() == 0 && deleted.tagCount() == 0) return;
        applyTaskDelta(updated, deleted);
        taskRevision++;
    }

    public synchronized void acceptTaskPage(NBTTagList updated, NBTTagList deleted, int page, int totalCount,
        long currentVersion, String mode) {
        if (page == 0 && "full".equals(mode)) {
            tasks.clear();
            taskVersions.clear();
            highestTaskPage = -1;
        }
        taskTotalCount = Math.max(0, totalCount);
        highestTaskPage = Math.max(highestTaskPage, page);
        taskGlobalVersion = Math.max(taskGlobalVersion, currentVersion);
        applyTaskDelta(updated, deleted);
        taskLoading = false;
        taskRevision++;
    }

    public synchronized void acceptTeamDelta(NBTTagList updated, NBTTagList deleted) {
        teamLoading = false;
        if (updated.tagCount() == 0 && deleted.tagCount() == 0) return;
        for (int i = 0; i < updated.tagCount(); i++) {
            NBTTagCompound entry = updated.getCompoundTagAt(i);
            if (!entry.hasKey("id")) continue;
            String id = entry.getString("id");
            teams.put(id, (NBTTagCompound) entry.copy());
            teamVersions.put(id, entry.getString("_v"));
        }
        for (int i = 0; i < deleted.tagCount(); i++) {
            String id = deleted.getCompoundTagAt(i)
                .getString("id");
            teams.remove(id);
            teamVersions.remove(id);
        }
        teamRevision++;
    }

    public synchronized void acceptTagDelta(NBTTagList updated, NBTTagList deleted) {
        tagLoading = false;
        if (updated.tagCount() == 0 && deleted.tagCount() == 0) return;
        for (int i = 0; i < updated.tagCount(); i++) {
            NBTTagCompound entry = updated.getCompoundTagAt(i);
            if (!entry.hasKey("id")) continue;
            String id = entry.getString("id");
            tags.put(id, (NBTTagCompound) entry.copy());
            tagVersions.put(id, entry.getString("_v"));
        }
        for (int i = 0; i < deleted.tagCount(); i++) {
            String id = deleted.getCompoundTagAt(i)
                .getString("id");
            tags.remove(id);
            tagVersions.remove(id);
        }
        tagRevision++;
    }

    public synchronized void acceptInviteSync(NBTTagList data) {
        invites = data;
        inviteRevision++;
    }

    public synchronized void acceptError(String code, String message) {
        lastErrorCode = code == null ? "" : code;
        lastErrorMessage = message == null ? "" : message;
        taskLoading = false;
        teamLoading = false;
        tagLoading = false;
        errorRevision++;
    }

    public synchronized void markTaskLoading() {
        taskLoading = true;
        taskRequestTime = System.currentTimeMillis();
        taskRevision++;
    }

    public synchronized void markTeamLoading() {
        teamLoading = true;
        teamRequestTime = System.currentTimeMillis();
        teamRevision++;
    }

    public synchronized void markTagLoading() {
        tagLoading = true;
        tagRequestTime = System.currentTimeMillis();
        tagRevision++;
    }

    public synchronized Map<String, NBTTagCompound> getTasksSnapshot() {
        return new HashMap<>(tasks);
    }

    public synchronized List<Task> getTaskList(boolean includeCompleted) {
        List<Task> result = new ArrayList<>();
        for (NBTTagCompound tag : tasks.values()) {
            Task task = readTask(tag);
            if (includeCompleted || !isDone(task.getStatus())) result.add(task);
        }
        return result;
    }

    public synchronized Task getTask(String taskId) {
        NBTTagCompound tag = tasks.get(taskId);
        return tag == null ? null : readTask(tag);
    }

    public synchronized List<Task> getSubtasks(String parentTaskId) {
        List<Task> result = new ArrayList<>();
        for (NBTTagCompound tag : tasks.values()) {
            if (parentTaskId.equals(tag.getString("parentTaskId"))) {
                result.add(readTask(tag));
            }
        }
        return result;
    }

    public synchronized Map<String, NBTTagCompound> getTeamsSnapshot() {
        return new HashMap<>(teams);
    }

    public synchronized List<NBTTagCompound> getTeamList() {
        List<NBTTagCompound> result = new ArrayList<>();
        for (NBTTagCompound tag : teams.values()) {
            result.add((NBTTagCompound) tag.copy());
        }
        return result;
    }

    public synchronized NBTTagCompound getTeam(String teamId) {
        NBTTagCompound tag = teams.get(teamId);
        return tag == null ? null : (NBTTagCompound) tag.copy();
    }

    public synchronized List<NBTTagCompound> getTagList() {
        List<NBTTagCompound> result = new ArrayList<>();
        for (NBTTagCompound tag : tags.values()) {
            result.add((NBTTagCompound) tag.copy());
        }
        return result;
    }

    public synchronized NBTTagCompound getTag(String tagId) {
        NBTTagCompound tag = tags.get(tagId);
        return tag == null ? null : (NBTTagCompound) tag.copy();
    }

    public synchronized List<NBTTagCompound> getTaskTags(String taskId) {
        List<NBTTagCompound> result = new ArrayList<>();
        NBTTagCompound task = tasks.get(taskId);
        if (task == null) return result;
        NBTTagList list = task.getTagList("tags", 10);
        for (int i = 0; i < list.tagCount(); i++) {
            result.add(
                (NBTTagCompound) list.getCompoundTagAt(i)
                    .copy());
        }
        return result;
    }

    public synchronized List<NBTTagCompound> getTaskAssignees(String taskId) {
        List<NBTTagCompound> result = new ArrayList<>();
        NBTTagCompound task = tasks.get(taskId);
        if (task == null) return result;
        NBTTagList list = task.getTagList("assignees", 10);
        for (int i = 0; i < list.tagCount(); i++) {
            result.add(
                (NBTTagCompound) list.getCompoundTagAt(i)
                    .copy());
        }
        return result;
    }

    public synchronized List<String> getTaskAssigneeIds(String taskId) {
        List<String> result = new ArrayList<>();
        for (NBTTagCompound assignee : getTaskAssignees(taskId)) {
            String id = assignee.getString("playerId");
            if (!id.isEmpty()) result.add(id);
        }
        NBTTagCompound task = tasks.get(taskId);
        if (task != null) {
            String legacyId = task.getString("assigneeId");
            if (!legacyId.isEmpty() && !result.contains(legacyId)) result.add(legacyId);
        }
        return result;
    }

    public synchronized NBTTagList getInvites() {
        return (NBTTagList) invites.copy();
    }

    public synchronized String getLastErrorCode() {
        return lastErrorCode;
    }

    public synchronized String getLastErrorMessage() {
        return lastErrorMessage;
    }

    public synchronized long getTaskRevision() {
        return taskRevision;
    }

    public synchronized long getTeamRevision() {
        return teamRevision;
    }

    public synchronized long getTagRevision() {
        return tagRevision;
    }

    public synchronized long getInviteRevision() {
        return inviteRevision;
    }

    public synchronized long getErrorRevision() {
        return errorRevision;
    }

    public synchronized long getSyncGlobalRev() {
        return syncGlobalRev;
    }

    public synchronized boolean isTaskLoading() {
        return taskLoading;
    }

    public synchronized boolean isTeamLoading() {
        return teamLoading;
    }

    public synchronized boolean isTagLoading() {
        return tagLoading;
    }

    public synchronized boolean isTaskTimedOut() {
        return taskLoading && System.currentTimeMillis() - taskRequestTime > 5_000L;
    }

    public synchronized boolean isTeamTimedOut() {
        return teamLoading && System.currentTimeMillis() - teamRequestTime > 5_000L;
    }

    public synchronized boolean isTagTimedOut() {
        return tagLoading && System.currentTimeMillis() - tagRequestTime > 5_000L;
    }

    public synchronized int getTaskTotalCount() {
        return taskTotalCount;
    }

    public synchronized int getHighestTaskPage() {
        return highestTaskPage;
    }

    public synchronized long getTaskGlobalVersion() {
        return taskGlobalVersion;
    }

    public synchronized void updateSyncRevision(long rev) {
        syncGlobalRev = rev;
    }

    public synchronized NBTTagList getTaskVersionList() {
        return buildVersionList(taskVersions);
    }

    public synchronized NBTTagList getTeamVersionList() {
        return buildVersionList(teamVersions);
    }

    public synchronized NBTTagList getTagVersionList() {
        return buildVersionList(tagVersions);
    }

    private static NBTTagList buildVersionList(Map<String, String> versions) {
        NBTTagList list = new NBTTagList();
        for (Map.Entry<String, String> e : versions.entrySet()) {
            NBTTagCompound entry = new NBTTagCompound();
            entry.setString("id", e.getKey());
            entry.setString("v", e.getValue());
            list.appendTag(entry);
        }
        return list;
    }

    private void acceptTaskTags(NBTTagList taskTags) {
        boolean changed = false;
        for (int i = 0; i < taskTags.tagCount(); i++) {
            NBTTagCompound tag = taskTags.getCompoundTagAt(i);
            if (!tag.hasKey("id") || tag.getString("id")
                .isEmpty()) continue;
            String id = tag.getString("id");
            NBTTagCompound copy = (NBTTagCompound) tag.copy();
            NBTTagCompound existing = tags.get(id);
            if (existing != null && !copy.hasKey("linkedTaskCount") && existing.hasKey("linkedTaskCount")) {
                copy.setInteger("linkedTaskCount", existing.getInteger("linkedTaskCount"));
            }
            tags.put(id, copy);
            changed = true;
        }
        if (changed) tagRevision++;
    }

    private void applyTaskDelta(NBTTagList updated, NBTTagList deleted) {
        for (int i = 0; i < updated.tagCount(); i++) {
            NBTTagCompound entry = updated.getCompoundTagAt(i);
            if (!entry.hasKey("id")) continue;
            String id = entry.getString("id");
            tasks.put(id, (NBTTagCompound) entry.copy());
            taskVersions.put(id, entry.getString("_v"));
            acceptTaskTags(entry.getTagList("tags", 10));
        }
        for (int i = 0; i < deleted.tagCount(); i++) {
            String id = deleted.getCompoundTagAt(i)
                .getString("id");
            tasks.remove(id);
            taskVersions.remove(id);
        }
    }

    private Task readTask(NBTTagCompound tag) {
        Task task = new Task();
        task.setId(tag.getString("id"));
        task.setTitle(tag.getString("title"));
        task.setDescription(tag.getString("description"));
        task.setVersion(tag.getInteger("version"));
        task.setStatus(readTaskStatus(tag.getString("status")));
        task.setImportance(readEnum(Task.Importance.class, tag.getString("importance"), Task.Importance.UNDEFINED));
        task.setUrgency(readEnum(Task.Urgency.class, tag.getString("urgency"), Task.Urgency.UNDEFINED));
        task.setPriority(
            readEnum(
                Task.Priority.class,
                tag.getString("priority"),
                Task.calculatePriority(task.getImportance(), task.getUrgency())));
        task.setVisibility(readEnum(Task.PrivacyLevel.class, tag.getString("visibility"), Task.PrivacyLevel.PRIVATE));
        if (!tag.getString("parentTaskId")
            .isEmpty()) task.setParentTaskId(tag.getString("parentTaskId"));
        if (!tag.getString("creator")
            .isEmpty()) task.setCreator(UUID.fromString(tag.getString("creator")));
        if (!tag.getString("assigneeId")
            .isEmpty()) task.setAssigneeId(UUID.fromString(tag.getString("assigneeId")));
        task.setAssigneeCount(tag.getInteger("assigneeCount"));
        if (!tag.getString("teamId")
            .isEmpty()) task.setTeamId(UUID.fromString(tag.getString("teamId")));
        if (!tag.getString("createTime")
            .isEmpty()) task.setCreateTime(LocalDateTime.parse(tag.getString("createTime")));
        if (!tag.getString("updateTime")
            .isEmpty()) task.setUpdateTime(LocalDateTime.parse(tag.getString("updateTime")));
        return task;
    }

    private boolean isDone(Task.TaskStatus status) {
        return status == Task.TaskStatus.Completed || status == Task.TaskStatus.Closed
            || status == Task.TaskStatus.Canceled;
    }

    private <T extends Enum<T>> T readEnum(Class<T> type, String value, T fallback) {
        if (value == null || value.isEmpty()) return fallback;
        try {
            return Enum.valueOf(type, value);
        } catch (IllegalArgumentException e) {
            return fallback;
        }
    }

    private Task.TaskStatus readTaskStatus(String value) {
        if ("UnStarted".equals(value)) return Task.TaskStatus.Claimed;
        return readEnum(Task.TaskStatus.class, value, Task.TaskStatus.UnClaimed);
    }
}
