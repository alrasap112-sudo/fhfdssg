# Gilded Ledger  (Minecraft 26.3 · Fabric)

Persistent **$5,000** cash economy plus secure player-to-player item trading.

**Target:** Minecraft Java **26.3** (released 2026-09-15) · Fabric Loader **0.19.5+** · Fabric API **0.161.0+26.3** · **Java 25** · Loom 1.17 · Gradle 9.6.0.

## STATUS — read this first
The source is complete, but **it has NOT been compiled or run.** The sandbox that wrote it could not reach
Fabric/Mojang/Gradle hosts and only had Java 21, so no JAR exists yet and **nothing is tested** (see TEST_REPORT.md).
Build it in one step with the included GitHub Actions workflow (below) or locally with Java 25.

## Build
- **GitHub (no setup):** push this folder to a GitHub repo → Actions tab → "build" → download artifact `gilded-ledger-jar`.
- **Local:** install Java 25, then `./gradlew build` → `build/libs/gilded-ledger-1.0.0.jar`.
If compilation reports an error, send me the log; the likeliest spots are listed in TEST_REPORT.md.

## Install
Put the one JAR in `mods/` on the **server and every client** (plus Fabric API). Same file for both.
Single player works as-is. To let *unmodded* clients join, set `"useCustomItemModel": false` (wallet then looks like paper).

## Using it
- You start with $5,000 once; balances are stored by UUID on the server (`<world>/gildedledger/economy.json`) and survive death, relogs, restarts.
- You get a **Cash Wallet** on first join (`/cash wallet` for another). It holds no money, so it can't be duplicated or lost with money.
- **Right-click** the wallet → Ledger menu. **Right-click a player** with the wallet → browse their items.
- Pick an item → set quantity (±1/±8/Max) and total price (±$1…$1000, shift-click ×10) → Submit → Confirm.
- The seller gets a chat message with clickable **[Accept] [Reject] [Details]**, or uses `/cash offers`
  (left-click accept, right-click reject). Offers expire (default 60 s). Nothing is charged until acceptance.

Commands: `/cash`, `/cash balance` (`/bal`), `/cash history`, `/cash offers`, `/cash trade <player>`, `/cash wallet`, `/cash help`,
admin (op): `/cash admin balance|set|add <player> [amount]`.

## Safety design
Server-thread only; the offer is removed from the pending map before processing (no double accept); everything is
revalidated at accept (online, funds, items, buyer space, overflow); inventories are snapshotted and fully restored if
anything fails; balances + history live in ONE file replaced atomically with a `.bak`. Unreadable data refuses to start
instead of resetting balances. **Limit:** player inventory files and our economy file are saved separately by Minecraft,
so a hard crash in the instant of a trade can't be made perfectly atomic.

## Not implemented (honest list)
Custom client-rendered GUI/skins-in-3D, HUD balance overlay and keybinds (would need client rendering code against
26.3's changing render API; replaced by server-side chest menus + commands, which also work with vanilla clients).
Per-item reservation across competing offers (instead, first accept wins and the rest fail revalidation safely).
