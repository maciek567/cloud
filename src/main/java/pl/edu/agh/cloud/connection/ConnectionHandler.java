package pl.edu.agh.cloud.connection;

import com.google.gson.Gson;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;
import pl.edu.agh.cloud.cloudsim.CloudDoesNotExistException;
import pl.edu.agh.cloud.cloudsim.CloudsimService;
import pl.edu.agh.cloud.connection.dto.CloudsInfoResponse;
import pl.edu.agh.cloud.connection.dto.TaskRequest;
import pl.edu.agh.cloud.connection.dto.TaskResponse;

@RestController
public class ConnectionHandler {

    private final CloudsimService cloudsimService;
    private final Gson gson;

    public ConnectionHandler(CloudsimService cloudsimService) {
        this.cloudsimService = cloudsimService;
        gson = new Gson();
    }

    @GetMapping(value = "/clouds")
    public ResponseEntity<String> getCloudsInfo() {
        CloudsInfoResponse cloudsInfoResponse = new CloudsInfoResponse();
        cloudsInfoResponse.setCloudInfoList(cloudsimService.getCloudsInfo());
        String responseJson = gson.toJson(cloudsInfoResponse);
        return new ResponseEntity<>(responseJson, HttpStatus.OK);
    }

    @PostMapping(value = "/process", consumes = "application/json")
    public ResponseEntity<String> processTask(@RequestBody String serializedTask) {
        TaskRequest taskRequest = gson.fromJson(serializedTask, TaskRequest.class);
        try {
            final TaskResponse taskResponse = cloudsimService.processTask(taskRequest);
            String responseJson = gson.toJson(taskResponse);
            return new ResponseEntity<>(responseJson, HttpStatus.OK);
        } catch (CloudDoesNotExistException e) {
            return new ResponseEntity<>("Cloud with that identifier not exists", HttpStatus.NOT_FOUND);
        }
    }

}
