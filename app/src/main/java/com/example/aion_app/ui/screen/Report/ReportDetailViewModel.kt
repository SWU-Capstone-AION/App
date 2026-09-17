package com.example.aion_app.ui.screen.report

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.aion_app.data.report.ReportRepository
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

/**
 * 상세 리포트 화면의 탭·날짜 이동·불러오기.
 * 날짜를 옮기면 그 기간만 다시 불러온다.
 */
class ReportDetailViewModel(
    private val repository: ReportRepository = ReportRepository()
) : ViewModel() {

    private var childId: String? = null

    var period by mutableStateOf(ReportPeriod.DAILY)
        private set

    // 0 = 오늘/이번 주/이번 달, 음수 = 과거. 미래로는 못 감.
    private var dayOffset by mutableStateOf(0)
    private var weekOffset by mutableStateOf(0)
    private var monthOffset by mutableStateOf(0)

    var daily by mutableStateOf<ReportLoadState<DailyReport>>(ReportLoadState.Loading)
        private set
    var weekly by mutableStateOf<ReportLoadState<WeeklyReport>>(ReportLoadState.Loading)
        private set
    var monthly by mutableStateOf<ReportLoadState<MonthlyReport>>(ReportLoadState.Loading)
        private set

    private var dailyJob: Job? = null
    private var weeklyJob: Job? = null
    private var monthlyJob: Job? = null

    // ---------- 화면에 보여줄 값 ----------

    /** 날짜 네비게이터 가운데 글자 (불러오는 중에도 바로 보이도록 여기서 계산) */
    val dateLabel: String
        get() = when (period) {
            ReportPeriod.DAILY -> ReportDates.dailyLabel(ReportDates.dayOf(dayOffset))
            ReportPeriod.WEEKLY -> ReportDates.weeklyLabel(ReportDates.mondayOf(weekOffset))
            ReportPeriod.MONTHLY -> ReportDates.monthLabel(ReportDates.monthOf(monthOffset))
        }

    /** 이미지 파일 이름에 붙일 날짜 */
    val fileDateLabel: String
        get() = when (period) {
            ReportPeriod.DAILY -> ReportDates.dailyDetailLabel(ReportDates.dayOf(dayOffset))
            ReportPeriod.WEEKLY -> ReportDates.weeklyDetailLabel(ReportDates.mondayOf(weekOffset)).replace(" ", "")
            ReportPeriod.MONTHLY -> ReportDates.monthDetailLabel(ReportDates.monthOf(monthOffset))
        }

    val nextEnabled: Boolean
        get() = when (period) {
            ReportPeriod.DAILY -> dayOffset < 0
            ReportPeriod.WEEKLY -> weekOffset < 0
            ReportPeriod.MONTHLY -> monthOffset < 0
        }

    // ---------- 화면에서 부르는 동작 ----------

    /** 화면에 들어올 때 한 번. 같은 아동이면 다시 불러오지 않는다. */
    fun start(childId: String) {
        if (this.childId == childId) return
        this.childId = childId
        loadDaily()
        loadWeekly()
        loadMonthly()
    }

    fun selectPeriod(value: ReportPeriod) {
        period = value
    }

    fun prev() {
        when (period) {
            ReportPeriod.DAILY -> { dayOffset--; loadDaily() }
            ReportPeriod.WEEKLY -> { weekOffset--; loadWeekly() }
            ReportPeriod.MONTHLY -> { monthOffset--; loadMonthly() }
        }
    }

    fun next() {
        if (!nextEnabled) return
        when (period) {
            ReportPeriod.DAILY -> { dayOffset++; loadDaily() }
            ReportPeriod.WEEKLY -> { weekOffset++; loadWeekly() }
            ReportPeriod.MONTHLY -> { monthOffset++; loadMonthly() }
        }
    }

    /** 월간 달력에서 날짜를 누르면 그날 일간 리포트로 이동 */
    fun openDayFromCalendar(day: Int) {
        val month = ReportDates.monthOf(monthOffset)
        dayOffset = ReportDates.dayOffsetFor(
            year = month.get(java.util.Calendar.YEAR),
            month1 = month.get(java.util.Calendar.MONTH) + 1,
            day = day
        ).coerceAtMost(0)
        period = ReportPeriod.DAILY
        loadDaily()
    }

    /** 실패했을 때 [다시 시도] */
    fun retry() {
        when (period) {
            ReportPeriod.DAILY -> loadDaily()
            ReportPeriod.WEEKLY -> loadWeekly()
            ReportPeriod.MONTHLY -> loadMonthly()
        }
    }

    // ---------- 불러오기 ----------
    // 이전 요청이 아직 안 끝났으면 취소한다 (날짜를 빠르게 넘길 때 옛날 값이 덮어쓰지 않도록)

    private fun loadDaily() {
        val id = childId ?: return
        val day = ReportDates.dayOf(dayOffset)
        dailyJob?.cancel()
        daily = ReportLoadState.Loading
        dailyJob = viewModelScope.launch {
            daily = repository.getDaily(id, day).fold(
                onSuccess = { ReportLoadState.Success(it) },
                onFailure = { ReportLoadState.Error(ERROR_MESSAGE) }
            )
        }
    }

    private fun loadWeekly() {
        val id = childId ?: return
        val monday = ReportDates.mondayOf(weekOffset)
        weeklyJob?.cancel()
        weekly = ReportLoadState.Loading
        weeklyJob = viewModelScope.launch {
            weekly = repository.getWeekly(id, monday).fold(
                onSuccess = { ReportLoadState.Success(it) },
                onFailure = { ReportLoadState.Error(ERROR_MESSAGE) }
            )
        }
    }

    private fun loadMonthly() {
        val id = childId ?: return
        val month = ReportDates.monthOf(monthOffset)
        monthlyJob?.cancel()
        monthly = ReportLoadState.Loading
        monthlyJob = viewModelScope.launch {
            monthly = repository.getMonthly(id, month).fold(
                onSuccess = { ReportLoadState.Success(it) },
                onFailure = { ReportLoadState.Error(ERROR_MESSAGE) }
            )
        }
    }

    private companion object {
        const val ERROR_MESSAGE = "리포트를 불러오지 못했어요.\n잠시 후 다시 시도해 주세요."
    }
}