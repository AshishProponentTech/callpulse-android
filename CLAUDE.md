> # ⛔ SUPERSEDED — read this before anything else
>
> **You are in the Android app** — this part of the repo is still current.
>
> **The CallPulse backend now lives in `D:\Project\proponent-admin`** (the live ERP at
> https://proponenttech.in), on branch `feature/project-app-data-callpulse`. It was moved there
> on 2026-09-08 because the `laravel/` scaffold in this repo sits on stub `users`/`projects`/
> `tasks` tables with no real data, while proponent-admin already owns the live projects and
> employee accounts the app authenticates against.
>
> **Do server-side work in proponent-admin, not here.** `D:\Project\call-track-android\laravel`
> is retired: do not add endpoints, migrations or services to it.
>
> ### Rules below that the shipped build does NOT follow
>
> These were decided against during the move. Do not "restore" them:
>
> | Rule below | What actually shipped |
> |---|---|
> | Reuse `pm-system` tasks / milestones / `project_user` | **No Project Management link at all.** `call_sessions` has no `task_id` or `milestone_id`; a call belongs to a `projects` row and nothing else. |
> | Four new tables incl. `call_app_releases` | Three: `call_devices`, `call_sessions`, `call_telemetry_events`. |
> | UTC in the database | The application timezone (`Asia/Kolkata`), matching proponent-admin's `config('app.timezone')`. |
> | Numbers masked without `call.view_numbers` | Numbers are shown **in full** to anyone holding `callpulse.view`. There is no masking layer. |
> | `App\Services\CallAnalytics`, 5 dashboard pages | One read-only screen pair, "Project App Data", driven by `App\Http\Controllers\CallData\CallDataController`. |
> | Column type `timestamp` | `dateTime`. MariaDB's default `explicit_defaults_for_timestamp=OFF` gives the first NOT NULL `TIMESTAMP` column an implicit `ON UPDATE CURRENT_TIMESTAMP`, which silently rewrote `started_at` on every re-ingest. |
>
> The Android app in `callpulse-android/` is still live and still built from here. Its production
> flavour points at `https://proponenttech.in/api/v1/`.

---

# CLAUDE.md — CallPulse

Project rules for any AI-assisted work in this repository. Read
`docs/callpulse/CALLPULSE_BUILD_INSTRUCTIONS.md` before doing anything. Work stage by stage
(A → P); do not skip ahead.

## What this project is

An **internal-only** Android app (`com.proponenttech.callpulse`, distributed as a signed **APK**,
never via Play Store) that logs employee work calls — number, direction, start, end, duration —
attributes each to a **project**, optionally links or creates a **task**, and syncs to the existing
Laravel `pm-system`, where managers view daily call-busy analytics.

## Non-negotiables

1. **Stage A recon first.** Never assume a table, column, enum, role name or code path exists in
   `pm-system`. Read it, record it in `docs/callpulse/RECON.md`, then build.
2. **Reuse, do not fork, PM master data.** `users`, `projects`, `project_user`, `milestones`,
   `tasks`, `task_templates`, Sanctum tokens. Creating `call_projects`, `call_tasks`,
   `call_employees` or a second tasks table is a hard failure.
3. **Never touch `work_logs`.** No auto-write, no new column, no suggestion flow.
4. **Never** record audio, read SMS, read message content, or upload the address book.
5. **Only four new tables, ever:** `call_sessions`, `call_devices`, `call_telemetry_events`,
   `call_app_releases`. Nothing else.
6. **`client_uuid` is the idempotency key.** Generate once per call on the device, never regenerate.
   Every server ingest is an upsert on it.
7. **Every call row has a `project_id`.** No exceptions.
8. **Busy time is an interval merge**, not `SUM(duration_sec)`. Overlapping calls count once.
9. **Reviewed rows are server-authoritative.** A resynced device draft must never overwrite a
   human correction.
10. **No permanent foreground service.** Capture is event-driven + periodic reconciliation scan
    with a watermark. See §13.1.
11. **String columns + PHP enums**, never MySQL native `ENUM()`.
12. **Never commit** the keystore, `local.properties`, `.env`, or any credential.
13. If reality contradicts the instructions, **stop**, write it to `docs/callpulse/DEVIATIONS.md`,
    and ask. Do not improvise schema or rename things.

## Conventions

- **Laravel:** `App\Enums\*` string-backed enums, API Resources for every response, a Policy check in
  every controller action, `CarbonImmutable`, UTC in the DB, `Asia/Kolkata` for analytics buckets.
  Aggregation logic lives once, in `App\Services\CallAnalytics` — the web dashboard and the API must
  call the same code.
- **Android:** Kotlin, Compose/Material 3, Hilt, Room + SQLCipher, WorkManager, Retrofit, DataStore.
  `minSdk 26`, `compileSdk`/`targetSdk 36`, JDK 17. No Firebase, no Play Services dependency.
  Room is a draft + offline queue cache only — the server is the source of truth.
- **Tests:** every endpoint gets a feature test; the interval merge and the CallLog mapper get unit
  tests. Test the **release** (minified) Android build, not just debug.
- One stage = one branch = one PR. Acceptance checks must pass before moving on.

## Privacy rules that are also product rules

- Consent is a stored, versioned record; the API rejects ingest from a device without one.
- `is_personal` calls are kept for audit, excluded from every KPI, and masked from everyone but the
  owner.
- Numbers are masked for non-owners without the `call.view_numbers` ability. Masking lives in the
  API Resource, never in the query.
- Retention: call rows are purged after `config('callpulse.retention_months')` (default 24).
