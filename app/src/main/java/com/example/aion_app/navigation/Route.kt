package com.example.aion_app.navigation

object Route {
    const val SPLASH = "splash"
    // 회원가입 플로우
    const val SIGN_UP = "sign_up"
    const val CHILD_PROFILE_SETUP = "child_profile_setup"
    const val ONBOARDING_COMPLETE = "onboarding_complete"

    const val PASSWORD_FIND = "password_find"
    const val PASSWORD_FIND_RESULT = "password_find_result"
    const val PASSWORD_CHANGE_CHECK = "password_change_check"
    const val PASSWORD_CHANGE = "password_change"

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
}