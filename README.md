<div align="center">

# Conduit
## **A modern Economy abstraction for Minecraft.**

[![Build](https://img.shields.io/github/actions/workflow/status/alazso/Conduit/ci.yml?branch=main&style=flat-square&label=build)](https://github.com/alazso/Conduit/actions/workflows/ci.yml)
[![Coverage](https://img.shields.io/codecov/c/github/alazso/Conduit?style=flat-square&label=coverage)](https://codecov.io/gh/alazso/Conduit)
[![Code quality](https://img.shields.io/codefactor/grade/github/alazso/Conduit?style=flat-square&label=code%20quality)](https://www.codefactor.io/repository/github/alazso/Conduit)
[![Release](https://img.shields.io/github/v/release/alazso/Conduit?style=flat-square&sort=semver)](https://github.com/alazso/Conduit/releases)
[![License](https://img.shields.io/github/license/alazso/Conduit?style=flat-square)](LICENSE)
[![Java](https://img.shields.io/badge/java-25-orange?style=flat-square)](https://openjdk.org/projects/jdk/25/)
[![Paper](https://img.shields.io/badge/server-Paper%2FFolia%2026.1%2B-blue?style=flat-square)](https://papermc.io/)
[![Stars](https://img.shields.io/github/stars/alazso/Conduit?style=flat-square)](https://github.com/alazso/Conduit/stargazers)

[**Docs**](https://alaz.so/conduit/docs) · [**Developer Guide**](https://alaz.so/conduit/developers) · [**Wiki**](https://github.com/alazso/Conduit/wiki)

</div>

---

## Why Conduit

- **Async by default.** Every operation returns a `CompletableFuture`. There's no blocking facade to fall back on.
- **`BigDecimal` everywhere.** No `double` anywhere in the API. We're not a fan of rounding bugs.
- **UUID-first.** Accounts are keyed by `UUID`.
- **Capability-aware.** You ask a provider what it supports before you call it, both through extension interfaces and through capability flags. No more catching `UnsupportedOperationException` in production.
- **Typed results and events.** Sealed `EconomyResult` cases tell you exactly what happened. Post-commit events and synchronous pre-auth interceptors let other plugins react or veto.
- **Not a Vault fork.** Conduit is its own API under `so.alaz.conduit`. It doesn't shim, wrap, or pretend to be Vault.

> Need permissions? We recommend LuckPerms. Need chat formatting? Grab LPC Minimessage. Conduit is for plugins and servers that want `so.alaz.conduit.api` for one thing: Economy.

## Quick start

Add the API as a `compileOnly` dependency. The actual runtime ships with the installed Conduit plugin, so you don't bundle it yourself.

```kotlin
repositories {
    maven("https://repo.alaz.so/releases")
}

dependencies {
    compileOnly("so.alaz.conduit:conduit-api:0.2.3")
}
```

Move some money around! The one-liner below is the beginner path. It accepts a
player or a `UUID`, a `long` or a `BigDecimal`, and stays fully async:

```java
import so.alaz.conduit.api.Conduit;

// Beginner one-liner (experimental).
Conduit.deposit(player, 100, "daily reward")
    .thenAccept(result -> result
        .ifSuccess(s -> getLogger().info("New balance: " + s.newBalance()))
        .ifFailure(r -> getLogger().warning(r.describe())));
```

The convenience surface on `Conduit` is `@ApiStatus.Experimental` for the 0.x
line; it only delegates to the active provider and adds no behaviour. Advanced
users keep using `Conduit.getEconomy()` or the registry directly. The robust,
load-order-insensitive form resolves the provider explicitly:

```java
import so.alaz.conduit.api.Conduit;
import so.alaz.conduit.api.economy.Economy;

// Order-insensitive: this runs as soon as a provider is registered,
// whether that happens before or after your plugin enables.
Conduit.whenProviderAvailable(Economy.class, economy ->
    economy.deposit(playerUuid, new BigDecimal("100.00"), "daily reward")
        .thenAccept(result -> {
            if (result instanceof EconomyResult.Success s) {
                getLogger().info("New balance: " + economy.format(s.newBalance()));
            }
        }));
```

Want a feature that not every provider has, like banks? Ask for it. An empty `Optional` just means the active provider doesn't do banking.

```java
Conduit.getRegistry().getProvider(BankingEconomy.class)
    .ifPresent(bank -> bank.getBankBalance("spawn_bank").thenAccept(...));
```

## Modules

| Module | Description |
| --- | --- |
| `conduit-api` | The public API: interfaces, records, results. No implementation, no shaded dependencies. |
| `conduit-core` | The runtime plugin: provider registry, dispatch, events, commands, metrics. |
| `bridges/bridge-essentialsx` | Native EssentialsX Economy bridge. |
| `bridges/bridge-template` | A starting point for writing your own economy bridge. |
| `conduit-test-fixtures` | Conformance tests every provider implementation should pass. |
| `examples/` | Reference plugins: a shop, points, per-action fees, and multi-economy. |

## Building

You'll need a Java 25 toolchain. If it isn't installed, Gradle provisions one through Foojay.

```bash
./gradlew build
```

That runs the full test suite and enforces the JaCoCo coverage gate.

## Documentation

- **[User and operator docs](https://alaz.so/conduit/docs)**: installation, commands, configuration
- **[Developer guide](https://alaz.so/conduit/developers)**: building plugins and bridges against the API *(coming soon)*
- **[Wiki](https://github.com/alazso/Conduit/wiki)**: design notes, FAQ, migration *(coming soon)*

## Contributing

We ship our own Conduit Bridges, but we know we'll never cover every plugin out there. Bridges are hot-pluggable and we actively encourage the community to build their own.

To make that easier, we include a [`bridge-template`](bridges/bridge-template) to start from. It comes with tests so you can run your bridge against the conformance fixtures until it's green. Issues and pull requests are welcome.

## License

Released under the [MIT License](LICENSE). © 2026 Alazso.
