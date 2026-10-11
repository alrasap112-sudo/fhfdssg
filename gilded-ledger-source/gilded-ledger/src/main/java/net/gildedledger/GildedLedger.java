package net.gildedledger;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.entity.event.v1.ServerPlayerEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.event.player.UseEntityCallback;
import net.fabricmc.fabric.api.event.player.UseItemCallback;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.loader.api.FabricLoader;
import net.gildedledger.gui.BrowsePage;
import net.gildedledger.gui.DashboardPage;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.level.storage.LevelResource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Path;

/**
 * Gilded Ledger: server-authoritative cash economy + player-to-player trading.
 * Works on dedicated servers, integrated servers and single player. One jar for server and client
 * (assets only; no client code). Vanilla clients can join if useCustomItemModel=false.
 */
public final class GildedLedger implements ModInitializer {
	public static final String MOD_ID = "gildedledger";
	public static final Logger LOG = LoggerFactory.getLogger(MOD_ID);

	private static LedgerConfig config;
	private static LedgerContext ctx;
	private int tick;

	public static LedgerContext ctx() { return ctx; }

	@Override
	public void onInitialize() {
		config = LedgerConfig.load(FabricLoader.getInstance().getConfigDir().resolve("gildedledger.json"), LOG);

		ServerLifecycleEvents.SERVER_STARTING.register(server -> {
			Path dir = server.getWorldPath(LevelResource.ROOT).resolve(MOD_ID);
			EconomyStore store = EconomyStore.load(dir.resolve("economy.json"), config.startingBalance, config.maxHistoryEntries, LOG);
			ctx = new LedgerContext(server, config, store, new TradeManager(server, store, config));
			LOG.info("Gilded Ledger ready (starting balance {}).", Money.format(config.startingBalance));
		});
		ServerLifecycleEvents.SERVER_STOPPING.register(server -> {
			if (ctx != null) {
				try { ctx.store().save(); } catch (IOException e) { LOG.error("Final economy save failed", e); }
				ctx = null;
			}
		});

		ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> {
			if (ctx == null) return;
			ServerPlayer p = handler.getPlayer();
			boolean created = ctx.store().ensureAccount(p.getUUID(), p.getName().getString());
			if (created) {
				ctx.store().saveQuietly();
				LOG.info("Created account for {} with {}", p.getName().getString(), Money.format(config.startingBalance));
				p.sendSystemMessage(Msg.ok("Welcome! You start with " + Money.format(config.startingBalance) + "."));
				if (config.giveWalletOnFirstJoin) giveWallet(p);
			}
		});
		ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> {
			if (ctx != null) ctx.trades().onDisconnect(handler.getPlayer().getUUID());
		});
		ServerPlayerEvents.AFTER_RESPAWN.register((oldPlayer, newPlayer, alive) -> {
			// Balance is account-based and untouched by death; just make sure they still have the shortcut item.
			if (ctx != null && config.giveWalletOnRespawn && !WalletItem.has(newPlayer.getInventory())) giveWallet(newPlayer);
		});
		ServerTickEvents.END_SERVER_TICK.register(server -> {
			if (ctx != null && ++tick % 20 == 0) ctx.trades().tickExpiry();
		});

		UseItemCallback.EVENT.register((player, level, hand) -> {
			if (!(player instanceof ServerPlayer sp) || ctx == null) return InteractionResult.PASS;
			if (!WalletItem.is(sp.getItemInHand(hand))) return InteractionResult.PASS;
			new DashboardPage(ctx, sp).open();
			return InteractionResult.SUCCESS;
		});
		UseEntityCallback.EVENT.register((player, level, hand, entity, hit) -> {
			if (!(player instanceof ServerPlayer me) || !(entity instanceof ServerPlayer other) || ctx == null) return InteractionResult.PASS;
			if (!WalletItem.is(me.getItemInHand(hand))) return InteractionResult.PASS;
			if (!config.tradingEnabled) { me.sendSystemMessage(Msg.err("Trading is disabled on this server.")); return InteractionResult.SUCCESS; }
			new BrowsePage(ctx, me, other).open();
			return InteractionResult.SUCCESS;
		});

		CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> LedgerCommands.register(dispatcher));
	}

	private static void giveWallet(ServerPlayer p) {
		if (!p.getInventory().add(WalletItem.create(config.useCustomItemModel)))
			p.sendSystemMessage(Msg.info("Your inventory is full - use /cash wallet later to get your Cash Wallet."));
	}
}
