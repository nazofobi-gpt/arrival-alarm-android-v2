// Keep expected client/body errors distinct from authentication and infrastructure failures.
// Never echo JSON parser diagnostics, which may include the request's input text.
export function classifyHttpError(error) {
  const message = error instanceof Error ? error.message : "unauthorized";
  if (message === "unauthorized") {
    return { status: 401, code: "unauthorized", challenge: "invalid_token" };
  }
  if (message === "insufficient_scope") {
    return { status: 403, code: "insufficient_scope", challenge: "insufficient_scope" };
  }
  if (message === "request_too_large") {
    return { status: 413, code: "request_too_large", challenge: null };
  }
  if (error instanceof SyntaxError) {
    return { status: 400, code: "invalid_json", challenge: null };
  }
  return { status: 503, code: message, challenge: null };
}
