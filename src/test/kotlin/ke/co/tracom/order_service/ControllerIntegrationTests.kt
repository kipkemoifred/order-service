package ke.co.tracom.order_service

import ke.co.tracom.order_service.controller.CatalogController
import ke.co.tracom.order_service.controller.ManufacturerController
import ke.co.tracom.order_service.controller.OrderController
import ke.co.tracom.order_service.controller.RefundController
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.http.MediaType
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.content
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import org.springframework.test.web.servlet.setup.MockMvcBuilders

@SpringBootTest
class ControllerIntegrationTests {

    @Autowired
    private lateinit var catalogController: CatalogController

    @Autowired
    private lateinit var orderController: OrderController

    @Autowired
    private lateinit var manufacturerController: ManufacturerController

    @Autowired
    private lateinit var refundController: RefundController

    @Autowired
    private lateinit var globalExceptionHandler: ke.co.tracom.order_service.exception.GlobalExceptionHandler

    @Autowired
    private lateinit var saleRepository: ke.co.tracom.order_service.repository.SaleRepository

    @Autowired
    private lateinit var openAPI: io.swagger.v3.oas.models.OpenAPI

    @Autowired(required = false)
    private var flyway: org.flywaydb.core.Flyway? = null

    private lateinit var mockMvc: MockMvc

    @BeforeEach
    fun setup() {
        mockMvc = MockMvcBuilders.standaloneSetup(
            catalogController,
            orderController,
            manufacturerController,
            refundController
        ).setControllerAdvice(globalExceptionHandler).build()
    }

    @Test
    fun `test GET catalog suppliers endpoint`() {
        mockMvc.perform(get("/api/catalog/suppliers"))
            .andExpect(status().isOk)
            .andExpect(content().contentType(MediaType.APPLICATION_JSON))
            .andExpect(jsonPath("$").isArray)
            .andExpect(jsonPath("$[0].supplierId").exists())
    }

    @Test
    fun `test POST calculate cart endpoint`() {
        val payload = """
            {
                "supplierId": "SUP-BROOKSIDE",
                "items": [
                    { "productId": "PRD-MILK-01", "quantity": 1 }
                ]
            }
        """.trimIndent()

        mockMvc.perform(
            post("/api/catalog/cart/calculate")
                .contentType(MediaType.APPLICATION_JSON)
                .content(payload)
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.subtotal").value(1440.00))
            .andExpect(jsonPath("$.taxAmount").value(230.40))
            .andExpect(jsonPath("$.deliveryCharges").value(350.00))
            .andExpect(jsonPath("$.totalAmount").value(2020.40))
            .andExpect(jsonPath("$.allAvailable").value(true))
    }

    @Test
    fun `test GET pending orders endpoint`() {
        mockMvc.perform(get("/api/orders/pending"))
            .andExpect(status().isOk)
            .andExpect(content().contentType(MediaType.APPLICATION_JSON))
            .andExpect(jsonPath("$").isArray)
    }

    @Test
    fun `test GET manufacturer demand aggregation endpoint`() {
        mockMvc.perform(get("/api/manufacturer/demand"))
            .andExpect(status().isOk)
            .andExpect(content().contentType(MediaType.APPLICATION_JSON))
            .andExpect(jsonPath("$").isArray)
            .andExpect(jsonPath("$[0].totalStockRequired").exists())
            .andExpect(jsonPath("$[0].region").exists())
    }

    @Test
    fun `test GET lookup sale endpoint`() {
        mockMvc.perform(
            get("/api/refunds/lookup-sale")
                .param("eTimsReceiptNumber", "ETIMS-REC-88492041")
                .param("supplierInvoiceNumber", "INV-SUP-99102")
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.saleReference").value("SALE-202610-101"))
            .andExpect(jsonPath("$.items").isArray)
    }

    @Test
    fun `test OpenAPI bean is configured with Swagger metadata`() {
        org.junit.jupiter.api.Assertions.assertNotNull(openAPI)
        org.junit.jupiter.api.Assertions.assertEquals("Supply Chain Order & Refund Service API", openAPI.info.title)
        org.junit.jupiter.api.Assertions.assertEquals("1.0.0", openAPI.info.version)
        org.junit.jupiter.api.Assertions.assertNotNull(openAPI.info.description)
    }

    @Test
    fun `test POST refunds with etimsReceiptNumber alias parses correctly without 500 error`() {
        val userCurlPayload = """
            {
              "saleReference": "string",
              "supplierInvoiceNumber": "string",
              "items": [
                {
                  "productId": "string",
                  "quantity": 0
                }
              ],
              "reason": "string",
              "requestedBy": "string",
              "etimsReceiptNumber": "string"
            }
        """.trimIndent()

        // Verifies the payload parses properly and reaches business logic, returning 404 for non-existent sale rather than 500
        mockMvc.perform(
            post("/api/refunds")
                .contentType(MediaType.APPLICATION_JSON)
                .content(userCurlPayload)
        )
            .andExpect(status().isNotFound)
            .andExpect(jsonPath("$.status").value(404))
            .andExpect(jsonPath("$.error").value("SALE_NOT_FOUND"))
    }

    @Test
    fun `test POST refunds with malformed JSON returns 400 Bad Request`() {
        mockMvc.perform(
            post("/api/refunds")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{ invalid json }")
        )
            .andExpect(status().isBadRequest)
            .andExpect(jsonPath("$.status").value(400))
            .andExpect(jsonPath("$.error").value("MALFORMED_JSON_REQUEST"))
    }

    @Test
    fun `test POST refunds valid request using etimsReceiptNumber lowercase alias`() {
        // Save isolated sale for controller integration test to avoid mutating baseline data
        val testSale = ke.co.tracom.order_service.domain.model.Sale(
            saleReference = "SALE-CTRL-ALIAS-01",
            eTimsReceiptNumber = "ETIMS-ALIAS-001",
            supplierInvoiceNumber = "INV-ALIAS-001",
            customerId = "CUST-CTRL",
            customerName = "Controller Test Store",
            subtotal = java.math.BigDecimal("1440.00"),
            taxAmount = java.math.BigDecimal("230.40"),
            totalAmount = java.math.BigDecimal("1670.40"),
            saleDate = java.time.LocalDateTime.now()
        )
        val testSaleItem = ke.co.tracom.order_service.domain.model.SaleItem(
            sale = testSale,
            productId = "PRD-MILK-01",
            productName = "Brookside Fresh Milk 500ml (Ctn of 24)",
            quantitySold = 2,
            quantityRefunded = 0,
            unitPrice = java.math.BigDecimal("1440.00"),
            taxRate = java.math.BigDecimal("0.16"),
            totalPrice = java.math.BigDecimal("2880.00"),
            taxAmount = java.math.BigDecimal("460.80")
        )
        testSale.items.add(testSaleItem)
        saleRepository.save(testSale)

        val validPayload = """
            {
              "saleReference": "SALE-CTRL-ALIAS-01",
              "supplierInvoiceNumber": "INV-ALIAS-001",
              "items": [
                {
                  "productId": "PRD-MILK-01",
                  "quantity": 1
                }
              ],
              "reason": "Defective goods",
              "requestedBy": "Store Manager",
              "etimsReceiptNumber": "ETIMS-ALIAS-001"
            }
        """.trimIndent()

        mockMvc.perform(
            post("/api/refunds")
                .contentType(MediaType.APPLICATION_JSON)
                .content(validPayload)
        )
            .andExpect(status().isCreated)
            .andExpect(jsonPath("$.refundNumber").exists())
            .andExpect(jsonPath("$.creditNote.creditNoteNumber").exists())
            .andExpect(jsonPath("$.status").value("COMPLETED"))
    }

    @Test
    fun `test Flyway migrations executed successfully`() {
        org.junit.jupiter.api.Assertions.assertNotNull(flyway, "Flyway bean must be configured")
        val info = flyway!!.info()
        val applied = info.applied()
        org.junit.jupiter.api.Assertions.assertTrue(applied.isNotEmpty(), "Flyway migrations should have been applied")
        org.junit.jupiter.api.Assertions.assertEquals("init schema", applied[0].description)
        org.junit.jupiter.api.Assertions.assertEquals("seed initial data", applied[1].description)
    }
}
