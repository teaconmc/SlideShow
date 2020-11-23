package org.teacon.slides;

import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.systems.RenderSystem;
import mcp.MethodsReturnNonnullByDefault;
import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.gui.screen.inventory.ContainerScreen;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.client.gui.widget.button.AbstractButton;
import net.minecraft.client.renderer.Matrix4f;
import net.minecraft.client.renderer.Vector3f;
import net.minecraft.client.renderer.Vector4f;
import net.minecraft.client.resources.I18n;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.Rotation;
import net.minecraft.util.text.ITextComponent;
import org.apache.commons.lang3.StringUtils;
import org.lwjgl.glfw.GLFW;

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

    private String url = "";
    private int color = 0x00000000;
    private float width = 0F, height = 0F;
    private float offsetX = 0F, offsetY = 0F, offsetZ = 0F;
    private ProjectorBlock.InternalRotation rotation = ProjectorBlock.InternalRotation.NONE;

    private boolean invalidURL = true;
    private boolean invalidColor = true;
    private boolean invalidWidth = true, invalidHeight = true;
    private boolean invalidOffsetX = true, invalidOffsetY = true, invalidOffsetZ = true;

    protected ProjectorControlScreen(ProjectorControlContainer container, PlayerInventory inv, ITextComponent title) {
        super(container, inv, title);
        this.xSize = 176;
        this.ySize = 217;
    }

    @Override
    protected void init() {
        super.init();
        Objects.requireNonNull(this.minecraft).keyboardListener.enableRepeatEvents(true);

        // url input
        this.urlInput = new TextFieldWidget(this.font, this.guiLeft + 30, this.guiTop + 29, 137, 16, I18n.format("gui.slide_show.url"));
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
        this.urlInput.setText(this.container.currentSlide.imageLocation);
        this.urlInput.setVisible(true);
        this.children.add(this.urlInput);

        // color input
        this.colorInput = new TextFieldWidget(this.font, this.guiLeft + 38, this.guiTop + 155, 56, 16, I18n.format("gui.slide_show.color"));
        this.colorInput.setMaxStringLength(8);
        this.colorInput.setResponder(input -> {
            try {
                this.color = Integer.parseUnsignedInt(input, 16);
                this.invalidColor = false;
            } catch (Exception e) {
                this.invalidColor = true;
            }
            this.colorInput.setTextColor(this.invalidColor ? 0xE04B4B : 0xE0E0E0);
        });
        this.colorInput.setText(String.format("%08X", this.container.currentSlide.color));
        this.colorInput.setVisible(true);
        this.children.add(this.colorInput);

        // width input
        this.widthInput = new TextFieldWidget(this.font, this.guiLeft + 30, this.guiTop + 51, 56, 16, I18n.format("gui.slide_show.width"));
        this.widthInput.setResponder(input -> {
            try {
                float newWidth = Float.parseFloat(input);
                if (!this.invalidOffsetX && !this.invalidOffsetY && !this.invalidOffsetZ) {
                    Vector3f relative = new Vector3f(this.offsetX, this.offsetY, this.offsetZ);
                    Vector3f absolute = relativeToAbsolute(relative, this.width, this.height, this.rotation);
                    Vector3f newRelative = absoluteToRelative(absolute, newWidth, this.height, this.rotation);
                    this.offsetXInput.setText(toSignedString(newRelative.getX()));
                    this.offsetYInput.setText(toSignedString(newRelative.getY()));
                    this.offsetZInput.setText(toSignedString(newRelative.getZ()));
                }
                this.width = newWidth;
                this.invalidWidth = false;
            } catch (Exception e) {
                this.invalidWidth = true;
            }
            this.widthInput.setTextColor(this.invalidWidth ? 0xE04B4B : 0xE0E0E0);
        });
        this.widthInput.setText(Float.toString(this.container.currentSlide.width));
        this.widthInput.setVisible(true);
        this.children.add(this.widthInput);

        // height input
        this.heightInput = new TextFieldWidget(this.font, this.guiLeft + 111, this.guiTop + 51, 56, 16, I18n.format("gui.slide_show.height"));
        this.heightInput.setResponder(input -> {
            try {
                float newHeight = Float.parseFloat(input);
                if (!this.invalidOffsetX && !this.invalidOffsetY && !this.invalidOffsetZ) {
                    Vector3f relative = new Vector3f(this.offsetX, this.offsetY, this.offsetZ);
                    Vector3f absolute = relativeToAbsolute(relative, this.width, this.height, this.rotation);
                    Vector3f newRelative = absoluteToRelative(absolute, this.width, newHeight, this.rotation);
                    this.offsetXInput.setText(toSignedString(newRelative.getX()));
                    this.offsetYInput.setText(toSignedString(newRelative.getY()));
                    this.offsetZInput.setText(toSignedString(newRelative.getZ()));
                }
                this.height = newHeight;
                this.invalidHeight = false;
            } catch (Exception e) {
                this.invalidHeight = true;
            }
            this.heightInput.setTextColor(this.invalidHeight ? 0xE04B4B : 0xE0E0E0);
        });
        this.heightInput.setText(Float.toString(this.container.currentSlide.height));
        this.heightInput.setVisible(true);
        this.children.add(this.heightInput);

        // offset x input
        this.offsetXInput = new TextFieldWidget(this.font, this.guiLeft + 30, this.guiTop + 103, 29, 16, I18n.format("gui.slide_show.offset_x"));
        this.offsetXInput.setResponder(input -> {
            try {
                this.offsetX = Float.parseFloat(input);
                this.invalidOffsetX = false;
            } catch (Exception e) {
                this.invalidOffsetX = true;
            }
            this.offsetXInput.setTextColor(this.invalidOffsetX ? 0xE04B4B : 0xE0E0E0);
        });
        this.offsetXInput.setText(toSignedString(this.container.currentSlide.offsetX));
        this.offsetXInput.setVisible(true);
        this.children.add(this.offsetXInput);

        // offset y input
        this.offsetYInput = new TextFieldWidget(this.font, this.guiLeft + 84, this.guiTop + 103, 29, 16, I18n.format("gui.slide_show.offset_y"));
        this.offsetYInput.setResponder(input -> {
            try {
                this.offsetY = Float.parseFloat(input);
                this.invalidOffsetY = false;
            } catch (Exception e) {
                this.invalidOffsetY = true;
            }
            this.offsetYInput.setTextColor(this.invalidOffsetY ? 0xE04B4B : 0xE0E0E0);
        });
        this.offsetYInput.setText(toSignedString(this.container.currentSlide.offsetY));
        this.offsetYInput.setVisible(true);
        this.children.add(this.offsetYInput);

        // offset z input
        this.offsetZInput = new TextFieldWidget(this.font, this.guiLeft + 138, this.guiTop + 103, 29, 16, I18n.format("gui.slide_show.offset_z"));
        this.offsetZInput.setResponder(input -> {
            try {
                this.offsetZ = Float.parseFloat(input);
                this.invalidOffsetZ = false;
            } catch (Exception e) {
                this.invalidOffsetZ = true;
            }
            this.offsetZInput.setTextColor(this.invalidOffsetZ ? 0xE04B4B : 0xE0E0E0);
        });
        this.offsetZInput.setText(toSignedString(this.container.currentSlide.offsetZ));
        this.offsetZInput.setVisible(true);
        this.children.add(this.offsetZInput);

        // internal rotation buttons
        this.addButton(new Button(this.guiLeft + 117, this.guiTop + 153, 179, 153, 18, 19, I18n.format("gui.slide_show.flip"), () -> {
            ProjectorBlock.InternalRotation newRotation = this.rotation.flip();
            if (!this.invalidOffsetX && !this.invalidOffsetY && !this.invalidOffsetZ) {
                Vector3f relative = new Vector3f(this.offsetX, this.offsetY, this.offsetZ);
                Vector3f absolute = relativeToAbsolute(relative, this.width, this.height, this.rotation);
                Vector3f newRelative = absoluteToRelative(absolute, this.width, this.height, newRotation);
                this.offsetXInput.setText(toSignedString(newRelative.getX()));
                this.offsetYInput.setText(toSignedString(newRelative.getY()));
                this.offsetZInput.setText(toSignedString(newRelative.getZ()));
            }
            this.rotation = newRotation;
        }));
        this.addButton(new Button(this.guiLeft + 142, this.guiTop + 153, 179, 173, 18, 19, I18n.format("gui.slide_show.rotate"), () -> {
            ProjectorBlock.InternalRotation newRotation = this.rotation.compose(Rotation.CLOCKWISE_90);
            if (!this.invalidOffsetX && !this.invalidOffsetY && !this.invalidOffsetZ) {
                Vector3f relative = new Vector3f(this.offsetX, this.offsetY, this.offsetZ);
                Vector3f absolute = relativeToAbsolute(relative, this.width, this.height, this.rotation);
                Vector3f newRelative = absoluteToRelative(absolute, this.width, this.height, newRotation);
                this.offsetXInput.setText(toSignedString(newRelative.getX()));
                this.offsetYInput.setText(toSignedString(newRelative.getY()));
                this.offsetZInput.setText(toSignedString(newRelative.getZ()));
            }
            this.rotation = newRotation;
        }));
        this.rotation = this.container.rotation;
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
    public void removed() {
        final UpdateImageInfoPacket packet = new UpdateImageInfoPacket();
        packet.pos = this.container.pos;
        packet.data.imageLocation = this.invalidURL ? this.container.currentSlide.imageLocation : this.url;
        packet.data.color = this.invalidColor ? this.container.currentSlide.color : this.color;
        packet.data.width = this.invalidWidth ? this.container.currentSlide.width : this.width;
        packet.data.height = this.invalidHeight ? this.container.currentSlide.height : this.height;
        packet.data.offsetX = this.invalidOffsetX ? this.container.currentSlide.offsetX : this.offsetX;
        packet.data.offsetY = this.invalidOffsetY ? this.container.currentSlide.offsetY : this.offsetY;
        packet.data.offsetZ = this.invalidOffsetZ ? this.container.currentSlide.offsetZ : this.offsetZ;
        packet.rotation = this.rotation;
        SlideShow.channel.sendToServer(packet);
        super.removed();
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
    public void render(int mouseX, int mouseY, float partialTicks) {
        this.renderBackground();
        super.render(mouseX, mouseY, partialTicks);
        this.urlInput.render(mouseX, mouseY, partialTicks);
        this.colorInput.render(mouseX, mouseY, partialTicks);
        this.widthInput.render(mouseX, mouseY, partialTicks);
        this.heightInput.render(mouseX, mouseY, partialTicks);
        this.offsetXInput.render(mouseX, mouseY, partialTicks);
        this.offsetYInput.render(mouseX, mouseY, partialTicks);
        this.offsetZInput.render(mouseX, mouseY, partialTicks);
        this.renderHoveredToolTip(mouseX, mouseY);
    }

    @Override
    protected void renderHoveredToolTip(int mouseX, int mouseY) {
        super.renderHoveredToolTip(mouseX, mouseY);
        int offsetX = mouseX - this.guiLeft, offsetY = mouseY - this.guiTop;
        if (offsetX >= 9 && offsetY >= 27 && offsetX < 27 && offsetY < 46) {
            this.renderTooltip(I18n.format("gui.slide_show.url"), mouseX, mouseY);
        }
        if (offsetX >= 17 && offsetY >= 153 && offsetX < 35 && offsetY < 172) {
            this.renderTooltip(I18n.format("gui.slide_show.color"), mouseX, mouseY);
        }
        if (offsetX >= 9 && offsetY >= 49 && offsetX < 27 && offsetY < 68) {
            this.renderTooltip(I18n.format("gui.slide_show.width"), mouseX, mouseY);
        }
        if (offsetX >= 90 && offsetY >= 49 && offsetX < 108 && offsetY < 68) {
            this.renderTooltip(I18n.format("gui.slide_show.height"), mouseX, mouseY);
        }
        if (offsetX >= 9 && offsetY >= 101 && offsetX < 27 && offsetY < 120) {
            this.renderTooltip(I18n.format("gui.slide_show.offset_x"), mouseX, mouseY);
        }
        if (offsetX >= 63 && offsetY >= 101 && offsetX < 81 && offsetY < 120) {
            this.renderTooltip(I18n.format("gui.slide_show.offset_y"), mouseX, mouseY);
        }
        if (offsetX >= 117 && offsetY >= 101 && offsetX < 135 && offsetY < 120) {
            this.renderTooltip(I18n.format("gui.slide_show.offset_z"), mouseX, mouseY);
        }
        if (offsetX >= 117 && offsetY >= 153 && offsetX < 135 && offsetY < 172) {
            this.renderTooltip(I18n.format("gui.slide_show.flip"), mouseX, mouseY);
        }
        if (offsetX >= 142 && offsetY >= 153 && offsetX < 160 && offsetY < 172) {
            this.renderTooltip(I18n.format("gui.slide_show.rotate"), mouseX, mouseY);
        }
    }

    @Override
    protected void drawGuiContainerBackgroundLayer(float partialTicks, int mouseX, int mouseY) {
        RenderSystem.color4f(1.0F, 1.0F, 1.0F, 1.0F);
        Objects.requireNonNull(this.minecraft).getTextureManager().bindTexture(GUI_TEXTURE);
        this.blit(this.guiLeft, this.guiTop, 0, 0, this.xSize, this.ySize);
    }

    @Override
    protected void drawGuiContainerForegroundLayer(int mouseX, int mouseY) {
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.blendFunc(GlStateManager.SourceFactor.SRC_ALPHA, GlStateManager.DestFactor.ONE_MINUS_SRC_ALPHA);

        int alpha = (this.color >>> 24) & 255;
        if (alpha > 0) {
            int red = (this.color >>> 16) & 255, green = (this.color >>> 8) & 255, blue = this.color & 255;
            Objects.requireNonNull(this.minecraft).getTextureManager().bindTexture(GUI_TEXTURE);
            RenderSystem.color4f(red / 255.0F, green / 255.0F, blue / 255.0F, alpha / 255.0F);
            this.blit(21, 157, 180, 194, 10, 10);
            this.blit(82, 185, 180, 194, 17, 17);
        }

        RenderSystem.color4f(1.0F, 1.0F, 1.0F, 1.0F);
        this.blit(82, 185, 202, 194 - this.rotation.ordinal() * 20, 17, 17);

        drawCenteredStringWithoutShadow(this.font, I18n.format("gui.slide_show.section.image"), 12);
        drawCenteredStringWithoutShadow(this.font, I18n.format("gui.slide_show.section.offset"), 86);
        drawCenteredStringWithoutShadow(this.font, I18n.format("gui.slide_show.section.others"), 138);
    }

    private static void drawCenteredStringWithoutShadow(FontRenderer renderer, String string, int y) {
        renderer.drawString(string, 88 - renderer.getStringWidth(string) / 2.0F, y, 0x404040);
    }

    private static String toSignedString(float f) {
        return Float.isNaN(f) ? "" + f : Math.copySign(1.0F, f) <= 0 ? "-" + (0.0F - f) : "+" + f;
    }

    private static Vector3f relativeToAbsolute(Vector3f relatedOffset, float width, float height, ProjectorBlock.InternalRotation rotation) {
        Vector4f center = new Vector4f(0.5F * width, 0.0F, 0.5F * height, 1.0F);
        // matrix 6: offset for slide (center[new] = center[old] + offset)
        center.transform(Matrix4f.makeTranslate(relatedOffset.getX(), -relatedOffset.getZ(), relatedOffset.getY()));
        // matrix 5: translation for slide
        center.transform(Matrix4f.makeTranslate(-0.5F, 0.0F, 0.5F - height));
        // matrix 4: internal rotation
        center.transform(rotation.getTransformation());
        // ok, that's enough
        return new Vector3f(center.getX(), center.getY(), center.getZ());
    }

    private static Vector3f absoluteToRelative(Vector3f absoluteOffset, float width, float height, ProjectorBlock.InternalRotation rotation) {
        Vector4f center = new Vector4f(absoluteOffset);
        // inverse matrix 4: internal rotation
        center.transform(rotation.invert().getTransformation());
        // inverse matrix 5: translation for slide
        center.transform(Matrix4f.makeTranslate(0.5F, 0.0F, -0.5F + height));
        // subtract (offset = center[new] - center[old])
        center.transform(Matrix4f.makeTranslate(-0.5F * width, 0.0F, -0.5F * height));
        // ok, that's enough (remember it is (a, -c, b) => (a, b, c))
        return new Vector3f(center.getX(), center.getZ(), -center.getY());
    }

    private final class Button extends AbstractButton {
        private final Runnable callback;
        private final int u;
        private final int v;

        public Button(int x, int y, int u, int v, int width, int height, String msg, Runnable callback) {
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
        public void renderButton(int p_renderButton_1_, int p_renderButton_2_, float p_renderButton_3_) {
            RenderSystem.enableBlend();
            RenderSystem.defaultBlendFunc();
            RenderSystem.blendFunc(GlStateManager.SourceFactor.SRC_ALPHA, GlStateManager.DestFactor.ONE_MINUS_SRC_ALPHA);

            Objects.requireNonNull(ProjectorControlScreen.this.minecraft).getTextureManager().bindTexture(GUI_TEXTURE);
            RenderSystem.color4f(1.0F, 1.0F, 1.0F, this.alpha);
            this.blit(this.x, this.y, this.u, this.v, this.width, this.height);
        }
    }
}