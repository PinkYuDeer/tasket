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
    private NBTTagList invites = new NBTTagList();
    private String lastErrorCode = "";
    private String lastErrorMessage = "";
    private long taskRevision;
    private long teamRevision;
    private long tagRevision;
    private long inviteRevision;
    private long errorRevision;

    private TaskClientStore() {}

    public synchronized void reset() {
        tasks.clear();
        teams.clear();
        tags.clear();
        invites = new NBTTagList();
        lastErrorCode = "";
        lastErrorMessage = "";
        taskRevision++;
        teamRevision++;
        tagRevision++;
        inviteRevision++;
        errorRevision++;
    }

    public synchronized void acceptTaskSync(NBTTagList data, boolean merge) {
        if (!merge) tasks.clear();
        for (int i = 0; i < data.tagCount(); i++) {
            NBTTagCompound entry = data.getCompoundTagAt(i);
            if (!entry.hasKey("id")) continue;
            tasks.put(entry.getString("id"), (NBTTagCompound) entry.copy());
            acceptTaskTags(entry.getTagList("tags", 10));
        }
        taskRevision++;
    }

    public synchronized void acceptTeamSync(NBTTagList data, boolean merge) {
        if (!merge) teams.clear();
        for (int i = 0; i < data.tagCount(); i++) {
            NBTTagCompound entry = data.getCompoundTagAt(i);
            if (!entry.hasKey("id")) continue;
            teams.put(entry.getString("id"), (NBTTagCompound) entry.copy());
        }
        teamRevision++;
    }

    public synchronized void acceptTagSync(NBTTagList data, boolean merge) {
        if (!merge) tags.clear();
        for (int i = 0; i < data.tagCount(); i++) {
            NBTTagCompound entry = data.getCompoundTagAt(i);
            if (!entry.hasKey("id")) continue;
            tags.put(entry.getString("id"), (NBTTagCompound) entry.copy());
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
        errorRevision++;
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
