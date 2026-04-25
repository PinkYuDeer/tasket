package com.pinkyudeer.tasket.core;

import java.io.File;
import java.io.IOException;

import net.minecraft.client.Minecraft;
import net.minecraft.server.integrated.IntegratedServer;
import net.minecraft.world.World;

import com.pinkyudeer.tasket.client.network.ClientTasketPacketHandler;
import com.pinkyudeer.tasket.gui.KeyBindGuiHandler;
import com.pinkyudeer.tasket.gui.ModularTheme;
import com.pinkyudeer.tasket.helper.ModFileHelper;
import com.pinkyudeer.tasket.network.TasketPacket;
import com.pinkyudeer.tasket.render.BlurHandler;

import cpw.mods.fml.common.FMLCommonHandler;
import cpw.mods.fml.common.Mod;
import cpw.mods.fml.common.event.FMLInitializationEvent;
import cpw.mods.fml.common.event.FMLPreInitializationEvent;
import cpw.mods.fml.relauncher.Side;

public class ClientProxy extends CommonProxy {

    // Override CommonProxy methods here, if you want a different behaviour on the client (e.g. registering renders).
    // Don't forget to call the super methods as well.

    @Mod.EventHandler
    public void preInit(FMLPreInitializationEvent event) {
        super.preInit(event);
        FMLCommonHandler.instance()
            .bus()
            .register(new KeyBindGuiHandler());
        ModularTheme.init();
    }

    @Mod.EventHandler
    public void init(FMLInitializationEvent event) {
        super.init(event);
        PacketHandler.INSTANCE.registerMessage(ClientTasketPacketHandler.class, TasketPacket.class, 0, Side.CLIENT);
        BlurHandler.init();
    }

    @Override
    public File getBaseDir() {
        return Minecraft.getMinecraft().mcDataDir;
    }

    @Override
    public File getCurrentWorldDir(World world) throws IOException {
        IntegratedServer server = Minecraft.getMinecraft()
            .getIntegratedServer();
        File savesDir = ModFileHelper.getSavesDir();
        if (server == null) return savesDir.getCanonicalFile();
        return new File(savesDir, server.getFolderName()).getCanonicalFile();
    }
}
