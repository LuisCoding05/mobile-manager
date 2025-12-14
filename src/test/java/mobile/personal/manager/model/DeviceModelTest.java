package mobile.personal.manager.model;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

public class DeviceModelTest {

    @Test
    public void friendlyStatusAndShortId() {
        Device d = Device.builder().id("ZX1G22XXXXABCDEFG").status("device").build();
        Assertions.assertEquals("Conectado", d.getFriendlyStatus());
        Assertions.assertTrue(d.getShortId().endsWith("…"));

        Device d2 = Device.builder().id("abcd").status("offline").build();
        Assertions.assertEquals("Desconectado", d2.getFriendlyStatus());
        Assertions.assertEquals("abcd", d2.getShortId());

        Device d3 = Device.builder().id("id").status("unauthorized").build();
        Assertions.assertEquals("No autorizado", d3.getFriendlyStatus());
    }
}
