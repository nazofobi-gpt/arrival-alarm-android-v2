# G-183 — Home favorite stops (v1.0)
Source: H-003 L0 IDEA baseline H003-IDEA-REV-004; existing approved Varış Material3 home visual reference H003-UI-VISUAL-REFERENCE-001 v1.0.
Scope: show bookmarked GTFS stops on Home without requiring a new GPS fix, and open the existing station departures surface.
Visual: one Material3 Card after the existing Home overview, before the saved-commutes Card. 16dp inner padding, 8dp vertical spacing, titleMedium heading, 48dp minimum full-width departure actions. No map, new modal, or inferred current location.
TR / DE / EN: title, empty-search call to action, unavailable-data message, stop-specific departure action; user-visible strings reside in locale resource XML.
States: zero saved IDs → search CTA; saved IDs + current local GTFS matches → up to eight stop-specific departure buttons; saved IDs without matches → explicit unavailable text and no fabricated stop; active/armed journey is not modified.
Data: reuse existing device-local favorite_stop_ids SharedPreferences; resolve stop IDs by parameterized SQLite current-GTFS lookup, never cached coordinates. Deleted or obsolete stop IDs cannot become phantom departures.
A11y/tests: semantics heading, 48dp action, stable tags, Compose positive/negative/empty tests, SQLite missing/stale/zero-limit test; Android CI / API36 Emulator required; field test G-136 remains open.
Collision: MainActivity is also touched by PR#721/#723, but this change only adds the adjacent Favorites Home component and its independent state/effect; merge separately only after fresh overlap review.
