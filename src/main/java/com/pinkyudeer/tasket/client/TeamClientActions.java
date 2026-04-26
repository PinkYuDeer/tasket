package com.pinkyudeer.tasket.client;

import net.minecraft.nbt.NBTTagCompound;

import com.pinkyudeer.tasket.network.handler.NetTeamAction;

public final class TeamClientActions {

    private TeamClientActions() {}

    public static void createTeam(String name, String description) {
        createTeam(name, description, "LOCAL");
    }

    public static void createTeam(String name, String description, String source) {
        NBTTagCompound payload = new NBTTagCompound();
        payload.setString("action", "create");
        payload.setString("name", safe(name));
        payload.setString("description", safe(description));
        payload.setString("source", safe(source));
        NetTeamAction.sendAction(payload);
    }

    public static void invitePlayer(String teamId, String playerId) {
        NBTTagCompound payload = new NBTTagCompound();
        payload.setString("action", "invite");
        payload.setString("teamId", safe(teamId));
        payload.setString("playerId", safe(playerId));
        NetTeamAction.sendAction(payload);
    }

    public static void leaveTeam(String teamId) {
        NBTTagCompound payload = new NBTTagCompound();
        payload.setString("action", "leave");
        payload.setString("teamId", safe(teamId));
        NetTeamAction.sendAction(payload);
    }

    public static void kickMember(String teamId, String playerId) {
        NBTTagCompound payload = new NBTTagCompound();
        payload.setString("action", "kick");
        payload.setString("teamId", safe(teamId));
        payload.setString("playerId", safe(playerId));
        NetTeamAction.sendAction(payload);
    }

    public static void transferOwner(String teamId, String playerId) {
        NBTTagCompound payload = new NBTTagCompound();
        payload.setString("action", "transfer_owner");
        payload.setString("teamId", safe(teamId));
        payload.setString("playerId", safe(playerId));
        NetTeamAction.sendAction(payload);
    }

    public static void linkBetterQuesting(String teamId, int partyId) {
        NBTTagCompound payload = new NBTTagCompound();
        payload.setString("action", "link_bq");
        payload.setString("teamId", safe(teamId));
        payload.setInteger("partyId", partyId);
        NetTeamAction.sendAction(payload);
    }

    public static void syncBetterQuesting(String teamId) {
        sync(teamId, "sync_bq");
    }

    public static void linkGtnhLib(String teamId, String teamKey) {
        NBTTagCompound payload = new NBTTagCompound();
        payload.setString("action", "link_gtnh");
        payload.setString("teamId", safe(teamId));
        payload.setString("teamKey", safe(teamKey));
        NetTeamAction.sendAction(payload);
    }

    public static void syncGtnhLib(String teamId) {
        sync(teamId, "sync_gtnh");
    }

    public static void unlinkExternal(String teamId) {
        sync(teamId, "unlink_external");
    }

    private static void sync(String teamId, String action) {
        NBTTagCompound payload = new NBTTagCompound();
        payload.setString("action", action);
        payload.setString("teamId", safe(teamId));
        NetTeamAction.sendAction(payload);
    }

    private static String safe(String value) {
        return value == null ? "" : value;
    }
}
