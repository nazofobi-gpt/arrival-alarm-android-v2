import test from "node:test";
import assert from "node:assert/strict";
import { Client } from "@modelcontextprotocol/sdk/client/index.js";
import { InMemoryTransport } from "@modelcontextprotocol/sdk/inMemory.js";
import { createArrivalAlarmMcpServer } from "../src/mcp-server.js";

function fakeService() {
  return {
    getCurrentJourney: () => ({
      state_version: 1,
      stale: false,
      journey_phase: "DESTINATION_SELECTED",
      source: "test",
      active_trip: null,
      distance_to_destination_meters: null,
    }),
    getTripProgress: () => ({
      state_version: 1,
      stale: false,
      trip_id: null,
      route_id: null,
      line: null,
      direction: null,
      origin: null,
      destination: null,
      boarding_stop: null,
      previous_stop: null,
      current_stop: null,
      next_stop: null,
      timing_basis: null,
      progress_source: null,
      progress_updated_at_epoch_seconds: null,
      progress_stale: true,
      scheduled_arrival_epoch_seconds: null,
      estimated_arrival_epoch_seconds: null,
    }),
    getAlarmState: () => ({
      state_version: 1,
      stale: false,
      journey_phase: "DESTINATION_SELECTED",
      alarm_armed: false,
      distance_to_destination_meters: null,
    }),
    getCommandResult: () => null,
    queueWrite: (_userId, command, version) => ({
      command_id: "cmd-1",
      idempotency_key: command.idempotency_key,
      status: "queued",
      expected_state_version: version,
      receipt: null,
    }),
  };
}

const READ_TOOLS = new Set([
  "get_profile",
  "get_current_journey",
  "get_trip_progress",
  "get_alarm_state",
  "get_command_result",
]);

const WRITE_TOOLS = new Set([
  "set_origin",
  "set_destination",
  "select_journey",
  "set_boarding_stop",
  "arm_arrival_alarm",
  "cancel_arrival_alarm",
]);

async function listedTools() {
  const server = createArrivalAlarmMcpServer({
    service: fakeService(),
    userId: "user-1",
    scopes: ["arrival.read", "arrival.write"],
    resourceMetadataUrl:
      "https://connector.example/.well-known/oauth-protected-resource",
  });
  const [clientTransport, serverTransport] = InMemoryTransport.createLinkedPair();
  const client = new Client(
    { name: "arrival-contract-test", version: "1.0.0" },
    { capabilities: {} }
  );

  await Promise.all([
    server.connect(serverTransport),
    client.connect(clientTransport),
  ]);

  try {
    return (await client.listTools()).tools;
  } finally {
    await client.close();
    await server.close();
  }
}

test("MCP publishes the complete expected read/write tool surface", async () => {
  const tools = await listedTools();
  const names = new Set(tools.map((tool) => tool.name));

  assert.deepEqual(names, new Set([...READ_TOOLS, ...WRITE_TOOLS]));
});

test("MCP tool annotations and OAuth scopes match real side effects", async () => {
  const tools = await listedTools();

  for (const tool of tools) {
    const annotations = tool.annotations ?? {};
    const schemes =
      tool.securitySchemes?.length > 0
        ? tool.securitySchemes
        : tool._meta?.securitySchemes ?? [];

    assert.equal(annotations.openWorldHint, false);
    assert.equal(annotations.idempotentHint, true);

    if (READ_TOOLS.has(tool.name)) {
      assert.equal(annotations.readOnlyHint, true, tool.name);
      assert.equal(annotations.destructiveHint, false, tool.name);
      assert.deepEqual(schemes, [{ type: "oauth2", scopes: ["arrival.read"] }]);
    } else if (WRITE_TOOLS.has(tool.name)) {
      assert.equal(annotations.readOnlyHint, false, tool.name);
      assert.equal(annotations.destructiveHint, false, tool.name);
      assert.deepEqual(schemes, [{ type: "oauth2", scopes: ["arrival.write"] }]);
    } else {
      assert.fail("Unexpected tool: " + tool.name);
    }
  }
});

test("profile tool keeps the ChatGPT connected-profile marker", async () => {
  const tools = await listedTools();
  const profile = tools.find((tool) => tool.name === "get_profile");

  assert.equal(profile?._meta?.["openai/profile"], true);
  assert.ok(profile?.outputSchema);
});
