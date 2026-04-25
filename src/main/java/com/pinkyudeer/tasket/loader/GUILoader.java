package com.pinkyudeer.tasket.loader;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.world.World;

import com.pinkyudeer.tasket.Tasket;

import cpw.mods.fml.common.network.IGuiHandler;
import cpw.mods.fml.common.network.NetworkRegistry;

public class GUILoader implements IGuiHandler {

    public GUILoader() {
        NetworkRegistry.INSTANCE.registerGuiHandler(Tasket.instance, this);
    }

    @Override
    public Object getServerGuiElement(int ID, EntityPlayer player, World world, int x, int y, int z) {
        // TODO
        return null;
    }

    @Override
    public Object getClientGuiElement(int ID, EntityPlayer player, World world, int x, int y, int z) {
        // TODO
        return null;
    }
}
