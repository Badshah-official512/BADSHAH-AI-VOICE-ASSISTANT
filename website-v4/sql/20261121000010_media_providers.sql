-- v7.3 (migration 010): image/video generation ke liye Pollinations providers
-- api_keys ke provider check mein 'pollinations-image' aur 'pollinations-video' shamil
alter table public.api_keys drop constraint if exists api_keys_provider_check;
alter table public.api_keys add constraint api_keys_provider_check
  check (provider in ('gemini','groq','elevenlabs','stability','replicate','openrouter','mistral','pollinations-image','pollinations-video'));
