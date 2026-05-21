package com.example.aion_app.function.password.eunseo

import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.View
import androidx.activity.ComponentActivity
import androidx.lifecycle.lifecycleScope
import com.example.aion_app.R
import com.example.aion_app.databinding.ActivityPasswordVerifyBinding
import com.example.aion_app.function.password.eunseo.data.MockPasswordRepository
import com.example.aion_app.function.password.eunseo.data.PasswordRepository
import com.example.aion_app.function.password.eunseo.data.PasswordResult
import kotlinx.coroutines.launch

/**
 * 화면 1: 비밀번호 변경 전 확인.
 *
 * 흐름:
 *   1. 아이디 + 현재 비밀번호 입력
 *   2. 둘 다 입력되면 "다음" 버튼 활성화
 *   3. "다음" 클릭 → Repository로 검증
 *   4. 성공: (다음 PR에서 만들 PasswordChangeActivity로 이동) — 지금은 finish() 처리만
 *      실패: 에러 메시지 표시
 *
 * Repository는 일단 MockPasswordRepository를 직접 생성한다.
 * 추후 DI(Hilt 등) 도입 시 생성자 주입 패턴으로 교체.
 */
class PasswordVerifyActivity : ComponentActivity() {

    private lateinit var binding: ActivityPasswordVerifyBinding

    // TODO: 추후 DI로 교체. 지금은 직접 생성.
    private val repository: PasswordRepository = MockPasswordRepository()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityPasswordVerifyBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupListeners()
    }

    private fun setupListeners() {
        binding.btnBack.setOnClickListener { finish() }

        // 입력값 변경 시: 다음 버튼 상태 갱신 + 에러 메시지 자동 숨김.
        val onInputChanged = {
            updateNextButtonState()
            hideError()
        }
        binding.etUserId.addTextChangedListener(SimpleTextWatcher(onInputChanged))
        binding.etCurrentPassword.addTextChangedListener(SimpleTextWatcher(onInputChanged))

        binding.btnNext.setOnClickListener { onNextClicked() }
    }

    private fun updateNextButtonState() {
        val idFilled = binding.etUserId.text.toString().isNotBlank()
        val pwFilled = binding.etCurrentPassword.text.toString().isNotBlank()
        binding.btnNext.isEnabled = idFilled && pwFilled
    }

    private fun onNextClicked() {
        val userId = binding.etUserId.text.toString().trim()
        val currentPw = binding.etCurrentPassword.text.toString()

        // 빈 값 가드 (버튼 자체가 disabled지만 방어).
        if (userId.isEmpty()) {
            showError(getString(R.string.password_error_empty_id))
            return
        }
        if (currentPw.isEmpty()) {
            showError(getString(R.string.password_error_empty_pw))
            return
        }

        // 입력 중 재요청 방지.
        binding.btnNext.isEnabled = false

        lifecycleScope.launch {
            val result = repository.verifyCurrentPassword(userId, currentPw)
            handleResult(result, userId, currentPw)
            // 결과 처리 후 버튼 활성화 다시 계산.
            updateNextButtonState()
        }
    }

    private fun handleResult(result: PasswordResult, userId: String, currentPw: String) {
        when (result) {
            is PasswordResult.Success -> {
                // TODO(eunseo): 다음 PR에서 PasswordChangeActivity로 이동.
                //   val intent = Intent(this, PasswordChangeActivity::class.java)
                //       .putExtra(EXTRA_USER_ID, userId)
                //       .putExtra(EXTRA_CURRENT_PW, currentPw)
                //   startActivity(intent)
                //   finish()
                hideError()
            }
            is PasswordResult.UserNotFound -> {
                showError(getString(R.string.password_error_user_not_found))
            }
            is PasswordResult.PasswordMismatch -> {
                showError(getString(R.string.password_error_mismatch))
            }
            is PasswordResult.Error -> {
                showError(getString(R.string.password_error_unknown))
            }
        }
    }

    private fun showError(message: String) {
        binding.tvError.text = message
        binding.tvError.visibility = View.VISIBLE
    }

    private fun hideError() {
        binding.tvError.visibility = View.INVISIBLE
    }

    companion object {
        // 다음 PR에서 다음 Activity로 넘길 때 사용.
        const val EXTRA_USER_ID = "extra_user_id"
        const val EXTRA_CURRENT_PW = "extra_current_pw"
    }
}

/**
 * TextWatcher boilerplate를 줄여주는 헬퍼.
 */
private class SimpleTextWatcher(
    private val onChanged: () -> Unit
) : TextWatcher {
    override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit
    override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) = onChanged()
    override fun afterTextChanged(s: Editable?) = Unit
}
