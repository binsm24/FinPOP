import { createHandler } from "./handler.mjs";

// 서버에 기본 제공되는 환경변수만 사용합니다. 실제 키를 소스에 기록하지 않습니다.
const handler = createHandler({
  url: Deno.env.get("SUPABASE_URL"),
  publicKey: Deno.env.get("SUPABASE_ANON_KEY"),
  serviceKey: Deno.env.get("SUPABASE_SERVICE_ROLE_KEY"),
});
Deno.serve(handler);

