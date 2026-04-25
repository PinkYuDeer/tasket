package com.pinkyudeer.tasket.inGame.block;

import net.minecraft.block.Block;
import net.minecraft.block.material.Material;

import com.pinkyudeer.tasket.loader.CreativeTabsLoader;

public class BlockDebugTasket extends Block {

    public BlockDebugTasket() {
        super(Material.gourd);
        this.setBlockName("debugTasket");
        this.setBlockTextureName("tasket:debug_tasket");
        this.setHardness(50F);
        this.setResistance(6000000.0F);
        this.setLightLevel(15.0F);
        this.setLightOpacity(0);
        this.setStepSound(soundTypeMetal);
        this.setCreativeTab(CreativeTabsLoader.creativeTabTasket);
    }
}
