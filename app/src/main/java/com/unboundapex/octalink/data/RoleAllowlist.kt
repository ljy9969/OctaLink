package com.unboundapex.octalink.data

import com.unboundapex.octalink.data.schema.Role

/**
 * 관장(MASTER) / 코치(COACH) 사전 등록 명단.
 *
 * 카카오 OAuth 회원 가입 시 이 명단에 있는 이름은 PENDING 단계 건너뛰고 즉시 APPROVED.
 * 명단에 없으면 MEMBER + PENDING 상태로 등록되어 관장 승인 대기.
 *
 * **보안 원칙: 사용자가 직접 자기 역할을 선택할 수 없음.** 이 명단은 앱 제작자/운영자가
 * 코드에서 관리하며 변경 시 새 빌드/배포 필요. 추후 Firestore의 보안 규칙 + Cloud
 * Functions 로 이전해서 런타임 업데이트 가능하게 확장 가능.
 *
 * 매칭 기준: 카카오 표시 이름. 동명이인 위험은 MVP에서 무시 (소규모 체육관 한정),
 * Firebase Auth 도입 시 카카오 unique user ID로 매칭하도록 강화 예정.
 */
object RoleAllowlist {

    /** 관장(MASTER) — 전권. 보통 1명, 운영자 본인 검증용 추가 가능 */
    val masters: Set<String> = setOf(
        "김파시",       // Team Posse Striking 강남점 관장
        "이지연",       // 앱 제작자 / 운영자 (BlackCat Strike) — 개발 + 운영 모드 검증용
    )

    /** 코치(COACH) — 부관리자. 일상 운영 권한 (코멘트 / 출결 검토 / 토너먼트 / 공지 / 스킬 점수 입력) */
    val coaches: Set<String> = setOf(
        // 운영자가 추가. 예: "박민수", "정승호" 등
    )

    /** 이름으로 역할 조회. 명단에 없으면 MEMBER 반환. */
    fun roleOf(name: String): Role = when {
        name in masters -> Role.MASTER
        name in coaches -> Role.COACH
        else -> Role.MEMBER
    }

    /** 가입 승인 단계를 건너뛸 수 있는 이름인지 (관장+코치). */
    fun skipApproval(name: String): Boolean = name in masters || name in coaches
}
