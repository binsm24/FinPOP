package com.finpop.app.data.auth

import android.content.Context
import androidx.credentials.CredentialManager
import androidx.credentials.GetCredentialRequest
import com.finpop.app.BuildConfig
import com.finpop.app.core.network.SupabaseProvider
import com.google.android.libraries.identity.googleid.GetSignInWithGoogleOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.auth.providers.Google
import io.github.jan.supabase.auth.providers.builtin.IDToken
import java.security.MessageDigest
import java.util.UUID

object AuthRepository {

    /** 구글 계정 선택 → Supabase 로그인. 실패하면 예외를 던진다. */
    suspend fun signInWithGoogle(activityContext: Context) {
        // 1. 일회용 번호(nonce): 원본은 Supabase에, 해시값은 구글에 보낸다
        val rawNonce = UUID.randomUUID().toString()
        val hashedNonce = MessageDigest.getInstance("SHA-256")
            .digest(rawNonce.toByteArray())
            .joinToString("") { "%02x".format(it) }

        // 2. 구글 계정 선택 창 띄우기 ("Google로 로그인" 버튼을 눌렀을 때 쓰는 방식)
        val googleOption = GetSignInWithGoogleOption.Builder(BuildConfig.GOOGLE_WEB_CLIENT_ID)
            .setNonce(hashedNonce)
            .build()
        val request = GetCredentialRequest.Builder()
            .addCredentialOption(googleOption)
            .build()
        val result = CredentialManager.create(activityContext)
            .getCredential(activityContext, request)

        // 3. 구글이 준 확인증(ID 토큰)을 Supabase에 넘겨 로그인
        val googleCredential = GoogleIdTokenCredential.createFrom(result.credential.data)
        SupabaseProvider.client.auth.signInWith(IDToken) {
            idToken = googleCredential.idToken
            provider = Google
            nonce = rawNonce
        }
    }
}
