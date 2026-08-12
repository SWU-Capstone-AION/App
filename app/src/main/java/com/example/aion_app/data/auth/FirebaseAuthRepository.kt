package com.example.aion_app.data.auth

import com.example.aion_app.ui.screen.login.ChildProfile
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseAuthInvalidCredentialsException
import com.google.firebase.auth.FirebaseAuthInvalidUserException
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.tasks.await

/**
 * 실제 Firebase에 연결되는 구현체.
 *
 * 저장 구조:
 *   users/{uid}        - 역할, 아이디, 아동 프로필
 *   loginIds/{loginId} - 아이디 중복 확인용 역인덱스
 */
class FirebaseAuthRepository(
    private val auth: FirebaseAuth = FirebaseAuth.getInstance(),
    private val db: FirebaseFirestore = FirebaseFirestore.getInstance(),
) : AuthRepository {

    override suspend fun isIdAvailable(loginId: String): Result<Boolean> = runCatching {
        val trimmed = loginId.trim()
        if (trimmed.isBlank()) return@runCatching false

        val doc = db.collection("loginIds").document(trimmed).get().await()
        !doc.exists()
    }

    override suspend fun register(
        signUpInput: SignUpInput,
        childProfile: ChildProfile
    ): Result<Unit> = runCatching {
        val loginId = signUpInput.userId.trim()

        if (loginId.isBlank()) throw IllegalArgumentException("아이디를 입력해 주세요.")
        if (!PASSWORD_PATTERN.matches(signUpInput.password)) {
            throw IllegalArgumentException("영문, 숫자 포함 8자 이상 입력해 주세요.")
        }

        // 1) 아이디 중복 확인 (화면에서 이미 했더라도 여기서 다시 검사)
        val existing = db.collection("loginIds").document(loginId).get().await()
        if (existing.exists()) throw IllegalStateException("이미 사용 중인 아이디입니다.")

        // 2) Auth 계정 생성
        val uid = auth
            .createUserWithEmailAndPassword(loginId.toAuthEmail(), signUpInput.password)
            .await()
            .user?.uid
            ?: throw IllegalStateException("계정 생성에 실패했습니다.")

        // 3) Firestore 기록 — 실패하면 방금 만든 Auth 계정을 되돌린다
        try {
            db.runBatch { batch ->
                batch.set(
                    db.collection("users").document(uid),
                    buildUserDocument(loginId, signUpInput, childProfile)
                )
                batch.set(
                    db.collection("loginIds").document(loginId),
                    mapOf("uid" to uid)
                )
            }.await()
        } catch (e: Exception) {
            auth.currentUser?.delete()?.await()
            throw e
        }

        Unit
    }

    override suspend fun login(loginId: String, password: String): Result<UserRole> = runCatching {
        val trimmed = loginId.trim()

        if (trimmed.isBlank()) throw IllegalArgumentException("아이디를 입력해 주세요.")
        if (password.isBlank()) throw IllegalArgumentException("비밀번호를 입력해 주세요.")

        // 아이디가 틀린 경우와 비밀번호가 틀린 경우를 구분해서 알려주지 않는다.
        // (존재하는 아이디를 추측당하지 않도록)
        val uid = try {
            auth.signInWithEmailAndPassword(trimmed.toAuthEmail(), password)
                .await()
                .user?.uid
        } catch (e: FirebaseAuthInvalidUserException) {
            throw IllegalStateException(LOGIN_FAILED_MESSAGE)
        } catch (e: FirebaseAuthInvalidCredentialsException) {
            throw IllegalStateException(LOGIN_FAILED_MESSAGE)
        } ?: throw IllegalStateException(LOGIN_FAILED_MESSAGE)

        // Auth 인증은 됐지만 프로필 문서가 없으면 정상 계정이 아니므로 되돌린다
        val document = db.collection("users").document(uid).get().await()
        when (document.getString("role")) {
            ROLE_CHILD -> UserRole.CHILD
            ROLE_TEACHER -> UserRole.TEACHER
            else -> {
                auth.signOut()
                throw IllegalStateException("계정 정보를 찾을 수 없습니다.")
            }
        }
    }

    override fun logout() {
        auth.signOut()
    }

    private fun buildUserDocument(
        loginId: String,
        signUpInput: SignUpInput,
        childProfile: ChildProfile
    ): Map<String, Any?> {
        val role = signUpInput.type.toRole()

        val document = mutableMapOf<String, Any?>(
            "role" to role,
            "loginId" to loginId,
            "createdAt" to FieldValue.serverTimestamp(),
        )

        if (role == ROLE_CHILD) {
            // 담당 교사는 가입 시점에 알 수 없다.
            // 교사가 '아동 등록' 화면에서 나중에 연결한다.
            document["teacherId"] = null

            document["name"] = childProfile.name
            document["gender"] = childProfile.gender
            document["birthYear"] = childProfile.birthYear
            document["birthMonth"] = childProfile.birthMonth
            document["birthDay"] = childProfile.birthDay
            // Firestore는 Set을 저장하지 못하므로 List로 변환한다
            document["sensoryTraits"] = childProfile.sensoryTraits.toList()
            document["behaviors"] = childProfile.behaviors.toList()
        }

        return document
    }

    /**
     * Firebase Auth는 이메일 기반이라 아이디를 내부용 주소로 변환한다.
     *
     * TODO: 회원가입 화면에 실제 이메일 입력이 추가되면 그 값으로 교체할 것.
     *       비밀번호 재설정 메일은 실존하는 주소로만 보낼 수 있어서,
     *       이 상태로는 '비밀번호 찾기' 기능을 만들 수 없다.
     */
    private fun String.toAuthEmail(): String = "${this.lowercase()}@aion.local"

    private fun String.toRole(): String =
        if (this.trim() == "아동용") ROLE_CHILD else ROLE_TEACHER

    companion object {
        const val ROLE_TEACHER = "TEACHER"
        const val ROLE_CHILD = "CHILD"

        private const val LOGIN_FAILED_MESSAGE = "아이디 또는 비밀번호가 올바르지 않습니다."

        // 화면 안내 문구("영문, 숫자 포함 8자 이상")와 동일한 규칙
        private val PASSWORD_PATTERN = Regex("^(?=.*[A-Za-z])(?=.*\\d).{8,}$")
    }
}