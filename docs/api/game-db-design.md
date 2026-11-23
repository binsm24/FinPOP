# 게임 DB 설계 초안

상태: 검토용. 문항 원본·버전 구조와 통합하여 PGlite PostgreSQL 환경에서 검증했습니다. 실제 Supabase 프로젝트에는 적용하지 않았습니다.
검토 파일: docs/api/game-db-draft.sql. 자동 배포를 피하기 위해 migrations 폴더에는 넣지 않았습니다.
기준: 사용자가 공유한 users·characters·user_characters 생성 SQL과 노션 API 명세.

## 기존 테이블과 관계

auth.users → public.users → game_sessions → game_players / game_tiles / game_turns / game_quiz_attempts.
캐릭터 정의는 기존 public.characters를 참조합니다. 기존 테이블·회원가입 트리거·정책은 변경하지 않습니다.
게임 생성 시 서버가 user_characters에서 보유·강화 레벨을 검증한 뒤 game_players에 사본을 저장합니다.
user_characters에 게임 참가자를 직접 연결하지 않아 대결 중 계정 성장 변경과 게임 상태를 분리합니다.
quiz_stage는 완료 게임 수에 따른 규칙으로 서버가 계산하여 생성 시 고정합니다.
계정 wallet_gold는 게임 game_gold와 별도이며 게임 생성 시 계정 지갑에서 700G를 차감하지 않습니다.

## 추가 객체

| 테이블 | 역할 |
|---|---|
| public.game_sessions | 사용자·모드·규칙/학습 버전·진행 단계·현재 차례·상태 버전 |
| public.game_players | P1 사용자·P2 AI의 출전 캐릭터·위치·게임 G·남은 턴·능력 상태 |
| public.game_tiles | 게임별 14칸 소유권 |
| public.game_turns | 주사위 결과·이동 경로·차례 완료 기록 |
| public.game_quiz_attempts | 발급 당시 공개 문제·보기·마감·응답 판정 |
| finpop_private.game_quiz_answers | 해당 응답의 비공개 정답·해설·출처 |
| finpop_private.game_action_requests | 요청 ID·입력·최초 HTTP 응답 보존 |

문항 원본·버전·정답·출처는 quiz-db-draft.sql에서 정의합니다. 적용은 해당 파일을 먼저 실행하는 순서입니다.
question_version_id는 UUID이며 원본 question_id와 함께 문항 버전을 외래키로 참조합니다. 게임별 question_id 유일 제약으로 다른 버전의 같은 원본도 재출제하지 않습니다.
토픽과 칸의 대응, 보기 ID 1~4의 유일성, 레벨별 능력 수치는 서버 규칙에서도 검증해야 합니다.
게임당 사용자·AI 2명과 14칸을 하나의 게임 생성 트랜잭션에서 작성해야 합니다.
DB 기본 제약만으로 이 행 개수까지 강제하지는 않습니다.

## 접근 제한

공개 테이블은 RLS를 켜고 authenticated의 본인 게임 SELECT만 허용합니다.
다른 사용자 게임·익명 클라이언트 조회·일반 앱의 직접 쓰기는 허용하지 않습니다.
상태를 바꾸는 API는 반드시 사용자 JWT와 게임 소유권을 검증해야 합니다.
service_role은 RLS를 우회하므로 키를 Android에 넣으면 안 됩니다.
비공개 스키마는 Exposed schemas에 추가하지 않습니다.
서버는 PostgreSQL 함수/RPC를 통해 비공개 테이블과 상태 갱신을 하나의 트랜잭션으로 처리하는 방향입니다.
service_role 권한 부여만으로 RPC 구현이나 사용자 권한 검증이 완성되는 것은 아닙니다.

공식 근거:
- https://supabase.com/docs/guides/api/securing-your-api
- https://supabase.com/docs/guides/database/postgres/row-level-security

## 서버에서 추가 구현할 원자적 처리

1. 인증 사용자·요청 키로 트랜잭션 잠금 후 기존 요청 결과를 먼저 확인합니다.
2. 같은 키·다른 경로/본문은 충돌, 같은 요청은 최초 응답을 반환합니다.
3. 게임 행을 FOR UPDATE로 잠그고 state_version·phase·참가자·현재 turn_id를 검증합니다.
4. 현재 turn_id에 이미 dice_value가 있으면 새 요청 키로 다시 굴리지 못하게 합니다.
5. 주사위·이동·출발 보상·퀴즈 발급/능력/이벤트를 확정합니다.
6. 퀴즈 정산과 소유권/잔액/상태 변경, 요청 결과 기록을 함께 커밋합니다.
7. 종료는 completed_at이 비어 있는 차례만 한 번 완료하여 턴을 한 번 차감합니다.

게임 생성 경쟁은 사용자별 잠금과 활성 게임 유일 인덱스로 막습니다.
네트워크 REST 호출 여러 번으로 이 과정을 나누면 안 됩니다.
트랜잭션 실패 시 요청 결과·주사위·재화 변경도 함께 롤백되어야 합니다.

## 적용 전 합의·검증할 사항

- PAUSED 상태, EVENT_PENDING 등 phase 이름을 최종 API 계약과 대조합니다.
- Kotlin의 LANDING_PENDING·SPECIAL_TILE_PENDING은 로컬 중간 상태라 DB phase에 넣지 않았습니다.
- 현재 캐릭터 Lv. 상한은 기존 최대값 5 기준입니다. 향후 상한 변경 시 제약 변경이 필요합니다.
- 게임 규칙 버전 1.0은 14칸·700G·7턴을 고정하며, 과거 버전은 덮어쓰지 않아야 합니다.
- 일시정지 시 퀴즈 잔여 시간·재개 마감 계산은 별도 API에서 확정합니다.
- 승패·결과·포인트/강화 G 보상 원장은 다음 설계 대상입니다. 현재 초안은 보상을 지급하지 않습니다.
- 별도 테스트 DB에서 생성·정답 미노출·타 사용자 조회 차단·직접 쓰기 차단·중복 요청을 검증해야 합니다.
- 로컬 PGlite에서 SQL 생성·RLS·기본 제약 검증을 수행했습니다. 실제 Supabase와 동시 트랜잭션 검증은 별도입니다. 결과는 game-db-validation.md를 참고합니다.
- 테이블이 이미 존재하면 SQL은 중단됩니다. 오류를 무시하고 IF NOT EXISTS를 붙여 재실행하지 않습니다.
- 파일은 BEGIN/COMMIT으로 묶었습니다. 운영 프로젝트 적용은 검토 후 별도 단계로 진행합니다.

## 다음 순서

팀 검토 → 테스트 DB에서 SQL 검증 → 트랜잭션 함수와 서버 API 구현 →
Android 원격 Repository 연결 → debug 화면에서 저장·재조회·동시 요청 검증.

