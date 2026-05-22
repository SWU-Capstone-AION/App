package com.example.aion_app.navigation

import androidx.compose.runtime.Composable
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.example.aion_app.ui.screen.password.PasswordChangeCheckScreen
import com.example.aion_app.ui.screen.password.PasswordChangeScreen
import com.example.aion_app.ui.screen.password.PasswordFindResultScreen
import com.example.aion_app.ui.screen.password.PasswordFindScreen

@Composable
fun AionNavHost() {
    val navController = rememberNavController()

    NavHost(
        navController = navController,
        startDestination = Route.PASSWORD_FIND
    ) {
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