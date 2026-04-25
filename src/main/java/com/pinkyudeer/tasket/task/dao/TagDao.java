package com.pinkyudeer.tasket.task.dao;

import java.sql.SQLException;
import java.util.List;
import java.util.UUID;

import com.pinkyudeer.tasket.db.SQLHelper;
import com.pinkyudeer.tasket.db.builder.DeleteBuilder;
import com.pinkyudeer.tasket.db.builder.SelectBuilder;
import com.pinkyudeer.tasket.db.builder.UpdateBuilder;
import com.pinkyudeer.tasket.task.entity.Tag;

public class TagDao {

    public static Integer insert(Tag tag) {
        return SQLHelper.insert(tag);
    }

    public static UpdateBuilder<Tag> update() {
        return SQLHelper.update(Tag.class);
    }

    public static Integer updateByIdByCompare(Tag tag, Tag oldTag) {
        return SQLHelper.updateByCompare(tag, oldTag)
            .byId()
            .execute();
    }

    public static DeleteBuilder<Tag> delete() {
        return SQLHelper.delete(Tag.class);
    }

    public static Integer deleteById(Tag tag) {
        return SQLHelper.deleteById(tag)
            .execute();
    }

    public static SelectBuilder<Tag> select() {
        return SQLHelper.select(Tag.class);
    }

    public static Tag selectById(UUID id) {
        return SQLHelper.selectByPremiereKey(Tag.class, id);
    }

    public static List<Tag> selectAll() throws SQLException {
        return SQLHelper.selectAllFrom(Tag.class);
    }
}
