# 런북: 인스타그램 게시 커넥터 (octalink_mma)

> ⚠️ **보안**: 아이디/비밀번호로 자동화하지 않는다(약관 위반·정지·보안 위험).
> 채팅에 노출된 비밀번호는 **즉시 변경**할 것. 게시는 **Meta 그래프 API 액세스 토큰**으로만.

## 1. 계정 준비
1. 인스타 `octalink_mma`를 **프로페셔널(비즈니스/크리에이터) 계정**으로 전환.
2. **페이스북 페이지** 하나 만들어 이 인스타 계정과 연결.

## 2. Meta 앱 + 토큰
3. developers.facebook.com → 앱 생성(유형: Business).
4. 제품에 **Instagram Graph API** 추가, 권한: `instagram_basic`, `instagram_content_publish`, `pages_read_engagement`.
5. 그래프 API 탐색기 또는 앱에서 **장기 액세스 토큰** + **IG 비즈니스 계정 ID(igUserId)** 발급.
6. `ops/mcp/.env`에:
   ```
   INSTAGRAM_TOKEN=EAAG...   # 장기 토큰
   IG_USER_ID=178414...      # 인스타 비즈니스 계정 ID
   ```

## 3. 게시 흐름 (게이트)
- social 에이전트가 초안 → `approvals/`에 제안서 → **사람이 승인** → 승인된 캡션 + **공개 이미지 URL**로 게시:
  ```
  node -e "import('./ops/connectors/instagram.mjs').then(m=>m.publish({token:process.env.INSTAGRAM_TOKEN,igUserId:process.env.IG_USER_ID,imageUrl:'https://.../story.jpg',caption:'...'}).then(console.log))"
  ```
- 인스타 그래프 API는 **텍스트 전용 게시 불가** → 이미지/영상 URL(공개 접근)이 필요.
- 토큰/유저ID 없으면 커넥터는 no-op(안전).
