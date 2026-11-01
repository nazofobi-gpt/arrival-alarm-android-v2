import test from "node:test";
import assert from "node:assert/strict";
import { Client } from "@modelcontextprotocol/sdk/client/index.js";
import { InMemoryTransport } from "@modelcontextprotocol/sdk/inMemory.js";
import { createArrivalAlarmMcpServer } from "../src/mcp-server.js";

async function withMcpClient(run) {
  const queued = [];
  const server = createArrivalAlarmMcpServer({
    userId: "user-1",
    scopes: ["arrival.read", "arrival.write"],
    service: {
      queueWrite(userId, command, expectedStateVersion) {
        queued.push({ userId, command, expectedStateVersion });
        return {
          command_id: "cmd-1",
          idempotency_key: command.idempotency_key,
          status: "queued",
          expected_state_version: expectedStateVersion,
          receipt: null,
        };
      },
    },
  });
  const [clientTransport, serverTransport] = InMemoryTransport.createLinkedPair();
  const client = new Client(
    { name: "mcp-input-validation-test", version: "1.0.0" },
    { capabilities: {} }
  );
  try {
    await Promise.all([server.connect(serverTransport), client.connect(clientTransport)]);
    await run(client, queued);
  } finally {
    await client.close();
    await server.close();
  }
}

const base = {
  expected_state_version: 1,
  idempotency_key: "mcp-validation-regression-001",
};

async function assertRejectedBeforeWrite(client, tool, input) {
  let rejected = false;
  try {
    const response = await client.callTool({ name: tool, arguments: { ...base, ...input } });
    rejected = response.isError === true;
  } catch {
    rejected = true;
  }
  assert.equal(rejected, true, tool + " must reject malformed input");
}

test("MCP write tools reject blank labels and out-of-range stop coordinates before queuing", async () => {
  await withMcpClient(async (client, queued) => {
    const invalid = [
      ["set_origin", { latitude: 52.66, longitude: 8.24, label: "   " }],
      ["set_destination", { latitude: 52.66, longitude: 8.24, label: "   " }],
      ["set_boarding_stop", { stop: { name: "   ", latitude: 53, longitude: 8 } }],
      ["set_boarding_stop", { stop: { name: "Bremen Hbf", latitude: 90.001, longitude: 8 } }],
      ["set_boarding_stop", { stop: { name: "Bremen Hbf", latitude: 53, longitude: -180.001 } }],
      ["set_boarding_stop", { stop: { name: "x".repeat(201) } }],
    ];
    for (const [tool, input] of invalid) {
      await assertRejectedBeforeWrite(client, tool, input);
      assert.equal(queued.length, 0, tool + " must not enqueue invalid input");
    }
  });
});

test("MCP accepts bounded coordinates and normalizes valid labels without losing nullable stop fields", async () => {
  await withMcpClient(async (client, queued) => {
    const boarding = await client.callTool({
      name: "set_boarding_stop",
      arguments: {
        ...base,
        stop: { id: null, name: "  Bremen Hbf  ", latitude: 53.08, longitude: 8.81 },
      },
    });
    assert.notEqual(boarding.isError, true);
    assert.equal(queued.length, 1);
    assert.deepEqual(queued[0].command.stop, {
      id: null, name: "Bremen Hbf", latitude: 53.08, longitude: 8.81,
    });

    const destination = await client.callTool({
      name: "set_destination",
      arguments: { ...base, latitude: -90, longitude: 180, label: "  Lohne  " },
    });
    assert.notEqual(destination.isError, true);
    assert.equal(queued.length, 2);
    assert.deepEqual(queued[1].command.point, {
      latitude: -90, longitude: 180, label: "Lohne",
    });
  });
});
