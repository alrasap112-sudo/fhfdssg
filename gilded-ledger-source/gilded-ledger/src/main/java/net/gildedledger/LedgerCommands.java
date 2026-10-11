package net.gildedledger;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.LongArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.gildedledger.gui.DashboardPage;
import net.gildedledger.gui.HistoryPage;
import net.gildedledger.gui.OffersPage;
import net.gildedledger.gui.PlayersPage;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

/** /cash ... Admin subcommands require game-master permission; ordinary players can never mint money. */
public final class LedgerCommands {
	private LedgerCommands() {}

	private static LedgerContext ctx(CommandContext<CommandSourceStack> c) {
		LedgerContext x = GildedLedger.ctx();
		if (x == null) c.getSource().sendFailure(Component.literal("The economy is not ready yet."));
		return x;
	}

	public static void register(CommandDispatcher<CommandSourceStack> d) {
		d.register(Commands.literal("cash")
			.executes(c -> {
				LedgerContext x = ctx(c); if (x == null) return 0;
				new DashboardPage(x, c.getSource().getPlayerOrException()).open();
				return 1;
			})
			.then(Commands.literal("help").executes(c -> {
				c.getSource().sendSuccess(() -> Msg.info("/cash  /cash balance  /cash history  /cash offers  /cash trade <player>  /cash wallet"), false);
				return 1;
			}))
			.then(Commands.literal("balance").executes(LedgerCommands::balance))
			.then(Commands.literal("history").executes(c -> {
				LedgerContext x = ctx(c); if (x == null) return 0;
				new HistoryPage(x, c.getSource().getPlayerOrException()).open();
				return 1;
			}))
			.then(Commands.literal("offers").executes(c -> {
				LedgerContext x = ctx(c); if (x == null) return 0;
				new OffersPage(x, c.getSource().getPlayerOrException()).open();
				return 1;
			}))
			.then(Commands.literal("wallet").executes(c -> {
				LedgerContext x = ctx(c); if (x == null) return 0;
				ServerPlayer p = c.getSource().getPlayerOrException();
				if (WalletItem.has(p.getInventory())) { p.sendSystemMessage(Msg.info("You already have a Cash Wallet.")); return 0; }
				if (!p.getInventory().add(WalletItem.create(x.cfg().useCustomItemModel))) { p.sendSystemMessage(Msg.err("Your inventory is full.")); return 0; }
				p.sendSystemMessage(Msg.ok("Cash Wallet added to your inventory."));
				return 1;
			}))
			.then(Commands.literal("trade").then(Commands.argument("player", EntityArgument.player()).executes(c -> {
				LedgerContext x = ctx(c); if (x == null) return 0;
				ServerPlayer me = c.getSource().getPlayerOrException();
				ServerPlayer other = EntityArgument.getPlayer(c, "player");
				if (other == me) { me.sendSystemMessage(Msg.err("You can't trade with yourself.")); return 0; }
				new net.gildedledger.gui.BrowsePage(x, me, other).open();
				return 1;
			})))
			.then(Commands.literal("offer")
				.then(Commands.literal("accept").then(Commands.argument("id", StringArgumentType.word()).executes(c -> resolve(c, 0))))
				.then(Commands.literal("reject").then(Commands.argument("id", StringArgumentType.word()).executes(c -> resolve(c, 1))))
				.then(Commands.literal("cancel").then(Commands.argument("id", StringArgumentType.word()).executes(c -> resolve(c, 2)))))
			.then(Commands.literal("admin").requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))
				.then(Commands.literal("balance").then(Commands.argument("player", EntityArgument.player()).executes(c -> {
					LedgerContext x = ctx(c); if (x == null) return 0;
					ServerPlayer t = EntityArgument.getPlayer(c, "player");
					c.getSource().sendSuccess(() -> Msg.info(t.getName().getString() + ": " + Money.format(x.store().balance(t.getUUID()))), false);
					return 1;
				})))
				.then(Commands.literal("set").then(Commands.argument("player", EntityArgument.player())
					.then(Commands.argument("amount", LongArgumentType.longArg(0, Money.MAX)).executes(c -> adjust(c, false)))))
				.then(Commands.literal("add").then(Commands.argument("player", EntityArgument.player())
					.then(Commands.argument("amount", LongArgumentType.longArg(0, Money.MAX)).executes(c -> adjust(c, true)))))));

		d.register(Commands.literal("bal").executes(LedgerCommands::balance));
	}

	private static int balance(CommandContext<CommandSourceStack> c) throws CommandSyntaxException {
		LedgerContext x = ctx(c); if (x == null) return 0;
		ServerPlayer p = c.getSource().getPlayerOrException();
		p.sendSystemMessage(Msg.info("Balance: " + Money.format(x.store().balance(p.getUUID()))));
		return 1;
	}

	private static int resolve(CommandContext<CommandSourceStack> c, int mode) throws CommandSyntaxException {
		LedgerContext x = ctx(c); if (x == null) return 0;
		ServerPlayer p = c.getSource().getPlayerOrException();
		String id = StringArgumentType.getString(c, "id");
		TradeManager.Result r = switch (mode) {
			case 0 -> x.trades().accept(p, id);
			case 1 -> x.trades().reject(p, id);
			default -> x.trades().cancel(p, id);
		};
		p.sendSystemMessage(r.ok() ? Msg.ok(r.message()) : Msg.err(r.message()));
		return r.ok() ? 1 : 0;
	}

	private static int adjust(CommandContext<CommandSourceStack> c, boolean add) throws CommandSyntaxException {
		LedgerContext x = ctx(c); if (x == null) return 0;
		ServerPlayer t = EntityArgument.getPlayer(c, "player");
		long amt = LongArgumentType.getLong(c, "amount");
		long cur = x.store().balance(t.getUUID());
		long next = add ? cur + amt : amt; // both operands <= MAX so no long overflow
		if (!x.store().setBalance(t.getUUID(), next)) {
			c.getSource().sendFailure(Component.literal("Invalid balance (must be 0.." + Money.MAX + ")."));
			return 0;
		}
		x.store().saveQuietly();
		GildedLedger.LOG.info("ADMIN {} set {} balance {} -> {}", c.getSource().getTextName(), t.getName().getString(), cur, next);
		c.getSource().sendSuccess(() -> Msg.ok(t.getName().getString() + " now has " + Money.format(next)), true);
		t.sendSystemMessage(Msg.info("Your balance is now " + Money.format(next)));
		return 1;
	}
}
