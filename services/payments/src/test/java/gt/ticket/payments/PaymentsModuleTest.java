package gt.ticket.payments;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class PaymentsModuleTest {

    @Test
    void exposesModuleName() {
        // Arrange
        String expected = "payments";

        // Act
        String actual = PaymentsModule.NAME;

        // Assert
        assertEquals(expected, actual);
    }
}
