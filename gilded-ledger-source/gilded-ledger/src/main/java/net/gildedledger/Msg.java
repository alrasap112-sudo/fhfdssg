package net.gildedledger;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.chat.MutableComponent;

/** Chat message helpers (gold accent, readable success/error colors). */
public final class Msg {
	private Msg() {}

	public static MutableComponent prefix() {
		return Component.literal("[Ledger] ").withStyle(ChatFormatting.GOLD);
	}

	public static Component info(String s) { return prefix().append(Component.literal(s).withStyle(ChatFormatting.WHITE)); }
	public static Component ok(String s) { return prefix().append(Component.literal(s).withStyle(ChatFormatting.GREEN)); }
	public static Component err(String s) { return prefix().append(Component.literal(s).withStyle(ChatFormatting.RED)); }

	public static Component button(String label, ChatFormatting color, String command, String hover) {
		return Component.literal("[" + label + "]").withStyle(st -> st
			.withColor(color).withBold(true)
			.withClickEvent(new ClickEvent.RunCommand(command))
			.withHoverEvent(new HoverEvent.ShowText(Component.literal(hover))));
	}
}
