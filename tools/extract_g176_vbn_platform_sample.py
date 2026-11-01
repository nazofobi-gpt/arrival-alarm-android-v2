#!/usr/bin/env python3
"""Extract strict VBN GTFS-RT assigned-stop evidence against the app's static GTFS."""
from __future__ import annotations

import argparse
import base64
import collections
import datetime as dt
import json
import os
import zipfile

import extract_g176_provider_samples as g176

VBN_ENDPOINT = "http://gtfsr.vbn.de/gtfsr_connect.bin"
VBN_PROVIDER = "VBN (Verkehrsverbund Bremen/Niedersachsen)"
VBN_LICENSE = "CC BY-SA 4.0"
GERMANY_STATIC_URL = "https://download.gtfs.de/germany/free/latest.zip"


def int_or_none(value):
    try:
        return int(value)
    except (TypeError, ValueError):
        return None


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--realtime", required=True)
    parser.add_argument("--static", required=True)
    parser.add_argument("--output", required=True)
    parser.add_argument("--endpoint", default=VBN_ENDPOINT)
    parser.add_argument("--captured-at-epoch", type=int, default=None)
    args = parser.parse_args()

    with open(args.realtime, "rb") as handle:
        payload = handle.read()
    if not payload:
        raise SystemExit("VBN realtime payload is empty")

    header_raw = None
    candidates = []
    entity_count = 0
    trip_entity_count = 0
    protocol_valid_trip_count = 0
    protocol_valid_stop_update_count = 0

    for kind, raw in g176.iter_feed_entities(payload):
        if kind == "header":
            header_raw = raw
            continue
        entity_count += 1
        summary = g176.parse_entity_summary(raw)
        if not summary:
            continue
        trip_entity_count += 1
        full = g176.parse_entity_full(raw)
        trip_update = (full or {}).get("trip_update") or {}
        stops = trip_update.get("stops") or []
        assigned_stops = [
            stop for stop in stops
            if ((stop.get("properties") or {}).get("assigned_stop_id") or "").strip()
        ]
        if assigned_stops:
            candidates.append({
                "raw": raw,
                "summary": summary,
                "full": full,
                "assigned_stops": assigned_stops,
            })
        valid_count = int(summary.get("protocol_valid_assigned_count") or 0)
        if valid_count > 0:
            protocol_valid_trip_count += 1
            protocol_valid_stop_update_count += valid_count

    if header_raw is None:
        raise SystemExit("VBN feed has no FeedHeader")

    header = g176.parse_header(header_raw)
    feed_timestamp = int(header.get("timestamp") or 0)
    if feed_timestamp <= 0:
        raise SystemExit("VBN FeedHeader.timestamp is missing or invalid")

    captured_at = args.captured_at_epoch or int(dt.datetime.now(dt.timezone.utc).timestamp())
    age_seconds = captured_at - feed_timestamp
    if age_seconds < -30 or age_seconds > 120:
        raise SystemExit(
            f"VBN feed is not current at capture: age_seconds={age_seconds}, allowed range is -30..120"
        )

    candidate_trip_ids = {
        str(candidate["summary"].get("trip_id") or "")
        for candidate in candidates
        if candidate["summary"].get("trip_id")
    }
    if not candidate_trip_ids:
        raise SystemExit(
            "VBN live feed contains no TripUpdate with assigned_stop_id; "
            f"protocol_valid_trip_count={protocol_valid_trip_count}"
        )

    with zipfile.ZipFile(args.static) as zf:
        trip_rows = {}
        for row in g176.csv_rows(zf, "trips.txt"):
            trip_id = row.get("trip_id", "")
            if trip_id in candidate_trip_ids:
                trip_rows[trip_id] = dict(row)

        matched_trip_ids = set(trip_rows)
        stop_times_by_trip = collections.defaultdict(list)
        for row in g176.csv_rows(zf, "stop_times.txt"):
            trip_id = row.get("trip_id", "")
            if trip_id in matched_trip_ids:
                stop_times_by_trip[trip_id].append(dict(row))

        required_stop_ids = set()
        for candidate in candidates:
            for stop in candidate["assigned_stops"]:
                assigned_id = ((stop.get("properties") or {}).get("assigned_stop_id") or "").strip()
                if assigned_id:
                    required_stop_ids.add(assigned_id)
        for rows in stop_times_by_trip.values():
            for row in rows:
                stop_id = (row.get("stop_id") or "").strip()
                if stop_id:
                    required_stop_ids.add(stop_id)

        static_stop_rows = {}
        for row in g176.csv_rows(zf, "stops.txt"):
            stop_id = row.get("stop_id", "")
            if stop_id in required_stop_ids:
                static_stop_rows[stop_id] = dict(row)

        route_ids = {row.get("route_id", "") for row in trip_rows.values() if row.get("route_id")}
        static_route_rows = {}
        for row in g176.csv_rows(zf, "routes.txt"):
            route_id = row.get("route_id", "")
            if route_id in route_ids:
                static_route_rows[route_id] = dict(row)

        agency_ids = {
            row.get("agency_id", "")
            for row in static_route_rows.values()
            if row.get("agency_id")
        }
        static_agency_rows = {}
        try:
            for row in g176.csv_rows(zf, "agency.txt"):
                agency_id = row.get("agency_id", "")
                if agency_id in agency_ids:
                    static_agency_rows[agency_id] = dict(row)
        except (KeyError, RuntimeError, ValueError):
            pass

        ranked = []
        static_matchable_trip_count = 0
        static_matchable_stop_update_count = 0
        for candidate in candidates:
            summary = dict(candidate["summary"])
            trip_id = str(summary.get("trip_id") or "")
            trip_row = trip_rows.get(trip_id)
            if not trip_row:
                continue
            static_route_id = trip_row.get("route_id", "")
            live_route_id = str(summary.get("route_id") or "")
            if live_route_id and static_route_id and live_route_id != static_route_id:
                continue

            static_stop_times = stop_times_by_trip.get(trip_id, [])
            sequence_counts = collections.Counter(
                int_or_none(row.get("stop_sequence"))
                for row in static_stop_times
                if int_or_none(row.get("stop_sequence")) is not None
            )
            rows_by_sequence = collections.defaultdict(list)
            for row in static_stop_times:
                sequence = int_or_none(row.get("stop_sequence"))
                if sequence is not None:
                    rows_by_sequence[sequence].append(row)

            valid_matches = []
            for stop in candidate["assigned_stops"]:
                properties = stop.get("properties") or {}
                assigned_id = (properties.get("assigned_stop_id") or "").strip()
                sequence = int_or_none(stop.get("stop_sequence"))
                realtime_stop_id = (stop.get("stop_id") or "").strip()
                if not assigned_id or sequence is None:
                    continue
                if realtime_stop_id and realtime_stop_id != assigned_id:
                    continue
                if sequence_counts.get(sequence, 0) != 1:
                    continue
                static_rows = rows_by_sequence.get(sequence, [])
                if len(static_rows) != 1:
                    continue
                scheduled_row = static_rows[0]
                scheduled_stop_id = (scheduled_row.get("stop_id") or "").strip()
                assigned_stop_row = static_stop_rows.get(assigned_id)
                scheduled_stop_row = static_stop_rows.get(scheduled_stop_id)
                if not assigned_stop_row or not scheduled_stop_row:
                    continue
                valid_matches.append({
                    "stop": stop,
                    "sequence": sequence,
                    "assigned_stop_id": assigned_id,
                    "realtime_stop_id": realtime_stop_id,
                    "scheduled_stop_id": scheduled_stop_id,
                    "scheduled_row": scheduled_row,
                    "assigned_stop_row": assigned_stop_row,
                    "scheduled_stop_row": scheduled_stop_row,
                })

            if valid_matches:
                static_matchable_trip_count += 1
                static_matchable_stop_update_count += len(valid_matches)
                for match in valid_matches:
                    assigned_row = match["assigned_stop_row"]
                    score = (
                        int(match["assigned_stop_id"] != match["scheduled_stop_id"]),
                        int(bool(assigned_row.get("platform_code"))),
                        int(bool(assigned_row.get("stop_name"))),
                        int(summary.get("delay_count") or 0),
                        -match["sequence"],
                    )
                    ranked.append((score, candidate, trip_row, static_stop_times, match, valid_matches))

        if not ranked:
            diagnostic = {
                "schema": "G176_VBN_PLATFORM_EVIDENCE_V1",
                "endpoint": args.endpoint,
                "source": VBN_PROVIDER,
                "license": VBN_LICENSE,
                "feedTimestampEpochSeconds": feed_timestamp,
                "capturedAtEpochSeconds": captured_at,
                "ageSecondsAtCapture": age_seconds,
                "entityCount": entity_count,
                "tripEntityCount": trip_entity_count,
                "candidateTripUpdateCount": len(candidates),
                "protocolValidAssignedTripCount": protocol_valid_trip_count,
                "protocolValidAssignedStopUpdateCount": protocol_valid_stop_update_count,
                "staticTripMatchCount": len(matched_trip_ids),
                "staticMatchableAssignedTripCount": static_matchable_trip_count,
                "staticMatchableAssignedStopUpdateCount": static_matchable_stop_update_count,
                "note": "No assigned stop passed exact trip/route/sequence and static assigned-stop identity checks.",
            }
            print(json.dumps(diagnostic, indent=2))
            raise SystemExit("VBN assigned_stop_id evidence has no unique static GTFS match")

        ranked.sort(key=lambda item: item[0], reverse=True)
        _, chosen, trip_row, static_stop_times, chosen_match, valid_matches = ranked[0]
        trip_id = str(chosen["summary"].get("trip_id") or "")
        route_id = trip_row.get("route_id", "")
        route_row = static_route_rows.get(route_id, {})
        agency_row = static_agency_rows.get(route_row.get("agency_id", ""), {})

        selected_stop_ids = {
            (row.get("stop_id") or "").strip()
            for row in static_stop_times
            if (row.get("stop_id") or "").strip()
        }
        selected_stop_ids.add(chosen_match["assigned_stop_id"])
        selected_stops = {
            stop_id: static_stop_rows[stop_id]
            for stop_id in sorted(selected_stop_ids)
            if stop_id in static_stop_rows
        }
        selected_stop_times = sorted(
            [dict(row) for row in static_stop_times],
            key=lambda row: int_or_none(row.get("stop_sequence")) or 0,
        )

        summary = dict(chosen["summary"])
        summary["static_matchable_assigned_count"] = len(valid_matches)
        summary["selected_stop_sequence"] = chosen_match["sequence"]
        summary["selected_assigned_stop_id"] = chosen_match["assigned_stop_id"]
        summary["scheduled_stop_id"] = chosen_match["scheduled_stop_id"]
        summary["assigned_stop_id_exists_in_static"] = True
        summary["is_platform_change"] = (
            chosen_match["assigned_stop_id"] != chosen_match["scheduled_stop_id"]
        )
        summary["assigned_platform_code"] = chosen_match["assigned_stop_row"].get("platform_code", "")
        summary["scheduled_platform_code"] = chosen_match["scheduled_stop_row"].get("platform_code", "")

        static_match = {
            "trip_id": trip_id,
            "route_id": route_id,
            "stop_sequence": chosen_match["sequence"],
            "scheduled_stop_id": chosen_match["scheduled_stop_id"],
            "assigned_stop_id": chosen_match["assigned_stop_id"],
            "unique_static_stop_time_match_count": 1,
            "assigned_stop_exists_in_static_stops": True,
            "realtime_stop_id_matches_assigned_stop_id": (
                not chosen_match["realtime_stop_id"]
                or chosen_match["realtime_stop_id"] == chosen_match["assigned_stop_id"]
            ),
            "is_platform_change_vs_static_schedule": summary["is_platform_change"],
        }

        platform_evidence = {
            "scope": "vbn_supplemental_platform_evidence",
            "endpoint": args.endpoint,
            "source": VBN_PROVIDER,
            "license": VBN_LICENSE,
            "capturedAtUtc": dt.datetime.fromtimestamp(captured_at, dt.timezone.utc).isoformat(),
            "capturedAtEpochSeconds": captured_at,
            "feedTimestampEpochSeconds": feed_timestamp,
            "ageSecondsAtCapture": age_seconds,
            "header": header,
            "headerPbBase64": base64.b64encode(header_raw).decode("ascii"),
            "entityPbBase64": base64.b64encode(chosen["raw"]).decode("ascii"),
            "realtimeSummary": summary,
            "realtimeEntity": chosen["full"],
            "staticTrip": trip_row,
            "staticRoute": route_row,
            "staticAgency": agency_row,
            "staticStopTimes": selected_stop_times,
            "staticStops": selected_stops,
            "staticMatch": static_match,
        }

    output = {
        "schema": "G176_VBN_PLATFORM_EVIDENCE_V1",
        "capturedAtUtc": dt.datetime.fromtimestamp(captured_at, dt.timezone.utc).isoformat(),
        "counts": {
            "candidateTripUpdateCount": len(candidates),
            "protocolValidAssignedTripCount": protocol_valid_trip_count,
            "protocolValidAssignedStopUpdateCount": protocol_valid_stop_update_count,
            "staticTripMatchCount": len(matched_trip_ids),
            "staticMatchableAssignedTripCount": static_matchable_trip_count,
            "staticMatchableAssignedStopUpdateCount": static_matchable_stop_update_count,
        },
        "realtime": {
            "endpoint": args.endpoint,
            "source": VBN_PROVIDER,
            "license": VBN_LICENSE,
            "sha256": g176.file_sha256(args.realtime),
            "bytes": os.path.getsize(args.realtime),
            "capturedAtEpochSeconds": captured_at,
            "feedTimestampEpochSeconds": feed_timestamp,
            "ageSecondsAtCapture": age_seconds,
            "header": header,
            "headerPbBase64": base64.b64encode(header_raw).decode("ascii"),
            "entityCount": entity_count,
            "tripEntityCount": trip_entity_count,
        },
        "static": {
            "source": GERMANY_STATIC_URL,
            "sha256": g176.file_sha256(args.static),
            "bytes": os.path.getsize(args.static),
            "matchedTripId": platform_evidence["staticMatch"]["trip_id"],
            "matchedRouteId": platform_evidence["staticMatch"]["route_id"],
        },
        "platformEvidence": platform_evidence,
    }
    with open(args.output, "w", encoding="utf-8") as handle:
        json.dump(output, handle, ensure_ascii=False, indent=2)
        handle.write("\n")
    print(json.dumps({
        "schema": output["schema"],
        "counts": output["counts"],
        "selected": {
            "trip_id": platform_evidence["realtimeSummary"].get("trip_id"),
            "route_id": platform_evidence["realtimeSummary"].get("route_id"),
            "stop_sequence": platform_evidence["staticMatch"]["stop_sequence"],
            "scheduled_stop_id": platform_evidence["staticMatch"]["scheduled_stop_id"],
            "assigned_stop_id": platform_evidence["staticMatch"]["assigned_stop_id"],
            "is_platform_change": platform_evidence["staticMatch"]["is_platform_change_vs_static_schedule"],
        },
        "realtime_sha256": output["realtime"]["sha256"],
        "static_sha256": output["static"]["sha256"],
        "age_seconds_at_capture": age_seconds,
    }, indent=2))


if __name__ == "__main__":
    main()
