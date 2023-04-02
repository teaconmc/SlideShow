package org.teacon.slides.url;

import com.mojang.brigadier.StringReader;
import com.mojang.brigadier.arguments.ArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.exceptions.DynamicCommandExceptionType;
import com.mojang.brigadier.suggestion.Suggestions;
import com.mojang.brigadier.suggestion.SuggestionsBuilder;
import com.mojang.datafixers.util.Either;
import net.minecraft.FieldsAreNonnullByDefault;
import net.minecraft.MethodsReturnNonnullByDefault;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.synchronization.SingletonArgumentInfo;
import net.minecraft.network.chat.Component;

import javax.annotation.ParametersAreNonnullByDefault;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import static net.minecraft.commands.synchronization.ArgumentTypeInfos.registerByClass;

@FieldsAreNonnullByDefault
@MethodsReturnNonnullByDefault
@ParametersAreNonnullByDefault
public final class ProjectorURLArgument implements ArgumentType<Either<UUID, ProjectorURL>> {
    private static final DynamicCommandExceptionType INVALID_URL = new DynamicCommandExceptionType(v -> Component.translatable("argument.slide_show.projector_url.error", v));

    public static SingletonArgumentInfo<ProjectorURLArgument> create() {
        return registerByClass(ProjectorURLArgument.class, SingletonArgumentInfo.contextFree(ProjectorURLArgument::new));
    }

    @SuppressWarnings("unchecked")
    public static Either<UUID, ProjectorURL> getUrl(CommandContext<CommandSourceStack> context, String argument) {
        return context.getArgument(argument, Either.class);
    }

    @Override
    public Either<UUID, ProjectorURL> parse(StringReader reader) throws CommandSyntaxException {
        var string = reader.readString();
        try {
            return Either.left(UUID.fromString(string));
        } catch (IllegalArgumentException e1) {
            try {
                return Either.right(new ProjectorURL(string));
            } catch (IllegalArgumentException e2) {
                throw INVALID_URL.create(string);
            }
        }
    }

    @Override
    public <S> CompletableFuture<Suggestions> listSuggestions(CommandContext<S> context, SuggestionsBuilder builder) {
        // TODO: fetch urls
        return ArgumentType.super.listSuggestions(context, builder);
    }
}
