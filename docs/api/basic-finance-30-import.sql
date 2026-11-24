-- 사용자 검토가 끝난 기본 금융 30문항 등록용입니다. 기존 데이터는 수정하지 않습니다.
-- DRAFT 등록 → 정답·출처 연결 → APPROVED 전환을 한 트랜잭션에서 수행합니다.
-- 원본 파일 SHA-256: 46a1e3e7e978dbfecbcb48c3e0d78b80d6773b037c8807879e92df344516738b
-- A/B/C/D → 1/2/3/4, 한국어 주제 → API 코드, BASIC·학습 단계 1로 변환합니다.
-- retrieved_at은 이 파일의 DB 등록 시각입니다. 출처 원문을 새로 조회한 시각이 아닙니다.
-- 원본의 publisher·scope·검토 상태 등 전체 메타데이터는 seed-data JSON에 보존합니다.
begin;
set local lock_timeout='5s';
set local statement_timeout='120s';
select pg_advisory_xact_lock(hashtextextended('finpop-basic-finance-30-import-v1',0));
do $guard$ begin
 if exists(select 1 from public.quiz_questions where id in ('BASIC-STOCK-01','BASIC-STOCK-02','BASIC-STOCK-03','BASIC-BOND-01','BASIC-BOND-02','BASIC-BOND-03','BASIC-MATURITY-01','BASIC-MATURITY-02','BASIC-MATURITY-03','BASIC-COMPOUND-01','BASIC-COMPOUND-02','BASIC-COMPOUND-03','BASIC-DIVIDEND-01','BASIC-DIVIDEND-02','BASIC-DIVIDEND-03','BASIC-DEPOSIT-01','BASIC-DEPOSIT-02','BASIC-DEPOSIT-03','BASIC-INSTALLMENT-01','BASIC-INSTALLMENT-02','BASIC-INSTALLMENT-03','BASIC-LOAN-01','BASIC-LOAN-02','BASIC-LOAN-03','BASIC-ASSET-01','BASIC-ASSET-02','BASIC-ASSET-03','BASIC-FUND-01','BASIC-FUND-02','BASIC-FUND-03')) then
   raise exception '기본 문항 ID가 이미 있습니다. 덮어쓰거나 중복 등록하지 않습니다.'; end if;
 if exists(select 1 from public.quiz_sources where id in ('76c2df5d-9ac0-872f-aab6-93cc18bb20c6'::uuid,'a97309af-b364-865d-ac2c-b9bb3b46fc2e'::uuid,'9ad76678-b079-8ae6-8cc7-055d6a5bded4'::uuid,'ca749320-75ce-85ab-86f0-46ef4a05c9a4'::uuid,'6237e05c-1fb3-8ca9-ac04-9600848c9277'::uuid,'86cbd6e2-4516-8693-8455-5931c6574ff2'::uuid,'9690d50a-543c-8866-9d23-d5cd4f28d314'::uuid,'adf1e97b-270c-8893-a3fe-9cd7af708119'::uuid,'a2e3a5ba-3816-8476-992e-9568aaaa4d56'::uuid,'57d03143-3e38-813a-b38d-8382e5f1a89d'::uuid,'bb70b11c-3717-8fcb-98d5-ad537cf53325'::uuid,'11f95a38-8c06-8f4f-a0f3-712ef970c3ea'::uuid,'1a4d906d-a0fc-83c6-9095-5dbae4c94101'::uuid)) then
   raise exception '출처 ID가 이미 있습니다. 등록 상태를 먼저 확인하세요.'; end if;
end $guard$;
insert into public.quiz_sources(id,title,url,published_at) values
  ('76c2df5d-9ac0-872f-aab6-93cc18bb20c6','미국 증권거래위원회(SEC), Investor.gov · Stocks - FAQs','https://www.investor.gov/introduction-investing/investing-basics/investment-products/stocks',null),
  ('a97309af-b364-865d-ac2c-b9bb3b46fc2e','미국 증권거래위원회(SEC), Investor.gov · Bonds - FAQs','https://www.investor.gov/introduction-investing/investing-basics/investment-products/bonds-or-fixed-income-products/bonds',null),
  ('9ad76678-b079-8ae6-8cc7-055d6a5bded4','미국 증권거래위원회(SEC), Investor.gov · Certificates of Deposit (CDs)','https://www.investor.gov/introduction-investing/investing-basics/investment-products/certificates-deposit-cds',null),
  ('ca749320-75ce-85ab-86f0-46ef4a05c9a4','미국 소비자금융보호국(CFPB) · How does compound interest work?','https://www.consumerfinance.gov/ask-cfpb/how-does-compound-interest-work-en-1683/',null),
  ('6237e05c-1fb3-8ca9-ac04-9600848c9277','저축은행중앙회 · 상품공시 - 정기예금','https://fsb.or.kr/ratedepo_0100.act',null),
  ('86cbd6e2-4516-8693-8455-5931c6574ff2','인성저축은행 · 정기적금 상품안내','https://insung.ibs.fsb.or.kr/ProdList_001.act?rnum=6',null),
  ('9690d50a-543c-8866-9d23-d5cd4f28d314','미국 연방예금보험공사(FDIC) · Loans','https://www.fdic.gov/consumer-resource-center/loans',null),
  ('adf1e97b-270c-8893-a3fe-9cd7af708119','미국 증권거래위원회(SEC), Investor.gov · Glossary - Asset / Principal','https://www.investor.gov/introduction-investing/investing-basics/glossary',null),
  ('a2e3a5ba-3816-8476-992e-9568aaaa4d56','미국 증권거래위원회(SEC), Investor.gov · Figure Out Your Finances','https://www.investor.gov/introduction-investing/investing-basics/save-and-invest/figure-out-your-finances',null),
  ('57d03143-3e38-813a-b38d-8382e5f1a89d','미국 증권거래위원회(SEC), Investor.gov · Mutual Funds','https://www.investor.gov/introduction-investing/investing-basics/investment-products/mutual-funds-and-exchange-traded-funds-etfs/mutual-funds',null),
  ('bb70b11c-3717-8fcb-98d5-ad537cf53325','미국 증권거래위원회(SEC), Investor.gov · Asset Allocation and Diversification','https://www.investor.gov/introduction-investing/getting-started/asset-allocation',null),
  ('11f95a38-8c06-8f4f-a0f3-712ef970c3ea','저축은행중앙회 · 상품공시 이용방법안내','https://www.fsb.or.kr/ratuserguide_0100.act',null),
  ('1a4d906d-a0fc-83c6-9095-5dbae4c94101','미국 소비자금융보호국(CFPB) · How does paying down a mortgage work?','https://www.consumerfinance.gov/ask-cfpb/how-does-paying-down-a-mortgage-work-en-1943/',null);
insert into public.quiz_questions(id,topic,source_type) values
  ('BASIC-STOCK-01','STOCK','BASIC'),
  ('BASIC-STOCK-02','STOCK','BASIC'),
  ('BASIC-STOCK-03','STOCK','BASIC'),
  ('BASIC-BOND-01','BOND','BASIC'),
  ('BASIC-BOND-02','BOND','BASIC'),
  ('BASIC-BOND-03','BOND','BASIC'),
  ('BASIC-MATURITY-01','MATURITY','BASIC'),
  ('BASIC-MATURITY-02','MATURITY','BASIC'),
  ('BASIC-MATURITY-03','MATURITY','BASIC'),
  ('BASIC-COMPOUND-01','COMPOUND','BASIC'),
  ('BASIC-COMPOUND-02','COMPOUND','BASIC'),
  ('BASIC-COMPOUND-03','COMPOUND','BASIC'),
  ('BASIC-DIVIDEND-01','DIVIDEND','BASIC'),
  ('BASIC-DIVIDEND-02','DIVIDEND','BASIC'),
  ('BASIC-DIVIDEND-03','DIVIDEND','BASIC'),
  ('BASIC-DEPOSIT-01','DEPOSIT','BASIC'),
  ('BASIC-DEPOSIT-02','DEPOSIT','BASIC'),
  ('BASIC-DEPOSIT-03','DEPOSIT','BASIC'),
  ('BASIC-INSTALLMENT-01','SAVINGS','BASIC'),
  ('BASIC-INSTALLMENT-02','SAVINGS','BASIC'),
  ('BASIC-INSTALLMENT-03','SAVINGS','BASIC'),
  ('BASIC-LOAN-01','LOAN','BASIC'),
  ('BASIC-LOAN-02','LOAN','BASIC'),
  ('BASIC-LOAN-03','LOAN','BASIC'),
  ('BASIC-ASSET-01','ASSET','BASIC'),
  ('BASIC-ASSET-02','ASSET','BASIC'),
  ('BASIC-ASSET-03','ASSET','BASIC'),
  ('BASIC-FUND-01','FUND','BASIC'),
  ('BASIC-FUND-02','FUND','BASIC'),
  ('BASIC-FUND-03','FUND','BASIC');
insert into public.quiz_question_versions(id,question_id,version,stage,question,hint,choices) values
  ('57aa296a-7d46-8644-b99e-29317ba312bc','BASIC-STOCK-01',1,1,'주식을 산다는 것은 기본적으로 무엇을 뜻할까요?','주주가 회사와 어떤 관계를 맺는지 생각해 보세요.','[{"id":1,"text":"회사에 돈을 빌려주고 상환을 약속받는 것"},{"id":2,"text":"회사의 소유 지분 일부를 갖는 것"},{"id":3,"text":"은행에 정해진 기간 돈을 맡기는 것"},{"id":4,"text":"보험 사고가 나면 보상받을 권리를 사는 것"}]'::jsonb),
  ('76cf08df-21cc-897f-a6bc-b842a949495b','BASIC-STOCK-02',1,1,'일반적인 주식 투자에 대한 설명으로 맞는 것은?','회사의 지분 가격이 달라지면 내 투자금의 가치도 달라져요.','[{"id":1,"text":"보유 기간이 길면 원금이 보장된다"},{"id":2,"text":"유명한 회사의 주가는 하락하지 않는다"},{"id":3,"text":"수익과 관계없이 배당이 매달 보장된다"},{"id":4,"text":"주가가 하락하면 투자 손실이 날 수 있다"}]'::jsonb),
  ('8fa1e2a0-a192-8396-a66f-c7e8db2d630d','BASIC-STOCK-03',1,1,'1주에 1만 원인 주식 2주를 사서 1주에 1만 2천 원에 모두 팔았어요. 매매 차익은? (세금·수수료 제외)','한 주의 가격 차이에 보유 주식 수를 곱해 보세요.','[{"id":1,"text":"4,000원"},{"id":2,"text":"2,000원"},{"id":3,"text":"20,000원"},{"id":4,"text":"24,000원"}]'::jsonb),
  ('2994e1cc-9fbf-827f-82e9-fc4564a30519','BASIC-BOND-01',1,1,'채권을 산 투자자는 발행자와 기본적으로 어떤 관계가 될까요?','채권 발행자는 투자자에게 돈을 조달하고 상환을 약속해요.','[{"id":1,"text":"회사 지분을 소유한 주주"},{"id":2,"text":"발행자의 상품을 대신 파는 판매자"},{"id":3,"text":"발행자에게 돈을 빌려준 채권자"},{"id":4,"text":"발행자의 손실을 보상하는 보험사"}]'::jsonb),
  ('fff51e16-3ebf-899a-bb93-64e59f4b143a','BASIC-BOND-02',1,1,'회사가 발행한 채권의 위험에 대한 설명으로 맞는 것은?','상환을 약속한 상대방이 약속을 지킬 수 있는지도 중요해요.','[{"id":1,"text":"채권이면 발행 회사와 관계없이 원금이 보장된다"},{"id":2,"text":"발행 회사가 돈을 갚지 못할 위험이 있다"},{"id":3,"text":"주가가 오르면 채권 원금도 자동으로 늘어난다"},{"id":4,"text":"만기 전에 팔아도 매입 가격이 보장된다"}]'::jsonb),
  ('4be484e7-1c4f-878c-814b-3bc8a20bcf51','BASIC-BOND-03',1,1,'다른 조건이 같을 때 시장금리가 오르면, 이미 발행된 고정금리 채권의 가격은 일반적으로?','새 채권이 더 높은 이자를 준다면 기존 채권의 매력은 어떻게 될까요?','[{"id":1,"text":"오른다"},{"id":2,"text":"항상 그대로다"},{"id":3,"text":"반드시 0원이 된다"},{"id":4,"text":"내린다"}]'::jsonb),
  ('cfe32125-bb7b-881d-96f5-baa48749ce00','BASIC-MATURITY-01',1,1,'정기예금에서 ‘만기일’은 무엇을 뜻할까요?','가입할 때 약속한 돈을 맡기는 기간을 떠올려 보세요.','[{"id":1,"text":"계약에서 정한 예치 기간이 끝나는 날"},{"id":2,"text":"계좌 비밀번호를 바꾸는 날"},{"id":3,"text":"예금 금리가 매일 바뀌는 시각"},{"id":4,"text":"예금 가입을 신청할 수 있는 마지막 날"}]'::jsonb),
  ('6ab8a89d-d3db-8224-a7c1-a213d9ba3787','BASIC-MATURITY-02',1,1,'정기예금을 만기 전에 해지하려고 해요. 먼저 확인할 항목은?','약속한 기간을 채우지 않았을 때 달라지는 계약 조건을 찾아보세요.','[{"id":1,"text":"은행 로고의 색상"},{"id":2,"text":"주식시장의 거래량"},{"id":3,"text":"중도해지 시 적용되는 이율과 조건"},{"id":4,"text":"다른 사람의 계좌번호"}]'::jsonb),
  ('8ccfcf1e-92a5-8bc0-808b-31ecd8273cd1','BASIC-MATURITY-03',1,1,'원금 100만 원과 세전 이자 3만 원을 만기일에 한꺼번에 받는 예금이에요. 세금이 없다고 가정한 만기 수령액은?','이자를 포함한 전체 금액을 묻는 문제예요.','[{"id":1,"text":"97만 원"},{"id":2,"text":"103만 원"},{"id":3,"text":"100만 원"},{"id":4,"text":"3만 원"}]'::jsonb),
  ('069baf4f-7361-8be9-86b8-bdebb4b492ef','BASIC-COMPOUND-01',1,1,'복리 방식의 특징으로 맞는 것은?','이자가 다음 기간의 계산 밑천에 포함되는지 생각해 보세요.','[{"id":1,"text":"처음 맡긴 원금에만 계속 이자가 붙는다"},{"id":2,"text":"이자를 받으면 원금이 그만큼 줄어든다"},{"id":3,"text":"기간과 관계없이 이자가 한 번만 붙는다"},{"id":4,"text":"원금에 더해진 이자에도 다음 이자가 붙는다"}]'::jsonb),
  ('9e92313f-5833-8b47-96cb-6c91c88bde9b','BASIC-COMPOUND-02',1,1,'100만 원을 연 10%, 1년마다 복리로 2년간 맡겨요. 세금·수수료가 없고 이자를 인출하지 않으면 최종 금액은?','첫해 이자를 더한 잔액에 둘째 해 금리를 적용해 보세요.','[{"id":1,"text":"121만 원"},{"id":2,"text":"120만 원"},{"id":3,"text":"110만 원"},{"id":4,"text":"200만 원"}]'::jsonb),
  ('4231d446-2ba0-8d9c-9656-f9812386baad','BASIC-COMPOUND-03',1,1,'100만 원을 연 10%로 2년간 맡겨요. 연 단리 이자는 20만 원, 연 복리 이자는 21만 원이에요. 복리 이자가 더 큰 이유는?','둘째 해 이자를 계산할 때 기준 금액이 어떻게 다른지 보세요.','[{"id":1,"text":"복리에는 세금이 없기 때문"},{"id":2,"text":"단리는 둘째 해 이자가 없기 때문"},{"id":3,"text":"첫해 이자에도 둘째 해 이자가 붙기 때문"},{"id":4,"text":"복리는 원금을 두 번 입금하기 때문"}]'::jsonb),
  ('b61c51a4-88f2-803c-86d9-4b61aaa6cf6a','BASIC-DIVIDEND-01',1,1,'주식의 ‘배당’에 대한 설명으로 맞는 것은?','배당은 주주라는 지위와 관련된 분배예요.','[{"id":1,"text":"주식을 팔 때 증권사에 내는 비용"},{"id":2,"text":"회사가 이익 등을 주주에게 나누어 주는 것"},{"id":3,"text":"채권 만기에 돌려받는 원금"},{"id":4,"text":"은행에 돈을 빌리며 내는 이자"}]'::jsonb),
  ('e04356f0-7264-8d4c-89e0-77cb6bc7c5ae','BASIC-DIVIDEND-02',1,1,'일반적인 보통주의 배당에 대한 설명으로 맞는 것은?','배당과 예금의 약정 이자가 같은 성격인지 생각해 보세요.','[{"id":1,"text":"모든 회사가 매달 같은 금액을 지급한다"},{"id":2,"text":"주식을 산 가격만큼 매년 지급한다"},{"id":3,"text":"작년에 지급했으면 올해도 같은 금액이 보장된다"},{"id":4,"text":"회사의 사정과 결정에 따라 지급하지 않을 수 있다"}]'::jsonb),
  ('ea60c6f7-4792-8f5a-9b36-b1f31adb7f3a','BASIC-DIVIDEND-03',1,1,'현금배당을 받을 권리가 있는 주식 10주를 갖고 있어요. 주당 배당금이 500원이면 세전 배당금 합계는?','주당 금액과 배당받는 주식 수를 함께 보세요.','[{"id":1,"text":"5,000원"},{"id":2,"text":"500원"},{"id":3,"text":"50,000원"},{"id":4,"text":"10,500원"}]'::jsonb),
  ('89726d8d-d9e0-81cc-beca-26e692263cd0','BASIC-DEPOSIT-01',1,1,'일반적인 정기예금의 저축 방식은?','이미 가진 목돈을 굴리는 방식과 조금씩 모으는 방식을 구별해 보세요.','[{"id":1,"text":"은행에서 돈을 빌려 나누어 갚는다"},{"id":2,"text":"주식을 매일 일정 수량씩 산다"},{"id":3,"text":"목돈을 한 번에 맡기고 약정 기간 동안 예치한다"},{"id":4,"text":"매달 같은 금액을 납입해 목돈을 모은다"}]'::jsonb),
  ('939fa5b6-f14e-85ed-a8c3-02d08b005e79','BASIC-DEPOSIT-02',1,1,'100만 원을 연 3% 단리 정기예금에 정확히 1년간 맡겼어요. 세전 이자는?','이자만 구하려면 원금에 연이율과 연 단위 기간을 곱해 보세요.','[{"id":1,"text":"3,000원"},{"id":2,"text":"30,000원"},{"id":3,"text":"300,000원"},{"id":4,"text":"1,030,000원"}]'::jsonb),
  ('b4ac5807-bb8f-8957-ae40-676bfeb410c3','BASIC-DEPOSIT-03',1,1,'같은 금액·기간의 예금 두 개를 비교해요. 광고의 ‘최고금리’ 외에 실제 적용금리를 알려면 무엇을 확인해야 할까요?','조건을 충족해야만 받을 수 있는 금리가 있는지 살펴보세요.','[{"id":1,"text":"광고에 나온 모델"},{"id":2,"text":"통장 표지의 디자인"},{"id":3,"text":"은행 이름의 글자 수"},{"id":4,"text":"기본금리와 내가 충족할 우대금리 조건"}]'::jsonb),
  ('246345dc-ac61-82ae-8d35-8d28c726a6c5','BASIC-INSTALLMENT-01',1,1,'일반적인 정기적금의 납입 방식은?','월급의 일부를 꾸준히 모아 목돈을 만드는 상황을 떠올려 보세요.','[{"id":1,"text":"정해진 기간 동안 매달 일정 금액을 넣는다"},{"id":2,"text":"목돈을 처음에 한 번만 넣는다"},{"id":3,"text":"은행에서 매달 돈을 빌린다"},{"id":4,"text":"주가가 오를 때만 돈을 넣는다"}]'::jsonb),
  ('78481828-4e28-8805-8480-85c1c190cc91','BASIC-INSTALLMENT-02',1,1,'매달 10만 원씩 총 12회 적금에 넣었어요. 이자를 제외한 납입 원금 합계는?','한 번의 납입액에 실제 납입 횟수를 곱해 보세요.','[{"id":1,"text":"10만 원"},{"id":2,"text":"110만 원"},{"id":3,"text":"120만 원"},{"id":4,"text":"132만 원"}]'::jsonb),
  ('49063da0-26e3-8a92-b78e-64b96bcc38a3','BASIC-INSTALLMENT-03',1,1,'매달 납입한 적금에서 금리가 같아도 첫 납입금의 이자가 마지막 납입금보다 보통 더 큰 이유는?','각 납입금이 은행에 머무는 기간을 비교해 보세요.','[{"id":1,"text":"마지막 납입금에는 원금이 없기 때문"},{"id":2,"text":"첫 납입금이 만기까지 더 오래 맡겨져 있기 때문"},{"id":3,"text":"납입할 때마다 금리가 반드시 낮아지기 때문"},{"id":4,"text":"은행이 첫 납입금만 투자하기 때문"}]'::jsonb),
  ('cc2e16d8-9630-8b82-a40c-561bf43a7d11','BASIC-LOAN-01',1,1,'대출에서 ‘원금’이란 무엇일까요?','빌린 금액 자체와 돈을 빌리는 데 드는 비용을 구별해 보세요.','[{"id":1,"text":"돈을 빌리는 대가로 내는 이자"},{"id":2,"text":"연체했을 때 추가로 내는 금액"},{"id":3,"text":"대출을 받으며 내는 수수료"},{"id":4,"text":"이자·수수료와 구별되는 빌린 돈 자체"}]'::jsonb),
  ('1ceade6d-8ac8-8db8-b5e3-888dda184345','BASIC-LOAN-02',1,1,'100만 원을 연 5% 단리로 정확히 1년 빌리고 원금은 만기에 갚아요. 중간 상환·수수료가 없을 때 1년 이자는?','원금이 1년 동안 그대로 유지된다는 조건을 활용해 보세요.','[{"id":1,"text":"50,000원"},{"id":2,"text":"5,000원"},{"id":3,"text":"500,000원"},{"id":4,"text":"1,050,000원"}]'::jsonb),
  ('b596c224-7b16-8ec5-ab0b-04f3cc7b2c68','BASIC-LOAN-03',1,1,'원금과 대출 기간이 같은 두 대출의 총비용을 비교하려고 해요. 적절한 방법은?','표시된 금리 외에 실제로 내는 돈에 영향을 주는 조건을 찾아보세요.','[{"id":1,"text":"광고의 글자 크기만 비교한다"},{"id":2,"text":"금리가 낮으면 다른 조건은 보지 않는다"},{"id":3,"text":"이자뿐 아니라 수수료와 상환 방식도 확인한다"},{"id":4,"text":"월 납입액이 작으면 총비용도 항상 작다고 본다"}]'::jsonb),
  ('7fef53f9-47df-86e8-bb44-ae0e915fb0a7','BASIC-ASSET-01',1,1,'개인이 보유한 자산에 해당하는 것은?','보유한 경제적 가치와 갚아야 할 의무를 나누어 보세요.','[{"id":1,"text":"아직 갚지 않은 대출금"},{"id":2,"text":"본인 명의의 은행 예금 잔액"},{"id":3,"text":"다음 달 갚아야 할 카드 대금"},{"id":4,"text":"친구에게 빌려서 갚아야 할 돈"}]'::jsonb),
  ('ed0ea2bf-e8bd-8ffd-9f07-1f917a319f1d','BASIC-ASSET-02',1,1,'총자산이 500만 원이고 부채가 200만 원이에요. 순자산은?','순자산은 보유 자산에서 갚아야 할 금액을 뺀 값이에요.','[{"id":1,"text":"700만 원"},{"id":2,"text":"500만 원"},{"id":3,"text":"200만 원"},{"id":4,"text":"300만 원"}]'::jsonb),
  ('2d178e2e-11cb-8bf7-b0c0-d74ce66fa0f8','BASIC-ASSET-03',1,1,'대출 100만 원을 받아 전액을 내 예금계좌에 넣었어요. 다른 변화와 비용이 없다면 순자산은?','예금 증가와 함께 갚아야 할 돈도 얼마나 늘었는지 보세요.','[{"id":1,"text":"변하지 않는다"},{"id":2,"text":"100만 원 늘어난다"},{"id":3,"text":"100만 원 줄어든다"},{"id":4,"text":"200만 원 늘어난다"}]'::jsonb),
  ('1a4f9ee5-b313-80b9-bb51-85472f13826c','BASIC-FUND-01',1,1,'일반적인 투자 펀드에 대한 설명으로 맞는 것은?','투자금을 모아 주식·채권 등에 운용하는 구조를 떠올려 보세요.','[{"id":1,"text":"투자자가 은행에서 빌리는 대출 상품"},{"id":2,"text":"매달 같은 이자를 보장하는 예금 상품"},{"id":3,"text":"여러 투자자의 돈을 모아 자산에 투자하는 상품"},{"id":4,"text":"회사가 주주에게 지급하는 현금배당 자체"}]'::jsonb),
  ('ebf974de-89f4-8f26-8dd8-b363fca776c8','BASIC-FUND-02',1,1,'은행에서 가입한 일반적인 주식형 펀드에 대한 설명으로 맞는 것은?','가입 장소와 투자 상품 자체의 위험은 구별해야 해요.','[{"id":1,"text":"은행에서 가입했으므로 예금과 같다"},{"id":2,"text":"운용 결과에 따라 원금 손실이 날 수 있다"},{"id":3,"text":"전문가가 운용하므로 손실이 나지 않는다"},{"id":4,"text":"가입 기간이 지나면 이익이 반드시 생긴다"}]'::jsonb),
  ('4c709f18-0f0c-84f2-b431-2859a6e89628','BASIC-FUND-03',1,1,'여러 펀드를 보유할 때 분산투자에 대한 설명으로 맞는 것은?','상품 개수보다 그 안에 어떤 자산이 들어 있는지 살펴보세요.','[{"id":1,"text":"펀드 이름이 다르면 투자 대상도 반드시 다르다"},{"id":2,"text":"펀드 수가 많으면 원금 손실이 불가능하다"},{"id":3,"text":"모두 같은 산업에 투자할수록 분산 효과가 커진다"},{"id":4,"text":"실제 투자 대상이 겹치는지 확인해야 한다"}]'::jsonb);
insert into finpop_private.quiz_answer_keys(question_version_id,correct_choice_id,explanation) values
  ('57aa296a-7d46-8644-b99e-29317ba312bc',2,'주식은 회사의 소유 지분을 나타냅니다. 돈을 빌려주고 상환을 약속받는 채권과 구별됩니다.'),
  ('76cf08df-21cc-897f-a6bc-b842a949495b',4,'주가는 변동하며 원금 손실이 날 수 있습니다. 유명한 회사이거나 오래 보유한다고 원금이 보장되지는 않습니다.'),
  ('8fa1e2a0-a192-8396-a66f-c7e8db2d630d',1,'한 주의 차익은 12,000 - 10,000 = 2,000원입니다. 2주이므로 총 차익은 4,000원입니다.'),
  ('2994e1cc-9fbf-827f-82e9-fc4564a30519',3,'채권은 빚을 나타내는 증권입니다. 투자자는 발행자에게 돈을 빌려주고 약정에 따른 상환을 약속받습니다.'),
  ('fff51e16-3ebf-899a-bb93-64e59f4b143a',2,'발행자가 원금이나 이자를 지급하지 못하는 신용 위험이 있습니다. 상환 약속이 있다는 것이 지급 보장을 뜻하지는 않습니다.'),
  ('4be484e7-1c4f-878c-814b-3bc8a20bcf51',4,'새 채권의 금리가 높아지면 기존의 낮은 고정 이자를 주는 채권은 상대적으로 덜 매력적입니다. 따라서 기존 채권 가격은 일반적으로 하락합니다.'),
  ('cfe32125-bb7b-881d-96f5-baa48749ce00',1,'만기일은 약정한 예치 기간이 끝나는 날입니다. 만기 지급액과 만기 후 처리 방식은 계약 조건에 따릅니다.'),
  ('6ab8a89d-d3db-8224-a7c1-a213d9ba3787',3,'만기 전 해지에는 별도의 중도해지이율 등이 적용될 수 있습니다. 가입할 때 본 만기 금리가 그대로 적용된다고 생각하면 안 됩니다.'),
  ('8ccfcf1e-92a5-8bc0-808b-31ecd8273cd1',2,'원금 100만 원에 이자 3만 원을 더하면 103만 원입니다. 실제 수령액은 세금과 계약 조건에 따라 달라질 수 있습니다.'),
  ('069baf4f-7361-8be9-86b8-bdebb4b492ef',4,'복리는 원금에 누적된 이자를 합한 금액을 기준으로 다음 이자를 계산하는 방식입니다. 원금만 기준으로 계산하는 단리와 다릅니다.'),
  ('9e92313f-5833-8b47-96cb-6c91c88bde9b',1,'첫해 말에는 100만 원 × 1.1 = 110만 원, 둘째 해 말에는 110만 원 × 1.1 = 121만 원입니다. 10%는 계산을 위한 가상 금리입니다.'),
  ('4231d446-2ba0-8d9c-9656-f9812386baad',3,'단리는 매년 원금 100만 원에 이자를 계산합니다. 연 복리는 둘째 해에 첫해 이자를 포함한 110만 원에 이자를 계산해 1만 원이 더 붙습니다. 세금·수수료는 제외한 비교입니다.'),
  ('b61c51a4-88f2-803c-86d9-4b61aaa6cf6a',2,'배당은 회사가 이익 등을 주주에게 분배하는 것입니다. 주식을 매도해 생기는 매매 차익과는 구별됩니다.'),
  ('e04356f0-7264-8d4c-89e0-77cb6bc7c5ae',4,'보통주의 배당은 고정적으로 보장되는 이자가 아닙니다. 회사의 실적과 배당 결정 등에 따라 금액이 달라지거나 지급되지 않을 수 있습니다.'),
  ('ea60c6f7-4792-8f5a-9b36-b1f31adb7f3a',1,'주당 배당금 500원 × 10주 = 5,000원입니다. 이는 세전 금액이며 실제 지급액은 세금 등에 따라 달라질 수 있습니다.'),
  ('89726d8d-d9e0-81cc-beca-26e692263cd0',3,'일반적인 정기예금은 목돈을 한 번에 맡겨 일정 기간 운용하는 방식입니다. 매달 일정 금액을 모으는 정기적금과 구별됩니다.'),
  ('939fa5b6-f14e-85ed-a8c3-02d08b005e79',2,'1,000,000원 × 0.03 × 1년 = 30,000원입니다. 원금을 포함한 수령액과 이자만의 금액을 구별해야 합니다.'),
  ('b4ac5807-bb8f-8957-ae40-676bfeb410c3',4,'최고금리는 우대조건을 모두 충족했을 때의 금리일 수 있습니다. 기본금리와 자신이 충족하는 조건을 확인해야 실제 적용금리를 비교할 수 있습니다.'),
  ('246345dc-ac61-82ae-8d35-8d28c726a6c5',1,'정기적금은 매달 일정 금액을 납입해 목돈을 마련하는 방식입니다. 자유적립식 적금은 납입 조건이 다를 수 있습니다.'),
  ('78481828-4e28-8805-8480-85c1c190cc91',3,'10만 원 × 12회 = 120만 원입니다. 문제는 이자를 포함한 만기 수령액이 아니라 납입 원금을 묻고 있습니다.'),
  ('49063da0-26e3-8a92-b78e-64b96bcc38a3',2,'같은 금리·금액이라면 이자가 붙는 기간이 길수록 이자가 커집니다. 적금의 각 납입금은 예치 기간이 달라 총 납입 원금 전체에 1년치 금리를 곱하면 안 됩니다.'),
  ('cc2e16d8-9630-8b82-a40c-561bf43a7d11',4,'대출 원금은 빌린 돈 자체입니다. 이자는 돈을 빌리는 대가이며 수수료 등과 함께 원금 외 비용이 될 수 있습니다.'),
  ('1ceade6d-8ac8-8db8-b5e3-888dda184345',1,'1,000,000원 × 0.05 × 1년 = 50,000원입니다. 원리금 합계 1,050,000원과 이자만의 금액은 다릅니다.'),
  ('b596c224-7b16-8ec5-ab0b-04f3cc7b2c68',3,'대출 비용은 금리 외에 수수료와 원금 상환 일정의 영향도 받습니다. 같은 원금·기간이어도 실제 총비용은 다를 수 있습니다.'),
  ('7fef53f9-47df-86e8-bb44-ae0e915fb0a7',2,'은행 예금 잔액은 보유한 경제적 가치인 자산입니다. 갚아야 할 대출금이나 카드 대금은 부채에 해당합니다.'),
  ('ed0ea2bf-e8bd-8ffd-9f07-1f917a319f1d',4,'순자산 = 총자산 - 부채이므로 500만 원 - 200만 원 = 300만 원입니다.'),
  ('2d178e2e-11cb-8bf7-b0c0-d74ce66fa0f8',1,'예금 자산이 100만 원 늘지만 부채도 100만 원 늘어납니다. 자산에서 부채를 뺀 순자산의 변화는 0원입니다.'),
  ('1a4f9ee5-b313-80b9-bb51-85472f13826c',3,'펀드는 여러 투자자의 돈을 모아 주식·채권 등 자산에 투자하는 상품입니다. 투자 성과에 따라 손익이 생깁니다.'),
  ('ebf974de-89f4-8f26-8dd8-b363fca776c8',2,'주식형 펀드는 편입 주식 등의 가치에 따라 손실이 날 수 있습니다. 은행에서 가입하거나 전문가가 운용한다고 원금이 보장되지는 않습니다.'),
  ('4c709f18-0f0c-84f2-b431-2859a6e89628',4,'이름이 다른 펀드도 같은 산업이나 종목에 집중할 수 있습니다. 분산 효과를 보려면 실제 투자 대상과 비중을 확인해야 하며, 분산해도 손실 위험은 남습니다.');
insert into public.quiz_version_sources(question_version_id,source_id) values
  ('57aa296a-7d46-8644-b99e-29317ba312bc','76c2df5d-9ac0-872f-aab6-93cc18bb20c6'),
  ('57aa296a-7d46-8644-b99e-29317ba312bc','a97309af-b364-865d-ac2c-b9bb3b46fc2e'),
  ('76cf08df-21cc-897f-a6bc-b842a949495b','76c2df5d-9ac0-872f-aab6-93cc18bb20c6'),
  ('8fa1e2a0-a192-8396-a66f-c7e8db2d630d','76c2df5d-9ac0-872f-aab6-93cc18bb20c6'),
  ('2994e1cc-9fbf-827f-82e9-fc4564a30519','a97309af-b364-865d-ac2c-b9bb3b46fc2e'),
  ('fff51e16-3ebf-899a-bb93-64e59f4b143a','a97309af-b364-865d-ac2c-b9bb3b46fc2e'),
  ('4be484e7-1c4f-878c-814b-3bc8a20bcf51','a97309af-b364-865d-ac2c-b9bb3b46fc2e'),
  ('cfe32125-bb7b-881d-96f5-baa48749ce00','9ad76678-b079-8ae6-8cc7-055d6a5bded4'),
  ('cfe32125-bb7b-881d-96f5-baa48749ce00','6237e05c-1fb3-8ca9-ac04-9600848c9277'),
  ('6ab8a89d-d3db-8224-a7c1-a213d9ba3787','6237e05c-1fb3-8ca9-ac04-9600848c9277'),
  ('8ccfcf1e-92a5-8bc0-808b-31ecd8273cd1','9ad76678-b079-8ae6-8cc7-055d6a5bded4'),
  ('069baf4f-7361-8be9-86b8-bdebb4b492ef','ca749320-75ce-85ab-86f0-46ef4a05c9a4'),
  ('9e92313f-5833-8b47-96cb-6c91c88bde9b','ca749320-75ce-85ab-86f0-46ef4a05c9a4'),
  ('4231d446-2ba0-8d9c-9656-f9812386baad','ca749320-75ce-85ab-86f0-46ef4a05c9a4'),
  ('b61c51a4-88f2-803c-86d9-4b61aaa6cf6a','76c2df5d-9ac0-872f-aab6-93cc18bb20c6'),
  ('e04356f0-7264-8d4c-89e0-77cb6bc7c5ae','76c2df5d-9ac0-872f-aab6-93cc18bb20c6'),
  ('ea60c6f7-4792-8f5a-9b36-b1f31adb7f3a','76c2df5d-9ac0-872f-aab6-93cc18bb20c6'),
  ('89726d8d-d9e0-81cc-beca-26e692263cd0','6237e05c-1fb3-8ca9-ac04-9600848c9277'),
  ('89726d8d-d9e0-81cc-beca-26e692263cd0','86cbd6e2-4516-8693-8455-5931c6574ff2'),
  ('939fa5b6-f14e-85ed-a8c3-02d08b005e79','6237e05c-1fb3-8ca9-ac04-9600848c9277'),
  ('b4ac5807-bb8f-8957-ae40-676bfeb410c3','6237e05c-1fb3-8ca9-ac04-9600848c9277'),
  ('b4ac5807-bb8f-8957-ae40-676bfeb410c3','11f95a38-8c06-8f4f-a0f3-712ef970c3ea'),
  ('246345dc-ac61-82ae-8d35-8d28c726a6c5','86cbd6e2-4516-8693-8455-5931c6574ff2'),
  ('78481828-4e28-8805-8480-85c1c190cc91','86cbd6e2-4516-8693-8455-5931c6574ff2'),
  ('49063da0-26e3-8a92-b78e-64b96bcc38a3','86cbd6e2-4516-8693-8455-5931c6574ff2'),
  ('cc2e16d8-9630-8b82-a40c-561bf43a7d11','9690d50a-543c-8866-9d23-d5cd4f28d314'),
  ('cc2e16d8-9630-8b82-a40c-561bf43a7d11','adf1e97b-270c-8893-a3fe-9cd7af708119'),
  ('1ceade6d-8ac8-8db8-b5e3-888dda184345','9690d50a-543c-8866-9d23-d5cd4f28d314'),
  ('b596c224-7b16-8ec5-ab0b-04f3cc7b2c68','9690d50a-543c-8866-9d23-d5cd4f28d314'),
  ('b596c224-7b16-8ec5-ab0b-04f3cc7b2c68','1a4d906d-a0fc-83c6-9095-5dbae4c94101'),
  ('7fef53f9-47df-86e8-bb44-ae0e915fb0a7','adf1e97b-270c-8893-a3fe-9cd7af708119'),
  ('7fef53f9-47df-86e8-bb44-ae0e915fb0a7','a2e3a5ba-3816-8476-992e-9568aaaa4d56'),
  ('ed0ea2bf-e8bd-8ffd-9f07-1f917a319f1d','a2e3a5ba-3816-8476-992e-9568aaaa4d56'),
  ('2d178e2e-11cb-8bf7-b0c0-d74ce66fa0f8','a2e3a5ba-3816-8476-992e-9568aaaa4d56'),
  ('1a4f9ee5-b313-80b9-bb51-85472f13826c','57d03143-3e38-813a-b38d-8382e5f1a89d'),
  ('ebf974de-89f4-8f26-8dd8-b363fca776c8','57d03143-3e38-813a-b38d-8382e5f1a89d'),
  ('4c709f18-0f0c-84f2-b431-2859a6e89628','bb70b11c-3717-8fcb-98d5-ad537cf53325');
update public.quiz_question_versions set review_status='APPROVED' where id in ('57aa296a-7d46-8644-b99e-29317ba312bc'::uuid,'76cf08df-21cc-897f-a6bc-b842a949495b'::uuid,'8fa1e2a0-a192-8396-a66f-c7e8db2d630d'::uuid,'2994e1cc-9fbf-827f-82e9-fc4564a30519'::uuid,'fff51e16-3ebf-899a-bb93-64e59f4b143a'::uuid,'4be484e7-1c4f-878c-814b-3bc8a20bcf51'::uuid,'cfe32125-bb7b-881d-96f5-baa48749ce00'::uuid,'6ab8a89d-d3db-8224-a7c1-a213d9ba3787'::uuid,'8ccfcf1e-92a5-8bc0-808b-31ecd8273cd1'::uuid,'069baf4f-7361-8be9-86b8-bdebb4b492ef'::uuid,'9e92313f-5833-8b47-96cb-6c91c88bde9b'::uuid,'4231d446-2ba0-8d9c-9656-f9812386baad'::uuid,'b61c51a4-88f2-803c-86d9-4b61aaa6cf6a'::uuid,'e04356f0-7264-8d4c-89e0-77cb6bc7c5ae'::uuid,'ea60c6f7-4792-8f5a-9b36-b1f31adb7f3a'::uuid,'89726d8d-d9e0-81cc-beca-26e692263cd0'::uuid,'939fa5b6-f14e-85ed-a8c3-02d08b005e79'::uuid,'b4ac5807-bb8f-8957-ae40-676bfeb410c3'::uuid,'246345dc-ac61-82ae-8d35-8d28c726a6c5'::uuid,'78481828-4e28-8805-8480-85c1c190cc91'::uuid,'49063da0-26e3-8a92-b78e-64b96bcc38a3'::uuid,'cc2e16d8-9630-8b82-a40c-561bf43a7d11'::uuid,'1ceade6d-8ac8-8db8-b5e3-888dda184345'::uuid,'b596c224-7b16-8ec5-ab0b-04f3cc7b2c68'::uuid,'7fef53f9-47df-86e8-bb44-ae0e915fb0a7'::uuid,'ed0ea2bf-e8bd-8ffd-9f07-1f917a319f1d'::uuid,'2d178e2e-11cb-8bf7-b0c0-d74ce66fa0f8'::uuid,'1a4f9ee5-b313-80b9-bb51-85472f13826c'::uuid,'ebf974de-89f4-8f26-8dd8-b363fca776c8'::uuid,'4c709f18-0f0c-84f2-b431-2859a6e89628'::uuid);
do $verify$ begin
 if (select count(*) from public.quiz_question_versions where id in ('57aa296a-7d46-8644-b99e-29317ba312bc'::uuid,'76cf08df-21cc-897f-a6bc-b842a949495b'::uuid,'8fa1e2a0-a192-8396-a66f-c7e8db2d630d'::uuid,'2994e1cc-9fbf-827f-82e9-fc4564a30519'::uuid,'fff51e16-3ebf-899a-bb93-64e59f4b143a'::uuid,'4be484e7-1c4f-878c-814b-3bc8a20bcf51'::uuid,'cfe32125-bb7b-881d-96f5-baa48749ce00'::uuid,'6ab8a89d-d3db-8224-a7c1-a213d9ba3787'::uuid,'8ccfcf1e-92a5-8bc0-808b-31ecd8273cd1'::uuid,'069baf4f-7361-8be9-86b8-bdebb4b492ef'::uuid,'9e92313f-5833-8b47-96cb-6c91c88bde9b'::uuid,'4231d446-2ba0-8d9c-9656-f9812386baad'::uuid,'b61c51a4-88f2-803c-86d9-4b61aaa6cf6a'::uuid,'e04356f0-7264-8d4c-89e0-77cb6bc7c5ae'::uuid,'ea60c6f7-4792-8f5a-9b36-b1f31adb7f3a'::uuid,'89726d8d-d9e0-81cc-beca-26e692263cd0'::uuid,'939fa5b6-f14e-85ed-a8c3-02d08b005e79'::uuid,'b4ac5807-bb8f-8957-ae40-676bfeb410c3'::uuid,'246345dc-ac61-82ae-8d35-8d28c726a6c5'::uuid,'78481828-4e28-8805-8480-85c1c190cc91'::uuid,'49063da0-26e3-8a92-b78e-64b96bcc38a3'::uuid,'cc2e16d8-9630-8b82-a40c-561bf43a7d11'::uuid,'1ceade6d-8ac8-8db8-b5e3-888dda184345'::uuid,'b596c224-7b16-8ec5-ab0b-04f3cc7b2c68'::uuid,'7fef53f9-47df-86e8-bb44-ae0e915fb0a7'::uuid,'ed0ea2bf-e8bd-8ffd-9f07-1f917a319f1d'::uuid,'2d178e2e-11cb-8bf7-b0c0-d74ce66fa0f8'::uuid,'1a4f9ee5-b313-80b9-bb51-85472f13826c'::uuid,'ebf974de-89f4-8f26-8dd8-b363fca776c8'::uuid,'4c709f18-0f0c-84f2-b431-2859a6e89628'::uuid) and review_status='APPROVED')<>30 then
   raise exception '승인 문항 수가 30개가 아닙니다.'; end if;
 if (select count(*) from finpop_private.quiz_answer_keys where question_version_id in ('57aa296a-7d46-8644-b99e-29317ba312bc'::uuid,'76cf08df-21cc-897f-a6bc-b842a949495b'::uuid,'8fa1e2a0-a192-8396-a66f-c7e8db2d630d'::uuid,'2994e1cc-9fbf-827f-82e9-fc4564a30519'::uuid,'fff51e16-3ebf-899a-bb93-64e59f4b143a'::uuid,'4be484e7-1c4f-878c-814b-3bc8a20bcf51'::uuid,'cfe32125-bb7b-881d-96f5-baa48749ce00'::uuid,'6ab8a89d-d3db-8224-a7c1-a213d9ba3787'::uuid,'8ccfcf1e-92a5-8bc0-808b-31ecd8273cd1'::uuid,'069baf4f-7361-8be9-86b8-bdebb4b492ef'::uuid,'9e92313f-5833-8b47-96cb-6c91c88bde9b'::uuid,'4231d446-2ba0-8d9c-9656-f9812386baad'::uuid,'b61c51a4-88f2-803c-86d9-4b61aaa6cf6a'::uuid,'e04356f0-7264-8d4c-89e0-77cb6bc7c5ae'::uuid,'ea60c6f7-4792-8f5a-9b36-b1f31adb7f3a'::uuid,'89726d8d-d9e0-81cc-beca-26e692263cd0'::uuid,'939fa5b6-f14e-85ed-a8c3-02d08b005e79'::uuid,'b4ac5807-bb8f-8957-ae40-676bfeb410c3'::uuid,'246345dc-ac61-82ae-8d35-8d28c726a6c5'::uuid,'78481828-4e28-8805-8480-85c1c190cc91'::uuid,'49063da0-26e3-8a92-b78e-64b96bcc38a3'::uuid,'cc2e16d8-9630-8b82-a40c-561bf43a7d11'::uuid,'1ceade6d-8ac8-8db8-b5e3-888dda184345'::uuid,'b596c224-7b16-8ec5-ab0b-04f3cc7b2c68'::uuid,'7fef53f9-47df-86e8-bb44-ae0e915fb0a7'::uuid,'ed0ea2bf-e8bd-8ffd-9f07-1f917a319f1d'::uuid,'2d178e2e-11cb-8bf7-b0c0-d74ce66fa0f8'::uuid,'1a4f9ee5-b313-80b9-bb51-85472f13826c'::uuid,'ebf974de-89f4-8f26-8dd8-b363fca776c8'::uuid,'4c709f18-0f0c-84f2-b431-2859a6e89628'::uuid))<>30 then
   raise exception '정답 수가 30개가 아닙니다.'; end if;
 if (select count(*) from public.quiz_version_sources where question_version_id in ('57aa296a-7d46-8644-b99e-29317ba312bc'::uuid,'76cf08df-21cc-897f-a6bc-b842a949495b'::uuid,'8fa1e2a0-a192-8396-a66f-c7e8db2d630d'::uuid,'2994e1cc-9fbf-827f-82e9-fc4564a30519'::uuid,'fff51e16-3ebf-899a-bb93-64e59f4b143a'::uuid,'4be484e7-1c4f-878c-814b-3bc8a20bcf51'::uuid,'cfe32125-bb7b-881d-96f5-baa48749ce00'::uuid,'6ab8a89d-d3db-8224-a7c1-a213d9ba3787'::uuid,'8ccfcf1e-92a5-8bc0-808b-31ecd8273cd1'::uuid,'069baf4f-7361-8be9-86b8-bdebb4b492ef'::uuid,'9e92313f-5833-8b47-96cb-6c91c88bde9b'::uuid,'4231d446-2ba0-8d9c-9656-f9812386baad'::uuid,'b61c51a4-88f2-803c-86d9-4b61aaa6cf6a'::uuid,'e04356f0-7264-8d4c-89e0-77cb6bc7c5ae'::uuid,'ea60c6f7-4792-8f5a-9b36-b1f31adb7f3a'::uuid,'89726d8d-d9e0-81cc-beca-26e692263cd0'::uuid,'939fa5b6-f14e-85ed-a8c3-02d08b005e79'::uuid,'b4ac5807-bb8f-8957-ae40-676bfeb410c3'::uuid,'246345dc-ac61-82ae-8d35-8d28c726a6c5'::uuid,'78481828-4e28-8805-8480-85c1c190cc91'::uuid,'49063da0-26e3-8a92-b78e-64b96bcc38a3'::uuid,'cc2e16d8-9630-8b82-a40c-561bf43a7d11'::uuid,'1ceade6d-8ac8-8db8-b5e3-888dda184345'::uuid,'b596c224-7b16-8ec5-ab0b-04f3cc7b2c68'::uuid,'7fef53f9-47df-86e8-bb44-ae0e915fb0a7'::uuid,'ed0ea2bf-e8bd-8ffd-9f07-1f917a319f1d'::uuid,'2d178e2e-11cb-8bf7-b0c0-d74ce66fa0f8'::uuid,'1a4f9ee5-b313-80b9-bb51-85472f13826c'::uuid,'ebf974de-89f4-8f26-8dd8-b363fca776c8'::uuid,'4c709f18-0f0c-84f2-b431-2859a6e89628'::uuid))<>37 then
   raise exception '출처 연결 수가 원본과 다릅니다.'; end if;
end $verify$;
select q.topic,v.review_status,count(*) as question_count from public.quiz_questions q
join public.quiz_question_versions v on v.question_id=q.id
where v.id in ('57aa296a-7d46-8644-b99e-29317ba312bc'::uuid,'76cf08df-21cc-897f-a6bc-b842a949495b'::uuid,'8fa1e2a0-a192-8396-a66f-c7e8db2d630d'::uuid,'2994e1cc-9fbf-827f-82e9-fc4564a30519'::uuid,'fff51e16-3ebf-899a-bb93-64e59f4b143a'::uuid,'4be484e7-1c4f-878c-814b-3bc8a20bcf51'::uuid,'cfe32125-bb7b-881d-96f5-baa48749ce00'::uuid,'6ab8a89d-d3db-8224-a7c1-a213d9ba3787'::uuid,'8ccfcf1e-92a5-8bc0-808b-31ecd8273cd1'::uuid,'069baf4f-7361-8be9-86b8-bdebb4b492ef'::uuid,'9e92313f-5833-8b47-96cb-6c91c88bde9b'::uuid,'4231d446-2ba0-8d9c-9656-f9812386baad'::uuid,'b61c51a4-88f2-803c-86d9-4b61aaa6cf6a'::uuid,'e04356f0-7264-8d4c-89e0-77cb6bc7c5ae'::uuid,'ea60c6f7-4792-8f5a-9b36-b1f31adb7f3a'::uuid,'89726d8d-d9e0-81cc-beca-26e692263cd0'::uuid,'939fa5b6-f14e-85ed-a8c3-02d08b005e79'::uuid,'b4ac5807-bb8f-8957-ae40-676bfeb410c3'::uuid,'246345dc-ac61-82ae-8d35-8d28c726a6c5'::uuid,'78481828-4e28-8805-8480-85c1c190cc91'::uuid,'49063da0-26e3-8a92-b78e-64b96bcc38a3'::uuid,'cc2e16d8-9630-8b82-a40c-561bf43a7d11'::uuid,'1ceade6d-8ac8-8db8-b5e3-888dda184345'::uuid,'b596c224-7b16-8ec5-ab0b-04f3cc7b2c68'::uuid,'7fef53f9-47df-86e8-bb44-ae0e915fb0a7'::uuid,'ed0ea2bf-e8bd-8ffd-9f07-1f917a319f1d'::uuid,'2d178e2e-11cb-8bf7-b0c0-d74ce66fa0f8'::uuid,'1a4f9ee5-b313-80b9-bb51-85472f13826c'::uuid,'ebf974de-89f4-8f26-8dd8-b363fca776c8'::uuid,'4c709f18-0f0c-84f2-b431-2859a6e89628'::uuid) group by q.topic,v.review_status order by q.topic;
commit;
