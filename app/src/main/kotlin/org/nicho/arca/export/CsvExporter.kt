package org.nicho.arca.export

import android.content.ContentValues
import android.content.Context
import android.provider.MediaStore
import dagger.hilt.android.qualifiers.ApplicationContext
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import org.nicho.arca.domain.repository.AccountReading
import org.nicho.arca.domain.repository.CategoryStore
import org.nicho.arca.domain.repository.TransactionReading
import org.nicho.arca.domain.time.PeriodClock

/** What an export wrote: how many rows, and the file's name in Downloads. */
data class ExportResult(val count: Int, val fileName: String)

/**
 * Reports' "Export CSV" (board 06): the chosen months' transactions as a CSV in Downloads, as a
 * backup the user owns. Columns: date, account, category, type, amount, note. Amounts are the
 * stored, always-positive minor units with the type beside them, so nothing is computed or
 * re-signed on the way out.
 *
 * Written through MediaStore, which needs no storage permission from Android 10 (minSdk 29).
 */
class CsvExporter @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val transactions: TransactionReading,
    private val accounts: AccountReading,
    private val categories: CategoryStore,
    private val clock: PeriodClock,
) {

    suspend fun export(from: YearMonth, to: YearMonth, fileName: String): ExportResult = withContext(Dispatchers.IO) {
        val start = clock.periodOf(from).start
        val end = clock.periodOf(to).endExclusive
        val rows = transactions.allForExport().filter { it.date >= start && it.date < end }
        val accountNames = accounts.observeAll(includeArchived = true).first().associate { it.id to it.name }
        val categoryNames = categories.observeAll(includeArchived = true).first().associate { it.id to it.name }
        val zone = clock.zone()
        val dateFormat = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")
        val csv = buildString {
            appendLine("date,account,category,type,amount,note")
            for (tx in rows) {
                appendLine(
                    listOf(
                        tx.date.atZone(zone).format(dateFormat),
                        accountNames[tx.accountId].orEmpty(),
                        tx.categoryId?.let(categoryNames::get).orEmpty(),
                        tx.type.code,
                        tx.amountMinor.toString(),
                        tx.note.orEmpty(),
                    ).joinToString(",") { field(it) },
                )
            }
        }
        write(fileName, csv.toByteArray(Charsets.UTF_8))
        ExportResult(rows.size, fileName)
    }

    private fun write(fileName: String, bytes: ByteArray) {
        val resolver = context.contentResolver
        val values = ContentValues().apply {
            put(MediaStore.Downloads.DISPLAY_NAME, fileName)
            put(MediaStore.Downloads.MIME_TYPE, "text/csv")
            put(MediaStore.Downloads.IS_PENDING, 1)
        }
        val uri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)
            ?: error("Couldn't create $fileName in Downloads")
        try {
            resolver.openOutputStream(uri)?.use { it.write(bytes) } ?: error("Couldn't open $fileName")
            values.clear()
            values.put(MediaStore.Downloads.IS_PENDING, 0)
            resolver.update(uri, values, null, null)
        } catch (e: Exception) {
            resolver.delete(uri, null, null)
            throw e
        }
    }

    /** RFC 4180: quote a field holding a comma, quote or line break; double the quotes inside. */
    private fun field(value: String): String =
        if (value.any { it == ',' || it == '"' || it == '\n' || it == '\r' }) "\"" + value.replace("\"", "\"\"") + "\"" else value
}
