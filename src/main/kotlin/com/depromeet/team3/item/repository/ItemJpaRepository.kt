package com.depromeet.team3.item.repository

import com.depromeet.team3.item.domain.Item
import org.springframework.data.jpa.repository.JpaRepository

interface ItemJpaRepository : JpaRepository<Item, Long>
