package org.teacon.slides;

import com.mojang.datafixers.util.Either;
import com.mojang.logging.annotations.FieldsAreNonnullByDefault;
import com.mojang.logging.annotations.MethodsReturnNonnullByDefault;
import net.minecraft.resources.Identifier;
import net.neoforged.fml.common.Mod;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.teacon.slides.block.ProjectorBlockEntity;
import org.teacon.slides.item.SlideItem;
import org.teacon.slides.url.ProjectorURL;

import javax.annotation.ParametersAreNonnullByDefault;
import java.util.*;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.function.Function;

@Mod(SlideShow.ID)
@FieldsAreNonnullByDefault
@MethodsReturnNonnullByDefault
@ParametersAreNonnullByDefault
public final class SlideShow {
    public static final String ID = "slide_show"; // as well as the namespace
    public static final Logger LOGGER = LogManager.getLogger("SlideShow");

    private static volatile Consumer<ProjectorBlockEntity> requestUrlPrefetch = Objects::hash;
    private static volatile BiConsumer<Set<UUID>, Map<UUID, ProjectorURL>> applyPrefetch = Objects::hash;
    private static volatile Function<SlideItem.Entry, SequencedCollection<String>> fetchRecommends = e -> List.of();
    private static volatile Function<Either<UUID, ProjectorURL>, ProjectorURL.Status> checkBlock = url -> ProjectorURL.Status.UNKNOWN;

    public static void setRequestUrlPrefetch(Consumer<ProjectorBlockEntity> requestUrlPrefetch) {
        SlideShow.requestUrlPrefetch = requestUrlPrefetch;
    }

    public static void requestUrlPrefetch(ProjectorBlockEntity projector) {
        requestUrlPrefetch.accept(projector);
    }

    public static void setApplyPrefetch(BiConsumer<Set<UUID>, Map<UUID, ProjectorURL>> applyPrefetch) {
        SlideShow.applyPrefetch = applyPrefetch;
    }

    public static void applyPrefetch(Set<UUID> nonExistent, Map<UUID, ProjectorURL> existent) {
        applyPrefetch.accept(nonExistent, existent);
    }

    public static void setFetchRecommends(Function<SlideItem.Entry, SequencedCollection<String>> fetchRecommends) {
        SlideShow.fetchRecommends = fetchRecommends;
    }

    public static SequencedCollection<String> fetchRecommends(SlideItem.Entry entry) {
        return fetchRecommends.apply(entry);
    }

    public static void setCheckBlock(Function<Either<UUID, ProjectorURL>, ProjectorURL.Status> checkBlock) {
        SlideShow.checkBlock = checkBlock;
    }

    public static ProjectorURL.Status checkBlock(UUID uuid) {
        return checkBlock.apply(Either.left(uuid));
    }

    public static ProjectorURL.Status checkBlock(ProjectorURL url) {
        return checkBlock.apply(Either.right(url));
    }

    public static Identifier id(String path) {
        return Identifier.fromNamespaceAndPath(SlideShow.ID, path);
    }
}
