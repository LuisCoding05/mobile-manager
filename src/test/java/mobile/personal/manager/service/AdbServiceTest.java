package mobile.personal.manager.service;

import mobile.personal.manager.model.Device;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.List;

public class AdbServiceTest {

    @Test
    public void parseDevicesOutput() {
        String sample = "List of devices attached\n" +
                "emulator-5554\tdevice\n" +
                "ZX1G22XXXX\tdevice\n" +
                "\n";
        AdbService s = new AdbService();
        List<Device> devices = s.parseDevicesFromOutput(sample);
        Assertions.assertEquals(2, devices.size());
        Assertions.assertEquals("emulator-5554", devices.get(0).getId());
        Assertions.assertEquals("device", devices.get(0).getStatus());
        Assertions.assertEquals("ZX1G22XXXX", devices.get(1).getId());
    }

}
