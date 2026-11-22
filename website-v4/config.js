// BADSHAH AI — frontend settings (NO secrets in this file).
// v3: EVERYTHING IS FREE for users. The FT Fusion key lives ONLY in the
// Cloudflare Worker secret. Owner API keys (Gemini/Groq/ElevenLabs) live in
// Supabase (api_keys pool) and are served/rotated by the Worker — users never
// need a key of their own. Admin password is verified against Supabase.
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
  chatModels: [
    { id: "gemini:gemini-3.8-flash", label: "Gemini 3.8 Flash — Chat" },
    { id: "gemini:gemini-3.5-flash-lite", label: "Gemini 3.5 Flash Lite — Fast Chat" },
    { id: "gemini:gemini-3.1-pro-preview", label: "Gemini 3.1 Pro — Coding" }
  ],
  imageModels: ["gemini-3.1-flash-image", "gemini-3-pro-image"],
  imageModelLabels: { "gemini-3.1-flash-image": "Gemini 3.1 Flash Image — Fast", "gemini-3-pro-image": "Gemini 3 Pro Image — Quality" },
  videoModels: ["agnes-video-2.5-flash"],
  moreModels: [
    { id: "openrouter:openrouter/free", label: "OpenRouter Free — Auto (jo free model mile)" },
    { id: "openrouter:google/gemma-4-31b-it:free", label: "CODING — Gemma 4 (Free, sirf coding ke liye)" },
    { id: "mistral:mistral-small-latest", label: "Mistral Small — Free Tier" }
  ],
  groqModels: [
    { id: "openai/gpt-oss-120b", label: "Groq GPT-OSS 120B (smart)" },
    { id: "openai/gpt-oss-20b", label: "Groq GPT-OSS 20B (fast)" }
  ],

  // Gemini Live (call) — key comes from the owner's pool via /api/live-key
  liveModel: "gemini-3.8-live",
  voicesMale: ["Charon", "Fenrir", "Puck", "Orus", "Algieba", "Achird", "Enceladus", "Iapetus", "Umbriel", "Algenib", "Rasalgethi", "Alnilam", "Schedar", "Gacrux", "Zubenelgenubi", "Sadachbia", "Sadaltager"],
  voicesFemale: ["Aoede", "Kore", "Zephyr", "Leda", "Sulafat", "Callirrhoe", "Autonoe", "Despina", "Erinome", "Laomedeia", "Pulcherrima", "Vindemiatrix"]
};
