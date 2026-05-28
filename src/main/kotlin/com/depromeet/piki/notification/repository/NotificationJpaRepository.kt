package com.depromeet.piki.notification.repository

import com.depromeet.piki.notification.domain.Notification
import org.springframework.data.jpa.repository.JpaRepository

interface NotificationJpaRepository : JpaRepository<Notification, Long>
