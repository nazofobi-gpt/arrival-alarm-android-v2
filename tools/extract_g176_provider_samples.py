#!/usr/bin/env python3
"""Extract provider-derived regional GTFS-RT samples and matching static GTFS rows.

No third-party protobuf binding is required. The script reads only the GTFS-RT
wire fields needed for deterministic sample selection, then retains the exact
FeedHeader / FeedEntity protobuf bytes as base64 plus matching static GTFS rows.
"""

import argparse
import base64
import csv
import hashlib
import io
import json
import math
import os
import sys
import zipfile
from datetime import datetime, timezone


REGIONS = {
    "bremen_vbn_area": {"lat": 53.0831, "lon": 8.8131, "radius_km": 55.0},
    "berlin": {"lat": 52.5251, "lon": 13.3694, "radius_km": 45.0},
}


def read_varint(buf, pos):
    value = 0
    shift = 0
    n = len(buf)
    while pos < n and shift < 70:
        byte = buf[pos]
        pos += 1
        value |= (byte & 0x7F) << shift
        if byte < 0x80:
            return value, pos
        shift += 7
    raise ValueError("invalid protobuf varint")


def iter_fields(buf):
    mv = memoryview(buf)
    pos = 0
    n = len(mv)
    while pos < n:
        key, pos = read_varint(mv, pos)
        field = key >> 3
        wire = key & 7
        if field == 0:
            raise ValueError("invalid protobuf field 0")
        if wire == 0:
            value, pos = read_varint(mv, pos)
            yield field, wire, value
        elif wire == 1:
            end = pos + 8
            if end > n:
                raise ValueError("truncated fixed64")
            yield field, wire, mv[pos:end]
            pos = end
        elif wire == 2:
            size, pos = read_varint(mv, pos)
            end = pos + size
            if end > n:
                raise ValueError("truncated length-delimited field")
            yield field, wire, mv[pos:end]
            pos = end
        elif wire == 5:
            end = pos + 4
            if end > n:
                raise ValueError("truncated fixed32")
            yield field, wire, mv[pos:end]
            pos = end
        else:
            raise ValueError(f"unsupported protobuf wire type {wire}")


def text(value):
    return bytes(value).decode("utf-8", errors="replace")


def signed_int32(value):
    value &= (1 << 64) - 1
    if value >= (1 << 63):
        value -= 1 << 64
    if value > 0x7FFFFFFF:
        value -= 1 << 32
    return value


def parse_header(raw):
    out = {"gtfs_realtime_version": None, "incrementality": 0, "timestamp": None, "feed_version": None}
    for field, wire, value in iter_fields(raw):
        if wire == 2 and field == 1:
            out["gtfs_realtime_version"] = text(value)
        elif wire == 0 and field == 2:
            out["incrementality"] = int(value)
        elif wire == 0 and field == 3:
            out["timestamp"] = int(value)
        elif wire == 2 and field == 4:
            out["feed_version"] = text(value)
    return out


def parse_trip_descriptor(raw):
    out = {
        "trip_id": None,
        "start_time": None,
        "start_date": None,
        "schedule_relationship": None,
        "route_id": None,
        "direction_id": None,
    }
    for field, wire, value in iter_fields(raw):
        if wire == 2 and field == 1:
            out["trip_id"] = text(value)
        elif wire == 2 and field == 2:
            out["start_time"] = text(value)
        elif wire == 2 and field == 3:
            out["start_date"] = text(value)
        elif wire == 0 and field == 4:
            out["schedule_relationship"] = int(value)
        elif wire == 2 and field == 5:
            out["route_id"] = text(value)
        elif wire == 0 and field == 6:
            out["direction_id"] = int(value)
    return out


def parse_stop_event(raw):
    out = {"delay": None, "time": None}
    for field, wire, value in iter_fields(raw):
        if wire == 0 and field == 1:
            out["delay"] = signed_int32(int(value))
        elif wire == 0 and field == 2:
            out["time"] = int(value)
    return out


def parse_stop_properties(raw):
    out = {"assigned_stop_id": None, "stop_headsign": None, "pickup_type": None, "drop_off_type": None}
    for field, wire, value in iter_fields(raw):
        if wire == 2 and field == 1:
            out["assigned_stop_id"] = text(value)
        elif wire == 2 and field == 2:
            out["stop_headsign"] = text(value)
        elif wire == 0 and field == 3:
            out["pickup_type"] = int(value)
        elif wire == 0 and field == 4:
            out["drop_off_type"] = int(value)
    return out


def parse_stop_update(raw):
    out = {
        "stop_sequence": None,
        "stop_id": None,
        "schedule_relationship": None,
        "arrival": None,
        "departure": None,
        "properties": None,
    }
    for field, wire, value in iter_fields(raw):
        if wire == 0 and field == 1:
            out["stop_sequence"] = int(value)
        elif wire == 2 and field == 2:
            out["arrival"] = parse_stop_event(value)
        elif wire == 2 and field == 3:
            out["departure"] = parse_stop_event(value)
        elif wire == 2 and field == 4:
            out["stop_id"] = text(value)
        elif wire == 0 and field == 5:
            out["schedule_relationship"] = int(value)
        elif wire == 2 and field == 6:
            out["properties"] = parse_stop_properties(value)
    return out


def parse_trip_update(raw, full=False):
    trip = None
    timestamp = None
    stops = []
    stop_count = 0
    delay_count = 0
    assigned_count = 0
    protocol_valid_assigned_count = 0
    protocol_valid_assigned_sequences = []
    for field, wire, value in iter_fields(raw):
        if wire == 2 and field == 1:
            trip = parse_trip_descriptor(value)
        elif wire == 2 and field == 2:
            stop_count += 1
            stop = parse_stop_update(value)
            if (stop.get("arrival") or {}).get("delay") is not None or (stop.get("departure") or {}).get("delay") is not None:
                delay_count += 1
            assigned_stop_id = (stop.get("properties") or {}).get("assigned_stop_id")
            if assigned_stop_id:
                assigned_count += 1
                stop_sequence = stop.get("stop_sequence")
                stop_id = stop.get("stop_id")
                if stop_sequence is not None and (not stop_id or stop_id == assigned_stop_id):
                    protocol_valid_assigned_count += 1
                    protocol_valid_assigned_sequences.append(stop_sequence)
            if full:
                stops.append(stop)
        elif wire == 0 and field == 4:
            timestamp = int(value)
    if not trip:
        return None
    return {
        "trip": trip,
        "timestamp": timestamp,
        "stops": stops if full else None,
        "stop_count": stop_count,
        "delay_count": delay_count,
        "assigned_count": assigned_count,
        "protocol_valid_assigned_count": protocol_valid_assigned_count,
        "protocol_valid_assigned_sequences": protocol_valid_assigned_sequences,
    }


def parse_entity_summary(raw):
    entity_id = None
    update = None
    has_alert = False
    for field, wire, value in iter_fields(raw):
        if wire == 2 and field == 1:
            entity_id = text(value)
        elif wire == 2 and field == 3:
            update = parse_trip_update(value, full=False)
        elif wire == 2 and field == 5:
            has_alert = True
    if not update:
        return None
    trip = update["trip"]
    if not trip.get("trip_id"):
        return None
    return {
        "entity_id": entity_id,
        "trip_id": trip["trip_id"],
        "route_id": trip.get("route_id"),
        "start_date": trip.get("start_date"),
        "schedule_relationship": trip.get("schedule_relationship"),
        "direction_id": trip.get("direction_id"),
        "timestamp": update.get("timestamp"),
        "stop_count": update["stop_count"],
        "delay_count": update["delay_count"],
        "assigned_count": update["assigned_count"],
        "protocol_valid_assigned_count": update["protocol_valid_assigned_count"],
        "protocol_valid_assigned_sequences": update["protocol_valid_assigned_sequences"],
        "has_alert": has_alert,
    }


def parse_entity_full(raw):
    entity_id = None
    update = None
    for field, wire, value in iter_fields(raw):
        if wire == 2 and field == 1:
            entity_id = text(value)
        elif wire == 2 and field == 3:
            update = parse_trip_update(value, full=True)
    return {"entity_id": entity_id, "trip_update": update}


def parse_selector(raw):
    out = {"agency_id": None, "route_id": None, "route_type": None, "trip": None, "stop_id": None, "direction_id": None}
    for field, wire, value in iter_fields(raw):
        if wire == 2 and field == 1:
            out["agency_id"] = text(value)
        elif wire == 2 and field == 2:
            out["route_id"] = text(value)
        elif wire == 0 and field == 3:
            out["route_type"] = int(value)
        elif wire == 2 and field == 4:
            out["trip"] = parse_trip_descriptor(value)
        elif wire == 2 and field == 5:
            out["stop_id"] = text(value)
        elif wire == 0 and field == 6:
            out["direction_id"] = int(value)
    return out


def parse_alert(raw):
    selectors = []
    cause = None
    effect = None
    severity = None
    for field, wire, value in iter_fields(raw):
        if wire == 2 and field == 5:
            selectors.append(parse_selector(value))
        elif wire == 0 and field == 6:
            cause = int(value)
        elif wire == 0 and field == 7:
            effect = int(value)
        elif wire == 0 and field == 14:
            severity = int(value)
    return {"selectors": selectors, "cause": cause, "effect": effect, "severity_level": severity}


def iter_feed_entities(payload):
    header_raw = None
    for field, wire, value in iter_fields(payload):
        if wire != 2:
            continue
        if field == 1:
            header_raw = bytes(value)
            yield "header", header_raw
        elif field == 2:
            yield "entity", bytes(value)


def file_sha256(path):
    digest = hashlib.sha256()
    with open(path, "rb") as handle:
        for chunk in iter(lambda: handle.read(1024 * 1024), b""):
            digest.update(chunk)
    return digest.hexdigest()


def csv_rows(zf, name):
    with zf.open(name) as raw:
        wrapper = io.TextIOWrapper(raw, encoding="utf-8-sig", newline="")
        yield from csv.DictReader(wrapper)


def haversine_km(lat1, lon1, lat2, lon2):
    radius = 6371.0088
    a1 = math.radians(lat1)
    a2 = math.radians(lat2)
    dlat = a2 - a1
    dlon = math.radians(lon2 - lon1)
    h = math.sin(dlat / 2) ** 2 + math.cos(a1) * math.cos(a2) * math.sin(dlon / 2) ** 2
    return 2 * radius * math.asin(math.sqrt(h))


def choose_sample(candidates, summaries):
    ranked = []
    for trip_id in candidates:
        summary = summaries.get(trip_id)
        if not summary:
            continue
        score = (
            min(summary["protocol_valid_assigned_count"], 1) * 30
            + min(summary["delay_count"], 1) * 20
            + (10 if summary.get("schedule_relationship") not in (None, 0) else 0)
            + min(summary["stop_count"], 20)
        )
        static_matchable = summary.get("static_matchable_assigned_count", 0)
        ranked.append((
            min(static_matchable, 1),
            static_matchable,
            score,
            summary["stop_count"],
            trip_id,
        ))
    if not ranked:
        return None
    ranked.sort(reverse=True)
    return ranked[0][4]


def choose_platform_evidence(summaries):
    ranked = []
    for trip_id, summary in summaries.items():
        static_matchable = summary.get("static_matchable_assigned_count", 0)
        if static_matchable <= 0:
            continue
        ranked.append((
            static_matchable,
            summary["protocol_valid_assigned_count"],
            min(summary["delay_count"], 1),
            summary["stop_count"],
            trip_id,
        ))
    if not ranked:
        return None
    ranked.sort(reverse=True)
    return ranked[0][4]


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--realtime", required=True)
    parser.add_argument("--static", required=True)
    parser.add_argument("--output", required=True)
    parser.add_argument("--endpoint", default="https://realtime.gtfs.de/realtime-free.pb")
    args = parser.parse_args()

    with open(args.realtime, "rb") as handle:
        payload = handle.read()

    header_raw = None
    summaries = {}
    entity_count = 0
    trip_entity_count = 0
    for kind, raw in iter_feed_entities(payload):
        if kind == "header":
            header_raw = raw
            continue
        entity_count += 1
        summary = parse_entity_summary(raw)
        if summary:
            trip_entity_count += 1
            summaries[summary["trip_id"]] = summary

    if not header_raw:
        raise SystemExit("GTFS-RT FeedHeader missing")
    header = parse_header(header_raw)
    live_trip_ids = set(summaries)

    candidate_ids = {name: set() for name in REGIONS}
    candidate_match_stop = {name: {} for name in REGIONS}
    region_stop_rows = {name: {} for name in REGIONS}
    assigned_sequences_by_trip = {
        trip_id: set(summary["protocol_valid_assigned_sequences"])
        for trip_id, summary in summaries.items()
        if summary["protocol_valid_assigned_sequences"]
    }
    static_assigned_sequence_counts = {
        trip_id: {} for trip_id in assigned_sequences_by_trip
    }

    with zipfile.ZipFile(args.static) as zf:
        names = set(zf.namelist())
        required = {"stops.txt", "stop_times.txt", "trips.txt", "routes.txt"}
        missing = required - names
        if missing:
            raise SystemExit(f"static GTFS missing {sorted(missing)}")

        region_stop_ids = {name: set() for name in REGIONS}
        for row in csv_rows(zf, "stops.txt"):
            try:
                lat = float(row.get("stop_lat") or "")
                lon = float(row.get("stop_lon") or "")
            except ValueError:
                continue
            for region_name, region in REGIONS.items():
                distance = haversine_km(lat, lon, region["lat"], region["lon"])
                if distance <= region["radius_km"]:
                    sid = row.get("stop_id") or ""
                    if sid:
                        region_stop_ids[region_name].add(sid)
                        copy = dict(row)
                        copy["_anchor_distance_km"] = round(distance, 3)
                        region_stop_rows[region_name][sid] = copy

        for row in csv_rows(zf, "stop_times.txt"):
            trip_id = row.get("trip_id") or ""
            if trip_id not in live_trip_ids:
                continue
            assigned_sequences = assigned_sequences_by_trip.get(trip_id)
            if assigned_sequences:
                try:
                    stop_sequence = int(row.get("stop_sequence") or "")
                except ValueError:
                    stop_sequence = None
                if stop_sequence in assigned_sequences:
                    counts = static_assigned_sequence_counts[trip_id]
                    counts[stop_sequence] = counts.get(stop_sequence, 0) + 1
            stop_id = row.get("stop_id") or ""
            for region_name in REGIONS:
                if stop_id in region_stop_ids[region_name]:
                    candidate_ids[region_name].add(trip_id)
                    candidate_match_stop[region_name].setdefault(trip_id, stop_id)

        for trip_id, summary in summaries.items():
            sequence_counts = static_assigned_sequence_counts.get(trip_id, {})
            summary["static_matchable_assigned_count"] = sum(
                1
                for sequence in summary["protocol_valid_assigned_sequences"]
                if sequence_counts.get(sequence) == 1
            )

        chosen = {name: choose_sample(candidate_ids[name], summaries) for name in REGIONS}
        platform_trip_id = choose_platform_evidence(summaries)
        missing_regions = [name for name, trip_id in chosen.items() if not trip_id]
        if missing_regions:
            raise SystemExit(
                "no provider-derived static/live candidate for "
                + ", ".join(missing_regions)
                + "; candidate counts="
                + json.dumps({k: len(v) for k, v in candidate_ids.items()})
            )

        chosen_ids = set(chosen.values())
        if platform_trip_id:
            chosen_ids.add(platform_trip_id)
        trip_rows = {}
        route_ids = set()
        for row in csv_rows(zf, "trips.txt"):
            trip_id = row.get("trip_id") or ""
            if trip_id in chosen_ids:
                trip_rows[trip_id] = dict(row)
                if row.get("route_id"):
                    route_ids.add(row["route_id"])

        if set(trip_rows) != chosen_ids:
            raise SystemExit(f"chosen trips missing from static trips.txt: {chosen_ids - set(trip_rows)}")

        route_rows = {}
        agency_ids = set()
        for row in csv_rows(zf, "routes.txt"):
            route_id = row.get("route_id") or ""
            if route_id in route_ids:
                route_rows[route_id] = dict(row)
                if row.get("agency_id"):
                    agency_ids.add(row["agency_id"])

        agency_rows = {}
        if "agency.txt" in names:
            for row in csv_rows(zf, "agency.txt"):
                agency_id = row.get("agency_id") or ""
                if agency_id in agency_ids:
                    agency_rows[agency_id] = dict(row)

        selected_stop_times = {trip_id: [] for trip_id in chosen_ids}
        selected_stop_ids = set()
        for row in csv_rows(zf, "stop_times.txt"):
            trip_id = row.get("trip_id") or ""
            if trip_id in selected_stop_times:
                copy = dict(row)
                selected_stop_times[trip_id].append(copy)
                stop_id = row.get("stop_id") or ""
                if stop_id:
                    selected_stop_ids.add(stop_id)

        selected_stop_rows = {}
        for row in csv_rows(zf, "stops.txt"):
            stop_id = row.get("stop_id") or ""
            if stop_id in selected_stop_ids:
                selected_stop_rows[stop_id] = dict(row)

        feed_info = None
        if "feed_info.txt" in names:
            feed_info = next(csv_rows(zf, "feed_info.txt"), None)

    selected_raw = {}
    selected_full = {}
    selected_ids = set(chosen_ids)
    for kind, raw in iter_feed_entities(payload):
        if kind != "entity":
            continue
        summary = parse_entity_summary(raw)
        if summary and summary["trip_id"] in selected_ids and summary["trip_id"] not in selected_raw:
            selected_raw[summary["trip_id"]] = raw
            selected_full[summary["trip_id"]] = parse_entity_full(raw)
            if len(selected_raw) == len(selected_ids):
                break

    if set(selected_raw) != selected_ids:
        raise SystemExit(f"selected GTFS-RT entities missing on second pass: {selected_ids - set(selected_raw)}")

    samples = {}
    for region_name, trip_id in chosen.items():
        trip_row = trip_rows[trip_id]
        route_id = trip_row.get("route_id") or ""
        match_stop = candidate_match_stop[region_name][trip_id]
        sample = {
            "region": region_name,
            "regionAnchor": REGIONS[region_name],
            "matchingRegionalStop": region_stop_rows[region_name].get(match_stop),
            "realtimeSummary": summaries[trip_id],
            "realtimeEntity": selected_full[trip_id],
            "entityPbBase64": base64.b64encode(selected_raw[trip_id]).decode("ascii"),
            "staticTrip": trip_row,
            "staticRoute": route_rows.get(route_id),
            "staticAgency": agency_rows.get((route_rows.get(route_id) or {}).get("agency_id") or ""),
            "staticStopTimes": selected_stop_times[trip_id],
            "staticStops": {
                stop_id: selected_stop_rows.get(stop_id)
                for stop_id in {row.get("stop_id") for row in selected_stop_times[trip_id]}
                if stop_id
            },
        }
        samples[region_name] = sample

    platform_evidence = None
    if platform_trip_id:
        trip_row = trip_rows[platform_trip_id]
        route_id = trip_row.get("route_id") or ""
        platform_evidence = {
            "scope": "germany_wide",
            "realtimeSummary": summaries[platform_trip_id],
            "realtimeEntity": selected_full[platform_trip_id],
            "entityPbBase64": base64.b64encode(selected_raw[platform_trip_id]).decode("ascii"),
            "staticTrip": trip_row,
            "staticRoute": route_rows.get(route_id),
            "staticAgency": agency_rows.get((route_rows.get(route_id) or {}).get("agency_id") or ""),
            "staticStopTimes": selected_stop_times[platform_trip_id],
            "staticStops": {
                stop_id: selected_stop_rows.get(stop_id)
                for stop_id in {row.get("stop_id") for row in selected_stop_times[platform_trip_id]}
                if stop_id
            },
        }

    chosen_routes = {sample["staticTrip"].get("route_id") for sample in samples.values()}
    chosen_stops = {
        row.get("stop_id")
        for sample in samples.values()
        for row in sample["staticStopTimes"]
        if row.get("stop_id")
    }
    chosen_trip_ids = set(chosen.values())

    associated_alerts = []
    for kind, raw in iter_feed_entities(payload):
        if kind != "entity":
            continue
        entity_id = None
        alert_raw = None
        for field, wire, value in iter_fields(raw):
            if wire == 2 and field == 1:
                entity_id = text(value)
            elif wire == 2 and field == 5:
                alert_raw = bytes(value)
        if not alert_raw:
            continue
        alert = parse_alert(alert_raw)
        matched = False
        for selector in alert["selectors"]:
            trip = selector.get("trip") or {}
            if (
                selector.get("route_id") in chosen_routes
                or selector.get("stop_id") in chosen_stops
                or trip.get("trip_id") in chosen_trip_ids
            ):
                matched = True
                break
        if matched:
            associated_alerts.append(
                {
                    "entityId": entity_id,
                    "alert": alert,
                    "entityPbBase64": base64.b64encode(raw).decode("ascii"),
                }
            )
            if len(associated_alerts) >= 8:
                break

    platform_evidence_state = (
        "PROVEN" if platform_evidence is not None else "PROVIDER_SAMPLE_UNAVAILABLE"
    )
    platform_evidence_counts = {
        "platformEvidenceState": platform_evidence_state,
        "globalProtocolValidAssignedTripCount": sum(
            1 for summary in summaries.values()
            if summary["protocol_valid_assigned_count"] > 0
        ),
        "globalStaticMatchableAssignedTripCount": sum(
            1 for summary in summaries.values()
            if summary["static_matchable_assigned_count"] > 0
        ),
    }

    output = {
        "schema": "G176_PROVIDER_DERIVED_REGIONAL_SAMPLES_V1",
        "capturedAtUtc": datetime.now(timezone.utc).isoformat(),
        "realtime": {
            "endpoint": args.endpoint,
            "sha256": file_sha256(args.realtime),
            "bytes": os.path.getsize(args.realtime),
            "header": header,
            "headerPbBase64": base64.b64encode(header_raw).decode("ascii"),
            "entityCount": entity_count,
            "tripEntityCount": trip_entity_count,
        },
        "static": {
            "source": "https://download.gtfs.de/germany/free/latest.zip",
            "sha256": file_sha256(args.static),
            "bytes": os.path.getsize(args.static),
            "feedInfo": feed_info,
        },
        "candidateCounts": {name: len(ids) for name, ids in candidate_ids.items()},
        "samples": samples,
        "platformEvidence": platform_evidence,
        "platformEvidenceState": platform_evidence_state,
        "platformEvidenceCounts": platform_evidence_counts,
        "associatedAlerts": associated_alerts,
        "notes": [
            "Samples are selected from the exact provider GTFS-RT payload by matching live trip_id to the matching Germany static GTFS.",
            "Regional identity is evidenced by a static stop within the recorded geographic anchor radius.",
            "No provider or agency completeness is inferred from this sample.",
        ],
    }

    with open(args.output, "w", encoding="utf-8") as handle:
        json.dump(output, handle, ensure_ascii=False, indent=2, sort_keys=True)
        handle.write("\n")

    print(json.dumps({
        "output": args.output,
        "realtimeSha256": output["realtime"]["sha256"],
        "staticSha256": output["static"]["sha256"],
        "samples": {
            name: {
                "tripId": sample["staticTrip"].get("trip_id"),
                "routeId": sample["staticTrip"].get("route_id"),
                "matchStop": (sample["matchingRegionalStop"] or {}).get("stop_id"),
                "delayCount": sample["realtimeSummary"]["delay_count"],
                "assignedCount": sample["realtimeSummary"]["assigned_count"],
                "protocolValidAssignedCount": sample["realtimeSummary"]["protocol_valid_assigned_count"],
                "staticMatchableAssignedCount": sample["realtimeSummary"]["static_matchable_assigned_count"],
                "relationship": sample["realtimeSummary"]["schedule_relationship"],
            }
            for name, sample in samples.items()
        },
        "platformEvidence": None if platform_evidence is None else {
            "tripId": platform_evidence["staticTrip"].get("trip_id"),
            "routeId": platform_evidence["staticTrip"].get("route_id"),
            "assignedCount": platform_evidence["realtimeSummary"]["assigned_count"],
            "protocolValidAssignedCount": platform_evidence["realtimeSummary"]["protocol_valid_assigned_count"],
            "staticMatchableAssignedCount": platform_evidence["realtimeSummary"]["static_matchable_assigned_count"],
        },
        "globalProtocolValidAssignedTripCount": sum(
            1 for summary in summaries.values()
            if summary["protocol_valid_assigned_count"] > 0
        ),
        "globalStaticMatchableAssignedTripCount": sum(
            1 for summary in summaries.values()
            if summary["static_matchable_assigned_count"] > 0
        ),
        "associatedAlerts": len(associated_alerts),
    }, ensure_ascii=False))


if __name__ == "__main__":
    main()
