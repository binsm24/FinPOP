# 게임 서버 API·트랜잭션 함수 구현

상태(2026-11-23): 실제 Supabase DB 구성·기본 금융 문항 30개 등록·Edge Function 배포 완료. Android debug 화면에서 실제 사용자 인증을 거쳐 게임 생성과 선공 결정 성공을 확인했습니다. 실제 서버 주사위 이동과 팀 보드 화면 통합은 아직 검증하지 않았습니다.

## 파일과 역할

- docs/api/game-db-draft.sql: 게임 상태·RLS 기본 구조, 테이블 7개.
- docs/api/quiz-db-draft.sql: 문항 원본·버전·비공개 정답·출처 테이블 5개.
- docs/api/game-rpc-draft.sql: 정규화된 문항 저장소를 사용하는 서버 전용 DB 함수.
- supabase/functions/finpop-api/index.ts: Supabase Edge Function 진입점.
- supabase/functions/finpop-api/handler.mjs: Auth 검증과 HTTP 요청·응답 처리.
- tools/db-schema/validate-rpc.mjs: 기본 스키마·문항 검수·RPC 통합 검증.
- tools/db-schema/handler.test.mjs: 인증·HTTP 요청·배포 경로 검증 20개.

서버 파일은 Android app 모듈과 다른 런타임입니다.
Android debug 전용 GameServerTestScreen에서 HTTP로 직접 호출합니다. 팀원 MainGameBoardContent에 서버 상태·주사위 콜백을 연결했습니다. 공통 GameRepository의 원격 구현은 후속 작업입니다.
DB 함수 SQL은 docs/api에 보관합니다. game-quiz-setup.sql과 basic-finance-30-import.sql은 대시보드에서 수동 적용했으며, 기존 사용자·캐릭터 테이블은 유지했습니다. 이미 적용한 프로젝트에 생성·등록 SQL을 다시 실행하지 않습니다.

## 지원 API

기준 URL: https://xzxbhbdartmcyvoslwsl.supabase.co/functions/v1/finpop-api

| 메서드 | 경로 | 처리 |
|---|---|---|
| GET | /v1/game-config | 규칙 1.0과 14칸 정의 |
| POST | /v1/games | 보유 캐릭터·레벨·완료 횟수 검증, 양측 700G·7턴 생성 |
| GET | /v1/games/{gameId} | 본인 게임 상태 조회 |
| POST | /v1/games/{gameId}/initiative | 동률 재추첨·선공·현재 차례 생성 |
| POST | /v1/games/{gameId}/ai/rolls | AI 현재 차례의 서버 주사위·이동·퀴즈 발급 |
| POST | /v1/games/{gameId}/rolls | 사용자 이동·치즈 능력·출발 보상·필요한 퀴즈 발급 |

상태 변경 요청은 UUID Idempotency-Key 헤더와 application/json 본문이 필요합니다.
create 본문은 mode: AI와 characterId, initiative/rolls는 expectedVersion만 받습니다.
사용자 ID·주사위 숫자·문항 정답을 클라이언트 입력으로 받지 않습니다.

## 인증과 원자적 처리

플랫폼 verify_jwt 기본값 true를 유지합니다.
handler는 Authorization의 사용자 JWT를 /auth/v1/user에서 추가 검증합니다.
Auth가 확인한 user.id만 서버 전용 public.finpop_game_action으로 전달합니다.
anon·authenticated는 이 RPC를 직접 실행할 수 없습니다.
service_role 전용 자격 증명은 서버 환경변수에서만 읽고 로그·응답·Android에 기록하지 않습니다.

한 RPC 호출 안에서 사용자별 트랜잭션 잠금 → 요청 키 조회 → 게임 행 잠금 →
입력·상태 버전 검증 → 게임 변경 → 최초 응답 기록을 처리합니다.
검증 실패 시 게임 변경은 롤백하고 알려진 오류 응답만 요청 기록에 보존합니다.
같은 키·같은 요청은 상태 버전이 달라져도 최초 응답을 그대로 반환합니다.
같은 키·다른 요청은 IDEMPOTENCY_CONFLICT입니다.
수정된 본문이나 새 동작은 새 요청 키를 사용해야 합니다.

서버 환경변수:
SUPABASE_URL, SUPABASE_ANON_KEY, SUPABASE_SERVICE_ROLE_KEY.
이 어댑터는 기본 legacy service_role JWT 환경변수를 사용합니다.
sb_secret 형태로 전환할 때는 서비스 인증 헤더 방식을 별도로 변경·검증해야 합니다.

## 규칙 구현과 현재 제한

출전 캐릭터 보유·해금 레벨·강화 레벨을 DB에서 검증합니다.
학습 단계는 1 + floor(completed_game_count / 2)로 생성 시 고정합니다.
AI는 CHEESE Lv.1로 시작합니다. 계정 wallet_gold는 게임 생성 시 차감하지 않습니다.
치즈 이동 능력은 Lv.1~5에 30/35/40/45/50G이며, 출발 통과·도착은 별도 200G입니다.
이동할 때 남은 턴은 차감하지 않습니다. 정답·구매·차례 종료 서버 API는 아직 없습니다.
다른 캐릭터 이동 능력은 ABILITY_UNAVAILABLE로 거절하고 이동·재화를 저장하지 않습니다.
황금열쇠는 EVENT_RULES_UNAVAILABLE로 거절하고 상태를 저장하지 않습니다.
무인도는 도착 상태와 다음 차례 대기 1회를 기록하지만 대기 처리 API는 아직 없습니다.
AI 선공이면 사용자 rolls는 거절됩니다. AI 이동은 별도 ai/rolls 경로로 처리합니다. AI 퀴즈 판정·구매·차례 종료는 아직 지원하지 않습니다.
따라서 이 서버 구현만으로 전체 게임을 완주할 수는 없습니다.

## 퀴즈 저장소

문항 원본·버전·정답·출처를 분리한 quiz-db-draft.sql 구조를 사용합니다. 상세 등록 방법은 quiz-db-design.md를 참고합니다.
승인된 버전만 출제하고 원본 question_id 기준으로 게임 내 중복을 차단합니다.
사용자가 검수한 기본 금융 문항 30개를 1단계·10개 주제별 3개씩 APPROVED로 등록했습니다. 원본은 supabase/seed-data/basic-finance-30.json, 등록 SQL은 docs/api/basic-finance-30-import.sql입니다. 정답이 포함된 원본 JSON은 서버 자료이며 Android assets나 res에 넣지 않습니다. 승인된 해당 주제·단계 문항이 없으면 QUIZ_POOL_EXHAUSTED입니다.
발급한 문제·보기는 공개 이력에, 정답·해설은 비공개 이력에 같은 트랜잭션으로 고정합니다.
응답에는 정답·해설을 넣지 않습니다. 같은 게임에서 이미 쓴 원본은 버전이 달라도 다시 내지 않습니다.
현재 선택은 ID 순서이며 뉴스 혼합·오답 복습 우선순위는 후속 콘텐츠 기능입니다.
문항 검증은 보기 ID 1~4·텍스트·단일 정답을 포함하며 실제 콘텐츠 검수도 별도로 필요합니다.

## 검증과 한계

문항 구조 통합 검증 결과는 game-db-validation.md를 참고합니다. HTTP 어댑터 테스트도 함께 실행합니다.
DB 테스트는 기존 스키마를 재현하고 service_role 호출을 수행했습니다.
HTTP 테스트는 Auth·PostgREST의 네트워크 응답을 모사했습니다.
실제 사용자 JWT로 배포된 함수와 Data API를 거쳐 게임 생성·선공 결정이 성공했습니다. 실제 주사위 이동·다중 연결 동시성은 아직 검증하지 않았습니다.
Android debug 화면과 INTERNET 권한을 추가했습니다. 기존 로그인 화면과 팀원 보드 화면에는 연결하지 않았습니다.

재실행:
cd tools/db-schema
npm install
npm test

## 실제 프로젝트 적용·확인 결과

- 기존 users·characters·user_characters는 유지하고 게임·문항 테이블 12개를 추가했습니다.
- 새 테이블 12개의 RLS 활성화와 RPC 실행 권한(anon·authenticated 불가, service_role 가능)을 확인했습니다.
- 기본 금융 문항 30개가 10개 주제별 APPROVED 3개로 조회됐습니다.
- finpop-api 배포 성공과 서버 코드 실행을 확인했습니다.
- 실제 게임 생성 후 INITIATIVE_PENDING과 게임 ID 발급을 확인했습니다.
- 선공 결정 HTTP 200 이후 TURN_READY·현재 차례 P2(AI)를 확인했습니다.
- 함수 이름 접두사가 남는 배포 경로를 처리하도록 handler.mjs를 수정했습니다. 전체 경로·함수 접두사 경로·직접 경로를 포함한 HTTP 테스트 18개가 통과했습니다.
- AI 선공 상태에서 사용자 주사위 버튼이 비활성화됐습니다. AI 차례 API가 없어 실제 이동 검증은 진행하지 못했습니다.

## debug 테스트와 남은 작업

FinPOP 게임 테스트 → 실제 서버 테스트로 이동에서 프로젝트 공개 키와 테스트 사용자 access_token을 입력합니다.
인증 정보는 화면 메모리에만 보관하며 토큰·비밀번호·service_role 키를 소스나 문서에 기록하지 않습니다.
같은 요청 재시도는 원래 본문과 요청 키를 사용합니다.

1. 사용자 차례에서 실제 주사위 값·이동 경로·퀴즈 발급과 상태 재조회를 검증합니다.
2. AI 차례, 퀴즈 답변·구매·차례 종료 서버 API를 구현합니다.
3. 최신 팀 보드 코드의 칸 ID를 확인하고 상태·주사위 콜백을 별도 연결 코드로 통합합니다.
4. 공통 원격 Repository와 팀원의 실제 로그인 세션을 연결합니다.
5. 실제 서버에서 중복 요청·상태 충돌·타인 게임 접근·복원·동시성을 검증합니다.

공식 참고:
- https://supabase.com/docs/guides/functions/auth-headers
- https://supabase.com/docs/guides/database/functions
- https://supabase.com/docs/guides/functions/secrets



## AI 이동 업데이트 (로컬 구현 완료, 원격 적용 전)

사용자·AI 이동은 같은 RPC 로직을 사용하며 ai-roll 연산은 P2 차례에만 허용합니다.
요청 본문은 expectedVersion만 받고, 인증된 게임 소유자와 요청 키·상태 버전을 검사합니다.
응답에는 playerId, diceValue, movementPath, events, game이 포함됩니다.
퀴즈는 이동한 참가자에게 발급되며 정답·해설은 이동 응답에 포함하지 않습니다.

기존 프로젝트 적용 순서:
1. SQL Editor에서 docs/api/game-ai-movement-update.sql 전체를 실행합니다.
2. Edge Functions → finpop-api → handler.mjs를 로컬 최신 파일 전체로 교체하고 Deploy updates를 누릅니다.
3. Android debug 앱을 다시 빌드·실행하고 기존 게임 ID를 조회합니다.
4. 서버 상태로 보드 열기를 누르면 TURN_READY·P2에서 게임·버전별 한 번 자동 이동을 요청합니다.
5. 응답 유실 시 같은 요청 재시도, 확정된 HTTP 오류 해결 후에는 AI 이동 요청 다시 시도를 사용합니다.

업데이트 SQL은 기존 계정·문항·게임을 변경하지 않고 public.finpop_game_action만 교체하며 서버 전용 권한을 유지합니다.
이미 적용한 프로젝트에서 game-quiz-setup.sql이나 문항 등록 SQL을 다시 실행하지 않습니다.
새 프로젝트용 game-quiz-setup.sql에도 같은 AI 이동 함수를 반영했습니다.
팀원 보드 원본은 수정하지 않고 debug 연결 파일에서 위치와 주사위 색상을 변환합니다.
황금열쇠 도착은 기존과 같이 EVENT_RULES_UNAVAILABLE로 전체 이동을 롤백합니다.
AI가 퀴즈에 도착하면 QUIZ_OPEN, 퀴즈가 필요 없는 칸이면 TURN_REVIEW에서 멈춥니다.
이번 단계는 AI 이동까지이며 퀴즈 판정·정산·차례 종료·자동 반복 플레이는 후속 작업입니다.
