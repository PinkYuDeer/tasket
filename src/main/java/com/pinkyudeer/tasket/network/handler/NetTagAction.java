package com.pinkyudeer.tasket.network.handler;

import java.util.UUID;

import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.nbt.NBTTagCompound;

import com.pinkyudeer.tasket.Tasket;
import com.pinkyudeer.tasket.network.PacketIds;
import com.pinkyudeer.tasket.network.PacketSender;
import com.pinkyudeer.tasket.network.PacketTypeRegistry;
import com.pinkyudeer.tasket.task.entity.Tag;
import com.pinkyudeer.tasket.task.service.TagService;
import com.pinkyudeer.tasket.task.service.TeamService;

public final class NetTagAction {

    private NetTagAction() {}

    public static void registerHandler() {
        PacketTypeRegistry.INSTANCE.registerServerHandler(PacketIds.TAG_ACTION, NetTagAction::onServer);
    }

    public static void sendAction(NBTTagCompound payload) {
        PacketSender.INSTANCE.sendToServer(PacketIds.TAG_ACTION, payload);
    }

    private static void onServer(NBTTagCompound payload, EntityPlayerMP sender) {
        if (sender == null) return;
        String action = payload.getString("action");
        try {
            if ("create".equals(action)) {
                UUID ownerTeamId = readUuid(payload, "ownerTeamId");
                Tag.TagScope scope = readScope(payload);
                Tag tag = TagService.createTag(
                    TeamService.contextFor(sender.getUniqueID(), ownerTeamId, isOp(sender)),
                    payload.getString("name"),
                    payload.getString("description"),
                    payload.getString("colorCode"),
                    scope,
                    ownerTeamId);
                if (tag == null) NetError.send(sender, NetError.SERVER_ERROR, "tag create failed");
                else NetTagSync.sendSync(sender, true);
            } else if ("update".equals(action)) {
                UUID tagId = readUuid(payload, "tagId");
                Tag tag = TagService.updateTag(
                    TeamService.contextFor(sender.getUniqueID(), null, isOp(sender)),
                    tagId,
                    payload.getString("name"),
                    payload.getString("description"),
                    payload.getString("colorCode"));
                if (tag == null) NetError.send(sender, NetError.SERVER_ERROR, "tag update failed");
                else NetTagSync.sendSync(sender, true);
            } else {
                NetError.send(sender, NetError.INVALID_ACTION, action);
            }
        } catch (SecurityException e) {
            NetError.send(sender, NetError.PERMISSION_DENIED, e.getMessage());
        } catch (IllegalArgumentException e) {
            NetError.send(sender, NetError.INVALID_PAYLOAD, e.getMessage());
        } catch (Exception e) {
            Tasket.LOG.error("Tag action failed: {}", action, e);
            NetError.send(sender, NetError.SERVER_ERROR, e.getMessage());
        }
    }

    private static Tag.TagScope readScope(NBTTagCompound payload) {
        if (!payload.hasKey("scope") || payload.getString("scope")
            .isEmpty()) return Tag.TagScope.PUBLIC;
        return Tag.TagScope.valueOf(payload.getString("scope"));
    }

    private static UUID readUuid(NBTTagCompound payload, String key) {
        if (!payload.hasKey(key) || payload.getString(key)
            .isEmpty()) return null;
        return UUID.fromString(payload.getString(key));
    }

    private static boolean isOp(EntityPlayerMP player) {
        return player.mcServer != null && player.mcServer.getConfigurationManager()
            .func_152596_g(player.getGameProfile());
    }
}
