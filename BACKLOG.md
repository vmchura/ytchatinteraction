# Code Improvement Backlog

Format: `[ID] PRIORITY | STATUS | LOCATION | DESCRIPTION | DEFERRED_REASON`

---

## HIGH PRIORITY

[H1] HIGH | PENDING | TournamentController:77-84 | N+1 Query - tournament registrations fetched per tournament in loop | Needs JOIN query refactor
[H2] HIGH | PENDING | AuthController | Missing rate limiting on auth endpoints | Security - requires filter implementation
[H3] HIGH | PENDING | Multiple services | Hardcoded config values (timeout=30, maxSize=1MB, alias prefixes) | Needs config file restructure

## MEDIUM PRIORITY

[M1] MEDIUM | PENDING | services/ | Duplicated session services (3 implementations: Analytical/Tournament/Casual) | Refactor to generic with type params
[M2] MEDIUM | PENDING | TournamentController:150 | Deep nesting (5+ levels) in showMatchScheduling | Extract private methods, reduce complexity
[M3] MEDIUM | PENDING | conf/evolutions/ | Missing DB indexes on frequently queried fields | Needs query pattern analysis first
[M4] MEDIUM | PENDING | PlayFetch.scala | Unused placeholder class | Dead code removal
[M5] MEDIUM | PENDING | Throughout codebase | Inconsistent error handling (mix of Future.successful/failed/Either/Option) | Standardize on Either pattern
[M6] MEDIUM | PENDING | CasualMatchController:207 | No pagination on user alias list | Fetches all records, scalability issue

## LOW PRIORITY

[L1] LOW | PENDING | Throughout | Scala 3 modernization (implicit→given/using, enum for traits) | Gradual migration, not urgent
[L2] LOW | PENDING | server/test/ | Missing unit tests for business logic (ELO, tournament results, replay processing) | Coverage gap
[L3] LOW | PENDING | client/src/ | Frontend error handling - errors only logged to console | User experience improvement
[L4] LOW | PENDING | Various | Magic numbers/strings (1024*1024, "replays", status codes) | Extract to named constants
[L5] LOW | PENDING | FileStorageService:63 | Inefficient path generation (timestamp+random vs UUID/content-hash) | Optimization

## EXCLUDED (User Confirmed Not Needed)

[X1] EXCLUDED | utils/auth/WithAdmin.scala:30 | Hardcoded admin user IDs (Set(1L)) | User is admin, acceptable for single-user admin
[X2] EXCLUDED | TUploadSessionService:32 | In-memory session storage (ConcurrentHashMap) | Single node deployment only
[X3] EXCLUDED | TournamentService:799+ | Missing transaction boundaries in multi-step ops | Partial state acceptable
[X4] EXCLUDED | AnalyticalReplayService:460-474 | Unsafe Option.get() / .head() calls | Verified safe by guard conditions (size==1 checks)

---

## Quick Stats

- **Total Issues:** 15
- **High Priority:** 3
- **Medium Priority:** 6
- **Low Priority:** 5
- **Excluded:** 4

## Next Actions When Resuming

1. Start with [H1] N+1 Query - requires repository method with JOIN
2. [H2] Rate limiting - implement Play Filter or custom Action
3. [H3] Config values - move to application.conf with defaults
