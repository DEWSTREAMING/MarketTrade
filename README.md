# Market Trade

A player market for Minecraft servers, in the style of the Steam community market. Players list items at their own price
and buy from each other, and the server keeps a small cut in a pool the owner can pay out. Minecraft 1.21.1, NeoForge.

Market Trade runs on the server. Players without it on their client can still join and trade through chest screens. Players
who install it get a full market screen with search, categories, price history and themes. Both use the same market.

Downloads and the full description are on CurseForge.

## Features
- Listings with full item data (enchantments, names, contents), a per-player limit and an expiry time.
- A wallet backed by any items you choose in config (diamonds by default, or your own coins), or money with no item at all.
- The server cut goes into a tracked pool, with commands to see it and pay it out.
- Nothing is lost: expired, cancelled or removed items go to a Returns page, and items that don't fit are kept there too.
- Moderation: an audit log of every trade and rename, player reports, shop bans and an optional blocked word list.

## Reporting issues
Bugs and ideas go in the [issues](https://github.com/DEWSTREAMING/MarketTrade/issues/new/choose) here. Add a crash report
or log link if the game crashed.

## Fair play
Market Trade is for trading things earned in game. Selling market money, listings, pool payouts or any other advantage
from this mod for real money, directly or indirectly (including on servers or in modpacks that use it), is not permitted.

## Building
Java 21 and NeoForge 21.1.x (ModDevGradle).

```
gradlew build
```

The jar ends up in `build/libs/`. `gradlew runGameTestServer` runs the GameTests.

## License
All Rights Reserved. You may include Market Trade in any modpack, public or private. Please ask before reusing the code.
