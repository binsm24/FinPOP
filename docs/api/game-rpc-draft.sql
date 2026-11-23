-- 검토용 서버 함수 초안입니다. game-db-draft.sql 적용 후 테스트 DB에서 검증합니다.
begin;

-- 문항은 quiz-db-draft.sql의 정규화된 저장소에서 조회합니다.

create function finpop_private.game_config() returns jsonb
language sql immutable security invoker set search_path='' as $$
select jsonb_build_object(
  'rulesVersion','1.0','initialGold',700,'turnsPerPlayer',7,'quizTimeLimitSeconds',20,
  'startRewardGold',200,'islandWaitTurns',1,'diceMin',1,'diceMax',6,
  'tiles',(select jsonb_agg(jsonb_build_object('tileId',id,'topic',topic,'price',price) order by id)
    from (values (0,'START',0),(1,'STOCK',190),(2,'BOND',160),(3,'GOLDEN_KEY',0),
      (4,'MATURITY',200),(5,'COMPOUND',220),(6,'DIVIDEND',240),(7,'ISLAND',0),
      (8,'DEPOSIT',120),(9,'SAVINGS',150),(10,'GOLDEN_KEY',0),
      (11,'LOAN',180),(12,'ASSET',210),(13,'FUND',170)) t(id,topic,price)),
  'eventTypes',jsonb_build_array('STEAL_TILE','GAIN_300','GAIN_200','MOVE_ISLAND','RETURN_TILE','MOVE_START'),
  'characterUpgradeCosts',jsonb_build_array(100,200,300,400),
  'ai',jsonb_build_object('correctProbability',0.7,'characterId','CHEESE','characterLevel',1));
$$;

-- UUID 생성기의 무작위 32비트를 사용하고 나머지 연산의 편향을 제거합니다.
-- 테스트에서는 이 비공개 함수만 고정 값으로 교체합니다. API에 난수 주입 인자는 없습니다.
create function finpop_private.roll_die() returns integer
language plpgsql volatile security invoker set search_path='' as $$
declare n bigint;
begin
  loop
    n := ('x'||left(replace(gen_random_uuid()::text,'-',''),8))::bit(32)::bigint;
    if n < 4294967292 then return (n % 6)::integer + 1; end if;
  end loop;
end;
$$;

create function finpop_private.game_snapshot(p_game_id uuid) returns jsonb
language sql stable security invoker set search_path='' as $$
select jsonb_build_object(
 'gameId',g.id,'mode',g.mode,'status',g.status,'phase',g.phase,
 'stateVersion',g.state_version,'rulesVersion',g.rules_version,'quizStage',g.quiz_stage,
 'currentPlayerId',g.current_player_id,'currentTurnId',g.current_turn_id,
 'players',(select coalesce(jsonb_agg(jsonb_build_object(
   'playerId',p.player_id,'isAi',p.is_ai,'characterId',p.character_id,
   'characterLevel',p.character_level,'position',p.position,'gameGold',p.game_gold,
   'remainingTurns',p.remaining_turns,'islandWaitTurns',p.island_wait_turns,
   'abilityState',p.ability_state) order by p.player_id),'[]'::jsonb)
   from public.game_players p where p.game_id=g.id),
 'tiles',(select coalesce(jsonb_agg(jsonb_build_object('tileId',t.tile_id,
   'ownerPlayerId',t.owner_player_id) order by t.tile_id),'[]'::jsonb)
   from public.game_tiles t where t.game_id=g.id),
 'pendingQuiz',(select jsonb_build_object('attemptId',q.id,'questionVersionId',q.question_version_id,
   'tileId',q.tile_id,'topic',q.topic,'sourceType',q.source_type,'stage',q.stage,
   'question',q.question,'hint',q.hint,'choices',q.choices,'issuedAt',q.issued_at,
   'deadlineAt',q.deadline_at) from public.game_quiz_attempts q
   where q.game_id=g.id and q.resolved_at is null),
 'pendingEvent',g.pending_event,'pendingPurchase',g.pending_purchase,'updatedAt',g.updated_at)
from public.game_sessions g where g.id=p_game_id;
$$;

create function finpop_private.game_fail(p_code text) returns void
language plpgsql security invoker set search_path='' as $$
begin raise exception using errcode='P0001',message=p_code; end;
$$;

-- service_role 전용 진입점입니다. Edge Function이 검증한 사용자 ID만 전달합니다.
-- 클라이언트는 이 함수를 직접 실행할 수 없습니다.
create function public.finpop_game_action(
 p_user_id uuid, p_operation text, p_game_id uuid default null,
 p_request_key uuid default null, p_body jsonb default '{}'::jsonb
) returns jsonb language plpgsql security invoker set search_path='' as $$
declare
 g public.game_sessions%rowtype;
 p public.game_players%rowtype;
 q record;
 cached finpop_private.game_action_requests%rowtype;
 v_game_id uuid := p_game_id;
 v_cache_game uuid;
 v_meta jsonb := jsonb_build_object('serverTime',clock_timestamp(),'requestId',gen_random_uuid());
 v_fingerprint jsonb := jsonb_build_object('operation',p_operation,'gameId',p_game_id,'body',p_body);
 v_data jsonb; v_envelope jsonb; v_status integer := 200; v_code text;
 v_mutation boolean := p_operation in ('create','initiative','roll','ai-roll');
 v_level integer; v_stage integer; v_user_level integer; v_completed integer;
 v_a integer; v_b integer; v_dice integer; v_position integer; v_bonus integer;
 v_path jsonb; v_rolls jsonb := '[]'::jsonb; v_events jsonb := '[]'::jsonb;
 v_actor text; v_first text; v_turn uuid; v_tile jsonb; v_owner text; v_topic text;
 v_attempt uuid; v_issued timestamptz; v_active uuid;
begin
 if p_user_id is null then
   return jsonb_build_object('httpStatus',401,'body',jsonb_build_object('error',
     jsonb_build_object('code','AUTH_REQUIRED','message','인증이 필요합니다.','details','{}'::jsonb),'meta',v_meta));
 end if;
 if not exists(select 1 from public.users where id=p_user_id) then
   return jsonb_build_object('httpStatus',409,'body',jsonb_build_object('error',
     jsonb_build_object('code','PROFILE_UNAVAILABLE','message','계정 초기화를 먼저 완료해 주세요.','details','{}'::jsonb),'meta',v_meta));
 end if;
 -- 사용자별 생성 경쟁과 요청 키 경쟁을 같은 트랜잭션에서 직렬화합니다.
 perform pg_advisory_xact_lock(hashtextextended(p_user_id::text,0));
 if v_mutation then
   if p_request_key is null then
     return jsonb_build_object('httpStatus',400,'body',jsonb_build_object('error',
       jsonb_build_object('code','INVALID_REQUEST','message','Idempotency-Key가 필요합니다.','details','{}'::jsonb),'meta',v_meta));
   end if;
   select * into cached from finpop_private.game_action_requests
     where user_id=p_user_id and request_key=p_request_key;
   if found then
     if cached.operation=p_operation and cached.request_body=v_fingerprint then
       return jsonb_build_object('httpStatus',cached.http_status,'body',cached.response_body);
     end if;
     return jsonb_build_object('httpStatus',409,'body',jsonb_build_object('error',
       jsonb_build_object('code','IDEMPOTENCY_CONFLICT','message','같은 키에 다른 요청을 사용할 수 없습니다.',
       'details','{}'::jsonb),'meta',v_meta));
   end if;
 end if;

 -- 이 블록의 모든 게임 변경은 오류 발생 시 함께 롤백됩니다.
 begin
   if p_body is null or jsonb_typeof(p_body)<>'object' then perform finpop_private.game_fail('INVALID_REQUEST'); end if;
   if p_operation='config' then
     if p_body<>'{}'::jsonb or p_game_id is not null then perform finpop_private.game_fail('INVALID_REQUEST'); end if;
     v_data:=finpop_private.game_config();
   elsif p_operation='create' then
     if (p_body - array['mode','characterId'])<>'{}'::jsonb or p_body->>'mode' is distinct from 'AI'
       or jsonb_typeof(p_body->'characterId') is distinct from 'string' or p_game_id is not null then
       perform finpop_private.game_fail('INVALID_REQUEST');
     end if;
     select id into v_active from public.game_sessions where user_id=p_user_id and status in ('ACTIVE','PAUSED');
     if found then perform finpop_private.game_fail('ACTIVE_GAME_EXISTS'); end if;
     select user_level,completed_game_count into v_user_level,v_completed from public.users
       where id=p_user_id for update;
     if v_completed<0 then perform finpop_private.game_fail('PROFILE_UNAVAILABLE'); end if;
     select uc.character_level into v_level from public.user_characters uc
       join public.characters c on c.id=uc.character_id
       where uc.user_id=p_user_id and uc.character_id=p_body->>'characterId'
         and c.unlock_user_level<=v_user_level and uc.character_level between 1 and least(c.max_level,5)
       for share of uc;
     if not found then perform finpop_private.game_fail('CHARACTER_LOCKED'); end if;
     v_stage:=1+v_completed/2;
     insert into public.game_sessions(user_id,quiz_stage) values(p_user_id,v_stage) returning id into v_game_id;
     insert into public.game_players(game_id,player_id,is_ai,character_id,character_level) values
       (v_game_id,'P1',false,p_body->>'characterId',v_level),(v_game_id,'P2',true,'CHEESE',1);
     insert into public.game_tiles(game_id,tile_id) select v_game_id,generate_series(0,13);
     v_data:=finpop_private.game_snapshot(v_game_id);
   elsif p_operation in ('get','initiative','roll','ai-roll') then
     if p_game_id is null then perform finpop_private.game_fail('INVALID_REQUEST'); end if;
     select * into g from public.game_sessions where id=p_game_id for update;
     if not found then perform finpop_private.game_fail('RESOURCE_NOT_FOUND'); end if;
     if g.user_id<>p_user_id then perform finpop_private.game_fail('FORBIDDEN'); end if;
     v_cache_game:=g.id;
     if p_operation='get' then
       if p_body<>'{}'::jsonb then perform finpop_private.game_fail('INVALID_REQUEST'); end if;
       v_data:=finpop_private.game_snapshot(g.id);
     else
       if (p_body - 'expectedVersion')<>'{}'::jsonb
         or jsonb_typeof(p_body->'expectedVersion') is distinct from 'number'
         or (p_body->>'expectedVersion') !~ '^[0-9]{1,10}$' then perform finpop_private.game_fail('INVALID_REQUEST'); end if;
       if (p_body->>'expectedVersion')::numeric<>g.state_version then perform finpop_private.game_fail('STATE_CONFLICT'); end if;
       if g.status<>'ACTIVE' then perform finpop_private.game_fail('GAME_NOT_ACTIVE'); end if;
       if g.rules_version<>'1.0' then perform finpop_private.game_fail('CONFIG_UNAVAILABLE'); end if;
       if p_operation='initiative' then
         if g.phase<>'INITIATIVE_PENDING' then perform finpop_private.game_fail('INITIATIVE_ALREADY_RESOLVED'); end if;
         for i in 1..1000 loop
           v_a:=finpop_private.roll_die(); v_b:=finpop_private.roll_die();
           v_rolls:=v_rolls||jsonb_build_array(jsonb_build_object('P1',v_a,'P2',v_b));
           exit when v_a<>v_b;
         end loop;
         if v_a=v_b then perform finpop_private.game_fail('DICE_UNAVAILABLE'); end if;
         v_first:=case when v_a>v_b then 'P1' else 'P2' end;
         insert into public.game_turns(game_id,player_id) values(g.id,v_first) returning id into v_turn;
         update public.game_sessions set current_player_id=v_first,current_turn_id=v_turn,phase='TURN_READY',
           state_version=state_version+1,updated_at=clock_timestamp() where id=g.id;
         v_data:=jsonb_build_object('rolls',v_rolls,'firstPlayerId',v_first,'game',finpop_private.game_snapshot(g.id));
       else
         v_actor:=case when p_operation='ai-roll' then 'P2' else 'P1' end;
         if g.mode<>'AI' or g.current_player_id is distinct from v_actor then perform finpop_private.game_fail('NOT_YOUR_TURN'); end if;
         if g.phase<>'TURN_READY' or g.current_turn_id is null then perform finpop_private.game_fail('INVALID_PHASE'); end if;
         select * into p from public.game_players where game_id=g.id and player_id=v_actor for update;
         if not found or p.is_ai is distinct from (v_actor='P2') then perform finpop_private.game_fail('INVALID_PHASE'); end if;
         if p.remaining_turns<=0 or p.island_wait_turns>0 then perform finpop_private.game_fail('INVALID_PHASE'); end if;
         -- 나머지 9종의 능력은 명세 구현 후 지원합니다. 미구현 능력을 생략하지 않습니다.
         if p.character_id<>'CHEESE' then perform finpop_private.game_fail('ABILITY_UNAVAILABLE'); end if;
         if exists(select 1 from public.game_turns where id=g.current_turn_id and
           (dice_value is not null or completed_at is not null)) then perform finpop_private.game_fail('INVALID_PHASE'); end if;
         v_dice:=finpop_private.roll_die();
         select jsonb_agg((p.position+step)%14 order by step) into v_path from generate_series(1,v_dice) step;
         v_position:=(p.position+v_dice)%14;
         v_bonus:=30+5*(p.character_level-1);
         v_events:=jsonb_build_array(jsonb_build_object('type','ABILITY_GOLD','playerId',v_actor,
           'amount',v_bonus,'abilityId','GOLDEN_POP'));
         if p.position+v_dice>=14 then
           v_bonus:=v_bonus+200;
           v_events:=v_events||jsonb_build_array(jsonb_build_object('type','START_REWARD','playerId',v_actor,'amount',200));
         end if;
         v_tile:=finpop_private.game_config()->'tiles'->v_position;
         v_topic:=v_tile->>'topic';
         select owner_player_id into v_owner from public.game_tiles where game_id=g.id and tile_id=v_position;
         if v_topic='GOLDEN_KEY' then perform finpop_private.game_fail('EVENT_RULES_UNAVAILABLE'); end if;
         if v_topic not in ('START','ISLAND') and v_owner is distinct from v_actor then
           select v.*, root.topic, root.source_type, answer.correct_choice_id, answer.explanation,
             (select jsonb_agg(jsonb_build_object('id',s.id,'title',s.title,'url',s.url,
               'publishedAt',s.published_at,'retrievedAt',s.retrieved_at) order by s.id)
              from public.quiz_version_sources link join public.quiz_sources s on s.id=link.source_id
              where link.question_version_id=v.id) as source
             into q
             from public.quiz_question_versions v
             join public.quiz_questions root on root.id=v.question_id
             join finpop_private.quiz_answer_keys answer on answer.question_version_id=v.id
             where v.review_status='APPROVED' and root.topic=v_topic
             and v.stage=g.quiz_stage and not exists(select 1 from public.game_quiz_attempts a
               where a.game_id=g.id and a.question_id=v.question_id)
             order by v.id limit 1 for share of v,root,answer;
           if not found then perform finpop_private.game_fail('QUIZ_POOL_EXHAUSTED'); end if;
           if not(q.choices @> '[{"id":1}]'::jsonb and q.choices @> '[{"id":2}]'::jsonb
             and q.choices @> '[{"id":3}]'::jsonb and q.choices @> '[{"id":4}]'::jsonb)
             or exists(select 1 from jsonb_array_elements(q.choices) c where
               jsonb_typeof(c->'text') is distinct from 'string') then perform finpop_private.game_fail('INVALID_QUIZ'); end if;
           v_issued:=clock_timestamp();
           insert into public.game_quiz_attempts(game_id,turn_id,player_id,question_id,question_version_id,tile_id,
             topic,source_type,stage,question,hint,choices,issued_at,deadline_at)
             values(g.id,g.current_turn_id,v_actor,q.question_id,q.id,v_position,q.topic,q.source_type,q.stage,q.question,q.hint,
               q.choices,v_issued,v_issued+interval '20 seconds') returning id into v_attempt;
           insert into finpop_private.game_quiz_answers(attempt_id,correct_choice_id,explanation,source)
             values(v_attempt,q.correct_choice_id,q.explanation,q.source);
         end if;
         update public.game_players set position=v_position,game_gold=game_gold+v_bonus,
           ability_state=jsonb_set(ability_state,'{triggerCount}',
             to_jsonb(coalesce((ability_state->>'triggerCount')::integer,0)+1)),
           island_wait_turns=case when v_topic='ISLAND' and remaining_turns>1 then 1 else island_wait_turns end
           where game_id=g.id and player_id=v_actor;
         update public.game_turns set dice_value=v_dice,movement_path=v_path where id=g.current_turn_id;
         update public.game_sessions set phase=case when v_attempt is not null then 'QUIZ_OPEN' else 'TURN_REVIEW' end,
           state_version=state_version+1,updated_at=clock_timestamp() where id=g.id;
         v_data:=jsonb_build_object('playerId',v_actor,'diceValue',v_dice,'movementPath',v_path,'events',v_events,
           'game',finpop_private.game_snapshot(g.id));
       end if;
     end if;
   else perform finpop_private.game_fail('INVALID_REQUEST');
   end if;
   v_envelope:=jsonb_build_object('data',v_data,'meta',v_meta);
 exception when sqlstate 'P0001' then
   v_code:=sqlerrm;
   v_status:=case
     when v_code='FORBIDDEN' then 403 when v_code='RESOURCE_NOT_FOUND' then 404
     when v_code='CHARACTER_LOCKED' then 422 when v_code='INVALID_REQUEST' then 400
     when v_code in ('CONFIG_UNAVAILABLE','DICE_UNAVAILABLE','ABILITY_UNAVAILABLE',
       'EVENT_RULES_UNAVAILABLE','QUIZ_POOL_EXHAUSTED','INVALID_QUIZ') then 503 else 409 end;
   v_envelope:=jsonb_build_object('error',jsonb_build_object('code',v_code,'message',
     case v_code when 'ACTIVE_GAME_EXISTS' then '진행 중인 게임을 이어하거나 포기해 주세요.'
       when 'CHARACTER_LOCKED' then '아직 해금되지 않은 캐릭터입니다.'
       when 'STATE_CONFLICT' then '최신 게임 상태를 확인해 주세요.'
       when 'QUIZ_POOL_EXHAUSTED' then '해당 주제의 승인된 미출제 문항이 부족합니다.'
       when 'ABILITY_UNAVAILABLE' then '이 캐릭터의 서버 능력 처리는 아직 연결하지 않았습니다.'
       when 'EVENT_RULES_UNAVAILABLE' then '황금열쇠 서버 효과 처리는 아직 연결하지 않았습니다.'
       else '요청을 처리할 수 없습니다. 상태와 입력을 확인해 주세요.' end,
     'details',case when v_code='ACTIVE_GAME_EXISTS' then jsonb_build_object('activeGameId',v_active) else '{}'::jsonb end),'meta',v_meta);
 end;
 if v_mutation then
   if v_status=200 then v_cache_game:=v_game_id; end if;
   insert into finpop_private.game_action_requests(user_id,request_key,operation,request_body,game_id,http_status,response_body)
     values(p_user_id,p_request_key,p_operation,v_fingerprint,v_cache_game,v_status,v_envelope);
 end if;
 return jsonb_build_object('httpStatus',v_status,'body',v_envelope);
end;
$$;

revoke all on function finpop_private.game_config(),finpop_private.roll_die(),
 finpop_private.game_snapshot(uuid),finpop_private.game_fail(text) from public,anon,authenticated;
grant execute on function finpop_private.game_config(),finpop_private.roll_die(),
 finpop_private.game_snapshot(uuid),finpop_private.game_fail(text) to service_role;
revoke all on function public.finpop_game_action(uuid,text,uuid,uuid,jsonb) from public,anon,authenticated;
grant execute on function public.finpop_game_action(uuid,text,uuid,uuid,jsonb) to service_role;
commit;


