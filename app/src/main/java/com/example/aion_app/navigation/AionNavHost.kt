package com.example.aion_app.navigation

import com.example.aion_app.isTabletDevice
import com.example.aion_app.ui.screen.notification.NotificationScreen
import com.example.aion_app.ui.screen.splash.SplashScreen

import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.NavType
import androidx.navigation.navArgument

import com.example.aion_app.ui.screen.home.HomeScreen
import com.example.aion_app.ui.screen.mypage.MyInfoScreen
import com.example.aion_app.ui.screen.mypage.MyInfoEditScreen
import com.example.aion_app.ui.screen.mypage.MyPageScreen

import com.example.aion_app.ui.screen.login.SignUpAccountScreen
import com.example.aion_app.ui.screen.login.SignUpScreen
import com.example.aion_app.ui.screen.login.SignUpViewModel
import com.example.aion_app.ui.screen.login.ChildProfileSetupScreen
import com.example.aion_app.ui.screen.login.OnboardingCompleteScreen

import com.example.aion_app.ui.screen.kids.KidsLoginScreen
import com.example.aion_app.ui.screen.kids.KidsSignUpAccountScreen
import com.example.aion_app.ui.screen.kids.KidsProfileSetupScreen
import com.example.aion_app.ui.screen.kids.KidsOnboardingCompleteScreen
import com.example.aion_app.ui.screen.kids.KidsHomeScreen

import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.aion_app.ui.screen.mypage.MyInfoViewModel
import androidx.compose.runtime.remember

import com.example.aion_app.ui.screen.password.PasswordChangeCheckScreen
import com.example.aion_app.ui.screen.password.PasswordChangeScreen
import com.example.aion_app.ui.screen.password.PasswordFindResultScreen
import com.example.aion_app.ui.screen.password.PasswordFindScreen
import com.example.aion_app.ui.screen.password.IdFindScreen
import com.example.aion_app.ui.screen.password.IdFindResultScreen
import com.example.aion_app.ui.screen.password.PasswordFindViewModel
import android.net.Uri
import com.example.aion_app.ui.screen.password.IdFindViewModel

import com.example.aion_app.ui.screen.mypage.calculateAge
import com.example.aion_app.ui.screen.report.ReportListScreen
import com.example.aion_app.ui.screen.report.ReportDetailScreen
import com.example.aion_app.ui.screen.report.sampleStudentReport

import com.example.aion_app.data.auth.UserRole
import com.example.aion_app.ui.screen.login.LoginViewModel

@Composable
fun AionNavHost() {
    val navController = rememberNavController()

    NavHost(
        navController = navController,
        // 앱 진입: 스플래시부터 시작
        startDestination = Route.SPLASH
        // startDestination = Route.SIGN_UP   // 회원가입 플로우 테스트용
        // startDestination = Route.HOME       // 홈 테스트용
        // startDestination = Route.MYPAGE     // 마이페이지 테스트용
        // startDestination = Route.KIDS_LOGIN  // 아동용 화면을 폰/프리뷰에서 강제로 볼 때
        // startDestination = Route.ID_FIND    // 아이디 찾기 테스트용
    ) {
        // ===== 하단탭 공용 이동 로직 =====
        // (지역변수라 선언보다 아래에서만 참조 가능 → NavHost 블록 맨 위에 둠)
        val onTabSelect: (String) -> Unit = { tab ->
            val route = when (tab) {
                "home" -> Route.HOME
                "report" -> Route.REPORT
                "mypage" -> Route.MYPAGE
                else -> Route.HOME
            }
            navController.navigate(route) {
                popUpTo(Route.HOME) { saveState = true }
                launchSingleTop = true
                restoreState = true
            }
        }

        // ===== 스플래시 =====
        // 기기 종류로 진입 화면을 나눈다.
        //   태블릿(sw600dp 이상) → 아동용 로그인
        //   폰                   → 교사용 로그인
        //
        // 판별은 MainActivity 의 화면 회전 결정과 반드시 같은 기준이어야 하므로
        // 동일한 isTabletDevice() 를 함께 쓴다. (기준이 갈리면 '폰인데 가로' 같은 버그가 난다)
        composable(Route.SPLASH) {
            val isTablet = isTabletDevice(LocalContext.current)

            SplashScreen(
                onFinish = {
                    val next = if (isTablet) Route.KIDS_LOGIN else Route.SIGN_UP
                    navController.navigate(next) {
                        popUpTo(Route.SPLASH) { inclusive = true }  // 스플래시는 스택에서 제거
                    }
                }
            )
        }

        // ===== 회원가입 플로우 =====
        // 세 화면이 같은 SignUpViewModel을 공유하도록 SIGN_UP 진입점을 parentEntry로 묶음
        // (MyInfoViewModel을 MYPAGE 그래프에서 공유하는 방식과 동일한 패턴)
        composable(Route.SIGN_UP) {
            val parentEntry = remember(it) {
                navController.getBackStackEntry(Route.SIGN_UP)
            }
            val signUpViewModel: SignUpViewModel = viewModel(parentEntry)
            val loginViewModel: LoginViewModel = viewModel()

            SignUpScreen(
                isLoading = loginViewModel.isLoading,
                errorMessage = loginViewModel.errorMessage,
                onLoginClick = { userId, password ->
                    loginViewModel.login(userId, password) { role ->
                        // 토글이 아니라 서버에 저장된 역할을 따라 분기한다
                        val next = if (role == UserRole.CHILD) Route.KIDS_HOME else Route.HOME
                        navController.navigate(next) {
                            popUpTo(Route.SIGN_UP) { inclusive = true }
                        }
                    }
                },
                onSignUpClick = { type ->
                    signUpViewModel.updateType(type)
                    // 토글에서 '아동용'을 고르면 아동용 회원가입 플로우로 분기
                    if (type == "아동용") {
                        navController.navigate(Route.KIDS_SIGN_UP_ACCOUNT)
                    } else {
                        navController.navigate(Route.SIGN_UP_ACCOUNT)
                    }
                },
                onFindIdClick = { navController.navigate(Route.ID_FIND) },
                onFindPasswordClick = { navController.navigate(Route.PASSWORD_FIND) }
            )
        }

        // ===== 회원가입: 아이디 + 비밀번호 (시안 2p) =====
        composable(Route.SIGN_UP_ACCOUNT) {
            val parentEntry = remember(it) {
                navController.getBackStackEntry(Route.SIGN_UP)
            }
            val signUpViewModel: SignUpViewModel = viewModel(parentEntry)
            SignUpAccountScreen(
                onBackClick = { navController.popBackStack() },
                onCheckDuplicate = { signUpViewModel.checkDuplicateId(it) },
                duplicateMessage = signUpViewModel.idCheckMessage,
                onNext = { userId, email, password ->
                    signUpViewModel.updateAccount(userId, password, email)
                    navController.navigate(Route.CHILD_PROFILE_SETUP)
                }
            )
        }
        // ===== 아이디 찾기 =====
        composable(Route.ID_FIND) {
            val idFindViewModel: IdFindViewModel = viewModel()

            IdFindScreen(
                isLoading = idFindViewModel.isLoading,
                errorMessage = idFindViewModel.errorMessage,
                onBackClick = { navController.popBackStack() },
                onFindSuccess = { name, email ->
                    idFindViewModel.findId(name, email) { loginId ->
                        // 이름에 한글·공백이 들어갈 수 있어 인코딩해서 넘긴다
                        navController.navigate(
                            "${Route.ID_FIND_RESULT}/${Uri.encode(loginId)}/${Uri.encode(name)}"
                        )
                    }
                },
                onSwitchToPasswordFind = {
                    navController.navigate(Route.PASSWORD_FIND) {
                        popUpTo(Route.ID_FIND) { inclusive = true }
                    }
                }
            )
        }

        composable(
            route = "${Route.ID_FIND_RESULT}/{userId}/{name}",
            arguments = listOf(
                navArgument("userId") { type = NavType.StringType },
                navArgument("name") { type = NavType.StringType }
            )
        ) { backStackEntry ->
            val userId = backStackEntry.arguments?.getString("userId") ?: ""
            val name = backStackEntry.arguments?.getString("name") ?: ""
            IdFindResultScreen(
                nickname = name,
                userId = userId,
                onBackClick = { navController.popBackStack() },
                onPasswordFindClick = {
                    navController.navigate(Route.PASSWORD_FIND) {
                        popUpTo(Route.ID_FIND) { inclusive = true }
                    }
                },
                onLoginClick = {
                    // 로그인 화면으로 (찾기 흐름은 스택에서 정리)
                    navController.navigate(Route.SIGN_UP) {
                        popUpTo(0) { inclusive = true }
                    }
                }
            )
        }
        composable(Route.CHILD_PROFILE_SETUP) {
            val parentEntry = remember(it) {
                navController.getBackStackEntry(Route.SIGN_UP)
            }
            val signUpViewModel: SignUpViewModel = viewModel(parentEntry)
            ChildProfileSetupScreen(
                onBackClick = { navController.popBackStack() },
                onComplete = { profile ->
                    signUpViewModel.updateChildProfile(profile)
                    // 아직 SIGN_UP을 스택에서 안 지움 — ViewModel을 OnboardingComplete에서도 써야 해서
                    navController.navigate(Route.ONBOARDING_COMPLETE)
                }
            )
        }
        composable(Route.ONBOARDING_COMPLETE) {
            val parentEntry = remember(it) {
                navController.getBackStackEntry(Route.SIGN_UP)
            }
            val signUpViewModel: SignUpViewModel = viewModel(parentEntry)
            OnboardingCompleteScreen(
                isSubmitting = signUpViewModel.isSubmitting,
                onConfirmClick = {
                    // ===== 백엔드 연결 지점 =====
                    // submit() 내부에서 AuthRepository.register()를 호출함.
                    // 지금은 FakeAuthRepository라 거의 항상 성공으로 옴.
                    signUpViewModel.submit { success ->
                        if (success) {
                            // 회원가입 완료 → 홈으로 (회원가입 플로우 전체를 스택/ViewModel에서 정리)
                            navController.navigate(Route.HOME) {
                                popUpTo(Route.SIGN_UP) { inclusive = true }
                            }
                        } else {
                            // TODO: 실패 시 에러 메시지 노출 (signUpViewModel.submitError 사용)
                        }
                    }
                }
            )
        }

        // ===== 아동용 로그인 / 회원가입 =====
        // 아동용 3개 화면(2p~6p)이 SignUpViewModel 하나를 공유하도록
        // KIDS_SIGN_UP_ACCOUNT 진입점을 parentEntry 로 묶는다.
        // (교사용 로그인에서 '아동용'을 골라 들어와도, 아동용 로그인에서 들어와도 동일하게 동작)
        composable(Route.KIDS_LOGIN) {
            val loginViewModel: LoginViewModel = viewModel()

            KidsLoginScreen(
                onTeacherClick = {
                    // 토글에서 '교사용' → 교사용 로그인 화면으로
                    navController.navigate(Route.SIGN_UP) {
                        popUpTo(Route.KIDS_LOGIN) { inclusive = true }
                    }
                },
                onLoginClick = { userId, password ->
                    loginViewModel.login(userId, password) { role ->
                        val next = if (role == UserRole.CHILD) Route.KIDS_HOME else Route.HOME
                        navController.navigate(next) {
                            popUpTo(Route.KIDS_LOGIN) { inclusive = true }
                        }
                    }
                },
                onSignUpClick = {
                    navController.navigate(Route.KIDS_SIGN_UP_ACCOUNT)
                }
            )
        }

        composable(Route.KIDS_SIGN_UP_ACCOUNT) {
            val parentEntry = remember(it) {
                navController.getBackStackEntry(Route.KIDS_SIGN_UP_ACCOUNT)
            }
            val signUpViewModel: SignUpViewModel = viewModel(parentEntry)
            KidsSignUpAccountScreen(
                onBackClick = { navController.popBackStack() },
                onCheckDuplicate = { _ ->
                    // TODO: 백엔드 아이디 중복확인 API 연결
                },
                onNext = { userId, password ->
                    signUpViewModel.updateType("아동용")
                    signUpViewModel.updateAccount(userId, password)
                    navController.navigate(Route.KIDS_PROFILE_SETUP)
                }
            )
        }

        composable(Route.KIDS_PROFILE_SETUP) {
            val parentEntry = remember(it) {
                navController.getBackStackEntry(Route.KIDS_SIGN_UP_ACCOUNT)
            }
            val signUpViewModel: SignUpViewModel = viewModel(parentEntry)
            KidsProfileSetupScreen(
                onBackClick = { navController.popBackStack() },
                onComplete = { profile ->
                    signUpViewModel.updateChildProfile(profile)
                    navController.navigate(Route.KIDS_ONBOARDING_COMPLETE)
                }
            )
        }

        composable(Route.KIDS_ONBOARDING_COMPLETE) {
            val parentEntry = remember(it) {
                navController.getBackStackEntry(Route.KIDS_SIGN_UP_ACCOUNT)
            }
            val signUpViewModel: SignUpViewModel = viewModel(parentEntry)
            KidsOnboardingCompleteScreen(
                isSubmitting = signUpViewModel.isSubmitting,
                onNextClick = {
                    // 교사용과 같은 AuthRepository.register() 를 그대로 탄다.
                    signUpViewModel.submit { success ->
                        if (success) {
                            navController.navigate(Route.KIDS_HOME) {
                                popUpTo(Route.KIDS_SIGN_UP_ACCOUNT) { inclusive = true }
                            }
                        } else {
                            // TODO: 실패 시 에러 메시지 노출 (signUpViewModel.submitError 사용)
                        }
                    }
                }
            )
        }

        // ===== 아동용 홈 =====
        composable(Route.KIDS_HOME) {
            // ★ 상동행동 감지 연결 지점
            // feature/stereotypy-monitor 가 develop 에 들어오면
            // StereotypyMonitorScreen 쪽에서 얻는 StereotypyDetector.State.anyAlarm 을
            // stereotypyDetected 로 넘기면 된다. 지금은 카메라 없이 화면만 확인하는 단계라 false.
            KidsHomeScreen(
                stereotypyDetected = false,
                points = 20,   // TODO: 실제 포인트 연결
                onProfileClick = {
                    // TODO: 아동용 마이페이지 (시안 나오면 연결)
                },
                onHelpRequest = {
                    // TODO: 교사에게 도움 요청 알림 전송
                },
                onBreathingComplete = {
                    // TODO: 호흡 완료 보상(젤리) 지급
                }
            )
        }

        // ===== 홈 =====
        composable(Route.HOME) {
            HomeScreen(
                onNotificationClick = {
                    navController.navigate(Route.NOTIFICATION)
                },
                onAlertClick = {
                    // TODO: 알림 상세로 이동
                },
                onStudentClick = { student ->
                    // TODO: 학생 상세로 이동
                },
                onTabSelect = onTabSelect
            )
        }

        // ===== 리포트 =====
        composable(Route.REPORT) {
            ReportListScreen(
                onStudentClick = { student ->
                    navController.navigate("${Route.REPORT_DETAIL}/${student.id}")
                },
                onTabSelect = onTabSelect
            )
        }
        composable("${Route.REPORT_DETAIL}/{studentId}") { entry ->
            val studentId = entry.arguments?.getString("studentId") ?: "2"
            ReportDetailScreen(
                report = sampleStudentReport(studentId),
                onBackClick = { navController.popBackStack() },
                onTabSelect = onTabSelect
            )
        }

        // ===== 알림센터 =====
        composable(Route.NOTIFICATION) {
            NotificationScreen(
                onBackClick = { navController.popBackStack() },
                onTabSelect = onTabSelect      // ← 이게 없어서 하단탭이 안 먹혔음
            )
        }

        // ===== 마이페이지 =====
        composable(Route.MYPAGE) {
            val parentEntry = remember(it) {
                navController.getBackStackEntry(Route.MYPAGE)
            }
            val viewModel: MyInfoViewModel = viewModel(parentEntry)
            val loginViewModel: LoginViewModel = viewModel()

            val info = viewModel.myInfo
            MyPageScreen(
                userName = info.name,
                userGender = info.gender.first().toString(),  // "남자" → "남"
                userAge = calculateAge(info.birthDate),       // "2019.12.21" → 6 (또는 나이)
                profileImageUri = info.profileImageUri,
                onEditProfileClick = {
                    navController.navigate(Route.MY_INFO)
                },
                onFindIdPasswordClick = {
                    navController.navigate(Route.ID_FIND)
                },
                onChangePasswordClick = {
                    navController.navigate(Route.PASSWORD_CHANGE_CHECK)
                },
                onLogoutClick = {
                    loginViewModel.logout()
                    // 백스택을 통째로 비운다.
                    // 스플래시는 이미 스택에서 지워졌으므로 popUpTo(0)으로 루트까지 제거.
                    navController.navigate(Route.SIGN_UP) {
                        popUpTo(0) { inclusive = true }
                    }
                },
                onTabSelect = onTabSelect
            )
        }

        composable(Route.MY_INFO) {
            // ViewModel을 NavBackStackEntry에 연결 → 같은 그래프 안에서 공유됨
            val parentEntry = remember(it) {
                navController.getBackStackEntry(Route.MYPAGE)
            }
            val viewModel: MyInfoViewModel = viewModel(parentEntry)

            val info = viewModel.myInfo
            MyInfoScreen(
                userName = info.name,
                userGender = info.gender,
                userBirthDate = info.birthDate,
                sensitiveStimuli = info.sensitiveStimuli,
                behaviorTraits = info.behaviorTraits,
                profileImageUri = info.profileImageUri,
                onBackClick = { navController.popBackStack() },
                onEditClick = {
                    navController.navigate(Route.MY_INFO_EDIT)
                }
            )
        }

        composable(Route.MY_INFO_EDIT) {
            val parentEntry = remember(it) {
                navController.getBackStackEntry(Route.MYPAGE)
            }
            val viewModel: MyInfoViewModel = viewModel(parentEntry)

            val info = viewModel.myInfo
            MyInfoEditScreen(
                initialName = info.name,
                initialGender = info.gender,
                initialBirthDate = info.birthDate,
                initialSensitiveStimuli = info.sensitiveStimuli,
                initialBehaviorTraits = info.behaviorTraits,
                initialProfileImageUri = info.profileImageUri,
                onBackClick = { navController.popBackStack() },
                onSaveClick = { newInfo ->
                    viewModel.updateMyInfo(newInfo)
                    navController.popBackStack()
                }
            )
        }

        // 1. 비밀번호 찾기 화면
        composable(Route.PASSWORD_FIND) {
            val passwordFindViewModel: PasswordFindViewModel = viewModel()

            PasswordFindScreen(
                isLoading = passwordFindViewModel.isLoading,
                errorMessage = passwordFindViewModel.errorMessage,
                onBackClick = { navController.popBackStack() },
                onFindSuccess = { id ->
                    passwordFindViewModel.sendResetMail(id) {
                        navController.navigate(Route.PASSWORD_FIND_RESULT)
                    }
                },
                onSwitchToIdFind = {
                    navController.navigate(Route.ID_FIND) {
                        popUpTo(Route.PASSWORD_FIND) { inclusive = true }
                    }
                }
            )
        }

        // 2. 비밀번호 찾기 결과 화면 (재설정 메일 발송 안내)
        composable(Route.PASSWORD_FIND_RESULT) {
            PasswordFindResultScreen(
                onBackClick = { navController.popBackStack() },
                onLoginClick = {
                    // 로그인 화면으로 (찾기 흐름은 스택에서 정리)
                    navController.navigate(Route.SIGN_UP) {
                        popUpTo(0) { inclusive = true }
                    }
                }
            )
        }

        // 3. 비밀번호 변경 전 확인 화면
        composable(Route.PASSWORD_CHANGE_CHECK) {
            PasswordChangeCheckScreen(
                onBackClick = { navController.popBackStack() },
                onNextClick = { _, _ ->
                    navController.navigate(Route.PASSWORD_CHANGE)
                }
            )
        }

        // 4. 비밀번호 변경 화면
        composable(Route.PASSWORD_CHANGE) {
            PasswordChangeScreen(
                onBackClick = { navController.popBackStack() },
                onChangeSuccess = {
                    // 변경 완료 후 처음 화면(비밀번호 찾기)으로 가기
                    // 중간 화면(결과, 변경 전 확인)도 모두 스택에서 제거
                    navController.navigate(Route.PASSWORD_FIND) {
                        popUpTo(Route.PASSWORD_FIND) {
                            inclusive = true
                        }
                    }
                }
            )
        }
    }
}