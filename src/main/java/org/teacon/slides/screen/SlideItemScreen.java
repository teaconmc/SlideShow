package org.teacon.slides.screen;

import com.mojang.logging.annotations.FieldsAreNonnullByDefault;
import com.mojang.logging.annotations.MethodsReturnNonnullByDefault;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.player.Inventory;
import net.neoforged.neoforge.client.network.ClientPacketDistributor;
import org.apache.commons.lang3.StringUtils;
import org.teacon.slides.SlideShow;
import org.teacon.slides.calc.Concrete;
import org.teacon.slides.inventory.SlideItemContainerMenu;
import org.teacon.slides.network.SlideItemUpdatePacket;
import org.teacon.slides.renderer.TextureState;
import org.teacon.slides.url.ProjectorURL;
import org.teacon.slides.url.ProjectorURLSavedData;

import javax.annotation.Nullable;
import javax.annotation.ParametersAreNonnullByDefault;
import java.util.*;

import static net.minecraft.client.renderer.RenderPipelines.GUI_TEXTURED;

@FieldsAreNonnullByDefault
@MethodsReturnNonnullByDefault
@ParametersAreNonnullByDefault
public final class SlideItemScreen extends AbstractContainerScreen<SlideItemContainerMenu> {
    private static final Identifier
            GUI_TEXTURE = SlideShow.id("textures/gui/projector_gui.png");

    private static final int
            GUI_WIDTH = 512,
            GUI_HEIGHT = 384;

    private static final Component
            IMAGE_TEXT = Component.translatable("gui.slide_show.section.image"),
            URL_TEXT = Component.translatable("gui.slide_show.url"),
            SIZE_TEXT = Component.translatable("gui.slide_show.size"),
            POSITION_TEXT = Component.translatable("gui.slide_show.position"),
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
                    .withStyle(ChatFormatting.GRAY),
            POSITION_HINT_1 = Component.translatable("gui.slide_show.position_hint.four",
                            Component.literal("left").withStyle(ChatFormatting.AQUA),
                            Component.literal("center").withStyle(ChatFormatting.AQUA),
                            Component.literal("right").withStyle(ChatFormatting.AQUA),
                            Component.literal("<x>%").withStyle(ChatFormatting.AQUA))
                    .withStyle(ChatFormatting.GRAY),
            POSITION_HINT_2 = Component.translatable("gui.slide_show.position_hint.x")
                    .withStyle(ChatFormatting.GRAY),
            POSITION_HINT_3 = Component.translatable("gui.slide_show.position_hint.four",
                            Component.literal("top").withStyle(ChatFormatting.AQUA),
                            Component.literal("center").withStyle(ChatFormatting.AQUA),
                            Component.literal("bottom").withStyle(ChatFormatting.AQUA),
                            Component.literal("<y>%").withStyle(ChatFormatting.AQUA))
                    .withStyle(ChatFormatting.GRAY),
            POSITION_HINT_4 = Component.translatable("gui.slide_show.position_hint.y")
                    .withStyle(ChatFormatting.GRAY),
            POSITION_HINT_5 = Component.translatable("gui.slide_show.position_hint.two",
                            Component.literal("<x>%").withStyle(ChatFormatting.AQUA),
                            Component.literal("<y>%").withStyle(ChatFormatting.AQUA))
                    .withStyle(ChatFormatting.GRAY),
            POSITION_HINT_6 = Component.translatable("gui.slide_show.position_hint.xy")
                    .withStyle(ChatFormatting.GRAY),
            POSITION_HINT_7 = Component.translatable("gui.slide_show.position_hint.three_one",
                            Component.literal("left").withStyle(ChatFormatting.AQUA),
                            Component.literal("center").withStyle(ChatFormatting.AQUA),
                            Component.literal("right").withStyle(ChatFormatting.AQUA),
                            Component.literal("<y>%").withStyle(ChatFormatting.AQUA),
                            Component.literal("left 50%").withStyle(ChatFormatting.GREEN))
                    .withStyle(ChatFormatting.GRAY),
            POSITION_HINT_8 = Component.translatable("gui.slide_show.position_hint.keyword_y")
                    .withStyle(ChatFormatting.GRAY),
            POSITION_HINT_9 = Component.translatable("gui.slide_show.position_hint.one_three",
                            Component.literal("<x>%").withStyle(ChatFormatting.AQUA),
                            Component.literal("top").withStyle(ChatFormatting.AQUA),
                            Component.literal("center").withStyle(ChatFormatting.AQUA),
                            Component.literal("bottom").withStyle(ChatFormatting.AQUA),
                            Component.literal("50% top").withStyle(ChatFormatting.GREEN))
                    .withStyle(ChatFormatting.GRAY),
            POSITION_HINT_10 = Component.translatable("gui.slide_show.position_hint.x_keyword")
                    .withStyle(ChatFormatting.GRAY),
            POSITION_HINT_11 = Component.translatable("gui.slide_show.position_hint.three_three",
                            Component.literal("left").withStyle(ChatFormatting.AQUA),
                            Component.literal("center").withStyle(ChatFormatting.AQUA),
                            Component.literal("right").withStyle(ChatFormatting.AQUA),
                            Component.literal("top").withStyle(ChatFormatting.AQUA),
                            Component.literal("center").withStyle(ChatFormatting.AQUA),
                            Component.literal("bottom").withStyle(ChatFormatting.AQUA),
                            Component.literal("left top").withStyle(ChatFormatting.GREEN))
                    .withStyle(ChatFormatting.GRAY),
            POSITION_HINT_12 = Component.translatable("gui.slide_show.position_hint.keyword_keyword")
                    .withStyle(ChatFormatting.GRAY),
            POSITION_HINT_13 = Component.translatable("gui.slide_show.position_hint.two_one_two_one",
                            Component.literal("left").withStyle(ChatFormatting.AQUA),
                            Component.literal("right").withStyle(ChatFormatting.AQUA),
                            Component.literal("<x>%").withStyle(ChatFormatting.AQUA),
                            Component.literal("top").withStyle(ChatFormatting.AQUA),
                            Component.literal("bottom").withStyle(ChatFormatting.AQUA),
                            Component.literal("<y>%").withStyle(ChatFormatting.AQUA),
                            Component.literal("right 10% bottom 20%").withStyle(ChatFormatting.GREEN))
                    .withStyle(ChatFormatting.GRAY),
            POSITION_HINT_14 = Component.translatable("gui.slide_show.position_hint.edge_offset")
                    .withStyle(ChatFormatting.GRAY);

    private static final int
            URL_MAX_LENGTH = 1 << 9,
            SIZE_MAX_LENGTH = 1 << 9;

    private final LazyWidget<EditBox> mUrlInput;
    private final LazyWidget<EditBox> mSizeInput;
    private final LazyWidget<EditBox> mPositionInput;

    private final SlideItemUpdatePacket mInitPacket;

    private Concrete.Size mSlideSize;
    private Concrete.Position mSlidePosition;
    private @Nullable ProjectorURL mSlideUrl;

    // refreshed after initialization

    private boolean mInvalidSize = true;
    private boolean mInvalidPosition = true;
    private UrlStatus mUrlStatus = UrlStatus.NO_CONTENT;

    public SlideItemScreen(SlideItemContainerMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title, 230, 82);
        // initialize variables
        mInitPacket = menu.packet;
        mSlideSize = menu.packet.size();
        mSlidePosition = menu.packet.position();
        mSlideUrl = menu.packet.url().orElse(null);
        // url input
        mUrlInput = LazyWidget.of(mInitPacket.url().map(u -> u.toUrl().toString()).orElse(""), EditBox::getValue, v -> {
            var input = new EditBox(font, leftPos + 27, topPos + 37, 197, 16, URL_TEXT);
            input.setEditable(mInitPacket.permissions().edit());
            input.setMaxLength(URL_MAX_LENGTH);
            input.setResponder(text -> {
                try {
                    mSlideUrl = new ProjectorURL(text);
                    if (mInitPacket.permissions().create()) {
                        var blocked = TextureState.getImgBlocked(mSlideUrl);
                        mUrlStatus = blocked ? UrlStatus.BLOCKED : UrlStatus.NORMAL;
                    } else {
                        var allowed = TextureState.getImgAllowed(mSlideUrl);
                        mUrlStatus = allowed ? UrlStatus.NORMAL : UrlStatus.INVALID;
                    }
                } catch (IllegalArgumentException e) {
                    mSlideUrl = null;
                    mUrlStatus = StringUtils.isNotBlank(text) ? UrlStatus.INVALID : UrlStatus.NO_CONTENT;
                }
                input.setTextColor(switch (mUrlStatus) {
                    case NORMAL, NO_CONTENT -> 0xFFE0E0E0;
                    case BLOCKED -> 0xFFE0E04B;
                    case INVALID -> 0xFFE04B4B;
                });
            });
            input.setValue(v);
            return input;
        });
        // size input
        mSizeInput = LazyWidget.of(mInitPacket.size().toString(), EditBox::getValue, v -> {
            var input = new EditBox(font, leftPos + 27, topPos + 59, 87, 16, SIZE_TEXT);
            input.setEditable(mInitPacket.permissions().edit());
            input.setMaxLength(SIZE_MAX_LENGTH);
            input.setResponder(text -> {
                try {
                    mSlideSize = Concrete.Size.parse(text);
                    mInvalidSize = false;
                } catch (IllegalArgumentException e) {
                    mInvalidSize = true;
                }
                input.setTextColor(mInvalidSize ? 0xFFE04B4B : 0xFFE0E0E0);
            });
            input.setValue(v);
            return input;
        });
        // position input
        mPositionInput = LazyWidget.of(mInitPacket.position().toString(), EditBox::getValue, v -> {
            var input = new EditBox(font, leftPos + 137, topPos + 59, 87, 16, POSITION_TEXT);
            input.setEditable(mInitPacket.permissions().edit());
            input.setMaxLength(SIZE_MAX_LENGTH);
            input.setResponder(text -> {
                try {
                    mSlidePosition = Concrete.Position.parse(text);
                    mInvalidPosition = false;
                } catch (IllegalArgumentException e) {
                    mInvalidPosition = true;
                }
                input.setTextColor(mInvalidPosition ? 0xFFE04B4B : 0xFFE0E0E0);
            });
            input.setValue(v);
            return input;
        });
    }

    @Override
    protected void init() {
        super.init();

        addRenderableWidget(mUrlInput.refresh());
        addRenderableWidget(mSizeInput.refresh());
        addRenderableWidget(mPositionInput.refresh());

        setInitialFocus(mUrlInput.get());
    }

    @Override
    public void removed() {
        super.removed();
        var slideImgId = mInitPacket.imgUniqueId();
        var urlFallback = (ProjectorURL) null;
        var urlRemoved = mUrlStatus == UrlStatus.NO_CONTENT && mInitPacket.url().isPresent();
        var urlChanged = mUrlStatus == UrlStatus.NORMAL && !Objects.equals(mSlideUrl, mInitPacket.url().orElse(null));
        if (urlRemoved || urlChanged) {
            urlFallback = urlRemoved ? null : mSlideUrl;
            // use default uuid to trigger update
            slideImgId = new UUID(0L, 0L);
        }
        var dummyLog = Optional.<ProjectorURLSavedData.Log>empty();
        ClientPacketDistributor.sendToServer(new SlideItemUpdatePacket(
                mInitPacket.slotId(), mInitPacket.permissions(), slideImgId,
                dummyLog, Optional.ofNullable(urlFallback), mSlideSize, mSlidePosition));
    }

    @Override
    public boolean keyPressed(KeyEvent event) {
        if (event.isEscape()) {
            Objects.requireNonNull(Objects.requireNonNull(minecraft).player).closeContainer();
            return true;
        }
        for (var input : List.of(mUrlInput, mSizeInput, mPositionInput)) {
            if (input.get().keyPressed(event) || input.get().canConsumeInput()) {
                return true;
            }
        }
        return super.keyPressed(event);
    }

    @Override
    public void extractBackground(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float a) {
        super.extractBackground(graphics, mouseX, mouseY, a);
        graphics.blit(GUI_TEXTURED, GUI_TEXTURE, leftPos, topPos, 0F, 302F, imageWidth, imageHeight, GUI_WIDTH, GUI_HEIGHT);
    }

    @Override
    protected void extractLabels(GuiGraphicsExtractor graphics, int xm, int ym) {
        if (mUrlStatus == UrlStatus.INVALID || mUrlStatus == UrlStatus.BLOCKED) {
            graphics.blit(GUI_TEXTURED, GUI_TEXTURE, 7, 35, 7F, 277F, 18, 19, GUI_WIDTH, GUI_HEIGHT);
        }
        graphics.text(font, IMAGE_TEXT, 116 - font.width(IMAGE_TEXT) / 2, 15, 0xFF404040, false);
    }

    @Override
    protected void extractTooltip(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
        super.extractTooltip(graphics, mouseX, mouseY);
        int offsetX = mouseX - leftPos, offsetY = mouseY - topPos;
        if (offsetX >= 7 && offsetY >= 35 && offsetX < 25 && offsetY < 54) {
            graphics.setComponentTooltipForNextFrame(font, this.getUrlTexts(), mouseX, mouseY);
        } else if (offsetX >= 7 && offsetY >= 57 && offsetX < 25 && offsetY < 76) {
            graphics.setComponentTooltipForNextFrame(font, List.of(SIZE_TEXT,
                    Component.literal(""), SIZE_HINT_1, SIZE_HINT_2,
                    Component.literal(""), SIZE_HINT_3, SIZE_HINT_4,
                    Component.literal(""), SIZE_HINT_5, SIZE_HINT_6,
                    Component.literal(""), SIZE_HINT_7, SIZE_HINT_8), mouseX, mouseY);
        } else if (offsetX >= 117 && offsetY >= 57 && offsetX < 135 && offsetY < 76) {
            graphics.setComponentTooltipForNextFrame(font, List.of(POSITION_TEXT,
                    Component.literal(""), POSITION_HINT_1, POSITION_HINT_2,
                    Component.literal(""), POSITION_HINT_3, POSITION_HINT_4,
                    Component.literal(""), POSITION_HINT_5, POSITION_HINT_6,
                    Component.literal(""), POSITION_HINT_7, POSITION_HINT_8,
                    Component.literal(""), POSITION_HINT_9, POSITION_HINT_10,
                    Component.literal(""), POSITION_HINT_11, POSITION_HINT_12,
                    Component.literal(""), POSITION_HINT_13, POSITION_HINT_14), mouseX, mouseY);
        }
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
