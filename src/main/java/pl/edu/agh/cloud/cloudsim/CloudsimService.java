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
import pl.edu.agh.cloud.connection.dto.Task;
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
    private int cloudId;

    public CloudsimService() {
        defineAvailableClouds();
        setCloudFromEnvVariable();
    }

    public TaskResponse processTask(Task taskRequest) throws CloudDoesNotExistException {
        try {
            setResources();
            prepareCloudlets(taskRequest);
            CloudSim.startSimulation();
            List<Cloudlet> cloudletReceivedList = broker.getCloudletReceivedList();
            CloudSim.stopSimulation();
            return prepareResponse(cloudletReceivedList.get(0));
        } catch (CloudDoesNotExistException e) {
            throw new CloudDoesNotExistException();
        } catch (Exception e) {
            e.printStackTrace();
            Log.printLine("The simulation has been terminated due to an unexpected error");
            return null;
        }
    }

    private void defineAvailableClouds() {
        List<CloudInfo> clouds = new ArrayList<>();
        clouds.add(new CloudInfo(0, 4000, 10000, 8192, 1000));
        clouds.add(new CloudInfo(1, 1000, 15000, 4096, 1000));
        clouds.add(new CloudInfo(2, 3000, 20000, 8192, 2000));
        this.cloudsInfo = clouds;
    }

    private void setCloudFromEnvVariable() {
        try {
            int cloud = Integer.parseInt(System.getenv("CLOUD_ID"));
            if (cloudsInfo.stream().map(CloudInfo::getId).noneMatch(cloudIdentifier -> cloudIdentifier == cloud)) {
                throw new CloudDoesNotExistException();
            }
            cloudId = cloud;
            System.out.println("Set cloudId to: " + cloud);

        } catch (NumberFormatException | CloudDoesNotExistException e) {
            cloudId = 0;
            System.out.println("Wrong cloudId given. Set cloudId to default value: 0.");
        }
    }

    private void setResources() throws CloudDoesNotExistException {
        int num_user = 1;
        Calendar calendar = Calendar.getInstance();
        boolean trace_flag = false;

        Log.printLine("Starting CloudSim...");
        CloudSim.init(num_user, calendar, trace_flag);

        Datacenter datacenter = createDatacenter();

        broker = createBroker();

        List<Vm> vmlist = createVM(broker.getId());
        broker.submitVmList(vmlist);
    }

    private Datacenter createDatacenter() {
        List<Host> hostList = new ArrayList<>();
        List<Pe> peList = new ArrayList<>();

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

    private List<Vm> createVM(int brokerId) {
        int pesNumber = 1; //number of cpus
        String vmm = "Xen"; //VMM name

        final CloudInfo cloudInfo = cloudsInfo.get(cloudId);
        LinkedList<Vm> list = new LinkedList<>();
        final Vm vm = new Vm(cloudId, brokerId, cloudInfo.getMips(), pesNumber, cloudInfo.getRam(),
                cloudInfo.getBandwidth(), cloudInfo.getStorage(), vmm, new CloudletSchedulerTimeShared());
        list.add(vm);

        return list;
    }

    private void prepareCloudlets(Task task) {
        int id = 0;
        List<Cloudlet> cloudletList = new ArrayList<>();
        UtilizationModel utilizationModel = new UtilizationModelFull();

        Cloudlet cloudlet = new Cloudlet(id, task.getCloudletSize(), task.getProcessingUnits(),
                task.getInputAndProgramFileSize(), task.getOutputFileSize(), utilizationModel,
                utilizationModel, utilizationModel);
        cloudlet.setUserId(broker.getId());
        cloudletList.add(cloudlet);

        broker.submitCloudletList(cloudletList);
    }

    private TaskResponse prepareResponse(Cloudlet cloudletReceived) {
        printCloudletList(cloudletReceived);
        Log.printLine("CloudSim simulation finished!");

        TaskResponse cloudletsExecutionResponse = new TaskResponse();
        double executionTime = cloudletReceived.getActualCPUTime();
        cloudletsExecutionResponse.setExecutionTime(executionTime);
        return cloudletsExecutionResponse;
    }

    private static void printCloudletList(Cloudlet cloudlet) {
        String indent = "    ";
        Log.printLine();
        Log.printLine("========== OUTPUT ==========");
        Log.printLine("Cloudlet ID" + indent + "STATUS" + indent +
                "Data center ID" + indent + "VM ID" + indent + "Time" + indent + "Start Time" + indent + "Finish Time");

        DecimalFormat dft = new DecimalFormat("###.##");

        Log.print(indent + cloudlet.getCloudletId() + indent + indent);

        if (cloudlet.getCloudletStatus() == Cloudlet.SUCCESS) {
            Log.print("SUCCESS");

            Log.printLine(indent + indent + cloudlet.getResourceId() + indent + indent + indent + cloudlet.getVmId() +
                    indent + indent + dft.format(cloudlet.getActualCPUTime()) + indent + indent + dft.format(cloudlet.getExecStartTime()) +
                    indent + indent + dft.format(cloudlet.getFinishTime()));
        }
    }
}
