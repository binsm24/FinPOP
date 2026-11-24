# 문항 저장 구조와 등록 방법

상태: 로컬 SQL 초안·검증 단계. 실제 Supabase 적용과 운영 30문항 등록은 하지 않았습니다.

## 구성

| 테이블 | 주요 정보 |
|---|---|
| public.quiz_questions | 고정 원본 ID, 금융 주제, BASIC/NEWS 분류 |
| public.quiz_question_versions | UUID 버전 ID, 원본 ID, 버전 번호, 학습 단계, 문제·힌트·보기, 검수 상태 |
| finpop_private.quiz_answer_keys | 버전 ID, 정답 보기 ID, 해설 |
| public.quiz_sources | 출처 제목·URL·발행일·조회일 |
| public.quiz_version_sources | 문항 버전과 하나 이상의 출처 연결 |
| 기존 public.game_quiz_attempts | 게임·원본·버전·발급 문제/보기 사본·사용자 응답·판정 |
| 기존 finpop_private.game_quiz_answers | 발급 당시 정답·해설·출처 사본 |

원본 ID는 기존 JSON의 BASIC-STOCK-01 같은 ID를 유지할 수 있습니다.
버전 ID는 UUID이며 (question_id, version)은 중복될 수 없습니다.
game_quiz_attempts의 원본·버전은 외래키로 연결하고 (game_id, question_id)를 유일하게 관리합니다.
따라서 같은 게임에서 버전만 바꿔 같은 문제를 다시 출제할 수 없습니다.

## 검수·수정 규칙

1. 문항 버전은 반드시 DRAFT로 등록합니다.
2. 보기 4개, 고유한 숫자 ID 1~4, 비어 있지 않은 문제·보기·해설을 준비합니다.
3. 비공개 정답과 하나 이상의 출처 연결을 등록합니다.
4. 내용을 사람이 검토한 뒤 APPROVED로 변경합니다. 검토 시각은 DB가 기록합니다.
5. 문제 수정 시 새 DRAFT 버전을 만듭니다. 승인된 문제·정답·출처 연결을 덮어쓰지 않습니다.
6. 새 버전 승인 시 같은 트랜잭션에서 이전 버전을 RETIRED로 바꾸고 새 버전을 APPROVED로 바꿉니다.
7. 원본당 승인 버전은 하나만 허용합니다. RETIRED를 다시 승인하거나 편집할 수 없습니다.

원본 분류와 출처 레코드는 추가 전용입니다.
분류나 출처 자체를 수정해야 한다면 새 레코드를 만들고 새 문항 버전에 연결합니다.
승인된 출처 연결을 삭제하거나 교체할 수 없으므로 과거 콘텐츠의 근거가 유지됩니다.
APPROVED는 DB가 사실의 정확성을 보증하는 상태가 아니라 팀 검수 완료 표시입니다.

## 등록 예시

아래는 입력 형식을 설명하는 예시이며 운영 문항이 아닙니다.

원본:
{"id":"BASIC-STOCK-01","topic":"STOCK","source_type":"BASIC"}

버전:
{
  "question_id":"BASIC-STOCK-01",
  "version":1,
  "stage":1,
  "question":"주식은 일반적으로 무엇을 나타내나요?",
  "hint":"회사의 소유와 관련된 개념입니다.",
  "choices":[
    {"id":1,"text":"회사의 지분"},
    {"id":2,"text":"예금 만기일"},
    {"id":3,"text":"카드 결제일"},
    {"id":4,"text":"은행 영업시간"}
  ]
}

정답은 별도 비공개 행에 question_version_id와 correct_choice_id:1, explanation을 기록합니다.
기존 JSON이 A/B/C/D ID라면 등록 시 A→1, B→2, C→3, D→4로 문제·정답을 함께 변환합니다.
배열 순서를 바꿔도 보기 ID 자체는 바꾸지 않습니다.

## 출제·API 연결

서버는 원본 주제 + 해당 학습 단계 + APPROVED 버전만 조회합니다.
한 게임에서 이미 사용한 question_id는 제외합니다.
선택한 문제·보기와 정답·해설·출처는 출제 트랜잭션에서 각각 사본으로 저장합니다.
API에는 문제·보기·힌트·마감만 전달하고, 정답과 해설은 판정 후 별도 응답으로 전달해야 합니다.
게임의 퀴즈 응답 판정 서버 API는 아직 후속 작업입니다.

출처는 한 문항에 여러 개를 연결할 수 있습니다.
출제 당시 비공개 source 사본은 출처 객체 배열입니다. 향후 판정·보관함 API에서 형식을 맞춰 전달합니다.
BASIC/NEWS는 원본 종류이고 오답 복습은 출제 전략입니다.
복습 선택·뉴스 혼합·무작위 문항 선택 로직은 이번 저장 구조 구현에 포함하지 않습니다.
현재 서버는 승인된 정확한 단계 문항을 ID 순으로 선택합니다. 해당 단계의 문항이 없으면 명확하게 실패합니다.

## 접근과 적용 순서

앱에는 카탈로그 전체 조회·쓰기 권한을 부여하지 않았습니다.
서버만 카탈로그를 조회·관리하고 앱은 본인에게 발급된 문제 사본만 조회합니다.
정답 테이블은 Exposed schemas에 추가하지 않는 finpop_private에 있습니다.

빈 테스트 DB에서 기존 users·characters·user_characters가 준비된 상태로 다음 순서를 사용합니다.

1. docs/api/quiz-db-draft.sql
2. docs/api/game-db-draft.sql
3. docs/api/game-rpc-draft.sql

이 초안들은 아직 원격에 적용하지 않았으므로 기존 초안 파일을 함께 수정했습니다.
이전 초안을 이미 적용한 별도 환경이 있다면 그대로 재실행하지 말고 별도 변경 마이그레이션을 작성해야 합니다.
테스트 fixture SQL은 실행 때 로컬 메모리 DB에만 사용하며 운영 문항 등록에 사용하지 않습니다.

## 검증

Node.js/npm 환경의 tools/db-schema에서 npm install 후 npm test로 재현합니다.
PGlite의 PostgreSQL 엔진에서 카탈로그 접근, 승인 전 정답·출처 확인, 게시 후 불변성,
버전별 이력, 원본 중복 출제 차단, 서버 출제·과거 사본 보존을 검증합니다.
실제 Supabase Auth·Deno 배포·다중 연결 동시성·30문항 콘텐츠 검수는 별도입니다.

