package com.ethwallet.core.kms

import jakarta.persistence.AttributeConverter
import jakarta.persistence.Converter
import org.springframework.stereotype.Component

@Converter
@Component
class EncryptedStringConverter(
    private val envelopeEncryptionService: EnvelopeEncryptionService,
) : AttributeConverter<String, String> {

    override fun convertToDatabaseColumn(attribute: String?): String? {
        return attribute?.let { envelopeEncryptionService.encrypt(it) }
    }

    override fun convertToEntityAttribute(dbData: String?): String? {
        return dbData?.let { envelopeEncryptionService.decrypt(it) }
    }
}
