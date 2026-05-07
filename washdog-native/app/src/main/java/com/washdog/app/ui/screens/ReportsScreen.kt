package com.washdog.app.ui.screens

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.TabRowDefaults
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.washdog.app.R
import com.washdog.app.data.models.TxType
import com.washdog.app.ui.theme.WDBg
import com.washdog.app.ui.theme.WDBgAlt
import com.washdog.app.ui.theme.WDCard
import com.washdog.app.ui.theme.WDExpense
import com.washdog.app.ui.theme.WDIncome
import com.washdog.app.ui.theme.WDInk
import com.washdog.app.ui.theme.WDInkMute
import com.washdog.app.ui.theme.WDInkSoft
import com.washdog.app.ui.theme.WDPrimary
import com.washdog.app.viewmodel.AppViewModel
import java.time.LocalDate

@Composable
fun ReportsScreen(vm: AppViewModel) {
    val config      = LocalConfiguration.current
    val isTablet    = config.screenWidthDp >= 600
    val ph          = if (isTablet) 24.dp else 16.dp
    val maxW        = if (isTablet) 720.dp else Dp.Unspecified
    var selectedTab by remember { mutableIntStateOf(0) }
    val tabs        = listOf(
        stringResource(R.string.period_week),
        stringResource(R.string.period_month),
        stringResource(R.string.period_year)
    )
    val today = LocalDate.now()

    val filtered = when (selectedTab) {
        0 -> vm.transactions.filter { tx ->
            try { LocalDate.parse(tx.dateTime.take(10)) >= today.minusDays(6) } catch (e: Exception) { false }
        }
        1 -> vm.transactions.filter { tx ->
            try {
                val d = LocalDate.parse(tx.dateTime.take(10))
                d.year == today.year && d.monthValue == today.monthValue
            } catch (e: Exception) { false }
        }
        else -> vm.transactions.filter { tx ->
            try { LocalDate.parse(tx.dateTime.take(10)).year == today.year } catch (e: Exception) { false }
        }
    }

    val filteredIncome  = filtered.filter { it.type == TxType.IN  }.sumOf { it.amount }
    val filteredExpense = filtered.filter { it.type == TxType.OUT }.sumOf { it.amount }

    val (incomePoints, expensePoints) = when (selectedTab) {
        0 -> {
            val inc = (6 downTo 0).map { d ->
                val day = today.minusDays(d.toLong()).toString()
                filtered.filter { it.dateTime.startsWith(day) && it.type == TxType.IN }.sumOf { it.amount }.toFloat()
            }
            val exp = (6 downTo 0).map { d ->
                val day = today.minusDays(d.toLong()).toString()
                filtered.filter { it.dateTime.startsWith(day) && it.type == TxType.OUT }.sumOf { it.amount }.toFloat()
            }
            inc to exp
        }
        1 -> {
            val inc = (3 downTo 0).map { w ->
                val start = today.minusWeeks(w.toLong())
                val end   = start.plusWeeks(1)
                filtered.filter {
                    try { val d = LocalDate.parse(it.dateTime.take(10)); !d.isBefore(start) && d.isBefore(end) && it.type == TxType.IN } catch (e: Exception) { false }
                }.sumOf { it.amount }.toFloat()
            }
            val exp = (3 downTo 0).map { w ->
                val start = today.minusWeeks(w.toLong())
                val end   = start.plusWeeks(1)
                filtered.filter {
                    try { val d = LocalDate.parse(it.dateTime.take(10)); !d.isBefore(start) && d.isBefore(end) && it.type == TxType.OUT } catch (e: Exception) { false }
                }.sumOf { it.amount }.toFloat()
            }
            inc to exp
        }
        else -> {
            val inc = (11 downTo 0).map { m ->
                val month = today.minusMonths(m.toLong())
                filtered.filter {
                    try { val d = LocalDate.parse(it.dateTime.take(10)); d.year == month.year && d.monthValue == month.monthValue && it.type == TxType.IN } catch (e: Exception) { false }
                }.sumOf { it.amount }.toFloat()
            }
            val exp = (11 downTo 0).map { m ->
                val month = today.minusMonths(m.toLong())
                filtered.filter {
                    try { val d = LocalDate.parse(it.dateTime.take(10)); d.year == month.year && d.monthValue == month.monthValue && it.type == TxType.OUT } catch (e: Exception) { false }
                }.sumOf { it.amount }.toFloat()
            }
            inc to exp
        }
    }

    val maxVal      = ((incomePoints + expensePoints).maxOrNull() ?: 0f).coerceAtLeast(1f)
    val incomeNorm  = incomePoints.map  { it / maxVal }
    val expenseNorm = expensePoints.map { it / maxVal }

    val netLabel = when (selectedTab) {
        0    -> "Neto de la semana"
        1    -> "Neto del mes"
        else -> "Neto del año"
    }

    Box(Modifier.fillMaxSize().background(WDBg)) {
        Column(
            modifier = Modifier
                .widthIn(max = maxW)
                .align(Alignment.TopCenter)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = ph, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text(
                stringResource(R.string.reports_title),
                fontSize = 22.sp, fontWeight = FontWeight.Bold, color = WDInk
            )
            TabRow(
                selectedTabIndex = selectedTab,
                containerColor   = WDBgAlt,
                contentColor     = WDPrimary,
                indicator        = { tabPositions ->
                    TabRowDefaults.SecondaryIndicator(
                        modifier = Modifier.tabIndicatorOffset(tabPositions[selectedTab]),
                        color    = WDPrimary
                    )
                }
            ) {
                tabs.forEachIndexed { idx, title ->
                    Tab(
                        selected = idx == selectedTab,
                        onClick  = { selectedTab = idx },
                        text     = { Text(title, color = if (idx == selectedTab) WDPrimary else WDInkMute) }
                    )
                }
            }
            HeroReportCard(
                income      = filteredIncome,
                expense     = filteredExpense,
                netLabel    = netLabel,
                incomeNorm  = incomeNorm,
                expenseNorm = expenseNorm
            )
            TopServicesCard(vm = vm)
            Spacer(Modifier.height(80.dp))
        }
    }
}

@Composable
private fun HeroReportCard(
    income: Double,
    expense: Double,
    netLabel: String,
    incomeNorm: List<Float>,
    expenseNorm: List<Float>
) {
    Card(
        modifier  = Modifier.fillMaxWidth(),
        shape     = RoundedCornerShape(22.dp),
        colors    = CardDefaults.cardColors(containerColor = WDCard),
        elevation = CardDefaults.cardElevation(2.dp)
    ) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(
                text       = "$${"%,.2f".format(income - expense)}",
                fontSize   = 32.sp,
                fontWeight = FontWeight.Bold,
                color      = WDPrimary
            )
            Text(text = netLabel, fontSize = 13.sp, color = WDInkMute)
            val incomeColor  = WDIncome
            val expenseColor = WDExpense
            Canvas(Modifier.fillMaxWidth().height(100.dp)) {
                drawBarChart(incomeNorm, expenseNorm, incomeColor, expenseColor)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                LegendItem(color = WDIncome,  label = "Ingresos")
                LegendItem(color = WDExpense, label = "Gastos")
            }
        }
    }
}

private fun DrawScope.drawBarChart(
    income: List<Float>,
    expense: List<Float>,
    incomeColor: Color,
    expenseColor: Color
) {
    if (income.isEmpty()) return
    val barW  = size.width / (income.size * 3f)
    val gap   = barW * 0.3f
    val maxH  = size.height - 16f
    income.forEachIndexed { i, v ->
        val x = i * (barW * 2f + gap * 2f) + gap
        if (v > 0f) drawRect(
            color   = incomeColor,
            topLeft = Offset(x, maxH * (1 - v)),
            size    = Size(barW, maxH * v)
        )
        val ev = expense.getOrElse(i) { 0f }
        if (ev > 0f) drawRect(
            color   = expenseColor,
            topLeft = Offset(x + barW + gap * 0.5f, maxH * (1 - ev)),
            size    = Size(barW, maxH * ev)
        )
    }
}

@Composable
private fun LegendItem(color: Color, label: String) {
    Row(
        verticalAlignment     = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Box(Modifier.size(10.dp).background(color, RoundedCornerShape(2.dp)))
        Text(label, fontSize = 11.sp, color = WDInkMute)
    }
}

@Composable
private fun TopServicesCard(vm: AppViewModel) {
    val services = vm.appointments
        .groupBy { it.service.trim() }
        .map { (svc, appts) -> svc to appts.size }
        .sortedByDescending { it.second }
        .take(5)

    if (services.isEmpty()) return

    val max = services.maxOf { it.second }.coerceAtLeast(1)

    Card(
        modifier  = Modifier.fillMaxWidth(),
        shape     = RoundedCornerShape(22.dp),
        colors    = CardDefaults.cardColors(containerColor = WDCard),
        elevation = CardDefaults.cardElevation(2.dp)
    ) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Text(stringResource(R.string.top_services), fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = WDInk)
            services.forEach { (name, count) ->
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Row(
                        modifier              = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(name, fontSize = 13.sp, color = WDInkSoft)
                        Text("$count citas", fontSize = 12.sp, color = WDInkMute)
                    }
                    LinearProgressIndicator(
                        progress   = { count.toFloat() / max.toFloat() },
                        modifier   = Modifier.fillMaxWidth().height(6.dp),
                        color      = WDIncome,
                        trackColor = WDBgAlt
                    )
                }
            }
        }
    }
}
