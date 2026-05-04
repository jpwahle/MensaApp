package com.lkaesberg.mensaapp.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.outlined.StarOutline
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lkaesberg.mensaapp.MealDate
import com.lkaesberg.mensaapp.data.EnrichedMeal
import com.lkaesberg.mensaapp.data.MealEnrichment
import com.lkaesberg.mensaapp.ui.MensaTheme
import com.lkaesberg.mensaapp.ui.MonoNumericStyle

/**
 * Compact horizontal meal card used by the daily feed.
 * 88×88 image left, content right (eyebrow + title + sides + diet pips + price).
 *
 * @param favoriteHint optional inline note like "Favorit · auch Do" rendered
 *                     in an amber chip below the meta row when [isFavorite].
 * @param priceText pre-formatted price string, e.g. "4,20 €" — null hides the price.
 */
@Composable
fun MealCard(
    mealDate: MealDate,
    isFavorite: Boolean,
    onClick: () -> Unit,
    onFavoriteToggle: () -> Unit,
    modifier: Modifier = Modifier,
    priceText: String? = null,
    favoriteHint: String? = null,
    enriched: EnrichedMeal = MealEnrichment.enrich(mealDate),
    /** Force the card into the deactivated visual state (e.g. canteen past closing). */
    forceDeactivated: Boolean = false,
) {
    val palette = MensaTheme.palette
    val isDeactivated = mealDate.deactivatedAt != null || forceDeactivated
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(palette.surface)
            .border(1.dp, palette.hair, RoundedCornerShape(16.dp))
            .alpha(if (isDeactivated) 0.5f else 1f)
            .clickable { onClick() }
            .padding(12.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Plate(meal = mealDate.meals, size = 88.dp, radius = 12.dp)
        Column(modifier = Modifier.weight(1f)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Eyebrow(
                    text = mealDate.category,
                    modifier = Modifier.weight(1f, fill = false),
                )
                Box(
                    modifier = Modifier
                        .size(28.dp)
                        .clip(RoundedCornerShape(14.dp))
                        .clickable { onFavoriteToggle() },
                    contentAlignment = Alignment.Center,
                ) {
                    if (isFavorite) {
                        Icon(
                            imageVector = Icons.Filled.Star,
                            contentDescription = "Favorit entfernen",
                            tint = palette.amber,
                            modifier = Modifier.size(20.dp),
                        )
                    } else {
                        Icon(
                            imageVector = Icons.Outlined.StarOutline,
                            contentDescription = "Favorisieren",
                            tint = palette.sub,
                            modifier = Modifier.size(20.dp),
                        )
                    }
                }
            }
            Spacer(Modifier.height(2.dp))
            Text(
                text = enriched.cleanTitle.ifBlank { mealDate.meals?.title ?: "" },
                color = palette.ink,
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
                lineHeight = 18.sp,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            if (enriched.sides.isNotEmpty()) {
                Spacer(Modifier.height(4.dp))
                Text(
                    text = "mit " + enriched.sides.joinToString(", "),
                    color = palette.sub,
                    fontSize = 12.sp,
                    lineHeight = 15.sp,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Spacer(Modifier.height(8.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    mealDate.meals?.icons.orEmpty().forEach { kind ->
                        DietPip(kind = kind, size = 18.dp)
                    }
                }
                if (priceText != null) {
                    Text(
                        text = priceText,
                        color = palette.forestDark,
                        fontSize = 13.sp,
                        style = MonoNumericStyle,
                        fontWeight = FontWeight.Bold,
                    )
                }
            }
            if (isFavorite && favoriteHint != null) {
                Spacer(Modifier.height(8.dp))
                Row(
                    modifier = Modifier
                        .clip(RoundedCornerShape(6.dp))
                        .background(palette.amberLight)
                        .padding(horizontal = 8.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    Icon(
                        imageVector = Icons.Filled.Notifications,
                        contentDescription = null,
                        tint = palette.amberDark,
                        modifier = Modifier.size(11.dp),
                    )
                    Text(
                        text = favoriteHint,
                        color = palette.amberDark,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.SemiBold,
                    )
                }
            }
        }
    }
}
