package com.pinkyudeer.tasket.task.dao.record;

import java.sql.SQLException;
import java.util.List;

import com.pinkyudeer.tasket.db.SQLHelper;
import com.pinkyudeer.tasket.db.builder.DeleteBuilder;
import com.pinkyudeer.tasket.db.builder.SelectBuilder;
import com.pinkyudeer.tasket.db.builder.UpdateBuilder;
import com.pinkyudeer.tasket.task.entity.record.TeamMember;

public class TeamMemberDao {

    public static Integer insert(TeamMember teamMember) {
        return SQLHelper.insert(teamMember);
    }

    public static UpdateBuilder<TeamMember> update() {
        return SQLHelper.update(TeamMember.class);
    }

    public static Integer updateByIdByCompare(TeamMember teamMember, TeamMember oldTeamMember) {
        return SQLHelper.updateByCompare(teamMember, oldTeamMember)
            .byId()
            .execute();
    }

    public static DeleteBuilder<TeamMember> delete() {
        return SQLHelper.delete(TeamMember.class);
    }

    public static Integer deleteById(TeamMember teamMember) {
        return SQLHelper.deleteById(teamMember)
            .execute();
    }

    public static SelectBuilder<TeamMember> select() {
        return SQLHelper.select(TeamMember.class);
    }

    public static List<TeamMember> selectAll() throws SQLException {
        return SQLHelper.selectAllFrom(TeamMember.class);
    }
}
