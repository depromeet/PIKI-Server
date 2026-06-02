package com.depromeet.piki.auth.controller

import com.depromeet.piki.auth.controller.dto.OAuthUrlResponse
import com.depromeet.piki.auth.infrastructure.oauth.OAuthProvider
import com.depromeet.piki.auth.service.OAuthUrlService
import com.depromeet.piki.common.response.ApiResponseBody
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/v1/auth")
class OAuthUrlController(
    private val oAuthUrlService: OAuthUrlService,
) : OAuthUrlApi {
    @GetMapping("/{provider}/url")
    override fun getAuthUrl(
        @PathVariable provider: String,
    ): ApiResponseBody<OAuthUrlResponse> {
        val oAuthProvider = OAuthProvider.from(provider)
        val result = oAuthUrlService.buildUrl(oAuthProvider)
        return ApiResponseBody.ok(OAuthUrlResponse.from(result))
    }
}
