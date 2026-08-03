package org.teacon.slides.renderer;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.logging.annotations.FieldsAreNonnullByDefault;
import com.mojang.logging.annotations.MethodsReturnNonnullByDefault;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.resources.Identifier;
import net.minecraft.util.LightCoordsUtil;
import net.minecraft.util.Mth;
import net.minecraft.util.Util;
import org.joml.Vector2i;
import org.teacon.slides.SlideShow;
import org.teacon.slides.block.ProjectorBlockEntity;
import org.teacon.slides.calc.Concrete;
import org.teacon.slides.renderer.bitmap.BitmapProvider;

import javax.annotation.ParametersAreNonnullByDefault;
import java.util.ArrayList;
import java.util.Collections;
import java.util.SequencedCollection;
import java.util.TreeSet;

import static net.minecraft.client.renderer.texture.OverlayTexture.NO_OVERLAY;

@FieldsAreNonnullByDefault
@MethodsReturnNonnullByDefault
@ParametersAreNonnullByDefault
public final class TextureSequence {
    private static final Identifier BACKGROUND_ID = SlideShow.id("textures/gui/slide_default.png");

    private final int color;
    private final boolean back;
    private final boolean front;
    private final boolean hideWhenEmpty;
    private final boolean hideWhenFailed;
    private final boolean hideWhenBlocked;
    private final boolean hideWhenLoading;
    private final Vector2i sizeMicros = new Vector2i();
    private final Vector2i marginMicros = new Vector2i();
    private final TreeSet<String> recommends = new TreeSet<>();
    private final ArrayList<Elem> elements = new ArrayList<>(2);

    public TextureSequence(int xSizeMicros, int ySizeMicros, int xMarginMicros, int yMarginMicros,
                           ProjectorBlockEntity.ColorTransform projectorColorTransform, boolean flipped) {
        this.color = projectorColorTransform.color;
        this.sizeMicros.set(xSizeMicros, ySizeMicros);
        this.marginMicros.set(xMarginMicros, yMarginMicros);
        this.back = !flipped || projectorColorTransform.doubleSided;
        this.front = flipped || projectorColorTransform.doubleSided;
        this.hideWhenEmpty = projectorColorTransform.hideEmptySlideIcon;
        this.hideWhenFailed = projectorColorTransform.hideFailedSlideIcon;
        this.hideWhenBlocked = projectorColorTransform.hideBlockedSlideIcon;
        this.hideWhenLoading = projectorColorTransform.hideLoadingSlideIcon;
    }

    public void addBackground() {
        this.elements.add(Background.DEFAULT);
    }

    public void addEmptyIcon() {
        if (this.hideWhenEmpty) {
            this.elements.removeIf(e -> e instanceof Background || e instanceof IconCentered);
            return;
        }
        this.elements.add(IconCentered.DEFAULT_EMPTY);
    }

    public void addFailedIcon() {
        if (this.hideWhenFailed) {
            this.elements.removeIf(e -> e instanceof Background || e instanceof IconCentered);
            return;
        }
        this.elements.add(IconCentered.DEFAULT_FAILED);
    }

    public void addBlockedIcon() {
        if (this.hideWhenBlocked) {
            this.elements.removeIf(e -> e instanceof Background || e instanceof IconCentered);
            return;
        }
        this.elements.add(IconCentered.DEFAULT_BLOCKED);
    }

    public void addLoadingIcon() {
        if (this.hideWhenLoading) {
            this.elements.removeIf(e -> e instanceof Background || e instanceof IconCentered);
            return;
        }
        this.elements.add(IconCentered.DEFAULT_LOADING);
    }

    public void addTexture(BitmapProvider provider, Concrete.Size size, Concrete.Position position) {
        this.recommends.add(provider.getRecommendedName());
        var textureSize = Util.make(new Vector2i(), provider::getSize);
        this.elements.add(new Texture(provider, Concrete.from(size, position, this.sizeMicros, textureSize)));
    }

    public SequencedCollection<String> getRecommends() {
        return Collections.unmodifiableSequencedCollection(this.recommends);
    }

    public void submitContent(SubmitNodeCollector snc, PoseStack stack,
                              int[] lightCoords, int lightCoordsRowOffset, long tick, float partial) {
        var viewportMicros = this.sizeMicros;
        var alpha = this.color >>> 24;
        if (alpha > 0) {
            var layer = 0;
            var red = (this.color >> 16) & 255;
            var green = (this.color >> 8) & 255;
            var blue = this.color & 255;
            var viewportMin = Math.min(viewportMicros.x, viewportMicros.y);
            var widthMicrosSq = (float) viewportMicros.x * viewportMicros.x;
            var heightMicrosSq = (float) viewportMicros.y * viewportMicros.y;
            var factor = viewportMin / (24 + Mth.fastInvCubeRoot(390625E4F / (widthMicrosSq + heightMicrosSq)));
            var scaleHint = new Vector2i(Math.round(viewportMicros.x / factor), Math.round(viewportMicros.y / factor));
            while (layer < this.elements.size()) {
                var element = this.elements.get(layer);
                if (this.front) {
                    element.render(snc, stack, viewportMicros, this.marginMicros, scaleHint,
                            layer, alpha, red, green, blue, lightCoords, lightCoordsRowOffset, tick, partial);
                }
                layer = ~layer;
                if (this.back) {
                    element.render(snc, stack, viewportMicros, this.marginMicros, scaleHint,
                            layer, alpha, red, green, blue, lightCoords, lightCoordsRowOffset, tick, partial);
                }
                layer = -layer;
            }
        }
    }

    public void submitClipOutline(SubmitNodeCollector snc, PoseStack stack, int light, long tick, float partialTick) {
        snc.submitCustomGeometry(stack, RenderTypes.outline(BACKGROUND_ID), (pose, consumer) -> {
            var x = this.sizeMicros.x;
            var y = this.sizeMicros.y;
            consumer.addVertex(pose, 0, 0, y)
                    .setColor(255F, 255F, 255F, 255F)
                    .setUv(0, 1).setLight(light).setOverlay(NO_OVERLAY)
                    .setNormal(pose, 0, 1, 0);
            consumer.addVertex(pose, x, 0, y)
                    .setColor(255F, 255F, 255F, 255F)
                    .setUv(1, 1).setLight(light).setOverlay(NO_OVERLAY)
                    .setNormal(pose, 0, 1, 0);
            consumer.addVertex(pose, x, 0, 0)
                    .setColor(255F, 255F, 255F, 255F)
                    .setUv(1, 0).setLight(light).setOverlay(NO_OVERLAY)
                    .setNormal(pose, 0, 1, 0);
            consumer.addVertex(pose, 0, 0, 0)
                    .setColor(255F, 255F, 255F, 255F)
                    .setUv(0, 0).setLight(light).setOverlay(NO_OVERLAY)
                    .setNormal(pose, 0, 1, 0);
        });
    }

    public void clear() {
        this.elements.clear();
        this.recommends.clear();
    }

    @Override
    public String toString() {
        return "TextureSequence{color=" + this.color + ", back=" + this.back + ", front=" + this.front +
                ", hideWhenEmpty=" + this.hideWhenEmpty + ", hideWhenFailed=" + this.hideWhenFailed +
                ", hideWhenBlocked=" + this.hideWhenBlocked + ", hideWhenLoading=" + this.hideWhenLoading +
                ", sizeMicros=" + this.sizeMicros + ", elements=" + this.elements + "}";
    }

    private sealed interface Elem permits Background, IconCentered, Texture {
        void render(SubmitNodeCollector snc, PoseStack stack,
                    Vector2i viewportMicros, Vector2i marginMicros, Vector2i scaleHint,
                    int layer, int alpha, int red, int green, int blue,
                    int[] lightCoords, int lightCoordsRowOffset, long tick, float partialTick);
    }

    private static boolean hasVisibleArea(Vector2i viewportMicros, Vector2i offsetMicros,
                                          double left, double top, double right, double bottom) {
        var x0 = Math.clamp(offsetMicros.x, 0, viewportMicros.x);
        var y0 = Math.clamp(offsetMicros.y, 0, viewportMicros.y);
        var x1 = Math.clamp((long) offsetMicros.x + 1000000, 0, viewportMicros.x);
        var y1 = Math.clamp((long) offsetMicros.y + 1000000, 0, viewportMicros.y);
        return left < x1 && right > x0 && top < y1 && bottom > y0;
    }

    private static void calculateQuadLight(int[] lightCoords, int lightCoordsRowOffset,
                                           Vector2i marginMicros, Vector2i offsetMicros, int[] lightOutput) {
        var column = Math.floorDiv(offsetMicros.x + marginMicros.x, 1000000) + 1;
        var row = Math.floorDiv(offsetMicros.y + marginMicros.y, 1000000) + 1;
        var topRow = (row - 1) * lightCoordsRowOffset;
        var centerRow = topRow + lightCoordsRowOffset;
        var bottomRow = centerRow + lightCoordsRowOffset;
        var centerLight = lightCoords[centerRow + column];
        lightOutput[0] = LightCoordsUtil.smoothBlend(
                lightCoords[topRow + column - 1],
                lightCoords[topRow + column],
                lightCoords[centerRow + column - 1], centerLight);
        lightOutput[1] = LightCoordsUtil.smoothBlend(
                lightCoords[topRow + column],
                lightCoords[topRow + column + 1],
                lightCoords[centerRow + column + 1], centerLight);
        lightOutput[2] = LightCoordsUtil.smoothBlend(
                lightCoords[centerRow + column - 1],
                lightCoords[bottomRow + column - 1],
                lightCoords[bottomRow + column], centerLight);
        lightOutput[3] = LightCoordsUtil.smoothBlend(
                lightCoords[centerRow + column + 1],
                lightCoords[bottomRow + column],
                lightCoords[bottomRow + column + 1], centerLight);
    }

    private static void submitFullTexture(SubmitNodeCollector snc, PoseStack stack,
                                          RenderType renderType, Vector2i viewportMicros,
                                          double left, double top, double right, double bottom,
                                          float uAtLeft, float vAtTop, float uAtRight, float vAtBottom,
                                          int layer, int alpha, int red, int green, int blue, int light) {
        var clippedLeft = Math.clamp(left, 0D, viewportMicros.x);
        var clippedTop = Math.clamp(top, 0D, viewportMicros.y);
        var clippedRight = Math.clamp(right, 0D, viewportMicros.x);
        var clippedBottom = Math.clamp(bottom, 0D, viewportMicros.y);
        // noinspection DuplicatedCode
        var x0 = (float) clippedLeft;
        var y0 = (float) clippedTop;
        var x1 = (float) clippedRight;
        var y1 = (float) clippedBottom;
        var z0 = 4096F * layer + 2048F;
        var u0 = (float) Mth.clampedMap(clippedLeft, left, right, uAtLeft, uAtRight);
        var v0 = (float) Mth.clampedMap(clippedTop, top, bottom, vAtTop, vAtBottom);
        var u1 = (float) Mth.clampedMap(clippedRight, left, right, uAtLeft, uAtRight);
        var v1 = (float) Mth.clampedMap(clippedBottom, top, bottom, vAtTop, vAtBottom);
        // noinspection DuplicatedCode
        if (layer >= 0 && clippedLeft < clippedRight && clippedTop < clippedBottom) {
            snc.submitCustomGeometry(stack, renderType, (pose, consumer) -> {
                // noinspection DuplicatedCode
                consumer.addVertex(pose, x0, z0, y1)
                        .setColor(red, green, blue, alpha)
                        .setUv(u0, v1).setLight(light).setOverlay(NO_OVERLAY)
                        .setNormal(pose, 0, 1, 0);
                consumer.addVertex(pose, x1, z0, y1)
                        .setColor(red, green, blue, alpha)
                        .setUv(u1, v1).setLight(light).setOverlay(NO_OVERLAY)
                        .setNormal(pose, 0, 1, 0);
                // noinspection DuplicatedCode
                consumer.addVertex(pose, x1, z0, y0)
                        .setColor(red, green, blue, alpha)
                        .setUv(u1, v0).setLight(light).setOverlay(NO_OVERLAY)
                        .setNormal(pose, 0, 1, 0);
                consumer.addVertex(pose, x0, z0, y0)
                        .setColor(red, green, blue, alpha)
                        .setUv(u0, v0).setLight(light).setOverlay(NO_OVERLAY)
                        .setNormal(pose, 0, 1, 0);
            });
        }
        if (layer < 0 && clippedLeft < clippedRight && clippedTop < clippedBottom) {
            snc.submitCustomGeometry(stack, renderType, (pose, consumer) -> {
                // noinspection DuplicatedCode
                consumer.addVertex(pose, x0, z0, y0)
                        .setColor(red, green, blue, alpha)
                        .setUv(u0, v0).setLight(light).setOverlay(NO_OVERLAY)
                        .setNormal(pose, 0, -1, 0);
                consumer.addVertex(pose, x1, z0, y0)
                        .setColor(red, green, blue, alpha)
                        .setUv(u1, v0).setLight(light).setOverlay(NO_OVERLAY)
                        .setNormal(pose, 0, -1, 0);
                // noinspection DuplicatedCode
                consumer.addVertex(pose, x1, z0, y1)
                        .setColor(red, green, blue, alpha)
                        .setUv(u1, v1).setLight(light).setOverlay(NO_OVERLAY)
                        .setNormal(pose, 0, -1, 0);
                consumer.addVertex(pose, x0, z0, y1)
                        .setColor(red, green, blue, alpha)
                        .setUv(u0, v1).setLight(light).setOverlay(NO_OVERLAY)
                        .setNormal(pose, 0, -1, 0);
            });
        }
    }

    private static void submitClippedQuad(SubmitNodeCollector snc,
                                          PoseStack stack, RenderType renderType,
                                          Vector2i viewportMicros, Vector2i offsetMicros,
                                          double left, double top, double right, double bottom,
                                          float uAtLeft, float vAtTop, float uAtRight, float vAtBottom,
                                          int layer, int alpha, int red, int green, int blue, int[] light) {
        var clippedLeft = Math.max(left, Math.clamp(offsetMicros.x, 0, viewportMicros.x));
        var clippedTop = Math.max(top, Math.clamp(offsetMicros.y, 0, viewportMicros.y));
        var clippedRight = Math.min(right, Math.clamp((long) offsetMicros.x + 1000000, 0, viewportMicros.x));
        var clippedBottom = Math.min(bottom, Math.clamp((long) offsetMicros.y + 1000000, 0, viewportMicros.y));
        // noinspection DuplicatedCode
        var x0 = (float) clippedLeft;
        var y0 = (float) clippedTop;
        var x1 = (float) clippedRight;
        var y1 = (float) clippedBottom;
        var z0 = 4096F * layer + 2048F;
        var u0 = (float) Mth.clampedMap(clippedLeft, left, right, uAtLeft, uAtRight);
        var v0 = (float) Mth.clampedMap(clippedTop, top, bottom, vAtTop, vAtBottom);
        var u1 = (float) Mth.clampedMap(clippedRight, left, right, uAtLeft, uAtRight);
        var v1 = (float) Mth.clampedMap(clippedBottom, top, bottom, vAtTop, vAtBottom);
        var x0Weight = Mth.inverseLerp(x0, offsetMicros.x, offsetMicros.x + 1E6F);
        var y0Weight = Mth.inverseLerp(y0, offsetMicros.y, offsetMicros.y + 1E6F);
        var x1Weight = Mth.inverseLerp(x1, offsetMicros.x, offsetMicros.x + 1E6F);
        var y1Weight = Mth.inverseLerp(y1, offsetMicros.y, offsetMicros.y + 1E6F);
        var x0WeightInv = 1F - x0Weight;
        var y0WeightInv = 1F - y0Weight;
        var x1WeightInv = 1F - x1Weight;
        var y1WeightInv = 1F - y1Weight;
        // noinspection DuplicatedCode
        var lightAtTopLeft = LightCoordsUtil.smoothWeightedBlend(light[0], light[1], light[2], light[3],
                x0WeightInv * y0WeightInv, x0Weight * y0WeightInv, x0WeightInv * y0Weight, x0Weight * y0Weight);
        var lightAtTopRight = LightCoordsUtil.smoothWeightedBlend(light[0], light[1], light[2], light[3],
                x1WeightInv * y0WeightInv, x1Weight * y0WeightInv, x1WeightInv * y0Weight, x1Weight * y0Weight);
        // noinspection DuplicatedCode
        var lightAtBottomLeft = LightCoordsUtil.smoothWeightedBlend(light[0], light[1], light[2], light[3],
                x0WeightInv * y1WeightInv, x0Weight * y1WeightInv, x0WeightInv * y1Weight, x0Weight * y1Weight);
        var lightAtBottomRight = LightCoordsUtil.smoothWeightedBlend(light[0], light[1], light[2], light[3],
                x1WeightInv * y1WeightInv, x1Weight * y1WeightInv, x1WeightInv * y1Weight, x1Weight * y1Weight);
        // noinspection DuplicatedCode
        if (layer >= 0 && clippedLeft < clippedRight && clippedTop < clippedBottom) {
            snc.submitCustomGeometry(stack, renderType, (pose, consumer) -> {
                // noinspection DuplicatedCode
                consumer.addVertex(pose, x0, z0, y1)
                        .setColor(red, green, blue, alpha)
                        .setUv(u0, v1).setLight(lightAtBottomLeft).setOverlay(NO_OVERLAY)
                        .setNormal(pose, 0, 1, 0);
                consumer.addVertex(pose, x1, z0, y1)
                        .setColor(red, green, blue, alpha)
                        .setUv(u1, v1).setLight(lightAtBottomRight).setOverlay(NO_OVERLAY)
                        .setNormal(pose, 0, 1, 0);
                // noinspection DuplicatedCode
                consumer.addVertex(pose, x1, z0, y0)
                        .setColor(red, green, blue, alpha)
                        .setUv(u1, v0).setLight(lightAtTopRight).setOverlay(NO_OVERLAY)
                        .setNormal(pose, 0, 1, 0);
                consumer.addVertex(pose, x0, z0, y0)
                        .setColor(red, green, blue, alpha)
                        .setUv(u0, v0).setLight(lightAtTopLeft).setOverlay(NO_OVERLAY)
                        .setNormal(pose, 0, 1, 0);
            });
        }
        if (layer < 0 && clippedLeft < clippedRight && clippedTop < clippedBottom) {
            snc.submitCustomGeometry(stack, renderType, (pose, consumer) -> {
                // noinspection DuplicatedCode
                consumer.addVertex(pose, x0, z0, y0)
                        .setColor(red, green, blue, alpha)
                        .setUv(u0, v0).setLight(lightAtTopLeft).setOverlay(NO_OVERLAY)
                        .setNormal(pose, 0, -1, 0);
                consumer.addVertex(pose, x1, z0, y0)
                        .setColor(red, green, blue, alpha)
                        .setUv(u1, v0).setLight(lightAtTopRight).setOverlay(NO_OVERLAY)
                        .setNormal(pose, 0, -1, 0);
                // noinspection DuplicatedCode
                consumer.addVertex(pose, x1, z0, y1)
                        .setColor(red, green, blue, alpha)
                        .setUv(u1, v1).setLight(lightAtBottomRight).setOverlay(NO_OVERLAY)
                        .setNormal(pose, 0, -1, 0);
                consumer.addVertex(pose, x0, z0, y1)
                        .setColor(red, green, blue, alpha)
                        .setUv(u0, v1).setLight(lightAtBottomLeft).setOverlay(NO_OVERLAY)
                        .setNormal(pose, 0, -1, 0);
            });
        }
    }

    private record Texture(BitmapProvider provider, Concrete concrete) implements Elem {
        @Override
        public void render(SubmitNodeCollector snc, PoseStack stack,
                           Vector2i viewportMicros, Vector2i marginMicros, Vector2i scaleHint,
                           int layer, int alpha, int red, int green, int blue,
                           int[] lightCoords, int lightCoordsRowOffset, long tick, float partialTick) {
            var top = this.concrete.topMicros();
            var right = this.concrete.rightMicros();
            var bottom = this.concrete.bottomMicros();
            var left = this.concrete.leftMicros();
            var type = this.provider.updateAndGet(tick, partialTick);
            if (lightCoords.length == 0) {
                submitFullTexture(snc, stack, type, viewportMicros, left, top, right, bottom,
                        0F, 0F, 1F, 1F, layer, alpha, red, green, blue, LightCoordsUtil.FULL_BRIGHT);
            } else {
                var light = new int[4];
                var offsetMicros = new Vector2i();
                for (var y = -marginMicros.y; y < viewportMicros.y; y += 1000000) {
                    for (var x = -marginMicros.x; x < viewportMicros.x; x += 1000000) {
                        calculateQuadLight(lightCoords, lightCoordsRowOffset, marginMicros, offsetMicros.set(x, y), light);
                        if (hasVisibleArea(viewportMicros, offsetMicros, left, top, right, bottom)) {
                            submitClippedQuad(snc, stack, type, viewportMicros, offsetMicros,
                                    left, top, right, bottom, 0F, 0F, 1F, 1F, layer, alpha, red, green, blue, light);
                        }
                    }
                }
            }
        }
    }

    private enum IconCentered implements Elem {
        DEFAULT_EMPTY(SlideShow.id("textures/gui/slide_icon_empty.png")),
        DEFAULT_FAILED(SlideShow.id("textures/gui/slide_icon_failed.png")),
        DEFAULT_BLOCKED(SlideShow.id("textures/gui/slide_icon_blocked.png")),
        DEFAULT_LOADING(SlideShow.id("textures/gui/slide_icon_loading.png"));

        private final RenderType iconRenderType;

        IconCentered(Identifier icon) {
            this.iconRenderType = SlideRenderSetup.createIconType(icon);
        }

        @Override
        public void render(SubmitNodeCollector snc, PoseStack stack,
                           Vector2i viewportMicros, Vector2i marginMicros, Vector2i scaleHint,
                           int layer, int alpha, int red, int green, int blue,
                           int[] lightCoords, int lightCoordsRowOffset, long tick, float partialTick) {
            var left = viewportMicros.x * (1F - 19F / scaleHint.x) / 2F;
            var top = viewportMicros.y * (1F - 16F / scaleHint.y) / 2F;
            var right = viewportMicros.x * (1F + 19F / scaleHint.x) / 2F;
            var bottom = viewportMicros.y * (1F + 16F / scaleHint.y) / 2F;
            var type = this.iconRenderType;
            if (lightCoords.length == 0) {
                submitFullTexture(snc, stack, type, viewportMicros, left, top, right, bottom,
                        0F, 0F, 1F, 1F, layer, alpha, 255, 255, 255, LightCoordsUtil.FULL_BRIGHT);
            } else {
                var light = new int[4];
                var offsetMicros = new Vector2i();
                for (var y = -marginMicros.y; y < viewportMicros.y; y += 1000000) {
                    for (var x = -marginMicros.x; x < viewportMicros.x; x += 1000000) {
                        calculateQuadLight(lightCoords, lightCoordsRowOffset, marginMicros, offsetMicros.set(x, y), light);
                        submitClippedQuad(snc, stack, type, viewportMicros, offsetMicros,
                                left, top, right, bottom, 0F, 0F, 1F, 1F, layer, alpha, 255, 255, 255, light);
                    }
                }
            }
        }
    }

    private enum Background implements Elem {
        DEFAULT(BACKGROUND_ID);

        private final RenderType backgroundRenderType;

        Background(Identifier background) {
            this.backgroundRenderType = SlideRenderSetup.createIconType(background);
        }

        @Override
        public void render(SubmitNodeCollector snc, PoseStack stack,
                           Vector2i viewportMicros, Vector2i marginMicros, Vector2i scaleHint,
                           int layer, int alpha, int red, int green, int blue,
                           int[] lightCoords, int lightCoordsRowOffset, long tick, float partialTick) {
            var x3 = (float) viewportMicros.x;
            var y3 = (float) viewportMicros.y;
            var x1 = x3 * 9F / scaleHint.x;
            var y1 = y3 * 9F / scaleHint.y;
            var x2 = x3 - x1;
            var y2 = y3 - y1;
            var u1 = 9F / 19F;
            var u2 = 1F - u1;
            var type = this.backgroundRenderType;
            if (lightCoords.length == 0) {
                submitFullTexture(snc, stack, type, viewportMicros, 0F, 0F, x1, y1,
                        0F, 0F, u1, u1, layer, alpha, 255, 255, 255, LightCoordsUtil.FULL_BRIGHT);
                submitFullTexture(snc, stack, type, viewportMicros, x1, 0F, x2, y1,
                        u1, 0F, u2, u1, layer, alpha, 255, 255, 255, LightCoordsUtil.FULL_BRIGHT);
                submitFullTexture(snc, stack, type, viewportMicros, x2, 0F, x3, y1,
                        u2, 0F, 1F, u1, layer, alpha, 255, 255, 255, LightCoordsUtil.FULL_BRIGHT);
                submitFullTexture(snc, stack, type, viewportMicros, 0F, y1, x1, y2,
                        0F, u1, u1, u2, layer, alpha, 255, 255, 255, LightCoordsUtil.FULL_BRIGHT);
                submitFullTexture(snc, stack, type, viewportMicros, x1, y1, x2, y2,
                        u1, u1, u2, u2, layer, alpha, 255, 255, 255, LightCoordsUtil.FULL_BRIGHT);
                submitFullTexture(snc, stack, type, viewportMicros, x2, y1, x3, y2,
                        u2, u1, 1F, u2, layer, alpha, 255, 255, 255, LightCoordsUtil.FULL_BRIGHT);
                submitFullTexture(snc, stack, type, viewportMicros, 0F, y2, x1, y3,
                        0F, u2, u1, 1F, layer, alpha, 255, 255, 255, LightCoordsUtil.FULL_BRIGHT);
                submitFullTexture(snc, stack, type, viewportMicros, x1, y2, x2, y3,
                        u1, u2, u2, 1F, layer, alpha, 255, 255, 255, LightCoordsUtil.FULL_BRIGHT);
                submitFullTexture(snc, stack, type, viewportMicros, x2, y2, x3, y3,
                        u2, u2, 1F, 1F, layer, alpha, 255, 255, 255, LightCoordsUtil.FULL_BRIGHT);
            } else {
                var light = new int[4];
                var offsetMicros = new Vector2i();
                for (var y = -marginMicros.y; y < viewportMicros.y; y += 1000000) {
                    for (var x = -marginMicros.x; x < viewportMicros.x; x += 1000000) {
                        calculateQuadLight(lightCoords, lightCoordsRowOffset, marginMicros, offsetMicros.set(x, y), light);
                        // Render the nine-slice background as individually clipped cells.
                        submitClippedQuad(snc, stack, type, viewportMicros, offsetMicros,
                                0F, 0F, x1, y1, 0F, 0F, u1, u1, layer, alpha, 255, 255, 255, light);
                        submitClippedQuad(snc, stack, type, viewportMicros, offsetMicros,
                                x1, 0F, x2, y1, u1, 0F, u2, u1, layer, alpha, 255, 255, 255, light);
                        submitClippedQuad(snc, stack, type, viewportMicros, offsetMicros,
                                x2, 0F, x3, y1, u2, 0F, 1F, u1, layer, alpha, 255, 255, 255, light);
                        submitClippedQuad(snc, stack, type, viewportMicros, offsetMicros,
                                0F, y1, x1, y2, 0F, u1, u1, u2, layer, alpha, 255, 255, 255, light);
                        submitClippedQuad(snc, stack, type, viewportMicros, offsetMicros,
                                x1, y1, x2, y2, u1, u1, u2, u2, layer, alpha, 255, 255, 255, light);
                        submitClippedQuad(snc, stack, type, viewportMicros, offsetMicros,
                                x2, y1, x3, y2, u2, u1, 1F, u2, layer, alpha, 255, 255, 255, light);
                        submitClippedQuad(snc, stack, type, viewportMicros, offsetMicros,
                                0F, y2, x1, y3, 0F, u2, u1, 1F, layer, alpha, 255, 255, 255, light);
                        submitClippedQuad(snc, stack, type, viewportMicros, offsetMicros,
                                x1, y2, x2, y3, u1, u2, u2, 1F, layer, alpha, 255, 255, 255, light);
                        submitClippedQuad(snc, stack, type, viewportMicros, offsetMicros,
                                x2, y2, x3, y3, u2, u2, 1F, 1F, layer, alpha, 255, 255, 255, light);
                    }
                }
            }
        }
    }
}
