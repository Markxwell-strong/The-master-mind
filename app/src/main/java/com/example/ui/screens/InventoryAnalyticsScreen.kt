package com.example.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ReceiptLong
import androidx.compose.material.icons.automirrored.filled.TrendingUp
import androidx.compose.material.icons.filled.AccountBalanceWallet
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Inventory2
import androidx.compose.material.icons.filled.LocalShipping
import androidx.compose.material.icons.filled.Payments
import androidx.compose.material.icons.filled.People
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.TableChart
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.model.BusinessDebtRecord
import com.example.data.model.InventoryItem
import com.example.system.formatKsh
import com.example.system.formatKshSigned
import com.example.ui.components.GlassButton
import com.example.ui.components.GlassCard
import com.example.ui.components.PulseBarChart
import com.example.ui.components.PulseTrendSparkline
import com.example.ui.components.SectionHeaderRow
import com.example.ui.components.StatisticsCard
import com.example.ui.viewmodel.FocusPulseViewModel
import java.util.Locale

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun InventoryAnalyticsScreen(
    viewModel: FocusPulseViewModel,
    onBack: (() -> Unit)? = null,
    showHeader: Boolean = true
) {
    val inventory by viewModel.inventory.collectAsStateWithLifecycle()
    val reorders by viewModel.reorders.collectAsStateWithLifecycle()
    val businessAccount by viewModel.businessAccount.collectAsStateWithLifecycle()
    val businessLedger by viewModel.businessLedger.collectAsStateWithLifecycle()
    val businessDebts by viewModel.businessDebts.collectAsStateWithLifecycle()
    val summary by viewModel.businessSummary.collectAsStateWithLifecycle()

    var activeSection by remember { mutableIntStateOf(0) } // 0=Stock & Worth, 1=Cash & Expenses, 2=Debtors & Creditors, 3=Ledger
    var filterMode by remember { mutableIntStateOf(0) } // 0 = All Goods, 1 = Low Stock, 2 = Priced Margin Only
    var searchQuery by remember { mutableStateOf("") }

    var showAddSku by remember { mutableStateOf(false) }
    var editingItem by remember { mutableStateOf<InventoryItem?>(null) }
    var showAddFundsDialog by remember { mutableStateOf(false) }
    var showAddExpenseDialog by remember { mutableStateOf(false) }
    var addDebtInitialType by remember { mutableStateOf<String?>(null) }
    var settlingDebt by remember { mutableStateOf<BusinessDebtRecord?>(null) }

    val lowStock = remember(inventory) { inventory.filter { it.isLowStock } }
    val totalUnitsInStock = remember(inventory) { inventory.sumOf { it.stockQty } }
    val totalValuation = remember(inventory) { inventory.sumOf { it.totalValuation } }
    val retailValue = remember(inventory) { inventory.sumOf { it.retailValuation } }
    val totalRevenue = remember(inventory) { inventory.sumOf { it.totalRevenue } }
    val totalCogs = remember(inventory) { inventory.sumOf { it.totalCogs } }
    val grossProfit = remember(inventory) { inventory.sumOf { it.grossProfit } }

    val pricedItems = remember(inventory) { inventory.filter { it.hasBuyingAndSellingPrice } }
    val pricedRetailValue = remember(pricedItems) { pricedItems.sumOf { it.retailValuation } }
    val expectedStockProfit = remember(pricedItems) { pricedItems.sumOf { it.expectedStockProfit } }
    val weightedMarginPercent = remember(pricedRetailValue, expectedStockProfit) {
        if (pricedRetailValue > 0.0) (expectedStockProfit / pricedRetailValue) * 100.0 else 0.0
    }
    val realizedMarginPercent = remember(totalRevenue, grossProfit) {
        if (totalRevenue > 0.0) (grossProfit / totalRevenue) * 100.0 else 0.0
    }
    val reorderSpend = remember(reorders) { reorders.sumOf { it.totalCost } }

    val filteredInventory = remember(inventory, filterMode, searchQuery) {
        val q = searchQuery.trim().lowercase(Locale.US)
        inventory.filter { item ->
            val matchesMode = when (filterMode) {
                1 -> item.isLowStock
                2 -> item.hasBuyingAndSellingPrice
                else -> true
            }
            val matchesQuery = q.isEmpty() ||
                item.name.lowercase(Locale.US).contains(q) ||
                item.sku.lowercase(Locale.US).contains(q) ||
                item.category.lowercase(Locale.US).contains(q) ||
                item.supplierName.lowercase(Locale.US).contains(q)
            matchesMode && matchesQuery
        }
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (showHeader && onBack != null) {
                    IconButton(
                        onClick = onBack,
                        modifier = Modifier.testTag("business_back_button")
                    ) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = if (showHeader) "Business & Stock Hub" else "Available Stock & Margin Table",
                        style = if (showHeader) MaterialTheme.typography.headlineSmall else MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "${inventory.size} goods • $totalUnitsInStock units • Worth ${formatKsh(summary.businessWorth, 0)}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Button(
                    onClick = { showAddSku = true },
                    modifier = Modifier.testTag("add_inventory_button")
                ) {
                    Icon(Icons.Default.Add, null, Modifier.size(16.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("Add Stock")
                }
            }
        }

        // First-Time Business Setup Banner when uninitialized
        if (!businessAccount.isInitialized) {
            item {
                FirstTimeBusinessSetupCard(
                    onInitialize = { name, openingCash ->
                        viewModel.initializeBusinessAccount(name, openingCash)
                    }
                )
            }
        }

        // Excessive Loan Warning Banner whenever debtors > cash
        if (summary.showLoanWarning) {
            item {
                ExcessiveLoanWarningCard(summary = summary)
            }
        }

        // Owner-Private Business Worth & Profit/Loss Banner
        if (businessAccount.isInitialized || businessLedger.isNotEmpty() || businessDebts.isNotEmpty()) {
            item {
                BusinessWorthBanner(
                    account = businessAccount,
                    summary = summary,
                    onAddFundsClick = { showAddFundsDialog = true },
                    onAddExpenseClick = { showAddExpenseDialog = true }
                )
            }
        }

        // Sub-section Switcher Chips
        item {
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                FilterChip(
                    selected = activeSection == 0,
                    onClick = { activeSection = 0 },
                    label = { Text("Stock & Valuation") },
                    leadingIcon = { Icon(Icons.Default.TableChart, null, Modifier.size(16.dp)) },
                    modifier = Modifier.testTag("business_tab_stock")
                )
                FilterChip(
                    selected = activeSection == 1,
                    onClick = { activeSection = 1 },
                    label = { Text("Cash & Expenses") },
                    leadingIcon = { Icon(Icons.Default.Payments, null, Modifier.size(16.dp)) },
                    modifier = Modifier.testTag("business_tab_expenses")
                )
                FilterChip(
                    selected = activeSection == 2,
                    onClick = { activeSection = 2 },
                    label = { Text("Debtors & Creditors (${businessDebts.size})") },
                    leadingIcon = { Icon(Icons.Default.People, null, Modifier.size(16.dp)) },
                    modifier = Modifier.testTag("business_tab_debts")
                )
                FilterChip(
                    selected = activeSection == 3,
                    onClick = { activeSection = 3 },
                    label = { Text("Records (${businessLedger.size})") },
                    leadingIcon = { Icon(Icons.AutoMirrored.Filled.ReceiptLong, null, Modifier.size(16.dp)) },
                    modifier = Modifier.testTag("business_tab_ledger")
                )
            }
        }

        when (activeSection) {
            1 -> {
                item {
                    CashAndExpensesSection(
                        summary = summary,
                        ledger = businessLedger,
                        onAddFundsClick = { showAddFundsDialog = true },
                        onAddExpenseClick = { showAddExpenseDialog = true },
                        onDeleteEntry = { viewModel.deleteLedgerEntry(it) }
                    )
                }
            }

            2 -> {
                item {
                    DebtorsAndCreditorsSection(
                        debts = businessDebts,
                        summary = summary,
                        onAddDebtClick = { type -> addDebtInitialType = type },
                        onSettleRecord = { rec -> settlingDebt = rec },
                        onDeleteRecord = { id -> viewModel.deleteDebtRecord(id) }
                    )
                }
            }

            3 -> {
                item {
                    FinancialLedgerSection(
                        ledger = businessLedger,
                        onDeleteEntry = { id -> viewModel.deleteLedgerEntry(id) }
                    )
                }
            }

            else -> {
                // 1. Live Stock & Profit Margin KPI Row (KSh)
                item {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        StatisticsCard(
                            label = "Available Stock",
                            value = "$totalUnitsInStock units",
                            delta = "${inventory.size} goods (${lowStock.size} low)",
                            icon = Icons.Default.Inventory2,
                            modifier = Modifier.weight(1f)
                        )
                        StatisticsCard(
                            label = "Profit Margin",
                            value = if (pricedItems.isNotEmpty()) "${String.format(Locale.US, "%.1f", weightedMarginPercent)}%" else "—",
                            delta = if (pricedItems.isNotEmpty()) {
                                "Est. ${formatKsh(expectedStockProfit, 0)}"
                            } else {
                                "Add buy & sell prices"
                            },
                            icon = Icons.AutoMirrored.Filled.TrendingUp,
                            accentColor = MaterialTheme.colorScheme.secondary,
                            modifier = Modifier.weight(1f)
                        )
                    }
                }

                // 2. Financial Summary Card (Stock Valuation + Realized P&L in KSh)
                item {
                    GlassCard(accentBorder = true) {
                        SectionHeaderRow(
                            title = "Stock Valuation & Profit Summary",
                            subtitle = "${pricedItems.size} of ${inventory.size} items have both buying & selling prices",
                            icon = Icons.Default.AccountBalanceWallet
                        )
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            FinanceStat("Buying Value", formatKsh(totalValuation))
                            FinanceStat("Selling Value", formatKsh(retailValue))
                            FinanceStat(
                                "Stock Profit",
                                formatKsh(expectedStockProfit),
                                highlight = expectedStockProfit >= 0.0
                            )
                        }
                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f))
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            FinanceStat("Sales Revenue", formatKsh(totalRevenue))
                            FinanceStat("Sales COGS", formatKsh(totalCogs))
                            FinanceStat(
                                "Realized Margin",
                                if (totalRevenue > 0.0) "${String.format(Locale.US, "%.1f", realizedMarginPercent)}%" else "—",
                                highlight = grossProfit >= 0.0
                            )
                        }
                    }
                }

                // 3. Available Stock & Profit Margin Table (All Goods in One View)
                item {
                    GlassCard {
                        SectionHeaderRow(
                            title = "Available Stock & Goods Table",
                            subtitle = "Restocking (+1 / +5) automatically deducts cost from business cash in KSh",
                            icon = Icons.Default.TableChart
                        )

                        OutlinedTextField(
                            value = searchQuery,
                            onValueChange = { searchQuery = it },
                            placeholder = { Text("Filter by product name, SKU, category, or supplier...") },
                            leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                            singleLine = true,
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("stock_search_input")
                        )

                        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            FilterChip(
                                selected = filterMode == 0,
                                onClick = { filterMode = 0 },
                                label = { Text("All (${inventory.size})") }
                            )
                            FilterChip(
                                selected = filterMode == 1,
                                onClick = { filterMode = 1 },
                                label = { Text("Low Stock (${lowStock.size})") }
                            )
                            FilterChip(
                                selected = filterMode == 2,
                                onClick = { filterMode = 2 },
                                label = { Text("Priced (${pricedItems.size})") }
                            )
                        }

                        if (filteredInventory.isEmpty()) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 12.dp),
                                verticalArrangement = Arrangement.spacedBy(10.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Text(
                                    text = if (inventory.isEmpty()) {
                                        "No stock items yet. Add goods with optional buying & selling prices in KSh to auto-calculate profit margins."
                                    } else {
                                        "No items match your current filter."
                                    },
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    textAlign = TextAlign.Center
                                )
                                if (inventory.isEmpty()) {
                                    GlassButton(
                                        text = "Add First Stock Item",
                                        icon = Icons.Default.Add,
                                        onClick = { showAddSku = true },
                                        modifier = Modifier.fillMaxWidth()
                                    )
                                }
                            }
                        } else {
                            StockInventoryTable(
                                items = filteredInventory,
                                onSellOne = { viewModel.adjustInventoryStock(it, -1L, recordSaleIfNegative = true) },
                                onAddOne = { viewModel.adjustInventoryStock(it, +1L) },
                                onAddFive = { viewModel.adjustInventoryStock(it, +5L) },
                                onEdit = { editingItem = it },
                                onDelete = { viewModel.deleteInventoryItem(it.id) }
                            )
                        }
                    }
                }

                // 4. Low-Stock Alerts & Automated Supplier Reorders
                item {
                    GlassCard(accentBorder = lowStock.isNotEmpty()) {
                        SectionHeaderRow(
                            title = "Low-Stock Alerts & Auto-Reorders",
                            subtitle = "${lowStock.size} low-stock alerts • ${reorders.count { it.status != "received" }} active supplier orders",
                            icon = if (lowStock.isNotEmpty()) Icons.Default.Warning else Icons.Default.LocalShipping
                        )

                        if (lowStock.isNotEmpty()) {
                            lowStock.forEach { item ->
                                Text(
                                    text = "• ${item.name} (${item.sku}): ${item.stockQty} left (alert <= ${item.lowStockThreshold})",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.error,
                                    fontWeight = FontWeight.Medium
                                )
                            }
                            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
                        }

                        if (reorders.isEmpty()) {
                            Text(
                                text = "No supplier reorders queued. Receiving POs automatically deducts cost from business cash.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        } else {
                            Text(
                                text = "Total Reorder Spend: ${formatKsh(reorderSpend)}",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.secondary,
                                fontWeight = FontWeight.SemiBold
                            )
                            reorders.take(6).forEach { order ->
                                Surface(
                                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.06f),
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
                                            Text("${order.itemName} (+${order.quantity})", fontWeight = FontWeight.Bold)
                                            Text(
                                                "${order.supplierName} • ${formatKsh(order.totalCost)} • ${order.status.uppercase()}",
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                        if (order.status != "received") {
                                            Button(onClick = { viewModel.receiveReorder(order) }) {
                                                Text("Receive")
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                // 5. Stock Valuation & Profit Charts
                if (inventory.isNotEmpty()) {
                    item {
                        GlassCard {
                            SectionHeaderRow(
                                title = "Stock Value & Profit Charts",
                                subtitle = "Top goods by retail valuation and profit velocity (KSh)",
                                icon = Icons.AutoMirrored.Filled.TrendingUp
                            )
                            val top = inventory.sortedByDescending { it.retailValuation.coerceAtLeast(it.totalValuation) }.take(6)
                            PulseBarChart(
                                values = top.map { it.retailValuation.coerceAtLeast(it.totalValuation).toFloat().coerceAtLeast(0f) },
                                labels = top.map { it.sku.ifBlank { it.name.take(6) } }
                            )
                            Spacer(Modifier.height(6.dp))
                            Text("Expected Stock Profit by SKU", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold)
                            PulseTrendSparkline(
                                points = inventory.take(8).map {
                                    (if (it.hasBuyingAndSellingPrice) it.expectedStockProfit else it.grossProfit)
                                        .toFloat()
                                        .coerceAtLeast(0f)
                                }
                            )
                        }
                    }
                }
            }
        }

        item { Spacer(Modifier.height(72.dp)) }
    }

    if (showAddSku) {
        InventoryFormDialog(
            existing = null,
            businessInitialized = businessAccount.isInitialized,
            onDismiss = { showAddSku = false },
            onConfirm = { item, deductCash ->
                viewModel.saveInventoryItem(item, deductCashForAddedStock = deductCash)
                showAddSku = false
            }
        )
    }

    val currentEdit = editingItem
    if (currentEdit != null) {
        InventoryFormDialog(
            existing = currentEdit,
            businessInitialized = businessAccount.isInitialized,
            onDismiss = { editingItem = null },
            onConfirm = { updated, deductCash ->
                viewModel.saveInventoryItem(updated, deductCashForAddedStock = deductCash)
                editingItem = null
            }
        )
    }

    if (showAddFundsDialog) {
        AddFundsDialog(
            onDismiss = { showAddFundsDialog = false },
            onConfirm = { amount, note ->
                viewModel.addBusinessCash(amount, note)
                showAddFundsDialog = false
            }
        )
    }

    if (showAddExpenseDialog) {
        AddExpenseDialog(
            onDismiss = { showAddExpenseDialog = false },
            onConfirm = { title, cat, amount ->
                viewModel.recordBusinessExpense(title, cat, amount)
                showAddExpenseDialog = false
            }
        )
    }

    val debtTypeDraft = addDebtInitialType
    if (debtTypeDraft != null) {
        AddDebtDialog(
            initialType = debtTypeDraft,
            onDismiss = { addDebtInitialType = null },
            onConfirm = { rec ->
                viewModel.saveDebtRecord(rec)
                addDebtInitialType = null
            }
        )
    }

    val activeSettle = settlingDebt
    if (activeSettle != null) {
        SettleDebtDialog(
            record = activeSettle,
            onDismiss = { settlingDebt = null },
            onConfirm = { pay ->
                viewModel.settleDebtRecord(activeSettle, pay)
                settlingDebt = null
            }
        )
    }
}

@Composable
private fun StockInventoryTable(
    items: List<InventoryItem>,
    onSellOne: (InventoryItem) -> Unit,
    onAddOne: (InventoryItem) -> Unit,
    onAddFive: (InventoryItem) -> Unit,
    onEdit: (InventoryItem) -> Unit,
    onDelete: (InventoryItem) -> Unit
) {
    val scrollState = rememberScrollState()

    val colProduct: Dp = 154.dp
    val colStock: Dp = 148.dp
    val colBuy: Dp = 98.dp
    val colSell: Dp = 98.dp
    val colProfitUnit: Dp = 104.dp
    val colMargin: Dp = 86.dp
    val colStockProfit: Dp = 110.dp
    val colSoldActions: Dp = 128.dp

    val totalUnits = items.sumOf { it.stockQty }
    val totalBuyVal = items.sumOf { it.totalValuation }
    val totalSellVal = items.sumOf { it.retailValuation }
    val totalExpProfit = items.filter { it.hasBuyingAndSellingPrice }.sumOf { it.expectedStockProfit }
    val pricedRetail = items.filter { it.hasBuyingAndSellingPrice }.sumOf { it.retailValuation }
    val totalMarginPct = if (pricedRetail > 0.0) (totalExpProfit / pricedRetail) * 100.0 else 0.0
    val totalSold = items.sumOf { it.unitsSold }

    Surface(
        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.04f),
        shape = RoundedCornerShape(14.dp),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.6f)),
        modifier = Modifier
            .fillMaxWidth()
            .testTag("stock_inventory_table")
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(scrollState)
        ) {
            Row(
                modifier = Modifier
                    .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.16f))
                    .padding(horizontal = 10.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                TableHeaderCell("Product / SKU", colProduct)
                TableHeaderCell("Available Stock", colStock)
                TableHeaderCell("Buying (KSh)", colBuy, alignEnd = true)
                TableHeaderCell("Selling (KSh)", colSell, alignEnd = true)
                TableHeaderCell("Profit/Unit", colProfitUnit, alignEnd = true)
                TableHeaderCell("Margin %", colMargin, alignEnd = true)
                TableHeaderCell("Stock Profit", colStockProfit, alignEnd = true)
                TableHeaderCell("Sold & Actions", colSoldActions, alignEnd = true)
            }

            HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.55f))

            items.forEachIndexed { index, item ->
                val rowBg = if (index % 2 == 1) {
                    MaterialTheme.colorScheme.onSurface.copy(alpha = 0.03f)
                } else {
                    Color.Transparent
                }
                val hasPrices = item.hasBuyingAndSellingPrice
                val profitUnit = item.profitPerUnit
                val marginPct = item.profitMarginPercent
                val stockProfit = item.expectedStockProfit

                Row(
                    modifier = Modifier
                        .background(rowBg)
                        .clickable { onEdit(item) }
                        .padding(horizontal = 10.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.width(colProduct)) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Text(
                                text = item.name,
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Bold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f, fill = false)
                            )
                            if (item.isLowStock) {
                                Surface(
                                    color = MaterialTheme.colorScheme.errorContainer,
                                    shape = RoundedCornerShape(4.dp)
                                ) {
                                    Text(
                                        text = "LOW",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onErrorContainer,
                                        fontWeight = FontWeight.Bold,
                                        modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
                                    )
                                }
                            }
                        }
                        Text(
                            text = "${item.sku} • ${item.category}",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }

                    Column(
                        modifier = Modifier.width(colStock),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Text(
                            text = "${item.stockQty} units",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            color = if (item.isLowStock) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            MiniStockActionChip(
                                label = "-1",
                                enabled = item.stockQty > 0L,
                                onClick = { onSellOne(item) }
                            )
                            MiniStockActionChip(
                                label = "+1",
                                enabled = true,
                                onClick = { onAddOne(item) }
                            )
                            MiniStockActionChip(
                                label = "+5",
                                enabled = true,
                                onClick = { onAddFive(item) }
                            )
                        }
                    }

                    TableValueCell(
                        text = if (item.unitCost > 0.0) formatKsh(item.unitCost) else "—",
                        width = colBuy,
                        muted = item.unitCost <= 0.0
                    )

                    TableValueCell(
                        text = if (item.unitPrice > 0.0) formatKsh(item.unitPrice) else "—",
                        width = colSell,
                        muted = item.unitPrice <= 0.0
                    )

                    val profitColor = when {
                        !hasPrices -> MaterialTheme.colorScheme.onSurfaceVariant
                        profitUnit >= 0.0 -> MaterialTheme.colorScheme.primary
                        else -> MaterialTheme.colorScheme.error
                    }
                    TableValueCell(
                        text = if (hasPrices) formatKshSigned(profitUnit) else "—",
                        width = colProfitUnit,
                        color = profitColor,
                        bold = hasPrices
                    )

                    Box(
                        modifier = Modifier.width(colMargin),
                        contentAlignment = Alignment.CenterEnd
                    ) {
                        if (hasPrices) {
                            val badgeColor = if (marginPct >= 0.0) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error
                            Surface(
                                color = badgeColor.copy(alpha = 0.18f),
                                shape = RoundedCornerShape(999.dp)
                            ) {
                                Text(
                                    text = "${String.format(Locale.US, "%.1f", marginPct)}%",
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = badgeColor,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                                )
                            }
                        } else {
                            Text(
                                text = "—",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    TableValueCell(
                        text = if (hasPrices) formatKsh(stockProfit) else "—",
                        width = colStockProfit,
                        color = if (!hasPrices) MaterialTheme.colorScheme.onSurfaceVariant else if (stockProfit >= 0.0) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.error,
                        bold = hasPrices
                    )

                    Row(
                        modifier = Modifier.width(colSoldActions),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.End
                    ) {
                        Text(
                            text = "${item.unitsSold}",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(end = 4.dp)
                        )
                        IconButton(
                            onClick = { onEdit(item) },
                            modifier = Modifier
                                .size(36.dp)
                                .testTag("edit_inventory_${item.id}")
                        ) {
                            Icon(Icons.Default.Edit, contentDescription = "Edit ${item.name}", modifier = Modifier.size(18.dp))
                        }
                        IconButton(
                            onClick = { onDelete(item) },
                            modifier = Modifier
                                .size(36.dp)
                                .testTag("delete_inventory_${item.id}")
                        ) {
                            Icon(Icons.Default.Delete, contentDescription = "Delete ${item.name}", modifier = Modifier.size(18.dp))
                        }
                    }
                }
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f))
            }

            Row(
                modifier = Modifier
                    .background(MaterialTheme.colorScheme.secondary.copy(alpha = 0.14f))
                    .padding(horizontal = 10.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "TOTAL (${items.size})",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.width(colProduct)
                )
                Text(
                    text = "$totalUnits units",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.width(colStock)
                )
                TableValueCell(
                    text = formatKsh(totalBuyVal),
                    width = colBuy,
                    bold = true
                )
                TableValueCell(
                    text = formatKsh(totalSellVal),
                    width = colSell,
                    bold = true
                )
                TableValueCell(
                    text = "—",
                    width = colProfitUnit,
                    muted = true
                )
                TableValueCell(
                    text = if (pricedRetail > 0.0) "${String.format(Locale.US, "%.1f", totalMarginPct)}%" else "—",
                    width = colMargin,
                    color = MaterialTheme.colorScheme.primary,
                    bold = true
                )
                TableValueCell(
                    text = formatKsh(totalExpProfit),
                    width = colStockProfit,
                    color = MaterialTheme.colorScheme.secondary,
                    bold = true
                )
                TableValueCell(
                    text = "$totalSold sold",
                    width = colSoldActions,
                    bold = true
                )
            }
        }
    }
}

@Composable
private fun TableHeaderCell(
    label: String,
    width: Dp,
    alignEnd: Boolean = false
) {
    Text(
        text = label,
        style = MaterialTheme.typography.labelMedium,
        fontWeight = FontWeight.Bold,
        color = MaterialTheme.colorScheme.onSurface,
        textAlign = if (alignEnd) TextAlign.End else TextAlign.Start,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        modifier = Modifier
            .width(width)
            .padding(horizontal = 4.dp)
    )
}

@Composable
private fun TableValueCell(
    text: String,
    width: Dp,
    color: Color = MaterialTheme.colorScheme.onSurface,
    bold: Boolean = false,
    muted: Boolean = false
) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodySmall,
        fontWeight = if (bold) FontWeight.Bold else FontWeight.Medium,
        color = if (muted) MaterialTheme.colorScheme.onSurfaceVariant else color,
        textAlign = TextAlign.End,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        modifier = Modifier
            .width(width)
            .padding(horizontal = 4.dp)
    )
}

@Composable
private fun MiniStockActionChip(
    label: String,
    enabled: Boolean,
    onClick: () -> Unit
) {
    Surface(
        color = if (enabled) MaterialTheme.colorScheme.primary.copy(alpha = 0.18f)
        else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.06f),
        shape = RoundedCornerShape(8.dp),
        modifier = Modifier
            .clip(RoundedCornerShape(8.dp))
            .clickable(enabled = enabled, onClick = onClick)
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Bold,
            color = if (enabled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 5.dp)
        )
    }
}

@Composable
private fun FinanceStat(
    label: String,
    value: String,
    highlight: Boolean = false
) {
    Column {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            text = value,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = if (highlight) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
        )
    }
}

@Composable
private fun InventoryFormDialog(
    existing: InventoryItem?,
    businessInitialized: Boolean,
    onDismiss: () -> Unit,
    onConfirm: (InventoryItem, deductCashFromBusiness: Boolean) -> Unit
) {
    val isEdit = existing != null
    var sku by remember { mutableStateOf(existing?.sku ?: "SKU-${(100..999).random()}") }
    var name by remember { mutableStateOf(existing?.name ?: "") }
    var category by remember { mutableStateOf(existing?.category ?: "Retail") }
    var stockQty by remember { mutableStateOf((existing?.stockQty ?: 12L).toString()) }
    var threshold by remember { mutableStateOf((existing?.lowStockThreshold ?: 5L).toString()) }
    var reorderQty by remember { mutableStateOf((existing?.reorderQty ?: 20L).toString()) }
    var buyingPrice by remember {
        mutableStateOf(
            if (existing != null && existing.unitCost > 0.0) existing.unitCost.toString() else ""
        )
    }
    var sellingPrice by remember {
        mutableStateOf(
            if (existing != null && existing.unitPrice > 0.0) existing.unitPrice.toString() else ""
        )
    }
    var supplier by remember { mutableStateOf(existing?.supplierName ?: "Direct Wholesale") }
    var autoReorder by remember { mutableStateOf(existing?.autoReorder ?: true) }
    var deductRestockFromCash by remember { mutableStateOf(businessInitialized) }

    val draftBuy = buyingPrice.toDoubleOrNull() ?: 0.0
    val draftSell = sellingPrice.toDoubleOrNull() ?: 0.0
    val draftQty = stockQty.toLongOrNull() ?: 0L
    val hasLivePricing = draftBuy > 0.0 && draftSell > 0.0
    val liveProfitPerUnit = draftSell - draftBuy
    val liveMarginPct = if (hasLivePricing) ((draftSell - draftBuy) / draftSell) * 100.0 else 0.0
    val liveMarkupPct = if (hasLivePricing) ((draftSell - draftBuy) / draftBuy) * 100.0 else 0.0
    val liveStockProfit = draftQty * liveProfitPerUnit

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (isEdit) "Update Stock Item" else "Add Stock Goods") },
        text = {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                item {
                    OutlinedTextField(
                        value = name,
                        onValueChange = { name = it },
                        label = { Text("Product / Goods Name") },
                        singleLine = true,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("inventory_name_input")
                    )
                }
                item {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(
                            value = sku,
                            onValueChange = { sku = it },
                            label = { Text("SKU") },
                            modifier = Modifier.weight(1f),
                            singleLine = true
                        )
                        OutlinedTextField(
                            value = category,
                            onValueChange = { category = it },
                            label = { Text("Category") },
                            modifier = Modifier.weight(1f),
                            singleLine = true
                        )
                    }
                }
                item {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(
                            value = stockQty,
                            onValueChange = { stockQty = it.filter { c -> c.isDigit() } },
                            label = { Text("Available Qty") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            modifier = Modifier.weight(1f),
                            singleLine = true
                        )
                        OutlinedTextField(
                            value = threshold,
                            onValueChange = { threshold = it.filter { c -> c.isDigit() } },
                            label = { Text("Low Alert") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            modifier = Modifier.weight(1f),
                            singleLine = true
                        )
                    }
                }
                item {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(
                            value = buyingPrice,
                            onValueChange = { buyingPrice = filterDecimalInput(it) },
                            label = { Text("Buying (KSh)") },
                            placeholder = { Text("0.00") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                            modifier = Modifier
                                .weight(1f)
                                .testTag("inventory_buying_input"),
                            singleLine = true
                        )
                        OutlinedTextField(
                            value = sellingPrice,
                            onValueChange = { sellingPrice = filterDecimalInput(it) },
                            label = { Text("Selling (KSh)") },
                            placeholder = { Text("0.00") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                            modifier = Modifier
                                .weight(1f)
                                .testTag("inventory_selling_input"),
                            singleLine = true
                        )
                    }
                }

                if (hasLivePricing) {
                    item {
                        val isPositive = liveProfitPerUnit >= 0.0
                        Surface(
                            color = (if (isPositive) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error)
                                .copy(alpha = 0.14f),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(
                                modifier = Modifier.padding(10.dp),
                                verticalArrangement = Arrangement.spacedBy(3.dp)
                            ) {
                                Text(
                                    text = "Calculated Profit Margin",
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = if (isPositive) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error
                                )
                                Text(
                                    text = "Profit/unit: ${formatKsh(liveProfitPerUnit)} • Margin: ${String.format(Locale.US, "%.1f", liveMarginPct)}% • Markup: ${String.format(Locale.US, "%.1f", liveMarkupPct)}%",
                                    style = MaterialTheme.typography.bodySmall,
                                    fontWeight = FontWeight.SemiBold
                                )
                                Text(
                                    text = "Est. Profit on $draftQty units: ${formatKsh(liveStockProfit)}",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }

                item {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(
                            value = reorderQty,
                            onValueChange = { reorderQty = it.filter { c -> c.isDigit() } },
                            label = { Text("Auto-Reorder Qty") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            modifier = Modifier.weight(1f),
                            singleLine = true
                        )
                        OutlinedTextField(
                            value = supplier,
                            onValueChange = { supplier = it },
                            label = { Text("Supplier") },
                            modifier = Modifier.weight(1f),
                            singleLine = true
                        )
                    }
                }
                item {
                    Row(
                        Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Enable Auto-Reorder")
                        Switch(checked = autoReorder, onCheckedChange = { autoReorder = it })
                    }
                }
                if (businessInitialized) {
                    item {
                        Row(
                            Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("Deduct added stock cost from cash", modifier = Modifier.weight(1f))
                            Switch(checked = deductRestockFromCash, onCheckedChange = { deductRestockFromCash = it })
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (sku.isNotBlank() && name.isNotBlank()) {
                        val parsedCost = (buyingPrice.toDoubleOrNull() ?: 0.0).coerceAtLeast(0.0)
                        val parsedPrice = (sellingPrice.toDoubleOrNull() ?: 0.0).coerceAtLeast(0.0)
                        onConfirm(
                            (existing ?: InventoryItem()).copy(
                                sku = sku.trim(),
                                name = name.trim(),
                                category = category.trim().ifBlank { "General" },
                                stockQty = stockQty.toLongOrNull() ?: 0L,
                                lowStockThreshold = threshold.toLongOrNull() ?: 5L,
                                reorderQty = (reorderQty.toLongOrNull() ?: 10L).coerceAtLeast(1L),
                                autoReorder = autoReorder,
                                unitCost = parsedCost,
                                unitPrice = parsedPrice,
                                supplierName = supplier.trim()
                            ),
                            deductRestockFromCash
                        )
                    }
                },
                modifier = Modifier.testTag("save_inventory_confirm_button")
            ) { Text(if (isEdit) "Update Stock" else "Save Stock") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}
