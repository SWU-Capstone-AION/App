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
    const val KIDS_HOME = "kids_home"    // 아동용 홈 (+ 진정 팝업 / 호흡)

    // 아동용 마이페이지 (시안 1p ~ 5p)
    const val KIDS_MYPAGE       = "kids_mypage"
    const val KIDS_MY_INFO      = "kids_my_info"
    const val KIDS_MY_INFO_EDIT = "kids_my_info_edit"

    // 아동용 비밀번호 변경 (시안 4p ~ 15p)
    // 교사용 PASSWORD_CHANGE_CHECK / PASSWORD_CHANGE 는 폰 세로 기준이라 따로 둔다.
    const val KIDS_PASSWORD_CHANGE_CHECK = "kids_password_change_check"
    const val KIDS_PASSWORD_CHANGE       = "kids_password_change"

    const val CHILD_LINK = "child_link"
    const val CHILD_LIST = "child_list"

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

    // 미니게임
    const val WEED_GAME = "weed_game"
    const val BOARD_GAME = "board_game"

    // ===== 상동행동 감지를 계속 돌릴 화면 =====
    // 여기 있는 라우트에 머무는 동안에는 화면이 바뀌어도 카메라를 놓지 않는다.
    // 목록 밖으로 나가면 즉시 카메라를 놓고 판정도 초기화한다.
    //
    // ⚠ MONITOR / WEED_GAME / BOARD_GAME 은 일부러 뺐다.
    //   카메라는 한 번에 한 곳만 쓸 수 있는데 그 화면들이 직접 카메라를 잡기 때문이다.
    // ⚠ 교사용 화면과 로그인 전 화면도 뺐다. 아동이 사용 중일 때만 감지한다.
    //
    // 아동용 화면을 새로 추가하면 여기에도 같이 넣어야 감지가 이어진다.
    val KIDS_DETECTION_ROUTES = setOf(
        KIDS_HOME,
        KIDS_MYPAGE,
        KIDS_MY_INFO,
        KIDS_MY_INFO_EDIT,
        KIDS_PASSWORD_CHANGE_CHECK,
        KIDS_PASSWORD_CHANGE,
    )
}