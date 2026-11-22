-- 014: api_keys provider CHECK mein 'novai' shamil karo
-- NovAI (aiapi-pro.com) Zhipu CogVideoX-Flash ko relay karta hai — har video generation FREE hai.
-- Key Admin Panel se add hogi (provider: novai); worker /api/video mein Agnes ke baad fallback hai.
alter table public.api_keys drop constraint if exists api_keys_provider_check;
alter table public.api_keys add constraint api_keys_provider_check
  check (provider in ('gemini','groq','elevenlabs','stability','replicate','openrouter','mistral','pollinations-image','pollinations-video','agnes','novai'));
