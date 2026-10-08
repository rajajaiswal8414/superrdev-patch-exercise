# Submission Notes (`NOTES.md`)

## Summary of Changes
1. **Restructured Backend Architecture**: Refactored co-located files into enterprise 3-tier structure (`controller`, `service`, `repository`, `model`, `dto`).
2. **Fixed SQL Operator Precedence**: Added explicit parentheses in `TaskRepository`, `search_tasks.sql`, and `task_search_package.sql` to fix status filtering bypass caused by `AND`/`OR` evaluation order.
3. **Fixed H2 Parameter Null Binding**: Converted native query to JPQL with `COALESCE` to eliminate H2 untyped parameter prepared statement issues.
4. **Corrected Pagination Order**: Added dynamic `Sort` parameter defaulting to `id ASC`, replacing descending `createdAt` sort so Page 1 lists items from ID 1 onwards.
5. **Fixed Frontend React Layout Shift**: Removed `TaskTable` unmounting on `loading` state, replacing it with a smooth opacity transition to eliminate page reload flickers.
6. **Added Priority Filter Feature**: Built full-stack priority filter (`PriorityFilter.jsx`, controller/service/repo params) supporting `HIGH`, `MEDIUM`, `LOW` filters.
7. **Handled Enum Exceptions & Latency**: Added `try-catch` around `TaskStatus.valueOf` and removed artificial `Thread.sleep` delay.

## What Was Not Changed & Why
- **Oracle PL/SQL Procedure Execution**: Kept `task_search_package.sql` as a reference artifact (as specified in `README.md`) and updated its SQL logic without adding local Oracle runtime drivers.
- **Task Entity Schema Fields**: Retained existing database schema (`tasks` table columns) to ensure backward compatibility and minimal surface diff.

## Biggest Remaining Risk
- **In-Memory Pagination**: Slicing results in `TaskService.subList(...)` fetches all matching rows from the database before slicing. For large production datasets, database-level `Pageable` limits (`LIMIT`/`OFFSET`) should be pushed directly down to SQL.

## Tools & AI Used
- Used **Antigravity AI** for automated codebase analysis, 3-tier backend refactoring, SQL operator precedence debugging, React layout shift optimization, and full-stack test verification.
