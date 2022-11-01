package org.teacon.slides.projector;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Matrix4f;
import com.mojang.math.Vector3f;
import com.mojang.math.Vector4f;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.ImageButton;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.phys.Vec2;
import net.objecthunter.exp4j.ExpressionBuilder;
import org.apache.commons.lang3.StringUtils;
import org.lwjgl.glfw.GLFW;
import org.teacon.slides.Slideshow;
import org.teacon.slides.mappings.ScreenMapper;
import org.teacon.slides.mappings.Text;
import org.teacon.slides.mappings.UtilitiesClient;
import org.teacon.slides.renderer.RenderUtils;
import org.teacon.slides.renderer.SlideState;

import javax.annotation.ParametersAreNonnullByDefault;

@SuppressWarnings("ConstantConditions")
@ParametersAreNonnullByDefault
public final class ProjectorScreen extends ScreenMapper {

	private static final ResourceLocation
			GUI_TEXTURE = new ResourceLocation(Slideshow.ID, "textures/gui/projector.png");

	private static final Component
			IMAGE_TEXT = Text.translatable("gui.slide_show.section.image"),
			OFFSET_TEXT = Text.translatable("gui.slide_show.section.offset"),
			OTHERS_TEXT = Text.translatable("gui.slide_show.section.others"),
			URL_TEXT = Text.translatable("gui.slide_show.url"),
			COLOR_TEXT = Text.translatable("gui.slide_show.color"),
			WIDTH_TEXT = Text.translatable("gui.slide_show.width"),
			HEIGHT_TEXT = Text.translatable("gui.slide_show.height"),
			OFFSET_X_TEXT = Text.translatable("gui.slide_show.offset_x"),
			OFFSET_Y_TEXT = Text.translatable("gui.slide_show.offset_y"),
			OFFSET_Z_TEXT = Text.translatable("gui.slide_show.offset_z"),
			FLIP_TEXT = Text.translatable("gui.slide_show.flip"),
			ROTATE_TEXT = Text.translatable("gui.slide_show.rotate"),
			SINGLE_DOUBLE_SIDED_TEXT = Text.translatable("gui.slide_show.single_double_sided");

	private EditBox mURLInput;
	private EditBox mColorInput;
	private EditBox mWidthInput;
	private EditBox mHeightInput;
	private EditBox mOffsetXInput;
	private EditBox mOffsetYInput;
	private EditBox mOffsetZInput;

	private ImageButton mSwitchSingleSided;
	private ImageButton mSwitchDoubleSided;

	private boolean mDoubleSided;
	private int mImageColor = ~0;
	private Vec2 mImageSize = Vec2.ONE;
	private Vector3f mImageOffset = new Vector3f();

	private ProjectorBlock.InternalRotation mRotation = ProjectorBlock.InternalRotation.NONE;

	private boolean mInvalidURL = true;
	private boolean mInvalidColor = true;
	private boolean mInvalidWidth = true, mInvalidHeight = true;
	private boolean mInvalidOffsetX = true, mInvalidOffsetY = true, mInvalidOffsetZ = true;

	private final ProjectorBlockEntity mEntity;
	private final int imageWidth;
	private final int imageHeight;

	public ProjectorScreen(BlockPos pos) {
		super(Text.literal(""));
		BlockEntity blockEntity = Minecraft.getInstance().level.getBlockEntity(pos);
		mEntity = blockEntity instanceof ProjectorBlockEntity ? (ProjectorBlockEntity) blockEntity : null;
		imageWidth = 176;
		imageHeight = 217;
	}

	public static void openScreen(Minecraft minecraftClient, FriendlyByteBuf packet) {
		BlockPos pos = packet.readBlockPos();
		minecraftClient.execute(() -> {
			if (!(minecraftClient.screen instanceof ProjectorScreen)) {
				UtilitiesClient.setScreen(minecraftClient, new ProjectorScreen(pos));
			}
		});
	}

	@Override
	protected void init() {
		super.init();
		if (mEntity == null) {
			return;
		}
		minecraft.keyboardHandler.setSendRepeatsToGui(true);

		final int leftPos = (width - imageWidth) / 2;
		final int topPos = (height - imageHeight) / 2;

		// url input
		mURLInput = new EditBox(font, leftPos + 30, topPos + 29, 137, 16,
				Text.translatable("gui.slide_show.url"));
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
		addDrawableChild(mURLInput);
		setInitialFocus(mURLInput);

		// color input
		mColorInput = new EditBox(font, leftPos + 55, topPos + 155, 56, 16,
				Text.translatable("gui.slide_show.color"));
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
		addDrawableChild(mColorInput);

		// width input
		mWidthInput = new EditBox(font, leftPos + 30, topPos + 51, 56, 16,
				Text.translatable("gui.slide_show.width"));
		mWidthInput.setResponder(text -> {
			try {
				Vec2 newSize = new Vec2(parseFloat(text), mImageSize.y);
				updateSize(newSize);
				mInvalidWidth = false;
			} catch (Exception e) {
				mInvalidWidth = true;
			}
			mWidthInput.setTextColor(mInvalidWidth ? 0xE04B4B : 0xE0E0E0);
		});
		mWidthInput.setValue(toOptionalSignedString(mEntity.mWidth));
		addDrawableChild(mWidthInput);

		// height input
		mHeightInput = new EditBox(font, leftPos + 111, topPos + 51, 56, 16,
				Text.translatable("gui.slide_show.height"));
		mHeightInput.setResponder(input -> {
			try {
				Vec2 newSize = new Vec2(mImageSize.x, parseFloat(input));
				updateSize(newSize);
				mInvalidHeight = false;
			} catch (Exception e) {
				mInvalidHeight = true;
			}
			mHeightInput.setTextColor(mInvalidHeight ? 0xE04B4B : 0xE0E0E0);
		});
		mHeightInput.setValue(toOptionalSignedString(mEntity.mHeight));
		addDrawableChild(mHeightInput);

		// offset x input
		mOffsetXInput = new EditBox(font, leftPos + 30, topPos + 103, 29, 16,
				Text.translatable("gui.slide_show.offset_x"));
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
		addDrawableChild(mOffsetXInput);

		// offset y input
		mOffsetYInput = new EditBox(font, leftPos + 84, topPos + 103, 29, 16,
				Text.translatable("gui.slide_show.offset_y"));
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
		addDrawableChild(mOffsetYInput);

		// offset z input
		mOffsetZInput = new EditBox(font, leftPos + 138, topPos + 103, 29, 16,
				Text.translatable("gui.slide_show.offset_z"));
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
		addDrawableChild(mOffsetZInput);

		// internal rotation buttons
		addDrawableChild(new ImageButton(leftPos + 117, topPos + 153, 18, 19, 179, 153, 0, GUI_TEXTURE, button -> {
			ProjectorBlock.InternalRotation newRotation = mRotation.flip();
			updateRotation(newRotation);
		}));
		addDrawableChild(new ImageButton(leftPos + 142, topPos + 153, 18, 19, 179, 173, 0, GUI_TEXTURE, button -> {
			ProjectorBlock.InternalRotation newRotation = mRotation.compose(Rotation.CLOCKWISE_90);
			updateRotation(newRotation);
		}));
		mRotation = mEntity.getBlockState().getValue(ProjectorBlock.ROTATION);

		// single sided / double sided
		mSwitchSingleSided = new ImageButton(leftPos + 9, topPos + 153, 18, 19, 179, 113, 0, GUI_TEXTURE, button -> {
			mDoubleSided = false;
			mSwitchDoubleSided.visible = true;
			mSwitchSingleSided.visible = false;
		});
		mSwitchDoubleSided = new ImageButton(leftPos + 9, topPos + 153, 18, 19, 179, 133, 0, GUI_TEXTURE, button -> {
			mDoubleSided = true;
			mSwitchSingleSided.visible = true;
			mSwitchDoubleSided.visible = false;
		});
		mDoubleSided = mEntity.mDoubleSided;
		mSwitchDoubleSided.visible = !mDoubleSided;
		mSwitchSingleSided.visible = mDoubleSided;
		addDrawableChild(mSwitchSingleSided);
		addDrawableChild(mSwitchDoubleSided);
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
	}

	@Override
	public void tick() {
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
	}

	@Override
	public void removed() {
		super.removed();
		minecraft.keyboardHandler.setSendRepeatsToGui(false);
		if (mEntity == null) {
			return;
		}
		final boolean invalidSize = mInvalidWidth || mInvalidHeight;
		final boolean invalidOffset = mInvalidOffsetX || mInvalidOffsetY || mInvalidOffsetZ;
		if (!mInvalidURL) {
			mEntity.mLocation = mURLInput.getValue();
		}
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
		new ProjectorUpdatePacket(mEntity, mRotation).sendToServer();
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
	public void render(PoseStack matrices, int mouseX, int mouseY, float delta) {
		if (mEntity != null) {
			final int leftPos = (width - imageWidth) / 2;
			final int topPos = (height - imageHeight) / 2;
			renderBackground(matrices);
			UtilitiesClient.beginDrawingTexture(GUI_TEXTURE);
			blit(matrices, leftPos, topPos, 0, 0, imageWidth, imageHeight);
			super.render(matrices, mouseX, mouseY, delta);

			int alpha = mImageColor >>> 24;
			if (alpha > 0) {
				int red = (mImageColor >> 16) & 255, green = (mImageColor >> 8) & 255, blue = mImageColor & 255;
				RenderUtils.setShaderColor(red / 255.0F, green / 255.0F, blue / 255.0F, alpha / 255.0F);
				blit(matrices, 38 + leftPos, 157 + topPos, 180, 194, 10, 10);
				blit(matrices, 82 + leftPos, 185 + topPos, 180, 194, 17, 17);
			}

			RenderUtils.setShaderColor(1.0F, 1.0F, 1.0F, 1.0F);
			blit(matrices, 82 + leftPos, 185 + topPos, 202, 194 - mRotation.ordinal() * 20, 17, 17);

			drawCenteredStringWithoutShadow(matrices, font, IMAGE_TEXT, width / 2F, 12 + topPos);
			drawCenteredStringWithoutShadow(matrices, font, OFFSET_TEXT, width / 2F, 86 + topPos);
			drawCenteredStringWithoutShadow(matrices, font, OTHERS_TEXT, width / 2F, 138 + topPos);

			int offsetX = mouseX - leftPos, offsetY = mouseY - topPos;
			if (offsetX >= 9 && offsetY >= 27 && offsetX < 27 && offsetY < 46) {
				renderTooltip(matrices, URL_TEXT, mouseX, mouseY);
			} else if (offsetX >= 34 && offsetY >= 153 && offsetX < 52 && offsetY < 172) {
				renderTooltip(matrices, COLOR_TEXT, mouseX, mouseY);
			} else if (offsetX >= 9 && offsetY >= 49 && offsetX < 27 && offsetY < 68) {
				renderTooltip(matrices, WIDTH_TEXT, mouseX, mouseY);
			} else if (offsetX >= 90 && offsetY >= 49 && offsetX < 108 && offsetY < 68) {
				renderTooltip(matrices, HEIGHT_TEXT, mouseX, mouseY);
			} else if (offsetX >= 9 && offsetY >= 101 && offsetX < 27 && offsetY < 120) {
				renderTooltip(matrices, OFFSET_X_TEXT, mouseX, mouseY);
			} else if (offsetX >= 63 && offsetY >= 101 && offsetX < 81 && offsetY < 120) {
				renderTooltip(matrices, OFFSET_Y_TEXT, mouseX, mouseY);
			} else if (offsetX >= 117 && offsetY >= 101 && offsetX < 135 && offsetY < 120) {
				renderTooltip(matrices, OFFSET_Z_TEXT, mouseX, mouseY);
			} else if (offsetX >= 117 && offsetY >= 153 && offsetX < 135 && offsetY < 172) {
				renderTooltip(matrices, FLIP_TEXT, mouseX, mouseY);
			} else if (offsetX >= 142 && offsetY >= 153 && offsetX < 160 && offsetY < 172) {
				renderTooltip(matrices, ROTATE_TEXT, mouseX, mouseY);
			} else if (offsetX >= 9 && offsetY >= 153 && offsetX < 27 && offsetY < 172) {
				renderTooltip(matrices, SINGLE_DOUBLE_SIDED_TEXT, mouseX, mouseY);
			}
		}
	}

	private static void drawCenteredStringWithoutShadow(PoseStack stack, Font renderer, Component string, float x, float y) {
		renderer.draw(stack, string, x - renderer.width(string) / 2F, y, 0x404040);
	}

	private static float parseFloat(String text) {
		return (float) new ExpressionBuilder(text).implicitMultiplication(false).build().evaluate();
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
}
