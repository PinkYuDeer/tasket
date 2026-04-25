package com.pinkyudeer.tasket.task.dao.record;

import java.sql.SQLException;
import java.util.List;

import com.pinkyudeer.tasket.db.SQLHelper;
import com.pinkyudeer.tasket.db.builder.DeleteBuilder;
import com.pinkyudeer.tasket.db.builder.SelectBuilder;
import com.pinkyudeer.tasket.db.builder.UpdateBuilder;
import com.pinkyudeer.tasket.task.entity.record.Notification;

public class NotificationDao {

    public static Integer insert(Notification notification) {
        return SQLHelper.insert(notification);
    }

    public static UpdateBuilder<Notification> update() {
        return SQLHelper.update(Notification.class);
    }

    public static Integer updateByIdByCompare(Notification notification, Notification oldNotification) {
        return SQLHelper.updateByCompare(notification, oldNotification)
            .byId()
            .execute();
    }

    public static DeleteBuilder<Notification> delete() {
        return SQLHelper.delete(Notification.class);
    }

    public static Integer deleteById(Notification notification) {
        return SQLHelper.deleteById(notification)
            .execute();
    }

    public static SelectBuilder<Notification> select() {
        return SQLHelper.select(Notification.class);
    }

    public static List<Notification> selectAll() throws SQLException {
        return SQLHelper.selectAllFrom(Notification.class);
    }
}
