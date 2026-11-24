import {readFile,writeFile} from "node:fs/promises";
import {createHash} from "node:crypto";
import assert from "node:assert/strict";

const input=new URL("../../supabase/seed-data/basic-finance-30.json",import.meta.url);
const output=new URL("../../docs/api/basic-finance-30-import.sql",import.meta.url);
const raw=await readFile(input,"utf8");
const data=JSON.parse(raw.replace(/^\uFEFF/,""));
const map={
 "주식":["STOCK",1],"채권":["BOND",2],"만기":["MATURITY",4],"복리":["COMPOUND",5],
 "배당":["DIVIDEND",6],"예금":["DEPOSIT",8],"적금":["SAVINGS",9],"대출":["LOAN",11],
 "자산":["ASSET",12],"펀드":["FUND",13],
};
const letters={A:1,B:2,C:3,D:4};
function uid(key){
 const bytes=createHash("sha256").update("finpop-basic-finance-30:"+key).digest().subarray(0,16);
 bytes[6]=(bytes[6]&15)|128;bytes[8]=(bytes[8]&63)|128;
 const h=bytes.toString("hex");
 return h.slice(0,8)+"-"+h.slice(8,12)+"-"+h.slice(12,16)+"-"+h.slice(16,20)+"-"+h.slice(20);
}
const literal=value=>value==null?"null":"'"+String(value).replaceAll("'","''")+"'";
const nonempty=value=>typeof value==="string"&&value.trim().length>0;
assert.equal(data.questions.length,30,"30문항이어야 합니다.");
assert.equal(new Set(data.questions.map(q=>q.id)).size,30,"문항 ID가 중복됐습니다.");
assert.equal(new Set(data.sources.map(s=>s.id)).size,data.sources.length,"출처 ID가 중복됐습니다.");
const sources=new Map(data.sources.map(s=>{
 assert.ok(nonempty(s.id)&&nonempty(s.publisher)&&nonempty(s.title)&&/^https?:\/\//.test(s.url),"출처 형식 오류");
 return [s.id,{...s,dbId:uid("source:"+s.id+":"+s.url)}];
}));
const questions=data.questions.map(q=>{
 assert.ok(nonempty(q.id)&&map[q.topic],"문항 ID·주제 오류");
 assert.equal(q.boardIndex,map[q.topic][1],"보드 주제가 다릅니다.");
 assert.equal(q.version,1,"이번 등록은 최초 버전 1만 처리합니다.");
 assert.equal(q.timeLimitSeconds,20,"제한 시간 오류");
 assert.ok(nonempty(q.question)&&nonempty(q.hint)&&nonempty(q.explanation),"본문 누락");
 assert.equal(q.options.length,4);
 assert.deepEqual([...q.options.map(o=>o.id)].sort(),["A","B","C","D"]);
 assert.ok(q.options.every(o=>nonempty(o.text))&&letters[q.correctOptionId],"보기·정답 오류");
 assert.ok(q.sourceIds.length>0&&q.sourceIds.every(id=>sources.has(id)),"출처 참조 오류");
 assert.equal(new Set(q.sourceIds).size,q.sourceIds.length,"문항 출처가 중복됐습니다.");
 return {...q,dbId:uid("question:"+q.id+":version:"+q.version),code:map[q.topic][0]};
});
for(const topic of Object.keys(map))assert.equal(questions.filter(q=>q.topic===topic).length,3);
const rows=items=>items.join(",\n  ");
const sourceIds=[...sources.values()].map(s=>literal(s.dbId)+"::uuid").join(",");
const questionIds=questions.map(q=>literal(q.id)).join(",");
const versionIds=questions.map(q=>literal(q.dbId)+"::uuid").join(",");
const links=questions.flatMap(q=>q.sourceIds.map(s=>"("+literal(q.dbId)+","+literal(sources.get(s).dbId)+")"));
const sql=[
"-- 사용자 검토가 끝난 기본 금융 30문항 등록용입니다. 기존 데이터는 수정하지 않습니다.",
"-- DRAFT 등록 → 정답·출처 연결 → APPROVED 전환을 한 트랜잭션에서 수행합니다.",
"-- 원본 파일 SHA-256: "+createHash("sha256").update(raw).digest("hex"),
"-- A/B/C/D → 1/2/3/4, 한국어 주제 → API 코드, BASIC·학습 단계 1로 변환합니다.",
"-- retrieved_at은 이 파일의 DB 등록 시각입니다. 출처 원문을 새로 조회한 시각이 아닙니다.",
"-- 원본의 publisher·scope·검토 상태 등 전체 메타데이터는 seed-data JSON에 보존합니다.",
"begin;",
"set local lock_timeout='5s';",
"set local statement_timeout='120s';",
"select pg_advisory_xact_lock(hashtextextended('finpop-basic-finance-30-import-v1',0));",
"do $guard$ begin",
" if exists(select 1 from public.quiz_questions where id in ("+questionIds+")) then",
"   raise exception '기본 문항 ID가 이미 있습니다. 덮어쓰거나 중복 등록하지 않습니다.'; end if;",
" if exists(select 1 from public.quiz_sources where id in ("+sourceIds+")) then",
"   raise exception '출처 ID가 이미 있습니다. 등록 상태를 먼저 확인하세요.'; end if;",
"end $guard$;",
"insert into public.quiz_sources(id,title,url,published_at) values\n  "+rows([...sources.values()].map(s=>"("+literal(s.dbId)+","+literal(s.publisher+" · "+s.title)+","+literal(s.url)+",null)"))+";",
"insert into public.quiz_questions(id,topic,source_type) values\n  "+rows(questions.map(q=>"("+literal(q.id)+","+literal(q.code)+",'BASIC')"))+";",
"insert into public.quiz_question_versions(id,question_id,version,stage,question,hint,choices) values\n  "+rows(questions.map(q=>"("+literal(q.dbId)+","+literal(q.id)+",1,1,"+literal(q.question)+","+literal(q.hint)+","+literal(JSON.stringify(q.options.map(o=>({id:letters[o.id],text:o.text}))))+"::jsonb)"))+";",
"insert into finpop_private.quiz_answer_keys(question_version_id,correct_choice_id,explanation) values\n  "+rows(questions.map(q=>"("+literal(q.dbId)+","+letters[q.correctOptionId]+","+literal(q.explanation)+")"))+";",
"insert into public.quiz_version_sources(question_version_id,source_id) values\n  "+rows(links)+";",
"update public.quiz_question_versions set review_status='APPROVED' where id in ("+versionIds+");",
"do $verify$ begin",
" if (select count(*) from public.quiz_question_versions where id in ("+versionIds+") and review_status='APPROVED')<>30 then",
"   raise exception '승인 문항 수가 30개가 아닙니다.'; end if;",
" if (select count(*) from finpop_private.quiz_answer_keys where question_version_id in ("+versionIds+"))<>30 then",
"   raise exception '정답 수가 30개가 아닙니다.'; end if;",
" if (select count(*) from public.quiz_version_sources where question_version_id in ("+versionIds+"))<>"+links.length+" then",
"   raise exception '출처 연결 수가 원본과 다릅니다.'; end if;",
"end $verify$;",
"select q.topic,v.review_status,count(*) as question_count from public.quiz_questions q",
"join public.quiz_question_versions v on v.question_id=q.id",
"where v.id in ("+versionIds+") group by q.topic,v.review_status order by q.topic;",
"commit;",
].join("\n")+"\n";
await writeFile(output,sql,"utf8");
console.log(JSON.stringify({questions:questions.length,sources:sources.size,sourceLinks:links.length,status:"APPROVED",stage:1,output:output.pathname}));

