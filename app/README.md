# Arca

## A calm, local-first money tracker for people with lots of accounts

Arca (Latin: *chest, strongbox*) is a personal money-management app for recording income, expenses and transfers across many accounts in a single currency, Indonesian Rupiah. It answers two questions: **"how much do I have?"** (per-account balances and one net position) and **"where did my money go?"** (period summaries and a category breakdown).

It is built by and for its owner, so it is deliberately opinionated. It records what you tell it and never connects to your bank, nudges you, or judges you. Everything works offline, and the UI is designed so that **the number is the loudest thing on every screen.**

> **Status:** early development (Phase 0). The module structure, design system and a navigable four-tab shell are in place. The ledger, transfers, reports and widgets are specified but not yet built (see the [roadmap](#roadmap)).
>
> **Platform note:** Arca is built Android-first, in Kotlin and Jetpack Compose, following SDD-ARC-002 (Android edition). The iOS client comes later and will read the same data contract.

## How it fits together

```
   you ──► widget · launcher shortcut · Quick Settings tile ──► CaptureActivity ─┐
   you ──► FAB (Catat) ─────────────────────────────────────► capture sheet ────┤
                                                                                ▼
   ┌────────────────────────────────────────────────────────────────────────────┐
   │ :app     Compose screens ◄── StateFlow<UiState> ── Hilt ViewModels         │
   │          Summary · Transactions · Accounts · Reports                       │
   ├────────────────────────────────────────────────────────────────────────────┤
   │ :domain  pure Kotlin, JVM-tested, no Android imports                       │
   │          BalanceCalculator · ReportAggregator  (the only money arithmetic) │
   │          TransferService · CurrencyParser · repository interfaces          │
   ├────────────────────────────────────────────────────────────────────────────┤
   │ :data    Room (source of truth) · DataStore · implementations (internal)   │
   │          Firestore sync mirror arrives post-v1.0                           │
   └────────────────────────────────────────────────────────────────────────────┘
```

## Features

**v1.0 scope: 26 requirements, all needed for the app to be viable**

- **Transactions:** record expenses and income, backdate them, edit or delete them, and browse them grouped by date.
- **Transfers:** move money between your own accounts without it counting as spending. A transfer is stored as paired transactions, created atomically. An optional fee is stored as a normal expense in a non-deletable **Fees** category, so a top-up surcharge never disappears into the principal.
- **Accounts at scale:** grouped list, favorites for fast selection, and archive instead of delete. It is designed for 10+ accounts.
- **Balances:** per-account balance plus a total net position.
- **Categories:** sensible defaults, fully manageable.
- **Reflection:** period summary, category breakdown chart and period navigation, so "what was my biggest category last month?" takes under 10 seconds.
- **Low-friction capture:** a Home Screen quick-add widget and a system shortcut action, to counter the biggest product risk, which is forgetting to log.
- **Local-first and offline:** the UI never waits on the network.
- **Calm empty states:** a first-time user and a returning user after a long gap see the same neutral screen. There are no streaks, guilt or confetti.

**Money handling:** amounts are integer rupiah with no decimals, so balances are exactly reproducible. Input accepts `50rb`, `50000` or `50.000`.

**Later:** cloud sync (Firebase), budgets (v1.1) and an optional mascot (v1.2). The mascot reads only a budget-status value and never your transactions or logging habits.

**Out of scope:** bank or payment connections, multiple currencies, financial advice or forecasting, and non-mobile clients.

## Using the app

Arca isn't released yet, so there is nothing to install. To try what exists today, build the debug app (see [Development](#development)) and run it on a device or emulator (Android 10+, API 29).

## Development

### Prerequisites

- Android Studio (current stable release)
- JDK 17 or newer to launch Gradle, with `JAVA_HOME` pointing at an installed JDK. The Gradle daemon itself runs on JDK 25, which Gradle downloads automatically.
- An emulator or device on Android 10+ (API 29+). `minSdk 29` is a placeholder until the target device is recorded (OPEN-A01).

### Build and run

```bash
git clone <this repository>
cd Arca
./gradlew :domain:test :app:assembleDebug
```

Or open the folder in Android Studio and press **Run**. The `debug`, `beta` and `release` builds have different application IDs, so all three can be installed side by side.

### Project layout

Three modules whose boundaries are enforced by the compiler (SDD-ARC-002 §5.1, DD-A02):

```
Arca/
├── domain/        pure Kotlin (no Android plugin): the rules, testable on the JVM
│   └── org.nicho.arca.domain
│       ├── model/          Account, Transaction, Category, enums with stable wire codes
│       ├── repository/     role interfaces: TransactionReading, TransferPersisting, …
│       ├── calculation/    BalanceCalculator, ReportAggregator (the only money arithmetic)
│       ├── transfer/       TransferService, TransferValidator
│       ├── validation/     TransactionValidator + one file per rule
│       ├── parsing/        CurrencyParser ("50rb", "1,5jt", "50.000")
│       ├── formatting/     MoneyFormatting: Standard, Compact, Signed, Accessible
│       ├── readmodel/      ObserveSummary, ObserveAccounts, ObserveReports
│       ├── time/           PeriodClock
│       ├── seeding/        DefaultCategoryProvider
│       └── error/          DomainError
├── data/          Android library: Room, DataStore; everything internal
│   ├── schemas/            exported Room schema JSON (committed)
│   └── org.nicho.arca.data
│       ├── db/             ArcaDatabase, entities/, dao/
│       ├── mapper/         entity ⇄ domain
│       ├── repository/     RoomTransactionStore, RoomAccountStore, RoomCategoryStore
│       ├── prefs/          CaptureContextStore
│       ├── seeding/        DemoDataSeeder
│       └── di/             DataModule (binds implementations to domain interfaces)
├── app/           Compose UI, ViewModels, navigation, Hilt wiring
│   └── org.nicho.arca
│       ├── ArcaApplication, MainActivity, CaptureActivity
│       ├── di/             DomainModule (builds every domain object)
│       ├── navigation/     type-safe routes, ArcaNavHost
│       ├── designsystem/   ArcaTheme, tokens, ArcaIcons (Lucide), ArcaLogo (mark, wordmark, lockup)
│       ├── ui/             app shell; components/ (every design-system component + Mascot); model/ (UI data)
│       ├── locale/         LanguageController (in-app English / Indonesian switch)
│       ├── feature/        summary, transactions, accounts, reports, capture, categories, settings
│       ├── undo/           UndoCoordinator
│       ├── widget/         Glance widgets (Phase 4)
│       ├── shortcuts/      launcher shortcuts and Quick Settings tile (Phase 4)
│       ├── security/       BiometricGate
│       └── export/         CsvExporter (Phase 5)
└── testing/       shared fixtures: in-memory stores, TestDataBuilder
```

### Using the design system

Wrap UI in `ArcaTheme` and read tokens through `ArcaDesignSystem`. Never hard-code colors, sizes or type.

```kotlin
ArcaTheme {
    Text(
        text = "+Rp50.000",
        color = ArcaDesignSystem.colors.income,
        style = ArcaDesignSystem.typography.amountRow,
        modifier = Modifier.padding(ArcaDesignSystem.spacing.screenMargin)
    )
}
```

The rules behind the tokens:

- **Money is the loudest thing.** De-emphasize with size and weight, never by fading text.
- **Expenses are `ink`.** Income is the only hue an amount may carry, always with a leading `+`. Transfers are `inkTertiary` with no sign.
- **`negative` is rare.** Use it only for a negative balance and destructive actions, never for an ordinary expense.
- **Neutral by default.** Separate with whitespace and hairlines, not cards. There is one shadow, for the FAB and the undo banner only.
- **Both themes ship together.** Check dark mode from day one.
- **Accessible.** Colour is never the only signal, and all text meets WCAG 2.1 AA.

### Language and text

- **Code is in English.** Classes, functions, packages and resource keys use English names, even where the specs name a screen in Indonesian: Ringkasan → `Summary`, Transaksi → `Transactions`, Akun → `Accounts`, Laporan → `Reports`.
- **All UI text is a string resource.** Never hard-code user-facing text in Kotlin. Add every key to both `res/values/strings.xml` (English) and `res/values-in/strings.xml` (Indonesian).
- **Users switch language in Settings.** The choice is English, Bahasa Indonesia or follow the system. It's stored by AppCompat's per-app locales and also appears in Android 13+'s system app-language settings.

### Architecture rules

- **One pattern on every screen:** a stateless `XxxScreen(state, …)` over a `@HiltViewModel` that exposes `StateFlow<UiState>`. ViewModels map domain read models; they never sum.
- **Money arithmetic lives in exactly two types:** `BalanceCalculator` and `ReportAggregator`. There are no SQL `SUM`/`TOTAL`/`AVG` in `:data`, and no `sumOf`/`fold` on amounts in `:app`.
- **Money is `Long` rupiah everywhere.** Kotlin's `Int` overflows at about Rp 2.1 billion, and there is never a `Double` in the money path.
- **Enums persist by `code`**, never by `name` or ordinal. A released code is never renamed.
- **Multi-row writes** (transfers) go through `insertAtomic` in one Room transaction.
- **No `@Inject` in `:domain`.** Domain objects are built in `app/di/DomainModule`.
- **No destructive migrations.** Every schema change ships a migration and a test.
- **External entry points only pre-fill the capture form;** they never write.
- **Secrets** (`google-services.json`, keystores) are never committed.

## Contributing

Arca is a solo project, so this section is mainly the ground rules for future me and any collaborator.

- Requirements have stable IDs (`FR-133`, `NFR-R03`). Reference them in commits and PRs, and don't reuse a withdrawn ID.
- New behaviour needs a requirement in the SRS first. Scope changes go through the MoSCoW list, and **shipping v1.0 outranks every other goal**.
- Domain changes ship with unit tests. Transfer changes must cover the three-member (fee) case.
- A design change that contradicts SDD-ARC-002 means editing the SDD (with a new `DD-A` ID), not just the code.
- Keep UI copy calm and neutral, in both languages.

## Known issues

- UI components, the mascot, the logo, the launcher icon and the splash screen are built. Domain logic and repositories are still stubs marked `TODO("Phase N")` (calling one throws), so every screen shows its empty state.
- The SRS (v1.6) has not yet been amended for Android-first. SDD-ARC-002 §2.2 lists the clauses it contradicts, starting with CON-01 ("MUST be written in Swift").
- `minSdk 29` is a placeholder (OPEN-A01).
- English UI strings are drafts. Indonesian is the reviewed copy.
- The icon set (Lucide) is provisional.
- On some Windows machines Gradle fails with `Unable to establish loopback connection`. Setting `JAVA_TOOL_OPTIONS=-Djdk.net.unixdomain.tmpdir=<a short writable dir>` works around it.

## Roadmap

| Phase | Scope |
| --- | --- |
| 0 | Foundation: data models, repository, `CurrencyParser`, `BalanceCalculator` with tests |
| 1 | Core capture and ledger: transactions, accounts, categories, empty states |
| 2 | Transfers, including fees |
| 3 | Analysis: summary, category chart, seeded demo data |
| 4 | Low-friction capture: widget and system shortcut |
| 5 | Hardening, accessibility audit, CSV export, **v1.0** |
| 6+ | Cloud sync, isolation verification, budgets (v1.1), mascot (v1.2) |

The v1.0 cutoff is **31 December 2026**. Ship whatever is done by then.

**Goals, in priority order:** ship a working app (30 days of real use), build native-mobile competence, get retrospective awareness, sustain use for 90 days, work as a portfolio piece.

## Specifications

- **SRS-ARC-001** v1.5 in `docs/` (SDD-ARC-002 cites a v1.6): requirements, data model, architecture, prioritization, risks and traceability.
- **SDD-ARC-002** v1.0: Android design document, the implementation reference for this repository (`docs/SDD-ARC-002-Android.md`).
- **SDD-ARC-001**: the iOS design, whose platform-neutral decisions SDD-ARC-002 inherits.
- **Arca design system**: color, type, spacing, radius and shadow tokens, plus content and accessibility rules.
