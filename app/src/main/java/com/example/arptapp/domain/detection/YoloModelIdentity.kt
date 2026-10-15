package com.example.arptapp.domain.detection

import java.nio.ByteBuffer
import java.security.MessageDigest

/** Checks once at initialization, never per frame; no model bytes/paths are logged or persisted. */
object YoloModelIdentity {
    fun validate(model: ByteBuffer) {
        val copy = model.asReadOnlyBuffer()
        if (copy.remaining().toLong() != YoloModelPolicy.CANONICAL_MODEL_BYTES) throw YoloModelIdentityMismatch()
        val digest = MessageDigest.getInstance("SHA-256")
        val chunk = ByteArray(8192)
        while (copy.hasRemaining()) {
            val count = minOf(copy.remaining(), chunk.size)
            copy.get(chunk, 0, count); digest.update(chunk, 0, count)
        }
        val hash = digest.digest().joinToString("") { "%02x".format(it.toInt() and 255) }
        if (hash != YoloModelPolicy.CANONICAL_SHA256) throw YoloModelIdentityMismatch()
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
