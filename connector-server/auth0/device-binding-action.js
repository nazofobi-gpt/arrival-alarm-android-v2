/**
 * Auth0 Free-plan Post Login Action for the Arrival Alarm Android native client.
 *
 * Action secret:
 *   ARRIVAL_ANDROID_CLIENT_ID=<native app client id>
 *
 * The Android authorize request sends ext-device_id. We persist the current
 * device id in app_metadata so refresh-token exchanges can reproduce the same
 * namespaced access-token claim without exposing a secret in the APK.
 *
 * Free MVP policy: one active Android device binding per Auth0 user. A login
 * from a new device intentionally replaces the previous binding.
 */
exports.onExecutePostLogin = async (event, api) => {
  if (event.client?.client_id !== event.secrets.ARRIVAL_ANDROID_CLIENT_ID) {
    return;
  }

  const requested =
    typeof event.request?.query?.["ext-device_id"] === "string"
      ? event.request.query["ext-device_id"].trim()
      : "";
  const stored =
    typeof event.user?.app_metadata?.arrival_alarm_device_id === "string"
      ? event.user.app_metadata.arrival_alarm_device_id.trim()
      : "";

  if (requested && !/^[A-Za-z0-9._:-]{8,128}$/.test(requested)) {
    api.access.deny("invalid_device_id");
    return;
  }

  const deviceId = requested || stored;
  if (!deviceId) {
    api.access.deny("device_id_required");
    return;
  }

  if (requested && requested !== stored) {
    api.user.setAppMetadata("arrival_alarm_device_id", requested);
  }

  api.accessToken.setCustomClaim(
    "https://arrival-alarm.app/device_id",
    deviceId
  );
};
