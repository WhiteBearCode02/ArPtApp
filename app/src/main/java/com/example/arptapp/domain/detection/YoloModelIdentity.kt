package com.example.arptapp.domain.detection

import java.nio.ByteBuffer
import java.security.MessageDigest

/** Checks once at initialization, never per frame; no model bytes/paths are logged or persisted. */
object YoloModelIdentity {
    fun matches(fileName: String, bytes: Long, sha256: String,
                identity: YoloModelDescriptor = YoloModelPolicy.identity): Boolean =
        KnownYoloModels.contains(identity) && fileName == identity.fileName && bytes == identity.bytes && sha256 == identity.sha256

    fun validate(model: ByteBuffer, identity: YoloModelDescriptor = YoloModelPolicy.identity) {
        if (!KnownYoloModels.contains(identity)) throw YoloModelIdentityMismatch()
        val copy = model.asReadOnlyBuffer()
        if (copy.remaining().toLong() != identity.bytes) throw YoloModelIdentityMismatch()
        val digest = MessageDigest.getInstance("SHA-256")
        val chunk = ByteArray(8192)
        while (copy.hasRemaining()) {
            val count = minOf(copy.remaining(), chunk.size)
            copy.get(chunk, 0, count); digest.update(chunk, 0, count)
        }
        val hash = digest.digest().joinToString("") { "%02x".format(it.toInt() and 255) }
        if (hash != identity.sha256) throw YoloModelIdentityMismatch()
    }
}

/** Distinguishes known op/schema incompatibility, without exposing native exception text to UI/logs. */
object YoloRuntimeCompatibility {
    fun isUnsupported(error: Throwable): Boolean = generateSequence(error) { it.cause }.take(8).any {
        val message = it.message.orEmpty().lowercase(java.util.Locale.ROOT)
        listOf("didn't find op", "did not find op", "unsupported op", "unresolved custom op",
            "op version", "schema version", "not a valid flatbuffer", "builtin opcode", "select tensorflow ops",
            "flex delegate", "unsupported builtin").any(message::contains)
    }
}
