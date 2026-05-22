package com.pinkyudeer.tasket.gui.panel;

import java.util.UUID;

import net.minecraft.client.Minecraft;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;

import org.lwjgl.input.Keyboard;

import com.cleanroommc.modularui.api.IPanelHandler;
import com.cleanroommc.modularui.api.drawable.IKey;
import com.cleanroommc.modularui.api.widget.IWidget;
import com.cleanroommc.modularui.screen.ModularPanel;
import com.cleanroommc.modularui.widgets.ListWidget;
import com.cleanroommc.modularui.widgets.layout.Flow;
import com.pinkyudeer.tasket.Tasket;
import com.pinkyudeer.tasket.client.TeamClientActions;
import com.pinkyudeer.tasket.gui.GuiStyle;
import com.pinkyudeer.tasket.gui.drawable.FrostedGlassDrawable;
import com.pinkyudeer.tasket.gui.drawable.ShaderDrawable;
import com.pinkyudeer.tasket.gui.widget.StyledButtonWidget;

import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;

@SideOnly(Side.CLIENT)
public class TeamDetailPanel extends AnimatedPanel {

    private static int nextId;

    private final NBTTagCompound team;
    private final Runnable onChanged;
    private final String uid = String.valueOf(nextId++);
    private ListWidget<IWidget, ?> memberList;
    private IPanelHandler invitePickerHandler;
    private IPanelHandler transferPickerHandler;
    private IWidget pendingInviteAnchor;
    private IWidget pendingTransferAnchor;

    public TeamDetailPanel(NBTTagCompound team, Runnable onChanged) {
        super("tasket_team_detail");
        this.team = (NBTTagCompound) team.copy();
        this.onChanged = onChanged;
        size(460, 360);
        center();
        background(FrostedGlassDrawable.create(10f));
        disableHoverBackground();
        overlay(ShaderDrawable.panel(10f, 0x222233D8, GuiStyle.ACCENT));
        child(buildContent());
    }

    private String pn(String base) {
        return "team_detail_" + base + "_" + uid;
    }

    @Override
    public boolean disablePanelsBelow() {
        return true;
    }

    @Override
    public boolean onKeyPressed(char character, int keyCode) {
        boolean handled = super.onKeyPressed(character, keyCode);
        if (handled) return true;
        if (keyCode == Keyboard.KEY_ESCAPE) {
            closeIfOpen();
            return true;
        }
        return GuiStyle.shouldKeepTypingFocus(this, keyCode);
    }

    private Flow buildContent() {
        Flow root = Flow.column();
        root.widthRel(1f)
            .heightRel(1f)
            .padding(10)
            .name("team_detail/root");
        root.child(buildHeader());
        root.child(
            IKey.str(shorten(team.getString("description"), 72))
                .color(0xAAAAAAff)
                .scale(0.85f)
                .asWidget()
                .widthRel(1f)
                .height(14));
        root.child(buildSummary());
        Flow body = Flow.row();
        body.widthRel(1f)
            .height(205)
            .marginTop(6)
            .name("team_detail/body");
        body.child(buildMembers());
        body.child(buildActionsPane());
        root.child(body);
        root.child(buildFooter());
        return root;
    }

    private Flow buildHeader() {
        Flow header = Flow.row();
        header.widthRel(1f)
            .height(18)
            .name("team_detail/header");
        header.child(
            IKey.str(shorten(team.getString("name"), 34))
                .color(GuiStyle.ACCENT)
                .shadow(true)
                .asWidget()
                .widthRel(0.86f)
                .heightRel(1f));
        header.child(
            GuiStyle.smallButton("Close", GuiStyle.TOGGLE_INACTIVE, 0xCCCCCCff)
                .widthRel(0.12f)
                .height(15)
                .onMousePressed(btn -> {
                    closeIfOpen();
                    return true;
                }));
        return header;
    }

    private Flow buildSummary() {
        Flow row = Flow.row();
        row.widthRel(1f)
            .height(18)
            .marginTop(4);
        row.child(summaryText("Members: " + team.getInteger("totalMembers"), 0.5f));
        return row;
    }

    private IWidget summaryText(String text, float width) {
        return IKey.str(text)
            .color(0xCCCCCCff)
            .scale(0.85f)
            .asWidget()
            .widthRel(width)
            .heightRel(1f);
    }

    private Flow buildMembers() {
        Flow col = Flow.column();
        col.widthRel(0.55f)
            .heightRel(1f)
            .paddingRight(6)
            .name("team_detail/members_col");
        col.child(GuiStyle.label("Members", 0));
        memberList = new ListWidget<>();
        memberList.widthRel(1f)
            .height(188)
            .name("team_detail/members");
        populateMembers();
        col.child(memberList);
        return col;
    }

    private void populateMembers() {
        NBTTagList members = team.getTagList("members", 10);
        if (members.tagCount() == 0) {
            memberList.child(
                IKey.str("No members.")
                    .color(0x666666ff)
                    .scale(0.85f)
                    .asWidget()
                    .widthRel(1f)
                    .height(16));
            return;
        }
        for (int i = 0; i < members.tagCount(); i++) {
            NBTTagCompound member = members.getCompoundTagAt(i);
            memberList.child(memberRow(member));
        }
    }

    private IWidget memberRow(NBTTagCompound member) {
        String player = member.getString("playerId");
        String display = memberDisplayName(member);
        String role = member.getString("role");
        String status = member.getString("status");
        boolean canKick = isOwner() && isLocalTeam()
            && "ACTIVE".equals(status)
            && !player.equals(team.getString("ownerId"));
        Flow row = Flow.row();
        row.widthRel(1f)
            .height(17)
            .marginTop(1);
        row.child(
            GuiStyle
                .button(
                    shorten(display, 18) + "  " + emptyAs(role, "MEMBER") + "  " + emptyAs(status, "ACTIVE"),
                    GuiStyle.ITEM_BG,
                    GuiStyle.ITEM_HOVER,
                    GuiStyle.ITEM_PRESSED,
                    0xDDDDDDff,
                    0.78f)
                .widthRel(canKick ? 0.75f : 1f)
                .height(15)
                .onMousePressed(btn -> true));
        if (canKick) {
            row.child(
                GuiStyle.dangerButton("Kick")
                    .widthRel(0.22f)
                    .height(15)
                    .marginLeft(4)
                    .onMousePressed(btn -> {
                        runAction(() -> TeamClientActions.kickMember(team.getString("id"), player));
                        return true;
                    }));
        }
        return row;
    }

    private String memberDisplayName(NBTTagCompound member) {
        String displayName = member.getString("displayName");
        if (!displayName.isEmpty()) return displayName;
        String playerName = member.getString("playerName");
        if (!playerName.isEmpty()) return playerName;
        return "Unknown";
    }

    private Flow buildActionsPane() {
        Flow col = Flow.column();
        col.widthRel(0.45f)
            .heightRel(1f)
            .paddingLeft(6)
            .name("team_detail/actions_col");
        col.child(GuiStyle.label("Actions", 0));
        if (isLocalTeam() && isOwner()) {
            StyledButtonWidget inviteButton = GuiStyle.smallButton("Invite Online", GuiStyle.BUTTON_BG, 0xFFFFFFFF);
            inviteButton.widthRel(1f)
                .height(16)
                .marginTop(4)
                .onMousePressed(btn -> {
                    openInvitePicker(inviteButton);
                    return true;
                });
            col.child(inviteButton);
        }
        if (!isLocalTeam()) {
            col.child(
                GuiStyle.smallButton("Sync", GuiStyle.BUTTON_BG, 0xFFFFFFFF)
                    .widthRel(1f)
                    .height(16)
                    .marginTop(4)
                    .onMousePressed(btn -> {
                        runAction(this::syncExternal);
                        return true;
                    }));
        }
        if (isOwner() && isLocalTeam()) {
            StyledButtonWidget transferButton = GuiStyle.smallButton("Transfer", GuiStyle.BUTTON_BG, 0xFFFFFFFF);
            transferButton.widthRel(1f)
                .height(16)
                .marginTop(4)
                .onMousePressed(btn -> {
                    openTransferPicker(transferButton);
                    return true;
                });
            col.child(transferButton);
        }
        return col;
    }

    private Flow actionRow(String left, String right, Runnable leftAction, Runnable rightAction) {
        Flow row = Flow.row();
        row.widthRel(1f)
            .height(18)
            .marginTop(4);
        row.child(
            GuiStyle.smallButton(left, GuiStyle.BUTTON_BG, 0xFFFFFFFF)
                .widthRel(0.48f)
                .height(16)
                .onMousePressed(btn -> {
                    runAction(leftAction);
                    return true;
                }));
        row.child(
            GuiStyle.smallButton(right, GuiStyle.BUTTON_BG, 0xFFFFFFFF)
                .widthRel(0.48f)
                .height(16)
                .marginLeft(4)
                .onMousePressed(btn -> {
                    runAction(rightAction);
                    return true;
                }));
        return row;
    }

    private Flow buildFooter() {
        Flow footer = Flow.row();
        footer.widthRel(1f)
            .height(20)
            .marginTop(8);
        footer.child(
            IKey.str(shorten(team.getString("id"), 44))
                .color(0x777777ff)
                .scale(0.8f)
                .asWidget()
                .widthRel(0.72f)
                .heightRel(1f));
        footer.child(
            GuiStyle.dangerButton("Leave")
                .widthRel(0.24f)
                .height(18)
                .onMousePressed(btn -> {
                    runAction(this::leaveTeam);
                    return true;
                }));
        return footer;
    }

    private void openInvitePicker(IWidget anchor) {
        pendingInviteAnchor = anchor;
        if (invitePickerHandler == null) {
            invitePickerHandler = IPanelHandler
                .simple(this, (p, pl) -> buildPlayerPicker(pn("team_invite"), pendingInviteAnchor, true), true);
        } else {
            if (invitePickerHandler.isPanelOpen()) return;
            invitePickerHandler.deleteCachedPanel();
        }
        invitePickerHandler.openPanel();
    }

    private void openTransferPicker(IWidget anchor) {
        pendingTransferAnchor = anchor;
        if (transferPickerHandler == null) {
            transferPickerHandler = IPanelHandler
                .simple(this, (p, pl) -> buildPlayerPicker(pn("team_transfer"), pendingTransferAnchor, false), true);
        } else {
            if (transferPickerHandler.isPanelOpen()) return;
            transferPickerHandler.deleteCachedPanel();
        }
        transferPickerHandler.openPanel();
    }

    private ModularPanel buildPlayerPicker(String name, IWidget anchor, boolean onlineOnly) {
        ModularPanel panel = new InputSafePanel(name);
        panel.size(170, 150);
        placeBelow(panel, anchor);
        panel.background(FrostedGlassDrawable.create(6f));
        panel.disableHoverBackground();
        panel.overlay(ShaderDrawable.panel(6f, 0x1E1E38F0, GuiStyle.ACCENT));

        ListWidget<IWidget, ?> list = new ListWidget<>();
        list.widthRel(1f)
            .heightRel(1f)
            .padding(6);
        NBTTagList players = onlineOnly ? team.getTagList("onlinePlayers", 10) : activeMembers();
        int added = 0;
        for (int i = 0; i < players.tagCount(); i++) {
            NBTTagCompound player = players.getCompoundTagAt(i);
            String playerId = player.getString("playerId");
            if (playerId.isEmpty() || (!onlineOnly && playerId.equals(team.getString("ownerId")))) continue;
            if (onlineOnly && isActiveMember(playerId)) continue;
            list.child(
                GuiStyle.smallButton(memberDisplayName(player), GuiStyle.TOGGLE_INACTIVE, 0xDDDDDDff)
                    .widthRel(0.95f)
                    .height(14)
                    .marginTop(2)
                    .onMousePressed(btn -> {
                        if (onlineOnly) runAction(() -> TeamClientActions.invitePlayer(team.getString("id"), playerId));
                        else runAction(() -> TeamClientActions.transferOwner(team.getString("id"), playerId));
                        panel.closeIfOpen();
                        return true;
                    }));
            added++;
        }
        if (added == 0) {
            list.child(
                IKey.str(onlineOnly ? "No online players." : "No member.")
                    .color(0x666666ff)
                    .scale(0.85f)
                    .asWidget()
                    .widthRel(1f)
                    .height(16));
        }
        panel.child(list);
        return panel;
    }

    private NBTTagList activeMembers() {
        NBTTagList out = new NBTTagList();
        NBTTagList members = team.getTagList("members", 10);
        for (int i = 0; i < members.tagCount(); i++) {
            NBTTagCompound member = members.getCompoundTagAt(i);
            if ("ACTIVE".equals(member.getString("status"))) out.appendTag(member.copy());
        }
        return out;
    }

    private void placeBelow(ModularPanel panel, IWidget anchor) {
        if (anchor == null) {
            panel.center();
            return;
        }
        panel.relative(anchor)
            .left(0)
            .top(
                anchor.getArea()
                    .h() + 2);
    }

    private void leaveTeam() {
        TeamClientActions.leaveTeam(team.getString("id"));
        closeIfOpen();
    }

    private void syncExternal() {
        String source = emptyAs(team.getString("syncSource"), "LOCAL");
        if ("BETTER_QUESTING".equals(source)) {
            TeamClientActions.syncBetterQuesting(team.getString("id"));
        } else if ("GTNH_LIB".equals(source)) {
            TeamClientActions.syncGtnhLib(team.getString("id"));
        }
    }

    private void runAction(Runnable action) {
        try {
            action.run();
            if (onChanged != null) onChanged.run();
        } catch (Exception e) {
            Tasket.LOG.error("Failed to run team GUI action", e);
        }
    }

    private boolean isLocalTeam() {
        return "LOCAL".equals(emptyAs(team.getString("syncSource"), "LOCAL"));
    }

    private boolean isOwner() {
        UUID self = currentPlayerId();
        return self != null && self.toString()
            .equals(team.getString("ownerId"));
    }

    private UUID currentPlayerId() {
        if (Minecraft.getMinecraft() == null || Minecraft.getMinecraft().thePlayer == null) return null;
        return Minecraft.getMinecraft().thePlayer.getUniqueID();
    }

    private boolean isActiveMember(String playerId) {
        NBTTagList members = team.getTagList("members", 10);
        for (int i = 0; i < members.tagCount(); i++) {
            NBTTagCompound member = members.getCompoundTagAt(i);
            if (playerId.equals(member.getString("playerId")) && "ACTIVE".equals(member.getString("status"))) {
                return true;
            }
        }
        return false;
    }

    private static String emptyAs(String value, String fallback) {
        return value == null || value.isEmpty() ? fallback : value;
    }

    private static String shorten(String value, int max) {
        if (value == null) return "";
        return value.length() > max ? value.substring(0, max - 2) + ".." : value;
    }
}
