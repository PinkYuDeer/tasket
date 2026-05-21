package com.pinkyudeer.tasket.task.service;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import java.util.UUID;

import org.junit.Test;

import com.pinkyudeer.tasket.db.SQLHelper;
import com.pinkyudeer.tasket.db.SQLiteManager;
import com.pinkyudeer.tasket.helper.UtilHelper;
import com.pinkyudeer.tasket.task.entity.Team;
import com.pinkyudeer.tasket.task.entity.record.TeamMember;
import com.pinkyudeer.tasket.task.entity.record.TeamRequest;
import com.pinkyudeer.tasket.test.SqliteTestSupport;

public class TeamServiceTest extends SqliteTestSupport {

    @Test
    public void localTeamInviteAcceptKickAndTransferFlowWorks() {
        UUID owner = UUID.randomUUID();
        UUID member = UUID.randomUUID();
        UUID nextOwner = UUID.randomUUID();

        Team team = TeamService.createLocalTeam("alpha", owner, "main team");
        assertNotNull(team);
        assertEquals(Team.SyncSource.LOCAL, team.getSyncSource());
        assertEquals(
            Team.TeamRole.ADMIN,
            TeamService.getMember(team.getId(), owner)
                .getRole());

        TeamRequest invite = TeamService.invitePlayer(team.getId(), member, owner);
        assertNotNull(invite);
        assertTrue(TeamService.acceptRequest(invite.getId(), member, false));
        TeamMember acceptedMember = TeamService.getMember(team.getId(), member);
        assertNotNull(acceptedMember);
        assertEquals(TeamMember.MemberStatus.ACTIVE, acceptedMember.getStatus());

        TeamRequest secondInvite = TeamService.invitePlayer(team.getId(), nextOwner, owner);
        assertTrue(TeamService.acceptRequest(secondInvite.getId(), nextOwner, false));
        assertTrue(TeamService.transferOwner(team.getId(), nextOwner, owner, false));
        assertEquals(
            nextOwner,
            TeamService.getTeam(team.getId())
                .getOwnerId());

        assertTrue(TeamService.kickMember(team.getId(), member, nextOwner, false));
        assertEquals(
            TeamMember.MemberStatus.LEFT,
            TeamService.getMember(team.getId(), member)
                .getStatus());
    }

    @Test(expected = SecurityException.class)
    public void linkedTeamCannotBeEditedLocally() {
        UUID owner = UUID.randomUUID();
        Team team = TeamService.createLocalTeam("linked", owner, null);
        team.setSyncSource(Team.SyncSource.GTNH_LIB);
        TeamService.unlinkExternalTeam(team.getId(), owner, true);
        Team linked = TeamService.getTeam(team.getId());
        linked.setSyncSource(Team.SyncSource.GTNH_LIB);
        com.pinkyudeer.tasket.db.SQLHelper.updateById(linked)
            .execute();

        TeamService.invitePlayer(team.getId(), UUID.randomUUID(), owner);
    }

    @Test
    public void versionedUpdateRetriesOnceAfterConflict() {
        UUID owner = UUID.randomUUID();
        Team team = TeamService.createLocalTeam("versioned", owner, null);
        assertNotNull(team);
        assertEquals(Integer.valueOf(1), team.getVersion());

        Team stale = UtilHelper.deepClone(team, Team.class);
        Team oldStale = UtilHelper.deepClone(team, Team.class);

        Team fresh = TeamService.getTeam(team.getId());
        fresh.setDescription("first writer");
        assertEquals(
            Integer.valueOf(1),
            SQLHelper.updateById(fresh)
                .execute());

        stale.setName("second writer");
        assertEquals(
            Integer.valueOf(1),
            SQLHelper.updateByCompare(stale, oldStale)
                .byId()
                .execute());

        Team saved = TeamService.getTeam(team.getId());
        assertEquals("second writer", saved.getName());
        assertEquals("first writer", saved.getDescription());
        assertEquals(Integer.valueOf(3), saved.getVersion());
    }

    @Test
    public void entityEventsAreWrittenForTeamLifecycle() {
        UUID owner = UUID.randomUUID();
        Team team = TeamService.createLocalTeam("events", owner, null);
        assertNotNull(team);

        team.setDescription("changed");
        assertEquals(
            Integer.valueOf(1),
            SQLHelper.updateById(team)
                .execute());

        assertTrue(
            SQLHelper.deleteById(team)
                .execute() > 0);

        Integer count = SQLiteManager.query(
            "SELECT COUNT(*) AS c FROM entity_events WHERE entity_type = ? AND entity_id = ?",
            rs -> rs.next() ? rs.getInt("c") : 0,
            "teams",
            team.getId()
                .toString());
        assertEquals(Integer.valueOf(3), count);
    }
}
