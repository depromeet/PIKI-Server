package com.depromeet.team3.auth.service.dto

class TokenPair(
    val accessToken: String,
    val refreshToken: String,
) {
    override fun toString(): String = "TokenPair(accessToken=${accessToken.take(10)}..., refreshToken=***)"
}
