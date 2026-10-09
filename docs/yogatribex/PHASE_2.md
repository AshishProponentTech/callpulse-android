# YogaTribeX CallPulse — CRM integration, Phase 2

The authoritative integration plan is
`C:\xampp\htdocs\YogatribexCRM\call-enquiry-plan.md`. This branch implements
Phase 2 only. The owner's later naming request changes the app label and login
heading to **YogaTribeX CallPulse**. CRM destinations retain the YogatribexCRM name.

App ID: `com.yogatribex.crm`; staging debug ID:
`com.yogatribex.crm.staging.debug`. Kotlin namespaces remain unchanged.
The login screen displays its version/build and has a password visibility toggle.

The app uses seven `mobile/*` endpoints, bearer tokens and the selected school UUID
in `X-School-Id`. Bootstrap owns the consent version, policy URL and school list.
Refresh rotates expiring tokens under a mutex. A late 401 for an older token cannot
clear the current session; revocation clears preferences and Room tables on IO
without deleting the SQLCipher key.

Room v3 rebuilds `call_drafts`, preserves client UUIDs and sync state, drops legacy
project/task/milestone references, and adds school/lead/review fields and
`personal_numbers`. Legacy numeric server IDs become null because they do not
identify CRM calls. Both migration paths (2→3 and 1→2→3) have phone tests.

Capture requires sign-in, consent, call-log permission and a chosen school. New
drafts use that school; selecting one also fills unassigned drafts. Private/short
numbers, unknown directions and personal exclusions are skipped. Sync processes
100-row chunks up to a 50-chunk safety cap, records matched leads, exposes rejected
rows and supports retrying them. A consent refusal clears cached acceptance.

Staging defaults to `http://10.0.2.2:8123/api/v1/`; production defaults to
`https://api.yogatribex.com/api/v1/`.

## Local phone verification

Use JBR at `D:\Android\Android Studio\jbr`, Gradle cache `D:\gradle`, and the
Redmi on USB (`9PDE7X6DZLLNSGYX`).

```powershell
$env:JAVA_HOME='D:\Android\Android Studio\jbr'
$env:GRADLE_USER_HOME='D:\gradle'
.\gradlew.bat :app:assembleStagingDebug
.\gradlew.bat :core-network:testDebugUnitTest :core-data:testDebugUnitTest :feature-capture:testDebugUnitTest
.\gradlew.bat :core-data:connectedDebugAndroidTest
.\gradlew.bat :app:installStagingDebug
& 'D:\Android\Sdk\platform-tools\adb.exe' -s 9PDE7X6DZLLNSGYX reverse tcp:8123 tcp:8123
```

Set Developer settings to `127.0.0.1:8123` and apply. The owner enters CRM
credentials on the phone. **The first sync uploads the last 24 hours of the
phone's real call log to the local dev CRM.** Warn the owner before completing
setup. Verify login → consent/policy → permissions → school → test call, then
inspect the Uploaded row, any matched lead and server records read-only.
The owner's API server must remain running.

Call-sheet/enquiry/link/dismiss UI and enrollment remain for later phases.

## Verification on 2026-10-09

- Staging debug assembly/install passed (version 1.0.2-staging). Final build 5
  is installed on the Redmi with label **YogaTribeX CallPulse**, alongside the old
  CallPulse package. The login heading/version and eye toggle were checked on
  build 4; build 5 carries the same UI and the worker scheduling fix.
- Requested unit tests passed: core-network 17, core-data 4, feature-capture 11.
- Redmi Android 13 instrumentation: all five tests passed, covering 2→3 and
  1→2→3 migrations, school assignment/queue/retry/lead persistence, stale-token
  401 handling, revocation from Main without deleting the encryption key, and
  preventing scan-watermark writes from retriggering consent observers/workers.
- USB reverse for port 8123 was verified; the public policy returned HTTP 200.
- A read-only transaction confirmed the dev connection is pgsql, with zero phone
  calls/devices before the owner login.
- The owner completed CRM login, consent, permissions and school selection.
  The server reports consent version `2026-10-09`, call-log/phone-state/
  notification permissions granted, and final app version code 5.
- First backfill uploaded five calls. Logcat showed `Sync: 1 accepted, 0 rejected`
  and `Sync: 4 accepted, 0 rejected`. A later read-only DB check found six calls,
  including an 11-second inbound call after the initial backfill. The owner
  confirmed a completed test call and an **Uploaded** row on Home.
- Final build 5's bootstrap and heartbeat returned HTTP 200 via the saved
  `http://127.0.0.1:8123/api/v1/` override; repeated worker scheduling stopped.

Not verified end-to-end: matched-lead name/status and admin `/call-logs` display
(none of the six calls matched an existing lead), opening the policy link in the
phone browser, live server token rotation/revocation, and extended background
operation. DTO lead decoding and local lead persistence are covered by tests;
the stale-401/revocation behavior is covered by the Redmi instrumentation test.
The server reported battery-optimization exemption as false.

No CRM source/plan changes were made; `main` and the untracked daemon properties
were left alone. Updating the external plan's Haal section requires owner approval.

On this laptop the build completed with `--no-parallel --max-workers=2` and
`'-Dorg.gradle.jvmargs=-Xmx1536m -Dfile.encoding=UTF-8'` to reduce RAM pressure.
