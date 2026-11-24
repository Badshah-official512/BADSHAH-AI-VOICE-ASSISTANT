// BADSHAH AI — frontend settings (NO secrets in this file).
// v7.8.3: EVERYTHING IS FREE for users. Chat and images run on the OWNER's API
// keys (Gemini/Groq/ElevenLabs), pooled in Supabase (api_keys) and served/rotated
// by the Worker — users need no key of their own for chat or images.
// Live voice calls are BYOK: each user adds their OWN free Gemini key (from
// Google AI Studio) inside the app, and the call runs on that key.
// Admin password is verified against Supabase.
window.BADSHAH_CONFIG = {
  imageUrl: "/api/image",
  videoUrl: "/api/video",
  apiUrl: "/api/chat",
  liveKeyUrl: "/api/live-key",
  ttsUrl: "/api/tts",

  // Supabase (same backend as the Android app) — publishable key is a public client key
  supabaseUrl: "https://aghlacmqlagmigoznxlm.supabase.co",
  supabaseAnonKey: "sb_publishable_cxY5POeM8kbv0CyJ4LAJ_Q_ao8vaFKn",

  // Firebase Auth (project badshah-ai-badshah222)
  // OWNER STEP: paste apiKey + appId from Firebase Console → Project settings → Your apps → Web app.
  firebase: {
    apiKey: "AIzaSyCy_uK_4kR3rEIrhdKIyVnlRYCil2zo25Y",
    authDomain: "badshah-ai-badshah222.firebaseapp.com",
    projectId: "badshah-ai-badshah222",
    storageBucket: "badshah-ai-badshah222.firebasestorage.app",
    messagingSenderId: "949903255351",
    appId: "1:949903255351:web:24181015a2dfbe76ae1cb2"
  },

  whatsapp: "https://wa.me/923251787969",
  supportEmail: "zainff043@gmail.com",
  apkPath: "./BADSHAH-AI.apk",

  // ALL models are FREE (owner's decision, v3)
  // v7.8.3 (FULL-CHECK B1/B3): every Gemini ID below was PROVEN live on
  // 2026-11-24 through the real /api/chat path (provider:"gemini").
  // gemini-3.8-flash + gemini-3.1-pro-preview NEVER answered (silently fell
  // over to Groq) — removed. Mistral 502'd on every model ID (pool key dead)
  // — removed so no menu item dead-ends. The OpenRouter "Gemma 4 coding"
  // entry never served its named model either (answered only via the
  // openrouter/free fallback, twice) — removed; openrouter/free stays.
  chatModels: [
    { id: "gemini:gemini-3.5-flash-lite", label: "Gemini 3.5 Flash Lite — Chat" },
    { id: "gemini:gemini-3.5-flash-lite", label: "Gemini 3.5 Flash Lite — Coding (same model)" }
  ],
  imageModels: ["gemini-3.1-flash-image", "gemini-3-pro-image"],
  imageModelLabels: { "gemini-3.1-flash-image": "Gemini 3.1 Flash Image — Fast", "gemini-3-pro-image": "Gemini 3 Pro Image — Quality" },
  videoModels: ["cogvideox-flash"],
  moreModels: [
    { id: "openrouter:openrouter/free", label: "OpenRouter Free — Auto (jo free model mile)" }
  ],
  groqModels: [
    { id: "openai/gpt-oss-120b", label: "Groq GPT-OSS 120B (smart)" },
    { id: "openai/gpt-oss-20b", label: "Groq GPT-OSS 20B (fast)" }
  ],

  // Gemini Live (call) — BYOK: the user adds their own free Gemini key in the app
  liveModel: "gemini-3.8-live",
  voicesMale: ["Charon", "Fenrir", "Puck", "Orus", "Algieba", "Achird", "Enceladus", "Iapetus", "Umbriel", "Algenib", "Rasalgethi", "Alnilam", "Schedar", "Gacrux", "Zubenelgenubi", "Sadachbia", "Sadaltager"],
  voicesFemale: ["Aoede", "Kore", "Zephyr", "Leda", "Sulafat", "Callirrhoe", "Autonoe", "Despina", "Erinome", "Laomedeia", "Pulcherrima", "Vindemiatrix"]
};
