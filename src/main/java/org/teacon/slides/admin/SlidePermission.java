package org.teacon.slides.admin;

import net.minecraft.FieldsAreNonnullByDefault;
import net.minecraft.MethodsReturnNonnullByDefault;
import net.minecraft.commands.CommandSource;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.rcon.RconConsoleSource;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.server.permission.PermissionAPI;
import net.minecraftforge.server.permission.events.PermissionGatherEvent;
import net.minecraftforge.server.permission.nodes.PermissionDynamicContext;
import net.minecraftforge.server.permission.nodes.PermissionNode;
import net.minecraftforge.server.permission.nodes.PermissionTypes;
import org.teacon.slides.SlideShow;

import javax.annotation.Nullable;
import javax.annotation.ParametersAreNonnullByDefault;
import java.util.Objects;
import java.util.UUID;

@FieldsAreNonnullByDefault
@MethodsReturnNonnullByDefault
@ParametersAreNonnullByDefault
@Mod.EventBusSubscriber(bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class SlidePermission {
    private static @Nullable PermissionNode<Boolean> INTERACT_CREATE_PERM;
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
        event.addNodes(LIST_PERM = new PermissionNode<>(SlideShow.ID,
                "interact_url.list", PermissionTypes.BOOLEAN, SlidePermission::operator));
        event.addNodes(BLOCK_PERM = new PermissionNode<>(SlideShow.ID,
                "interact_url.block", PermissionTypes.BOOLEAN, SlidePermission::operator));
        event.addNodes(UNBLOCK_PERM = new PermissionNode<>(SlideShow.ID,
                "interact_url.unblock", PermissionTypes.BOOLEAN, SlidePermission::operator));
    }

    public static boolean canInteract(@Nullable CommandSource source) {
        if (source instanceof ServerPlayer sp) {
            return PermissionAPI.getPermission(sp, Objects.requireNonNull(INTERACT_PERM));
        }
        return false;
    }

    public static boolean canInteractCreateUrl(@Nullable CommandSource source) {
        if (source instanceof ServerPlayer serverPlayer) {
            return PermissionAPI.getPermission(serverPlayer, Objects.requireNonNull(INTERACT_CREATE_PERM));
        }
        return false;
    }

    public static boolean canListUrl(@Nullable CommandSource source) {
        if (source instanceof MinecraftServer || source instanceof RconConsoleSource) {
            return true;
        }
        if (source instanceof ServerPlayer serverPlayer) {
            return PermissionAPI.getPermission(serverPlayer, Objects.requireNonNull(LIST_PERM));
        }
        return false;
    }

    public static boolean canBlockUrl(@Nullable CommandSource source) {
        if (source instanceof MinecraftServer || source instanceof RconConsoleSource) {
            return true;
        }
        if (source instanceof ServerPlayer serverPlayer) {
            return PermissionAPI.getPermission(serverPlayer, Objects.requireNonNull(BLOCK_PERM));
        }
        return false;
    }

    public static boolean canUnblockUrl(@Nullable CommandSource source) {
        if (source instanceof MinecraftServer || source instanceof RconConsoleSource) {
            return true;
        }
        if (source instanceof ServerPlayer serverPlayer) {
            return PermissionAPI.getPermission(serverPlayer, Objects.requireNonNull(UNBLOCK_PERM));
        }
        return false;
    }

    private static boolean everyone(@Nullable ServerPlayer player, UUID uuid, PermissionDynamicContext<?>... context) {
        return true;
    }

    private static boolean operator(@Nullable ServerPlayer player, UUID uuid, PermissionDynamicContext<?>... context) {
        return player != null && player.hasPermissions(2);
    }
}
