package mobile.personal.manager.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Device {

    private String id;
    private String status;
    private String model;
    private String manufacturer;
    private String product;
    private String description;

    public String getFriendlyStatus() {
        if (status == null) return "Desconocido";
        switch (status.toLowerCase()) {
            case "device":
                return "Conectado";
            case "offline":
                return "Desconectado";
            case "unauthorized":
                return "No autorizado";
            default:
                return status;
        }
    }

    public String getShortId() {
        if (id == null) return "";
        return id.length() > 8 ? id.substring(0, 8) + "…" : id;
    }

}
