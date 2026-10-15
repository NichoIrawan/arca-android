package org.nicho.arca.designsystem

import androidx.annotation.DrawableRes
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import org.nicho.arca.R
import org.nicho.arca.domain.model.AccountType

/**
 * Lucide icons (DSG-ARC-001 §7.1, provisional), drawn at the design system's 1.5 stroke.
 * The drawables are copied from Lucide into res/drawable/lucide_*.xml; licence in
 * third_party/lucide/LICENSE.
 *
 * Categories and accounts store an icon *name* ("utensils", "shopping-bag"), so the name → drawable
 * mapping lives here. An unknown name draws a tag rather than failing.
 */
object ArcaIcons {
    // Navigation and actions
    val overview = R.drawable.lucide_chart_pie
    val transactions = R.drawable.lucide_list
    val budget = R.drawable.lucide_wallet
    val wallet = R.drawable.lucide_wallet
    val accounts = R.drawable.lucide_credit_card
    val reports = R.drawable.lucide_chart_line
    val settings = R.drawable.lucide_settings
    val add = R.drawable.lucide_plus
    val minus = R.drawable.lucide_minus
    val more = R.drawable.lucide_ellipsis
    val back = R.drawable.lucide_arrow_left
    val close = R.drawable.lucide_x
    val chevronRight = R.drawable.lucide_chevron_right
    val chevronLeft = R.drawable.lucide_chevron_left
    val chevronDown = R.drawable.lucide_chevron_down
    val search = R.drawable.lucide_search
    val filter = R.drawable.lucide_sliders_horizontal
    val arrowDown = R.drawable.lucide_arrow_down
    val arrowUp = R.drawable.lucide_arrow_up
    val arrowRight = R.drawable.lucide_arrow_right
    val adjustment = R.drawable.lucide_scale
    val warning = R.drawable.lucide_triangle_alert
    val banknote = R.drawable.lucide_banknote
    val transfer = R.drawable.lucide_arrow_left_right
    val income = R.drawable.lucide_briefcase
    val lock = R.drawable.lucide_lock
    val star = R.drawable.lucide_star
    val check = R.drawable.lucide_check
    val delete = R.drawable.lucide_trash_2
    val archive = R.drawable.lucide_archive
    val calendar = R.drawable.lucide_calendar
    val repeat = R.drawable.lucide_repeat
    val download = R.drawable.lucide_download
    val edit = R.drawable.lucide_pencil
    val info = R.drawable.lucide_info
    val language = R.drawable.lucide_languages
    val fingerprint = R.drawable.lucide_fingerprint_pattern
    val receipt = R.drawable.lucide_receipt

    /** Icons offered in the category and account editors. */
    val pickable: List<String> = listOf(
        "utensils", "car", "shopping-bag", "file-text", "heart", "film", "ellipsis", "coffee", "cookie",
        "fuel", "circle-parking", "smartphone", "repeat", "dumbbell", "graduation-cap", "gift", "house",
        "sparkles", "paw-print", "hand-heart", "briefcase", "coins", "award", "laptop", "wallet",
        "landmark", "layers", "credit-card", "receipt", "tag",
    )

    @DrawableRes
    fun named(name: String): Int = when (name) {
        "utensils" -> R.drawable.lucide_utensils
        "car" -> R.drawable.lucide_car
        "shopping-bag" -> R.drawable.lucide_shopping_bag
        "file-text" -> R.drawable.lucide_file_text
        "heart" -> R.drawable.lucide_heart
        "film" -> R.drawable.lucide_film
        "ellipsis" -> R.drawable.lucide_ellipsis
        "banknote" -> R.drawable.lucide_banknote
        "briefcase" -> R.drawable.lucide_briefcase
        "coins" -> R.drawable.lucide_coins
        "wallet" -> R.drawable.lucide_wallet
        "landmark" -> R.drawable.lucide_landmark
        "smartphone" -> R.drawable.lucide_smartphone
        "layers" -> R.drawable.lucide_layers
        "credit-card" -> R.drawable.lucide_credit_card
        "coffee" -> R.drawable.lucide_coffee
        "cookie" -> R.drawable.lucide_cookie
        "fuel" -> R.drawable.lucide_fuel
        "circle-parking" -> R.drawable.lucide_circle_parking
        "repeat" -> R.drawable.lucide_repeat
        "dumbbell" -> R.drawable.lucide_dumbbell
        "graduation-cap" -> R.drawable.lucide_graduation_cap
        "gift" -> R.drawable.lucide_gift
        "house" -> R.drawable.lucide_house
        "sparkles" -> R.drawable.lucide_sparkles
        "paw-print" -> R.drawable.lucide_paw_print
        "hand-heart" -> R.drawable.lucide_hand_heart
        "award" -> R.drawable.lucide_award
        "laptop" -> R.drawable.lucide_laptop
        "receipt" -> R.drawable.lucide_receipt
        else -> R.drawable.lucide_tag
    }

    /** DSG-ARC-001 §7.1 default icon per account type. */
    fun forAccountType(type: AccountType): String = when (type) {
        AccountType.CASH -> "wallet"
        AccountType.BANK -> "landmark"
        AccountType.EWALLET -> "smartphone"
        AccountType.OTHER -> "layers"
    }
}

/** A Lucide icon. Decorative by default (null description): the label beside it says what it is. */
@Composable
fun ArcaIcon(
    @DrawableRes icon: Int,
    modifier: Modifier = Modifier,
    tint: Color = ArcaDesignSystem.colors.ink,
    size: Dp = 20.dp,
    contentDescription: String? = null,
) {
    Icon(
        painter = painterResource(icon),
        contentDescription = contentDescription,
        tint = tint,
        modifier = modifier.size(size),
    )
}
