-- 로컬 검증 전용입니다. 운영 문항으로 사용하지 않습니다.
insert into public.quiz_questions(id,topic,source_type) values('baseline-q','STOCK','BASIC');
insert into public.quiz_sources(id,title,url)
values('bbbbbbbb-bbbb-4bbb-8bbb-bbbbbbbbbbb1','테스트 출처','https://example.com/fixture');
insert into public.quiz_question_versions(id,question_id,version,stage,question,choices)
values('99999999-9999-4999-8999-999999999999','baseline-q',1,1,'문제',
'[{"id":1,"text":"가"},{"id":2,"text":"나"},{"id":3,"text":"다"},{"id":4,"text":"라"}]');
insert into finpop_private.quiz_answer_keys(question_version_id,correct_choice_id,explanation)
values('99999999-9999-4999-8999-999999999999',1,'테스트 해설');
insert into public.quiz_version_sources(question_version_id,source_id)
values('99999999-9999-4999-8999-999999999999','bbbbbbbb-bbbb-4bbb-8bbb-bbbbbbbbbbb1');
update public.quiz_question_versions set review_status='APPROVED'
where id='99999999-9999-4999-8999-999999999999';

