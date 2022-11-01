package org.teacon.slides.forge;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderDispatcher;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntityType;
import org.teacon.slides.mappings.BlockEntityMapper;
import org.teacon.slides.mappings.BlockEntityRendererMapper;
import org.teacon.slides.mappings.NetworkUtilities;
import org.teacon.slides.mappings.RegistryUtilitiesClient;

import java.util.function.Consumer;
import java.util.function.Function;

public class RegistryClientImpl {

	public static void registerBlockRenderType(RenderType type, Block block) {
		RegistryUtilitiesClient.registerRenderType(type, block);
	}

	public static <T extends BlockEntityMapper> void registerTileEntityRenderer(BlockEntityType<T> type, Function<BlockEntityRenderDispatcher, BlockEntityRendererMapper<T>> function) {
		RegistryUtilitiesClient.registerTileEntityRenderer(type, function);
	}

	public static void registerNetworkReceiver(ResourceLocation resourceLocation, Consumer<FriendlyByteBuf> consumer) {
		NetworkUtilities.registerReceiverS2C(resourceLocation, (packet, context) -> consumer.accept(packet));
	}

	public static void registerClientStoppingEvent(Consumer<Minecraft> consumer) {
		RegistryUtilitiesClient.registerClientStoppingEvent(consumer);
	}

	public static void registerTickEvent(Consumer<Minecraft> consumer) {
		RegistryUtilitiesClient.registerClientTickEvent(consumer);
	}

	public static void sendToServer(ResourceLocation id, FriendlyByteBuf packet) {
		NetworkUtilities.sendToServer(id, packet);
	}
}
