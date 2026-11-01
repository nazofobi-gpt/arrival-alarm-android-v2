"""Unit tests for provider-derived G-176 platform reassignment evidence."""

from pathlib import Path
import sys
import unittest

sys.path.insert(0, str(Path(__file__).resolve().parents[1]))
from extract_g176_provider_samples import (  # noqa: E402
    choose_platform_evidence,
    parse_trip_update,
    resolve_regions,
    select_protocol_valid_stop_id_reassignments,
    select_static_matchable_assigned_stops,
    select_static_matchable_stop_id_reassignments,
)


def _varint(value: int) -> bytes:
    out = bytearray()
    while value > 0x7F:
        out.append((value & 0x7F) | 0x80)
        value >>= 7
    out.append(value)
    return bytes(out)


def _field_varint(number: int, value: int) -> bytes:
    return _varint((number << 3) | 0) + _varint(value)


def _field_bytes(number: int, value: bytes) -> bytes:
    return _varint((number << 3) | 2) + _varint(len(value)) + value


def _trip_update(updates):
    trip = _field_bytes(1, b"trip-1")
    update = _field_bytes(1, trip)
    for sequence, stop_id, assigned_stop_id in updates:
        stop = b""
        if sequence is not None:
            stop += _field_varint(1, sequence)
        if stop_id is not None:
            stop += _field_bytes(4, stop_id.encode("utf-8"))
        if assigned_stop_id is not None:
            properties = _field_bytes(1, assigned_stop_id.encode("utf-8"))
            stop += _field_bytes(6, properties)
        update += _field_bytes(2, stop)
    return update


class G176PlatformReassignmentEvidenceTest(unittest.TestCase):
    def test_assigned_stop_without_sequence_is_not_protocol_valid(self):
        parsed = parse_trip_update(_trip_update([(None, None, "platform-2")]))
        self.assertEqual(1, parsed["assigned_count"])
        self.assertEqual(0, parsed["protocol_valid_assigned_count"])
        self.assertEqual([], parsed["protocol_valid_assigned_stops"])

    def test_assigned_stop_must_agree_with_explicit_stop_id(self):
        parsed = parse_trip_update(_trip_update([(7, "scheduled-7", "platform-7")]))
        self.assertEqual(1, parsed["assigned_count"])
        self.assertEqual(0, parsed["protocol_valid_assigned_count"])

    def test_assigned_stop_with_sequence_and_omitted_stop_id_requires_same_station(self):
        parsed = parse_trip_update(_trip_update([(7, None, "platform-7")]))
        self.assertEqual(1, parsed["protocol_valid_assigned_count"])
        static_rows = {7: ["scheduled-7"]}
        matchable = select_static_matchable_assigned_stops(
            parsed,
            static_rows,
            {"scheduled-7", "platform-7"},
            {"scheduled-7": "station-1", "platform-7": "station-1"},
        )
        self.assertEqual(
            [{
                "stop_sequence": 7,
                "stop_id": None,
                "assigned_stop_id": "platform-7",
                "scheduled_stop_id": "scheduled-7",
                "parent_station_id": "station-1",
            }],
            matchable,
        )

    def test_assigned_stop_cross_station_or_missing_static_stop_fails_closed(self):
        parsed = parse_trip_update(_trip_update([(7, None, "platform-7")]))
        static_rows = {7: ["scheduled-7"]}
        self.assertEqual([], select_static_matchable_assigned_stops(
            parsed,
            static_rows,
            {"scheduled-7", "platform-7"},
            {"scheduled-7": "station-1", "platform-7": "station-2"},
        ))
        self.assertEqual([], select_static_matchable_assigned_stops(
            parsed,
            static_rows,
            {"scheduled-7", "platform-7"},
            {"scheduled-7": "", "platform-7": ""},
        ))
        self.assertEqual([], select_static_matchable_assigned_stops(
            parsed,
            static_rows,
            {"scheduled-7"},
            {"scheduled-7": "station-1"},
        ))

    def test_duplicate_provider_sequence_is_not_static_matchable(self):
        parsed = parse_trip_update(_trip_update([
            (7, None, "platform-7a"),
            (7, None, "platform-7b"),
        ]))
        self.assertEqual(2, parsed["protocol_valid_assigned_count"])
        self.assertEqual([], select_static_matchable_assigned_stops(
            parsed,
            {7: ["scheduled-7"]},
            {"scheduled-7", "platform-7a", "platform-7b"},
            {
                "scheduled-7": "station-1",
                "platform-7a": "station-1",
                "platform-7b": "station-1",
            },
        ))

    def test_duplicate_static_sequence_is_not_matchable(self):
        parsed = parse_trip_update(_trip_update([(7, None, "platform-7")]))
        self.assertEqual([], select_static_matchable_assigned_stops(
            parsed,
            {7: ["scheduled-7a", "scheduled-7b"]},
            {"scheduled-7a", "scheduled-7b", "platform-7"},
            {
                "scheduled-7a": "station-1",
                "scheduled-7b": "station-1",
                "platform-7": "station-1",
            },
        ))

    def test_stop_id_reassignment_with_unique_sequence_and_same_station_is_matchable(self):
        parsed = parse_trip_update(_trip_update([(7, "platform-7", None)]))
        self.assertEqual(1, parsed["stop_id_reassignment_candidate_count"])
        protocol_valid = select_protocol_valid_stop_id_reassignments(
            parsed,
            {7: ["scheduled-7"]},
        )
        self.assertEqual(
            [{
                "stop_sequence": 7,
                "stop_id": "platform-7",
                "scheduled_stop_id": "scheduled-7",
                "realtime_stop_id": "platform-7",
            }],
            protocol_valid,
        )
        self.assertEqual(
            [{
                "stop_sequence": 7,
                "stop_id": "platform-7",
                "scheduled_stop_id": "scheduled-7",
                "realtime_stop_id": "platform-7",
                "parent_station_id": "station-1",
            }],
            select_static_matchable_stop_id_reassignments(
                protocol_valid,
                {"scheduled-7", "platform-7"},
                {"scheduled-7": "station-1", "platform-7": "station-1"},
            ),
        )

    def test_stop_id_equal_to_scheduled_stop_is_not_a_reassignment(self):
        parsed = parse_trip_update(_trip_update([(7, "scheduled-7", None)]))
        self.assertEqual(1, parsed["stop_id_reassignment_candidate_count"])
        self.assertEqual([], select_protocol_valid_stop_id_reassignments(
            parsed,
            {7: ["scheduled-7"]},
        ))

    def test_stop_id_reassignment_cross_station_missing_stop_or_missing_parent_fails_closed(self):
        parsed = parse_trip_update(_trip_update([(7, "platform-7", None)]))
        protocol_valid = select_protocol_valid_stop_id_reassignments(
            parsed,
            {7: ["scheduled-7"]},
        )
        self.assertEqual([], select_static_matchable_stop_id_reassignments(
            protocol_valid,
            {"scheduled-7", "platform-7"},
            {"scheduled-7": "station-1", "platform-7": "station-2"},
        ))
        self.assertEqual([], select_static_matchable_stop_id_reassignments(
            protocol_valid,
            {"scheduled-7"},
            {"scheduled-7": "station-1"},
        ))
        self.assertEqual([], select_static_matchable_stop_id_reassignments(
            protocol_valid,
            {"scheduled-7", "platform-7"},
            {"scheduled-7": "", "platform-7": ""},
        ))

    def test_stop_id_reassignment_requires_unique_provider_and_static_sequence(self):
        duplicate_provider = parse_trip_update(_trip_update([
            (7, "platform-7a", None),
            (7, "platform-7b", None),
        ]))
        self.assertEqual([], select_protocol_valid_stop_id_reassignments(
            duplicate_provider,
            {7: ["scheduled-7"]},
        ))
        unique_provider = parse_trip_update(_trip_update([(7, "platform-7", None)]))
        self.assertEqual([], select_protocol_valid_stop_id_reassignments(
            unique_provider,
            {7: ["scheduled-7a", "scheduled-7b"]},
        ))

    def test_platform_evidence_can_use_either_supported_representation(self):
        summaries = {
            "raw-only": {
                "static_matchable_platform_evidence_count": 0,
                "protocol_valid_assigned_count": 1,
                "protocol_valid_stop_id_reassignment_count": 0,
                "delay_count": 12,
                "stop_count": 8,
            },
            "assigned": {
                "static_matchable_platform_evidence_count": 1,
                "protocol_valid_assigned_count": 1,
                "protocol_valid_stop_id_reassignment_count": 0,
                "delay_count": 2,
                "stop_count": 4,
            },
            "stop-id": {
                "static_matchable_platform_evidence_count": 2,
                "protocol_valid_assigned_count": 0,
                "protocol_valid_stop_id_reassignment_count": 2,
                "delay_count": 1,
                "stop_count": 5,
            },
        }
        self.assertEqual("stop-id", choose_platform_evidence(summaries))
        summaries["assigned"]["static_matchable_platform_evidence_count"] = 0
        summaries["stop-id"]["static_matchable_platform_evidence_count"] = 0
        self.assertIsNone(choose_platform_evidence(summaries))


    def test_region_selection_can_target_vbb_berlin_only(self):
        selected = resolve_regions(["berlin"])
        self.assertEqual(["berlin"], list(selected))
        self.assertEqual(52.5251, selected["berlin"]["lat"])

    def test_region_selection_rejects_duplicates_and_unknown_names(self):
        with self.assertRaises(ValueError):
            resolve_regions(["berlin", "berlin"])
        with self.assertRaises(ValueError):
            resolve_regions(["hamburg"])


if __name__ == "__main__":
    unittest.main()
