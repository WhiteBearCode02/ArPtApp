package com.example.arptapp.domain.report

/** Same conservative body-part evidence binding as Edge. Lexical, never a semantic guarantee. */
object MetricClaimValidator {
    fun validate(texts: List<String>, reps: List<StructuredRepAnalysis>, references: Set<String>? = null) {
        val fields = mutableSetOf<String>()
        val issues = mutableSetOf<FormErrorType>()
        reps.forEach { rep ->
            ExerciseMetricPolicy.joints(rep.jointMetrics).keys.forEach { if (references == null || "jointMetrics.$it" in references) fields += it }
            ExerciseMetricPolicy.asymmetries(rep.asymmetryMetrics).keys.forEach { if (references == null || "asymmetryMetrics.$it" in references) fields += it }
            rep.errorEvents.forEach { if (references == null || DirectionalClaimValidator.key(it) in references || "issue:${it.type.name}" in references) issues += it.type }
        }
        fun has(pattern: String) = fields.any { Regex(pattern).containsMatchIn(it) }
        val checks = listOf(
            "무릎|\\bknee\\b" to (has("Knee|knee") || issues.any { "KNEE" in it.name }),
            "고관절|hip\\s*angle" to (has("Hip|hip") || FormErrorType.HIP_ASYMMETRY in issues),
            "팔꿈치|\\belbow\\b" to (has("Elbow|elbow") || FormErrorType.ELBOW_ASYMMETRY in issues),
            "상완|upper\\s*arm" to (has("upperArm") || FormErrorType.UPPER_ARM_POSITION_VARIATION in issues),
            "몸통|상체|torso|trunk" to (has("torso|bodyLine|Lateral|returnToNeutral") || issues.any { Regex("LEAN|TRUNK|BODY_LINE|LATERAL|RETURN_TO").containsMatchIn(it.name) }),
            "어깨\\s*높이|shoulder\\s*height" to has("shoulderHeight"),
            "측굴|lateral\\s*flexion" to has("Lateral"))
        texts.forEach { text -> checks.forEach { (pattern, measured) ->
            require(measured || !Regex(pattern, RegexOption.IGNORE_CASE).containsMatchIn(text)) { "Unmeasured body metric claim" }
        } }
    }
}
