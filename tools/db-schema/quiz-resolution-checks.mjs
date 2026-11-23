import assert from "node:assert/strict";
import {readFile} from "node:fs/promises";

export async function validateQuizResolution({db,test,rpc,fixedDice,ids}) {
 await db.exec(await readFile(new URL("../../docs/api/game-quiz-resolution-update.sql",import.meta.url),"utf8"));
 async function prepare({actor="P1",owner=null,balance=730,expired=false}={}){
  // 매 시나리오는 별도 로컬 게임으로 시작합니다. 원격 프로젝트는 사용하지 않습니다.
  await db.exec("truncate public.game_sessions cascade");
  const created=await rpc("create",null,{mode:"AI",characterId:"CHEESE"});
  assert.equal(created.httpStatus,200);
  const gameId=created.body.data.gameId;
  const turn=(await db.query("insert into public.game_turns(game_id,player_id) values($1,$2) returning id",[gameId,actor])).rows[0].id;
  await db.query("update public.game_sessions set phase='TURN_READY',state_version=2,current_player_id=$2,current_turn_id=$3 where id=$1",[gameId,actor,turn]);
  if(owner)await db.query("update public.game_tiles set owner_player_id=$2 where game_id=$1 and tile_id=1",[gameId,owner]);
  await fixedDice(1);
  const moved=await rpc(actor==="P1"?"roll":"ai-roll",gameId,{expectedVersion:2});
  assert.equal(moved.httpStatus,200);
  const attempt=moved.body.data.game.pendingQuiz.attemptId;
  await db.query("update public.game_players set game_gold=$3 where game_id=$1 and player_id=$2",[gameId,actor,balance]);
  await db.query("update public.game_quiz_attempts set issued_at=clock_timestamp()+$2::interval,deadline_at=clock_timestamp()+$3::interval where id=$1",
   [attempt,expired?"-30 seconds":"0 seconds",expired?"-10 seconds":"20 seconds"]);
  const body={expectedVersion:3,attemptId:attempt};
  const invoke=(op,extra={},key=crypto.randomUUID(),user=ids.u1)=>rpc(op,gameId,{...body,...extra},key,user);
  const get=async()=> (await rpc("get",gameId,{},null)).body.data;
  return {gameId,attempt,body,invoke,get};
 }
 await test("판정 함수는 앱 역할에 공개하지 않음",async()=>{
  for(const signature of ["finpop_private.game_quiz_result(uuid)","finpop_private.quiz_ai_choice(integer)","finpop_private.resolve_game_quiz(uuid,text,uuid,integer,boolean,boolean)"])
   for(const role of ["anon","authenticated"])
    assert.equal((await db.query("select has_function_privilege($1,$2,'EXECUTE') allowed",[role,signature])).rows[0].allowed,false);
 });
 await test("정답은 구매 대기로 전환하고 재조회 결과 보존",async()=>{
  const f=await prepare(); const result=await f.invoke("answer",{choiceId:2});
  assert.equal(result.httpStatus,200);assert.equal(result.body.data.result.resolution,"CORRECT");
  assert.equal(result.body.data.game.phase,"PURCHASE_PENDING");
  assert.equal(result.body.data.game.pendingPurchase.price,190);
  assert.equal(result.body.data.game.players[0].remainingTurns,7);
  assert.equal((await f.get()).lastQuizResult.correctChoiceId,2);
  assert.equal((await f.get()).pendingQuiz,null);
 });
 await test("오답은 구매 없이 차례 마무리 대기",async()=>{
  const f=await prepare();const result=await f.invoke("answer",{choiceId:1});
  assert.equal(result.body.data.result.resolution,"INCORRECT");
  assert.equal(result.body.data.game.phase,"TURN_REVIEW");assert.equal(result.body.data.game.pendingPurchase,null);
 });
 await test("시간 전 조기 시간초과는 거절하고 상태 유지",async()=>{
  const f=await prepare();const before=await f.get();
  assert.equal((await f.invoke("quiz-timeout")).body.error.code,"QUIZ_NOT_EXPIRED");
  assert.deepEqual(await f.get(),before);
 });
 await test("서버 기한 경과 후 정답 제출도 TIMEOUT 처리",async()=>{
  const f=await prepare({expired:true});const result=await f.invoke("answer",{choiceId:2});
  assert.equal(result.body.data.result.resolution,"TIMEOUT");assert.equal(result.body.data.result.selectedChoiceId,null);
  assert.equal(result.body.data.result.remainingMillis,0);assert.equal(result.body.data.game.phase,"TURN_REVIEW");
 });
 await test("명시적 시간초과와 앱 재시작 후 기한 확인",async()=>{
  const f=await prepare({expired:true});const result=await f.invoke("quiz-timeout");
  assert.equal(result.httpStatus,200);assert.equal(result.body.data.result.resolution,"TIMEOUT");
  assert.equal((await f.get()).lastQuizResult.resolution,"TIMEOUT");
 });
 await test("같은 답변 요청은 재사용하며 다른 답변·새 키 중복 차단",async()=>{
  const f=await prepare();const key=crypto.randomUUID();
  const first=await f.invoke("answer",{choiceId:2},key);
  assert.deepEqual(await f.invoke("answer",{choiceId:2},key),first);
  assert.equal((await f.invoke("answer",{choiceId:1},key)).body.error.code,"IDEMPOTENCY_CONFLICT");
  assert.equal((await f.invoke("answer",{choiceId:1,expectedVersion:4})).body.error.code,"INVALID_PHASE");
  assert.equal((await f.get()).stateVersion,4);
 });
 await test("다른 문항·타인 접근·오래된 상태·잘못된 차례 거절",async()=>{
  const f=await prepare();
  assert.equal((await f.invoke("answer",{choiceId:2,attemptId:crypto.randomUUID()})).body.error.code,"QUIZ_NOT_ACTIVE");
  assert.equal((await f.invoke("answer",{choiceId:2},crypto.randomUUID(),ids.u2)).httpStatus,403);
  assert.equal((await f.invoke("answer",{choiceId:2,expectedVersion:2})).body.error.code,"STATE_CONFLICT");
  assert.equal((await f.invoke("ai-answer")).body.error.code,"NOT_YOUR_TURN");
 });
 await test("잘못된 선택지·시간·정답 주입 차단",async()=>{
  const f=await prepare();
  for(const extra of [{choiceId:0},{choiceId:5},{choiceId:1.5},{choiceId:2,remainingMillis:20000},{choiceId:2,correctChoiceId:2}]){
   assert.equal((await f.invoke("answer",extra)).httpStatus,400);
  }
  assert.equal((await f.get()).phase,"QUIZ_OPEN");
 });
 for(const [choice,resolution,amount] of [[2,"CORRECT",95],[1,"INCORRECT",190]]){
  await test("상대 칸 "+resolution+" 방문 정산 원자 처리·음수 잔액",async()=>{
   const f=await prepare({owner:"P2",balance:10});const key=crypto.randomUUID();
   const result=await f.invoke("answer",{choiceId:choice},key);
   assert.equal(result.body.data.result.payment.amount,amount);
   const state=await f.get();
   assert.equal(state.players[0].gameGold,10-amount);assert.equal(state.players[1].gameGold,700+amount);
   assert.equal(state.phase,"TURN_REVIEW");
   await f.invoke("answer",{choiceId:choice},key);assert.deepEqual(await f.get(),state);
  });
 }
 await test("시간초과도 상대 칸 전액 정산",async()=>{
  const f=await prepare({owner:"P2",expired:true});
  assert.equal((await f.invoke("quiz-timeout")).body.data.result.payment.amount,190);
 });
 await test("AI 정답은 서버 선택만 사용하고 구매 대기",async()=>{
  const f=await prepare({actor:"P2"});
  await db.exec("create or replace function finpop_private.quiz_ai_choice(p_correct integer) returns integer language sql as $$ select 2 $$");
  const result=await f.invoke("ai-answer");
  assert.equal(result.httpStatus,200);assert.equal(result.body.data.result.playerId,"P2");
  assert.equal(result.body.data.result.resolution,"CORRECT");assert.equal(result.body.data.game.phase,"PURCHASE_PENDING");
 });
 await test("AI 오답 방문 정산은 AI 잔액에서만 출금",async()=>{
  const f=await prepare({actor:"P2",owner:"P1"});
  await db.exec("create or replace function finpop_private.quiz_ai_choice(p_correct integer) returns integer language sql as $$ select 1 $$");
  const result=await f.invoke("ai-answer");
  assert.equal(result.body.data.result.resolution,"INCORRECT");
  assert.equal(result.body.data.game.players[1].gameGold,540);
  assert.equal(result.body.data.game.players[0].gameGold,890);
 });
 await test("오래된 AI 퀴즈는 확률 추첨 없이 TIMEOUT",async()=>{
  const f=await prepare({actor:"P2",expired:true});
  assert.equal((await f.invoke("ai-answer")).body.data.result.resolution,"TIMEOUT");
 });

 await test("수개월 지난 퀴즈도 정수 범위 오류 없이 TIMEOUT",async()=>{
  const f=await prepare();
  await db.query("update public.game_quiz_attempts set issued_at=clock_timestamp()-interval '90 days',deadline_at=clock_timestamp()-interval '90 days'+interval '20 seconds' where id=$1",[f.attempt]);
  const result=await f.invoke("quiz-timeout");
  assert.equal(result.httpStatus,200);assert.equal(result.body.data.result.remainingMillis,0);
 });
 await test("퀴즈 업데이트 재적용은 진행 중 문제·잔액·권한 보존",async()=>{
  const f=await prepare();const before=await f.get();
  await db.exec(await readFile(new URL("../../docs/api/game-quiz-resolution-update.sql",import.meta.url),"utf8"));
  assert.deepEqual(await f.get(),before);
  assert.equal((await db.query("select has_function_privilege('authenticated','public.finpop_game_action(uuid,text,uuid,uuid,jsonb)','EXECUTE') allowed")).rows[0].allowed,false);
 });
}