package org.teacon.slides;

import dev.architectury.injectables.annotations.ExpectPlatform;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderDispatcher;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntityType;
import org.teacon.slides.mappings.BlockEntityMapper;
import org.teacon.slides.mappings.BlockEntityRendererMapper;

import java.util.function.Consumer;
import java.util.function.Function;

public class RegistryClient {

	@ExpectPlatform
	public static void registerBlockRenderType(RenderType type, Block block) {
		throw new AssertionError();
	}

	@ExpectPlatform
	public static <T extends BlockEntityMapper> void registerTileEntityRenderer(BlockEntityType<T> type, Function<BlockEntityRenderDispatcher, BlockEntityRendererMapper<T>> function) {
		throw new AssertionError();
	}

	@ExpectPlatform
	public static void registerNetworkReceiver(ResourceLocation resourceLocation, Consumer<FriendlyByteBuf> consumer) {
		throw new AssertionError();
	}

	@ExpectPlatform
	public static void registerClientStoppingEvent(Consumer<Minecraft> consumer) {
		throw new AssertionError();
	}

	@ExpectPlatform
	public static void registerTickEvent(Consumer<Minecraft> consumer) {
		throw new AssertionError();
	}

	@ExpectPlatform
	public static void sendToServer(ResourceLocation id, FriendlyByteBuf packet) {
		throw new AssertionError();
	}
}
