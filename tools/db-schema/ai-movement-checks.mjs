import assert from "node:assert/strict";

export async function validateAiMovement({db,test,rpc,fixedDice,ids}) {
 let gameId,first,moved;
 const key=crypto.randomUUID();
 await db.query("insert into public.user_characters(user_id,character_id) values($1,'CHEESE')",[ids.u2]);
 const invoke=(op,body={},requestKey=crypto.randomUUID(),user=ids.u2)=>rpc(op,gameId,body,requestKey,user);
 await db.exec("create sequence finpop_private.ai_test_dice; grant usage on sequence finpop_private.ai_test_dice to service_role; create or replace function finpop_private.roll_die() returns integer language sql volatile security invoker set search_path='' as $$ select case nextval('finpop_private.ai_test_dice') when 1 then 1 else 6 end $$");
 await test("AI 선공 게임도 동일한 생성·선공 규칙 사용",async()=>{
  const created=await rpc("create",null,{mode:"AI",characterId:"CHEESE"},crypto.randomUUID(),ids.u2);
  assert.equal(created.httpStatus,200);gameId=created.body.data.gameId;
  first=await invoke("initiative",{expectedVersion:1});
  assert.equal(first.body.data.firstPlayerId,"P2");
 });
 await test("AI 차례에 사용자 이동 요청 거절",async()=>{
  assert.equal((await invoke("roll",{expectedVersion:2})).body.error.code,"NOT_YOUR_TURN");
 });
 await test("AI 요청 타인 게임 접근 거절",async()=>{
  assert.equal((await invoke("ai-roll",{expectedVersion:2},crypto.randomUUID(),ids.u1)).httpStatus,403);
 });
 await test("AI 요청 오래된 상태·추가 입력 차단",async()=>{
  assert.equal((await invoke("ai-roll",{expectedVersion:1})).body.error.code,"STATE_CONFLICT");
  assert.equal((await invoke("ai-roll",{expectedVersion:2,diceValue:6})).httpStatus,400);
 });
 await fixedDice(1);
 await test("AI 이동·능력·퀴즈는 P2에만 저장",async()=>{
  moved=await invoke("ai-roll",{expectedVersion:2},key);
  assert.equal(moved.httpStatus,200);
  const data=moved.body.data;
  assert.equal(data.playerId,"P2");assert.equal(data.diceValue,1);assert.deepEqual(data.movementPath,[1]);
  assert.equal(data.game.phase,"QUIZ_OPEN");assert.equal(data.game.stateVersion,3);
  assert.equal(data.game.players[0].position,0);assert.equal(data.game.players[0].gameGold,700);
  assert.equal(data.game.players[1].position,1);assert.equal(data.game.players[1].gameGold,730);
  assert.equal(data.game.players[1].remainingTurns,7);
  assert.ok(data.events.every(e=>e.playerId==="P2"));
  const attempt=(await db.query("select player_id from public.game_quiz_attempts where game_id=$1",[gameId])).rows[0];
  assert.equal(attempt.player_id,"P2");
  assert.ok(!JSON.stringify(data).includes("correctChoiceId"));
 });
 await test("AI 이동 재시도 동일 응답·추가 이동 차단",async()=>{
  assert.deepEqual(await invoke("ai-roll",{expectedVersion:2},key),moved);
  assert.equal((await invoke("ai-roll",{expectedVersion:3})).body.error.code,"INVALID_PHASE");
  assert.equal((await invoke("ai-roll",{expectedVersion:3},key)).body.error.code,"IDEMPOTENCY_CONFLICT");
 });
 // 다음 검증을 위한 로컬 DB 준비이며 실제 API에 상태 강제 변경 기능을 제공하지 않습니다.
 await db.query("update public.game_quiz_attempts set resolution='TIMEOUT',resolved_at=clock_timestamp() where game_id=$1",[gameId]);
 await db.query("update public.game_turns set dice_value=null,movement_path=null where game_id=$1",[gameId]);
 await db.query("update public.game_sessions set phase='TURN_READY' where id=$1",[gameId]);
 await db.query("update public.game_players set position=13 where game_id=$1 and player_id='P2'",[gameId]);
 await test("AI 출발 통과 보상은 AI에만 지급",async()=>{
  const result=await invoke("ai-roll",{expectedVersion:3});
  assert.equal(result.httpStatus,200);assert.equal(result.body.data.game.players[1].gameGold,960);
  assert.equal(result.body.data.game.players[0].gameGold,700);
  assert.equal(result.body.data.game.phase,"TURN_REVIEW");
  assert.equal(result.body.data.events.find(e=>e.type==="START_REWARD").playerId,"P2");
 });
 await db.query("update public.game_turns set dice_value=null,movement_path=null where game_id=$1",[gameId]);
 await db.query("update public.game_sessions set phase='TURN_READY' where id=$1",[gameId]);
 await db.query("update public.game_players set position=0 where game_id=$1 and player_id='P2'",[gameId]);
 await fixedDice(3);
 await test("AI 황금열쇠 미구현 실패도 이동·보상 원자 롤백",async()=>{
  const before=await invoke("get",{},null);
  assert.equal((await invoke("ai-roll",{expectedVersion:4})).body.error.code,"EVENT_RULES_UNAVAILABLE");
  const after=await invoke("get",{},null);
  assert.deepEqual(after.body.data,before.body.data);
 });
 await db.query("update public.game_sessions set current_turn_id=null,current_player_id='P1' where id=$1",[gameId]);
 await test("사용자 차례에서 AI 이동 요청 거절",async()=>{
  assert.equal((await invoke("ai-roll",{expectedVersion:4})).body.error.code,"NOT_YOUR_TURN");
 });
}
