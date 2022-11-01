package org.teacon.slides.forge;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import org.teacon.slides.mappings.NetworkUtilities;

public class RegistryImpl {

	public static void registerNetworkReceiver(ResourceLocation resourceLocation, NetworkUtilities.PacketCallback packetCallback) {
		NetworkUtilities.registerReceiverC2S(resourceLocation, packetCallback);
	}

	public static void sendToPlayer(ServerPlayer player, ResourceLocation id, FriendlyByteBuf packet) {
		NetworkUtilities.sendToPlayer(player, id, packet);
	}
}
