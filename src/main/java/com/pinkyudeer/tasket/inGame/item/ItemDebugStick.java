package com.pinkyudeer.tasket.inGame.item;

import net.minecraft.item.Item;

import com.pinkyudeer.tasket.loader.CreativeTabsLoader;

public class ItemDebugStick extends Item {

    public ItemDebugStick() {
        super();
        this.setUnlocalizedName("debugStick");
        this.setTextureName("tasket:debug_stick");
        this.setCreativeTab(CreativeTabsLoader.creativeTabTasket);
    }
}
