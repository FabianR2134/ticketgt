package gt.ticket.orders;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class OrdersModuleTest {

    @Test
    void exposesModuleName() {
        // Arrange
        String expected = "orders";

        // Act
        String actual = OrdersModule.NAME;

        // Assert
        assertEquals(expected, actual);
    }
}
