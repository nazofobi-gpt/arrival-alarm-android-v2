# G-182 / L5-1045 — GTFS trip search selection

Status: implementation contract for PR #687. This is a code-only integration slice; it does not constitute physical-device or store-release acceptance.

## Entry and identity

- Entry is available only from a local GTFS `TRIP` search result.
- The result carries nonblank `trip_id`, `route_id`, and `service_id`, plus optional `direction_id`, from the indexed feed.
- The selection template is bound to the current `sourceVersion`; a changed feed version invalidates confirmation.
- The rider explicitly chooses a GTFS service date. The app does not infer it from the wall clock or a departure time.
- Resolution uses the exact `GtfsTripInstanceIdentity`. Inactive service dates, changed identities, unsupported frequency instances, incomplete stop sequences, and unreadable indexes fail closed.

## Screen states

`TRIP_RESULT → SERVICE_DATE_REQUIRED → RESOLVING → ORDERED_STOPS_READY → ROLES_SELECTED → CONFIRMABLE → SAVED`

Resolution and validation failures remain visible and cannot be confirmed. Changing the service date clears inspected detail and role selections. Changing boarding clears destination and alarm-target selections; changing destination clears an explicit alarm-target override.

## Ordered-stop role contract

- Show ordered stops with stop name, sequence, scheduled arrival/departure, platform, parent station when present, and wheelchair-accessibility metadata.
- Use bounded lazy lists for ordered stops and stop-role options.
- Boarding must be an allowed pickup stop with an actionable departure time.
- Destination must be a later allowed drop-off stop with an actionable arrival time.
- Alarm target defaults to the selected destination and may be moved to an eligible stop inside the selected segment.
- The existing `TripStopRoleValidator` remains authoritative for sequence order, time consistency, coordinates, and role eligibility.

## Accessibility and localization

- Stop names, sequence numbers, times, platform, parent station, wheelchair metadata, and selected roles are rendered as text for TalkBack.
- Role pickers have explicit labels and are screen-reader operable; selected roles are reflected in the ordered stop list.
- New stop metadata strings are supplied in Turkish, German, and English.
- Service dates are interpreted in the Europe/Berlin timezone and formatted without timezone day-shift.

## Persistence and safety boundaries

- No selection is persisted before the rider presses the explicit confirmation action.
- Confirmation uses the request gate, source-version check, existing selected-trip store, and read-back verification.
- This flow saves trip and stop choices only. It does not start GPS tracking, arm or schedule an alarm, start background work, or claim physical-device acceptance.

## Non-goals

- Realtime arrival prediction or realtime trip substitution.
- Frequency-based trip instances.
- Automatic service-date inference.
- Automatic alarm activation, GPS tracking, or background scheduling.
- G-136/G-138 physical-device and release acceptance.

## Code-level acceptance

- Android CI, API 36 instrumented tests, locale checks, and Duplicate Work Guard must pass on the exact final PR head before merge.
