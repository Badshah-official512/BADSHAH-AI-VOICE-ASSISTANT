-- v7.3 (migration 012): video key asal mein Agnes AI (platform.agnes-ai.com) ki hai — provider sahi karo
alter table public.api_keys drop constraint if exists api_keys_provider_check;
alter table public.api_keys add constraint api_keys_provider_check
  check (provider in ('gemini','groq','elevenlabs','stability','replicate','openrouter','mistral','pollinations-image','pollinations-video','agnes'));
update public.api_keys set provider = 'agnes', label = 'Agnes video (owner)' where provider = 'pollinations-video';
