-- Supabase 수동 적용용: 기존 users·characters·user_characters는 유지합니다.
-- 문항·게임·서버 함수를 하나의 트랜잭션으로 추가합니다.
-- 반드시 파일 전체를 선택하여 실행하세요. 테스트 fixture는 포함하지 않습니다.
begin;
set local lock_timeout = '5s';
set local statement_timeout = '120s';
do $preflight$
declare obj text;
begin
  foreach obj in array array['public.users','public.characters','public.user_characters'] loop
    if to_regclass(obj) is null then
      raise exception '필수 기존 테이블이 없습니다: %', obj;
    end if;
  end loop;
  if not exists(select 1 from public.characters where id='CHEESE') then
    raise exception '기본 캐릭터 CHEESE가 없습니다.';
  end if;
  foreach obj in array array[
    'public.quiz_questions','public.quiz_question_versions','public.quiz_sources','public.quiz_version_sources',
    'finpop_private.quiz_answer_keys','public.game_sessions','public.game_players','public.game_tiles',
    'public.game_turns','public.game_quiz_attempts','finpop_private.game_quiz_answers',
    'finpop_private.game_action_requests'
  ] loop
    if to_regclass(obj) is not null then
      raise exception '추가할 테이블이 이미 존재합니다. 중복 적용하지 마세요: %', obj;
    end if;
  end loop;
end;
$preflight$;
-- 원본: docs/api/quiz-db-draft.sql
-- 문항 저장 구조 검토용입니다. 실제 프로젝트에는 아직 적용하지 않습니다.
-- 적용 순서: 이 파일 → game-db-draft.sql → game-rpc-draft.sql.

create schema if not exists finpop_private;
revoke all on schema finpop_private from public,anon,authenticated;
grant usage on schema finpop_private to service_role;

create table public.quiz_questions (
  id text primary key check (length(btrim(id)) > 0),
  topic text not null check (topic in ('STOCK','BOND','MATURITY','COMPOUND','DIVIDEND','DEPOSIT','SAVINGS','LOAN','ASSET','FUND')),
  source_type text not null check (source_type in ('BASIC','NEWS')),
  created_at timestamptz not null default now(),
  unique(id,topic,source_type)
);
create table public.quiz_question_versions (
  id uuid primary key default gen_random_uuid(),
  question_id text not null references public.quiz_questions(id),
  version integer not null check (version > 0),
  stage integer not null check (stage > 0),
  question text not null check (length(btrim(question)) > 0),
  hint text,
  choices jsonb not null check (
    case when jsonb_typeof(choices)='array' then
      jsonb_array_length(choices)=4 and
      choices @> '[{"id":1}]'::jsonb and choices @> '[{"id":2}]'::jsonb and
      choices @> '[{"id":3}]'::jsonb and choices @> '[{"id":4}]'::jsonb
    else false end),
  review_status text not null default 'DRAFT' check (review_status in ('DRAFT','APPROVED','RETIRED')),
  reviewed_at timestamptz,
  created_at timestamptz not null default now(),
  unique(question_id,version),
  unique(id,question_id),
  check ((review_status='DRAFT' and reviewed_at is null) or
    (review_status in ('APPROVED','RETIRED') and reviewed_at is not null))
);
-- 같은 원본의 여러 승인 버전을 한꺼번에 출제하지 않습니다.
create unique index quiz_versions_one_approved on public.quiz_question_versions(question_id)
  where review_status='APPROVED';

create table finpop_private.quiz_answer_keys (
  question_version_id uuid primary key references public.quiz_question_versions(id) on delete cascade,
  correct_choice_id integer not null check (correct_choice_id between 1 and 4),
  explanation text not null check (length(btrim(explanation)) > 0)
);
create table public.quiz_sources (
  id uuid primary key default gen_random_uuid(),
  title text not null check (length(btrim(title)) > 0),
  url text not null check (url ~ '^https?://'),
  published_at timestamptz,
  retrieved_at timestamptz not null default now()
);
create table public.quiz_version_sources (
  question_version_id uuid not null references public.quiz_question_versions(id) on delete cascade,
  source_id uuid not null references public.quiz_sources(id),
  primary key(question_version_id,source_id)
);

-- 원본 분류와 출처는 추가 전용입니다. 수정할 때 새 원본/출처를 등록합니다.
create function finpop_private.quiz_catalog_immutable() returns trigger
language plpgsql security invoker set search_path='' as $$
begin raise exception using errcode='P0001',message='QUIZ_CATALOG_IMMUTABLE'; end;
$$;
create trigger quiz_question_identity_immutable before update on public.quiz_questions
  for each row execute function finpop_private.quiz_catalog_immutable();
create trigger quiz_source_snapshot_immutable before update on public.quiz_sources
  for each row execute function finpop_private.quiz_catalog_immutable();

-- 정답·출처 연결의 변경과 승인 작업은 버전 행 잠금으로 순서를 맞춥니다.
create function finpop_private.quiz_dependency_guard() returns trigger
language plpgsql security invoker set search_path='' as $$
declare v_id uuid; v_status text;
begin
 if tg_op='UPDATE' then
   if new.question_version_id<>old.question_version_id then
     raise exception using errcode='P0001',message='QUIZ_VERSION_REFERENCE_IMMUTABLE';
   end if;
 end if;
 if tg_op='DELETE' then v_id:=old.question_version_id; else v_id:=new.question_version_id; end if;
 select review_status into v_status from public.quiz_question_versions where id=v_id for update;
 if v_status in ('APPROVED','RETIRED') then
   raise exception using errcode='P0001',message='PUBLISHED_QUIZ_IMMUTABLE';
 end if;
 if tg_op='DELETE' then return old; end if;
 return new;
end;
$$;
create trigger quiz_answer_key_guard before insert or update or delete on finpop_private.quiz_answer_keys
  for each row execute function finpop_private.quiz_dependency_guard();
create trigger quiz_source_link_guard before insert or update or delete on public.quiz_version_sources
  for each row execute function finpop_private.quiz_dependency_guard();

create function finpop_private.quiz_version_guard() returns trigger
language plpgsql security invoker set search_path='' as $$
begin
 if tg_op='DELETE' then
   if old.review_status<>'DRAFT' then
     raise exception using errcode='P0001',message='PUBLISHED_QUIZ_IMMUTABLE';
   end if;
   return old;
 end if;
 if tg_op='INSERT' and new.review_status<>'DRAFT' then
   raise exception using errcode='P0001',message='QUIZ_MUST_START_AS_DRAFT';
 end if;
 if tg_op='UPDATE' and old.review_status<>'DRAFT' then
   if (to_jsonb(new)-'review_status') is distinct from (to_jsonb(old)-'review_status')
     or (old.review_status='RETIRED' and new.review_status<>'RETIRED')
     or (old.review_status='APPROVED' and new.review_status not in ('APPROVED','RETIRED')) then
     raise exception using errcode='P0001',message='PUBLISHED_QUIZ_IMMUTABLE';
   end if;
 end if;
 -- 초안에서도 보기 본문은 비어 있지 않은 문자열이어야 합니다.
 if jsonb_typeof(new.choices)<>'array' or exists(
   select 1 from jsonb_array_elements(new.choices) c
   where jsonb_typeof(c->'text') is distinct from 'string' or length(btrim(c->>'text'))=0
 ) then raise exception using errcode='P0001',message='INVALID_QUIZ_CHOICES'; end if;
 if new.review_status='RETIRED' and (tg_op='INSERT' or old.review_status='DRAFT') then
   raise exception using errcode='P0001',message='INVALID_QUIZ_REVIEW_TRANSITION';
 end if;
 if new.review_status='APPROVED' and old.review_status='DRAFT' then
   if not exists(select 1 from finpop_private.quiz_answer_keys where question_version_id=new.id)
     or not exists(select 1 from public.quiz_version_sources where question_version_id=new.id) then
     raise exception using errcode='P0001',message='QUIZ_APPROVAL_REQUIRES_ANSWER_AND_SOURCE';
   end if;
   new.reviewed_at:=clock_timestamp();
 end if;
 return new;
end;
$$;
create trigger quiz_version_review_guard before insert or update or delete on public.quiz_question_versions
  for each row execute function finpop_private.quiz_version_guard();

-- 문항 목록 전체가 앱에 노출되지 않도록 출제 API로만 전달합니다.
revoke all on public.quiz_questions,public.quiz_question_versions,public.quiz_sources,public.quiz_version_sources,
 finpop_private.quiz_answer_keys from public,anon,authenticated;
grant select,insert,update,delete on public.quiz_questions,public.quiz_question_versions,public.quiz_sources,
 public.quiz_version_sources,finpop_private.quiz_answer_keys to service_role;
alter table public.quiz_questions enable row level security;
alter table public.quiz_question_versions enable row level security;
alter table public.quiz_sources enable row level security;
alter table public.quiz_version_sources enable row level security;
alter table finpop_private.quiz_answer_keys enable row level security;

revoke all on function finpop_private.quiz_catalog_immutable(),finpop_private.quiz_dependency_guard(),
 finpop_private.quiz_version_guard() from public,anon,authenticated;
grant execute on function finpop_private.quiz_catalog_immutable(),finpop_private.quiz_dependency_guard(),
 finpop_private.quiz_version_guard() to service_role;

-- 원본: docs/api/game-db-draft.sql
-- 검토용 초안입니다. 아직 원격 DB에 적용하지 않았습니다.
-- 기존 public.users / characters / user_characters와 auth.users는 변경하지 않습니다.
-- 승인 후 마이그레이션 파일로 옮겨 테스트 DB에서 먼저 검증합니다.

-- 비공개 정답·중복 요청 기록은 Data API에 노출하지 않는 스키마에 둡니다.
-- quiz-db-draft.sql이 만든 비공개 스키마를 공유합니다.
create schema if not exists finpop_private;
revoke all on schema finpop_private from public, anon, authenticated;
grant usage on schema finpop_private to service_role;

create table public.game_sessions (
  id uuid primary key default gen_random_uuid(),
  user_id uuid not null references public.users(id) on delete cascade,
  mode text not null default 'AI' check (mode = 'AI'),
  status text not null default 'ACTIVE'
    check (status in ('ACTIVE', 'PAUSED', 'FINISHED', 'ABANDONED')),
  phase text not null default 'INITIATIVE_PENDING'
    check (phase in ('INITIATIVE_PENDING', 'TURN_READY', 'QUIZ_OPEN',
      'PURCHASE_PENDING', 'EVENT_PENDING', 'TURN_REVIEW', 'FINISHED', 'ABANDONED')),
  state_version integer not null default 1 check (state_version >= 1),
  rules_version text not null default '1.0',
  quiz_stage integer not null check (quiz_stage >= 1),
  current_player_id text check (current_player_id in ('P1', 'P2')),
  current_turn_id uuid check (current_turn_id is null or current_player_id is not null),
  -- 선택 가능한 칸·이벤트 식별자 등 공개 상태만 저장합니다. 정답은 저장하지 않습니다.
  pending_event jsonb,
  pending_purchase jsonb check (pending_purchase is null or jsonb_typeof(pending_purchase) = 'object'),
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now(),
  finished_at timestamptz,
  check (pending_event is null or jsonb_typeof(pending_event) = 'object'),
  check ((status = 'FINISHED') = (finished_at is not null))
);
-- 일시정지한 대결도 활성 슬롯을 사용합니다. 새 게임 전에 재개·포기를 선택합니다.
create unique index game_sessions_one_active_ai_per_user
  on public.game_sessions(user_id) where status in ('ACTIVE', 'PAUSED');
create index game_sessions_user_created on public.game_sessions(user_id, created_at desc);

create table public.game_players (
  game_id uuid not null references public.game_sessions(id) on delete cascade,
  player_id text not null check (player_id in ('P1', 'P2')),
  is_ai boolean not null,
  character_id text not null references public.characters(id),
  -- 시작 시점의 강화 레벨을 고정합니다. 대결 중 계정 강화가 반영되면 안 됩니다.
  character_level integer not null check (character_level between 1 and 5),
  position integer not null default 0 check (position between 0 and 13),
  game_gold integer not null default 700,
  remaining_turns integer not null default 7 check (remaining_turns between 0 and 7),
  island_wait_turns integer not null default 0 check (island_wait_turns between 0 and 1),
  ability_state jsonb not null default '{"triggerCount":0}'::jsonb
    check (jsonb_typeof(ability_state) = 'object'),
  primary key (game_id, player_id),
  check ((player_id = 'P1' and not is_ai) or (player_id = 'P2' and is_ai))
);
-- 방문 정산으로 음수가 될 수 있으므로 game_gold에는 0 이상 제약을 두지 않습니다.

create table public.game_tiles (
  game_id uuid not null references public.game_sessions(id) on delete cascade,
  tile_id integer not null check (tile_id between 0 and 13),
  owner_player_id text,
  primary key (game_id, tile_id),
  foreign key (game_id, owner_player_id)
    references public.game_players(game_id, player_id),
  -- 출발·황금열쇠·무인도는 소유할 수 없습니다.
  check (owner_player_id is null or tile_id not in (0, 3, 7, 10))
);

create table public.game_turns (
  id uuid primary key default gen_random_uuid(),
  game_id uuid not null references public.game_sessions(id) on delete cascade,
  player_id text not null,
  started_at timestamptz not null default now(),
  completed_at timestamptz,
  dice_value integer check (dice_value between 1 and 6),
  movement_path jsonb check (
    movement_path is null or jsonb_typeof(movement_path) = 'array'),
  unique (game_id, id),
  unique (game_id, id, player_id),
  foreign key (game_id, player_id) references public.game_players(game_id, player_id)
);
create unique index game_turns_one_open_turn
  on public.game_turns(game_id) where completed_at is null;

-- 현재 차례가 다른 게임·참가자를 가리키지 못하게 합니다.
alter table public.game_sessions add constraint game_sessions_current_player_fk
  foreign key (id, current_player_id) references public.game_players(game_id, player_id)
  deferrable initially deferred;
alter table public.game_sessions add constraint game_sessions_current_turn_fk
  foreign key (id, current_turn_id, current_player_id) references public.game_turns(game_id, id, player_id)
  deferrable initially deferred;

create table public.game_quiz_attempts (
  id uuid primary key default gen_random_uuid(),
  game_id uuid not null references public.game_sessions(id) on delete cascade,
  turn_id uuid not null,
  player_id text not null,
  -- 원본과 버전 식별자를 함께 보존하여 같은 원본의 중복 출제를 막습니다.
  question_id text not null,
  question_version_id uuid not null,
  tile_id integer not null check (tile_id between 0 and 13 and tile_id not in (0, 3, 7, 10)),
  topic text not null check (topic in ('STOCK','BOND','MATURITY','COMPOUND',
    'DIVIDEND','DEPOSIT','SAVINGS','LOAN','ASSET','FUND')),
  source_type text not null check (source_type in ('BASIC', 'NEWS', 'REVIEW')),
  stage integer not null check (stage >= 1),
  question text not null,
  hint text,
  -- 출제 당시 문제·보기의 사본입니다. 정답·해설은 별도 비공개 테이블에 저장합니다.
  choices jsonb not null check (
    case when jsonb_typeof(choices) = 'array' then jsonb_array_length(choices) = 4 else false end),
  issued_at timestamptz not null default now(),
  deadline_at timestamptz not null,
  remaining_millis integer check (remaining_millis between 0 and 20000),
  selected_choice_id integer check (selected_choice_id between 1 and 4),
  resolution text check (resolution in ('CORRECT', 'INCORRECT', 'TIMEOUT')),
  resolved_at timestamptz,
  unique (game_id, question_version_id),
  unique (game_id, question_id),
  foreign key (question_version_id, question_id) references public.quiz_question_versions(id, question_id),
  foreign key (question_id, topic, source_type) references public.quiz_questions(id, topic, source_type),
  unique (game_id, id),
  foreign key (game_id, turn_id, player_id) references public.game_turns(game_id, id, player_id),
  foreign key (game_id, player_id) references public.game_players(game_id, player_id),
  foreign key (game_id, tile_id) references public.game_tiles(game_id, tile_id),
  check (deadline_at > issued_at),
  check (
    (resolution is null and resolved_at is null and selected_choice_id is null)
    or (resolution is not null and resolved_at is not null and (
      (resolution = 'TIMEOUT' and selected_choice_id is null)
      or (resolution in ('CORRECT','INCORRECT') and selected_choice_id is not null)
    ))
  )
);
create unique index game_quiz_attempts_one_pending
  on public.game_quiz_attempts(game_id) where resolved_at is null;
create index game_quiz_attempts_due
  on public.game_quiz_attempts(deadline_at) where resolved_at is null;

create table finpop_private.game_quiz_answers (
  attempt_id uuid primary key references public.game_quiz_attempts(id) on delete cascade,
  correct_choice_id integer not null check (correct_choice_id between 1 and 4),
  explanation text not null,
  source jsonb,
  -- 이 행의 정답·해설은 미응답 클라이언트에 절대 전달하지 않습니다.
  created_at timestamptz not null default now()
);

-- 게임 생성 요청은 game_id가 정해지기 전에도 중복 검사를 해야 합니다.
-- 같은 사용자·키는 경로와 무관하게 하나의 요청만 나타냅니다.
create table finpop_private.game_action_requests (
  user_id uuid not null references public.users(id) on delete cascade,
  request_key uuid not null,
  operation text not null,
  request_body jsonb not null,
  game_id uuid references public.game_sessions(id) on delete cascade,
  http_status integer not null check (http_status between 200 and 599),
  response_body jsonb not null,
  created_at timestamptz not null default now(),
  primary key (user_id, request_key)
);

-- 새 객체의 기본 권한에 의존하지 않고 앱의 직접 쓰기를 차단합니다.
revoke all on table public.game_sessions, public.game_players, public.game_tiles,
  public.game_turns, public.game_quiz_attempts
  from public, anon, authenticated;
grant select on table public.game_sessions, public.game_players, public.game_tiles,
  public.game_turns, public.game_quiz_attempts to authenticated;
grant select, insert, update, delete on table public.game_sessions, public.game_players,
  public.game_tiles, public.game_turns, public.game_quiz_attempts to service_role;
revoke all on table finpop_private.game_quiz_answers, finpop_private.game_action_requests
  from public, anon, authenticated;
grant select, insert, update, delete on table finpop_private.game_quiz_answers,
  finpop_private.game_action_requests to service_role;

alter table public.game_sessions enable row level security;
alter table public.game_players enable row level security;
alter table public.game_tiles enable row level security;
alter table public.game_turns enable row level security;
alter table public.game_quiz_attempts enable row level security;
alter table finpop_private.game_quiz_answers enable row level security;
alter table finpop_private.game_action_requests enable row level security;

create policy game_sessions_read_own on public.game_sessions for select to authenticated
  using (user_id = (select auth.uid()));
create policy game_players_read_own on public.game_players for select to authenticated
  using (exists (select 1 from public.game_sessions g
    where g.id = game_id and g.user_id = (select auth.uid())));
create policy game_tiles_read_own on public.game_tiles for select to authenticated
  using (exists (select 1 from public.game_sessions g
    where g.id = game_id and g.user_id = (select auth.uid())));
create policy game_turns_read_own on public.game_turns for select to authenticated
  using (exists (select 1 from public.game_sessions g
    where g.id = game_id and g.user_id = (select auth.uid())));
create policy game_quiz_attempts_read_own on public.game_quiz_attempts for select to authenticated
  using (exists (select 1 from public.game_sessions g
    where g.id = game_id and g.user_id = (select auth.uid())));

-- 원본: docs/api/game-rpc-draft.sql
-- 검토용 서버 함수 초안입니다. game-db-draft.sql 적용 후 테스트 DB에서 검증합니다.

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

-- 최종 확인 결과입니다. 기존 테이블에는 INSERT·UPDATE·DELETE를 수행하지 않습니다.
select schemaname, tablename
from pg_tables
where (schemaname='public' and tablename in (
  'users','characters','user_characters','quiz_questions','quiz_question_versions','quiz_sources',
  'quiz_version_sources','game_sessions','game_players','game_tiles','game_turns','game_quiz_attempts'))
  or (schemaname='finpop_private' and tablename in ('quiz_answer_keys','game_quiz_answers','game_action_requests'))
order by schemaname,tablename;
commit;