# TwoFunds

Android household expense tracker using Jetpack Compose, Room, and local SMS parsing. The running application is the `app/` module; the older root `src/` directory is a separate, unused prototype. No Gemini API key is needed for the active application.

## Build and verify

Use JDK 17 and Android SDK platform 36.1 with build tools 36.0.0. The checked-in wrapper pins Gradle 9.3.1 and verifies the distribution checksum.

```sh
./gradlew :app:testDebugUnitTest :app:lintDebug :app:assembleDebug
./gradlew :app:connectedDebugAndroidTest
```

On Windows, use `gradlew.bat`. The second command requires an attached Android device or emulator. Instrumentation tests clear the test installation's local transaction database and preferences, so use a dedicated emulator. Debug APK: `app/build/outputs/apk/debug/app-debug.apk`. Release signing uses the existing keystore environment variables.

## Calculation policy

- Amounts are INR, rounded per transaction to two decimal places using HALF_UP. Other currencies, invalid records, unknown transaction directions, and future timestamps are excluded from INR reports.
- Day and month boundaries follow the device timezone. Weeks start Monday. Period totals include only records through the reporting instant.
- Spending is total debits. Credits are shown separately; remaining budget is configured budget minus debits plus eligible credits. Manual credits and incoming SMS credits to the configured owner's ICICI destination are eligible. Credits are not independently verified income and may include internal transfers; this policy is a cash-flow estimate, not accounting income.
- A profile filter changes its transaction totals. The Spend Radar remaining amount remains the shared household balance. Monthly search changes all displayed monthly subtotals, calendar values, and category shares together.
- Gauge graphics stop at 100%; percentage labels retain utilization above 100%. Zero budget has no percentage denominator. Overspending produces a negative remaining amount.
- Weekend estimates release only completed weekdays' allowance, offset overspending against underspending, subtract recorded weekend spending, and are capped by positive remaining weekly and monthly budgets. Historical pacing changes and missing SMS coverage cannot be reconstructed; this is an estimate from recorded data.
- Saved SMS amounts and directions are reconciled at startup when their original messages can be parsed unambiguously. Source messages, categories, and assigned profiles are retained; unsettled messages remain stored as UNCONFIRMED and are excluded. Unsupported messages are left for source reconciliation.
- Exact SMS plus timestamp or a bank transaction reference identifies duplicate notifications. Equal amount and merchant alone do not. New deletion records are scoped to the transaction; legacy global deletion fingerprints are retained because they lack original timestamps.

The Family Allocator prototype is not routed in the active app and still contains example allocations. Monthly category budgets are component-tested but are not routed in the active navigation. Validate ownership, credit policy, complete SMS coverage, and bank statement reconciliation before relying on recorded balances as actual cash availability.
