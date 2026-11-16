import { createHandler } from "./handler.ts";

// Unified AI gateway for BADSHAH AI. Provider keys are Edge Function secrets —
// they never ship in the Android app. Deploy with Verify JWT OFF (same as
// admin-api); callers authenticate with the x-gateway-secret header.
Deno.serve(createHandler({ env: Deno.env }));
