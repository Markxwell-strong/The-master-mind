package com.example.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountBalanceWallet
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Inventory2
import androidx.compose.material.icons.filled.LocalShipping
import androidx.compose.material.icons.filled.ShoppingCart
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.model.InventoryItem
import com.example.data.model.UserWorkspaceConfig
import com.example.ui.components.MasterMindAdaptiveBoard
import com.example.ui.components.PulseBarChart
import com.example.ui.components.PulseCard
import com.example.ui.components.PulseTrendSparkline
import com.example.ui.components.SectionHeaderRow
import com.example.ui.viewmodel.FocusPulseViewModel
import com.example.ui.viewmodel.UiState

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun InventoryAnalyticsScreen(viewModel: FocusPulseViewModel) {
    val configState by viewModel.workspaceConfig.collectAsStateWithLifecycle()
    val inventory by viewModel.inventory.collectAsStateWithLifecycle()
    val reorders by viewModel.reorders.collectAsStateWithLifecycle()
    val config = (configState as? UiState.Success)?.data ?: UserWorkspaceConfig()

    var selectedTab by remember { mutableIntStateOf(0) } // 0 = Inventory, 1 = Financial Analytics, 2 = Auto-Reorders
    var showAddSku by remember { mutableStateOf(false) }
    var editingItem by remember { mutableStateOf<InventoryItem?>(null) }

    val lowStock = remember(inventory) { inventory.filter { it.isLowStock } }
    val totalValuation = remember(inventory) { inventory.sumOf { it.totalValuation } }
    val retailValue = remember(inventory) { inventory.sumOf { it.retailValuation } }
    val totalRevenue = remember(inventory) { inventory.sumOf { it.totalRevenue } }
    val totalCogs = remember(inventory) { inventory.sumOf { it.totalCogs } }
    val grossProfit = remember(inventory) { inventory.sumOf { it.grossProfit } }
    val reorderSpend = remember(reorders) { reorders.sumOf { it.totalCost } }

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
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Shop Ops & Financials",
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "${inventory.size} SKUs tracked • ${lowStock.size} low stock",
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
                    Text("Add SKU")
                }
            }
        }

        item {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(
                    selected = selectedTab == 0,
                    onClick = { selectedTab = 0 },
                    label = { Text("Inventory List") },
                    leadingIcon = { Icon(Icons.Default.Inventory2, null, Modifier.size(16.dp)) }
                )
                FilterChip(
                    selected = selectedTab == 1,
                    onClick = { selectedTab = 1 },
                    label = { Text("Analytics & P&L") },
                    leadingIcon = { Icon(Icons.Default.AccountBalanceWallet, null, Modifier.size(16.dp)) }
                )
                FilterChip(
                    selected = selectedTab == 2,
                    onClick = { selectedTab = 2 },
                    label = { Text("Auto-Reorders") },
                    leadingIcon = { Icon(Icons.Default.LocalShipping, null, Modifier.size(16.dp)) }
                )
            }
        }

        if (lowStock.isNotEmpty() && selectedTab == 0) {
            item {
                PulseCard(accentBorder = true) {
                    SectionHeaderRow(
                        title = "Low-Stock Reorder Alerts",
                        subtitle = "${lowStock.size} items at or below reorder threshold",
                        icon = Icons.Default.Warning
                    )
                    lowStock.forEach { item ->
                        Text(
                            text = "• ${item.name} (${item.sku}): ${item.stockQty} left (threshold ${item.lowStockThreshold})",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error
                        )
                    }
                }
            }
        }

        when (selectedTab) {
            0 -> {
                if (inventory.isEmpty()) {
                    item {
                        PulseCard {
                            SectionHeaderRow(
                                title = "Track Shop & Business Stock",
                                subtitle = "Add products with optional buying & selling prices and low-stock alerts.",
                                icon = Icons.Default.Inventory2
                            )
                            OutlinedButton(
                                onClick = { showAddSku = true },
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text("Add First Product SKU")
                            }
                        }
                    }
                } else {
                    items(inventory, key = { it.id }) { item ->
                        InventoryListRowCard(
                            item = item,
                            onSellOne = { viewModel.adjustInventoryStock(item, -1L, recordSaleIfNegative = true) },
                            onAddOne = { viewModel.adjustInventoryStock(item, +1L) },
                            onAddFive = { viewModel.adjustInventoryStock(item, +5L) },
                            onEdit = { editingItem = item },
                            onDelete = { viewModel.deleteInventoryItem(item.id) }
                        )
                    }
                }
            }

            1 -> {
                val analyticsBlocks: List<@Composable (Modifier) -> Unit> = listOf(
                    { mod ->
                        PulseCard(modifier = mod, accentBorder = true) {
                            SectionHeaderRow(
                                title = "Cloud Financial & Profit Pulse",
                                subtitle = "Real-time cross-device valuation & margin telemetry",
                                icon = Icons.Default.AccountBalanceWallet
                            )
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                FinanceStat("Revenue", "$${String.format("%.2f", totalRevenue)}")
                                FinanceStat("COGS", "$${String.format("%.2f", totalCogs)}")
                                FinanceStat("Gross Profit", "$${String.format("%.2f", grossProfit)}")
                            }
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                FinanceStat("Stock Cost Value", "$${String.format("%.2f", totalValuation)}")
                                FinanceStat("Retail Value", "$${String.format("%.2f", retailValue)}")
                                FinanceStat("Reorder Spend", "$${String.format("%.2f", reorderSpend)}")
                            }
                        }
                    },
                    { mod ->
                        PulseCard(modifier = mod) {
                            Text("Stock Valuation by SKU", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                            val top = inventory.sortedByDescending { it.retailValuation }.take(6)
                            PulseBarChart(
                                values = top.map { it.retailValuation.toFloat().coerceAtLeast(0f) },
                                labels = top.map { it.sku }
                            )
                            Spacer(Modifier.size(8.dp))
                            Text("Gross Profit Velocity", style = MaterialTheme.typography.titleSmall)
                            PulseTrendSparkline(
                                points = inventory.takeLast(6).map { it.grossProfit.toFloat().coerceAtLeast(0f) }
                            )
                        }
                    }
                )
                item {
                    MasterMindAdaptiveBoard(
                        layoutStyle = config.appLayoutStyle,
                        blocks = analyticsBlocks
                    )
                }
            }

            else -> {
                if (reorders.isEmpty()) {
                    item {
                        PulseCard {
                            Text("No automated or manual reorders yet. When any SKU with Auto-Reorder ON drops to or below its threshold, THE MASTER MIND queues a supplier PO automatically.")
                        }
                    }
                } else {
                    items(reorders, key = { it.id }) { order ->
                        PulseCard(accentBorder = order.status != "received") {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text("${order.itemName} (+${order.quantity})", fontWeight = FontWeight.Bold)
                                    Text(
                                        "${order.supplierName} • $${String.format("%.2f", order.totalCost)} • ${if (order.isAutoTriggered) "AUTO-PO" else "MANUAL"}",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    Text(
                                        "Status: ${order.status.uppercase()}",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                }
                                if (order.status != "received") {
                                    Button(onClick = { viewModel.receiveReorder(order) }) {
                                        Text("Mark Received")
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    if (showAddSku) {
        InventoryFormDialog(
            existing = null,
            onDismiss = { showAddSku = false },
            onConfirm = { item ->
                viewModel.saveInventoryItem(item)
                showAddSku = false
            }
        )
    }

    val currentEdit = editingItem
    if (currentEdit != null) {
        InventoryFormDialog(
            existing = currentEdit,
            onDismiss = { editingItem = null },
            onConfirm = { updated ->
                viewModel.saveInventoryItem(updated)
                editingItem = null
            }
        )
    }
}

@Composable
private fun InventoryListRowCard(
    item: InventoryItem,
    onSellOne: () -> Unit,
    onAddOne: () -> Unit,
    onAddFive: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit
) {
    PulseCard(accentBorder = item.isLowStock) {
        // Header Row: Name + SKU + Category + Actions
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = item.name,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    if (item.isLowStock) {
                        Surface(
                            color = MaterialTheme.colorScheme.errorContainer,
                            shape = RoundedCornerShape(6.dp)
                        ) {
                            Text(
                                text = "LOW STOCK",
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onErrorContainer,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }
                    }
                }
                Text(
                    text = "${item.sku} • ${item.category}${if (item.supplierName.isNotBlank()) " • ${item.supplierName}" else ""}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(
                    onClick = onEdit,
                    modifier = Modifier.testTag("edit_inventory_${item.id}")
                ) {
                    Icon(Icons.Default.Edit, contentDescription = "Edit SKU")
                }
                IconButton(
                    onClick = onDelete,
                    modifier = Modifier.testTag("delete_inventory_${item.id}")
                ) {
                    Icon(Icons.Default.Delete, contentDescription = "Delete SKU")
                }
            }
        }

        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))

        // Clean Scannable Metrics Strip
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            InventoryMetricCell(
                label = "In Stock",
                value = "${item.stockQty}",
                highlightError = item.isLowStock,
                modifier = Modifier.weight(1f)
            )
            InventoryMetricCell(
                label = "Sold",
                value = "${item.unitsSold}",
                modifier = Modifier.weight(1f)
            )
            InventoryMetricCell(
                label = "Buying",
                value = if (item.unitCost > 0.0) "$${String.format("%.2f", item.unitCost)}" else "Optional",
                muted = item.unitCost <= 0.0,
                modifier = Modifier.weight(1f)
            )
            InventoryMetricCell(
                label = "Selling",
                value = if (item.unitPrice > 0.0) "$${String.format("%.2f", item.unitPrice)}" else "Optional",
                muted = item.unitPrice <= 0.0,
                modifier = Modifier.weight(1f)
            )
        }

        val unitProfitText = if (item.unitCost > 0.0 && item.unitPrice > 0.0) {
            val profitPerUnit = item.unitPrice - item.unitCost
            "Profit/unit: $${String.format("%.2f", profitPerUnit)} • "
        } else {
            ""
        }
        Text(
            text = "${unitProfitText}Alert <= ${item.lowStockThreshold} • Auto-Reorder: ${if (item.autoReorder) "ON (+${item.reorderQty})" else "OFF"}",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        // Easy One-Tap Stock Update Row
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            OutlinedButton(
                onClick = onSellOne,
                enabled = item.stockQty > 0L,
                modifier = Modifier.weight(1.1f)
            ) {
                Icon(Icons.Default.ShoppingCart, null, Modifier.size(15.dp))
                Spacer(Modifier.width(4.dp))
                Text("-1 Sell")
            }
            FilledTonalButton(
                onClick = onAddOne,
                modifier = Modifier.weight(0.9f)
            ) {
                Text("+1")
            }
            Button(
                onClick = onAddFive,
                modifier = Modifier.weight(1f)
            ) {
                Text("+5 Stock")
            }
        }
    }
}

@Composable
private fun InventoryMetricCell(
    label: String,
    value: String,
    highlightError: Boolean = false,
    muted: Boolean = false,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            text = value,
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Bold,
            color = when {
                highlightError -> MaterialTheme.colorScheme.error
                muted -> MaterialTheme.colorScheme.onSurfaceVariant
                else -> MaterialTheme.colorScheme.onSurface
            }
        )
    }
}

@Composable
private fun FinanceStat(label: String, value: String) {
    Column {
        Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun InventoryFormDialog(
    existing: InventoryItem?,
    onDismiss: () -> Unit,
    onConfirm: (InventoryItem) -> Unit
) {
    val isEdit = existing != null
    var sku by remember { mutableStateOf(existing?.sku ?: "SKU-001") }
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

    fun filterDecimal(input: String): String {
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

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (isEdit) "Update Inventory Item" else "Add Inventory SKU") },
        text = {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                item {
                    OutlinedTextField(
                        value = name,
                        onValueChange = { name = it },
                        label = { Text("Product Name") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
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
                            label = { Text("Stock Qty") },
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
                            onValueChange = { buyingPrice = filterDecimal(it) },
                            label = { Text("Buying Price (Optional)") },
                            placeholder = { Text("0.00") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                            modifier = Modifier.weight(1f),
                            singleLine = true
                        )
                        OutlinedTextField(
                            value = sellingPrice,
                            onValueChange = { sellingPrice = filterDecimal(it) },
                            label = { Text("Selling Price (Optional)") },
                            placeholder = { Text("0.00") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                            modifier = Modifier.weight(1f),
                            singleLine = true
                        )
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
                            )
                        )
                    }
                }
            ) { Text(if (isEdit) "Update SKU" else "Save SKU") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}
