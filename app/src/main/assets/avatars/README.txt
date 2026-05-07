Team Posse Striking — Avatar 이미지 드롭존
=============================================

이 폴더는 카탈로그 등록용 참고용입니다.
실제 런타임에서 코드가 찾는 위치는 다음입니다:

   app/src/main/res/drawable-nodpi/avatar_<id>.png

캐릭터 ID(코드 카탈로그와 매칭):
  avatar_ryu       (Ryu)
  avatar_ken       (Ken)
  avatar_chunli    (Chun-Li)
  avatar_guile     (Guile)
  avatar_zangief   (Zangief)
  avatar_dhalsim   (Dhalsim)
  avatar_blanka    (Blanka)
  avatar_honda     (E.Honda)
  avatar_bison     (M.Bison)
  avatar_akuma     (Akuma)
  avatar_cammy     (Cammy)
  avatar_vega      (Vega)

작동 방식
---------
1. AvatarTile 컴포저블이 런타임에 resources.getIdentifier("avatar_<id>", "drawable", ...)로
   조회합니다.
2. 파일이 있으면 PNG 표시, 없으면 캐릭터 컬러 + 이니셜 플레이스홀더 표시.
3. 코드 수정 없이 PNG만 드롭하면 자동으로 인식됩니다.

권장 스펙
---------
- 정사각 (1:1), 최소 256×256, 권장 512×512 PNG
- 배경 투명 또는 단색 (원형으로 클립되므로 외곽은 안 보임)

⚠️ 저작권 주의
--------------
스트리트 파이터 캐릭터 이미지는 캡콤(CAPCOM)의 저작물입니다.
Play 스토어에 배포하는 빌드에는 절대 포함하지 마세요. IP 침해로
앱이 거부 또는 제거됩니다. 개인/내부 빌드(.aab 비공개 트랙)에서만
참고용으로 사용하세요.

배포용으로는:
- 캡콤 공식 라이선스 취득
- 또는 자체 일러스트 의뢰
- 또는 유저 업로드 사진 + 이니셜 플레이스홀더만 유지
중 하나의 길을 선택해야 합니다.
