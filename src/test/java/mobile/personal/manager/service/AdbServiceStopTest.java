package mobile.personal.manager.service;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

public class AdbServiceStopTest {

    @Test
    public void stopNonExistentReturnsFalse() {
        AdbService s = new AdbService();
        Assertions.assertFalse(s.stopScrcpyForDevice("nonexistent"));
    }

    @Test
    public void isScrcpyRunningInitialFalse() {
        AdbService s = new AdbService();
        Assertions.assertFalse(s.isScrcpyRunning("any"));
    }
}
