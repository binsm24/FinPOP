import { PGlite } from "@electric-sql/pglite";
import {readFile,writeFile} from "node:fs/promises";
import assert from "node:assert/strict";
const db=new PGlite();
await db.exec([
"create role anon; create role authenticated; create role service_role bypassrls;",
"create schema auth; grant usage on schema public to anon,authenticated,service_role; grant usage on schema auth to authenticated;",
"create function auth.uid() returns uuid language sql stable as $$ select nullif(current_setting('request.jwt.claim.sub',true),'')::uuid $$;",
"create table auth.users(id uuid primary key);",
"create table public.characters(id text primary key,name text not null,unlock_user_level integer not null,ability_name text not null,ability_description text not null,max_level integer not null default 5);",
"create table public.users(id uuid primary key references auth.users(id) on delete cascade,nickname text not null default '팝콘초보',avatar_path text,total_points integer not null default 0,user_level integer not null default 1,wallet_gold integer not null default 0,completed_game_count integer not null default 0,selected_character_id text default 'CHEESE' references public.characters(id),created_at timestamptz not null default now(),updated_at timestamptz not null default now());",
"create table public.user_characters(user_id uuid references public.users(id) on delete cascade,character_id text references public.characters(id),character_level integer not null default 1,unlocked_at timestamptz not null default now(),primary key(user_id,character_id));",
"alter default privileges in schema public grant all on tables to anon,authenticated,service_role;"
].join("\n"));
await db.exec(await readFile(new URL("../../docs/api/quiz-db-draft.sql",import.meta.url),"utf8"));
await db.exec(await readFile(new URL("../../docs/api/game-db-draft.sql",import.meta.url),"utf8"));
await db.exec(await readFile(new URL("./quiz-fixture.sql",import.meta.url),"utf8"));
const ids={u1:"11111111-1111-4111-8111-111111111111",u2:"22222222-2222-4222-8222-222222222222",g1:"33333333-3333-4333-8333-333333333333",g2:"44444444-4444-4444-8444-444444444444",t1:"55555555-5555-4555-8555-555555555555",t2:"66666666-6666-4666-8666-666666666666",a1:"77777777-7777-4777-8777-777777777777",key:"88888888-8888-4888-8888-888888888888"};
function sql(s){return s.replace(/@(\w+)/g,(_,k)=>ids[k]);}
await db.exec(sql([
"insert into public.characters values ('CHEESE','치즈 팝콘',1,'이동 보너스','테스트',5);",
"insert into auth.users values ('@u1'),('@u2'); insert into public.users(id) values('@u1'),('@u2');",
"insert into public.game_sessions(id,user_id,quiz_stage) values('@g1','@u1',1),('@g2','@u2',1);",
"insert into public.game_players(game_id,player_id,is_ai,character_id,character_level) select id,p,p='P2','CHEESE',1 from public.game_sessions cross join unnest(array['P1','P2']) p;",
"insert into public.game_tiles(game_id,tile_id) select id,generate_series(0,13) from public.game_sessions;",
"insert into public.game_turns(id,game_id,player_id) values('@t1','@g1','P1'),('@t2','@g2','P1');",
"insert into public.game_quiz_attempts(id,game_id,turn_id,player_id,question_id,question_version_id,tile_id,topic,source_type,stage,question,choices,issued_at,deadline_at) values('@a1','@g1','@t1','P1','baseline-q','99999999-9999-4999-8999-999999999999',1,'STOCK','BASIC',1,'문제','[{\"id\":1,\"text\":\"가\"},{\"id\":2,\"text\":\"나\"},{\"id\":3,\"text\":\"다\"},{\"id\":4,\"text\":\"라\"}]','2026-11-17T00:00:00Z','2026-11-17T00:00:20Z');",
"insert into finpop_private.game_quiz_answers(attempt_id,correct_choice_id,explanation) values('@a1',1,'비공개 해설');"
].join("\n")));
const results=[];
async function test(name,work){try{await work();results.push({name,passed:true});}catch(e){results.push({name,passed:false,error:e.message});}}
async function reject(statement,code){
 await db.exec("begin");
 try{await db.exec(sql(statement));await db.exec("set constraints all immediate");}
 catch(e){assert.equal(e.code,code);return;}
 finally{await db.exec("rollback");}
 assert.fail("허용되면 안 되는 SQL이 성공했습니다.");
}
async function role(name,uid,work){
 await db.exec("set role "+name);
 if(uid)await db.query("select set_config('request.jwt.claim.sub',$1,false)",[uid]);
 try{await work();}finally{await db.exec("reset role");}
}
await test("SQL 전체 생성",async()=>assert.equal((await db.query("select count(*)::int n from pg_tables where tablename like 'game_%'")).rows[0].n,7));
for(const table of ["game_sessions","game_players","game_tiles","game_turns","game_quiz_attempts"]){
 await test(table+" 본인 조회",()=>role("authenticated",ids.u1,async()=>{
  const rows=(await db.query("select * from public."+table)).rows;
  assert.ok(rows.length>0);assert.ok(rows.every(r=>(r.game_id??r.id)===ids.g1));
 }));
 await test(table+" 타인 비노출",()=>role("authenticated",ids.u2,async()=>{
  const column=table==="game_sessions"?"id":"game_id";
  assert.equal((await db.query("select * from public."+table+" where "+column+"=$1",[ids.g1])).rows.length,0);
 }));
}
await test("익명 조회 차단",()=>role("anon",null,()=>reject("select * from public.game_sessions","42501")));
await test("앱 생성 차단",()=>role("authenticated",ids.u1,()=>reject("insert into public.game_sessions(user_id,quiz_stage) values('@u1',1)","42501")));
await test("앱 잔액 변경 차단",()=>role("authenticated",ids.u1,()=>reject("update public.game_players set game_gold=999999 where game_id='@g1'","42501")));
await test("앱 삭제 차단",()=>role("authenticated",ids.u1,()=>reject("delete from public.game_tiles","42501")));
for(const table of ["game_quiz_answers","game_action_requests"])
 await test(table+" 비공개",()=>role("authenticated",ids.u1,()=>reject("select * from finpop_private."+table,"42501")));
await test("서버 역할 조회",()=>role("service_role",null,async()=>assert.equal((await db.query("select * from finpop_private.game_quiz_answers")).rows.length,1)));
await test("활성 게임 중복 차단",()=>reject("insert into public.game_sessions(user_id,quiz_stage) values('@u1',1)","23505"));
await test("열린 차례 중복 차단",()=>reject("insert into public.game_turns(game_id,player_id) values('@g1','P2')","23505"));
await test("다른 게임 차례 참조 차단",()=>reject("update public.game_sessions set current_player_id='P1',current_turn_id='@t2' where id='@g1'","23503"));
await test("다른 참가자 차례 참조 차단",()=>reject("update public.game_sessions set current_player_id='P2',current_turn_id='@t1' where id='@g1'","23503"));
await test("차례가 있는데 현재 참가자 없음 차단",()=>reject("update public.game_sessions set current_turn_id='@t1',current_player_id=null where id='@g1'","23514"));
await test("퀴즈 참가자와 차례 불일치 차단",()=>reject("update public.game_quiz_attempts set player_id='P2' where id='@a1'","23503"));
await test("올바른 현재 참가자와 차례 허용",async()=>{
 await db.exec("begin");
 try{await db.exec(sql("update public.game_sessions set current_player_id='P1',current_turn_id='@t1',phase='TURN_READY' where id='@g1'"));await db.exec("set constraints all immediate");}
 finally{await db.exec("rollback");}
});
await test("특수칸 소유 차단",()=>reject("update public.game_tiles set owner_player_id='P1' where game_id='@g1' and tile_id=3","23514"));
await test("보드 밖 위치 차단",()=>reject("update public.game_players set position=14 where game_id='@g1'","23514"));
await test("미판정 선택지 기록 차단",()=>reject("update public.game_quiz_attempts set selected_choice_id=1 where id='@a1'","23514"));
await test("시간 초과 선택지 기록 차단",()=>reject("update public.game_quiz_attempts set selected_choice_id=1,resolution='TIMEOUT',resolved_at=now() where id='@a1'","23514"));
await test("음수 잔액 허용",async()=>{
 await db.exec("begin");
 try{await db.exec(sql("update public.game_players set game_gold=-130 where game_id='@g1' and player_id='P1'"));assert.equal((await db.query(sql("select game_gold from public.game_players where game_id='@g1' and player_id='P1'"))).rows[0].game_gold,-130);}
 finally{await db.exec("rollback");}
});
await test("문항 중복 출제 차단",()=>reject("update public.game_quiz_attempts set resolution='TIMEOUT',resolved_at=now() where id='@a1'; insert into public.game_quiz_attempts(game_id,turn_id,player_id,question_id,question_version_id,tile_id,topic,source_type,stage,question,choices,issued_at,deadline_at) select game_id,turn_id,player_id,question_id,question_version_id,tile_id,topic,source_type,stage,question,choices,issued_at,deadline_at from public.game_quiz_attempts where id='@a1'","23505"));
await test("요청 키 중복 차단",()=>reject("insert into finpop_private.game_action_requests(user_id,request_key,operation,request_body,http_status,response_body) values('@u1','@key','create','{}',200,'{}'),('@u1','@key','roll','{}',200,'{}')","23505"));
const report={engine:"PGlite 0.5.16",passed:results.filter(r=>r.passed).length,failed:results.filter(r=>!r.passed).length,results};
await writeFile(new URL("./results.json",import.meta.url),JSON.stringify(report,null,2));
console.log(JSON.stringify(report,null,2));
await db.close();process.exitCode=report.failed?1:0;
