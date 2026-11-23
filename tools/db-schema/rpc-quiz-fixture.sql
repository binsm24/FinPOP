-- 로컬 검증 전용입니다. 실제 문항이 아닙니다.
insert into public.quiz_questions(id,topic,source_type) values('rpc-q1','STOCK','BASIC');
insert into public.quiz_question_versions(id,question_id,version,stage,question,choices)
values('aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaa1','rpc-q1',1,1,'테스트 문제',
'[{"id":1,"text":"가"},{"id":2,"text":"나"},{"id":3,"text":"다"},{"id":4,"text":"라"}]');
insert into finpop_private.quiz_answer_keys(question_version_id,correct_choice_id,explanation)
values('aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaa1',1,'비공개 판정 설명');
insert into public.quiz_version_sources(question_version_id,source_id)
values('aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaa1','bbbbbbbb-bbbb-4bbb-8bbb-bbbbbbbbbbb1');
update public.quiz_question_versions set review_status='APPROVED'
where id='aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaa1';

