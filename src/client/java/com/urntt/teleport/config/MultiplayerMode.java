package com.urntt.teleport.config;

import com.google.gson.annotations.SerializedName;
import com.mojang.serialization.Codec;
import net.minecraft.network.chat.Component;
import net.minecraft.util.StringRepresentable;

/**
 * Decides on which multiplayer servers the mod may teleport the player. On servers it rules out, Click TP stays
 * off and teleport commands only report that teleporting is disabled.
 */
public enum MultiplayerMode implements StringRepresentable {
	/** Teleporting is never allowed in multiplayer. */
	@SerializedName("disabled")
	DISABLED("disabled"),
	/** Teleporting is allowed only on servers in the server list. */
	@SerializedName("whitelist")
	WHITELIST("whitelist"),
	/** Teleporting is allowed on every server except those in the server list. */
	@SerializedName("blacklist")
	BLACKLIST("blacklist");

	public static final Codec<MultiplayerMode> CODEC = StringRepresentable.fromEnum(MultiplayerMode::values);

	private final String name;

	MultiplayerMode(final String name) {
		this.name = name;
	}

	@Override
	public String getSerializedName() {
		return this.name;
	}

	public Component label() {
		return Component.translatable("options.teleport.multiplayer_mode." + this.name);
	}

	public Component description() {
		return Component.translatable("options.teleport.multiplayer_mode." + this.name + ".description");
	}
}
