package pl.edu.agh.cloud.connection.dto;

import lombok.Data;

import java.util.List;

@Data
public class TaskResponse {

    private List<Double> executionTimes;

}
