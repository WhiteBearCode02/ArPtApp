import cv2
import mediapipe as mp
import pandas as pd
import os

# 1. MediaPipe 설정 (우리 앱과 동일한 관절 추출 엔진)
mp_pose = mp.solutions.pose
pose = mp_pose.Pose(static_image_mode=False, min_detection_confidence=0.5)

def extract_landmarks(video_path):
    cap = cv2.VideoCapture(video_path)
    data = []

    while cap.isOpened():
        success, frame = cap.read()
        if not success: break

        # AI 추론을 위해 이미지 색상 변환 (BGR -> RGB)
        results = pose.process(cv2.cvtColor(frame, cv2.COLOR_BGR2RGB))

        if results.pose_landmarks:
            # 33개 관절의 x, y, z, visibility 데이터를 추출합니다.
            landmarks = []
            for lm in results.pose_landmarks.landmark:
                landmarks.extend([lm.x, lm.y, lm.z, lm.visibility])
            data.append(landmarks)

    cap.release()
    return data

# [메인 실행] data 폴더 안의 영상들을 읽어 CSV로 저장합니다.
if __name__ == "__main__":
    # 영상이 들어있는 경로 설정
    video_folder = "./data/SQUAT" 
    all_features = []

    for file_name in os.listdir(video_folder):
        if file_name.endswith((".mp4", ".avi")):
            print(f"분석 중: {file_name}")
            path = os.path.join(video_folder, file_name)
            landmarks_sequence = extract_landmarks(path)
            all_features.append(landmarks_sequence)

    # 추출된 데이터를 나중에 학습하기 좋게 저장합니다.
    print("전처리 완료! 이제 이 데이터를 AI 모델 학습에 사용합니다.")