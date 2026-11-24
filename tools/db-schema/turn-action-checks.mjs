import assert from "node:assert/strict";
import {readFile} from "node:fs/promises";

export async function validateTurnActions({db,test,rpc,fixedDice,ids}) {
 const upgrade=await readFile(new URL("../../docs/api/game-turn-update.sql",import.meta.url),"utf8");
 await db.exec(upgrade);
 async function prepare({actor="P1",correct=true,balance=730}={}){
  await db.exec("truncate public.game_sessions cascade");
  const created=await rpc("create",null,{mode:"AI",characterId:"CHEESE"});
  const id=created.body.data.gameId;
  const turn=(await db.query("insert into public.game_turns(game_id,player_id) values($1,$2) returning id",[id,actor])).rows[0].id;
  await db.query("update public.game_sessions set phase='TURN_READY',state_version=2,current_player_id=$2,current_turn_id=$3 where id=$1",[id,actor,turn]);
  await fixedDice(1);
  const moved=await rpc(actor==="P1"?"roll":"ai-roll",id,{expectedVersion:2});
  assert.equal(moved.httpStatus,200);
  const attempt=moved.body.data.game.pendingQuiz.attemptId;
  await db.query("update public.game_quiz_attempts set issued_at=clock_timestamp(),deadline_at=clock_timestamp()+interval '20 seconds' where id=$1",[attempt]);
  if(actor==="P2")await db.exec("create or replace function finpop_private.quiz_ai_choice(p_correct integer) returns integer language sql as $$ select "+(correct?2:1)+" $$");
  const answered=await rpc(actor==="P1"?"answer":"ai-answer",id,{expectedVersion:3,attemptId:attempt,...(actor==="P1"?{choiceId:correct?2:1}:{})});
  assert.equal(answered.httpStatus,200);
  await db.query("update public.game_players set game_gold=$3 where game_id=$1 and player_id=$2",[id,actor,balance]);
  const invoke=(op,body,key=crypto.randomUUID(),user=ids.u1)=>rpc(op,id,body,key,user);
  const get=async()=>(await rpc("get",id,{},null)).body.data;
  return {id,turn,invoke,get};
 }
 await test("구매는 잔액·소유권만 갱신하고 차례 종료 전 턴 유지",async()=>{
  const f=await prepare();const key=crypto.randomUUID();
  const first=await f.invoke("purchase",{expectedVersion:4,tileId:1,action:"BUY"},key);
  assert.equal(first.httpStatus,200);
  assert.equal(first.body.data.game.players[0].gameGold,540);
  assert.equal(first.body.data.game.players[0].remainingTurns,7);
  assert.equal(first.body.data.game.tiles[1].ownerPlayerId,"P1");
  assert.equal(first.body.data.game.phase,"TURN_REVIEW");
  assert.deepEqual(await f.invoke("purchase",{expectedVersion:4,tileId:1,action:"BUY"},key),first);
  assert.equal((await f.invoke("purchase",{expectedVersion:5,tileId:1,action:"BUY"})).body.error.code,"PURCHASE_NOT_AVAILABLE");
 });
 await test("패스는 잔액·소유권을 유지",async()=>{
  const f=await prepare();
  const r=await f.invoke("purchase",{expectedVersion:4,tileId:1,action:"PASS"});
  assert.equal(r.body.data.purchaseEvent.action,"PASS");
  assert.equal(r.body.data.game.players[0].gameGold,730);assert.equal(r.body.data.game.tiles[1].ownerPlayerId,null);
 });
 await test("잔액 부족 구매는 롤백하고 패스 가능",async()=>{
  const f=await prepare({balance:100});const before=await f.get();
  assert.equal((await f.invoke("purchase",{expectedVersion:4,tileId:1,action:"BUY"})).body.error.code,"INSUFFICIENT_GOLD");
  assert.deepEqual(await f.get(),before);
  assert.equal((await f.invoke("purchase",{expectedVersion:4,tileId:1,action:"PASS"})).httpStatus,200);
 });
 await test("구매 위조·다른 칸·다른 차례·타인·오래된 버전 거절",async()=>{
  const f=await prepare();
  assert.equal((await f.invoke("purchase",{expectedVersion:4,tileId:2,action:"BUY"})).body.error.code,"PURCHASE_NOT_AVAILABLE");
  assert.equal((await f.invoke("purchase",{expectedVersion:4,tileId:1,action:"BUY",price:1})).httpStatus,400);
  assert.equal((await f.invoke("purchase",{expectedVersion:3,tileId:1,action:"BUY"})).body.error.code,"STATE_CONFLICT");
  assert.equal((await f.invoke("ai-purchase",{expectedVersion:4,tileId:1})).body.error.code,"NOT_YOUR_TURN");
  assert.equal((await f.invoke("purchase",{expectedVersion:4,tileId:1,action:"BUY"},crypto.randomUUID(),ids.u2)).httpStatus,403);
 });
 await test("구매 미결 상태에서는 차례 종료 불가",async()=>{
  const f=await prepare();
  assert.equal((await f.invoke("complete-turn",{expectedVersion:4,turnId:f.turn})).body.error.code,"TURN_NOT_READY_TO_COMPLETE");
 });
 await test("차례 종료는 1턴만 차감·다음 참가자와 열린 차례 생성",async()=>{
  const f=await prepare({correct:false});const key=crypto.randomUUID();
  const first=await f.invoke("complete-turn",{expectedVersion:4,turnId:f.turn},key);
  assert.equal(first.httpStatus,200);
  assert.equal(first.body.data.game.players[0].remainingTurns,6);
  assert.equal(first.body.data.game.players[1].remainingTurns,7);
  assert.equal(first.body.data.game.currentPlayerId,"P2");assert.equal(first.body.data.game.phase,"TURN_READY");
  assert.notEqual(first.body.data.game.currentTurnId,f.turn);
  assert.equal((await db.query("select count(*)::int n from public.game_turns where game_id=$1 and completed_at is null",[f.id])).rows[0].n,1);
  assert.deepEqual(await f.invoke("complete-turn",{expectedVersion:4,turnId:f.turn},key),first);
  assert.equal((await f.invoke("ai-complete-turn",{expectedVersion:5,turnId:f.turn})).body.error.code,"TURN_ALREADY_COMPLETED");
 });
 await test("AI 구매는 클라이언트 선택 없이 서버 잔액으로 결정",async()=>{
  const f=await prepare({actor:"P2"});
  const buy=await f.invoke("ai-purchase",{expectedVersion:4,tileId:1});
  assert.equal(buy.body.data.purchaseEvent.action,"BUY");assert.equal(buy.body.data.game.tiles[1].ownerPlayerId,"P2");
  assert.equal(buy.body.data.game.players[1].gameGold,540);
  const end=await f.invoke("ai-complete-turn",{expectedVersion:5,turnId:f.turn});
  assert.equal(end.body.data.game.currentPlayerId,"P1");assert.equal(end.body.data.game.players[1].remainingTurns,6);
 });
 await test("AI 잔액 부족이면 자동 패스",async()=>{
  const f=await prepare({actor:"P2",balance:100});
  assert.equal((await f.invoke("ai-purchase",{expectedVersion:4,tileId:1})).body.data.purchaseEvent.action,"PASS");
 });
 await test("양쪽 마지막 턴 종료 시 FINISHED·계정 보상은 미변경",async()=>{
  const f=await prepare({correct:false});
  await db.query("update public.game_players set remaining_turns=case when player_id='P1' then 1 else 0 end where game_id=$1",[f.id]);
  const before=(await db.query("select wallet_gold,completed_game_count,total_points from public.users where id=$1",[ids.u1])).rows[0];
  const end=await f.invoke("complete-turn",{expectedVersion:4,turnId:f.turn});
  const g=end.body.data.game;
  assert.equal(g.phase,"FINISHED");assert.equal(g.status,"FINISHED");assert.equal(g.currentPlayerId,null);
  assert.equal(g.currentTurnId,null);assert.ok(g.players.every(p=>p.remainingTurns===0));
  assert.deepEqual((await db.query("select wallet_gold,completed_game_count,total_points from public.users where id=$1",[ids.u1])).rows[0],before);
  assert.equal((await f.invoke("complete-turn",{expectedVersion:5,turnId:f.turn})).body.error.code,"GAME_NOT_ACTIVE");
 });
 await test("상대 턴이 소진됐으면 남은 참가자가 계속 진행",async()=>{
  const f=await prepare({correct:false});
  await db.query("update public.game_players set remaining_turns=0 where game_id=$1 and player_id='P2'",[f.id]);
  const end=await f.invoke("complete-turn",{expectedVersion:4,turnId:f.turn});
  assert.equal(end.body.data.game.currentPlayerId,"P1");assert.equal(end.body.data.game.status,"ACTIVE");
 });
 await test("무인도 대기는 이동·보상 없이 한 턴 소비·대기 해제",async()=>{
  const f=await prepare({correct:false});
  await db.query("update public.game_sessions set phase='TURN_READY' where id=$1",[f.id]);
  await db.query("update public.game_turns set dice_value=null,movement_path=null where id=$1",[f.turn]);
  await db.query("update public.game_players set position=7,island_wait_turns=1 where game_id=$1 and player_id='P1'",[f.id]);
  const r=await f.invoke("island-skip",{expectedVersion:4,turnId:f.turn});
  assert.equal(r.httpStatus,200);const p=r.body.data.game.players[0];
  assert.equal(p.position,7);assert.equal(p.gameGold,730);assert.equal(p.remainingTurns,6);assert.equal(p.islandWaitTurns,0);
  assert.equal(r.body.data.game.currentPlayerId,"P2");assert.equal(r.body.data.turnResult.skippedIsland,true);
 });
 await test("일반 차례를 무인도 대기로 강제 종료할 수 없음",async()=>{
  const f=await prepare({correct:false});
  assert.equal((await f.invoke("island-skip",{expectedVersion:4,turnId:f.turn})).body.error.code,"INVALID_PHASE");
 });
 await test("턴 업데이트 재적용은 진행 중 구매·게임·권한 보존",async()=>{
  const f=await prepare();const before=await f.get();await db.exec(upgrade);
  assert.deepEqual(await f.get(),before);
  for(const signature of ["finpop_private.purchase_game_tile(uuid,text,integer,text,boolean)","finpop_private.complete_game_turn(uuid,text,uuid,boolean)"])
   assert.equal((await db.query("select has_function_privilege('authenticated',$1,'EXECUTE') allowed",[signature])).rows[0].allowed,false);
 });

 await test("사용자 종료→AI 이동·종료→사용자 차례 순환",async()=>{
  const f=await prepare({correct:false});
  const userEnd=await f.invoke("complete-turn",{expectedVersion:4,turnId:f.turn});
  assert.equal(userEnd.body.data.game.currentPlayerId,"P2");
  await db.query("update public.game_players set position=13 where game_id=$1 and player_id='P2'",[f.id]);
  await fixedDice(1);
  const aiMove=await f.invoke("ai-roll",{expectedVersion:5});
  assert.equal(aiMove.body.data.game.phase,"TURN_REVIEW");
  const aiEnd=await f.invoke("ai-complete-turn",{expectedVersion:6,turnId:aiMove.body.data.game.currentTurnId});
  assert.equal(aiEnd.body.data.game.currentPlayerId,"P1");assert.equal(aiEnd.body.data.game.phase,"TURN_READY");
  assert.deepEqual(aiEnd.body.data.game.players.map(p=>p.remainingTurns),[6,6]);
 });
}