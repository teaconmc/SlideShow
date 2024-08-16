package org.teacon.slides.screen;

import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.ChatFormatting;
import net.minecraft.FieldsAreNonnullByDefault;
import net.minecraft.MethodsReturnNonnullByDefault;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Inventory;
import net.neoforged.neoforge.network.PacketDistributor;
import org.apache.commons.lang3.StringUtils;
import org.lwjgl.glfw.GLFW;
import org.teacon.slides.SlideShow;
import org.teacon.slides.inventory.SlideItemContainerMenu;
import org.teacon.slides.item.SlideItem;
import org.teacon.slides.network.SlideItemUpdatePacket;
import org.teacon.slides.renderer.SlideState;
import org.teacon.slides.url.ProjectorURL;

import javax.annotation.Nullable;
import javax.annotation.ParametersAreNonnullByDefault;
import java.util.*;

@FieldsAreNonnullByDefault
@MethodsReturnNonnullByDefault
@ParametersAreNonnullByDefault
public final class SlideItemScreen extends AbstractContainerScreen<SlideItemContainerMenu> {
    private static final ResourceLocation
            GUI_TEXTURE = SlideShow.id("textures/gui/projector_gui.png");

    private static final int
            GUI_WIDTH = 512,
            GUI_HEIGHT = 384;

    private static final Component
            IMAGE_TEXT = Component.translatable("gui.slide_show.section.image"),
            URL_TEXT = Component.translatable("gui.slide_show.url"),
            SIZE_TEXT = Component.translatable("gui.slide_show.size"),
            SIZE_HINT_1 = Component.translatable("gui.slide_show.size_hint.two",
                            Component.literal("contain").withStyle(ChatFormatting.AQUA),
                            Component.literal("cover").withStyle(ChatFormatting.AQUA))
                    .withStyle(ChatFormatting.GRAY),
            SIZE_HINT_2 = Component.translatable("gui.slide_show.size_hint.contain_or_cover")
                    .withStyle(ChatFormatting.GRAY),
            SIZE_HINT_3 = Component.translatable("gui.slide_show.size_hint.two",
                            Component.literal("<width>% auto").withStyle(ChatFormatting.AQUA),
                            Component.literal("<width>%").withStyle(ChatFormatting.AQUA))
                    .withStyle(ChatFormatting.GRAY),
            SIZE_HINT_4 = Component.translatable("gui.slide_show.size_hint.height_auto")
                    .withStyle(ChatFormatting.GRAY),
            SIZE_HINT_5 = Component.translatable("gui.slide_show.size_hint.one",
                            Component.literal("auto <height>%").withStyle(ChatFormatting.AQUA))
                    .withStyle(ChatFormatting.GRAY),
            SIZE_HINT_6 = Component.translatable("gui.slide_show.size_hint.width_auto")
                    .withStyle(ChatFormatting.GRAY),
            SIZE_HINT_7 = Component.translatable("gui.slide_show.size_hint.one",
                            Component.literal("<width>% <height>%").withStyle(ChatFormatting.AQUA))
                    .withStyle(ChatFormatting.GRAY),
            SIZE_HINT_8 = Component.translatable("gui.slide_show.size_hint.both")
                    .withStyle(ChatFormatting.GRAY);

    private static final int
            URL_MAX_LENGTH = 1 << 9,
            SIZE_MAX_LENGTH = 1 << 9;

    private final LazyWidget<EditBox> mURLInput;
    private final LazyWidget<EditBox> mSizeInput;

    private final SlideItemUpdatePacket mInitPacket;

    private SlideItem.Size mSlideSize;
    private @Nullable ProjectorURL mImgUrl;

    // refreshed after initialization

    private boolean mInvalidSize = true;
    private UrlStatus mUrlStatus = UrlStatus.NO_CONTENT;

    public SlideItemScreen(SlideItemContainerMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title);
        imageWidth = 230;
        imageHeight = 82;
        // initialize variables
        mInitPacket = menu.packet;
        mSlideSize = menu.packet.size();
        mImgUrl = menu.packet.url().orElse(null);
        // url input
        mURLInput = LazyWidget.of(mInitPacket.url().map(u -> u.toUrl().toString()).orElse(""), EditBox::getValue, v -> {
            var input = new EditBox(font, leftPos + 27, topPos + 37, 197, 16, URL_TEXT);
            input.setEditable(mInitPacket.permissions().edit());
            input.setMaxLength(URL_MAX_LENGTH);
            input.setResponder(text -> {
                try {
                    mImgUrl = new ProjectorURL(text);
                    if (mInitPacket.permissions().create()) {
                        var blocked = SlideState.getImgBlocked(mImgUrl);
                        mUrlStatus = blocked ? UrlStatus.BLOCKED : UrlStatus.NORMAL;
                    } else {
                        var allowed = SlideState.getImgAllowed(mImgUrl);
                        mUrlStatus = allowed ? UrlStatus.NORMAL : UrlStatus.INVALID;
                    }
                } catch (IllegalArgumentException e) {
                    mImgUrl = null;
                    mUrlStatus = StringUtils.isNotBlank(text) ? UrlStatus.INVALID : UrlStatus.NO_CONTENT;
                }
                input.setTextColor(switch (mUrlStatus) {
                    case NORMAL, NO_CONTENT -> 0xE0E0E0;
                    case BLOCKED -> 0xE0E04B;
                    case INVALID -> 0xE04B4B;
                });
            });
            input.setValue(v);
            return input;
        });
        // size input
        mSizeInput = LazyWidget.of(mInitPacket.size().toString(), EditBox::getValue, v -> {
            var input = new EditBox(font, leftPos + 27, topPos + 59, 197, 16, SIZE_TEXT);
            input.setEditable(mInitPacket.permissions().edit());
            input.setMaxLength(SIZE_MAX_LENGTH);
            input.setResponder(text -> {
                try {
                    mSlideSize = SlideItem.Size.parse(text);
                    mInvalidSize = false;
                } catch (IllegalArgumentException e) {
                    mInvalidSize = true;
                }
                input.setTextColor(mInvalidSize ? 0xE04B4B : 0xE0E0E0);
            });
            input.setValue(v);
            return input;
        });
    }

    @Override
    protected void init() {
        super.init();

        addRenderableWidget(mURLInput.refresh());
        addRenderableWidget(mSizeInput.refresh());

        setInitialFocus(mURLInput.get());
    }

    @Override
    public void removed() {
        super.removed();
        var imgId = mInitPacket.imgUniqueId();
        var urlFallback = (ProjectorURL) null;
        var urlRemoved = mUrlStatus == UrlStatus.NO_CONTENT && mInitPacket.url().isPresent();
        var urlChanged = mUrlStatus == UrlStatus.NORMAL && !Objects.equals(mImgUrl, mInitPacket.url().orElse(null));
        if (urlRemoved || urlChanged) {
            urlFallback = urlRemoved ? null : mImgUrl;
            // use default uuid to trigger update
            imgId = new UUID(0L, 0L);
        }
        PacketDistributor.sendToServer(new SlideItemUpdatePacket(
                mInitPacket.slotId(), mInitPacket.permissions(), imgId,
                Optional.empty(), Optional.ofNullable(urlFallback), mSlideSize));
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
                || mSizeInput.get().keyPressed(keyCode, scanCode, modifier) || mSizeInput.get().canConsumeInput()
                || super.keyPressed(keyCode, scanCode, modifier);
    }

    @Override
    protected void renderBg(GuiGraphics gui, float partialTicks, int mouseX, int mouseY) {
        RenderSystem.setShaderColor(1F, 1F, 1F, 1F);
        RenderSystem.setShaderTexture(0, GUI_TEXTURE);
        gui.blit(GUI_TEXTURE, leftPos, topPos, 0F, 302F, imageWidth, imageHeight, GUI_WIDTH, GUI_HEIGHT);
    }

    @Override
    protected void renderLabels(GuiGraphics gui, int mouseX, int mouseY) {
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.setShaderTexture(0, GUI_TEXTURE);

        if (mUrlStatus == UrlStatus.INVALID || mUrlStatus == UrlStatus.BLOCKED) {
            gui.blit(GUI_TEXTURE, 7, 35, 7F, 277F, 18, 19, GUI_WIDTH, GUI_HEIGHT);
        }

        gui.drawString(font, IMAGE_TEXT.getVisualOrderText(), 116 - font.width(IMAGE_TEXT) / 2F, 15, 0x404040, false);
    }

    @Override
    protected void renderTooltip(GuiGraphics gui, int mouseX, int mouseY) {
        super.renderTooltip(gui, mouseX, mouseY);
        int offsetX = mouseX - leftPos, offsetY = mouseY - topPos;
        if (offsetX >= 7 && offsetY >= 35 && offsetX < 25 && offsetY < 54) {
            gui.renderComponentTooltip(font, this.getUrlTexts(), mouseX, mouseY);
        } else if (offsetX >= 7 && offsetY >= 57 && offsetX < 25 && offsetY < 76) {
            gui.renderComponentTooltip(font, List.of(SIZE_TEXT,
                    Component.literal(""), SIZE_HINT_1, SIZE_HINT_2,
                    Component.literal(""), SIZE_HINT_3, SIZE_HINT_4,
                    Component.literal(""), SIZE_HINT_5, SIZE_HINT_6,
                    Component.literal(""), SIZE_HINT_7, SIZE_HINT_8), mouseX, mouseY);
        }
    }

    @Override
    public void render(GuiGraphics gui, int mouseX, int mouseY, float partialTick) {
        super.render(gui, mouseX, mouseY, partialTick);
        this.renderTooltip(gui, mouseX, mouseY);
    }

    private List<Component> getUrlTexts() {
        var lastLogOptional = mInitPacket.oldLastLog();
        var components = new ArrayList<Component>();
        components.add(URL_TEXT);
        if (lastLogOptional.isPresent()) {
            var lastLog = lastLogOptional.get();
            var mc = Objects.requireNonNull(this.minecraft);
            lastLog.addToTooltip(mc.level == null ? null : mc.level.dimension(), components);
        }
        return components;
    }

    private enum UrlStatus {
        NORMAL, BLOCKED, INVALID, NO_CONTENT
    }
}
