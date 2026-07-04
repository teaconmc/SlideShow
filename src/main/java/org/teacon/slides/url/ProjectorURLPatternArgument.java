package org.teacon.slides.url;

import com.mojang.brigadier.StringReader;
import com.mojang.brigadier.arguments.ArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.exceptions.DynamicCommandExceptionType;
import com.mojang.brigadier.suggestion.Suggestions;
import com.mojang.brigadier.suggestion.SuggestionsBuilder;
import com.mojang.logging.annotations.FieldsAreNonnullByDefault;
import com.mojang.logging.annotations.MethodsReturnNonnullByDefault;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.synchronization.SingletonArgumentInfo;
import net.minecraft.network.chat.Component;
import org.teacon.urlpattern.URLPattern;

import javax.annotation.ParametersAreNonnullByDefault;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

import static net.minecraft.commands.synchronization.ArgumentTypeInfos.registerByClass;

@FieldsAreNonnullByDefault
@MethodsReturnNonnullByDefault
@ParametersAreNonnullByDefault
public final class ProjectorURLPatternArgument implements ArgumentType<Map.Entry<String, URLPattern>> {
    private static final DynamicCommandExceptionType INVALID_URL_PATTERN = new DynamicCommandExceptionType(v -> Component.translatable("argument.slide_show.projector_url_pattern.error", v));

    public static SingletonArgumentInfo<ProjectorURLPatternArgument> create() {
        return registerByClass(ProjectorURLPatternArgument.class, SingletonArgumentInfo.contextFree(ProjectorURLPatternArgument::new));
    }

    @SuppressWarnings("unchecked")
    public static Map.Entry<String, URLPattern> getInput(CommandContext<CommandSourceStack> context, String argument) {
        return context.getArgument(argument, Map.Entry.class);
    }

    @Override
    public Map.Entry<String, URLPattern> parse(StringReader reader) throws CommandSyntaxException {
        var sb = new StringBuilder(reader.getRemainingLength());
        while (reader.canRead() && reader.peek() != ' ') {
            sb.append(reader.read());
        }
        var string = sb.toString();
        try {
            return Map.entry(string, new URLPattern(string));
        } catch (IllegalArgumentException e) {
            throw INVALID_URL_PATTERN.create(string);
        }
    }

    @Override
    public <S> CompletableFuture<Suggestions> listSuggestions(CommandContext<S> context, SuggestionsBuilder builder) {
        // TODO: fetch urls
        return ArgumentType.super.listSuggestions(context, builder);
    }
}
