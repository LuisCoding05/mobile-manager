package mobile.personal.manager.web;

import mobile.personal.manager.model.Device;
import mobile.personal.manager.service.AdbService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.util.List;

@Controller
public class HomeController {

    private static final Logger log = LoggerFactory.getLogger(HomeController.class);

    private final AdbService adbService;

    public HomeController(AdbService adbService) {
        this.adbService = adbService;
    }

    @GetMapping({"/", ""})
    public String index(Model model) {
        List<Device> devices = adbService.listDevices();
        log.info("Found {} devices", devices.size());
        model.addAttribute("devices", devices);
        model.addAttribute("activeDeviceIds", adbService.getActiveDeviceIds());
        return "index";
    }

    @GetMapping("/devices")
    public String devicesFragment(Model model) {
        List<Device> devices = adbService.listDevices();
        model.addAttribute("devices", devices);
        model.addAttribute("activeDeviceIds", adbService.getActiveDeviceIds());
        return "index :: deviceList";
    }

    @PostMapping("/disconnect/{id}")
    public String disconnect(@PathVariable("id") String id, RedirectAttributes ra) {
        try {
            boolean ok = adbService.stopScrcpyForDevice(id);
            if (ok) {
                ra.addFlashAttribute("message", "scrcpy detenido para " + id);
            } else {
                ra.addFlashAttribute("error", "No se encontró scrcpy corriendo para " + id);
            }
        } catch (Exception ex) {
            log.error("Error stopping scrcpy for {}", id, ex);
            ra.addFlashAttribute("error", "Error al detener scrcpy: " + ex.getMessage());
        }
        return "redirect:/";
    }

    @PostMapping("/connect/{id}")
    public String connect(@PathVariable("id") String id, RedirectAttributes ra) {
        try {
            boolean ok = adbService.startScrcpyForDevice(id);
            if (ok) {
                log.info("Started scrcpy for {}", id);
                ra.addFlashAttribute("message", "scrcpy iniciado para " + id);
            } else {
                log.error("Failed to start scrcpy for {}", id);
                java.util.List<String> logs = adbService.getScrcpyLogs(id);
                String snippet = logs.stream().limit(10).collect(java.util.stream.Collectors.joining("\n"));
                if (snippet.isEmpty()) snippet = "Sin salida de scrcpy";
                ra.addFlashAttribute("error", "No se pudo iniciar scrcpy para " + id + ". Salida:\n" + snippet);
            }
        } catch (Exception ex) {
            log.error("Error starting scrcpy for {}", id, ex);
            ra.addFlashAttribute("error", "Error al iniciar scrcpy: " + ex.getMessage());
        }
        return "redirect:/";
    }

    @GetMapping(value = "/logs/{id}")
    public org.springframework.http.ResponseEntity<String> logsForDevice(@PathVariable("id") String id) {
        java.util.List<String> logs = adbService.getScrcpyLogs(id);
        if (logs.isEmpty()) {
            return org.springframework.http.ResponseEntity.ok("No logs available for device " + id);
        }
        String result = String.join("\n", logs);
        return org.springframework.http.ResponseEntity.ok().header("Content-Type", "text/plain; charset=utf-8").body(result);
    }

}
