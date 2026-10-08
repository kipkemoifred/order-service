package ke.co.tracom.order_service.service

import ke.co.tracom.order_service.domain.dto.*
import ke.co.tracom.order_service.domain.model.*
import ke.co.tracom.order_service.exception.*
import ke.co.tracom.order_service.repository.*
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.UUID

@Service
class PaymentGatewayService {

    fun processMpesaPayment(phoneNumber: String, amount: BigDecimal, simulateFailure: Boolean): String {
        if (simulateFailure) {
            throw PaymentFailedException("Payment could not be completed. Please try again")
        }
        val cleanPhone = phoneNumber.replace("+", "").trim()
        val isValidPhone = cleanPhone.matches(Regex("^(254|0)?(7|1)\\d{8}$"))
        if (!isValidPhone) {
            throw PaymentFailedException("Payment could not be completed. Please try again")
        }
        val txId = "WS" + UUID.randomUUID().toString().replace("-", "").take(10).uppercase()
        return "MPESA-$txId"
    }

    fun processCardPayment(cardNumber: String?, amount: BigDecimal, simulateFailure: Boolean): String {
        if (simulateFailure || cardNumber.isNullOrBlank() || cardNumber.replace(" ", "").length < 12) {
            throw PaymentFailedException("Payment could not be completed. Please try again")
        }
        val txId = "CARD" + UUID.randomUUID().toString().replace("-", "").take(10).uppercase()
        return "CRD-$txId"
    }
}

@Service
class ETimsService {

    fun issueCreditNote(
        originalReceiptNumber: String,
        supplierInvoiceNumber: String,
        creditAmount: BigDecimal,
        taxAmount: BigDecimal,
        buyerPin: String? = null
    ): ETimsCreditNoteDto {
        val totalCredit = creditAmount.add(taxAmount).setScale(2, RoundingMode.HALF_UP)
        val cnNumber = "KRA-CN-" + DateTimeFormatter.ofPattern("yyyyMMdd").format(LocalDate.now()) + "-" +
                UUID.randomUUID().toString().take(6).uppercase()
        val qrCode = "https://etims.kra.go.ke/verify?cn=$cnNumber&orig=$originalReceiptNumber&amt=$totalCredit"

        return ETimsCreditNoteDto(
            creditNoteNumber = cnNumber,
            originalReceiptNumber = originalReceiptNumber,
            supplierInvoiceNumber = supplierInvoiceNumber,
            creditAmount = creditAmount.setScale(2, RoundingMode.HALF_UP),
            taxAmount = taxAmount.setScale(2, RoundingMode.HALF_UP),
            totalCreditAmount = totalCredit,
            issuedAt = LocalDateTime.now(),
            kraStatus = "ACCEPTED",
            qrCodeData = qrCode
        )
    }
}

@Service
class CatalogService(
    private val productRepository: ProductRepository
) {
    fun listSuppliers(): List<SupplierSummaryDto> {
        val products = productRepository.findAll()
        return products.groupBy { it.supplierId }
            .map { (supplierId, items) ->
                SupplierSummaryDto(
                    supplierId = supplierId,
                    supplierName = items.first().supplierName,
                    productCount = items.size,
                    region = items.first().region
                )
            }
    }

    fun getSupplierCatalog(supplierId: String): List<ProductDto> {
        return productRepository.findBySupplierId(supplierId).map { it.toDto() }
    }

    fun getAllProducts(): List<ProductDto> {
        return productRepository.findAllByOrderBySupplierIdAsc().map { it.toDto() }
    }

    fun calculateCart(request: CartCalculationRequest): OrderSummaryResponse {
        var subtotal = BigDecimal.ZERO
        var totalTax = BigDecimal.ZERO
        var allAvailable = true

        val details = request.items.map { itemReq ->
            val product = productRepository.findByProductId(itemReq.productId)
                ?: throw OutOfStockException("The item is currently out of stock")

            val available = product.availableStock >= itemReq.quantity
            if (!available) {
                allAvailable = false
            }

            val lineTotal = product.unitPrice.multiply(BigDecimal(itemReq.quantity)).setScale(2, RoundingMode.HALF_UP)
            val lineTax = lineTotal.multiply(product.taxRate).setScale(2, RoundingMode.HALF_UP)

            subtotal = subtotal.add(lineTotal)
            totalTax = totalTax.add(lineTax)

            CalculatedItemDetail(
                productId = product.productId,
                productName = product.name,
                quantity = itemReq.quantity,
                unitPrice = product.unitPrice,
                taxRate = product.taxRate,
                lineTotal = lineTotal,
                lineTax = lineTax,
                isAvailable = available,
                availableStock = product.availableStock
            )
        }

        val deliveryCharges = if (request.items.isEmpty()) BigDecimal.ZERO else BigDecimal("350.00")
        val grandTotal = subtotal.add(totalTax).add(deliveryCharges).setScale(2, RoundingMode.HALF_UP)

        return OrderSummaryResponse(
            subtotal = subtotal.setScale(2, RoundingMode.HALF_UP),
            taxAmount = totalTax.setScale(2, RoundingMode.HALF_UP),
            deliveryCharges = deliveryCharges,
            totalAmount = grandTotal,
            itemDetails = details,
            allAvailable = allAvailable
        )
    }

    private fun Product.toDto() = ProductDto(
        productId = this.productId,
        supplierId = this.supplierId,
        supplierName = this.supplierName,
        name = this.name,
        category = this.category,
        unitPrice = this.unitPrice,
        taxRate = this.taxRate,
        stockLevel = this.stockLevel,
        reservedStock = this.reservedStock,
        availableStock = this.availableStock,
        region = this.region
    )
}

@Service
@Transactional
class OrderService(
    private val orderRepository: OrderRepository,
    private val productRepository: ProductRepository,
    private val inventoryMovementRepository: InventoryMovementRepository,
    private val supplierNotificationRepository: SupplierNotificationRepository,
    private val paymentGatewayService: PaymentGatewayService
) {

    fun createOrder(request: CreateOrderRequest): OrderResponse {
        if (request.items.isEmpty()) {
            throw MissingMandatoryInformationException("Missing mandatory information: Order items are required")
        }

        // Validate availability per item
        val productMap = mutableMapOf<String, Product>()
        for (itemReq in request.items) {
            val product = productRepository.findByProductId(itemReq.productId)
                ?: throw OutOfStockException("The item is currently out of stock")
            if (product.availableStock < itemReq.quantity) {
                throw OutOfStockException("The item is currently out of stock")
            }
            productMap[itemReq.productId] = product
        }

        // Calculate order amounts
        var subtotal = BigDecimal.ZERO
        var totalTax = BigDecimal.ZERO
        val orderItems = mutableListOf<OrderItem>()

        for (itemReq in request.items) {
            val product = productMap[itemReq.productId]!!
            val lineTotal = product.unitPrice.multiply(BigDecimal(itemReq.quantity)).setScale(2, RoundingMode.HALF_UP)
            val lineTax = lineTotal.multiply(product.taxRate).setScale(2, RoundingMode.HALF_UP)

            subtotal = subtotal.add(lineTotal)
            totalTax = totalTax.add(lineTax)

            val orderItem = OrderItem(
                productId = product.productId,
                productName = product.name,
                quantity = itemReq.quantity,
                unitPrice = product.unitPrice,
                taxRate = product.taxRate,
                lineTotal = lineTotal,
                lineTax = lineTax
            )
            orderItems.add(orderItem)
        }

        val deliveryCharges = BigDecimal("350.00")
        val totalPayable = subtotal.add(totalTax).add(deliveryCharges).setScale(2, RoundingMode.HALF_UP)

        // Process Payment according to selected flow
        var paymentReference: String? = null
        val paymentStatus: PaymentStatus
        val amountPaid: BigDecimal
        val amountOutstanding: BigDecimal

        when (request.paymentMethod) {
            PaymentMethod.MPESA -> {
                val phone = request.mpesaPhoneNumber
                    ?: throw MissingMandatoryInformationException("Missing mandatory information: M-Pesa phone number required")
                paymentReference = paymentGatewayService.processMpesaPayment(phone, totalPayable, request.simulatePaymentFailure)
                paymentStatus = PaymentStatus.PAID
                amountPaid = totalPayable
                amountOutstanding = BigDecimal.ZERO
            }
            PaymentMethod.CARD -> {
                val card = request.cardNumber
                    ?: throw MissingMandatoryInformationException("Missing mandatory information: Card number required")
                paymentReference = paymentGatewayService.processCardPayment(card, totalPayable, request.simulatePaymentFailure)
                paymentStatus = PaymentStatus.PAID
                amountPaid = totalPayable
                amountOutstanding = BigDecimal.ZERO
            }
            PaymentMethod.PAY_ON_DELIVERY -> {
                // Check order history for shop owner
                val pastOrdersCount = orderRepository.countByShopOwnerId(request.shopOwnerId)
                paymentStatus = PaymentStatus.CONFIRMED_UNPAID
                amountPaid = BigDecimal.ZERO
                amountOutstanding = totalPayable
            }
        }

        // Generate business references
        val todayStr = DateTimeFormatter.ofPattern("yyyyMMdd").format(LocalDate.now())
        val randomSuffix = UUID.randomUUID().toString().take(6).uppercase()
        val orderNumber = "ORD-$todayStr-$randomSuffix"
        val invoiceNumber = "INV-$todayStr-$randomSuffix"

        val supplierName = productMap.values.first().supplierName

        val order = Order(
            orderNumber = orderNumber,
            invoiceNumber = invoiceNumber,
            shopOwnerId = request.shopOwnerId,
            shopOwnerName = request.shopOwnerName,
            shopOwnerPhone = request.shopOwnerPhone,
            supplierId = request.supplierId,
            supplierName = supplierName,
            region = request.region,
            subtotal = subtotal,
            taxAmount = totalTax,
            deliveryCharges = deliveryCharges,
            totalAmount = totalPayable,
            paymentMethod = request.paymentMethod,
            paymentStatus = paymentStatus,
            amountPaid = amountPaid,
            amountOutstanding = amountOutstanding,
            paymentReference = paymentReference,
            orderStatus = OrderStatus.PENDING_FULFILMENT,
            deliveryStatus = DeliveryStatus.PENDING,
            expectedDeliveryDate = LocalDate.now().plusDays(2),
            createdAt = LocalDateTime.now(),
            updatedAt = LocalDateTime.now()
        )

        for (item in orderItems) {
            item.order = order
            order.items.add(item)
        }

        val savedOrder = orderRepository.save(order)

        // Adjust inventory
        for (itemReq in request.items) {
            val product = productMap[itemReq.productId]!!
            if (paymentStatus == PaymentStatus.PAID) {
                // Prepayment: decrement sold quantity directly & record movement
                product.stockLevel -= itemReq.quantity
                inventoryMovementRepository.save(
                    InventoryMovement(
                        productId = product.productId,
                        supplierId = product.supplierId,
                        orderNumber = orderNumber,
                        movementType = InventoryMovementType.DEDUCTION_SALE,
                        quantity = itemReq.quantity,
                        notes = "Prepaid Order $orderNumber deduction"
                    )
                )
            } else {
                // Pay on Delivery: reserve inventory
                product.reservedStock += itemReq.quantity
                inventoryMovementRepository.save(
                    InventoryMovement(
                        productId = product.productId,
                        supplierId = product.supplierId,
                        orderNumber = orderNumber,
                        movementType = InventoryMovementType.RESERVATION,
                        quantity = itemReq.quantity,
                        notes = "Pay on Delivery Order $orderNumber reservation"
                    )
                )
            }
            productRepository.save(product)
        }

        // Notify Supplier
        supplierNotificationRepository.save(
            SupplierNotification(
                supplierId = request.supplierId,
                orderNumber = orderNumber,
                notificationType = NotificationType.NEW_ORDER,
                message = "New order $orderNumber received from ${request.shopOwnerName} for total KES $totalPayable (${paymentStatus.name})"
            )
        )

        return savedOrder.toOrderResponse()
    }

    @Transactional(readOnly = true)
    fun getPendingOrders(shopOwnerId: String? = null, supplierId: String? = null): List<OrderResponse> {
        val pendingStatuses = listOf(
            OrderStatus.PENDING_FULFILMENT,
            OrderStatus.DISPATCHED,
            OrderStatus.AWAITING_DELIVERY
        )
        val orders = orderRepository.findByOrderStatusIn(pendingStatuses)
        return orders
            .filter { shopOwnerId == null || it.shopOwnerId == shopOwnerId }
            .filter { supplierId == null || it.supplierId == supplierId }
            .map { it.toOrderResponse() }
    }

    @Transactional(readOnly = true)
    fun getOrderByOrderNumber(orderNumber: String): OrderResponse {
        val order = orderRepository.findByOrderNumber(orderNumber)
            ?: throw OrderNotFoundException("Order with number $orderNumber not found")
        return order.toOrderResponse()
    }

    @Transactional(readOnly = true)
    fun getOrderByInvoiceNumber(invoiceNumber: String): OrderResponse {
        val order = orderRepository.findByInvoiceNumber(invoiceNumber)
            ?: throw OrderNotFoundException("Order with invoice number $invoiceNumber not found")
        return order.toOrderResponse()
    }

    fun adjustOrder(orderNumber: String, request: AdjustOrderRequest): OrderResponse {
        val order = orderRepository.findByOrderNumber(orderNumber)
            ?: throw OrderNotFoundException("Order with number $orderNumber not found")

        if (order.orderStatus != OrderStatus.PENDING_FULFILMENT) {
            throw InvalidOrderStateException("Cannot adjust order in state ${order.orderStatus}")
        }

        // Release existing reservations/deductions first to properly re-validate
        for (item in order.items) {
            val product = productRepository.findByProductId(item.productId)
            if (product != null) {
                if (order.paymentStatus == PaymentStatus.PAID) {
                    product.stockLevel += item.quantity
                } else {
                    product.reservedStock = (product.reservedStock - item.quantity).coerceAtLeast(0)
                }
                productRepository.save(product)
            }
        }

        // Validate new items availability
        val productMap = mutableMapOf<String, Product>()
        for (itemReq in request.items) {
            val product = productRepository.findByProductId(itemReq.productId)
                ?: throw OutOfStockException("The item is currently out of stock")
            if (product.availableStock < itemReq.quantity) {
                // Rollback stock levels before throwing
                rollbackOrderInventory(order)
                throw OutOfStockException("The item is currently out of stock")
            }
            productMap[itemReq.productId] = product
        }

        // Clear existing items and recalculate
        order.items.clear()
        var subtotal = BigDecimal.ZERO
        var totalTax = BigDecimal.ZERO

        for (itemReq in request.items) {
            val product = productMap[itemReq.productId]!!
            val lineTotal = product.unitPrice.multiply(BigDecimal(itemReq.quantity)).setScale(2, RoundingMode.HALF_UP)
            val lineTax = lineTotal.multiply(product.taxRate).setScale(2, RoundingMode.HALF_UP)

            subtotal = subtotal.add(lineTotal)
            totalTax = totalTax.add(lineTax)

            val orderItem = OrderItem(
                order = order,
                productId = product.productId,
                productName = product.name,
                quantity = itemReq.quantity,
                unitPrice = product.unitPrice,
                taxRate = product.taxRate,
                lineTotal = lineTotal,
                lineTax = lineTax
            )
            order.items.add(orderItem)
        }

        val deliveryCharges = if (request.items.isEmpty()) BigDecimal.ZERO else BigDecimal("350.00")
        val grandTotal = subtotal.add(totalTax).add(deliveryCharges).setScale(2, RoundingMode.HALF_UP)

        order.subtotal = subtotal
        order.taxAmount = totalTax
        order.deliveryCharges = deliveryCharges
        order.totalAmount = grandTotal
        order.updatedAt = LocalDateTime.now()

        if (order.paymentStatus == PaymentStatus.PAID) {
            // Adjust amount paid / outstanding
            order.amountPaid = grandTotal
            order.amountOutstanding = BigDecimal.ZERO
        } else {
            order.amountOutstanding = grandTotal
        }

        // Apply new reservations/deductions
        for (itemReq in request.items) {
            val product = productMap[itemReq.productId]!!
            if (order.paymentStatus == PaymentStatus.PAID) {
                product.stockLevel -= itemReq.quantity
                inventoryMovementRepository.save(
                    InventoryMovement(
                        productId = product.productId,
                        supplierId = product.supplierId,
                        orderNumber = order.orderNumber,
                        movementType = InventoryMovementType.DEDUCTION_SALE,
                        quantity = itemReq.quantity,
                        notes = "Order ${order.orderNumber} adjustment deduction"
                    )
                )
            } else {
                product.reservedStock += itemReq.quantity
                inventoryMovementRepository.save(
                    InventoryMovement(
                        productId = product.productId,
                        supplierId = product.supplierId,
                        orderNumber = order.orderNumber,
                        movementType = InventoryMovementType.RESERVATION,
                        quantity = itemReq.quantity,
                        notes = "Order ${order.orderNumber} adjustment reservation"
                    )
                )
            }
            productRepository.save(product)
        }

        val updatedOrder = orderRepository.save(order)

        supplierNotificationRepository.save(
            SupplierNotification(
                supplierId = order.supplierId,
                orderNumber = order.orderNumber,
                notificationType = NotificationType.ORDER_ADJUSTED,
                message = "Order ${order.orderNumber} was adjusted. New total: KES $grandTotal"
            )
        )

        return updatedOrder.toOrderResponse()
    }

    private fun rollbackOrderInventory(order: Order) {
        for (item in order.items) {
            val product = productRepository.findByProductId(item.productId)
            if (product != null) {
                if (order.paymentStatus == PaymentStatus.PAID) {
                    product.stockLevel -= item.quantity
                } else {
                    product.reservedStock += item.quantity
                }
                productRepository.save(product)
            }
        }
    }

    fun cancelOrder(request: CancelOrderRequest): OrderResponse {
        val order = orderRepository.findByInvoiceNumber(request.invoiceNumber)
            ?: throw OrderNotFoundException("Order with invoice number ${request.invoiceNumber} not found")

        if (order.orderStatus == OrderStatus.DELIVERED) {
            throw InvalidOrderStateException("Cannot cancel an already delivered order")
        }
        if (order.orderStatus == OrderStatus.CANCELLED) {
            throw InvalidOrderStateException("Order is already cancelled")
        }

        order.orderStatus = OrderStatus.CANCELLED
        order.cancellationReason = request.reason ?: "Cancelled by shop owner"
        order.cancelledAt = LocalDateTime.now()
        order.updatedAt = LocalDateTime.now()

        // Release inventory reservations or restock
        for (item in order.items) {
            val product = productRepository.findByProductId(item.productId)
            if (product != null) {
                if (order.paymentStatus == PaymentStatus.PAID) {
                    product.stockLevel += item.quantity
                    inventoryMovementRepository.save(
                        InventoryMovement(
                            productId = product.productId,
                            supplierId = product.supplierId,
                            orderNumber = order.orderNumber,
                            movementType = InventoryMovementType.RESTOCK_RETURN,
                            quantity = item.quantity,
                            notes = "Order ${order.orderNumber} cancellation stock restore"
                        )
                    )
                } else {
                    product.reservedStock = (product.reservedStock - item.quantity).coerceAtLeast(0)
                    inventoryMovementRepository.save(
                        InventoryMovement(
                            productId = product.productId,
                            supplierId = product.supplierId,
                            orderNumber = order.orderNumber,
                            movementType = InventoryMovementType.RESERVATION_RELEASE,
                            quantity = item.quantity,
                            notes = "Order ${order.orderNumber} cancellation reservation release"
                        )
                    )
                }
                productRepository.save(product)
            }
        }

        val updatedOrder = orderRepository.save(order)

        supplierNotificationRepository.save(
            SupplierNotification(
                supplierId = order.supplierId,
                orderNumber = order.orderNumber,
                notificationType = NotificationType.ORDER_CANCELLED,
                message = "Order ${order.orderNumber} (Invoice ${order.invoiceNumber}) was cancelled."
            )
        )

        return updatedOrder.toOrderResponse()
    }

    fun confirmDelivery(orderNumber: String, request: ConfirmDeliveryRequest): OrderResponse {
        val order = orderRepository.findByOrderNumber(orderNumber)
            ?: throw OrderNotFoundException("Order with number $orderNumber not found")

        if (order.orderStatus == OrderStatus.CANCELLED) {
            throw InvalidOrderStateException("Cannot confirm delivery on a cancelled order")
        }
        if (order.orderStatus == OrderStatus.DELIVERED) {
            return order.toOrderResponse()
        }

        order.orderStatus = OrderStatus.DELIVERED
        order.deliveryStatus = DeliveryStatus.DELIVERED
        order.deliveryConfirmedAt = LocalDateTime.now()
        order.deliveryConfirmedBy = request.confirmedBy
        order.updatedAt = LocalDateTime.now()

        // If it was pay on delivery, finalize payment settlement and decrement physical inventory
        if (order.paymentStatus != PaymentStatus.PAID) {
            order.paymentStatus = PaymentStatus.PAID
            order.amountPaid = order.totalAmount
            order.amountOutstanding = BigDecimal.ZERO
            order.paymentReference = request.paymentReference ?: "POD-SETTLED-${UUID.randomUUID().toString().take(6).uppercase()}"

            // Convert reserved stock into deducted sale stock
            for (item in order.items) {
                val product = productRepository.findByProductId(item.productId)
                if (product != null) {
                    product.reservedStock = (product.reservedStock - item.quantity).coerceAtLeast(0)
                    product.stockLevel = (product.stockLevel - item.quantity).coerceAtLeast(0)
                    productRepository.save(product)

                    inventoryMovementRepository.save(
                        InventoryMovement(
                            productId = product.productId,
                            supplierId = product.supplierId,
                            orderNumber = order.orderNumber,
                            movementType = InventoryMovementType.DEDUCTION_SALE,
                            quantity = item.quantity,
                            notes = "Pay on delivery settled and stock deducted for ${order.orderNumber}"
                        )
                    )
                }
            }
        }

        val updatedOrder = orderRepository.save(order)

        supplierNotificationRepository.save(
            SupplierNotification(
                supplierId = order.supplierId,
                orderNumber = order.orderNumber,
                notificationType = NotificationType.ORDER_DELIVERED,
                message = "Order ${order.orderNumber} has been delivered to ${order.shopOwnerName} and confirmed by ${request.confirmedBy}."
            )
        )

        return updatedOrder.toOrderResponse()
    }

    private fun Order.toOrderResponse(): OrderResponse {
        val itemDtos = this.items.map {
            OrderItemDto(
                productId = it.productId,
                productName = it.productName,
                quantity = it.quantity,
                unitPrice = it.unitPrice,
                taxRate = it.taxRate,
                lineTotal = it.lineTotal,
                lineTax = it.lineTax
            )
        }

        val receipt = if (this.paymentStatus == PaymentStatus.PAID) {
            DigitalReceiptDto(
                receiptNumber = "REC-" + this.invoiceNumber.removePrefix("INV-"),
                orderNumber = this.orderNumber,
                invoiceNumber = this.invoiceNumber,
                issuedAt = this.updatedAt,
                shopOwnerName = this.shopOwnerName,
                supplierName = this.supplierName,
                paymentMethod = this.paymentMethod,
                paymentReference = this.paymentReference,
                totalAmount = this.totalAmount,
                amountPaid = this.amountPaid
            )
        } else null

        return OrderResponse(
            id = this.id,
            orderNumber = this.orderNumber,
            invoiceNumber = this.invoiceNumber,
            shopOwnerId = this.shopOwnerId,
            shopOwnerName = this.shopOwnerName,
            shopOwnerPhone = this.shopOwnerPhone,
            supplierId = this.supplierId,
            supplierName = this.supplierName,
            region = this.region,
            items = itemDtos,
            subtotal = this.subtotal,
            taxAmount = this.taxAmount,
            deliveryCharges = this.deliveryCharges,
            totalAmount = this.totalAmount,
            paymentMethod = this.paymentMethod,
            paymentStatus = this.paymentStatus,
            amountPaid = this.amountPaid,
            amountOutstanding = this.amountOutstanding,
            paymentReference = this.paymentReference,
            orderStatus = this.orderStatus,
            dispatchDate = this.dispatchDate,
            expectedDeliveryDate = this.expectedDeliveryDate,
            deliveryStatus = this.deliveryStatus,
            deliveryConfirmedAt = this.deliveryConfirmedAt,
            deliveryConfirmedBy = this.deliveryConfirmedBy,
            createdAt = this.createdAt,
            updatedAt = this.updatedAt,
            cancellationReason = this.cancellationReason,
            cancelledAt = this.cancelledAt,
            receipt = receipt
        )
    }
}

@Service
class ManufacturerService(
    private val orderRepository: OrderRepository
) {
    fun getAggregatedDemand(): List<ManufacturerDemandAggregationDto> {
        val pendingStatuses = listOf(
            OrderStatus.PENDING_FULFILMENT,
            OrderStatus.DISPATCHED,
            OrderStatus.AWAITING_DELIVERY
        )
        val pendingOrders = orderRepository.findByOrderStatusIn(pendingStatuses)

        data class GroupKey(val productId: String, val region: String)

        val groups = mutableMapOf<GroupKey, MutableList<Pair<Order, OrderItem>>>()

        for (order in pendingOrders) {
            for (item in order.items) {
                val key = GroupKey(item.productId, order.region)
                groups.computeIfAbsent(key) { mutableListOf() }.add(Pair(order, item))
            }
        }

        return groups.map { (key, pairs) ->
            val sampleItem = pairs.first().second
            val totalQty = pairs.sumOf { it.second.quantity }
            val earliestExpected = pairs.mapNotNull { it.first.expectedDeliveryDate }.minOrNull()
            val distinctOrders = pairs.map { it.first.orderNumber }.distinct().size
            val suppliers = pairs.map { it.first.supplierName }.distinct()

            ManufacturerDemandAggregationDto(
                productId = key.productId,
                productName = sampleItem.productName,
                region = key.region,
                totalStockRequired = totalQty,
                requiredByDate = earliestExpected,
                pendingOrdersCount = distinctOrders,
                suppliersInvolved = suppliers
            )
        }.sortedWith(compareBy({ it.region }, { it.productName }))
    }
}

@Service
@Transactional
class RefundService(
    private val saleRepository: SaleRepository,
    private val productRepository: ProductRepository,
    private val refundReturnRepository: RefundReturnRepository,
    private val inventoryMovementRepository: InventoryMovementRepository,
    private val orderRepository: OrderRepository,
    private val supplierNotificationRepository: SupplierNotificationRepository,
    private val eTimsService: ETimsService
) {

    @Transactional(readOnly = true)
    fun lookupSale(eTimsReceiptNumber: String, supplierInvoiceNumber: String): SaleResponse {
        val sale = saleRepository.findByETimsReceiptNumberAndSupplierInvoiceNumber(
            eTimsReceiptNumber.trim(),
            supplierInvoiceNumber.trim()
        )

        if (sale == null) {
            // Check individual matches to provide exact error as specified in EX-03 and EX-04
            val matchByReceipt = saleRepository.findByETimsReceiptNumber(eTimsReceiptNumber.trim())
            val matchByInvoice = saleRepository.findBySupplierInvoiceNumber(supplierInvoiceNumber.trim())
            when {
                matchByReceipt == null && matchByInvoice == null ->
                    throw SaleNotFoundException("Sale not found")
                matchByReceipt == null ->
                    throw InvalidETimsReceiptException("Invalid e-TIMS receipt number")
                else ->
                    throw InvalidSupplierInvoiceException("Invalid supplier invoice number")
            }
        }

        if (sale.status == SaleStatus.FULLY_REFUNDED) {
            throw SaleNotEligibleForReturnException("Sale is not eligible for refund/return")
        }

        return sale.toResponse()
    }

    fun processRefund(request: CreateRefundRequest): RefundResponse {
        // EX-06 Missing Mandatory Information check
        if (request.saleReference.isBlank() ||
            request.eTimsReceiptNumber.isBlank() ||
            request.supplierInvoiceNumber.isBlank() ||
            request.items.isEmpty() ||
            request.reason.isBlank()
        ) {
            throw MissingMandatoryInformationException(
                "Missing mandatory information: sale reference, e-TIMS receipt, supplier invoice, returned items, and return reason are required"
            )
        }

        val sale = saleRepository.findBySaleReference(request.saleReference.trim())
            ?: throw SaleNotFoundException("Sale not found")

        // EX-03 & EX-04: Validate match
        if (!sale.eTimsReceiptNumber.equals(request.eTimsReceiptNumber.trim(), ignoreCase = true)) {
            throw InvalidETimsReceiptException("Invalid e-TIMS receipt number")
        }
        if (!sale.supplierInvoiceNumber.equals(request.supplierInvoiceNumber.trim(), ignoreCase = true)) {
            throw InvalidSupplierInvoiceException("Invalid supplier invoice number")
        }

        // EX-02: Eligibility check
        if (sale.status == SaleStatus.FULLY_REFUNDED) {
            throw SaleNotEligibleForReturnException("Sale is not eligible for refund/return")
        }

        var refundSubtotal = BigDecimal.ZERO
        var refundTax = BigDecimal.ZERO
        val refundPreviewItems = mutableListOf<RefundItemPreviewDto>()
        val refundReturnItems = mutableListOf<RefundReturnItem>()

        val saleItemMap = sale.items.associateBy { it.productId }

        for (returnItem in request.items) {
            if (returnItem.quantity <= 0) {
                throw InvalidReturnQuantityException("Return quantity must be greater than zero")
            }

            val originalItem = saleItemMap[returnItem.productId]
                ?: throw InvalidReturnQuantityException("Item ${returnItem.productId} was not part of the original sale")

            // EX-05: Return quantity validation
            if (returnItem.quantity > originalItem.remainingEligibleQuantity) {
                throw InvalidReturnQuantityException("Return quantity exceeds eligible quantity")
            }

            val itemRefundAmount = originalItem.unitPrice.multiply(BigDecimal(returnItem.quantity)).setScale(2, RoundingMode.HALF_UP)
            val itemTaxRefund = itemRefundAmount.multiply(originalItem.taxRate).setScale(2, RoundingMode.HALF_UP)

            refundSubtotal = refundSubtotal.add(itemRefundAmount)
            refundTax = refundTax.add(itemTaxRefund)

            val previewDto = RefundItemPreviewDto(
                productId = originalItem.productId,
                productName = originalItem.productName,
                quantity = returnItem.quantity,
                unitPrice = originalItem.unitPrice,
                taxRate = originalItem.taxRate,
                refundAmount = itemRefundAmount,
                taxRefundAmount = itemTaxRefund
            )
            refundPreviewItems.add(previewDto)

            val refundEntityItem = RefundReturnItem(
                productId = originalItem.productId,
                productName = originalItem.productName,
                quantity = returnItem.quantity,
                unitPrice = originalItem.unitPrice,
                taxRate = originalItem.taxRate,
                refundAmount = itemRefundAmount,
                taxRefundAmount = itemTaxRefund
            )
            refundReturnItems.add(refundEntityItem)

            // Update item quantity refunded on the original sale
            originalItem.quantityRefunded += returnItem.quantity

            // Reverse stock levels
            val product = productRepository.findByProductId(originalItem.productId)
            if (product != null) {
                product.stockLevel += returnItem.quantity
                productRepository.save(product)

                inventoryMovementRepository.save(
                    InventoryMovement(
                        productId = product.productId,
                        supplierId = product.supplierId,
                        orderNumber = null,
                        movementType = InventoryMovementType.RESTOCK_RETURN,
                        quantity = returnItem.quantity,
                        notes = "Refund/return restock against sale ${sale.saleReference}"
                    )
                )
            }
        }

        val totalRefundAmount = refundSubtotal.add(refundTax).setScale(2, RoundingMode.HALF_UP)

        // Issue Credit Note to KRA e-TIMS
        val creditNote = eTimsService.issueCreditNote(
            originalReceiptNumber = sale.eTimsReceiptNumber,
            supplierInvoiceNumber = sale.supplierInvoiceNumber,
            creditAmount = refundSubtotal,
            taxAmount = refundTax
        )

        // Reverse or cancel any linked unfulfilled orders requested when the sale was made
        var reversedOrderNumber: String? = null
        if (!sale.linkedOrderNumber.isNullOrBlank()) {
            val linkedOrder = orderRepository.findByOrderNumber(sale.linkedOrderNumber!!)
            if (linkedOrder != null && linkedOrder.orderStatus == OrderStatus.PENDING_FULFILMENT) {
                linkedOrder.orderStatus = OrderStatus.CANCELLED
                linkedOrder.cancellationReason = "Reversed due to refund of original sale ${sale.saleReference}"
                linkedOrder.cancelledAt = LocalDateTime.now()

                // Release reserved stock on the linked order
                for (item in linkedOrder.items) {
                    val prod = productRepository.findByProductId(item.productId)
                    if (prod != null) {
                        prod.reservedStock = (prod.reservedStock - item.quantity).coerceAtLeast(0)
                        productRepository.save(prod)
                    }
                }
                orderRepository.save(linkedOrder)
                reversedOrderNumber = linkedOrder.orderNumber

                supplierNotificationRepository.save(
                    SupplierNotification(
                        supplierId = linkedOrder.supplierId,
                        orderNumber = linkedOrder.orderNumber,
                        notificationType = NotificationType.ORDER_CANCELLED,
                        message = "Linked backorder ${linkedOrder.orderNumber} was cancelled due to sale refund."
                    )
                )
            }
        }

        // Update original sale status
        val allItemsFullyRefunded = sale.items.all { it.remainingEligibleQuantity == 0 }
        sale.status = if (allItemsFullyRefunded) SaleStatus.FULLY_REFUNDED else SaleStatus.PARTIALLY_REFUNDED
        saleRepository.save(sale)

        // Record RefundReturn transaction
        val refundNumber = "REF-" + DateTimeFormatter.ofPattern("yyyyMMdd").format(LocalDate.now()) + "-" +
                UUID.randomUUID().toString().take(6).uppercase()

        val refundReturn = RefundReturn(
            refundNumber = refundNumber,
            saleReference = sale.saleReference,
            eTimsReceiptNumber = sale.eTimsReceiptNumber,
            supplierInvoiceNumber = sale.supplierInvoiceNumber,
            creditNoteNumber = creditNote.creditNoteNumber,
            refundSubtotal = refundSubtotal,
            refundTaxAmount = refundTax,
            refundTotalAmount = totalRefundAmount,
            reason = request.reason,
            status = RefundStatus.COMPLETED,
            requestedBy = request.requestedBy,
            createdAt = LocalDateTime.now(),
            processedAt = LocalDateTime.now(),
            reversedLinkedOrderNumber = reversedOrderNumber
        )

        for (entityItem in refundReturnItems) {
            entityItem.refundReturn = refundReturn
            refundReturn.items.add(entityItem)
        }

        refundReturnRepository.save(refundReturn)

        return RefundResponse(
            refundNumber = refundNumber,
            saleReference = sale.saleReference,
            eTimsReceiptNumber = sale.eTimsReceiptNumber,
            supplierInvoiceNumber = sale.supplierInvoiceNumber,
            creditNote = creditNote,
            refundSubtotal = refundSubtotal,
            refundTaxAmount = refundTax,
            refundTotalAmount = totalRefundAmount,
            reason = request.reason,
            status = RefundStatus.COMPLETED,
            requestedBy = request.requestedBy,
            createdAt = refundReturn.createdAt,
            reversedLinkedOrderNumber = reversedOrderNumber,
            inventoryReversed = true,
            items = refundPreviewItems
        )
    }

    private fun Sale.toResponse() = SaleResponse(
        saleReference = this.saleReference,
        eTimsReceiptNumber = this.eTimsReceiptNumber,
        supplierInvoiceNumber = this.supplierInvoiceNumber,
        customerId = this.customerId,
        customerName = this.customerName,
        items = this.items.map {
            SaleItemDto(
                productId = it.productId,
                productName = it.productName,
                quantitySold = it.quantitySold,
                quantityRefunded = it.quantityRefunded,
                remainingEligibleQuantity = it.remainingEligibleQuantity,
                unitPrice = it.unitPrice,
                taxRate = it.taxRate,
                totalPrice = it.totalPrice,
                taxAmount = it.taxAmount
            )
        },
        subtotal = this.subtotal,
        taxAmount = this.taxAmount,
        totalAmount = this.totalAmount,
        saleDate = this.saleDate,
        status = this.status,
        linkedOrderNumber = this.linkedOrderNumber
    )
}
