package com.example

import com.example.data.model.BusinessAccountConfig
import com.example.data.model.BusinessDebtRecord
import com.example.data.model.BusinessLedgerEntry
import com.example.data.model.InventoryItem
import com.example.data.model.computeBusinessFinancialSummary
import com.example.system.formatKsh
import com.example.system.formatKshSigned
import org.junit.Assert.*
import org.junit.Test

/**
 * Local unit tests for inventory margins, Kenyan Shillings (KSh) formatting,
 * and Business Accounting financial summary calculations.
 */
class ExampleUnitTest {
    @Test
    fun addition_isCorrect() {
        assertEquals(4, 2 + 2)
    }

    @Test
    fun inventoryItem_calculatesProfitMarginWhenBuyingAndSellingEntered() {
        val priced = InventoryItem(
            sku = "SKU-101",
            name = "Coffee Beans 1kg",
            stockQty = 10L,
            unitCost = 1500.0,
            unitPrice = 2500.0,
            unitsSold = 4L
        )
        assertTrue(priced.hasBuyingAndSellingPrice)
        assertEquals(1000.0, priced.profitPerUnit, 0.001)
        assertEquals(40.0, priced.profitMarginPercent, 0.001)
        assertEquals(66.666, priced.markupPercent, 0.01)
        assertEquals(10000.0, priced.expectedStockProfit, 0.001)
        assertEquals(4000.0, priced.grossProfit, 0.001)

        val optionalOnly = InventoryItem(
            sku = "SKU-102",
            name = "Sample Pack",
            stockQty = 5L,
            unitCost = 0.0,
            unitPrice = 2000.0
        )
        assertFalse(optionalOnly.hasBuyingAndSellingPrice)
        assertEquals(0.0, optionalOnly.profitMarginPercent, 0.001)
    }

    @Test
    fun formatKsh_formatsKenyanShillingsProperly() {
        assertEquals("KSh 12,500.00", formatKsh(12500.0))
        assertEquals("KSh 1,250", formatKsh(1250.0, decimals = 0))
        assertEquals("+KSh 4,500.00", formatKshSigned(4500.0))
        assertEquals("-KSh 750.00", formatKshSigned(-750.0))
    }

    @Test
    fun businessFinancialSummary_calculatesWorthProfitLossAndLoanWarning() {
        val account = BusinessAccountConfig(
            businessName = "Nairobi Mart",
            isInitialized = true,
            openingCash = 20000.0,
            currentCash = 10000.0,
            totalCapitalAdded = 5000.0
        )
        val inventory = listOf(
            InventoryItem(
                name = "Sugar 2kg",
                stockQty = 10L,
                unitCost = 300.0,
                unitPrice = 400.0,
                unitsSold = 50L
            )
        )
        val ledger = listOf(
            BusinessLedgerEntry(entryType = "expense", amount = 2000.0)
        )
        val debts = listOf(
            BusinessDebtRecord(personName = "Alice", debtType = "debtor", totalAmount = 15000.0, settledAmount = 0.0),
            BusinessDebtRecord(personName = "Wholesale Ltd", debtType = "creditor", totalAmount = 4000.0, settledAmount = 1000.0)
        )

        val summary = computeBusinessFinancialSummary(account, inventory, ledger, debts)
        // Cash = 10,000, StockCost = 10 * 300 = 3,000, Debtors = 15,000, Creditors = 3,000 -> Worth = 25,000
        assertEquals(25000.0, summary.businessWorth, 0.01)
        assertEquals(3000.0, summary.stockCostValue, 0.01)
        assertEquals(15000.0, summary.totalDebtors, 0.01)
        assertEquals(3000.0, summary.totalCreditors, 0.01)
        // Realized gross = 50 * (400 - 300) = 5,000; expenses = 2,000 -> net operating profit = +3,000
        assertEquals(3000.0, summary.netProfitOrLoss, 0.01)
        assertFalse(summary.isMakingLoss)
        // Debtors (15,000) > CurrentCash (10,000) -> loan warning triggers
        assertTrue(summary.showLoanWarning)
    }
}
