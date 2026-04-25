package com.pinkyudeer.tasket.loader;

import net.minecraft.block.Block;

import com.pinkyudeer.tasket.inGame.block.BlockDebugTasket;
import com.pinkyudeer.tasket.inGame.block.BlockViewer;

import cpw.mods.fml.common.event.FMLPreInitializationEvent;
import cpw.mods.fml.common.registry.GameRegistry;

public class BlockLoader {

    public static final BlockDebugTasket debugTasket = new BlockDebugTasket();
    public static final BlockViewer viewer = new BlockViewer();

    public BlockLoader(FMLPreInitializationEvent event) {
        init();
    }

    private static void registerBlock(Block block, String name) {
        GameRegistry.registerBlock(block, name);
    }

    public static void init() {
        registerBlock(debugTasket, "debugTasket");
        registerBlock(viewer, "viewer");
    }
}
