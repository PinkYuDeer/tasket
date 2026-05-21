package com.pinkyudeer.tasket.network.handler;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;

import com.pinkyudeer.tasket.client.TaskClientStore;
import com.pinkyudeer.tasket.core.ServerTaskScheduler;
import com.pinkyudeer.tasket.db.AsyncSqlExecutor;
import com.pinkyudeer.tasket.network.PacketIds;
import com.pinkyudeer.tasket.network.PacketSender;
import com.pinkyudeer.tasket.network.PacketTypeRegistry;
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
        TaskClientStore.INSTANCE.markTeamLoading();
        NBTTagCompound payload = new NBTTagCompound();
        payload.setTag("versions", TaskClientStore.INSTANCE.getTeamVersionList());
        payload.setLong("rev", TaskClientStore.INSTANCE.getSyncGlobalRev());
        PacketSender.INSTANCE.sendToServer(PacketIds.TEAM_SYNC, payload);
    }

    public static void sendSync(EntityPlayerMP player) {
        NetMainSync.bumpRevision();
        sendDeltaAsync(player, new HashMap<>(), NetMainSync.getRevision());
    }

    static void sendDelta(EntityPlayerMP player, Map<String, String> clientVersions, List<Team> visibleTeams,
        Map<UUID, Player> playerCache) {
        NBTTagCompound payload = buildDeltaPayload(player.getUniqueID(), clientVersions, visibleTeams, playerCache);
        attachOnlinePlayers(payload, player);
        PacketSender.INSTANCE.sendToPlayers(PacketIds.TEAM_SYNC, payload, player);
    }

    static void sendEmptyDelta(EntityPlayerMP player) {
        NBTTagCompound payload = new NBTTagCompound();
        payload.setTag("updated", new NBTTagList());
        payload.setTag("deleted", new NBTTagList());
        payload.setLong("rev", NetMainSync.getRevision());
        PacketSender.INSTANCE.sendToPlayers(PacketIds.TEAM_SYNC, payload, player);
    }

    private static void onServer(NBTTagCompound payload, EntityPlayerMP sender) {
        if (sender == null) return;
        long clientRev = payload.getLong("rev");
        Map<String, String> clientVersions = NetTaskSync.parseVersionList(payload.getTagList("versions", 10));
        if (clientRev == NetMainSync.getRevision() && !clientVersions.isEmpty()) {
            sendEmptyDelta(sender);
            return;
        }
        sendDeltaAsync(sender, clientVersions, clientRev);
    }

    private static void sendDeltaAsync(EntityPlayerMP sender, Map<String, String> clientVersions, long clientRev) {
        UUID viewerId = sender.getUniqueID();
        String key = "team_sync:" + viewerId
            + ':'
            + clientVersions.hashCode()
            + ':'
            + clientRev
            + ':'
            + NetMainSync.getRevision();
        AsyncSqlExecutor.INSTANCE
            .submit(
                key,
                new HashSet<>(java.util.Arrays.asList("teams", "team_members", "players", "entity_events")),
                () -> buildDeltaPayload(viewerId, clientVersions, null, new HashMap<>()))
            .whenComplete((response, error) -> ServerTaskScheduler.INSTANCE.schedule(() -> {
                if (error != null) {
                    NetError.send(sender, NetError.SERVER_ERROR, "team sync failed");
                    return;
                }
                NBTTagCompound outbound = (NBTTagCompound) response.copy();
                attachOnlinePlayers(outbound, sender);
                PacketSender.INSTANCE.sendToPlayers(PacketIds.TEAM_SYNC, outbound, sender);
            }, false));
    }

    private static void onClient(NBTTagCompound payload) {
        TaskClientStore.INSTANCE.acceptTeamDelta(payload.getTagList("updated", 10), payload.getTagList("deleted", 10));
        TaskClientStore.INSTANCE.updateSyncRevision(payload.getLong("rev"));
    }

    private static NBTTagCompound buildDeltaPayload(UUID viewerId, Map<String, String> clientVersions,
        List<Team> visibleTeams, Map<UUID, Player> playerCache) {
        List<Team> teams = visibleTeams != null ? visibleTeams : TeamService.getVisibleTeams(viewerId);

        Set<String> visibleIds = new HashSet<>();
        List<Team> updated = new ArrayList<>();
        for (Team team : teams) {
            if (team == null || team.getId() == null) continue;
            String id = team.getId()
                .toString();
            visibleIds.add(id);
            String serverVersion = teamVersion(team);
            String clientVersion = clientVersions.get(id);
            if (!serverVersion.equals(clientVersion)) {
                updated.add(team);
            }
        }

        NBTTagList deletedList = new NBTTagList();
        for (Map.Entry<String, String> entry : clientVersions.entrySet()) {
            if (!visibleIds.contains(entry.getKey())) {
                NBTTagCompound del = new NBTTagCompound();
                del.setString("id", entry.getKey());
                deletedList.appendTag(del);
            }
        }

        NBTTagList updatedList = new NBTTagList();
        for (Team team : updated) {
            updatedList.appendTag(writeTeam(team, TeamService.getMembers(team.getId()), playerCache));
        }

        NBTTagCompound payload = new NBTTagCompound();
        payload.setTag("updated", updatedList);
        payload.setTag("deleted", deletedList);
        payload.setLong("rev", NetMainSync.getRevision());
        return payload;
    }

    private static NBTTagCompound writeTeam(Team team, List<TeamMember> members, Map<UUID, Player> playerCache) {
        NBTTagCompound tag = new NBTTagCompound();
        tag.setString(
            "id",
            team.getId() == null ? ""
                : team.getId()
                    .toString());
        tag.setString("_v", teamVersion(team));
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
            Player player = NetTaskSync.lookupPlayer(playerCache, member.getPlayerId());
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

        tag.setTag("onlinePlayers", new NBTTagList());
        return tag;
    }

    private static void attachOnlinePlayers(NBTTagCompound payload, EntityPlayerMP viewer) {
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
        NBTTagList updatedTeams = payload.getTagList("updated", 10);
        for (int i = 0; i < updatedTeams.tagCount(); i++) {
            updatedTeams.getCompoundTagAt(i)
                .setTag("onlinePlayers", (NBTTagList) onlinePlayers.copy());
        }
    }

    static String teamVersion(Team team) {
        return String.valueOf(team.getVersion() == null ? 0 : team.getVersion());
    }

    private static String writeTime(LocalDateTime time) {
        return time == null ? "" : time.toString();
    }
}
