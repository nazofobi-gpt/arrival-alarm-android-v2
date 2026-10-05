# Varış V2 — Visual Source of Truth v1.0

**Status:** ACTIVE DESIGN SPEC  
**Date:** 2026-10-05  
**Product:** H-003 / Varış V2  
**Figma target:** https://www.figma.com/design/c225z5N1EqrW1YiXDcEOGp  
**Reference viewport:** 412 × 915 px/dp-class  
**Implementation:** Android Jetpack Compose  
**Design objective:** Transit glanceability + controlled information density.  
**Important:** This document is a visual/interaction specification. Live product status remains canonical in `hafıza` and GitHub exact-head evidence.

## 1. Why this document exists

Earlier builds passed screenshot-presence and automated UI gates while still looking generic and materially different from the intended product. This file prevents that failure mode by making visual decisions explicit before Compose implementation.

Rules:

1. A screen is not production-ready because it renders.
2. Generic Material component stacking is not visual acceptance.
3. Every production screen must be traceable to this spec and the Figma master.
4. The Figma frame is the visual source of truth; Compose is an implementation.
5. A UI change is accepted only after reference-vs-device comparison.
6. Selection-heavy settings use a compact row + radio sheet/list, never three giant buttons.
7. Origin/destination/location selection is list-first. Map is secondary.
8. Real transit density is used in design examples; placeholder-empty layouts are not accepted.
9. Every screen must define loading, empty, offline, stale, timeout, permission and partial-capability behavior where applicable.
10. Light/dark parity, 200% text and TalkBack are part of design acceptance, not post-polish.

## 2. Visual character

Varış should feel like a calm, precise mobility instrument rather than a generic form application.

- Primary information: time, current/next/target stop, line/direction, alarm state.
- Secondary information: platform, delay, walking, transfer margin, source/freshness.
- Decorative whitespace is limited. Space must improve scanning or grouping.
- Most data is presented in dense rows and structured timelines, not isolated large cards.
- Cards are reserved for important grouped objects: route option, active journey summary, alarm state, critical readiness state.
- One screen should have one dominant action.
- Color never carries status alone; text/icon shape must repeat the meaning.
- Brand color is an accent, not a blanket surface.
- Maps support orientation; they do not replace searchable/selectable lists.

## 3. Tokens

Implementation tokens live in `docs/design/varis_design_tokens_v1.json`.

Core dimensions:

| Token | Value |
|---|---:|
| Reference screen width | 412 |
| Horizontal content padding | 16 |
| Section gap | 20 |
| Compact micro gap | 4 |
| Standard gap | 8 / 12 / 16 |
| Minimum touch target | 48 |
| Default transit list row | 64–72 |
| Bottom navigation | 76 |
| Main radius | 14–18 |
| Large grouped surface radius | 24 |

Typography uses **Roboto**, matching Android SansSerif implementation intent. ETA/current step uses Display/H1; line/destination uses H2/Title; metadata uses Body/Meta.

## 4. Component grammar

Every repeated object must be a reusable Figma component and an equivalent Compose component.

### C01 — Search/Omnibox
Height 56–60. Leading search icon. Optional current-location/favorite trailing action. No oversized empty card around it.

### C02 — SelectionRow
Used for Origin, Destination, Language, Theme, Voice and similar single-choice settings.

Structure:
- 40×40 semantic icon tile
- 12 gap
- eyebrow label
- selected/current value
- optional supporting status
- trailing chevron
- min height 64

Tap opens the appropriate list/bottom sheet.

### C03 — StationRow
- station icon
- station name
- canonical parent station metadata
- distance/bearing or lines
- optional favorite affordance
- optional live/stale label
- 64–76 high
- no duplicate platform children unless expanded intentionally

### C04 — DepartureRow
- left fixed 54–60 time column
- line badge
- direction
- platform
- live/planned label
- right disclosure affordance
- cancelled rows use danger semantics but remain readable

### C05 — RouteOptionCard
- first line: depart → arrive times with total duration
- second: origin → destination
- line/mode sequence
- transfer + walk summary
- realtime/source state
- selection CTA
- selected state is visually stronger but not a separate huge panel

### C06 — JourneyStepRow
Timeline row with:
- time column
- phase/stop marker
- title
- metadata (platform, live delay, walking)
- current/next/target states
Current and target must remain visually distinguishable without color.

### C07 — LiveStatusTile
Compact status tile for alarm/audio/realtime. Two-up layout where useful.

### C08 — StatusBanner
Variants: info, success, warning, danger, offline, stale. Contains state + consequence + next action.

### C09 — SettingRow
Compact 56–68 row. Label/value/summary; switch or chevron only when required. No giant button wall.

### C10 — RadioChoiceSheet
Bottom sheet/list for language/theme/voice/mode choices. 56 min row; selected radio; one tap applies.

### C11 — PrimaryAction
52 min height. Used only for the dominant step: route, start journey, arm alarm, retry critical setup.

### C12 — SecondaryAction
48 min height. Outlined/tonal. Never visually competes with the dominant action.

### C13 — BottomNavigation
5 destinations max. Proper vector icons; selected destination has compact active treatment. No Unicode/emoji.

### C14 — ReadinessRow
Subsystem name + direct state + optional remediation. Location, notifications, data, audio, realtime, connector are independent rows.

### C15 — MapPanel
Usually 180–260 high in selection screens; can expand in dedicated map screen. Search/list remains usable if map fails.

## 5. 26-screen master inventory

All screens are designed first in light mode with corresponding dark/state rules.

### S01 — Launch / readiness
Purpose: fast launch and state restoration.
- Small logo/wordmark only when no active journey.
- If journey exists: immediately show resume card.
- Data setup state shown with explicit phase: download → verify → index → ready.
- No indefinite “Veri hazırlanıyor”.
- Retry is only shown after typed failure.

### S02 — Onboarding
- Brand/value block occupies <= 35% first viewport.
- Three concise benefits: route, live journey, arrival alarm.
- Privacy/permission note compact.
- One primary “Başla” action.
- No emoji iconography.

### S03 — Home
Top order:
1. greeting/product title + compact readiness affordance
2. “Nereye?” omnibox
3. current-location / favorite shortcuts
4. active journey card OR origin/destination selector
5. nearby stations (3–4)
6. compact map preview
7. bottom navigation

No empty half-screen. Route planning starts above the fold.

### S04 — Search overlay
- Search field pinned at top.
- Grouped results: Favorites, Recents, Stops/Stations, Places/Addresses, Lines/Trips.
- Type label visible.
- Provider timeout and zero-result are distinct.
- Keyboard is always a complete fallback even if voice search is added later.

### S05 — Map / nearby
- Map is larger but still paired with a synchronized bottom list.
- Selected marker ↔ selected list row.
- Current-location accuracy state visible.
- No fake live vehicle position.

### S06 — Place / station detail
- station name + locality
- favorite action
- live/planned departures
- lines served
- alerts
- accessibility state: known/unknown/limited
- platform children expandable

### S07 — Line detail
- line badge/name
- direction selector
- all stops dense list
- service alerts
- live capability/source state
- active vehicle only if observed

### S08 — Trip / sefer detail
- service identity and direction
- full stop sequence
- planned vs actual times
- platform change
- cancellation state
- realtime freshness/source

### S09 — Planner options
Compact sections:
- time: now/depart/arrive
- route priority
- walking / transfer preference
- accessibility
- optional Germany profile (D-Ticket, bike/dog) only if in v1
All single-choice controls are list/sheet rows, not giant segmented-button walls.

### S10 — Route results
- journey summary header
- 2–4 comparable RouteOptionCards
- obvious time/duration/transfer differences
- selected route state
- source/realtime status
- no route / provider failure distinct

### S11 — Journey detail
- route headline
- compact route summary
- complete leg/stop timeline
- transfer margins
- alerts
- map segment
- start journey / arm alarm CTA

### S12 — Pre-departure
- leave-by countdown
- first stop / walking distance
- platform / line / direction
- readiness checks
- route change warning
- one dominant “Yolculuğu başlat” action

### S13 — Walk to stop
- destination stop dominant
- walking distance/time
- departure countdown
- direction/map support
- late warning / reroute
- transit departure context remains visible

### S14 — Wait / boarding
- line badge + direction are dominant
- platform
- departure time/delay
- boarding cue
- ambiguity must fail closed
- “bu araca bindim” confirmation only when product state requires it

### S15 — On-board live trip
Dominant:
- current/next/target hierarchy
- remaining stops
- get-off countdown
Secondary:
- alarm state
- audio route
- delay/freshness
- compact route strip

### S16 — Transfer
- get-off stop
- transfer walking/platform
- departure countdown
- transfer margin/risk
- missed-connection CTA
- alternative immediately reachable

### S17 — Arrival / final walk
- arrival confirmation
- alarm fired/dismissed state
- final walking instructions where relevant
- end journey
- preserve/recent-history behavior according to privacy setting

### S18 — Standalone destination alarm
Transit-independent.
- search/pin/POI destination
- distance / time / adaptive lead
- readiness
- test cue
- arm alarm
No route is required.

### S19 — Departures board
- station header
- live/planned source
- dense departure rows
- line/direction filters if useful
- favorite
- alert rows

### S20 — Favorites / commute
Sections:
- places
- stops
- lines
- saved routes
- recurring commute
Reorder/edit without visual noise.

### S21 — History
- recent searches
- recent trips
- clear all
- private mode / disable history
Precise journey data is never displayed more broadly than necessary.

### S22 — Offline data
- installed feed/version/date
- storage size
- download/update state
- Wi-Fi/mobile/manual preference
- update/clear actions
- old-good retained on update failure
- explicit stale status

### S23 — Settings
Sectioned list only.
- appearance: Theme → current value
- language → current value
- voice/TTS → current value
- notification/alarm
- accessibility
- offline/data
- privacy
- connector
- help
Rows open choice sheets/subscreens; no giant isolated buttons.

### S24 — Readiness / diagnostics
Subsystem rows:
- Location
- Notifications
- Static data
- Search
- Routing
- Realtime
- TTS/audio
- Background journey
- Connector
Overall READY may not hide one broken core subsystem.

### S25 — Connector
- connected service identity
- scopes
- last sync/freshness
- device binding
- revoke/disconnect
- command receipts
- stale/offline fail-closed state
No AI runtime inside the Android app.

### S26 — Help / feedback
- quick help topics
- replay onboarding
- report issue
- redacted diagnostic export
- privacy/support links
- app/source version

## 6. Screen state matrix

Every applicable screen must provide an explicit design for:

| State | Visual rule |
|---|---|
| Loading | bounded progress + what is happening |
| Empty | explain what is empty + primary next action |
| Offline | static/local capability remains visible |
| Stale | data remains visible with age/source warning |
| Timeout | retry/recovery; never rendered as empty |
| Permission denied | manual/degraded path + settings remediation |
| Partial capability | unsupported part hidden or explicitly unknown |
| Corrupt/update failure | last-known-good state retained |
| 200% text | reflow, no clipped CTA or metadata |
| TalkBack | headings, state and action semantics align with visual hierarchy |
| Light/Dark/System | same information hierarchy |
| Compact/Wide | no unbounded full-width forms on large screens |

## 7. Exact content density examples

Route result example:

**08:14 → 09:07 · 53 dk**  
Lohne (Oldb) → Achim  
RE 18 → Bremen Hbf · Gleis 3 · **+4 dk**  
RB 37 → Achim · Gleis 7  
**1 aktarma · 8 dk yürüme · CANLI**

Live-trip example:

**Sonraki: Bremen Hbf**  
4 durak · yaklaşık 18 dk  
Hedef: **Achim**  
Alarm: **Açık · 2 durak önce**  
Ses: Bluetooth kulaklık  
Realtime: **Canlı · 32 sn önce**

The visual system must be designed using this level of realistic content density.

## 8. Responsive rules

### Compact phone
- 16 horizontal padding
- single column
- map capped in list-first screens
- fixed bottom navigation

### Wide / tablet / foldable
- max readable content width ~720–840 for forms/lists
- route/map can become two pane
- bottom sheet uses constrained width
- navigation may promote to rail
- dense lists never stretch metadata excessively

## 9. Motion

Motion is functional:
- selection transition
- journey phase change
- alarm readiness/fired state
- expand/collapse platform detail
- bottom sheet transitions

Rules:
- no ornamental delay
- reduced-motion safe
- predictive back supported where implementation permits
- meaning remains without animation

## 10. Visual acceptance protocol

A screen does not pass because an automated test finds it.

For every canonical family:
1. Figma source frame exists.
2. Exact Compose screenshot exists.
3. Same representative production data.
4. Side-by-side visual review.
5. Check:
   - composition
   - information hierarchy
   - whitespace ratio
   - typography
   - iconography
   - row/card density
   - CTA prominence
   - line wrapping
   - state visibility
6. Light and dark pass.
7. 200% text pass.
8. Physical Android screenshot pass.

If the discrepancy is materially visible at normal viewing scale, the screen does not pass.

## 11. Implementation rule

No new major screen should be invented directly in Compose. The required sequence is:

`Figma/spec → user/design approval → Compose → exact-head screenshot → visual comparison → device acceptance`.

Existing screens that diverge from this spec are treated as implementation debt, not as design precedent.

## 12. Starter-plan Figma constraint

The created Figma account is on the Starter plan, which currently limits the design file to three pages and has an MCP tool-call quota. Therefore the file structure is intentionally compressed to:

1. Foundations & Components
2. Product Screens
3. QA & States

The limitation changes file organization only. It does not weaken the visual acceptance contract. Once Figma MCP quota is available, this spec is to be materialized into editable components and all 26 screen frames in the existing Figma file.
