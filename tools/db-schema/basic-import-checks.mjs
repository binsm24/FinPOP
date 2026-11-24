import {readFile} from "node:fs/promises";
import assert from "node:assert/strict";

/** 사용자 검토 완료된 원본으로 생성한 등록 SQL을 로컬 DB에서만 검증합니다. */
export async function validateBasicImport({db,test}){
 const input=JSON.parse((await readFile(new URL("../../supabase/seed-data/basic-finance-30.json",import.meta.url),"utf8")).replace(/^\uFEFF/,""));
 const sql=await readFile(new URL("../../docs/api/basic-finance-30-import.sql",import.meta.url),"utf8");
 const tables=["public.users","public.characters","public.user_characters","public.game_sessions","public.game_players",
   "public.game_tiles","public.game_turns","public.game_quiz_attempts","finpop_private.game_action_requests","finpop_private.game_quiz_answers"];
 async function capture(){
   const result={};
   for(const table of tables)result[table]=(await db.query("select coalesce(jsonb_agg(to_jsonb(t) order by to_jsonb(t)::text),'[]'::jsonb) snapshot from "+table+" t")).rows[0].snapshot;
   return result;
 }
 const before=await capture();
 const ids=input.questions.map(q=>q.id);
 await test("기본 30문항·13출처·37연결 승인 등록",async()=>{
   await db.exec(sql);
   const stats=(await db.query("select count(*)::int questions,count(*) filter(where review_status='APPROVED')::int approved from public.quiz_question_versions where question_id=any($1::text[])",[ids])).rows[0];
   assert.equal(stats.questions,30);assert.equal(stats.approved,30);
   const links=(await db.query("select count(*)::int links,count(distinct s.source_id)::int sources from public.quiz_version_sources s join public.quiz_question_versions v on v.id=s.question_version_id where v.question_id=any($1::text[])",[ids])).rows[0];
   assert.equal(links.links,37);assert.equal(links.sources,13);
 });
 await test("원본 문제·힌트·보기·정답·해설 변환 일치",async()=>{
   const rows=(await db.query("select q.id,q.source_type,v.stage,v.version,v.question,v.hint,v.choices,a.correct_choice_id,a.explanation from public.quiz_questions q join public.quiz_question_versions v on v.question_id=q.id join finpop_private.quiz_answer_keys a on a.question_version_id=v.id where q.id=any($1::text[])",[ids])).rows;
   for(const q of input.questions){
     const row=rows.find(r=>r.id===q.id);
     assert.equal(row.question,q.question);assert.equal(row.hint,q.hint);
     assert.equal(row.explanation,q.explanation);assert.equal(row.correct_choice_id,"ABCD".indexOf(q.correctOptionId)+1);
     assert.deepEqual(row.choices,q.options.map(o=>({id:"ABCD".indexOf(o.id)+1,text:o.text})));
     assert.equal(row.stage,1);assert.equal(row.source_type,"BASIC");assert.equal(row.version,q.version);
   }
 });
 await test("기존 사용자·캐릭터·게임 데이터 보존",async()=>assert.deepEqual(await capture(),before));
 await test("중복 등록은 덮어쓰기 없이 중단",async()=>{
   const counts=(await db.query("select count(*)::int n from public.quiz_question_versions")).rows[0].n;
   let rejected=false;
   try{await db.exec(sql);}catch(e){assert.equal(e.code,"P0001");rejected=true;await db.exec("rollback");}
   assert.ok(rejected);assert.equal((await db.query("select count(*)::int n from public.quiz_question_versions")).rows[0].n,counts);
   assert.deepEqual(await capture(),before);
 });
}

