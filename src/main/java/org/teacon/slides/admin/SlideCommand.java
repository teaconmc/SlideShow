package org.teacon.slides.admin;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.exceptions.DynamicCommandExceptionType;
import net.minecraft.ChatFormatting;
import net.minecraft.FieldsAreNonnullByDefault;
import net.minecraft.MethodsReturnNonnullByDefault;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.network.chat.Component;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.apache.commons.lang3.StringUtils;
import org.teacon.slides.SlideShow;
import org.teacon.slides.network.ProjectorURLPrefetchPacket;
import org.teacon.slides.url.ProjectorURLArgument;
import org.teacon.slides.url.ProjectorURLSavedData;

import javax.annotation.ParametersAreNonnullByDefault;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static net.minecraft.commands.Commands.argument;
import static net.minecraft.commands.Commands.literal;

@FieldsAreNonnullByDefault
@MethodsReturnNonnullByDefault
@ParametersAreNonnullByDefault
@Mod.EventBusSubscriber(bus = Mod.EventBusSubscriber.Bus.FORGE)
public class SlideCommand {
    private static final DynamicCommandExceptionType URL_NOT_EXIST = new DynamicCommandExceptionType(v -> Component.translatable("command.slide_show.failed.url_not_exist", v));

    @SubscribeEvent
    public static void onCommandsRegister(RegisterCommandsEvent event) {
        var node = event.getDispatcher().register(command(SlideShow.ID.replace('_', '-')));
        event.getDispatcher().register(literal(StringUtils.substringBefore(SlideShow.ID, '_')).redirect(node));
    }

    private static LiteralArgumentBuilder<CommandSourceStack> command(String name) {
        return literal(name)
                .then(literal("prefetch")
                        .then(argument("url", new ProjectorURLArgument())
                                .executes(SlideCommand::prefetchProjectorUrl)))
                .then(literal("block")
                        .then(argument("url", new ProjectorURLArgument())
                                .executes(SlideCommand::blockByProjectorUrl)))
                .then(literal("unblock")
                        .then(argument("url", new ProjectorURLArgument())
                                .executes(SlideCommand::unblockByProjectorUrl)));
    }

    private static int prefetchProjectorUrl(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        // noinspection DuplicatedCode
        var level = ctx.getSource().getLevel();
        var data = ProjectorURLSavedData.get(level);
        var arg = ProjectorURLArgument.getUrl(ctx, "url");
        var urlOptional = arg.map(data::getUrlById, Optional::of);
        if (urlOptional.isPresent()) {
            var url = urlOptional.get();
            var uuid = data.getOrCreateIdByCommand(url, ctx.getSource());
            new ProjectorURLPrefetchPacket(Set.of(uuid), data).sendToAll();
            var msg = Component.translatable("command.slide_show.prefetch_projector_url.success", url, uuid);
            ctx.getSource().sendSuccess(msg.withStyle(ChatFormatting.GREEN), true);
            return Command.SINGLE_SUCCESS;
        }
        throw URL_NOT_EXIST.create(arg.map(UUID::toString, u -> u.toUrl().toString()));
    }

    private static int blockByProjectorUrl(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        // noinspection DuplicatedCode
        var level = ctx.getSource().getLevel();
        var data = ProjectorURLSavedData.get(level);
        var arg = ProjectorURLArgument.getUrl(ctx, "url");
        var urlOptional = arg.map(data::getUrlById, Optional::of);
        var uuidOptional = arg.map(Optional::of, data::getIdByUrl);
        if (urlOptional.isPresent() && uuidOptional.isPresent()) {
            var url = urlOptional.get();
            var uuid = uuidOptional.get();
            if (data.setBlockedStatusByCommand(uuid, url, ctx.getSource(), true)) {
                var msg = Component.translatable("command.slide_show.block_projector_url.success", url, uuid);
                ctx.getSource().sendSuccess(msg.withStyle(ChatFormatting.GREEN), true);
                return Command.SINGLE_SUCCESS;
            }
        }
        throw URL_NOT_EXIST.create(arg.map(UUID::toString, u -> u.toUrl().toString()));
    }

    private static int unblockByProjectorUrl(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        // noinspection DuplicatedCode
        var level = ctx.getSource().getLevel();
        var data = ProjectorURLSavedData.get(level);
        var arg = ProjectorURLArgument.getUrl(ctx, "url");
        var urlOptional = arg.map(data::getUrlById, Optional::of);
        var uuidOptional = arg.map(Optional::of, data::getIdByUrl);
        if (urlOptional.isPresent() && uuidOptional.isPresent()) {
            var url = urlOptional.get();
            var uuid = uuidOptional.get();
            if (data.setBlockedStatusByCommand(uuid, url, ctx.getSource(), false)) {
                var msg = Component.translatable("command.slide_show.unblock_projector_url.success", url, uuid);
                ctx.getSource().sendSuccess(msg.withStyle(ChatFormatting.GREEN), true);
                return Command.SINGLE_SUCCESS;
            }
        }
        throw URL_NOT_EXIST.create(arg.map(UUID::toString, u -> u.toUrl().toString()));
    }
}
