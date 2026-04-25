package com.pinkyudeer.tasket.gui;

import net.minecraft.creativetab.CreativeTabs;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;

import com.pinkyudeer.tasket.loader.BlockLoader;

public class CreativeTabsTasket extends CreativeTabs {

    public CreativeTabsTasket() {
        super("tasket");
    }

    @Override
    public Item getTabIconItem() {
        return this.getIconItemStack()
            .getItem();
    }

    @Override
    public ItemStack getIconItemStack() {
        return new ItemStack(BlockLoader.debugTasket);
    }
}
