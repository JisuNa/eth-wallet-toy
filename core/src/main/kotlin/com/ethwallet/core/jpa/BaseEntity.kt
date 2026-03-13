package com.ethwallet.core.jpa

import jakarta.persistence.*
import org.springframework.data.annotation.CreatedDate
import org.springframework.data.annotation.LastModifiedDate
import org.springframework.data.jpa.domain.support.AuditingEntityListener
import java.time.LocalDateTime

@MappedSuperclass
abstract class BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long = 0L

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other == null || this::class != other::class) return false
        other as BaseEntity
        if (id == 0L) return false
        return id == other.id
    }

    override fun hashCode(): Int {
        if (id == 0L) return System.identityHashCode(this)
        return id.hashCode()
    }
}

@MappedSuperclass
@EntityListeners(AuditingEntityListener::class)
abstract class BaseAuditEntity : BaseEntity() {

    @CreatedDate
    @Column(updatable = false)
    var createdAt: LocalDateTime = LocalDateTime.MIN
        protected set
}

@MappedSuperclass
abstract class BaseModifyAuditEntity : BaseAuditEntity() {

    @LastModifiedDate
    var updatedAt: LocalDateTime = LocalDateTime.MIN
        protected set
}
