# CallPulse — Deviations

Every point where reality contradicts `CALLPULSE_BUILD_INSTRUCTIONS.md`, per §0.5 and
`CLAUDE.md` rule 13. Nothing here was improvised silently; each entry records the
contradiction, the ruling, and who made it.

Status values: `OPEN` (needs a human ruling) · `RULED` (decided, implemented) · `CLOSED` (verified).

---

## D-001 — There is no `pm-system` repository to reuse

**Status:** RULED · Stage B · ruled by project owner, 2026-08-11

**Contradiction.** The entire build order assumes an existing Laravel `pm-system` holding
`users`, `projects`, `project_user`, `milestones`, `tasks`, `task_templates` and `work_logs`.
`CLAUDE.md` rule 2 makes reusing them non-negotiable and calls forking them "a hard failure".
Stage A exists solely to recon that repo.

No such repository exists on the build machine. The only ProponentTech Laravel application
present (`proponent-admin`) is an HR/ERP system — attendance, payroll, leave, invoices. It has
`users` and `projects` but **no** `tasks`, `milestones`, `task_templates` or `work_logs`, so it
is not `pm-system` and cannot be reconned against.

**Ruling.** This repository is a **fresh backend that will be reconciled with the real CRM
later** (owner's decision: "starting point, pm-system merges in later"). It therefore has to
own minimal PM master-data tables itself. Stage A is recorded as not-applicable; this file
replaces `RECON.md` as the record of what the schema actually is.

**Consequence.** `CLAUDE.md` rule 2 is suspended for `projects`, `project_user`, `milestones`,
`tasks`, `task_templates`. It remains in force in spirit: these tables are deliberately **thin
and conventional**, named exactly as the spec names them, so that merging with the real
`pm-system` is a data migration rather than a rewrite. The forbidden names in §2 — `call_projects`,
`call_employees`, `call_tasks`, `call_follow_up_tasks` — are still never used.

**Merge risk.** Column-level divergence from the real `pm-system` is near-certain. Mitigations
applied: no CallPulse code reads a PM column outside the models' public accessors; project
access is resolved through a single scope (`Project::scopeAccessibleBy`) so the merge only has
to re-point one method; `tasks` creation goes through one service, not scattered `Task::create()`.

---

## D-002 — `work_logs` does not exist

**Status:** RULED · Stage B

**Contradiction.** `CLAUDE.md` rule 3 and decision #3 forbid touching `work_logs`. The table
does not exist here, so there is nothing to avoid touching.

**Ruling.** `work_logs` is **not created**. The prohibition is preserved as intent: no call code
writes work logs, and no `work_logs.call_session_id` column will ever be added. If the real
`pm-system` brings its own `work_logs` at merge time, it stays untouched.

---

## D-003 — `users` gains four CallPulse columns

**Status:** RULED · Stage B

**Contradiction.** Stage A questions 4 and 6 assume `users` already carries a role concept, a
`left_at` column, and possibly a per-employee daily capacity (decision #5). A stock Laravel
`users` table has none of these, and §6.5 policies plus decision #12 cannot be implemented
without a role.

**Ruling.** Four nullable, additive columns plus soft deletes:

| Column | Why |
|--------|-----|
| `role` | §6.5 policies, §7.1 page audiences. Values: `employee` `pm` `hr` `admin` |
| `daily_work_seconds` | Decision #5 — the per-employee capacity the resolver prefers over `config('callpulse.workday_seconds')` |
| `can_view_call_numbers` | Decision #12 — the explicit `call.view_numbers` ability for managers/PMs |
| `left_at` | Stage A Q6 assumes it; analytics excludes departed employees |
| `deleted_at` | Stage A Q6 assumes soft deletes on `users` |

All additive and nullable, so the merge with the real `pm-system` is a column copy, not a
reconciliation. If that system has its own roles table or permission package, `role` and
`can_view_call_numbers` are dropped and the policies re-point to it — one file each.

---

## D-004 — `compileSdk 37` instead of the specified `compileSdk 36`

**Status:** RULED · pre-Stage F · ruled by project owner, 2026-08-11

**Contradiction.** Decision #9 locks `minSdk 26`, `compileSdk 36`, `targetSdk 36`. The build
machine's Android SDK has only the **API 37** (Android 17) platform installed; API 36 is absent.

**Ruling.** `compileSdk = 37`, `targetSdk = 36`, `minSdk = 26`.

`compileSdk` is compile-time only and needs the platform files on disk; `targetSdk` is a runtime
behaviour contract and needs no platform installed. Compiling against 37 while targeting 36 is
the configuration Google recommends and leaves the spec's actual intent — the app opts into
API 36 behaviour, not API 37's — fully intact.

`targetSdk` was deliberately **not** raised to 37. Each Android release tightens background
receivers, `READ_CALL_LOG`, WorkManager scheduling and foreground-service types — precisely
what the capture engine depends on. Decision #1 keeps this app off the Play Store, so no policy
deadline forces a `targetSdk` bump; it moves only when we choose and test it.

**To verify at Stage F.** The pinned AGP must support `compileSdk 37`. If it does not, Gradle
fails explicitly, and the fallback is installing the API 36 platform.

---

## D-013 — Two permissions are merged in transitively by AndroidX

**Status:** RULED · Stage F

**Contradiction.** §12 lists the manifest permission set and the Appendix C Stage H prompt says
"exactly as listed (no extras)". The built APK contains two more:

| Permission | Added by |
|------------|----------|
| `android.permission.WAKE_LOCK` | `androidx.work:work-runtime` — WorkManager holds a wakelock while a worker runs |
| `com.proponenttech.callpulse.DYNAMIC_RECEIVER_NOT_EXPORTED_PERMISSION` | `androidx.core` — guards its own runtime-registered receivers |

**Ruling.** Both are accepted. Neither is declared by our manifest; the manifest merger pulls
them from library dependencies that §10 mandates, and neither can be removed without dropping
WorkManager — which is the backbone of the §13.1 capture architecture. Both are self-scoped
(the second is a signature permission on our own package) and neither reads user data.

The prohibitions that matter are intact and verified against the built APK: no `READ_SMS`,
no `RECORD_AUDIO`, no `READ_EXTERNAL_STORAGE`, no `REQUEST_INSTALL_PACKAGES` (that one arrives
with the Stage N updater).

**Verified by:** `aapt2 dump badging` on `app-production-release.apk` — full list is
`READ_CALL_LOG`, `READ_PHONE_STATE`, `READ_CONTACTS`, `POST_NOTIFICATIONS`, `INTERNET`,
`ACCESS_NETWORK_STATE`, `RECEIVE_BOOT_COMPLETED`, `REQUEST_IGNORE_BATTERY_OPTIMIZATIONS`,
`FOREGROUND_SERVICE`, `FOREGROUND_SERVICE_DATA_SYNC`, plus the two above.

---

## D-014 — AGP 9 removed the standalone Kotlin plugin

**Status:** RULED · Stage F

**Contradiction.** Decision #9 says "Kotlin 2.x, AGP current stable" and to pin them at scaffold
time. Current stable AGP is 9.3.1, which has **built-in Kotlin support** and fails the build
outright if `org.jetbrains.kotlin.android` is applied:

> The 'org.jetbrains.kotlin.android' plugin is no longer required for Kotlin support since AGP 9.0.

**Ruling.** The standalone plugin is not applied anywhere. The Kotlin compiler *plugins* that
are still separate — `kotlin.plugin.compose` and `kotlin.plugin.serialization` — are applied
explicitly, and `kotlin { compilerOptions { jvmTarget } }` still configures the JVM target, so
decision #9's JDK 17 bytecode target is honoured. Verified: both build variants compile.

---

## D-015 — The API 36 emulator image crashes under swiftshader_indirect

**Status:** RULED · Stage G · environment note, not a code change

**Symptom.** With `hw.gpu.mode=swiftshader_indirect`, the app dies on launch with a native
`SIGABRT` on the `RenderThread`:

```
F libc : Fatal signal 6 (SIGABRT), code -6 (SI_TKILL) in tid (RenderThread)
F DEBUG: android::Choreographer::registerRefreshRateCallback
        art::ScopedCheck::CheckFieldAccess ... android::ibinderForJavaObject
```

No Kotlin stack trace and no `FATAL EXCEPTION` — the abort is inside `libhwui`/`libgui`, in a
CheckJNI field-access assertion. **This is not application code.**

**Ruling.** Run the emulator with `-gpu host`. Verified: same APK, same AVD, launches and runs
correctly. Recorded because it will otherwise be mistaken for an app crash during Stage O QA,
and because a `RenderThread` SIGABRT looks alarming enough to send someone hunting through
Compose code for a bug that is not there.

```
emulator -avd callpulse_api36 -gpu host -no-snapshot -no-boot-anim
```

A "System UI isn't responding" ANR may also appear on first boot. That is the emulator's own
SystemUI process under load, not CallPulse; dismiss with Wait.

---

## D-005 — Testing over plain HTTP on the LAN

**Status:** OPEN · resolve at Stage F/N

**Contradiction.** §18.6 requires HTTPS only and `android:usesCleartextTraffic="false"`. Local
testing runs the phone against `http://192.168.1.20:8000` over office WiFi, with no certificate.

**Proposed ruling.** A `network_security_config.xml` scoped to the **staging flavor only**,
permitting cleartext to that single LAN address. Production keeps HTTPS-only with no custom
trust anchor and no cleartext exemption. To be confirmed when Stage F scaffolds the app.

---

## D-006 — Test framework is PHPUnit, not Pest

**Status:** RULED · Stage B

**Contradiction.** Stage A question 18 asks which framework the repo uses. The Laravel installer
failed twice with an empty log, so the app was created with `composer create-project`, which
ships Laravel's default — **PHPUnit 12.5**.

**Ruling.** Stay on PHPUnit. It satisfies every testing requirement in the build instructions
(§6 acceptance, §8 coverage targets). Switching to Pest later is a contained change.

---

## D-007 — `call_app_releases` deferred to Stage N, as specified

**Status:** RULED · Stage B

Not a contradiction — recorded so the §6.1 endpoint list is not read as incomplete. §5.1 says
`call_app_releases` is created in Stage N (§18.4). Consequently `GET /app/latest-version` and
`GET /app/download` are **also** deferred to Stage N, even though §6.1 lists them under Stage C.
The Stage C acceptance check "`route:list` matches §6.1 exactly" is assessed against the other
20 endpoints.

---

## Analytics rulings still open

These are recorded in `PLAN_OF_ACTION.md` §3.1–3.3 and **must be ruled before Stage C**, since
they change `CallAnalytics`, the §21 payload, the web timeline and the Android day view.

| Ref | Question | Status |
|-----|----------|--------|
| D-008 | `busy_blocks[].project_id` is undefined when a merged block spans two projects | OPEN |
| D-009 | `by_channel` / `by_project` cannot sum to a merged `talk_seconds` when calls overlap | OPEN |
| D-010 | `by_hour` proportional split — over merged intervals or raw calls? | OPEN |
| D-011 | Ingest of a soft-deleted `client_uuid` — resurrect, reject, or report `duplicate`? | RULED — below |
| D-012 | Device-sent `reviewed_at` vs server-authoritative reviewed rows | RULED — below |

---

## D-011 — Ingest of a soft-deleted `client_uuid`

**Status:** RULED · Stage C · ruled by Claude, needs owner confirmation

**Contradiction.** `DELETE /call-sessions/{id}` soft-deletes. `client_uuid` is UNIQUE and the
index survives a soft delete. Ingest upserts on `client_uuid`. A device that re-sends a draft the
user deliberately deleted therefore either violates the unique constraint or silently resurrects
the row. The spec addresses neither.

**Ruling.** Ingest that matches a soft-deleted row returns `status: duplicate` with the existing
id and **does not restore it**. Deletion is a human decision and the device must not undo it —
the same principle as §6.2.2 making reviewed rows server-authoritative. Never a 500.

---

## D-012 — Device-sent `reviewed_at` on a row the server already reviewed

**Status:** RULED · Stage C · ruled by Claude, needs owner confirmation

**Contradiction.** §21's ingest payload includes `reviewed_at` and the review fields, but §6.2.2
says a reviewed row is authoritative on the server and a re-sent draft must not clobber a human
correction. Which wins is not stated.

**Ruling.** If the server row already has a non-null `reviewed_at`, the entire device payload is
ignored for that row and it is returned as `duplicate` — including `project_id`, `task_id`,
`outcome`, `notes` and `is_personal`, not just `reviewed_at`. A partial merge would let a stale
draft half-overwrite a correction, which is the failure §6.2.2 exists to prevent. If the server
row is unreviewed, the device payload is applied in full and may set `reviewed_at`.
