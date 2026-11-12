package com.example.arptapp.domain.detection

import com.example.arptapp.domain.classifier.ExerciseType
import java.util.Collections

/** Immutable specification, NOT verification. Profile existence never authorizes camera inference. */
class YoloModelProfile internal constructor(val descriptor: YoloModelDescriptor, classes: List<ExerciseType>) {
    init {
        require(KnownYoloModels.contains(descriptor))
        require(classes.isNotEmpty() && classes.distinct().size == classes.size && classes.all { it in ExerciseType.supported })
    }
    val inputShape: List<Int> = Collections.unmodifiableList(listOf(1, 3, 640, 640))
    val outputShape: List<Int> = Collections.unmodifiableList(listOf(1, 4 + classes.size, 8400))
    val classMapping: Map<Int, ExerciseType> = Collections.unmodifiableMap(classes.withIndex().associate { it.index to it.value })
    val supportedExercises: Set<ExerciseType> = Collections.unmodifiableSet(LinkedHashSet(classes))
    val countKeys: List<String> = Collections.unmodifiableList(classes.map { it.name } + "UNKNOWN")
    val inputBytes: Int = inputShape.fold(4, Math::multiplyExact)
    val outputBytes: Int = outputShape.fold(4, Math::multiplyExact)
    val anchors: Int get() = outputShape.last()
}

/** Historical EXP02/03 identities and their two-class semantics remain independent of EXP04. */
object YoloModelProfiles {
    val EXP02 = YoloModelProfile(KnownYoloModels.EXP02, listOf(ExerciseType.SQUAT, ExerciseType.SHOULDER_PRESS))
    val EXP03 = YoloModelProfile(KnownYoloModels.EXP03, listOf(ExerciseType.SQUAT, ExerciseType.SHOULDER_PRESS))
    val EXP04 = YoloModelProfile(KnownYoloModels.EXP04, listOf(ExerciseType.SQUAT, ExerciseType.SHOULDER_PRESS,
        ExerciseType.BICEP_CURL, ExerciseType.PUSH_UP, ExerciseType.BENT_OVER_ROW, ExerciseType.TRICEPS_EXTENSION, ExerciseType.SIDE_BEND))
    fun find(modelId: String): YoloModelProfile? = when (modelId) {
        KnownYoloModels.EXP02.modelId -> EXP02; KnownYoloModels.EXP03.modelId -> EXP03
        KnownYoloModels.EXP04.modelId -> EXP04; else -> null
    }
    fun requireModel(modelId: String): YoloModelProfile = find(modelId) ?: throw YoloModelIdentityMismatch()
    fun forIdentity(identity: YoloModelDescriptor): YoloModelProfile {
        if (!KnownYoloModels.contains(identity)) throw YoloModelIdentityMismatch()
        return requireModel(identity.modelId)
    }
}
