package com.pinkyudeer.tasket.loader;

import com.pinkyudeer.tasket.task.TaskCommand;

import cpw.mods.fml.common.event.FMLServerStartingEvent;

public class CommandLoader {

    public static void init(FMLServerStartingEvent event) {
        event.registerServerCommand(new TaskCommand());
    }
}
