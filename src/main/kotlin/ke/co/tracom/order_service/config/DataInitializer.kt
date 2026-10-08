package ke.co.tracom.order_service.config

import ke.co.tracom.order_service.domain.model.*
import ke.co.tracom.order_service.repository.*
import org.springframework.boot.CommandLineRunner
import org.springframework.context.annotation.Profile
import org.springframework.stereotype.Component
import java.math.BigDecimal
import java.time.LocalDate
import java.time.LocalDateTime

@Component
class DataInitializer(
    private val productRepository: ProductRepository,
    private val saleRepository: SaleRepository,
    private val orderRepository: OrderRepository
) : CommandLineRunner {

    override fun run(vararg args: String) {
        if (productRepository.count() > 0) return

        // 1. Seed Products for Suppliers
        val p1 = Product(
            productId = "PRD-MILK-01",
            supplierId = "SUP-BROOKSIDE",
            supplierName = "Brookside Dairy Ltd",
            name = "Brookside Fresh Milk 500ml (Ctn of 24)",
            category = "Dairy",
            unitPrice = BigDecimal("1440.00"),
            taxRate = BigDecimal("0.16"),
            stockLevel = 150,
            reservedStock = 10,
            region = "Nairobi"
        )
        val p2 = Product(
            productId = "PRD-MILK-02",
            supplierId = "SUP-BROOKSIDE",
            supplierName = "Brookside Dairy Ltd",
            name = "Brookside Long Life UHT 1L (Ctn of 12)",
            category = "Dairy",
            unitPrice = BigDecimal("1920.00"),
            taxRate = BigDecimal("0.16"),
            stockLevel = 80,
            reservedStock = 0,
            region = "Rift Valley"
        )
        val p3 = Product(
            productId = "PRD-OIL-01",
            supplierId = "SUP-BIDCO",
            supplierName = "Bidco Africa Ltd",
            name = "Elianto Pure Corn Oil 5L (Box of 4)",
            category = "Cooking Oil",
            unitPrice = BigDecimal("4600.00"),
            taxRate = BigDecimal("0.16"),
            stockLevel = 45,
            reservedStock = 5,
            region = "Nairobi"
        )
        val p4 = Product(
            productId = "PRD-SOAP-01",
            supplierId = "SUP-BIDCO",
            supplierName = "Bidco Africa Ltd",
            name = "Gental Washing Powder 1kg (Ctn of 12)",
            category = "Detergent",
            unitPrice = BigDecimal("2400.00"),
            taxRate = BigDecimal("0.16"),
            stockLevel = 100,
            reservedStock = 0,
            region = "Coast"
        )
        val p5 = Product(
            productId = "PRD-FLOUR-01",
            supplierId = "SUP-UNGA",
            supplierName = "Unga Group Ltd",
            name = "Jogoo Maize Flour 2kg (Bundle of 12)",
            category = "Flour & Grains",
            unitPrice = BigDecimal("1800.00"),
            taxRate = BigDecimal("0.00"), // Zero-rated basic staple
            stockLevel = 200,
            reservedStock = 20,
            region = "Nairobi"
        )
        val p6 = Product(
            productId = "PRD-OUT-01",
            supplierId = "SUP-UNGA",
            supplierName = "Unga Group Ltd",
            name = "Amana Basmati Rice 5kg (Limited Run)",
            category = "Rice",
            unitPrice = BigDecimal("1250.00"),
            taxRate = BigDecimal("0.16"),
            stockLevel = 0,
            reservedStock = 0,
            region = "Nairobi"
        )

        productRepository.saveAll(listOf(p1, p2, p3, p4, p5, p6))

        // 2. Seed a sample Linked Backorder requested when an original sale experienced item shortage
        val linkedBackorder = Order(
            orderNumber = "ORD-BACKORDER-001",
            invoiceNumber = "INV-BACKORDER-001",
            shopOwnerId = "RET-001",
            shopOwnerName = "Mama Jane Duka",
            shopOwnerPhone = "0712345678",
            supplierId = "SUP-BROOKSIDE",
            supplierName = "Brookside Dairy Ltd",
            region = "Nairobi",
            subtotal = BigDecimal("1440.00"),
            taxAmount = BigDecimal("230.40"),
            deliveryCharges = BigDecimal("350.00"),
            totalAmount = BigDecimal("2020.40"),
            paymentMethod = PaymentMethod.PAY_ON_DELIVERY,
            paymentStatus = PaymentStatus.CONFIRMED_UNPAID,
            amountPaid = BigDecimal.ZERO,
            amountOutstanding = BigDecimal("2020.40"),
            orderStatus = OrderStatus.PENDING_FULFILMENT,
            expectedDeliveryDate = LocalDate.now().plusDays(3)
        )
        val boItem = OrderItem(
            order = linkedBackorder,
            productId = "PRD-MILK-01",
            productName = "Brookside Fresh Milk 500ml (Ctn of 24)",
            quantity = 1,
            unitPrice = BigDecimal("1440.00"),
            taxRate = BigDecimal("0.16"),
            lineTotal = BigDecimal("1440.00"),
            lineTax = BigDecimal("230.40")
        )
        linkedBackorder.items.add(boItem)
        orderRepository.save(linkedBackorder)

        // 3. Seed an existing completed sale with e-TIMS receipt & supplier invoice
        val sale = Sale(
            saleReference = "SALE-202610-101",
            eTimsReceiptNumber = "ETIMS-REC-88492041",
            supplierInvoiceNumber = "INV-SUP-99102",
            customerId = "CUST-KIBERA-01",
            customerName = "Kibera Corner Store",
            subtotal = BigDecimal("6040.00"),
            taxAmount = BigDecimal("966.40"),
            totalAmount = BigDecimal("7006.40"),
            saleDate = LocalDateTime.now().minusDays(2),
            status = SaleStatus.COMPLETED,
            linkedOrderNumber = "ORD-BACKORDER-001"
        )
        val sItem1 = SaleItem(
            sale = sale,
            productId = "PRD-MILK-01",
            productName = "Brookside Fresh Milk 500ml (Ctn of 24)",
            quantitySold = 3,
            quantityRefunded = 0,
            unitPrice = BigDecimal("1440.00"),
            taxRate = BigDecimal("0.16"),
            totalPrice = BigDecimal("4320.00"),
            taxAmount = BigDecimal("691.20")
        )
        val sItem2 = SaleItem(
            sale = sale,
            productId = "PRD-OIL-01",
            productName = "Elianto Pure Corn Oil 5L (Box of 4)",
            quantitySold = 1,
            quantityRefunded = 0,
            unitPrice = BigDecimal("1720.00"),
            taxRate = BigDecimal("0.16"),
            totalPrice = BigDecimal("1720.00"),
            taxAmount = BigDecimal("275.20")
        )
        sale.items.addAll(listOf(sItem1, sItem2))
        saleRepository.save(sale)

        // 4. Seed an existing distributor/retailer pending order for regional demand planning
        val regionalOrder = Order(
            orderNumber = "ORD-PENDING-REG-01",
            invoiceNumber = "INV-PENDING-REG-01",
            shopOwnerId = "RET-002",
            shopOwnerName = "Eldoret Highway Wholesalers",
            shopOwnerPhone = "0722998877",
            supplierId = "SUP-BROOKSIDE",
            supplierName = "Brookside Dairy Ltd",
            region = "Rift Valley",
            subtotal = BigDecimal("3840.00"),
            taxAmount = BigDecimal("614.40"),
            deliveryCharges = BigDecimal("350.00"),
            totalAmount = BigDecimal("4804.40"),
            paymentMethod = PaymentMethod.PAY_ON_DELIVERY,
            paymentStatus = PaymentStatus.CONFIRMED_UNPAID,
            amountPaid = BigDecimal.ZERO,
            amountOutstanding = BigDecimal("4804.40"),
            orderStatus = OrderStatus.PENDING_FULFILMENT,
            expectedDeliveryDate = LocalDate.now().plusDays(4)
        )
        val regItem = OrderItem(
            order = regionalOrder,
            productId = "PRD-MILK-02",
            productName = "Brookside Long Life UHT 1L (Ctn of 12)",
            quantity = 2,
            unitPrice = BigDecimal("1920.00"),
            taxRate = BigDecimal("0.16"),
            lineTotal = BigDecimal("3840.00"),
            lineTax = BigDecimal("614.40")
        )
        regionalOrder.items.add(regItem)
        orderRepository.save(regionalOrder)
    }
}
