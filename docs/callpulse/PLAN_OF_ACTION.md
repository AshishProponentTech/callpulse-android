# CallPulse — Plan of Action

Analysis of `CALLPULSE_BUILD_INSTRUCTIONS.md` (v1.0) + `CLAUDE.md`, current-state gap
assessment, and an execution plan.

Prepared 2026-08-11. Status: **pre-Stage A, blocked.**

---

## 1. What the plan actually asks for

Two deliverables in two separate repos:

| Repo | Deliverable | Stages |
|------|-------------|--------|
| `pm-system` (existing Laravel) | 4 new tables, 22 API endpoints, ingest service, `CallAnalytics`, 5 dashboard pages, 2 artisan commands | A – E |
| `callpulse-android` (new) | Kotlin/Compose APK, `com.proponenttech.callpulse`, sideloaded | F – N |
| both | QA matrix + pilot rollout | O – P |

The design is sound and unusually well-specified. The three decisions that carry the most
weight — reconciliation-scan capture instead of a foreground service (§13.1), `client_uuid`
as an immutable idempotency key (§13.3), and interval-merge busy time (§6.4) — are the right
calls and are exactly where a naive build would fail.

**Scale estimate:** ~7–9 weeks of focused single-developer work to Definition of Done,
plus a 1-week pilot. Backend ≈ 11–14 days, Android ≈ 20–25 days, QA ≈ 4–6 days.
The critical path runs through Stage C (API) and Stage I (capture engine).

---

## 2. Current state — what exists

```
D:\Project\call-track-android\
└── project-plan\
    ├── CALLPULSE_BUILD_INSTRUCTIONS.md   (74 KB, complete)
    └── CLAUDE.md                          (3.5 KB)
```

That is everything. No git repository, no code, no `docs/callpulse/`, no Android project,
and **no `pm-system` checkout on this machine.**

### 2.1 Blockers (must clear before any code)

| # | Blocker | Impact | Owner |
|---|---------|--------|-------|
| B1 | **`pm-system` is not on this machine.** I scanned all of `D:\Project\` — the only ProponentTech Laravel app present is `proponent-admin`, which is an HR/ERP system (attendance, payroll, leave, invoices). It has `users` and `projects`, but **no `tasks`, `milestones`, `task_templates`, or `work_logs`** — the four tables CallPulse is built to reuse. It is not `pm-system`. | Stage A cannot start. Stages B–E are entirely downstream of Stage A. | Human — clone `pm-system` locally with a working `.env` and a scratch DB |
| B2 | Staging Laravel URL over HTTPS with a **valid public cert** (§3, §18.6) | Blocks the Stage E→F handoff gate and all real-device testing | Human/IT |
| B3 | 2+ physical Android phones, one of them Xiaomi/Oppo/Vivo/Realme (§3). Emulators cannot test call capture. | Blocks Stage I acceptance and the entire Stage O matrix | Human |
| B4 | Signed consent policy PDF, hosted at a public URL (§3, decision #14) | Blocks Stage H; the server rejects ingest without a consent record | Human/HR+Legal |
| B5 | Release keystore generated and backed up in two places (§18.1) | Blocks Stage N. Losing it later forces every employee to uninstall/reinstall. | Human |

B1 is the only one blocking *today's* work. B2–B5 have lead time — start them in parallel now.

### 2.2 Repo hygiene problems in the plan documents themselves

1. **`CLAUDE.md` is in `project-plan/`, not at a repo root.** Claude Code loads `CLAUDE.md`
   from the working directory root. As placed, it will not auto-load in either repo.
2. **Path mismatch.** `CLAUDE.md` line 4 and every Appendix C prompt reference
   `docs/callpulse/CALLPULSE_BUILD_INSTRUCTIONS.md`. The file lives at
   `project-plan/CALLPULSE_BUILD_INSTRUCTIONS.md`. Every stage prompt will fail its first read.
3. **No git.** `one stage = one branch = one PR` (§0.4) is unenforceable.
4. The instructions assume the docs live inside each repo. They currently live in a third,
   unrelated directory.

---

## 3. Contradictions and gaps to resolve

These are real ambiguities in the spec, not style preferences. Each will otherwise be resolved
by improvisation at 2am. Resolve each at the stage noted, and record the ruling in
`docs/callpulse/DEVIATIONS.md`.

### 3.1 Analytics — the merge-vs-breakdown problem (resolve **before Stage C**, highest priority)

`talk_seconds` is defined as a **merged** interval total (§6.4). But §21 returns
`by_channel`, `by_project`, `by_hour`, and `busy_blocks[].project_id` alongside it. These are
mutually inconsistent the moment two calls overlap:

- **`busy_blocks[].project_id`** — a merged block spanning a Project-1 call and an overlapping
  Project-2 call has no single `project_id`. The field as specified cannot always be populated.
- **`by_channel` / `by_project`** — the §21 example has `4800 + 1620 = 6420 = talk_seconds`.
  That identity only holds with zero overlap. QA scenario #22 (overlapping cellular + manual
  WhatsApp) deliberately creates overlap, so the example is unachievable as written.
- **`by_hour`** — §6.4 requires proportional splitting across hour boundaries, but doesn't say
  whether the split runs over merged intervals or raw calls. Only one of those sums to
  `talk_seconds`.

**Recommended ruling** (adopt unless the project lead says otherwise): merge first, then
attribute each merged second to the **earliest-starting call covering that second**. This makes
every breakdown a true partition of `talk_seconds` — all three sum exactly, `busy_blocks` get a
single defensible `project_id`, and the whole thing stays one tested function. Implement as
`CallAnalytics::attributeMergedSeconds()` and unit-test it against the QA #22 case.

### 3.2 Soft delete vs. `client_uuid` UNIQUE (resolve at Stage C)

`DELETE /call-sessions/{id}` soft-deletes; `client_uuid` is UNIQUE and survives the soft delete;
ingest upserts on `client_uuid`. A device that re-syncs a deleted draft either hits the unique
constraint or silently resurrects a row the user deliberately removed. Neither is stated.

**Recommended ruling:** ingest matching a soft-deleted row returns `status: duplicate` with the
existing id and **does not** restore it. Add a feature test.

### 3.3 `reviewed_at` sent by the device (resolve at Stage C)

§21's ingest payload includes `reviewed_at`, but §6.2.2 makes reviewed rows server-authoritative.
Ruling needed: if the server row already has `reviewed_at`, the device's value and all its review
fields are ignored; if the server row is unreviewed, the device's review is accepted. Test both
directions.

### 3.4 Stage C endpoint list includes Stage N tables (resolve at Stage C)

§6.1 lists `GET /app/latest-version` and `GET /app/download`, but `call_app_releases` isn't
created until Stage N (§5.1). Meanwhile §6 Acceptance requires `route:list` to match §6.1
"exactly." Ruling: implement those two routes in Stage N and note the deferral in the Stage C
acceptance record — or pull the table forward into Stage B. **Recommend deferring to Stage N**;
`CLAUDE.md` rule 5 permits the table either way.

### 3.5 Masking format (cosmetic, resolve at Stage C)

Decision #12 shows `+91 98XXXXXX21` masking `…3210`; §21 shows `+91 98XXXXXX10` for the same
number. Define once, in one helper: **first 4 and last 2 digits visible**, everything between
replaced with `X`. §21's example is the correct one.

### 3.6 Android platform risks (validate at Stage F/I, before committing to the design)

- Manifest-declared `android.intent.action.PHONE_STATE` receivers and expedited work started from
  them behave differently across Android 14/15/16 and OEM ROMs. §13.1 already treats the receiver
  as an optimisation and the periodic reconcile as the reliability mechanism — **keep it that way**
  and prove the periodic path works with the receiver disabled.
- `compileSdk`/`targetSdk 36` + Room + SQLCipher + Hilt + Compose need concrete compatible versions
  pinned in `libs.versions.toml` at scaffold time. Verify the installed AGP/Kotlin first (§ decision #9)
  rather than assuming.
- `REQUEST_INSTALL_PACKAGES` is only added at Stage N (§18.4) — don't let it creep into the
  Stage H manifest.

---

## 4. Plan of action

### Phase 0 — Repo setup and unblocking (½ day, do now)

Parallel human track (start immediately, these have lead time):

- [ ] **B1** Clone `pm-system` to `D:\Project\pm-system`, `.env` configured, `php artisan migrate` clean on a scratch DB
- [ ] **B2** Request staging HTTPS URL with a valid public cert
- [ ] **B3** Source 2 test phones (one Xiaomi/Oppo/Vivo/Realme)
- [ ] **B4** Kick off consent policy PDF with HR/Legal — longest lead time of the five
- [ ] **B5** Schedule keystore generation + password-manager custody with IT

Build track (I can do all of this now, no blockers):

1. `git init` this directory; commit the plan documents as the baseline.
2. Decide topology — **recommendation:** this directory becomes the `callpulse-android` repo;
   Laravel work happens in the separate `pm-system` clone. Docs are copied into both under
   `docs/callpulse/` (§10 module layout already calls for this).
3. Create `docs/callpulse/` in both repos containing `CALLPULSE_BUILD_INSTRUCTIONS.md`,
   this plan, and an empty `DEVIATIONS.md` + `RECON.md` skeleton.
4. Move `CLAUDE.md` to each repo root and fix its line-4 path reference.
5. Add `.gitignore` covering the §2 never-commit list: `*.jks`, `local.properties`, `.env`,
   `gradle.properties` secrets.
6. Open `DEVIATIONS.md` with the six §3 items above, each marked `OPEN`, so no stage silently
   improvises past one.

**Gate:** repos exist, docs load from the paths the Appendix C prompts expect, `DEVIATIONS.md`
seeded.

### Phase 1 — Backend (Stages A–E, ~11–14 days) — needs B1

| Stage | Work | Est. | Gate |
|-------|------|------|------|
| **A** | Recon `pm-system`, write `RECON.md` — all 19 questions with file+line refs. No code. | 1 d | **Human sign-off on the deviations list.** Non-negotiable. Answers to Q4 (roles), Q7 (project scope query), Q10 (template→task path) determine the shape of Stages C and D. |
| **B** | Migration (3 tables), 7 enums, 3 models + factories + policies, `config/callpulse.php` | 1 d | `migrate:fresh` + `rollback` clean; forbidden-table test passes; `work_logs`/`users` unchanged |
| **C** | 20 endpoints (2 deferred to N), `CallSessionIngestService`, `CallAnalytics`, policies, rate limits, Postman collection | 5–6 d | Every §6 acceptance bullet has a passing feature test. **Resolve §3.1–3.5 first.** |
| **D** | 5 dashboard pages on the existing UI stack, CSV export, nav entries | 3–4 d | Web numbers == API numbers, asserted by test; role gating tested |
| **E** | Retention + device-health commands, demo seeder, ≥85% coverage on analytics + ingest | 1–2 d | Suite green |

**Handoff gate (§9)** — all five boxes before any Android work: staging live over HTTPS,
Postman green against staging, test employee in ≥2 projects, `/bootstrap` returning real data,
§21 contract frozen and committed.

Stage C is the single largest backend chunk and the one to slow down on. The ingest service and
the interval merge are where correctness is won or lost, and both are cheap to test and expensive
to retrofit.

### Phase 2 — Android (Stages F–N, ~20–25 days) — needs B2, B3, B4, B5

| Stage | Work | Est. | Notes |
|-------|------|------|-------|
| **F** | Scaffold: modules, version catalog, Hilt, Room+SQLCipher, Retrofit, WorkManager, 2 flavors, R8 | 1–2 d | Verify installed AGP/Kotlin before pinning. **Test the release build parses JSON** — the classic failure. |
| **G** | Login, `/bootstrap`, encrypted token + DB, rotation, 401/403 rules, sticky project | 2 d | Drafts must survive re-login |
| **H** | Consent + permission stepper, exact manifest set, health banner, `HeartbeatWorker`, OEM screen | 2 d | Needs B4 (consent PDF) |
| **I** | **Capture engine.** 4 mechanisms, watermark, first-run backfill, 3-level dedupe, Appendix A mapper, coalesced notifications | 4–5 d | Hardest stage. Needs B3 (real phones). Unit-test the mapper and dedupe; the rest is manual on hardware. |
| **J** | Review sheet (8 elements), task search/link, playbook + custom creation, offline `PendingTaskCreation` | 3 d | |
| **K** | `SyncWorker`, 6-step run order, batch chunking, per-row results, 4 statuses, visible 422s | 2–3 d | |
| **L** | Day view: timeline from server `busy_blocks`, totals, list, labelled offline fallback | 2 d | |
| **M** | Manual WhatsApp logging only. No AccessibilityService. | 1 d | |
| **N** | Keystore config, versioning, in-app update + mandatory block, managed config, network security, `call_app_releases` + the 2 deferred endpoints | 2 d | Needs B5. Test the mandatory-update block once, deliberately. |

Stage F can begin as soon as §21 is frozen (end of Stage C), overlapping Stages D and E — the
scaffold only depends on the contract, not on the dashboard. That's the one safe parallelisation;
everything else in the sequence is genuinely ordered.

### Phase 3 — QA and rollout (Stages O–P, ~1 week + 1 week pilot)

- **O:** 25-scenario matrix × 2 devices → `QA_RESULTS.md`. Instrumentation tests where feasible
  (1–17); scenarios 23 (8h battery soak) and 25 (upgrade install) are manual and need real time.
  **Run the whole matrix against the minified release build** (§19 #24), not debug.
- **P:** 3–5 person pilot for one week → fix top 3 → **ship a versionCode bump through the in-app
  update path** (this is the real test of §18.4, before you need it in anger) → wave 2 →
  track §14 metrics weekly for two weeks.

---

## 5. Recommended immediate next steps

1. Confirm where `pm-system` lives and get it cloned locally — **everything backend is blocked on this.**
2. Confirm repo topology (this dir = `callpulse-android`?), then I run Phase 0 in full.
3. Get a ruling on §3.1 (the merge/breakdown attribution rule) — it changes `CallAnalytics`,
   the §21 payload, the dashboard timeline, and the Android day view. Cheap to decide now,
   expensive to change after Stage L.
4. Start the four long-lead human items (B2–B5) today; the consent PDF in particular gates
   Stage H and typically takes longest.

Once `pm-system` is available, Stage A is a clean first task and produces the `RECON.md` that
everything else keys off.
