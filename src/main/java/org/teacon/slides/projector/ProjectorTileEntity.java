package org.teacon.slides.projector;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.Connection;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.TranslatableComponent;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.network.NetworkHooks;
import org.teacon.slides.Registries;
import org.teacon.slides.renderer.ProjectorWorldRender;
import org.teacon.slides.renderer.SlideData;

import javax.annotation.Nonnull;
import javax.annotation.ParametersAreNonnullByDefault;

@SuppressWarnings("ConstantConditions")
@ParametersAreNonnullByDefault
public final class ProjectorTileEntity extends BlockEntity implements MenuProvider {

    private static final Component TITLE = new TranslatableComponent("gui.slide_show.title");

    public SlideData currentSlide = new SlideData();

    public ProjectorTileEntity(BlockPos blockPos, BlockState blockState) {
        super(Registries.TILE_TYPE, blockPos, blockState);
    }

    public void openGui(BlockState state, BlockPos pos, Player player) {
        NetworkHooks.openGui((ServerPlayer) player, this, buf -> {
            buf.writeBlockPos(pos);
            buf.writeNbt(this.currentSlide.serializeNBT());
            buf.writeEnum(state.getValue(ProjectorBlock.ROTATION));
        });
    }

    @Nonnull
    @Override
    public AbstractContainerMenu createMenu(int containerId, Inventory inventory, Player player) {
        return ProjectorControlContainerMenu.fromServer(containerId, inventory, this);
    }

    @Nonnull
    @Override
    public Component getDisplayName() {
        return TITLE;
    }

    @Override
    public void onLoad() {
        super.onLoad();
        if (level.isClientSide) {
            ProjectorWorldRender.add(this);
        }
    }

    @Override
    public void onChunkUnloaded() {
        super.onChunkUnloaded();
        if (level.isClientSide) {
            ProjectorWorldRender.remove(this);
        }
    }

    @Override
    public void setRemoved() {
        super.setRemoved();
        if (level.isClientSide) {
            ProjectorWorldRender.remove(this);
        }
    }

    @Override
    public void saveAdditional(CompoundTag tag) {
        tag.merge(currentSlide.serializeNBT());
    }

    @Override
    public void load(CompoundTag tag) {
        super.load(tag);
        currentSlide.deserializeNBT(tag);
    }

    @Nonnull
    @Override
    public ClientboundBlockEntityDataPacket getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }

    @Override
    public void onDataPacket(Connection net, ClientboundBlockEntityDataPacket packet) {
        currentSlide.deserializeNBT(packet.getTag());
    }

    @Nonnull
    @Override
    public CompoundTag getUpdateTag() {
        CompoundTag tag = super.getUpdateTag();
        saveAdditional(tag);
        return tag;
    }

    @Override
    public void handleUpdateTag(CompoundTag tag) {
        load(tag);
    }

    // NO USE, see ProjectorRenderer.getViewDistance
    /*@OnlyIn(Dist.CLIENT)
    @Override
    public double getViewDistance() {
        return 65536.0D;
    }*/

    // NO USE, see ProjectorRenderer.shouldRenderOffScreen()
    /*@OnlyIn(Dist.CLIENT)
    @Override
    public AABB getRenderBoundingBox() {
        final Matrix4f transformation = getTransformation();
        final Vector4f v00 = new Vector4f(0, 0, 0, 1);
        final Vector4f v11 = new Vector4f(1, 0, 1, 1);
        v00.transform(transformation);
        v11.transform(transformation);
        AABB base = new AABB(v00.x(), v00.y(), v00.z(), v11.x(), v11.y(), v11.z());
        return base.move(this.getBlockPos()).inflate(0.5);
    }*/
}
