package com.pinkyudeer.tasket.task.service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import com.github.bsideup.jabel.Desugar;
import com.pinkyudeer.tasket.Tasket;
import com.pinkyudeer.tasket.db.EntityHandler;
import com.pinkyudeer.tasket.db.SQLHelper;
import com.pinkyudeer.tasket.db.SQLiteManager;
import com.pinkyudeer.tasket.helper.UtilHelper;
import com.pinkyudeer.tasket.task.dao.TaskDao;
import com.pinkyudeer.tasket.task.dao.record.TaskInteractionDao;
import com.pinkyudeer.tasket.task.entity.Task;
import com.pinkyudeer.tasket.task.entity.Task.TaskStatus;
import com.pinkyudeer.tasket.task.entity.Team;
import com.pinkyudeer.tasket.task.entity.record.StatusChangeRecord;
import com.pinkyudeer.tasket.task.entity.record.TaskInteraction;
import com.pinkyudeer.tasket.task.entity.record.TeamMember;

public class TaskService {

    private static final int DEFAULT_PAGE_SIZE = 20;
    private static final int MAX_PAGE_SIZE = 100;

    public static Task createTask(String title, String description, UUID creatorId) {
        Task task = new Task(title, description, creatorId);
        Integer result = TaskDao.insert(task);
        if (result == null || result <= 0) {
            Tasket.LOG.error("Failed to create task: {}", title);
            return null;
        }
        Tasket.LOG.info("Task created: {} ({})", title, task.getId());
        return task;
    }

    public static Task createTask(String title, String description, UUID creatorId, Task.Importance importance,
        Task.Urgency urgency) {
        Task task = new Task(title, description, creatorId, importance, urgency);
        Integer result = TaskDao.insert(task);
        if (result == null || result <= 0) {
            Tasket.LOG.error("Failed to create task: {}", title);
            return null;
        }
        return task;
    }

    public static Task createTask(PermissionContext context, String title, String description,
        Task.Importance importance, Task.Urgency urgency) {
        return createTask(context, title, description, importance, urgency, null);
    }

    public static Task createTask(PermissionContext context, String title, String description,
        Task.Importance importance, Task.Urgency urgency, Task.PrivacyLevel visibility) {
        if (context == null) throw new IllegalArgumentException("权限上下文不能为空");
        if (context.teamId() != null && !context.canCreateTeamTask()) {
            Tasket.LOG.warn("Player {} cannot create task in team {}", context.actorId(), context.teamId());
            return null;
        }
        Task task = new Task(title, description, context.actorId(), importance, urgency);
        task.setTeamId(context.teamId());
        task.setVisibility(resolveVisibility(context, visibility));
        Integer result = TaskDao.insert(task);
        if (result == null || result <= 0) {
            Tasket.LOG.error("Failed to create task: {}", title);
            return null;
        }
        return task;
    }

    public static boolean updateTask(Task task, Task oldTask) {
        task.setUpdateTime(LocalDateTime.now());
        Integer result = TaskDao.updateByIdByCompare(task, oldTask);
        if (result == null || result <= 0) {
            Tasket.LOG.error("Failed to update task: {}", task.getId());
            return false;
        }
        return true;
    }

    public static Task getTask(String taskId) {
        try {
            return SQLHelper.select(Task.class)
                .where("id", SQLHelper.Operator.EQ, taskId)
                .first();
        } catch (Exception e) {
            Tasket.LOG.error("Failed to fetch task: {}", taskId, e);
            return null;
        }
    }

    public static boolean changeStatus(String taskId, TaskStatus newStatus, UUID operatorId) {
        try {
            return SQLiteManager.transaction(() -> {
                Task task = getTask(taskId);
                if (task == null) {
                    Tasket.LOG.error("Task not found: {}", taskId);
                    return false;
                }
                TaskStatus oldStatus = task.getStatus();
                if (oldStatus == newStatus) return true;

                Task oldTask = UtilHelper.deepClone(task, Task.class);
                task.setStatus(newStatus);
                if (newStatus == TaskStatus.UnClaimed) {
                    task.setAssigneeId(null);
                    task.setAssigneeCount(0);
                    UUID taskUuid = UUID.fromString(taskId);
                    for (TaskInteraction interaction : activeAssignmentRecords(taskUuid)) {
                        TaskInteraction oldInteraction = UtilHelper.deepClone(interaction, TaskInteraction.class);
                        interaction.setStatus(TaskInteraction.InteractionStatus.REVOKED);
                        TaskInteractionDao.updateByIdByCompare(interaction, oldInteraction);
                    }
                }
                task.setUpdateTime(LocalDateTime.now());
                task.setLastOperator(operatorId);

                if (newStatus == TaskStatus.Completed || newStatus == TaskStatus.Closed) {
                    task.setEndTime(LocalDateTime.now());
                }
                if (newStatus == TaskStatus.InProgress && task.getStartTime() == null) {
                    task.setStartTime(LocalDateTime.now());
                }

                Integer result = TaskDao.updateByIdByCompare(task, oldTask);
                if (result == null || result <= 0) return false;

                StatusChangeRecord record = new StatusChangeRecord(
                    operatorId,
                    UUID.fromString(taskId),
                    oldStatus,
                    newStatus);
                SQLHelper.insert(record);

                Tasket.LOG.info("Task {} status changed: {} -> {}", taskId, oldStatus, newStatus);
                return true;
            });
        } catch (Exception e) {
            Tasket.LOG.error("Failed to change task status", e);
            return false;
        }
    }

    public static boolean assignTask(PermissionContext context, String taskId, UUID assigneeId) {
        return setAssigneesForTask(
            context,
            taskId,
            assigneeId == null ? Collections.emptyList() : java.util.Collections.singletonList(assigneeId));
    }

    public static boolean setAssigneesForTask(PermissionContext context, String taskId, List<UUID> assigneeIds) {
        if (context == null) throw new IllegalArgumentException("权限上下文不能为空");
        try {
            return SQLiteManager.transaction(() -> {
                Task task = getTask(taskId);
                if (task == null) {
                    Tasket.LOG.error("Task not found: {}", taskId);
                    return false;
                }
                if (canWriteTask(context, task)) throw new SecurityException("无权指派此任务");

                LinkedHashSet<UUID> targetIds = new LinkedHashSet<>();
                if (assigneeIds != null) {
                    for (UUID assigneeId : assigneeIds) {
                        if (assigneeId == null) continue;
                        assertAssigneeAllowed(context, task, assigneeId);
                        targetIds.add(assigneeId);
                    }
                }

                Task oldTask = UtilHelper.deepClone(task, Task.class);
                TaskStatus oldStatus = task.getStatus();
                if (targetIds.isEmpty()) {
                    task.setAssigneeId(null);
                    task.setAssigneeCount(0);
                    if (task.getStatus() == TaskStatus.Claimed) task.setStatus(TaskStatus.UnClaimed);
                } else {
                    task.setAssigneeId(
                        targetIds.iterator()
                            .next());
                    task.setAssigneeCount(targetIds.size());
                    if (task.getStatus() == TaskStatus.UnClaimed) task.setStatus(TaskStatus.Claimed);
                }
                task.setUpdateTime(LocalDateTime.now());
                task.setLastOperator(context.actorId());

                UUID taskUuid = UUID.fromString(taskId);
                List<TaskInteraction> oldAssignments = activeAssignmentRecords(taskUuid);
                for (TaskInteraction interaction : oldAssignments) {
                    if (!targetIds.remove(interaction.getPlayerId())) {
                        TaskInteraction oldInteraction = UtilHelper.deepClone(interaction, TaskInteraction.class);
                        interaction.setStatus(TaskInteraction.InteractionStatus.REVOKED);
                        TaskInteractionDao.updateByIdByCompare(interaction, oldInteraction);
                    }
                }
                for (UUID assigneeId : targetIds) {
                    TaskInteraction interaction = new TaskInteraction(
                        TaskInteraction.InteractionType.ASSIGN,
                        taskUuid,
                        assigneeId,
                        context.actorId());
                    TaskInteractionDao.insert(interaction);
                }

                Integer result = TaskDao.updateByIdByCompare(task, oldTask);
                if (result == null || result <= 0) return false;

                if (oldStatus != task.getStatus()) {
                    StatusChangeRecord record = new StatusChangeRecord(
                        context.actorId(),
                        UUID.fromString(taskId),
                        oldStatus,
                        task.getStatus());
                    SQLHelper.insert(record);
                }
                return true;
            });
        } catch (SecurityException e) {
            throw e;
        } catch (Exception e) {
            Tasket.LOG.error("Failed to assign task: {}", taskId, e);
            return false;
        }
    }

    public static List<UUID> getAssigneesForTask(String taskId) {
        if (taskId == null || taskId.isEmpty()) return Collections.emptyList();
        try {
            return getAssigneesForTask(getTask(taskId));
        } catch (Exception e) {
            Tasket.LOG.warn("Failed to load task assignees: {}", taskId, e);
            return Collections.emptyList();
        }
    }

    /**
     * 已加载 Task 的 assignee 查询，避免重复 SELECT * FROM tasks WHERE id=?
     */
    public static List<UUID> getAssigneesForTask(Task task) {
        if (task == null || task.getId() == null) return Collections.emptyList();
        LinkedHashSet<UUID> result = new LinkedHashSet<>();
        try {
            UUID taskUuid = UUID.fromString(task.getId());
            for (TaskInteraction interaction : activeAssignmentRecords(taskUuid)) {
                if (interaction.getPlayerId() != null) result.add(interaction.getPlayerId());
            }
            if (task.getAssigneeId() != null) result.add(task.getAssigneeId());
        } catch (Exception e) {
            Tasket.LOG.warn("Failed to load task assignees: {}", task.getId(), e);
        }
        return new ArrayList<>(result);
    }

    /**
     * 批量加载多个任务的 ASSIGN/ACTIVE 互动记录，避免 N+1 查询。
     * 返回 Map<taskId, assigneeIds>，已合并 Task.assigneeId。
     */
    public static Map<String, List<UUID>> collectAssigneesByTask(Collection<Task> tasks) {
        if (tasks == null || tasks.isEmpty()) return Collections.emptyMap();
        Map<UUID, String> uuidToId = new HashMap<>();
        List<UUID> taskUuids = new ArrayList<>();
        for (Task task : tasks) {
            if (task == null || task.getId() == null) continue;
            try {
                UUID uuid = UUID.fromString(task.getId());
                uuidToId.put(uuid, task.getId());
                taskUuids.add(uuid);
            } catch (IllegalArgumentException ignored) {}
        }

        Map<String, LinkedHashSet<UUID>> byTask = new HashMap<>();
        if (!taskUuids.isEmpty()) {
            try {
                List<TaskInteraction> rows = TaskInteractionDao.select()
                    .where("task_id", SQLHelper.Operator.IN, taskUuids)
                    .where("type", SQLHelper.Operator.EQ, TaskInteraction.InteractionType.ASSIGN)
                    .where("status", SQLHelper.Operator.EQ, TaskInteraction.InteractionStatus.ACTIVE)
                    .list();
                for (TaskInteraction interaction : rows) {
                    if (interaction.getTaskId() == null || interaction.getPlayerId() == null) continue;
                    String key = uuidToId.get(interaction.getTaskId());
                    if (key == null) continue;
                    byTask.computeIfAbsent(key, k -> new LinkedHashSet<>())
                        .add(interaction.getPlayerId());
                }
            } catch (Exception e) {
                Tasket.LOG.warn("Failed to batch load task assignees", e);
            }
        }

        Map<String, List<UUID>> result = new HashMap<>();
        for (Task task : tasks) {
            if (task == null || task.getId() == null) continue;
            LinkedHashSet<UUID> ids = byTask.getOrDefault(task.getId(), new LinkedHashSet<>());
            if (task.getAssigneeId() != null) ids.add(task.getAssigneeId());
            result.put(task.getId(), new ArrayList<>(ids));
        }
        return result;
    }

    private static List<TaskInteraction> activeAssignmentRecords(UUID taskId) {
        if (taskId == null) return Collections.emptyList();
        return TaskInteractionDao.select()
            .where("task_id", SQLHelper.Operator.EQ, taskId)
            .where("type", SQLHelper.Operator.EQ, TaskInteraction.InteractionType.ASSIGN)
            .where("status", SQLHelper.Operator.EQ, TaskInteraction.InteractionStatus.ACTIVE)
            .list();
    }

    public static boolean completeTask(String taskId, UUID operatorId) {
        return changeStatus(taskId, TaskStatus.Completed, operatorId);
    }

    public static boolean archiveTask(String taskId, UUID operatorId) {
        return changeStatus(taskId, TaskStatus.Closed, operatorId);
    }

    public static List<Task> getActiveTasks() {
        try {
            List<String> excluded = java.util.Arrays
                .asList(TaskStatus.Completed.name(), TaskStatus.Closed.name(), TaskStatus.Canceled.name());
            return SQLHelper.select(Task.class)
                .where("status", SQLHelper.Operator.NOT_IN, excluded)
                .list();
        } catch (Exception e) {
            Tasket.LOG.error("Failed to fetch active tasks", e);
            return Collections.emptyList();
        }
    }

    public static List<Task> getAllTasks() {
        try {
            return TaskDao.selectAll();
        } catch (Exception e) {
            Tasket.LOG.error("Failed to fetch all tasks", e);
            return Collections.emptyList();
        }
    }

    public static List<Task> getVisibleTasks(UUID playerId, boolean isOp) {
        if (isOp) return getAllTasks();
        java.util.ArrayList<Task> visible = new java.util.ArrayList<>();
        for (Task task : getAllTasks()) {
            if (canViewTask(playerId, false, task)) visible.add(task);
        }
        return visible;
    }

    public static PagedTasks getVisibleTasksPage(UUID playerId, boolean isOp, int page, int pageSize, String sortMode,
        boolean includeCompleted, boolean mineOnly) {
        int safePage = Math.max(0, page);
        int safePageSize = Math.max(1, Math.min(MAX_PAGE_SIZE, pageSize <= 0 ? DEFAULT_PAGE_SIZE : pageSize));
        QueryParts query = visibleTaskQuery(playerId, isOp, includeCompleted, mineOnly);
        int total = SQLiteManager.query(
            "SELECT COUNT(*) AS c FROM tasks" + query.whereClause(),
            rs -> rs.next() ? rs.getInt("c") : 0,
            query.params()
                .toArray());
        List<Object> params = new ArrayList<>(query.params());
        params.add(safePageSize);
        params.add(safePage * safePageSize);
        List<Task> tasks = SQLiteManager.query(
            "SELECT * FROM tasks" + query.whereClause() + " ORDER BY " + orderBy(sortMode) + " LIMIT ? OFFSET ?",
            rs -> EntityHandler.handleList(rs, Task.class),
            params.toArray());
        return new PagedTasks(
            tasks == null ? Collections.emptyList() : tasks,
            Math.max(0, total),
            safePage,
            safePageSize);
    }

    public static Set<String> getVisibleTaskIds(UUID playerId, boolean isOp, Collection<String> taskIds,
        boolean includeCompleted, boolean mineOnly) {
        if (taskIds == null || taskIds.isEmpty()) return Collections.emptySet();
        List<String> ids = new ArrayList<>();
        for (String id : taskIds) {
            if (id != null && !id.isEmpty()) ids.add(id);
        }
        if (ids.isEmpty()) return Collections.emptySet();

        QueryParts query = visibleTaskQuery(playerId, isOp, includeCompleted, mineOnly);
        StringBuilder sql = new StringBuilder("SELECT id FROM tasks");
        if (query.whereClause()
            .isEmpty()) {
            sql.append(" WHERE ");
        } else {
            sql.append(query.whereClause())
                .append(" AND ");
        }
        sql.append("id IN (")
            .append(String.join(", ", Collections.nCopies(ids.size(), "?")))
            .append(")");

        List<Object> params = new ArrayList<>(query.params());
        params.addAll(ids);
        Set<String> visibleIds = SQLiteManager.query(sql.toString(), rs -> {
            Set<String> result = new HashSet<>();
            while (rs.next()) result.add(rs.getString("id"));
            return result;
        }, params.toArray());
        return visibleIds == null ? Collections.emptySet() : visibleIds;
    }

    public static boolean canViewTask(UUID playerId, boolean isOp, Task task) {
        if (task == null) return false;
        if (isOp || task.getVisibility() == Task.PrivacyLevel.PUBLIC) return true;
        if (playerId != null && playerId.equals(task.getCreator())) return true;
        if (playerId != null && task.getTeamId() != null && task.getVisibility() == Task.PrivacyLevel.TEAM) {
            TeamMember member = TeamService.getMember(task.getTeamId(), playerId);
            return member != null && member.getStatus() == TeamMember.MemberStatus.ACTIVE;
        }
        return false;
    }

    public static boolean canWriteTask(PermissionContext context, Task task) {
        if (context == null || task == null) return true;
        if (context.op()) return false;
        if (context.actorId() != null && context.actorId()
            .equals(task.getCreator())) return false;
        if (task.getTeamId() != null && task.getTeamId()
            .equals(context.teamId())) {
            return !context.canAssignTeamTask();
        }
        return true;
    }

    private static Task.PrivacyLevel resolveVisibility(PermissionContext context, Task.PrivacyLevel requested) {
        if (requested == null) return context.teamId() == null ? Task.PrivacyLevel.PRIVATE : Task.PrivacyLevel.TEAM;
        if (requested == Task.PrivacyLevel.TEAM && context.teamId() == null) return Task.PrivacyLevel.PRIVATE;
        return requested;
    }

    private static QueryParts visibleTaskQuery(UUID playerId, boolean isOp, boolean includeCompleted,
        boolean mineOnly) {
        List<String> clauses = new ArrayList<>();
        List<Object> params = new ArrayList<>();
        if (!isOp) {
            List<UUID> teamIds = activeTeamIds(playerId);
            StringBuilder visibility = new StringBuilder("(visibility = ?");
            params.add(Task.PrivacyLevel.PUBLIC.name());
            if (playerId != null) {
                visibility.append(" OR creator = ?");
                params.add(playerId.toString());
            }
            if (!teamIds.isEmpty()) {
                visibility.append(" OR (visibility = ? AND team_id IN (")
                    .append(String.join(", ", Collections.nCopies(teamIds.size(), "?")))
                    .append("))");
                params.add(Task.PrivacyLevel.TEAM.name());
                for (UUID teamId : teamIds) params.add(teamId.toString());
            }
            visibility.append(")");
            clauses.add(visibility.toString());
        }
        if (!includeCompleted) {
            clauses.add("status NOT IN (?, ?, ?)");
            params.add(TaskStatus.Completed.name());
            params.add(TaskStatus.Closed.name());
            params.add(TaskStatus.Canceled.name());
        }
        if (mineOnly && playerId != null) {
            clauses.add("(creator = ? OR assignee_id = ?)");
            params.add(playerId.toString());
            params.add(playerId.toString());
        }
        return new QueryParts(clauses.isEmpty() ? "" : " WHERE " + String.join(" AND ", clauses), params);
    }

    private static List<UUID> activeTeamIds(UUID playerId) {
        if (playerId == null) return Collections.emptyList();
        List<UUID> ids = SQLiteManager
            .query("SELECT team_id FROM team_members WHERE player_id = ? AND status = ?", rs -> {
                List<UUID> result = new ArrayList<>();
                while (rs.next()) {
                    Object value = rs.getObject("team_id");
                    if (value instanceof UUID uuid) result.add(uuid);
                    else if (value != null) result.add(UUID.fromString(value.toString()));
                }
                return result;
            }, playerId.toString(), TeamMember.MemberStatus.ACTIVE.name());
        return ids == null ? Collections.emptyList() : ids;
    }

    private static String orderBy(String sortMode) {
        if ("time_asc".equals(sortMode)) return "create_time ASC";
        if ("time_desc".equals(sortMode)) return "create_time DESC";
        return "priority ASC, create_time DESC";
    }

    private static void assertAssigneeAllowed(PermissionContext context, Task task, UUID assigneeId) {
        if (assigneeId == null) return;
        if (task.getTeamId() == null) {
            if (!assigneeId.equals(context.actorId())) throw new SecurityException("个人任务只能指派给自己");
            return;
        }
        TeamMember member = TeamService.getMember(task.getTeamId(), assigneeId);
        if (member == null || member.getStatus() != TeamMember.MemberStatus.ACTIVE) {
            throw new SecurityException("负责人不是团队成员");
        }
    }

    public static boolean deleteTask(String taskId) {
        try {
            return SQLiteManager.transaction(() -> {
                SQLHelper.delete(Task.class)
                    .where("parent_task_id", SQLHelper.Operator.EQ, taskId)
                    .execute();
                Integer result = SQLHelper.delete(Task.class)
                    .where("id", SQLHelper.Operator.EQ, taskId)
                    .execute();
                return result != null && result > 0;
            });
        } catch (Exception e) {
            Tasket.LOG.error("Failed to delete task: {}", taskId, e);
            return false;
        }
    }

    public static Task createSubtask(String title, String description, UUID creatorId, String parentTaskId) {
        Task task = new Task(title, description, creatorId);
        task.setParentTaskId(parentTaskId);
        Integer result = TaskDao.insert(task);
        if (result == null || result <= 0) {
            Tasket.LOG.error("Failed to create subtask: {}", title);
            return null;
        }
        return task;
    }

    public static Task createSubtask(String title, String description, UUID creatorId, String parentTaskId,
        Task.Importance importance, Task.Urgency urgency) {
        Task task = new Task(title, description, creatorId, importance, urgency);
        task.setParentTaskId(parentTaskId);
        Integer result = TaskDao.insert(task);
        if (result == null || result <= 0) {
            Tasket.LOG.error("Failed to create subtask: {}", title);
            return null;
        }
        return task;
    }

    public static List<Task> getSubtasks(String parentTaskId) {
        try {
            return SQLHelper.select(Task.class)
                .where("parent_task_id", SQLHelper.Operator.EQ, parentTaskId)
                .list();
        } catch (Exception e) {
            Tasket.LOG.error("Failed to fetch subtasks for: {}", parentTaskId, e);
            return Collections.emptyList();
        }
    }

    public static int getSubtaskCount(String parentTaskId) {
        if (parentTaskId == null || parentTaskId.isEmpty()) return 0;
        try {
            Integer count = SQLiteManager.query(
                "SELECT COUNT(*) AS c FROM tasks WHERE parent_task_id = ?",
                rs -> rs.next() ? rs.getInt("c") : 0,
                parentTaskId);
            return count == null ? 0 : count;
        } catch (Exception e) {
            return 0;
        }
    }

    @Desugar
    public record PermissionContext(UUID actorId, UUID teamId, Team.TeamRole teamRole, boolean op) {

        public boolean canCreateTeamTask() {
            return op || teamId == null || teamRole == Team.TeamRole.ADMIN || teamRole == Team.TeamRole.MEMBER;
        }

        public boolean canAssignTeamTask() {
            return op || teamRole == Team.TeamRole.ADMIN;
        }
    }

    @Desugar
    public record PagedTasks(List<Task> tasks, int totalCount, int page, int pageSize) {}

    @Desugar
    private record QueryParts(String whereClause, List<Object> params) {}
}
