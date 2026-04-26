package com.pinkyudeer.tasket.client;

import net.minecraft.nbt.NBTTagCompound;

import com.pinkyudeer.tasket.network.handler.NetTagAction;
import com.pinkyudeer.tasket.task.entity.Tag;

public final class TagClientActions {

    private TagClientActions() {}

    public static void createTag(String name, String description, String colorCode, Tag.TagScope scope,
        String ownerTeamId) {
        NBTTagCompound payload = new NBTTagCompound();
        payload.setString("action", "create");
        payload.setString("name", safe(name));
        payload.setString("description", safe(description));
        payload.setString("colorCode", safe(colorCode));
        payload.setString("scope", scope == null ? Tag.TagScope.PUBLIC.name() : scope.name());
        payload.setString("ownerTeamId", safe(ownerTeamId));
        NetTagAction.sendAction(payload);
    }

    public static void updateTag(String tagId, String name, String description, String colorCode) {
        NBTTagCompound payload = new NBTTagCompound();
        payload.setString("action", "update");
        payload.setString("tagId", safe(tagId));
        payload.setString("name", safe(name));
        payload.setString("description", safe(description));
        payload.setString("colorCode", safe(colorCode));
        NetTagAction.sendAction(payload);
    }

    private static String safe(String value) {
        return value == null ? "" : value;
    }
}
