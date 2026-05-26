package com.example.aion_app.navigation

import androidx.compose.runtime.Composable
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController

import com.example.aion_app.ui.screen.mypage.MyInfoScreen
import com.example.aion_app.ui.screen.mypage.MyInfoEditScreen
import com.example.aion_app.ui.screen.mypage.MyPageScreen

import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.aion_app.ui.screen.mypage.MyInfoViewModel
import androidx.compose.runtime.remember

import com.example.aion_app.ui.screen.password.PasswordChangeCheckScreen
import com.example.aion_app.ui.screen.password.PasswordChangeScreen
import com.example.aion_app.ui.screen.password.PasswordFindResultScreen
import com.example.aion_app.ui.screen.password.PasswordFindScreen

import com.example.aion_app.ui.screen.mypage.calculateAge

@Composable
fun AionNavHost() {
    val navController = rememberNavController()

    NavHost(
        navController = navController,
        //startDestination = Route.PASSWORD_FIND
        startDestination = Route.MYPAGE //임시 수정
    ) {
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
                }
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