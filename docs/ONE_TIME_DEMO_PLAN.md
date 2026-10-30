# One-time DEMO adaptation — implementation checklist

Base: feature/issue-60-safe-to-spend (Safe-to-Spend 2.0).

## Acceptance criteria
- Fresh install seeds the DEMO profile and transactions once.
- Deleting DEMO, deleting all DEMO transactions, or using in-app reset never automatically reseeds DEMO.
- Updating the APK preserves the decision not to recreate DEMO.
- Uninstalling and reinstalling the app permits fresh-install seeding.
- Android system Clear storage is equivalent to a fresh install because it erases local installation markers.
- DEMO is compatible with Safe-to-Spend 2.0, including recurring payments, execution logs, forecasts, and What-if scenarios.
- Existing user profiles and real financial data must not be replaced by seed data.
- Add regression tests for first launch, subsequent launch, empty profile, deletion, in-app reset, and DEMO financial consistency.

## Existing behavior to replace
BudgetRepository.ensureDefaultProfileExists() calls loadDemoData() whenever no transactions exist and can reload an older DEMO based on a content heuristic. Remove these implicit reseed paths and replace with an explicit, durable installation-scoped seed decision. Ensure the in-app reset does not erase that decision.

## Delivery
This document initializes a draft PR; implementation, tests, and CI verification are pending. Do not merge until the actual code and checks are complete.
