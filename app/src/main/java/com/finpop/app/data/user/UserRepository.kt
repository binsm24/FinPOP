package com.finpop.app.data.user

import com.finpop.app.core.network.SupabaseProvider
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.postgrest.from
import io.github.jan.supabase.postgrest.query.Columns
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class UserProfile(
    val id: String,
    val nickname: String,
    @SerialName("total_points") val totalPoints: Int,
    @SerialName("user_level") val userLevel: Int,
    @SerialName("wallet_gold") val walletGold: Int,
    @SerialName("selected_character_id") val selectedCharacterId: String? = null,
)

object UserRepository {

    /** 로그인한 내 정보를 users 테이블에서 가져온다. */
    suspend fun getMyProfile(): UserProfile {
        val uid = SupabaseProvider.client.auth.currentUserOrNull()?.id
            ?: error("로그인이 필요합니다")
        return SupabaseProvider.client.from("users")
            .select(Columns.list("id", "nickname", "total_points", "user_level", "wallet_gold", "selected_character_id")) {
                filter { eq("id", uid) }
            }
            .decodeSingle<UserProfile>()
    }
}
