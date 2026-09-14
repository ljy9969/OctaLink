# 런북: 인스타그램 게시 커넥터 (octalink_mma) — ✅ 활성

> ⚠️ **보안**: 아이디/비밀번호로 자동화하지 않는다(약관 위반·정지·보안 위험).
> 게시는 **Meta 그래프 API 시스템 사용자 토큰(무기한)** 으로만. 토큰 노출 시 비즈니스 설정에서 재발급.

현재 연결 정보(이미 `.env`에 저장·검증됨):
- IG 계정: `octalink_mma` / **IG_USER_ID=`17841432089964910`**
- 페이스북 페이지: OctaLink (id `1291347837396129`)
- 시스템 사용자: `octalink-ops` (무기한 토큰, 권한: instagram_basic·instagram_content_publish·pages_show_list·pages_read_engagement·business_management)

## 1. 계정/토큰 준비 (완료된 방식 기록)
1. 인스타 `octalink_mma` → **프로페셔널** 전환.
2. **비즈니스 관리자**(business.facebook.com)에 포트폴리오 + **페이스북 페이지** 생성.
3. **페이지에 IG 연결**(중요: "비즈니스에 IG 추가"가 아니라 **페이지↔IG 연결**이라야 `instagram_business_account` 엣지가 생김).
4. developers.facebook.com → 앱(Business) 생성 → Instagram 제품 추가 → 비즈니스에 앱 연결.
5. 비즈니스 설정 → **시스템 사용자** → 페이지·앱 자산 할당 → **무기한 토큰 생성**(위 권한 체크).
6. IG_USER_ID 조회: `GET /me/accounts?fields=instagram_business_account` → 페이지의 `instagram_business_account.id`.

## 2. 이미지 호스팅 (자동)
IG는 **공개 HTTPS 이미지 URL**을 요구(로컬 업로드 불가). `connectors/storage.mjs`가 로컬 이미지를
**Firebase Storage**(버킷 `octalink-28088.firebasestorage.app`)에 올려 **서명 URL(1h)** 로 만들어 게시에 넘김.
포맷은 **JPEG** 권장(PNG는 거부될 수 있음), 스토리는 9:16(1080×1920), 피드는 4:5~1.91:1.

## 3. 게시 흐름 (게이트)
social 에이전트 초안 → `approvals/` 제안 → **사람 승인** → 게시.
- 로컬 이미지 자동 호스팅 + 게시(스토리):
  ```js
  import { publishLocal } from './ops/connectors/instagram.mjs';
  await publishLocal({ token: process.env.INSTAGRAM_TOKEN, igUserId: process.env.IG_USER_ID,
    imagePath: 'C:/.../recruit_story.jpg', mediaType: 'STORIES',
    credentialJson: fs.readFileSync(process.env.FIREBASE_SERVICE_ACCOUNT, 'utf8') });
  ```
- 이미 공개 URL이 있으면 `publish({..., imageUrl, mediaType})` 직접 사용. `mediaType`: `IMAGE`(피드 기본)·`STORIES`(스토리, 캡션 미지원).
- 토큰/유저ID 없으면 커넥터 no-op(안전). 게시 한도 확인: `GET /{ig}/content_publishing_limit`(24h당 50).

## 검증 이력
2026-09-14 스토리 테스트 게시 성공(media_product_type=STORY). recruit_story.jpg(1080×1920).
