package ke.co.tracom.order_service.repository

import ke.co.tracom.order_service.domain.model.*
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import org.springframework.stereotype.Repository

@Repository
interface ProductRepository : JpaRepository<Product, Long> {
    @Query("SELECT p FROM Product p WHERE p.productCode = :productId")
    fun findByProductId(@Param("productId") productId: String): Product?

    fun findByProductCode(productCode: String): Product?

    fun findBySupplierId(supplierId: String): List<Product> =
        findAll().filter { it.supplierId == supplierId }

    fun findAllByOrderBySupplierIdAsc(): List<Product> =
        findAll().sortedBy { it.supplierId }

    fun findByRegion(region: String): List<Product> =
        findAll().filter { it.region == region }
}

@Repository
interface InventoryStockRepository : JpaRepository<InventoryStock, Long> {
    fun findByProduct(product: Product): InventoryStock?

    @Query("SELECT s FROM InventoryStock s WHERE s.product.id = :productId")
    fun findByProductId(@Param("productId") productId: Long): InventoryStock?
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
