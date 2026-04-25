package com.pinkyudeer.tasket.task.dao;

import java.sql.SQLException;
import java.util.List;

import com.pinkyudeer.tasket.db.SQLHelper;
import com.pinkyudeer.tasket.db.builder.DeleteBuilder;
import com.pinkyudeer.tasket.db.builder.SelectBuilder;
import com.pinkyudeer.tasket.db.builder.UpdateBuilder;
import com.pinkyudeer.tasket.task.entity.Task;

public class TaskDao {

    public static Integer insert(Task task) {
        return SQLHelper.insert(task);
    }

    public static UpdateBuilder<Task> update() {
        return SQLHelper.update(Task.class);
    }

    public static Integer updateByIdByCompare(Task task, Task oldTask) {
        return SQLHelper.updateByCompare(task, oldTask)
            .byId()
            .execute();
    }

    public static DeleteBuilder<Task> delete() {
        return SQLHelper.delete(Task.class);
    }

    public static Integer deleteById(Task task) {
        return SQLHelper.deleteById(task)
            .execute();
    }

    public static SelectBuilder<Task> select() {
        return SQLHelper.select(Task.class);
    }

    public static List<Task> selectAll() throws SQLException {
        return SQLHelper.selectAllFrom(Task.class);
    }
}
