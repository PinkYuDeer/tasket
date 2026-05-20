package com.pinkyudeer.tasket.network;

import java.util.UUID;

import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.ResourceLocation;

import com.pinkyudeer.tasket.Tasket;
import com.pinkyudeer.tasket.core.ServerTaskScheduler;

import cpw.mods.fml.common.network.ByteBufUtils;
import cpw.mods.fml.common.network.simpleimpl.IMessage;
import cpw.mods.fml.common.network.simpleimpl.IMessageHandler;
import cpw.mods.fml.common.network.simpleimpl.MessageContext;
import io.netty.buffer.ByteBuf;
import lombok.Getter;

@Getter
public class TasketPacket implements IMessage {

    private NBTTagCompound tags = new NBTTagCompound();

    @SuppressWarnings("unused")
    public TasketPacket() {}

    public TasketPacket(NBTTagCompound tags) {
        this.tags = tags;
    }

    @Override
    public void fromBytes(ByteBuf buf) {
        tags = ByteBufUtils.readTag(buf);
    }

    @Override
    public void toBytes(ByteBuf buf) {
        ByteBufUtils.writeTag(buf, tags);
    }

    public static class ServerHandler implements IMessageHandler<TasketPacket, IMessage> {

        @Override
        public IMessage onMessage(TasketPacket packet, MessageContext ctx) {
            if (packet == null || packet.tags == null || ctx.getServerHandler() == null) {
                Tasket.LOG.error("Received invalid tasket packet on server");
                return null;
            }

            EntityPlayerMP sender = ctx.getServerHandler().playerEntity;
            UUID owner = sender == null ? null : sender.getUniqueID();
            NBTTagCompound message = PacketAssembly.INSTANCE.assemblePacket(owner, packet.tags);
            if (message == null) return null;
            if (!message.hasKey("ID")) {
                Tasket.LOG.warn("Received tasket server packet without ID");
                return null;
            }

            ResourceLocation id = new ResourceLocation(message.getString("ID"));
            PacketTypeRegistry.ServerPacketHandler handler = PacketTypeRegistry.INSTANCE.getServerHandler(id);
            if (handler == null) {
                Tasket.LOG.warn("Received tasket server packet with unknown ID: {}", id);
                return null;
            }

            ServerTaskScheduler.INSTANCE.schedule(() -> handler.handle(message, sender), true);
            return null;
        }
    }
}
