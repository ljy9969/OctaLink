OctaLink — Avatar 이미지 드롭존
=================================

이 폴더는 카탈로그 등록용 참고용입니다.
실제 런타임에서 코드가 찾는 위치는 다음입니다:

   app/src/main/res/drawable-nodpi/<id>.png

캐릭터 ID = {gender}_{weight} 조합 10개:
  m_feather  m_light  m_welter  m_middle  m_heavy   (남자 5체급)
  f_feather  f_light  f_welter  f_middle  f_heavy   (여자 5체급)

작동 방식
---------
1. AvatarTile 컴포저블이 런타임에 resources.getIdentifier("{id}", "drawable", ...)로
   조회합니다.
2. 파일이 있으면 PNG 표시, 없으면 캐릭터 컬러 + 이니셜("남"/"여") 플레이스홀더 표시.
3. 캐릭터 본체 PNG 하나만 사용 — 벨트 색은 카드 좌측 스트라이프 + 벨트 칩 + 텍스트 라벨로
   분리 표시. 캐릭터 이미지에 동적 색 변형 없음.

권장 스펙
---------
- 정사각 (1:1), 최소 256×256, 권장 512×512 PNG
- 배경 투명 또는 단색 (원형으로 클립되므로 외곽은 안 보임)
- 캐릭터는 TopCenter 정렬 후 fillMaxSize Crop 되므로 상반신 중앙 정렬 권장

스프라이트 시트에서 일괄 추출
-------------------------------
sheet.png(5×2 그리드, 위→남 / 아래→여, 좌→우 페더→헤비)을
tools/split_sprites.py 로 10개 PNG 로 분리:

   python tools/split_sprites.py path/to/sheet.png
   # → out/{m,f}_{feather,light,welter,middle,heavy}.png

⚠️ 저작권 주의
--------------
Play 스토어 배포 빌드에 타사 캐릭터 IP 이미지를 포함하지 마세요. 자체 일러스트
또는 라이선스 확보된 자산만 main 브랜치에 둡니다.
