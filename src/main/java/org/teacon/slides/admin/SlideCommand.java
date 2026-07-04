package org.teacon.slides.admin;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.exceptions.DynamicCommandExceptionType;
import com.mojang.brigadier.exceptions.SimpleCommandExceptionType;
import com.mojang.datafixers.util.Either;
import com.mojang.logging.annotations.FieldsAreNonnullByDefault;
import com.mojang.logging.annotations.MethodsReturnNonnullByDefault;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.arguments.coordinates.BlockPosArgument;
import net.minecraft.core.BlockPos;
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
import org.teacon.slides.block.ProjectorBlockEntity;
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
@EventBusSubscriber(modid = SlideShow.ID)
public final class SlideCommand {
    private static final DynamicCommandExceptionType URL_NOT_EXIST = new DynamicCommandExceptionType(v -> Component.translatable("command.slide_show.failed.url_not_exist", v));

    private static final SimpleCommandExceptionType PERM_NOT_EXIST = new SimpleCommandExceptionType(Component.translatable("command.slide_show.failed.perm_not_exist").withStyle(ChatFormatting.RED));

    private static final DynamicCommandExceptionType URL_NOT_ALLOWED = new DynamicCommandExceptionType(v -> Component.translatable("command.slide_show.failed.url_not_allowed", v));

    private static final SimpleCommandExceptionType ALLOW_RULE_NOT_EXIST = new SimpleCommandExceptionType(Component.translatable("command.slide_show.failed.allow_rule_not_exist").withStyle(ChatFormatting.RED));

    private static final SimpleCommandExceptionType ALLOW_RULE_ALREADY_EXIST = new SimpleCommandExceptionType(Component.translatable("command.slide_show.failed.allow_rule_already_exist").withStyle(ChatFormatting.RED));

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
                                        ProjectorURLPatternArgument.getInput(context, "pattern").getValue(),
                                        ProjectorURLSavedData.get(context.getSource().getServer()))))
                        .executes(context -> list(context.getSource(),
                                new URLPattern(Map.of(URLPattern.ComponentType.PROTOCOL, "http(s?)")),
                                ProjectorURLSavedData.get(context.getSource().getServer()))))
                .then(literal("scroll")
                        .then(argument("pos", BlockPosArgument.blockPos())
                                .then(literal("up")
                                        .then(argument("count", IntegerArgumentType.integer(1))
                                                .executes(context -> scrollUp(context.getSource(),
                                                        IntegerArgumentType.getInteger(context, "count"),
                                                        BlockPosArgument.getLoadedBlockPos(context, "pos"))))
                                        .executes(context -> scrollUp(context.getSource(), 1,
                                                BlockPosArgument.getLoadedBlockPos(context, "pos"))))
                                .then(literal("down")
                                        .then(argument("count", IntegerArgumentType.integer(1))
                                                .executes(context -> scrollDown(context.getSource(),
                                                        IntegerArgumentType.getInteger(context, "count"),
                                                        BlockPosArgument.getLoadedBlockPos(context, "pos"))))
                                        .executes(context -> scrollDown(context.getSource(), 1,
                                                BlockPosArgument.getLoadedBlockPos(context, "pos"))))
                                .then(literal("current")
                                        .executes(context -> scrollCurrent(context.getSource(),
                                                BlockPosArgument.getLoadedBlockPos(context, "pos"))))
                                .then(literal("amount")
                                        .executes(context -> scrollAmount(context.getSource(),
                                                BlockPosArgument.getLoadedBlockPos(context, "pos"))))))
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
                                        ProjectorURLSavedData.get(context.getSource().getServer())))))
                .then(literal("allow")
                        .then(argument("pattern", new ProjectorURLPatternArgument())
                                .executes(context -> allow(context.getSource(),
                                        ProjectorURLPatternArgument.getInput(context, "pattern"),
                                        ProjectorURLSavedData.get(context.getSource().getServer()))))
                        .then(literal("list")
                                .executes(context -> listAllowRules(context.getSource(),
                                        ProjectorURLSavedData.get(context.getSource().getServer())))))
                .then(literal("unallow")
                        .then(argument("pattern", new ProjectorURLPatternArgument())
                                .executes(context -> unallow(context.getSource(),
                                        ProjectorURLPatternArgument.getInput(context, "pattern"),
                                        ProjectorURLSavedData.get(context.getSource().getServer())))));
    }

    private static int scrollUp(CommandSourceStack source, int count, BlockPos pos) {
        var blockEntity = source.getLevel().getBlockEntity(pos);
        var moveCount = blockEntity instanceof ProjectorBlockEntity projector ? -projector.moveSlideItems(-count) : 0;
        if (moveCount > 0) {
            var msg = Component.translatable("command.slide_show.scroll_up.success", moveCount);
            source.sendSuccess(() -> msg.withStyle(ChatFormatting.GREEN), true);
        } else {
            var msg = Component.translatable("command.slide_show.scroll_up.not_enough");
            source.sendSuccess(() -> msg, true);
        }
        return moveCount;
    }

    private static int scrollDown(CommandSourceStack source, int count, BlockPos pos) {
        var blockEntity = source.getLevel().getBlockEntity(pos);
        var moveCount = blockEntity instanceof ProjectorBlockEntity projector ? projector.moveSlideItems(count) : 0;
        if (moveCount > 0) {
            var msg = Component.translatable("command.slide_show.scroll_down.success", moveCount);
            source.sendSuccess(() -> msg.withStyle(ChatFormatting.GREEN), true);
        } else {
            var msg = Component.translatable("command.slide_show.scroll_down.not_enough");
            source.sendSuccess(() -> msg, true);
        }
        return moveCount;
    }

    private static int scrollCurrent(CommandSourceStack source, BlockPos pos) {
        var blockEntity = source.getLevel().getBlockEntity(pos);
        var current = blockEntity instanceof ProjectorBlockEntity projector ? projector.getItemsDisplayedCount() : 0;
        var msg = Component.translatable("command.slide_show.scroll_current.success", current);
        source.sendSuccess(() -> msg.withStyle(ChatFormatting.GREEN), true);
        return current;
    }

    private static int scrollAmount(CommandSourceStack source, BlockPos pos) {
        var blockEntity = source.getLevel().getBlockEntity(pos);
        var current = blockEntity instanceof ProjectorBlockEntity projector ? projector.getItemsDisplayedCount() : 0;
        var waiting = blockEntity instanceof ProjectorBlockEntity projector ? projector.getItemsToDisplayCount() : 0;
        var msg = Component.translatable("command.slide_show.scroll_amount.success", current + waiting);
        source.sendSuccess(() -> msg.withStyle(ChatFormatting.GREEN), true);
        return current + waiting;
    }

    private static int prefetch(CommandSourceStack source,
                                Either<UUID, ProjectorURL> urlArgument,
                                ProjectorURLSavedData data) throws CommandSyntaxException {
        var urlOptional = urlArgument.map(data::getUrlById, Optional::of);
        if (urlOptional.isEmpty()) {
            throw URL_NOT_EXIST.create(urlArgument.map(SlideCommand::toText, SlideCommand::toText));
        }
        var url = urlOptional.get();
        var uuidOptional = data.getIdByUrl(url);
        if (uuidOptional.isEmpty() && SlidePermission.canInteractCreateUrl(source)) {
            uuidOptional = data.getOrCreateIdByCommand(url, source);
        }
        if (uuidOptional.isEmpty()) {
            throw URL_NOT_ALLOWED.create(toText(url));
        }
        var uuid = uuidOptional.get();
        PacketDistributor.sendToAllPlayers(new SlideURLPrefetchPacket(Set.of(uuid), data));
        var msg = Component.translatable("command.slide_show.prefetch_projector_url.success", toText(uuid, url));
        source.sendSuccess(() -> msg.withStyle(ChatFormatting.GREEN), true);
        return Command.SINGLE_SUCCESS;
    }

    private static int list(CommandSourceStack source,
                            URLPattern urlPatternArgument,
                            ProjectorURLSavedData data) throws CommandSyntaxException {
        if (SlidePermission.canListUrl(source)) {
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
        if (SlidePermission.canBlockUrl(source)) {
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
        if (SlidePermission.canUnblockUrl(source)) {
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
        var click = new ClickEvent.OpenUrl(url.toUrl());
        var text = StringUtils.abbreviate(StringUtils.substringAfter(url.toString(), "://"), 15);
        var hover = new HoverEvent.ShowText(Component.literal("UUID:\n" + id + "\n\nURL:\n" + url.toUrl()));
        return Component.literal(text).withStyle(s -> s.withColor(ChatFormatting.AQUA).withHoverEvent(hover).withClickEvent(click));
    }

    private static Component toText(ProjectorURL url) {
        var click = new ClickEvent.OpenUrl(url.toUrl());
        var text = StringUtils.abbreviate(StringUtils.substringAfter(url.toString(), "://"), 15);
        var hover = new HoverEvent.ShowText(Component.literal("URL:\n" + url.toUrl()));
        return Component.literal(text).withStyle(s -> s.withColor(ChatFormatting.AQUA).withHoverEvent(hover).withClickEvent(click));
    }

    private static Component toText(UUID id) {
        var text = StringUtils.abbreviate(id.toString(), 15);
        var hover = new HoverEvent.ShowText(Component.literal("UUID:\n" + id));
        return Component.literal(text).withStyle(s -> s.withColor(ChatFormatting.AQUA).withHoverEvent(hover));
    }

    private static int allow(CommandSourceStack source,
                             Map.Entry<String, URLPattern> input,
                             ProjectorURLSavedData data) throws CommandSyntaxException {
        if (SlidePermission.canBlockUrl(source)) {
            if (data.addAllowRule(input.getValue(), input.getKey())) {
                var msg = Component.translatable("command.slide_show.allow_projector_url.success", Component.literal(input.getKey()).withStyle(ChatFormatting.AQUA));
                source.sendSuccess(() -> msg.withStyle(ChatFormatting.GREEN), true);
                return Command.SINGLE_SUCCESS;
            }
            throw ALLOW_RULE_ALREADY_EXIST.create();
        }
        throw PERM_NOT_EXIST.create();
    }

    private static int unallow(CommandSourceStack source,
                               Map.Entry<String, URLPattern> input,
                               ProjectorURLSavedData data) throws CommandSyntaxException {
        if (SlidePermission.canUnblockUrl(source)) {
            if (data.removeAllowRule(input.getValue())) {
                var msg = Component.translatable("command.slide_show.unallow_projector_url.success", Component.literal(input.getKey()).withStyle(ChatFormatting.AQUA));
                source.sendSuccess(() -> msg.withStyle(ChatFormatting.GREEN), true);
                if (data.getAllowPatterns().isEmpty()) {
                    source.sendSuccess(() -> Component.translatable("command.slide_show.unallow_projector_url.warn_empty").withStyle(ChatFormatting.YELLOW), false);
                }
                return Command.SINGLE_SUCCESS;
            }
            throw ALLOW_RULE_NOT_EXIST.create();
        }
        throw PERM_NOT_EXIST.create();
    }

    private static int listAllowRules(CommandSourceStack source,
                                      ProjectorURLSavedData data) throws CommandSyntaxException {
        if (SlidePermission.canBlockUrl(source)) {
            var rules = data.getAllowPatterns();
            var components = rules.stream().map(raw -> Component.literal(raw).withStyle(ChatFormatting.AQUA)).toArray(Component[]::new);
            var component = ComponentUtils.formatList(Arrays.asList(components), Function.identity());
            var msg = Component.translatable("command.slide_show.list_allow_projector_url.success", rules.size(), component);
            source.sendSuccess(() -> msg.withStyle(ChatFormatting.GREEN), true);
            return Command.SINGLE_SUCCESS;
        }
        throw PERM_NOT_EXIST.create();
    }
}
