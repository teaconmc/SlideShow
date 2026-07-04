package org.teacon.slides.admin;

import com.mojang.logging.annotations.FieldsAreNonnullByDefault;
import com.mojang.logging.annotations.MethodsReturnNonnullByDefault;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.permissions.Permission;
import net.minecraft.server.permissions.PermissionLevel;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.server.permission.PermissionAPI;
import net.neoforged.neoforge.server.permission.events.PermissionGatherEvent;
import net.neoforged.neoforge.server.permission.nodes.PermissionDynamicContext;
import net.neoforged.neoforge.server.permission.nodes.PermissionNode;
import net.neoforged.neoforge.server.permission.nodes.PermissionTypes;
import org.teacon.slides.SlideShow;

import javax.annotation.Nullable;
import javax.annotation.ParametersAreNonnullByDefault;
import java.util.Objects;
import java.util.UUID;

@FieldsAreNonnullByDefault
@MethodsReturnNonnullByDefault
@ParametersAreNonnullByDefault
@EventBusSubscriber(modid = SlideShow.ID)
public final class SlidePermission {
    private static @Nullable PermissionNode<Boolean> INTERACT_CREATE_PERM;
    private static @Nullable PermissionNode<Boolean> INTERACT_EDIT_PERM;
    private static @Nullable PermissionNode<Boolean> INTERACT_PERM;
    private static @Nullable PermissionNode<Boolean> LIST_PERM;
    private static @Nullable PermissionNode<Boolean> BLOCK_PERM;
    private static @Nullable PermissionNode<Boolean> UNBLOCK_PERM;

    @SubscribeEvent
    public static void gatherPermNodes(PermissionGatherEvent.Nodes event) {
        // FIXME: permission resolving
        event.addNodes(INTERACT_PERM = new PermissionNode<>(SlideShow.ID,
                "interact.projector", PermissionTypes.BOOLEAN, SlidePermission::everyone));
        event.addNodes(INTERACT_CREATE_PERM = new PermissionNode<>(SlideShow.ID,
                "interact.projector.create_url", PermissionTypes.BOOLEAN, SlidePermission::everyone));
        event.addNodes(INTERACT_EDIT_PERM = new PermissionNode<>(SlideShow.ID,
                "interact.projector.edit_slide", PermissionTypes.BOOLEAN, SlidePermission::everyone));
        event.addNodes(LIST_PERM = new PermissionNode<>(SlideShow.ID,
                "interact_url.list", PermissionTypes.BOOLEAN, SlidePermission::operator));
        event.addNodes(BLOCK_PERM = new PermissionNode<>(SlideShow.ID,
                "interact_url.block", PermissionTypes.BOOLEAN, SlidePermission::operator));
        event.addNodes(UNBLOCK_PERM = new PermissionNode<>(SlideShow.ID,
                "interact_url.unblock", PermissionTypes.BOOLEAN, SlidePermission::operator));
    }

    public static boolean canInteract(CommandSourceStack source) {
        return test(source, Objects.requireNonNull(INTERACT_PERM));
    }

    public static boolean canInteractCreateUrl(CommandSourceStack source) {
        return test(source, Objects.requireNonNull(INTERACT_CREATE_PERM));
    }

    public static boolean canInteractEditSlide(CommandSourceStack source) {
        return test(source, Objects.requireNonNull(INTERACT_EDIT_PERM));
    }

    public static boolean canListUrl(CommandSourceStack source) {
        return test(source, Objects.requireNonNull(LIST_PERM));
    }

    public static boolean canBlockUrl(CommandSourceStack source) {
        return test(source, Objects.requireNonNull(BLOCK_PERM));
    }

    public static boolean canUnblockUrl(CommandSourceStack source) {
        return test(source, Objects.requireNonNull(UNBLOCK_PERM));
    }

    private static boolean test(CommandSourceStack source, PermissionNode<Boolean> node) {
        var profile = source.getSourceProfile();
        if (profile.isPresent()) {
            var uuid = profile.get().id();
            var player = source.getServer().getPlayerList().getPlayer(uuid);
            if (player != null) {
                return PermissionAPI.getPermission(player, node);
            }
            return PermissionAPI.getOfflinePermission(uuid, node);
        }
        return false;
    }

    private static boolean everyone(@Nullable ServerPlayer player, UUID uuid, PermissionDynamicContext<?>... context) {
        return true;
    }

    private static boolean operator(@Nullable ServerPlayer player, UUID uuid, PermissionDynamicContext<?>... context) {
        return player != null && player.permissions().hasPermission(new Permission.HasCommandLevel(PermissionLevel.GAMEMASTERS));
    }
}
