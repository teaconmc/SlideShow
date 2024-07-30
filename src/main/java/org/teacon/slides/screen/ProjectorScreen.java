package org.teacon.slides.screen;

import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.ChatFormatting;
import net.minecraft.FieldsAreNonnullByDefault;
import net.minecraft.MethodsReturnNonnullByDefault;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractButton;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.narration.NarratedElementType;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Rotation;
import net.neoforged.neoforge.network.PacketDistributor;
import net.objecthunter.exp4j.ExpressionBuilder;
import org.apache.commons.lang3.StringUtils;
import org.joml.*;
import org.lwjgl.glfw.GLFW;
import org.teacon.slides.SlideShow;
import org.teacon.slides.network.SlideUpdatePacket;
import org.teacon.slides.projector.ProjectorBlock;
import org.teacon.slides.projector.ProjectorBlockEntity;
import org.teacon.slides.projector.ProjectorContainerMenu;
import org.teacon.slides.renderer.SlideState;
import org.teacon.slides.url.ProjectorURL;

import javax.annotation.Nullable;
import javax.annotation.ParametersAreNonnullByDefault;
import java.lang.Math;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.*;

import static org.joml.RoundingMode.HALF_EVEN;

@FieldsAreNonnullByDefault
@MethodsReturnNonnullByDefault
@ParametersAreNonnullByDefault
public final class ProjectorScreen extends AbstractContainerScreen<ProjectorContainerMenu> {
    private static final ResourceLocation
            GUI_TEXTURE = ResourceLocation.fromNamespaceAndPath(SlideShow.ID, "textures/gui/projector.png");

    private static final Component
            IMAGE_TEXT = Component.translatable("gui.slide_show.section.image"),
            OFFSET_TEXT = Component.translatable("gui.slide_show.section.offset"),
            OTHERS_TEXT = Component.translatable("gui.slide_show.section.others"),
            URL_TEXT = Component.translatable("gui.slide_show.url"),
            COLOR_TEXT = Component.translatable("gui.slide_show.color"),
            WIDTH_TEXT = Component.translatable("gui.slide_show.width"),
            HEIGHT_TEXT = Component.translatable("gui.slide_show.height"),
            KEEP_ASPECT_RATIO_TEXT = Component.translatable("gui.slide_show.keep_aspect_ratio"),
            OFFSET_X_TEXT = Component.translatable("gui.slide_show.offset_x"),
            OFFSET_Y_TEXT = Component.translatable("gui.slide_show.offset_y"),
            OFFSET_Z_TEXT = Component.translatable("gui.slide_show.offset_z"),
            FLIP_TEXT = Component.translatable("gui.slide_show.flip"),
            ROTATE_TEXT = Component.translatable("gui.slide_show.rotate"),
            SINGLE_DOUBLE_SIDED_TEXT = Component.translatable("gui.slide_show.single_double_sided");

    private static final int
            URL_MAX_LENGTH = 1 << 9,
            COLOR_MAX_LENGTH = 1 << 3;

    private final LazyWidget<EditBox> mURLInput;
    private final LazyWidget<EditBox> mColorInput;
    private final LazyWidget<EditBox> mWidthInput;
    private final LazyWidget<EditBox> mHeightInput;
    private final LazyWidget<EditBox> mOffsetXInput;
    private final LazyWidget<EditBox> mOffsetYInput;
    private final LazyWidget<EditBox> mOffsetZInput;

    private final LazyWidget<Button> mFlipRotation;
    private final LazyWidget<Button> mCycleRotation;
    private final LazyWidget<Button> mSwitchSingleSided;
    private final LazyWidget<Button> mSwitchDoubleSided;

    private final LazyWidget<Button> mKeepAspectChecked;
    private final LazyWidget<Button> mKeepAspectUnchecked;

    private final SlideUpdatePacket mInitSlide;

    private @Nullable ProjectorURL mImgUrl;
    private int mImageColor = 0xFFFFFFFF;
    private Vector3i mImageOffsetMicros = new Vector3i(0, 0, 0);
    private Vector2i mImageSizeMicros = new Vector2i(1_000_000, 1_000_000);

    // initialized after construction

    private boolean mDoubleSided;
    private boolean mKeepAspectRatio;
    private SyncAspectRatio mSyncAspectRatio;
    private ProjectorBlock.InternalRotation mRotation;

    // refreshed after initialization

    private boolean mInvalidColor = true;
    private boolean mInvalidWidth = true, mInvalidHeight = true;
    private boolean mInvalidOffsetX = true, mInvalidOffsetY = true, mInvalidOffsetZ = true;
    private ImageUrlStatus mImageUrlStatus = ImageUrlStatus.NO_CONTENT;

    public ProjectorScreen(ProjectorContainerMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title);
        imageWidth = 176;
        imageHeight = 217;
        // initialize variables
        var packet = menu.slideUpdatePacket;
        mInitSlide = packet;
        mRotation = packet.rotation;
        mDoubleSided = packet.doubleSided;
        mKeepAspectRatio = packet.keepAspectRatio;
        mSyncAspectRatio = packet.keepAspectRatio ? SyncAspectRatio.SYNC_WIDTH_WITH_HEIGHT : SyncAspectRatio.SYNCED;
        // url input
        mURLInput = LazyWidget.of(toImageUrl(mInitSlide.imgUrl), EditBox::getValue, value -> {
            var input = new EditBox(font, leftPos + 30, topPos + 29, 136, 16, URL_TEXT);
            input.setMaxLength(URL_MAX_LENGTH);
            input.setResponder(text -> {
                try {
                    mImgUrl = new ProjectorURL(text);
                    if (mInitSlide.hasCreatePermission) {
                        var blocked = SlideState.getImgBlocked(mImgUrl);
                        mImageUrlStatus = blocked ? ImageUrlStatus.BLOCKED : ImageUrlStatus.NORMAL;
                    } else {
                        var allowed = SlideState.getImgAllowed(mImgUrl);
                        mImageUrlStatus = allowed ? ImageUrlStatus.NORMAL : ImageUrlStatus.INVALID;
                    }
                } catch (IllegalArgumentException e) {
                    mImgUrl = null;
                    mImageUrlStatus = StringUtils.isNotBlank(text) ? ImageUrlStatus.INVALID : ImageUrlStatus.NO_CONTENT;
                }
                input.setTextColor(switch (mImageUrlStatus) {
                    case NORMAL, NO_CONTENT -> 0xE0E0E0;
                    case BLOCKED -> 0xE0E04B;
                    case INVALID -> 0xE04B4B;
                });
            });
            input.setValue(value);
            return input;
        });
        // color input
        mColorInput = LazyWidget.of(String.format("%08X", mInitSlide.color), EditBox::getValue, value -> {
            var input = new EditBox(font, leftPos + 55, topPos + 155, 56, 16, COLOR_TEXT);
            input.setMaxLength(COLOR_MAX_LENGTH);
            input.setResponder(text -> {
                try {
                    mImageColor = Integer.parseUnsignedInt(text, 16);
                    mInvalidColor = false;
                } catch (Exception e) {
                    mInvalidColor = true;
                }
                input.setTextColor(mInvalidColor ? 0xE04B4B : 0xE0E0E0);
            });
            input.setValue(value);
            return input;
        });
        // width input
        mWidthInput = LazyWidget.of(fromMicros(toMicros(mInitSlide.dimensionX), false), EditBox::getValue, value -> {
            var input = new EditBox(font, leftPos + 30, topPos + 51, 46, 16, WIDTH_TEXT);
            input.setResponder(text -> {
                try {
                    var newSizeMicros = new Vector2i(toMicros(text), mImageSizeMicros.y);
                    updateOffsetByDimension(newSizeMicros);
                    mInvalidWidth = false;
                } catch (Exception e) {
                    mInvalidWidth = true;
                }
                input.setTextColor(mInvalidWidth ? 0xE04B4B : 0xE0E0E0);
                if (!mInvalidWidth && mKeepAspectRatio) {
                    mSyncAspectRatio = SyncAspectRatio.SYNC_HEIGHT_WITH_WIDTH;
                }
            });
            input.setValue(value);
            return input;
        });
        // height input
        mHeightInput = LazyWidget.of(fromMicros(toMicros(mInitSlide.dimensionY), false), EditBox::getValue, value -> {
            var input = new EditBox(font, leftPos + 100, topPos + 51, 46, 16, HEIGHT_TEXT);
            input.setResponder(text -> {
                try {
                    var newSizeMicros = new Vector2i(mImageSizeMicros.x, toMicros(text));
                    updateOffsetByDimension(newSizeMicros);
                    mInvalidHeight = false;
                } catch (Exception e) {
                    mInvalidHeight = true;
                }
                input.setTextColor(mInvalidHeight ? 0xE04B4B : 0xE0E0E0);
                if (!mInvalidHeight && mKeepAspectRatio) {
                    mSyncAspectRatio = SyncAspectRatio.SYNC_WIDTH_WITH_HEIGHT;
                }
            });
            input.setValue(value);
            return input;
        });
        // offset x input
        mOffsetXInput = LazyWidget.of(fromMicros(toMicros(mInitSlide.slideOffsetX), true), EditBox::getValue, value -> {
            var input = new EditBox(font, leftPos + 30, topPos + 103, 29, 16, OFFSET_X_TEXT);
            input.setResponder(text -> {
                try {
                    mImageOffsetMicros = new Vector3i(toMicros(text), mImageOffsetMicros.y(), mImageOffsetMicros.z());
                    mInvalidOffsetX = false;
                } catch (Exception e) {
                    mInvalidOffsetX = true;
                }
                input.setTextColor(mInvalidOffsetX ? 0xE04B4B : 0xE0E0E0);
            });
            input.setValue(value);
            return input;
        });
        // offset y input
        mOffsetYInput = LazyWidget.of(fromMicros(toMicros(mInitSlide.slideOffsetY), true), EditBox::getValue, value -> {
            var input = new EditBox(font, leftPos + 84, topPos + 103, 29, 16, OFFSET_Y_TEXT);
            input.setResponder(text -> {
                try {
                    mImageOffsetMicros = new Vector3i(mImageOffsetMicros.x(), toMicros(text), mImageOffsetMicros.z());
                    mInvalidOffsetY = false;
                } catch (Exception e) {
                    mInvalidOffsetY = true;
                }
                input.setTextColor(mInvalidOffsetY ? 0xE04B4B : 0xE0E0E0);
            });
            input.setValue(value);
            return input;
        });
        // offset z input
        mOffsetZInput = LazyWidget.of(fromMicros(toMicros(mInitSlide.slideOffsetZ), true), EditBox::getValue, value -> {
            var input = new EditBox(font, leftPos + 138, topPos + 103, 29, 16, OFFSET_Z_TEXT);
            input.setResponder(text -> {
                try {
                    mImageOffsetMicros = new Vector3i(mImageOffsetMicros.x(), mImageOffsetMicros.y(), toMicros(text));
                    mInvalidOffsetZ = false;
                } catch (Exception e) {
                    mInvalidOffsetZ = true;
                }
                input.setTextColor(mInvalidOffsetZ ? 0xE04B4B : 0xE0E0E0);
            });
            input.setValue(value);
            return input;
        });
        // internal rotation buttons
        mFlipRotation = LazyWidget.of(true, b -> b.visible, value -> {
            var button = new Button(leftPos + 117, topPos + 153, 179, 153, 18, 19, FLIP_TEXT, () -> {
                var newRotation = mRotation.flip();
                updateOffsetByRotation(newRotation);
            });
            button.visible = value;
            return button;
        });
        mCycleRotation = LazyWidget.of(true, b -> b.visible, value -> {
            var button = new Button(leftPos + 142, topPos + 153, 179, 173, 18, 19, ROTATE_TEXT, () -> {
                var newRotation = mRotation.compose(Rotation.CLOCKWISE_90);
                updateOffsetByRotation(newRotation);
            });
            button.visible = value;
            return button;
        });
        // single sided / double sided
        mSwitchSingleSided = LazyWidget.of(mDoubleSided, b -> b.visible, value -> {
            var button = new Button(leftPos + 9, topPos + 153, 179, 113, 18, 19, SINGLE_DOUBLE_SIDED_TEXT, () -> {
                if (mDoubleSided) {
                    updateDoubleSided(false);
                }
            });
            button.visible = value;
            return button;
        });
        mSwitchDoubleSided = LazyWidget.of(!mDoubleSided, b -> b.visible, value -> {
            var button = new Button(leftPos + 9, topPos + 153, 179, 133, 18, 19, SINGLE_DOUBLE_SIDED_TEXT, () -> {
                if (!mDoubleSided) {
                    updateDoubleSided(true);
                }
            });
            button.visible = value;
            return button;
        });
        mKeepAspectChecked = LazyWidget.of(mKeepAspectRatio, b -> b.visible, value -> {
            var button = new Button(leftPos + 149, topPos + 49, 179, 93, 18, 19, KEEP_ASPECT_RATIO_TEXT, () -> {
                if (mKeepAspectRatio) {
                    updateKeepAspectRatio(false);
                }
            });
            button.visible = value;
            return button;
        });
        mKeepAspectUnchecked = LazyWidget.of(!mKeepAspectRatio, b -> b.visible, value -> {
            var button = new Button(leftPos + 149, topPos + 49, 179, 73, 18, 19, KEEP_ASPECT_RATIO_TEXT, () -> {
                if (!mKeepAspectRatio) {
                    updateKeepAspectRatio(true);
                }
            });
            button.visible = value;
            return button;
        });
    }

    @Override
    protected void init() {
        super.init();

        addRenderableWidget(mURLInput.refresh());
        addRenderableWidget(mColorInput.refresh());
        addRenderableWidget(mWidthInput.refresh());
        addRenderableWidget(mHeightInput.refresh());
        addRenderableWidget(mOffsetXInput.refresh());
        addRenderableWidget(mOffsetYInput.refresh());
        addRenderableWidget(mOffsetZInput.refresh());

        addRenderableWidget(mFlipRotation.refresh());
        addRenderableWidget(mCycleRotation.refresh());
        addRenderableWidget(mSwitchSingleSided.refresh());
        addRenderableWidget(mSwitchDoubleSided.refresh());
        addRenderableWidget(mKeepAspectChecked.refresh());
        addRenderableWidget(mKeepAspectUnchecked.refresh());

        setInitialFocus(mURLInput.get());
    }

    private void updateOffsetByRotation(ProjectorBlock.InternalRotation newRotation) {
        // noinspection DuplicatedCode
        if (!mInvalidOffsetX && !mInvalidOffsetY && !mInvalidOffsetZ) {
            var absoluteMicros = fromRelativeOffsetMicros(mImageOffsetMicros, mImageSizeMicros, mRotation);
            var newRelativeMicros = fromAbsoluteOffsetMicros(absoluteMicros, mImageSizeMicros, newRotation);
            mOffsetXInput.get().setValue(fromMicros(newRelativeMicros.x(), true));
            mOffsetYInput.get().setValue(fromMicros(newRelativeMicros.y(), true));
            mOffsetZInput.get().setValue(fromMicros(newRelativeMicros.z(), true));
        }
        mRotation = newRotation;
    }

    private void updateOffsetByDimension(Vector2i newDimensionMicros) {
        // noinspection DuplicatedCode
        if (!mInvalidOffsetX && !mInvalidOffsetY && !mInvalidOffsetZ) {
            var absoluteMicros = fromRelativeOffsetMicros(mImageOffsetMicros, mImageSizeMicros, mRotation);
            var newRelativeMicros = fromAbsoluteOffsetMicros(absoluteMicros, newDimensionMicros, mRotation);
            mOffsetXInput.get().setValue(fromMicros(newRelativeMicros.x(), true));
            mOffsetYInput.get().setValue(fromMicros(newRelativeMicros.y(), true));
            mOffsetZInput.get().setValue(fromMicros(newRelativeMicros.z(), true));
        }
        mImageSizeMicros = newDimensionMicros;
    }

    private void updateDoubleSided(boolean doubleSided) {
        mDoubleSided = doubleSided;
        mSwitchSingleSided.get().visible = doubleSided;
        mSwitchDoubleSided.get().visible = !doubleSided;
    }

    private void updateKeepAspectRatio(boolean keepAspectRatio) {
        mKeepAspectRatio = keepAspectRatio;
        mKeepAspectUnchecked.get().visible = !keepAspectRatio;
        mKeepAspectChecked.get().visible = keepAspectRatio;
        mSyncAspectRatio = mKeepAspectRatio ? SyncAspectRatio.SYNC_WIDTH_WITH_HEIGHT : SyncAspectRatio.SYNCED;
    }

    @Override
    public void containerTick() {
        this.syncAspectRatioTick();
    }

    @Override
    public void removed() {
        super.removed();
        var tilePos = mInitSlide.pos;
        var level = Objects.requireNonNull(minecraft).level;
        if (level != null && level.getBlockEntity(tilePos) instanceof ProjectorBlockEntity tile) {
            var urlFallback = (ProjectorURL) null;
            var urlRemoved = mImageUrlStatus == ImageUrlStatus.NO_CONTENT && mInitSlide.imgUrl != null;
            var urlChanged = mImageUrlStatus == ImageUrlStatus.NORMAL && !Objects.equals(mImgUrl, mInitSlide.imgUrl);
            if (urlRemoved || urlChanged) {
                // apply random uuid and wait for server updates
                urlFallback = urlRemoved ? null : mImgUrl;
                tile.setImageLocation(UUID.randomUUID());
            }
            var validColor = !mInvalidColor;
            if (validColor) {
                tile.setColorARGB(mImageColor);
            }
            var validSize = !mInvalidWidth && !mInvalidHeight;
            if (validSize) {
                var dimensionX = fromMicros(mImageSizeMicros.x());
                var dimensionY = fromMicros(mImageSizeMicros.y());
                tile.setDimension(new Vector2f(dimensionX, dimensionY));
            }
            var validOffset = !mInvalidOffsetX && !mInvalidOffsetY && !mInvalidOffsetZ;
            if (validOffset) {
                var offsetX = fromMicros(mImageOffsetMicros.x());
                var offsetY = fromMicros(mImageOffsetMicros.y());
                var offsetZ = fromMicros(mImageOffsetMicros.z());
                tile.setSlideOffset(new Vector3f(offsetX, offsetY, offsetZ));
            }
            var state = tile.getBlockState().setValue(ProjectorBlock.ROTATION, mRotation);
            level.setBlock(tilePos, state, Block.UPDATE_NONE);
            tile.setDoubleSided(mDoubleSided);
            tile.setKeepAspectRatio(mKeepAspectRatio);
            var urlFallbackOptional = Optional.ofNullable(urlFallback);
            var hasCreatePermission = mInitSlide.hasCreatePermission;
            PacketDistributor.sendToServer(new SlideUpdatePacket(tile, hasCreatePermission, u -> urlFallbackOptional));
        }
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        if (mWidthInput.get().isMouseOver(mouseX, mouseY)) {
            if (!mInvalidWidth) {
                mWidthInput.get().setValue(fromMicros((int) Math.rint(mImageSizeMicros.x() + scrollY * 25E4), false));
                if (mKeepAspectRatio) {
                    mSyncAspectRatio = SyncAspectRatio.SYNC_HEIGHT_WITH_WIDTH;
                }
                return true;
            }
        } else if (mHeightInput.get().isMouseOver(mouseX, mouseY)) {
            if (!mInvalidHeight) {
                mHeightInput.get().setValue(fromMicros((int) Math.rint(mImageSizeMicros.y() + scrollY * 25E4), false));
                if (mKeepAspectRatio) {
                    mSyncAspectRatio = SyncAspectRatio.SYNC_WIDTH_WITH_HEIGHT;
                }
                return true;
            }
        }
        return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifier) {
        var isEscape = false;

        if (keyCode == GLFW.GLFW_KEY_ESCAPE) {
            Objects.requireNonNull(Objects.requireNonNull(minecraft).player).closeContainer();
            isEscape = true;
        }

        return isEscape
                || mURLInput.get().keyPressed(keyCode, scanCode, modifier) || mURLInput.get().canConsumeInput()
                || mColorInput.get().keyPressed(keyCode, scanCode, modifier) || mColorInput.get().canConsumeInput()
                || mWidthInput.get().keyPressed(keyCode, scanCode, modifier) || mWidthInput.get().canConsumeInput()
                || mHeightInput.get().keyPressed(keyCode, scanCode, modifier) || mHeightInput.get().canConsumeInput()
                || mOffsetXInput.get().keyPressed(keyCode, scanCode, modifier) || mOffsetXInput.get().canConsumeInput()
                || mOffsetYInput.get().keyPressed(keyCode, scanCode, modifier) || mOffsetYInput.get().canConsumeInput()
                || mOffsetZInput.get().keyPressed(keyCode, scanCode, modifier) || mOffsetZInput.get().canConsumeInput()
                || super.keyPressed(keyCode, scanCode, modifier);
    }

    @Override
    protected void renderBg(GuiGraphics gui, float partialTicks, int mouseX, int mouseY) {
        RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, 1.0F);
        RenderSystem.setShaderTexture(0, GUI_TEXTURE);
        gui.blit(GUI_TEXTURE, leftPos, topPos, 0, 0, imageWidth, imageHeight);
        if (mImageUrlStatus == ImageUrlStatus.INVALID || mImageUrlStatus == ImageUrlStatus.BLOCKED) {
            gui.blit(GUI_TEXTURE, leftPos + 9, topPos + 27, 179, 53, 18, 19);
        }
    }

    @Override
    protected void renderLabels(GuiGraphics gui, int mouseX, int mouseY) {
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.setShaderTexture(0, GUI_TEXTURE);

        int alpha = mImageColor >>> 24;
        if (alpha > 0) {
            int red = (mImageColor >> 16) & 255, green = (mImageColor >> COLOR_MAX_LENGTH) & 255, blue = mImageColor & 255;
            RenderSystem.setShaderColor(red / 255.0F, green / 255.0F, blue / 255.0F, alpha / 255.0F);
            gui.blit(GUI_TEXTURE, 38, 157, 180, 194, 10, 10);
            gui.blit(GUI_TEXTURE, 82, 185, 180, 194, 17, 17);
        }

        RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, 1.0F);
        gui.blit(GUI_TEXTURE, 82, 185, 202, 194 - mRotation.ordinal() * 20, 17, 17);

        drawCenteredStringWithoutShadow(gui, font, IMAGE_TEXT, 12);
        drawCenteredStringWithoutShadow(gui, font, OFFSET_TEXT, 86);
        drawCenteredStringWithoutShadow(gui, font, OTHERS_TEXT, 138);

        int offsetX = mouseX - leftPos, offsetY = mouseY - topPos;
        if (offsetX >= 9 && offsetY >= 27 && offsetX < 27 && offsetY < 46) {
            gui.renderComponentTooltip(font, this.getUrlTexts(), offsetX, offsetY);
        } else if (offsetX >= 34 && offsetY >= 153 && offsetX < 52 && offsetY < 172) {
            gui.renderTooltip(font, COLOR_TEXT, offsetX, offsetY);
        } else if (offsetX >= 9 && offsetY >= 49 && offsetX < 27 && offsetY < 68) {
            gui.renderTooltip(font, WIDTH_TEXT, offsetX, offsetY);
        } else if (offsetX >= 79 && offsetY >= 49 && offsetX < 97 && offsetY < 68) {
            gui.renderTooltip(font, HEIGHT_TEXT, offsetX, offsetY);
        } else if (offsetX >= 149 && offsetY >= 49 && offsetX < 167 && offsetY < 68) {
            gui.renderTooltip(font, KEEP_ASPECT_RATIO_TEXT, offsetX, offsetY);
        } else if (offsetX >= 9 && offsetY >= 101 && offsetX < 27 && offsetY < 120) {
            gui.renderTooltip(font, OFFSET_X_TEXT, offsetX, offsetY);
        } else if (offsetX >= 63 && offsetY >= 101 && offsetX < 81 && offsetY < 120) {
            gui.renderTooltip(font, OFFSET_Y_TEXT, offsetX, offsetY);
        } else if (offsetX >= 117 && offsetY >= 101 && offsetX < 135 && offsetY < 120) {
            gui.renderTooltip(font, OFFSET_Z_TEXT, offsetX, offsetY);
        } else if (offsetX >= 117 && offsetY >= 153 && offsetX < 135 && offsetY < 172) {
            gui.renderTooltip(font, FLIP_TEXT, offsetX, offsetY);
        } else if (offsetX >= 142 && offsetY >= 153 && offsetX < 160 && offsetY < 172) {
            gui.renderTooltip(font, ROTATE_TEXT, offsetX, offsetY);
        } else if (offsetX >= 9 && offsetY >= 153 && offsetX < 27 && offsetY < 172) {
            gui.renderTooltip(font, SINGLE_DOUBLE_SIDED_TEXT, offsetX, offsetY);
        }
    }

    private void syncAspectRatioTick() {
        if (!mURLInput.get().isFocused()) {
            if (mSyncAspectRatio != SyncAspectRatio.SYNCED && !mInvalidWidth && !mInvalidHeight) {
                var slide = SlideState.getSlide(mInitSlide.imgId);
                var dimensionOptional = slide == null ? Optional.<Vector2i>empty() : slide.getDimension();
                if (dimensionOptional.isPresent()) {
                    var imageWidth = dimensionOptional.get().x();
                    var imageHeight = dimensionOptional.get().y();
                    if (mSyncAspectRatio == SyncAspectRatio.SYNC_WIDTH_WITH_HEIGHT) {
                        var newWidthMicros = (int) Math.rint(1.0 * mImageSizeMicros.y() * imageWidth / imageHeight);
                        var newSizeMicrosByHeight = new Vector2i(newWidthMicros, mImageSizeMicros.y());
                        updateOffsetByDimension(newSizeMicrosByHeight);
                        if (!mWidthInput.get().isFocused()) {
                            mWidthInput.get().setValue(fromMicros(newSizeMicrosByHeight.x(), false));
                        }
                    }
                    if (mSyncAspectRatio == SyncAspectRatio.SYNC_HEIGHT_WITH_WIDTH) {
                        var newHeightMicros = (int) Math.rint(1.0 * mImageSizeMicros.x() * imageHeight / imageWidth);
                        var newSizeMicrosByWidth = new Vector2i(mImageSizeMicros.x(), newHeightMicros);
                        updateOffsetByDimension(newSizeMicrosByWidth);
                        if (!mHeightInput.get().isFocused()) {
                            mHeightInput.get().setValue(fromMicros(newSizeMicrosByWidth.y(), false));
                        }
                    }
                    mSyncAspectRatio = SyncAspectRatio.SYNCED;
                }
            }
        }
    }

    private List<Component> getUrlTexts() {
        var lastLog = mInitSlide.lastOperationLog;
        var components = new ArrayList<Component>();
        components.add(URL_TEXT);
        if (lastLog != null) {
            var lastLogType = lastLog.type();
            var lastLogProjector = lastLog.projector();
            var mc = Objects.requireNonNull(this.minecraft);
            var time = lastLog.time().atZone(ZoneId.systemDefault());
            var pos = lastLogProjector.map(GlobalPos::pos).orElse(BlockPos.ZERO);
            if (lastLogProjector.isEmpty()) {
                var path = lastLogType.id().getPath();
                var namespace = lastLogType.id().getNamespace();
                var key = String.format("gui.slide_show.log_message.%s.%s", namespace, path);
                components.add(Component.translatable(key).withStyle(ChatFormatting.GRAY));
            } else if (mc.level == null || !mc.level.dimension().equals(lastLogProjector.get().dimension())) {
                var path = lastLogType.id().getPath();
                var namespace = lastLogType.id().getNamespace();
                var key = String.format("gui.slide_show.log_message.%s.%s.in_another_level", namespace, path);
                components.add(Component.translatable(key).withStyle(ChatFormatting.GRAY));
            } else {
                var path = lastLogType.id().getPath();
                var namespace = lastLogType.id().getNamespace();
                var posText = Component.translatable("chat.coordinates", pos.getX(), pos.getY(), pos.getZ());
                var key = String.format("gui.slide_show.log_message.%s.%s.in_current_level", namespace, path);
                components.add(Component.translatable(key, posText).withStyle(ChatFormatting.GRAY));
            }
            var timeText = Component.literal(DateTimeFormatter.RFC_1123_DATE_TIME.format(time.toOffsetDateTime()));
            if (lastLog.operator().isPresent()) {
                var nameText = lastLog.operator().get().getName();
                components.add(Component.translatable(
                        "gui.slide_show.log_comment", timeText, nameText).withStyle(ChatFormatting.GRAY));
            } else {
                components.add(Component.translatable(
                        "gui.slide_show.log_comment_nobody", timeText).withStyle(ChatFormatting.GRAY));
            }
        }
        return components;
    }

    private static void drawCenteredStringWithoutShadow(GuiGraphics gui, Font renderer, Component string, int y) {
        gui.drawString(renderer, string.getVisualOrderText(), (88 - renderer.width(string) / 2.0F), y, 0x404040, false);
    }

    private static int toMicros(float value) {
        return (int) Math.rint(value * 1E6);
    }

    private static int toMicros(String text) {
        return (int) Math.rint(new ExpressionBuilder(text).implicitMultiplication(false).build().evaluate() * 1E6);
    }

    private static String toImageUrl(@Nullable ProjectorURL imgUrl) {
        return imgUrl == null ? "" : imgUrl.toUrl().toString();
    }

    private static float fromMicros(int micros) {
        return (float) (micros / 1E6);
    }

    private static String fromMicros(int micros, boolean positiveSigned) {
        return (micros < 0 ? "-" : micros > 0 && positiveSigned ? "+" : "") + Math.abs(micros / 1E6);
    }

    private static Vector3d fromRelativeOffsetMicros(Vector3i relOffsetMicros,
                                                     Vector2i sizeMicros, ProjectorBlock.InternalRotation rotation) {
        var center = new Vector4d(sizeMicros.x() / 2.0, 0.0, sizeMicros.y() / 2.0, 1.0);
        // matrix 6: offset for slide (center[new] = center[old] + offset)
        center.mul(new Matrix4d().translate(relOffsetMicros.x(), -relOffsetMicros.z(), relOffsetMicros.y()));
        // matrix 5: translation for slide
        center.mul(new Matrix4d().translate(-5E5, 0.0, 5E5 - sizeMicros.y()));
        // matrix 4: internal rotation
        rotation.transform(center);
        // ok, that's enough
        return new Vector3d(center.x() / center.w(), center.y() / center.w(), center.z() / center.w());
    }

    private static Vector3i fromAbsoluteOffsetMicros(Vector3d absOffsetMicros,
                                                     Vector2i sizeMicros, ProjectorBlock.InternalRotation rotation) {
        var center = new Vector4d(absOffsetMicros, 1.0);
        // inverse matrix 4: internal rotation
        rotation.invert().transform(center);
        // inverse matrix 5: translation for slide
        center.mul(new Matrix4d().translate(5E5, 0.0, -5E5 + sizeMicros.y()));
        // subtract (offset = center[new] - center[old])
        center.mul(new Matrix4d().translate(sizeMicros.x() / -2.0, 0.0, sizeMicros.y() / -2.0));
        // ok, that's enough (remember it is (a, -c, b) => (a, b, c))
        return new Vector3i(center.x() / center.w(), center.z() / center.w(), -center.y() / center.w(), HALF_EVEN);
    }

    private static class Button extends AbstractButton {

        private final Runnable callback;
        private final Component msg;
        private final int u;
        private final int v;

        public Button(int x, int y, int u, int v, int width, int height, Component msg, Runnable callback) {
            super(x, y, width, height, msg);
            this.callback = callback;
            this.msg = msg;
            this.u = u;
            this.v = v;
        }

        @Override
        public void onPress() {
            callback.run();
        }

        @Override
        public void renderWidget(GuiGraphics gui, int mouseX, int mouseY, float partialTicks) {
            RenderSystem.enableBlend();
            RenderSystem.defaultBlendFunc();
            RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, alpha);
            gui.blit(GUI_TEXTURE, getX(), getY(), u, v, width, height);
        }

        @Override
        protected void updateWidgetNarration(NarrationElementOutput output) {
            output.add(NarratedElementType.TITLE, msg);
        }
    }

    private enum ImageUrlStatus {
        NORMAL, BLOCKED, INVALID, NO_CONTENT
    }

    private enum SyncAspectRatio {
        SYNCED, SYNC_WIDTH_WITH_HEIGHT, SYNC_HEIGHT_WITH_WIDTH
    }
}
