package com.example

import com.example.data.model.InventoryItem
import org.junit.Assert.*
import org.junit.Test

/**
 * Example local unit test, which will execute on the development machine (host).
 *
 * See [testing documentation](http://d.android.com/tools/testing).
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
      unitCost = 15.0,
      unitPrice = 25.0,
      unitsSold = 4L
    )
    assertTrue(priced.hasBuyingAndSellingPrice)
    assertEquals(10.0, priced.profitPerUnit, 0.001)
    assertEquals(40.0, priced.profitMarginPercent, 0.001)
    assertEquals(66.666, priced.markupPercent, 0.01)
    assertEquals(100.0, priced.expectedStockProfit, 0.001)
    assertEquals(40.0, priced.grossProfit, 0.001)

    val optionalOnly = InventoryItem(
      sku = "SKU-102",
      name = "Sample Pack",
      stockQty = 5L,
      unitCost = 0.0,
      unitPrice = 20.0
    )
    assertFalse(optionalOnly.hasBuyingAndSellingPrice)
    assertEquals(0.0, optionalOnly.profitMarginPercent, 0.001)
  }
}
