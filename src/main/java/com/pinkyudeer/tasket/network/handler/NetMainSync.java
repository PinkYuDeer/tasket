package com.pinkyudeer.tasket.network.handler;

import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.nbt.NBTTagCompound;

import com.pinkyudeer.tasket.Tasket;
import com.pinkyudeer.tasket.client.TaskClientStore;
import com.pinkyudeer.tasket.network.PacketIds;
import com.pinkyudeer.tasket.network.PacketSender;
import com.pinkyudeer.tasket.network.PacketTypeRegistry;

public final class NetMainSync {

    private static volatile long globalRev = 0;

    private NetMainSync() {}

    static long getRevision() {
        return globalRev;
    }

    static void bumpRevision() {
        globalRev++;
    }

    public static void registerHandler() {
        PacketTypeRegistry.INSTANCE.registerServerHandler(PacketIds.MAIN_SYNC, NetMainSync::onServer);
        PacketTypeRegistry.INSTANCE.registerClientHandler(PacketIds.MAIN_SYNC, NetMainSync::onClient);
    }

    public static void requestSync() {
        NetTaskSync.requestSync();
    }

    public static void sendReset(EntityPlayerMP player, boolean reset, boolean respond) {
        NBTTagCompound payload = new NBTTagCompound();
        payload.setBoolean("reset", reset);
        payload.setBoolean("respond", respond);
        PacketSender.INSTANCE.sendToPlayers(PacketIds.MAIN_SYNC, payload, player);
    }

    private static void onServer(NBTTagCompound payload, EntityPlayerMP sender) {
        if (sender == null) return;
        Tasket.LOG.debug("收到 main_sync 请求，转交任务同步");
        NetTaskSync.sendSync(sender);
        NetInviteSync.sendSync(sender);
    }

    private static void onClient(NBTTagCompound payload) {
        if (payload.getBoolean("reset")) {
            TaskClientStore.INSTANCE.reset();
        }
        if (payload.getBoolean("respond")) {
            requestSync();
        }
    }
}
