package ke.co.tracom.order_service.repository

import ke.co.tracom.order_service.domain.model.*
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository

@Repository
interface ProductRepository : JpaRepository<Product, Long> {
    fun findByProductId(productId: String): Product?
    fun findBySupplierId(supplierId: String): List<Product>
    fun findAllByOrderBySupplierIdAsc(): List<Product>
    fun findByRegion(region: String): List<Product>
}

@Repository
interface OrderRepository : JpaRepository<Order, Long> {
    fun findByOrderNumber(orderNumber: String): Order?
    fun findByInvoiceNumber(invoiceNumber: String): Order?
    fun findByOrderStatusIn(statuses: List<OrderStatus>): List<Order>
    fun findByShopOwnerId(shopOwnerId: String): List<Order>
    fun findBySupplierId(supplierId: String): List<Order>
    fun findByRegion(region: String): List<Order>
    fun countByShopOwnerId(shopOwnerId: String): Long
}

@Repository
interface OrderItemRepository : JpaRepository<OrderItem, Long> {
    fun findByOrder(order: Order): List<OrderItem>
}

@Repository
interface InventoryMovementRepository : JpaRepository<InventoryMovement, Long> {
    fun findByProductId(productId: String): List<InventoryMovement>
    fun findByOrderNumber(orderNumber: String): List<InventoryMovement>
}

@Repository
interface SupplierNotificationRepository : JpaRepository<SupplierNotification, Long> {
    fun findBySupplierId(supplierId: String): List<SupplierNotification>
    fun findByOrderNumber(orderNumber: String): List<SupplierNotification>
}

@Repository
interface SaleRepository : JpaRepository<Sale, Long> {
    fun findBySaleReference(saleReference: String): Sale?
    fun findByETimsReceiptNumber(eTimsReceiptNumber: String): Sale?
    fun findBySupplierInvoiceNumber(supplierInvoiceNumber: String): Sale?
    fun findByETimsReceiptNumberAndSupplierInvoiceNumber(
        eTimsReceiptNumber: String,
        supplierInvoiceNumber: String
    ): Sale?
}

@Repository
interface RefundReturnRepository : JpaRepository<RefundReturn, Long> {
    fun findByRefundNumber(refundNumber: String): RefundReturn?
    fun findBySaleReference(saleReference: String): List<RefundReturn>
    fun findByCreditNoteNumber(creditNoteNumber: String): RefundReturn?
}
