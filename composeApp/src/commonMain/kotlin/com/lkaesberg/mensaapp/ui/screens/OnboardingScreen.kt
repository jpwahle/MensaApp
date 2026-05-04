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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccessTime
import androidx.compose.material.icons.filled.Apartment
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lkaesberg.mensaapp.Meal
import com.lkaesberg.mensaapp.MealsAppState
import com.lkaesberg.mensaapp.data.CanteenStaticData
import com.lkaesberg.mensaapp.ui.MensaPalette
import com.lkaesberg.mensaapp.ui.MensaTheme
import com.lkaesberg.mensaapp.ui.components.DietPip
import com.lkaesberg.mensaapp.ui.components.Plate
import kotlin.random.Random

private const val STEP_COUNT = 4

@Composable
fun OnboardingScreen(
    state: MealsAppState,
    requestNotificationPermission: ((granted: Boolean) -> Unit) -> Unit,
    onComplete: () -> Unit,
) {
    val palette = MensaTheme.palette
    val scope = rememberCoroutineScope()
    var step by remember { mutableStateOf(0) }
    val canteens by state.canteens.collectAsState()
    val upcomingMap by state.upcomingAcrossCanteens.collectAsState()

    LaunchedEffect(Unit) {
        state.loadCanteens(scope)
        state.loadUpcomingAcrossCanteens(scope)
    }

    // Pick two random meals with real photos for the welcome hero. Stable
    // for the lifetime of the screen so the images don't flicker as the
    // user moves between steps; reseeds when fresh data arrives.
    val heroMeals = remember(upcomingMap.size) { pickHeroMeals(upcomingMap) }

    val finishOnboarding: () -> Unit = {
        state.settings.putBoolean("onboarded", true)
        onComplete()
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(palette.paper)
            .padding(horizontal = 22.dp, vertical = 20.dp),
    ) {
        StepIndicator(step = step, totalSteps = STEP_COUNT, palette = palette)
        Spacer(Modifier.height(20.dp))

        Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
            when (step) {
                0 -> WelcomeStep(palette, heroMeals)
                1 -> FeaturesStep(palette)
                2 -> ChooseCanteenStep(state, palette)
                else -> NotificationsStep(palette)
            }
        }

        Spacer(Modifier.height(12.dp))
        when (step) {
            STEP_COUNT - 1 -> {
                // Final step: enable notifications + finish
                Button(
                    onClick = {
                        // Optimistically enable; if the user declines the
                        // system dialog we revert so the master switch in
                        // Settings reflects reality.
                        state.settings.putBoolean("notif_favorites", true)
                        state.notificationScheduler.setEnabled(
                            enabled = true,
                            leadDays = state.settings.getInt("notif_lead_days", 3),
                            hourOfDay = state.settings.getInt("notif_time_hour", 9),
                        )
                        requestNotificationPermission { granted ->
                            if (!granted) {
                                state.settings.putBoolean("notif_favorites", false)
                                state.notificationScheduler.setEnabled(
                                    enabled = false,
                                    leadDays = state.settings.getInt("notif_lead_days", 3),
                                    hourOfDay = state.settings.getInt("notif_time_hour", 9),
                                )
                            }
                        }
                        finishOnboarding()
                    },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(100.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = palette.forest, contentColor = Color.White),
                    contentPadding = PaddingValues(vertical = 14.dp),
                ) {
                    Text("Benachrichtigungen erlauben", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                }
                Spacer(Modifier.height(12.dp))
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { finishOnboarding() },
                    contentAlignment = Alignment.Center,
                ) {
                    Text("Ohne Benachrichtigungen fortfahren", color = palette.sub, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                }
            }
            else -> {
                Button(
                    onClick = { step += 1 },
                    enabled = canContinue(step, state, canteens),
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(100.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = palette.forest, contentColor = Color.White),
                    contentPadding = PaddingValues(vertical = 14.dp),
                ) {
                    Text("Weiter", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                }
                Spacer(Modifier.height(12.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    if (step > 0) {
                        Box(
                            modifier = Modifier.clickable { step -= 1 }.padding(8.dp),
                        ) {
                            Text("Zurück", color = palette.sub, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                        }
                    } else {
                        Spacer(Modifier.width(0.dp))
                    }
                    Box(
                        modifier = Modifier.clickable { finishOnboarding() }.padding(8.dp),
                    ) {
                        Text("Überspringen", color = palette.sub, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                    }
                }
            }
        }
    }
}

private fun canContinue(step: Int, state: MealsAppState, canteens: List<com.lkaesberg.mensaapp.Canteen>): Boolean = when (step) {
    2 -> state.selectedCanteen.value != null || canteens.isEmpty()
    else -> true
}

@Composable
private fun StepIndicator(step: Int, totalSteps: Int, palette: MensaPalette) {
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        repeat(totalSteps) { i ->
            Box(
                modifier = Modifier
                    .weight(1f)
                    .height(3.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(if (i <= step) palette.forest else palette.hair)
            )
        }
    }
}

// ── Step 0 — welcome ───────────────────────────────────────────────────────
@Composable
private fun WelcomeStep(palette: MensaPalette, heroMeals: List<Meal>) {
    Column(modifier = Modifier.fillMaxSize()) {
        Hero(palette, heroMeals)
        Spacer(Modifier.height(8.dp))
        Text(
            text = "Verpasse keinen",
            color = palette.ink,
            fontSize = 28.sp,
            fontWeight = FontWeight.ExtraBold,
            letterSpacing = (-0.6).sp,
        )
        Text(
            text = "Lieblingsteller mehr.",
            color = palette.forest,
            fontSize = 28.sp,
            fontWeight = FontWeight.ExtraBold,
            letterSpacing = (-0.6).sp,
        )
        Spacer(Modifier.height(12.dp))
        Text(
            text = "Behalte den Speiseplan aller Mensen Göttingens an einem Ort im Blick — mit Diät-Filtern, Preisen und Benachrichtigungen für deine Favoriten.",
            color = palette.sub,
            fontSize = 14.sp,
            lineHeight = 20.sp,
        )
    }
}

@Composable
private fun Hero(palette: MensaPalette, heroMeals: List<Meal>) {
    Box(
        modifier = Modifier.fillMaxWidth().height(200.dp),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            modifier = Modifier
                .size(180.dp)
                .clip(CircleShape)
                .background(palette.moss)
        )
        Box(
            modifier = Modifier
                .padding(start = 100.dp, top = 30.dp)
                .size(70.dp)
                .clip(CircleShape)
                .background(palette.amberLight)
        )
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            HeroPlateCard(heroMeals.getOrNull(0), -7f, "vegan", palette)
            Spacer(Modifier.width(0.dp))
            HeroPlateCard(heroMeals.getOrNull(1), 5f, "vegetarisch", palette)
        }
    }
}

@Composable
private fun HeroPlateCard(meal: Meal?, rotation: Float, fallbackDietKind: String, palette: MensaPalette) {
    val dietKind = meal?.icons?.firstOrNull() ?: fallbackDietKind
    Column(
        modifier = Modifier
            .clip(RoundedCornerShape(18.dp))
            .background(palette.surface)
            .border(1.dp, palette.hair, RoundedCornerShape(18.dp))
            .rotate(rotation)
            .padding(8.dp),
    ) {
        Plate(meal = meal, size = 80.dp, radius = 12.dp)
        Row(
            modifier = Modifier.padding(top = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            DietPip(kind = dietKind, size = 12.dp)
            Text("★", color = palette.amber, fontSize = 11.sp, fontWeight = FontWeight.Bold)
        }
    }
}

private fun pickHeroMeals(
    upcomingMap: Map<Pair<com.lkaesberg.mensaapp.Canteen, kotlinx.datetime.LocalDate>, List<com.lkaesberg.mensaapp.MealDate>>,
): List<Meal> {
    val pool = upcomingMap.values.asSequence()
        .flatten()
        .mapNotNull { it.meals }
        .filter { !it.imagePath.isNullOrBlank() }
        .distinctBy { it.id }
        .toList()
    if (pool.size < 2) return pool
    val rng = Random.Default
    val first = pool.random(rng)
    val second = (pool - first).random(rng)
    return listOf(first, second)
}

// ── Step 1 — features ──────────────────────────────────────────────────────
@Composable
private fun FeaturesStep(palette: MensaPalette) {
    Column(modifier = Modifier.fillMaxSize()) {
        Text(
            text = "So hilft dir die App",
            color = palette.ink,
            fontSize = 26.sp,
            fontWeight = FontWeight.ExtraBold,
            letterSpacing = (-0.5).sp,
        )
        Spacer(Modifier.height(8.dp))
        Text(
            text = "Drei Funktionen, die dir den Mensagang erleichtern.",
            color = palette.sub,
            fontSize = 14.sp,
            lineHeight = 20.sp,
        )
        Spacer(Modifier.height(20.dp))
        FeatureBullet(
            icon = Icons.Filled.Star,
            title = "Lieblingsgerichte favorisieren",
            subtitle = "Tippe das ★ auf einem Gericht, um es zu speichern.",
            palette = palette,
        )
        Spacer(Modifier.height(12.dp))
        FeatureBullet(
            icon = Icons.Filled.AccessTime,
            title = "Tagesplan, Preise & Allergene",
            subtitle = "Gerichte für 5 Tage im Voraus mit Beilagen, Studi-Preis und Diät-Filtern.",
            palette = palette,
        )
        Spacer(Modifier.height(12.dp))
        FeatureBullet(
            icon = Icons.Filled.Notifications,
            title = "Push, wenn dein Favorit läuft",
            subtitle = "Wir melden uns morgens, sobald dein Favorit auf dem Plan steht.",
            palette = palette,
        )
    }
}

@Composable
private fun FeatureBullet(icon: ImageVector, title: String, subtitle: String, palette: MensaPalette) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.Top,
    ) {
        Box(
            modifier = Modifier
                .size(38.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(palette.moss),
            contentAlignment = Alignment.Center,
        ) {
            Icon(icon, null, tint = palette.forestDark, modifier = Modifier.size(19.dp))
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(title, color = palette.ink, fontSize = 14.sp, fontWeight = FontWeight.Bold)
            Text(subtitle, color = palette.sub, fontSize = 12.sp, lineHeight = 17.sp)
        }
    }
}

// ── Step 2 — pick a canteen ────────────────────────────────────────────────
@Composable
private fun ChooseCanteenStep(state: MealsAppState, palette: MensaPalette) {
    val scope = rememberCoroutineScope()
    val canteens by state.canteens.collectAsState()
    val selected by state.selectedCanteen.collectAsState()
    Column(modifier = Modifier.fillMaxSize()) {
        Text(
            text = "Wähle deine Mensa",
            color = palette.ink,
            fontSize = 26.sp,
            fontWeight = FontWeight.ExtraBold,
            letterSpacing = (-0.5).sp,
        )
        Spacer(Modifier.height(8.dp))
        Text(
            text = "Wir öffnen die App immer mit dieser Mensa. Du kannst später jederzeit wechseln.",
            color = palette.sub,
            fontSize = 14.sp,
            lineHeight = 20.sp,
        )
        Spacer(Modifier.height(16.dp))
        if (canteens.isEmpty()) {
            Text(
                text = "Mensen werden geladen…",
                color = palette.sub,
                fontSize = 13.sp,
            )
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                items(canteens.sortedBy { it.name.lowercase() }, key = { it.id }) { canteen ->
                    val info = CanteenStaticData.matchFor(canteen.name)
                    val isSelected = canteen.id == selected?.id
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(14.dp))
                            .background(palette.surface)
                            .border(
                                width = if (isSelected) 2.dp else 1.dp,
                                color = if (isSelected) palette.forest else palette.hair,
                                shape = RoundedCornerShape(14.dp),
                            )
                            .clickable { state.selectCanteen(scope, canteen) }
                            .padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        Box(
                            modifier = Modifier
                                .size(36.dp)
                                .clip(RoundedCornerShape(10.dp))
                                .background(if (isSelected) palette.forest else palette.moss),
                            contentAlignment = Alignment.Center,
                        ) {
                            Icon(
                                Icons.Filled.Apartment,
                                null,
                                tint = if (isSelected) Color.White else palette.forestDark,
                                modifier = Modifier.size(18.dp),
                            )
                        }
                        Column(modifier = Modifier.weight(1f)) {
                            Text(canteen.name, color = palette.ink, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                            if (info != null) {
                                Text(
                                    text = info.address.substringAfter(", ").take(40),
                                    color = palette.sub,
                                    fontSize = 11.sp,
                                )
                            }
                        }
                        if (isSelected) {
                            Icon(
                                Icons.Filled.CheckCircle,
                                null,
                                tint = palette.forest,
                                modifier = Modifier.size(20.dp),
                            )
                        }
                    }
                }
            }
        }
    }
}

// ── Step 3 — notifications ────────────────────────────────────────────────
@Composable
private fun NotificationsStep(palette: MensaPalette) {
    Column(modifier = Modifier.fillMaxSize()) {
        Text(
            text = "Push für Favoriten",
            color = palette.ink,
            fontSize = 26.sp,
            fontWeight = FontWeight.ExtraBold,
            letterSpacing = (-0.5).sp,
        )
        Spacer(Modifier.height(8.dp))
        Text(
            text = "Erlaube Benachrichtigungen, damit wir dir Bescheid geben, wenn dein Lieblingsgericht auf dem Plan steht.",
            color = palette.sub,
            fontSize = 14.sp,
            lineHeight = 20.sp,
        )
        Spacer(Modifier.height(20.dp))
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(14.dp))
                .background(palette.surface)
                .border(1.dp, palette.hair, RoundedCornerShape(14.dp))
                .padding(12.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.Top,
        ) {
            Box(
                modifier = Modifier.size(38.dp).clip(RoundedCornerShape(10.dp)).background(palette.forest),
                contentAlignment = Alignment.Center,
            ) {
                Icon(Icons.Filled.Notifications, null, tint = Color.White, modifier = Modifier.size(18.dp))
            }
            Column(modifier = Modifier.weight(1f)) {
                Text("SO SIEHT'S AUS", color = palette.sub, fontSize = 9.sp, fontWeight = FontWeight.Bold, letterSpacing = 0.6.sp)
                Spacer(Modifier.height(2.dp))
                Text("★ Linsen-Dal morgen!", color = palette.ink, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                Text("Zentralmensa · 3,80 €", color = palette.sub, fontSize = 11.sp)
            }
        }
        Spacer(Modifier.height(12.dp))
        Text(
            text = "Du kannst die Reichweite (Heute / Morgen / 3 Tage / Woche) später in den Einstellungen anpassen oder Benachrichtigungen wieder ausschalten.",
            color = palette.sub,
            fontSize = 12.sp,
            lineHeight = 17.sp,
        )
    }
}
