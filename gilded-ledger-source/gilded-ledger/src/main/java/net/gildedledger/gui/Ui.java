package net.gildedledger.gui;

import net.minecraft.ChatFormatting;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.ItemLore;
import net.minecraft.world.item.component.ResolvableProfile;

import java.util.ArrayList;
import java.util.List;

/** Visual vocabulary: charcoal filler, gold accents, gray secondary text, green/red states. */
final class Ui {
	private Ui() {}

	static Component t(String s, ChatFormatting f) {
		return Component.literal(s).withStyle(f).withStyle(st -> st.withItalic(false));
	}

	static Component gray(String s) { return t(s, ChatFormatting.GRAY); }
	static Component white(String s) { return t(s, ChatFormatting.WHITE); }
	static Component gold(String s) { return t(s, ChatFormatting.GOLD); }
	static Component green(String s) { return t(s, ChatFormatting.GREEN); }
	static Component red(String s) { return t(s, ChatFormatting.RED); }

	static ItemStack filler() {
		ItemStack s = new ItemStack(Items.GRAY_STAINED_GLASS_PANE);
		s.set(DataComponents.CUSTOM_NAME, Component.literal(" "));
		return s;
	}

	static ItemStack icon(Item item, Component name, Component... lore) {
		ItemStack s = new ItemStack(item);
		s.set(DataComponents.CUSTOM_NAME, name);
		if (lore.length > 0) s.set(DataComponents.LORE, new ItemLore(List.of(lore)));
		return s;
	}

	static ItemStack head(ServerPlayer p, Component name, Component... lore) {
		ItemStack s = icon(Items.PLAYER_HEAD, name, lore);
		s.set(DataComponents.PROFILE, ResolvableProfile.createResolved(p.getGameProfile()));
		return s;
	}

	/** Copy of a real item (real icon + vanilla tooltip: enchantments, durability) with extra lore lines appended. */
	static ItemStack show(ItemStack template, int shownCount, Component... extra) {
		ItemStack s = template.copyWithCount(Math.max(1, Math.min(shownCount, template.getMaxStackSize())));
		List<Component> lines = new ArrayList<>(s.getOrDefault(DataComponents.LORE, ItemLore.EMPTY).lines());
		for (Component c : extra) lines.add(c);
		s.set(DataComponents.LORE, new ItemLore(lines));
		return s;
	}
}
