-- 구매와 차례 종료는 인증된 RPC가 게임 행을 잠근 뒤 호출합니다.
create or replace function finpop_private.purchase_game_tile(
 p_game_id uuid,p_actor text,p_tile integer,p_action text,p_ai boolean
) returns jsonb language plpgsql security invoker set search_path='' as $$
declare
 g public.game_sessions%rowtype;p public.game_players%rowtype;
 v_owner text;v_price integer;v_action text;v_event uuid:=gen_random_uuid();
begin
 select * into g from public.game_sessions where id=p_game_id;
 if g.phase<>'PURCHASE_PENDING' or g.current_player_id is distinct from p_actor
  or g.pending_purchase is null or (g.pending_purchase->>'tileId')::integer is distinct from p_tile then
  perform finpop_private.game_fail('PURCHASE_NOT_AVAILABLE');
 end if;
 if not exists(select 1 from public.game_quiz_attempts where game_id=g.id and turn_id=g.current_turn_id
  and player_id=p_actor and tile_id=p_tile and resolution='CORRECT') then
  perform finpop_private.game_fail('PURCHASE_NOT_AVAILABLE');
 end if;
 select * into p from public.game_players where game_id=g.id and player_id=p_actor for update;
 select owner_player_id into v_owner from public.game_tiles where game_id=g.id and tile_id=p_tile for update;
 if not found or v_owner is not null or p_tile in (0,3,7,10) then
  perform finpop_private.game_fail('PURCHASE_NOT_AVAILABLE');
 end if;
 select (t->>'price')::integer into v_price
  from jsonb_array_elements(finpop_private.game_config()->'tiles') t where (t->>'tileId')::integer=p_tile;
 if v_price is null or v_price<=0 then perform finpop_private.game_fail('CONFIG_UNAVAILABLE'); end if;
 v_action:=case when p_ai then case when p.game_gold>=v_price then 'BUY' else 'PASS' end else p_action end;
 if v_action not in ('BUY','PASS') or v_action is null then perform finpop_private.game_fail('INVALID_REQUEST'); end if;
 if v_action='BUY' then
  if p.game_gold<v_price then perform finpop_private.game_fail('INSUFFICIENT_GOLD'); end if;
  update public.game_players set game_gold=game_gold-v_price where game_id=g.id and player_id=p_actor;
  update public.game_tiles set owner_player_id=p_actor where game_id=g.id and tile_id=p_tile;
 end if;
 update public.game_sessions set phase='TURN_REVIEW',pending_purchase=null,
  state_version=state_version+1,updated_at=clock_timestamp() where id=g.id;
 return jsonb_build_object('eventId',v_event,'playerId',p_actor,'tileId',p_tile,'action',v_action,
  'amount',case when v_action='BUY' then v_price else 0 end);
end;
$$;

create or replace function finpop_private.complete_game_turn(
 p_game_id uuid,p_actor text,p_turn uuid,p_skip_island boolean
) returns jsonb language plpgsql security invoker set search_path='' as $$
declare
 g public.game_sessions%rowtype;p public.game_players%rowtype;
 v_closed uuid;v_next text;v_next_turn uuid;v_finished boolean;v_now timestamptz:=clock_timestamp();
begin
 select * into g from public.game_sessions where id=p_game_id;
 if g.current_player_id is distinct from p_actor or g.current_turn_id is distinct from p_turn
  or p_turn is null then perform finpop_private.game_fail('TURN_ALREADY_COMPLETED'); end if;
 if g.pending_purchase is not null or g.pending_event is not null or exists(
  select 1 from public.game_quiz_attempts where game_id=g.id and resolved_at is null) then
  perform finpop_private.game_fail('TURN_NOT_READY_TO_COMPLETE');
 end if;
 select * into p from public.game_players where game_id=g.id and player_id=p_actor for update;
 if not found or p.remaining_turns<=0 then perform finpop_private.game_fail('TURN_ALREADY_COMPLETED'); end if;
 if p_skip_island then
  if g.phase<>'TURN_READY' or p.island_wait_turns<>1 or exists(
   select 1 from public.game_turns where id=p_turn and dice_value is not null) then
   perform finpop_private.game_fail('INVALID_PHASE');
  end if;
 elsif g.phase<>'TURN_REVIEW' then perform finpop_private.game_fail('TURN_NOT_READY_TO_COMPLETE');
 end if;
 update public.game_turns set completed_at=v_now where id=p_turn and game_id=g.id
  and player_id=p_actor and completed_at is null returning id into v_closed;
 if not found then perform finpop_private.game_fail('TURN_ALREADY_COMPLETED'); end if;
 update public.game_players set remaining_turns=remaining_turns-1,
  island_wait_turns=case when p_skip_island then 0 else island_wait_turns end
  where game_id=g.id and player_id=p_actor;
 select not exists(select 1 from public.game_players where game_id=g.id and remaining_turns>0) into v_finished;
 if not v_finished then
  select player_id into v_next from public.game_players where game_id=g.id and remaining_turns>0
   order by case when player_id<>p_actor then 0 else 1 end limit 1;
  insert into public.game_turns(game_id,player_id) values(g.id,v_next) returning id into v_next_turn;
 end if;
 update public.game_sessions set status=case when v_finished then 'FINISHED' else 'ACTIVE' end,
  phase=case when v_finished then 'FINISHED' else 'TURN_READY' end,
  current_player_id=v_next,current_turn_id=v_next_turn,pending_purchase=null,pending_event=null,
  finished_at=case when v_finished then v_now else null end,
  state_version=state_version+1,updated_at=v_now where id=g.id;
 -- 계정 보상·완료 횟수는 별도 구현 전까지 갱신하지 않습니다.
 return jsonb_build_object('completedPlayerId',p_actor,'completedTurnId',p_turn,
  'nextPlayerId',v_next,'skippedIsland',p_skip_island,'finished',v_finished);
end;
$$;
revoke all on function finpop_private.purchase_game_tile(uuid,text,integer,text,boolean),
 finpop_private.complete_game_turn(uuid,text,uuid,boolean) from public,anon,authenticated;
grant execute on function finpop_private.purchase_game_tile(uuid,text,integer,text,boolean),
 finpop_private.complete_game_turn(uuid,text,uuid,boolean) to service_role;
