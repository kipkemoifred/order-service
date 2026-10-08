package ke.co.tracom.order_service.controller

import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.Parameter
import io.swagger.v3.oas.annotations.tags.Tag
import ke.co.tracom.order_service.domain.dto.*
import ke.co.tracom.order_service.exception.*
import ke.co.tracom.order_service.service.CatalogService
import ke.co.tracom.order_service.service.ManufacturerService
import ke.co.tracom.order_service.service.OrderService
import ke.co.tracom.order_service.service.RefundService
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*

@Tag(name = "1. Catalog & Availability", description = "Browse supplier catalogs, check inventory, and compute cart totals")
@RestController
@RequestMapping("/api/catalog")
@CrossOrigin(origins = ["*"])
class CatalogController(
    private val catalogService: CatalogService
) {
    @Operation(summary = "List all suppliers", description = "Retrieves active suppliers along with regional and product summaries")
    @GetMapping("/suppliers")
    fun listSuppliers(): ResponseEntity<List<SupplierSummaryDto>> {
        return ResponseEntity.ok(catalogService.listSuppliers())
    }

    @Operation(summary = "Get supplier catalog", description = "Returns available products, stock levels, and unit prices for a specific supplier")
    @GetMapping("/suppliers/{supplierId}/products")
    fun getSupplierCatalog(
        @Parameter(description = "Unique supplier identifier, e.g. SUP-BROOKSIDE")
        @PathVariable supplierId: String
    ): ResponseEntity<List<ProductDto>> {
        return ResponseEntity.ok(catalogService.getSupplierCatalog(supplierId))
    }

    @Operation(summary = "List all products", description = "Lists all available products across all suppliers")
    @GetMapping("/products")
    fun getAllProducts(): ResponseEntity<List<ProductDto>> {
        return ResponseEntity.ok(catalogService.getAllProducts())
    }

    @Operation(summary = "Calculate cart & check availability", description = "Validates item stock levels, calculates net subtotal, 16% VAT, delivery charges, and total payable")
    @PostMapping("/cart/calculate")
    fun calculateCart(@RequestBody request: CartCalculationRequest): ResponseEntity<OrderSummaryResponse> {
        return ResponseEntity.ok(catalogService.calculateCart(request))
    }
}

@Tag(name = "2. Orders Module (UC-05)", description = "Place orders, pending orders tracking, adjustments, cancellations, and delivery confirmation")
@RestController
@RequestMapping("/api/orders")
@CrossOrigin(origins = ["*"])
class OrderController(
    private val orderService: OrderService
) {
    @Operation(summary = "Place New Order", description = "Processes new order with prepayment (M-Pesa STK push / Card) or Pay on Delivery (AP1). Adjusts inventory and notifies supplier.")
    @PostMapping
    fun createOrder(@RequestBody request: CreateOrderRequest): ResponseEntity<OrderResponse> {
        val response = orderService.createOrder(request)
        return ResponseEntity.status(HttpStatus.CREATED).body(response)
    }

    @Operation(summary = "View Pending Orders", description = "Retrieves orders pending delivery, dispatched, or awaiting confirmation with full pricing and delivery breakdown")
    @GetMapping("/pending")
    fun getPendingOrders(
        @Parameter(description = "Optional filter by retailer/shop owner ID")
        @RequestParam(required = false) shopOwnerId: String?,
        @Parameter(description = "Optional filter by supplier ID")
        @RequestParam(required = false) supplierId: String?
    ): ResponseEntity<List<OrderResponse>> {
        return ResponseEntity.ok(orderService.getPendingOrders(shopOwnerId, supplierId))
    }

    @Operation(summary = "Get Order by Order Number", description = "Retrieves details of an order using its business order number")
    @GetMapping("/{orderNumber}")
    fun getOrderByOrderNumber(
        @Parameter(description = "Business order number, e.g. ORD-20261008-XXXX")
        @PathVariable orderNumber: String
    ): ResponseEntity<OrderResponse> {
        return ResponseEntity.ok(orderService.getOrderByOrderNumber(orderNumber))
    }

    @Operation(summary = "Get Order by Invoice Number", description = "Retrieves order details using its invoice number")
    @GetMapping("/by-invoice/{invoiceNumber}")
    fun getOrderByInvoiceNumber(
        @Parameter(description = "Invoice reference number, e.g. INV-20261008-XXXX")
        @PathVariable invoiceNumber: String
    ): ResponseEntity<OrderResponse> {
        return ResponseEntity.ok(orderService.getOrderByInvoiceNumber(invoiceNumber))
    }

    @Operation(summary = "Adjust Order (AP 3)", description = "Adds or removes items, updates quantities, recalculates total amount, and adjusts inventory reservations")
    @PutMapping("/{orderNumber}/adjust")
    fun adjustOrder(
        @Parameter(description = "Order number to adjust")
        @PathVariable orderNumber: String,
        @RequestBody request: AdjustOrderRequest
    ): ResponseEntity<OrderResponse> {
        return ResponseEntity.ok(orderService.adjustOrder(orderNumber, request))
    }

    @Operation(summary = "Cancel Order by Invoice (AP 4)", description = "Cancels a pending order after entering the invoice number, releases reserved inventory, and notifies the supplier")
    @PostMapping("/cancel")
    fun cancelOrder(@RequestBody request: CancelOrderRequest): ResponseEntity<OrderResponse> {
        return ResponseEntity.ok(orderService.cancelOrder(request))
    }

    @Operation(summary = "Confirm Delivery (AP-03)", description = "Confirms goods received by store owner, sets status to DELIVERED, records timestamp & user, and settles Pay on Delivery payments")
    @PostMapping("/{orderNumber}/confirm-delivery")
    fun confirmDelivery(
        @Parameter(description = "Order number delivered")
        @PathVariable orderNumber: String,
        @RequestBody request: ConfirmDeliveryRequest
    ): ResponseEntity<OrderResponse> {
        return ResponseEntity.ok(orderService.confirmDelivery(orderNumber, request))
    }
}

@Tag(name = "3. Manufacturer Portal", description = "Aggregated demand tracking across supply chain regions for production and loan financing planning")
@RestController
@RequestMapping("/api/manufacturer")
@CrossOrigin(origins = ["*"])
class ManufacturerController(
    private val manufacturerService: ManufacturerService
) {
    @Operation(summary = "Aggregated Pending Orders Demand", description = "Aggregates total stock required and required-by dates per product and region across distributors and retailers")
    @GetMapping("/demand")
    fun getAggregatedDemand(): ResponseEntity<List<ManufacturerDemandAggregationDto>> {
        return ResponseEntity.ok(manufacturerService.getAggregatedDemand())
    }
}

@Tag(name = "4. Refunds & Returns (UC-06)", description = "Sale retrieval by e-TIMS receipt & supplier invoice, tax computation, and KRA e-TIMS Credit Note issuance")
@RestController
@RequestMapping("/api/refunds")
@CrossOrigin(origins = ["*"])
class RefundController(
    private val refundService: RefundService
) {
    @Operation(summary = "Lookup Original Sale", description = "Retrieves prior sale and eligible items using KRA e-TIMS receipt number and supplier invoice number")
    @GetMapping("/lookup-sale")
    fun lookupSale(
        @Parameter(description = "KRA e-TIMS receipt number, e.g. ETIMS-REC-88492041")
        @RequestParam(required = false) eTimsReceiptNumber: String?,
        @Parameter(description = "KRA e-TIMS receipt number alias, e.g. ETIMS-REC-88492041")
        @RequestParam(required = false) etimsReceiptNumber: String?,
        @Parameter(description = "Supplier invoice number, e.g. INV-SUP-99102")
        @RequestParam supplierInvoiceNumber: String
    ): ResponseEntity<SaleResponse> {
        val receiptNumber = (eTimsReceiptNumber ?: etimsReceiptNumber)
            ?: throw MissingMandatoryInformationException("eTimsReceiptNumber is required")
        return ResponseEntity.ok(refundService.lookupSale(receiptNumber, supplierInvoiceNumber))
    }

    @Operation(summary = "Process Refund & Issue KRA Credit Note", description = "Computes adjusted prices and affected tax, issues Credit Note to KRA e-TIMS, reverses stock, and cancels linked unfulfilled backorders")
    @PostMapping
    fun processRefund(@RequestBody request: CreateRefundRequest): ResponseEntity<RefundResponse> {
        val response = refundService.processRefund(request)
        return ResponseEntity.status(HttpStatus.CREATED).body(response)
    }
}
