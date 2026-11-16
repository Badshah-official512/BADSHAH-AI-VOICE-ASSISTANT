// Configurable server-side email. Provider: Resend (https://resend.com).
// Configure with Edge Function secrets: RESEND_API_KEY, EMAIL_FROM.
// If either is missing nothing is sent and the outbox row is marked 'skipped'.
export interface EmailResult { status: "sent" | "failed" | "skipped"; error?: string }

export async function sendEmail(
  env: { get(k: string): string | undefined },
  to: string, subject: string, text: string,
  fetchFn: typeof fetch = fetch,
): Promise<EmailResult> {
  const key = env.get("RESEND_API_KEY"), from = env.get("EMAIL_FROM");
  if (!key || !from) return { status: "skipped", error: "email provider not configured (RESEND_API_KEY / EMAIL_FROM)" };
  try {
    const r = await fetchFn("https://api.resend.com/emails", {
      method: "POST",
      headers: { "Authorization": `Bearer ${key}`, "Content-Type": "application/json" },
      body: JSON.stringify({ from, to: [to], subject, text }),
    });
    if (!r.ok) return { status: "failed", error: `provider HTTP ${r.status}` };
    return { status: "sent" };
  } catch (e) {
    return { status: "failed", error: String(e).slice(0, 200) };
  }
}
