package net.gildedledger;

import net.minecraft.ChatFormatting;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.component.ItemLore;

import java.util.List;

/**
 * The Cash Wallet is only a shortcut item (a tagged paper). It stores NO money, so it can never be a duplication
 * vector or hold a stale balance. Registering no new item keeps the mod joinable by vanilla clients.
 */
public final class WalletItem {
	private static final String KEY = "gildedledger_wallet";

	private WalletItem() {}

	public static ItemStack create(boolean customModel) {
		ItemStack s = new ItemStack(Items.PAPER);
		s.set(DataComponents.CUSTOM_NAME, Component.literal("Cash Wallet")
			.withStyle(ChatFormatting.GOLD).withStyle(st -> st.withItalic(false).withBold(true)));
		s.set(DataComponents.LORE, new ItemLore(List.of(
			line("Your economy wallet."),
			line("Right-click: open the Ledger."),
			line("Right-click a player: start a trade."),
			line("Money lives on the server; it is never lost on death."))));
		CompoundTag tag = new CompoundTag();
		tag.putBoolean(KEY, true);
		s.set(DataComponents.CUSTOM_DATA, CustomData.of(tag));
		s.set(DataComponents.MAX_STACK_SIZE, 1);
		if (customModel) s.set(DataComponents.ITEM_MODEL, Identifier.fromNamespaceAndPath(GildedLedger.MOD_ID, "wallet"));
		return s;
	}

	private static Component line(String t) {
		return Component.literal(t).withStyle(ChatFormatting.GRAY).withStyle(st -> st.withItalic(false));
	}

	public static boolean is(ItemStack s) {
		if (s.isEmpty() || !s.is(Items.PAPER)) return false;
		CustomData d = s.get(DataComponents.CUSTOM_DATA);
		return d != null && d.contains(KEY);
	}

	public static boolean has(Inventory inv) {
		for (int i = 0; i < InventoryOps.SIZE; i++) if (is(inv.getItem(i))) return true;
		return false;
	}
}
