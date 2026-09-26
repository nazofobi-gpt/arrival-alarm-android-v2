import { McpServer } from "@modelcontextprotocol/sdk/server/mcp.js";
import { z } from "zod";

const stopSchema = z.object({
  id: z.string().nullable().optional(),
  name: z.string(),
  latitude: z.number().nullable().optional(),
  longitude: z.number().nullable().optional(),
});

const pointInput = {
  latitude: z.number().min(-90).max(90),
  longitude: z.number().min(-180).max(180),
  label: z.string().min(1).max(200),
};

const writeBaseInput = {
  expected_state_version: z.number().int().positive(),
  idempotency_key: z.string().min(8).max(200),
};

const writeOutput = {
  command_id: z.string(),
  idempotency_key: z.string(),
  status: z.string(),
  expected_state_version: z.number().int(),
  receipt: z.unknown().nullable(),
};

const readAnnotations = {
  readOnlyHint: true,
  destructiveHint: false,
  openWorldHint: false,
  idempotentHint: true,
};

const writeAnnotations = {
  readOnlyHint: false,
  destructiveHint: false,
  openWorldHint: false,
  idempotentHint: true,
};

const readSecuritySchemes = [{ type: "oauth2", scopes: ["arrival.read"] }];
const writeSecuritySchemes = [{ type: "oauth2", scopes: ["arrival.write"] }];

function ok(data, message) {
  return {
    structuredContent: data,
    content: [{ type: "text", text: message }],
  };
}

function failed(error) {
  const message = error instanceof Error ? error.message : "connector_error";
  return {
    isError: true,
    content: [{ type: "text", text: message }],
  };
}

export function createArrivalAlarmMcpServer({ service, userId }) {
  const server = new McpServer(
    {
      name: "arrival-alarm",
      version: "0.1.0",
    },
    {
      instructions:
        "Use read tools to answer trip/alarm questions from current app state. " +
        "For writes, use only the specific action the user requested. " +
        "Never invent a route, stop, trip, or fresh state when the tool reports missing/stale data.",
    }
  );

  server.registerTool(
    "get_profile",
    {
      title: "Get connected Arrival Alarm profile",
      description:
        "Return the stable profile identity represented by this authenticated Arrival Alarm connection.",
      inputSchema: {},
      outputSchema: {
        id: z.string().min(1),
      },
      annotations: readAnnotations,
      securitySchemes: readSecuritySchemes,
      _meta: {
        "openai/profile": true,
      },
    },
    async () => {
      const profile = { id: userId };
      return {
        isError: false,
        structuredContent: profile,
        content: [{ type: "text", text: JSON.stringify(profile) }],
      };
    }
  );

  server.registerTool(
    "get_current_journey",
    {
      title: "Get current journey",
      description:
        "Read the connected Arrival Alarm app's current journey, route and destination state. " +
        "Use for questions such as which trip the user is on or where they are going.",
      inputSchema: {},
      annotations: readAnnotations,
      securitySchemes: readSecuritySchemes,
    },
    async () => {
      try {
        return ok(service.getCurrentJourney(userId), "Current journey state loaded.");
      } catch (error) {
        return failed(error);
      }
    }
  );

  server.registerTool(
    "get_trip_progress",
    {
      title: "Get trip progress",
      description:
        "Read the connected trip's previous, current and next stops plus line and direction. " +
        "Use for questions such as next stop, current stop, or which stop was just passed.",
      inputSchema: {},
      annotations: readAnnotations,
      securitySchemes: readSecuritySchemes,
    },
    async () => {
      try {
        return ok(service.getTripProgress(userId), "Trip progress loaded.");
      } catch (error) {
        return failed(error);
      }
    }
  );

  server.registerTool(
    "get_alarm_state",
    {
      title: "Get arrival alarm state",
      description:
        "Read whether the Arrival Alarm app currently has an armed arrival alarm and its remaining distance when available.",
      inputSchema: {},
      annotations: readAnnotations,
      securitySchemes: readSecuritySchemes,
    },
    async () => {
      try {
        return ok(service.getAlarmState(userId), "Arrival alarm state loaded.");
      } catch (error) {
        return failed(error);
      }
    }
  );

  server.registerTool(
    "set_origin",
    {
      title: "Set trip origin",
      description:
        "Change the connected Arrival Alarm app's trip origin to the exact place the user requested.",
      inputSchema: { ...writeBaseInput, ...pointInput },
      outputSchema: writeOutput,
      annotations: writeAnnotations,
      securitySchemes: writeSecuritySchemes,
    },
    async (args) => {
      try {
        const command = {
          type: "set_origin",
          idempotency_key: args.idempotency_key,
          point: {
            latitude: args.latitude,
            longitude: args.longitude,
            label: args.label,
          },
        };
        const result = service.queueWrite(userId, command, args.expected_state_version);
        return ok(result, "Origin change queued for the connected device.");
      } catch (error) {
        return failed(error);
      }
    }
  );

  server.registerTool(
    "set_destination",
    {
      title: "Set trip destination",
      description:
        "Change the connected Arrival Alarm app's destination to the exact place the user requested.",
      inputSchema: { ...writeBaseInput, ...pointInput },
      outputSchema: writeOutput,
      annotations: writeAnnotations,
      securitySchemes: writeSecuritySchemes,
    },
    async (args) => {
      try {
        const command = {
          type: "set_destination",
          idempotency_key: args.idempotency_key,
          point: {
            latitude: args.latitude,
            longitude: args.longitude,
            label: args.label,
          },
        };
        const result = service.queueWrite(userId, command, args.expected_state_version);
        return ok(result, "Destination change queued for the connected device.");
      } catch (error) {
        return failed(error);
      }
    }
  );

  server.registerTool(
    "select_journey",
    {
      title: "Select journey",
      description:
        "Select one route/journey that is already present in the connected Arrival Alarm app state. " +
        "Do not fabricate a route id.",
      inputSchema: {
        ...writeBaseInput,
        route_id: z.string().min(1).max(300),
      },
      outputSchema: writeOutput,
      annotations: writeAnnotations,
      securitySchemes: writeSecuritySchemes,
    },
    async (args) => {
      try {
        const result = service.queueWrite(
          userId,
          {
            type: "select_journey",
            idempotency_key: args.idempotency_key,
            route_id: args.route_id,
          },
          args.expected_state_version
        );
        return ok(result, "Journey selection queued for the connected device.");
      } catch (error) {
        return failed(error);
      }
    }
  );

  server.registerTool(
    "set_boarding_stop",
    {
      title: "Set boarding stop",
      description:
        "Set the boarding stop for the active journey in the connected Arrival Alarm app.",
      inputSchema: {
        ...writeBaseInput,
        stop: stopSchema,
      },
      outputSchema: writeOutput,
      annotations: writeAnnotations,
      securitySchemes: writeSecuritySchemes,
    },
    async (args) => {
      try {
        const result = service.queueWrite(
          userId,
          {
            type: "set_boarding_stop",
            idempotency_key: args.idempotency_key,
            stop: args.stop,
          },
          args.expected_state_version
        );
        return ok(result, "Boarding stop change queued for the connected device.");
      } catch (error) {
        return failed(error);
      }
    }
  );

  server.registerTool(
    "arm_arrival_alarm",
    {
      title: "Arm arrival alarm",
      description:
        "Arm the arrival alarm for the currently selected origin, destination and journey in the connected app.",
      inputSchema: writeBaseInput,
      outputSchema: writeOutput,
      annotations: writeAnnotations,
      securitySchemes: writeSecuritySchemes,
    },
    async (args) => {
      try {
        const result = service.queueWrite(
          userId,
          {
            type: "arm_arrival_alarm",
            idempotency_key: args.idempotency_key,
          },
          args.expected_state_version
        );
        return ok(result, "Arrival alarm command queued for the connected device.");
      } catch (error) {
        return failed(error);
      }
    }
  );

  server.registerTool(
    "cancel_arrival_alarm",
    {
      title: "Cancel arrival alarm",
      description:
        "Cancel the currently armed arrival alarm in the connected Arrival Alarm app.",
      inputSchema: writeBaseInput,
      outputSchema: writeOutput,
      annotations: writeAnnotations,
      securitySchemes: writeSecuritySchemes,
    },
    async (args) => {
      try {
        const result = service.queueWrite(
          userId,
          {
            type: "cancel_arrival_alarm",
            idempotency_key: args.idempotency_key,
          },
          args.expected_state_version
        );
        return ok(result, "Arrival alarm cancellation queued for the connected device.");
      } catch (error) {
        return failed(error);
      }
    }
  );

  server.registerTool(
    "get_command_result",
    {
      title: "Get command result",
      description:
        "Read the device receipt for a previously queued Arrival Alarm command.",
      inputSchema: {
        idempotency_key: z.string().min(8).max(200),
      },
      annotations: readAnnotations,
      securitySchemes: readSecuritySchemes,
    },
    async ({ idempotency_key }) => {
      const result = service.getCommandResult(userId, idempotency_key);
      return ok(
        result ?? { status: "not_found", idempotency_key },
        result ? "Command result loaded." : "No command result exists for that idempotency key."
      );
    }
  );

  return server;
}
