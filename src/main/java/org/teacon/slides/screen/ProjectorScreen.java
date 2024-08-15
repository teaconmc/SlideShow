package org.teacon.slides.screen;

import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.FieldsAreNonnullByDefault;
import net.minecraft.MethodsReturnNonnullByDefault;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractButton;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.narration.NarratedElementType;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.level.block.Rotation;
import net.neoforged.neoforge.network.PacketDistributor;
import org.joml.Vector2i;
import org.joml.Vector3i;
import org.lwjgl.glfw.GLFW;
import org.teacon.slides.SlideShow;
import org.teacon.slides.block.ProjectorBlock;
import org.teacon.slides.block.ProjectorBlockEntity.ColorTransform;
import org.teacon.slides.calc.CalcMicros;
import org.teacon.slides.inventory.ProjectorContainerMenu;
import org.teacon.slides.network.ProjectorUpdatePacket;
import org.teacon.slides.network.ProjectorUpdatePacket.Category;

import javax.annotation.ParametersAreNonnullByDefault;
import java.util.Arrays;
import java.util.EnumSet;
import java.util.Objects;

@FieldsAreNonnullByDefault
@MethodsReturnNonnullByDefault
@ParametersAreNonnullByDefault
public final class ProjectorScreen extends AbstractContainerScreen<ProjectorContainerMenu> {
    private static final ResourceLocation
            GUI_TEXTURE = SlideShow.id("textures/gui/projector_gui.png");

    private static final int
            GUI_WIDTH = 512, GUI_HEIGHT = 384,
            COLOR_MAX_LENGTH = 8, VALID_TEXT_COLOR = 0xE0E0E0, INVALID_TEXT_COLOR = 0xE04B4B;

    private static final Component
            SIZE_TEXT = Component.translatable("gui.slide_show.section.size"),
            OFFSET_TEXT = Component.translatable("gui.slide_show.section.offset"),
            OTHERS_FIRST_TEXT = Component.translatable("gui.slide_show.section.others.first"),
            OTHERS_SECOND_TEXT = Component.translatable("gui.slide_show.section.others.second"),
            COLOR_TEXT = Component.translatable("gui.slide_show.color"),
            WIDTH_TEXT = Component.translatable("gui.slide_show.width"),
            HEIGHT_TEXT = Component.translatable("gui.slide_show.height"),
            OFFSET_X_TEXT = Component.translatable("gui.slide_show.offset_x"),
            OFFSET_Y_TEXT = Component.translatable("gui.slide_show.offset_y"),
            OFFSET_Z_TEXT = Component.translatable("gui.slide_show.offset_z"),
            MOVE_TO_BEGIN_TEXT = Component.translatable("gui.slide_show.move_to_begin"),
            MOVE_UPWARD_TEXT = Component.translatable("gui.slide_show.move_upward"),
            MOVE_DOWNWARD_TEXT = Component.translatable("gui.slide_show.move_downward"),
            MOVE_TO_END_TEXT = Component.translatable("gui.slide_show.move_to_end"),
            FLIP_TEXT = Component.translatable("gui.slide_show.flip"),
            ROTATE_TEXT = Component.translatable("gui.slide_show.rotate"),
            SINGLE_DOUBLE_SIDED_TEXT = Component.translatable("gui.slide_show.single_double_sided");

    private final LazyWidget<EditBox> mColorInput;
    private final LazyWidget<EditBox> mWidthInput;
    private final LazyWidget<EditBox> mHeightInput;
    private final LazyWidget<EditBox> mOffsetXInput;
    private final LazyWidget<EditBox> mOffsetYInput;
    private final LazyWidget<EditBox> mOffsetZInput;

    private final LazyWidget<Button> mMoveToBegin;
    private final LazyWidget<Button> mMoveUpward;
    private final LazyWidget<Button> mMoveDownward;
    private final LazyWidget<Button> mMoveToEnd;
    private final LazyWidget<Button> mFlipRotation;
    private final LazyWidget<Button> mCycleRotation;
    private final LazyWidget<Button> mSwitchSingleSided;
    private final LazyWidget<Button> mSwitchDoubleSided;

    private final BlockPos mBlockPos;
    private final Vector2i mSizeMicros;
    private final Vector3i mOffsetMicros;
    private final ColorTransform mColorTransform;
    private ProjectorBlock.InternalRotation mCurrentRotation;
    private final EnumSet<Category> mSyncedCategories = EnumSet.allOf(Category.class);

    public ProjectorScreen(ProjectorContainerMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title);
        imageWidth = 404;
        imageHeight = 271;
        // initialize variables
        mBlockPos = menu.tilePos;
        mSizeMicros = menu.tileSizeMicros;
        mOffsetMicros = menu.tileOffsetMicros;
        mColorTransform = menu.tileColorTransform;
        mCurrentRotation = menu.tileInitialRotation;
        // color input
        mColorInput = LazyWidget.of(String.format("%08X", mColorTransform.color), EditBox::getValue, value -> {
            var input = new EditBox(font, leftPos + 342, topPos + 161, 56, 16, COLOR_TEXT);
            input.setMaxLength(COLOR_MAX_LENGTH);
            input.setResponder(text -> {
                try {
                    this.syncRemote(Category.SET_ADDITIONAL_COLOR, Integer.parseUnsignedInt(text, 16));
                    input.setTextColor(VALID_TEXT_COLOR);
                } catch (IllegalArgumentException e) {
                    this.detachRemote(Category.SET_ADDITIONAL_COLOR);
                } catch (Exception e) {
                    SlideShow.LOGGER.error("Critical error on parsing color: {}", text, e);
                    this.detachRemote(Category.SET_ADDITIONAL_COLOR);
                }
            });
            input.setValue(value);
            return input;
        });
        // width input
        mWidthInput = LazyWidget.of(CalcMicros.toString(mSizeMicros.x, false), EditBox::getValue, value -> {
            var input = new EditBox(font, leftPos + 255, topPos + 37, 56, 16, WIDTH_TEXT);
            input.setResponder(text -> {
                try {
                    this.syncRemote(Category.SET_WIDTH_MICROS, CalcMicros.fromString(text, mSizeMicros.x));
                    input.setTextColor(VALID_TEXT_COLOR);
                } catch (IllegalArgumentException e) {
                    this.detachRemote(Category.SET_WIDTH_MICROS);
                    input.setTextColor(INVALID_TEXT_COLOR);
                } catch (Exception e) {
                    SlideShow.LOGGER.error("Critical error on parsing width: {}", text, e);
                    this.detachRemote(Category.SET_WIDTH_MICROS);
                    input.setTextColor(INVALID_TEXT_COLOR);
                }
            });
            input.setValue(value);
            return input;
        });
        // height input
        mHeightInput = LazyWidget.of(CalcMicros.toString(mSizeMicros.y, false), EditBox::getValue, value -> {
            var input = new EditBox(font, leftPos + 255, topPos + 59, 56, 16, HEIGHT_TEXT);
            input.setResponder(text -> {
                try {
                    this.syncRemote(Category.SET_HEIGHT_MICROS, CalcMicros.fromString(text, mSizeMicros.y));
                    input.setTextColor(VALID_TEXT_COLOR);
                } catch (IllegalArgumentException e) {
                    this.detachRemote(Category.SET_HEIGHT_MICROS);
                    input.setTextColor(INVALID_TEXT_COLOR);
                } catch (Exception e) {
                    SlideShow.LOGGER.error("Critical error on parsing height: {}", text, e);
                    this.detachRemote(Category.SET_HEIGHT_MICROS);
                    input.setTextColor(INVALID_TEXT_COLOR);
                }
            });
            input.setValue(value);
            return input;
        });
        // offset x input
        mOffsetXInput = LazyWidget.of(CalcMicros.toString(mOffsetMicros.x, true), EditBox::getValue, value -> {
            var input = new EditBox(font, leftPos + 255, topPos + 117, 56, 16, OFFSET_X_TEXT);
            input.setResponder(text -> {
                try {
                    this.syncRemote(Category.SET_OFFSET_X_MICROS, CalcMicros.fromString(text, mOffsetMicros.x));
                    input.setTextColor(VALID_TEXT_COLOR);
                } catch (IllegalArgumentException e) {
                    this.detachRemote(Category.SET_OFFSET_X_MICROS);
                    input.setTextColor(INVALID_TEXT_COLOR);
                } catch (Exception e) {
                    SlideShow.LOGGER.error("Critical error on parsing offset x: {}", text, e);
                    this.detachRemote(Category.SET_OFFSET_X_MICROS);
                    input.setTextColor(INVALID_TEXT_COLOR);
                }
            });
            input.setValue(value);
            return input;
        });
        // offset y input
        mOffsetYInput = LazyWidget.of(CalcMicros.toString(mOffsetMicros.y, true), EditBox::getValue, value -> {
            var input = new EditBox(font, leftPos + 255, topPos + 139, 56, 16, OFFSET_Y_TEXT);
            input.setResponder(text -> {
                try {
                    this.syncRemote(Category.SET_OFFSET_Y_MICROS, CalcMicros.fromString(text, mOffsetMicros.y));
                    input.setTextColor(VALID_TEXT_COLOR);
                } catch (IllegalArgumentException e) {
                    this.detachRemote(Category.SET_OFFSET_Y_MICROS);
                    input.setTextColor(INVALID_TEXT_COLOR);
                } catch (Exception e) {
                    SlideShow.LOGGER.error("Critical error on parsing offset y: {}", text, e);
                    this.detachRemote(Category.SET_OFFSET_Y_MICROS);
                    input.setTextColor(INVALID_TEXT_COLOR);
                }
            });
            input.setValue(value);
            return input;
        });
        // offset z input
        mOffsetZInput = LazyWidget.of(CalcMicros.toString(mOffsetMicros.z, true), EditBox::getValue, value -> {
            var input = new EditBox(font, leftPos + 255, topPos + 161, 56, 16, OFFSET_Z_TEXT);
            input.setResponder(text -> {
                try {
                    this.syncRemote(Category.SET_OFFSET_Z_MICROS, CalcMicros.fromString(text, mOffsetMicros.z));
                    input.setTextColor(VALID_TEXT_COLOR);
                } catch (IllegalArgumentException e) {
                    this.detachRemote(Category.SET_OFFSET_Z_MICROS);
                    input.setTextColor(INVALID_TEXT_COLOR);
                } catch (Exception e) {
                    SlideShow.LOGGER.error("Critical error on parsing offset z: {}", text, e);
                    this.detachRemote(Category.SET_OFFSET_Z_MICROS);
                    input.setTextColor(INVALID_TEXT_COLOR);
                }
            });
            input.setValue(value);
            return input;
        });
        // move to begin
        mMoveToBegin = LazyWidget.of(true, b -> b.visible, value -> {
            var button = new Button(leftPos + 19, topPos + 126, 19, 126, 18, 19, MOVE_TO_BEGIN_TEXT, () -> {
                var offset = -ProjectorBlock.SLIDE_ITEM_HANDLER_CAPACITY;
                this.syncRemote(Category.MOVE_SLIDE_ITEMS, offset);
            });
            button.visible = value;
            return button;
        });
        // move upward
        mMoveUpward = LazyWidget.of(true, b -> b.visible, value -> {
            var button = new Button(leftPos + 61, topPos + 126, 61, 126, 18, 19, MOVE_UPWARD_TEXT, () -> {
                var offset = -1;
                this.syncRemote(Category.MOVE_SLIDE_ITEMS, offset);
            });
            button.visible = value;
            return button;
        });
        // move downward
        mMoveDownward = LazyWidget.of(true, b -> b.visible, value -> {
            var button = new Button(leftPos + 151, topPos + 126, 151, 126, 18, 19, MOVE_DOWNWARD_TEXT, () -> {
                var offset = 1;
                this.syncRemote(Category.MOVE_SLIDE_ITEMS, offset);
            });
            button.visible = value;
            return button;
        });
        // move to the end
        mMoveToEnd = LazyWidget.of(true, b -> b.visible, value -> {
            var button = new Button(leftPos + 193, topPos + 126, 193, 126, 18, 19, MOVE_TO_END_TEXT, () -> {
                var offset = ProjectorBlock.SLIDE_ITEM_HANDLER_CAPACITY;
                this.syncRemote(Category.MOVE_SLIDE_ITEMS, offset);
            });
            button.visible = value;
            return button;
        });
        // internal rotation buttons
        mFlipRotation = LazyWidget.of(true, b -> b.visible, value -> {
            var button = new Button(leftPos + 378, topPos + 46, 378, 46, 18, 19, FLIP_TEXT, () -> {
                var newRotation = mCurrentRotation.flip();
                this.syncRemote(Category.SET_INTERNAL_ROTATION, newRotation.ordinal());
            });
            button.visible = value;
            return button;
        });
        mCycleRotation = LazyWidget.of(true, b -> b.visible, value -> {
            var button = new Button(leftPos + 350, topPos + 46, 350, 46, 18, 19, ROTATE_TEXT, () -> {
                var newRotation = mCurrentRotation.compose(Rotation.CLOCKWISE_90);
                this.syncRemote(Category.SET_INTERNAL_ROTATION, newRotation.ordinal());
            });
            button.visible = value;
            return button;
        });
        // single sided / double sided
        mSwitchSingleSided = LazyWidget.of(mColorTransform.doubleSided, b -> b.visible, value -> {
            var button = new Button(leftPos + 322, topPos + 46, 322, 46, 18, 19, SINGLE_DOUBLE_SIDED_TEXT, () -> {
                if (mColorTransform.doubleSided) {
                    this.syncRemote(Category.SET_DOUBLE_SIDED, 0);
                }
            });
            button.visible = value;
            return button;
        });
        mSwitchDoubleSided = LazyWidget.of(!mColorTransform.doubleSided, b -> b.visible, value -> {
            var button = new Button(leftPos + 322, topPos + 46, 322, 277, 18, 19, SINGLE_DOUBLE_SIDED_TEXT, () -> {
                if (!mColorTransform.doubleSided) {
                    this.syncRemote(Category.SET_DOUBLE_SIDED, 1);
                }
            });
            button.visible = value;
            return button;
        });
    }

    @Override
    protected void init() {
        super.init();

        addRenderableWidget(mColorInput.refresh());
        addRenderableWidget(mWidthInput.refresh());
        addRenderableWidget(mHeightInput.refresh());
        addRenderableWidget(mOffsetXInput.refresh());
        addRenderableWidget(mOffsetYInput.refresh());
        addRenderableWidget(mOffsetZInput.refresh());

        addRenderableWidget(mMoveToBegin.refresh());
        addRenderableWidget(mMoveUpward.refresh());
        addRenderableWidget(mMoveDownward.refresh());
        addRenderableWidget(mMoveToEnd.refresh());
        addRenderableWidget(mFlipRotation.refresh());
        addRenderableWidget(mCycleRotation.refresh());
        addRenderableWidget(mSwitchSingleSided.refresh());
        addRenderableWidget(mSwitchDoubleSided.refresh());

        setInitialFocus(mColorInput.get());
    }

    private boolean allSynced(Category... categories) {
        return this.mSyncedCategories.containsAll(Arrays.asList(categories));
    }

    private void syncRemote(Category category, int value) {
        var changed = switch (category) {
            case MOVE_SLIDE_ITEMS -> value != 0;
            case SET_WIDTH_MICROS -> this.updateSize(new Vector2i(value, mSizeMicros.y));
            case SET_HEIGHT_MICROS -> this.updateSize(new Vector2i(mSizeMicros.x, value));
            case SET_OFFSET_X_MICROS -> {
                var old = mOffsetMicros.x;
                mOffsetMicros.x = value;
                yield old != value;
            }
            case SET_OFFSET_Y_MICROS -> {
                var old = mOffsetMicros.y;
                mOffsetMicros.y = value;
                yield old != value;
            }
            case SET_OFFSET_Z_MICROS -> {
                var old = mOffsetMicros.z;
                mOffsetMicros.z = value;
                yield old != value;
            }
            case SET_ADDITIONAL_COLOR -> {
                var old = mColorTransform.color;
                mColorTransform.color = value;
                yield old != value;
            }
            case SET_DOUBLE_SIDED -> this.updateDoubleSided(value != 0);
            case SET_INTERNAL_ROTATION -> this.updateRotation(ProjectorBlock.InternalRotation.BY_ID.apply(value));
        };
        if (changed) {
            PacketDistributor.sendToServer(new ProjectorUpdatePacket(category, mBlockPos, value));
        }
        this.mSyncedCategories.add(category);
    }

    private void detachRemote(Category category) {
        this.mSyncedCategories.remove(category);
    }

    private boolean updateRotation(ProjectorBlock.InternalRotation newRotation) {
        // noinspection DuplicatedCode
        if (this.allSynced(Category.SET_OFFSET_X_MICROS, Category.SET_OFFSET_Y_MICROS, Category.SET_OFFSET_Z_MICROS)) {
            var absoluteMicros = CalcMicros.fromRelToAbs(mOffsetMicros, mSizeMicros, mCurrentRotation);
            var newRelativeMicros = CalcMicros.fromAbsToRel(absoluteMicros, mSizeMicros, newRotation);
            mOffsetXInput.get().setValue(CalcMicros.toString(newRelativeMicros.x(), true));
            mOffsetYInput.get().setValue(CalcMicros.toString(newRelativeMicros.y(), true));
            mOffsetZInput.get().setValue(CalcMicros.toString(newRelativeMicros.z(), true));
        }
        if (!mCurrentRotation.equals(newRotation)) {
            mCurrentRotation = newRotation;
            return true;
        }
        return false;
    }

    private boolean updateSize(Vector2i newSizeMicros) {
        // noinspection DuplicatedCode
        if (this.allSynced(Category.SET_OFFSET_X_MICROS, Category.SET_OFFSET_Y_MICROS, Category.SET_OFFSET_Z_MICROS)) {
            var absoluteMicros = CalcMicros.fromRelToAbs(mOffsetMicros, mSizeMicros, mCurrentRotation);
            var newRelativeMicros = CalcMicros.fromAbsToRel(absoluteMicros, newSizeMicros, mCurrentRotation);
            mOffsetXInput.get().setValue(CalcMicros.toString(newRelativeMicros.x(), true));
            mOffsetYInput.get().setValue(CalcMicros.toString(newRelativeMicros.y(), true));
            mOffsetZInput.get().setValue(CalcMicros.toString(newRelativeMicros.z(), true));
        }
        if (!mSizeMicros.equals(newSizeMicros)) {
            mSizeMicros.set(newSizeMicros);
            return true;
        }
        return false;
    }

    private boolean updateDoubleSided(boolean doubleSided) {
        var old = mColorTransform.doubleSided;
        if (old != doubleSided) {
            mColorTransform.doubleSided = doubleSided;
            mSwitchSingleSided.get().visible = doubleSided;
            mSwitchDoubleSided.get().visible = !doubleSided;
            return true;
        }
        return false;
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        if (mWidthInput.get().isMouseOver(mouseX, mouseY)) {
            if (this.allSynced(Category.SET_WIDTH_MICROS)) {
                mWidthInput.get().setValue(CalcMicros.toString((int) Math.rint(mSizeMicros.x + scrollY * 25E4), false));
                return true;
            }
        } else if (mHeightInput.get().isMouseOver(mouseX, mouseY)) {
            if (this.allSynced(Category.SET_HEIGHT_MICROS)) {
                mHeightInput.get().setValue(CalcMicros.toString((int) Math.rint(mSizeMicros.y + scrollY * 25E4), false));
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
        RenderSystem.setShaderColor(1F, 1F, 1F, 1F);
        RenderSystem.setShaderTexture(0, GUI_TEXTURE);
        gui.blit(GUI_TEXTURE, leftPos, topPos, 0F, 0F, imageWidth, imageHeight, GUI_WIDTH, GUI_HEIGHT);
    }

    @Override
    protected void renderLabels(GuiGraphics gui, int mouseX, int mouseY) {
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.setShaderTexture(0, GUI_TEXTURE);

        var alpha = mColorTransform.color >>> 24;
        if (alpha > 0) {
            var blue = mColorTransform.color & 255;
            var green = (mColorTransform.color >> 8) & 255;
            var red = (mColorTransform.color >> 8 + 8) & 255;
            RenderSystem.setShaderColor(red / 255F, green / 255F, blue / 255F, alpha / 255F);
            gui.blit(GUI_TEXTURE, 326, 163, 357F, 278F, 10, 10, GUI_WIDTH, GUI_HEIGHT);
            gui.blit(GUI_TEXTURE, 342, 95, 34, 34, 357F, 278F, 17, 17, GUI_WIDTH, GUI_HEIGHT);
        }

        RenderSystem.setShaderColor(1F, 1F, 1F, 1F);
        var exampleUOffset = 287F - mCurrentRotation.ordinal() * 35F;
        gui.blit(GUI_TEXTURE, 342, 95, 34, 34, exampleUOffset, 278F, 17, 17, GUI_WIDTH, GUI_HEIGHT);

        gui.drawString(font, SIZE_TEXT.getVisualOrderText(), 273 - font.width(SIZE_TEXT) / 2F, 15, 0x404040, false);
        gui.drawString(font, OFFSET_TEXT.getVisualOrderText(), 273 - font.width(OFFSET_TEXT) / 2F, 95, 0x404040, false);
        gui.drawString(font, OTHERS_FIRST_TEXT.getVisualOrderText(), 361 - font.width(OTHERS_FIRST_TEXT) / 2F, 15, 0x404040, false);
        gui.drawString(font, OTHERS_SECOND_TEXT.getVisualOrderText(), 361 - font.width(OTHERS_SECOND_TEXT) / 2F, 26, 0x404040, false);
    }

    @Override
    protected void renderTooltip(GuiGraphics gui, int mouseX, int mouseY) {
        super.renderTooltip(gui, mouseX, mouseY);
        int offsetX = mouseX - leftPos, offsetY = mouseY - topPos;
        if (offsetX >= 322 && offsetY >= 159 && offsetX < 340 && offsetY < 178) {
            gui.renderTooltip(font, COLOR_TEXT, mouseX, mouseY);
        } else if (offsetX >= 235 && offsetY >= 35 && offsetX < 253 && offsetY < 54) {
            gui.renderTooltip(font, WIDTH_TEXT, mouseX, mouseY);
        } else if (offsetX >= 235 && offsetY >= 57 && offsetX < 253 && offsetY < 76) {
            gui.renderTooltip(font, HEIGHT_TEXT, mouseX, mouseY);
        } else if (offsetX >= 235 && offsetY >= 115 && offsetX < 253 && offsetY < 134) {
            gui.renderTooltip(font, OFFSET_X_TEXT, mouseX, mouseY);
        } else if (offsetX >= 235 && offsetY >= 137 && offsetX < 253 && offsetY < 156) {
            gui.renderTooltip(font, OFFSET_Y_TEXT, mouseX, mouseY);
        } else if (offsetX >= 235 && offsetY >= 159 && offsetX < 253 && offsetY < 178) {
            gui.renderTooltip(font, OFFSET_Z_TEXT, mouseX, mouseY);
        } else if (offsetX >= 19 && offsetY >= 126 && offsetX < 37 && offsetY < 145) {
            gui.renderTooltip(font, MOVE_TO_BEGIN_TEXT, mouseX, mouseY);
        } else if (offsetX >= 61 && offsetY >= 126 && offsetX < 79 && offsetY < 145) {
            gui.renderTooltip(font, MOVE_UPWARD_TEXT, mouseX, mouseY);
        } else if (offsetX >= 151 && offsetY >= 126 && offsetX < 169 && offsetY < 145) {
            gui.renderTooltip(font, MOVE_DOWNWARD_TEXT, mouseX, mouseY);
        } else if (offsetX >= 193 && offsetY >= 126 && offsetX < 211 && offsetY < 145) {
            gui.renderTooltip(font, MOVE_TO_END_TEXT, mouseX, mouseY);
        } else if (offsetX >= 378 && offsetY >= 46 && offsetX < 396 && offsetY < 65) {
            gui.renderTooltip(font, FLIP_TEXT, mouseX, mouseY);
        } else if (offsetX >= 350 && offsetY >= 46 && offsetX < 368 && offsetY < 65) {
            gui.renderTooltip(font, ROTATE_TEXT, mouseX, mouseY);
        } else if (offsetX >= 322 && offsetY >= 46 && offsetX < 340 && offsetY < 65) {
            gui.renderTooltip(font, SINGLE_DOUBLE_SIDED_TEXT, mouseX, mouseY);
        }
    }

    @Override
    public void render(GuiGraphics gui, int mouseX, int mouseY, float partialTick) {
        super.render(gui, mouseX, mouseY, partialTick);
        this.renderTooltip(gui, mouseX, mouseY);
    }

    private static class Button extends AbstractButton {

        private final Runnable callback;
        private final Component msg;
        private final float u;
        private final float v;

        public Button(int x, int y, float u, float v, int width, int height, Component msg, Runnable callback) {
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
            RenderSystem.setShaderColor(1F, 1F, 1F, alpha);
            gui.blit(GUI_TEXTURE, getX(), getY(), u, v, width, height, GUI_WIDTH, GUI_HEIGHT);
        }

        @Override
        protected void updateWidgetNarration(NarrationElementOutput output) {
            output.add(NarratedElementType.TITLE, msg);
        }
    }
}
