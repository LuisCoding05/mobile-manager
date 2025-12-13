package mobile.personal.manager.service;

import mobile.personal.manager.model.Device;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

@Service
public class AdbService {

    private static final Logger log = LoggerFactory.getLogger(AdbService.class);

    @Value("${adb.path:adb}")
    private String adbPath;

    @Value("${scrcpy.path:/home/adrian/scrcpy}")
    private String scrcpyPath;

    // track scrcpy processes started by this service keyed by device id
    private final Map<String, Process> scrcpyProcesses = new ConcurrentHashMap<>();

    public List<Device> listDevices() {
        List<Device> devices = new ArrayList<>();
        try {
            ProcessBuilder pb = new ProcessBuilder(adbPath, "devices");
            Process p = pb.start();
            try (BufferedReader r = new BufferedReader(new InputStreamReader(p.getInputStream()))) {
                String out = r.lines().collect(Collectors.joining("\n"));
                devices = parseDevicesFromOutput(out);
            }
        } catch (IOException e) {
            log.error("Error running adb devices", e);
        }
        return devices;
    }

    public List<Device> parseDevicesFromOutput(String output) {
        List<Device> devices = new ArrayList<>();
        if (output == null || output.isEmpty()) return devices;
        String[] lines = output.split("\n");
        for (String l : lines) {
            l = l.trim();
            if (l.isEmpty()) continue;
            if (l.startsWith("List of devices")) continue;
            // expected format: <id>\t<status>
            String[] parts = l.split("\t+|\\s+");
            if (parts.length >= 2) {
                devices.add(new Device(parts[0], parts[1]));
            }
        }
        return devices;
    }

    public boolean startScrcpyForDevice(String deviceId) {
        // if a scrcpy is already running for this device, don't start another
        if (deviceId != null && !deviceId.isEmpty()) {
            Process existing = scrcpyProcesses.get(deviceId);
            if (existing != null && existing.isAlive()) {
                log.info("scrcpy already running for {} (pid={})", deviceId, existing.pid());
                return true;
            }
        }
        List<String> cmd = new ArrayList<>();
        cmd.add("scrcpy");
        if (deviceId != null && !deviceId.isEmpty()) {
            cmd.add("-s");
            cmd.add(deviceId);
        }
        ProcessBuilder pb = new ProcessBuilder(cmd);
        pb.directory(new File(scrcpyPath));
        pb.redirectErrorStream(true);
        try {
            Process p = pb.start();
            log.info("scrcpy started for {} (pid={})", deviceId, p.pid());
            if (deviceId != null && !deviceId.isEmpty()) {
                scrcpyProcesses.put(deviceId, p);
            }
            return true;
        } catch (IOException e) {
            log.error("Failed to start scrcpy", e);
            return false;
        }
    }

    public boolean stopScrcpyForDevice(String deviceId) {
        if (deviceId == null || deviceId.isEmpty()) return false;
        Process p = scrcpyProcesses.get(deviceId);
        if (p == null) return false;
        try {
            p.destroy();
            boolean exited = p.waitFor(5, java.util.concurrent.TimeUnit.SECONDS);
            if (!exited && p.isAlive()) {
                p.destroyForcibly();
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        scrcpyProcesses.remove(deviceId);
        log.info("scrcpy stopped for {}", deviceId);
        return true;
    }

    public boolean isScrcpyRunning(String deviceId) {
        if (deviceId == null) return false;
        Process p = scrcpyProcesses.get(deviceId);
        return p != null && p.isAlive();
    }

    public Set<String> getActiveDeviceIds() {
        return scrcpyProcesses.keySet();
    }

}
