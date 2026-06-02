package com.depromeet.piki.auth.infrastructure.oauth.apple

import com.depromeet.piki.auth.infrastructure.oauth.OAuthClient
import com.depromeet.piki.auth.infrastructure.oauth.OAuthException
import com.depromeet.piki.auth.infrastructure.oauth.OAuthParams
import com.depromeet.piki.auth.infrastructure.oauth.OAuthProvider
import com.depromeet.piki.auth.infrastructure.oauth.OAuthRestClient
import com.depromeet.piki.auth.infrastructure.oauth.OAuthUserInfo
import com.depromeet.piki.auth.infrastructure.oauth.apple.dto.AppleTokenResponse
import io.jsonwebtoken.Claims
import io.jsonwebtoken.Header
import io.jsonwebtoken.Jwts
import io.jsonwebtoken.Locator
import io.jsonwebtoken.security.Jwks
import org.slf4j.LoggerFactory
import org.springframework.http.MediaType
import org.springframework.util.LinkedMultiValueMap
import org.springframework.web.client.body
import org.springframework.web.util.UriComponentsBuilder
import java.security.Key
import java.security.KeyFactory
import java.security.spec.PKCS8EncodedKeySpec
import java.util.Base64
import java.util.Date

class AppleOAuthClient(
    private val props: AppleProperties,
) : OAuthClient {
    override val provider = OAuthProvider.APPLE

    private val log = LoggerFactory.getLogger(javaClass)
    private val appleClient = OAuthRestClient.create(APPLE_BASE_URL)

    @Volatile private var cachedJwksJson: String? = null
    @Volatile private var jwksCachedAt = 0L

    override fun buildAuthUrl(
        state: String,
        redirectUri: String?,
    ): String =
        UriComponentsBuilder
            .fromUriString(AUTH_URL)
            .queryParam(OAuthParams.CLIENT_ID, props.clientId)
            .queryParam(OAuthParams.REDIRECT_URI, redirectUri ?: props.redirectUri)
            .queryParam(OAuthParams.RESPONSE_TYPE, OAuthParams.RESPONSE_TYPE_CODE)
            .queryParam("response_mode", "form_post")
            .queryParam(OAuthParams.SCOPE, "email name")
            .queryParam(OAuthParams.STATE, state)
            .build()
            .encode()
            .toUriString()

    // v1: 백엔드가 code → id_token 교환 후 id_token 검증. aud = clientId (Services ID).
    override fun fetchUserInfoByCode(
        code: String,
        redirectUri: String,
    ): OAuthUserInfo {
        val clientSecret = buildClientSecret()
        val params =
            LinkedMultiValueMap<String, String>().apply {
                add(OAuthParams.GRANT_TYPE, OAuthParams.GRANT_TYPE_AUTHORIZATION_CODE)
                add(OAuthParams.CLIENT_ID, props.clientId)
                add(OAuthParams.CLIENT_SECRET, clientSecret)
                add(OAuthParams.REDIRECT_URI, redirectUri)
                add(OAuthParams.CODE, code)
            }
        val tokenResponse =
            try {
                appleClient
                    .post()
                    .uri(TOKEN_PATH)
                    .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                    .body(params)
                    .retrieve()
                    .body<AppleTokenResponse>()
                    ?: error("Apple token response body is null")
            } catch (e: Exception) {
                throw OAuthException.providerError(e)
            }
        return verifyIdTokenAndExtract(tokenResponse.idToken, expectedAud = props.clientId)
    }

    // v2: iOS SDK 가 발급한 identityToken(JWT)을 직접 검증. aud = bundleId (Bundle ID).
    override fun fetchUserInfoByAccessToken(accessToken: String): OAuthUserInfo =
        verifyIdTokenAndExtract(accessToken, expectedAud = props.bundleId)

    private fun verifyIdTokenAndExtract(
        idToken: String,
        expectedAud: String,
    ): OAuthUserInfo {
        val firstResult = runCatching { verifyWithJwks(idToken, expectedAud, getJwks()) }
        if (firstResult.isSuccess) return firstResult.getOrThrow()
        // JWKS 키 교체 직후 kid 불일치 가능성 → 캐시 무효화 후 1회 재시도
        log.warn("Apple id_token 파싱 실패, JWKS 캐시 무효화 후 재시도: {}", firstResult.exceptionOrNull()?.message)
        jwksCachedAt = 0L
        return verifyWithJwks(idToken, expectedAud, getJwks())
    }

    // 외부 호출(JWKS fetch) 없이 검증·추출만 수행하는 순수 함수. 단위 테스트가 직접 호출한다.
    internal fun verifyWithJwks(
        idToken: String,
        expectedAud: String,
        jwksJson: String,
    ): OAuthUserInfo {
        val claims =
            try {
                parseIdToken(idToken, jwksJson)
            } catch (e: Exception) {
                throw OAuthException.providerError(e)
            }

        // aud 는 string 또는 array — JJWT 가 Set<String> 으로 정규화
        val aud = claims.audience ?: emptySet()
        if (!aud.contains(expectedAud)) {
            log.warn("Apple id_token aud 불일치: expected={}, actual={}", expectedAud, aud)
            throw OAuthException.providerError(IllegalArgumentException("Apple id_token audience mismatch"))
        }

        val sub = claims.subject ?: error("Apple id_token sub 가 없음")
        return OAuthUserInfo(provider = OAuthProvider.APPLE, socialId = sub, profileImage = null)
    }

    internal fun parseIdToken(
        idToken: String,
        jwksJson: String,
    ): Claims {
        val jwkSet = Jwks.setParser().build().parse(jwksJson)
        // Header extends Map<String, Object> — header["kid"] 으로 kid 값에 직접 접근한다.
        // jwkSet.getKeys() 는 JWK 셋의 키 목록 (Map.keys 와 다름 — Map.keys 는 문자열 키 셋).
        val keyLocator =
            object : Locator<Key> {
                override fun locate(header: Header): Key? {
                    val kid = header["kid"] as? String ?: return null
                    return jwkSet.getKeys().firstOrNull { it.getId() == kid }?.toKey()
                }
            }
        return Jwts
            .parser()
            .keyLocator(keyLocator)
            .requireIssuer(APPLE_ISSUER)
            .build()
            .parseSignedClaims(idToken)
            .payload
    }

    private fun getJwks(): String {
        val now = System.currentTimeMillis()
        val cached = cachedJwksJson
        if (cached != null && now - jwksCachedAt < JWKS_CACHE_TTL_MS) return cached

        return try {
            val fresh =
                appleClient
                    .get()
                    .uri(JWKS_PATH)
                    .retrieve()
                    .body(String::class.java)
                    ?: error("Apple JWKS response body is null")
            cachedJwksJson = fresh
            jwksCachedAt = System.currentTimeMillis()
            fresh
        } catch (e: Exception) {
            throw OAuthException.providerError(e)
        }
    }

    internal fun buildClientSecret(): String {
        val privateKey = parseEcPrivateKey(props.privateKey)
        val now = Date()
        val expiry = Date(now.time + CLIENT_SECRET_TTL_MS)
        return Jwts
            .builder()
            .header()
            .add("kid", props.keyId)
            .and()
            .issuer(props.teamId)
            .subject(props.clientId)
            .audience()
            .add(APPLE_ISSUER)
            .and()
            .issuedAt(now)
            .expiration(expiry)
            .signWith(privateKey)
            .compact()
    }

    internal fun parseEcPrivateKey(pem: String): java.security.PrivateKey {
        val stripped =
            pem
                .replace("-----BEGIN PRIVATE KEY-----", "")
                .replace("-----END PRIVATE KEY-----", "")
                .replace("\\n", "\n")
                .replace("\n", "")
                .trim()
        val keyBytes = Base64.getDecoder().decode(stripped)
        return KeyFactory.getInstance("EC").generatePrivate(PKCS8EncodedKeySpec(keyBytes))
    }

    companion object {
        private const val APPLE_BASE_URL = "https://appleid.apple.com"
        private const val AUTH_URL = "https://appleid.apple.com/auth/authorize"
        private const val TOKEN_PATH = "/auth/token"
        private const val JWKS_PATH = "/auth/keys"
        private const val APPLE_ISSUER = "https://appleid.apple.com"
        private val JWKS_CACHE_TTL_MS = 60 * 60 * 1000L // 1시간
        private val CLIENT_SECRET_TTL_MS = 180L * 24 * 60 * 60 * 1000 // 6개월 (Apple 최대)
    }
}
