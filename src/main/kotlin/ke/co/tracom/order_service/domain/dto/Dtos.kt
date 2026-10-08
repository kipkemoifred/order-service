package ke.co.tracom.order_service.domain.dto

import com.fasterxml.jackson.annotation.JsonAlias
import com.fasterxml.jackson.annotation.JsonProperty
import ke.co.tracom.order_service.domain.model.*
import java.math.BigDecimal
import java.time.LocalDate
import java.time.LocalDateTime

data class ProductDto(
    val productId: String,
    val supplierId: String,
    val supplierName: String,
    val name: String,
    val category: String,
    val unitPrice: BigDecimal,
    val taxRate: BigDecimal,
    val stockLevel: Int,
    val reservedStock: Int,
    val availableStock: Int,
    val region: String
)

data class SupplierSummaryDto(
    val supplierId: String,
    val supplierName: String,
    val productCount: Int,
    val region: String
)

data class OrderItemRequest(
    val productId: String = "",
    val quantity: Int = 0
)

data class CartCalculationRequest(
    val supplierId: String = "",
    val region: String = "Nairobi",
    val items: List<OrderItemRequest> = emptyList()
)

data class CalculatedItemDetail(
    val productId: String,
    val productName: String,
    val quantity: Int,
    val unitPrice: BigDecimal,
    val taxRate: BigDecimal,
    val lineTotal: BigDecimal,
    val lineTax: BigDecimal,
    val isAvailable: Boolean,
    val availableStock: Int
)

data class OrderSummaryResponse(
    val subtotal: BigDecimal,
    val taxAmount: BigDecimal,
    val deliveryCharges: BigDecimal,
    val totalAmount: BigDecimal,
    val itemDetails: List<CalculatedItemDetail>,
    val allAvailable: Boolean
)

data class CreateOrderRequest(
    val shopOwnerId: String = "",
    val shopOwnerName: String = "",
    val shopOwnerPhone: String = "",
    val supplierId: String = "",
    val region: String = "Nairobi",
    val items: List<OrderItemRequest> = emptyList(),
    val paymentMethod: PaymentMethod = PaymentMethod.PAY_ON_DELIVERY,
    val mpesaPhoneNumber: String? = null,
    val cardNumber: String? = null,
    val simulatePaymentFailure: Boolean = false
)

data class OrderItemDto(
    val productId: String,
    val productName: String,
    val quantity: Int,
    val unitPrice: BigDecimal,
    val taxRate: BigDecimal,
    val lineTotal: BigDecimal,
    val lineTax: BigDecimal
)

data class DigitalReceiptDto(
    val receiptNumber: String,
    val orderNumber: String,
    val invoiceNumber: String,
    val issuedAt: LocalDateTime,
    val shopOwnerName: String,
    val supplierName: String,
    val paymentMethod: PaymentMethod,
    val paymentReference: String?,
    val totalAmount: BigDecimal,
    val amountPaid: BigDecimal
)

data class OrderResponse(
    val id: Long?,
    val orderNumber: String,
    val invoiceNumber: String,
    val shopOwnerId: String,
    val shopOwnerName: String,
    val shopOwnerPhone: String,
    val supplierId: String,
    val supplierName: String,
    val region: String,
    val items: List<OrderItemDto>,
    val subtotal: BigDecimal,
    val taxAmount: BigDecimal,
    val deliveryCharges: BigDecimal,
    val totalAmount: BigDecimal,
    val paymentMethod: PaymentMethod,
    val paymentStatus: PaymentStatus,
    val amountPaid: BigDecimal,
    val amountOutstanding: BigDecimal,
    val paymentReference: String?,
    val orderStatus: OrderStatus,
    val dispatchDate: LocalDateTime?,
    val expectedDeliveryDate: LocalDate?,
    val deliveryStatus: DeliveryStatus,
    val deliveryConfirmedAt: LocalDateTime?,
    val deliveryConfirmedBy: String?,
    val createdAt: LocalDateTime,
    val updatedAt: LocalDateTime,
    val cancellationReason: String?,
    val cancelledAt: LocalDateTime?,
    val receipt: DigitalReceiptDto? = null
)

data class AdjustOrderRequest(
    val items: List<OrderItemRequest> = emptyList()
)

data class CancelOrderRequest(
    val invoiceNumber: String = "",
    val reason: String? = null
)

data class ConfirmDeliveryRequest(
    val confirmedBy: String = "",
    val payOnDeliverySettlementMethod: PaymentMethod? = null,
    val paymentReference: String? = null
)

data class ManufacturerDemandAggregationDto(
    val productId: String,
    val productName: String,
    val region: String,
    val totalStockRequired: Int,
    val requiredByDate: LocalDate?,
    val pendingOrdersCount: Int,
    val suppliersInvolved: List<String>
)

// Refund / Return DTOs
data class SaleLookupRequest(
    @JsonProperty("etimsReceiptNumber")
    @JsonAlias("eTimsReceiptNumber", "etimsReceiptNumber", "e_tims_receipt_number", "ETimsReceiptNumber")
    val eTimsReceiptNumber: String = "",
    val supplierInvoiceNumber: String = ""
)

data class SaleItemDto(
    val productId: String,
    val productName: String,
    val quantitySold: Int,
    val quantityRefunded: Int,
    val remainingEligibleQuantity: Int,
    val unitPrice: BigDecimal,
    val taxRate: BigDecimal,
    val totalPrice: BigDecimal,
    val taxAmount: BigDecimal
)

data class SaleResponse(
    val saleReference: String,
    @JsonProperty("eTimsReceiptNumber")
    val eTimsReceiptNumber: String,
    val supplierInvoiceNumber: String,
    val customerId: String,
    val customerName: String,
    val items: List<SaleItemDto>,
    val subtotal: BigDecimal,
    val taxAmount: BigDecimal,
    val totalAmount: BigDecimal,
    val saleDate: LocalDateTime,
    val status: SaleStatus,
    val linkedOrderNumber: String?
) {
    @get:JsonProperty("etimsReceiptNumber")
    val etimsReceiptNumberAlias: String
        get() = eTimsReceiptNumber
}

data class RefundItemRequest(
    val productId: String = "",
    val quantity: Int = 0
)

data class CreateRefundRequest(
    val saleReference: String = "",
    @JsonProperty("etimsReceiptNumber")
    @JsonAlias("eTimsReceiptNumber", "etimsReceiptNumber", "e_tims_receipt_number", "ETimsReceiptNumber")
    val eTimsReceiptNumber: String = "",
    val supplierInvoiceNumber: String = "",
    val items: List<RefundItemRequest> = emptyList(),
    val reason: String = "",
    val requestedBy: String = ""
)

data class RefundItemPreviewDto(
    val productId: String,
    val productName: String,
    val quantity: Int,
    val unitPrice: BigDecimal,
    val taxRate: BigDecimal,
    val refundAmount: BigDecimal,
    val taxRefundAmount: BigDecimal
)

data class RefundCalculationPreviewResponse(
    val saleReference: String,
    val items: List<RefundItemPreviewDto>,
    val refundSubtotal: BigDecimal,
    val refundTaxAmount: BigDecimal,
    val refundTotalAmount: BigDecimal
)

data class ETimsCreditNoteDto(
    val creditNoteNumber: String,
    val originalReceiptNumber: String,
    val supplierInvoiceNumber: String,
    val creditAmount: BigDecimal,
    val taxAmount: BigDecimal,
    val totalCreditAmount: BigDecimal,
    val issuedAt: LocalDateTime,
    val kraStatus: String,
    val qrCodeData: String
)

data class RefundResponse(
    val refundNumber: String,
    val saleReference: String,
    @JsonProperty("eTimsReceiptNumber")
    val eTimsReceiptNumber: String,
    val supplierInvoiceNumber: String,
    val creditNote: ETimsCreditNoteDto,
    val refundSubtotal: BigDecimal,
    val refundTaxAmount: BigDecimal,
    val refundTotalAmount: BigDecimal,
    val reason: String,
    val status: RefundStatus,
    val requestedBy: String,
    val createdAt: LocalDateTime,
    val reversedLinkedOrderNumber: String?,
    val inventoryReversed: Boolean,
    val items: List<RefundItemPreviewDto>
) {
    @get:JsonProperty("etimsReceiptNumber")
    val etimsReceiptNumberAlias: String
        get() = eTimsReceiptNumber
}
