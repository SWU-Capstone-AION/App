package com.example.aion_app.navigation

import com.example.aion_app.ui.screen.notification.NotificationScreen

import androidx.compose.runtime.Composable
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController

import com.example.aion_app.ui.screen.home.HomeScreen
import com.example.aion_app.ui.screen.mypage.MyInfoScreen
import com.example.aion_app.ui.screen.mypage.MyInfoEditScreen
import com.example.aion_app.ui.screen.mypage.MyPageScreen

import com.example.aion_app.ui.screen.login.SignUpScreen
import com.example.aion_app.ui.screen.login.SignUpViewModel
import com.example.aion_app.ui.screen.login.ChildProfileSetupScreen
import com.example.aion_app.ui.screen.login.OnboardingCompleteScreen

import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.aion_app.ui.screen.mypage.MyInfoViewModel
import androidx.compose.runtime.remember

import com.example.aion_app.ui.screen.password.PasswordChangeCheckScreen
import com.example.aion_app.ui.screen.password.PasswordChangeScreen
import com.example.aion_app.ui.screen.password.PasswordFindResultScreen
import com.example.aion_app.ui.screen.password.PasswordFindScreen

import com.example.aion_app.ui.screen.mypage.calculateAge
import com.example.aion_app.ui.screen.report.ReportListScreen
import com.example.aion_app.ui.screen.report.ReportDetailScreen
import com.example.aion_app.ui.screen.report.sampleStudentReport

@Composable
fun AionNavHost() {
    val navController = rememberNavController()

    NavHost(
        navController = navController,
        startDestination = Route.HOME  // ← 홈으로 임시 변경 (테스트용)
        //startDestination = Route.PASSWORD_FIND
        //startDestination = Route.SIGN_UP // 회원가입 플로우부터 시작 (팀원 작업 화면 테스트용)
        // startDestination = Route.HOME // 홈 테스트용 (임시)
        // startDestination = Route.MYPAGE // 마이페이지 테스트용 (임시)
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

        // ===== 회원가입 플로우 =====
        // 세 화면이 같은 SignUpViewModel을 공유하도록 SIGN_UP 진입점을 parentEntry로 묶음
        // (MyInfoViewModel을 MYPAGE 그래프에서 공유하는 방식과 동일한 패턴)
        composable(Route.SIGN_UP) {
            val parentEntry = remember(it) {
                navController.getBackStackEntry(Route.SIGN_UP)
            }
            val signUpViewModel: SignUpViewModel = viewModel(parentEntry)
            SignUpScreen(
                onLoginClick = {
                    // TODO: 로그인(이메일/비번 입력) 화면이 아직 없음 — 별도 화면 만들면 여기 연결
                },
                onSignUpClick = { type, email, password ->
                    signUpViewModel.updateSignUpInput(type, email, password)
                    navController.navigate(Route.CHILD_PROFILE_SETUP)
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
                            navController.navigate(Route.MYPAGE) {
                                // 여기서야 회원가입 플로우 전체를 스택/ViewModel에서 정리
                                popUpTo(Route.SIGN_UP) { inclusive = true }
                            }
                        } else {
                            // TODO: 실패 시 에러 메시지 노출 (signUpViewModel.submitError 사용)
                        }
                    }
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
                onBackClick = { navController.popBackStack() }
            )
        }

        // ===== 마이페이지 =====
        composable(Route.MYPAGE) {
            val parentEntry = remember(it) {
                navController.getBackStackEntry(Route.MYPAGE)
            }
            val viewModel: MyInfoViewModel = viewModel(parentEntry)

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
                    navController.navigate(Route.PASSWORD_FIND)
                },
                onChangePasswordClick = {
                    navController.navigate(Route.PASSWORD_CHANGE_CHECK)
                },
                onLogoutClick = {
                    // TODO: 로그아웃 처리
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
            PasswordFindScreen(
                onBackClick = { navController.popBackStack() },
                onFindSuccess = {
                    navController.navigate(Route.PASSWORD_FIND_RESULT)
                }
            )
        }

        // 2. 비밀번호 찾기 결과 화면
        composable(Route.PASSWORD_FIND_RESULT) {
            PasswordFindResultScreen(
                onBackClick = { navController.popBackStack() },
                onChangePasswordClick = {
                    navController.navigate(Route.PASSWORD_CHANGE_CHECK)
                },
                onLoginClick = {
                    // 처음 화면(비밀번호 찾기)으로 돌아가기
                    navController.popBackStack(
                        route = Route.PASSWORD_FIND,
                        inclusive = false
                    )
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