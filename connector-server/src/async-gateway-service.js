export class AsyncGatewayService {
  constructor(store, { nowEpochSeconds = () => Math.floor(Date.now() / 1000) } = {}) {
    this.store = store;
    this.nowEpochSeconds = nowEpochSeconds;
  }

  async getCurrentJourney(userId) {
    const snapshot = await this.#requiredSnapshot(userId);
    return {
      state_version: snapshot.state_version,
      stale: this.#isStale(snapshot),
      journey_phase: snapshot.journey_phase,
      source: snapshot.source,
      active_trip: snapshot.active_trip ?? null,
      distance_to_destination_meters: snapshot.distance_to_destination_meters ?? null,
    };
  }

  async getTripProgress(userId) {
    const snapshot = await this.#requiredSnapshot(userId);
    const trip = snapshot.active_trip ?? {};
    const progressUpdatedAt = Number(trip.progress_updated_at_epoch_seconds ?? 0);
    const progressStaleAfter = Number(snapshot.stale_after_seconds ?? 120);
    const progressStale =
      progressUpdatedAt <= 0 ||
      this.nowEpochSeconds() - progressUpdatedAt > progressStaleAfter;
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
      timing_basis: trip.timing_basis ?? null,
      progress_source: trip.progress_source ?? null,
      progress_updated_at_epoch_seconds: trip.progress_updated_at_epoch_seconds ?? null,
      progress_stale: progressStale,
      scheduled_arrival_epoch_seconds: trip.scheduled_arrival_epoch_seconds ?? null,
      estimated_arrival_epoch_seconds: trip.estimated_arrival_epoch_seconds ?? null,
    };
  }

  async getAlarmState(userId) {
    const snapshot = await this.#requiredSnapshot(userId);
    return {
      state_version: snapshot.state_version,
      stale: this.#isStale(snapshot),
      journey_phase: snapshot.journey_phase,
      alarm_armed: Boolean(snapshot.alarm_armed),
      distance_to_destination_meters: snapshot.distance_to_destination_meters ?? null,
    };
  }

  async queueWrite(userId, command, expectedStateVersion) {
    const snapshot = await this.#requiredSnapshot(userId);
    if (this.#isStale(snapshot)) throw new Error("stale_device_state");
    if (expectedStateVersion !== snapshot.state_version) {
      throw new Error("state_version_mismatch");
    }
    const item = await this.store.enqueueCommand(
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

  async getCommandResult(userId, idempotencyKey) {
    const item = await this.store.commandByIdempotencyKey(userId, idempotencyKey);
    if (!item) return null;
    return {
      command_id: item.command_id,
      idempotency_key: item.command.idempotency_key,
      status: item.status,
      receipt: item.receipt,
    };
  }

  async #requiredSnapshot(userId) {
    const snapshot = await this.store.getSnapshot(userId);
    if (!snapshot) throw new Error("device_state_unavailable");
    return snapshot;
  }

  #isStale(snapshot) {
    const staleAfter = Number(snapshot.stale_after_seconds ?? 120);
    const receivedAt = Number(snapshot.gateway_received_at_epoch_seconds ?? 0);
    return receivedAt <= 0 || this.nowEpochSeconds() - receivedAt > staleAfter;
  }
}
