🏋️‍♂️ ArPtApp: 3D AR 기반 포즈 추정 모델을 활용한 피트니스 자세 교정 앱
AI Pose Estimation 기술을 활용하여 실시간으로 운동 자세를 교정해주는 지능형 홈 트레이닝 및 웨이트 트레이닝 솔루션입니다.

📌 기획 배경 (Background)
현대 헬스장 이용객들은 홈 트레이닝 환경을 넘어 실전 웨이트 트레이닝 시 다음과 같은 실질적 어려움에 직면해 있습니다.

숙련도 부족: 초보자의 기구 사용 및 정확한 타겟 근육 자세 숙지의 어려움.

높은 진입장벽: 개인 PT의 심리적, 경제적 부담감.

기존 앱의 한계: 단순 영상 시청 위주의 앱들은 실시간 피드백 및 데이터 관리 기능이 부재함.

🎯 기획 목적 (Objectives)
스마트폰 하나만으로 전문가의 도움 없이 안전하게 운동할 수 있는 '실시간 지능형 운동 가이드 시스템' 구축을 최종 목적으로 합니다.

비대면 실시간 자세 교정: MediaPipe 기반의 즉각적인 모션 트래킹 및 교정.

온디바이스 AI 최적화: 서버 통신 없는 빠른 응답 속도로 최적화된 UX 제공.

정량적 운동 관리: Room DB를 통한 체계적인 신체 변화 및 운동 데이터 트래킹.

🔍 AI 포즈 추정 모델 비교 분석

실시간 구동과 모바일 최적화를 위해 객체 탐지 및 포즈 추정 분야의 주요 모델들을 비교 분석하였습니다.

| 비교 항목 | OpenPose | MediaPipe (BlazePose) | YOLOv11 Pose |
| :--- | :---: | :---: | :---: |
| 처리 방식 | Bottom-up (정밀도 중심) | Top-down (속도 중심) | Single-stage (최신 최적화) |
| 지원 관절 수 | 18 ~ 25개 (2D 중심) | 33개 (3D 좌표 지원) | 17개 (COCO 기준) |
| 모바일 성능 | 낮음 | 매우 높음 | 높음 |
| 장점 | 다중 인원 인식에 강함 | 3D Vector 추출 가능 | 검출 및 추정 동시 수행 |
| 한계점 | 모바일 실시간 구동 불가 | 1인 추적에 최적화됨 | V11 기반 최신 런타임 필요 |

> 🎯 최종 모델 채택 근거
> 단일 사용자의 전신을 정밀하게 분석해야 하므로, 33개의 관절 랜드마크를 제공하고 깊이(Z축) 정보를 포함한 3D 좌표를 실시간으로 추출할 수 있는 MediaPipe를 메인 포즈 추정 엔진으로 채택하였습니다.


핵심 기능
1. AI 자세 분석: MediaPipe/YOLO를 이용한 실시간 모션 트래킹
2. 다정한 UX: 사용자 친화적인 피드백 메시지 제공
3. 운동 기록 관리: 데이터 기반의 체계적인 신체 변화 추적

-----------------------------------------------------------------------------------------------------------------------------------------------------

🛠 기술 스택
Language: Kotlin 1.9.x

UI Framework: XML (View-based) with ViewBinding

Architecture: Android Jetpack (Lifecycle, Intent, Activity Result API)

Hardware Interface: CameraX (Core, Lifecycle, View)

AI Engine: MediaPipe Tasks Vision 0.10.x

Database: Room Persistence Library

Background Service: AlarmManager, BroadcastReceiver, TTS Engine

Mathematical Logic: Vector Trigonometry (Atan2 based)

Graphics API: Android Canvas API (Custom Drawing)


🚀 주요 개발 성과
1. UI/UX 디자인 시스템 및 인프라 구축
Branding: Deep Matte Dark 테마와 Action Blue 포인트를 통한 고대비 UI 설계로 시인성 확보.

Security Flow: 로그인 성공 시 MainActivity를 종료(finish)하여 보안 스택을 관리하는 로직 적용.

Runtime Permission: Activity Result API 기반의 실시간 카메라 및 알림 권한 요청 로직 탑재.

2. 온디바이스 AI 및 시각화 엔진 (AI & Visualization)
MediaPipe Pose Landmarker: 하드웨어 가속(GPU)을 활용한 실시간(30FPS+) 전신 트래킹 구현.

Asynchronous Analysis: ImageAnalysis 유즈케이스와 백그라운드 스레드(Executor)를 분리하여 프레임 드랍 방지.

Custom Overlay System: Canvas API를 활용하여 AI 분석 좌표와 스켈레톤 라인을 프리뷰 위에 실시간 렌더링.

3. 벡터 기반 지능형 운동 알고리즘 (Exercise Logic)
Angle Calculation: 골반-무릎-발목 세 점의 벡터를 분석하여 관절 사잇각을 산출하는 calculateAngle 로직 구현.

State Machine Counting: '앉음(Down)'과 '일어남(Up)'의 상태 변화를 추적하는 상태 머신 설계를 통해 중복 없는 정밀 카운팅 시스템 탑재.

4. 사용자 경험 고도화 (Advanced UX)
TTS Feedback: 운동 횟수 달성 시 상단 UI 업데이트와 동시에 음성으로 카운트를 읽어주는 멀티모달 피드백 제공.

Persistence & Reminder: Room DB 기반의 운동 기록 저장 및 AlarmManager를 활용한 매일 오후 8시 운동 권장 알림 시스템 구축.