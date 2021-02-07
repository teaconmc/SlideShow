package org.teacon.slides.projector;

import com.mojang.blaze3d.matrix.MatrixStack;
import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.systems.RenderSystem;
import mcp.MethodsReturnNonnullByDefault;
import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.gui.screen.inventory.ContainerScreen;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.client.gui.widget.button.AbstractButton;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.Rotation;
import net.minecraft.util.math.vector.Matrix4f;
import net.minecraft.util.math.vector.Vector2f;
import net.minecraft.util.math.vector.Vector3f;
import net.minecraft.util.math.vector.Vector4f;
import net.minecraft.util.text.ITextComponent;
import net.minecraft.util.text.TranslationTextComponent;
import org.apache.commons.lang3.StringUtils;
import org.lwjgl.glfw.GLFW;
import org.teacon.slides.SlideShow;
import org.teacon.slides.network.SlideData;
import org.teacon.slides.network.UpdateImageInfoPacket;

import javax.annotation.ParametersAreNonnullByDefault;
import java.util.Objects;

@ParametersAreNonnullByDefault
@MethodsReturnNonnullByDefault
public final class ProjectorControlScreen extends ContainerScreen<ProjectorControlContainer> {

    private static final ResourceLocation GUI_TEXTURE = new ResourceLocation("slide_show", "textures/gui/projector.png");

    private TextFieldWidget urlInput;
    private TextFieldWidget colorInput;
    private TextFieldWidget widthInput;
    private TextFieldWidget heightInput;
    private TextFieldWidget offsetXInput;
    private TextFieldWidget offsetYInput;
    private TextFieldWidget offsetZInput;

    private Button switchSingleSided;
    private Button switchDoubleSided;

    private String url = "";
    private boolean isDoubleSided;
    private int imgColor = 0x00000000;
    private Vector2f imgSize = Vector2f.ONE;
    private Vector3f imgOffset = new Vector3f();

    private ProjectorBlock.InternalRotation rotation = ProjectorBlock.InternalRotation.NONE;

    private boolean invalidURL = true;
    private boolean invalidColor = true;
    private boolean invalidWidth = true, invalidHeight = true;
    private boolean invalidOffsetX = true, invalidOffsetY = true, invalidOffsetZ = true;

    public ProjectorControlScreen(ProjectorControlContainer container, PlayerInventory inv, ITextComponent title) {
        super(container, inv, title);
        this.xSize = 176;
        this.ySize = 217;
    }

    @Override
    protected void init() {
        super.init();
        Objects.requireNonNull(this.minecraft).keyboardListener.enableRepeatEvents(true);

        // url input
        this.urlInput = new TextFieldWidget(this.font, this.guiLeft + 30, this.guiTop + 29, 137, 16, new TranslationTextComponent("gui.slide_show.url"));
        this.urlInput.setMaxStringLength(32767);
        this.urlInput.setResponder(input -> {
            try {
                new java.net.URL(this.url = input);
                this.invalidURL = false;
            } catch (Exception e) {
                this.invalidURL = StringUtils.isNotBlank(input);
            }
            this.urlInput.setTextColor(this.invalidURL ? 0xE04B4B : 0xE0E0E0);
        });
        this.urlInput.setText(this.container.currentSlide.getImageLocation());
        this.urlInput.setVisible(true);
        this.children.add(this.urlInput);

        // color input
        this.colorInput = new TextFieldWidget(this.font, this.guiLeft + 55, this.guiTop + 155, 56, 16, new TranslationTextComponent("gui.slide_show.color"));
        this.colorInput.setMaxStringLength(8);
        this.colorInput.setResponder(input -> {
            try {
                this.imgColor = Integer.parseUnsignedInt(input, 16);
                this.invalidColor = false;
            } catch (Exception e) {
                this.invalidColor = true;
            }
            this.colorInput.setTextColor(this.invalidColor ? 0xE04B4B : 0xE0E0E0);
        });
        this.colorInput.setText(String.format("%08X", this.container.currentSlide.getColor()));
        this.colorInput.setVisible(true);
        this.children.add(this.colorInput);

        // width input
        this.widthInput = new TextFieldWidget(this.font, this.guiLeft + 30, this.guiTop + 51, 56, 16, new TranslationTextComponent("gui.slide_show.width"));
        this.widthInput.setResponder(input -> {
            try {
                Vector2f newSize = new Vector2f(Float.parseFloat(input), this.imgSize.y);
                if (!this.invalidOffsetX && !this.invalidOffsetY && !this.invalidOffsetZ) {
                    Vector3f absolute = relativeToAbsolute(this.imgOffset, this.imgSize, this.rotation);
                    Vector3f newRelative = absoluteToRelative(absolute, newSize, this.rotation);
                    this.offsetXInput.setText(toSignedString(newRelative.getX()));
                    this.offsetYInput.setText(toSignedString(newRelative.getY()));
                    this.offsetZInput.setText(toSignedString(newRelative.getZ()));
                }
                this.imgSize = newSize;
                this.invalidWidth = false;
            } catch (Exception e) {
                this.invalidWidth = true;
            }
            this.widthInput.setTextColor(this.invalidWidth ? 0xE04B4B : 0xE0E0E0);
        });
        this.widthInput.setText(toOptionalSignedString(this.container.currentSlide.getSize().x));
        this.widthInput.setVisible(true);
        this.children.add(this.widthInput);

        // height input
        this.heightInput = new TextFieldWidget(this.font, this.guiLeft + 111, this.guiTop + 51, 56, 16, new TranslationTextComponent("gui.slide_show.height"));
        this.heightInput.setResponder(input -> {
            try {
                Vector2f newSize = new Vector2f(this.imgSize.x, Float.parseFloat(input));
                if (!this.invalidOffsetX && !this.invalidOffsetY && !this.invalidOffsetZ) {
                    Vector3f absolute = relativeToAbsolute(this.imgOffset, this.imgSize, this.rotation);
                    Vector3f newRelative = absoluteToRelative(absolute, newSize, this.rotation);
                    this.offsetXInput.setText(toSignedString(newRelative.getX()));
                    this.offsetYInput.setText(toSignedString(newRelative.getY()));
                    this.offsetZInput.setText(toSignedString(newRelative.getZ()));
                }
                this.imgSize = newSize;
                this.invalidHeight = false;
            } catch (Exception e) {
                this.invalidHeight = true;
            }
            this.heightInput.setTextColor(this.invalidHeight ? 0xE04B4B : 0xE0E0E0);
        });
        this.heightInput.setText(toOptionalSignedString(this.container.currentSlide.getSize().y));
        this.heightInput.setVisible(true);
        this.children.add(this.heightInput);

        // offset x input
        this.offsetXInput = new TextFieldWidget(this.font, this.guiLeft + 30, this.guiTop + 103, 29, 16, new TranslationTextComponent("gui.slide_show.offset_x"));
        this.offsetXInput.setResponder(input -> {
            try {
                this.imgOffset = new Vector3f(Float.parseFloat(input), this.imgOffset.getY(), this.imgOffset.getZ());
                this.invalidOffsetX = false;
            } catch (Exception e) {
                this.invalidOffsetX = true;
            }
            this.offsetXInput.setTextColor(this.invalidOffsetX ? 0xE04B4B : 0xE0E0E0);
        });
        this.offsetXInput.setText(toSignedString(this.container.currentSlide.getOffset().getX()));
        this.offsetXInput.setVisible(true);
        this.children.add(this.offsetXInput);

        // offset y input
        this.offsetYInput = new TextFieldWidget(this.font, this.guiLeft + 84, this.guiTop + 103, 29, 16, new TranslationTextComponent("gui.slide_show.offset_y"));
        this.offsetYInput.setResponder(input -> {
            try {
                this.imgOffset = new Vector3f(this.imgOffset.getX(), Float.parseFloat(input), this.imgOffset.getZ());
                this.invalidOffsetY = false;
            } catch (Exception e) {
                this.invalidOffsetY = true;
            }
            this.offsetYInput.setTextColor(this.invalidOffsetY ? 0xE04B4B : 0xE0E0E0);
        });
        this.offsetYInput.setText(toSignedString(this.container.currentSlide.getOffset().getY()));
        this.offsetYInput.setVisible(true);
        this.children.add(this.offsetYInput);

        // offset z input
        this.offsetZInput = new TextFieldWidget(this.font, this.guiLeft + 138, this.guiTop + 103, 29, 16, new TranslationTextComponent("gui.slide_show.offset_z"));
        this.offsetZInput.setResponder(input -> {
            try {
                this.imgOffset = new Vector3f(this.imgOffset.getX(), this.imgOffset.getY(), Float.parseFloat(input));
                this.invalidOffsetZ = false;
            } catch (Exception e) {
                this.invalidOffsetZ = true;
            }
            this.offsetZInput.setTextColor(this.invalidOffsetZ ? 0xE04B4B : 0xE0E0E0);
        });
        this.offsetZInput.setText(toSignedString(this.container.currentSlide.getOffset().getZ()));
        this.offsetZInput.setVisible(true);
        this.children.add(this.offsetZInput);

        // internal rotation buttons
        this.addButton(new Button(this.guiLeft + 117, this.guiTop + 153, 179, 153, 18, 19, new TranslationTextComponent("gui.slide_show.flip"), () -> {
            ProjectorBlock.InternalRotation newRotation = this.rotation.flip();
            if (!this.invalidOffsetX && !this.invalidOffsetY && !this.invalidOffsetZ) {
                Vector3f absolute = relativeToAbsolute(this.imgOffset, this.imgSize, this.rotation);
                Vector3f newRelative = absoluteToRelative(absolute, this.imgSize, newRotation);
                this.offsetXInput.setText(toSignedString(newRelative.getX()));
                this.offsetYInput.setText(toSignedString(newRelative.getY()));
                this.offsetZInput.setText(toSignedString(newRelative.getZ()));
            }
            this.rotation = newRotation;
        }));
        this.addButton(new Button(this.guiLeft + 142, this.guiTop + 153, 179, 173, 18, 19, new TranslationTextComponent("gui.slide_show.rotate"), () -> {
            ProjectorBlock.InternalRotation newRotation = this.rotation.compose(Rotation.CLOCKWISE_90);
            if (!this.invalidOffsetX && !this.invalidOffsetY && !this.invalidOffsetZ) {
                Vector3f absolute = relativeToAbsolute(this.imgOffset, this.imgSize, this.rotation);
                Vector3f newRelative = absoluteToRelative(absolute, this.imgSize, newRotation);
                this.offsetXInput.setText(toSignedString(newRelative.getX()));
                this.offsetYInput.setText(toSignedString(newRelative.getY()));
                this.offsetZInput.setText(toSignedString(newRelative.getZ()));
            }
            this.rotation = newRotation;
        }));
        this.rotation = this.container.rotation;

        // single sided / double sided
        this.switchSingleSided = this.addButton(new Button(this.guiLeft + 9, this.guiTop + 153, 179, 113, 18, 19, new TranslationTextComponent("gui.slide_show.single_double_sided"), () -> {
            this.isDoubleSided = false;
            this.switchDoubleSided.visible = true;
            this.switchSingleSided.visible = false;
        }));
        this.switchDoubleSided = this.addButton(new Button(this.guiLeft + 9, this.guiTop + 153, 179, 133, 18, 19, new TranslationTextComponent("gui.slide_show.single_double_sided"), () -> {
            this.isDoubleSided = true;
            this.switchSingleSided.visible = true;
            this.switchDoubleSided.visible = false;
        }));
        this.isDoubleSided = this.rotation.isFlipped() ? this.container.currentSlide.isBackVisible() : this.container.currentSlide.isFrontVisible();
        this.switchDoubleSided.visible = !this.isDoubleSided;
        this.switchSingleSided.visible = this.isDoubleSided;
    }

    @Override
    public void tick() {
        super.tick();
        this.urlInput.tick();
        this.colorInput.tick();
        this.widthInput.tick();
        this.heightInput.tick();
        this.offsetXInput.tick();
        this.offsetYInput.tick();
        this.offsetZInput.tick();
    }

    @Override
    public void onClose() {
        final SlideData oldData = this.container.currentSlide;
        final UpdateImageInfoPacket packet = new UpdateImageInfoPacket();
        final boolean invalidSize = this.invalidWidth || this.invalidHeight;
        final boolean invalidOffset = this.invalidOffsetX || this.invalidOffsetY || this.invalidOffsetZ;
        packet.pos = this.container.pos;
        packet.data
                .setImageLocation(this.invalidURL ? oldData.getImageLocation() : this.url)
                .setColor(this.invalidColor ? oldData.getColor() : this.imgColor)
                .setSize(invalidSize ? oldData.getSize() : this.imgSize)
                .setOffset(invalidOffset ? oldData.getOffset() : this.imgOffset)
                .setFrontVisible(this.isDoubleSided || this.rotation.isFlipped())
                .setBackVisible(this.isDoubleSided || !this.rotation.isFlipped());
        packet.rotation = this.rotation;
        SlideShow.channel.sendToServer(packet);
        super.onClose();
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifier) {
        if (keyCode == GLFW.GLFW_KEY_ESCAPE) {
            Objects.requireNonNull(Objects.requireNonNull(this.minecraft).player).closeScreen();
            return true;
        }

        return this.urlInput.keyPressed(keyCode, scanCode, modifier) || this.urlInput.canWrite()
                || this.colorInput.keyPressed(keyCode, scanCode, modifier) || this.colorInput.canWrite()
                || this.widthInput.keyPressed(keyCode, scanCode, modifier) || this.widthInput.canWrite()
                || this.heightInput.keyPressed(keyCode, scanCode, modifier) || this.heightInput.canWrite()
                || this.offsetXInput.keyPressed(keyCode, scanCode, modifier) || this.offsetXInput.canWrite()
                || this.offsetYInput.keyPressed(keyCode, scanCode, modifier) || this.offsetYInput.canWrite()
                || this.offsetZInput.keyPressed(keyCode, scanCode, modifier) || this.offsetZInput.canWrite()
                || super.keyPressed(keyCode, scanCode, modifier);
    }

    @Override
    public void render(MatrixStack stack, int mouseX, int mouseY, float partialTicks) {
        this.renderBackground(stack);
        super.render(stack, mouseX, mouseY, partialTicks);
        this.urlInput.render(stack, mouseX, mouseY, partialTicks);
        this.colorInput.render(stack, mouseX, mouseY, partialTicks);
        this.widthInput.render(stack, mouseX, mouseY, partialTicks);
        this.heightInput.render(stack, mouseX, mouseY, partialTicks);
        this.offsetXInput.render(stack, mouseX, mouseY, partialTicks);
        this.offsetYInput.render(stack, mouseX, mouseY, partialTicks);
        this.offsetZInput.render(stack, mouseX, mouseY, partialTicks);
        this.renderHoveredTooltip(stack, mouseX, mouseY);
    }

    @Override
    protected void renderHoveredTooltip(MatrixStack stack, int mouseX, int mouseY) {
        super.renderHoveredTooltip(stack, mouseX, mouseY);
        int offsetX = mouseX - this.guiLeft, offsetY = mouseY - this.guiTop;
        if (offsetX >= 9 && offsetY >= 27 && offsetX < 27 && offsetY < 46) {
            this.renderTooltip(stack, new TranslationTextComponent("gui.slide_show.url"), mouseX, mouseY);
        }
        if (offsetX >= 34 && offsetY >= 153 && offsetX < 52 && offsetY < 172) {
            this.renderTooltip(stack, new TranslationTextComponent("gui.slide_show.color"), mouseX, mouseY);
        }
        if (offsetX >= 9 && offsetY >= 49 && offsetX < 27 && offsetY < 68) {
            this.renderTooltip(stack, new TranslationTextComponent("gui.slide_show.width"), mouseX, mouseY);
        }
        if (offsetX >= 90 && offsetY >= 49 && offsetX < 108 && offsetY < 68) {
            this.renderTooltip(stack, new TranslationTextComponent("gui.slide_show.height"), mouseX, mouseY);
        }
        if (offsetX >= 9 && offsetY >= 101 && offsetX < 27 && offsetY < 120) {
            this.renderTooltip(stack, new TranslationTextComponent("gui.slide_show.offset_x"), mouseX, mouseY);
        }
        if (offsetX >= 63 && offsetY >= 101 && offsetX < 81 && offsetY < 120) {
            this.renderTooltip(stack, new TranslationTextComponent("gui.slide_show.offset_y"), mouseX, mouseY);
        }
        if (offsetX >= 117 && offsetY >= 101 && offsetX < 135 && offsetY < 120) {
            this.renderTooltip(stack, new TranslationTextComponent("gui.slide_show.offset_z"), mouseX, mouseY);
        }
        if (offsetX >= 117 && offsetY >= 153 && offsetX < 135 && offsetY < 172) {
            this.renderTooltip(stack, new TranslationTextComponent("gui.slide_show.flip"), mouseX, mouseY);
        }
        if (offsetX >= 142 && offsetY >= 153 && offsetX < 160 && offsetY < 172) {
            this.renderTooltip(stack, new TranslationTextComponent("gui.slide_show.rotate"), mouseX, mouseY);
        }
        if (offsetX >= 9 && offsetY >= 153 && offsetX < 27 && offsetY < 172) {
            this.renderTooltip(stack, new TranslationTextComponent("gui.slide_show.single_double_sided"), mouseX, mouseY);
        }
    }

    @Override
    @SuppressWarnings("deprecation")
    protected void drawGuiContainerBackgroundLayer(MatrixStack stack, float partialTicks, int mouseX, int mouseY) {
        RenderSystem.color4f(1.0F, 1.0F, 1.0F, 1.0F);
        Objects.requireNonNull(this.minecraft).getTextureManager().bindTexture(GUI_TEXTURE);
        this.blit(stack, this.guiLeft, this.guiTop, 0, 0, this.xSize, this.ySize);
    }

    @Override
    @SuppressWarnings("deprecation")
    protected void drawGuiContainerForegroundLayer(MatrixStack stack, int mouseX, int mouseY) {
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.blendFunc(GlStateManager.SourceFactor.SRC_ALPHA, GlStateManager.DestFactor.ONE_MINUS_SRC_ALPHA);

        int alpha = (this.imgColor >>> 24) & 255;
        if (alpha > 0) {
            int red = (this.imgColor >>> 16) & 255, green = (this.imgColor >>> 8) & 255, blue = this.imgColor & 255;
            Objects.requireNonNull(this.minecraft).getTextureManager().bindTexture(GUI_TEXTURE);
            RenderSystem.color4f(red / 255.0F, green / 255.0F, blue / 255.0F, alpha / 255.0F);
            this.blit(stack, 38, 157, 180, 194, 10, 10);
            this.blit(stack, 82, 185, 180, 194, 17, 17);
        }

        RenderSystem.color4f(1.0F, 1.0F, 1.0F, 1.0F);
        this.blit(stack, 82, 185, 202, 194 - this.rotation.ordinal() * 20, 17, 17);

        drawCenteredStringWithoutShadow(stack, this.font, new TranslationTextComponent("gui.slide_show.section.image"), 12);
        drawCenteredStringWithoutShadow(stack, this.font, new TranslationTextComponent("gui.slide_show.section.offset"), 86);
        drawCenteredStringWithoutShadow(stack, this.font, new TranslationTextComponent("gui.slide_show.section.others"), 138);
    }

    private static void drawCenteredStringWithoutShadow(MatrixStack stack, FontRenderer renderer, ITextComponent string, int y) {
        renderer.func_243248_b(stack, string, 88 - renderer.getStringPropertyWidth(string) / 2.0F, y, 0x404040);
    }

    private static String toOptionalSignedString(float f) {
        return Float.toString(Math.round(f * 1.0E5F) / 1.0E5F);
    }

    private static String toSignedString(float f) {
         return Float.isNaN(f) ? "" + f : Math.copySign(1.0F, f) <= 0 ? "-" + Math.round(0.0F - f * 1.0E5F) / 1.0E5F : "+" + Math.round(f * 1.0E5F) / 1.0E5F;
    }

    private static Vector3f relativeToAbsolute(Vector3f relatedOffset, Vector2f size, ProjectorBlock.InternalRotation rotation) {
        Vector4f center = new Vector4f(0.5F * size.x, 0.0F, 0.5F * size.y, 1.0F);
        // matrix 6: offset for slide (center[new] = center[old] + offset)
        center.transform(Matrix4f.makeTranslate(relatedOffset.getX(), -relatedOffset.getZ(), relatedOffset.getY()));
        // matrix 5: translation for slide
        center.transform(Matrix4f.makeTranslate(-0.5F, 0.0F, 0.5F - size.y));
        // matrix 4: internal rotation
        center.transform(rotation.getTransformation());
        // ok, that's enough
        return new Vector3f(center.getX(), center.getY(), center.getZ());
    }

    private static Vector3f absoluteToRelative(Vector3f absoluteOffset, Vector2f size, ProjectorBlock.InternalRotation rotation) {
        Vector4f center = new Vector4f(absoluteOffset);
        // inverse matrix 4: internal rotation
        center.transform(rotation.invert().getTransformation());
        // inverse matrix 5: translation for slide
        center.transform(Matrix4f.makeTranslate(0.5F, 0.0F, -0.5F + size.y));
        // subtract (offset = center[new] - center[old])
        center.transform(Matrix4f.makeTranslate(-0.5F * size.x, 0.0F, -0.5F * size.y));
        // ok, that's enough (remember it is (a, -c, b) => (a, b, c))
        return new Vector3f(center.getX(), center.getZ(), -center.getY());
    }

    private final class Button extends AbstractButton {
        private final Runnable callback;
        private final int u;
        private final int v;

        public Button(int x, int y, int u, int v, int width, int height, ITextComponent msg, Runnable callback) {
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
        @SuppressWarnings("deprecation")
        public void renderButton(MatrixStack stack, int mouseX, int mouseY, float partialTicks) {
            RenderSystem.enableBlend();
            RenderSystem.defaultBlendFunc();
            RenderSystem.blendFunc(GlStateManager.SourceFactor.SRC_ALPHA, GlStateManager.DestFactor.ONE_MINUS_SRC_ALPHA);

            Objects.requireNonNull(ProjectorControlScreen.this.minecraft).getTextureManager().bindTexture(GUI_TEXTURE);
            RenderSystem.color4f(1.0F, 1.0F, 1.0F, this.alpha);
            this.blit(stack, this.x, this.y, this.u, this.v, this.width, this.height);
        }
    }
}