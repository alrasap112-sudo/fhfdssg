# Configuration — `config/gildedledger.json` (auto-created, validated, never resets balances)
| Key | Default | Meaning |
|---|---|---|
| startingBalance | 5000 | Granted once per UUID |
| tradingEnabled | true | Master switch |
| requireProximity | true | Same dimension + within maxTradeDistance when sending an offer |
| maxTradeDistance | 16.0 | Blocks |
| offerExpirySeconds | 60 | 5–3600 |
| maxHistoryEntries | 50 | Per player (1–1000) |
| giveWalletOnFirstJoin / giveWalletOnRespawn | true | Auto-give Cash Wallet |
| useCustomItemModel | true | false = vanilla clients can join (wallet shows as paper) |
| maxPrice | 1000000000 | Per offer |
| maxPendingOffersPerPlayer | 5 | Anti-spam |
Malformed file → kept as `.broken-<time>`, defaults used. Economy data: `<world>/gildedledger/economy.json` (+ `.bak`).
Disconnect policy: pending offers involving a player who leaves are cancelled; nothing moves.
