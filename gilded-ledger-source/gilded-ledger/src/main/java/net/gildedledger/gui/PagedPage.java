package net.gildedledger.gui;

import net.gildedledger.LedgerContext;
import net.minecraft.world.item.Items;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;

/** 45-slot paged list with a navigation bar: Back (45), Prev (48), Refresh/page (49), Next (50), Close (53). */
abstract class PagedPage<T> extends Page {
	private static final int PER = 45;
	protected List<T> items = new ArrayList<>();
	protected int pageIndex = 0;

	protected PagedPage(LedgerContext ctx, ServerPlayer p) { super(ctx, p); }

	protected abstract List<T> load();
	protected abstract ItemStack icon(T t);
	protected abstract void onSelect(T t, GuiMenu.Click c);
	protected abstract Page parent();
	protected String emptyText() { return "Nothing here yet."; }
	protected void footer() { }

	@Override
	protected final void build() {
		items = load();
		int pages = Math.max(1, (items.size() + PER - 1) / PER);
		pageIndex = Math.max(0, Math.min(pageIndex, pages - 1));
		for (int i = 0; i < PER; i++) {
			int idx = pageIndex * PER + i;
			if (idx >= items.size()) break;
			T t = items.get(idx);
			button(i, icon(t), c -> onSelect(t, c));
		}
		if (items.isEmpty()) put(22, Ui.icon(Items.STRUCTURE_VOID, Ui.gray(emptyText())));
		button(45, Ui.icon(Items.ARROW, Ui.white("Back")), c -> navigate(parent()));
		if (pageIndex > 0) button(48, Ui.icon(Items.SPECTRAL_ARROW, Ui.gold("Previous page")), c -> { pageIndex--; refresh(); });
		button(49, Ui.icon(Items.CLOCK, Ui.gold("Page " + (pageIndex + 1) + "/" + pages), Ui.gray("Click to refresh")), c -> refresh());
		if (pageIndex < pages - 1) button(50, Ui.icon(Items.SPECTRAL_ARROW, Ui.gold("Next page")), c -> { pageIndex++; refresh(); });
		button(53, Ui.icon(Items.BARRIER, Ui.red("Close")), c -> closeMenu());
		footer();
	}
}
