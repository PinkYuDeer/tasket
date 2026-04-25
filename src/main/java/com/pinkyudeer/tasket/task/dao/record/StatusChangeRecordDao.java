package com.pinkyudeer.tasket.task.dao.record;

import java.sql.SQLException;
import java.util.List;

import com.pinkyudeer.tasket.db.SQLHelper;
import com.pinkyudeer.tasket.db.builder.DeleteBuilder;
import com.pinkyudeer.tasket.db.builder.SelectBuilder;
import com.pinkyudeer.tasket.db.builder.UpdateBuilder;
import com.pinkyudeer.tasket.task.entity.record.StatusChangeRecord;

public class StatusChangeRecordDao {

    public static Integer insert(StatusChangeRecord record) {
        return SQLHelper.insert(record);
    }

    public static UpdateBuilder<StatusChangeRecord> update() {
        return SQLHelper.update(StatusChangeRecord.class);
    }

    public static Integer updateByIdByCompare(StatusChangeRecord record, StatusChangeRecord oldRecord) {
        return SQLHelper.updateByCompare(record, oldRecord)
            .byId()
            .execute();
    }

    public static DeleteBuilder<StatusChangeRecord> delete() {
        return SQLHelper.delete(StatusChangeRecord.class);
    }

    public static Integer deleteById(StatusChangeRecord record) {
        return SQLHelper.deleteById(record)
            .execute();
    }

    public static SelectBuilder<StatusChangeRecord> select() {
        return SQLHelper.select(StatusChangeRecord.class);
    }

    public static List<StatusChangeRecord> selectAll() throws SQLException {
        return SQLHelper.selectAllFrom(StatusChangeRecord.class);
    }
}
