package org.fog.rbannon;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.LinkedList;
import java.util.List;

import org.cloudbus.cloudsim.Host;
import org.cloudbus.cloudsim.Log;
import org.cloudbus.cloudsim.Pe;
import org.cloudbus.cloudsim.Storage;
import org.cloudbus.cloudsim.core.CloudSim;
import org.cloudbus.cloudsim.power.PowerHost;
import org.cloudbus.cloudsim.provisioners.RamProvisionerSimple;
import org.cloudbus.cloudsim.sdn.overbooking.BwProvisionerOverbooking;
import org.cloudbus.cloudsim.sdn.overbooking.PeProvisionerOverbooking;
import org.fog.application.AppEdge;
import org.fog.application.AppLoop;
import org.fog.application.Application;
import org.fog.application.selectivity.FractionalSelectivity;
import org.fog.entities.Actuator;
import org.fog.entities.FogBroker;
import org.fog.entities.FogDevice;
import org.fog.entities.FogDeviceCharacteristics;
import org.fog.entities.Sensor;
import org.fog.entities.Tuple;
import org.fog.placement.Controller;
import org.fog.placement.ModuleMapping;
import org.fog.placement.ModulePlacementEdgewards;
import org.fog.placement.ModulePlacementMapping;
import org.fog.policy.AppModuleAllocationPolicy;
import org.fog.scheduler.StreamOperatorScheduler;
import org.fog.utils.FogLinearPowerModel;
import org.fog.utils.FogUtils;
import org.fog.utils.TimeKeeper;
import org.fog.utils.distribution.DeterministicDistribution;

/**
 * Simulation setup for a Smart Waste Management System
 * @author Ryan Bannon
 *
 */
public class SmartWaste3_1_Fog {
	static List<FogDevice> fogDevices = new ArrayList<FogDevice>();
	static List<Sensor> sensors = new ArrayList<Sensor>();
	static List<Actuator> actuators = new ArrayList<Actuator>();
	static int numOfAreas = 5;
	static int numOfBinsPerArea = 5;
	
	private static boolean CLOUD = false;
	
	public static void main(String[] args) {
		Log.printLine("Starting Smart Waste Management System...");

		try {
			Log.disable();
			int num_user = 1; // number of cloud users
			Calendar calendar = Calendar.getInstance();
			boolean trace_flag = false; // mean trace events

			CloudSim.init(num_user, calendar, trace_flag);

			String appId = "swms"; // identifier of the application
			
			FogBroker broker = new FogBroker("broker");
			
			Application application = createApplication(appId, broker.getId());
			application.setUserId(broker.getId());
			
			createFogDevices(broker.getId(), appId);
			
			Controller controller = null;
			
			ModuleMapping moduleMapping = ModuleMapping.createModuleMapping(); // initializing a module mapping
			for(FogDevice device : fogDevices){
				if(device.getName().startsWith("b")){ // names of all Smart Bins start with 'b' 
					moduleMapping.addModuleToDevice("waste-info-module", device.getName());  // fixing 1 instance of the waste info module to each Smart Bin
				}
			}
			moduleMapping.addModuleToDevice("user_interface", "cloud"); // fixing instances of User Interface module in the Cloud
			if(CLOUD){ // if the mode of deployment is cloud-based
				moduleMapping.addModuleToDevice("master-module", "cloud"); // placing all instances of master module in the Cloud
			}
			
			controller = new Controller("master-controller", fogDevices, sensors, 
					actuators);
			
			controller.submitApplication(application, 
					(CLOUD)?(new ModulePlacementMapping(fogDevices, application, moduleMapping))
							:(new ModulePlacementEdgewards(fogDevices, sensors, actuators, application, moduleMapping)));
			
			TimeKeeper.getInstance().setSimulationStartTime(Calendar.getInstance().getTimeInMillis());
			
			CloudSim.startSimulation();

			CloudSim.stopSimulation();

			Log.printLine("Waste Management simulation is finished!");
		} catch (Exception e) {
			e.printStackTrace();
			Log.printLine("Unwanted errors happen");
		}
	}
	
	/**
	 * Creates the fog devices in the physical topology of the simulation.
	 * @param userId
	 * @param appId
	 */
	private static void createFogDevices(int userId, String appId) {
		FogDevice cloud = createFogDevice("cloud", 44800, 40000, 100, 10000, 0, 0.01, 16*103, 16*83.25);
		cloud.setParentId(-1);
		fogDevices.add(cloud);
		FogDevice proxy = createFogDevice("proxy-server", 2800, 4000, 10000, 10000, 1, 0.0, 107.339, 83.4333);
		proxy.setParentId(cloud.getId());
		proxy.setUplinkLatency(100); // latency of connection between proxy server and cloud is 100 ms
		fogDevices.add(proxy);
		for(int i=0;i<numOfAreas;i++){
			addArea(i+"", userId, appId, proxy.getId());
		}
	}

	private static FogDevice addArea(String id, int userId, String appId, int parentId){
		FogDevice router = createFogDevice("a-"+id, 2800, 4000, 10000, 10000, 2, 0.0, 107.339, 83.4333);
		fogDevices.add(router);
		router.setUplinkLatency(2); // latency of connection between router and proxy server is 2 ms
		for(int i=0;i<numOfBinsPerArea;i++){
			String mobileId = id+"-"+i;
			FogDevice bin = addBin(mobileId, userId, appId, router.getId()); // adding a smart bin to the physical topology. Smart bins have been modeled as fog devices as well.
			bin.setUplinkLatency(2); // latency of connection between bin and router is 2 ms
			fogDevices.add(bin);
		}
		router.setParentId(parentId);
		return router;
	}
	
	private static FogDevice addBin(String id, int userId, String appId, int parentId){
		FogDevice bin = createFogDevice("b-"+id, 500, 1000, 10000, 10000, 3, 0, 87.53, 82.44);
		bin.setParentId(parentId);
		Sensor sensor = new Sensor("s-"+id, "BIN", userId, appId, new DeterministicDistribution(5)); // inter-transmission time of bin (sensor) follows a deterministic distribution
		sensors.add(sensor);
		Actuator act = new Actuator("act-"+id, userId, appId, "ACT_CONTROL");
		actuators.add(act);
		sensor.setGatewayDeviceId(bin.getId());
		sensor.setLatency(1.0);  // latency of connection between bin (sensor) and the parent Smart Bin is 1 ms
		act.setGatewayDeviceId(bin.getId());
		act.setLatency(1.0);  // latency of connection between ACT Control and the parent Smart Bin is 1 ms
		return bin;
	}
	
	/**
	 * Creates a vanilla fog device
	 * @param nodeName name of the device to be used in simulation
	 * @param mips MIPS
	 * @param ram RAM
	 * @param upBw uplink bandwidth
	 * @param downBw downlink bandwidth
	 * @param level hierarchy level of the device
	 * @param ratePerMips cost rate per MIPS used
	 * @param busyPower
	 * @param idlePower
	 * @return
	 */
	private static FogDevice createFogDevice(String nodeName, long mips,
			int ram, long upBw, long downBw, int level, double ratePerMips, double busyPower, double idlePower) {
		
		List<Pe> peList = new ArrayList<Pe>();

		// 3. Create PEs and add these into a list.
		peList.add(new Pe(0, new PeProvisionerOverbooking(mips))); // need to store Pe id and MIPS Rating

		int hostId = FogUtils.generateEntityId();
		long storage = 1000000; // host storage
		int bw = 10000;

		PowerHost host = new PowerHost(
				hostId,
				new RamProvisionerSimple(ram),
				new BwProvisionerOverbooking(bw),
				storage,
				peList,
				new StreamOperatorScheduler(peList),
				new FogLinearPowerModel(busyPower, idlePower)
			);

		List<Host> hostList = new ArrayList<Host>();
		hostList.add(host);

		String arch = "x86"; // system architecture
		String os = "Linux"; // operating system
		String vmm = "Xen";
		double time_zone = 10.0; // time zone this resource located
		double cost = 3.0; // the cost of using processing in this resource
		double costPerMem = 0.05; // the cost of using memory in this resource
		double costPerStorage = 0.001; // the cost of using storage in this
										// resource
		double costPerBw = 0.0; // the cost of using bw in this resource
		LinkedList<Storage> storageList = new LinkedList<Storage>(); // we are not adding SAN
													// devices by now

		FogDeviceCharacteristics characteristics = new FogDeviceCharacteristics(
				arch, os, vmm, host, time_zone, cost, costPerMem,
				costPerStorage, costPerBw);

		FogDevice fogdevice = null;
		try {
			fogdevice = new FogDevice(nodeName, characteristics, 
					new AppModuleAllocationPolicy(hostList), storageList, 10, upBw, downBw, 0, ratePerMips);
		} catch (Exception e) {
			e.printStackTrace();
		}
		
		fogdevice.setLevel(level);
		return fogdevice;
	}

	/**
	 * Function to create the Intelligent Surveillance application in the DDF model. 
	 * @param appId unique identifier of the application
	 * @param userId identifier of the user of the application
	 * @return
	 */
	@SuppressWarnings({"serial" })
	private static Application createApplication(String appId, int userId){
		
		Application application = Application.createApplication(appId, userId);
		/*
		 * Adding modules (vertices) to the application model (directed graph)
		 */
		application.addAppModule("master-module", 10);
		application.addAppModule("waste-info-module", 10);
		application.addAppModule("user_interface", 10);
		
		/*
		 * Connecting the application modules (vertices) in the application model (directed graph) with edges
		 */
		application.addAppEdge("BIN", "waste-info-module", 1000, 20000, "BIN", Tuple.UP, AppEdge.SENSOR); // adding edge from BIN (sensor) to Motion Detector module carrying tuples of type BIN
		application.addAppEdge("waste-info-module", "master-module", 2000, 2000, "THRESHOLD_REACHED", Tuple.UP, AppEdge.MODULE); // adding edge from Motion Detector to Object Detector module carrying tuples of type THRESHOLD_REACHED
		application.addAppEdge("master-module", "user_interface", 500, 2000, "REQUEST_COLLECTION", Tuple.UP, AppEdge.MODULE); // adding edge from Object Detector to User Interface module carrying tuples of type REQUEST_COLLECTION
		application.addAppEdge("master-module", "ACT_CONTROL", 100, 28, 100, "ACT_PARAMS", Tuple.DOWN, AppEdge.ACTUATOR); // adding edge from Object Tracker to ACT CONTROL (actuator) carrying tuples of type ACT_PARAMS
		
		/*
		 * Defining the input-output relationships (represented by selectivity) of the application modules. 
		 */
		application.addTupleMapping("waste-info-module", "BIN", "THRESHOLD_REACHED", new FractionalSelectivity(1.0)); // 1.0 tuples of type THRESHOLD_REACHED are emitted by Motion Detector module per incoming tuple of type BIN
		application.addTupleMapping("master-module", "THRESHOLD_REACHED", "ACT_PARAMS", new FractionalSelectivity(1.0)); // 1.0 tuples of type OBJECT_LOCATION are emitted by Object Detector module per incoming tuple of type THRESHOLD_REACHED
		application.addTupleMapping("master-module", "THRESHOLD_REACHED", "REQUEST_COLLECTION", new FractionalSelectivity(0.05)); // 0.05 tuples of type THRESHOLD_REACHED are emitted by Object Detector module per incoming tuple of type THRESHOLD_REACHED
	
		/*
		 * Defining application loops (maybe incomplete loops) to monitor the latency of. 
		 * Here, we add two loops for monitoring : Motion Detector -> Object Detector -> Object Tracker and Object Tracker -> ACT Control
		 */
		final AppLoop loop1 = new AppLoop(new ArrayList<String>(){{add("waste-info-module");add("master-module");add("user_interface");}});
		final AppLoop loop2 = new AppLoop(new ArrayList<String>(){{add("master-module");add("ACT_CONTROL");}});
		List<AppLoop> loops = new ArrayList<AppLoop>(){{add(loop1);add(loop2);}};
		
		application.setLoops(loops);
		return application;
	}
}
