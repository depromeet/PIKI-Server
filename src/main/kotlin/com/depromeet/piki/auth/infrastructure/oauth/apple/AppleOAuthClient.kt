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
        // parse(서명·issuer 검증)만 재시도한다. aud/sub 같은 클레임 검증 실패는 JWKS refresh 로 복구되지 않으므로
        // 재시도에 포함시키면 잘못된 토큰이 몰릴 때 /auth/keys 호출이 self-amplifying 으로 증폭된다.
        val claims =
            runCatching { parseIdToken(idToken, getJwks()) }
                .getOrElse { firstError ->
                    // JWKS 키 교체 직후 kid 불일치·서명 검증 실패 가능성 → 캐시 무효화 후 1회 재시도
                    log.warn("Apple id_token parse 실패, JWKS 캐시 무효화 후 재시도: {}", firstError.message)
                    jwksCachedAt = 0L
                    try {
                        parseIdToken(idToken, getJwks())
                    } catch (e: Exception) {
                        throw OAuthException.providerError(e)
                    }
                }
        return verifyClaimsAndExtract(claims, expectedAud)
    }

    // JWKS fetch / parse 와 분리된 순수 클레임 검증. 외부 호출 없이 단위 테스트가 직접 호출한다.
    // parse 가 끝난 claims 를 받아 aud · sub 만 본다 — 검증 실패는 JWKS refresh 로 복구되지 않으므로
    // 재시도 경로에 포함시키지 않는다.
    internal fun verifyClaimsAndExtract(
        claims: Claims,
        expectedAud: String,
    ): OAuthUserInfo {
        // aud 는 string 또는 array — JJWT 가 Set<String> 으로 정규화
        val aud = claims.audience ?: emptySet()
        if (!aud.contains(expectedAud)) {
            log.warn("Apple id_token aud 불일치: expected={}, actual={}", expectedAud, aud)
            throw OAuthException.providerError(IllegalArgumentException("Apple id_token audience mismatch"))
        }

        // sub 누락은 Apple 의 비정상 응답·공격성 JWT 가능성. 외부 입력 검증 실패이므로 500 이 아닌
        // OAuthException.providerError(502) 로 매핑해 contract 일관성을 유지한다.
        val sub =
            claims.subject
                ?: throw OAuthException.providerError(IllegalArgumentException("Apple id_token sub missing"))
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
