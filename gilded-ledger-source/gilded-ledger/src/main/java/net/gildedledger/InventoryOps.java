package net.gildedledger;

import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;

/** Server-side inventory helpers over the 36 main slots (hotbar + storage). Armor/offhand are never traded. */
public final class InventoryOps {
	public static final int SIZE = 36;

	private InventoryOps() {}

	public static final class Entry {
		public final ItemStack template; // count 1, full components
		public int total;
		Entry(ItemStack template, int total) { this.template = template; this.total = total; }
	}

	private static boolean same(ItemStack a, ItemStack b) {
		return !a.isEmpty() && !b.isEmpty() && ItemStack.isSameItemSameComponents(a, b);
	}

	public static int count(Inventory inv, ItemStack tpl) {
		long n = 0;
		for (int i = 0; i < SIZE; i++) {
			ItemStack s = inv.getItem(i);
			if (same(s, tpl)) n += s.getCount();
		}
		return (int) Math.min(n, Integer.MAX_VALUE);
	}

	public static List<Entry> aggregate(Inventory inv) {
		List<Entry> out = new ArrayList<>();
		for (int i = 0; i < SIZE; i++) {
			ItemStack s = inv.getItem(i);
			if (s.isEmpty() || WalletItem.is(s)) continue;
			Entry hit = null;
			for (Entry e : out) if (same(e.template, s)) { hit = e; break; }
			if (hit != null) hit.total += s.getCount();
			else out.add(new Entry(s.copyWithCount(1), s.getCount()));
		}
		return out;
	}

	public static ItemStack[] snapshot(Inventory inv) {
		ItemStack[] a = new ItemStack[SIZE];
		for (int i = 0; i < SIZE; i++) a[i] = inv.getItem(i).copy();
		return a;
	}

	public static void restore(Inventory inv, ItemStack[] snap) {
		for (int i = 0; i < SIZE; i++) inv.setItem(i, snap[i].copy());
	}

	public static boolean remove(Inventory inv, ItemStack tpl, int qty) {
		if (qty < 1 || count(inv, tpl) < qty) return false;
		int left = qty;
		for (int i = 0; i < SIZE && left > 0; i++) {
			ItemStack s = inv.getItem(i);
			if (!same(s, tpl)) continue;
			int take = Math.min(left, s.getCount());
			s.shrink(take);
			if (s.isEmpty()) inv.setItem(i, ItemStack.EMPTY);
			left -= take;
		}
		return left == 0;
	}

	public static boolean canFit(Inventory inv, ItemStack tpl, int qty) {
		int max = Math.max(1, tpl.getMaxStackSize());
		long left = qty;
		for (int i = 0; i < SIZE && left > 0; i++) {
			ItemStack s = inv.getItem(i);
			if (s.isEmpty()) left -= max;
			else if (same(s, tpl)) left -= Math.max(0, max - s.getCount());
		}
		return left <= 0;
	}

	/** Adds exactly qty items or nothing (returns false without modifying when it would not fit). */
	public static boolean add(Inventory inv, ItemStack tpl, int qty) {
		if (qty < 1 || !canFit(inv, tpl, qty)) return false;
		int max = Math.max(1, tpl.getMaxStackSize());
		int left = qty;
		for (int i = 0; i < SIZE && left > 0; i++) {
			ItemStack s = inv.getItem(i);
			if (same(s, tpl) && s.getCount() < max) {
				int put = Math.min(left, max - s.getCount());
				s.grow(put);
				left -= put;
			}
		}
		for (int i = 0; i < SIZE && left > 0; i++) {
			if (inv.getItem(i).isEmpty()) {
				int put = Math.min(left, max);
				inv.setItem(i, tpl.copyWithCount(put));
				left -= put;
			}
		}
		return left == 0;
	}
}
