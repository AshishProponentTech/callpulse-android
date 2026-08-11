# CallPulse — Build Instructions for Claude Code

> **Read this whole file before writing a single line of code.**
> This is a work order, not a design document. The design lives in
> `androidcalltrackingapp.md` (v1.1). Where the two disagree, **this file wins** —
> it resolves the open decisions that spec left hanging.

**Deliverable:** an internally-distributed **APK** (`com.proponenttech.callpulse`) that logs
work calls on employee Android phones, plus the **Laravel side of `pm-system`** that stores,
serves and *displays* that data inside the existing PM system.

**Document version:** 1.0 · Target start date 2026-08-11 · Owner: Project Lead

---

## 0. How to use this file

1. Work **stage by stage, in order**. Stages are labelled `A` → `P`.
2. Every stage has **Inputs → Do → Acceptance**. Do not move on until Acceptance passes.
3. **Stage A is mandatory and produces no code.** It is a recon pass over the existing
   `pm-system` repo. Everything after it depends on what you find there.
4. At the end of each stage: run the stage's tests, then `git commit` on the stage branch
   (`feat/callpulse-stage-a`, `-b`, …). One stage = one PR.
5. If reality contradicts this document (a table has a different name, a column is missing,
   a package version differs) — **stop, write the contradiction into
   `docs/callpulse/DEVIATIONS.md`, and ask the human before improvising.** Do not silently
   invent tables or rename things.
6. Copy §Appendix C prompts into Claude Code one at a time. Do not paste the whole file as
   a single prompt.

### Two repositories

| Repo | What | Branching |
|------|------|-----------|
| `pm-system` (existing Laravel) | migrations, models, API v1, Livewire dashboards | branch off `main` → `feat/callpulse-*` |
| `callpulse-android` (new) | Kotlin app, produces the APK | `main` + `feat/*` |

They are **separate repos**. Never vendor one into the other. The contract between them is
§21 (API reference) — treat it as frozen once Stage C ships; changes require a version bump.

---

## 1. Locked decisions

The source spec §16 listed six open decisions. All are now closed. Additional decisions the
spec did not raise are numbered 7+ and are equally binding.

| # | Decision | Ruling | Consequence for you |
|---|----------|--------|---------------------|
| 1 | **Distribution** | **Internal APK only. No Play Store, ever.** | No Play Console declaration form. `READ_CALL_LOG` is unrestricted for us. Sideload / MDM install. You MUST build an in-app update path (§18.4) because there is no Play auto-update. |
| 2 | **WhatsApp** | **Manual log button in MVP.** AccessibilityService is **not built now** — Phase 3, behind a disabled feature flag. | Ship `channel = whatsapp_manual` only. Write the enum values for `whatsapp` now so no migration is needed later, but never emit them. |
| 3 | **Work logs** | **No auto-write to `work_logs`. Not even suggest-only.** | Do not add `work_logs.call_session_id`. Do not touch the `work_logs` table at all. |
| 4 | **WhatsApp voice vs video** | **Same channel, split by a new `sub_channel` column** (`voice` / `video` / `null`). | One extra nullable column, no second channel enum value. |
| 5 | **Default workday** | **Resolve in Stage A.** If `pm-system` already stores per-employee daily capacity (e.g. `employee_daily_hours`, `users.daily_hours`, a `capacities` table), use it. Otherwise fall back to `config('callpulse.workday_seconds')` default `28800`. | Analytics must call one resolver method, `CallAnalytics::workdaySecondsFor(User $u, CarbonImmutable $date)`. Never hardcode 28800 in more than one place. |
| 6 | **Interview number matching** | **Not in MVP.** | Do **not** add `call_sessions.interview_id`. Do not query `interviews`. |
| 7 | **Auth mechanism** | Sanctum personal access tokens, one token per device install, named `callpulse:{device_uuid}`, `expires_at = now()+90 days`. Rotation endpoint `POST /auth/refresh` issues a new token and deletes the old one. | App refreshes when <14 days remain. On `401` the app wipes local token and forces re-login. It must **not** wipe unsynced call drafts. |
| 8 | **Time storage** | DB stores **UTC**. API speaks **ISO-8601 with offset** (`2026-08-11T14:32:07+05:30`). Analytics bucket in `Asia/Kolkata`. Device sends its UTC offset with every row. | New column `tz_offset_minutes`. Never use `date()` / `now()` without an explicit timezone. Use `CarbonImmutable`. |
| 9 | **Android SDK levels** | `minSdk 26`, `compileSdk 36`, `targetSdk 36`. Kotlin 2.x, AGP current stable, JDK 17. | Verify the installed AGP/Kotlin at scaffold time and pin them in `gradle/libs.versions.toml`. |
| 10 | **No permanent foreground service** | The spec's `CallLogObserverService` as an always-on foreground service is **rejected**. See §13.1 for the replacement architecture. | Event-driven capture + periodic reconciliation scan. A foreground service is used only for the brief capture burst, if at all. |
| 11 | **Personal calls** | Employees will use these phones for personal calls. Add `is_personal` boolean. Marking personal keeps the row (audit) but excludes it from all KPI aggregates and **masks the number** for everyone except the owner. | New column + a filter in every analytics query + resource-level masking. |
| 12 | **Number privacy** | `phone_e164` / `phone_display` / `contact_name` are returned in full to the **owner** and to `admin`. Managers/PMs see a masked form `+91 98XXXXXX21` unless they hold an explicit `call.view_numbers` permission. Personal calls are masked for everyone but the owner. | Masking lives in `CallSessionResource`, never in the query. |
| 13 | **Data retention** | `call_sessions` older than `config('callpulse.retention_months')` (default 24) are hard-deleted by a scheduled command. | One artisan command + a schedule entry. Ship it in MVP so nobody forgets. |
| 14 | **Consent** | Consent is a **stored record**, not just a screen. Device reports `consent_version` + `consent_accepted_at`. API rejects call ingestion from a device with no consent record (`409 consent_required`). | Columns on `call_devices`, a gate in the ingest controller. |
| 15 | **Crash/telemetry** | No Firebase/Crashlytics (adds Google dependency to an internal app). Use a self-hosted endpoint `POST /api/v1/telemetry/events` for crashes + permission-denial events. | Small table `call_telemetry_events`, capped and purged with retention. |

Anything not listed here and not in the source spec: **ask, do not decide.**

---

## 2. Guardrails — hard rules

These map to the source spec §5.3 and §5.0 and are non-negotiable.

**Never create:**

- `call_projects`, `call_employees`, `call_tasks`, `call_follow_up_tasks` — reuse `projects`, `users`, `tasks`.
- `call_recordings`, any audio blob column or table.
- `whatsapp_messages` or any table holding message content.
- `raw_android_call_logs` — a full CallLog mirror.
- A daily aggregate table as the primary source (`employees_call_stats`). Aggregate from `call_sessions`; cache later only if a query is proven slow.
- `call_session_task` many-to-many. One call → at most one `task_id`.
- MySQL native `ENUM()` columns. Use `string` + string-backed PHP enum, matching the rest of `pm-system`.
- A second local "analytics warehouse" on the phone. Room is a draft + offline queue cache only.

**Never do:**

- Never write to `work_logs` from call code (decision #3).
- Never auto-create a task without explicit user confirmation.
- Never record audio, read SMS, read message content, or upload the address book.
- Never store call data on the device unencrypted.
- Never hardcode the API base URL in more than one place (`BuildConfig` + managed config only).
- Never delete a `call_session` when its task is deleted — `task_id` goes null, row survives.
- Never let a call row exist without a `project_id`.
- Never commit the release keystore, `local.properties`, or `.env` to git.

**Always:**

- Upsert by `client_uuid`. Every ingest is idempotent.
- Guard every API response through an API Resource — no raw model JSON.
- Every new endpoint gets a Policy check and a feature test.
- Every DB write that touches `tasks` uses the **existing** task creation path/service if one exists (found in Stage A), not a raw `Task::create()`.

---

## 3. Prerequisites (human, before Stage A)

- [ ] `pm-system` cloned locally, `.env` configured, `php artisan migrate` runs clean on a scratch DB.
- [ ] PHP + Composer + Node versions matching `pm-system`'s `composer.json` / `package.json`.
- [ ] Android Studio (current stable) with SDK 36 + Build Tools installed, JDK 17.
- [ ] At least 2 physical Android test phones. One must be Xiaomi/Oppo/Vivo/Realme (aggressive battery killers). Emulators cannot test call capture.
- [ ] A staging Laravel URL reachable from the phones over **HTTPS with a valid cert** (not self-signed, not plain HTTP — see §18.6).
- [ ] Signed employee consent policy PDF, and the policy hosted at a URL the app can link to.

---

## 4. STAGE A — Recon of `pm-system` (no code)

**Nothing in stages B–F may be written until this stage's output file exists.**

### Do

Read the existing repo and write `docs/callpulse/RECON.md` answering **every** question below
with a file path and line reference. Where the answer is "does not exist", say so explicitly.

**Framework & auth**

1. Laravel version (`composer.json`), PHP version constraint.
2. Is Sanctum installed? Config at `config/sanctum.php` — current `expiration` value.
3. Existing API routes: does `routes/api.php` exist? Is there already an `/api/v1` prefix or an `Api\V1` controller namespace? What middleware group?
4. How are roles/permissions done — Spatie `laravel-permission`, an enum on `users`, a `roles` table, gates? Give the exact role slugs for: employee, PM, HR, admin.
5. Is there an existing API Resource / response envelope convention? Show one example.

**Schema**

6. `users` — full column list. Confirm `left_at` and soft deletes. Any per-employee daily hours/capacity column or related table (decision #5)?
7. `projects` and `project_user` — columns, and the exact query used elsewhere in the app to list "projects this user can work on". Copy that scope; do not write a new one.
8. `milestones` — columns, FK to project.
9. `tasks` — full column list, and the enums used (`TaskCategory`, `TaskStatus`, `Priority`) with their backing values.
10. `task_templates` — full column list. How are templates filtered/grouped (by project? service group? category?). What is the existing code path that turns a template into a task? File + method.
11. `work_logs` — columns (for confirmation only; we will not touch it).
12. Do the migrations use `foreignId()->constrained()` style? Any table prefix? Any non-default connection?

**Frontend**

13. Livewire version (2 or 3?) or Inertia/Blade only. Where do existing dashboard pages live?
14. CSS framework and the existing layout component name used by dashboard pages.
15. Is there an existing chart library in use (ApexCharts, Chart.js)? Reuse it — do not add a second.
16. Existing table/list component or pattern to copy for the new pages.

**Ops**

17. Is `php artisan schedule:run` actually wired on the server? (Needed for retention + heartbeat alerts.)
18. Existing test framework — Pest or PHPUnit? Factory conventions.
19. Is there an existing activity/audit log package (spatie/laravel-activitylog)? If yes we reuse it for the call audit trail instead of building one.

### Acceptance

- `docs/callpulse/RECON.md` exists, every numbered item answered with a path.
- A short section at the end: **"Deviations from the CallPulse spec's assumptions"** listing anything that differs from the source spec §5.1.
- Human sign-off on that deviations list before Stage B.

---

## 5. STAGE B — Laravel schema, enums, models

### Inputs
`docs/callpulse/RECON.md`.

### 5.1 Migration

One migration file: `database/migrations/2026_08_XX_000001_create_call_tracking_tables.php`
creating **three** tables. Match the repo's existing migration style found in Stage A.
(A fourth table, `call_app_releases`, is added later in Stage N — see §18.4. Do not create it now.)

#### `call_sessions`

| Column | Type | Null | Notes |
|--------|------|------|-------|
| `id` | bigint PK | no | |
| `client_uuid` | uuid | no | **UNIQUE** — idempotency key from device |
| `user_id` | FK `users` | no | cascadeOnDelete |
| `project_id` | FK `projects` | no | cascadeOnDelete |
| `task_id` | FK `tasks` | yes | nullOnDelete |
| `milestone_id` | FK `milestones` | yes | nullOnDelete; must belong to `project_id` |
| `phone_e164` | string(32) | yes | null if hidden/private number |
| `phone_display` | string(64) | yes | raw dialer string |
| `contact_name` | string(120) | yes | device contact cache; nullable, never required |
| `direction` | string(20) | no | `inbound` `outbound` `missed` `rejected` `unknown` |
| `channel` | string(30) | no | `cellular` `whatsapp` `whatsapp_manual` `other_voip` |
| `sub_channel` | string(20) | yes | `voice` `video` — decision #4 |
| `started_at` | timestamp | no | UTC |
| `ended_at` | timestamp | yes | UTC |
| `tz_offset_minutes` | smallint | no | device offset at capture, default 330 |
| `duration_sec` | unsigned int | no | default 0 |
| `ring_sec` | unsigned int | yes | usually null — see §13.4 |
| `answered` | boolean | no | default false |
| `is_personal` | boolean | no | default false — decision #11 |
| `confidence` | string(10) | no | `high` `medium` `low`, default `high` |
| `source` | string(30) | no | `call_log` `telephony` `accessibility` `manual` |
| `device_uuid` | string(64) | yes | → `call_devices.device_uuid` |
| `android_call_log_id` | string(64) | yes | CallLog `_ID` for dedupe |
| `phone_account_id` | string(64) | yes | dual-SIM identification |
| `outcome` | string(40) | yes | `connected` `no_answer` `callback_needed` `not_interested` `wrong_number` `other` |
| `notes` | text | yes | |
| `app_version` | string(20) | yes | for debugging bad rows |
| `captured_at` | timestamp | yes | when device recorded it (≠ started_at) |
| `reviewed_at` | timestamp | yes | null = unreviewed queue |
| `created_at`/`updated_at` | timestamps | no | |
| `deleted_at` | softDeletes | yes | |

Indexes — exactly these:

```text
UNIQUE (client_uuid)
INDEX  (user_id, started_at)
INDEX  (project_id, started_at)
INDEX  (phone_e164)
INDEX  (user_id, android_call_log_id)
INDEX  (task_id)
INDEX  (reviewed_at)
INDEX  (device_uuid)
```

#### `call_devices`

| Column | Type | Null | Notes |
|--------|------|------|-------|
| `id` | bigint PK | no | |
| `user_id` | FK `users` | no | |
| `device_uuid` | string(64) | no | **UNIQUE** |
| `platform` | string(20) | no | `android` |
| `app_version` | string(20) | yes | |
| `app_version_code` | unsigned int | yes | |
| `os_version` | string(40) | yes | |
| `sdk_int` | unsigned smallint | yes | |
| `manufacturer` | string(60) | yes | |
| `model` | string(60) | yes | |
| `call_log_permission` | boolean | no | default false |
| `phone_state_permission` | boolean | no | default false |
| `contacts_permission` | boolean | no | default false |
| `notification_permission` | boolean | no | default false |
| `whatsapp_assist_enabled` | boolean | no | default false |
| `battery_optimization_ignored` | boolean | yes | |
| `consent_version` | string(20) | yes | decision #14 |
| `consent_accepted_at` | timestamp | yes | |
| `last_heartbeat_at` | timestamp | yes | |
| `last_scan_at` | timestamp | yes | last CallLog reconciliation |
| `last_call_captured_at` | timestamp | yes | |
| `revoked_at` | timestamp | yes | remote kill switch |
| `created_at`/`updated_at` | timestamps | no | |

```text
UNIQUE (device_uuid)
INDEX  (user_id, last_heartbeat_at)
```

Never store call payloads on this table.

#### `call_telemetry_events` (decision #15)

`id`, `user_id` (nullable FK), `device_uuid` string(64) nullable, `type` string(40)
(`crash` `permission_denied` `sync_failure` `capture_gap`), `message` text nullable,
`context` json nullable, `app_version` string(20) nullable, `occurred_at` timestamp,
`created_at`/`updated_at`. Index `(type, occurred_at)`, `(device_uuid, occurred_at)`.

**Do not add**: `interview_id`, `users.call_default_project_id`, `work_logs.call_session_id`.
All three are explicitly deferred (decisions #6, #3, and spec §5.8).

### 5.2 Enums

New files in `app/Enums/`, string-backed, matching the existing style found in Stage A:
`CallDirection`, `CallChannel`, `CallSubChannel`, `CallConfidence`, `CallSource`, `CallOutcome`,
`TelemetryEventType`. Each gets a `label(): string` method if the repo's existing enums have one.

### 5.3 Models

- `App\Models\CallSession` — SoftDeletes, casts for all enums + `answered`/`is_personal` bool +
  `started_at`/`ended_at`/`reviewed_at`/`captured_at` to `immutable_datetime`.
  Relations: `user()`, `project()`, `task()`, `milestone()`, `device()`.
  Scopes: `scopeForUser`, `scopeBetween($from,$to)`, `scopeBillableForKpi()`
  (= `where('is_personal', false)->whereIn('confidence', ['high','medium'])`), `scopeUnreviewed()`.
- `App\Models\CallDevice` — relations `user()`, `sessions()`. Accessor `isHealthy` (heartbeat < 24h,
  `call_log_permission` true, `revoked_at` null).
- `App\Models\CallTelemetryEvent`.

Factories for all three. Policies: `CallSessionPolicy`, `CallDevicePolicy` — see §6.5.

### 5.4 Config

`config/callpulse.php`:

```php
return [
    'workday_seconds'    => env('CALLPULSE_WORKDAY_SECONDS', 28800),
    'timezone'           => env('CALLPULSE_TZ', 'Asia/Kolkata'),
    'retention_months'   => env('CALLPULSE_RETENTION_MONTHS', 24),
    'consent_version'    => env('CALLPULSE_CONSENT_VERSION', '1.0'),
    'consent_policy_url' => env('CALLPULSE_CONSENT_URL'),
    'clock_skew_minutes' => 10,      // reject started_at further in the future than this
    'batch_max'          => 200,     // max rows per /call-sessions/batch
    'dedupe_window_sec'  => 30,
    'min_app_version_code' => env('CALLPULSE_MIN_APP_VERSION_CODE', 1),
];
```

### Acceptance

- `php artisan migrate:fresh` clean, then `php artisan migrate:rollback` clean.
- Factories produce valid rows; `CallSession::factory()->count(50)->create()` works.
- A test asserts none of the forbidden tables exist (`call_recordings`, `raw_android_call_logs`, …).
- `work_logs` and `users` schema unchanged — assert via a schema diff test or manual review.

---

## 6. STAGE C — Laravel API v1

Routes in `routes/api.php` under prefix `v1`, middleware `auth:sanctum` except login.
Namespace `App\Http\Controllers\Api\V1`. Follow the response convention found in Stage A;
if none exists, use §Appendix B.

### 6.1 Endpoint list

| Method | Path | Purpose |
|--------|------|---------|
| POST | `/api/v1/auth/login` | email + password + device info → token |
| POST | `/api/v1/auth/refresh` | rotate token (decision #7) |
| POST | `/api/v1/auth/logout` | delete current token |
| GET | `/api/v1/me` | user + roles |
| GET | `/api/v1/bootstrap` | **one call**: me, projects, workday seconds, feature flags, min app version, consent version |
| GET | `/api/v1/projects/mine` | projects the user may log calls against |
| GET | `/api/v1/projects/{id}/milestones` | |
| GET | `/api/v1/projects/{id}/tasks?q=&status=open` | task picker, paginated |
| GET | `/api/v1/projects/{id}/playbook-templates?group=` | task templates for follow-ups |
| POST | `/api/v1/call-sessions` | ingest one, idempotent on `client_uuid` |
| POST | `/api/v1/call-sessions/batch` | ingest ≤ `batch_max` rows, per-row result |
| PATCH | `/api/v1/call-sessions/{id}` | review: project, task, milestone, outcome, notes, is_personal |
| POST | `/api/v1/call-sessions/{id}/create-task` | playbook or custom → creates `tasks` row, links it |
| DELETE | `/api/v1/call-sessions/{id}` | soft delete, owner only, within 24h of `started_at` |
| GET | `/api/v1/call-sessions?from=&to=&user_id=&project_id=&reviewed=` | paginated list |
| POST | `/api/v1/devices/register` | upsert `call_devices` by `device_uuid` |
| POST | `/api/v1/devices/heartbeat` | permission health + counters |
| POST | `/api/v1/devices/consent` | record consent version + timestamp |
| POST | `/api/v1/telemetry/events` | batch crash/permission events |
| GET | `/api/v1/analytics/call-busy?date=&user_id=` | one day |
| GET | `/api/v1/analytics/call-busy/range?from=&to=&user_id=` | multi-day |
| GET | `/api/v1/app/latest-version` | in-app update check (§18.4) |
| GET | `/api/v1/app/download` | authenticated APK download |

Full request/response shapes: **§21**. Implement exactly those shapes.

### 6.2 Ingest rules (the part that matters most)

`POST /call-sessions` and `/call-sessions/batch` share one `CallSessionIngestService`:

1. **Auth + device gate.** Token's user owns the payload's `device_uuid`. Device must exist,
   `revoked_at` null, and have a `consent_accepted_at` — else `409 {code: consent_required}`.
2. **Upsert by `client_uuid`.** If it exists → update only if not yet `reviewed_at`
   (a reviewed row is authoritative on the server; a re-sent draft must not clobber a human
   correction). Return `200` with the existing row either way. Never 500 on a duplicate.
3. **Secondary dedupe**: same `user_id` + non-null `android_call_log_id` → treat as the same call,
   merge into the existing row and return its id.
4. **Fuzzy dedupe** (manual/WhatsApp rows only): same `user_id` + `phone_e164` + `channel` +
   `started_at` within `dedupe_window_sec` → merge.
5. **Validation**
   - `project_id` required, must be in the user's accessible project scope (reuse the scope found
     in Stage A). Reject with `422` if not — do **not** silently reassign.
   - `task_id`, if present: task must exist and its `project_id` must equal the payload's
     `project_id`. **Rule chosen: reject mismatches (422).** Do not auto-move the call.
   - `milestone_id`, if present: milestone's `project_id` must equal `project_id`.
   - `ended_at >= started_at` when present.
   - For `channel = cellular` **and** `confidence = high`: `|duration_sec − (ended_at − started_at)|`
     must be ≤ 2s, else downgrade `confidence` to `medium` server-side and log a
     `capture_gap` telemetry event. Do not reject.
   - `started_at` may not be more than `clock_skew_minutes` in the future → `422`.
   - `started_at` may not be older than 90 days → `422` (stops a reinstall from replaying ancient logs).
6. **Normalisation**: server re-normalises `phone_e164` with `giggsey/libphonenumber-for-php`
   (default region IN). If the device already sent a valid E.164, keep it; if they disagree, trust
   the server and record a telemetry event.
7. **Never** create tasks, work logs, or projects as a side effect of ingest.
8. Wrap batch ingest in a transaction **per row**, not per batch — one bad row must not lose 199 good ones.
   Response returns `{ results: [{ client_uuid, status: created|updated|duplicate|rejected, id?, errors? }] }`
   with HTTP `207`-style semantics (use `200` with per-row status; the app reads the array).

### 6.3 Task creation endpoint

`POST /call-sessions/{id}/create-task`

```json
{ "mode": "playbook|custom", "template_id": 12, "title": "…", "milestone_id": 5, "category": "…", "priority": "…", "due_date": "2026-08-14" }
```

- `playbook` → resolve `TaskTemplate`, build the task **through the existing template→task code path
  discovered in Stage A**. If none exists, replicate its field mapping exactly and note it in DEVIATIONS.md.
- `custom` → title required, category from existing `TaskCategory` enum.
- Default title when blank: `Call follow-up · {phone_display or contact_name} · {d M Y}`.
- Assign to the authenticated user. Status = the repo's existing "open/todo" default.
- Project = the call's `project_id`. Milestone optional, validated against project.
- On success: set `call_sessions.task_id`, set `reviewed_at = now()` if null, return the call session
  with the embedded task.
- **Idempotency**: if the call already has a `task_id`, return `409 {code: task_already_linked}`.

### 6.4 Analytics

`App\Services\CallAnalytics` — pure, unit-testable, no HTTP.

- `workdaySecondsFor(User, CarbonImmutable $date): int` — the single resolver from decision #5.
- `busyDay(User $u, CarbonImmutable $date, string $tz): array` — returns the §21 payload.
- **Talk time = interval merge, not `SUM(duration_sec)`.** Build `[started_at, ended_at]` intervals
  for rows where `answered = true`, sort, merge overlaps, sum merged lengths. Source spec §5.4.8 —
  this is the single most commonly botched requirement. Write the merge as a standalone tested
  function `mergeIntervals(array $intervals): array`.
- Exclude `is_personal = true` and `confidence = low` from headline KPIs. Return `low` confidence
  totals separately as `estimated_talk_seconds` so the UI can show "Estimated" (spec §8).
- Hour buckets are in `Asia/Kolkata`; a call spanning 10:58–11:04 contributes to **both** hours
  proportionally, not wholly to the start hour.
- `by_project`, `by_channel`, `unreviewed_count`, `task_coverage_pct` per spec §8.

### 6.5 Authorization

`CallSessionPolicy`:

- `view` — owner, or a user with team-visibility over the call's project/user per the existing role
  rules found in Stage A.
- `update` / `delete` — **owner only**, and `delete` only within 24h of `started_at`.
- `viewAny` with a `user_id` filter — only if the requester manages that user.
- Number unmasking — separate ability `viewNumbers`, granted to owner + admin (decision #12).

`CallDevicePolicy`: user sees own devices; managers/admin see team devices.

Register both in `AuthServiceProvider`. **Every** controller action calls `authorize()`.

### 6.6 Rate limits

`throttle:60,1` on ingest, `throttle:10,1` on login, `throttle:120,1` elsewhere.

### Acceptance

- Feature test per endpoint. Minimum set:
  - login returns a token; wrong password 422; throttled after 10 tries.
  - ingest same `client_uuid` twice → one row, second returns `updated` or `duplicate`.
  - ingest a reviewed row again → server value preserved.
  - ingest with `project_id` the user cannot access → 422, no row.
  - batch of 3 where row 2 is invalid → rows 1 and 3 created, row 2 `rejected`.
  - device without consent → 409.
  - `create-task` twice → second is 409, no orphan task.
  - deleting the linked task → call row survives with `task_id` null.
  - analytics: two overlapping calls 10:00–10:10 and 10:05–10:15 → busy = 900s, **not** 1200s.
  - a manager cannot read another team's calls; a non-owner sees a masked number.
- `php artisan route:list --path=api/v1` matches §6.1 exactly.
- A Postman/Bruno collection exported to `docs/callpulse/CallPulse.postman_collection.json`.

---

## 7. STAGE D — Laravel web dashboard (where you actually see the data)

This is the stage that answers *"I want to see the data in our existing Laravel system."*
Build with whatever the Stage A recon says the app already uses (Livewire 3 / Blade / Inertia) and
the **existing layout component**. Do not introduce a new UI stack or a second chart library.

### 7.1 Pages

| Route | Page | Audience |
|-------|------|----------|
| `/calls/my-day` | **My Call Day** — date picker, timeline of merged busy blocks on a 24h (or workday-bounded) axis, totals card (talk time, busy %, call count, cellular vs WhatsApp), list below with inline review actions | employee |
| `/calls/unreviewed` | **Unreviewed queue** — calls with `reviewed_at` null, bulk-assign project, mark personal | employee |
| `/calls/team` | **Team Call Busy** — date or range picker, one row per employee, horizontal busy bar, sortable totals, drill into a person's day | PM / HR / admin |
| `/calls/projects` | **Project Call Load** — talk time grouped by project over a range, plus task-coverage % | PM / admin |
| `/calls/devices` | **Device health** — per employee: app version, last heartbeat, permission flags, last capture. Red row if heartbeat > 24h or `call_log_permission` false | admin |

Every page: reuses the same `CallAnalytics` service as the API. **No duplicated aggregation logic
between web and API** — if you find yourself writing the same GROUP BY twice, extract it.

### 7.2 Details that matter

- Timeline: render merged intervals (§6.4), colour by project, hatch pattern for `confidence = low`
  with an "Estimated" legend, and grey for `is_personal`.
- Number masking respected in every Blade/Livewire view — call the same resource/helper the API uses.
- CSV export on `/calls/team` and `/calls/projects` (streamed, respects masking and policy).
- Empty states that say something useful ("No calls captured — check device health").
- Add the nav entries to the existing sidebar guarded by the same role checks as the policies.

### Acceptance

- Livewire/feature tests: each page loads for an authorised role, 403s for an unauthorised one.
- Numbers on `/calls/my-day` match `GET /api/v1/analytics/call-busy` byte for byte for the same
  user+date (write a test asserting exactly this).
- Manually seeded demo data renders a sane timeline.

---

## 8. STAGE E — Laravel ops & tests

- `php artisan callpulse:purge-old-sessions` — hard-deletes beyond `retention_months`. Scheduled daily.
- `php artisan callpulse:device-health-report` — logs/notifies devices with no heartbeat in 48h. Scheduled daily 09:30 IST.
- Audit trail: if `spatie/laravel-activitylog` exists (Stage A #19), log `CallSession` create/update
  with `causer`. If not, skip — do **not** add the package just for this in MVP.
- Seeder `CallPulseDemoSeeder` — 3 users × 7 days × ~12 calls, some unreviewed, some personal,
  some overlapping, one `low` confidence. Used by dashboard tests and manual QA.
- Full test suite green. Target ≥ 85% coverage on `App\Services\CallAnalytics` and the ingest service.

---

## 9. Backend → Android handoff gate

**Do not start Stage F until all of these are true.** This is the gate the project lead enforces.

- [ ] Staging API live over HTTPS with a valid certificate.
- [ ] Postman collection runs green against staging.
- [ ] A test employee account exists with membership in ≥ 2 projects.
- [ ] `GET /api/v1/bootstrap` returns projects, workday seconds, consent version.
- [ ] §21 contract frozen and committed.

---

## 10. STAGE F — Android project scaffold

New repo `callpulse-android`.

### Stack (locked)

- Kotlin 2.x, JDK 17, AGP current stable, Gradle version catalog (`gradle/libs.versions.toml`)
- `minSdk 26`, `compileSdk 36`, `targetSdk 36`
- Jetpack **Compose** (Material 3), single Activity + Navigation Compose
- **Hilt** DI
- **Room** (+ SQLCipher, §11.3) — drafts and offline queue only
- **WorkManager** — sync and reconciliation
- **Retrofit** + OkHttp + **kotlinx.serialization** (pick one JSON lib and stick to it)
- **DataStore (Preferences)** — sticky project, settings
- `libphonenumber-android` — E.164 normalisation, default region IN
- No Firebase, no Google Play Services dependency (decision #15, and it keeps the APK installable
  on devices without GMS)

### Module layout

```text
callpulse-android/
  app/                 # Application, DI graph, navigation, MainActivity
  core-common/         # Result types, time utils, phone utils, logging
  core-network/        # Retrofit, interceptors, DTOs, ApiService
  core-data/           # Room, DataStore, repositories, sync orchestration
  core-ui/             # theme, shared composables
  feature-auth/        # login, onboarding, permissions wizard
  feature-capture/     # receivers, workers, CallLog reader
  feature-review/      # post-call review sheet, task picker/creator
  feature-day/         # day timeline + totals
  docs/                # copy of this file + API contract
```

### Build config

```kotlin
// app/build.gradle.kts (illustrative)
android {
    namespace = "com.proponenttech.callpulse"
    defaultConfig {
        applicationId = "com.proponenttech.callpulse"
        minSdk = 26; targetSdk = 36
        versionCode = 1
        versionName = "0.1.0"
    }
    buildTypes {
        debug   { applicationIdSuffix = ".debug"; isMinifyEnabled = false }
        release { isMinifyEnabled = true; isShrinkResources = true
                  proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro") }
    }
    flavorDimensions += "env"
    productFlavors {
        create("staging")    { buildConfigField("String","API_BASE_URL","\"https://staging.pm.proponenttech.com/api/v1/\"")
                               applicationIdSuffix = ".staging" }
        create("production") { buildConfigField("String","API_BASE_URL","\"https://pm.proponenttech.com/api/v1/\"") }
    }
}
```

- `versionCode` scheme: integer, monotonically increasing, **never reused** (`1, 2, 3…`).
  `versionName` semver `MAJOR.MINOR.PATCH`.
- Two flavors so QA phones can hold staging and production side by side.
- R8 rules for Retrofit, kotlinx.serialization, Room, and Hilt. Verify the **release** build works —
  a minified build that crashes on JSON parsing is the classic Stage F failure.

### Acceptance

- `./gradlew assembleStagingDebug assembleProductionRelease` both succeed.
- App installs and shows a login screen.
- `docs/` contains this file and the frozen API contract.

---

## 11. STAGE G — Auth, bootstrap, secure storage

### 11.1 Login

- Email + password → `POST /auth/login`, sending `device_uuid`, `platform`, `app_version`,
  `app_version_code`, `os_version`, `sdk_int`, `manufacturer`, `model`.
- `device_uuid`: generate once with `UUID.randomUUID()` on first launch, store in encrypted prefs.
  **Do not** use `ANDROID_ID`, IMEI, or any hardware identifier.
- On success: store token, call `GET /bootstrap`, cache projects, workday seconds, consent version,
  feature flags, `min_app_version_code`.

### 11.2 Token lifecycle (decision #7)

- OkHttp `Authenticator`/interceptor adds `Authorization: Bearer`.
- A `TokenRefreshWorker` runs daily; if `expires_at − now < 14 days`, call `POST /auth/refresh`.
- On `401`: clear token, mark session invalid, show login. **Do not clear the Room DB** — unsynced
  drafts must survive re-login and re-attach to the same user id.
- On `403 device_revoked`: wipe local DB and prefs, show "Access revoked, contact admin".

### 11.3 Encryption

- Room encrypted with **SQLCipher** (`net.zetetic:sqlcipher-android`); passphrase generated once,
  stored via `EncryptedSharedPreferences` backed by the Android Keystore.
- Token stored in `EncryptedSharedPreferences`, never in DataStore plaintext or Room.
- Nothing sensitive in logs. `Timber` debug tree only in debug builds; release logs no numbers.

### 11.4 Sticky project

- `default_project_id` in DataStore (decision: on-device only, per spec §5.2C MVP preference).
- Header chip `Working on: {Project}` on every screen; tap → project picker.
- Changing it affects **future** calls only. Never rewrite historical drafts.
- If the cached default project disappears from `/projects/mine` on refresh, prompt for a new one
  and block capture review until chosen.

### Acceptance

- Login → bootstrap → project picker → home, on a clean install.
- Kill and relaunch: still logged in, project remembered.
- Server-side token revoke → app lands on login within one heartbeat, drafts intact.

---

## 12. STAGE H — Permissions, consent, OEM onboarding

Runs immediately after first login, as a stepper. Each step explains **why** before the system dialog.

| Step | Permission | Required? | Notes |
|------|-----------|-----------|-------|
| 1 | Consent screen | **Yes** | Full disclosure text + link to policy PDF + explicit checkbox. On accept → `POST /devices/consent` with `consent_version`. Capture stays disabled until this returns 2xx. |
| 2 | `READ_CALL_LOG` | **Yes** | Core. Without it the app is useless — show a blocking state, not a silent failure. |
| 3 | `READ_PHONE_STATE` | Yes | Faster "call ended" detection. |
| 4 | `POST_NOTIFICATIONS` (API 33+) | Yes | Review prompts. |
| 5 | `READ_CONTACTS` | **Optional** | Only to show a name instead of a bare number. App must fully work without it. |
| 6 | Battery optimisation exemption | Recommended | `ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS` — prompt, never silent. |
| 7 | OEM autostart | Recommended | Xiaomi/Oppo/Vivo/Realme/Samsung deep links + screenshots. See §12.2. |

Manifest permissions:

```xml
<uses-permission android:name="android.permission.READ_CALL_LOG"/>
<uses-permission android:name="android.permission.READ_PHONE_STATE"/>
<uses-permission android:name="android.permission.READ_CONTACTS"/>
<uses-permission android:name="android.permission.POST_NOTIFICATIONS"/>
<uses-permission android:name="android.permission.INTERNET"/>
<uses-permission android:name="android.permission.ACCESS_NETWORK_STATE"/>
<uses-permission android:name="android.permission.RECEIVE_BOOT_COMPLETED"/>
<uses-permission android:name="android.permission.REQUEST_IGNORE_BATTERY_OPTIMIZATIONS"/>
<uses-permission android:name="android.permission.FOREGROUND_SERVICE"/>
<uses-permission android:name="android.permission.FOREGROUND_SERVICE_DATA_SYNC"/>
```

No `READ_SMS`. No `RECORD_AUDIO`. No `READ_EXTERNAL_STORAGE`. If you find yourself adding one, stop.

### 12.1 Permission health

A `HeartbeatWorker` (every 6h + on app open) posts current permission booleans to
`POST /devices/heartbeat`. This is how managers see "employee turned it off" without nagging in-app.
Also show a persistent in-app banner when a required permission is missing.

### 12.2 OEM checklist screen

A settings screen with a per-manufacturer card: detect `Build.MANUFACTURER`, show the right
instructions, and an intent to the OEM autostart/battery screen where a known one exists
(wrap every such intent in `try/catch` — these Activities vary by ROM and throw
`ActivityNotFoundException` freely). Always provide manual text steps as the fallback.

### Acceptance

- Denying `READ_CALL_LOG` shows a clear blocking screen with a "Grant" button, no crash.
- Consent not accepted → capture disabled, and any queued row is rejected by the server with 409
  and surfaced in the UI.
- Heartbeat visible on the Laravel `/calls/devices` page within 5 minutes.

---

## 13. STAGE I — Call capture engine (the heart of the app)

### 13.1 Architecture — replaces the spec's always-on foreground service

The source spec §7 proposes a permanently-running foreground `CallLogObserverService`.
**We are not doing that** (decision #10), for three reasons: modern Android places runtime caps on
long-lived `dataSync` foreground services; a permanent notification invites employees to force-stop
the app; and OEM battery managers kill it anyway. Reconciliation-based capture is more reliable and
uses less battery.

Four cooperating mechanisms:

```text
1. PhoneStateReceiver      (manifest <receiver>, android.intent.action.PHONE_STATE)
     OFFHOOK → remember "a call is in progress"
     IDLE    → enqueue an *expedited* OneTimeWorkRequest (CallLogScanWorker), ~2s delay
               (the CallLog row is written slightly after IDLE — retry up to 3× at 2s/5s/10s)

2. CallLogScanWorker       (the ONLY thing that reads CallLog — single code path)
     Reads CallLog rows WHERE DATE > last_scanned_call_date
     Maps each row → CallSessionEntity draft (see Appendix A)
     Dedupes, persists, fires the review notification for the newest one

3. PeriodicReconcileWorker (every 15 min, plus on app foreground, plus on connectivity regain)
     Same scan. Catches everything missed while the process was dead, the phone was rebooting,
     or the OEM killed us. THIS is what makes capture reliable — not the receiver.

4. BootCompletedReceiver   (RECEIVE_BOOT_COMPLETED)
     Re-enqueues the periodic worker and runs one immediate scan.
```

A foreground service is used **only** if a scan needs to outlive the receiver's window — start it
with `foregroundServiceType="dataSync"`, do the scan, stop it. Seconds, not hours.

### 13.2 Watermark, not "latest row"

Persist `last_scanned_call_date` (epoch millis of the newest CallLog `DATE` processed) and
`last_scanned_call_id` in DataStore. Every scan queries `DATE > watermark` ordered ascending and
processes **all** new rows, not just the most recent one. Rapid back-to-back calls and
post-reboot backfill both depend on this.

**First-run backfill:** on first successful login, seed the watermark to `now − 24h` and import that
window so the employee immediately sees something. Do not import months of history —
the server rejects anything older than 90 days anyway (§6.2).

### 13.3 Local dedupe

Before inserting a draft:

1. Same `android_call_log_id` already in Room → skip.
2. Same number + `started_at` within 60s + same channel → skip (guards the receiver/periodic race).
3. Generate `client_uuid` once at draft creation and **never regenerate it** — it is the server's
   idempotency key. Regenerating on retry is the #1 way to create duplicate rows in production.

### 13.4 CallLog field semantics — read this carefully

- `CallLog.Calls.DATE` — epoch millis when the call **started** (placed or received). → `started_at`.
- `CallLog.Calls.DURATION` — **connected talk seconds**. `0` for missed/rejected/unanswered.
  → `duration_sec`.
- `ended_at` = `started_at + duration_sec`. This is an **approximation** — for outgoing calls the
  ringing period is not separated out by the platform. Document it in code and accept it.
- **`ring_sec` is generally not obtainable.** Leave it `null` for cellular. Only populate it if the
  telephony state machine actually measured OFFHOOK-to-connect, and mark `confidence = medium`
  if you do. Do not fabricate it.
- `CallLog.Calls.TYPE` → direction, per Appendix A.
- `CallLog.Calls.NUMBER` may be empty/`-1`/`private` for hidden numbers → store `null` in
  `phone_e164`, keep whatever string in `phone_display`.
- `CallLog.Calls.CACHED_NAME` → `contact_name` (only if contacts permission granted).
- `CallLog.Calls.PHONE_ACCOUNT_ID` → `phone_account_id` (dual SIM).
- Normalise to E.164 with libphonenumber, default region `IN`. If normalisation fails, keep
  `phone_e164 = null` rather than storing garbage.

### 13.5 Review notification

- On new draft: post a notification "Call with {name or number} · {duration} — tap to assign project".
- Tapping deep-links to the review sheet (§14).
- If the user has "auto-open review sheet" enabled and the app is foregrounded, open the sheet directly.
- Dismissing leaves the draft **unreviewed** — it still counts toward busy time (spec §4.3).
- Coalesce: if 3 calls arrive in 5 minutes, show one notification "3 calls need review".

### Acceptance (test on real hardware, both phones)

- Incoming answered call → draft within ~5s, duration within ±2s of the dialer.
- Outgoing unanswered → `direction = outbound`, `answered = false`, `duration_sec = 0`.
- Two calls within 30s → two drafts, no duplicates.
- Force-stop the app, make 3 calls, reopen → all 3 appear after the first scan.
- Reboot, make a call before opening the app → captured by boot receiver + periodic worker.
- Airplane mode → drafts created locally, sync queued.
- Hidden/private number → row created with null `phone_e164`, no crash.

---

## 14. STAGE J — Review sheet & task creation

Compose bottom sheet, reachable from notification, day list, or the unreviewed badge.

Contents, top to bottom (spec §4.3):

1. **Header** — number/contact name, channel icon, direction, start time, duration.
2. **Project** — pre-selected to sticky default, tappable to change. Required.
3. **Personal toggle** — "This was a personal call" (decision #11). When on, project/task fields
   collapse and the row is excluded from KPI. Still saved and synced.
4. **Task** — search open tasks on the chosen project (`GET /projects/{id}/tasks?q=`, debounced 300ms,
   cached). Selecting one links it.
5. **Or create task** — segmented `Playbook | Custom`.
   - Playbook: list templates from `GET /projects/{id}/playbook-templates`, optional milestone picker.
   - Custom: title (prefilled `Call follow-up · {number} · {date}`), category, optional milestone.
   - Never create a task without an explicit tap on "Create".
6. **Outcome chips** — connected / no answer / callback needed / not interested / wrong number / other.
7. **Notes** — one short free-text field.
8. **Save** → sets `reviewed_at` locally, marks `sync_status = pending`, closes.

Rules:

- Changing project **clears** a selected task if that task belongs to a different project
  (the server rejects mismatches — §6.2).
- Task creation calls `POST /call-sessions/{id}/create-task`, which requires the call to already
  exist server-side. If the call is still `pending` sync, **sync it first, then create the task** —
  chain the operations, do not fire them in parallel.
- Offline: allow full review, queue everything. Task creation while offline queues a
  `PendingTaskCreation` record processed by the sync worker after the call session syncs.

### Acceptance

- Review a call offline, go online → call syncs, then task is created, then `task_id` links back.
- Created task is visible in the PM web UI on the right project, assigned to the employee.
- Attempting to link a task from another project is prevented in the UI, not just by the server.

---

## 15. STAGE K — Sync engine

`SyncWorker` (WorkManager, `NetworkType.CONNECTED`, exponential backoff from 30s, max 5 attempts
then park until next periodic run).

Triggers: on draft created, on review saved, every 15 min periodic, on connectivity regain, on app foreground.

Order of operations in one run:

1. `POST /devices/heartbeat` (cheap, also validates the token).
2. Push pending call sessions via `POST /call-sessions/batch` in chunks of ≤ 100.
   Per-row result handling:
   - `created` / `updated` / `duplicate` → mark `synced`, store `server_id`.
   - `rejected` with a validation error → mark `failed`, store the error, **surface it in the UI**
     (a silently failing row is worse than a visible one). Do not retry a 422 forever.
3. Process `PendingTaskCreation` queue.
4. Pull refresh: projects, milestones, open tasks, templates (ETag / `updated_since` if the API
   supports it; otherwise a plain refresh no more than once an hour).
5. Push queued telemetry events.
6. Update `last_sync_at`; show it in Settings so employees can self-diagnose.

Sync status per row in Room: `pending | syncing | synced | failed`. Never delete a local row just
because it synced — keep it for the day view, purge locally after 30 days.

### Acceptance

- 50 offline drafts → one batch run syncs all, dashboard shows 50.
- Kill the app mid-sync → no duplicates on the server (`client_uuid` does its job).
- A row rejected with 422 shows an actionable message in the app.

---

## 16. STAGE L — Day view (employee)

- Date picker (default today), timezone `Asia/Kolkata`.
- **Timeline**: merged busy blocks on a workday axis, coloured by project. Same interval-merge
  semantics as the server (§6.4) — and a unit test asserting the client total equals the server's
  `talk_seconds` for the same day.
- **Totals card**: talk time, busy % of workday, call count, cellular vs WhatsApp split,
  unreviewed count (tappable → unreviewed queue).
- **List**: calls chronologically, with project chip, task link, unreviewed badge, personal badge.
- Pull to refresh → `GET /analytics/call-busy?date=`. Prefer the server's numbers when online;
  fall back to local computation when offline and label it "offline estimate".

---

## 17. STAGE M — WhatsApp manual logging (MVP scope only)

Decision #2: **manual only**. No AccessibilityService in this build.

- A FAB / quick-settings tile "Log a WhatsApp call".
- Fields: number (contact picker or manual), voice/video (`sub_channel`), direction,
  start time (default: now minus entered duration), duration (minute/second picker), then the
  normal review fields.
- Sets `channel = whatsapp_manual`, `source = manual`, `confidence = medium`.
- Optional nudge: after the user returns from WhatsApp *if* they have opted into it — implement only
  as a plain "Did you just have a WhatsApp call?" prompt triggered by the user, never by UI sniffing.
- Write the `whatsapp` channel and `accessibility` source enum values into the app now so Phase 3
  needs no schema change, but keep the assist code path out of this build entirely.

---

## 18. STAGE N — APK build, signing, distribution

### 18.1 Keystore

```bash
keytool -genkeypair -v \
  -keystore callpulse-release.jks -keyalg RSA -keysize 4096 -validity 10000 \
  -alias callpulse -dname "CN=ProponentTech, OU=IT, O=ProponentTech, L=..., C=IN"
```

- Generate **once**. Back it up in the company password manager **and** an offline copy.
- Losing it means every employee must uninstall/reinstall to take an update. Treat it as a company asset.
- Never in git. Credentials go in `~/.gradle/gradle.properties` or `local.properties` (both gitignored):

```properties
CALLPULSE_STORE_FILE=/absolute/path/callpulse-release.jks
CALLPULSE_STORE_PASSWORD=…
CALLPULSE_KEY_ALIAS=callpulse
CALLPULSE_KEY_PASSWORD=…
```

```kotlin
signingConfigs {
    create("release") {
        storeFile = file(providers.gradleProperty("CALLPULSE_STORE_FILE").get())
        storePassword = providers.gradleProperty("CALLPULSE_STORE_PASSWORD").get()
        keyAlias = providers.gradleProperty("CALLPULSE_KEY_ALIAS").get()
        keyPassword = providers.gradleProperty("CALLPULSE_KEY_PASSWORD").get()
        enableV1Signing = true; enableV2Signing = true; enableV3Signing = true
    }
}
```

### 18.2 Building the APK

```bash
./gradlew clean assembleProductionRelease
# output: app/build/outputs/apk/production/release/app-production-release.apk
```

- Build **APK**, not AAB — there is no Play Store in this project (decision #1).
- Rename output to `callpulse-{versionName}-{versionCode}.apk`.
- Verify before every distribution:
  ```bash
  apksigner verify --print-certs callpulse-1.0.0-3.apk
  ```
- Bump `versionCode` on **every** build you hand to anyone. Tag the commit `v1.0.0+3`.
- Test the **release** build, not just debug — R8 breaks reflection-based JSON silently.

### 18.3 Getting it onto phones

Preferred order:

1. **MDM** (if ProponentTech has one) — silent install, managed config for `API_BASE_URL`.
2. **Authenticated download from `pm-system`**: `GET /api/v1/app/download` plus a web page at
   `/calls/app` that a logged-in employee opens on the phone. Serve with
   `Content-Type: application/vnd.android.package-archive`.
3. Manual sideload as the fallback: employees enable "Install unknown apps" for their browser.

Ship a one-page install guide with screenshots (Play Protect will warn — tell people in advance
that "Install anyway" is expected for internal apps, and get IT to say so too).

### 18.4 In-app update (mandatory, because there is no Play Store)

- On app start and once daily: `GET /api/v1/app/latest-version` →
  `{ version_code, version_name, url, mandatory, notes, min_supported_version_code }`.
- If `version_code > BuildConfig.VERSION_CODE` → show an update card with release notes.
- If `BuildConfig.VERSION_CODE < min_supported_version_code` → **block the app** with a
  "Update required" screen. This is your only lever to retire a broken build.
- Download via the authenticated endpoint, then `Intent.ACTION_VIEW` on a `FileProvider` URI with
  `REQUEST_INSTALL_PACKAGES`. Add that permission only when you implement this step.
- Server side: a small `call_app_releases` table (`version_code`, `version_name`, `apk_path`,
  `notes`, `mandatory`, `min_supported_version_code`, `published_at`) plus an admin upload page.

### 18.5 Managed configuration

Declare an `<app-restrictions>` XML with `api_base_url` so MDM can point a build at staging without
a rebuild. Read it via `RestrictionsManager`; fall back to `BuildConfig.API_BASE_URL`.

### 18.6 Network security

- HTTPS only. `android:usesCleartextTraffic="false"`.
- If staging must use a private CA, add a `network_security_config.xml` that trusts it **for the
  staging flavor only**. Never ship a custom trust anchor or a cert-bypass in production.
- Certificate pinning: optional, and only once the production cert renewal process is understood —
  a pinned app dies the day the cert rotates.

---

## 19. STAGE O — QA matrix

Run on **at least** one Pixel/stock device and one Xiaomi/Oppo/Vivo/Realme. Record results in
`docs/callpulse/QA_RESULTS.md` with device, OS version, and pass/fail.

| # | Scenario | Expected |
|---|----------|----------|
| 1 | Incoming answered cellular | duration matches dialer ±2s; review prompt appears |
| 2 | Outgoing answered | correct direction; correct project = sticky default |
| 3 | Outgoing unanswered | `answered=false`, `duration_sec=0`, still logged |
| 4 | Incoming missed | `direction=missed`, logged |
| 5 | Rejected call | `direction=rejected` |
| 6 | 3 rapid successive calls | 3 drafts, 0 duplicates |
| 7 | Call during airplane mode → online later | queued then synced, no duplicates |
| 8 | App force-stopped, then 2 calls, then open app | both captured by reconcile scan |
| 9 | Device reboot, call before opening app | captured |
| 10 | Change default project mid-day | new calls use new project; earlier rows unchanged |
| 11 | Create playbook task | task appears in PM web, correct project + assignee, `task_id` linked |
| 12 | Create custom task offline, go online | task created once, not twice |
| 13 | Link a task, then delete the task in PM web | call row survives, `task_id` null |
| 14 | Mark call personal | excluded from busy %, number masked for manager |
| 15 | Manual WhatsApp log (voice and video) | `channel=whatsapp_manual`, correct `sub_channel`, `confidence=medium` |
| 16 | Hidden/private number | logged, `phone_e164` null, no crash |
| 17 | Dual-SIM call on SIM 2 | `phone_account_id` recorded |
| 18 | Employee not a member of Project X | Project X not selectable; direct API attempt → 422 |
| 19 | Manager opens team day view | sees the team, numbers masked per decision #12 |
| 20 | Revoke `READ_CALL_LOG` in settings | banner appears; heartbeat reports it; `/calls/devices` turns red |
| 21 | Token revoked server-side | app forces re-login, unsynced drafts survive |
| 22 | Two overlapping calls (cellular + manual WA) | busy time = wall clock, not the sum |
| 23 | 8h battery soak with 20 calls | app drain reported as negligible; no OEM kill |
| 24 | Release (minified) build of every flow | identical behaviour to debug |
| 25 | Install over a previous version | data preserved, Room migration clean |

### Room migrations

Every schema change ships a real `Migration` and an exported schema JSON in version control.
`fallbackToDestructiveMigration()` is **banned** past the first internal release — it silently eats
employees' unsynced calls.

---

## 20. STAGE P — Rollout

1. **Pilot**: one project team, 3–5 employees, 1 week. Daily check of `/calls/devices` and the
   unreviewed queue. Collect telemetry events.
2. Fix the top 3 issues. Ship a versionCode bump via in-app update — this also **tests the update
   path** before you need it in anger.
3. **Wave 2**: all counselling/interview roles.
4. Track the spec §14 success metrics weekly: install+permission coverage ≥80%, 24h review rate ≥70%,
   task-link rate ≥50%, and a manager able to answer "who was on calls 11:00–13:00 and for which project".
5. Only after those hold for two weeks: consider Phase 3 (WhatsApp assist) and Phase 4
   (manager heatmaps, alerts).

---

## 21. API reference (frozen contract)

All requests: `Accept: application/json`, `Content-Type: application/json`,
`Authorization: Bearer {token}` (except login), `X-App-Version-Code: {int}`.
All timestamps ISO-8601 **with offset**.

### POST /auth/login

```json
{
  "email": "priya@proponenttech.com",
  "password": "…",
  "device": {
    "device_uuid": "b1f0…", "platform": "android",
    "app_version": "1.0.0", "app_version_code": 3,
    "os_version": "14", "sdk_int": 34,
    "manufacturer": "Xiaomi", "model": "M2101K6G"
  }
}
```

```json
{
  "data": {
    "token": "12|abcdef…",
    "expires_at": "2026-11-09T00:00:00+05:30",
    "user": { "id": 42, "name": "Priya S", "email": "…", "roles": ["employee"] }
  }
}
```

### GET /bootstrap

```json
{
  "data": {
    "user": { "id": 42, "name": "Priya S", "roles": ["employee"] },
    "server_time": "2026-08-11T14:32:07+05:30",
    "timezone": "Asia/Kolkata",
    "workday_seconds": 28800,
    "consent": { "version": "1.0", "policy_url": "https://…/consent.pdf", "accepted_at": null },
    "app": { "min_supported_version_code": 1, "latest_version_code": 3 },
    "features": { "whatsapp_assist": false, "manual_whatsapp_log": true },
    "projects": [
      { "id": 1, "name": "Rishikul - DigiLead", "client_name": "Rishikul", "updated_at": "…" }
    ]
  }
}
```

### POST /call-sessions  ·  POST /call-sessions/batch

Single object, or `{ "sessions": [ … ] }` for batch (max `batch_max`).

```json
{
  "client_uuid": "7c3f9e2a-1b4d-4c8a-9f11-22aa33bb44cc",
  "device_uuid": "b1f0…",
  "project_id": 1,
  "task_id": null,
  "milestone_id": null,
  "phone_e164": "+919876543210",
  "phone_display": "+91 98765 43210",
  "contact_name": "Amit (candidate)",
  "direction": "inbound",
  "channel": "cellular",
  "sub_channel": null,
  "started_at": "2026-08-11T10:14:22+05:30",
  "ended_at": "2026-08-11T10:21:02+05:30",
  "tz_offset_minutes": 330,
  "duration_sec": 400,
  "ring_sec": null,
  "answered": true,
  "is_personal": false,
  "confidence": "high",
  "source": "call_log",
  "android_call_log_id": "84213",
  "phone_account_id": "sim1",
  "outcome": null,
  "notes": null,
  "app_version": "1.0.0",
  "captured_at": "2026-08-11T10:21:09+05:30",
  "reviewed_at": null
}
```

Batch response (HTTP 200, per-row status):

```json
{
  "data": {
    "results": [
      { "client_uuid": "7c3f…", "status": "created",  "id": 9001 },
      { "client_uuid": "9a2b…", "status": "duplicate","id": 8994 },
      { "client_uuid": "3d7e…", "status": "rejected", "errors": { "project_id": ["Not accessible."] } }
    ]
  }
}
```

### PATCH /call-sessions/{id}

Accepts any of: `project_id`, `task_id`, `milestone_id`, `outcome`, `notes`, `is_personal`,
`reviewed_at`. Returns the full `CallSessionResource`.

### POST /call-sessions/{id}/create-task

```json
{ "mode": "playbook", "template_id": 12, "milestone_id": 5, "due_date": "2026-08-14" }
```
```json
{ "mode": "custom", "title": "Follow up with Amit", "category": "…", "priority": "…", "milestone_id": null }
```

Returns the call session with `task` embedded. `409 task_already_linked` if `task_id` is set.

### CallSessionResource (response shape)

```json
{
  "id": 9001, "client_uuid": "7c3f…",
  "user": { "id": 42, "name": "Priya S" },
  "project": { "id": 1, "name": "Rishikul - DigiLead" },
  "task": { "id": 551, "title": "Call follow-up · Amit · 11 Aug 2026", "status": "open" },
  "milestone": null,
  "phone_e164": "+919876543210",
  "phone_display": "+91 98765 43210",
  "phone_masked": false,
  "contact_name": "Amit (candidate)",
  "direction": "inbound", "channel": "cellular", "sub_channel": null,
  "started_at": "2026-08-11T10:14:22+05:30",
  "ended_at": "2026-08-11T10:21:02+05:30",
  "duration_sec": 400, "answered": true, "is_personal": false,
  "confidence": "high", "source": "call_log",
  "outcome": null, "notes": null,
  "reviewed_at": null,
  "created_at": "2026-08-11T10:21:11+05:30"
}
```

When masked: `phone_e164` and `phone_display` become `"+91 98XXXXXX10"`, `contact_name` becomes
`null`, and `phone_masked` is `true`.

### GET /analytics/call-busy?date=YYYY-MM-DD&user_id=

```json
{
  "data": {
    "date": "2026-08-11",
    "timezone": "Asia/Kolkata",
    "user_id": 42,
    "workday_seconds": 28800,
    "talk_seconds": 6420,
    "estimated_talk_seconds": 300,
    "busy_ratio": 0.223,
    "calls": 14,
    "answered_calls": 11,
    "missed_calls": 3,
    "unreviewed_count": 4,
    "task_coverage_pct": 54.5,
    "personal_calls": 2,
    "by_hour": [ { "hour": 10, "talk_seconds": 900 }, { "hour": 11, "talk_seconds": 1200 } ],
    "by_channel": { "cellular": 4800, "whatsapp_manual": 1620 },
    "by_project": [ { "project_id": 1, "name": "Rishikul - DigiLead", "talk_seconds": 5100 } ],
    "busy_blocks": [ { "start": "2026-08-11T10:14:22+05:30", "end": "2026-08-11T10:21:02+05:30", "project_id": 1 } ]
  }
}
```

`busy_blocks` are the **merged** intervals — the client renders these directly, so the phone and the
web dashboard cannot disagree.

### POST /devices/heartbeat

```json
{
  "device_uuid": "b1f0…", "app_version": "1.0.0", "app_version_code": 3,
  "os_version": "14", "sdk_int": 34, "manufacturer": "Xiaomi", "model": "M2101K6G",
  "call_log_permission": true, "phone_state_permission": true,
  "contacts_permission": false, "notification_permission": true,
  "whatsapp_assist_enabled": false, "battery_optimization_ignored": true,
  "last_scan_at": "2026-08-11T14:30:00+05:30",
  "last_call_captured_at": "2026-08-11T13:02:11+05:30",
  "pending_sync_count": 0
}
```

Response includes `{ "revoked": false, "min_supported_version_code": 1 }` so the app can react.

### POST /devices/consent

```json
{ "device_uuid": "b1f0…", "consent_version": "1.0", "accepted_at": "2026-08-11T09:00:00+05:30" }
```

### GET /app/latest-version

```json
{
  "data": {
    "version_code": 3, "version_name": "1.0.0",
    "min_supported_version_code": 1,
    "mandatory": false,
    "notes": "Faster call capture after reboot.",
    "url": "https://pm.proponenttech.com/api/v1/app/download?v=3"
  }
}
```

---

## 22. What this document adds to the original spec

Recorded so the changes are reviewable, not silently smuggled in. Each item is either a
correctness fix or a gap that would have bitten us in production.

| # | Addition | Why it was needed |
|---|----------|-------------------|
| 1 | **Reconciliation-scan capture** replacing the always-on foreground service (§13.1) | The spec's design loses calls whenever the OS or an OEM kills the service — which on Xiaomi/Oppo is *routine*. A watermark-based periodic scan is what actually makes capture reliable. |
| 2 | **Watermark + first-run backfill** (§13.2) | The spec reads "the latest row" on observer fire. Back-to-back calls, reboots, and force-stops all silently drop calls under that model. |
| 3 | **Boot receiver** | Nothing in the spec restarts capture after a reboot. |
| 4 | **Batch ingest endpoint** with per-row results (§6.2) | The spec has only single-row POST. Flushing 50 offline drafts one-by-one on a bad connection is slow and partially fails in ways the app can't reason about. |
| 5 | **`GET /bootstrap`** | Collapses 4 cold-start round trips into 1 and gives the server one place to push config (workday seconds, consent version, min app version, feature flags). |
| 6 | **In-app update + `min_supported_version_code`** (§18.4) | Decision #1 removes Play Store auto-update. Without this, a broken build lives on employee phones forever. This is the single biggest gap in the original spec. |
| 7 | **`is_personal` flag** (decision #11) | The spec has no answer for personal calls on a work-monitored phone. Without it employees will either disable the app or the KPIs will be wrong — and it's the main trust/privacy objection you'll hear in rollout. |
| 8 | **Consent as a stored record + server gate** (decision #14) | The spec has a consent *screen*. For DPDP-style employee-monitoring defensibility you need a timestamped, versioned record the server can prove. |
| 9 | **Number masking rules** (decision #12) | The spec says "mask numbers for some roles if needed" but never defines the rule. Undefined privacy rules become no privacy rules. |
| 10 | **Data retention command** (decision #13) | Not mentioned at all. Indefinite retention of employee call metadata is a liability. |
| 11 | **`sub_channel`, `phone_account_id`, `contact_name`, `tz_offset_minutes`, `app_version`, `captured_at`** columns | Resolves spec open decision #4; adds dual-SIM attribution, human-readable names, correct timezone maths, and the debugging fields you always wish you had after a bad release. |
| 12 | **Explicit CallLog semantics** (§13.4) | The spec asks for `ring_sec` "if available" — it generally isn't. Left ambiguous, a developer will invent it and corrupt the analytics. |
| 13 | **`client_uuid` must never be regenerated** (§13.3) | Not stated in the spec; it is the #1 cause of duplicate rows in sync systems like this. |
| 14 | **Reviewed rows are server-authoritative** (§6.2.2) | The spec's "upsert on client_uuid" would let a stale device draft overwrite a human's correction. |
| 15 | **Task/project mismatch: reject, don't reassign** (§6.2.5) | Spec §5.4.2 says "pick one rule and enforce" — it is now picked. |
| 16 | **Server-side `busy_blocks` in the analytics payload** | Guarantees the phone timeline and the web timeline can never show different pictures. |
| 17 | **Telemetry endpoint instead of Crashlytics** (decision #15) | Keeps the internal app free of Google Play Services, so it installs on any device, and keeps employee data in-house. |
| 18 | **Token rotation + drafts survive logout** (decision #7) | Sanctum has no refresh flow out of the box; the spec's "short-lived tokens + refresh" was unimplementable as written. |
| 19 | **Two build flavors, keystore custody rules, `versionCode` discipline** (§18) | The spec ends at "internal APK distribution" with no mechanics. Losing the keystore is unrecoverable. |
| 20 | **Room migration ban on destructive fallback** (§19) | Destructive fallback on an app holding unsynced calls destroys real data. |
| 21 | **Stage A recon gate** | The spec assumes table and enum names in `pm-system`. Assumptions like these are exactly where an AI-assisted build goes off the rails at hour two. |
| 22 | **Rate limits, pagination, error envelope** (§6.6, Appendix B) | Absent from the spec; needed before the first real device hits the API. |

**Deliberately still out of scope** (do not build): call recording, WhatsApp AccessibilityService,
message/chat capture, interview number matching, work-log auto-writes, KPI snapshot tables,
live "on call now" presence, manager heatmaps. Phases 3 and 4 of the source spec stand as written.

---

## 23. Definition of Done

**Backend**

- [ ] `call_sessions`, `call_devices`, `call_telemetry_events`, `call_app_releases` created; nothing else added or altered
- [ ] Enums, models, factories, policies registered
- [ ] All §6.1 endpoints live, each with a policy check and a feature test
- [ ] Ingest is idempotent and proven so by test
- [ ] `CallAnalytics` interval merge unit-tested against overlap cases
- [ ] Five dashboard pages live and role-gated; web numbers == API numbers (asserted by test)
- [ ] Retention + device-health commands scheduled
- [ ] Postman collection committed and green against staging

**Android**

- [ ] Release APK builds, signed, verified with `apksigner`
- [ ] Login → consent → permissions → project → capture → review → sync works end to end on 2 real devices
- [ ] Capture survives force-stop, reboot, and airplane mode (QA #7, #8, #9)
- [ ] No duplicates under any QA scenario
- [ ] Offline task creation completes correctly after reconnect
- [ ] Encrypted Room + encrypted token storage verified
- [ ] In-app update check works and a mandatory-update block was tested at least once
- [ ] Full QA matrix (§19) recorded in `QA_RESULTS.md`

**Ops**

- [ ] Keystore backed up in two places, documented owner
- [ ] Consent policy PDF signed and hosted
- [ ] Install guide written with screenshots
- [ ] Pilot team onboarded, `/calls/devices` all green
- [ ] `docs/callpulse/DEVIATIONS.md` reviewed and closed out

---

## Appendix A — `CallLog.Calls.TYPE` → `direction` mapping

| Constant | Value | `direction` | `answered` | Notes |
|----------|-------|-------------|------------|-------|
| `INCOMING_TYPE` | 1 | `inbound` | `duration_sec > 0` | |
| `OUTGOING_TYPE` | 2 | `outbound` | `duration_sec > 0` | unanswered outgoing has duration 0 |
| `MISSED_TYPE` | 3 | `missed` | `false` | |
| `VOICEMAIL_TYPE` | 4 | `inbound` | `false` | `outcome = other`, `confidence = medium` |
| `REJECTED_TYPE` | 5 | `rejected` | `false` | |
| `BLOCKED_TYPE` | 6 | — | — | **skip entirely**, do not log |
| `ANSWERED_EXTERNALLY_TYPE` | 7 | `inbound` | `false` | answered on another device; `confidence = low` |
| anything else | — | `unknown` | `false` | log a `capture_gap` telemetry event |

Constants live on `android.provider.CallLog.Calls`. Read them from the framework, don't hardcode
the integers, and always handle the `else` branch.

---

## Appendix B — Error envelope

Use the convention found in Stage A. If none exists:

```json
{
  "message": "Human readable summary.",
  "code": "consent_required",
  "errors": { "project_id": ["The selected project is not accessible."] }
}
```

| HTTP | `code` | When |
|------|--------|------|
| 401 | `unauthenticated` | missing/expired token → app forces re-login |
| 403 | `device_revoked` | `call_devices.revoked_at` set → app wipes and locks |
| 403 | `forbidden` | policy denial |
| 409 | `consent_required` | no consent record for the device |
| 409 | `task_already_linked` | create-task on a call that already has a task |
| 422 | `validation_failed` | field errors in `errors` |
| 426 | `app_update_required` | `X-App-Version-Code < min_supported_version_code` |
| 429 | `rate_limited` | includes `Retry-After` |

The app must handle **every** row of this table explicitly. A generic "something went wrong"
toast for a 409 `consent_required` is a support ticket waiting to happen.

---

## Appendix C — Prompts to paste into Claude Code, one per stage

Paste these one at a time, in order, in the correct repo. Do not paste more than one.

**Stage A** (in `pm-system`)
> Read `docs/callpulse/CALLPULSE_BUILD_INSTRUCTIONS.md` §4. Perform the recon it describes over this
> repository and write `docs/callpulse/RECON.md`. Answer every numbered question with a concrete
> file path and line reference, or state explicitly that the thing does not exist. Write no other
> code. End with the "Deviations from the CallPulse spec's assumptions" section.

**Stage B**
> Read §2 (guardrails) and §5, and `docs/callpulse/RECON.md`. Create the migration, enums, models,
> factories and `config/callpulse.php` exactly as §5 specifies, matching this repo's existing
> conventions from RECON.md. Create nothing listed in §2 "Never create". Then run the §5 Acceptance
> checks and show me the results.

**Stage C**
> Read §6, §21 and Appendix B. Implement all §6.1 endpoints under `routes/api.php` v1 with Sanctum,
> using the exact request/response shapes in §21. Implement `CallSessionIngestService` with the
> idempotency and validation rules in §6.2, `CallAnalytics` with interval merge per §6.4, and the
> policies in §6.5. Write a feature test for every bullet in the §6 Acceptance list. Do not start
> the dashboard.

**Stage D**
> Read §7 and `docs/callpulse/RECON.md`. Build the five dashboard pages using this repo's existing
> UI stack, layout component and chart library — introduce no new frontend dependency. Reuse
> `CallAnalytics`; duplicate no aggregation logic. Include the test that asserts the web page
> numbers equal `GET /api/v1/analytics/call-busy` for the same user and date.

**Stage E**
> Read §8. Add the retention and device-health artisan commands with schedule entries, the demo
> seeder, and bring the suite green with ≥85% coverage on `CallAnalytics` and the ingest service.

**Stage F** (in a new empty `callpulse-android` repo)
> Read §10. Scaffold the Kotlin/Compose project exactly as specified: module layout, version
> catalog, Hilt, Room+SQLCipher, Retrofit, WorkManager, DataStore, two product flavors, release
> signing config reading from gradle properties, R8 rules. Verify both
> `assembleStagingDebug` and `assembleProductionRelease` succeed and that the release build does not
> crash on JSON parsing.

**Stage G**
> Read §11 and §21. Implement login, `/bootstrap`, encrypted token + Room storage, token rotation,
> the 401/403 handling rules, and the sticky project chip backed by DataStore.

**Stage H**
> Read §12. Build the consent + permission onboarding stepper, the manifest permission set exactly
> as listed (no extras), the permission-health banner, the `HeartbeatWorker`, and the OEM checklist
> screen with try/catch-guarded intents.

**Stage I**
> Read §13 and Appendix A. Implement the four-mechanism capture engine, the watermark scan, first-run
> backfill, the three-level local dedupe, the CallLog→entity mapper following Appendix A exactly, and
> the review notification with coalescing. Write unit tests for the mapper and the dedupe logic. Then
> give me a manual test script for the §13 Acceptance list.

**Stage J**
> Read §14. Build the review bottom sheet with all eight elements, the task search/link flow, the
> playbook and custom task creation flows, the offline `PendingTaskCreation` queue, and the
> project-change-clears-task rule.

**Stage K**
> Read §15. Implement `SyncWorker` with the exact six-step run order, batch chunking, per-row result
> handling, the four sync statuses, and user-visible surfacing of 422 failures.

**Stage L**
> Read §16. Build the day view: date picker, timeline rendered from the server's `busy_blocks`,
> totals card, call list, offline fallback labelled as an estimate. Add the test asserting the client
> total matches the server's `talk_seconds`.

**Stage M**
> Read §17. Add manual WhatsApp call logging only. Do not create any AccessibilityService.

**Stage N**
> Read §18. Set up the keystore config (never committing secrets), the versioning scheme, the
> in-app update check with mandatory-update blocking, the managed configuration for `api_base_url`,
> and the network security config. Then produce a signed release APK and show me the
> `apksigner verify --print-certs` output.

**Stage O**
> Read §19. Turn the QA matrix into `docs/callpulse/QA_RESULTS.md` with a row per scenario per
> device, and write any automated instrumentation tests that are feasible for scenarios 1–17.

---

*CallPulse build instructions v1.0 — derived from `androidcalltrackingapp.md` v1.1.
All six open decisions closed; 22 additions logged in §22.*
