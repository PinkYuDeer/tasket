package com.pinkyudeer.tasket.inGame.item;

import net.minecraft.item.Item;

import com.pinkyudeer.tasket.loader.CreativeTabsLoader;

public class ItemHandViewer extends Item {

    public ItemHandViewer() {
        super();
        this.setUnlocalizedName("handViewer");
        this.setTextureName("tasket:hand_viewer");
        this.setCreativeTab(CreativeTabsLoader.creativeTabTasket);
    }
}
