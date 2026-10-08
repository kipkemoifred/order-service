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
        ).build()
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
    fun `test Flyway migrations executed successfully`() {
        org.junit.jupiter.api.Assertions.assertNotNull(flyway, "Flyway bean must be configured")
        val info = flyway!!.info()
        val applied = info.applied()
        org.junit.jupiter.api.Assertions.assertTrue(applied.isNotEmpty(), "Flyway migrations should have been applied")
        org.junit.jupiter.api.Assertions.assertEquals("init schema", applied[0].description)
        org.junit.jupiter.api.Assertions.assertEquals("seed initial data", applied[1].description)
    }
}
