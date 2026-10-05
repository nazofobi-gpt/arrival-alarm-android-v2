# Varış V2 — Master Product Completion & Release Plan

**Plan ID:** H003-MASTER-COMPLETION-PLAN-001  
**Version:** 1.2  
**Date:** 2026-10-05  
**Product:** Varış V2 — Almanya odaklı varış alarmı Android uygulaması  
**Repository:** `nazofobi-gpt/arrival-alarm-android-v2`  
**Canonical product target:** H-003  
**Idea baseline:** `H003-IDEA-REV-002`  
**Level:** 1 / Master Product Plan  
**Reconciliation:** `CURRENT` — 2026-10-05; reconciled through IDEA-H003-031. IDEA-H003-029/030 define L0→L5 lineage and IDEA-H003-031 activates Level-5 live execution.  
**Purpose:** This document is the detailed execution plan to be followed until the product reaches product-complete, field-accepted and Play-release-ready state.

**v1.2 audit note:** v1.0 was broad but not fully explicit; v1.1 closed the identified execution gaps. v1.2 additionally embeds the complete canonical 175-row requirement catalog, the original release-acceptance scenario matrix and the design-QA checklist so this file is self-contained for development tracking. The additions below are mandatory and supersede any weaker/implicit wording in earlier versions.

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

- Transit-independent general destination proximity alarm for taxi, train, bus or any other trip, without requiring a transit provider.
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
- User-approved inference only: uncertain inferred journey/boarding/target state must never arm or change the alarm without required confirmation.
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


# Appendix A — v1.1 completeness audit corrections (MANDATORY)

This appendix exists because the v1.0 plan was comprehensive but still left several canonical requirements implicit. The application must not be declared complete while any item below remains implicit, unowned or unverified.

## A1. Product foundation: dual-mode alarm contract (G-235 / PF-002, PF-004, PF-005, PF-006)

The application has **two distinct but interoperable product modes**:

### A1.1 General destination alarm mode
This mode is independent of public-transit APIs and must work for taxi, train, bus, car passenger or other travel situations.

Required flow:
1. User selects a destination by address, POI, stop/station, map pin, favorite or current search result.
2. User chooses alarm lead strategy supported by the general mode (distance/time/adaptive as implemented).
3. App displays the exact destination, trigger basis and readiness state before arming.
4. App tracks location only for the user-started armed journey.
5. Provider/transit outage cannot disable the core GPS destination alarm.
6. Poor GPS/tunnel behavior uses explicit confidence/degraded semantics rather than inventing position.
7. Alarm remains one-shot and recoverable across background/process recreation.

### A1.2 Transit journey alarm mode
Transit mode adds route, line, platform, stop progress, transfer, realtime and scheduled context, but the arrival-alarm trigger must remain safe when those enrichments disappear.

### A1.3 One canonical state authority
A single normalized journey/alarm state contract feeds:
- Home/Route/Journey/Live screens,
- notification and lock-screen surfaces,
- TTS/voice output,
- alarm trigger logic,
- diagnostics,
- external ChatGPT connector reads,
- approved typed connector writes.

A UI screen, provider adapter or connector cannot directly invent or mutate canonical state without validation.

### A1.4 Inference confirmation rule
Any inferred boarding, selected trip, target stop or route state that materially affects arming must have an explicit confidence/provenance field. Where the field-acceptance contract requires confirmation, the app **must not arm from inference without user confirmation**.

### A1.5 Critical-action recovery
Every critical action must have a defined cancel/back/retry/recovery path:
- download/import,
- search,
- route calculation,
- route selection,
- journey start,
- boarding/route switch,
- alarm arm/cancel,
- connector write,
- data update,
- permission denial.

**Exit gate:** PF-002/PF-004/PF-005/PF-006 are individually evidenced, not merely inferred from architecture.

---

## A2. Onboarding and permission lifecycle (G-236)

This is a dedicated workstream and must not be hidden inside visual polish.

### A2.1 Permission sequencing
- Do not request all permissions on launch.
- Location permission is requested in context when the user uses current location, nearby or alarm tracking.
- Explain why precise location is required for reliable proximity alarms; handle approximate-only grant explicitly.
- Notification permission is requested before an active journey/alarm requires notifications, not gratuitously at first launch.
- Foreground-service behavior starts only from a user-initiated active journey/alarm.
- Bluetooth permission is requested only when Bluetooth-aware audio behavior is used on platform versions that require it.
- Permission denial must preserve usable non-dependent features and show a recovery action.
- Permanent denial routes to system app settings with clear explanation.

### A2.2 First-journey quick setup
Before the first armed journey, provide a compact setup for:
- TTS language/voice,
- audio route preference,
- Bluetooth/headphones behavior,
- haptic/visual fallback,
- accessibility quick settings,
- transit-data download/offline behavior.

### A2.3 Onboarding replay and skip
- Onboarding is replayable from Settings.
- It never blocks normal app use after completion.
- Replay does not reset journey/data state without explicit confirmation.

### A2.4 Permission regression matrix
Test permission flows on representative Android behavior boundaries: API 23/26/29/31/33/36 or a documented supported-minSdk alternative.

**Exit gate:** ONB-002..ONB-008 have individual PASS or user-approved scope disposition.

---

## A3. Search and discovery corrections

In addition to Workstream 2:
- Voice search (SRCH-010) receives an explicit `IN_V1` or `OUT_OF_SCOPE_V1` decision; if implemented it uses Android/system voice intent rather than embedding an AI assistant.
- Search aliases include common abbreviations, diacritics and station naming variants.
- Search result provenance distinguishes local GTFS, address/POI provider and realtime enrichment.
- Local GTFS results must render even if a secondary network provider is unavailable.
- Search cancellation must release work and leave the previous stable state usable.

---

## A4. Map corrections

Explicitly account for MAP-002/003/004/005/007/008:
- route geometry and walking legs use distinct hierarchy,
- station entrances/exits/pathways are capability-gated,
- live vehicle marker appears only from real VehiclePosition/authoritative source,
- high-contrast map compatibility is tested,
- walking turn-by-turn text + map steps receive an explicit v1 scope decision,
- offline map is a separate cost/provider decision and is never silently assumed.

Map tile/provider attribution, terms, rate limits and commercial-use conditions are part of release evidence.

---

## A5. Trip-planning corrections

The planner workstream additionally requires explicit decisions/evidence for:
- Deutschland-Ticket/local-transit preference,
- bike/dog/stroller/luggage profile,
- transfer-buffer preference,
- wheelchair/step-free capability truthfulness,
- route reliability score only if backed by measurable evidence,
- fare/price only from authoritative provider data,
- weather-aware less-outdoor-walking only if a provider and privacy/cost decision exists.

No ranking label such as “reliable”, “accessible” or “cheapest” may appear without source-backed semantics.

---

## A6. Active-journey corrections

The state machine workstream additionally includes:
- leave-now reminder,
- “last safe departure” / late-to-stop warning,
- walking-to-stop guidance,
- boarding cue with line/direction/platform,
- missed-stop recovery,
- missed-connection recovery,
- intentional mid-journey departure/route switch,
- journey summary sharing/link/QR only with explicit privacy rules,
- live-location sharing only with separate explicit consent and separate retention rules.

**Exit gate:** JNY-001..JNY-012 each has a disposition and evidence.

---

## A7. Arrival-alarm corrections

In addition to Workstream 6:
- volume-safe TTS behavior is defined,
- snooze behavior is either implemented or explicitly excluded,
- earlier-alert adjustment is either implemented or explicitly excluded,
- test-alarm action exists if kept in v1 and is clearly non-production tracking,
- exact-alarm API is used only if technically necessary and Play-policy compliant,
- notification-channel-disabled state is part of readiness preflight,
- missing TTS engine / missing selected language voice is a tested failure state,
- headphones-only preference and Bluetooth reconnect are tested separately.

**Exit gate:** ALM-001/003/005/006/007/008/009/011/013/014 are individually closed.

---

## A8. GTFS schema, realtime and data provenance corrections

The data layer must explicitly retain or deliberately reject with rationale:
- stops,
- routes,
- trips,
- stop_times,
- calendar,
- calendar_dates/exceptions,
- transfers,
- shapes,
- parent_station/platform hierarchy,
- wheelchair/accessibility metadata,
- pathways/entrances where present,
- fare metadata where legally/source-wise usable.

Realtime:
- TripUpdates,
- VehiclePositions,
- ServiceAlerts,
- freshness timestamp/age,
- delay,
- cancellation,
- platform change,
- source/provider,
- match confidence/selector provenance.

If vehicle position is interpolated rather than observed, UI/diagnostics must say so; an interpolated marker may never masquerade as an observed vehicle.

Feed updates additionally define Wi-Fi/mobile-data behavior, resumability/safe restart, storage budget and update cancellation.

---

## A9. Personalization corrections

Add explicit scope decisions for:
- recurring commute/pendler reminders,
- optional account/sync as a future capability that cannot become a hidden core dependency,
- calendar-to-trip suggestions only through explicit connector/OS consent,
- history retention duration,
- history disable/clear,
- export/delete behavior if sync is ever introduced.

---

## A10. Accessibility/design-system corrections

Add explicit acceptance for:
- reduced motion,
- no essential information conveyed only by animation,
- color-blind-safe line/status encoding,
- transit line colors constrained by contrast and source branding,
- predictive back where supported,
- motion/transition system that never blocks navigation,
- large-screen/foldable adaptive layout,
- responsive bottom sheets/dialog max width,
- documented density tiers,
- 8pt spacing system,
- screen-reader announcements for dynamic journey changes.

---

## A11. Time, timezone and service-day correctness

Routing, realtime and alarm tests must include:
- Europe/Berlin timezone,
- DST spring-forward,
- DST fall-back/repeated hour,
- service after midnight,
- device clock/timezone change during inactive app,
- stale timestamp after long sleep,
- future route selected across a DST boundary when practical.

No route/ETA/alarm calculation may silently mix UTC, device timezone and provider-local time.

---

## A12. Android/device compatibility matrix expansion

PLAN-NEW-001 is strengthened as follows.

If `minSdk=23` remains:
- API 23: install/launch/basic location/TTS,
- API 26: notification channel + background execution changes,
- API 29: location/privacy behavior,
- API 31: Bluetooth/runtime restrictions and PendingIntent mutability-sensitive behavior,
- API 33: notification runtime permission,
- API 36: current target behavior and FGS policy.

Physical-device matrix should include:
- at least one current Android device,
- at least one non-Pixel/OEM device with aggressive background/battery management when available,
- low-storage condition,
- offline condition,
- compact screen,
- large/wide window.

A support promise that is not tested must be removed by raising minSdk or explicitly narrowing the compatibility claim before release.

---

## A13. Android application security hardening

Release security is expanded beyond generic “HTTPS + no secrets”. Verify:
- exported components minimized and reviewed,
- OAuth/deep-link callback validation,
- intent spoofing protections where applicable,
- PendingIntent mutability flags correct,
- sensitive connector credentials stored using platform-secure storage/Keystore-backed approach where applicable,
- `allowBackup`/backup policy matches privacy design,
- no cleartext traffic,
- TLS certificate failures fail closed,
- no token/location data in logcat or diagnostics,
- dependency/SBOM/vulnerability and secret scans,
- release build contains no debug endpoints or test credentials,
- threat model for location, connector and command-write paths.

---

## A14. Provider/data license and legal provenance

Release evidence must include for every external source/provider:
- provider name,
- endpoint/source,
- license/terms URL or retained evidence,
- permitted use relevant to the app,
- attribution requirement,
- update/fetch restrictions,
- retention/caching limits if any,
- rate limits,
- commercial-use restriction if any,
- source/version/fetched-at in diagnostics where relevant.

A provider that cannot legally/operationally support the intended product must be capability-disabled rather than silently used.

---

## A15. Play Console/store checklist expansion

G-138 must explicitly verify the exact accepted build against:
- final application ID/package ownership,
- target SDK/current Play requirement,
- versionCode/versionName,
- Play App Signing/upload-key custody,
- release AAB,
- R8/ProGuard/minification decision and mapping retention when used,
- adaptive launcher icon,
- splash/launch branding,
- store icon,
- feature graphic if required,
- phone screenshots from accepted UI,
- localized store text where shipped,
- app category,
- target audience declaration,
- content rating/IARC questionnaire,
- ads declaration,
- app-access declaration,
- Data Safety,
- privacy-policy URL,
- support email/URL,
- foreground-service/location declarations,
- testing-track strategy,
- staged rollout,
- rollback/stop rollout criteria.

Store screenshots/claims must describe the accepted build, not future or provider-dependent features.

---

## A16. Install, upgrade and migration acceptance

Before release:
- clean install of release-signed artifact,
- upgrade from latest distributed test build where signature lineage permits,
- upgrade from the last compatible production/internal release if one exists,
- DB/schema migration,
- transit-data migration/reindex,
- preferences/favorites/history migration,
- connector session handling after upgrade,
- active-journey behavior across app update must be explicitly supported or explicitly blocked with safe UX,
- failed migration must not silently destroy user data,
- reinstall/signature-mismatch troubleshooting documented for test builds.

Downgrade support is not assumed unless explicitly designed.

---

## A17. Support, incident and status operations

PLAN-NEW-004/005 additionally require:
- user-facing support path,
- issue intake template containing app/source version and redacted diagnostics,
- severity and ownership rules,
- provider outage status procedure,
- GTFS source outage/update-failure procedure,
- connector outage procedure,
- security incident procedure,
- rollback/hotfix decision tree,
- Play rollout halt procedure,
- post-incident review template,
- known-issues/release-notes process.

---

## A18. Privacy/GDPR planning for Germany/EU

Because the product uses precise location and optional external connector state, release planning must include an EU/Germany privacy review:
- data inventory and data-flow diagram,
- local-only vs transmitted fields,
- purpose for each transmitted field,
- minimum retention,
- processor/subprocessor inventory for connector/hosting providers,
- deletion/revoke behavior,
- privacy-policy consistency,
- consent/permission UX where legally or product-wise required,
- no unnecessary analytics/advertising identifiers,
- user diagnostics export is redacted by default.

This is a product/compliance checklist, not a substitute for professional legal advice where legal interpretation is required.

---

## A19. Monetization and traction experiments (persistent H-003 scope)

The canonical mobile-product scope includes monetization experiments. This must be planned separately from first-release product correctness.

### A19.1 Default release posture
- Core release may launch free.
- Do not add ads, subscription or paywall merely to satisfy “monetization”.
- No billing dependency may block the core arrival alarm unless the user explicitly changes product strategy.

### A19.2 Evidence to collect after a stable release/pilot
Privacy-minimal signals:
- successful journey starts/completions,
- alarm success/failure feedback,
- repeat usage/retention at an aggregate or consented level,
- feature usage relevant to premium hypotheses,
- provider/hosting operating cost,
- support burden,
- explicit user feedback.

### A19.3 Premium hypotheses
Only after evidence, compare options such as:
- one-time unlock,
- subscription,
- advanced saved/commute features,
- enhanced realtime/provider capability,
- advanced TTS/audio/personalization.

If any digital paid feature is sold in-app, Play Billing/current policy is reverified before implementation.

### A19.4 Analytics rule
Do not introduce invasive third-party analytics solely for monetization. Any telemetry must have a stated purpose, minimization, disclosure and deletion/retention policy.

**New planning task required:** `MONETIZATION / TRACTION EXPERIMENT PLAN` after stable release or controlled pilot; it is not a blocker for G-136 unless the user explicitly makes monetization part of v1 acceptance.

---

## A20. Requirement traceability register — mandatory before G-254 terminal

The plan must not rely only on narrative prose. A machine/auditor-readable requirement register must exist with columns:

`Requirement ID | Priority | Initial State | v1 Disposition | Canonical Task | Code/Artifact | Automated Evidence | Provider/Feed Evidence | Physical Evidence | Final State | Notes`

The 158 initially non-PASS requirements are mapped as follows:

| Coverage node | Requirement IDs |
|---|---|
| G-235 Product foundation | PF-002, PF-004, PF-005, PF-006 |
| G-236 Onboarding & permissions | ONB-002..ONB-008 |
| G-237 Search & discovery | SRCH-001..SRCH-010 |
| G-238 Map | MAP-002, MAP-003, MAP-004, MAP-005, MAP-007, MAP-008 |
| G-239 Trip planning | PLAN-001..PLAN-012 |
| G-240 Transit detail IA | DISC-001..DISC-008 |
| G-241 Active journey | JNY-001..JNY-012 |
| G-242 Arrival alarm | ALM-001, ALM-003, ALM-005, ALM-006, ALM-007, ALM-008, ALM-009, ALM-011, ALM-013, ALM-014 |
| G-243 Data & realtime | DATA-001..DATA-005; RT-001..RT-007 |
| G-244 Offline/degraded | OFF-001, OFF-004, OFF-005, OFF-006 |
| G-245 Personalization | PERS-001..PERS-006 |
| G-246 Accessibility/adaptive | A11Y-001..A11Y-010 |
| G-247 Design system | DES-001, DES-002, DES-003, DES-004, DES-006, DES-007, DES-008, DES-009, DES-010, DES-011, DES-012 |
| G-248 System surfaces | SYS-001..SYS-006 |
| G-249 Germany-specific | GER-001..GER-007 |
| G-250 External connector | CON-002, CON-004, CON-005 |
| G-251 Content/localization | LOC-001..LOC-005 |
| G-252 Performance/privacy/observability | PERF-001..PERF-006; SEC-001..SEC-003; OBS-001..OBS-003 |
| G-253 QA/release | QA-002..QA-010; REL-001..REL-004 |

These rows total **158** initially non-PASS requirements. The exact **17 initially-PASS requirements** from the canonical Gap Analysis are:

`PF-001, PF-003, ONB-001, MAP-001, MAP-006, ALM-002, ALM-004, ALM-010, ALM-012, OFF-002, OFF-003, DES-005, CON-001, CON-003, CON-006, CON-007, QA-001`.

Together: **158 + 17 = 175 exact requirement IDs**. The 17 initial PASS rows are not permanently trusted: G-254 must re-audit them against the final product lineage because later implementation can regress a previously passing requirement.

**New planning task required:** produce and retain `H003_175_REQUIREMENT_TRACEABILITY_MATRIX` with all 175 exact IDs before G-254 terminal acceptance.

---

## A21. Expanded final physical scenarios

The G-136/real-device suite in Section 25 is expanded with these mandatory scenarios:

31. General destination alarm without transit provider.
32. Destination selected by address/POI/map pin.
33. Inference requiring confirmation — verify alarm cannot arm without confirmation.
34. Approximate-only location permission and upgrade to precise.
35. Notification permission denied; readiness explains recovery.
36. Notification channel manually disabled.
37. TTS engine/language unavailable.
38. Bluetooth connected → disconnect → reconnect.
39. Device battery saver/Doze.
40. App process killed during armed journey and recovered.
41. Low storage during data download/import.
42. Interrupted data download/update; old-good data retained where applicable.
43. Corrupt feed/update rollback.
44. DST/service-after-midnight route/alarm case.
45. Search/route provider 5xx/rate-limit/timeout differentiated from no-result.
46. Release build upgrade/migration smoke.
47. Redacted diagnostics export contains no token or precise-history leak by default.
48. OAuth/deep-link connector callback validation and revoke.
49. Stale connector read and duplicate connector write fail closed.
50. Physical route/alarm smoke on at least one aggressive-background OEM device when available.

---

## A22. Definition of Ready / Definition of Done for every task

### Definition of Ready
A task is READY only if:
- exact requirement IDs/scope are known,
- direct dependencies are terminal or explicitly non-blocking,
- current main/active PR collision is checked,
- source of truth/data/provider is identified,
- acceptance criteria include failure/degraded behavior,
- evidence type is known (unit/CI/API36/provider/physical/user),
- no unresolved user decision is being silently guessed.

### Definition of Done
A task is DONE only if:
- production source is integrated or a verified no-code decision is recorded,
- exact-head automated gates pass,
- required real-feed/provider evidence passes,
- required physical evidence passes,
- accessibility/localization consequences are handled,
- diagnostics/failure states exist,
- no stale branch is the only evidence,
- canonical task row is read back after update,
- downstream dependency is released intentionally.

---

## A23. Audit verdict for v1.1

The v1.0 plan was **not fully explicit enough** to claim “everything planned”. The main omissions were:

1. transit-independent general GPS destination-alarm mode,
2. user-approved inference gate,
3. dedicated onboarding/permission lifecycle,
4. per-requirement traceability for all 175 requirements,
5. several P1/P2 items that were only implied (voice search, route reliability, weather, share/QR, snooze/test alarm, reduced motion, predictive back, calendar suggestions),
6. DST/timezone acceptance,
7. deeper Android security/release hardening,
8. store-console metadata/declarations,
9. install/upgrade/migration behavior,
10. EU/Germany privacy-operational review,
11. persistent monetization/traction experiment scope.

With this appendix, these omissions are now explicitly planned. The remaining non-document work is to **create/reconcile the corresponding canonical tasks/requirement rows and execute them**. The document itself does not convert an unimplemented requirement into PASS.


# Appendix B — Canonical 175 Requirement Catalog (self-contained baseline)

The table below is embedded from `Varis_Uygulamasi_Master_Product_Plan_v1.0.docx`. It is the normative feature/acceptance checklist for H-003. v1.2 does not replace these requirements with summaries; it carries them directly so development can select an exact requirement ID before implementation and can close it only with evidence.

| Req ID | Alan | Requirement | Öncelik | Acceptance | Mevcut eşleşme |
| --- | --- | --- | --- | --- | --- |
| PF-001 | Ürün temeli | GPS hedef-yakınlık alarmı transit API olmadan da çalışmalı. | P0 | Kullanıcı herhangi bir harita pini/POI/adres için alarm kurabilir; transit provider kapalıyken de konum temelli alarm çalışır. | G-181 |
| PF-002 | Ürün temeli | Transit journey ve genel destination-alarm aynı uygulamada iki ayrı ama birleşebilir mod olmalı. | P0 | Transit rota yokken standalone alarm; rota seçilince journey state ile otomatik bağlanır. | G-180; G-181 |
| PF-003 | Ürün temeli | Uygulama gerçek veri yokken mock/demo/uydurma sonuç göstermemeli. | P0 | Production build’de fixture/demo seçilemez; unavailable/stale açık görünür. | G-149; G-173 |
| PF-004 | Ürün temeli | Tek canonical journey state tüm ekran, bildirim, TTS ve connector yüzeylerini beslemeli. | P0 | Aynı anda çelişkili current/next/target bilgisi üretilemez. | G-180; G-153 |
| PF-005 | Ürün temeli | Provider eksikliği ürün gerçeği olarak modellenmeli. | P0 | Capability matrix: available/stale/unavailable/not-supported ayrı durumlar. | G-176; G-177 |
| PF-006 | Ürün temeli | Tüm kritik işlemler iptal/geri dönüş ve recovery yoluna sahip olmalı. | P0 | Search, route, alarm, journey, feed update ve connector için deterministic recovery state vardır. | G-173 |
| ONB-001 | Onboarding & permissions | İlk açılışta ürün değeri 3 ekranı geçmeden anlatılır; özellik duvarı yoktur. | P1 | <60 sn içinde arama veya destination alarm kurulabilir. | G-191; G-167 |
| ONB-002 | Onboarding & permissions | Konum izni bağlam içinde ve minimum scope ile istenir. | P0 | İzin verilmeden önce neden gerektiği açıklanır; ret durumunda manuel origin çalışır. | G-191; G-167 |
| ONB-003 | Onboarding & permissions | Bildirim izni aktif yolculuk/alarma geçmeden önce bağlamlı istenir. | P0 | Ret halinde in-app guidance devam eder ve kullanıcı risk konusunda bilgilendirilir. | G-191; G-167 |
| ONB-004 | Onboarding & permissions | Background/FGS davranışı yalnız kullanıcı başlattığı journey süresince ve politika uyumlu olur. | P0 | FGS başlangıcı kullanıcı aksiyonuna bağlı; journey bitince kapanır. [R6] | G-191; G-167 |
| ONB-005 | Onboarding & permissions | TTS/Bluetooth/headphones tercihi ilk journey öncesi hızlı setup ile yapılabilir. | P1 | Test phrase + output route görünür. | G-191; G-167 |
| ONB-006 | Onboarding & permissions | Erişilebilirlik quick setup sunulur. | P1 | Büyük metin, yüksek kontrast, azaltılmış motion, sesli rehber kısayolları. | G-191; G-167 |
| ONB-007 | Onboarding & permissions | Data/offline download tercihi açıklanır. | P1 | Wi-Fi only / cellular / manual update seçenekleri. | G-191; G-167 |
| ONB-008 | Onboarding & permissions | Onboarding tekrar oynatılabilir ama uygulamayı kullanmayı engellemez. | P2 | Settings→Help üzerinden erişim. | G-191; G-167 |
| SRCH-001 | Search & discovery | Ana ekran tek dokunuşla “Nereye?” araması, mevcut konum ve favori hedefleri sunar. | P0 | Arama alanı ilk viewportta; current location erişilebilir. | G-168; G-170; G-171; G-182 |
| SRCH-002 | Search & discovery | Stop, station, address, POI, line ve trip/sefer araması tek omnibox içinde desteklenir. | P0 | Result type rozetleri ve doğru destination navigation. | G-168; G-170; G-171; G-182 |
| SRCH-003 | Search & discovery | Nationwide local index autocomplete anında çalışır; provider hatası no-result ile karışmaz. | P0 | Lohne/Berlin/München fixture + real-device smoke. | G-168; G-170; G-171; G-182 |
| SRCH-004 | Search & discovery | Typo/fuzzy/diacritic/alias arama desteklenir. | P1 | München/Muenchen; Hbf/Hauptbahnhof; yerel aliaslar. | G-168; G-170; G-171; G-182 |
| SRCH-005 | Search & discovery | Yakın duraklar station-level canonicalize edilir; platformlar gerektiğinde expand edilir. | P0 | Duplicate station row yok; distance+bearing doğru. | G-168; G-170; G-171; G-182 |
| SRCH-006 | Search & discovery | Recent searches/trips gizlilik dostu local history ile gösterilir. | P1 | Silme/clear all; private mode. | G-168; G-170; G-171; G-182 |
| SRCH-007 | Search & discovery | Favorite place/stop/line arama sonuçlarında görünür ve hızlı aksiyon sağlar. | P1 | Home/Work/custom favorites. | G-168; G-170; G-171; G-182 |
| SRCH-008 | Search & discovery | Arama offline cache üzerinde çalışır. | P0 | Ağ yokken local results; stale badge. | G-168; G-170; G-171; G-182 |
| SRCH-009 | Search & discovery | Empty/error/provider-timeout farklı tasarım state’leri kullanır. | P0 | Her state kullanıcıya bir sonraki eylemi verir. | G-168; G-170; G-171; G-182 |
| SRCH-010 | Search & discovery | Voice search Android system intent ile opsiyonel desteklenir. | P2 | Permissionless speech intent fallback; keyboard her zaman var. | G-168; G-170; G-171; G-182 |
| MAP-001 | Map experience | Harita mevcut konum, yakın duraklar ve seçilebilir pinleri gösterir. | P0 | Location + stop marker + selection state. | G-187; G-189 |
| MAP-002 | Map experience | Route geometry ve walking legs ayrı görsel hiyerarşiyle çizilir. | P0 | Transit/walk legs karışmaz. | G-187; G-189 |
| MAP-003 | Map experience | Station entrances/exits/pathways veri varsa gösterilir. | P1 | Capability-gated; veri yoksa saklanır. | G-187; G-189 |
| MAP-004 | Map experience | Live vehicle marker yalnız gerçek provider position varsa gösterilir. | P1 | Freshness timestamp + source; fake interpolation yok. | G-187; G-189 |
| MAP-005 | Map experience | Map style light/dark ve high-contrast modlarla uyumludur. | P1 | Labels okunabilir; rota rengi contrast PASS. | G-187; G-189 |
| MAP-006 | Map experience | Map bağımsız source-of-truth değildir; list/detail eşdeğer bilgi sağlar. | P0 | Map yüklenmese journey sürdürülebilir. | G-187; G-189 |
| MAP-007 | Map experience | Walking turn-by-turn harita ve metin adımlarıyla verilir. | P1 | Off-route/re-route davranışı. | G-187; G-189 |
| MAP-008 | Map experience | Offline map strategy ayrı provider/cost kararıyla capability-gated tasarlanır. | P2 | Offline tiles varsa lisans/size policy PASS. | G-187; G-189 |
| PLAN-001 | Trip planning | Depart now / arrive by / future date-time desteklenir. | P0 | DST/timezone/service-day doğru. | G-169; G-175; G-178; G-179; G-184; G-188 |
| PLAN-002 | Trip planning | Multiple route options aynı normalize itinerary modelinde üretilir. | P0 | Her seçenek total time, depart/arrive, walk, transfer, modes. | G-169; G-175; G-178; G-179; G-184; G-188 |
| PLAN-003 | Trip planning | Tercihler: fastest, fewest transfers, less walking, simple, accessible. | P0 | Preference değişince sıralama deterministik. | G-169; G-175; G-178; G-179; G-184; G-188 |
| PLAN-004 | Trip planning | Germany-specific: D-Ticket only / local transit preference. | P1 | D-Ticket suitability açık; garanti olmayan fare işaretlenir. | G-169; G-175; G-178; G-179; G-184; G-188 |
| PLAN-005 | Trip planning | Bike, dog, stroller, luggage, transfer-buffer kullanıcı profilleri. | P1 | Provider/data varsa filter; yoksa unsupported açıklaması. | G-169; G-175; G-178; G-179; G-184; G-188 |
| PLAN-006 | Trip planning | Wheelchair/step-free routing yalnız veri yeterliyse önerilir. | P0 | Eksik accessibility data güvenli şekilde belirtilir. | G-169; G-175; G-178; G-179; G-184; G-188 |
| PLAN-007 | Trip planning | Weather-aware “less outdoor walking” opsiyonu provider varsa eklenebilir. | P2 | Core routing hava verisine bağımlı olmaz. | G-169; G-175; G-178; G-179; G-184; G-188 |
| PLAN-008 | Trip planning | Provider timeout halinde static local router first-use fallback üretir. | P0 | Cache önkoşulu olmadan route. | G-169; G-175; G-178; G-179; G-184; G-188 |
| PLAN-009 | Trip planning | No-path ve provider-error ayrıdır. | P0 | Retry / change options / offline path önerileri. | G-169; G-175; G-178; G-179; G-184; G-188 |
| PLAN-010 | Trip planning | Alternatif kalkışlar ve missed-connection recovery her itinerary’de erişilebilir. | P0 | Bir sonraki gerçek viable option görünür. | G-169; G-175; G-178; G-179; G-184; G-188 |
| PLAN-011 | Trip planning | Route reliability score gerçek kanıta dayanır. | P1 | Transfer margin, RT freshness, cancellations; uydurma puan yok. | G-169; G-175; G-178; G-179; G-184; G-188 |
| PLAN-012 | Trip planning | Fare/price provider varsa route comparison’da gösterilir. | P1 | Currency, fare source, disclaimer. | G-169; G-175; G-178; G-179; G-184; G-188 |
| DISC-001 | Transit detail IA | Stop/station detail: departures, arrivals, lines, platforms, alerts, accessibility. | P0 | Route plan önkoşulu olmadan açılır. | G-171; G-176; G-182; G-189 |
| DISC-002 | Transit detail IA | Line detail: directions, all stops, active vehicles/provider status, alerts. | P0 | Direction ayrımı açık. | G-171; G-176; G-182; G-189 |
| DISC-003 | Transit detail IA | Trip detail: full stop sequence, planned vs realtime, platform changes. | P0 | Every stop timing + freshness. | G-171; G-176; G-182; G-189 |
| DISC-004 | Transit detail IA | Parent station / child platform hierarchy korunur. | P0 | Platform-level detay expandable. | G-171; G-176; G-182; G-189 |
| DISC-005 | Transit detail IA | Network/route map view provider datası uygunsa sunulur. | P1 | Static PDF/deep-link veya native map. | G-171; G-176; G-182; G-189 |
| DISC-006 | Transit detail IA | Station amenities/entrances/exits/toilets/lockers provider varsa capability-gated. | P2 | Availability/source label. | G-171; G-176; G-182; G-189 |
| DISC-007 | Transit detail IA | Crowding/coach sequence/best carriage provider varsa capability-gated. | P2 | Unsupported gizlenir; stale görünür. | G-171; G-176; G-182; G-189 |
| DISC-008 | Transit detail IA | Service alerts doğru entity’ye bağlanır. | P0 | Route/stop/trip scoped alerts. | G-171; G-176; G-182; G-189 |
| JNY-001 | Active journey | Journey state phases: PREPARE→WALK→WAIT→BOARD→RIDE→TRANSFER→GET_OFF→FINAL_WALK→ARRIVED. | P0 | Her phase için explicit transition ve recovery. | G-172; G-180; G-186 |
| JNY-002 | Active journey | Leave-now reminder ve “son güvenli çıkış” zamanı. | P1 | User-configurable; calendar-independent. | G-172; G-180; G-186 |
| JNY-003 | Active journey | Walking-to-stop turn guidance ve late warning. | P0 | Off-route reroute. | G-172; G-180; G-186 |
| JNY-004 | Active journey | Boarding cue: doğru line/direction/platform ve araç bekleme. | P0 | Wrong-direction ambiguity fail-closed. | G-172; G-180; G-186 |
| JNY-005 | Active journey | Current/next/target stop ve remaining stops. | P0 | Evidence-based; no phantom current stop. | G-172; G-180; G-186 |
| JNY-006 | Active journey | Transfer countdown, platform change, connection risk. | P0 | Miss risk → alternate route CTA. | G-172; G-180; G-186 |
| JNY-007 | Active journey | Get-off cue with pre-alert, imminent alert and final alarm. | P0 | Screen off + background. | G-172; G-180; G-186 |
| JNY-008 | Active journey | Missed stop/connection recovery. | P0 | State machine reroutes or selects next viable stop. | G-172; G-180; G-186 |
| JNY-009 | Active journey | Realtime change journey state’i güvenli günceller. | P0 | Schedule vs RT distinction. | G-172; G-180; G-186 |
| JNY-010 | Active journey | User can switch departure/route mid-journey intentionally. | P1 | Explicit confirm if alarm target changes. | G-172; G-180; G-186 |
| JNY-011 | Active journey | Share journey summary/link/QR; live location only explicit consent. | P1 | Privacy default off. | G-172; G-180; G-186 |
| JNY-012 | Active journey | Trip progress system surfaces: notification/lockscreen/widget/watch. | P1 | Same state, no divergent copy. | G-172; G-180; G-186 |
| ALM-001 | Arrival alarm | Alarm lead can be distance, time, stops or adaptive hybrid. | P0 | Mode-specific validation. | G-181; G-191 |
| ALM-002 | Arrival alarm | Adaptive threshold considers accuracy, speed, RT uncertainty and stop sequence. | P0 | Bounded; no oscillating arm/fire. | G-181; G-191 |
| ALM-003 | Arrival alarm | Poor GPS/tunnel fallback uses schedule/stop progress with confidence. | P0 | Uncertainty visible; no false precision. | G-181; G-191 |
| ALM-004 | Arrival alarm | One-shot/idempotent final alarm. | P0 | Process recreation/reconnect duplicate=0. | G-181; G-191 |
| ALM-005 | Arrival alarm | Missed-stop detection and recovery. | P0 | Passed target → explicit recovery, not silent success. | G-181; G-191 |
| ALM-006 | Arrival alarm | Audio: device TTS, selected voice/language, volume-safe behavior. | P0 | TR/DE/EN voice acceptance. | G-181; G-191 |
| ALM-007 | Arrival alarm | Headphones-only and Bluetooth routing options. | P1 | Reconnect does not duplicate or miss cue. | G-181; G-191 |
| ALM-008 | Arrival alarm | Haptic pattern + visual notification always available as fallback. | P0 | Hearing-impaired path. | G-181; G-191 |
| ALM-009 | Arrival alarm | Snooze / earlier alert / cancel / test alarm. | P1 | State updates receipt + UI. | G-181; G-191 |
| ALM-010 | Arrival alarm | Locked screen/background/process death continuity. | P0 | Physical device acceptance. | G-181; G-191 |
| ALM-011 | Arrival alarm | Battery saver / Doze degradation is explicitly handled. | P0 | User sees readiness and remediation. | G-181; G-191 |
| ALM-012 | Arrival alarm | Standalone alarm for taxi/car/passenger mode. | P1 | No transit data requirement. | G-181; G-191 |
| ALM-013 | Arrival alarm | Alarm readiness preflight before arming. | P0 | Location, notifications, TTS, battery restrictions, destination validated. | G-181; G-191 |
| ALM-014 | Arrival alarm | Exact alarm API only if functionally justified and policy-compliant. | P0 | Permission path audited; avoid unnecessary exact alarms. | G-181; G-191 |
| DATA-001 | Data & realtime | Germany Full GTFS imported transactionally with version/provenance. | P0 | Old-known-good DB survives failed update. | G-174; G-176; G-177; G-185 |
| DATA-002 | Data & realtime | Stops, routes, trips, stop_times, calendars, exceptions, transfers, shapes modeled. | P0 | Integrity/count checks. | G-174; G-176; G-177; G-185 |
| DATA-003 | Data & realtime | parent_station/platform hierarchy retained. | P0 | Nearby and departure board consistency. | G-174; G-176; G-177; G-185 |
| DATA-004 | Data & realtime | Accessibility/pathways/fare metadata retained when present. | P1 | No schema loss. | G-174; G-176; G-177; G-185 |
| DATA-005 | Data & realtime | Feed update has Wi-Fi/data/storage budgets and resume. | P1 | Partial download never corrupts active DB. | G-174; G-176; G-177; G-185 |
| RT-001 | Data & realtime | TripUpdates, VehiclePositions, ServiceAlerts capability-gated. | P0 | Fresh/stale/unavailable distinct. [R8][R9] | G-174; G-176; G-177; G-185 |
| RT-002 | Data & realtime | RT age/freshness thresholds visible. | P0 | Stale RT cannot masquerade as live. | G-174; G-176; G-177; G-185 |
| RT-003 | Data & realtime | Cancellation, platform, delay overlay static itinerary safely. | P0 | Static fallback retained. | G-174; G-176; G-177; G-185 |
| RT-004 | Data & realtime | Multi-region provider routing/failover. | P0 | Provider health + backoff + circuit breaker. | G-174; G-176; G-177; G-185 |
| RT-005 | Data & realtime | Provider error taxonomy stable. | P0 | timeout/auth/rate-limit/invalid-data/no-result separate. | G-174; G-176; G-177; G-185 |
| RT-006 | Data & realtime | No provider can directly mutate canonical UI state without validation. | P0 | Adapter→normalize→domain validation. | G-174; G-176; G-177; G-185 |
| RT-007 | Data & realtime | Live vehicle interpolation optional and explicitly distinguished from observed position. | P2 | No fabricated “live”. | G-174; G-176; G-177; G-185 |
| OFF-001 | Offline & degraded mode | Offline stop search and static trip planning. | P0 | Airplane-mode route test. | G-185; G-169 |
| OFF-002 | Offline & degraded mode | Last successful journey cached for active trip continuity. | P0 | No stale route silently shown as current. | G-185; G-169 |
| OFF-003 | Offline & degraded mode | Offline TTS and alarm remain available. | P0 | No cloud dependency. | G-185; G-169 |
| OFF-004 | Offline & degraded mode | Offline/stale banners are informative but not blocking. | P1 | Source+last update shown. | G-185; G-169 |
| OFF-005 | Offline & degraded mode | User can manage offline data/storage. | P1 | Size, last update, clear/redownload. | G-185; G-169 |
| OFF-006 | Offline & degraded mode | Provider outage on first use still allows static route after GTFS install. | P0 | No prior remote success required. | G-185; G-169 |
| PERS-001 | Personalization | Favorite places/stops/lines/routes. | P1 | Home quick access + edit/reorder. | G-183; G-153 |
| PERS-002 | Personalization | Recurring commute / pendler journeys and reminders. | P1 | Weekday/time schedule. | G-183; G-153 |
| PERS-003 | Personalization | Travel preferences persist locally. | P1 | Walking, transfers, accessibility, modes. | G-183; G-153 |
| PERS-004 | Personalization | Recent history can be disabled/cleared. | P1 | Private mode. | G-183; G-153 |
| PERS-005 | Personalization | Optional account/sync is separate future capability, not core dependency. | P2 | App usable without login. | G-183; G-153 |
| PERS-006 | Personalization | Calendar-to-trip suggestion only via explicit external connector/consent. | P2 | No blanket calendar read. | G-183; G-153 |
| A11Y-001 | Accessibility & adaptive | TalkBack full path on every critical flow. | P0 | Search→route→journey→alarm navigable without sight. | G-184; G-166; G-167 |
| A11Y-002 | Accessibility & adaptive | Touch targets ≥48dp; no icon-only ambiguous controls. | P0 | Accessibility scanner + manual. [R4] | G-184; G-166; G-167 |
| A11Y-003 | Accessibility & adaptive | 200% font scale without clipping. | P0 | TR/DE/EN screenshot matrix. | G-184; G-166; G-167 |
| A11Y-004 | Accessibility & adaptive | Contrast WCAG-equivalent Android quality targets. | P0 | Small text 4.5:1 target; large/graphics 3:1. | G-184; G-166; G-167 |
| A11Y-005 | Accessibility & adaptive | Reduced motion / no essential information only in animation. | P1 | Motion preference respected. | G-184; G-166; G-167 |
| A11Y-006 | Accessibility & adaptive | Color-blind-safe line/status encoding with text/icon redundancy. | P1 | No color-only status. | G-184; G-166; G-167 |
| A11Y-007 | Accessibility & adaptive | Step-free/wheelchair routing capability-aware. | P0 | Unknown accessibility data explicitly unknown. | G-184; G-166; G-167 |
| A11Y-008 | Accessibility & adaptive | Hearing accessibility: haptic/visual equivalents for voice. | P0 | Alarm works silent/audio-off. | G-184; G-166; G-167 |
| A11Y-009 | Accessibility & adaptive | Cognitive load: one next action per journey phase. | P1 | Active journey screen prioritizes current step. | G-184; G-166; G-167 |
| A11Y-010 | Accessibility & adaptive | Large screen/foldable adaptive layouts, Tier 2 minimum; Tier 1 where useful. | P1 | Phone/tablet/foldable/desktop window tests. [R2] | G-184; G-166; G-167 |
| DES-001 | Design system | Original Material 3 Expressive-compatible design language. | P0 | Custom tokens; generic Card/Button stacking avoided. [R1] | G-167; G-160; G-166 |
| DES-002 | Design system | Typography scale optimized for transit glanceability. | P0 | ETA/line/stop hierarchy consistent. | G-167; G-160; G-166 |
| DES-003 | Design system | 8pt spacing grid + documented density tiers. | P0 | No dead-space or cramped information. | G-167; G-160; G-166 |
| DES-004 | Design system | Proper vector icon system; Unicode/emoji controls forbidden. | P0 | Icon semantic consistency. | G-167; G-160; G-166 |
| DES-005 | Design system | Light/Dark/System parity. | P0 | Same hierarchy, contrast, map readability. | G-167; G-160; G-166 |
| DES-006 | Design system | Transit line colors constrained by contrast and source branding. | P1 | Fallback palette deterministic. | G-167; G-160; G-166 |
| DES-007 | Design system | Motion system: meaningful transitions, predictive back, reduced-motion safe. | P1 | No ornamental motion that delays task. | G-167; G-160; G-166 |
| DES-008 | Design system | State components for loading/empty/error/offline/stale/permission. | P0 | All screens use shared primitives. | G-167; G-160; G-166 |
| DES-009 | Design system | Bottom sheets/cards/dialogs have responsive max-width on large screens. | P1 | No full-width tablet sheet misuse. | G-167; G-160; G-166 |
| DES-010 | Design system | Map + list visual grammar is coherent. | P1 | Selection synchronized. | G-167; G-160; G-166 |
| DES-011 | Design system | Real-device visual regression board for every screen family. | P0 | Side-by-side approved screenshots. | G-167; G-160; G-166 |
| DES-012 | Design system | Brand identity subtle; navigation readability dominates. | P1 | Logo/color never obscures route info. | G-167; G-160; G-166 |
| SYS-001 | System surfaces | Android 16 progress-centric notification for active journey. | P1 | Milestones/segments from canonical journey state. [R7] | G-186; G-191 |
| SYS-002 | System surfaces | Lock-screen ongoing journey controls. | P1 | Open, mute voice, cancel journey/alarm. | G-186; G-191 |
| SYS-003 | System surfaces | Home-screen commuter/favorites/departures widgets. | P1 | Stale/source state visible. | G-186; G-191 |
| SYS-004 | System surfaces | Wear OS journey preview and get-off cue. | P2 | Phone state mirrored; independent app not required for core. | G-186; G-191 |
| SYS-005 | System surfaces | Android Auto/Automotive passenger-safe surface evaluated separately. | P2 | No distracting unsupported UI; policy review required. | G-186; G-191 |
| SYS-006 | System surfaces | Quick settings/shortcut/deep links for “alarm to destination” and favorites. | P2 | Idempotent deep link. | G-186; G-191 |
| GER-001 | Germany-specific mobility | Deutschland-Ticket eligibility filter and explanation. | P1 | Route shows D-Ticket compatible/unknown. | G-188; G-189 |
| GER-002 | Germany-specific mobility | Fare data shown when authoritative source exists. | P1 | No invented price. | G-188; G-189 |
| GER-003 | Germany-specific mobility | Ticket purchase via official deep-link/interoperability before direct sales. | P1 | Partner handoff; return-to-app journey continuity. | G-188; G-189 |
| GER-004 | Germany-specific mobility | Bike/dog/passenger profile compatibility. | P1 | Data/capability-gated. | G-188; G-189 |
| GER-005 | Germany-specific mobility | Long-distance coach sequence/occupancy where provider permits. | P2 | Source/freshness. | G-188; G-189 |
| GER-006 | Germany-specific mobility | Station facilities and platform sector information provider-gated. | P2 | No false completeness. | G-188; G-189 |
| GER-007 | Germany-specific mobility | Cross-border route support architecture-ready but Germany-first acceptance. | P2 | No release blocker. | G-188; G-189 |
| CON-001 | External connector | Android app içinde LLM/AI runtime/API key bulunmaz. | P0 | Static scan + code review. | G-153; G-136 |
| CON-002 | External connector | External ChatGPT connector typed read tools ile journey state okur. | P1 | Freshness/source included. | G-153; G-136 |
| CON-003 | External connector | Typed write tools allowlist: origin/destination/journey/alarm only. | P1 | App validation/state machine used; raw DB mutation yok. | G-153; G-136 |
| CON-004 | External connector | Writes explicit user approval + idempotency receipt. | P0 | Duplicate side effect=0. | G-153; G-136 |
| CON-005 | External connector | OAuth/device binding/least privilege/revoke. | P0 | Secrets repo/log dışında. | G-153; G-136 |
| CON-006 | External connector | Offline/stale connector state fail-closed. | P0 | No hallucinated journey facts. | G-153; G-136 |
| CON-007 | External connector | Provider-neutral contract; ChatGPT sadece bir adapter. | P2 | Future connectors possible. | G-153; G-136 |
| LOC-001 | Content & localization | TR/DE/EN full parity and locale-correct time/date/plural. | P0 | No hard-coded UI strings. | G-192 |
| LOC-002 | Content & localization | German transit terminology reviewed. | P0 | Gleis/Bahnsteig/Haltestelle/Fahrt/Umstieg consistent. | G-192 |
| LOC-003 | Content & localization | Error copy tells what happened + what user can do. | P0 | No technical provider codes exposed as primary text. | G-192 |
| LOC-004 | Content & localization | Source/freshness copy concise. | P1 | “Planlı”, “canlı”, “x dk önce güncellendi”. | G-192 |
| LOC-005 | Content & localization | Voice/TTS pronunciation for stop names validated. | P1 | Locale/tts_stop_name when available. | G-192 |
| PERF-001 | Performance / privacy / observability | Cold start and time-to-search measured on midrange physical Android. | P0 | Budgets set from baseline; regressions block release. | G-190; G-191; G-193; G-173 |
| PERF-002 | Performance / privacy / observability | Search local results target sub-second after index ready. | P0 | P95 tracked. | G-190; G-191; G-193; G-173 |
| PERF-003 | Performance / privacy / observability | Static routing is cancelable and bounded in memory/time. | P0 | Representative Germany routes benchmark. | G-190; G-191; G-193; G-173 |
| PERF-004 | Performance / privacy / observability | Large GTFS import is streaming/transactional and never blocks UI. | P0 | ANR=0 in acceptance. | G-190; G-191; G-193; G-173 |
| PERF-005 | Performance / privacy / observability | Location update frequency adapts to journey phase and battery. | P0 | No unnecessary high-rate GPS. | G-190; G-191; G-193; G-173 |
| PERF-006 | Performance / privacy / observability | Network calls use timeout/backoff/circuit breaker. | P0 | No infinite spinner. | G-190; G-191; G-193; G-173 |
| SEC-001 | Performance / privacy / observability | Location/journey data local-first; minimum retention. | P0 | No analytics use without explicit need/consent. | G-190; G-191; G-193; G-173 |
| SEC-002 | Performance / privacy / observability | Logs redact precise location, tokens and sensitive journey history by default. | P0 | Export bundle sanitized. | G-190; G-191; G-193; G-173 |
| SEC-003 | Performance / privacy / observability | Dependencies/SBOM/secrets scanning and secure network transport. | P0 | Release gate. | G-190; G-191; G-193; G-173 |
| OBS-001 | Performance / privacy / observability | Health model separates local index, search provider, route provider, RT, connector. | P0 | Overall READY cannot hide broken core. | G-190; G-191; G-193; G-173 |
| OBS-002 | Performance / privacy / observability | Field evidence records source SHA, device/API, feed version and scenario. | P0 | Reproducible defect receipt. | G-190; G-191; G-193; G-173 |
| OBS-003 | Performance / privacy / observability | Permanent Lohne regression suite retained. | P0 | Search + route + offline injection. | G-190; G-191; G-193; G-173 |
| QA-001 | QA & release | Unit tests for parsers, state machines, ranking, alarm hysteresis. | P0 | Deterministic fixtures. | G-166; G-173; G-136; G-138 |
| QA-002 | QA & release | Integration tests across GTFS→router→journey→alarm. | P0 | Real feed sample. | G-166; G-173; G-136; G-138 |
| QA-003 | QA & release | Compose UI tests for all critical flows and degraded states. | P0 | API36 + supported min API. | G-166; G-173; G-136; G-138 |
| QA-004 | QA & release | Screenshot visual regression light/dark/TR/DE/EN/font scales. | P0 | Canonical boards. | G-166; G-173; G-136; G-138 |
| QA-005 | QA & release | Accessibility manual + automated test. | P0 | TalkBack physical device. | G-166; G-173; G-136; G-138 |
| QA-006 | QA & release | Physical background tests: lock screen, Doze, process kill, Bluetooth reconnect. | P0 | Multiple OEMs. | G-166; G-173; G-136; G-138 |
| QA-007 | QA & release | Network matrix: offline, high latency, packet loss, provider 5xx, rate limit. | P0 | Correct degrade behavior. | G-166; G-173; G-136; G-138 |
| QA-008 | QA & release | GPS matrix: good, poor, tunnel, stale last-known, location disabled. | P0 | No false arrival. | G-166; G-173; G-136; G-138 |
| QA-009 | QA & release | Data update failure/rollback/corrupt feed tests. | P0 | Old-known-good retained. | G-166; G-173; G-136; G-138 |
| QA-010 | QA & release | Release candidate exact SHA and reproducible artifact receipt. | P0 | Checksum/source provenance. | G-166; G-173; G-136; G-138 |
| REL-001 | QA & release | G-136 final field test only after product-complete gate. | P0 | No planned feature work remains afterward. | G-166; G-173; G-136; G-138 |
| REL-002 | QA & release | G-138 packaging cannot change accepted product behavior. | P0 | Any behavior change reopens acceptance. | G-166; G-173; G-136; G-138 |
| REL-003 | QA & release | Play Data Safety/privacy/permissions reflect exact accepted code. | P0 | Evidence-backed declarations. | G-166; G-173; G-136; G-138 |
| REL-004 | QA & release | Staged rollout + rollback plan. | P0 | Crash/ANR/critical defect thresholds defined. | G-166; G-173; G-136; G-138 |

---

# Appendix C — Canonical release-acceptance scenario baseline

These canonical scenarios remain mandatory where their gate applies. Appendix A21 adds further scenarios; it does not weaken this baseline.

| ID | Journey | Condition | Expected result | Gate |
| --- | --- | --- | --- | --- |
| SC-01 | Lohne → Achim | Online, provider healthy | Search/route/journey/alarm end-to-end | P0 |
| SC-02 | Lohne → Achim | Provider timeout | Static route; error≠no-result | P0 |
| SC-03 | Germany cross-region | Online | Multi-region transfer/service-day correctness | P0 |
| SC-04 | Any destination | Airplane mode | Offline search/route/alarm/TTS | P0 |
| SC-05 | Active journey | Screen locked | Notification/TTS/alarm continuity | P0 |
| SC-06 | Active journey | Doze/battery saver | Readiness/degrade + alarm reliability | P0 |
| SC-07 | Active journey | Process killed | State restore; duplicate final alarm=0 | P0 |
| SC-08 | Active journey | Poor GPS/tunnel | Hybrid confidence fallback; no false arrival | P0 |
| SC-09 | Active journey | Bluetooth disconnect/reconnect | No duplicate/missed voice cue | P0 |
| SC-10 | Transit disruption | Delay/cancel/platform change | Live overlay + alternative | P0 |
| SC-11 | Accessibility | TalkBack + 200% text | Full critical path | P0 |
| SC-12 | Large screen | Tablet/foldable/multi-window | Adaptive layout parity | P1 |
| SC-13 | Connector | Live external ChatGPT | Read + user-approved write round-trip | P1 |
| SC-14 | Data update | Corrupt/partial feed | Old-known-good retained | P0 |
| SC-15 | Theme/locale | TR/DE/EN × light/dark | Visual/content parity | P1 |

---

# Appendix D — Canonical design QA checklist

| ID | Alan | Kontrol |
| --- | --- | --- |
| DQ-01 | Hierarchy | 5-second glance: user current step, line/direction, next stop, ETA/alarm state’i anlıyor mu? |
| DQ-02 | Density | Boş alan estetik uğruna bilgi kaybettirmiyor; yoğun listelerde satırlar taranabilir. |
| DQ-03 | Iconography | Emoji/Unicode control yok; semantic vector icon. |
| DQ-04 | Typography | ETA/current step dominant; metadata secondary; 200% safe. |
| DQ-05 | Color | Status color-only değil; dark/light contrast. |
| DQ-06 | State design | Loading/empty/error/offline/stale/permission gerçek, actionable ve ayırt edilebilir. |
| DQ-07 | Motion | Süreç değişimini anlatıyor; reduced motion safe. |
| DQ-08 | Map | Route, walking leg, stop, selected state görsel olarak karışmıyor. |
| DQ-09 | Consistency | Aynı entity/component farklı ekranlarda aynı grammar. |
| DQ-10 | Platform fit | Edge-to-edge, predictive back, keyboard/foldable behavior doğal. |
| DQ-11 | Originality | Benchmark mantığı kullanılmış ama başka uygulamanın görünümü kopyalanmamış. |
| DQ-12 | Real-device proof | Canonical board ve real screenshot side-by-side gözle görülür eşleşiyor. |

---

## Appendix E — Requirement accounting invariant

- Canonical catalog rows: **175**.
- Initially PASS in Gap Analysis v1.0: **17**.
- Initially non-PASS mapped one-to-one to G-235..G-253: **158**.
- Final G-254 rule: **175/175 accounted for**, with `PARTIAL/MISSING/BLOCKED=0`; a requirement may be non-implemented only when it has an explicit user-approved `OUT_OF_SCOPE_V1`/provider-gated disposition consistent with the master plan.
- Every catalog row must carry final evidence pointers in `H003_175_REQUIREMENT_TRACEABILITY_MATRIX` before release closure.

## 32. Final rule

A development lane may report progress, but it may not declare the application “finished”, “release ready” or “Play ready” until the gates in this document are satisfied by current evidence.

**The completion path is:**

`Foundations → visual/search/realtime regressions → discovery/planner → active journey → alarm → offline/accessibility/performance/policy/diagnostics → master requirement closure → pre-user-test completeness → exact RC → physical field acceptance → frozen release packaging → staged Play rollout → post-release operations.`

This plan is deliberately evidence-driven. The priority is not to maximize the number of implemented features; it is to produce a reliable, coherent, testable Android product whose accepted release behavior matches what is shipped.
