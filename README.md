프로젝트 이름: ArPtApp(3D AR 기반 포즈 추정 모델을 활용한 피트니스 자세 교정 앱 프로젝트)

프로젝트 설명: AI Pose Estimation 기술을 활용하여 실시간으로 운동 자세를 교정해주는 지능형 홈 트레이닝 솔루션입니다.

기획 배경: 홈 트레이닝 환경을 넘어 헬스장에서도 사용자가 자립적으로 올바른 운동을 수행 할 수 있도록 돕는데 초점을 맞췄고 현대 헬스장 이용객들은 다음과 같은 실질적 어려움에 직면해 있습니다.

1. 초보자의 기구 사용 및 자세 숙지 어려움
2. PT의 심리적, 경제적 진입장벽
3. 타 스마트폰 앱들의 활용 한계
4. 운동 기록에 대한 데이터 관리의 부재

기획 목적: 복잡한 헬스장 환경 내에서도 스마트폰 하나만으로 전문가의 도움 없이 정확하게 안전하게 운도할 수 있는 '실시간 지능형 운동 가이드 시스템' 구축을 최종 목적으로 하며, 세부적인 목표는 다음과 같습니다.

1. 비대면 실시간 자세 교정 시스템 구현
2. 온 디바이스 AI 기반의 최적화된 사용자 경험 제공
3. 기구 인식 및 자동 맞춤 가이드 제공
4. 데이터 기반의 정량적 운동 관리

실시간 구동과 모바일 최적화를 위해 객체 탐지 및 포즈 추정 분야의 주요 무델들을 비교 분석하였습니다.

비교 항목	    | OpenPose |	                 | MediaPipe(BlazePose) | 	                | YOLOv11 Pose |
처리 방식	 | Bottom-up(정밀도 중심) |	          | Top-down(속도 중심) |	            | Single-stage(최신 최적화) |
지원 관절 수  | 18 ~ 25개(2D 중심) |	            | 33개(3D 좌표 지원) |	                  | 17개(COCO기준) |
모바일 성능	        | 낮음 |	                        | 매우 높음 |	                           | 높음 |
장점	     | 다중 인원 인식에 강함 |	            | 3D Vector 추출 가능 |	              | 검출과 포즈 추정 동시 수행 |
한계점	    | 모바일 실시간 구동 불가 |	             | 1인 추적에 최적화됨 |	           | V11 기반의 최신 런타임 필요 |

-> 최종적으로 MediaPipe를 선택하게 되었고 그 이유는 다음과 같습니다.
단일 사용자의 전식을 정밀하게 분석해야 하므로, 33개의 관절 랜드마크를 제공하고 깊이(Z축) 정보를 포함한 3D좌표를 실시간으로 추출할 수 있는 MediaPipe를 메인 포즈 추정 엔진으로 채택하였습니다.


핵심 기능
1. AI 자세 분석: MediaPipe/YOLO를 이용한 실시간 모션 트래킹
2. 다정한 UX: 사용자 친화적인 피드백 메시지 제공
3. 운동 기록 관리: 데이터 기반의 체계적인 신체 변화 추적

-----------------------------------------------------------------------------------------------------------------------------------------------------

ArPtApp: AI Motion Analysis PT Solution
Current Status: Milestone 2 - Camera Infrastructure Completed

🛠 기술 스택
Language: Kotlin 1.9.x

UI Framework: XML (View-based) with ViewBinding

Architecture: Android Jetpack (Lifecycle, Intent, Activity Result API)

Hardware Interface: CameraX (Core, Lifecycle, View)

AI Engine: MediaPipe Tasks Vision 0.10.x

Mathematical Logic: Vector Trigonometry (Atan2 based)

Graphics API: Android Canvas API (Custom Drawing)

🚀 주요 개발 성과
1. UI/UX 디자인 시스템 구축
Branding: Deep Matte Dark 테마와 Action Blue 포인트를 통한 고대비 UI 설계로 시인성 확보.

Components: 12dp의 라운드 코너가 적용된 입력 필드 및 버튼 디자인 시스템화.

2. 사용자 인증 및 화면 전환 아키텍처
Security Flow: 로그인 성공 시 MainActivity를 종료(finish)하여 보안 스택을 관리하는 현업 수준의 로직 적용.

Navigation: Intent 메신저를 활용한 액티비티 간 데이터 흐름 및 화면 전환 구현.

3. 보안 및 하드웨어 권한 시스템 (Critical)
Runtime Permission: 안드로이드 6.0 이상 정책에 대응하는 Activity Result API 기반 실시간 카메라 권한 요청 로직 탑재.

Manifest Optimization: 카메라 하드웨어 필수 조건(uses-feature) 선언을 통한 앱 안정성 강화.

4. AI 카메라 인프라 준비
CameraX Integration: 구글의 최신 카메라 라이브러리(CameraX) 의존성 설정 및 하드웨어 가속 준비 완료.

5. 온디바이스 AI 포즈 추정 엔진 최적화 (AI Core)

MediaPipe Pose Landmarker: 하드웨어 가속(GPU)을 활용한 .task 바이너리 모델을 성공적으로 통합하여 실시간(30FPS+) 전신 트래킹 구현.

Asynchronous Image Analysis: ImageAnalysis 유즈케이스와 백그라운드 스레드(Executor)를 분리 설계하여 프레임 드랍 없는 안정적인 분석 환경 구축.

6. 실시간 모션 시각화 및 좌표 매핑 (Visualization)

Custom Overlay System: 카메라 프리뷰 레이어 위에 독립적인 OverlayView를 설계하여 AI가 분석한 관절 좌표와 스켈레톤 라인을 실시간 렌더링.

Coordinate Normalization: AI 모델의 정규화 좌표(0.0~1.0)를 디바이스 해상도에 맞춰 픽셀 단위로 정밀하게 변환하는 매핑 알고리즘 적용.

7. 벡터 기반 지능형 운동 알고리즘 (Exercise Logic)

Trigonometric Angle Calculation: 골반-무릎-발목 세 점의 벡터를 분석하여 관절 사잇각을 산출하는 calculateAngle 수학 로직 구현.

State Machine Counting: '앉음(Down)'과 '일어남(Up)'의 상태 변화를 추적하는 상태 머신(State Machine) 설계를 통해 중복 없는 정확한 스쿼트 카운팅 시스템 탑재.

8. 실시간 사용자 피드백 시스템 (UX/UI)

Interactive UI Update: 운동 횟수 달성 시 상단 타이틀 텍스트 실시간 변경 및 토스트 메시지를 통한 즉각적인 동기부여 피드백 제공.