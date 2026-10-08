package ke.co.tracom.order_service

import ke.co.tracom.order_service.domain.dto.*
import ke.co.tracom.order_service.domain.model.*
import ke.co.tracom.order_service.exception.*
import ke.co.tracom.order_service.repository.*
import ke.co.tracom.order_service.service.RefundService
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.transaction.annotation.Transactional
import java.math.BigDecimal

@SpringBootTest
@Transactional
class RefundServiceTests {

    @Autowired
    private lateinit var refundService: RefundService

    @Autowired
    private lateinit var saleRepository: SaleRepository

    @Autowired
    private lateinit var productRepository: ProductRepository

    @Autowired
    private lateinit var orderRepository: OrderRepository

    @Autowired
    private lateinit var refundReturnRepository: RefundReturnRepository

    @Test
    fun `test lookup sale successfully with valid e-TIMS receipt and supplier invoice`() {
        // Uses seeded sale "SALE-202610-101", receipt "ETIMS-REC-88492041", invoice "INV-SUP-99102"
        val sale = refundService.lookupSale("ETIMS-REC-88492041", "INV-SUP-99102")

        assertNotNull(sale)
        assertEquals("SALE-202610-101", sale.saleReference)
        assertEquals("Kibera Corner Store", sale.customerName)
        assertEquals(2, sale.items.size)
        assertEquals("ORD-BACKORDER-001", sale.linkedOrderNumber)
    }

    @Test
    fun `test EX-01 Sale not found when both receipt and invoice do not exist`() {
        val ex = assertThrows<SaleNotFoundException> {
            refundService.lookupSale("ETIMS-NON-EXISTENT", "INV-NON-EXISTENT")
        }
        assertEquals("Sale not found", ex.message)
    }

    @Test
    fun `test EX-03 Invalid e-TIMS receipt number when receipt is wrong but invoice exists`() {
        val ex = assertThrows<InvalidETimsReceiptException> {
            refundService.lookupSale("ETIMS-WRONG-999", "INV-SUP-99102")
        }
        assertEquals("Invalid e-TIMS receipt number", ex.message)
    }

    @Test
    fun `test EX-04 Invalid supplier invoice when receipt exists but invoice is wrong`() {
        val ex = assertThrows<InvalidSupplierInvoiceException> {
            refundService.lookupSale("ETIMS-REC-88492041", "INV-WRONG-888")
        }
        assertEquals("Invalid supplier invoice number", ex.message)
    }

    @Test
    fun `test process refund computes prices and tax, issues KRA credit note, reverses stock and linked order`() {
        val initialProduct = productRepository.findByProductId("PRD-MILK-01")!!
        val initialStock = initialProduct.stockLevel

        val linkedOrderBefore = orderRepository.findByOrderNumber("ORD-BACKORDER-001")!!
        assertEquals(OrderStatus.PENDING_FULFILMENT, linkedOrderBefore.orderStatus)

        // Request refund for 2 units of Milk (PRD-MILK-01)
        val refundReq = CreateRefundRequest(
            saleReference = "SALE-202610-101",
            eTimsReceiptNumber = "ETIMS-REC-88492041",
            supplierInvoiceNumber = "INV-SUP-99102",
            items = listOf(
                RefundItemRequest(productId = "PRD-MILK-01", quantity = 2)
            ),
            reason = "Customer received damaged packaging on 2 milk cartons",
            requestedBy = "John Agent"
        )

        val response = refundService.processRefund(refundReq)

        // 1. Verify calculated amounts and KRA Credit Note
        assertNotNull(response.refundNumber)
        // 2 units * 1440.00 = 2880.00
        assertEquals(BigDecimal("2880.00"), response.refundSubtotal)
        // 2880.00 * 0.16 = 460.80
        assertEquals(BigDecimal("460.80"), response.refundTaxAmount)
        assertEquals(BigDecimal("3340.80"), response.refundTotalAmount)

        // Verify Credit Note issued to KRA e-TIMS
        assertNotNull(response.creditNote)
        assertTrue(response.creditNote.creditNoteNumber.startsWith("KRA-CN-"))
        assertEquals("ACCEPTED", response.creditNote.kraStatus)
        assertTrue(response.creditNote.qrCodeData.contains("etims.kra.go.ke"))

        // 2. Verify stock levels reversed (restocked)
        val updatedProduct = productRepository.findByProductId("PRD-MILK-01")!!
        assertEquals(initialStock + 2, updatedProduct.stockLevel)

        // 3. Verify linked unfulfilled backorder was cancelled and reversed
        assertEquals("ORD-BACKORDER-001", response.reversedLinkedOrderNumber)
        val linkedOrderAfter = orderRepository.findByOrderNumber("ORD-BACKORDER-001")!!
        assertEquals(OrderStatus.CANCELLED, linkedOrderAfter.orderStatus)
        assertTrue(linkedOrderAfter.cancellationReason!!.contains("Reversed due to refund"))

        // 4. Verify original sale reflects refunded quantity and status
        val updatedSale = saleRepository.findBySaleReference("SALE-202610-101")!!
        assertEquals(SaleStatus.PARTIALLY_REFUNDED, updatedSale.status)
        val milkItem = updatedSale.items.find { it.productId == "PRD-MILK-01" }!!
        assertEquals(2, milkItem.quantityRefunded)
        assertEquals(1, milkItem.remainingEligibleQuantity)
    }

    @Test
    fun `test EX-05 Invalid return quantity exceeds eligible quantity`() {
        val ex = assertThrows<InvalidReturnQuantityException> {
            refundService.processRefund(
                CreateRefundRequest(
                    saleReference = "SALE-202610-101",
                    eTimsReceiptNumber = "ETIMS-REC-88492041",
                    supplierInvoiceNumber = "INV-SUP-99102",
                    items = listOf(
                        RefundItemRequest(productId = "PRD-MILK-01", quantity = 10) // sold only 3
                    ),
                    reason = "Trying to return more than sold",
                    requestedBy = "John Agent"
                )
            )
        }
        assertEquals("Return quantity exceeds eligible quantity", ex.message)
    }

    @Test
    fun `test EX-06 Missing mandatory information prevents submission`() {
        val ex = assertThrows<MissingMandatoryInformationException> {
            refundService.processRefund(
                CreateRefundRequest(
                    saleReference = "SALE-202610-101",
                    eTimsReceiptNumber = "", // missing
                    supplierInvoiceNumber = "INV-SUP-99102",
                    items = listOf(
                        RefundItemRequest(productId = "PRD-MILK-01", quantity = 1)
                    ),
                    reason = "",
                    requestedBy = "John Agent"
                )
            )
        }
        assertTrue(ex.message!!.contains("Missing mandatory information"))
    }

    @Test
    fun `test EX-02 Sale not eligible for return after full refund`() {
        // Refund all items completely
        refundService.processRefund(
            CreateRefundRequest(
                saleReference = "SALE-202610-101",
                eTimsReceiptNumber = "ETIMS-REC-88492041",
                supplierInvoiceNumber = "INV-SUP-99102",
                items = listOf(
                    RefundItemRequest(productId = "PRD-MILK-01", quantity = 3),
                    RefundItemRequest(productId = "PRD-OIL-01", quantity = 1)
                ),
                reason = "All goods defective",
                requestedBy = "John Agent"
            )
        )

        // Try looking up or refunding again
        val ex = assertThrows<SaleNotEligibleForReturnException> {
            refundService.lookupSale("ETIMS-REC-88492041", "INV-SUP-99102")
        }
        assertEquals("Sale is not eligible for refund/return", ex.message)
    }
}
