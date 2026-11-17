// BADSHAH AI — frontend settings (NO secrets in this file).
// The FT Fusion key lives ONLY in the Cloudflare Worker secret BADSHAH_API_KEY.
// The admin password lives ONLY in the Cloudflare Worker secret ADMIN_PASSWORD.
window.BADSHAH_CONFIG = {
  // AI chat/image endpoint (our own secure Worker proxy)
  apiUrl: "/api/chat",

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

  // Business
  paymentNumber: "03251787969",
  whatsapp: "https://wa.me/923251787969",
  supportEmail: "zainff043@gmail.com",
  apkPath: "./BADSHAH-AI.apk",

  // Plans (PKR). Custom duration = months × 100.
  plans: [
    { months: 1, price: 100 },
    { months: 3, price: 250 },
    { months: 5, price: 400 },
    { months: 12, price: 700 }
  ],

  // Models
  freeChatModels: ["fusion-flex", "fusion-max"],
  freeImageModel: "fusion-uni",
  paidChatModels: ["fusion-pulse", "fusion-spark", "fusion-lexus", "fusion-synth", "fusion-void", "fusion-reaper", "fusion-dread", "fusion-shadow", "fusion-abyss", "fusion-vortex"],
  paidImageModel: "fusion-astra",

  // Gemini Live (call) — user's OWN key, browser-only storage
  liveModel: "gemini-3.8-live",
  voicesMale: ["Charon", "Fenrir", "Puck", "Orus", "Algieba", "Achird"],
  voicesFemale: ["Aoede", "Kore", "Zephyr", "Leda", "Sulafat", "Callirrhoe"]
};
