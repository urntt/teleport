package com.urntt.teleport.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.mojang.brigadier.CommandDispatcher;
import com.urntt.teleport.TeleportClient;
import com.urntt.teleport.command.TeleportCommands;
import com.urntt.teleport.teleport.SavedLocation;
import java.util.Set;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.client.multiplayer.ClientSuggestionProvider;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.protocol.game.ClientboundCommandsPacket;
import net.minecraft.network.protocol.game.ClientboundGameEventPacket;
import net.minecraft.network.protocol.game.ClientboundPlayerCombatKillPacket;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.PositionMoveRotation;
import net.minecraft.world.entity.Relative;
import org.jspecify.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Hooks the client's packet listener:
 * <ul>
 * <li>{@code /tp @c} and {@code /teleport @c}, typed or clicked, run on the client instead of being sent to the server.
 * Every other command is sent as in vanilla.</li>
 * <li>The mod's command nodes join the command tree the server sends, for suggestions and syntax highlighting.</li>
 * <li>Server teleports, deaths and invalid respawn blocks are recorded for {@code back} and {@code spawn}.</li>
 * </ul>
 */
@Mixin(ClientPacketListener.class)
public abstract class ClientPacketListenerMixin {
	private static final String ENSURE_RUNNING_ON_SAME_THREAD =
			"Lnet/minecraft/network/protocol/PacketUtils;ensureRunningOnSameThread(Lnet/minecraft/network/protocol/Packet;"
					+ "Lnet/minecraft/network/PacketListener;Lnet/minecraft/network/PacketProcessor;)V";

	@Shadow
	private CommandDispatcher<ClientSuggestionProvider> commands;

	@Inject(method = "sendCommand", at = @At("HEAD"), cancellable = true)
	private void teleport$runTypedCommand(final String command, final CallbackInfo ci) {
		if (TeleportCommands.handles(command)) {
			TeleportCommands.execute(command);
			ci.cancel();
		}
	}

	@Inject(method = "sendUnattendedCommand", at = @At("HEAD"), cancellable = true)
	private void teleport$runClickedCommand(final String command, final @Nullable Screen screenAfterCommand, final CallbackInfo ci) {
		if (TeleportCommands.handles(command)) {
			// Vanilla returns to this screen after sending a clicked command it has no concerns about.
			Minecraft.getInstance().gui.setScreen(screenAfterCommand);
			TeleportCommands.execute(command);
			ci.cancel();
		}
	}

	@Inject(method = "handleCommands", at = @At("TAIL"))
	private void teleport$addCommandNodes(final ClientboundCommandsPacket packet, final CallbackInfo ci) {
		TeleportCommands.addTo(this.commands);
	}

	@WrapOperation(
			method = "handleMovePlayer",
			at = @At(
					value = "INVOKE",
					target = "Lnet/minecraft/client/multiplayer/ClientPacketListener;setValuesFromPositionPacket(Lnet/minecraft/world/entity/PositionMoveRotation;Ljava/util/Set;Lnet/minecraft/world/entity/Entity;Z)Z"))
	private boolean teleport$recordServerTeleport(final PositionMoveRotation change, final Set<Relative> relatives,
			final Entity entity, final boolean interpolate, final Operation<Boolean> original) {
		SavedLocation before = SavedLocation.of(entity);
		boolean result = original.call(change, relatives, entity, interpolate);
		if (entity instanceof LocalPlayer player) {
			TeleportClient.tracker().onServerPosition(player, before, entity.position());
		}
		return result;
	}

	@Inject(method = "handlePlayerCombatKill", at = @At(value = "INVOKE", target = ENSURE_RUNNING_ON_SAME_THREAD, shift = At.Shift.AFTER))
	private void teleport$recordDeath(final ClientboundPlayerCombatKillPacket packet, final CallbackInfo ci) {
		LocalPlayer player = Minecraft.getInstance().player;
		if (player != null && packet.playerId() == player.getId()) {
			TeleportClient.tracker().onDeath(player);
		}
	}

	@Inject(method = "handleGameEvent", at = @At(value = "INVOKE", target = ENSURE_RUNNING_ON_SAME_THREAD, shift = At.Shift.AFTER))
	private void teleport$recordInvalidRespawnBlock(final ClientboundGameEventPacket packet, final CallbackInfo ci) {
		if (packet.getEvent() == ClientboundGameEventPacket.NO_RESPAWN_BLOCK_AVAILABLE) {
			TeleportClient.tracker().onNoRespawnBlock();
		}
	}
}
