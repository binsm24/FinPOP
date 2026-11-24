-- 검토용 초안입니다. 아직 원격 DB에 적용하지 않았습니다.
-- 기존 public.users / characters / user_characters와 auth.users는 변경하지 않습니다.
-- 승인 후 마이그레이션 파일로 옮겨 테스트 DB에서 먼저 검증합니다.
begin;

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

commit;

