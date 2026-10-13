# 주요 외부 구성요소 고지

2026-10-13 로컬 Gradle 선언/캐시 POM 및 공식 출처 기준의 1차 목록입니다. 라이선스 전문을 수정·대체하지 않으며, 이 목록 자체가 모든 전이 dependency/native 구성요소의 고지를 충족하거나 호환성을 보증하지 않습니다. 공개 APK에는 적용되는 원본 license/NOTICE와 대응 소스 제공 조건을 별도 확인해야 합니다.

| 구성요소 | 현재 버전/사용 | 라이선스 / 출처 |
|---|---|---|
| Ultralytics YOLO26n | Dataset A similar_dataset 기반 사용자 학습 EXP01/EXP02, 2클래스 운동 탐지; Android 연동 전 | AGPL-3.0 영향을 받는 것으로 관리. [공식 안내](https://www.ultralytics.com/license), [프로젝트](https://github.com/ultralytics/ultralytics) |
| MediaPipe Tasks Vision | 0.10.14, 관절 추적 | Apache-2.0, [원본](https://github.com/google-ai-edge/mediapipe), 캐시 POM 확인 |
| TensorFlow Lite | 2.14.0, 모델 실행 | Apache-2.0, [원본](https://github.com/tensorflow/tensorflow), 캐시 POM 확인 |
| TensorFlow Lite Support | 0.4.4, 기존 보조 도구 | Apache-2.0, [원본](https://github.com/tensorflow/tflite-support), 캐시 POM 확인 |
| LiteRT | 사용자 모델의 export 형식 명칭 | 이번 작업에서 새 LiteRT runtime dependency 추가 없음. [공식 프로젝트](https://github.com/google-ai-edge/LiteRT) |
| AndroidX / CameraX / Room | Kotlin UI·카메라·로컬 저장, 버전은 app/build.gradle.kts 참조 | Apache-2.0, [원본](https://android.googlesource.com/platform/frameworks/support/) |
| Material Components Android | 1.11.0, UI | Apache-2.0, [원본](https://github.com/material-components/material-components-android) |
| Supabase Kotlin | 3.0.0, Auth/PostgREST | MIT, [원본/라이선스](https://github.com/supabase-community/supabase-kt), 캐시 POM 확인 |
| Ktor | 3.0.0-rc-1, HTTP 클라이언트 | Apache-2.0, [원본](https://github.com/ktorio/ktor), 캐시 POM 확인 |
| Kotlin Coroutines | Gradle 직접 선언 1.7.3, 비동기 작업 | Apache-2.0, [원본](https://github.com/Kotlin/kotlinx.coroutines), 캐시 POM 확인; 실제 resolve 버전은 별도 확인 |
| Gson | 2.10.1, JSON | Apache-2.0, [원본](https://github.com/google/gson), 캐시 POM 확인 |
| MPAndroidChart | v3.1.0, 차트 | Apache-2.0, [해당 버전 LICENSE](https://github.com/PhilJay/MPAndroidChart/blob/v3.1.0/LICENSE) |
| Android LocationManager / AndroidX LocationManagerCompat | Android 플랫폼 / 기존 AndroidX Core, 일회성 위치 확인 | 플랫폼 API와 기존 Apache-2.0 AndroidX 사용. [플랫폼 API](https://developer.android.com/reference/android/location/LocationManager) |

Google Play services Location 21.3.0은 2026-10-13 사용자 승인으로 제거했습니다. 과거 POM에 Android SDK License가 표시되어 검토 대상으로 기록했으며, 해당 SDK를 임의로 AGPL 재라이선스하지 않고 사용을 대체했습니다. 이 변경 자체가 다른 dependency/모델/데이터의 조건까지 해결했다는 의미는 아닙니다.

## 모델 자산 — 코드 라이선스와 별도 확인

| 자산 | 현재 사용/추적 상태 | 출처·라이선스 검토 |
|---|---|---|
| `airptcoach_yolo26n_exp02_fp32.tflite` | 로컬 존재, Git ignore, Android 통합 전 | 사용자 학습 YOLO26n / Dataset A RickyRiccio similar_dataset / Ultralytics AGPL 영향과 trained-weight 재배포 추가 검토. [모델 카드](MODEL_CARD.md) |
| `yolo11n-pose_int8.tflite` | Git 추적. 휴면 `utils/Yolo26Classifier.kt`에 로딩 코드가 있으나 현재 실행 경로 호출 미확인 | **unused asset candidate**. EXP02 detector로 간주하지 않음. 원본 다운로드/export 출처·license/attribution 증빙 미확인; 사용자 승인 후 제거 권장 여부 검토. 이번에는 삭제하지 않음 |
| `pose_landmarker_lite.task` | Git 추적, `ui/camera/PoseCameraController.kt`에서 실제 로딩 | 아래 공식 출처·모델 카드 및 로컬 동일성 확인. bundle 전체 구성요소 고지는 추가 확인 |

### pose_landmarker_lite.task 공식 근거

- 공식 사용 안내: [MediaPipe Pose Landmarker](https://ai.google.dev/edge/mediapipe/solutions/vision/pose_landmarker/index).
- 안내 페이지의 lite bundle 다운로드: [공식 모델 자산](https://storage.googleapis.com/mediapipe-models/pose_landmarker/pose_landmarker_lite/float16/latest/pose_landmarker_lite.task).
- 안내 페이지가 연결하는 [BlazePose GHUM 3D 모델 카드](https://storage.googleapis.com/mediapipe-assets/Model%20Card%20BlazePose%20GHUM%203D.pdf)에는 Apache License, Version 2.0이 표시됩니다. 이는 모델 카드에서 확인한 근거이지 MediaPipe **코드** 라이선스에서 추정한 결과가 아닙니다.
- 2026-10-13 로컬 파일 5,777,746 bytes와 MD5 `04a75ddf7c811ac7a1a4523266dd7d88`(base64 `BKdd33yBGsehpFIyZt19iA==`)는 공식 URL의 HEAD Content-Length/x-goog-hash와 일치했습니다. 로컬 SHA-256은 `59929e1d1ee95287735ddd833b19cf4ac46d29bc7afddbbf6753c459690d574a`입니다.
- `latest` URL은 바뀔 수 있으며 해시 일치는 출처의 기술적 근거이지 모든 법적 권리/조건의 보증이 아닙니다. bundle은 detector/landmarker를 포함하므로 구성요소별 원본 license/NOTICE 및 attribution 의무를 공개 전에 확인합니다. **Model asset redistribution terms require additional confirmation** for the complete bundle/notices.
- Google/MediaPipe 출처, 공식 모델 카드 링크와 적용되는 원본 저작권/license/NOTICE를 보존해야 합니다. 문서 링크만으로 배포 고지 의무를 모두 충족했다고 판단하지 않습니다.

## 라이선스 적용 범위

의존성과 자산의 라이선스는 각각 유지합니다. AGPL 운영 의사로 타사 파일의 license를 덮어쓰지 않습니다. Android LocationManager는 플랫폼 API이며 신규 라이브러리를 추가하지 않습니다. Google Play services Location은 previously used / removed 기록일 뿐 현재 dependency가 아닙니다.

`app/build.gradle.kts`의 기존 packaging은 `/META-INF/{AL2.0,LGPL2.1}`을 제외합니다. 해당 설정을 이번에 변경하지 않았지만 공개 APK의 실제 포함 license/NOTICE와 별도 고지 제공 방식은 검토해야 합니다. 현재 목록은 전이 dependency/native 라이브러리까지 완전한 법적 호환성 감사가 아닙니다. 전체 repository LICENSE는 사용자 승인 전 생성하지 않습니다.
