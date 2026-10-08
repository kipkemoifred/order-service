package ke.co.tracom.order_service

import ke.co.tracom.order_service.domain.dto.*
import ke.co.tracom.order_service.domain.model.*
import ke.co.tracom.order_service.exception.*
import ke.co.tracom.order_service.repository.*
import ke.co.tracom.order_service.service.*
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.transaction.annotation.Transactional
import java.math.BigDecimal

@SpringBootTest
@Transactional
class OrderServiceTests {

    @Autowired
    private lateinit var orderService: OrderService

    @Autowired
    private lateinit var catalogService: CatalogService

    @Autowired
    private lateinit var manufacturerService: ManufacturerService

    @Autowired
    private lateinit var productRepository: ProductRepository

    @Autowired
    private lateinit var orderRepository: OrderRepository

    @Autowired
    private lateinit var supplierNotificationRepository: SupplierNotificationRepository

    @Test
    fun `test catalog lists suppliers and validates item availability in cart`() {
        val suppliers = catalogService.listSuppliers()
        assertTrue(suppliers.isNotEmpty(), "Suppliers should not be empty")

        val brooksideProducts = catalogService.getSupplierCatalog("SUP-BROOKSIDE")
        assertTrue(brooksideProducts.isNotEmpty())

        val cartCalc = catalogService.calculateCart(
            CartCalculationRequest(
                supplierId = "SUP-BROOKSIDE",
                items = listOf(OrderItemRequest("PRD-MILK-01", 2))
            )
        )
        assertTrue(cartCalc.allAvailable)
        assertEquals(BigDecimal("2880.00"), cartCalc.subtotal)
        assertEquals(BigDecimal("460.80"), cartCalc.taxAmount)
        assertEquals(BigDecimal("350.00"), cartCalc.deliveryCharges)
        assertEquals(BigDecimal("3690.80"), cartCalc.totalAmount)
    }

    @Test
    fun `test EX-01 Out of stock exception when item is out of stock`() {
        val ex = assertThrows<OutOfStockException> {
            orderService.createOrder(
                CreateOrderRequest(
                    shopOwnerId = "RET-001",
                    shopOwnerName = "Mama Jane Duka",
                    shopOwnerPhone = "0712345678",
                    supplierId = "SUP-UNGA",
                    items = listOf(OrderItemRequest("PRD-OUT-01", 1)),
                    paymentMethod = PaymentMethod.PAY_ON_DELIVERY
                )
            )
        }
        assertEquals("The item is currently out of stock", ex.message)
    }

    @Test
    fun `test normal flow - Place order and make payment with M-Pesa STK push`() {
        val productBefore = productRepository.findByProductId("PRD-MILK-01")!!
        val stockBefore = productBefore.stockLevel

        val orderResponse = orderService.createOrder(
            CreateOrderRequest(
                shopOwnerId = "RET-001",
                shopOwnerName = "Mama Jane Duka",
                shopOwnerPhone = "0712345678",
                supplierId = "SUP-BROOKSIDE",
                region = "Nairobi",
                items = listOf(OrderItemRequest("PRD-MILK-01", 2)),
                paymentMethod = PaymentMethod.MPESA,
                mpesaPhoneNumber = "0712345678"
            )
        )

        assertNotNull(orderResponse.orderNumber)
        assertNotNull(orderResponse.invoiceNumber)
        assertEquals(PaymentStatus.PAID, orderResponse.paymentStatus)
        assertEquals(BigDecimal.ZERO, orderResponse.amountOutstanding)
        assertNotNull(orderResponse.paymentReference)
        assertTrue(orderResponse.paymentReference!!.startsWith("MPESA-"))
        assertNotNull(orderResponse.receipt)
        assertEquals(orderResponse.totalAmount, orderResponse.receipt?.totalAmount)

        // Verify stock decremented directly on prepaid sale
        val productAfter = productRepository.findByProductId("PRD-MILK-01")!!
        assertEquals(stockBefore - 2, productAfter.stockLevel)

        // Verify supplier notification
        val notifications = supplierNotificationRepository.findByOrderNumber(orderResponse.orderNumber)
        assertTrue(notifications.isNotEmpty())
        assertEquals(NotificationType.NEW_ORDER, notifications.first().notificationType)
    }

    @Test
    fun `test EX-02 Payment authorization failure halts order and notifies user`() {
        val ex = assertThrows<PaymentFailedException> {
            orderService.createOrder(
                CreateOrderRequest(
                    shopOwnerId = "RET-001",
                    shopOwnerName = "Mama Jane Duka",
                    shopOwnerPhone = "0712345678",
                    supplierId = "SUP-BROOKSIDE",
                    items = listOf(OrderItemRequest("PRD-MILK-01", 1)),
                    paymentMethod = PaymentMethod.MPESA,
                    mpesaPhoneNumber = "0712345678",
                    simulatePaymentFailure = true
                )
            )
        }
        assertEquals("Payment could not be completed. Please try again", ex.message)
    }

    @Test
    fun `test AP1 - Pay on Delivery places order as Confirmed Unpaid and reserves inventory`() {
        val productBefore = productRepository.findByProductId("PRD-OIL-01")!!
        val reservedBefore = productBefore.reservedStock

        val order = orderService.createOrder(
            CreateOrderRequest(
                shopOwnerId = "RET-001",
                shopOwnerName = "Mama Jane Duka",
                shopOwnerPhone = "0712345678",
                supplierId = "SUP-BIDCO",
                region = "Nairobi",
                items = listOf(OrderItemRequest("PRD-OIL-01", 3)),
                paymentMethod = PaymentMethod.PAY_ON_DELIVERY
            )
        )

        assertEquals(PaymentStatus.CONFIRMED_UNPAID, order.paymentStatus)
        assertEquals(order.totalAmount, order.amountOutstanding)
        assertEquals(BigDecimal.ZERO, order.amountPaid)
        assertNull(order.receipt)

        val productAfter = productRepository.findByProductId("PRD-OIL-01")!!
        assertEquals(reservedBefore + 3, productAfter.reservedStock)

        // Appears in pending orders view
        val pendingOrders = orderService.getPendingOrders(shopOwnerId = "RET-001")
        assertTrue(pendingOrders.any { it.orderNumber == order.orderNumber })
    }

    @Test
    fun `test AP3 - Adjust Order adds or removes items, recalculates total, and adjusts inventory`() {
        val order = orderService.createOrder(
            CreateOrderRequest(
                shopOwnerId = "RET-001",
                shopOwnerName = "Mama Jane Duka",
                shopOwnerPhone = "0712345678",
                supplierId = "SUP-BROOKSIDE",
                region = "Nairobi",
                items = listOf(OrderItemRequest("PRD-MILK-01", 2)),
                paymentMethod = PaymentMethod.PAY_ON_DELIVERY
            )
        )

        val adjustedOrder = orderService.adjustOrder(
            order.orderNumber,
            AdjustOrderRequest(
                items = listOf(
                    OrderItemRequest("PRD-MILK-01", 1) // reduced from 2 to 1
                )
            )
        )

        assertEquals(1, adjustedOrder.items.size)
        assertEquals(1, adjustedOrder.items.first().quantity)
        // Subtotal should be 1440.00
        assertEquals(BigDecimal("1440.00"), adjustedOrder.subtotal)

        // Verify supplier notification for adjustment
        val notifications = supplierNotificationRepository.findByOrderNumber(order.orderNumber)
        assertTrue(notifications.any { it.notificationType == NotificationType.ORDER_ADJUSTED })
    }

    @Test
    fun `test AP4 - Cancel order by invoice number releases inventory and notifies supplier`() {
        val productBefore = productRepository.findByProductId("PRD-OIL-01")!!
        val reservedBefore = productBefore.reservedStock

        val order = orderService.createOrder(
            CreateOrderRequest(
                shopOwnerId = "RET-001",
                shopOwnerName = "Mama Jane Duka",
                shopOwnerPhone = "0712345678",
                supplierId = "SUP-BIDCO",
                region = "Nairobi",
                items = listOf(OrderItemRequest("PRD-OIL-01", 2)),
                paymentMethod = PaymentMethod.PAY_ON_DELIVERY
            )
        )

        // Cancel order using invoice number
        val cancelled = orderService.cancelOrder(
            CancelOrderRequest(
                invoiceNumber = order.invoiceNumber,
                reason = "Incorrect order quantity entered"
            )
        )

        assertEquals(OrderStatus.CANCELLED, cancelled.orderStatus)
        assertNotNull(cancelled.cancelledAt)

        // Reservations released back
        val productAfter = productRepository.findByProductId("PRD-OIL-01")!!
        assertEquals(reservedBefore, productAfter.reservedStock)

        // Supplier notified
        val notifications = supplierNotificationRepository.findByOrderNumber(order.orderNumber)
        assertTrue(notifications.any { it.notificationType == NotificationType.ORDER_CANCELLED })
    }

    @Test
    fun `test AP-03 - Confirm delivery marks Delivered and settles Pay on Delivery`() {
        val order = orderService.createOrder(
            CreateOrderRequest(
                shopOwnerId = "RET-001",
                shopOwnerName = "Mama Jane Duka",
                shopOwnerPhone = "0712345678",
                supplierId = "SUP-BROOKSIDE",
                region = "Nairobi",
                items = listOf(OrderItemRequest("PRD-MILK-01", 2)),
                paymentMethod = PaymentMethod.PAY_ON_DELIVERY
            )
        )

        val delivered = orderService.confirmDelivery(
            order.orderNumber,
            ConfirmDeliveryRequest(
                confirmedBy = "Jane Mwangi",
                payOnDeliverySettlementMethod = PaymentMethod.MPESA,
                paymentReference = "POD-MPESA-CONFIRMED-99"
            )
        )

        assertEquals(OrderStatus.DELIVERED, delivered.orderStatus)
        assertEquals(DeliveryStatus.DELIVERED, delivered.deliveryStatus)
        assertEquals(PaymentStatus.PAID, delivered.paymentStatus)
        assertEquals("Jane Mwangi", delivered.deliveryConfirmedBy)
        assertNotNull(delivered.deliveryConfirmedAt)
        assertEquals(BigDecimal.ZERO, delivered.amountOutstanding)
    }

    @Test
    fun `test manufacturer demand aggregation across products and regions`() {
        val demand = manufacturerService.getAggregatedDemand()
        assertTrue(demand.isNotEmpty(), "Manufacturer demand should include pending orders")

        // PRD-MILK-02 was seeded with Rift Valley order
        val riftValleyMilk = demand.find { it.productId == "PRD-MILK-02" && it.region == "Rift Valley" }
        assertNotNull(riftValleyMilk)
        assertTrue(riftValleyMilk!!.totalStockRequired >= 2)
        assertNotNull(riftValleyMilk.requiredByDate)
    }

    @Test
    fun `test place order with cash payment method`() {
        val productBefore = productRepository.findByProductId("PRD-MILK-01")!!
        val stockBefore = productBefore.stockLevel

        val orderResponse = orderService.createOrder(
            CreateOrderRequest(
                shopOwnerId = "RET-001",
                shopOwnerName = "Mama Jane Duka",
                shopOwnerPhone = "0712345678",
                supplierId = "SUP-BROOKSIDE",
                region = "Nairobi",
                items = listOf(OrderItemRequest("PRD-MILK-01", 1)),
                paymentMethod = PaymentMethod.CASH
            )
        )

        assertNotNull(orderResponse.orderNumber)
        assertNotNull(orderResponse.invoiceNumber)
        assertEquals(PaymentStatus.PAID, orderResponse.paymentStatus)
        assertEquals(PaymentMethod.CASH, orderResponse.paymentMethod)
        assertEquals(BigDecimal.ZERO, orderResponse.amountOutstanding)
        assertNotNull(orderResponse.paymentReference)
        assertTrue(orderResponse.paymentReference!!.startsWith("CASH-"))

        val productAfter = productRepository.findByProductId("PRD-MILK-01")!!
        assertEquals(stockBefore - 1, productAfter.stockLevel)
    }
}
