/** 문항 카탈로그·검수·게시 후 불변성과 원본 중복 출제를 검증합니다. */
export async function validateQuiz({db,test,reject,role,ids}) {
 const v1="99999999-9999-4999-8999-999999999999";
 const v2="99999999-9999-4999-8999-999999999992";
 const draft="99999999-9999-4999-8999-999999999993";
 const source="bbbbbbbb-bbbb-4bbb-8bbb-bbbbbbbbbbb1";
 const choices='[{"id":1,"text":"가"},{"id":2,"text":"나"},{"id":3,"text":"다"},{"id":4,"text":"라"}]';
 for(const table of ["public.quiz_questions","public.quiz_question_versions","public.quiz_sources","public.quiz_version_sources","finpop_private.quiz_answer_keys"]){
  await test(table+" 앱 카탈로그 직접 조회 차단",()=>role("authenticated",ids.u1,()=>reject("select * from "+table,"42501")));
 }
 await test("승인 문항 내용 수정 차단",()=>reject("update public.quiz_question_versions set question='수정' where id='"+v1+"'","P0001"));
 await test("승인 정답 수정 차단",()=>reject("update finpop_private.quiz_answer_keys set correct_choice_id=2 where question_version_id='"+v1+"'","P0001"));
 await test("승인 정답 삭제 차단",()=>reject("delete from finpop_private.quiz_answer_keys where question_version_id='"+v1+"'","P0001"));
 await test("승인 출처 연결 삭제 차단",()=>reject("delete from public.quiz_version_sources where question_version_id='"+v1+"'","P0001"));
 await test("출처 내용 덮어쓰기 차단",()=>reject("update public.quiz_sources set url='https://example.com/changed' where id='"+source+"'","P0001"));
 await test("원본 주제 변경 차단",()=>reject("update public.quiz_questions set topic='BOND' where id='baseline-q'","P0001"));
 await test("승인 문항 삭제 차단",()=>reject("delete from public.quiz_question_versions where id='"+v1+"'","P0001"));
 await test("승인 상태로 직접 등록 차단",()=>reject("insert into public.quiz_question_versions(question_id,version,stage,question,choices,review_status,reviewed_at) values('baseline-q',100,1,'문제','"+choices+"','APPROVED',now())","P0001"));
 await db.exec("insert into public.quiz_questions(id,topic,source_type) values('catalog-draft','BOND','BASIC')");
 await db.query("insert into public.quiz_question_versions(id,question_id,version,stage,question,choices) values($1,'catalog-draft',1,1,'초안 문제',$2::jsonb)",[draft,choices]);
 await test("정답 없는 문항 승인 차단",()=>reject("update public.quiz_question_versions set review_status='APPROVED' where id='"+draft+"'","P0001"));
 await db.query("insert into finpop_private.quiz_answer_keys values($1,2,'초안 해설')",[draft]);
 await test("출처 없는 문항 승인 차단",()=>reject("update public.quiz_question_versions set review_status='APPROVED' where id='"+draft+"'","P0001"));
 await test("초안 직접 폐기 상태 전환 차단",()=>reject("update public.quiz_question_versions set review_status='RETIRED' where id='"+draft+"'","P0001"));
 await test("빈 보기 본문 차단",()=>reject("update public.quiz_question_versions set choices='[{\"id\":1,\"text\":\"\"},{\"id\":2,\"text\":\"나\"},{\"id\":3,\"text\":\"다\"},{\"id\":4,\"text\":\"라\"}]' where id='"+draft+"'","P0001"));
 await test("중복 보기 ID 차단",()=>reject("update public.quiz_question_versions set choices='[{\"id\":1,\"text\":\"가\"},{\"id\":1,\"text\":\"나\"},{\"id\":3,\"text\":\"다\"},{\"id\":4,\"text\":\"라\"}]' where id='"+draft+"'","23514"));
 await test("잘못된 정답 ID 차단",()=>reject("update finpop_private.quiz_answer_keys set correct_choice_id=5 where question_version_id='"+draft+"'","23514"));
 await test("초안 편집 가능",async()=>{
  await db.exec("begin");
  try{await db.query("update public.quiz_question_versions set question='고친 초안' where id=$1",[draft]);
   await db.query("update finpop_private.quiz_answer_keys set explanation='고친 해설' where question_version_id=$1",[draft]);
   const row=(await db.query("select question from public.quiz_question_versions where id=$1",[draft])).rows[0];
   if(row.question!=="고친 초안")throw new Error("초안 수정 실패");
  }finally{await db.exec("rollback");}
 });
 await db.query("insert into public.quiz_version_sources values($1,$2)",[draft,source]);
 await test("정답·출처 확인 후 승인과 검토 시각 기록",async()=>{
  await db.query("update public.quiz_question_versions set review_status='APPROVED' where id=$1",[draft]);
  const row=(await db.query("select review_status,reviewed_at from public.quiz_question_versions where id=$1",[draft])).rows[0];
  if(row.review_status!=="APPROVED"||!row.reviewed_at)throw new Error("승인 기록 누락");
 });
 await db.query("insert into public.quiz_question_versions(id,question_id,version,stage,question,choices) values($1,'baseline-q',2,1,'문제 두 번째 버전',$2::jsonb)",[v2,choices]);
 await db.query("insert into finpop_private.quiz_answer_keys values($1,2,'두 번째 해설')",[v2]);
 await db.query("insert into public.quiz_version_sources values($1,$2)",[v2,source]);
 await test("원본당 동시 승인 버전 중복 차단",()=>reject("update public.quiz_question_versions set review_status='APPROVED' where id='"+v2+"'","23505"));
 await db.query("update public.quiz_question_versions set review_status='RETIRED' where id=$1",[v1]);
 await db.query("update public.quiz_question_versions set review_status='APPROVED' where id=$1",[v2]);
 await test("폐기 버전 재승인 차단",()=>reject("update public.quiz_question_versions set review_status='APPROVED' where id='"+v1+"'","P0001"));
 await test("폐기 버전 편집 차단",()=>reject("update public.quiz_question_versions set hint='변경' where id='"+v1+"'","P0001"));
 await test("다른 버전이어도 같은 게임의 원본 재출제 차단",()=>reject(
  "update public.game_quiz_attempts set resolution='TIMEOUT',resolved_at=now() where id='@a1'; "+
  "insert into public.game_quiz_attempts(game_id,turn_id,player_id,question_id,question_version_id,tile_id,topic,source_type,stage,question,choices,issued_at,deadline_at) "+
  "select game_id,turn_id,player_id,question_id,'"+v2+"',tile_id,topic,source_type,stage,question,choices,issued_at,deadline_at from public.game_quiz_attempts where id='@a1'","23505"));
 await test("새 버전 승인 후 과거 문제·정답 사본 보존",async()=>{
  const row=(await db.query("select a.question,s.correct_choice_id,s.explanation from public.game_quiz_attempts a join finpop_private.game_quiz_answers s on s.attempt_id=a.id where a.id=$1",[ids.a1])).rows[0];
  if(row.question!=="문제"||row.correct_choice_id!==1||row.explanation!=="비공개 해설")throw new Error("과거 사본 변경");
 });
 await test("문항 버전과 원본 ID 불일치 차단",()=>reject("update public.game_quiz_attempts set question_id='catalog-draft' where id='@a1'","23503"));
 await test("문항 원본과 토픽 불일치 차단",()=>reject("update public.game_quiz_attempts set topic='BOND' where id='@a1'","23503"));
}

