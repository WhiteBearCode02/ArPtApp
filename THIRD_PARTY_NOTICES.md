# 주요 외부 구성요소 고지

2026-10-13 로컬 Gradle 선언/캐시 POM 및 공식 출처 기준의 1차 목록입니다. 라이선스 전문을 수정·대체하지 않으며, 이 목록 자체가 모든 전이 dependency/native 구성요소의 고지를 충족하거나 호환성을 보증하지 않습니다. 공개 APK에는 적용되는 원본 license/NOTICE와 대응 소스 제공 조건을 별도 확인해야 합니다.

| 구성요소 | 현재 버전/사용 | 라이선스 / 출처 |
|---|---|---|
| Ultralytics YOLO26n | 사용자 학습 EXP02 모델, 운동 탐지 연동 예정 | AGPL-3.0 영향을 받는 것으로 관리. [공식 안내](https://www.ultralytics.com/license), [프로젝트](https://github.com/ultralytics/ultralytics) |
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

## 별도로 확인할 자산

- `airptcoach_yolo26n_exp02_fp32.tflite`: Git 제외, 학습 데이터/weight 배포 미확정. [MODEL_CARD.md](MODEL_CARD.md).
- 기존 `yolo11n-pose_int8.tflite`: 이번 작업에서 삭제/교체하지 않음. 원본 다운로드·export 출처 및 개별 배포 고지 증빙은 추가 확인 필요.
- 기존 `pose_landmarker_lite.task`: 기존 기능 보존. MediaPipe 코드 라이선스와 해당 모델 자산의 배포 조건을 동일하다고 가정하지 않으며 원본 모델 카드/배포 고지 확인 필요.

의존성의 라이선스는 각각 유지됩니다. 프로젝트를 AGPL로 운영한다는 의사만으로 타사 파일의 라이선스를 AGPL로 덮어쓰거나 비공개 SDK의 대응 소스를 공개할 권한을 얻는 것은 아닙니다.
