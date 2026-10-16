# Recent Transactions Screen (Room & Jetpack Compose)

Create a dedicated, high-fidelity Jetpack Compose screen that retrieves and displays the user's financial transactions from the local Room database, showcasing transaction amount, category, date, and type with interactive filtering, grouping, and details.

## User Review & Critical Decisions

> [!IMPORTANT]
> The screen connects directly to the existing Room database (`TransactionDao` / `BudgetRepository`) via `BudgetViewModel`, maintaining instant reactivity whenever transactions are added, edited, or deleted.

- **Data Source**: Uses Room `TransactionEntity` observed reactively as `StateFlow<List<TransactionEntity>>`.
- **Presentation**: Grouped chronologically by date with daily totals, styled in the app's signature dark slate and neon theme (`Slate950`, `DarkCard`, `Emerald500`, `Rose500`).
- **Interactive Capabilities**: Filter by transaction type (All, Expense, Income), search by category name, and tap items for full details.

---

## 1. Overview & Core Concept

- **What It Does**: Provides a dedicated, responsive view of all recent user financial transactions stored in the SQLite Room database. Each transaction clearly displays the transaction amount (color-coded for income vs. expense), assigned category with an icon, and formatted date/timestamp.
- **Target Audience**: Users managing their personal budget who need a clear, un-cluttered chronological ledger of their income and spending.
- **Key Value**: Instant visibility into past expenditures without nested menus, complete with reactive updates as new transactions are logged.

---

## 2. User Experience & Visual Design

### Key User Flows
1. **Entry**: User accesses the Recent Transactions screen from the top app bar action or the overview dashboard in `PeriodBudgetScreen`.
2. **Browsing**: User scrolls through a smooth `LazyColumn` of transactions, grouped by day (e.g. "Сегодня", "Вчера", "12 октября").
3. **Filtering**: User taps filter chips (`Все`, `Расходы`, `Доходы`) or types in a search bar to instantly narrow down transactions by category or amount.
4. **Item Details**: Tapping a transaction card reveals full metadata (account, subcategory, timestamp, profile id).
5. **Empty State**: If no transactions exist, an inviting empty state card with an illustration and "Добавить первую запись" prompt is displayed.

### Visual Identity & Theme
- **Theme**: Premium dark financial aesthetic consistent with `Theme.kt` and `Color.kt`.
  - **Background**: `Slate950` / `DarkBg` (0xFF0B0F19)
  - **Cards & Surfaces**: `Slate900` / `DarkCard` (0xFF1E293B) with subtle `Slate700` border strokes
  - **Accents**: `Emerald400` / `Emerald500` for Income (+), `Rose400` / `Rose500` for Expenses (-), and `Indigo500` for primary actions and date badges
  - **Typography**: M3 typography tokens with clean hierarchy, bold currency numerals, and muted timestamp labels

---

## 3. Key Product Decisions & Trade-Offs

- **Decision 1: Direct Room StateFlow vs. One-shot Query**
  - *Chosen Approach*: Observe `TransactionDao.getAllTransactions()` or `BudgetRepository.getTransactionsForBudget()` as a reactive `Flow` / `StateFlow`.
  - *Why*: Ensures the screen automatically recomposes with zero lag whenever new transactions are created (via manual dialogs, voice input, or demo fixtures).
  - *Alternatives Considered*: Manual pull-to-refresh or one-shot coroutine queries (rejected because it risks showing stale transaction states).

- **Decision 2: Chronological Grouping by Date**
  - *Chosen Approach*: Group transactions by ISO date with formatted sticky/clean headers showing the day name and total net expenditure for that day.
  - *Why*: Greatly improves scannability for users reviewing their daily habits.

---

## 4. Technical Architecture & Data Strategy

### System & Component Diagram

```
┌─────────────────────────────────────────────────────────────┐
│                       Room Database                         │
│                    (AppDatabase.sqlite)                     │
└──────────────────────────────┬──────────────────────────────┘
                               │ Flow<List<TransactionEntity>>
                               ▼
┌─────────────────────────────────────────────────────────────┐
│                      BudgetRepository                       │
│              (getTransactionsForBudget / All)               │
└──────────────────────────────┬──────────────────────────────┘
                               │ StateFlow<List<TransactionEntity>>
                               ▼
┌─────────────────────────────────────────────────────────────┐
│                      BudgetViewModel                        │
│             (transactions, selectedBudgetId)                │
└──────────────────────────────┬──────────────────────────────┘
                               │
                               ▼
┌─────────────────────────────────────────────────────────────┐
│                 RecentTransactionsScreen                    │
│  ┌───────────────────────────────────────────────────────┐  │
│  │ Summary Metrics Banner (Total Spent, Total Earned)    │  │
│  ├───────────────────────────────────────────────────────┤  │
│  │ Filter & Search Bar (All / Expense / Income, Search)  │  │
│  ├───────────────────────────────────────────────────────┤  │
│  │ LazyColumn of Grouped Transactions                    │  │
│  │   ├── DateHeaderItem (e.g. "14 октября" • -2 450 ₽)   │  │
│  │   └── TransactionCardItem (Icon, Category, Date, Sum) │  │
│  └───────────────────────────────────────────────────────┘  │
└─────────────────────────────────────────────────────────────┘
```

### Components & State Mapping
- **`RecentTransactionsScreen.kt`**:
  - `transactions: List<TransactionEntity>` collected via `viewModel.transactions.collectAsStateWithLifecycle()`
  - `selectedFilter: TransactionTypeFilter` (ALL, EXPENSE, INCOME)
  - `searchQuery: String`
  - `selectedTransactionForDetails: TransactionEntity?`
- **`TransactionItemCard`**:
  - Displays category icon mapped dynamically from category name
  - Displays formatted date (e.g. `14 окт, 18:30`)
  - Formatted amount in local currency with color coding (+ / -)
- **Navigation Integration**:
  - Linked cleanly in `MainActivity.kt` with back navigation support via `BackHandler`.
