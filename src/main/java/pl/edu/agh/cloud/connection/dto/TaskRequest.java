package pl.edu.agh.cloud.connection.dto;

import lombok.Data;

import java.util.List;

@Data
public class TaskRequest {

    private List<CloudletInfo> cloudlets;
    private int cloudId;

}
