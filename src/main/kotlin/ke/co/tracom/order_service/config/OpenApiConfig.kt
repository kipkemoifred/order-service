package ke.co.tracom.order_service.config

import io.swagger.v3.oas.models.OpenAPI
import io.swagger.v3.oas.models.info.Contact
import io.swagger.v3.oas.models.info.Info
import io.swagger.v3.oas.models.info.License
import io.swagger.v3.oas.models.servers.Server
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

@Configuration
class OpenApiConfig {

    @Bean
    fun customOpenAPI(): OpenAPI {
        return OpenAPI()
            .info(
                Info()
                    .title("Supply Chain Order & Refund Service API")
                    .version("1.0.0")
                    .description(
                        """
                        ### Value Chain Order, Payment, and Refund Management System
                        
                        This API powers two core business modules:
                        1. **Module 3.3 - Orders (UC-05)**:
                           - Supplier catalogue browsing and real-time inventory availability checks.
                           - Order placement with prepayment (**M-Pesa STK Push** / Card) or **Pay on Delivery (AP1)**.
                           - Centralized pending-orders view for stock planning and loan financing.
                           - Delivery confirmation with Pay on Delivery settlement (**AP-03**).
                           - Order adjustment with dynamic total recalculation and inventory reservation updates (**AP 3**).
                           - Order cancellation using invoice number with stock release (**AP 4**).
                           - Manufacturer demand aggregation portal across regions and products.
                        
                        2. **Module 3.4 - Refunds and Returns (UC-06)**:
                           - Original sale retrieval using **KRA e-TIMS receipt number** and supplier invoice number.
                           - Return item and quantity validation.
                           - Automatic computation of adjusted net prices and reversed VAT (16%).
                           - Official **KRA e-TIMS Credit Note** generation with verification QR link.
                           - Automatic physical stock reversal and cancellation of linked unfulfilled backorders.
                        """.trimIndent()
                    )
                    .contact(
                        Contact()
                            .name("Tracom Supply Chain Engineering")
                            .email("support@tracom.co.ke")
                    )
                    .license(
                        License()
                            .name("Apache 2.0")
                            .url("https://www.apache.org/licenses/LICENSE-2.0")
                    )
            )
            .servers(
                listOf(
                    Server().url("/").description("Current Server")
                )
            )
    }
}
