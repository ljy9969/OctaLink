# 런북: 로컬 Ollama로 무료 실가동 (버전 C)

> **완전 무료 · 카드·계정·크레딧 전부 불필요.** 모델을 내 PC에서 돌립니다.
> 트레이드오프: 소형 모델(7B)이라 한국어 카피·JSON 판정 품질이 프런티어보다 낮습니다(RAM 되면 :14b 권장).

## 1) Ollama 설치 (최초 1회)
- Windows: https://ollama.com/download 에서 설치, 또는 `winget install Ollama.Ollama`
- 설치되면 백그라운드 서버가 `http://localhost:11434`에서 실행됩니다.

## 2) 모델 pull (무료)
```
ollama pull qwen2.5:7b          # 범용·한국어(약 4.7GB)
ollama pull qwen2.5-coder:7b    # dev 에이전트용 코딩(약 4.7GB)
```
- RAM 여유가 적으면 `qwen2.5:3b`로 낮추고, 품질을 원하면 `qwen2.5:14b`로 올리세요(프로필의 name 수정).

## 3) 버전 C 활성화
```
node ops/engine/apply-profile.mjs ollama
```

## 4) 실행 ($0)
```
node ops/engine/run-chain.mjs research bug        # safe 에이전트 예시
```
- 키·토큰 불필요. `OLLAMA_URL`이 기본값(localhost:11434)이면 `.env`에 아무것도 안 넣어도 됩니다.
- 데이터가 외부로 나가지 않습니다(100% 로컬).

## 버전 전환
```
node ops/engine/apply-profile.mjs openrouter   # 버전 A (OpenRouter, 키 필요)
node ops/engine/apply-profile.mjs ollama       # 버전 C (로컬 무료)
```
