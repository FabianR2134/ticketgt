package gt.ticket.inventory;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class InventoryModuleTest {

    @Test
    void exposesModuleName() {
        // Arrange
        String expected = "inventory";

        // Act
        String actual = InventoryModule.NAME;

        // Assert
        assertEquals(expected, actual);
    }
}
