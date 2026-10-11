# Test report — honest status
**Build: NOT RUN.** No network access to maven.fabricmc.net / Mojang / Gradle and Java 21 only (Java 25 required).
**Unit tests written, NOT RUN:** `EconomyStoreTest` (start balance once, conservation, overdraft/negative/overflow refused,
persistence, backup recovery, corrupt-file refusal, bounded history, money formatting).
**Runtime acceptance tests (A–D, purchase, reject, funds, stale inventory, full inventory, duplicate accept, competing buyers,
disconnect, GUI scales): ALL UNTESTED.** The logic is written to satisfy each (see README "Safety design").

## Highest-risk API guesses for 26.3 (fix first if the build fails)
1. `Commands.hasPermission(Commands.LEVEL_GAMEMASTERS)` (permission API changed in 1.21.11+).
2. `ResolvableProfile.createResolved(player.getGameProfile())` (player-head skins).
3. `Identifier.fromNamespaceAndPath`, `DataComponents.ITEM_MODEL` / `CUSTOM_DATA` / `PROFILE`, `CustomData.contains`.
4. `ChestMenu` constructor + `clicked(int,int,ClickType,Player)` override; `sendAllDataToRemote()`.
5. `ClickEvent.RunCommand` / `HoverEvent.ShowText`; `ServerPlayer.playNotifySound`, `displayClientMessage`.
6. Fabric API callbacks returning `InteractionResult` (UseItemCallback/UseEntityCallback).
