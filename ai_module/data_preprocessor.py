import cv2
import mediapipe as mp
import pandas as pd
import os

# MediaPipe Pose 초기화
mp_pose = mp.solutions.pose
pose = mp_pose.Pose(static_image_mode=False, min_detection_confidence=0.5)

def process_videos():
    base_path = "./data"
    exercise_types = ['SQUAT', 'LUNGE', 'READY']
    all_rows = []

    for label in exercise_types:
        folder_path = os.path.join(base_path, label)
        if not os.path.exists(folder_path): continue

        for video_name in os.listdir(folder_path):
            if not video_name.endswith(('.mp4', '.avi')): continue
            
            video_path = os.path.join(folder_path, video_name)
            cap = cv2.VideoCapture(video_path)
            print(f"[{label}] 분석 중: {video_name}")

            while cap.isOpened():
                success, frame = cap.read()
                if not success: break

                # RGB 변환 및 포즈 추출
                results = pose.process(cv2.cvtColor(frame, cv2.COLOR_BGR2RGB))

                if results.pose_landmarks:
                    # 33개 관절의 x, y, z, visibility 추출 (총 132개 숫자)
                    landmarks = []
                    for lm in results.pose_landmarks.landmark:
                        landmarks.extend([lm.x, lm.y, lm.z, lm.visibility])
                    
                    # 데이터 한 줄에 [라벨, 좌표들...] 형태로 저장
                    all_rows.append([label] + landmarks)

            cap.release()

    # 데이터프레임 생성 및 CSV 저장
    df = pd.DataFrame(all_rows)
    df.to_csv("raw_data.csv", index=False)
    print(f"--- 전처리 완료! 총 {len(all_rows)}프레임의 데이터가 raw_data.csv에 저장되었습니다. ---")

if __name__ == "__main__":
    process_videos()