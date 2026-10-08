package ke.co.tracom.order_service.exception

import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.RestControllerAdvice
import java.time.LocalDateTime

class OutOfStockException(message: String = "The item is currently out of stock") : RuntimeException(message)

class PaymentFailedException(message: String = "Payment could not be completed. Please try again") : RuntimeException(message)

class OrderNotFoundException(message: String) : RuntimeException(message)

class SaleNotFoundException(message: String = "Sale not found") : RuntimeException(message)

class SaleNotEligibleForReturnException(message: String = "Sale is not eligible for refund/return") : RuntimeException(message)

class InvalidETimsReceiptException(message: String = "Invalid e-TIMS receipt number") : RuntimeException(message)

class InvalidSupplierInvoiceException(message: String = "Invalid supplier invoice number") : RuntimeException(message)

class InvalidReturnQuantityException(message: String = "Return quantity exceeds eligible quantity") : RuntimeException(message)

class MissingMandatoryInformationException(message: String) : RuntimeException(message)

class InvalidOrderStateException(message: String) : RuntimeException(message)

data class ErrorResponse(
    val timestamp: LocalDateTime = LocalDateTime.now(),
    val status: Int,
    val error: String,
    val message: String
)

@RestControllerAdvice
class GlobalExceptionHandler {

    @ExceptionHandler(OutOfStockException::class)
    fun handleOutOfStock(ex: OutOfStockException): ResponseEntity<ErrorResponse> {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(
            ErrorResponse(
                status = HttpStatus.BAD_REQUEST.value(),
                error = "OUT_OF_STOCK",
                message = ex.message ?: "The item is currently out of stock"
            )
        )
    }

    @ExceptionHandler(PaymentFailedException::class)
    fun handlePaymentFailed(ex: PaymentFailedException): ResponseEntity<ErrorResponse> {
        return ResponseEntity.status(HttpStatus.PAYMENT_REQUIRED).body(
            ErrorResponse(
                status = HttpStatus.PAYMENT_REQUIRED.value(),
                error = "PAYMENT_FAILED",
                message = ex.message ?: "Payment could not be completed. Please try again"
            )
        )
    }

    @ExceptionHandler(OrderNotFoundException::class)
    fun handleOrderNotFound(ex: OrderNotFoundException): ResponseEntity<ErrorResponse> {
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(
            ErrorResponse(
                status = HttpStatus.NOT_FOUND.value(),
                error = "ORDER_NOT_FOUND",
                message = ex.message ?: "Order not found"
            )
        )
    }

    @ExceptionHandler(SaleNotFoundException::class)
    fun handleSaleNotFound(ex: SaleNotFoundException): ResponseEntity<ErrorResponse> {
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(
            ErrorResponse(
                status = HttpStatus.NOT_FOUND.value(),
                error = "SALE_NOT_FOUND",
                message = ex.message ?: "Sale not found"
            )
        )
    }

    @ExceptionHandler(SaleNotEligibleForReturnException::class)
    fun handleSaleNotEligible(ex: SaleNotEligibleForReturnException): ResponseEntity<ErrorResponse> {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(
            ErrorResponse(
                status = HttpStatus.BAD_REQUEST.value(),
                error = "SALE_NOT_ELIGIBLE",
                message = ex.message ?: "Sale is not eligible for refund/return"
            )
        )
    }

    @ExceptionHandler(InvalidETimsReceiptException::class)
    fun handleInvalidEtimsReceipt(ex: InvalidETimsReceiptException): ResponseEntity<ErrorResponse> {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(
            ErrorResponse(
                status = HttpStatus.BAD_REQUEST.value(),
                error = "INVALID_ETIMS_RECEIPT",
                message = ex.message ?: "Invalid e-TIMS receipt number"
            )
        )
    }

    @ExceptionHandler(InvalidSupplierInvoiceException::class)
    fun handleInvalidSupplierInvoice(ex: InvalidSupplierInvoiceException): ResponseEntity<ErrorResponse> {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(
            ErrorResponse(
                status = HttpStatus.BAD_REQUEST.value(),
                error = "INVALID_SUPPLIER_INVOICE",
                message = ex.message ?: "Invalid supplier invoice number"
            )
        )
    }

    @ExceptionHandler(InvalidReturnQuantityException::class)
    fun handleInvalidReturnQuantity(ex: InvalidReturnQuantityException): ResponseEntity<ErrorResponse> {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(
            ErrorResponse(
                status = HttpStatus.BAD_REQUEST.value(),
                error = "INVALID_RETURN_QUANTITY",
                message = ex.message ?: "Return quantity exceeds eligible quantity"
            )
        )
    }

    @ExceptionHandler(MissingMandatoryInformationException::class)
    fun handleMissingInfo(ex: MissingMandatoryInformationException): ResponseEntity<ErrorResponse> {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(
            ErrorResponse(
                status = HttpStatus.BAD_REQUEST.value(),
                error = "MISSING_MANDATORY_INFO",
                message = ex.message ?: "Missing mandatory information"
            )
        )
    }

    @ExceptionHandler(InvalidOrderStateException::class)
    fun handleInvalidState(ex: InvalidOrderStateException): ResponseEntity<ErrorResponse> {
        return ResponseEntity.status(HttpStatus.CONFLICT).body(
            ErrorResponse(
                status = HttpStatus.CONFLICT.value(),
                error = "INVALID_ORDER_STATE",
                message = ex.message ?: "Invalid order state"
            )
        )
    }

    @ExceptionHandler(Exception::class)
    fun handleGeneralException(ex: Exception): ResponseEntity<ErrorResponse> {
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(
            ErrorResponse(
                status = HttpStatus.INTERNAL_SERVER_ERROR.value(),
                error = "INTERNAL_ERROR",
                message = ex.message ?: "An unexpected error occurred"
            )
        )
    }
}
