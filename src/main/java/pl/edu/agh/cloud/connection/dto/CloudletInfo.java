package pl.edu.agh.cloud.connection.dto;

import lombok.Data;

@Data
public class CloudletInfo {

    private int cloudletSize;
    private int inputAndProgramFileSize;
    private int outputFileSize;
    private int processingUnits;

}
