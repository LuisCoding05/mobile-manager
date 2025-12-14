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
import java.util.concurrent.ConcurrentLinkedDeque;
import java.util.Deque;
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
    // recent logs for scrcpy per device
    private final Map<String, Deque<String>> scrcpyLogs = new ConcurrentHashMap<>();

    private static final int MAX_LOG_LINES = 200;

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
        // enrich devices with model/manufacturer/product when possible
        for (Device d : devices) {
            if ("device".equals(d.getStatus())) {
                try {
                    enrichDeviceInfo(d);
                } catch (Exception ex) {
                    log.debug("Could not enrich device {} info", d.getId(), ex);
                }
            }
        }
        return devices;
    }

    private String runAdbCommand(String... args) throws IOException {
        List<String> cmd = new ArrayList<>();
        cmd.add(adbPath);
        cmd.addAll(java.util.Arrays.asList(args));
        ProcessBuilder pb = new ProcessBuilder(cmd);
        Process p = pb.start();
        try (BufferedReader br = new BufferedReader(new InputStreamReader(p.getInputStream()))) {
            return br.lines().collect(Collectors.joining("\n")).trim();
        }
    }

    public void enrichDeviceInfo(Device d) {
        if (d == null || d.getId() == null) return;
        try {
            String id = d.getId();
            String model = runAdbCommand("-s", id, "shell", "getprop", "ro.product.model");
            String manufacturer = runAdbCommand("-s", id, "shell", "getprop", "ro.product.manufacturer");
            String product = runAdbCommand("-s", id, "shell", "getprop", "ro.product.device");
            if (model != null && !model.isEmpty()) d.setModel(model);
            if (manufacturer != null && !manufacturer.isEmpty()) d.setManufacturer(manufacturer);
            if (product != null && !product.isEmpty()) d.setProduct(product);
            StringBuilder desc = new StringBuilder();
            if (manufacturer != null && !manufacturer.isEmpty()) desc.append(manufacturer);
            if (model != null && !model.isEmpty()) {
                if (desc.length() > 0) desc.append(' ');
                desc.append(model);
            }
            if (desc.length() > 0) d.setDescription(desc.toString());
        } catch (IOException ex) {
            log.debug("Error fetching properties for device {}", d.getId(), ex);
        }
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
                devices.add(Device.builder().id(parts[0]).status(parts[1]).build());
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
            // drain and log output asynchronously so process doesn't block
            logProcessOutput(p, deviceId);
            // monitor the process exit and remove from map when it ends
            new Thread(() -> {
                try {
                    int exit = p.waitFor();
                    log.info("scrcpy exited for {} (exit={})", deviceId, exit);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                } finally {
                    if (deviceId != null && !deviceId.isEmpty()) {
                        scrcpyProcesses.remove(deviceId);
                    }
                }
            }, "scrcpy-monitor-" + deviceId).start();
            // wait briefly to detect immediate failures
            try {
                Thread.sleep(300);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            if (!p.isAlive()) {
                // read recent logs and store exit info
                int exit = -1;
                try {
                    exit = p.exitValue();
                } catch (IllegalThreadStateException ex) {
                    // ignore
                }
                String recent = String.join("\n", getScrcpyLogs(deviceId));
                log.error("scrcpy for {} exited early (exit={}), logs:\n{}", deviceId, exit, recent);
                // ensure it's removed from the active list
                scrcpyProcesses.remove(deviceId);
                return false;
            }
            return true;
        } catch (IOException e) {
            log.error("Failed to start scrcpy", e);
            return false;
        }
    }

    private void logProcessOutput(Process p, String deviceId) {
        Thread t = new Thread(() -> {
            try (BufferedReader r = new BufferedReader(new InputStreamReader(p.getInputStream()))) {
                String line;
                while ((line = r.readLine()) != null) {
                    log.debug("[scrcpy {}] {}", deviceId, line);
                    appendScrcpyLog(deviceId, line);
                }
            } catch (IOException ex) {
                log.debug("Error reading scrcpy output for {}", deviceId, ex);
            }
        }, "scrcpy-output-" + deviceId);
        t.setDaemon(true);
        t.start();
    }

    private void appendScrcpyLog(String deviceId, String line) {
        if (deviceId == null) deviceId = "default";
        Deque<String> q = scrcpyLogs.computeIfAbsent(deviceId, k -> new ConcurrentLinkedDeque<>());
        q.addLast(line);
        while (q.size() > MAX_LOG_LINES) {
            q.pollFirst();
        }
    }

    public java.util.List<String> getScrcpyLogs(String deviceId) {
        if (deviceId == null) deviceId = "default";
        Deque<String> q = scrcpyLogs.get(deviceId);
        if (q == null) return java.util.Collections.emptyList();
        return new java.util.ArrayList<>(q);
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
