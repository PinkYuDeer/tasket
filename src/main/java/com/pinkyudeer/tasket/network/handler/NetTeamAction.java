package com.pinkyudeer.tasket.network.handler;

import java.util.UUID;

import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.nbt.NBTTagCompound;

import com.pinkyudeer.tasket.Tasket;
import com.pinkyudeer.tasket.network.PacketIds;
import com.pinkyudeer.tasket.network.PacketSender;
import com.pinkyudeer.tasket.network.PacketTypeRegistry;
import com.pinkyudeer.tasket.task.entity.Team;
import com.pinkyudeer.tasket.task.service.TeamService;
import com.pinkyudeer.tasket.task.team.TeamProvider;
import com.pinkyudeer.tasket.task.team.TeamProviders;

public final class NetTeamAction {

    private NetTeamAction() {}

    public static void registerHandler() {
        PacketTypeRegistry.INSTANCE.registerServerHandler(PacketIds.TEAM_ACTION, NetTeamAction::onServer);
    }

    public static void sendAction(NBTTagCompound payload) {
        PacketSender.INSTANCE.sendToServer(PacketIds.TEAM_ACTION, payload);
    }

    private static void onServer(NBTTagCompound payload, EntityPlayerMP sender) {
        if (sender == null) return;
        String action = payload.getString("action");
        try {
            UUID actorId = sender.getUniqueID();
            boolean op = isOp(sender);
            if ("create".equals(action)) {
                Team team = TeamService
                    .createLocalTeam(payload.getString("name"), actorId, payload.getString("description"));
                linkCreatedTeam(team, payload.getString("source"), actorId, op);
                NetTeamSync.sendSync(sender, true);
            } else if ("invite".equals(action)) {
                TeamService.invitePlayer(readUuid(payload, "teamId"), readUuid(payload, "playerId"), actorId);
                NetTeamSync.sendSync(sender, true);
            } else if ("request_join".equals(action)) {
                TeamService.requestJoin(readUuid(payload, "teamId"), actorId, payload.getString("reason"));
                NetTeamSync.sendSync(sender, true);
            } else if ("accept".equals(action)) {
                TeamService.acceptRequest(readUuid(payload, "requestId"), actorId, op);
                NetTeamSync.sendSync(sender, true);
            } else if ("kick".equals(action)) {
                TeamService.kickMember(readUuid(payload, "teamId"), readUuid(payload, "playerId"), actorId, op);
                NetTeamSync.sendSync(sender, true);
            } else if ("leave".equals(action)) {
                TeamService.leaveTeam(readUuid(payload, "teamId"), actorId);
                NetTeamSync.sendSync(sender, true);
            } else if ("transfer_owner".equals(action)) {
                TeamService.transferOwner(readUuid(payload, "teamId"), readUuid(payload, "playerId"), actorId, op);
                NetTeamSync.sendSync(sender, true);
            } else if ("link_bq".equals(action)) {
                TeamService
                    .linkBetterQuestingParty(readUuid(payload, "teamId"), payload.getInteger("partyId"), actorId, op);
                NetTeamSync.sendSync(sender, true);
            } else if ("sync_bq".equals(action)) {
                TeamService.syncBetterQuestingTeam(readUuid(payload, "teamId"), actorId, op);
                NetTeamSync.sendSync(sender, true);
            } else if ("unlink_bq".equals(action)) {
                TeamService.unlinkBetterQuestingTeam(readUuid(payload, "teamId"), actorId, op);
                NetTeamSync.sendSync(sender, true);
            } else if ("link_gtnh".equals(action)) {
                TeamService.linkGtnhLibTeam(readUuid(payload, "teamId"), payload.getString("teamKey"), actorId, op);
                NetTeamSync.sendSync(sender, true);
            } else if ("sync_gtnh".equals(action)) {
                TeamService.syncGtnhLibTeam(readUuid(payload, "teamId"), actorId, op);
                NetTeamSync.sendSync(sender, true);
            } else if ("unlink_gtnh".equals(action) || "unlink_external".equals(action)) {
                TeamService.unlinkExternalTeam(readUuid(payload, "teamId"), actorId, op);
                NetTeamSync.sendSync(sender, true);
            } else {
                NetError.send(sender, NetError.INVALID_ACTION, action);
            }
        } catch (SecurityException e) {
            NetError.send(sender, NetError.PERMISSION_DENIED, e.getMessage());
        } catch (IllegalArgumentException e) {
            NetError.send(sender, NetError.INVALID_PAYLOAD, e.getMessage());
        } catch (Exception e) {
            Tasket.LOG.error("Team action failed: {}", action, e);
            NetError.send(sender, NetError.SERVER_ERROR, e.getMessage());
        }
    }

    private static UUID readUuid(NBTTagCompound payload, String key) {
        if (!payload.hasKey(key) || payload.getString(key)
            .isEmpty()) return null;
        return UUID.fromString(payload.getString(key));
    }

    private static void linkCreatedTeam(Team team, String source, UUID actorId, boolean op) {
        if (team == null || source == null || source.isEmpty() || "LOCAL".equals(source)) return;
        if ("BETTER_QUESTING".equals(source)) {
            TeamProvider provider = TeamProviders.betterQuesting();
            int partyId = provider.getPartyForPlayer(actorId);
            if (partyId == TeamProvider.NO_PARTY) throw new IllegalArgumentException("未找到 BetterQuesting 队伍");
            TeamService.linkBetterQuestingParty(team.getId(), partyId, actorId, op);
        } else if ("GTNH_LIB".equals(source)) {
            TeamProvider provider = TeamProviders.gtnhLib();
            String teamKey = provider.getTeamKeyForPlayer(actorId);
            if (teamKey == null || teamKey.isEmpty()) throw new IllegalArgumentException("未找到 GTNHLib 队伍");
            TeamService.linkGtnhLibTeam(team.getId(), teamKey, actorId, op);
        }
    }

    private static boolean isOp(EntityPlayerMP player) {
        return player.mcServer != null && player.mcServer.getConfigurationManager()
            .func_152596_g(player.getGameProfile());
    }
}
