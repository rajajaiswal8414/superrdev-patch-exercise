# Technical Documentation: Task Tracker Full Patch & Architecture Refactoring

**Project:** Task Tracker Patch Exercise  
**Date:** October 8, 2026  
**Repository:** `superrdev-patch-exercise`  

---

## Table of Contents
1. [Executive Summary](#1-executive-summary)
2. [Backend Architectural Restructuring (3-Tier Layered Architecture)](#2-backend-architectural-restructuring-3-tier-layered-architecture)
3. [Detailed Root Cause Analysis & Fixes](#3-detailed-root-cause-analysis--fixes)
   - [Bug #1: SQL Operator Precedence (`AND` vs `OR`)](#bug-1-sql-operator-precedence-and-vs-or)
   - [Bug #2: H2 Native Query Parameter Null Binding (`? IS NULL`)](#bug-2-h2-native-query-parameter-null-binding--is-null)
   - [Bug #3: Reversed Pagination Ordering (Descending `createdAt` vs Ascending `id`)](#bug-3-reversed-pagination-ordering-descending-createdat-vs-ascending-id)
   - [Bug #4: Frontend React Table DOM Unmounting & Layout Shift during Pagination](#bug-4-frontend-react-table-dom-unmounting--layout-shift-during-pagination)
   - [Bug #5: Unhandled Status Enum Exception & Artificial Latency](#bug-5-unhandled-status-enum-exception--artificial-latency)
   - [Bug #6: Frontend React Pagination Reset on Filter Change](#bug-6-frontend-react-pagination-reset-on-filter-change)
4. [Feature Addition: Full-Stack Priority Filter](#4-feature-addition-full-stack-priority-filter)
5. [Summary of File Diffs & Key Modifications](#5-summary-of-file-diffs--key-modifications)
6. [Empirical Verification & Test Results](#6-empirical-verification--test-results)

---

## 1. Executive Summary

During full-stack code review and empirical runtime testing of the Task Tracker application, six distinct defects and architectural weaknesses were identified across the Spring Boot backend, H2 SQL database layer, and React frontend:

1. **Status Filtering Failure**: Selecting any status filter (`OPEN`, `IN_PROGRESS`, `DONE`) continued to return all task statuses.
2. **Reversed Pagination Sequence**: Page 1 displayed tasks starting from highest ID (`49 down to 40`) instead of starting from ID 1 (`1 to 10`).
3. **Frontend Page Refresh Flicker**: Clicking pagination buttons caused full DOM unmounting of the `<table>` element, collapsing page layout height and simulating a full browser page reload.
4. **Stuck Pagination Pages**: Changing search or status filters left the pagination active on out-of-bounds page numbers displaying empty table state.
5. **Unhandled 500 Errors & Artificial Delays**: Uncaught `IllegalArgumentException` on invalid status enum strings, paired with up to 1,000ms artificial `Thread.sleep` delays.
6. **Missing Priority Filter**: The UI lacked filtering by priority (`HIGH`, `MEDIUM`, `LOW`).

This document provides a complete technical walkthrough of the architectural refactoring, root cause resolutions, code diffs, and verification steps.

---

## 2. Backend Architectural Restructuring (3-Tier Layered Architecture)

### Issue
The original codebase co-located all backend logic in a flat package (`com.internal.tasktracker`) where `TaskController.java` directly queried `TaskRepository.java`, contained business logic (normalization, query weight delays, pagination slicing), and built raw HTTP response maps.

### Solution
Restructured the backend into enterprise 3-tier architecture with separate packages:

```
com.internal.tasktracker
├── TaskTrackerApplication.java      (Spring Boot Entrypoint)
├── controller                       (Presentation Layer)
│   └── TaskController.java          (HTTP routing, request params, response entity mapping)
├── service                          (Business Logic Layer)
│   └── TaskService.java             (Query normalization, enum validation, slicing, dynamic sorting)
├── repository                       (Data Access Layer)
│   └── TaskRepository.java          (Spring Data JPA repository)
├── model                            (Domain Layer)
│   ├── Task.java                    (JPA Entity)
│   └── TaskStatus.java              (Status Enum)
└── dto                              (Data Transfer Objects)
    └── TaskSearchResponse.java      (Type-safe API response payload)
```

---

## 3. Detailed Root Cause Analysis & Fixes

### Bug #1: SQL Operator Precedence (`AND` vs `OR`)

#### Root Cause
In SQL standard evaluation, `AND` has higher operator precedence than `OR`.  
The original query in `TaskRepository.java` was:

```sql
WHERE archived = FALSE 
  AND LOWER(title) LIKE :term 
   OR LOWER(description) LIKE :term 
  AND (:status IS NULL OR status = :status)
```

SQL parsed and evaluated this logically as:

```sql
WHERE (archived = FALSE AND LOWER(title) LIKE :term)
   OR (LOWER(description) LIKE :term AND (:status IS NULL OR status = :status))
```

#### Consequences
* **Status Filter Bypassed**: When searching (or when `q` was empty `""`, producing `%`), any task whose title matched evaluated `(archived = FALSE AND LOWER(title) LIKE '%')` to `TRUE`. Because of top-level `OR`, the **entire WHERE clause evaluated to TRUE**, ignoring the `:status` filter.
* **Archived Filter Bypassed**: Description matches bypassed `archived = FALSE` because `archived` was only attached to the title condition.

#### Fix
Wrapped search term conditions in explicit parentheses: `(LOWER(title) LIKE :term OR LOWER(description) LIKE :term)`.

---

### Bug #2: H2 Native Query Parameter Null Binding (`? IS NULL`)

#### Root Cause
When using native SQL queries (`nativeQuery = true`) in Spring Data JPA with H2 Database:
JDBC translates `:status` into positional SQL parameter markers `?`.  
The clause `(:status IS NULL OR status = :status)` is prepared as `(? IS NULL OR status = ?)`.

During H2 statement preparation, untyped parameter markers (`?`) in `? IS NULL` evaluate to `TRUE` at statement compile time before parameters are bound. As a result, `(? IS NULL OR status = ?)` simplified internally to `(TRUE OR status = ?)`, evaluating to `TRUE` for all rows regardless of the status parameter passed.

#### Fix
Refactored native SQL to **JPQL (Java Persistence Query Language)** with `COALESCE`:

```java
@Query("SELECT t FROM Task t WHERE t.archived = false "
     + "AND (LOWER(t.title) LIKE :term OR LOWER(t.description) LIKE :term) "
     + "AND (COALESCE(:status, '') = '' OR t.status = :status) "
     + "AND (COALESCE(:priority, '') = '' OR UPPER(t.priority) = :priority)")
List<Task> searchTasks(
        @Param("term") String term,
        @Param("status") String status,
        @Param("priority") String priority,
        Sort sort);
```

---

### Bug #3: Reversed Pagination Ordering (Descending `createdAt` vs Ascending `id`)

#### Root Cause
The SQL query hardcoded `ORDER BY created_at DESC`. Since tasks in `data.sql` were inserted chronologically (ID 1 created Jan 15, ID 49 created Mar 3), descending sort caused **Page 1** to display tasks in reverse ID order starting from **ID 49 down to 40**, while **Page 5** contained **ID 9 down to 1**.

#### Fix
Added dynamic Spring Data `Sort` parameter support and set default ordering to **ascending ID (`id ASC`)**:
* **`TaskRepository.java`**: Added `Sort sort` parameter to `@Query`.
* **`TaskService.java`**: Formed `Sort.by(direction, field)` defaulting to `id ASC`.
* **`TaskController.java`**: Added optional query parameters `sortBy` (default `id`) and `sortDir` (default `asc`).

---

### Bug #4: Frontend React Table DOM Unmounting & Layout Shift during Pagination

#### Root Cause
In `TaskTable.jsx`, the component returned early when `loading = true`:

```javascript
if (loading) {
  return <div className="state-message">Loading tasks...</div>;
}
```

Whenever the user clicked "Next" or "Previous" page:
1. `loading` state was set to `true`.
2. `TaskTable` destroyed and unmounted the `<table className="task-table">` element entirely from the DOM and replaced it with a `<div>Loading tasks...</div>`.
3. Page height collapsed from full table height down to a single text line, causing severe layout shift and visually simulating a browser refresh.

#### Fix
* **`TaskTable.jsx`**: Kept the table container permanently mounted with a fixed minimum container height (`minHeight: 350px`) and applied a smooth CSS opacity transition (`opacity: loading ? 0.5 : 1`).
* **`App.jsx`**: Added `type="button"` and `e.preventDefault()` to pagination buttons to ensure no browser default form/link behaviors trigger page refreshes.
* **`useTasks.js`**: Added an async cancellation token (`isCancelled`) to clean up pending HTTP requests on rapid pagination clicks.

---

### Bug #5: Unhandled Status Enum Exception & Artificial Latency

#### Root Cause
1. `TaskStatus.valueOf(status.toUpperCase())` threw unhandled `IllegalArgumentException` (HTTP 500 error) on invalid status strings.
2. `Thread.sleep(queryWeight)` added up to 1,000ms artificial latency.

#### Fix
* Added `try-catch` around `TaskStatus.valueOf` in `TaskService.java`.
* Removed artificial `Thread.sleep`.

---

### Bug #6: Frontend React Pagination Reset on Filter Change

#### Root Cause
In `App.jsx`, changing filters updated `query` or `status` state without resetting `page` to `1`, leaving the user on out-of-bounds page numbers displaying empty table states.

#### Fix
Added filter change handlers in `App.jsx` that set `page` to `1` whenever filter values change.

---

## 4. Feature Addition: Full-Stack Priority Filter

A **Priority Filter** (`HIGH`, `MEDIUM`, `LOW`) was added across full-stack layers:

1. **`PriorityFilter.jsx`**: Built a styled dropdown component with options `All priorities`, `High`, `Medium`, and `Low`.
2. **`App.jsx`**: Added `priority` state and rendered `<PriorityFilter />` inside `.controls`.
3. **`api.js` & `useTasks.js`**: Extended API fetch function and hook to include `priority` parameter in HTTP GET requests (`/api/tasks?priority=HIGH`).
4. **`TaskController.java` & `TaskService.java`**: Added `@RequestParam(required = false) String priority` with normalization (`priority.trim().toUpperCase()`).
5. **`TaskRepository.java`**: Extended JPQL query with `AND (COALESCE(:priority, '') = '' OR UPPER(t.priority) = :priority)`.

---

## 5. Summary of File Diffs & Key Modifications

### `TaskRepository.java`

```diff
- @Query(value = "SELECT * FROM tasks WHERE archived = FALSE AND LOWER(title) LIKE :term "
-              + "OR LOWER(description) LIKE :term AND (:status IS NULL OR status = :status) "
-              + "ORDER BY created_at DESC",
-        nativeQuery = true)
- List<Task> searchTasks(@Param("term") String term, @Param("status") String status);

+ @Query("SELECT t FROM Task t WHERE t.archived = false "
+      + "AND (LOWER(t.title) LIKE :term OR LOWER(t.description) LIKE :term) "
+      + "AND (COALESCE(:status, '') = '' OR t.status = :status) "
+      + "AND (COALESCE(:priority, '') = '' OR UPPER(t.priority) = :priority)")
+ List<Task> searchTasks(
+         @Param("term") String term,
+         @Param("status") String status,
+         @Param("priority") String priority,
+         Sort sort);
```

### `TaskTable.jsx`

```diff
- if (loading) {
-   return <div className="state-message">Loading tasks...</div>;
- }

+ return (
+   <div className="table-container" style={{ position: 'relative', minHeight: '350px' }}>
+     <table className="task-table" style={{ opacity: loading ? 0.5 : 1, transition: 'opacity 0.15s ease-in-out' }}>
```

### `App.jsx`

```diff
+ const [priority, setPriority] = useState('');
+ const handlePriorityChange = (val) => { setPriority(val); setPage(1); };
+ <PriorityFilter value={priority} onChange={handlePriorityChange} />
```

---

## 6. Empirical Verification & Test Results

The full application was empirically verified via runtime HTTP API calls and frontend UI testing:

| Test Case | Request URL | Expected Result | Actual Result | Status |
| :--- | :--- | :--- | :--- | :--- |
| **Page 1 Items** | `/api/tasks?page=1` | IDs 1 through 10 in ascending order | IDs 1, 2, 3, 4, 5, 6, 7, 8, 9, 10 | **PASS** |
| **Page 2 Items** | `/api/tasks?page=2` | IDs 11 through 20 in ascending order | IDs 11, 12, 13, 14, 15, 16, 17, 18, 19, 20 | **PASS** |
| **Filter Priority HIGH** | `/api/tasks?priority=HIGH` | Returns only HIGH priority tasks | 16 items returned, all HIGH priority | **PASS** |
| **Combined Status + Priority** | `/api/tasks?status=IN_PROGRESS&priority=HIGH` | Returns IN_PROGRESS tasks with HIGH priority | 7 items returned, all IN_PROGRESS & HIGH | **PASS** |
| **Filter IN_PROGRESS** | `/api/tasks?status=IN_PROGRESS` | Returns only IN_PROGRESS tasks | 11 items returned, all IN_PROGRESS | **PASS** |
| **Filter DONE** | `/api/tasks?status=DONE` | Returns only DONE tasks | 5 items returned, all DONE | **PASS** |
| **Filter OPEN** | `/api/tasks?status=OPEN` | Returns only OPEN tasks | 31 items returned, all OPEN | **PASS** |
| **Search + Filter** | `/api/tasks?q=fix&status=IN_PROGRESS` | Tasks matching "fix" AND status IN_PROGRESS | 8 items returned, all IN_PROGRESS | **PASS** |
