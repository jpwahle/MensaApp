package com.lkaesberg.mensaapp.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lkaesberg.mensaapp.MealsAppState
import com.lkaesberg.mensaapp.data.CanteenStaticData
import com.lkaesberg.mensaapp.data.HoursEntry
import com.lkaesberg.mensaapp.ui.MensaTheme
import com.lkaesberg.mensaapp.ui.components.MTopBar
import com.lkaesberg.mensaapp.ui.components.OccupancyChip
import kotlinx.datetime.Clock
import kotlinx.datetime.TimeZone
import kotlinx.datetime.todayIn

@Composable
fun CanteenPickerScreen(
    state: MealsAppState,
    onBack: () -> Unit,
    onSelected: (slug: String) -> Unit,
) {
    val palette = MensaTheme.palette
    val scope = rememberCoroutineScope()
    val canteens by state.canteens.collectAsState()
    val selected by state.selectedCanteen.collectAsState()
    val disabledCanteenIds by state.disabledCanteenIds.collectAsState()
    val todayDow = remember { Clock.System.todayIn(TimeZone.currentSystemDefault()).dayOfWeek }

    val visibleCanteens = canteens.filter { it.id !in disabledCanteenIds }
    val occupancyMap by state.occupancy.collectAsState()
    val s = com.lkaesberg.mensaapp.i18n.LocalStrings.current
    Column(modifier = Modifier.fillMaxSize().background(palette.paper)) {
        MTopBar(
            title = s.pickCanteen,
            subtitle = s.canteenPickerSubtitle(visibleCanteens.size),
            onBack = onBack,
        )
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            val sortedCanteens = visibleCanteens.sortedBy { it.name.lowercase() }
            items(sortedCanteens, key = { it.id }) { canteen ->
                val info = CanteenStaticData.matchFor(canteen.name)
                val isActive = canteen.id == selected?.id
                val isOpen = state.openNow(canteen)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(14.dp))
                        .background(palette.surface)
                        .border(
                            width = if (isActive) 2.dp else 1.dp,
                            color = if (isActive) palette.forest else palette.hair,
                            shape = RoundedCornerShape(14.dp),
                        )
                        .clickable {
                            state.selectCanteen(scope, canteen)
                            onSelected(info?.slug ?: canteen.id)
                        }
                        .padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .clip(CircleShape)
                            .background(if (isOpen) palette.forest else palette.hair),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            text = (sortedCanteens.indexOf(canteen) + 1).toString(),
                            color = if (isOpen) Color.White else palette.sub,
                            fontWeight = FontWeight.ExtraBold,
                            fontSize = 14.sp,
                        )
                    }
                    Column(modifier = Modifier.weight(1f)) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text(
                                text = canteen.name,
                                color = palette.ink,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold,
                            )
                            if (isActive) {
                                Text(
                                    text = "AKTIV",
                                    color = palette.forestDark,
                                    fontSize = 9.sp,
                                    fontWeight = FontWeight.Bold,
                                    letterSpacing = 0.4.sp,
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(4.dp))
                                        .background(palette.moss)
                                        .padding(horizontal = 6.dp, vertical = 2.dp),
                                )
                            }
                        }
                        Row(
                            modifier = Modifier.padding(top = 3.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                        ) {
                            Box(modifier = Modifier.size(6.dp).clip(CircleShape).background(if (isOpen) palette.open else palette.closed))
                            Text(
                                text = if (isOpen) "${if (s.openLabel == "Open") "until" else "bis"} ${state.closesAt(canteen)}" else s.closedLabel.lowercase(),
                                color = if (isOpen) palette.open else palette.closed,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.SemiBold,
                            )
                            if (info != null) {
                                Text("·", color = palette.sub.copy(alpha = 0.5f), fontSize = 11.sp)
                                Text(info.address.substringAfter(", ").take(30), color = palette.sub, fontSize = 11.sp)
                            }
                        }
                        if (isOpen) {
                            Row(
                                modifier = Modifier.padding(top = 4.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                OccupancyChip(
                                    occupancy = occupancyMap[canteen.id],
                                    isOpen = true,
                                )
                            }
                        }
                        if (info != null) {
                            Spacer(Modifier.height(8.dp))
                            HoursList(hours = info.hours, todayDow = todayDow)
                        }
                    }
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                        contentDescription = null,
                        tint = palette.sub,
                        modifier = Modifier.size(16.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun HoursList(
    hours: List<HoursEntry>,
    todayDow: kotlinx.datetime.DayOfWeek,
) {
    val palette = MensaTheme.palette
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        hours.forEach { h ->
            val isToday = todayDow in h.days
            val labelColor = if (isToday) palette.ink else palette.sub
            val weight = if (isToday) FontWeight.Bold else FontWeight.Medium
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Text(
                    text = h.daysLabel,
                    color = labelColor,
                    fontSize = 11.sp,
                    fontWeight = weight,
                    modifier = Modifier.padding(end = 4.dp),
                )
                Box(modifier = Modifier.weight(1f).height(1.dp).background(palette.hair.copy(alpha = 0.6f)))
                Text(
                    text = h.time,
                    color = labelColor,
                    fontSize = 11.sp,
                    fontWeight = weight,
                )
            }
        }
    }
}

