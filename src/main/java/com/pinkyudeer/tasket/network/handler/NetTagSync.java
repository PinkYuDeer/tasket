package com.pinkyudeer.tasket.network.handler;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;

import com.pinkyudeer.tasket.client.TaskClientStore;
import com.pinkyudeer.tasket.core.ServerTaskScheduler;
import com.pinkyudeer.tasket.db.AsyncSqlExecutor;
import com.pinkyudeer.tasket.network.PacketIds;
import com.pinkyudeer.tasket.network.PacketSender;
import com.pinkyudeer.tasket.network.PacketTypeRegistry;
import com.pinkyudeer.tasket.task.entity.Tag;
import com.pinkyudeer.tasket.task.entity.Team;
import com.pinkyudeer.tasket.task.service.TagService;

public final class NetTagSync {

    private NetTagSync() {}

    public static void registerHandler() {
        PacketTypeRegistry.INSTANCE.registerServerHandler(PacketIds.TAG_SYNC, NetTagSync::onServer);
        PacketTypeRegistry.INSTANCE.registerClientHandler(PacketIds.TAG_SYNC, NetTagSync::onClient);
    }

    public static void requestSync() {
        TaskClientStore.INSTANCE.markTagLoading();
        NBTTagCompound payload = new NBTTagCompound();
        payload.setTag("versions", TaskClientStore.INSTANCE.getTagVersionList());
        payload.setLong("rev", TaskClientStore.INSTANCE.getSyncGlobalRev());
        PacketSender.INSTANCE.sendToServer(PacketIds.TAG_SYNC, payload);
    }

    public static void sendSync(EntityPlayerMP player) {
        NetMainSync.bumpRevision();
        sendDeltaAsync(player, new HashMap<>(), NetMainSync.getRevision());
    }

    static void sendDelta(EntityPlayerMP player, Map<String, String> clientVersions, List<Team> visibleTeams) {
        NBTTagCompound payload = buildDeltaPayload(player.getUniqueID(), isOp(player), clientVersions, visibleTeams);
        PacketSender.INSTANCE.sendToPlayers(PacketIds.TAG_SYNC, payload, player);
    }

    static void sendEmptyDelta(EntityPlayerMP player) {
        NBTTagCompound payload = new NBTTagCompound();
        payload.setTag("updated", new NBTTagList());
        payload.setTag("deleted", new NBTTagList());
        payload.setLong("rev", NetMainSync.getRevision());
        PacketSender.INSTANCE.sendToPlayers(PacketIds.TAG_SYNC, payload, player);
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
        java.util.UUID viewerId = sender.getUniqueID();
        boolean op = isOp(sender);
        String key = "tag_sync:" + viewerId
            + ':'
            + op
            + ':'
            + clientVersions.hashCode()
            + ':'
            + clientRev
            + ':'
            + NetMainSync.getRevision();
        AsyncSqlExecutor.INSTANCE
            .submit(
                key,
                new HashSet<>(java.util.Arrays.asList("tags", "teams", "team_members", "entity_events")),
                () -> buildDeltaPayload(viewerId, op, clientVersions, null))
            .whenComplete((response, error) -> ServerTaskScheduler.INSTANCE.schedule(() -> {
                if (error != null) {
                    NetError.send(sender, NetError.SERVER_ERROR, "tag sync failed");
                    return;
                }
                PacketSender.INSTANCE.sendToPlayers(PacketIds.TAG_SYNC, response, sender);
            }, false));
    }

    private static void onClient(NBTTagCompound payload) {
        TaskClientStore.INSTANCE.acceptTagDelta(payload.getTagList("updated", 10), payload.getTagList("deleted", 10));
        TaskClientStore.INSTANCE.updateSyncRevision(payload.getLong("rev"));
    }

    private static NBTTagCompound buildDeltaPayload(java.util.UUID viewerId, boolean op,
        Map<String, String> clientVersions, List<Team> visibleTeams) {
        List<Tag> visibleTags = TagService.getVisibleTagsForPlayer(viewerId, op, visibleTeams);

        Set<String> visibleIds = new HashSet<>();
        List<Tag> updated = new ArrayList<>();
        for (Tag tag : visibleTags) {
            if (tag == null || tag.getId() == null) continue;
            String id = tag.getId()
                .toString();
            visibleIds.add(id);
            String serverVersion = tagVersion(tag);
            String clientVersion = clientVersions.get(id);
            if (!serverVersion.equals(clientVersion)) {
                updated.add(tag);
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
        for (Tag tag : updated) {
            updatedList.appendTag(writeTag(tag));
        }

        NBTTagCompound payload = new NBTTagCompound();
        payload.setTag("updated", updatedList);
        payload.setTag("deleted", deletedList);
        payload.setLong("rev", NetMainSync.getRevision());
        return payload;
    }

    static NBTTagCompound writeTag(Tag tag) {
        NBTTagCompound out = new NBTTagCompound();
        out.setString(
            "id",
            tag.getId() == null ? ""
                : tag.getId()
                    .toString());
        out.setString("_v", tagVersion(tag));
        out.setString("name", safe(tag.getName()));
        out.setString("description", safe(tag.getDescription()));
        out.setString("colorCode", safe(tag.getColorCode()));
        out.setString("fontColorCode", safe(tag.getFontColorCode()));
        out.setString(
            "scope",
            tag.getScope() == null ? ""
                : tag.getScope()
                    .name());
        out.setString(
            "ownerId",
            tag.getOwnerId() == null ? ""
                : tag.getOwnerId()
                    .toString());
        out.setString(
            "ownerTeamId",
            tag.getOwnerTeamId() == null ? ""
                : tag.getOwnerTeamId()
                    .toString());
        out.setInteger("linkedTaskCount", tag.getLinkedTaskCount() == null ? 0 : tag.getLinkedTaskCount());
        out.setInteger("linkedTeamCount", tag.getLinkedTeamCount() == null ? 0 : tag.getLinkedTeamCount());
        out.setInteger("linkedPlayerCount", tag.getLinkedPlayerCount() == null ? 0 : tag.getLinkedPlayerCount());
        return out;
    }

    private static String tagVersion(Tag tag) {
        return String.valueOf(tag.getVersion() == null ? 0 : tag.getVersion());
    }

    private static String safe(String value) {
        return value == null ? "" : value;
    }

    private static boolean isOp(EntityPlayerMP player) {
        return player.mcServer != null && player.mcServer.getConfigurationManager()
            .func_152596_g(player.getGameProfile());
    }
}
