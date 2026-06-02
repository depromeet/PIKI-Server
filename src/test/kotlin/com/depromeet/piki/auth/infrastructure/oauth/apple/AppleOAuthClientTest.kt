package com.depromeet.piki.auth.infrastructure.oauth.apple

import com.depromeet.piki.auth.infrastructure.oauth.OAuthException
import com.depromeet.piki.auth.infrastructure.oauth.OAuthProvider
import io.jsonwebtoken.Jwts
import io.jsonwebtoken.security.Jwks
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import java.security.KeyPair
import java.security.KeyPairGenerator
import java.security.interfaces.ECPrivateKey
import java.security.interfaces.ECPublicKey
import java.security.spec.ECGenParameterSpec
import java.util.Base64
import java.util.Date
import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class AppleOAuthClientTest {
    // 단위 테스트에서 동적으로 만드는 EC 키쌍. Apple 의 .p8 / JWKS 를 흉내 내어
    // client_secret 서명 · id_token 서명/검증 분기를 모두 외부 호출 없이 검증한다.
    private val testKeyPair: KeyPair = generateEcKeyPair()
    private val testKid = "TEST_KID"
    private val testJwksJson = buildJwksJson(testKeyPair.public as ECPublicKey, testKid)
    private val testP8Pem = encodeP8Pem(testKeyPair.private as ECPrivateKey)

    private fun props(
        teamId: String = "TEST_TEAM_ID",
        keyId: String = testKid,
        clientId: String = "com.test.service",
        bundleId: String = "com.test.app",
        privateKey: String = testP8Pem,
        redirectUri: String = "https://example.com/callback",
    ) = AppleProperties(
        teamId = teamId,
        keyId = keyId,
        clientId = clientId,
        bundleId = bundleId,
        privateKey = privateKey,
        redirectUri = redirectUri,
    )

    @Nested
    inner class ParseEcPrivateKey {
        @Test
        fun `정상 PEM 을 EC PrivateKey 로 파싱한다`() {
            val client = AppleOAuthClient(props())
            val key = client.parseEcPrivateKey(testP8Pem)
            assertEquals("EC", key.algorithm)
        }

        @Test
        fun `환경변수 한 줄 형태 - 리터럴 백슬래시 n 을 실제 개행으로 치환해 파싱한다`() {
            val flattened = testP8Pem.replace("\n", "\\n")
            val client = AppleOAuthClient(props())
            val key = client.parseEcPrivateKey(flattened)
            assertEquals("EC", key.algorithm)
        }

        @Test
        fun `손상된 base64 는 예외를 던진다`() {
            val client = AppleOAuthClient(props())
            assertFailsWith<IllegalArgumentException> {
                client.parseEcPrivateKey("-----BEGIN PRIVATE KEY-----\n!!!notbase64!!!\n-----END PRIVATE KEY-----")
            }
        }
    }

    @Nested
    inner class BuildClientSecret {
        @Test
        fun `client_secret 헤더와 클레임이 Apple 명세를 따른다`() {
            val client = AppleOAuthClient(props())
            val clientSecret = client.buildClientSecret()

            // public key 로 검증 (Apple 이 client_secret 받아 동일하게 검증함)
            val parsed =
                Jwts
                    .parser()
                    .verifyWith(testKeyPair.public)
                    .build()
                    .parseSignedClaims(clientSecret)

            assertEquals(testKid, parsed.header["kid"])
            assertEquals("ES256", parsed.header.algorithm)
            assertEquals("TEST_TEAM_ID", parsed.payload.issuer)
            assertEquals("com.test.service", parsed.payload.subject)
            assertTrue(
                parsed.payload.audience.contains("https://appleid.apple.com"),
                "aud 에 https://appleid.apple.com 이 포함돼야 한다: ${parsed.payload.audience}",
            )

            val iat = parsed.payload.issuedAt
            val exp = parsed.payload.expiration
            assertNotNull(iat)
            assertNotNull(exp)
            val ttlMs = exp.time - iat.time
            assertTrue(ttlMs > 0, "exp 는 iat 보다 미래여야 한다")
            // Apple 정책: client_secret 유효기간 최대 6개월
            val sixMonthsMs = 180L * 24 * 60 * 60 * 1000
            assertTrue(ttlMs <= sixMonthsMs, "exp - iat 는 6개월 이하여야 한다 (실제 ${ttlMs}ms)")
        }
    }

    @Nested
    inner class VerifyWithJwks {
        @Test
        fun `v1 - aud 가 clientId 와 일치하면 OAuthUserInfo 를 반환한다`() {
            val client = AppleOAuthClient(props())
            val sub = "001234.abcdef.5678"
            val idToken = signAppleIdToken(audience = "com.test.service", subject = sub)

            val result = client.verifyWithJwks(idToken, expectedAud = "com.test.service", jwksJson = testJwksJson)

            assertEquals(OAuthProvider.APPLE, result.provider)
            assertEquals(sub, result.socialId)
            assertEquals(null, result.profileImage)
        }

        @Test
        fun `v2 - aud 가 bundleId 와 일치하면 OAuthUserInfo 를 반환한다`() {
            val client = AppleOAuthClient(props())
            val sub = "002345.bcdefg.6789"
            val idToken = signAppleIdToken(audience = "com.test.app", subject = sub)

            val result = client.verifyWithJwks(idToken, expectedAud = "com.test.app", jwksJson = testJwksJson)

            assertEquals(sub, result.socialId)
        }

        @Test
        fun `aud 불일치 - OAuthException providerError 를 던진다`() {
            val client = AppleOAuthClient(props())
            val idToken = signAppleIdToken(audience = "com.other.service", subject = "001234.xx")

            val ex =
                assertFailsWith<OAuthException> {
                    client.verifyWithJwks(idToken, expectedAud = "com.test.service", jwksJson = testJwksJson)
                }
            assertEquals(502, ex.httpStatus.value())
        }

        @Test
        fun `issuer 가 Apple 이 아니면 OAuthException providerError 를 던진다`() {
            val client = AppleOAuthClient(props())
            val idToken =
                signAppleIdToken(
                    audience = "com.test.service",
                    subject = "001234.xx",
                    issuer = "https://attacker.example.com",
                )

            assertFailsWith<OAuthException> {
                client.verifyWithJwks(idToken, expectedAud = "com.test.service", jwksJson = testJwksJson)
            }
        }

        @Test
        fun `서명 키가 JWKS 에 없는 kid 면 OAuthException providerError 를 던진다`() {
            val client = AppleOAuthClient(props())
            // JWKS 에 없는 kid 로 서명된 토큰
            val foreignKeyPair = generateEcKeyPair()
            val now = Date()
            val idToken =
                Jwts
                    .builder()
                    .header()
                    .add("kid", "UNKNOWN_KID")
                    .and()
                    .issuer("https://appleid.apple.com")
                    .subject("001234.xx")
                    .audience()
                    .add("com.test.service")
                    .and()
                    .issuedAt(now)
                    .expiration(Date(now.time + 60_000))
                    .signWith(foreignKeyPair.private)
                    .compact()

            assertFailsWith<OAuthException> {
                client.verifyWithJwks(idToken, expectedAud = "com.test.service", jwksJson = testJwksJson)
            }
        }

        @Test
        fun `서명이 위조되면 OAuthException providerError 를 던진다`() {
            val client = AppleOAuthClient(props())
            // 같은 kid 로 다른 키쌍으로 서명 → JWKS 의 public key 로 verify 실패
            val foreignKeyPair = generateEcKeyPair()
            val now = Date()
            val idToken =
                Jwts
                    .builder()
                    .header()
                    .add("kid", testKid)
                    .and()
                    .issuer("https://appleid.apple.com")
                    .subject("001234.xx")
                    .audience()
                    .add("com.test.service")
                    .and()
                    .issuedAt(now)
                    .expiration(Date(now.time + 60_000))
                    .signWith(foreignKeyPair.private)
                    .compact()

            assertFailsWith<OAuthException> {
                client.verifyWithJwks(idToken, expectedAud = "com.test.service", jwksJson = testJwksJson)
            }
        }
    }

    // ---- helpers ----

    private fun signAppleIdToken(
        audience: String,
        subject: String,
        issuer: String = "https://appleid.apple.com",
        kid: String = testKid,
        privateKey: java.security.PrivateKey = testKeyPair.private,
    ): String {
        val now = Date()
        return Jwts
            .builder()
            .header()
            .add("kid", kid)
            .and()
            .issuer(issuer)
            .subject(subject)
            .audience()
            .add(audience)
            .and()
            .issuedAt(now)
            .expiration(Date(now.time + 60_000))
            .signWith(privateKey)
            .compact()
    }

    private fun generateEcKeyPair(): KeyPair {
        val gen = KeyPairGenerator.getInstance("EC")
        gen.initialize(ECGenParameterSpec("secp256r1"))
        return gen.generateKeyPair()
    }

    private fun buildJwksJson(publicKey: ECPublicKey, kid: String): String {
        // 단위 테스트가 흉내내는 Apple JWKS — public key 만 노출하는 EC JWK 한 장.
        val jwk = Jwks.builder().key(publicKey).id(kid).build()
        // Apple JWKS 형식: { "keys": [ {<jwk>}, ... ] }
        val singleJwkJson = Jwks.json(jwk)
        return """{"keys":[$singleJwkJson]}"""
    }

    private fun encodeP8Pem(privateKey: ECPrivateKey): String {
        val der = privateKey.encoded
        val b64 = Base64.getEncoder().encodeToString(der)
        // 64자 단위 줄바꿈은 PEM 관례지만, parseEcPrivateKey 가 모든 개행을 strip 하므로 굳이 안 해도 됨.
        return "-----BEGIN PRIVATE KEY-----\n$b64\n-----END PRIVATE KEY-----"
    }

    // 컴파일러 안 쓰이는 unused 경고 회피
    private val unused = UUID.randomUUID()
}
