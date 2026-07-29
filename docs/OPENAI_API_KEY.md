# OpenAI / LLM API Key

Rupiyah uses an OpenAI-compatible chat API to extract transaction fields from messy bank emails and SMS when deterministic parsing is not enough.

## Option A — OpenAI

1. Create an account at [platform.openai.com](https://platform.openai.com/).
2. Add billing if required by OpenAI.
3. Open [API keys](https://platform.openai.com/api-keys).
4. **Create new secret key**, copy it once.
5. In Rupiyah: **Settings → Intelligence → LLM Providers**
   - Base URL: `https://api.openai.com/v1`
   - Model: e.g. `gpt-4o-mini`
   - API key: paste the secret
   - Save

## Option B — Groq (default in app)

1. Create an account at [console.groq.com](https://console.groq.com/).
2. Open **API Keys** → create a key.
3. In Rupiyah:
   - Base URL: `https://api.groq.com/openai/v1`
   - Model: e.g. `llama-3.3-70b-versatile`
   - API key: paste the Groq key

## Option C — Any OpenAI-compatible proxy

Any host that implements `/v1/chat/completions` works (Azure OpenAI-compatible gateways, local Ollama with OpenAI shim, etc.).

- Base URL must end before `/chat/completions` (example: `http://127.0.0.1:11434/v1`)
- Model must match what the host expects

## Privacy

- Email bodies are redacted before leaving the device (card numbers, OTPs, etc. stripped where possible).
- Keys are stored in **EncryptedSharedPreferences** on device.
- Never commit API keys to git.

## Dev system prompt

Advanced users can edit the extraction system prompt under the hidden **Developer** page (tap the app version on Settings seven times).
