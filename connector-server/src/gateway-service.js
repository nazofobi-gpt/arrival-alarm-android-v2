export class GatewayService {
  constructor(store, { nowEpochSeconds = () => Math.floor(Date.now() / 1000) } = {}) {
    this.store = store;
    this.nowEpochSeconds = nowEpochSeconds;
  }

  getCurrentJourney(userId) {
    const snapshot = this.#requiredSnapshot(userId);
    return {
      state_version: snapshot.state_version,
      stale: this.#isStale(snapshot),
      journey_phase: snapshot.journey_phase,
      source: snapshot.source,
      active_trip: snapshot.active_trip ?? null,
      distance_to_destination_meters: snapshot.distance_to_destination_meters ?? null,
    };
  }

  getTripProgress(userId) {
    const snapshot = this.#requiredSnapshot(userId);
    const trip = snapshot.active_trip ?? {};
    return {
      state_version: snapshot.state_version,
      stale: this.#isStale(snapshot),
      trip_id: trip.trip_id ?? null,
      route_id: trip.route_id ?? null,
      line: trip.line ?? null,
      direction: trip.direction ?? null,
      origin: trip.origin ?? null,
      destination: trip.destination ?? null,
      boarding_stop: trip.boarding_stop ?? null,
      previous_stop: trip.previous_stop ?? null,
      current_stop: trip.current_stop ?? null,
      next_stop: trip.next_stop ?? null,
    };
  }

  getAlarmState(userId) {
    const snapshot = this.#requiredSnapshot(userId);
    return {
      state_version: snapshot.state_version,
      stale: this.#isStale(snapshot),
      journey_phase: snapshot.journey_phase,
      alarm_armed: Boolean(snapshot.alarm_armed),
      distance_to_destination_meters: snapshot.distance_to_destination_meters ?? null,
    };
  }

  queueWrite(userId, command, expectedStateVersion) {
    const snapshot = this.#requiredSnapshot(userId);
    if (this.#isStale(snapshot)) {
      throw new Error("stale_device_state");
    }
    if (expectedStateVersion !== snapshot.state_version) {
      throw new Error("state_version_mismatch");
    }
    const item = this.store.enqueueCommand(
      userId,
      command,
      expectedStateVersion,
      snapshot.gateway_device_id ?? null
    );
    return {
      command_id: item.command_id,
      idempotency_key: item.command.idempotency_key,
      status: item.status,
      expected_state_version: item.expected_state_version,
      receipt: item.receipt,
    };
  }

  getCommandResult(userId, idempotencyKey) {
    const item = this.store.commandByIdempotencyKey(userId, idempotencyKey);
    if (!item) return null;
    return {
      command_id: item.command_id,
      idempotency_key: item.command.idempotency_key,
      status: item.status,
      receipt: item.receipt,
    };
  }

  #requiredSnapshot(userId) {
    const snapshot = this.store.getSnapshot(userId);
    if (!snapshot) throw new Error("device_state_unavailable");
    return snapshot;
  }

  #isStale(snapshot) {
    const staleAfter = Number(snapshot.stale_after_seconds ?? 120);
    // Device freshness is based on when this gateway actually received a snapshot.
    // Domain values may remain unchanged for minutes during an idle journey screen.
    const receivedAt = Number(snapshot.gateway_received_at_epoch_seconds ?? 0);
    return receivedAt <= 0 || this.nowEpochSeconds() - receivedAt > staleAfter;
  }
}
