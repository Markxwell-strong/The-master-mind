package com.example.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ReceiptLong
import androidx.compose.material.icons.filled.AccountBalance
import androidx.compose.material.icons.filled.AccountBalanceWallet
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Payments
import androidx.compose.material.icons.filled.People
import androidx.compose.material.icons.filled.TrendingDown
import androidx.compose.material.icons.filled.TrendingUp
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.data.model.BusinessAccountConfig
import com.example.data.model.BusinessDebtRecord
import com.example.data.model.BusinessFinancialSummary
import com.example.data.model.BusinessLedgerEntry
import com.example.system.formatKsh
import com.example.system.formatKshSigned
import com.example.ui.components.GlassButton
import com.example.ui.components.GlassCard
import com.example.ui.components.SectionHeaderRow
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

internal fun filterDecimalInput(input: String): String {
    var dotSeen = false
    return buildString {
        for (ch in input) {
            if (ch.isDigit()) append(ch)
            else if (ch == '.' && !dotSeen) {
                append(ch)
                dotSeen = true
            }
        }
    }
}

@Composable
fun FirstTimeBusinessSetupCard(
    onInitialize: (businessName: String, openingCash: Double) -> Unit
) {
    var businessName by remember { mutableStateOf("") }
    var openingCashText by remember { mutableStateOf("") }

    GlassCard(accentBorder = true) {
        SectionHeaderRow(
            title = "Set Up Business Account",
            subtitle = "Enter starting cash & business name to track live worth and profit/loss in KSh",
            icon = Icons.Default.AccountBalance
        )
        Surface(
            color = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f),
            shape = RoundedCornerShape(10.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                modifier = Modifier.padding(10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(Icons.Default.Lock, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(16.dp))
                Text(
                    text = "Only visible to you (Business Owner). Financial records are locked to your account.",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
        }
        OutlinedTextField(
            value = businessName,
            onValueChange = { businessName = it },
            label = { Text("Business Name") },
            placeholder = { Text("e.g. Master Mind Traders") },
            singleLine = true,
            modifier = Modifier
                .fillMaxWidth()
                .testTag("business_setup_name_input")
        )
        OutlinedTextField(
            value = openingCashText,
            onValueChange = { openingCashText = filterDecimalInput(it) },
            label = { Text("Opening Cash in Business (KSh)") },
            prefix = { Text("KSh ") },
            placeholder = { Text("0.00") },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
            singleLine = true,
            modifier = Modifier
                .fillMaxWidth()
                .testTag("business_setup_cash_input")
        )
        GlassButton(
            text = "Initialize Business Account",
            icon = Icons.Default.CheckCircle,
            enabled = businessName.trim().isNotBlank(),
            onClick = {
                val cash = (openingCashText.toDoubleOrNull() ?: 0.0).coerceAtLeast(0.0)
                onInitialize(businessName.trim(), cash)
            },
            modifier = Modifier
                .fillMaxWidth()
                .testTag("business_setup_submit_button")
        )
    }
}

@Composable
fun BusinessWorthBanner(
    account: BusinessAccountConfig,
    summary: BusinessFinancialSummary,
    onAddFundsClick: () -> Unit,
    onAddExpenseClick: () -> Unit
) {
    val isProfitable = !summary.isMakingLoss
    val profitColor = if (isProfitable) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error
    val startDateLabel = remember(account.startedAtMillis) {
        if (account.startedAtMillis > 0L) {
            SimpleDateFormat("dd MMM yyyy", Locale.getDefault()).format(Date(account.startedAtMillis))
        } else {
            "Not initialized"
        }
    }

    GlassCard(accentBorder = true) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = account.businessName.ifBlank { "Business Financial Hub" },
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "Owner Private • Started $startDateLabel",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Surface(
                color = profitColor.copy(alpha = 0.18f),
                shape = RoundedCornerShape(999.dp)
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Icon(
                        imageVector = if (isProfitable) Icons.Default.TrendingUp else Icons.Default.TrendingDown,
                        contentDescription = null,
                        tint = profitColor,
                        modifier = Modifier.size(15.dp)
                    )
                    Text(
                        text = if (isProfitable) "MAKING PROFIT" else "MAKING A LOSS",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = profitColor
                    )
                }
            }
        }

        Column {
            Text(
                text = "Current Business Worth",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = formatKsh(summary.businessWorth),
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.ExtraBold,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = "Worth = Cash (${formatKsh(summary.currentCash, 0)}) + Stock (${formatKsh(summary.stockCostValue, 0)}) + Debtors (${formatKsh(summary.totalDebtors, 0)}) - Creditors (${formatKsh(summary.totalCreditors, 0)})",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f))

        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            BusinessMetricCol("Available Cash", formatKsh(summary.currentCash), MaterialTheme.colorScheme.primary)
            BusinessMetricCol("Capital Invested", formatKsh(summary.totalInvestedCapital), MaterialTheme.colorScheme.secondary)
            BusinessMetricCol(
                label = if (summary.netProfitOrLoss >= 0.0) "Net Profit" else "Net Loss",
                value = formatKshSigned(summary.netProfitOrLoss),
                color = profitColor
            )
        }

        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            BusinessMetricCol("Expenses", formatKsh(summary.totalExpenses), MaterialTheme.colorScheme.tertiary)
            BusinessMetricCol("Money Outside", formatKsh(summary.totalDebtors), if (summary.showLoanWarning) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface)
            BusinessMetricCol("Creditors", formatKsh(summary.totalCreditors), MaterialTheme.colorScheme.onSurface)
        }

        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(
                onClick = onAddFundsClick,
                modifier = Modifier
                    .weight(1f)
                    .testTag("business_add_funds_button")
            ) {
                Icon(Icons.Default.Add, null, Modifier.size(16.dp))
                Spacer(Modifier.width(4.dp))
                Text("Add Cash")
            }
            OutlinedButton(
                onClick = onAddExpenseClick,
                modifier = Modifier
                    .weight(1f)
                    .testTag("business_add_expense_button")
            ) {
                Icon(Icons.Default.Payments, null, Modifier.size(16.dp))
                Spacer(Modifier.width(4.dp))
                Text("Add Expense")
            }
        }
    }
}

@Composable
fun ExcessiveLoanWarningCard(summary: BusinessFinancialSummary) {
    Surface(
        color = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.88f),
        shape = RoundedCornerShape(16.dp),
        border = BorderStroke(1.5.dp, MaterialTheme.colorScheme.error),
        modifier = Modifier
            .fillMaxWidth()
            .testTag("excessive_loan_warning_card")
    ) {
        Row(
            modifier = Modifier.padding(14.dp),
            verticalAlignment = Alignment.Top,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Icon(
                imageVector = Icons.Default.Warning,
                contentDescription = "Excessive loan warning",
                tint = MaterialTheme.colorScheme.error,
                modifier = Modifier.size(24.dp)
            )
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    text = "Warning: Stop Giving Goods on Loan!",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onErrorContainer
                )
                Text(
                    text = "Money outside with debtors (${formatKsh(summary.totalDebtors)}) is higher than your available business cash (${formatKsh(summary.currentCash)}). Pause credit sales and focus on debt collection.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onErrorContainer
                )
            }
        }
    }
}

@Composable
private fun BusinessMetricCol(
    label: String,
    value: String,
    color: androidx.compose.ui.graphics.Color
) {
    Column {
        Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold, color = color)
    }
}

@Composable
fun CashAndExpensesSection(
    summary: BusinessFinancialSummary,
    ledger: List<BusinessLedgerEntry>,
    onAddFundsClick: () -> Unit,
    onAddExpenseClick: () -> Unit,
    onDeleteEntry: (String) -> Unit
) {
    val expenses = remember(ledger) { ledger.filter { it.entryType == "expense" } }
    val fmt = remember { SimpleDateFormat("dd MMM, HH:mm", Locale.getDefault()) }

    GlassCard(accentBorder = true) {
        SectionHeaderRow(
            title = "Expenses Column & Cash Outflows",
            subtitle = "Total Expenses: ${formatKsh(summary.totalExpenses)} • Cash Balance: ${formatKsh(summary.currentCash)}",
            icon = Icons.Default.Payments,
            trailing = {
                Button(onClick = onAddExpenseClick) {
                    Icon(Icons.Default.Add, null, Modifier.size(16.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("Expense")
                }
            }
        )

        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            GlassButton(
                text = "Add Funds / Cash",
                icon = Icons.Default.AccountBalanceWallet,
                tonal = true,
                onClick = onAddFundsClick,
                modifier = Modifier.fillMaxWidth()
            )
        }

        if (expenses.isEmpty()) {
            Text(
                text = "No business expenses recorded yet. Tap + Expense to log rent, transport, utilities, wages, or supplies.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        } else {
            // Structured Expenses Column Table
            val colDesc = 150.dp
            val colCat = 96.dp
            val colAmt = 104.dp
            val colDate = 96.dp
            Surface(
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.04f),
                shape = RoundedCornerShape(12.dp),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.5f)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.horizontalScroll(rememberScrollState())) {
                    Row(
                        modifier = Modifier
                            .background(MaterialTheme.colorScheme.tertiary.copy(alpha = 0.16f))
                            .padding(horizontal = 10.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Expense Item", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.labelMedium, modifier = Modifier.width(colDesc))
                        Text("Category", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.labelMedium, modifier = Modifier.width(colCat))
                        Text("Expense (KSh)", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.labelMedium, textAlign = TextAlign.End, modifier = Modifier.width(colAmt))
                        Text("Date", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.labelMedium, textAlign = TextAlign.End, modifier = Modifier.width(colDate))
                        Spacer(Modifier.width(40.dp))
                    }
                    HorizontalDivider()
                    expenses.forEach { exp ->
                        Row(
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = exp.title,
                                style = MaterialTheme.typography.bodySmall,
                                fontWeight = FontWeight.SemiBold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.width(colDesc)
                            )
                            Text(
                                text = exp.category,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.width(colCat)
                            )
                            Text(
                                text = formatKsh(exp.amount),
                                style = MaterialTheme.typography.bodySmall,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.error,
                                textAlign = TextAlign.End,
                                modifier = Modifier.width(colAmt)
                            )
                            Text(
                                text = fmt.format(Date(exp.occurredAtMillis)),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                textAlign = TextAlign.End,
                                modifier = Modifier.width(colDate)
                            )
                            IconButton(
                                onClick = { onDeleteEntry(exp.id) },
                                modifier = Modifier.size(36.dp)
                            ) {
                                Icon(Icons.Default.Delete, contentDescription = "Delete expense", modifier = Modifier.size(16.dp))
                            }
                        }
                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))
                    }
                }
            }
        }
    }
}

@Composable
fun DebtorsAndCreditorsSection(
    debts: List<BusinessDebtRecord>,
    summary: BusinessFinancialSummary,
    onAddDebtClick: (defaultType: String) -> Unit,
    onSettleRecord: (BusinessDebtRecord) -> Unit,
    onDeleteRecord: (String) -> Unit
) {
    val debtors = remember(debts) { debts.filter { it.debtType != "creditor" } }
    val creditors = remember(debts) { debts.filter { it.debtType == "creditor" } }

    GlassCard {
        SectionHeaderRow(
            title = "Debtors & Creditors",
            subtitle = "Owed to business: ${formatKsh(summary.totalDebtors)} • Owed by business: ${formatKsh(summary.totalCreditors)}",
            icon = Icons.Default.People
        )

        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(
                onClick = { onAddDebtClick("debtor") },
                modifier = Modifier
                    .weight(1f)
                    .testTag("add_debtor_button")
            ) {
                Icon(Icons.Default.Add, null, Modifier.size(16.dp))
                Spacer(Modifier.width(4.dp))
                Text("Add Debtor")
            }
            OutlinedButton(
                onClick = { onAddDebtClick("creditor") },
                modifier = Modifier
                    .weight(1f)
                    .testTag("add_creditor_button")
            ) {
                Icon(Icons.Default.Add, null, Modifier.size(16.dp))
                Spacer(Modifier.width(4.dp))
                Text("Add Creditor")
            }
        }

        if (debts.isEmpty()) {
            Text(
                text = "No debtors or creditors logged. Track people who owe the business money (debtors) and suppliers/lenders the business owes (creditors).",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        } else {
            if (debtors.isNotEmpty()) {
                Text("Debtors (People Owing Business)", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                debtors.forEach { rec ->
                    DebtRecordRow(
                        record = rec,
                        isDebtor = true,
                        onSettle = { onSettleRecord(rec) },
                        onDelete = { onDeleteRecord(rec.id) }
                    )
                }
            }
            if (creditors.isNotEmpty()) {
                Spacer(Modifier.height(6.dp))
                Text("Creditors (Business Owes)", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.tertiary)
                creditors.forEach { rec ->
                    DebtRecordRow(
                        record = rec,
                        isDebtor = false,
                        onSettle = { onSettleRecord(rec) },
                        onDelete = { onDeleteRecord(rec.id) }
                    )
                }
            }
        }
    }
}

@Composable
private fun DebtRecordRow(
    record: BusinessDebtRecord,
    isDebtor: Boolean,
    onSettle: () -> Unit,
    onDelete: () -> Unit
) {
    Surface(
        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.05f),
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "${record.personName}${if (record.isSettled) " • SETTLED" else ""}",
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = if (isDebtor) {
                        "Owes ${formatKsh(record.remainingAmount)} (Total ${formatKsh(record.totalAmount)})"
                    } else {
                        "Payable ${formatKsh(record.remainingAmount)} (Total ${formatKsh(record.totalAmount)})"
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = if (record.isSettled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                )
                if (record.note.isNotBlank() || record.phone.isNotBlank()) {
                    Text(
                        text = listOf(record.phone, record.note).filter { it.isNotBlank() }.joinToString(" • "),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                if (!record.isSettled && record.remainingAmount > 0.0) {
                    OutlinedButton(onClick = onSettle) {
                        Text(if (isDebtor) "Collect" else "Pay")
                    }
                }
                IconButton(onClick = onDelete, modifier = Modifier.size(36.dp)) {
                    Icon(Icons.Default.Delete, contentDescription = "Delete record", modifier = Modifier.size(18.dp))
                }
            }
        }
    }
}

@Composable
fun FinancialLedgerSection(
    ledger: List<BusinessLedgerEntry>,
    onDeleteEntry: (String) -> Unit
) {
    val fmt = remember { SimpleDateFormat("dd MMM, HH:mm", Locale.getDefault()) }

    GlassCard {
        SectionHeaderRow(
            title = "Owner Financial Records Ledger",
            subtitle = "Complete timestamped history of cash, restocks, sales, debts & expenses",
            icon = Icons.AutoMirrored.Filled.ReceiptLong
        )
        if (ledger.isEmpty()) {
            Text(
                text = "No financial transactions recorded yet. Initialize business cash, add funds, restock, or record expenses to build your ledger.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        } else {
            ledger.forEach { entry ->
                val isPositive = entry.entryType in setOf("opening_balance", "capital_add", "sale_inflow", "debtor_payment")
                val amtColor = if (isPositive) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error
                Surface(
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.05f),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 9.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(entry.title, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text(
                                text = "${entry.entryType.replace('_', ' ').uppercase()} • ${fmt.format(Date(entry.occurredAtMillis))}",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(
                                text = "Balance after: ${formatKsh(entry.balanceAfter)}",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.secondary
                            )
                        }
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = formatKshSigned(if (isPositive) entry.amount else -entry.amount),
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold,
                                color = amtColor
                            )
                            IconButton(onClick = { onDeleteEntry(entry.id) }, modifier = Modifier.size(34.dp)) {
                                Icon(Icons.Default.Delete, contentDescription = "Delete ledger entry", modifier = Modifier.size(16.dp))
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun AddFundsDialog(
    onDismiss: () -> Unit,
    onConfirm: (amount: Double, note: String) -> Unit
) {
    var amountText by remember { mutableStateOf("") }
    var note by remember { mutableStateOf("Capital Injection") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Add Funds / Cash to Business") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = amountText,
                    onValueChange = { amountText = filterDecimalInput(it) },
                    label = { Text("Amount (KSh)") },
                    prefix = { Text("KSh ") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().testTag("add_funds_amount_input")
                )
                OutlinedTextField(
                    value = note,
                    onValueChange = { note = it },
                    label = { Text("Source / Note") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            val amt = amountText.toDoubleOrNull() ?: 0.0
            Button(
                onClick = { onConfirm(amt, note) },
                enabled = amt > 0.0,
                modifier = Modifier.testTag("confirm_add_funds_button")
            ) { Text("Add Cash") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun AddExpenseDialog(
    onDismiss: () -> Unit,
    onConfirm: (title: String, category: String, amount: Double) -> Unit
) {
    var title by remember { mutableStateOf("") }
    var category by remember { mutableStateOf("Transport") }
    var amountText by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Record Business Expense") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    label = { Text("Used on what expense?") },
                    placeholder = { Text("e.g. Delivery boda, Shop rent, Electricity") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().testTag("expense_title_input")
                )
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    listOf("Rent", "Transport", "Utilities", "Wages", "Supplies").forEach { cat ->
                        FilterChip(
                            selected = category == cat,
                            onClick = { category = cat },
                            label = { Text(cat) }
                        )
                    }
                }
                OutlinedTextField(
                    value = amountText,
                    onValueChange = { amountText = filterDecimalInput(it) },
                    label = { Text("Amount Used (KSh)") },
                    prefix = { Text("KSh ") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().testTag("expense_amount_input")
                )
            }
        },
        confirmButton = {
            val amt = amountText.toDoubleOrNull() ?: 0.0
            Button(
                onClick = { onConfirm(title, category, amt) },
                enabled = title.trim().isNotBlank() && amt > 0.0,
                modifier = Modifier.testTag("confirm_expense_button")
            ) { Text("Deduct Expense") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun AddDebtDialog(
    initialType: String,
    onDismiss: () -> Unit,
    onConfirm: (BusinessDebtRecord) -> Unit
) {
    var debtType by remember { mutableStateOf(initialType) }
    var personName by remember { mutableStateOf("") }
    var phone by remember { mutableStateOf("") }
    var amountText by remember { mutableStateOf("") }
    var note by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (debtType == "creditor") "Add Creditor" else "Add Debtor (Loan / Credit)") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(
                        selected = debtType == "debtor",
                        onClick = { debtType = "debtor" },
                        label = { Text("Debtor (Owes Us)") }
                    )
                    FilterChip(
                        selected = debtType == "creditor",
                        onClick = { debtType = "creditor" },
                        label = { Text("Creditor (We Owe)") }
                    )
                }
                OutlinedTextField(
                    value = personName,
                    onValueChange = { personName = it },
                    label = { Text("Person / Supplier Name") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().testTag("debt_person_input")
                )
                OutlinedTextField(
                    value = amountText,
                    onValueChange = { amountText = filterDecimalInput(it) },
                    label = { Text("Amount (KSh)") },
                    prefix = { Text("KSh ") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().testTag("debt_amount_input")
                )
                OutlinedTextField(
                    value = phone,
                    onValueChange = { phone = it },
                    label = { Text("Phone Number (optional)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = note,
                    onValueChange = { note = it },
                    label = { Text("Item / Reason Note (optional)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            val amt = amountText.toDoubleOrNull() ?: 0.0
            Button(
                onClick = {
                    onConfirm(
                        BusinessDebtRecord(
                            personName = personName.trim(),
                            phone = phone.trim(),
                            debtType = debtType,
                            totalAmount = amt,
                            note = note.trim()
                        )
                    )
                },
                enabled = personName.trim().isNotBlank() && amt > 0.0,
                modifier = Modifier.testTag("confirm_debt_button")
            ) { Text("Save Record") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}

@Composable
fun SettleDebtDialog(
    record: BusinessDebtRecord,
    onDismiss: () -> Unit,
    onConfirm: (Double) -> Unit
) {
    var paymentText by remember { mutableStateOf(String.format(Locale.US, "%.2f", record.remainingAmount)) }
    val isDebtor = record.debtType != "creditor"

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (isDebtor) "Collect Payment from ${record.personName}" else "Pay Creditor ${record.personName}") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = "Remaining balance: ${formatKsh(record.remainingAmount)}",
                    style = MaterialTheme.typography.bodySmall
                )
                OutlinedTextField(
                    value = paymentText,
                    onValueChange = { paymentText = filterDecimalInput(it) },
                    label = { Text("Payment Amount (KSh)") },
                    prefix = { Text("KSh ") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            val pay = (paymentText.toDoubleOrNull() ?: 0.0).coerceIn(0.0, record.remainingAmount)
            Button(onClick = { onConfirm(pay) }, enabled = pay > 0.0) {
                Text(if (isDebtor) "Record Collection" else "Record Payment")
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}
