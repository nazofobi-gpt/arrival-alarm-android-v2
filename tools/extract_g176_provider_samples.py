#!/usr/bin/env python3
"""Extract provider-derived regional GTFS-RT samples and matching static GTFS rows.

No third-party protobuf binding is required. The script reads only the GTFS-RT
wire fields needed for deterministic sample selection, then retains the exact
FeedHeader / FeedEntity protobuf bytes as base64 plus matching static GTFS rows.
"""

import argparse
import base64
import csv
from collections import Counter
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


def resolve_regions(names):
    selected = list(names or REGIONS.keys())
    if not selected:
        raise ValueError("at least one region must be selected")
    if len(set(selected)) != len(selected):
        raise ValueError("duplicate region selection")
    unknown = [name for name in selected if name not in REGIONS]
    if unknown:
        raise ValueError(f"unknown region(s): {unknown}")
    return {name: REGIONS[name] for name in selected}


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
    protocol_valid_assigned_stops = []
    stop_id_reassignment_candidate_count = 0
    stop_id_reassignment_candidates = []
    provider_stop_sequence_counts = {}
    for field, wire, value in iter_fields(raw):
        if wire == 2 and field == 1:
            trip = parse_trip_descriptor(value)
        elif wire == 2 and field == 2:
            stop_count += 1
            stop = parse_stop_update(value)
            stop_sequence = stop.get("stop_sequence")
            if stop_sequence is not None:
                provider_stop_sequence_counts[stop_sequence] = (
                    provider_stop_sequence_counts.get(stop_sequence, 0) + 1
                )
            if (stop.get("arrival") or {}).get("delay") is not None or (stop.get("departure") or {}).get("delay") is not None:
                delay_count += 1
            assigned_stop_id = (stop.get("properties") or {}).get("assigned_stop_id")
            stop_id = stop.get("stop_id")
            if assigned_stop_id:
                assigned_count += 1
                if stop_sequence is not None and (not stop_id or stop_id == assigned_stop_id):
                    protocol_valid_assigned_count += 1
                    protocol_valid_assigned_sequences.append(stop_sequence)
                    protocol_valid_assigned_stops.append({
                        "stop_sequence": stop_sequence,
                        "stop_id": stop_id,
                        "assigned_stop_id": assigned_stop_id,
                    })
            elif stop_sequence is not None and stop_id:
                stop_id_reassignment_candidate_count += 1
                stop_id_reassignment_candidates.append({
                    "stop_sequence": stop_sequence,
                    "stop_id": stop_id,
                })
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
        "protocol_valid_assigned_stops": protocol_valid_assigned_stops,
        "stop_id_reassignment_candidate_count": stop_id_reassignment_candidate_count,
        "stop_id_reassignment_candidates": stop_id_reassignment_candidates,
        "provider_stop_sequence_counts": provider_stop_sequence_counts,
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
        "protocol_valid_assigned_stops": update["protocol_valid_assigned_stops"],
        "stop_id_reassignment_candidate_count": update["stop_id_reassignment_candidate_count"],
        "stop_id_reassignment_candidates": update["stop_id_reassignment_candidates"],
        "provider_stop_sequence_counts": update["provider_stop_sequence_counts"],
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


def select_static_matchable_assigned_stops(
    summary,
    static_sequence_rows,
    static_stop_ids,
    parent_station_by_stop,
):
    """Return only unique, same-station assigned_stop_id reassignments."""
    assignments = summary.get("protocol_valid_assigned_stops", [])
    provider_sequence_counts = summary.get("provider_stop_sequence_counts", {})
    matchable = []
    for assignment in assignments:
        sequence = assignment.get("stop_sequence")
        if provider_sequence_counts.get(sequence, 0) != 1:
            continue
        scheduled_ids = static_sequence_rows.get(sequence, [])
        if len(scheduled_ids) != 1:
            continue
        scheduled_stop_id = scheduled_ids[0]
        assigned_stop_id = assignment.get("assigned_stop_id") or ""
        if not scheduled_stop_id or not assigned_stop_id or assigned_stop_id == scheduled_stop_id:
            continue
        if scheduled_stop_id not in static_stop_ids or assigned_stop_id not in static_stop_ids:
            continue
        scheduled_parent = parent_station_by_stop.get(scheduled_stop_id) or ""
        assigned_parent = parent_station_by_stop.get(assigned_stop_id) or ""
        if not scheduled_parent or scheduled_parent != assigned_parent:
            continue
        matchable.append({
            **assignment,
            "scheduled_stop_id": scheduled_stop_id,
            "parent_station_id": scheduled_parent,
        })
    return matchable


def select_protocol_valid_stop_id_reassignments(summary, static_sequence_rows):
    """Resolve stop_id + stop_sequence to one changed static stop_time row."""
    candidates = summary.get("stop_id_reassignment_candidates", [])
    provider_sequence_counts = summary.get("provider_stop_sequence_counts", {})
    protocol_valid = []
    for candidate in candidates:
        sequence = candidate.get("stop_sequence")
        realtime_stop_id = candidate.get("stop_id") or ""
        if provider_sequence_counts.get(sequence, 0) != 1:
            continue
        scheduled_ids = static_sequence_rows.get(sequence, [])
        if len(scheduled_ids) != 1:
            continue
        scheduled_stop_id = scheduled_ids[0]
        if not scheduled_stop_id or not realtime_stop_id or realtime_stop_id == scheduled_stop_id:
            continue
        protocol_valid.append({
            **candidate,
            "scheduled_stop_id": scheduled_stop_id,
            "realtime_stop_id": realtime_stop_id,
        })
    return protocol_valid


def select_static_matchable_stop_id_reassignments(
    protocol_valid,
    static_stop_ids,
    parent_station_by_stop,
):
    """Require both stop IDs in stops.txt and the same non-empty parent_station."""
    matchable = []
    for reassignment in protocol_valid:
        scheduled_stop_id = reassignment.get("scheduled_stop_id") or ""
        realtime_stop_id = reassignment.get("realtime_stop_id") or ""
        if scheduled_stop_id not in static_stop_ids or realtime_stop_id not in static_stop_ids:
            continue
        scheduled_parent = parent_station_by_stop.get(scheduled_stop_id) or ""
        realtime_parent = parent_station_by_stop.get(realtime_stop_id) or ""
        if not scheduled_parent or scheduled_parent != realtime_parent:
            continue
        matchable.append({
            **reassignment,
            "parent_station_id": scheduled_parent,
        })
    return matchable




def choose_sample(candidates, summaries):
    ranked = []
    for trip_id in candidates:
        summary = summaries.get(trip_id)
        if not summary:
            continue
        protocol_valid_platform = (
            summary.get("protocol_valid_assigned_count", 0)
            + summary.get("protocol_valid_stop_id_reassignment_count", 0)
        )
        static_matchable = summary.get("static_matchable_platform_evidence_count", 0)
        score = (
            min(protocol_valid_platform, 1) * 30
            + min(summary["delay_count"], 1) * 20
            + (10 if summary.get("schedule_relationship") not in (None, 0) else 0)
            + min(summary["stop_count"], 20)
        )
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
        static_matchable = summary.get("static_matchable_platform_evidence_count", 0)
        if static_matchable <= 0:
            continue
        protocol_valid_platform = (
            summary.get("protocol_valid_assigned_count", 0)
            + summary.get("protocol_valid_stop_id_reassignment_count", 0)
        )
        ranked.append((
            static_matchable,
            protocol_valid_platform,
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
    parser.add_argument("--static-source", default="https://download.gtfs.de/germany/free/latest.zip")
    parser.add_argument("--regions", nargs="+", choices=tuple(REGIONS), default=list(REGIONS))
    parser.add_argument("--allow-missing-regions", action="store_true")
    args = parser.parse_args()
    regions = resolve_regions(args.regions)

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

    candidate_ids = {name: set() for name in regions}
    candidate_match_stop = {name: {} for name in regions}
    region_stop_rows = {name: {} for name in regions}
    assigned_sequences_by_trip = {
        trip_id: set(summary["protocol_valid_assigned_sequences"])
        for trip_id, summary in summaries.items()
        if summary["protocol_valid_assigned_sequences"]
    }
    stop_id_sequences_by_trip = {
        trip_id: {
            candidate["stop_sequence"]
            for candidate in summary["stop_id_reassignment_candidates"]
        }
        for trip_id, summary in summaries.items()
        if summary["stop_id_reassignment_candidates"]
    }
    candidate_sequences_by_trip = {
        trip_id: assigned_sequences_by_trip.get(trip_id, set())
        | stop_id_sequences_by_trip.get(trip_id, set())
        for trip_id in live_trip_ids
    }
    static_sequence_rows_by_trip = {}

    with zipfile.ZipFile(args.static) as zf:
        names = set(zf.namelist())
        required = {"stops.txt", "stop_times.txt", "trips.txt", "routes.txt"}
        missing = required - names
        if missing:
            raise SystemExit(f"static GTFS missing {sorted(missing)}")

        region_stop_ids = {name: set() for name in regions}
        static_stop_ids = set()
        parent_station_by_stop = {}
        for row in csv_rows(zf, "stops.txt"):
            static_stop_id = row.get("stop_id") or ""
            if static_stop_id:
                static_stop_ids.add(static_stop_id)
                parent_station_by_stop[static_stop_id] = row.get("parent_station") or ""
            try:
                lat = float(row.get("stop_lat") or "")
                lon = float(row.get("stop_lon") or "")
            except ValueError:
                continue
            for region_name, region in regions.items():
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
            stop_id = row.get("stop_id") or ""
            try:
                stop_sequence = int(row.get("stop_sequence") or "")
            except ValueError:
                stop_sequence = None
            if stop_sequence is not None and stop_sequence in candidate_sequences_by_trip.get(trip_id, set()):
                static_sequence_rows_by_trip.setdefault(trip_id, {}).setdefault(stop_sequence, []).append(stop_id)
            for region_name in regions:
                if stop_id in region_stop_ids[region_name]:
                    candidate_ids[region_name].add(trip_id)
                    candidate_match_stop[region_name].setdefault(trip_id, stop_id)

        for trip_id, summary in summaries.items():
            static_sequence_rows = static_sequence_rows_by_trip.get(trip_id, {})
            protocol_valid_stop_id = select_protocol_valid_stop_id_reassignments(
                summary,
                static_sequence_rows,
            )
            matchable_assigned = select_static_matchable_assigned_stops(
                summary,
                static_sequence_rows,
                static_stop_ids,
                parent_station_by_stop,
            )
            matchable_stop_id = select_static_matchable_stop_id_reassignments(
                protocol_valid_stop_id,
                static_stop_ids,
                parent_station_by_stop,
            )
            summary["protocol_valid_stop_id_reassignments"] = protocol_valid_stop_id
            summary["protocol_valid_stop_id_reassignment_count"] = len(protocol_valid_stop_id)
            summary["static_matchable_assigned_stops"] = matchable_assigned
            summary["static_matchable_assigned_count"] = len(matchable_assigned)
            summary["static_matchable_stop_id_reassignments"] = matchable_stop_id
            summary["static_matchable_stop_id_reassignment_count"] = len(matchable_stop_id)
            summary["protocol_valid_platform_evidence_count"] = (
                summary["protocol_valid_assigned_count"]
                + summary["protocol_valid_stop_id_reassignment_count"]
            )
            summary["static_matchable_platform_evidence_count"] = (
                summary["static_matchable_assigned_count"]
                + summary["static_matchable_stop_id_reassignment_count"]
            )

        chosen = {name: choose_sample(candidate_ids[name], summaries) for name in regions}
        platform_trip_id = choose_platform_evidence(summaries)
        missing_regions = [name for name, trip_id in chosen.items() if not trip_id]
        if missing_regions and not args.allow_missing_regions:
            raise SystemExit(
                "no provider-derived static/live candidate for "
                + ", ".join(missing_regions)
                + "; candidate counts="
                + json.dumps({k: len(v) for k, v in candidate_ids.items()})
            )

        chosen_ids = {trip_id for trip_id in chosen.values() if trip_id}
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
        selected_stop_ids_by_trip = {trip_id: set() for trip_id in chosen_ids}
        for row in csv_rows(zf, "stop_times.txt"):
            trip_id = row.get("trip_id") or ""
            if trip_id in selected_stop_times:
                copy = dict(row)
                selected_stop_times[trip_id].append(copy)
                stop_id = row.get("stop_id") or ""
                if stop_id:
                    selected_stop_ids_by_trip[trip_id].add(stop_id)

        for trip_id in chosen_ids:
            for assignment in summaries[trip_id].get("static_matchable_assigned_stops", []):
                assigned_stop_id = assignment.get("assigned_stop_id") or ""
                if assigned_stop_id:
                    selected_stop_ids_by_trip[trip_id].add(assigned_stop_id)
            for reassignment in summaries[trip_id].get("static_matchable_stop_id_reassignments", []):
                realtime_stop_id = reassignment.get("realtime_stop_id") or ""
                if realtime_stop_id:
                    selected_stop_ids_by_trip[trip_id].add(realtime_stop_id)

        selected_stop_ids = set().union(*selected_stop_ids_by_trip.values()) if selected_stop_ids_by_trip else set()
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
        if not trip_id:
            continue
        trip_row = trip_rows[trip_id]
        route_id = trip_row.get("route_id") or ""
        match_stop = candidate_match_stop[region_name][trip_id]
        sample = {
            "region": region_name,
            "regionAnchor": regions[region_name],
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
                for stop_id in selected_stop_ids_by_trip[trip_id]
            },
        }
        samples[region_name] = sample

    platform_evidence = None
    if platform_trip_id:
        trip_row = trip_rows[platform_trip_id]
        route_id = trip_row.get("route_id") or ""
        platform_evidence = {
            "scope": args.endpoint,
            "realtimeSummary": summaries[platform_trip_id],
            "realtimeEntity": selected_full[platform_trip_id],
            "entityPbBase64": base64.b64encode(selected_raw[platform_trip_id]).decode("ascii"),
            "staticTrip": trip_row,
            "staticRoute": route_rows.get(route_id),
            "staticAgency": agency_rows.get((route_rows.get(route_id) or {}).get("agency_id") or ""),
            "staticStopTimes": selected_stop_times[platform_trip_id],
            "staticStops": {
                stop_id: selected_stop_rows.get(stop_id)
                for stop_id in selected_stop_ids_by_trip[platform_trip_id]
            },
        }

    chosen_routes = {sample["staticTrip"].get("route_id") for sample in samples.values()}
    chosen_stops = {
        stop_id
        for trip_id in chosen.values()
        if trip_id
        for stop_id in selected_stop_ids_by_trip[trip_id]
    }
    chosen_trip_ids = {trip_id for trip_id in chosen.values() if trip_id}

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
        "globalProtocolValidStopIdReassignmentTripCount": sum(
            1 for summary in summaries.values()
            if summary["protocol_valid_stop_id_reassignment_count"] > 0
        ),
        "globalStaticMatchableStopIdReassignmentTripCount": sum(
            1 for summary in summaries.values()
            if summary["static_matchable_stop_id_reassignment_count"] > 0
        ),
        "globalProtocolValidPlatformTripCount": sum(
            1 for summary in summaries.values()
            if summary["protocol_valid_platform_evidence_count"] > 0
        ),
        "globalStaticMatchablePlatformTripCount": sum(
            1 for summary in summaries.values()
            if summary["static_matchable_platform_evidence_count"] > 0
        ),
    }

    output = {
        "schema": "G176_PROVIDER_DERIVED_REGIONAL_SAMPLES_V1",
        "capturedAtUtc": datetime.now(timezone.utc).isoformat(),
        "regions": list(regions),
        "allowMissingRegions": args.allow_missing_regions,
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
            "source": args.static_source,
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
            "Samples are selected from the exact provider GTFS-RT payload by matching live trip_id to the supplied provider static GTFS.",
            "Regional identity is evidenced by a static stop within the recorded geographic anchor radius.",
            "Platform evidence accepts either assigned_stop_id plus stop_sequence or stop_id plus stop_sequence when both static stops share the same non-empty parent_station.",
            "Assigned-stop and stop_id reassignment counters remain separate; duplicate provider/static sequences and cross-station IDs fail closed.",
            "When allowMissingRegions is true, the regional sample map may be empty while global platform-evidence extraction still runs.",
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
                "protocolValidStopIdReassignmentCount": sample["realtimeSummary"]["protocol_valid_stop_id_reassignment_count"],
                "staticMatchableStopIdReassignmentCount": sample["realtimeSummary"]["static_matchable_stop_id_reassignment_count"],
                "staticMatchablePlatformEvidenceCount": sample["realtimeSummary"]["static_matchable_platform_evidence_count"],
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
            "protocolValidStopIdReassignmentCount": platform_evidence["realtimeSummary"]["protocol_valid_stop_id_reassignment_count"],
            "staticMatchableStopIdReassignmentCount": platform_evidence["realtimeSummary"]["static_matchable_stop_id_reassignment_count"],
            "staticMatchablePlatformEvidenceCount": platform_evidence["realtimeSummary"]["static_matchable_platform_evidence_count"],
        },
        "globalProtocolValidAssignedTripCount": sum(
            1 for summary in summaries.values()
            if summary["protocol_valid_assigned_count"] > 0
        ),
        "globalStaticMatchableAssignedTripCount": sum(
            1 for summary in summaries.values()
            if summary["static_matchable_assigned_count"] > 0
        ),
        "globalProtocolValidStopIdReassignmentTripCount": platform_evidence_counts["globalProtocolValidStopIdReassignmentTripCount"],
        "globalStaticMatchableStopIdReassignmentTripCount": platform_evidence_counts["globalStaticMatchableStopIdReassignmentTripCount"],
        "globalProtocolValidPlatformTripCount": platform_evidence_counts["globalProtocolValidPlatformTripCount"],
        "globalStaticMatchablePlatformTripCount": platform_evidence_counts["globalStaticMatchablePlatformTripCount"],
        "associatedAlerts": len(associated_alerts),
    }, ensure_ascii=False))


if __name__ == "__main__":
    main()
