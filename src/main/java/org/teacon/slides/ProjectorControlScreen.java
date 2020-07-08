package org.teacon.slides;

import com.mojang.blaze3d.systems.RenderSystem;

import org.lwjgl.glfw.GLFW;

import it.unimi.dsi.fastutil.objects.ObjectArrays;
import net.minecraft.client.gui.screen.inventory.ContainerScreen;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.client.resources.I18n;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.text.ITextComponent;

public final class ProjectorControlScreen extends ContainerScreen<ProjectorControlContainer> {

    private static final ResourceLocation GUI_BG = new ResourceLocation("slide_show", "textures/gui/projector.png");

    private TextFieldWidget urlInput;
    private TextFieldWidget colorInput;
    private TextFieldWidget widthInput;
    private TextFieldWidget heightInput;
    private TextFieldWidget offsetXInput;
    private TextFieldWidget offsetYInput;
    private TextFieldWidget offsetZInput;

    private String url = "";
    private int color = 0;
    private float width = 0F, height = 0F, offsetX = 0F, offsetY = 0F, offsetZ = 0F;
    private boolean invalidURL = false, invalidColor = false, invalidWidth = false, invalidHeight = false,
        invalidOffsetX = false, invalidOffsetY = false, invalidOffsetZ = false;

    protected ProjectorControlScreen(ProjectorControlContainer container, PlayerInventory inv, ITextComponent title) {
        super(container, inv, title);
    }

    @Override
    protected void init() {
        super.init();
        this.minecraft.keyboardListener.enableRepeatEvents(true);
        this.urlInput = new TextFieldWidget(this.font, this.guiLeft + 10, this.guiTop + 20, 120, 16, "Image URL");
        this.urlInput.setMaxStringLength(32767);
        this.urlInput.setResponder(input -> {
            try {
                new java.net.URL(this.url = input);
                this.invalidURL = false;
            } catch(Exception e) {
                this.invalidURL = true;
            }
        });
        this.urlInput.setText(this.container.url);
        this.urlInput.setVisible(true);
        this.children.add(this.urlInput);
        this.colorInput = new TextFieldWidget(this.font, this.guiLeft + 10, this.guiTop + 55, 60, 16, "Color");
        this.colorInput.setMaxStringLength(8);
        this.colorInput.setResponder(input -> {
            try {
                this.color = Integer.parseUnsignedInt(input, 16);
                this.invalidColor = false;
            } catch(Exception e) {
                this.invalidColor = true;
            }
        });
        this.colorInput.setText(Integer.toUnsignedString(this.container.color, 16));
        this.colorInput.setVisible(true);
        this.children.add(this.colorInput);
        
        this.widthInput = new TextFieldWidget(this.font, this.guiLeft + 10, this.guiTop + 90, 40, 16, "Width");
        this.widthInput.setResponder(input -> {
            try {
                this.width = Float.parseFloat(input);
                this.invalidWidth = false;
            } catch (Exception e) {
                this.invalidWidth = true;
            }
        });
        this.widthInput.setText(Float.toString(this.container.width));
        this.widthInput.setVisible(true);
        this.children.add(this.widthInput);
        this.heightInput = new TextFieldWidget(this.font, this.guiLeft + 80, this.guiTop + 90, 40, 16, "Height");
        this.heightInput.setResponder(input -> {
            try {
                this.height = Float.parseFloat(input);
                this.invalidHeight = false;
            } catch (Exception e) {
                this.invalidHeight = true;
            }
        });
        this.heightInput.setText(Float.toString(this.container.height));
        this.heightInput.setVisible(true);
        this.children.add(this.heightInput);

        this.offsetXInput = new TextFieldWidget(this.font, this.guiLeft + 10, this.guiTop + 130, 40, 16, "OffsetX");
        this.offsetXInput.setResponder(input -> {
            try {
                this.offsetX = Float.parseFloat(input);
                this.invalidOffsetX = false;
            } catch (Exception e) {
                this.invalidOffsetX = true;
            }
        });
        this.offsetXInput.setText(Float.toString(this.container.offsetX));
        this.offsetXInput.setVisible(true);
        this.children.add(this.offsetXInput);
        this.offsetYInput = new TextFieldWidget(this.font, this.guiLeft + 60, this.guiTop + 130, 40, 16, "OffsetY");
        this.offsetYInput.setResponder(input -> {
            try {
                this.offsetY = Float.parseFloat(input);
                this.invalidOffsetY = false;
            } catch (Exception e) {
                this.invalidOffsetY = true;
            }
        });
        this.offsetYInput.setText(Float.toString(this.container.offsetY));
        this.offsetYInput.setVisible(true);
        this.children.add(this.offsetYInput);
        this.offsetZInput = new TextFieldWidget(this.font, this.guiLeft + 110, this.guiTop + 130, 40, 16, "OffsetZ");
        this.offsetZInput.setResponder(input -> {
            try {
                this.offsetZ = Float.parseFloat(input);
                this.invalidOffsetZ = false;
            } catch (Exception e) {
                this.invalidOffsetZ = true;
            }
        });
        this.offsetZInput.setText(Float.toString(this.container.offsetZ));
        this.offsetZInput.setVisible(true);
        this.children.add(this.offsetZInput);
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
        packet.url = this.invalidURL ? this.container.url : this.url;
        packet.color = this.invalidColor ? this.container.color : this.color;
        packet.width = this.invalidWidth ? this.container.width : this.width;
        packet.height = this.invalidHeight ? this.container.height : this.height;
        packet.offsetX = this.invalidOffsetX ? this.container.offsetX : this.offsetX;
        packet.offsetY = this.invalidOffsetY ? this.container.offsetY : this.offsetY;
        packet.offsetZ = this.invalidOffsetZ ? this.container.offsetZ : this.offsetZ;
        SlideShow.channel.sendToServer(packet);
        super.removed();
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifier) {
        if (keyCode == GLFW.GLFW_KEY_ESCAPE) {
            this.minecraft.player.closeScreen();
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
        this.font.drawString(I18n.format("gui.slide_show.url", ObjectArrays.EMPTY_ARRAY), this.guiLeft + 12, this.guiTop + 10, this.invalidURL ? 0xFF5555 : 0x404040);
        this.urlInput.render(mouseX, mouseY, partialTicks);
        this.font.drawString(I18n.format("gui.slide_show.color", ObjectArrays.EMPTY_ARRAY), this.guiLeft + 12, this.guiTop + 45, this.invalidColor ? 0xFF5555 : this.color);
        this.colorInput.render(mouseX, mouseY, partialTicks);
        this.font.drawString(I18n.format("gui.slide_show.width", ObjectArrays.EMPTY_ARRAY), this.guiLeft + 12, this.guiTop + 80, this.invalidWidth ? 0xFF5555 : 0x404040);
        this.widthInput.render(mouseX, mouseY, partialTicks);
        this.font.drawString(I18n.format("gui.slide_show.height", ObjectArrays.EMPTY_ARRAY), this.guiLeft + 82, this.guiTop + 80, this.invalidHeight ? 0xFF5555 : 0x404040);
        this.heightInput.render(mouseX, mouseY, partialTicks);
        this.font.drawString(I18n.format("gui.slide_show.offset_x", ObjectArrays.EMPTY_ARRAY), this.guiLeft + 12, this.guiTop + 120, this.invalidOffsetX ? 0xFF5555 : 0x404040);
        this.offsetXInput.render(mouseX, mouseY, partialTicks);
        this.font.drawString(I18n.format("gui.slide_show.offset_y", ObjectArrays.EMPTY_ARRAY), this.guiLeft + 62, this.guiTop + 120, this.invalidOffsetY ? 0xFF5555 : 0x404040);
        this.offsetYInput.render(mouseX, mouseY, partialTicks);
        this.font.drawString(I18n.format("gui.slide_show.offset_z", ObjectArrays.EMPTY_ARRAY), this.guiLeft + 112, this.guiTop + 120, this.invalidOffsetZ ? 0xFF5555 : 0x404040);
        this.offsetZInput.render(mouseX, mouseY, partialTicks);
        this.renderHoveredToolTip(mouseX, mouseY);
    }

    @Override
    protected void drawGuiContainerBackgroundLayer(float partialTicks, int mouseX, int mouseY) {
        RenderSystem.color4f(1.0F, 1.0F, 1.0F, 1.0F);
        this.minecraft.getTextureManager().bindTexture(GUI_BG);
        this.blit(this.guiLeft, this.guiTop, 0, 0, this.xSize, this.ySize);
    }
    
}