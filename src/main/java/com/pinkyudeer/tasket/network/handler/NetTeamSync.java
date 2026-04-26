package com.pinkyudeer.tasket.network.handler;

import java.time.LocalDateTime;
import java.util.List;

import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;

import com.pinkyudeer.tasket.client.TaskClientStore;
import com.pinkyudeer.tasket.network.PacketIds;
import com.pinkyudeer.tasket.network.PacketSender;
import com.pinkyudeer.tasket.network.PacketTypeRegistry;
import com.pinkyudeer.tasket.task.dao.PlayerDao;
import com.pinkyudeer.tasket.task.entity.Player;
import com.pinkyudeer.tasket.task.entity.Team;
import com.pinkyudeer.tasket.task.entity.record.TeamMember;
import com.pinkyudeer.tasket.task.service.TeamService;

public final class NetTeamSync {

    private NetTeamSync() {}

    public static void registerHandler() {
        PacketTypeRegistry.INSTANCE.registerServerHandler(PacketIds.TEAM_SYNC, NetTeamSync::onServer);
        PacketTypeRegistry.INSTANCE.registerClientHandler(PacketIds.TEAM_SYNC, NetTeamSync::onClient);
    }

    public static void requestSync() {
        PacketSender.INSTANCE.sendToServer(PacketIds.TEAM_SYNC, new NBTTagCompound());
    }

    public static void sendSync(EntityPlayerMP player, boolean merge) {
        NBTTagCompound payload = new NBTTagCompound();
        payload.setBoolean("merge", merge);
        NBTTagList list = new NBTTagList();
        for (Team team : TeamService.getVisibleTeams(player.getUniqueID())) {
            list.appendTag(writeTeam(player, team, TeamService.getMembers(team.getId())));
        }
        payload.setTag("data", list);
        PacketSender.INSTANCE.sendToPlayers(PacketIds.TEAM_SYNC, payload, player);
    }

    private static void onServer(NBTTagCompound payload, EntityPlayerMP sender) {
        if (sender != null) sendSync(sender, false);
    }

    private static void onClient(NBTTagCompound payload) {
        TaskClientStore.INSTANCE.acceptTeamSync(payload.getTagList("data", 10), payload.getBoolean("merge"));
    }

    private static NBTTagCompound writeTeam(EntityPlayerMP viewer, Team team, List<TeamMember> members) {
        NBTTagCompound tag = new NBTTagCompound();
        tag.setString(
            "id",
            team.getId() == null ? ""
                : team.getId()
                    .toString());
        tag.setString("name", team.getName() == null ? "" : team.getName());
        tag.setString("description", team.getDescription() == null ? "" : team.getDescription());
        tag.setString(
            "ownerId",
            team.getOwnerId() == null ? ""
                : team.getOwnerId()
                    .toString());
        tag.setInteger("totalMembers", team.getTotalMembers() == null ? 0 : team.getTotalMembers());
        tag.setString(
            "syncSource",
            team.getSyncSource() == null ? ""
                : team.getSyncSource()
                    .name());
        tag.setInteger("externalPartyId", team.getExternalPartyId() == null ? -1 : team.getExternalPartyId());
        tag.setString("externalTeamKey", team.getExternalTeamKey() == null ? "" : team.getExternalTeamKey());
        tag.setString(
            "syncStatus",
            team.getSyncStatus() == null ? ""
                : team.getSyncStatus()
                    .name());
        tag.setString("lastSyncTime", writeTime(team.getLastSyncTime()));

        NBTTagList memberList = new NBTTagList();
        for (TeamMember member : members) {
            NBTTagCompound memberTag = new NBTTagCompound();
            memberTag.setString(
                "playerId",
                member.getPlayerId() == null ? ""
                    : member.getPlayerId()
                        .toString());
            Player player = member.getPlayerId() == null ? null : PlayerDao.selectById(member.getPlayerId());
            memberTag.setString(
                "playerName",
                player == null || player.getPlayerName() == null ? "" : player.getPlayerName());
            memberTag.setString(
                "displayName",
                player == null || player.getDisplayName() == null ? "" : player.getDisplayName());
            memberTag.setString(
                "role",
                member.getRole() == null ? ""
                    : member.getRole()
                        .name());
            memberTag.setString(
                "status",
                member.getStatus() == null ? ""
                    : member.getStatus()
                        .name());
            memberList.appendTag(memberTag);
        }
        tag.setTag("members", memberList);

        NBTTagList onlinePlayers = new NBTTagList();
        if (viewer != null && viewer.mcServer != null && viewer.mcServer.getConfigurationManager() != null) {
            for (Object entry : viewer.mcServer.getConfigurationManager().playerEntityList) {
                if (!(entry instanceof EntityPlayerMP online)) continue;
                NBTTagCompound playerTag = new NBTTagCompound();
                playerTag.setString(
                    "playerId",
                    online.getUniqueID()
                        .toString());
                playerTag.setString("playerName", online.getCommandSenderName());
                playerTag.setString("displayName", online.getDisplayName());
                onlinePlayers.appendTag(playerTag);
            }
        }
        tag.setTag("onlinePlayers", onlinePlayers);
        return tag;
    }

    private static String writeTime(LocalDateTime time) {
        return time == null ? "" : time.toString();
    }
}
