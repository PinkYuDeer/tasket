package com.pinkyudeer.tasket.client.network;

import net.minecraft.client.Minecraft;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.ResourceLocation;

import com.pinkyudeer.tasket.Tasket;
import com.pinkyudeer.tasket.network.PacketAssembly;
import com.pinkyudeer.tasket.network.PacketTypeRegistry;
import com.pinkyudeer.tasket.network.TasketPacket;

import cpw.mods.fml.common.network.simpleimpl.IMessage;
import cpw.mods.fml.common.network.simpleimpl.IMessageHandler;
import cpw.mods.fml.common.network.simpleimpl.MessageContext;

public class ClientTasketPacketHandler implements IMessageHandler<TasketPacket, IMessage> {

    @Override
    public IMessage onMessage(TasketPacket packet, MessageContext ctx) {
        if (packet == null || packet.getTags() == null) {
            Tasket.LOG.error("Received invalid tasket packet on client");
            return null;
        }

        NBTTagCompound message = PacketAssembly.INSTANCE.assemblePacket(null, packet.getTags());
        if (message == null) return null;
        if (!message.hasKey("ID")) {
            Tasket.LOG.warn("Received tasket client packet without ID");
            return null;
        }

        ResourceLocation id = new ResourceLocation(message.getString("ID"));
        PacketTypeRegistry.ClientPacketHandler handler = PacketTypeRegistry.INSTANCE.getClientHandler(id);
        if (handler == null) {
            Tasket.LOG.warn("Received tasket client packet with unknown ID: {}", id);
            return null;
        }

        Minecraft.getMinecraft()
            .func_152343_a(() -> {
                handler.handle(message);
                return null;
            });
        return null;
    }
}
