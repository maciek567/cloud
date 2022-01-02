package pl.edu.agh.cloud.connection.dto;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class CloudInfo {

    private int id;
    private int mips;
    private int storage;
    private int ram;
    private int bandwidth;

}
