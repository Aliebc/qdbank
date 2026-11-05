# QDBank 1.0.4

QDBank is a cross-server personal item bank for Paper servers. It requires Paper 1.21.11–26.2 and Java 21.

## What is a bank account?

Every player has their own private bank accounts. A bank account works like a 54-slot Ender Chest: items placed in it belong to that player and can be accessed again from any connected Minecraft server.

Each player automatically receives two accounts:

- `checking`
- `saving`

Players may create additional named accounts. Each account has a separate 54-slot inventory.

The same account may be viewed from multiple connected servers. QDBank uses a central authoritative server to process changes in arrival order, reject conflicting updates, and synchronize every open copy of the inventory.

If the QDBank connection is lost, open bank inventories are closed immediately to protect their contents.

## Player commands

| Command | Description | Permission |
| --- | --- | --- |
| `/qdbank open <account>` | Create a new personal bank account. | `qdbank.command.open` |
| `/qdbank close <account>` | Delete one of your accounts. The default `checking` and `saving` accounts cannot be deleted. | `qdbank.command.close` |
| `/qdbank list` | List all your bank accounts. | `qdbank.command.list` |
| `/qdbank see <account>` | Open an account as a 54-slot bank inventory. | `qdbank.command.see` |
| `/qdbank status` | Show the client version, server version, connection state, and current network latency. | `qdbank.command.status` |

Examples:

```text
/qdbank see checking
/qdbank see saving
/qdbank open building
/qdbank list
```

Account names are suggested automatically when using `/qdbank see`.

## Administrator commands

| Command | Description | Permission |
| --- | --- | --- |
| `/qdbank reload` | Reload the client configuration and reconnect to the QDBank server. | `qdbank.admin.reload` |
| `/qdbank admin open <player> <account>` | Create an account for another player. | `qdbank.admin.open` |
| `/qdbank admin close <player> <account>` | Delete another player's account. | `qdbank.admin.close` |
| `/qdbank admin list <player>` | List another player's accounts. | `qdbank.admin.list` |
| `/qdbank admin see <player> <account>` | Open and manage another player's account. | `qdbank.admin.see` |
| `/qdbank admin lookup <parameters>` | Search the item audit history. | `qdbank.admin.lookup` |
| `/qdbank admin page <number>` | Open another page of the most recent lookup. | `qdbank.admin.lookup` |
| `/qdbank admin rollback <parameters>` | Reverse matching item changes. | `qdbank.admin.rollback` |

Player and account arguments are suggested from the central bank directory. Bank players are identified by their authenticated Minecraft UUIDs.

## Audit lookup

The lookup syntax is inspired by CoreProtect:

```text
/qdbank admin lookup user:<player> action:<action> time:<range> include:<item> exclude:<item>
```

Available parameters:

| Parameter | Description | Examples |
| --- | --- | --- |
| `user:` | Player whose bank history should be searched. | `user:Steve` |
| `action:` | Action filter. The default is `inventory`. | `inventory`, `+inventory`, `-inventory`, `open`, `close`, `create`, `delete`, `rollback` |
| `time:` | How far back to search. The default is `30m`. | `30m`, `1h`, `6h`, `1d`, `7d`, `2w` |
| `include:` | Only show changes involving this item type. | `include:diamond` |
| `exclude:` | Exclude changes involving this item type. | `exclude:cobblestone` |

`+inventory` searches for items added to a bank. `-inventory` searches for items removed from a bank.

Examples:

```text
/qdbank admin lookup user:Steve
/qdbank admin lookup user:Steve action:-inventory time:6h
/qdbank admin lookup user:Steve time:7d include:diamond
/qdbank admin lookup user:Steve action:inventory time:1d exclude:cobblestone
```

Lookup results include the operator, item and amount, absolute timestamp, world, coordinates, and source server ID. Item names are interactive in Minecraft and display the full item tooltip when hovered. Use the clickable previous/next buttons or `/qdbank admin page <number>` to change pages.

## Rollback

Rollback uses the same filters as lookup:

```text
/qdbank admin rollback user:<player> action:<action> time:<range> include:<item> exclude:<item>
```

Examples:

```text
/qdbank admin rollback user:Steve time:30m
/qdbank admin rollback user:Steve action:-inventory time:1h include:diamond
```

Rollback operates on individual audited item changes rather than replacing an entire inventory snapshot. Always run the equivalent lookup first and confirm the affected records before performing a rollback.

## Item storage

QDBank stores each inventory slot independently as `Slot:0` through `Slot:53`. Complete item SNBT is produced and restored with NBT-API, including container contents, equipment components, player heads, custom data, and persistent data containers.

NBT-API is embedded inside `qdbank-client-{version}.jar`; server owners do not need to install it separately.

## Building

Run:

```bash
./build.sh
```

The final deployment files are placed together in `dist/` (`{version}` is the Maven project version, currently `1.0.4`):

- `dist/qdbank-server-{version}.jar`
- `dist/qdbank-client-{version}.jar`
- configuration examples
- deployment notes

You can also build the Maven reactor directly:

```bash
mvn clean package
```

## Basic deployment

1. Place `qdbank-server-{version}.jar` in a standalone directory and run it with `java -jar qdbank-server-{version}.jar`.
2. Edit the generated server `config.yml`, set a strong shared secret, and configure every allowed client ID.
3. Place `qdbank-client-{version}.jar` in the `plugins` directory of every connected Paper server.
4. Edit each client configuration so its server address, port, client ID, and secret match the central server.
5. Restart Paper and verify the connection with `/qdbank status`.

Restrict the QDBank TCP port to trusted Minecraft hosts with a firewall. Back up SQLite while the service is stopped or with a SQLite-aware online backup tool.

Copyright © 2026 QDBank · mc100.top. All rights reserved.
