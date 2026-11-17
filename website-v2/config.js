// BADSHAH AI — frontend settings (NO secrets in this file).
// v3: EVERYTHING IS FREE for users. The FT Fusion key lives ONLY in the
// Cloudflare Worker secret. Owner API keys (Gemini/Groq/ElevenLabs) live in
// Supabase (api_keys pool) and are served/rotated by the Worker — users never
// need a key of their own. Admin password is verified against Supabase.
window.BADSHAH_CONFIG = {
  apiUrl: "/api/chat",
  liveKeyUrl: "/api/live-key",
  ttsUrl: "/api/tts",

  // Supabase (same backend as the Android app) — publishable key is a public client key
  supabaseUrl: "https://aghlacmqlagmigoznxlm.supabase.co",
  supabaseAnonKey: "sb_publishable_cxY5POeM8kbv0CyJ4LAJ_Q_ao8vaFKn",

  // Firebase Auth (project badshah-ai-badshah222)
  // OWNER STEP: paste apiKey + appId from Firebase Console → Project settings → Your apps → Web app.
  firebase: {
    apiKey: "PASTE_FROM_FIREBASE_CONSOLE",
    authDomain: "badshah-ai-badshah222.firebaseapp.com",
    projectId: "badshah-ai-badshah222",
    storageBucket: "badshah-ai-badshah222.firebasestorage.app",
    messagingSenderId: "949903255351",
    appId: "PASTE_FROM_FIREBASE_CONSOLE"
  },

  whatsapp: "https://wa.me/923251787969",
  supportEmail: "zainff043@gmail.com",
  apkPath: "./BADSHAH-AI.apk",

  // ALL models are FREE (owner's decision, v3)
  chatModels: ["fusion-flex", "fusion-max", "fusion-pulse", "fusion-spark", "fusion-lexus", "fusion-synth", "fusion-void", "fusion-reaper", "fusion-dread", "fusion-shadow", "fusion-abyss", "fusion-vortex"],
  imageModels: ["fusion-uni", "fusion-astra"],
  groqModels: [
    { id: "llama-3.3-70b-versatile", label: "Groq Llama 3.3 (fast)" },
    { id: "llama-3.1-8b-instant", label: "Groq Llama 3.1 (fastest)" }
  ],

  // Gemini Live (call) — key comes from the owner's pool via /api/live-key
  liveModel: "gemini-3.8-live",
  voicesMale: ["Charon", "Fenrir", "Puck", "Orus", "Algieba", "Achird"],
  voicesFemale: ["Aoede", "Kore", "Zephyr", "Leda", "Sulafat", "Callirrhoe"]
};
