package com.pinkyudeer.tasket.network.handler;

import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;

import com.pinkyudeer.tasket.client.TaskClientStore;
import com.pinkyudeer.tasket.network.PacketIds;
import com.pinkyudeer.tasket.network.PacketSender;
import com.pinkyudeer.tasket.network.PacketTypeRegistry;

public final class NetInviteSync {

    private NetInviteSync() {}

    public static void registerHandler() {
        PacketTypeRegistry.INSTANCE.registerServerHandler(PacketIds.INVITE_SYNC, NetInviteSync::onServer);
        PacketTypeRegistry.INSTANCE.registerClientHandler(PacketIds.INVITE_SYNC, NetInviteSync::onClient);
    }

    public static void sendSync(EntityPlayerMP player) {
        NBTTagCompound payload = new NBTTagCompound();
        payload.setTag("data", new NBTTagList());
        PacketSender.INSTANCE.sendToPlayers(PacketIds.INVITE_SYNC, payload, player);
    }

    private static void onServer(NBTTagCompound payload, EntityPlayerMP sender) {
        if (sender != null) sendSync(sender);
    }

    private static void onClient(NBTTagCompound payload) {
        TaskClientStore.INSTANCE.acceptInviteSync(payload.getTagList("data", 10));
    }
}
