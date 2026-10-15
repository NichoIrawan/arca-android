package org.nicho.arca.feature.transactions

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import org.nicho.arca.R
import org.nicho.arca.designsystem.ArcaDesignSystem
import org.nicho.arca.designsystem.ArcaIcon
import org.nicho.arca.designsystem.ArcaIcons
import org.nicho.arca.domain.model.TransactionId
import org.nicho.arca.domain.readmodel.ObserveTransactionList
import org.nicho.arca.domain.readmodel.PeriodChoice
import org.nicho.arca.domain.readmodel.TransactionFilter
import org.nicho.arca.domain.readmodel.TransactionListModel
import org.nicho.arca.ui.components.IconAction
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import org.nicho.arca.domain.model.TransactionListItem
import org.nicho.arca.domain.model.TransactionType
import org.nicho.arca.ui.components.Hairline
import org.nicho.arca.ui.components.IconTile
import org.nicho.arca.ui.components.pressable
import org.nicho.arca.ui.components.transactionText
import org.nicho.arca.ui.format.dayLabel
import kotlin.time.Duration.Companion.milliseconds

/** Searches transaction notes across everything recorded (board "Search Transactions"). */
@HiltViewModel
class SearchViewModel @Inject constructor(observeTransactionList: ObserveTransactionList) : ViewModel() {
    private val _query = MutableStateFlow("")
    val query: StateFlow<String> = _query.asStateFlow()

    @OptIn(FlowPreview::class, ExperimentalCoroutinesApi::class)
    val results: StateFlow<TransactionListModel?> = observeTransactionList(
        _query.debounce(200.milliseconds).map { TransactionFilter(PeriodChoice.AllTime, query = it) },
    ).stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    fun onQueryChange(text: String) {
        _query.value = text
    }

    private companion object {
        const val ALL_TIME_DAYS = 365 * 30
    }
}

@Composable
fun SearchRoute(onBack: () -> Unit, onOpenTransaction: (TransactionId) -> Unit, viewModel: SearchViewModel = hiltViewModel()) {
    val query by viewModel.query.collectAsStateWithLifecycle()
    val results by viewModel.results.collectAsStateWithLifecycle()
    val colors = ArcaDesignSystem.colors
    val spacing = ArcaDesignSystem.spacing
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { focus.requestFocus() }

    Column(
        Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .imePadding(),
    ) {
        // The field takes the title's place (board 16).
        Row(
            Modifier.padding(start = spacing.screenMargin - 8.dp, end = spacing.screenMargin, top = 12.dp, bottom = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            IconAction(ArcaIcons.back, stringResource(R.string.action_back), onBack)
            Row(
                Modifier
                    .weight(1f)
                    .height(44.dp)
                    .background(colors.surfaceSunken, ArcaDesignSystem.shapes.pill)
                    .padding(start = 14.dp, end = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(spacing.space2),
            ) {
                ArcaIcon(ArcaIcons.search, tint = colors.inkTertiary, size = 18.dp)
                BasicTextField(
                    value = query,
                    onValueChange = viewModel::onQueryChange,
                    singleLine = true,
                    textStyle = ArcaDesignSystem.typography.body.copy(color = colors.ink),
                    cursorBrush = SolidColor(colors.ink),
                    modifier = Modifier
                        .weight(1f)
                        .focusRequester(focus),
                    decorationBox = { inner ->
                        Box {
                            if (query.isEmpty()) Text(stringResource(R.string.search_placeholder), style = ArcaDesignSystem.typography.body, color = colors.inkTertiary)
                            inner()
                        }
                    },
                )
                if (query.isNotEmpty()) {
                    IconAction(ArcaIcons.close, stringResource(R.string.search_clear), { viewModel.onQueryChange("") }, size = 32.dp, tint = colors.inkSecondary)
                }
            }
        }
        Text(
            stringResource(R.string.search_hint),
            style = ArcaDesignSystem.typography.footnote,
            color = colors.inkTertiary,
            modifier = Modifier.padding(start = spacing.screenMargin, end = spacing.screenMargin, top = spacing.space3),
        )
        val model = results
        if (query.isNotBlank() && model != null) {
            val rows = model.days.flatMap { it.items }
            LazyColumn(contentPadding = PaddingValues(start = spacing.screenMargin, end = spacing.screenMargin, bottom = spacing.space5)) {
                item {
                    Text(
                        pluralStringResource(R.plurals.search_results, model.count, model.count, query),
                        style = ArcaDesignSystem.typography.subhead,
                        color = colors.inkSecondary,
                        modifier = Modifier.padding(top = spacing.space5, bottom = spacing.space2),
                    )
                }
                itemsIndexed(rows, key = { _, it -> it.transaction.id.value }) { i, item ->
                    if (i > 0) Hairline(startInset = 52.dp)
                    SearchResultRow(item, query) { onOpenTransaction(item.transaction.id) }
                }
            }
        }
    }
}

/** A result: the category, "Account · note" with the match marked, and the amount over its day. */
@Composable
private fun SearchResultRow(item: TransactionListItem, query: String, onClick: () -> Unit) {
    val colors = ArcaDesignSystem.colors
    val tx = item.transaction
    val text = transactionText(item)
    val marked = buildAnnotatedString {
        val subtitle = text.subtitle
        var from = 0
        val needle = query.trim()
        while (needle.isNotEmpty()) {
            val at = subtitle.indexOf(needle, from, ignoreCase = true)
            if (at < 0) break
            append(subtitle.substring(from, at))
            withStyle(SpanStyle(background = colors.surfaceSunken, color = colors.ink)) { append(subtitle.substring(at, at + needle.length)) }
            from = at + needle.length
        }
        append(subtitle.substring(from))
    }
    Row(
        Modifier
            .fillMaxWidth()
            .pressable(onClick, pressedScale = 1f)
            .padding(vertical = ArcaDesignSystem.spacing.space3),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(ArcaDesignSystem.spacing.space3),
    ) {
        IconTile(
            ArcaIcons.named(item.categoryIcon ?: "tag"),
            when (tx.type) {
                TransactionType.INCOME -> colors.income
                TransactionType.EXPENSE -> item.categoryColorHex?.let(colors::category) ?: colors.inkSecondary
                else -> colors.inkTertiary
            },
        )
        Column(Modifier.weight(1f)) {
            Text(text.title, style = ArcaDesignSystem.typography.headline, color = colors.ink, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(marked, style = ArcaDesignSystem.typography.footnote, color = colors.inkTertiary, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 1.dp))
        }
        Column(horizontalAlignment = Alignment.End) {
            Text(
                ArcaDesignSystem.money.transaction(tx.amountMinor, tx.type),
                style = ArcaDesignSystem.typography.amountRow,
                color = if (tx.type == TransactionType.INCOME) colors.income else colors.ink,
            )
            Text(
                dayLabel(tx.date.atZone(java.time.ZoneId.systemDefault()).toLocalDate()),
                style = ArcaDesignSystem.typography.footnote,
                color = colors.inkTertiary,
                modifier = Modifier.padding(top = 1.dp),
            )
        }
    }
}
