package com.pinkyudeer.tasket.core;

import com.pinkyudeer.tasket.Tasket;
import com.pinkyudeer.tasket.network.PacketTypeRegistry;
import com.pinkyudeer.tasket.network.TasketPacket;

import cpw.mods.fml.common.network.NetworkRegistry;
import cpw.mods.fml.common.network.simpleimpl.SimpleNetworkWrapper;
import cpw.mods.fml.relauncher.Side;

public class PacketHandler {

    public static final SimpleNetworkWrapper INSTANCE = NetworkRegistry.INSTANCE.newSimpleChannel(Tasket.MODID);

    public static void registerMessages() {
        PacketTypeRegistry.INSTANCE.init();
        INSTANCE.registerMessage(TasketPacket.ServerHandler.class, TasketPacket.class, 0, Side.SERVER);
    }
}
