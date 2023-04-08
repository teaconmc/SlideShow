package org.teacon.slides.admin;

import net.minecraft.FieldsAreNonnullByDefault;
import net.minecraft.MethodsReturnNonnullByDefault;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.server.permission.PermissionAPI;
import net.minecraftforge.server.permission.events.PermissionGatherEvent;
import net.minecraftforge.server.permission.nodes.PermissionNode;
import net.minecraftforge.server.permission.nodes.PermissionTypes;
import org.teacon.slides.SlideShow;

import javax.annotation.Nullable;
import javax.annotation.ParametersAreNonnullByDefault;
import java.util.Objects;

@FieldsAreNonnullByDefault
@MethodsReturnNonnullByDefault
@ParametersAreNonnullByDefault
@Mod.EventBusSubscriber(bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class SlidePermission {
    private static @Nullable PermissionNode<Boolean> INTERACT_PERM;

    @SubscribeEvent
    public static void gatherPermNodes(PermissionGatherEvent.Nodes event) {
        // FIXME: permission resolving
        event.addNodes(INTERACT_PERM = new PermissionNode<>(SlideShow.ID,
                "interact.projector", PermissionTypes.BOOLEAN, (player, playerUUID, context) -> true));
    }

    public static boolean canInteract(Player p) {
        return p instanceof ServerPlayer sp && PermissionAPI.getPermission(sp, Objects.requireNonNull(INTERACT_PERM));
    }
}
