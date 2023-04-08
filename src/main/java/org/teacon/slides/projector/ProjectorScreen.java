package org.teacon.slides.projector;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.components.AbstractButton;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.narration.NarratedElementType;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.phys.Vec2;
import net.objecthunter.exp4j.ExpressionBuilder;
import org.apache.commons.lang3.StringUtils;
import org.joml.Vector3f;
import org.joml.Vector4f;
import org.lwjgl.glfw.GLFW;
import org.teacon.slides.SlideShow;
import org.teacon.slides.renderer.SlideState;
import org.teacon.slides.slide.Slide;

import javax.annotation.ParametersAreNonnullByDefault;

@SuppressWarnings("ConstantConditions")
@ParametersAreNonnullByDefault
public final class ProjectorScreen extends AbstractContainerScreen<ProjectorContainerMenu> {

    private static final ResourceLocation
            GUI_TEXTURE = new ResourceLocation(SlideShow.ID, "textures/gui/projector.png");

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

    private EditBox mURLInput;
    private EditBox mColorInput;
    private EditBox mWidthInput;
    private EditBox mHeightInput;
    private EditBox mOffsetXInput;
    private EditBox mOffsetYInput;
    private EditBox mOffsetZInput;

    private Button mSwitchSingleSided;
    private Button mSwitchDoubleSided;

    private Button mKeepAspectChecked;
    private Button mKeepAspectUnchecked;

    private boolean mDoubleSided;
    private boolean mKeepAspectRatio;

    private static final int SYNCED = 0;
    private static final int SYNC_WIDTH_WITH_HEIGHT = 1;
    private static final int SYNC_HEIGHT_WITH_WIDTH = 2;
    private int mSyncAspectRatio;

    private boolean mBroadcastSizeChanges = false;

    private int mImageColor = ~0;
    private Vec2 mImageSize = Vec2.ONE;
    private Vector3f mImageOffset = new Vector3f();

    private ProjectorBlock.InternalRotation mRotation = ProjectorBlock.InternalRotation.NONE;

    private boolean mInvalidURL = true;
    private boolean mInvalidColor = true;
    private boolean mInvalidWidth = true, mInvalidHeight = true;
    private boolean mInvalidOffsetX = true, mInvalidOffsetY = true, mInvalidOffsetZ = true;

    private final ProjectorBlockEntity mEntity;

    public ProjectorScreen(ProjectorContainerMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title);
        mEntity = menu.mEntity;
        imageWidth = 176;
        imageHeight = 217;
    }

    @Override
    protected void init() {
        super.init();
        if (mEntity == null) {
            return;
        }
        // minecraft.keyboardHandler.setSendRepeatsToGui(true);

        // url input
        mURLInput = new EditBox(font, leftPos + 30, topPos + 29, 136, 16,
                URL_TEXT);
        mURLInput.setMaxLength(512);
        mURLInput.setResponder(text -> {
            if (StringUtils.isNotBlank(text)) {
                mInvalidURL = SlideState.createURI(text) == null;
            } else {
                mInvalidURL = false;
            }
            mURLInput.setTextColor(mInvalidURL ? 0xE04B4B : 0xE0E0E0);
        });
        mURLInput.setValue(mEntity.mLocation);
        addRenderableWidget(mURLInput);
        //setInitialFocus(mURLInput);

        // color input
        mColorInput = new EditBox(font, leftPos + 55, topPos + 155, 56, 16,
                COLOR_TEXT);
        mColorInput.setMaxLength(8);
        mColorInput.setResponder(text -> {
            try {
                mImageColor = Integer.parseUnsignedInt(text, 16);
                mInvalidColor = false;
            } catch (Exception e) {
                mInvalidColor = true;
            }
            mColorInput.setTextColor(mInvalidColor ? 0xE04B4B : 0xE0E0E0);
        });
        mColorInput.setValue(String.format("%08X", mEntity.mColor));
        addRenderableWidget(mColorInput);

        // width input
        mWidthInput = new EditBox(font, leftPos + 30, topPos + 51, 46, 16,
                WIDTH_TEXT);
        mWidthInput.setResponder(text -> {
            if (mBroadcastSizeChanges) return;
            try {
                Vec2 newSize = new Vec2(parseFloat(text), mImageSize.y);
                updateSize(newSize);
                mInvalidWidth = false;
            } catch (Exception e) {
                mInvalidWidth = true;
            }
            mWidthInput.setTextColor(mInvalidWidth ? 0xE04B4B : 0xE0E0E0);
            if (!mInvalidWidth && mKeepAspectRatio) {
                mSyncAspectRatio = SYNC_HEIGHT_WITH_WIDTH;
            }
        });
        mWidthInput.setValue(toOptionalSignedString(mEntity.mWidth));
        addRenderableWidget(mWidthInput);

        // height input
        mHeightInput = new EditBox(font, leftPos + 100, topPos + 51, 46, 16,
                HEIGHT_TEXT);
        mHeightInput.setResponder(input -> {
            if (mBroadcastSizeChanges) return;
            try {
                Vec2 newSize = new Vec2(mImageSize.x, parseFloat(input));
                updateSize(newSize);
                mInvalidHeight = false;
            } catch (Exception e) {
                mInvalidHeight = true;
            }
            mHeightInput.setTextColor(mInvalidHeight ? 0xE04B4B : 0xE0E0E0);
            if (!mInvalidHeight && mKeepAspectRatio) {
                mSyncAspectRatio = SYNC_WIDTH_WITH_HEIGHT;
            }
        });
        mHeightInput.setValue(toOptionalSignedString(mEntity.mHeight));
        addRenderableWidget(mHeightInput);

        // offset x input
        mOffsetXInput = new EditBox(font, leftPos + 30, topPos + 103, 29, 16,
                OFFSET_X_TEXT);
        mOffsetXInput.setResponder(input -> {
            try {
                mImageOffset = new Vector3f(parseFloat(input), mImageOffset.y(), mImageOffset.z());
                mInvalidOffsetX = false;
            } catch (Exception e) {
                mInvalidOffsetX = true;
            }
            mOffsetXInput.setTextColor(mInvalidOffsetX ? 0xE04B4B : 0xE0E0E0);
        });
        mOffsetXInput.setValue(toSignedString(mEntity.mOffsetX));
        addRenderableWidget(mOffsetXInput);

        // offset y input
        mOffsetYInput = new EditBox(font, leftPos + 84, topPos + 103, 29, 16,
                OFFSET_Y_TEXT);
        mOffsetYInput.setResponder(input -> {
            try {
                mImageOffset = new Vector3f(mImageOffset.x(), parseFloat(input), mImageOffset.z());
                mInvalidOffsetY = false;
            } catch (Exception e) {
                mInvalidOffsetY = true;
            }
            mOffsetYInput.setTextColor(mInvalidOffsetY ? 0xE04B4B : 0xE0E0E0);
        });
        mOffsetYInput.setValue(toSignedString(mEntity.mOffsetY));
        addRenderableWidget(mOffsetYInput);

        // offset z input
        mOffsetZInput = new EditBox(font, leftPos + 138, topPos + 103, 29, 16,
                OFFSET_Z_TEXT);
        mOffsetZInput.setResponder(input -> {
            try {
                mImageOffset = new Vector3f(mImageOffset.x(), mImageOffset.y(), parseFloat(input));
                mInvalidOffsetZ = false;
            } catch (Exception e) {
                mInvalidOffsetZ = true;
            }
            mOffsetZInput.setTextColor(mInvalidOffsetZ ? 0xE04B4B : 0xE0E0E0);
        });
        mOffsetZInput.setValue(toSignedString(mEntity.mOffsetZ));
        addRenderableWidget(mOffsetZInput);

        // internal rotation buttons
        addRenderableWidget(new Button(leftPos + 117, topPos + 153, 179, 153, 18, 19,
                FLIP_TEXT, () -> {
            ProjectorBlock.InternalRotation newRotation = mRotation.flip();
            updateRotation(newRotation);
        }));
        addRenderableWidget(new Button(leftPos + 142, topPos + 153, 179, 173, 18, 19,
                ROTATE_TEXT, () -> {
            ProjectorBlock.InternalRotation newRotation = mRotation.compose(Rotation.CLOCKWISE_90);
            updateRotation(newRotation);
        }));
        mRotation = mEntity.getBlockState().getValue(ProjectorBlock.ROTATION);

        // single sided / double sided
        mSwitchSingleSided = addRenderableWidget(new Button(leftPos + 9, topPos + 153, 179, 113, 18, 19,
                SINGLE_DOUBLE_SIDED_TEXT, () -> {
            mDoubleSided = false;
            mSwitchDoubleSided.visible = true;
            mSwitchSingleSided.visible = false;
        }));
        mSwitchDoubleSided = addRenderableWidget(new Button(leftPos + 9, topPos + 153, 179, 133, 18, 19,
                SINGLE_DOUBLE_SIDED_TEXT, () -> {
            mDoubleSided = true;
            mSwitchSingleSided.visible = true;
            mSwitchDoubleSided.visible = false;
        }));
        mDoubleSided = mEntity.mDoubleSided;
        mSwitchDoubleSided.visible = !mDoubleSided;
        mSwitchSingleSided.visible = mDoubleSided;

        mKeepAspectUnchecked = addRenderableWidget(new Button(leftPos + 149, topPos + 49, 179, 73, 18, 19,
                KEEP_ASPECT_RATIO_TEXT, () -> {
            mKeepAspectRatio = true;
            mKeepAspectUnchecked.visible = false;
            mKeepAspectChecked.visible = true;
            mSyncAspectRatio = SYNC_WIDTH_WITH_HEIGHT;
        }));
        mKeepAspectChecked = addRenderableWidget(new Button(leftPos + 149, topPos + 49, 179, 93, 18, 19,
                KEEP_ASPECT_RATIO_TEXT, () -> {
            mKeepAspectRatio = false;
            mKeepAspectChecked.visible = false;
            mKeepAspectUnchecked.visible = true;
            mSyncAspectRatio = SYNCED;
        }));
        mKeepAspectRatio = mEntity.mKeepAspectRatio;
        mKeepAspectUnchecked.visible = !mKeepAspectRatio;
        mKeepAspectChecked.visible = mKeepAspectRatio;

        if (mKeepAspectRatio) {
            mSyncAspectRatio = SYNC_WIDTH_WITH_HEIGHT;
        } else {
            mSyncAspectRatio = SYNCED;
        }
    }

    private void updateRotation(ProjectorBlock.InternalRotation newRotation) {
        if (!mInvalidOffsetX && !mInvalidOffsetY && !mInvalidOffsetZ) {
            Vector3f absolute = relativeToAbsolute(mImageOffset, mImageSize, mRotation);
            Vector3f newRelative = absoluteToRelative(absolute, mImageSize, newRotation);
            mOffsetXInput.setValue(toSignedString(newRelative.x()));
            mOffsetYInput.setValue(toSignedString(newRelative.y()));
            mOffsetZInput.setValue(toSignedString(newRelative.z()));
        }
        mRotation = newRotation;
    }

    private void updateSize(Vec2 newSize) {
        if (!mInvalidOffsetX && !mInvalidOffsetY && !mInvalidOffsetZ) {
            Vector3f absolute = relativeToAbsolute(mImageOffset, mImageSize, mRotation);
            Vector3f newRelative = absoluteToRelative(absolute, newSize, mRotation);
            mOffsetXInput.setValue(toSignedString(newRelative.x()));
            mOffsetYInput.setValue(toSignedString(newRelative.y()));
            mOffsetZInput.setValue(toSignedString(newRelative.z()));
        }
        mImageSize = newSize;
        mBroadcastSizeChanges = true;
        if (mWidthInput != null && mHeightInput != null) {
            if (!mWidthInput.isFocused()) {
                mWidthInput.setValue(toOptionalSignedString(mImageSize.x));
            }
            if (!mHeightInput.isFocused()) {
                mHeightInput.setValue(toOptionalSignedString(mImageSize.y));
            }
        }
        mBroadcastSizeChanges = false;
    }

    @Override
    public void containerTick() {
        if (mEntity == null) {
            minecraft.player.closeContainer();
            return;
        }
        mURLInput.tick();
        mColorInput.tick();
        mWidthInput.tick();
        mHeightInput.tick();
        mOffsetXInput.tick();
        mOffsetYInput.tick();
        mOffsetZInput.tick();
        if (!mURLInput.isFocused()) {
            if (!mInvalidURL) {
                mEntity.mLocation = mURLInput.getValue();
            }
            if (mSyncAspectRatio != SYNCED &&
                    !mInvalidWidth && !mInvalidHeight) {
                Slide slide = SlideState.getSlide(mEntity.mLocation);
                if (slide != null) {
                    float aspect = slide.getImageAspectRatio();
                    if (!Float.isNaN(aspect)) {
                        Vec2 newSize = mImageSize;
                        if (mSyncAspectRatio == SYNC_WIDTH_WITH_HEIGHT) {
                            newSize = new Vec2(mImageSize.y * aspect, mImageSize.y);
                        } else if (mSyncAspectRatio == SYNC_HEIGHT_WITH_WIDTH) {
                            newSize = new Vec2(mImageSize.x, mImageSize.x / aspect);
                        }
                        mSyncAspectRatio = SYNCED;
                        updateSize(newSize);
                    }
                }
            }
        }
    }

    @Override
    public void removed() {
        super.removed();
        // minecraft.keyboardHandler.setSendRepeatsToGui(false);
        if (mEntity == null) {
            return;
        }
        final ProjectorUpdatePacket packet = new ProjectorUpdatePacket(mEntity, mRotation);
        final boolean invalidSize = mInvalidWidth || mInvalidHeight;
        final boolean invalidOffset = mInvalidOffsetX || mInvalidOffsetY || mInvalidOffsetZ;
        if (!mInvalidColor) {
            mEntity.mColor = mImageColor;
        }
        if (!invalidSize) {
            mEntity.mWidth = mImageSize.x;
            mEntity.mHeight = mImageSize.y;
        }
        if (!invalidOffset) {
            mEntity.mOffsetX = mImageOffset.x();
            mEntity.mOffsetY = mImageOffset.y();
            mEntity.mOffsetZ = mImageOffset.z();
        }
        mEntity.mDoubleSided = mDoubleSided;
        mEntity.mKeepAspectRatio = mKeepAspectRatio;
        packet.sendToServer();
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollY) {
        if (mWidthInput.isMouseOver(mouseX, mouseY)) {
            if (!mInvalidWidth) {
                updateSize(new Vec2(Math.round(mImageSize.x * 2.0 + scrollY) * 0.5f, mImageSize.y));
                if (mKeepAspectRatio) {
                    mSyncAspectRatio = SYNC_HEIGHT_WITH_WIDTH;
                }
                return true;
            }
        } else if (mHeightInput.isMouseOver(mouseX, mouseY)) {
            if (!mInvalidHeight) {
                updateSize(new Vec2(mImageSize.x, Math.round(mImageSize.y * 2.0 + scrollY) * 0.5f));
                if (mKeepAspectRatio) {
                    mSyncAspectRatio = SYNC_WIDTH_WITH_HEIGHT;
                }
                return true;
            }
        }
        return super.mouseScrolled(mouseX, mouseY, scrollY);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifier) {
        if (keyCode == GLFW.GLFW_KEY_ESCAPE) {
            minecraft.player.closeContainer();
            return true;
        }

        return mURLInput.keyPressed(keyCode, scanCode, modifier) || mURLInput.canConsumeInput()
                || mColorInput.keyPressed(keyCode, scanCode, modifier) || mColorInput.canConsumeInput()
                || mWidthInput.keyPressed(keyCode, scanCode, modifier) || mWidthInput.canConsumeInput()
                || mHeightInput.keyPressed(keyCode, scanCode, modifier) || mHeightInput.canConsumeInput()
                || mOffsetXInput.keyPressed(keyCode, scanCode, modifier) || mOffsetXInput.canConsumeInput()
                || mOffsetYInput.keyPressed(keyCode, scanCode, modifier) || mOffsetYInput.canConsumeInput()
                || mOffsetZInput.keyPressed(keyCode, scanCode, modifier) || mOffsetZInput.canConsumeInput()
                || super.keyPressed(keyCode, scanCode, modifier);
    }

    @Override
    public void render(PoseStack pPoseStack, int pMouseX, int pMouseY, float pPartialTick) {
        if (mEntity != null) {
            super.render(pPoseStack, pMouseX, pMouseY, pPartialTick);
        }
    }

    @Override
    protected void renderBg(PoseStack stack, float partialTicks, int mouseX, int mouseY) {
        renderBackground(stack);
        RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, 1.0F);
        RenderSystem.setShaderTexture(0, GUI_TEXTURE);
        blit(stack, leftPos, topPos, 0, 0, imageWidth, imageHeight);
    }

    @Override
    protected void renderLabels(PoseStack stack, int mouseX, int mouseY) {
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.setShaderTexture(0, GUI_TEXTURE);

        int alpha = mImageColor >>> 24;
        if (alpha > 0) {
            int red = (mImageColor >> 16) & 255, green = (mImageColor >> 8) & 255, blue = mImageColor & 255;
            RenderSystem.setShaderColor(red / 255.0F, green / 255.0F, blue / 255.0F, alpha / 255.0F);
            blit(stack, 38, 157, 180, 194, 10, 10);
            blit(stack, 82, 185, 180, 194, 17, 17);
        }

        RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, 1.0F);
        blit(stack, 82, 185, 202, 194 - mRotation.ordinal() * 20, 17, 17);

        drawCenteredStringWithoutShadow(stack, font, IMAGE_TEXT, 12);
        drawCenteredStringWithoutShadow(stack, font, OFFSET_TEXT, 86);
        drawCenteredStringWithoutShadow(stack, font, OTHERS_TEXT, 138);

        int offsetX = mouseX - leftPos, offsetY = mouseY - topPos;
        if (offsetX >= 9 && offsetY >= 27 && offsetX < 27 && offsetY < 46) {
            renderTooltip(stack, URL_TEXT, offsetX, offsetY);
        } else if (offsetX >= 34 && offsetY >= 153 && offsetX < 52 && offsetY < 172) {
            renderTooltip(stack, COLOR_TEXT, offsetX, offsetY);
        } else if (offsetX >= 9 && offsetY >= 49 && offsetX < 27 && offsetY < 68) {
            renderTooltip(stack, WIDTH_TEXT, offsetX, offsetY);
        } else if (offsetX >= 79 && offsetY >= 49 && offsetX < 97 && offsetY < 68) {
            renderTooltip(stack, HEIGHT_TEXT, offsetX, offsetY);
        } else if (offsetX >= 149 && offsetY >= 49 && offsetX < 167 && offsetY < 68) {
            renderTooltip(stack, KEEP_ASPECT_RATIO_TEXT, offsetX, offsetY);
        } else if (offsetX >= 9 && offsetY >= 101 && offsetX < 27 && offsetY < 120) {
            renderTooltip(stack, OFFSET_X_TEXT, offsetX, offsetY);
        } else if (offsetX >= 63 && offsetY >= 101 && offsetX < 81 && offsetY < 120) {
            renderTooltip(stack, OFFSET_Y_TEXT, offsetX, offsetY);
        } else if (offsetX >= 117 && offsetY >= 101 && offsetX < 135 && offsetY < 120) {
            renderTooltip(stack, OFFSET_Z_TEXT, offsetX, offsetY);
        } else if (offsetX >= 117 && offsetY >= 153 && offsetX < 135 && offsetY < 172) {
            renderTooltip(stack, FLIP_TEXT, offsetX, offsetY);
        } else if (offsetX >= 142 && offsetY >= 153 && offsetX < 160 && offsetY < 172) {
            renderTooltip(stack, ROTATE_TEXT, offsetX, offsetY);
        } else if (offsetX >= 9 && offsetY >= 153 && offsetX < 27 && offsetY < 172) {
            renderTooltip(stack, SINGLE_DOUBLE_SIDED_TEXT, offsetX, offsetY);
        }
    }

    private static void drawCenteredStringWithoutShadow(PoseStack stack, Font renderer, Component string, int y) {
        renderer.draw(stack, string, 88 - renderer.width(string) / 2.0F, y, 0x404040);
    }

    private static float parseFloat(String text) {
        return (float) new ExpressionBuilder(text).implicitMultiplication(false).build().evaluate();
    }

    private static String toOptionalSignedString(float f) {
        return Float.toString(Math.round(f * 1.0E3F) / 1.0E3F);
    }

    private static String toSignedString(float f) {
        return Float.isNaN(f) ? "" + f : Math.copySign(1.0F, f) <= 0 ? "-" + Math.round(0.0F - f * 1.0E3F) / 1.0E3F :
                "+" + Math.round(f * 1.0E3F) / 1.0E3F;
    }

    private static Vector3f relativeToAbsolute(Vector3f relatedOffset, Vec2 size,
                                               ProjectorBlock.InternalRotation rotation) {
        Vector4f center = new Vector4f(0.5F * size.x, 0.0F, 0.5F * size.y, 1.0F);
        // matrix 6: offset for slide (center[new] = center[old] + offset)
        center.add(relatedOffset.x() * center.w(), -relatedOffset.z() * center.w(), relatedOffset.y() * center.w(),
                0.0F);
        // matrix 5: translation for slide
        center.add(-0.5F * center.w(), 0.0F, 0.5F * center.w() - size.y * center.w(), 0.0F);
        // matrix 4: internal rotation
        center = rotation.transform(center);
        // ok, that's enough
        return new Vector3f(center.x() / center.w(), center.y() / center.w(), center.z() / center.w());
    }

    private static Vector3f absoluteToRelative(Vector3f absoluteOffset, Vec2 size,
                                               ProjectorBlock.InternalRotation rotation) {
        Vector4f center = new Vector4f(absoluteOffset, 1.0F);
        // inverse matrix 4: internal rotation
        rotation.invert().transform(center);
        // inverse matrix 5: translation for slide
        center.add(0.5F * center.w(), 0.0F, -0.5F * center.w() + size.y * center.w(), 0.0F);
        // subtract (offset = center[new] - center[old])
        center.add(-0.5F * size.x * center.w(), 0.0F, -0.5F * size.y * center.w(), 0.0F);
        // ok, that's enough (remember it is (a, -c, b) => (a, b, c))
        return new Vector3f(center.x(), center.z(), -center.y());
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
        public void renderWidget(PoseStack stack, int mouseX, int mouseY, float partialTicks) {
            RenderSystem.enableBlend();
            RenderSystem.defaultBlendFunc();
            RenderSystem.setShaderTexture(0, GUI_TEXTURE);
            RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, alpha);
            blit(stack, getX(), getY(), u, v, width, height);
        }

        @Override
        protected void updateWidgetNarration(NarrationElementOutput output) {
            output.add(NarratedElementType.TITLE, msg);
        }
    }
}
