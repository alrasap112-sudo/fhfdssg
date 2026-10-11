package net.gildedledger.gui;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.ChestMenu;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.ItemStack;

import java.util.Map;
import java.util.function.Consumer;

/** A 6-row chest menu that is display-only: every click is intercepted, nothing can be moved, taken or dropped. */
public final class GuiMenu extends ChestMenu {
	public record Click(boolean right, boolean shift) { }

	private final Map<Integer, Consumer<Click>> actions;

	GuiMenu(int id, Inventory inv, Container box, Map<Integer, Consumer<Click>> actions) {
		super(MenuType.GENERIC_9x6, id, inv, box, 6);
		this.actions = actions;
	}

	@Override
	public void clicked(int slotIndex, int button, ContainerInput type, Player player) {
		if (slotIndex >= 0 && slotIndex < 54 && (type == ContainerInput.PICKUP || type == ContainerInput.QUICK_MOVE)) {
			Consumer<Click> a = actions.get(slotIndex);
			if (a != null) a.accept(new Click(type == ContainerInput.PICKUP && button == 1, type == ContainerInput.QUICK_MOVE));
		}
		if (player instanceof ServerPlayer && player.containerMenu == this) {
			setCarried(ItemStack.EMPTY);
			sendAllDataToRemote(); // undo any client-side prediction
		}
	}

	@Override
	public ItemStack quickMoveStack(Player player, int index) { return ItemStack.EMPTY; }
}
