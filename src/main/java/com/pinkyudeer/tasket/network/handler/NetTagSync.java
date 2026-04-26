package com.pinkyudeer.tasket.network.handler;

import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;

import com.pinkyudeer.tasket.client.TaskClientStore;
import com.pinkyudeer.tasket.network.PacketIds;
import com.pinkyudeer.tasket.network.PacketSender;
import com.pinkyudeer.tasket.network.PacketTypeRegistry;
import com.pinkyudeer.tasket.task.entity.Tag;
import com.pinkyudeer.tasket.task.service.TagService;

public final class NetTagSync {

    private NetTagSync() {}

    public static void registerHandler() {
        PacketTypeRegistry.INSTANCE.registerServerHandler(PacketIds.TAG_SYNC, NetTagSync::onServer);
        PacketTypeRegistry.INSTANCE.registerClientHandler(PacketIds.TAG_SYNC, NetTagSync::onClient);
    }

    public static void requestSync() {
        PacketSender.INSTANCE.sendToServer(PacketIds.TAG_SYNC, new NBTTagCompound());
    }

    public static void sendSync(EntityPlayerMP player, boolean merge) {
        NBTTagCompound payload = new NBTTagCompound();
        payload.setBoolean("merge", merge);
        NBTTagList list = new NBTTagList();
        for (Tag tag : TagService.getVisibleTagsForPlayer(player.getUniqueID(), isOp(player))) {
            list.appendTag(writeTag(tag));
        }
        payload.setTag("data", list);
        PacketSender.INSTANCE.sendToPlayers(PacketIds.TAG_SYNC, payload, player);
    }

    private static void onServer(NBTTagCompound payload, EntityPlayerMP sender) {
        if (sender != null) sendSync(sender, false);
    }

    private static void onClient(NBTTagCompound payload) {
        TaskClientStore.INSTANCE.acceptTagSync(payload.getTagList("data", 10), payload.getBoolean("merge"));
    }

    static NBTTagCompound writeTag(Tag tag) {
        NBTTagCompound out = new NBTTagCompound();
        out.setString(
            "id",
            tag.getId() == null ? ""
                : tag.getId()
                    .toString());
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

    private static String safe(String value) {
        return value == null ? "" : value;
    }

    private static boolean isOp(EntityPlayerMP player) {
        return player.mcServer != null && player.mcServer.getConfigurationManager()
            .func_152596_g(player.getGameProfile());
    }
}
