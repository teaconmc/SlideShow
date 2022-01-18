package org.teacon.slides.projector;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Matrix4f;
import com.mojang.math.Vector3f;
import com.mojang.math.Vector4f;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.components.AbstractButton;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.TranslatableComponent;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.phys.Vec2;
import org.apache.commons.lang3.StringUtils;
import org.lwjgl.glfw.GLFW;
import org.teacon.slides.SlideShow;
import org.teacon.slides.renderer.SlideData;

import javax.annotation.ParametersAreNonnullByDefault;
import java.util.Objects;

@ParametersAreNonnullByDefault
public final class ProjectorControlScreen extends AbstractContainerScreen<ProjectorControlContainerMenu> {

    private static final ResourceLocation GUI_TEXTURE = new ResourceLocation("slide_show", "textures/gui/projector" +
            ".png");

    private static final TranslatableComponent
            IMAGE_TEXT = new TranslatableComponent("gui.slide_show.section.image"),
            OFFSET_TEXT = new TranslatableComponent("gui.slide_show.section.offset"),
            OTHERS_TEXT = new TranslatableComponent("gui.slide_show.section.others");

    private EditBox urlInput;
    private EditBox colorInput;
    private EditBox widthInput;
    private EditBox heightInput;
    private EditBox offsetXInput;
    private EditBox offsetYInput;
    private EditBox offsetZInput;

    private Button switchSingleSided;
    private Button switchDoubleSided;

    private String url = "";
    private boolean isDoubleSided;
    private int imgColor = 0x00000000;
    private Vec2 imgSize = Vec2.ONE;
    private Vector3f imgOffset = new Vector3f();

    private ProjectorBlock.InternalRotation rotation = ProjectorBlock.InternalRotation.NONE;

    private boolean invalidURL = true;
    private boolean invalidColor = true;
    private boolean invalidWidth = true, invalidHeight = true;
    private boolean invalidOffsetX = true, invalidOffsetY = true, invalidOffsetZ = true;

    public ProjectorControlScreen(ProjectorControlContainerMenu container, Inventory inv, Component title) {
        super(container, inv, title);
        this.imageWidth = 176;
        this.imageHeight = 217;
    }

    @Override
    protected void init() {
        super.init();
        Objects.requireNonNull(this.minecraft).keyboardHandler.setSendRepeatsToGui(true);

        // url input
        this.urlInput = new EditBox(this.font, this.leftPos + 30, this.topPos + 29, 137, 16,
                new TranslatableComponent("gui.slide_show.url"));
        this.urlInput.setMaxLength(32767);
        this.urlInput.setResponder(input -> {
            try {
                new java.net.URL(this.url = input);
                this.invalidURL = false;
            } catch (Exception e) {
                this.invalidURL = StringUtils.isNotBlank(input);
            }
            this.urlInput.setTextColor(this.invalidURL ? 0xE04B4B : 0xE0E0E0);
        });
        this.urlInput.setValue(this.menu.currentSlide.getImageLocation());
        this.urlInput.setVisible(true);
        this.addWidget(this.urlInput);
        this.setInitialFocus(this.urlInput);

        // color input
        this.colorInput = new EditBox(this.font, this.leftPos + 55, this.topPos + 155, 56, 16,
                new TranslatableComponent("gui.slide_show.color"));
        this.colorInput.setMaxLength(8);
        this.colorInput.setResponder(input -> {
            try {
                this.imgColor = Integer.parseUnsignedInt(input, 16);
                this.invalidColor = false;
            } catch (Exception e) {
                this.invalidColor = true;
            }
            this.colorInput.setTextColor(this.invalidColor ? 0xE04B4B : 0xE0E0E0);
        });
        this.colorInput.setValue(String.format("%08X", this.menu.currentSlide.getColor()));
        this.colorInput.setVisible(true);
        this.addWidget(this.colorInput);

        // width input
        this.widthInput = new EditBox(this.font, this.leftPos + 30, this.topPos + 51, 56, 16,
                new TranslatableComponent("gui.slide_show.width"));
        this.widthInput.setResponder(input -> {
            try {
                Vec2 newSize = new Vec2(Float.parseFloat(input), this.imgSize.y);
                if (!this.invalidOffsetX && !this.invalidOffsetY && !this.invalidOffsetZ) {
                    Vector3f absolute = relativeToAbsolute(this.imgOffset, this.imgSize, this.rotation);
                    Vector3f newRelative = absoluteToRelative(absolute, newSize, this.rotation);
                    this.offsetXInput.setValue(toSignedString(newRelative.x()));
                    this.offsetYInput.setValue(toSignedString(newRelative.y()));
                    this.offsetZInput.setValue(toSignedString(newRelative.z()));
                }
                this.imgSize = newSize;
                this.invalidWidth = false;
            } catch (Exception e) {
                this.invalidWidth = true;
            }
            this.widthInput.setTextColor(this.invalidWidth ? 0xE04B4B : 0xE0E0E0);
        });
        this.widthInput.setValue(toOptionalSignedString(this.menu.currentSlide.getSize().x));
        this.widthInput.setVisible(true);
        this.addWidget(this.widthInput);

        // height input
        this.heightInput = new EditBox(this.font, this.leftPos + 111, this.topPos + 51, 56, 16,
                new TranslatableComponent("gui.slide_show.height"));
        this.heightInput.setResponder(input -> {
            try {
                Vec2 newSize = new Vec2(this.imgSize.x, Float.parseFloat(input));
                if (!this.invalidOffsetX && !this.invalidOffsetY && !this.invalidOffsetZ) {
                    Vector3f absolute = relativeToAbsolute(this.imgOffset, this.imgSize, this.rotation);
                    Vector3f newRelative = absoluteToRelative(absolute, newSize, this.rotation);
                    this.offsetXInput.setValue(toSignedString(newRelative.x()));
                    this.offsetYInput.setValue(toSignedString(newRelative.y()));
                    this.offsetZInput.setValue(toSignedString(newRelative.z()));
                }
                this.imgSize = newSize;
                this.invalidHeight = false;
            } catch (Exception e) {
                this.invalidHeight = true;
            }
            this.heightInput.setTextColor(this.invalidHeight ? 0xE04B4B : 0xE0E0E0);
        });
        this.heightInput.setValue(toOptionalSignedString(this.menu.currentSlide.getSize().y));
        this.heightInput.setVisible(true);
        this.addWidget(this.heightInput);

        // offset x input
        this.offsetXInput = new EditBox(this.font, this.leftPos + 30, this.topPos + 103, 29, 16,
                new TranslatableComponent("gui.slide_show.offset_x"));
        this.offsetXInput.setResponder(input -> {
            try {
                this.imgOffset = new Vector3f(Float.parseFloat(input), this.imgOffset.y(), this.imgOffset.z());
                this.invalidOffsetX = false;
            } catch (Exception e) {
                this.invalidOffsetX = true;
            }
            this.offsetXInput.setTextColor(this.invalidOffsetX ? 0xE04B4B : 0xE0E0E0);
        });
        this.offsetXInput.setValue(toSignedString(this.menu.currentSlide.getOffset().x()));
        this.offsetXInput.setVisible(true);
        this.addWidget(this.offsetXInput);

        // offset y input
        this.offsetYInput = new EditBox(this.font, this.leftPos + 84, this.topPos + 103, 29, 16,
                new TranslatableComponent("gui.slide_show.offset_y"));
        this.offsetYInput.setResponder(input -> {
            try {
                this.imgOffset = new Vector3f(this.imgOffset.x(), Float.parseFloat(input), this.imgOffset.z());
                this.invalidOffsetY = false;
            } catch (Exception e) {
                this.invalidOffsetY = true;
            }
            this.offsetYInput.setTextColor(this.invalidOffsetY ? 0xE04B4B : 0xE0E0E0);
        });
        this.offsetYInput.setValue(toSignedString(this.menu.currentSlide.getOffset().y()));
        this.offsetYInput.setVisible(true);
        this.addWidget(this.offsetYInput);

        // offset z input
        this.offsetZInput = new EditBox(this.font, this.leftPos + 138, this.topPos + 103, 29, 16,
                new TranslatableComponent("gui.slide_show.offset_z"));
        this.offsetZInput.setResponder(input -> {
            try {
                this.imgOffset = new Vector3f(this.imgOffset.x(), this.imgOffset.y(), Float.parseFloat(input));
                this.invalidOffsetZ = false;
            } catch (Exception e) {
                this.invalidOffsetZ = true;
            }
            this.offsetZInput.setTextColor(this.invalidOffsetZ ? 0xE04B4B : 0xE0E0E0);
        });
        this.offsetZInput.setValue(toSignedString(this.menu.currentSlide.getOffset().z()));
        this.offsetZInput.setVisible(true);
        this.addWidget(this.offsetZInput);

        // internal rotation buttons
        this.addWidget(new Button(this.leftPos + 117, this.topPos + 153, 179, 153, 18, 19, new TranslatableComponent(
                "gui.slide_show.flip"), () -> {
            ProjectorBlock.InternalRotation newRotation = this.rotation.flip();
            if (!this.invalidOffsetX && !this.invalidOffsetY && !this.invalidOffsetZ) {
                Vector3f absolute = relativeToAbsolute(this.imgOffset, this.imgSize, this.rotation);
                Vector3f newRelative = absoluteToRelative(absolute, this.imgSize, newRotation);
                this.offsetXInput.setValue(toSignedString(newRelative.x()));
                this.offsetYInput.setValue(toSignedString(newRelative.y()));
                this.offsetZInput.setValue(toSignedString(newRelative.z()));
            }
            this.rotation = newRotation;
        }));
        this.addWidget(new Button(this.leftPos + 142, this.topPos + 153, 179, 173, 18, 19, new TranslatableComponent(
                "gui.slide_show.rotate"), () -> {
            ProjectorBlock.InternalRotation newRotation = this.rotation.compose(Rotation.CLOCKWISE_90);
            if (!this.invalidOffsetX && !this.invalidOffsetY && !this.invalidOffsetZ) {
                Vector3f absolute = relativeToAbsolute(this.imgOffset, this.imgSize, this.rotation);
                Vector3f newRelative = absoluteToRelative(absolute, this.imgSize, newRotation);
                this.offsetXInput.setValue(toSignedString(newRelative.x()));
                this.offsetYInput.setValue(toSignedString(newRelative.y()));
                this.offsetZInput.setValue(toSignedString(newRelative.z()));
            }
            this.rotation = newRotation;
        }));
        this.rotation = this.menu.rotation;

        // single sided / double sided
        this.switchSingleSided = this.addWidget(new Button(this.leftPos + 9, this.topPos + 153, 179, 113, 18, 19,
                new TranslatableComponent("gui.slide_show.single_double_sided"), () -> {
            this.isDoubleSided = false;
            this.switchDoubleSided.visible = true;
            this.switchSingleSided.visible = false;
        }));
        this.switchDoubleSided = this.addWidget(new Button(this.leftPos + 9, this.topPos + 153, 179, 133, 18, 19,
                new TranslatableComponent("gui.slide_show.single_double_sided"), () -> {
            this.isDoubleSided = true;
            this.switchSingleSided.visible = true;
            this.switchDoubleSided.visible = false;
        }));
        this.isDoubleSided = this.rotation.isFlipped() ? this.menu.currentSlide.isBackVisible() :
                this.menu.currentSlide.isFrontVisible();
        this.switchDoubleSided.visible = !this.isDoubleSided;
        this.switchSingleSided.visible = this.isDoubleSided;
    }

    @Override
    public void containerTick() {
//        super.containerTick();
//        this.urlInput.tick();
//        this.colorInput.tick();
//        this.widthInput.tick();
//        this.heightInput.tick();
//        this.offsetXInput.tick();
//        this.offsetYInput.tick();
//        this.offsetZInput.tick();
    }

    @Override
    public void removed() {
        final SlideData oldData = this.menu.currentSlide;
        final UpdateImageInfoPacket packet = new UpdateImageInfoPacket();
        final boolean invalidSize = this.invalidWidth || this.invalidHeight;
        final boolean invalidOffset = this.invalidOffsetX || this.invalidOffsetY || this.invalidOffsetZ;
        packet.pos = this.menu.pos;
        packet.data
                .setImageLocation(this.invalidURL ? oldData.getImageLocation() : this.url)
                .setColor(this.invalidColor ? oldData.getColor() : this.imgColor)
                .setSize(invalidSize ? oldData.getSize() : this.imgSize)
                .setOffset(invalidOffset ? oldData.getOffset() : this.imgOffset)
                .setFrontVisible(this.isDoubleSided || this.rotation.isFlipped())
                .setBackVisible(this.isDoubleSided || !this.rotation.isFlipped());
        packet.rotation = this.rotation;
        SlideShow.CHANNEL.sendToServer(packet);
        super.removed();
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifier) {
        if (keyCode == GLFW.GLFW_KEY_ESCAPE) {
            Objects.requireNonNull(Objects.requireNonNull(this.minecraft).player).closeContainer();
            return true;
        }

        return this.urlInput.keyPressed(keyCode, scanCode, modifier) || this.urlInput.canConsumeInput()
                || this.colorInput.keyPressed(keyCode, scanCode, modifier) || this.colorInput.canConsumeInput()
                || this.widthInput.keyPressed(keyCode, scanCode, modifier) || this.widthInput.canConsumeInput()
                || this.heightInput.keyPressed(keyCode, scanCode, modifier) || this.heightInput.canConsumeInput()
                || this.offsetXInput.keyPressed(keyCode, scanCode, modifier) || this.offsetXInput.canConsumeInput()
                || this.offsetYInput.keyPressed(keyCode, scanCode, modifier) || this.offsetYInput.canConsumeInput()
                || this.offsetZInput.keyPressed(keyCode, scanCode, modifier) || this.offsetZInput.canConsumeInput()
                || super.keyPressed(keyCode, scanCode, modifier);
    }

    @Override
    public void render(PoseStack stack, int mouseX, int mouseY, float partialTicks) {
        this.renderBackground(stack);
        super.render(stack, mouseX, mouseY, partialTicks);
        this.urlInput.render(stack, mouseX, mouseY, partialTicks);
        this.colorInput.render(stack, mouseX, mouseY, partialTicks);
        this.widthInput.render(stack, mouseX, mouseY, partialTicks);
        this.heightInput.render(stack, mouseX, mouseY, partialTicks);
        this.offsetXInput.render(stack, mouseX, mouseY, partialTicks);
        this.offsetYInput.render(stack, mouseX, mouseY, partialTicks);
        this.offsetZInput.render(stack, mouseX, mouseY, partialTicks);
        this.renderTooltip(stack, mouseX, mouseY);
    }

    @Override
    protected void renderTooltip(PoseStack stack, int mouseX, int mouseY) {
        super.renderTooltip(stack, mouseX, mouseY);
        int offsetX = mouseX - this.leftPos, offsetY = mouseY - this.topPos;
        if (offsetX >= 9 && offsetY >= 27 && offsetX < 27 && offsetY < 46) {
            this.renderTooltip(stack, new TranslatableComponent("gui.slide_show.url"), mouseX, mouseY);
        }
        if (offsetX >= 34 && offsetY >= 153 && offsetX < 52 && offsetY < 172) {
            this.renderTooltip(stack, new TranslatableComponent("gui.slide_show.color"), mouseX, mouseY);
        }
        if (offsetX >= 9 && offsetY >= 49 && offsetX < 27 && offsetY < 68) {
            this.renderTooltip(stack, new TranslatableComponent("gui.slide_show.width"), mouseX, mouseY);
        }
        if (offsetX >= 90 && offsetY >= 49 && offsetX < 108 && offsetY < 68) {
            this.renderTooltip(stack, new TranslatableComponent("gui.slide_show.height"), mouseX, mouseY);
        }
        if (offsetX >= 9 && offsetY >= 101 && offsetX < 27 && offsetY < 120) {
            this.renderTooltip(stack, new TranslatableComponent("gui.slide_show.offset_x"), mouseX, mouseY);
        }
        if (offsetX >= 63 && offsetY >= 101 && offsetX < 81 && offsetY < 120) {
            this.renderTooltip(stack, new TranslatableComponent("gui.slide_show.offset_y"), mouseX, mouseY);
        }
        if (offsetX >= 117 && offsetY >= 101 && offsetX < 135 && offsetY < 120) {
            this.renderTooltip(stack, new TranslatableComponent("gui.slide_show.offset_z"), mouseX, mouseY);
        }
        if (offsetX >= 117 && offsetY >= 153 && offsetX < 135 && offsetY < 172) {
            this.renderTooltip(stack, new TranslatableComponent("gui.slide_show.flip"), mouseX, mouseY);
        }
        if (offsetX >= 142 && offsetY >= 153 && offsetX < 160 && offsetY < 172) {
            this.renderTooltip(stack, new TranslatableComponent("gui.slide_show.rotate"), mouseX, mouseY);
        }
        if (offsetX >= 9 && offsetY >= 153 && offsetX < 27 && offsetY < 172) {
            this.renderTooltip(stack, new TranslatableComponent("gui.slide_show.single_double_sided"), mouseX, mouseY);
        }
    }

    @Override
    protected void renderBg(PoseStack stack, float partialTicks, int mouseX, int mouseY) {
        RenderSystem.setShader(GameRenderer::getPositionTexShader);
        RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, 1.0F);
        RenderSystem.setShaderTexture(0, GUI_TEXTURE);
        this.blit(stack, this.leftPos, this.topPos, 0, 0, this.imageWidth, this.imageHeight);
    }

    @Override
    protected void renderLabels(PoseStack stack, int mouseX, int mouseY) {
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.setShaderTexture(0, GUI_TEXTURE);

        int alpha = (this.imgColor >>> 24) & 255;
        if (alpha > 0) {
            int red = (this.imgColor >>> 16) & 255, green = (this.imgColor >>> 8) & 255, blue = this.imgColor & 255;
            RenderSystem.setShaderColor(red / 255.0F, green / 255.0F, blue / 255.0F, alpha / 255.0F);
            this.blit(stack, 38, 157, 180, 194, 10, 10);
            this.blit(stack, 82, 185, 180, 194, 17, 17);
        }

        RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, 1.0F);
        this.blit(stack, 82, 185, 202, 194 - this.rotation.ordinal() * 20, 17, 17);

        drawCenteredStringWithoutShadow(stack, this.font, IMAGE_TEXT, 12);
        drawCenteredStringWithoutShadow(stack, this.font, OFFSET_TEXT, 86);
        drawCenteredStringWithoutShadow(stack, this.font, OTHERS_TEXT, 138);
    }

    private static void drawCenteredStringWithoutShadow(PoseStack stack, Font renderer, Component string, int y) {
        renderer.draw(stack, string, 88 - renderer.width(string) / 2.0F, y, 0x404040);
    }

    private static String toOptionalSignedString(float f) {
        return Float.toString(Math.round(f * 1.0E5F) / 1.0E5F);
    }

    private static String toSignedString(float f) {
        return Float.isNaN(f) ? "" + f : Math.copySign(1.0F, f) <= 0 ? "-" + Math.round(0.0F - f * 1.0E5F) / 1.0E5F :
                "+" + Math.round(f * 1.0E5F) / 1.0E5F;
    }

    private static Vector3f relativeToAbsolute(Vector3f relatedOffset, Vec2 size,
                                               ProjectorBlock.InternalRotation rotation) {
        Vector4f center = new Vector4f(0.5F * size.x, 0.0F, 0.5F * size.y, 1.0F);
        // matrix 6: offset for slide (center[new] = center[old] + offset)
        center.transform(Matrix4f.createTranslateMatrix(relatedOffset.x(), -relatedOffset.z(), relatedOffset.y()));
        // matrix 5: translation for slide
        center.transform(Matrix4f.createTranslateMatrix(-0.5F, 0.0F, 0.5F - size.y));
        // matrix 4: internal rotation
        rotation.transform(center);
        // ok, that's enough
        return new Vector3f(center.x(), center.y(), center.z());
    }

    private static Vector3f absoluteToRelative(Vector3f absoluteOffset, Vec2 size,
                                               ProjectorBlock.InternalRotation rotation) {
        Vector4f center = new Vector4f(absoluteOffset);
        // inverse matrix 4: internal rotation
        rotation.invert().transform(center);
        // inverse matrix 5: translation for slide
        center.transform(Matrix4f.createTranslateMatrix(0.5F, 0.0F, -0.5F + size.y));
        // subtract (offset = center[new] - center[old])
        center.transform(Matrix4f.createTranslateMatrix(-0.5F * size.x, 0.0F, -0.5F * size.y));
        // ok, that's enough (remember it is (a, -c, b) => (a, b, c))
        return new Vector3f(center.x(), center.z(), -center.y());
    }

    private static class Button extends AbstractButton {

        private final Runnable callback;
        private final int u;
        private final int v;

        public Button(int x, int y, int u, int v, int width, int height, Component msg, Runnable callback) {
            super(x, y, width, height, msg);
            this.callback = callback;
            this.u = u;
            this.v = v;
        }

        @Override
        public void onPress() {
            this.callback.run();
        }

        @Override
        public void renderButton(PoseStack stack, int mouseX, int mouseY, float partialTicks) {
            RenderSystem.enableBlend();
            RenderSystem.defaultBlendFunc();
            RenderSystem.setShaderTexture(0, GUI_TEXTURE);
            RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, this.alpha);
            this.blit(stack, this.x, this.y, this.u, this.v, this.width, this.height);
        }

        @Override
        public void updateNarration(NarrationElementOutput p_169152_) {
//            this.defaultButtonNarrationText(p_168838_);
//            this.onTooltip.narrateTooltip((p_168841_) -> {
//                p_168838_.add(NarratedElementType.HINT, p_168841_);
//            });
        }
    }
}