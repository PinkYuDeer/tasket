package com.pinkyudeer.tasket.core;

import java.io.IOException;

import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.world.WorldEvent;

import com.pinkyudeer.tasket.Tasket;
import com.pinkyudeer.tasket.db.SQLiteManager;
import com.pinkyudeer.tasket.helper.ModFileHelper;
import com.pinkyudeer.tasket.network.handler.NetMainSync;
import com.pinkyudeer.tasket.task.TaskSqlHelper;
import com.pinkyudeer.tasket.task.service.TeamService;

import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.common.gameevent.PlayerEvent;

public class EventHandler {

    public static void registerCommonEvents() {
        MinecraftForge.EVENT_BUS.register(new WorldHandler());
    }

    /**
     * 世界事件处理
     */
    public static class WorldHandler {

        @SubscribeEvent
        public void onWorldLoad(WorldEvent.Load event) throws IOException {
            if (event.world.provider.dimensionId != 0) return;

            ModFileHelper.updateModWorldDir(Tasket.proxy.getCurrentWorldDir(event.world));
            SQLiteManager.initSqlite();
        }

        @SubscribeEvent
        public void onWorldSave(WorldEvent.Save event) {
            // TODO: 测试其他世界暂停是否会保存主世界
            if (event.world.provider.dimensionId != 0) return;

            Tasket.LOG.info("World save event triggered");

            SQLiteManager.saveDataFromMemoryToFile();
        }

        @SubscribeEvent
        public void onWorldUnload(WorldEvent.Unload event) {
            if (event.world.provider.dimensionId != 0) return;

            Tasket.LOG.info("World unload event triggered");

            ModFileHelper.updateModWorldDir(null);

            SQLiteManager.close();
        }
    }

    public static class serverHandler {

        @SubscribeEvent
        public void onPlayerLogin(PlayerEvent.PlayerLoggedInEvent event) {
            Tasket.LOG.info("Player logged in: {}", event.player.getDisplayName());

            TaskSqlHelper.player.login(event.player);

            if (event.player instanceof EntityPlayerMP) {
                EntityPlayerMP player = (EntityPlayerMP) event.player;
                TeamService.syncLinkedTeamsForPlayer(player.getUniqueID(), isOp(player));
                NetMainSync.sendReset(player, true, true);
            }
        }

        @SubscribeEvent
        public void onPlayerLogout(PlayerEvent.PlayerLoggedOutEvent event) {
            Tasket.LOG.info("Player logged out: {}", event.player.getDisplayName());
        }

        private boolean isOp(EntityPlayerMP player) {
            return player.mcServer != null && player.mcServer.getConfigurationManager()
                .func_152596_g(player.getGameProfile());
        }
    }
}
