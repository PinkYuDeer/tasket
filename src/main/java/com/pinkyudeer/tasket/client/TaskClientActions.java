package com.pinkyudeer.tasket.client;

import java.util.Collections;
import java.util.List;

import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.nbt.NBTTagString;

import com.pinkyudeer.tasket.network.handler.NetTaskAction;
import com.pinkyudeer.tasket.task.entity.Tag;
import com.pinkyudeer.tasket.task.entity.Task;

public final class TaskClientActions {

    private TaskClientActions() {}

    public static void createTask(String title, String description, String parentTaskId, Task.Importance importance,
        Task.Urgency urgency) {
        createTask(
            title,
            description,
            parentTaskId,
            importance,
            urgency,
            Task.PrivacyLevel.PRIVATE,
            Collections.emptyList(),
            "",
            "",
            "#FFFFFF",
            Tag.TagScope.PUBLIC,
            "");
    }

    public static void createTask(String title, String description, String parentTaskId, Task.Importance importance,
        Task.Urgency urgency, Task.PrivacyLevel visibility, List<String> tagIds, String newTagName,
        String newTagDescription, String newTagColorCode, Tag.TagScope newTagScope, String assigneeId) {
        createTask(
            title,
            description,
            parentTaskId,
            importance,
            urgency,
            visibility,
            tagIds,
            newTagName,
            newTagDescription,
            newTagColorCode,
            newTagScope,
            assigneeId == null || assigneeId.isEmpty() ? Collections.emptyList()
                : java.util.Collections.singletonList(assigneeId));
    }

    public static void createTask(String title, String description, String parentTaskId, Task.Importance importance,
        Task.Urgency urgency, Task.PrivacyLevel visibility, List<String> tagIds, String newTagName,
        String newTagDescription, String newTagColorCode, Tag.TagScope newTagScope, List<String> assigneeIds) {
        NBTTagCompound payload = new NBTTagCompound();
        payload.setString("action", parentTaskId == null ? "create" : "create_subtask");
        payload.setString("title", safe(title));
        payload.setString("description", safe(description));
        payload.setString("importance", importance == null ? Task.Importance.UNDEFINED.name() : importance.name());
        payload.setString("urgency", urgency == null ? Task.Urgency.UNDEFINED.name() : urgency.name());
        payload.setString("visibility", visibility == null ? Task.PrivacyLevel.PRIVATE.name() : visibility.name());
        if (parentTaskId != null) payload.setString("parentTaskId", parentTaskId);
        NBTTagList tagList = new NBTTagList();
        if (tagIds != null) {
            for (String tagId : tagIds) {
                if (tagId != null && !tagId.isEmpty()) tagList.appendTag(new NBTTagString(tagId));
            }
        }
        payload.setTag("tagIds", tagList);
        payload.setString("newTagName", safe(newTagName));
        payload.setString("newTagDescription", safe(newTagDescription));
        payload.setString("newTagColorCode", safe(newTagColorCode));
        payload.setString("newTagScope", newTagScope == null ? Tag.TagScope.PUBLIC.name() : newTagScope.name());
        NBTTagList assigneeList = stringList(assigneeIds);
        payload.setTag("assigneeIds", assigneeList);
        payload.setString("assigneeId", assigneeList.tagCount() == 0 ? "" : assigneeList.getStringTagAt(0));
        NetTaskAction.sendAction(payload);
    }

    public static void updateTask(String taskId, int version, String title, String description,
        Task.Importance importance, Task.Urgency urgency, Task.TaskStatus status) {
        updateTask(taskId, version, title, description, importance, urgency, status, null);
    }

    public static void updateTask(String taskId, int version, String title, String description,
        Task.Importance importance, Task.Urgency urgency, Task.TaskStatus status, Task.PrivacyLevel visibility) {
        NBTTagCompound payload = new NBTTagCompound();
        payload.setString("action", "update");
        payload.setString("taskId", safe(taskId));
        payload.setInteger("version", version);
        payload.setString("title", safe(title));
        payload.setString("description", safe(description));
        payload.setString("importance", importance == null ? Task.Importance.UNDEFINED.name() : importance.name());
        payload.setString("urgency", urgency == null ? Task.Urgency.UNDEFINED.name() : urgency.name());
        if (status != null) payload.setString("status", status.name());
        if (visibility != null) payload.setString("visibility", visibility.name());
        NetTaskAction.sendAction(payload);
    }

    public static void changeStatus(String taskId, Task.TaskStatus status) {
        NBTTagCompound payload = new NBTTagCompound();
        payload.setString("action", "change_status");
        payload.setString("taskId", safe(taskId));
        payload.setString("status", status == null ? Task.TaskStatus.UnClaimed.name() : status.name());
        NetTaskAction.sendAction(payload);
    }

    public static void completeTask(String taskId) {
        NBTTagCompound payload = new NBTTagCompound();
        payload.setString("action", "complete");
        payload.setString("taskId", safe(taskId));
        NetTaskAction.sendAction(payload);
    }

    public static void deleteTask(String taskId) {
        NBTTagCompound payload = new NBTTagCompound();
        payload.setString("action", "delete");
        payload.setString("taskId", safe(taskId));
        NetTaskAction.sendAction(payload);
    }

    public static void addTag(String taskId, String tagId) {
        NBTTagCompound payload = new NBTTagCompound();
        payload.setString("action", "add_tag");
        payload.setString("taskId", safe(taskId));
        payload.setString("tagId", safe(tagId));
        NetTaskAction.sendAction(payload);
    }

    public static void addTagByName(String taskId, String tagName, String description, String colorCode,
        Tag.TagScope scope) {
        NBTTagCompound payload = new NBTTagCompound();
        payload.setString("action", "add_tag_by_name");
        payload.setString("taskId", safe(taskId));
        payload.setString("tagName", safe(tagName));
        payload.setString("description", safe(description));
        payload.setString("colorCode", safe(colorCode));
        payload.setString("scope", scope == null ? Tag.TagScope.PUBLIC.name() : scope.name());
        NetTaskAction.sendAction(payload);
    }

    public static void removeTag(String taskId, String tagId) {
        NBTTagCompound payload = new NBTTagCompound();
        payload.setString("action", "remove_tag");
        payload.setString("taskId", safe(taskId));
        payload.setString("tagId", safe(tagId));
        NetTaskAction.sendAction(payload);
    }

    public static void setTags(String taskId, List<String> tagIds) {
        NBTTagCompound payload = new NBTTagCompound();
        payload.setString("action", "set_tags");
        payload.setString("taskId", safe(taskId));
        NBTTagList tagList = new NBTTagList();
        if (tagIds != null) {
            for (String tagId : tagIds) {
                if (tagId != null && !tagId.isEmpty()) tagList.appendTag(new NBTTagString(tagId));
            }
        }
        payload.setTag("tagIds", tagList);
        NetTaskAction.sendAction(payload);
    }

    public static void assignTask(String taskId, String assigneeId) {
        assignTask(
            taskId,
            assigneeId == null || assigneeId.isEmpty() ? Collections.emptyList()
                : java.util.Collections.singletonList(assigneeId));
    }

    public static void assignTask(String taskId, List<String> assigneeIds) {
        NBTTagCompound payload = new NBTTagCompound();
        payload.setString("action", "assign");
        payload.setString("taskId", safe(taskId));
        NBTTagList list = stringList(assigneeIds);
        payload.setTag("assigneeIds", list);
        payload.setString("assigneeId", list.tagCount() == 0 ? "" : list.getStringTagAt(0));
        NetTaskAction.sendAction(payload);
    }

    private static NBTTagList stringList(List<String> values) {
        NBTTagList list = new NBTTagList();
        if (values != null) {
            for (String value : values) {
                if (value != null && !value.isEmpty()) list.appendTag(new NBTTagString(value));
            }
        }
        return list;
    }

    private static String safe(String value) {
        return value == null ? "" : value;
    }
}
