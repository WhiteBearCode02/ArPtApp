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
        val observations = reps.flatMap { r -> r.errorEvents.map { r.repNumber to it } }.filter { (_, e) -> references == null ||
            key(e) in references || "issue:${e.type.name}" in references }
        texts.forEach { text ->
            text.split(Regex("[;\\n!?。]|(?<!\\d)\\.(?!\\d)")).forEach sentenceLoop@ { sentence ->
                val sides = sidePatterns.filterValues { it.containsMatchIn(sentence) }.keys
                val phases = phasePatterns.filterValues { it.containsMatchIn(sentence) }.keys
                if (sides.isEmpty() && phases.isEmpty()) return@sentenceLoop
                val types = when {
                    Regex("무릎|knee", RegexOption.IGNORE_CASE).containsMatchIn(sentence) -> setOf(FormErrorType.KNEE_VALGUS, FormErrorType.KNEE_ASYMMETRY)
                    Regex("몸통|상체|torso", RegexOption.IGNORE_CASE).containsMatchIn(sentence) -> setOf(FormErrorType.FORWARD_LEAN)
                    Regex("팔|elbow", RegexOption.IGNORE_CASE).containsMatchIn(sentence) -> setOf(FormErrorType.ELBOW_ASYMMETRY)
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
