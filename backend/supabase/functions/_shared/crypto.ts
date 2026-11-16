// Small WebCrypto helpers (work in Deno and Node 20+).
const enc = new TextEncoder();

export async function sha256Hex(input: string): Promise<string> {
  const buf = await crypto.subtle.digest("SHA-256", enc.encode(input));
  return [...new Uint8Array(buf)].map((b) => b.toString(16).padStart(2, "0")).join("");
}

export function randomToken(bytes = 32): string {
  const a = crypto.getRandomValues(new Uint8Array(bytes));
  return btoa(String.fromCharCode(...a)).replace(/\+/g, "-").replace(/\//g, "_").replace(/=+$/, "");
}

/** Constant-time string comparison (length is not secret here). */
export function timingSafeEqual(a: string, b: string): boolean {
  const x = enc.encode(a), y = enc.encode(b);
  if (x.length !== y.length) return false;
  let r = 0;
  for (let i = 0; i < x.length; i++) r |= x[i] ^ y[i];
  return r === 0;
}
