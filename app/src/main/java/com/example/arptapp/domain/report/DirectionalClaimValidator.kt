package com.example.arptapp.domain.report

/** Conservative lexical guard. One side/phase assertion per sentence; not a semantic proof. */
object DirectionalClaimValidator {
    fun key(e: FormErrorEvent) = "event:${e.type.name}:${e.side?.name ?: "UNKNOWN"}:${e.phase?.name ?: "UNKNOWN"}"
    private val sidePatterns = mapOf(BodySide.LEFT to Regex("왼쪽|좌측|왼\\s*(?:무릎|팔)|\\bleft\\b", RegexOption.IGNORE_CASE),
        BodySide.RIGHT to Regex("오른쪽|우측|오른\\s*(?:무릎|팔)|\\bright\\b", RegexOption.IGNORE_CASE))
    private val phasePatterns = mapOf(
        MovementPhase.DESCENT to Regex("내려가|내려오|내려갈|내려가기|하강|복귀|\\bdescent\\b", RegexOption.IGNORE_CASE),
        MovementPhase.BOTTOM to Regex("하단|최저점|\\bbottom\\b", RegexOption.IGNORE_CASE),
        MovementPhase.ASCENT to Regex("올라가|올라오|올라갈|올라가기|밀어\\s*올|상승|\\bascent\\b", RegexOption.IGNORE_CASE))
    fun validate(texts: List<String>, reps: List<StructuredRepAnalysis>, references: Set<String>? = null) {
        val exercise = reps.firstOrNull()?.exerciseType.orEmpty()
        val aliases = when (exercise) {
            "BICEP_CURL" -> listOf("팔을 굽히는|flexing", "최대 굽힘|peak flexion", "팔을 펴는|복귀|extending")
            "BENT_OVER_ROW" -> listOf("당기는 구간|pulling", "최대 당김|peak pull", "돌아가는|복귀|returning")
            "TRICEPS_EXTENSION" -> listOf("팔꿈치를 굽히는|복귀|flexing", "전환|bottom", "팔꿈치를 펴는|extending")
            "SIDE_BEND" -> listOf("옆으로 기울이는|bending", "최대 기울임|peak", "중앙으로 돌아오는|복귀|returning")
            else -> null
        }
        val effectivePhases = aliases?.let { patterns -> MovementPhase.entries.associateWith { key ->
            Regex("${patterns[key.ordinal]}|\\b${key.name}\\b", RegexOption.IGNORE_CASE)
        } } ?: phasePatterns
        val observations = reps.flatMap { r -> r.errorEvents.map { r.repNumber to it } }.filter { (_, e) -> references == null ||
            key(e) in references || "issue:${e.type.name}" in references }
        texts.forEach { text ->
            text.split(Regex("[;\\n!?。]|(?<!\\d)\\.(?!\\d)")).forEach sentenceLoop@ { sentence ->
                val sides = sidePatterns.filterValues { it.containsMatchIn(sentence) }.keys
                val phases = effectivePhases.filterValues { it.containsMatchIn(sentence) }.keys
                if (sides.isEmpty() && phases.isEmpty()) return@sentenceLoop
                if (phases.isEmpty() && sides.size == 1 && exercise in setOf("BICEP_CURL", "SIDE_BEND") &&
                    Regex("^(?:사용 팔|움직인 방향)\\s*[:：]\\s*(?:왼쪽|오른쪽)$").matches(sentence.trim()) &&
                    reps.any { it.activeSide == sides.single() } && (references == null || "activeSide" in references)) return@sentenceLoop
                val types = when {
                    Regex("무릎|knee", RegexOption.IGNORE_CASE).containsMatchIn(sentence) -> setOf(FormErrorType.KNEE_VALGUS, FormErrorType.KNEE_ASYMMETRY)
                    Regex("몸통|상체|torso", RegexOption.IGNORE_CASE).containsMatchIn(sentence) -> setOf(FormErrorType.FORWARD_LEAN,
                        FormErrorType.TRUNK_POSITION_VARIATION, FormErrorType.BODY_LINE_VARIATION, FormErrorType.LATERAL_RANGE_ASYMMETRY, FormErrorType.RETURN_TO_NEUTRAL_VARIATION)
                    Regex("팔|상완|elbow", RegexOption.IGNORE_CASE).containsMatchIn(sentence) -> setOf(FormErrorType.ELBOW_ASYMMETRY, FormErrorType.UPPER_ARM_POSITION_VARIATION)
                    else -> FormErrorType.entries.toSet()
                }
                val mentionedReps = Regex("(\\d+)\\s*(?:회차|번째)").findAll(sentence).mapNotNull { it.groupValues[1].toIntOrNull() }.toSet()
                val matching = observations.filter { (number, e) -> e.type in types && (mentionedReps.isEmpty() || number in mentionedReps) }.map { it.second }
                val requestedSides = if (sides.isEmpty()) listOf<BodySide?>(null) else sides.toList()
                val requestedPhases = if (phases.isEmpty()) listOf<MovementPhase?>(null) else phases.toList()
                // Same-event pairing: RIGHT/DESCENT + LEFT/ASCENT never authorizes RIGHT/ASCENT.
                requestedSides.forEach { side -> requestedPhases.forEach { phase ->
                    require(matching.any { (side == null || it.side == side) && (phase == null || it.phase == phase) }) {
                        "Unobserved side/phase claim"
                    }
                } }
            }
        }
    }
}
