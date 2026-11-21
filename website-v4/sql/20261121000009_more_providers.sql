-- v7.2: allow OpenRouter + Mistral keys in the api_keys pool. Safe to re-run.
alter table public.api_keys drop constraint if exists api_keys_provider_check;
alter table public.api_keys add constraint api_keys_provider_check
  check (provider in ('gemini','groq','elevenlabs','stability','replicate','openrouter','mistral'));
