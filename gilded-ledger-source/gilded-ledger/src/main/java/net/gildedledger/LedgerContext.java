package net.gildedledger;

import net.minecraft.server.MinecraftServer;

/** Per-server runtime state. */
public record LedgerContext(MinecraftServer server, LedgerConfig cfg, EconomyStore store, TradeManager trades) { }
