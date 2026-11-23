-- 문항 저장 구조 검토용입니다. 실제 프로젝트에는 아직 적용하지 않습니다.
-- 적용 순서: 이 파일 → game-db-draft.sql → game-rpc-draft.sql.
begin;
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
commit;

