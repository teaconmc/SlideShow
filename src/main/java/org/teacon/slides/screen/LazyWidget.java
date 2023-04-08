package org.teacon.slides.screen;

import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.FieldsAreNonnullByDefault;
import net.minecraft.MethodsReturnNonnullByDefault;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.narration.NarratableEntry;
import net.minecraftforge.common.util.NonNullFunction;
import net.minecraftforge.common.util.NonNullSupplier;

import javax.annotation.Nullable;
import javax.annotation.ParametersAreNonnullByDefault;
import java.util.function.Supplier;

@FieldsAreNonnullByDefault
@MethodsReturnNonnullByDefault
@ParametersAreNonnullByDefault
public final class LazyWidget<T extends GuiEventListener & NarratableEntry> implements Supplier<T> {
    private @Nullable T cached;
    private final NonNullSupplier<T> initializer;
    private final NonNullFunction<T, T> refresher;

    private <U> LazyWidget(U init, NonNullFunction<? super T, U> refresher, NonNullFunction<U, ? extends T> supplier) {
        this.refresher = old -> supplier.apply(refresher.apply(old));
        this.initializer = () -> supplier.apply(init);
        this.cached = null;
    }

    public static <T extends GuiEventListener & NarratableEntry, U> LazyWidget<T> of(
            U init, NonNullFunction<? super T, U> refresher, NonNullFunction<U, ? extends T> supplier) {
        return new LazyWidget<>(init, refresher, supplier);
    }

    public T refresh() {
        RenderSystem.assertOnRenderThread();
        var obj = this.cached;
        if (obj == null) {
            obj = this.initializer.get();
        } else {
            obj = this.refresher.apply(obj);
        }
        this.cached = obj;
        return obj;
    }

    @Override
    public T get() {
        RenderSystem.assertOnRenderThread();
        var obj = this.cached;
        if (obj == null) {
            obj = this.initializer.get();
            this.cached = obj;
        }
        return obj;
    }
}
