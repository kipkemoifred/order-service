package ke.co.tracom.order_service.domain.model

import jakarta.persistence.*
import java.math.BigDecimal
import java.time.LocalDate
import java.time.LocalDateTime

enum class PaymentMethod {
    MPESA,
    CARD,
    CASH,
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

    @Column(name = "name", nullable = false, length = 150)
    var name: String = "",

    @Column(name = "product_code", nullable = false, unique = true, length = 100)
    var productCode: String = "",

    @Column(name = "description", length = 1000)
    var description: String? = null,

    @Column(name = "category", nullable = false, length = 100)
    var category: String = "General",

    @Column(name = "unit_of_measure", nullable = false, length = 50)
    var unitOfMeasure: String = "PIECES",

    @Column(name = "unit_price", nullable = false, precision = 12, scale = 2)
    var unitPrice: BigDecimal = BigDecimal.ZERO,

    @Column(name = "tax_type", nullable = false, length = 50)
    var taxType: String = "STANDARD",

    @Column(name = "has_discount", nullable = false)
    var hasDiscount: Boolean = false,

    @Column(name = "discount_rate", nullable = false, precision = 5, scale = 2)
    var discountRate: BigDecimal = BigDecimal.ZERO,

    @Column(name = "image_url", length = 500)
    var imageUrl: String? = null,

    @Column(name = "active", nullable = false)
    var active: Boolean = true,

    @Column(name = "synced_to_supplier_catalogue", nullable = false)
    var syncedToSupplierCatalogue: Boolean = false,

    @Column(name = "created_at", nullable = false)
    var createdAt: LocalDateTime = LocalDateTime.now(),

    @Column(name = "updated_at", nullable = false)
    var updatedAt: LocalDateTime = LocalDateTime.now(),

    @OneToOne(mappedBy = "product", cascade = [CascadeType.ALL], fetch = FetchType.EAGER)
    var stock: InventoryStock? = null
) {
    // Secondary constructor to remain backward compatible with existing constructors in code and tests
    constructor(
        id: Long? = null,
        productId: String = "",
        supplierId: String = "",
        supplierName: String = "",
        name: String = "",
        category: String = "General",
        unitPrice: BigDecimal = BigDecimal.ZERO,
        taxRate: BigDecimal = BigDecimal("0.16"),
        stockLevel: Int = 0,
        reservedStock: Int = 0,
        region: String = "Nairobi",
        productCode: String = productId,
        unitOfMeasure: String = "PIECES",
        taxType: String = if (taxRate.compareTo(BigDecimal.ZERO) == 0) "ZERO_RATED" else "STANDARD"
    ) : this(
        id = id,
        name = name,
        productCode = if (productCode.isNotBlank()) productCode else productId,
        description = null,
        category = category,
        unitOfMeasure = unitOfMeasure,
        unitPrice = unitPrice,
        taxType = taxType,
        hasDiscount = false,
        discountRate = BigDecimal.ZERO,
        imageUrl = null,
        active = true,
        syncedToSupplierCatalogue = true,
        createdAt = LocalDateTime.now(),
        updatedAt = LocalDateTime.now(),
        stock = null
    ) {
        this._productId = if (productId.isNotBlank()) productId else productCode
        this._supplierId = if (supplierId.isNotBlank()) supplierId else null
        this._supplierName = if (supplierName.isNotBlank()) supplierName else null
        this._region = if (region.isNotBlank()) region else null
        this._taxRate = taxRate
        this._stockLevel = stockLevel
        this._reservedStock = reservedStock
        val s = InventoryStock(
            product = this,
            availableQuantity = BigDecimal(stockLevel),
            orderedQuantity = BigDecimal(reservedStock),
            reorderThreshold = BigDecimal("20.00"),
            lastUpdated = LocalDateTime.now()
        )
        this.stock = s
    }

    @Transient
    private var _productId: String? = null

    var productId: String
        get() = if (!_productId.isNullOrBlank()) _productId!! else productCode
        set(value) {
            _productId = value
            productCode = value
        }

    @Transient
    private var _taxRate: BigDecimal? = null

    var taxRate: BigDecimal
        get() = _taxRate ?: when (taxType.uppercase()) {
            "ZERO_RATED", "EXEMPT" -> BigDecimal("0.00")
            else -> BigDecimal("0.16")
        }
        set(value) {
            _taxRate = value
            taxType = if (value.compareTo(BigDecimal.ZERO) == 0) "ZERO_RATED" else "STANDARD"
        }

    @Transient
    private var _stockLevel: Int? = null

    var stockLevel: Int
        get() = stock?.availableQuantity?.toInt() ?: (_stockLevel ?: 0)
        set(value) {
            _stockLevel = value
            val s = stock ?: InventoryStock(product = this).also { this.stock = it }
            s.availableQuantity = BigDecimal(value)
            s.lastUpdated = LocalDateTime.now()
        }

    @Transient
    private var _reservedStock: Int? = null

    var reservedStock: Int
        get() = stock?.orderedQuantity?.toInt() ?: (_reservedStock ?: 0)
        set(value) {
            _reservedStock = value
            val s = stock ?: InventoryStock(product = this).also { this.stock = it }
            s.orderedQuantity = BigDecimal(value)
            s.lastUpdated = LocalDateTime.now()
        }

    val availableStock: Int
        get() = (stockLevel - reservedStock).coerceAtLeast(0)

    @Transient
    private var _supplierId: String? = null

    var supplierId: String
        get() = _supplierId ?: deriveSupplierId(category, productCode)
        set(value) { _supplierId = value }

    @Transient
    private var _supplierName: String? = null

    var supplierName: String
        get() = _supplierName ?: deriveSupplierName(supplierId)
        set(value) { _supplierName = value }

    @Transient
    private var _region: String? = null

    var region: String
        get() = _region ?: deriveRegion(category, productCode)
        set(value) { _region = value }

    companion object {
        fun deriveSupplierId(category: String, productCode: String): String {
            return when {
                productCode.startsWith("PRD-MILK") || category.equals("Dairy", ignoreCase = true) || productCode.startsWith("MILK") -> "SUP-BROOKSIDE"
                productCode.startsWith("PRD-OIL") || productCode.startsWith("PRD-SOAP") || category.equals("Cooking Oil", ignoreCase = true) || category.equals("Detergent", ignoreCase = true) || productCode.startsWith("OIL") -> "SUP-BIDCO"
                productCode.startsWith("PRD-FLOUR") || productCode.startsWith("PRD-OUT") || category.equals("Flour & Grains", ignoreCase = true) || category.equals("Rice", ignoreCase = true) || category.equals("Bakery", ignoreCase = true) || productCode.startsWith("BREAD") -> "SUP-UNGA"
                category.equals("Beverages", ignoreCase = true) || productCode.startsWith("WATER") -> "SUP-BROOKSIDE"
                else -> "SUP-GENERAL"
            }
        }

        fun deriveSupplierName(supplierId: String): String {
            return when (supplierId) {
                "SUP-BROOKSIDE" -> "Brookside Dairy Ltd"
                "SUP-BIDCO" -> "Bidco Africa Ltd"
                "SUP-UNGA" -> "Unga Group Ltd"
                else -> "General Supplier Ltd"
            }
        }

        fun deriveRegion(category: String, productCode: String): String {
            return when {
                productCode == "PRD-MILK-02" -> "Rift Valley"
                productCode == "PRD-SOAP-01" -> "Coast"
                else -> "Nairobi"
            }
        }
    }
}

@Entity
@Table(name = "inventory_stocks")
class InventoryStock(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    var id: Long? = null,

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "product_id", nullable = false, unique = true)
    var product: Product? = null,

    @Column(name = "available_quantity", nullable = false, precision = 12, scale = 2)
    var availableQuantity: BigDecimal = BigDecimal.ZERO,

    @Column(name = "ordered_quantity", nullable = false, precision = 12, scale = 2)
    var orderedQuantity: BigDecimal = BigDecimal.ZERO,

    @Column(name = "reorder_threshold", nullable = false, precision = 12, scale = 2)
    var reorderThreshold: BigDecimal = BigDecimal.ZERO,

    @Column(name = "last_updated", nullable = false)
    var lastUpdated: LocalDateTime = LocalDateTime.now()
) {
    val productId: Long?
        get() = product?.id
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
