# Varış V2 — Master Product Completion & Release Plan

**Plan ID:** H003-MASTER-COMPLETION-PLAN-001  
**Version:** 1.0  
**Date:** 2026-10-05  
**Product:** Varış V2 — Almanya odaklı varış alarmı Android uygulaması  
**Repository:** `nazofobi-gpt/arrival-alarm-android-v2`  
**Canonical product target:** H-003  
**Purpose:** This document is the detailed execution plan to be followed until the product reaches product-complete, field-accepted and Play-release-ready state.

---

## 1. Plan authority and operating rule

This file governs **execution sequence, acceptance gates, evidence requirements and scope control** for H-003. It does not replace live task status in the canonical `hafıza` workbook or exact GitHub evidence. Before any implementation work, the executor must read the relevant current task row and current repository state.

Development follows these rules:

1. **Fresh state before work.** Read current H-003 task state, direct dependencies, current `main`, open PRs and exact CI status before mutation.
2. **One bounded problem per implementation lane.** Avoid unrelated refactors in the same PR.
3. **No mock/demo acceptance.** Fixtures are allowed for deterministic tests, but terminal product acceptance requires production wiring and, where specified, real-feed or physical-device evidence.
4. **Exact-head verification.** CI/API36 evidence must belong to the exact commit being accepted.
5. **Physical evidence where emulator is insufficient.** Visual fidelity, TalkBack/focus, battery, lock-screen/background behavior, Bluetooth/TTS and field reliability require a real device.
6. **Fail closed.** Provider failure, stale data, missing capability, unknown accessibility or missing realtime must never be presented as success.
7. **No silent scope deletion.** Every requirement must end as PASS or explicit approved OUT_OF_SCOPE/DEFERRED. “Not implemented” without a recorded decision is not completion.
8. **Accepted RC freeze.** After final field acceptance, no feature/visual/behavior change may enter the release lineage without invalidating acceptance and repeating the necessary gates.
9. **Read → compare → minimal mutation → readback.**
10. **Completion is an end-to-end product result, not a feature count.**

---

## 2. Definition of “finished”

The application is considered **PRODUCT COMPLETE** only when all of the following are true:

- The selected v1 requirement set is closed with no unknown P0 gap.
- Germany-wide static data, search and routing work from production data.
- Realtime safely enriches static truth without making static routing unusable.
- First-use and degraded/offline states are usable and clearly communicated.
- The 8 canonical screen families match the approved visual language on a real Android device.
- The active journey and arrival alarm survive lock-screen/background/process recreation scenarios.
- Alarm/TTS/Bluetooth behavior is reliable and does not duplicate or silently fail.
- TR/DE/EN, large text and TalkBack critical paths pass.
- Performance, storage, battery, privacy and Android/Play policy gates pass.
- The exact release candidate passes integrated automated and physical-device acceptance.
- The external ChatGPT connector, if included in v1, passes real read/write E2E with device binding and user-approved writes.
- Release AAB/signing/Data Safety/privacy/store assets are generated from the **same accepted source lineage**.
- Staged rollout, rollback, diagnostics, incident response and support paths exist.

There are three completion states:

### 2.1 Engineering Complete
All selected product behavior is implemented and integrated. No known planned feature/visual behavior work remains.

### 2.2 Field Accepted
The exact RC has passed real-device visual, accessibility, search, routing, journey, alarm, offline, realtime and connector scenarios.

### 2.3 Release Ready
The accepted RC is signed/package-ready for Play with policy/privacy/store/rollback evidence and no behavior change since field acceptance.

---

## 3. Scope model

All 175 master requirements remain traceable. They must not be silently dropped.

### P0 — release blocking
Every P0 requirement must PASS before G-136 final field acceptance.

### P1 — product scope decision required
P1 items should be implemented when they materially support the core product. If intentionally deferred, the decision must be recorded explicitly as `OUT_OF_SCOPE_V1` with rationale and no hidden dependency on the deferred feature.

### P2 — optional/post-v1
P2 items may be deferred from first release, but each must have a recorded disposition. Optional features must never delay a reliable core arrival-alarm release unless they become direct dependencies of a chosen P0/P1 feature.

### Core v1 product promise
The minimum releasable product must provide:

- Germany-wide stop/station/place discovery.
- Current-location start and selectable destination.
- Real static transit routing with alternatives/transfers/walking context.
- Safe realtime enrichment where available.
- Active journey progress and get-off context.
- Reliable arrival alarm with audio/haptic/visual fallback.
- Lock-screen/background continuity.
- Clear offline/provider degradation.
- Production-grade list-first UI with map as supporting surface.
- TR/DE/EN and accessibility-critical support.
- Privacy-safe diagnostics and Play compliance.

---

## 4. Current verified baseline

At plan creation, the following foundations are already materially established and must not be rebuilt without regression evidence:

- **G-174:** Germany Full GTFS normalized data foundation — verified.
- **G-175:** provider-independent static router — verified.
- **G-177:** provider abstraction/resilience/failover — verified.
- **G-167:** full visual rebuild is active; automated CI/API36 head has reached green, but physical visual/a11y acceptance remains.
- **G-176:** realtime model/matcher/wiring exists, but terminal real-sample acceptance remains open.
- **G-169:** static first-use fallback core is implemented; physical acceptance remains.
- **G-153:** connector software/contracts exist; live external/device E2E remains field-gated.

No “complete” claim may be derived from this baseline alone.

---

# 5. Master execution sequence

The order below is dependency-driven. Later phases do not become terminal while an earlier hard dependency is open.

## Phase A — Plan adoption, scope freeze and branch hygiene

### A1. Adopt this master plan
- Every new H-003 implementation task references this plan.
- Existing canonical task IDs remain source of live status.
- New gaps discovered during execution are added to the plan and to the canonical task graph before being treated as complete.

### A2. Freeze v1 scope
Create one explicit scope disposition table for all P1/P2 items:
- `IN_V1`
- `OUT_OF_SCOPE_V1`
- `PROVIDER_GATED`
- `POST_V1`
- `USER_REQUIRED`

No feature disappears because it is inconvenient.

### A3. Repository hygiene
Before final integration:
- Close stale/unmergeable historical PRs whose work is already superseded.
- Never merge an old verified PR solely because it is green.
- Verify every open PR against fresh `main`.
- Keep one active implementation branch per current task lane.
- Remove obsolete workflow assumptions and test tags only with replacement evidence.
- Protect the accepted RC SHA from accidental source drift.

**Exit gate A:** v1 scope is explicit; stale integration risk is controlled; no ambiguous competing PR represents the same feature.

---

# 6. Workstream 1 — Canonical visual system and interaction architecture

**Primary tasks:** G-167, G-160, G-166, G-247, G-246, G-192

## 6.1 Eight screen families
The production implementation must cover:

1. Onboarding
2. Home
3. Search/location selection
4. Route results
5. Journey detail
6. Live trip
7. Stop/station departures
8. Settings/readiness

## 6.2 Mandatory interaction rules
- Theme/language/voice and similar single-choice settings use one row + radio-list/bottom-sheet, not a wall of large buttons.
- Origin/destination/location selection is list-first.
- Map is secondary and must never block list-based task completion.
- Bottom navigation uses proper vector icons; no Unicode/emoji controls.
- One primary action per journey phase.
- Density must prioritize transit information rather than decorative empty space.

## 6.3 Visual acceptance
For every screen:
- Canonical light theme comparison.
- Canonical dark theme comparison.
- System theme behavior.
- Normal text and 200% text.
- Loading, empty, error, offline, stale, permission-denied and retry states.
- Touch target >=48dp.
- TalkBack labels, focus order, headings and control state.
- Non-color cue for status.
- No clipping/overlap at compact size.
- Large/wide screen max-width behavior.

## 6.4 Evidence
- Exact-head Android CI SUCCESS.
- Exact-head API36 SUCCESS.
- Eight automated production-state screenshots.
- Human visual comparison against canonical boards.
- Physical-device 8-screen light + dark captures.
- 200% text physical check.
- TalkBack/focus/touch-target/contrast physical check.

**Exit gate W1:** G-167, G-160 and G-166 are terminal; visual/a11y debt is zero for the selected v1 screen set.

---

# 7. Workstream 2 — Germany-wide search, nearby and discovery

**Primary tasks:** G-168, G-170, G-171, G-182, G-237, G-240

## 7.1 Search contract
A single search surface must support:
- stop
- station
- address
- POI
- line
- trip/service

Search requirements:
- Germany-wide local index first.
- Local hit must remain usable when a network provider is down.
- Provider timeout/error is not “no result”.
- Fuzzy/diacritic/alias behavior is defined.
- Recent searches are local/privacy-safe and clearable.
- Favorites are identifiable in results.
- Offline static search works after data is installed.
- Loading/error/empty states are distinct.

## 7.2 Permanent regression places
Retain deterministic tests for at least:
- Lohne
- Achim
- Bremen
- Berlin
- München
- second-region/non-Niedersachsen sample

## 7.3 Nearby station model
- Canonicalize platform children under parent station.
- Stable favorite identity.
- Distance and direction/bearing.
- Deterministic ordering.
- Expand child platforms only when useful.
- No duplicate “same station at 0m/5m” presentation.

## 7.4 Discovery destinations
Normal navigation must expose:
- stop/station detail
- line detail
- trip detail
- next departures independent of an active route
- platform/freshness/alerts where available
- direct favorite/recent access

**Exit gate W2:** nationwide search and station/line/trip discovery work on real indexed data and physical device; Lohne regression is permanently retained.

---

# 8. Workstream 3 — Static routing, realtime and provider degradation

**Primary tasks:** G-169, G-175, G-176, G-177, G-243, G-244

## 8.1 Static routing truth
Local static GTFS is the canonical route truth. Network providers enrich or provide alternative capability but cannot make a static route disappear merely because a provider fails.

## 8.2 Required routing cases
- Lohne → Achim.
- Berlin local route.
- München local route.
- Cross-region route.
- direct route.
- transfer route.
- no-path.
- same-origin.
- midnight/service-day rollover.
- frequency-based service.
- linked/block continuity.
- walking transfer.
- provider timeout/offline.

## 8.3 Realtime overlay
Terminal realtime acceptance requires:
- current live provenance smoke.
- retained Niedersachsen/Bremen sample.
- retained second-region sample.
- cancellation.
- delay.
- platform change.
- service alert.
- fresh/stale/unavailable/no-match taxonomy.
- static route remains usable when realtime is absent.
- VehiclePosition appears only when genuinely supported.

## 8.4 Provider behavior
For every network provider:
- timeout budget
- retry budget
- circuit breaker
- typed failure
- cache TTL/ETag
- freshness
- sanitised UI error
- no raw exception leakage
- bounded request rate

**Exit gate W3:** G-169 physical acceptance and G-176 terminal sample acceptance pass; static route remains usable under provider failure.

---

# 9. Workstream 4 — Trip planning and itinerary quality

**Primary tasks:** G-178, G-179, G-239

## 9.1 Planning modes
- Depart now.
- Depart at future date/time.
- Arrive by.
- Explicit service-day/timezone handling.

## 9.2 Route preferences
Implement only when they change routing/ranking:
- fastest
- fewest transfers
- less walking
- simple
- accessible/step-free when source data permits
- configurable transfer buffer
- optional Germany-specific preferences where source-backed

## 9.3 Itinerary model
Use one normalized model for:
- route results
- journey detail
- saved route
- active journey
- connector read state

Itinerary must expose:
- start/end time
- total duration
- walking
- transfers
- line/direction
- platforms
- all legs
- all transit stops
- planned vs realtime state
- cancellation/connection risk
- geometry where available
- source/freshness

## 9.4 Comparison UX
- 2+ alternatives when viable.
- Clear reason when no alternative exists.
- Refresh must not silently switch the selected journey.
- Missed/cancelled/impossible connection is explicit.
- Alternative departure is actionable, not a count-only label.

**Exit gate W4:** route choice is a real product feature, not decorative UI; itinerary state is shared consistently across screens.

---

# 10. Workstream 5 — Active journey state machine

**Primary tasks:** G-180, G-172, G-241

Implement deterministic phases:

`PREPARE → WALK → WAIT → BOARD → RIDE → TRANSFER → GET_OFF → FINAL_WALK → ARRIVED`

## 10.1 Phase requirements
### PREPARE
- readiness
- permissions
- route selected
- alarm configuration

### WALK
- destination stop
- walking context
- late warning where supported

### WAIT
- correct line/direction/platform
- departure timing/freshness

### BOARD
- explicit boarding confirmation/evidence
- do not infer a current vehicle without sufficient evidence

### RIDE
- previous/current/next/target
- remaining stops
- realtime changes

### TRANSFER
- transfer destination/platform
- countdown/risk
- missed-connection recovery

### GET_OFF
- pre-alert
- imminent alert
- final alarm context

### FINAL_WALK
- destination context if route includes egress

### ARRIVED
- stop tracking
- dismiss journey
- preserve history only under chosen privacy settings

## 10.2 Recovery
- process kill/recreation
- provider disappearance
- GPS degradation
- route cancellation
- missed stop
- missed connection
- user switches route/departure
- network offline

**Exit gate W5:** active journey state is deterministic, recoverable and drives UI, notification, TTS and connector consistently.

---

# 11. Workstream 6 — Arrival alarm reliability

**Primary tasks:** G-181, G-242, G-191

The alarm is the core feature and requires physical acceptance.

## 11.1 Trigger strategy
Support a safe combination of:
- distance
- time
- stops remaining
- adaptive hybrid

Configuration must be understandable and testable.

## 11.2 Degraded location behavior
- poor GPS
- tunnel
- stale last-known location
- location disabled
- schedule/stop-progress fallback with confidence
- no false claim of physical position without evidence

## 11.3 Side effects
- one-shot final alarm
- device TTS
- selected language/voice
- vibration/haptic fallback
- visible notification fallback
- Bluetooth/headphone routing option
- reconnect behavior
- no duplicate speech/alarm after recreation
- safe cancel
- optional test alarm

## 11.4 Background reliability
Physical-device scenarios:
- screen off
- lock screen
- app background
- process recreation
- battery saver
- Doze
- Bluetooth disconnect/reconnect
- offline transition

**Exit gate W6:** zero duplicate final alarms, no missed simulated alarm in defined matrix, and physical journey acceptance passes.

---

# 12. Workstream 7 — Offline data lifecycle

**Primary tasks:** G-185, G-244, G-174, G-190

The user explicitly does **not** require a pre-embedded Lohne snapshot. Therefore the data lifecycle must instead be honest and usable.

## 12.1 First-run states
Define:
- no transit pack
- downloading
- validating
- indexing
- ready
- update available
- stale
- update failed but old-good retained
- insufficient storage
- cancelled/interrupted

The UI must never claim “ready” while search/routing is not usable.

## 12.2 Data management
- source/version/fetched-at/checksum
- ETag/version based unchanged-feed handling
- resumable or safe restart strategy
- transactional database swap
- corrupt feed rollback
- storage usage
- user cleanup/manage data
- Wi-Fi/mobile-data preference where applicable

## 12.3 Offline acceptance
After a valid pack is installed:
- stop search offline
- static route offline
- active trip continues under network loss
- realtime becomes explicitly unavailable/stale
- provider outage on first route attempt does not blank the product

**Exit gate W7:** interrupted/failed updates never destroy the last-known-good usable data state.

---

# 13. Workstream 8 — Personalization and daily-use utility

**Primary tasks:** G-183, G-245

Implement:
- favorite place
- favorite stop/station
- favorite line
- saved route
- recent searches
- recent trips
- delete/clear controls
- preference persistence
- optional recurring commute/pendler behavior if IN_V1

Privacy:
- local-first
- no account required for core personalization
- history may be disabled
- sync remains separate optional capability

**Exit gate W8:** personalization is useful from normal navigation and can be fully cleared.

---

# 14. Workstream 9 — Maps

**Primary tasks:** G-187, G-238

Map is supportive, not the only way to use the app.

Required:
- route shape when available
- visually distinct walking legs
- station markers canonicalized
- stop tap → detail
- selected route context
- light/dark compatibility
- provider attribution
- map failure does not block route/search
- no fake vehicle marker

Optional/provider-gated:
- entrances/pathways
- live vehicle positions
- offline maps
- detailed walking navigation

**Exit gate W9:** list flow is complete without a map; map adds context without becoming source of truth.

---

# 15. Workstream 10 — Accessibility

**Primary tasks:** G-184, G-246, G-166

Required:
- TalkBack full critical journey
- correct semantics/state
- deterministic focus order
- >=48dp targets
- 200% font scale
- contrast
- non-color status cues
- haptic/visual equivalents for audio
- cognitive load control
- wheelchair/step-free claims only from known data
- unknown accessibility is shown as unknown, not accessible
- compact and wide/adaptive layouts

**Exit gate W10:** a TalkBack user can search, select, route, start a journey, understand next/target stop and arm/cancel alarm without sighted assistance.

---

# 16. Workstream 11 — Localization and TTS language quality

**Primary tasks:** G-192, G-251

Languages:
- Turkish
- German
- English

Required:
- string resource parity
- no hard-coded user-facing strings
- locale-correct time/date/plural
- German transit terminology review
- errors explain what happened and next action
- concise source/freshness text
- stop-name TTS pronunciation smoke in at least TR/DE voice paths
- 200% text screenshot/content matrix

**Exit gate W11:** all selected v1 screens and critical notifications/TTS are language-consistent.

---

# 17. Workstream 12 — Performance, memory, storage, battery and network

**Primary tasks:** G-190, G-252

Freeze budgets before final benchmark.

Measure on a representative mid-range physical phone:
- cold start
- warm start
- time to first usable search
- local search latency
- nearest-stop latency
- route calculation latency
- GTFS import duration
- import peak memory
- route peak memory
- active journey steady-state memory
- UI jank
- battery drain during journey
- network requests per journey
- feed update bytes
- storage footprint

Required engineering:
- no large import on UI thread
- routing cancellable/bounded
- no unbounded provider polling
- no memory growth/leak over long journey
- baseline profile/release optimization where appropriate

**Exit gate W12:** performance budget is measured, recorded and met on physical hardware; no emulator-only “feels fast” acceptance.

---

# 18. Workstream 13 — Privacy, security and Play policy

**Primary tasks:** G-191, G-252, G-138

## 18.1 Location/privacy
- minimum permission scope
- avoid background location permission unless strictly required and policy-justified
- location FGS only during user-started active journey
- clear foreground notification
- local-first journey/location state
- retention policy
- clear/delete controls
- redacted logs

## 18.2 Security
- no secrets/tokens in APK, repository or logs
- HTTPS only
- dependency vulnerability review
- secrets scanning
- SBOM/dependency inventory
- secure connector tokens
- revoke/disconnect

## 18.3 Play policy artifacts
- exact permission inventory
- location disclosure
- foreground service declaration evidence
- Data Safety mapping
- privacy policy
- data deletion/revoke documentation
- store disclosure consistent with exact accepted code

**Exit gate W13:** policy statements match code; no release claim is based on an earlier build.

---

# 19. Workstream 14 — Privacy-safe observability and diagnostics

**Primary tasks:** G-193, G-252, G-173

Required diagnostics:
- app/source SHA
- app version
- device/API
- data feed version/checksum
- index readiness
- search provider health
- route provider health
- realtime health/freshness
- connector state
- typed failure class
- latency
- route/search outcome

Redaction:
- tokens
- exact sensitive history
- precise location by default
- connector secrets

Support export:
- user-visible export action
- redacted diagnostic bundle
- deterministic filename/version
- no private location trail unless explicitly opted in

**Exit gate W14:** a real search/route failure can be diagnosed without exposing secrets or requiring guesswork.

---

# 20. Workstream 15 — External ChatGPT connector

**Primary tasks:** G-153, G-250

The Android app itself contains no AI/LLM.

Required live acceptance:
- deploy free approved connector infrastructure
- OAuth/device binding
- least privilege
- revoke/disconnect
- read current/fresh journey state
- stale/offline state fails closed
- typed writes:
  - set origin
  - set destination
  - select journey
  - set boarding stop
  - arm alarm
  - cancel alarm
- explicit user approval for writes
- idempotency key
- receipt/action result
- device command delivery
- post-write fresh readback
- duplicate write does not duplicate side effect

**Exit gate W15:** real ChatGPT → gateway → physical device → receipt → fresh-state round-trip passes on the accepted RC.

---

# 21. Workstream 16 — Android system surfaces

**Primary tasks:** G-186, G-248

Core v1:
- active journey ongoing notification
- lock-screen information/actions
- accurate next/target/phase
- safe/idempotent actions

Scope-decision items:
- home-screen widgets
- shortcuts/deep links
- Wear OS
- Android Auto/Automotive

These optional surfaces must not destabilize the core alarm.

---

# 22. Workstream 17 — Germany-specific mobility features

**Primary tasks:** G-188, G-189, G-249

Capability-gated only:
- Deutschland-Ticket eligibility
- fare information
- bike/dog/passenger profile
- booking/ticket deep-links
- station facilities
- carriage/coach/crowding
- long-distance coach data
- cross-border architecture

Rules:
- no unsupported fare claim
- no fabricated ticket eligibility
- no direct ticket custody/payment in core scope without a separate commercial decision
- unknown must be explicit

---

# 23. New planning gaps that must be added to the canonical task graph

These are separate tasks to create before release closure because they are not sufficiently explicit in the current graph.

## PLAN-NEW-001 — Android OS/API compatibility matrix
Current `minSdk=23` means compatibility cannot be inferred from API36 alone.

Plan:
- Decide whether minSdk 23 remains a supported promise.
- If yes, verify representative behavior on API 23, 26, 29, 31, 33 and 36.
- At minimum test install/launch, permission flow, notifications, FGS/background journey, storage/data pack, TTS, Bluetooth behavior where platform changes apply.
- Include at least one real non-Pixel OEM if practical.
- If support cannot be maintained reliably, explicitly raise minSdk before release and document the decision.

## PLAN-NEW-002 — Production signing, AAB and key custody
- Enable Play App Signing strategy.
- Define upload key generation and secure custody.
- No private signing key in repository.
- Release build type.
- Produce AAB.
- VersionCode/versionName strategy.
- Reproducible release provenance.
- checksum
- mapping/native debug symbols if applicable
- install/upgrade smoke from prior test build
- key rotation/recovery runbook

## PLAN-NEW-003 — Third-party licenses and attribution
Produce one audited NOTICE/attribution inventory covering:
- Android/Open Source dependencies
- GTFS/realtime data source attribution
- map engine/provider/tile attribution
- any required license text
- store/listing acknowledgement if required

## PLAN-NEW-004 — Privacy policy, support and user data request surface
- Public privacy-policy artifact.
- Support contact/channel.
- Data deletion/clear instructions.
- Connector revoke instructions.
- Diagnostic export instructions.
- Permission rationale.
- Store support URL/email.
- Versioned policy ownership/update procedure.

## PLAN-NEW-005 — Post-release reliability operations
Define:
- crash/ANR monitoring source
- Play Vitals review cadence
- provider/feed outage triage
- severity levels
- rollback criteria
- hotfix flow
- release freeze rule
- incident evidence template
- support triage
- data-feed emergency disable/fallback
- connector outage behavior
- secret rotation
- staged rollout stop criteria

## PLAN-NEW-006 — Release branch and stale PR cleanup
- Close superseded PRs.
- Archive obsolete release/test workflows.
- Ensure final release branch starts from accepted RC lineage only.
- Prevent old feature branches from being merged after field acceptance.

---

# 24. Comprehensive QA matrix

Automated and physical QA must cover the following dimensions.

## 24.1 Data
- valid Germany Full feed
- unchanged feed
- interrupted download
- corrupt ZIP
- corrupt DB
- insufficient disk
- migration from previous DB
- rollback to old-good DB

## 24.2 Network
- normal
- offline
- airplane mode
- high latency
- packet loss where test surface permits
- provider timeout
- provider 5xx
- rate limit
- realtime stale
- realtime unavailable

## 24.3 GPS/location
- good fix
- poor accuracy
- stale last-known
- tunnel/no-fix
- location disabled
- permission denied/revoked
- movement toward destination
- movement away
- crossing threshold once

## 24.4 Android lifecycle
- foreground
- background
- screen off
- lock screen
- process recreation
- service recreation
- battery saver
- Doze
- app relaunch during active journey

## 24.5 Audio
- TTS ready
- TTS unavailable
- Bluetooth connected
- Bluetooth disconnect
- reconnect
- headphones-only preference
- silent/vibration fallback
- duplicate prevention

## 24.6 UI/a11y
- light
- dark
- system
- 100% text
- 200% text
- TalkBack
- compact phone
- wide window/tablet class
- TR/DE/EN
- offline/loading/error/stale states

## 24.7 Routing
- direct
- transfer
- multiple alternatives
- no path
- same origin
- service-day rollover
- frequency
- linked transfer
- nearby walking transfer
- cancellation
- missed connection

---

# 25. Real-device acceptance pack

The final physical-device suite must be run on one exact RC.

Required scenario pack:

1. Clean install.
2. Permission onboarding.
3. Transit pack setup/download.
4. Search “Lohne”.
5. Nearby station list — no duplicate station rows.
6. Current location as origin.
7. Select destination.
8. Lohne → Achim route.
9. Provider available route.
10. Provider timeout/offline route.
11. Route alternatives.
12. Start journey.
13. Lock phone.
14. Background continuity.
15. Bluetooth/TTS behavior.
16. Network loss during journey.
17. Realtime unavailable/stale behavior.
18. GPS degradation.
19. Approaching target.
20. Pre-alert/final alarm.
21. One-shot behavior.
22. Process recreation.
23. Cancel/re-arm.
24. Light theme 8 screens.
25. Dark theme 8 screens.
26. 200% text.
27. TalkBack critical path.
28. Diagnostic export.
29. Connector live read.
30. User-approved connector write + receipt + fresh readback.

Each scenario records:
- source SHA
- APK/AAB checksum
- app version
- device model
- Android API
- feed version
- date/time
- expected
- actual
- PASS/FAIL
- evidence attachment

---

# 26. Master quality gates

## Gate Q1 — Source quality
- lint/unit green
- no known TODO/FIXME in release-critical path
- no placeholder/demo production state
- no hard-coded route/location truth

## Gate Q2 — API36 integration
- exact-head CI
- exact-head API36
- production-state screenshots
- field-RC artifact/provenance

## Gate Q3 — Physical visual/a11y
- 8 screens light/dark
- 200% text
- TalkBack/focus
- touch targets/contrast

## Gate Q4 — Core product
- search
- nearby
- static route
- realtime
- offline/degraded
- journey
- alarm

## Gate Q5 — Quality/platform
- performance
- battery
- privacy/security
- policy
- diagnostics
- localization

## Gate Q6 — Master requirement closure
- G-235..G-253 re-audited
- G-254 = all 175 accounted for
- P0 open = 0
- PARTIAL/MISSING/BLOCKED without explicit disposition = 0

## Gate Q7 — Pre-user-test completeness
- G-149 PASS
- G-160 PASS
- G-166 PASS
- G-173 PASS
- no planned feature/visual behavior debt for selected v1

## Gate Q8 — Final field acceptance
- G-136 PASS on exact RC
- connector live E2E if IN_V1

## Gate Q9 — Release packaging
- G-138
- signing/AAB
- privacy/Data Safety
- store listing
- staged rollout/rollback
- no behavior change after G-136

Only after Q9 is the application `RELEASE_READY`.

---

# 27. Release engineering plan

After G-136 passes:

1. Tag accepted source SHA internally.
2. Freeze behavior.
3. Set final versionCode/versionName.
4. Build release AAB.
5. Sign through approved upload-key process.
6. Record SHA256/provenance.
7. Verify manifest/permissions.
8. Verify no debug/test controls.
9. Install/upgrade smoke.
10. Generate final store screenshots from accepted UI.
11. Complete privacy/Data Safety.
12. Verify support/privacy links.
13. Verify license attribution.
14. Prepare closed/internal testing track if required.
15. Staged rollout plan.
16. Rollback/stop criteria.
17. Production publish remains an explicit Play Console action.

No feature fix is “small enough” to bypass re-acceptance after G-136.

---

# 28. Post-release operations

## Daily/automated signals
- crash/ANR
- Play Vitals
- release adoption
- provider/feed health
- connector health if enabled

## Incident severity
### SEV-1
Alarm safety/reliability failure, widespread crash, corrupt data migration, security exposure.

Action: halt rollout; rollback/disable affected capability; open incident immediately.

### SEV-2
Search/routing/realtime major region failure with usable fallback.

Action: typed degraded mode, provider mitigation, targeted hotfix.

### SEV-3
Non-core visual/content issue.

Action: normal patch queue.

## Hotfix rule
A hotfix that changes journey/alarm/routing semantics repeats the relevant automated and physical acceptance slices before rollout.

---

# 29. Risk register

| Risk | Impact | Mitigation |
|---|---|---|
| CI green but real device broken | Critical | G-173 + physical gates; no emulator-only completion |
| Stale PR accidentally merged | Critical | branch/PR hygiene task |
| GTFS update corrupts DB | Critical | transactional import + old-good rollback |
| Provider outage appears as no-result | High | typed errors + local static SSOT |
| Realtime mismatches static trip | High | selector/freshness validation + retained regional samples |
| Alarm fails in Doze/background | Critical | physical lifecycle matrix |
| Duplicate alarm/TTS | Critical | persisted one-shot/idempotency tests |
| Visual rebuild regresses behavior | High | exact-head UI + behavior instrumentation |
| 175-requirement scope never closes | High | P0/P1/P2 disposition freeze |
| Old Android versions unsupported despite minSdk23 | High | compatibility matrix or minSdk decision |
| Signing key/release lineage error | Critical | dedicated signing/AAB task |
| Privacy statement differs from code | Critical | policy gate on exact accepted SHA |
| Sensitive location leaks in diagnostics | Critical | default redaction + export audit |
| Optional features delay core release | Medium | provider-gated/post-v1 disposition |
| Connector write duplicates action | High | user approval + idempotency + receipt/readback |

---

# 30. Development execution checklist for every implementation task

Before coding:
- [ ] Read exact task row.
- [ ] Read direct dependency states.
- [ ] Read current `main` SHA.
- [ ] Inspect active/open PR collision.
- [ ] Define bounded acceptance.
- [ ] Define production vs fixture evidence.

During coding:
- [ ] Preserve real production wiring.
- [ ] Add typed failure states.
- [ ] Add/retain test tags only where meaningful.
- [ ] Avoid unrelated refactor.
- [ ] Add regression test for the defect.
- [ ] Update localization/accessibility where UI changes.

Before merge:
- [ ] Exact-head lint/unit/assemble PASS.
- [ ] Required API36 PASS.
- [ ] Real-feed acceptance if data/routing/realtime changed.
- [ ] Screenshot review if UI changed.
- [ ] Physical evidence if required.
- [ ] Source diff reviewed for unrelated changes.
- [ ] Task evidence updated.
- [ ] Next dependency released only after readback.

---

# 31. Final completion checklist

The product is not finished until every applicable box is checked.

### Product
- [ ] Germany-wide search usable.
- [ ] Nearby canonicalization correct.
- [ ] Static route usable.
- [ ] Realtime safely integrated.
- [ ] Trip alternatives/preferences complete.
- [ ] Active journey complete.
- [ ] Arrival alarm physically reliable.
- [ ] Offline/degraded behavior complete.
- [ ] Favorites/recents selected v1 scope complete.
- [ ] Map supportive and non-blocking.

### UX/accessibility
- [ ] 8 canonical screens accepted.
- [ ] Light/dark/system accepted.
- [ ] TR/DE/EN accepted.
- [ ] 200% text accepted.
- [ ] TalkBack accepted.
- [ ] Loading/error/offline/stale states accepted.

### Platform
- [ ] Performance budgets met.
- [ ] Battery/lifecycle accepted.
- [ ] Privacy/security accepted.
- [ ] Android API compatibility decision and matrix complete.
- [ ] Diagnostics/support export complete.

### External connector
- [ ] Real deployment.
- [ ] Device binding.
- [ ] Read E2E.
- [ ] User-approved write E2E.
- [ ] Revoke/offline/duplicate behavior.

### Master gates
- [ ] G-173 PASS.
- [ ] G-235..G-253 audited.
- [ ] G-254 master closure.
- [ ] G-149 PASS.
- [ ] G-160 PASS.
- [ ] G-166 PASS.
- [ ] G-136 PASS.

### Release
- [ ] Release signing/key custody.
- [ ] AAB.
- [ ] Versioning.
- [ ] Privacy policy.
- [ ] Data Safety.
- [ ] Permission declarations.
- [ ] Attribution/NOTICE.
- [ ] Store assets.
- [ ] Install/upgrade smoke.
- [ ] Staged rollout plan.
- [ ] Rollback plan.
- [ ] Post-release incident/support plan.
- [ ] G-138 PASS.

---

## 32. Final rule

A development lane may report progress, but it may not declare the application “finished”, “release ready” or “Play ready” until the gates in this document are satisfied by current evidence.

**The completion path is:**

`Foundations → visual/search/realtime regressions → discovery/planner → active journey → alarm → offline/accessibility/performance/policy/diagnostics → master requirement closure → pre-user-test completeness → exact RC → physical field acceptance → frozen release packaging → staged Play rollout → post-release operations.`

This plan is deliberately evidence-driven. The priority is not to maximize the number of implemented features; it is to produce a reliable, coherent, testable Android product whose accepted release behavior matches what is shipped.
