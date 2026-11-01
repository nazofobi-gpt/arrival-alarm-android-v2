"""Unit tests for provider-derived G-176 assigned-stop evidence selection."""

from pathlib import Path
import sys
import unittest

sys.path.insert(0, str(Path(__file__).resolve().parents[1]))
from extract_g176_provider_samples import (  # noqa: E402
    choose_platform_evidence,
    parse_trip_update,
    select_static_matchable_assigned_stops,
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


def _trip_update(assignments):
    trip = _field_bytes(1, b"trip-1")
    update = _field_bytes(1, trip)
    for sequence, stop_id, assigned_stop_id in assignments:
        properties = _field_bytes(1, assigned_stop_id.encode("utf-8"))
        stop = b""
        if sequence is not None:
            stop += _field_varint(1, sequence)
        if stop_id is not None:
            stop += _field_bytes(4, stop_id.encode("utf-8"))
        stop += _field_bytes(6, properties)
        update += _field_bytes(2, stop)
    return update


class G176AssignedStopEvidenceTest(unittest.TestCase):
    def test_assigned_stop_without_sequence_is_not_protocol_valid(self):
        parsed = parse_trip_update(_trip_update([(None, None, "platform-2")]))
        self.assertEqual(1, parsed["assigned_count"])
        self.assertEqual(0, parsed["protocol_valid_assigned_count"])
        self.assertEqual([], parsed["protocol_valid_assigned_stops"])

    def test_assigned_stop_must_agree_with_explicit_stop_id(self):
        parsed = parse_trip_update(_trip_update([(7, "scheduled-7", "platform-7")]))
        self.assertEqual(1, parsed["assigned_count"])
        self.assertEqual(0, parsed["protocol_valid_assigned_count"])

    def test_assigned_stop_with_sequence_and_omitted_stop_id_is_valid(self):
        parsed = parse_trip_update(_trip_update([(7, None, "platform-7")]))
        self.assertEqual(1, parsed["protocol_valid_assigned_count"])
        self.assertEqual(
            [{"stop_sequence": 7, "stop_id": None, "assigned_stop_id": "platform-7"}],
            parsed["protocol_valid_assigned_stops"],
        )
        self.assertEqual(
            parsed["protocol_valid_assigned_stops"],
            select_static_matchable_assigned_stops(parsed, {7: 1}),
        )

    def test_duplicate_provider_sequence_is_not_static_matchable(self):
        parsed = parse_trip_update(_trip_update([
            (7, None, "platform-7a"),
            (7, None, "platform-7b"),
        ]))
        self.assertEqual(2, parsed["protocol_valid_assigned_count"])
        self.assertEqual(
            [],
            select_static_matchable_assigned_stops(parsed, {7: 1}),
        )

    def test_duplicate_static_sequence_is_not_matchable(self):
        parsed = parse_trip_update(_trip_update([(7, None, "platform-7")]))
        self.assertEqual(
            [],
            select_static_matchable_assigned_stops(parsed, {7: 2}),
        )

    def test_platform_evidence_requires_static_matchable_assignment(self):
        summaries = {
            "raw-only": {
                "static_matchable_assigned_count": 0,
                "protocol_valid_assigned_count": 1,
                "delay_count": 12,
                "stop_count": 8,
            },
            "verified": {
                "static_matchable_assigned_count": 1,
                "protocol_valid_assigned_count": 1,
                "delay_count": 2,
                "stop_count": 4,
            },
        }
        self.assertEqual("verified", choose_platform_evidence(summaries))
        summaries["verified"]["static_matchable_assigned_count"] = 0
        self.assertIsNone(choose_platform_evidence(summaries))


if __name__ == "__main__":
    unittest.main()
