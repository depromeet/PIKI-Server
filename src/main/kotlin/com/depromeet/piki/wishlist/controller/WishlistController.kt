package com.depromeet.piki.wishlist.controller

import com.depromeet.piki.common.response.ApiResponseBody
import com.depromeet.piki.common.response.PageResponse
import com.depromeet.piki.wishlist.controller.dto.WishItemResponse
import com.depromeet.piki.wishlist.controller.dto.WishlistRegisterRequest
import com.depromeet.piki.wishlist.controller.dto.WishlistUpdateRequest
import com.depromeet.piki.wishlist.service.WishlistService
import jakarta.validation.Valid
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PatchMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController
import org.springframework.web.multipart.MultipartFile
import java.util.UUID

@RestController
@RequestMapping("/api/v1/wishlists")
class WishlistController(
    private val wishlistService: WishlistService,
) : WishlistApi {
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    override fun register(
        @AuthenticationPrincipal userId: UUID,
        @Valid @RequestBody request: WishlistRegisterRequest,
    ): ApiResponseBody<WishItemResponse> {
        val result = wishlistService.register(rawUrl = request.url, userId = userId)
        return ApiResponseBody.created(WishItemResponse.from(result.wish, result.item))
    }

    @PostMapping("/ocr", consumes = [MediaType.MULTIPART_FORM_DATA_VALUE])
    @ResponseStatus(HttpStatus.CREATED)
    override fun registerFromOcr(
        @AuthenticationPrincipal userId: UUID,
        @RequestParam("image") image: MultipartFile,
    ): ApiResponseBody<WishItemResponse> {
        val result = wishlistService.registerFromOcr(image = image, userId = userId)
        return ApiResponseBody.created(WishItemResponse.from(result.wish, result.item))
    }

    @GetMapping
    override fun getWishlist(
        @AuthenticationPrincipal userId: UUID,
        @RequestParam(required = false) cursor: String?,
        @RequestParam(required = false) size: Int?,
    ): ApiResponseBody<List<WishItemResponse>> {
        val page = wishlistService.getWishlist(userId = userId, rawCursor = cursor, rawSize = size)
        val data = page.entries.map { WishItemResponse.from(it.wish, it.item) }
        return ApiResponseBody.ok(
            data = data,
            pageResponse = PageResponse(nextCursor = page.nextCursor, hasNext = page.hasNext),
        )
    }

    @GetMapping("/{wishId}")
    override fun getWish(
        @AuthenticationPrincipal userId: UUID,
        @PathVariable wishId: Long,
    ): ApiResponseBody<WishItemResponse> {
        val result = wishlistService.getWish(userId = userId, wishId = wishId)
        return ApiResponseBody.ok(WishItemResponse.from(result.wish, result.item))
    }

    @PatchMapping("/{wishId}")
    override fun updateWish(
        @AuthenticationPrincipal userId: UUID,
        @PathVariable wishId: Long,
        @Valid @RequestBody request: WishlistUpdateRequest,
    ): ApiResponseBody<WishItemResponse> {
        val result =
            wishlistService.updateWish(
                userId = userId,
                wishId = wishId,
                name = request.name,
                currentPrice = request.currentPrice,
                imageUrl = request.imageUrl,
                currency = request.currency,
            )
        return ApiResponseBody.ok(WishItemResponse.from(result.wish, result.item))
    }

    @DeleteMapping("/{wishId}")
    override fun deleteWish(
        @AuthenticationPrincipal userId: UUID,
        @PathVariable wishId: Long,
    ): ApiResponseBody<Unit> {
        wishlistService.deleteWish(userId = userId, wishId = wishId)
        return ApiResponseBody.ok()
    }
}
