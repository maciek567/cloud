package pl.edu.agh.cloud.cloudsim;

import lombok.Getter;
import lombok.Setter;
import org.cloudbus.cloudsim.*;
import org.cloudbus.cloudsim.core.CloudSim;
import org.cloudbus.cloudsim.provisioners.BwProvisionerSimple;
import org.cloudbus.cloudsim.provisioners.PeProvisionerSimple;
import org.cloudbus.cloudsim.provisioners.RamProvisionerSimple;
import org.springframework.stereotype.Service;
import pl.edu.agh.cloud.connection.dto.CloudInfo;
import pl.edu.agh.cloud.connection.dto.CloudletInfo;
import pl.edu.agh.cloud.connection.dto.TaskRequest;
import pl.edu.agh.cloud.connection.dto.TaskResponse;

import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.LinkedList;
import java.util.List;

@Service
public class CloudsimService {

    @Getter
    @Setter
    private List<CloudInfo> cloudsInfo;
    private DatacenterBroker broker;

    public CloudsimService() {
        List<CloudInfo> clouds = new ArrayList<>();
        clouds.add(new CloudInfo(0, 10000, 10000, 8192, 1000));
        clouds.add(new CloudInfo(1, 30000, 100000, 16384, 1000));
        clouds.add(new CloudInfo(2, 4000, 10000, 8192, 1000));
        this.cloudsInfo = clouds;
    }

    public TaskResponse processTask(TaskRequest taskRequest) throws CloudDoesNotExistException {
        try {
            setResources(taskRequest.getCloudId());
            prepareCloudlets(taskRequest);
            CloudSim.startSimulation();
            List<Cloudlet> cloudletReceivedList = broker.getCloudletReceivedList();
            CloudSim.stopSimulation();
            return prepareResponse(cloudletReceivedList);
        } catch (CloudDoesNotExistException e) {
            throw new CloudDoesNotExistException();
        } catch (Exception e) {
            e.printStackTrace();
            Log.printLine("The simulation has been terminated due to an unexpected error");
            return null;
        }
    }

    private void setResources(int cloudId) throws CloudDoesNotExistException {
        int num_user = 1;
        Calendar calendar = Calendar.getInstance();
        boolean trace_flag = false;

        Log.printLine("Starting CloudSim...");
        CloudSim.init(num_user, calendar, trace_flag);

        Datacenter datacenter = createDatacenter(cloudId);

        broker = createBroker();

        List<Vm> vmlist = createVM(cloudId, broker.getId());
        broker.submitVmList(vmlist);
    }

    private Datacenter createDatacenter(int cloudId) throws CloudDoesNotExistException {
        List<Host> hostList = new ArrayList<>();
        List<Pe> peList = new ArrayList<>();

        if (cloudsInfo.stream().map(CloudInfo::getId).noneMatch(cloudIdentifier -> cloudIdentifier == cloudId)) {
            throw new CloudDoesNotExistException();
        }

        peList.add(new Pe(0, new PeProvisionerSimple(cloudsInfo.get(cloudId).getMips())));
        int hostId = 0;

        hostList.add(
                new Host(
                        hostId,
                        new RamProvisionerSimple(cloudsInfo.get(cloudId).getRam()),
                        new BwProvisionerSimple(cloudsInfo.get(cloudId).getBandwidth()),
                        cloudsInfo.get(cloudId).getStorage(),
                        peList,
                        new VmSchedulerSpaceShared(peList)
                )
        );

        String arch = "x86";
        String os = "Linux";
        String vmm = "Xen";
        double time_zone = 10.0;
        double cost = 3.0;
        double costPerMem = 0.05;
        double costPerStorage = 0.001;
        double costPerBw = 0.0;
        DatacenterCharacteristics characteristics = new DatacenterCharacteristics(
                arch, os, vmm, hostList, time_zone, cost, costPerMem, costPerStorage, costPerBw);

        Datacenter datacenter = null;
        LinkedList<Storage> storageList = new LinkedList<>();
        try {
            datacenter = new Datacenter("Datacenter", characteristics, new VmAllocationPolicySimple(hostList), storageList, 0);
        } catch (Exception e) {
            e.printStackTrace();
        }
        return datacenter;
    }

    private static DatacenterBroker createBroker() {
        DatacenterBroker broker;
        try {
            broker = new DatacenterBroker("Broker");
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
        return broker;
    }

    private List<Vm> createVM(int cloudId, int brokerId) {
        int pesNumber = 1; //number of cpus
        String vmm = "Xen"; //VMM name

        final CloudInfo cloudInfo = cloudsInfo.get(cloudId);
        LinkedList<Vm> list = new LinkedList<>();
        final Vm vm = new Vm(cloudId, brokerId, cloudInfo.getMips(), pesNumber, cloudInfo.getRam(),
                cloudInfo.getBandwidth(), cloudInfo.getStorage(), vmm, new CloudletSchedulerTimeShared());
        list.add(vm);

        return list;
    }

    private void prepareCloudlets(TaskRequest taskRequest) {
        int id = 0;
        List<Cloudlet> cloudletList = new ArrayList<>();
        UtilizationModel utilizationModel = new UtilizationModelFull();

        for (CloudletInfo cloudletInfo : taskRequest.getCloudlets()) {
            Cloudlet cloudlet = new Cloudlet(id, cloudletInfo.getCloudletSize(), cloudletInfo.getProcessingUnits(),
                    cloudletInfo.getInputAndProgramFileSize(), cloudletInfo.getOutputFileSize(), utilizationModel,
                    utilizationModel, utilizationModel);
            cloudlet.setUserId(broker.getId());
            cloudletList.add(cloudlet);
            id++;
        }

        broker.submitCloudletList(cloudletList);
    }

    private TaskResponse prepareResponse(List<Cloudlet> cloudletReceivedList) {
        printCloudletList(cloudletReceivedList);
        Log.printLine("CloudSim simulation finished!");

        List<Double> executionTimes = new ArrayList<>();
        TaskResponse cloudletsExecutionResponse = new TaskResponse();
        for (Cloudlet cloudlet : cloudletReceivedList) {
            executionTimes.add(cloudlet.getActualCPUTime());
        }
        cloudletsExecutionResponse.setExecutionTimes(executionTimes);
        return cloudletsExecutionResponse;
    }

    private static void printCloudletList(List<Cloudlet> list) {
        int size = list.size();
        Cloudlet cloudlet;

        String indent = "    ";
        Log.printLine();
        Log.printLine("========== OUTPUT ==========");
        Log.printLine("Cloudlet ID" + indent + "STATUS" + indent +
                "Data center ID" + indent + "VM ID" + indent + "Time" + indent + "Start Time" + indent + "Finish Time");

        DecimalFormat dft = new DecimalFormat("###.##");
        for (int i = 0; i < size; i++) {
            cloudlet = list.get(i);
            Log.print(indent + cloudlet.getCloudletId() + indent + indent);

            if (cloudlet.getCloudletStatus() == Cloudlet.SUCCESS) {
                Log.print("SUCCESS");

                Log.printLine(indent + indent + cloudlet.getResourceId() + indent + indent + indent + cloudlet.getVmId() +
                        indent + indent + dft.format(cloudlet.getActualCPUTime()) + indent + indent + dft.format(cloudlet.getExecStartTime()) +
                        indent + indent + dft.format(cloudlet.getFinishTime()));
            }
        }
    }
}
