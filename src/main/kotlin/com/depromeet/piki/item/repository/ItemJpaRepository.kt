package com.depromeet.piki.item.repository

import com.depromeet.piki.item.domain.Item
import org.springframework.data.jpa.repository.JpaRepository

interface ItemJpaRepository : JpaRepository<Item, Long>
