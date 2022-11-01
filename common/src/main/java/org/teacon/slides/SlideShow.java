package org.teacon.slides;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntityType;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.teacon.slides.mappings.BlockEntityMapper;
import org.teacon.slides.projector.ProjectorUpdatePacket;

import javax.annotation.ParametersAreNonnullByDefault;
import java.util.function.BiConsumer;

@ParametersAreNonnullByDefault
public final class SlideShow {

	public static final String ID = "slide_show"; // as well as the namespace
	public static final Logger LOGGER = LogManager.getLogger("SlideShow");
	public static final ResourceLocation PACKET_UPDATE = new ResourceLocation(ID, "update");
	public static final ResourceLocation PACKET_OPEN_GUI = new ResourceLocation(ID, "open_gui");

	public static boolean sOptiFineLoaded;

	static {
		try {
			Class.forName("optifine.Installer");
			sOptiFineLoaded = true;
		} catch (ClassNotFoundException ignored) {
		}
	}

	public static void init(RegisterBlockItem registerBlockItem, BiConsumer<String, RegistryObject<? extends BlockEntityType<? extends BlockEntityMapper>>> registerBlockEntityType) {
		registerBlockItem.accept("projector", Registries.PROJECTOR, CreativeModeTab.TAB_MISC);
		registerBlockEntityType.accept("projector", Registries.BLOCK_ENTITY);
		Registry.registerNetworkReceiver(PACKET_UPDATE, ProjectorUpdatePacket::handle);
	}

	@FunctionalInterface
	public interface RegisterBlockItem {
		void accept(String string, RegistryObject<Block> block, CreativeModeTab tab);
	}
}
