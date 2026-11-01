import test from "node:test";
import assert from "node:assert/strict";
import { readJson } from "../src/cloudflare-worker.js";

const MAX_BODY_BYTES = 256 * 1024;
const encoder = new TextEncoder();

function streamedRequest(chunks, headers = {}) {
  const pending = [...chunks];
  let cancelled = false;
  const body = new ReadableStream({
    pull(controller) {
      if (pending.length) controller.enqueue(pending.shift());
      else controller.close();
    },
    cancel() {
      cancelled = true;
    },
  });
  return {
    request: new Request("https://worker.example/device/state", {
      method: "POST",
      headers,
      body,
      duplex: "half",
    }),
    wasCancelled: () => cancelled,
  };
}

test("Worker decodes bounded JSON across streamed chunks", async () => {
  const bytes = encoder.encode(JSON.stringify({ stop: "Lohne", active: true }));
  const { request } = streamedRequest([bytes.slice(0, 8), bytes.slice(8, 17), bytes.slice(17)]);
  assert.deepEqual(await readJson(request), { stop: "Lohne", active: true });
  assert.deepEqual(await readJson(new Request("https://worker.example/device/state")), {});
});

test("Worker accepts valid JSON at exactly the 256 KiB limit", async () => {
  const emptyJson = JSON.stringify({ value: "" });
  const payload = JSON.stringify({ value: "x".repeat(MAX_BODY_BYTES - encoder.encode(emptyJson).length) });
  const bytes = encoder.encode(payload);
  assert.equal(bytes.byteLength, MAX_BODY_BYTES);
  const { request } = streamedRequest([
    bytes.slice(0, 65_536),
    bytes.slice(65_536, 131_072),
    bytes.slice(131_072),
  ]);
  const parsed = await readJson(request);
  assert.equal(parsed.value.length, MAX_BODY_BYTES - encoder.encode(emptyJson).length);
});

test("Worker stops and cancels an oversized chunked body with no length header", async () => {
  const { request, wasCancelled } = streamedRequest([
    encoder.encode("x".repeat(90_000)),
    encoder.encode("x".repeat(90_000)),
    encoder.encode("x".repeat(90_000)),
    encoder.encode("x".repeat(90_000)),
  ]);
  await assert.rejects(readJson(request), /request_too_large/);
  assert.equal(wasCancelled(), true);
});

test("Worker enforces the stream limit when Content-Length is understated", async () => {
  const { request, wasCancelled } = streamedRequest(
    [new Uint8Array(150_000), new Uint8Array(150_000)],
    { "content-length": "1" }
  );
  await assert.rejects(readJson(request), /request_too_large/);
  assert.equal(wasCancelled(), true);
});

test("Worker rejects advertised oversized bodies without waiting for body consumption", async () => {
  const { request } = streamedRequest([encoder.encode("{}")], {
    "content-length": String(MAX_BODY_BYTES + 1),
  });
  await assert.rejects(readJson(request), /request_too_large/);
});
