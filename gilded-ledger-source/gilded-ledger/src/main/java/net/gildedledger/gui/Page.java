package net.gildedledger.gui;

import net.gildedledger.LedgerContext;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.item.ItemStack;

import java.util.HashMap;
import java.util.Map;
import java.util.function.Consumer;

/** One screen. The container is mutated in place on refresh, so the menu never flickers or reopens. */
public abstract class Page {
	protected final LedgerContext ctx;
	protected final ServerPlayer player;
	protected final SimpleContainer box = new SimpleContainer(54);
	protected final Map<Integer, Consumer<GuiMenu.Click>> actions = new HashMap<>();

	protected Page(LedgerContext ctx, ServerPlayer player) {
		this.ctx = ctx;
		this.player = player;
	}

	protected abstract Component title();
	protected abstract void build();

	public final void open() {
		refresh();
		player.openMenu(new SimpleMenuProvider((id, inv, p) -> new GuiMenu(id, inv, box, actions), title()));
	}

	protected final void refresh() {
		box.clearContent();
		actions.clear();
		for (int i = 0; i < 54; i++) box.setItem(i, Ui.filler());
		build();
	}

	protected final void put(int slot, ItemStack s) { box.setItem(slot, s); }

	protected final void button(int slot, ItemStack s, Consumer<GuiMenu.Click> a) {
		box.setItem(slot, s);
		actions.put(slot, a);
	}

	/** Defers to the next tick so we never swap menus in the middle of a click packet. */
	protected final void navigate(Page next) { ctx.server().execute(next::open); }

	protected final void closeMenu() { ctx.server().execute(player::closeContainer); }

	protected final void say(Component c) { player.sendSystemMessage(c); }
}
