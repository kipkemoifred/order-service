package ke.co.tracom.order_service.domain.model

import jakarta.persistence.*
import java.math.BigDecimal
import java.time.LocalDate
import java.time.LocalDateTime

enum class PaymentMethod {
    MPESA,
    CARD,
    PAY_ON_DELIVERY
}

enum class PaymentStatus {
    PAID,
    UNPAID_PAY_ON_DELIVERY,
    CONFIRMED_UNPAID,
    FAILED
}

enum class OrderStatus {
    PENDING_FULFILMENT,
    DISPATCHED,
    AWAITING_DELIVERY,
    DELIVERED,
    CANCELLED
}

enum class DeliveryStatus {
    PENDING,
    DISPATCHED,
    DELIVERED
}

enum class InventoryMovementType {
    RESERVATION,
    RESERVATION_RELEASE,
    DEDUCTION_SALE,
    RESTOCK_RETURN
}

enum class NotificationType {
    NEW_ORDER,
    ORDER_ADJUSTED,
    ORDER_CANCELLED,
    ORDER_DELIVERED
}

enum class SaleStatus {
    COMPLETED,
    PARTIALLY_REFUNDED,
    FULLY_REFUNDED
}

enum class RefundStatus {
    SUBMITTED,
    APPROVED,
    COMPLETED,
    REJECTED
}

@Entity
@Table(name = "products")
class Product(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    var id: Long? = null,

    @Column(nullable = false, unique = true)
    var productId: String = "",

    @Column(nullable = false)
    var supplierId: String = "",

    @Column(nullable = false)
    var supplierName: String = "",

    @Column(nullable = false)
    var name: String = "",

    var category: String = "General",

    @Column(nullable = false, precision = 12, scale = 2)
    var unitPrice: BigDecimal = BigDecimal.ZERO,

    @Column(nullable = false, precision = 5, scale = 4)
    var taxRate: BigDecimal = BigDecimal("0.16"),

    @Column(nullable = false)
    var stockLevel: Int = 0,

    @Column(nullable = false)
    var reservedStock: Int = 0,

    var region: String = "Nairobi"
) {
    val availableStock: Int
        get() = (stockLevel - reservedStock).coerceAtLeast(0)
}

@Entity
@Table(name = "orders")
class Order(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    var id: Long? = null,

    @Column(nullable = false, unique = true)
    var orderNumber: String = "",

    @Column(nullable = false, unique = true)
    var invoiceNumber: String = "",

    @Column(nullable = false)
    var shopOwnerId: String = "",

    @Column(nullable = false)
    var shopOwnerName: String = "",

    @Column(nullable = false)
    var shopOwnerPhone: String = "",

    @Column(nullable = false)
    var supplierId: String = "",

    @Column(nullable = false)
    var supplierName: String = "",

    @Column(nullable = false)
    var region: String = "Nairobi",

    @OneToMany(mappedBy = "order", cascade = [CascadeType.ALL], orphanRemoval = true, fetch = FetchType.EAGER)
    var items: MutableList<OrderItem> = mutableListOf(),

    @Column(nullable = false, precision = 12, scale = 2)
    var subtotal: BigDecimal = BigDecimal.ZERO,

    @Column(nullable = false, precision = 12, scale = 2)
    var taxAmount: BigDecimal = BigDecimal.ZERO,

    @Column(nullable = false, precision = 12, scale = 2)
    var deliveryCharges: BigDecimal = BigDecimal.ZERO,

    @Column(nullable = false, precision = 12, scale = 2)
    var totalAmount: BigDecimal = BigDecimal.ZERO,

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    var paymentMethod: PaymentMethod = PaymentMethod.PAY_ON_DELIVERY,

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    var paymentStatus: PaymentStatus = PaymentStatus.CONFIRMED_UNPAID,

    @Column(nullable = false, precision = 12, scale = 2)
    var amountPaid: BigDecimal = BigDecimal.ZERO,

    @Column(nullable = false, precision = 12, scale = 2)
    var amountOutstanding: BigDecimal = BigDecimal.ZERO,

    var paymentReference: String? = null,

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    var orderStatus: OrderStatus = OrderStatus.PENDING_FULFILMENT,

    var dispatchDate: LocalDateTime? = null,

    var expectedDeliveryDate: LocalDate? = null,

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    var deliveryStatus: DeliveryStatus = DeliveryStatus.PENDING,

    var deliveryConfirmedAt: LocalDateTime? = null,

    var deliveryConfirmedBy: String? = null,

    @Column(nullable = false)
    var createdAt: LocalDateTime = LocalDateTime.now(),

    @Column(nullable = false)
    var updatedAt: LocalDateTime = LocalDateTime.now(),

    var cancellationReason: String? = null,

    var cancelledAt: LocalDateTime? = null
)

@Entity
@Table(name = "order_items")
class OrderItem(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    var id: Long? = null,

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "order_id", nullable = false)
    var order: Order? = null,

    @Column(nullable = false)
    var productId: String = "",

    @Column(nullable = false)
    var productName: String = "",

    @Column(nullable = false)
    var quantity: Int = 0,

    @Column(nullable = false, precision = 12, scale = 2)
    var unitPrice: BigDecimal = BigDecimal.ZERO,

    @Column(nullable = false, precision = 5, scale = 4)
    var taxRate: BigDecimal = BigDecimal("0.16"),

    @Column(nullable = false, precision = 12, scale = 2)
    var lineTotal: BigDecimal = BigDecimal.ZERO,

    @Column(nullable = false, precision = 12, scale = 2)
    var lineTax: BigDecimal = BigDecimal.ZERO
)

@Entity
@Table(name = "inventory_movements")
class InventoryMovement(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    var id: Long? = null,

    @Column(nullable = false)
    var productId: String = "",

    @Column(nullable = false)
    var supplierId: String = "",

    var orderNumber: String? = null,

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    var movementType: InventoryMovementType = InventoryMovementType.RESERVATION,

    @Column(nullable = false)
    var quantity: Int = 0,

    @Column(nullable = false)
    var timestamp: LocalDateTime = LocalDateTime.now(),

    var notes: String? = null
)

@Entity
@Table(name = "supplier_notifications")
class SupplierNotification(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    var id: Long? = null,

    @Column(nullable = false)
    var supplierId: String = "",

    @Column(nullable = false)
    var orderNumber: String = "",

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    var notificationType: NotificationType = NotificationType.NEW_ORDER,

    @Column(nullable = false, length = 1000)
    var message: String = "",

    @Column(nullable = false)
    var timestamp: LocalDateTime = LocalDateTime.now(),

    @Column(nullable = false)
    var isRead: Boolean = false
)

@Entity
@Table(name = "sales")
class Sale(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    var id: Long? = null,

    @Column(nullable = false, unique = true)
    var saleReference: String = "",

    @Column(nullable = false, unique = true)
    var eTimsReceiptNumber: String = "",

    @Column(nullable = false)
    var supplierInvoiceNumber: String = "",

    @Column(nullable = false)
    var customerId: String = "",

    @Column(nullable = false)
    var customerName: String = "",

    @OneToMany(mappedBy = "sale", cascade = [CascadeType.ALL], orphanRemoval = true, fetch = FetchType.EAGER)
    var items: MutableList<SaleItem> = mutableListOf(),

    @Column(nullable = false, precision = 12, scale = 2)
    var subtotal: BigDecimal = BigDecimal.ZERO,

    @Column(nullable = false, precision = 12, scale = 2)
    var taxAmount: BigDecimal = BigDecimal.ZERO,

    @Column(nullable = false, precision = 12, scale = 2)
    var totalAmount: BigDecimal = BigDecimal.ZERO,

    @Column(nullable = false)
    var saleDate: LocalDateTime = LocalDateTime.now(),

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    var status: SaleStatus = SaleStatus.COMPLETED,

    var linkedOrderNumber: String? = null
)

@Entity
@Table(name = "sale_items")
class SaleItem(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    var id: Long? = null,

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "sale_id", nullable = false)
    var sale: Sale? = null,

    @Column(nullable = false)
    var productId: String = "",

    @Column(nullable = false)
    var productName: String = "",

    @Column(nullable = false)
    var quantitySold: Int = 0,

    @Column(nullable = false)
    var quantityRefunded: Int = 0,

    @Column(nullable = false, precision = 12, scale = 2)
    var unitPrice: BigDecimal = BigDecimal.ZERO,

    @Column(nullable = false, precision = 5, scale = 4)
    var taxRate: BigDecimal = BigDecimal("0.16"),

    @Column(nullable = false, precision = 12, scale = 2)
    var totalPrice: BigDecimal = BigDecimal.ZERO,

    @Column(nullable = false, precision = 12, scale = 2)
    var taxAmount: BigDecimal = BigDecimal.ZERO
) {
    val remainingEligibleQuantity: Int
        get() = (quantitySold - quantityRefunded).coerceAtLeast(0)
}

@Entity
@Table(name = "refund_returns")
class RefundReturn(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    var id: Long? = null,

    @Column(nullable = false, unique = true)
    var refundNumber: String = "",

    @Column(nullable = false)
    var saleReference: String = "",

    @Column(nullable = false)
    var eTimsReceiptNumber: String = "",

    @Column(nullable = false)
    var supplierInvoiceNumber: String = "",

    @Column(nullable = false)
    var creditNoteNumber: String = "",

    @Column(nullable = false, precision = 12, scale = 2)
    var refundSubtotal: BigDecimal = BigDecimal.ZERO,

    @Column(nullable = false, precision = 12, scale = 2)
    var refundTaxAmount: BigDecimal = BigDecimal.ZERO,

    @Column(nullable = false, precision = 12, scale = 2)
    var refundTotalAmount: BigDecimal = BigDecimal.ZERO,

    @Column(nullable = false)
    var reason: String = "",

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    var status: RefundStatus = RefundStatus.COMPLETED,

    @Column(nullable = false)
    var requestedBy: String = "",

    @Column(nullable = false)
    var createdAt: LocalDateTime = LocalDateTime.now(),

    var processedAt: LocalDateTime? = null,

    @OneToMany(mappedBy = "refundReturn", cascade = [CascadeType.ALL], orphanRemoval = true, fetch = FetchType.EAGER)
    var items: MutableList<RefundReturnItem> = mutableListOf(),

    var reversedLinkedOrderNumber: String? = null
)

@Entity
@Table(name = "refund_return_items")
class RefundReturnItem(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    var id: Long? = null,

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "refund_return_id", nullable = false)
    var refundReturn: RefundReturn? = null,

    @Column(nullable = false)
    var productId: String = "",

    @Column(nullable = false)
    var productName: String = "",

    @Column(nullable = false)
    var quantity: Int = 0,

    @Column(nullable = false, precision = 12, scale = 2)
    var unitPrice: BigDecimal = BigDecimal.ZERO,

    @Column(nullable = false, precision = 5, scale = 4)
    var taxRate: BigDecimal = BigDecimal("0.16"),

    @Column(nullable = false, precision = 12, scale = 2)
    var refundAmount: BigDecimal = BigDecimal.ZERO,

    @Column(nullable = false, precision = 12, scale = 2)
    var taxRefundAmount: BigDecimal = BigDecimal.ZERO
)
