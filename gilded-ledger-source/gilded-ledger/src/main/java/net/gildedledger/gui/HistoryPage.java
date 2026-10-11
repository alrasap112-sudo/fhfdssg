package net.gildedledger.gui;

import net.gildedledger.EconomyStore.TradeRecord;
import net.gildedledger.LedgerContext;
import net.gildedledger.Money;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

/** Only the viewer's own records. Filter button cycles All / Bought / Sold / Completed / Not completed. */
public final class HistoryPage extends PagedPage<TradeRecord> {
	private enum Filter { ALL, BOUGHT, SOLD, COMPLETED, OTHER }

	private static final DateTimeFormatter FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm").withZone(ZoneOffset.UTC);
	private Filter filter = Filter.ALL;

	public HistoryPage(LedgerContext ctx, ServerPlayer p) { super(ctx, p); }

	@Override protected Component title() { return Ui.gold("History - " + filter.name().toLowerCase()); }
	@Override protected String emptyText() { return "No transactions match."; }
	@Override protected Page parent() { return new DashboardPage(ctx, player); }

	private boolean bought(TradeRecord r) { return r.buyerId.equals(player.getUUID().toString()); }

	@Override
	protected List<TradeRecord> load() {
		List<TradeRecord> out = new ArrayList<>();
		for (TradeRecord r : ctx.store().history(player.getUUID())) {
			boolean done = "COMPLETED".equals(r.outcome);
			boolean keep = switch (filter) {
				case ALL -> true;
				case BOUGHT -> bought(r);
				case SOLD -> !bought(r);
				case COMPLETED -> done;
				case OTHER -> !done;
			};
			if (keep) out.add(r);
		}
		return out;
	}

	@Override
	protected ItemStack icon(TradeRecord r) {
		boolean done = "COMPLETED".equals(r.outcome);
		Item it = done ? Items.EMERALD : "FAILED".equals(r.outcome) ? Items.REDSTONE : Items.GUNPOWDER;
		String other = bought(r) ? r.sellerName : r.buyerName;
		return Ui.icon(it, (done ? Ui.green(r.outcome) : Ui.red(r.outcome)),
			Ui.white((bought(r) ? "Bought " : "Sold ") + r.quantity + "x " + r.itemName),
			Ui.gray((bought(r) ? "From " : "To ") + other), Ui.gold(Money.format(r.price)),
			Ui.gray(FMT.format(Instant.ofEpochMilli(r.time)) + " UTC"), Ui.gray("ID: " + r.id));
	}

	@Override protected void onSelect(TradeRecord r, GuiMenu.Click c) { }

	@Override
	protected void footer() {
		button(47, Ui.icon(Items.HOPPER, Ui.gold("Filter: " + filter.name().toLowerCase()), Ui.gray("Click to change")), c -> {
			filter = Filter.values()[(filter.ordinal() + 1) % Filter.values().length];
			pageIndex = 0;
			refresh();
		});
	}
}
