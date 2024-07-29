package org.teacon.slides.admin;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.exceptions.DynamicCommandExceptionType;
import com.mojang.brigadier.exceptions.SimpleCommandExceptionType;
import com.mojang.datafixers.util.Either;
import net.minecraft.ChatFormatting;
import net.minecraft.FieldsAreNonnullByDefault;
import net.minecraft.MethodsReturnNonnullByDefault;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ComponentUtils;
import net.minecraft.network.chat.HoverEvent;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.neoforge.network.PacketDistributor;
import org.apache.commons.lang3.StringUtils;
import org.teacon.slides.SlideShow;
import org.teacon.slides.network.SlideURLPrefetchPacket;
import org.teacon.slides.url.ProjectorURL;
import org.teacon.slides.url.ProjectorURLArgument;
import org.teacon.slides.url.ProjectorURLPatternArgument;
import org.teacon.slides.url.ProjectorURLSavedData;
import org.teacon.urlpattern.URLPattern;

import javax.annotation.ParametersAreNonnullByDefault;
import java.util.*;
import java.util.function.Function;

import static net.minecraft.commands.Commands.argument;
import static net.minecraft.commands.Commands.literal;

@FieldsAreNonnullByDefault
@MethodsReturnNonnullByDefault
@ParametersAreNonnullByDefault
@EventBusSubscriber(bus = EventBusSubscriber.Bus.GAME)
public final class SlideCommand {
    private static final DynamicCommandExceptionType URL_NOT_EXIST = new DynamicCommandExceptionType(v -> Component.translatable("command.slide_show.failed.url_not_exist", v));

    private static final SimpleCommandExceptionType PERM_NOT_EXIST = new SimpleCommandExceptionType(Component.translatable("command.slide_show.failed.perm_not_exist").withStyle(ChatFormatting.RED));

    @SubscribeEvent
    public static void onCommandsRegister(RegisterCommandsEvent event) {
        var node = event.getDispatcher().register(command(SlideShow.ID.replace('_', '-')));
        event.getDispatcher().register(literal(StringUtils.substringBefore(SlideShow.ID, '_')).redirect(node));
    }

    private static LiteralArgumentBuilder<CommandSourceStack> command(String name) {
        return literal(name)
                .then(literal("list")
                        .then(argument("pattern", new ProjectorURLPatternArgument())
                                .executes(context -> list(context.getSource(),
                                        ProjectorURLPatternArgument.getUrl(context, "pattern"),
                                        ProjectorURLSavedData.get(context.getSource().getServer()))))
                        .executes(context -> list(context.getSource(),
                                new URLPattern(Map.of(URLPattern.ComponentType.PROTOCOL, "http(s?)")),
                                ProjectorURLSavedData.get(context.getSource().getServer()))))
                .then(literal("prefetch")
                        .then(argument("url", new ProjectorURLArgument())
                                .executes(context -> prefetch(context.getSource(),
                                        ProjectorURLArgument.getUrl(context, "url"),
                                        ProjectorURLSavedData.get(context.getSource().getServer())))))
                .then(literal("block")
                        .then(argument("url", new ProjectorURLArgument())
                                .executes(context -> block(context.getSource(),
                                        ProjectorURLArgument.getUrl(context, "url"),
                                        ProjectorURLSavedData.get(context.getSource().getServer())))))
                .then(literal("unblock")
                        .then(argument("url", new ProjectorURLArgument())
                                .executes(context -> unblock(context.getSource(),
                                        ProjectorURLArgument.getUrl(context, "url"),
                                        ProjectorURLSavedData.get(context.getSource().getServer())))));
    }

    private static int prefetch(CommandSourceStack source,
                                Either<UUID, ProjectorURL> urlArgument,
                                ProjectorURLSavedData data) throws CommandSyntaxException {
        var urlOptional = urlArgument.map(data::getUrlById, Optional::of);
        if (urlOptional.isPresent()) {
            var url = urlOptional.get();
            var uuidOptional = data.getIdByUrl(url);
            if (uuidOptional.isPresent() || SlidePermission.canInteractCreateUrl(source.source)) {
                var uuid = uuidOptional.orElseGet(() -> data.getOrCreateIdByCommand(url, source));
                PacketDistributor.sendToAllPlayers(new SlideURLPrefetchPacket(Set.of(uuid), data));
                var msg = Component.translatable("command.slide_show.prefetch_projector_url.success", toText(uuid, url));
                source.sendSuccess(() -> msg.withStyle(ChatFormatting.GREEN), true);
                return Command.SINGLE_SUCCESS;
            }
        }
        throw URL_NOT_EXIST.create(urlArgument.map(SlideCommand::toText, SlideCommand::toText));
    }

    private static int list(CommandSourceStack source,
                            URLPattern urlPatternArgument,
                            ProjectorURLSavedData data) throws CommandSyntaxException {
        if (SlidePermission.canListUrl(source.source)) {
            var limit = 20;
            var matchResults = data.getUrlMatchResults(urlPatternArgument, limit);
            var components = Arrays.asList(matchResults.value().keySet().stream().flatMap(
                    id -> data.getUrlById(id).map(u -> toText(id, u)).stream()).toArray(Component[]::new));
            var matchCount = matchResults.keyInt();
            if (matchCount > limit) {
                components.set(limit - 1, Component.literal("...").withStyle(ChatFormatting.GRAY));
            }
            var component = ComponentUtils.formatList(components, Function.identity());
            var msg = Component.translatable("command.slide_show.list_projector_url.success", matchCount, component);
            source.sendSuccess(() -> msg.withStyle(ChatFormatting.GREEN), true);
            return Command.SINGLE_SUCCESS;
        }
        throw PERM_NOT_EXIST.create();
    }

    private static int block(CommandSourceStack source,
                             Either<UUID, ProjectorURL> urlArgument,
                             ProjectorURLSavedData data) throws CommandSyntaxException {
        if (SlidePermission.canBlockUrl(source.source)) {
            var pairOptional = toPairOpt(data, urlArgument);
            if (pairOptional.isPresent()) {
                var pair = pairOptional.get();
                var text = toText(pair.getKey(), pair.getValue());
                if (data.setBlockedStatusByCommand(pair.getKey(), pair.getValue(), source, true)) {
                    var msg = Component.translatable("command.slide_show.block_projector_url.success", text);
                    source.sendSuccess(() -> msg.withStyle(ChatFormatting.GREEN), true);
                    return Command.SINGLE_SUCCESS;
                }
            }
            throw URL_NOT_EXIST.create(urlArgument.map(SlideCommand::toText, SlideCommand::toText));
        }
        throw PERM_NOT_EXIST.create();
    }

    private static int unblock(CommandSourceStack source,
                               Either<UUID, ProjectorURL> urlArgument,
                               ProjectorURLSavedData data) throws CommandSyntaxException {
        if (SlidePermission.canUnblockUrl(source.source)) {
            var pairOptional = toPairOpt(data, urlArgument);
            if (pairOptional.isPresent()) {
                var pair = pairOptional.get();
                var text = toText(pair.getKey(), pair.getValue());
                if (data.setBlockedStatusByCommand(pair.getKey(), pair.getValue(), source, false)) {
                    var msg = Component.translatable("command.slide_show.unblock_projector_url.success", text);
                    source.sendSuccess(() -> msg.withStyle(ChatFormatting.GREEN), true);
                    return Command.SINGLE_SUCCESS;
                }
            }
            throw URL_NOT_EXIST.create(urlArgument.map(SlideCommand::toText, SlideCommand::toText));
        }
        throw PERM_NOT_EXIST.create();
    }

    private static Optional<Map.Entry<UUID, ProjectorURL>> toPairOpt(ProjectorURLSavedData data,
                                                                     Either<UUID, ProjectorURL> arg) {
        return arg.map(
                id -> data.getUrlById(id).map(url -> Map.entry(id, url)),
                url -> data.getIdByUrl(url).map(id -> Map.entry(id, url)));
    }

    private static Component toText(UUID id, ProjectorURL url) {
        var click = new ClickEvent(ClickEvent.Action.OPEN_URL, url.toUrl().toString());
        var text = StringUtils.abbreviate(StringUtils.substringAfter(url.toString(), "://"), 15);
        var hover = new HoverEvent(HoverEvent.Action.SHOW_TEXT, Component.literal("UUID:\n" + id + "\n\nURL:\n" + url.toUrl()));
        return Component.literal(text).withStyle(s -> s.withColor(ChatFormatting.AQUA).withHoverEvent(hover).withClickEvent(click));
    }

    private static Component toText(ProjectorURL url) {
        var click = new ClickEvent(ClickEvent.Action.OPEN_URL, url.toUrl().toString());
        var text = StringUtils.abbreviate(StringUtils.substringAfter(url.toString(), "://"), 15);
        var hover = new HoverEvent(HoverEvent.Action.SHOW_TEXT, Component.literal("URL:\n" + url.toUrl()));
        return Component.literal(text).withStyle(s -> s.withColor(ChatFormatting.AQUA).withHoverEvent(hover).withClickEvent(click));
    }

    private static Component toText(UUID id) {
        var text = StringUtils.abbreviate(id.toString(), 15);
        var hover = new HoverEvent(HoverEvent.Action.SHOW_TEXT, Component.literal("UUID:\n" + id));
        return Component.literal(text).withStyle(s -> s.withColor(ChatFormatting.AQUA).withHoverEvent(hover));
    }
}
