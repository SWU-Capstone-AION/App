package com.example.aion_app.navigation

object Route {
    const val SPLASH = "splash"

    // 회원가입 플로우 (교사용)
    const val SIGN_UP = "sign_up"
    const val SIGN_UP_ACCOUNT = "sign_up_account"   // 시안 2p: 아이디 + 비밀번호 입력
    const val CHILD_PROFILE_SETUP = "child_profile_setup"
    const val ONBOARDING_COMPLETE = "onboarding_complete"

    // 아동용 로그인 / 회원가입 플로우
    const val KIDS_LOGIN = "kids_login"                        // 아동용 시안 1p
    const val KIDS_SIGN_UP_ACCOUNT = "kids_sign_up_account"    // 아동용 시안 2p
    const val KIDS_PROFILE_SETUP = "kids_profile_setup"        // 아동용 시안 3p~5p
    const val KIDS_ONBOARDING_COMPLETE = "kids_onboarding_complete"  // 아동용 시안 6p
    const val KIDS_HOME = "kids_home"                          // 아동용 홈 (+ 진정 팝업 / 호흡)

    const val PASSWORD_FIND = "password_find"
    const val PASSWORD_FIND_RESULT = "password_find_result"
    const val PASSWORD_CHANGE_CHECK = "password_change_check"
    const val PASSWORD_CHANGE = "password_change"

    const val ID_FIND = "id_find"
    const val ID_FIND_RESULT = "id_find_result"

    // 마이페이지
    const val MYPAGE = "mypage"
    const val MY_INFO = "my_info"
    const val MY_INFO_EDIT = "my_info_edit"

    // 홈
    const val HOME = "home"
    const val NOTIFICATION = "notification"

    // 리포트
    const val REPORT = "report"
    const val REPORT_DETAIL = "report_detail"   // 사용: "report_detail/{studentId}"

    // 상동행동 모니터링
    const val MONITOR = "monitor"
}