package com.finpop.app.data.game

import com.finpop.app.core.model.*

/** 정답을 포함하는 로컬 판정 자료입니다. 운영 API에서는 정답을 서버에만 보관해야 합니다. */
data class LocalQuestion(
    val id: String,
    val topic: TileTopic,
    val question: String,
    val choices: List<QuizChoice>,
    val correctChoiceId: Int,
    val explanation: String,
)
fun interface LocalQuizProvider {
    fun find(topic: TileTopic, stage: Int, excludedIds: Set<String>): LocalQuestion?
}

/** 연동 검증용 문항입니다. 실제 조사한 30문항이나 단계별 콘텐츠를 대체하지 않습니다. */
class SampleQuizProvider : LocalQuizProvider {
    private val facts = listOf(
        Triple(TileTopic.STOCK, "주식은 일반적으로 무엇을 나타내나요?", "회사의 지분"),
        Triple(TileTopic.BOND, "채권 발행자가 투자자에게 약속하는 것은?", "조건에 따른 원금 상환과 이자 지급"),
        Triple(TileTopic.MATURITY, "금융상품에서 만기는 무엇인가요?", "약정한 계약 기간이 끝나는 시점"),
        Triple(TileTopic.COMPOUND, "복리는 어떤 방식인가요?", "원금과 누적 이자에 이자가 붙는 방식"),
        Triple(TileTopic.DIVIDEND, "배당은 무엇인가요?", "회사가 주주에게 이익 등을 분배하는 것"),
        Triple(TileTopic.DEPOSIT, "정기예금의 일반적인 특징은?", "일정 금액을 정해진 기간 맡기는 것"),
        Triple(TileTopic.SAVINGS, "적금의 일반적인 특징은?", "일정 기간 돈을 나누어 납입하는 것"),
        Triple(TileTopic.LOAN, "대출 원금은 무엇인가요?", "이자·수수료와 구별되는 빌린 돈 자체"),
        Triple(TileTopic.ASSET, "순자산은 어떻게 계산하나요?", "자산에서 부채를 뺀 금액"),
        Triple(TileTopic.FUND, "투자 펀드는 무엇인가요?", "여러 투자자의 돈을 모아 자산에 투자하는 상품"),
    )
    override fun find(topic: TileTopic, stage: Int, excludedIds: Set<String>): LocalQuestion? {
        val fact = facts.firstOrNull { it.first == topic } ?: return null
        val id = "SAMPLE_${topic.name}_1"
        if (id in excludedIds) return null
        return LocalQuestion(id, topic, fact.second, listOf(
            QuizChoice(1, fact.third), QuizChoice(2, "항상 수익이 보장되는 것"),
            QuizChoice(3, "모든 금융상품의 위험이 같다는 뜻"), QuizChoice(4, "금융상품의 이름과 무관한 의미"),
        ), 1, fact.third + "입니다. 이 문항은 기능 연결 확인용 예시입니다.")
    }
}

