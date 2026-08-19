package com.example.aion_app.data.auth

import android.util.Log
import com.example.aion_app.ui.screen.login.ChildProfile
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseAuthInvalidCredentialsException
import com.google.firebase.auth.FirebaseAuthInvalidUserException
import com.google.firebase.auth.FirebaseAuthUserCollisionException
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.messaging.FirebaseMessaging
import kotlinx.coroutines.tasks.await

/**
 * 실제 Firebase에 연결되는 구현체.
 *
 * 저장 구조:
 *   users/{uid}        - 역할, 아이디, 이메일, 이름, FCM 토큰, (아동이면) 아동 프로필
 *   loginIds/{loginId} - 아이디 중복 확인 + 로그인 시 이메일 조회용 역인덱스
 *
 * Firebase Auth는 이메일로 로그인하는데 화면에서는 아이디를 입력받으므로,
 * loginIds 문서에 이메일을 함께 저장해 두고 로그인할 때 조회해서 쓴다.
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
        val role = signUpInput.type.toRole()
        val inputEmail = signUpInput.email.trim()

        if (loginId.isBlank()) throw IllegalArgumentException("아이디를 입력해 주세요.")
        if (!PASSWORD_PATTERN.matches(signUpInput.password)) {
            throw IllegalArgumentException("영문, 숫자 포함 8자 이상 입력해 주세요.")
        }

        // 교사 계정은 비밀번호 재설정 메일을 받아야 하므로 실제 이메일이 필수.
        // 아동 계정은 이메일이 없으므로 내부용 가짜 주소를 만들어 쓴다.
        val authEmail = when {
            role == ROLE_CHILD && inputEmail.isBlank() -> loginId.toInternalEmail()
            inputEmail.isBlank() -> throw IllegalArgumentException("이메일을 입력해 주세요.")
            !EMAIL_PATTERN.matches(inputEmail) ->
                throw IllegalArgumentException("이메일 형식이 올바르지 않습니다.")
            else -> inputEmail.lowercase()
        }

        // 1) 아이디 중복 확인 (화면에서 이미 했더라도 여기서 다시 검사)
        val existing = db.collection("loginIds").document(loginId).get().await()
        if (existing.exists()) throw IllegalStateException("이미 사용 중인 아이디입니다.")

        // 2) Auth 계정 생성
        val uid = try {
            auth.createUserWithEmailAndPassword(authEmail, signUpInput.password)
                .await()
                .user?.uid
        } catch (e: FirebaseAuthUserCollisionException) {
            throw IllegalStateException("이미 가입된 이메일입니다.")
        } ?: throw IllegalStateException("계정 생성에 실패했습니다.")

        // 3) Firestore 기록 — 실패하면 방금 만든 Auth 계정을 되돌린다
        try {
            db.runBatch { batch ->
                batch.set(
                    db.collection("users").document(uid),
                    buildUserDocument(loginId, authEmail, role, childProfile)
                )
                batch.set(
                    db.collection("loginIds").document(loginId),
                    mapOf("uid" to uid, "email" to authEmail)
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

        // 아이디로 로그인용 이메일을 찾는다.
        // email 필드가 없는 계정은 이메일 도입 이전에 만들어진 것이므로 가짜 주소로 대체.
        val idDoc = db.collection("loginIds").document(trimmed).get().await()
        val email = idDoc.getString("email")
            ?: if (idDoc.exists()) trimmed.toInternalEmail()
            else throw IllegalStateException(LOGIN_FAILED_MESSAGE)

        // 아이디가 틀린 경우와 비밀번호가 틀린 경우를 구분해서 알려주지 않는다.
        // (존재하는 아이디를 추측당하지 않도록)
        val uid = try {
            auth.signInWithEmailAndPassword(email, password)
                .await()
                .user?.uid
        } catch (e: FirebaseAuthInvalidUserException) {
            throw IllegalStateException(LOGIN_FAILED_MESSAGE)
        } catch (e: FirebaseAuthInvalidCredentialsException) {
            throw IllegalStateException(LOGIN_FAILED_MESSAGE)
        } ?: throw IllegalStateException(LOGIN_FAILED_MESSAGE)

        // Auth 인증은 됐지만 프로필 문서가 없으면 정상 계정이 아니므로 되돌린다
        val document = db.collection("users").document(uid).get().await()
        val role = when (document.getString("role")) {
            ROLE_CHILD -> UserRole.CHILD
            ROLE_TEACHER -> UserRole.TEACHER
            else -> {
                auth.signOut()
                throw IllegalStateException("계정 정보를 찾을 수 없습니다.")
            }
        }

        // 이 기기로 알림을 받을 수 있도록 FCM 토큰을 저장.
        // 실패해도 로그인 자체는 막지 않는다.
        try {
            saveFcmToken(uid)
        } catch (e: Exception) {
            Log.w(FCM_TAG, "FCM 토큰 저장 실패", e)
        }

        role
    }

    /**
     * 이름 + 이메일이 모두 일치하는 계정의 아이디를 찾는다.
     *
     * 이메일로 먼저 조회한 뒤 이름을 코드에서 대조한다.
     * (필드 두 개로 동시에 조회하면 복합 색인이 필요할 수 있어서 단일 필드로 좁힌다)
     */
    override suspend fun findLoginId(name: String, email: String): Result<String> = runCatching {
        val trimmedName = name.trim()
        val trimmedEmail = email.trim().lowercase()

        if (trimmedName.isBlank()) throw IllegalArgumentException("이름을 입력해 주세요.")
        if (!EMAIL_PATTERN.matches(trimmedEmail)) {
            throw IllegalArgumentException("이메일 형식이 올바르지 않습니다.")
        }

        val snapshot = db.collection("users")
            .whereEqualTo("email", trimmedEmail)
            .get()
            .await()

        val matched = snapshot.documents.firstOrNull { document ->
            document.getString("name")?.trim() == trimmedName
        } ?: throw IllegalStateException(ID_NOT_FOUND_MESSAGE)

        matched.getString("loginId") ?: throw IllegalStateException(ID_NOT_FOUND_MESSAGE)
    }

    /**
     * 아이디로 가입한 이메일을 찾아 비밀번호 재설정 메일을 보낸다.
     *
     * 가입 정보가 없어도 성공으로 처리한다.
     * (가입된 아이디인지 아닌지를 알려주면 계정을 추측당할 수 있다)
     */
    override suspend fun sendPasswordReset(loginId: String): Result<Unit> = runCatching {
        val trimmed = loginId.trim()
        if (trimmed.isBlank()) throw IllegalArgumentException("아이디를 입력해 주세요.")

        val idDoc = db.collection("loginIds").document(trimmed).get().await()
        val email = idDoc.getString("email") ?: return@runCatching Unit

        // 내부용 가짜 주소는 실제로 메일을 받을 수 없다
        if (email.endsWith(INTERNAL_EMAIL_DOMAIN)) {
            throw IllegalStateException("이 계정은 이메일이 등록되어 있지 않습니다. 선생님께 문의해 주세요.")
        }

        auth.sendPasswordResetEmail(email).await()
        Unit
    }

    override suspend fun getCurrentUser(): Result<UserInfo> = runCatching {
        val uid = auth.currentUser?.uid
            ?: throw IllegalStateException("로그인이 필요합니다.")

        val document = db.collection("users").document(uid).get().await()
        if (!document.exists()) throw IllegalStateException("계정 정보를 찾을 수 없습니다.")

        UserInfo(
            uid = uid,
            role = if (document.getString("role") == ROLE_CHILD) UserRole.CHILD else UserRole.TEACHER,
            loginId = document.getString("loginId").orEmpty(),
            email = document.getString("email").orEmpty(),
            name = document.getString("name").orEmpty(),
            gender = document.getString("gender"),
            birthYear = document.getLong("birthYear")?.toInt(),
            birthMonth = document.getLong("birthMonth")?.toInt(),
            birthDay = document.getLong("birthDay")?.toInt(),
            // Firestore는 List로 돌려준다 (저장할 때 Set을 List로 바꿔 넣었음)
            sensoryTraits = (document.get("sensoryTraits") as? List<*>)
                ?.filterIsInstance<String>().orEmpty(),
            behaviors = (document.get("behaviors") as? List<*>)
                ?.filterIsInstance<String>().orEmpty(),
        )
    }

    override suspend fun updateProfile(
        name: String,
        gender: String?,
        birthYear: Int?,
        birthMonth: Int?,
        birthDay: Int?
    ): Result<Unit> = runCatching {
        val uid = auth.currentUser?.uid
            ?: throw IllegalStateException("로그인이 필요합니다.")
        if (name.isBlank()) throw IllegalArgumentException("이름을 입력해 주세요.")

        db.collection("users").document(uid).update(
            mapOf(
                "name" to name.trim(),
                "gender" to gender,
                "birthYear" to birthYear,
                "birthMonth" to birthMonth,
                "birthDay" to birthDay,
            )
        ).await()

        Unit
    }

    /**
     * 로그아웃.
     *
     * 저장된 FCM 토큰을 먼저 지운다.
     * 안 지우면 로그아웃한 뒤에도 이 기기로 아동 알림이 계속 온다.
     */
    override suspend fun logout() {
        val uid = auth.currentUser?.uid
        if (uid != null) {
            try {
                db.collection("users").document(uid)
                    .update("fcmToken", FieldValue.delete())
                    .await()
            } catch (e: Exception) {
                Log.w(FCM_TAG, "FCM 토큰 삭제 실패", e)
            }
        }
        auth.signOut()
    }

    /** 이 기기의 FCM 토큰을 사용자 문서에 저장한다. */
    private suspend fun saveFcmToken(uid: String) {
        val token = FirebaseMessaging.getInstance().token.await()
        Log.d(FCM_TAG, token)   // 발송 테스트용으로 로그에 남긴다
        db.collection("users").document(uid).update("fcmToken", token).await()
    }

    private fun buildUserDocument(
        loginId: String,
        email: String,
        role: String,
        childProfile: ChildProfile
    ): Map<String, Any?> {
        // 이름·성별·생년월일은 교사·아동 모두의 정보라 역할과 무관하게 저장한다.
        // (아이디 찾기에서 이름을 쓰므로 교사도 반드시 필요)
        val document = mutableMapOf<String, Any?>(
            "role" to role,
            "loginId" to loginId,
            "email" to email,
            "name" to childProfile.name,
            "gender" to childProfile.gender,
            "birthYear" to childProfile.birthYear,
            "birthMonth" to childProfile.birthMonth,
            "birthDay" to childProfile.birthDay,
            "createdAt" to FieldValue.serverTimestamp(),
        )

        if (role == ROLE_CHILD) {
            // 담당 교사는 가입 시점에 알 수 없다.
            // 교사가 '담당 아동 연결' 화면에서 나중에 연결한다.
            document["teacherId"] = null

            // 감각특성·상동행동은 아동에게만 해당하는 항목
            // Firestore는 Set을 저장하지 못하므로 List로 변환한다
            document["sensoryTraits"] = childProfile.sensoryTraits.toList()
            document["behaviors"] = childProfile.behaviors.toList()
        }

        return document
    }

    /** 이메일이 없는 아동 계정용 내부 주소. 실제로 메일을 받을 수 없는 가짜 주소다. */
    private fun String.toInternalEmail(): String = "${this.lowercase()}$INTERNAL_EMAIL_DOMAIN"

    private fun String.toRole(): String =
        if (this.trim() == "아동용") ROLE_CHILD else ROLE_TEACHER

    companion object {
        const val ROLE_TEACHER = "TEACHER"
        const val ROLE_CHILD = "CHILD"

        private const val FCM_TAG = "FCM_TOKEN"
        private const val INTERNAL_EMAIL_DOMAIN = "@aion.local"
        private const val LOGIN_FAILED_MESSAGE = "아이디 또는 비밀번호가 올바르지 않습니다."
        private const val ID_NOT_FOUND_MESSAGE = "일치하는 가입 정보가 없습니다."

        // 화면 안내 문구("영문, 숫자 포함 8자 이상")와 동일한 규칙
        private val PASSWORD_PATTERN = Regex("^(?=.*[A-Za-z])(?=.*\\d).{8,}$")
        private val EMAIL_PATTERN = Regex("^[\\w.+-]+@[\\w-]+\\.[\\w.-]+$")
    }
}