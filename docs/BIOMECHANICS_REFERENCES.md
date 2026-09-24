# AIRPTCoach 생체역학 근거 및 알고리즘 매핑

> 상태: 1단계 저장소 분석 및 2단계 논문-알고리즘 매핑 완료  
> 최종 검토일: 2026-09-24  
> 범위: MediaPipe Pose 기반 스쿼트·스탠딩 오버헤드 프레스 분석

이 문서는 현재 앱이 실제로 수행하는 계산과 향후 구현할 자세 분석의 근거를 분리해 기록한다. 논문에 등장하는 실험 조건이나 집단 평균은 곧바로 개인의 정상/오류 판정 기준이 아니다. AIRPTCoach의 결과는 운동 보조 정보이며 진단, 치료 또는 부상 위험 예측을 대신하지 않는다.

## 1. 근거 출처 유형

모든 수치 기준은 아래 네 유형 중 하나를 반드시 가진다.

| 유형 | 의미 | 앱에서의 사용 원칙 |
|---|---|---|
| `PAPER_DIRECT` | 논문이 직접 정의하거나 보고한 값 | 논문의 대상·과제·측정 방법과 동일한 범위에서만 사용 |
| `PAPER_DERIVED` | 논문의 정의나 결과로부터 명시적인 식을 통해 계산한 값 | 계산식과 가정을 함께 기록하고 별도 검증 필요 |
| `ENGINEERING_INITIAL` | 상태 전이, 노이즈 억제 등을 위한 초기 공학 값 | 임상적 정상/이상으로 표현하지 않고 데이터로 재조정 |
| `EXPERT_VERIFIED` | 자격을 갖춘 전문가와 재현성 검증을 마친 값 | 검증 대상, 표본, 버전 및 승인 이력 기록 |

현재 코드의 자세 판정 및 반복 횟수 관련 임계값은 모두 `ENGINEERING_INITIAL`이다. 아래 논문 중 어떤 것도 현 앱의 `90°`, `160°`, `20°`, `0.08`을 보편적인 정상/오류 기준으로 제시하지 않는다.

## 2. 1단계: 현재 저장소 분석

### 2.1 실제 실행 흐름

```text
CameraX ImageProxy
  -> 프레임 회전/비트맵 변환
  -> MediaPipe Pose Landmarker (LIVE_STREAM)
  -> result.landmarks(): 정규화 이미지 랜드마크
  -> 가장 큰 바운딩 박스의 사람 선택
  -> 랜드마크 규칙 기반 운동 분류(5 프레임 안정화)
  -> 대표 관절각 계산
  -> ExerciseCounter 상태 전이 및 횟수 증가
  -> FormErrorAnalyzer 오류 태그 + DTW 유사 점수
  -> MainViewModel RepRecord
  -> Room ExerciseRecord + Supabase 세션/회차 업로드
  -> OverlayView 및 결과 리포트
```

### 2.2 컴포넌트별 현황과 위험

| 컴포넌트 | 현재 동작 | 재사용 가능 요소 | 확인된 위험/불일치 |
|---|---|---|---|
| `DashboardActivity` | Pose Landmarker 초기화, 사람 선택, 분류·카운트·점수·오버레이 연결 | CameraX/MediaPipe 연결과 세션 흐름 | 콜백의 주요 분석이 UI 스레드에서 수행됨. `setNumPoses`가 명시되지 않아 다인 검출 의도와 설정을 재확인해야 함 |
| `selectMainPerson` | 검출된 포즈 중 이미지상 바운딩 박스가 가장 큰 포즈 선택 | 사용자 주체 선택 정책의 시작점 | 거리·지속 추적 ID가 없어 프레임 사이 대상 전환 가능. 기본 포즈 수가 1이면 비교 로직이 실질적으로 동작하지 않음 |
| `ExerciseClassifier` | 손목이 양쪽 어깨 위면 숄더 프레스, 양 무릎 내각이 130° 이하면 스쿼트, 비대칭이면 런지로 분류 | 학습 모델 전 fallback | 운동 자세의 일부 구간만 보고 종목을 추정하며, `UNKNOWN`에서 이전 종목을 유지해 오인식이 지속될 수 있음 |
| `Yolo26Classifier` | `yolo11n-pose_int8.tflite` 출력을 3개 운동 라벨 점수처럼 해석하는 코드 | 향후 검증된 분류 모델 어댑터 형태 | 현재 대시보드에서 사용되지 않음. 자산이 운동 분류용으로 학습·검증됐다는 근거가 없어 연결하면 잘못된 분류 가능 |
| `PoseAngleExtractor.analyzePose` | 좌측 한 관절의 2D 내각을 대표값으로 반환 | 단순 상태 전이용 각도 계산 | 가시성이 낮으면 `0.0`을 반환해 실제 0°와 측정 불가를 구분하지 못함 |
| `PoseAngleExtractor.extract*Angles` | 정규화 랜드마크의 x/y/z 벡터 내적으로 무릎·엉덩이·팔꿈치 내각 계산 | 양측 관절 벡터 구성 | `NormalizedLandmark.z`를 포함하지만 실제 미터 단위 world 좌표가 아니므로 해부학적 3D 각도로 부를 수 없음 |
| `CoordinateNormalizer` | 골반 중심 이동, 어깨-엉덩이 거리로 스케일 정규화, 전면 카메라 x 반전 | 기기/거리 변화 완화 아이디어 | 현재 주요 실행 흐름에서 사용되지 않음. 스케일이 0에 가까울 때 보호가 없고, 주석의 3D 표현과 달리 각도는 2D 계산 |
| `ExerciseCounter` | 스쿼트 90°→160°, 숄더 프레스 70°→160° 상태 전이로 반복 완료 | 이력 호환성이 있는 현재 카운터 | 안정 프레임·최소 반복 시간 검증이 없어 경계 노이즈가 횟수로 기록될 수 있음 |
| `SquatAnalyzer` / `SquatRepCounter` | 중앙값 필터, 안정 프레임 3회, 최소 반복 600 ms를 별도로 구현 | 노이즈에 강한 카운트 부품 | 대시보드의 실제 카운터와 중복되고 기준·대표 다리가 다름. 현재 단위 테스트 한 건도 95° 입력과 90° 하강 기준이 불일치 |
| `ShoulderPressAnalyzer` | 양쪽 팔꿈치 평균각으로 독립 상태 전이 | 양측 평균 아이디어 | 실제 실행 경로의 좌측 대표각 방식과 중복·불일치 |
| `FormErrorAnalyzer` | 무릎의 x 오프셋 비율과 어깨-엉덩이 영상 기울기로 태그 생성 | 실시간 피드백 인터페이스 | 카메라 시점 검증이 없고 FPPA가 아님. 측정 불가와 오류 없음이 모두 빈 목록으로 반환됨 |
| `DTWCalculator` / `squat.json` | 합성 각도 시퀀스와 프레임 점수를 비교 | 시퀀스 비교 인터페이스 | 표준 동작의 출처·촬영 조건이 없고, 평균 점수 함수는 실제 DTW 경로 대신 리샘플링된 프레임 평균을 사용 |
| `OverlayView` | 33개 점과 연결선을 표시하고 전면 카메라를 반전 | 사용자에게 인식 대상을 보여주는 핵심 UI | 가시성에 따른 필터/색상 및 측정 가능한 관절·현재 단계 표시가 없음 |
| `MainViewModel` | 회차별 각도, 흔들림, 오류 태그, 구간 시간, 점수를 저장 | 기존 리포트·DB 흐름 | `<=90°`/`>=160°`로 100점을 부여하고 “전문가 수준” 표현을 사용하지만 검증 근거가 없음 |
| Room `ExerciseRecord` | 세션 요약과 JSON 회차 분석 저장, DB v5 | 기존 기록 보존 기반 | 알려지지 않은 마이그레이션 경로에서 `fallbackToDestructiveMigration()`이 데이터 삭제를 허용하므로 제거 전 명시적 마이그레이션 필요 |
| `SupabaseRepository` | `exercise_sessions`, `exercise_rep_records`에 세션/회차 업로드 | 원격 동기화 구조 | 회차별 관절 측정의 정의·단위·시점·품질·근거 버전 필드가 부족함 |
| 결과 리포트 | 회차별 점수 바와 문장 표시 | 기존 상세 화면 | 사용자가 검증할 원시 측정값, 측정 불가 이유, 카메라 시점, 근거 출처가 표시되지 않음 |

### 2.3 현재 좌표와 각도의 의미

- `result.landmarks()`는 영상 폭·높이에 대해 정규화된 이미지 랜드마크다. 이 경로의 z는 world 좌표의 미터 단위 깊이가 아니다.
- 현재 분석은 `result.worldLandmarks()`를 사용하지 않는다.
- 전면 카메라는 오버레이와 정규화 유틸리티에서 x축을 반전하지만, 모든 분석 경로가 동일한 좌표 변환을 공유하지 않는다.
- 관절 내각은 세 점 `A-B-C`에서 B를 꼭짓점으로 계산한다. 완전 신전은 보통 약 180°다.
- 논문에서 말하는 “무릎 굴곡 0°”는 완전 신전을 뜻하는 경우가 많으므로, 현재 내각과 비교하려면 같은 평면·시점이라는 전제 아래 `굴곡각 = 180° - 내각` 변환이 필요하다.
- 정규화 이미지 x/y/z 내적각, 2D 영상각, 모션 캡처의 해부학적 3D 관절각은 서로 같은 측정값이 아니다.

### 2.4 현재 임계값 등록부

| 대상 | 현재 값 | 용도 | 출처 유형 | 검증 상태 |
|---|---:|---|---|---|
| 랜드마크 visibility | 0.65 | 각도/오류 분석 허용 | `ENGINEERING_INITIAL` | 기기·조명·가림 조건 검증 안 됨 |
| 분류 안정 프레임 | 5 frames | 종목 전환 억제 | `ENGINEERING_INITIAL` | FPS 독립 시간 기준 아님 |
| 스쿼트 분류 무릎 내각 | 양측 ≤130° | 스쿼트 추정 | `ENGINEERING_INITIAL` | 분류 정확도 데이터 없음 |
| 런지 분류 무릎 내각 | 한쪽 ≤115°, 반대 ≥145° | 런지 추정 | `ENGINEERING_INITIAL` | 본 문서의 대상 종목 밖, 검증 안 됨 |
| 스쿼트 하강/상승 내각 | ≤90° / ≥160° | 반복 카운트 | `ENGINEERING_INITIAL` | 논문 기반 정상 기준 아님 |
| 숄더 프레스 하단/상단 팔꿈치 내각 | ≤70° / ≥160° | 반복 카운트 | `ENGINEERING_INITIAL` | 논문의 집단 ROM과 직접 비교 불가 |
| `SquatRepCounter` 안정/시간 | 3 frames / 600 ms | 노이즈·비정상 반복 억제 | `ENGINEERING_INITIAL` | 실제 대시보드 미사용 |
| 무릎 안쪽 이동 비율 | >0.08 | `ERROR_KNEE_VALGUS` | `ENGINEERING_INITIAL` | FPPA가 아니며 시점 의존 |
| 몸통 영상 기울기 | >20° | `ERROR_FORWARD_LEAN` | `ENGINEERING_INITIAL` | 부하·목적·경골 기울기 미고려 |
| DTW 프레임 차이 | 30° | 유사도 점수 | `ENGINEERING_INITIAL` | 표준 데이터 및 타당도 검증 없음 |
| YOLO 분류 confidence | 0.45 | 휴면 분류기 출력 허용 | `ENGINEERING_INITIAL` | 모델-라벨 계약 자체가 검증되지 않음 |

## 3. 2단계: 논문-알고리즘 매핑

### 3.1 근거 논문 요약

| 논문 | 연구 유형·대상·과제 | 직접 지지하는 내용 | 지지하지 않는 내용 |
|---|---|---|---|
| Straub & Powers (2024), [DOI](https://doi.org/10.26603/001c.94600), [PMC](https://pmc.ncbi.nlm.nih.gov/articles/PMC10987311/) | 근거 수준 5의 임상 해설/생체역학 문헌 고찰 | 스쿼트의 너비, 발 회전, 몸통·경골 기울기, 깊이가 관절 모멘트와 근육 요구를 바꿈. 무릎 굴곡 기준으로 얕은 0–90°, 중간 90–110°/대퇴 평행, 깊은 110–135°를 조작적으로 정의. 몸통-경골 기울기 차이로 hip/knee extensor bias를 기술 | 스마트폰 2D 랜드마크의 정확도, 개인별 정상 자세, 통증·부상 진단, 현 앱의 카운트/오류 임계값 |
| Escamilla (2001), [DOI](https://doi.org/10.1097/00005768-200101000-00020), [PubMed](https://pubmed.ncbi.nlm.nih.gov/11194098/) | 동적 스쿼트의 무릎 생체역학 고찰 | 굴곡 증가에 따라 슬개대퇴 및 경골대퇴 압박·전단력이 대체로 증가. 많은 재활 대상에 0–50° 기능 범위, 건강한 운동선수의 평행 스쿼트에 0–100° 굴곡을 논의 | 사용자에게 50°/100°를 정상·오류 점수 경계로 적용하는 것, 개인 질환별 처방, 2D 자세 추정의 타당도 |
| Gwynne & Curran (2014), [PMC](https://pmc.ncbi.nlm.nih.gov/articles/PMC4275194/) | 건강하고 활동적인 성인 18명, 전면 촬영, 60° 무릎 굴곡의 맨발 단일다리 스쿼트, 3회 평균 | FPPA 정의와 부호, 2D 측정의 반복 신뢰도 및 3D 측정과의 상관. 연구 내 상관 `r=0.64–0.78`, 검사자 내 ICC 0.86, 검사자 간 ICC 0.74, SEM 약 2–4° | 양발 스쿼트의 병적 valgus 컷오프, MediaPipe 무표식 랜드마크의 동일 정확도, 단일 프레임으로 부상 위험 진단 |
| An et al. (2025), [DOI](https://doi.org/10.5103/KJAB.2025.35.2.76), [원문](https://e-kjab.org/archive/detail/304) | 저항운동 경험 남성 14명, 70% 1-RM, 바벨/머신 스탠딩 오버헤드 프레스, 250 Hz 3D 모션캡처 및 2,000 Hz EMG | 상승·하강 구간의 어깨/팔꿈치/몸통 ROM을 분리해 기록할 필요, 기구 조건에 따라 ROM과 근활성이 달라짐. 예: 상승 팔꿈치 굴곡/신전 ROM은 바벨 -102.30±9.88°, 머신 -94.91±9.76° | 맨몸·덤벨·밴드 프레스의 정상 컷오프, 2D MediaPipe 각도와 3D ROM의 등가성, EMG나 부하 궤적을 카메라만으로 추정하는 것 |

### 3.2 수식과 좌표 정의

#### 2D 세 점 내각

영상 평면의 세 점 `A`, `B`, `C`에서 B를 꼭짓점으로 한다.

```text
u = A - B
v = C - B
theta = atan2(|u_x v_y - u_y v_x|, u · v) * 180 / pi
```

- 결과 범위: 0–180°
- 무릎: `hip-knee-ankle`, 팔꿈치: `shoulder-elbow-wrist`
- 장점: `acos`만 사용할 때보다 거의 평행한 벡터에서 수치적으로 안정적이다.
- 제한: 카메라 광축에 대한 회전과 원근 왜곡 때문에 관절의 실제 3D 각도와 달라진다.

#### 정규화 x/y/z 벡터 내각

현재 코드가 일부 경로에서 사용하는 식은 `acos((u·v)/(|u||v|))`이다. 식 자체는 벡터 내각이지만 입력이 `NormalizedLandmark`이므로 결과 이름은 `normalizedLandmarkAngle`처럼 좌표계를 드러내야 한다. MediaPipe world landmark를 사용하고 축·시점·좌표 변환을 검증하기 전에는 “해부학적 3D 관절각”으로 표시하지 않는다.

#### 무릎 굴곡각과 내각 변환

```text
kneeFlexionDeg = 180° - kneeInteriorDeg
```

이 변환은 같은 평면에서 신전 상태의 내각을 180°로 정의한 경우에만 유효하다. Straub & Powers 및 Escamilla의 굴곡각을 앱의 내각과 비교할 때 반드시 각도 정의를 함께 표시한다.

#### FPPA 유사 측정

Gwynne & Curran의 FPPA는 전면에서 대퇴/엉덩이 표식선과 발목/무릎 표식선이 이루는 각이다. 해당 논문의 부호는 무릎이 몸 중앙으로 이동한 valgus가 음수, 바깥쪽 varus가 양수다.

MediaPipe 대응은 `hip-knee-ankle`의 전면 2D 투영으로 근사할 수 있으나 다음 차이가 있다.

- 논문은 피부 표식과 통제된 단일다리 스쿼트를 사용했다.
- MediaPipe hip 랜드마크는 ASIS 표식과 동일하지 않다.
- 앱의 주 대상은 양발 스쿼트이며 논문은 단일다리 스쿼트다.
- 따라서 이름은 우선 `frontalKneeProjectionProxyDeg`로 두고, 양발 스쿼트 데이터로 검증하기 전에는 병적 valgus 판정을 하지 않는다.

#### 몸통·경골 기울기

측면 영상에서 수직선에 대한 영상 평면 기울기:

```text
trunkInclination = atan2(horizontal shoulderHip displacement, vertical shoulderHip displacement)
tibiaInclination = atan2(horizontal kneeAnkle displacement, vertical kneeAnkle displacement)
trunkTibiaDifference = trunkInclination - tibiaInclination
```

Straub & Powers가 소개한 `±10°` 범주는 정상/오류가 아니라 스쿼트의 상대적인 hip/knee extensor bias를 설명하는 범주다. 측면 시점이 확인되고 논문과 부호 정의를 맞춘 경우에만 `PAPER_DIRECT` 설명 지표로 사용할 수 있다.

#### 동작 범위(ROM)

```text
ROM = max(angle during phase) - min(angle during phase)
```

An et al.의 ROM은 3D 모션캡처 좌표계와 바벨/머신 70% 1-RM 조건의 구간별 변화량이다. 앱은 2D/landmark 기반 ROM을 별도 이름과 단위로 저장하고 논문 평균을 개인 점수 컷오프로 사용하지 않는다.

### 3.3 MediaPipe 랜드마크 대응

| 지표 | 랜드마크 인덱스 | 적합한 시점 | 출력 의미 |
|---|---|---|---|
| 좌/우 무릎 내각 | 23-25-27 / 24-26-28 | 측면 또는 각 다리가 충분히 분리된 사선 | 영상/랜드마크 좌표의 관절 내각 |
| 좌/우 엉덩이 내각 | 11-23-25 / 12-24-26 | 측면 | 몸통-대퇴 사이의 영상 내각 proxy |
| 좌/우 팔꿈치 내각 | 11-13-15 / 12-14-16 | 전면·사선·측면, 팔이 가려지지 않을 때 | 팔꿈치 신전/굴곡 proxy |
| 전면 무릎 투영각 proxy | 23-25-27 / 24-26-28의 x/y | 정면 | FPPA와 유사한 2D 정렬 지표, 검증 전 진단 금지 |
| 몸통 기울기 | 양쪽 어깨 중점-양쪽 엉덩이 중점 | 측면 | 수직선 대비 영상 평면 기울기 |
| 경골 기울기 | 무릎-발목 | 측면 | 수직선 대비 영상 평면 기울기 |
| 손목 높이/좌우 대칭 | 15/16, 11/12 | 정면 | 프레스 경로의 영상상 상대 높이·비대칭 |

### 3.4 시점별 지원 범위

| 카메라 시점 | 스쿼트 | 숄더 프레스 | 판정 보류 조건 |
|---|---|---|---|
| 정면 | 좌우 대칭, 무릎 투영각 proxy | 팔꿈치·손목 좌우 대칭, 양팔 상승 여부 | 몸통/경골의 시상면 기울기와 깊이 판정 보류 |
| 측면 | 무릎·엉덩이 내각, 깊이 범주, 몸통·경골 기울기 | 팔꿈치 ROM, 몸통 기울기 변화 | FPPA/좌우 대칭 판정 보류 |
| 사선 | 반복 카운트와 일부 ROM의 보조 | 반복 카운트와 손목 높이의 보조 | 정면·측면 전용 지표를 확정 판정하지 않음 |
| 알 수 없음 | 가시성 좋은 관절의 원시값만 표시 | 가시성 좋은 관절의 원시값만 표시 | 오류 태그 대신 `NOT_EVALUABLE_VIEW` |

초기 시점 분류는 어깨·엉덩이의 좌우 간격, 몸통 폭, 사지 겹침을 이용할 수 있으나 이는 `ENGINEERING_INITIAL`이며 실제 촬영 데이터 검증이 필요하다. 시점을 신뢰할 수 없으면 측정값을 숨기기보다 “측정 보류” 이유를 반환한다.

### 3.5 논문별 앱 적용 경계

#### 스쿼트 깊이와 부하

- Straub & Powers의 얕은/중간/깊은 범주는 `PAPER_DIRECT` 설명 레이블로 매핑 가능하다.
- 현재 앱의 무릎 내각 90°는 굴곡각 약 90°에 해당하지만, 이것이 좋은 자세의 보편적인 하한이라는 뜻은 아니다.
- Escamilla의 0–50°와 0–100°는 각각 특정 재활 고려와 건강한 운동선수의 평행 스쿼트 논의다. 사용자 질환 정보를 모르는 앱의 합격/불합격 기준으로 사용하지 않는다.
- 부하, 발 위치, 스탠스, 사용자 목적을 모르면 깊이만으로 안전성 또는 부상 위험을 판정하지 않는다.

#### 무릎 정렬

- Gwynne & Curran이 지지하는 것은 통제된 단일다리 스쿼트에서 FPPA 측정의 신뢰도/타당도다.
- 현재 `inwardOffset / legWidth > 0.08`은 FPPA가 아니며 `ENGINEERING_INITIAL`이다.
- 향후 전면 시점에서 부호가 있는 각도와 시작-최저점 변화량을 모두 저장한다.
- 양발 스쿼트의 오류 임계값은 별도 데이터와 전문가 검증 전까지 두지 않는다.

#### 스탠딩 오버헤드 프레스

- An et al.은 프레스의 상승/하강 구간, 어깨·팔꿈치·몸통 ROM, 기구 조건을 분리해야 함을 지지한다.
- 논문 표의 집단 평균 ROM을 현재 사용자의 정상 범위로 복사하지 않는다.
- 카메라만으로 EMG, 70% 1-RM, 바벨의 정확한 AP/SI 궤적을 추정하지 않는다.
- 앱의 초기 측정 후보는 양측 팔꿈치 내각/ROM, 손목 높이 비대칭, 측면 몸통 기울기 변화이며 모두 좌표계·시점·품질을 함께 저장한다.

## 4. 제안 데이터 계약

향후 작은 단위로 구현할 측정 객체는 값 하나 대신 의미와 신뢰 조건을 보존해야 한다.

```text
AngleMeasurement
  metricId
  valueDeg: Double?
  angleDefinition: INTERIOR | FLEXION | INCLINATION | FPPA_PROXY | ROM
  coordinateSpace: IMAGE_2D | NORMALIZED_LANDMARK | WORLD_LANDMARK
  cameraView: FRONT | SIDE_LEFT | SIDE_RIGHT | OBLIQUE | UNKNOWN
  side: LEFT | RIGHT | BILATERAL | NONE
  quality: VALID | LOW_VISIBILITY | DEGENERATE_VECTOR | WRONG_VIEW | MISSING
  minVisibility
  sourceType
  referenceId
```

회차 결과는 최소한 다음을 포함한다.

```text
RepAnalysis
  repNumber
  detectedExercise
  phaseDurations
  measurements[]
  observations[]       // 관찰 가능한 사실
  guidance[]           // 조건부 개선 안내
  notEvaluableReasons[]
  algorithmVersion
  referenceVersion
```

`observations`에는 “왼쪽 무릎 투영각이 시작점보다 안쪽 방향으로 변함”처럼 측정에 가까운 표현을 사용한다. “부상 위험”, “정상”, “전문가 수준”, “치료 필요” 같은 표현은 검증 없이 생성하지 않는다.

## 5. 안전한 구현 순서

1. 값/좌표계/각도 정의/품질을 가진 `AngleMeasurement`와 `CameraView`를 추가하고 기존 반환값을 어댑터로 유지한다.
2. 2D 각도, 내각↔굴곡각, 좌우 대칭, 퇴화 벡터, 낮은 visibility를 단위 테스트로 고정한다.
3. `0.0` 및 빈 오류 목록 대신 명시적인 `NOT_EVALUABLE` 상태를 도입한다.
4. 대시보드의 실제 카운터와 중복 Analyzer를 하나의 인터페이스로 통합하되 기존 횟수·Room 레코드를 보존한다.
5. 스쿼트 측면 지표와 정면 proxy를 시점별로 분리하고, 임계값 없는 관찰 결과부터 제공한다.
6. 숄더 프레스의 상승/하강 구간별 양측 팔꿈치 ROM과 몸통 기울기 변화를 기록한다.
7. Room에 명시적 마이그레이션을 추가하고 난 뒤 측정 JSON/버전 필드를 확장한다. 데이터 보존 검증 전에는 destructive migration을 제거하지 않는다.
8. Supabase 스키마는 nullable 신규 컬럼 또는 버전된 측정 테이블로 확장해 구버전 앱과 호환한다.
9. 리포트에 회차별 근거, 값·단위, 시점, 측정 불가 이유를 표시한다.
10. 다양한 체형·성별·의복·조명·기기·정면/측면 촬영으로 반복 카운트와 측정 오차를 검증한 후에만 임계값을 조정한다.

## 6. 검증 체크리스트

- [ ] 동일 입력에서 기존 반복 횟수와 새 어댑터 결과를 비교했다.
- [ ] 정면/측면/사선/가림 입력에서 지원하지 않는 지표가 `NOT_EVALUABLE`인지 확인했다.
- [ ] 좌우 반전 후 좌우 레이블과 부호가 일관되는지 확인했다.
- [ ] 정규화 좌표와 world 좌표 결과를 같은 지표명으로 저장하지 않는다.
- [ ] 회차가 완료될 때 사용된 프레임 범위와 최저/최고점이 재현 가능하다.
- [ ] 앱 재설치·DB 업그레이드 없이 기존 기록이 유지된다.
- [ ] 네트워크 실패 시 Room 저장과 세션 종료가 유지된다.
- [ ] 논문 수치가 개인 정상/오류 컷오프로 과장되지 않는다.
- [ ] UI 문구가 진단·치료·부상 예측으로 오해되지 않는다.

## 7. 참고문헌

1. Straub RK, Powers CM. *A Biomechanical Review of the Squat Exercise: Implications for Clinical Practice*. International Journal of Sports Physical Therapy. 2024;19(4):490–501. https://doi.org/10.26603/001c.94600
2. Escamilla RF. *Knee biomechanics of the dynamic squat exercise*. Medicine & Science in Sports & Exercise. 2001;33(1):127–141. https://doi.org/10.1097/00005768-200101000-00020
3. Gwynne CR, Curran SA. *Quantifying frontal plane knee motion during single limb squats: reliability and validity of 2-dimensional measures*. International Journal of Sports Physical Therapy. 2014;9(7):898–906. https://pmc.ncbi.nlm.nih.gov/articles/PMC4275194/
4. An SK, Lim YT, Kwon MS, Lee JW. *Comparison of Kinematic Variables of the Body, Weight Load, and Muscle Activation between Barbell and Machine Conditions during the Standing Overhead Press*. Korean Journal of Applied Biomechanics. 2025;35(2):76–87. https://doi.org/10.5103/KJAB.2025.35.2.76
