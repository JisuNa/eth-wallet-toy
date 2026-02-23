package com.example.wallet.infrastructure.entity

import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.MappedSuperclass

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
