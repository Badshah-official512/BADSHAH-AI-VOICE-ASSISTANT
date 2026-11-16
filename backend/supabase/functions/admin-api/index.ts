import { createClient } from "npm:@supabase/supabase-js@2";
import { createHandler } from "./handler.ts";

// SUPABASE_URL and SUPABASE_SERVICE_ROLE_KEY are injected by Supabase automatically.
const sb = createClient(Deno.env.get("SUPABASE_URL")!, Deno.env.get("SUPABASE_SERVICE_ROLE_KEY")!, {
  auth: { persistSession: false, autoRefreshToken: false },
});
Deno.serve(createHandler({ sb, env: Deno.env }));
