package org.teacon.slides.renderer;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.Util;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import org.joml.Vector2i;
import org.teacon.slides.SlideShow;
import org.teacon.slides.block.ProjectorBlockEntity;
import org.teacon.slides.calc.Concrete;
import org.teacon.slides.texture.TextureProvider;

import java.util.ArrayList;
import java.util.Collections;
import java.util.SequencedCollection;
import java.util.TreeSet;

public final class TextureSequence {
    private final int color;
    private final boolean back;
    private final boolean front;
    private final boolean hideWhenEmpty;
    private final boolean hideWhenFailed;
    private final boolean hideWhenBlocked;
    private final boolean hideWhenLoading;
    private final Vector2i sizeMicros = new Vector2i();
    private final TreeSet<String> recommends = new TreeSet<>();
    private final ArrayList<Elem> elements = new ArrayList<>(2);

    public TextureSequence(Vector2i sizeMicros, ProjectorBlockEntity.ColorTransform ct, boolean flipped) {
        this.color = ct.color;
        this.sizeMicros.set(sizeMicros);
        this.back = !flipped || ct.doubleSided;
        this.front = flipped || ct.doubleSided;
        this.hideWhenEmpty = ct.hideEmptySlideIcon;
        this.hideWhenFailed = ct.hideFailedSlideIcon;
        this.hideWhenBlocked = ct.hideBlockedSlideIcon;
        this.hideWhenLoading = ct.hideLoadingSlideIcon;
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
        this.elements.add(IconCentered.DEFAULT_EMPTY);
    }

    public void addBlockedIcon() {
        if (this.hideWhenBlocked) {
            this.elements.removeIf(e -> e instanceof Background || e instanceof IconCentered);
            return;
        }
        this.elements.add(IconCentered.DEFAULT_EMPTY);
    }

    public void addLoadingIcon() {
        if (this.hideWhenLoading) {
            this.elements.removeIf(e -> e instanceof Background || e instanceof IconCentered);
            return;
        }
        this.elements.add(IconCentered.DEFAULT_EMPTY);
    }

    public void addTexture(TextureProvider provider, Concrete.Size size) {
        this.recommends.add(provider.getRecommendedName());
        var textureSize = Util.make(new Vector2i(), provider::getSize);
        var concrete = Concrete.from(size, this.sizeMicros, textureSize);
        this.elements.add(new Texture(provider, concrete, 0, 0, textureSize.x, textureSize.y));
    }

    public SequencedCollection<String> getRecommends() {
        return Collections.unmodifiableSequencedCollection(this.recommends);
    }

    public void render(MultiBufferSource src, PoseStack.Pose pose,
                       Vector2i viewportMicros, int light, int overlay, long tick, float partialTick) {
        var alpha = this.color >>> 24;
        if (alpha > 0) {
            var viewportMin = Math.min(viewportMicros.x, viewportMicros.y);
            var widthMicrosSq = (float) viewportMicros.x * viewportMicros.x;
            var heightMicrosSq = (float) viewportMicros.y * viewportMicros.y;
            var factor = viewportMin / (24 + Mth.fastInvCubeRoot(390625E4F / (widthMicrosSq + heightMicrosSq)));
            var scaleHint = new Vector2i(Math.round(viewportMicros.x / factor), Math.round(viewportMicros.y / factor));
            var red = (this.color >> 16) & 255;
            var green = (this.color >> 8) & 255;
            var blue = this.color & 255;
            var layer = 0;
            while (layer < this.elements.size()) {
                var element = this.elements.get(layer);
                if (this.front) {
                    element.render(src, pose, viewportMicros, scaleHint, layer,
                            alpha, red, green, blue, light, overlay, tick, partialTick);
                }
                layer = ~layer;
                if (this.back) {
                    element.render(src, pose, viewportMicros, scaleHint, layer,
                            alpha, red, green, blue, light, overlay, tick, partialTick);
                }
                layer = -layer;
            }
        }
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
        void render(MultiBufferSource src, PoseStack.Pose pose,
                    Vector2i viewportMicros, Vector2i scaleHint,
                    int layer, int alpha, int red, int green, int blue,
                    int light, int overlay, long tick, float partialTick);
    }

    private record Texture(TextureProvider provider, Concrete concrete, int x, int y, int w, int h) implements Elem {
        @Override
        public void render(MultiBufferSource src, PoseStack.Pose pose,
                           Vector2i viewportMicros, Vector2i scaleHint,
                           int layer, int alpha, int red, int green, int blue,
                           int light, int overlay, long tick, float partialTick) {
            // get vertex consumer
            var consumer = src.getBuffer(this.provider.updateAndGet(tick, partialTick));
            // calculate image boundaries without clipping
            var top = this.concrete.topMicros();
            var right = this.concrete.rightMicros();
            var bottom = this.concrete.bottomMicros();
            var left = this.concrete.leftMicros();
            // clip image boundaries
            var x0 = (float) Math.clamp(left, 0D, viewportMicros.x);
            var y0 = (float) Math.clamp(top, 0D, viewportMicros.y);
            var x1 = (float) Math.clamp(right, 0D, viewportMicros.x);
            var y1 = (float) Math.clamp(bottom, 0D, viewportMicros.y);
            var z0 = 4096F * layer + 2048F;
            // calculate uv for rendering
            var u0 = left == right ? 0F : (float) Mth.clamp(Mth.inverseLerp(0D, left, right), 0D, 1D);
            var v0 = top == bottom ? 0F : (float) Mth.clamp(Mth.inverseLerp(0D, top, bottom), 0D, 1D);
            var u1 = left == right ? 1F : (float) Mth.clamp(Mth.inverseLerp(viewportMicros.x, left, right), 0D, 1D);
            var v1 = top == bottom ? 1F : (float) Mth.clamp(Mth.inverseLerp(viewportMicros.y, top, bottom), 0D, 1D);
            if (layer >= 0) {
                // noinspection DuplicatedCode
                consumer.addVertex(pose, x0, z0, y1)
                        .setColor(red, green, blue, alpha)
                        .setUv(u0, v1).setLight(light)
                        .setNormal(pose, 0, 1, 0);
                consumer.addVertex(pose, x1, z0, y1)
                        .setColor(red, green, blue, alpha)
                        .setUv(u1, v1).setLight(light)
                        .setNormal(pose, 0, 1, 0);
                // noinspection DuplicatedCode
                consumer.addVertex(pose, x1, z0, y0)
                        .setColor(red, green, blue, alpha)
                        .setUv(u1, v0).setLight(light)
                        .setNormal(pose, 0, 1, 0);
                consumer.addVertex(pose, x0, z0, y0)
                        .setColor(red, green, blue, alpha)
                        .setUv(u0, v0).setLight(light)
                        .setNormal(pose, 0, 1, 0);
            } else {
                // noinspection DuplicatedCode
                consumer.addVertex(pose, x0, z0, y0)
                        .setColor(red, green, blue, alpha)
                        .setUv(u0, v0).setLight(light)
                        .setNormal(pose, 0, -1, 0);
                consumer.addVertex(pose, x1, z0, y0)
                        .setColor(red, green, blue, alpha)
                        .setUv(u1, v0).setLight(light)
                        .setNormal(pose, 0, -1, 0);
                // noinspection DuplicatedCode
                consumer.addVertex(pose, x1, z0, y1)
                        .setColor(red, green, blue, alpha)
                        .setUv(u1, v1).setLight(light)
                        .setNormal(pose, 0, -1, 0);
                consumer.addVertex(pose, x0, z0, y1)
                        .setColor(red, green, blue, alpha)
                        .setUv(u0, v1).setLight(light)
                        .setNormal(pose, 0, -1, 0);
            }
        }
    }

    private enum IconCentered implements Elem {
        DEFAULT_EMPTY(SlideShow.id("textures/gui/slide_icon_empty.png")),
        DEFAULT_FAILED(SlideShow.id("textures/gui/slide_icon_failed.png")),
        DEFAULT_BLOCKED(SlideShow.id("textures/gui/slide_icon_blocked.png")),
        DEFAULT_LOADING(SlideShow.id("textures/gui/slide_icon_loading.png"));

        private final SlideRenderType iconRenderType;

        IconCentered(ResourceLocation icon) {
            this.iconRenderType = new SlideRenderType(icon);
        }

        @Override
        public void render(MultiBufferSource src, PoseStack.Pose pose,
                           Vector2i viewportMicros, Vector2i scaleHint,
                           int layer, int alpha, int red, int green, int blue,
                           int light, int overlay, long tick, float partialTick) {
            var consumer = src.getBuffer(this.iconRenderType);
            var x1 = viewportMicros.x * (1F - 19F / scaleHint.x) / 2F;
            var y1 = viewportMicros.y * (1F - 16F / scaleHint.y) / 2F;
            var x2 = viewportMicros.x * (1F + 19F / scaleHint.x) / 2F;
            var y2 = viewportMicros.y * (1F + 16F / scaleHint.y) / 2F;
            var z0 = 4096F * layer + 2048F;
            if (layer >= 0) {
                consumer.addVertex(pose, x1, z0, y2)
                        .setColor(255, 255, 255, alpha)
                        .setUv(0F, 1F).setLight(light)
                        .setNormal(pose, 0, 1, 0);
                consumer.addVertex(pose, x2, z0, y2)
                        .setColor(255, 255, 255, alpha)
                        .setUv(1F, 1F).setLight(light)
                        .setNormal(pose, 0, 1, 0);
                consumer.addVertex(pose, x2, z0, y1)
                        .setColor(255, 255, 255, alpha)
                        .setUv(1F, 0F).setLight(light)
                        .setNormal(pose, 0, 1, 0);
                consumer.addVertex(pose, x1, z0, y1)
                        .setColor(255, 255, 255, alpha)
                        .setUv(0F, 0F).setLight(light)
                        .setNormal(pose, 0, 1, 0);
            } else {
                consumer.addVertex(pose, x1, z0, y1)
                        .setColor(255, 255, 255, alpha)
                        .setUv(0F, 0F).setLight(light)
                        .setNormal(pose, 0, -1, 0);
                consumer.addVertex(pose, x2, z0, y1)
                        .setColor(255, 255, 255, alpha)
                        .setUv(1F, 0F).setLight(light)
                        .setNormal(pose, 0, -1, 0);
                consumer.addVertex(pose, x2, z0, y2)
                        .setColor(255, 255, 255, alpha)
                        .setUv(1F, 1F).setLight(light)
                        .setNormal(pose, 0, -1, 0);
                consumer.addVertex(pose, x1, z0, y2)
                        .setColor(255, 255, 255, alpha)
                        .setUv(0F, 1F).setLight(light)
                        .setNormal(pose, 0, -1, 0);
            }
        }
    }

    private enum Background implements Elem {
        DEFAULT(SlideShow.id("textures/gui/slide_default.png"));

        private final SlideRenderType iconRenderType;

        Background(ResourceLocation background) {
            this.iconRenderType = new SlideRenderType(background);
        }

        @Override
        public void render(MultiBufferSource src, PoseStack.Pose pose,
                           Vector2i viewportMicros, Vector2i scaleHint,
                           int layer, int alpha, int red, int green, int blue,
                           int light, int overlay, long tick, float partialTick) {
            var consumer = src.getBuffer(this.iconRenderType);
            var x3 = viewportMicros.x;
            var y3 = viewportMicros.y;
            var x2 = x3 * (1F - 9F / scaleHint.x);
            var y2 = y3 * (1F - 9F / scaleHint.y);
            var x1 = x3 * 9F / scaleHint.x;
            var y1 = y3 * 9F / scaleHint.y;
            var z0 = 4096F * layer + 2048F;
            var u2 = 1F - 9F / 19F;
            var u1 = 9F / 19F;
            // below is the generation code
            /*
             * #!/usr/bin/python3
             *
             * xs = [('0F', '0F'), ('x1', 'u1'), ('x2', 'u2'), ('x3', '1F')]
             * ys = [('0F', '0F'), ('y1', 'u1'), ('y2', 'u2'), ('y3', '1F')]
             *
             * fmt = '\n'.join([
             *     '    consumer.addVertex(pose, {}, {}, {})',
             *     '            .setColor(255, 255, 255, alpha)',
             *     '            .setUv({}, {}).setLight(light)',
             *     '            .setNormal(pose, 0, {}, 0);',
             * ])
             *
             * print('if (layer >= 0) {')
             * for i in range(3):
             *     for j in range(3):
             *         a, b, c, d = xs[i], xs[i + 1], ys[j], ys[j + 1]
             *         for k, l in [(a, d), (b, d), (b, c), (a, c)]:
             *             print(fmt.format(k[0], 'z0', l[0], k[1], l[1], 1))
             * print('} else {')
             * for i in range(3):
             *     for j in range(3):
             *         a, b, c, d = xs[i], xs[i + 1], ys[j], ys[j + 1]
             *         for k, l in [(a, c), (b, c), (b, d), (a, d)]:
             *             print(fmt.format(k[0], 'z0', l[0], k[1], l[1], -1))
             * print('}')
             */
            if (layer >= 0) {
                consumer.addVertex(pose, 0F, z0, y1)
                        .setColor(255, 255, 255, alpha)
                        .setUv(0F, u1).setLight(light)
                        .setNormal(pose, 0, 1, 0);
                consumer.addVertex(pose, x1, z0, y1)
                        .setColor(255, 255, 255, alpha)
                        .setUv(u1, u1).setLight(light)
                        .setNormal(pose, 0, 1, 0);
                consumer.addVertex(pose, x1, z0, 0F)
                        .setColor(255, 255, 255, alpha)
                        .setUv(u1, 0F).setLight(light)
                        .setNormal(pose, 0, 1, 0);
                consumer.addVertex(pose, 0F, z0, 0F)
                        .setColor(255, 255, 255, alpha)
                        .setUv(0F, 0F).setLight(light)
                        .setNormal(pose, 0, 1, 0);
                consumer.addVertex(pose, 0F, z0, y2)
                        .setColor(255, 255, 255, alpha)
                        .setUv(0F, u2).setLight(light)
                        .setNormal(pose, 0, 1, 0);
                consumer.addVertex(pose, x1, z0, y2)
                        .setColor(255, 255, 255, alpha)
                        .setUv(u1, u2).setLight(light)
                        .setNormal(pose, 0, 1, 0);
                consumer.addVertex(pose, x1, z0, y1)
                        .setColor(255, 255, 255, alpha)
                        .setUv(u1, u1).setLight(light)
                        .setNormal(pose, 0, 1, 0);
                consumer.addVertex(pose, 0F, z0, y1)
                        .setColor(255, 255, 255, alpha)
                        .setUv(0F, u1).setLight(light)
                        .setNormal(pose, 0, 1, 0);
                consumer.addVertex(pose, 0F, z0, y3)
                        .setColor(255, 255, 255, alpha)
                        .setUv(0F, 1F).setLight(light)
                        .setNormal(pose, 0, 1, 0);
                consumer.addVertex(pose, x1, z0, y3)
                        .setColor(255, 255, 255, alpha)
                        .setUv(u1, 1F).setLight(light)
                        .setNormal(pose, 0, 1, 0);
                consumer.addVertex(pose, x1, z0, y2)
                        .setColor(255, 255, 255, alpha)
                        .setUv(u1, u2).setLight(light)
                        .setNormal(pose, 0, 1, 0);
                consumer.addVertex(pose, 0F, z0, y2)
                        .setColor(255, 255, 255, alpha)
                        .setUv(0F, u2).setLight(light)
                        .setNormal(pose, 0, 1, 0);
                consumer.addVertex(pose, x1, z0, y1)
                        .setColor(255, 255, 255, alpha)
                        .setUv(u1, u1).setLight(light)
                        .setNormal(pose, 0, 1, 0);
                consumer.addVertex(pose, x2, z0, y1)
                        .setColor(255, 255, 255, alpha)
                        .setUv(u2, u1).setLight(light)
                        .setNormal(pose, 0, 1, 0);
                consumer.addVertex(pose, x2, z0, 0F)
                        .setColor(255, 255, 255, alpha)
                        .setUv(u2, 0F).setLight(light)
                        .setNormal(pose, 0, 1, 0);
                consumer.addVertex(pose, x1, z0, 0F)
                        .setColor(255, 255, 255, alpha)
                        .setUv(u1, 0F).setLight(light)
                        .setNormal(pose, 0, 1, 0);
                consumer.addVertex(pose, x1, z0, y2)
                        .setColor(255, 255, 255, alpha)
                        .setUv(u1, u2).setLight(light)
                        .setNormal(pose, 0, 1, 0);
                consumer.addVertex(pose, x2, z0, y2)
                        .setColor(255, 255, 255, alpha)
                        .setUv(u2, u2).setLight(light)
                        .setNormal(pose, 0, 1, 0);
                consumer.addVertex(pose, x2, z0, y1)
                        .setColor(255, 255, 255, alpha)
                        .setUv(u2, u1).setLight(light)
                        .setNormal(pose, 0, 1, 0);
                consumer.addVertex(pose, x1, z0, y1)
                        .setColor(255, 255, 255, alpha)
                        .setUv(u1, u1).setLight(light)
                        .setNormal(pose, 0, 1, 0);
                consumer.addVertex(pose, x1, z0, y3)
                        .setColor(255, 255, 255, alpha)
                        .setUv(u1, 1F).setLight(light)
                        .setNormal(pose, 0, 1, 0);
                consumer.addVertex(pose, x2, z0, y3)
                        .setColor(255, 255, 255, alpha)
                        .setUv(u2, 1F).setLight(light)
                        .setNormal(pose, 0, 1, 0);
                consumer.addVertex(pose, x2, z0, y2)
                        .setColor(255, 255, 255, alpha)
                        .setUv(u2, u2).setLight(light)
                        .setNormal(pose, 0, 1, 0);
                consumer.addVertex(pose, x1, z0, y2)
                        .setColor(255, 255, 255, alpha)
                        .setUv(u1, u2).setLight(light)
                        .setNormal(pose, 0, 1, 0);
                consumer.addVertex(pose, x2, z0, y1)
                        .setColor(255, 255, 255, alpha)
                        .setUv(u2, u1).setLight(light)
                        .setNormal(pose, 0, 1, 0);
                consumer.addVertex(pose, x3, z0, y1)
                        .setColor(255, 255, 255, alpha)
                        .setUv(1F, u1).setLight(light)
                        .setNormal(pose, 0, 1, 0);
                consumer.addVertex(pose, x3, z0, 0F)
                        .setColor(255, 255, 255, alpha)
                        .setUv(1F, 0F).setLight(light)
                        .setNormal(pose, 0, 1, 0);
                consumer.addVertex(pose, x2, z0, 0F)
                        .setColor(255, 255, 255, alpha)
                        .setUv(u2, 0F).setLight(light)
                        .setNormal(pose, 0, 1, 0);
                consumer.addVertex(pose, x2, z0, y2)
                        .setColor(255, 255, 255, alpha)
                        .setUv(u2, u2).setLight(light)
                        .setNormal(pose, 0, 1, 0);
                consumer.addVertex(pose, x3, z0, y2)
                        .setColor(255, 255, 255, alpha)
                        .setUv(1F, u2).setLight(light)
                        .setNormal(pose, 0, 1, 0);
                consumer.addVertex(pose, x3, z0, y1)
                        .setColor(255, 255, 255, alpha)
                        .setUv(1F, u1).setLight(light)
                        .setNormal(pose, 0, 1, 0);
                consumer.addVertex(pose, x2, z0, y1)
                        .setColor(255, 255, 255, alpha)
                        .setUv(u2, u1).setLight(light)
                        .setNormal(pose, 0, 1, 0);
                consumer.addVertex(pose, x2, z0, y3)
                        .setColor(255, 255, 255, alpha)
                        .setUv(u2, 1F).setLight(light)
                        .setNormal(pose, 0, 1, 0);
                consumer.addVertex(pose, x3, z0, y3)
                        .setColor(255, 255, 255, alpha)
                        .setUv(1F, 1F).setLight(light)
                        .setNormal(pose, 0, 1, 0);
                consumer.addVertex(pose, x3, z0, y2)
                        .setColor(255, 255, 255, alpha)
                        .setUv(1F, u2).setLight(light)
                        .setNormal(pose, 0, 1, 0);
                consumer.addVertex(pose, x2, z0, y2)
                        .setColor(255, 255, 255, alpha)
                        .setUv(u2, u2).setLight(light)
                        .setNormal(pose, 0, 1, 0);
            } else {
                consumer.addVertex(pose, 0F, z0, 0F)
                        .setColor(255, 255, 255, alpha)
                        .setUv(0F, 0F).setLight(light)
                        .setNormal(pose, 0, -1, 0);
                consumer.addVertex(pose, x1, z0, 0F)
                        .setColor(255, 255, 255, alpha)
                        .setUv(u1, 0F).setLight(light)
                        .setNormal(pose, 0, -1, 0);
                consumer.addVertex(pose, x1, z0, y1)
                        .setColor(255, 255, 255, alpha)
                        .setUv(u1, u1).setLight(light)
                        .setNormal(pose, 0, -1, 0);
                consumer.addVertex(pose, 0F, z0, y1)
                        .setColor(255, 255, 255, alpha)
                        .setUv(0F, u1).setLight(light)
                        .setNormal(pose, 0, -1, 0);
                consumer.addVertex(pose, 0F, z0, y1)
                        .setColor(255, 255, 255, alpha)
                        .setUv(0F, u1).setLight(light)
                        .setNormal(pose, 0, -1, 0);
                consumer.addVertex(pose, x1, z0, y1)
                        .setColor(255, 255, 255, alpha)
                        .setUv(u1, u1).setLight(light)
                        .setNormal(pose, 0, -1, 0);
                consumer.addVertex(pose, x1, z0, y2)
                        .setColor(255, 255, 255, alpha)
                        .setUv(u1, u2).setLight(light)
                        .setNormal(pose, 0, -1, 0);
                consumer.addVertex(pose, 0F, z0, y2)
                        .setColor(255, 255, 255, alpha)
                        .setUv(0F, u2).setLight(light)
                        .setNormal(pose, 0, -1, 0);
                consumer.addVertex(pose, 0F, z0, y2)
                        .setColor(255, 255, 255, alpha)
                        .setUv(0F, u2).setLight(light)
                        .setNormal(pose, 0, -1, 0);
                consumer.addVertex(pose, x1, z0, y2)
                        .setColor(255, 255, 255, alpha)
                        .setUv(u1, u2).setLight(light)
                        .setNormal(pose, 0, -1, 0);
                consumer.addVertex(pose, x1, z0, y3)
                        .setColor(255, 255, 255, alpha)
                        .setUv(u1, 1F).setLight(light)
                        .setNormal(pose, 0, -1, 0);
                consumer.addVertex(pose, 0F, z0, y3)
                        .setColor(255, 255, 255, alpha)
                        .setUv(0F, 1F).setLight(light)
                        .setNormal(pose, 0, -1, 0);
                consumer.addVertex(pose, x1, z0, 0F)
                        .setColor(255, 255, 255, alpha)
                        .setUv(u1, 0F).setLight(light)
                        .setNormal(pose, 0, -1, 0);
                consumer.addVertex(pose, x2, z0, 0F)
                        .setColor(255, 255, 255, alpha)
                        .setUv(u2, 0F).setLight(light)
                        .setNormal(pose, 0, -1, 0);
                consumer.addVertex(pose, x2, z0, y1)
                        .setColor(255, 255, 255, alpha)
                        .setUv(u2, u1).setLight(light)
                        .setNormal(pose, 0, -1, 0);
                consumer.addVertex(pose, x1, z0, y1)
                        .setColor(255, 255, 255, alpha)
                        .setUv(u1, u1).setLight(light)
                        .setNormal(pose, 0, -1, 0);
                consumer.addVertex(pose, x1, z0, y1)
                        .setColor(255, 255, 255, alpha)
                        .setUv(u1, u1).setLight(light)
                        .setNormal(pose, 0, -1, 0);
                consumer.addVertex(pose, x2, z0, y1)
                        .setColor(255, 255, 255, alpha)
                        .setUv(u2, u1).setLight(light)
                        .setNormal(pose, 0, -1, 0);
                consumer.addVertex(pose, x2, z0, y2)
                        .setColor(255, 255, 255, alpha)
                        .setUv(u2, u2).setLight(light)
                        .setNormal(pose, 0, -1, 0);
                consumer.addVertex(pose, x1, z0, y2)
                        .setColor(255, 255, 255, alpha)
                        .setUv(u1, u2).setLight(light)
                        .setNormal(pose, 0, -1, 0);
                consumer.addVertex(pose, x1, z0, y2)
                        .setColor(255, 255, 255, alpha)
                        .setUv(u1, u2).setLight(light)
                        .setNormal(pose, 0, -1, 0);
                consumer.addVertex(pose, x2, z0, y2)
                        .setColor(255, 255, 255, alpha)
                        .setUv(u2, u2).setLight(light)
                        .setNormal(pose, 0, -1, 0);
                consumer.addVertex(pose, x2, z0, y3)
                        .setColor(255, 255, 255, alpha)
                        .setUv(u2, 1F).setLight(light)
                        .setNormal(pose, 0, -1, 0);
                consumer.addVertex(pose, x1, z0, y3)
                        .setColor(255, 255, 255, alpha)
                        .setUv(u1, 1F).setLight(light)
                        .setNormal(pose, 0, -1, 0);
                consumer.addVertex(pose, x2, z0, 0F)
                        .setColor(255, 255, 255, alpha)
                        .setUv(u2, 0F).setLight(light)
                        .setNormal(pose, 0, -1, 0);
                consumer.addVertex(pose, x3, z0, 0F)
                        .setColor(255, 255, 255, alpha)
                        .setUv(1F, 0F).setLight(light)
                        .setNormal(pose, 0, -1, 0);
                consumer.addVertex(pose, x3, z0, y1)
                        .setColor(255, 255, 255, alpha)
                        .setUv(1F, u1).setLight(light)
                        .setNormal(pose, 0, -1, 0);
                consumer.addVertex(pose, x2, z0, y1)
                        .setColor(255, 255, 255, alpha)
                        .setUv(u2, u1).setLight(light)
                        .setNormal(pose, 0, -1, 0);
                consumer.addVertex(pose, x2, z0, y1)
                        .setColor(255, 255, 255, alpha)
                        .setUv(u2, u1).setLight(light)
                        .setNormal(pose, 0, -1, 0);
                consumer.addVertex(pose, x3, z0, y1)
                        .setColor(255, 255, 255, alpha)
                        .setUv(1F, u1).setLight(light)
                        .setNormal(pose, 0, -1, 0);
                consumer.addVertex(pose, x3, z0, y2)
                        .setColor(255, 255, 255, alpha)
                        .setUv(1F, u2).setLight(light)
                        .setNormal(pose, 0, -1, 0);
                consumer.addVertex(pose, x2, z0, y2)
                        .setColor(255, 255, 255, alpha)
                        .setUv(u2, u2).setLight(light)
                        .setNormal(pose, 0, -1, 0);
                consumer.addVertex(pose, x2, z0, y2)
                        .setColor(255, 255, 255, alpha)
                        .setUv(u2, u2).setLight(light)
                        .setNormal(pose, 0, -1, 0);
                consumer.addVertex(pose, x3, z0, y2)
                        .setColor(255, 255, 255, alpha)
                        .setUv(1F, u2).setLight(light)
                        .setNormal(pose, 0, -1, 0);
                consumer.addVertex(pose, x3, z0, y3)
                        .setColor(255, 255, 255, alpha)
                        .setUv(1F, 1F).setLight(light)
                        .setNormal(pose, 0, -1, 0);
                consumer.addVertex(pose, x2, z0, y3)
                        .setColor(255, 255, 255, alpha)
                        .setUv(u2, 1F).setLight(light)
                        .setNormal(pose, 0, -1, 0);
            }
        }
    }
}
