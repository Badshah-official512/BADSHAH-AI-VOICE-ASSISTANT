// BADSHAH AI admin API. Never called by browsers directly: the Cloudflare
// Worker adds x-gateway-secret, x-client-ip and x-admin-token (from an
// HttpOnly cookie). Everything here runs with the service role.
import { randomToken, sha256Hex, timingSafeEqual } from "../_shared/crypto.ts";
import { sendEmail } from "../_shared/email.ts";

// deno-lint-ignore no-explicit-any
type Sb = any;
export interface Env { get(k: string): string | undefined }
export interface Deps { sb: Sb; env: Env; fetchFn?: typeof fetch; sleep?: (ms: number) => Promise<void> }

const json = (status: number, body: unknown) =>
  new Response(JSON.stringify(body), { status, headers: { "content-type": "application/json", "cache-control": "no-store" } });
const bad = (msg: string, status = 400) => json(status, { error: msg });

const TICKET_STATUS = ["open", "in_progress", "resolved", "closed"];
const SECRETISH = /(secret|api[_-]?key|token|password|passwd|private)/i;
const str = (v: unknown, max: number) => (typeof v === "string" ? v.trim().slice(0, max) : "");
const uuidRe = /^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/i;

function hasSecretKey(v: unknown): boolean {
  if (Array.isArray(v)) return v.some(hasSecretKey);
  if (v && typeof v === "object") return Object.entries(v).some(([k, x]) => SECRETISH.test(k) || hasSecretKey(x));
  return false;
}

export function createHandler(deps: Deps) {
  const { sb, env } = deps;
  const sleep = deps.sleep ?? ((ms: number) => new Promise((r) => setTimeout(r, ms)));
  const rpc = async (fn: string, args: Record<string, unknown> = {}) => {
    const { data, error } = await sb.rpc(fn, args);
    if (error) throw new Error(`${fn}: ${error.message}`);
    return data;
  };
  const audit = (action: string, target: string | null, details: Record<string, unknown> = {}) =>
    rpc("admin_write_audit", { p_action: action, p_target_user_id: target, p_details: details }).catch(() => {});

  return async function handle(req: Request): Promise<Response> {
    if (req.method !== "POST") return bad("method not allowed", 405);
    const gw = env.get("GATEWAY_SECRET") ?? "";
    if (gw.length < 24 || !timingSafeEqual(req.headers.get("x-gateway-secret") ?? "", gw)) {
      return bad("forbidden", 403); // direct calls (not via the Worker) are rejected
    }
    let body: Record<string, unknown>;
    try { body = await req.json(); } catch { return bad("invalid JSON"); }
    const action = str(body.action, 60);
    const ip = str(req.headers.get("x-client-ip"), 80) || "unknown";
    const ipHash = await sha256Hex(`${ip}|${env.get("IP_HASH_PEPPER") ?? "pepper"}`);

    try {
      // ---------------- LOGIN (password only) ----------------
      if (action === "login") {
        const pw = typeof body.password === "string" ? body.password : "";
        if (!pw || pw.length > 256) return bad("invalid credentials", 401);
        if (!(await rpc("admin_rate_limit_ok", { p_ip_hash: ipHash }))) {
          return json(429, { error: "Too many failed attempts. Try again in 15 minutes." });
        }
        const ok = await rpc("verify_admin_password", { p_password: pw });
        await rpc("admin_record_attempt", { p_ip_hash: ipHash, p_success: ok === true });
        if (ok !== true) { await sleep(400); await audit("admin_login_failed", null, {}); return bad("invalid credentials", 401); }
        const token = randomToken(32);
        const ttl = Math.min(Math.max(parseInt(env.get("ADMIN_SESSION_MINUTES") ?? "120"), 5), 720);
        const expires_at = await rpc("admin_create_session", { p_token_hash: await sha256Hex(token), p_ttl_minutes: ttl });
        await audit("admin_login", null, {});
        return json(200, { token, expires_at });
      }

      // ---------------- everything else needs a session ----------------
      const token = str(req.headers.get("x-admin-token"), 200);
      if (!token) return bad("unauthorized", 401);
      const tokenHash = await sha256Hex(token);
      const idle = Math.min(Math.max(parseInt(env.get("ADMIN_IDLE_MINUTES") ?? "30"), 5), 240);
      const sid = await rpc("admin_validate_session", { p_token_hash: tokenHash, p_idle_minutes: idle });
      if (!sid) return bad("session expired", 401);

      switch (action) {
        case "session": return json(200, { ok: true, session_id: sid });
        case "logout":
          await rpc("admin_revoke_session", { p_token_hash: tokenHash });
          await audit("admin_logout", null, {});
          return json(200, { ok: true });

        // ---------- analytics ----------
        case "overview": return json(200, await rpc("admin_overview"));
        case "analytics.login": return json(200, await rpc("admin_login_analytics", { p_days: Number(body.days) || 30 }));
        case "analytics.api": return json(200, await rpc("admin_api_analytics", { p_days: Number(body.days) || 30 }));

        // ---------- users ----------
        case "users.list": {
          const st = ["all", "active", "blocked"].includes(String(body.status)) ? String(body.status) : "all";
          return json(200, await rpc("admin_list_users", {
            p_search: str(body.search, 100) || null, p_status: st,
            p_limit: Math.min(Number(body.limit) || 25, 100), p_offset: Math.max(Number(body.offset) || 0, 0),
          }));
        }
        case "users.detail": {
          const id = str(body.user_id, 200); if (!id) return bad("user_id required");
          return json(200, await rpc("admin_user_detail", { p_user_id: id }));
        }
        case "users.block":
        case "users.unblock": {
          const id = str(body.user_id, 200); if (!id) return bad("user_id required");
          const blocked = action === "users.block";
          const { data, error } = await sb.from("profiles").update({ is_blocked: blocked, updated_at: new Date().toISOString() }).eq("id", id).select("id");
          if (error) throw new Error(error.message);
          if (!data?.length) return bad("user not found", 404);
          await audit(blocked ? "user_blocked" : "user_unblocked", id, {});
          return json(200, { ok: true, is_blocked: blocked });
        }
        case "users.update": {
          const id = str(body.user_id, 200); if (!id) return bad("user_id required");
          const patch: Record<string, unknown> = { updated_at: new Date().toISOString() };
          if (typeof body.display_name === "string") patch.display_name = str(body.display_name, 120);
          if (Object.keys(patch).length < 2) return bad("nothing to update");
          const { data, error } = await sb.from("profiles").update(patch).eq("id", id).select("id");
          if (error) throw new Error(error.message);
          if (!data?.length) return bad("user not found", 404);
          await audit("user_updated", id, { fields: Object.keys(patch).filter((k) => k !== "updated_at") });
          return json(200, { ok: true });
        }
        case "users.delete": {
          const id = str(body.user_id, 200); if (!id) return bad("user_id required");
          if (body.confirm !== id) return bad("confirmation must equal the user id");
          const { data, error } = await sb.from("profiles").delete().eq("id", id).select("id");
          if (error) throw new Error(error.message);
          if (!data?.length) return bad("user not found", 404);
          await audit("user_deleted", id, {});
          return json(200, {
            ok: true,
            note: "Database records deleted. The Firebase Auth account still exists; if the person signs in again an empty profile is recreated. Prefer Block, or also delete the account in Firebase Console > Authentication.",
          });
        }

        // ---------- support ----------
        case "tickets.list": {
          let q = sb.from("support_tickets").select("id,user_id,subject,category,status,priority,created_at,updated_at").order("updated_at", { ascending: false }).limit(200);
          const st = str(body.status, 20);
          if (st) { if (!TICKET_STATUS.includes(st)) return bad("bad status"); q = q.eq("status", st); }
          const { data, error } = await q; if (error) throw new Error(error.message);
          const ids = [...new Set((data ?? []).map((t: { user_id: string }) => t.user_id))];
          const { data: ps } = ids.length ? await sb.from("profiles").select("id,email,display_name").in("id", ids) : { data: [] };
          const byId = new Map((ps ?? []).map((p: { id: string }) => [p.id, p]));
          return json(200, { tickets: (data ?? []).map((t: { user_id: string }) => ({ ...t, user: byId.get(t.user_id) ?? null })) });
        }
        case "tickets.get": {
          const id = str(body.ticket_id, 40); if (!uuidRe.test(id)) return bad("bad ticket_id");
          const { data: t } = await sb.from("support_tickets").select("*").eq("id", id).maybeSingle();
          if (!t) return bad("ticket not found", 404);
          const { data: msgs } = await sb.from("support_messages").select("id,sender_type,message,created_at").eq("ticket_id", id).order("created_at", { ascending: true });
          const { data: p } = await sb.from("profiles").select("id,email,display_name").eq("id", t.user_id).maybeSingle();
          return json(200, { ticket: t, messages: msgs ?? [], user: p ?? null });
        }
        case "tickets.reply": {
          const id = str(body.ticket_id, 40); if (!uuidRe.test(id)) return bad("bad ticket_id");
          const text = str(body.message, 5000); if (!text) return bad("message required");
          const status = body.status ? str(body.status, 20) : "in_progress";
          if (!TICKET_STATUS.includes(status)) return bad("bad status");
          const { data: t } = await sb.from("support_tickets").select("id,user_id,subject").eq("id", id).maybeSingle();
          if (!t) return bad("ticket not found", 404);
          const ins = await sb.from("support_messages").insert({ ticket_id: id, sender_type: "admin", message: text });
          if (ins.error) throw new Error(ins.error.message);
          await sb.from("support_tickets").update({ admin_reply: text, status }).eq("id", id);
          const { data: p } = await sb.from("profiles").select("email,display_name").eq("id", t.user_id).maybeSingle();
          let email: { status: string; error?: string } = { status: "skipped", error: "user has no email on file" };
          if (p?.email) {
            const subject = `Re: ${t.subject} - BADSHAH AI Support`;
            const mail = `Hello ${p.display_name ?? ""},\n\n${text}\n\nYou can also read this reply in the BADSHAH AI app under Help Center.\n\n- BADSHAH AI Support`;
            email = await sendEmail(env, p.email, subject, mail, deps.fetchFn);
            await sb.from("email_outbox").insert({ ticket_id: id, to_email: p.email, subject, body: mail, status: email.status, error: email.error ?? null, sent_at: email.status === "sent" ? new Date().toISOString() : null });
          }
          await audit("ticket_reply", t.user_id, { ticket_id: id, status, email: email.status });
          return json(200, { ok: true, status, email });
        }
        case "tickets.status": {
          const id = str(body.ticket_id, 40); if (!uuidRe.test(id)) return bad("bad ticket_id");
          const status = str(body.status, 20); if (!TICKET_STATUS.includes(status)) return bad("bad status");
          const { data, error } = await sb.from("support_tickets").update({ status }).eq("id", id).select("id,user_id");
          if (error) throw new Error(error.message);
          if (!data?.length) return bad("ticket not found", 404);
          await audit("ticket_status", data[0].user_id, { ticket_id: id, status });
          return json(200, { ok: true });
        }

        // ---------- app management ----------
        case "announcements.list": {
          const { data } = await sb.from("announcements").select("*").order("created_at", { ascending: false }).limit(100);
          return json(200, { announcements: data ?? [] });
        }
        case "announcements.save": {
          const title = str(body.title, 200), content = str(body.content, 5000);
          if (!title || !content) return bad("title and content required");
          const row = { title, content, is_published: body.is_published === true, updated_at: new Date().toISOString() };
          const id = str(body.id, 40);
          const r = id ? (uuidRe.test(id) ? await sb.from("announcements").update(row).eq("id", id).select("id") : null)
                       : await sb.from("announcements").insert(row).select("id");
          if (!r) return bad("bad id"); if (r.error) throw new Error(r.error.message);
          await audit("announcement_saved", null, { id: r.data?.[0]?.id, published: row.is_published });
          return json(200, { ok: true, id: r.data?.[0]?.id });
        }
        case "announcements.delete": {
          const id = str(body.id, 40); if (!uuidRe.test(id)) return bad("bad id");
          await sb.from("announcements").delete().eq("id", id);
          await audit("announcement_deleted", null, { id });
          return json(200, { ok: true });
        }
        case "config.list": {
          const { data } = await sb.from("app_config").select("*").order("config_key");
          return json(200, { config: data ?? [] });
        }
        case "config.set": {
          const key = str(body.key, 60);
          if (!/^[a-z0-9_.-]{1,60}$/.test(key)) return bad("invalid key");
          if (SECRETISH.test(key)) return bad("secrets must never be stored in app_config");
          const raw = JSON.stringify(body.value ?? null);
          if (raw.length > 10000) return bad("value too large");
          if (hasSecretKey(body.value)) return bad("secrets must never be stored in app_config");
          const { error } = await sb.from("app_config").upsert({ config_key: key, config_value: body.value ?? {}, updated_at: new Date().toISOString() });
          if (error) throw new Error(error.message);
          await audit("config_set", null, { key });
          return json(200, { ok: true });
        }
        case "voices.list": {
          const { data } = await sb.from("voice_profiles").select("*").order("sort_order").limit(200);
          return json(200, { voices: data ?? [] });
        }
        case "voices.save": {
          const id = str(body.id, 60);
          if (!/^[a-z0-9_-]{2,60}$/.test(id)) return bad("id must be lowercase letters, digits, - or _");
          const row = {
            id, label: str(body.label, 80), category: str(body.category, 30) || "natural", language: str(body.language, 20) || "en",
            engine: ["android_tts", "elevenlabs"].includes(String(body.engine)) ? String(body.engine) : "android_tts",
            pitch: Math.min(Math.max(Number(body.pitch) || 1, 0.5), 2), speed: Math.min(Math.max(Number(body.speed) || 1, 0.5), 2),
            is_enabled: body.is_enabled !== false, sort_order: Math.trunc(Number(body.sort_order) || 100),
            notes: str(body.notes, 300) || null, updated_at: new Date().toISOString(),
          };
          if (!row.label) return bad("label required");
          const { error } = await sb.from("voice_profiles").upsert(row);
          if (error) throw new Error(error.message);
          await audit("voice_saved", null, { id });
          return json(200, { ok: true });
        }
        case "voices.delete": {
          const id = str(body.id, 60); if (!id) return bad("id required");
          await sb.from("voice_profiles").delete().eq("id", id);
          await audit("voice_deleted", null, { id });
          return json(200, { ok: true });
        }

        // ---------- security ----------
        case "security.overview":
          return json(200, {
            attempts: await rpc("admin_list_attempts", { p_limit: 100 }),
            audit: await rpc("admin_list_audit", { p_limit: 100 }),
            sessions: await rpc("admin_list_sessions", { p_limit: 50 }),
            current_session_id: sid,
          });
        case "security.revoke_session": {
          const target = str(body.session_id, 40); if (!uuidRe.test(target)) return bad("bad session_id");
          const n = await rpc("admin_revoke_session", { p_session_id: target });
          await audit("session_revoked", null, { session_id: target });
          return json(200, { ok: true, revoked: n });
        }
        case "security.revoke_others": {
          const n = await rpc("admin_revoke_all_sessions", { p_except_token_hash: tokenHash });
          await audit("sessions_revoked_others", null, { count: n });
          return json(200, { ok: true, revoked: n });
        }
        case "security.change_password": {
          const cur = typeof body.current_password === "string" ? body.current_password : "";
          const next = typeof body.new_password === "string" ? body.new_password : "";
          if (next.length < 12) return bad("new password must be at least 12 characters");
          if (!(await rpc("admin_rate_limit_ok", { p_ip_hash: ipHash }))) return json(429, { error: "Too many failed attempts." });
          const ok = await rpc("verify_admin_password", { p_password: cur });
          await rpc("admin_record_attempt", { p_ip_hash: ipHash, p_success: ok === true });
          if (ok !== true) return bad("current password is wrong", 401);
          if (!(await rpc("change_admin_password", { p_new_password: next }))) return bad("password rejected");
          await rpc("admin_revoke_all_sessions", { p_except_token_hash: tokenHash });
          await audit("admin_password_changed", null, {});
          return json(200, { ok: true, note: "All other admin sessions were signed out." });
        }
        default: return bad("unknown action", 404);
      }
    } catch (e) {
      console.error("admin-api error", action, String(e));
      return json(500, { error: "internal error" }); // never leak DB details
    }
  };
}
