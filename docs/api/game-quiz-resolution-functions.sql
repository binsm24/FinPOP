-- 판정된 현재 차례의 결과만 반환합니다. 미응답 문항의 정답은 노출하지 않습니다.
create or replace function finpop_private.game_quiz_result(p_game_id uuid) returns jsonb
language sql stable security invoker set search_path='' as $$
select jsonb_build_object(
 'attemptId',a.id,'playerId',a.player_id,'resolution',a.resolution,
 'selectedChoiceId',a.selected_choice_id,'correctChoiceId',s.correct_choice_id,
 'question',a.question,'choices',a.choices,'explanation',s.explanation,'sources',s.source,
 'remainingMillis',a.remaining_millis,'resolvedAt',a.resolved_at)
from public.game_sessions g join public.game_quiz_attempts a
 on a.game_id=g.id and a.turn_id=g.current_turn_id
join finpop_private.game_quiz_answers s on s.attempt_id=a.id
where g.id=p_game_id and a.resolved_at is not null
order by a.resolved_at desc limit 1;
$$;

-- 0~9를 균등 추첨해 70%는 정답, 나머지는 유효한 오답을 고릅니다.
create or replace function finpop_private.quiz_ai_choice(p_correct integer) returns integer
language plpgsql volatile security invoker set search_path='' as $$
declare n bigint;
begin
 loop
  n:=('x'||left(replace(gen_random_uuid()::text,'-',''),8))::bit(32)::bigint;
  exit when n<4294967290;
 end loop;
 return case when n%10<7 then p_correct when p_correct=1 then 2 else 1 end;
end;
$$;

-- 게임 행 잠금과 인증·버전 검사는 상위 RPC에서 처리합니다.
create or replace function finpop_private.resolve_game_quiz(
 p_game_id uuid,p_actor text,p_attempt uuid,p_choice integer,p_timeout_only boolean,p_ai boolean
) returns jsonb language plpgsql security invoker set search_path='' as $$
declare
 g public.game_sessions%rowtype; a public.game_quiz_attempts%rowtype;
 s finpop_private.game_quiz_answers%rowtype;
 v_now timestamptz:=clock_timestamp(); v_choice integer; v_resolution text;
 v_owner text; v_price integer; v_payment integer:=0; v_purchase jsonb;
begin
 select * into g from public.game_sessions where id=p_game_id;
 if g.phase<>'QUIZ_OPEN' or g.current_player_id is distinct from p_actor then
  perform finpop_private.game_fail('INVALID_PHASE');
 end if;
 select * into a from public.game_quiz_attempts
  where id=p_attempt and game_id=g.id and turn_id=g.current_turn_id and player_id=p_actor for update;
 if not found or a.resolved_at is not null then perform finpop_private.game_fail('QUIZ_NOT_ACTIVE'); end if;
 select * into s from finpop_private.game_quiz_answers where attempt_id=a.id;
 if not found then perform finpop_private.game_fail('INVALID_QUIZ'); end if;
 if v_now>=a.deadline_at then
  v_resolution:='TIMEOUT';v_choice:=null;
 elsif p_timeout_only then
  perform finpop_private.game_fail('QUIZ_NOT_EXPIRED');
 else
  v_choice:=case when p_ai then finpop_private.quiz_ai_choice(s.correct_choice_id) else p_choice end;
  if v_choice is null or not exists(select 1 from jsonb_array_elements(a.choices) c
    where c->>'id'=v_choice::text) then perform finpop_private.game_fail('INVALID_REQUEST'); end if;
  v_resolution:=case when v_choice=s.correct_choice_id then 'CORRECT' else 'INCORRECT' end;
 end if;
 select owner_player_id into v_owner from public.game_tiles where game_id=g.id and tile_id=a.tile_id for update;
 select (t->>'price')::integer into v_price
  from jsonb_array_elements(finpop_private.game_config()->'tiles') t where (t->>'tileId')::integer=a.tile_id;
 if v_owner is null and v_resolution='CORRECT' then
  v_purchase:=jsonb_build_object('tileId',a.tile_id,'price',v_price,'playerId',p_actor);
 elsif v_owner is not null and v_owner<>p_actor then
  v_payment:=case when v_resolution='CORRECT' then v_price/2 else v_price end;
  -- 양쪽 잔액을 한 트랜잭션에서 갱신하며 음수 잔액도 허용합니다.
  update public.game_players set game_gold=game_gold+
   case when player_id=p_actor then -v_payment else v_payment end
   where game_id=g.id and player_id in (p_actor,v_owner);
 end if;
 update public.game_quiz_attempts set selected_choice_id=v_choice,resolution=v_resolution,resolved_at=v_now,
  remaining_millis=greatest(0,least(20000,floor(extract(epoch from (deadline_at-v_now))*1000)))::integer
  where id=a.id;
 update public.game_sessions set phase=case when v_purchase is not null then 'PURCHASE_PENDING' else 'TURN_REVIEW' end,
  pending_purchase=v_purchase,state_version=state_version+1,updated_at=v_now where id=g.id;
 return finpop_private.game_quiz_result(g.id)||jsonb_build_object('payment',
  case when v_payment>0 then jsonb_build_object('fromPlayerId',p_actor,'toPlayerId',v_owner,'amount',v_payment) else null end);
end;
$$;
revoke all on function finpop_private.game_quiz_result(uuid),finpop_private.quiz_ai_choice(integer),
 finpop_private.resolve_game_quiz(uuid,text,uuid,integer,boolean,boolean) from public,anon,authenticated;
grant execute on function finpop_private.game_quiz_result(uuid),finpop_private.quiz_ai_choice(integer),
 finpop_private.resolve_game_quiz(uuid,text,uuid,integer,boolean,boolean) to service_role;
