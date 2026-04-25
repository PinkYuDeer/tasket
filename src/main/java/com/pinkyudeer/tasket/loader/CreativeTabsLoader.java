package com.pinkyudeer.tasket.loader;

import net.minecraft.creativetab.CreativeTabs;

import com.pinkyudeer.tasket.gui.CreativeTabsTasket;

import cpw.mods.fml.common.event.FMLPreInitializationEvent;

public class CreativeTabsLoader {

    public static CreativeTabs creativeTabTasket;

    public CreativeTabsLoader(FMLPreInitializationEvent event) {
        init();
    }

    public static void init() {
        creativeTabTasket = new CreativeTabsTasket();
    }
}
