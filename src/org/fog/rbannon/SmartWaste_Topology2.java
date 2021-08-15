package org.fog.rbannon;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;

import org.cloudbus.cloudsim.Log;
import org.cloudbus.cloudsim.core.CloudSim;
import org.fog.application.AppEdge;
import org.fog.application.AppLoop;
import org.fog.application.Application;
import org.fog.application.selectivity.FractionalSelectivity;
import org.fog.entities.FogBroker;
import org.fog.entities.PhysicalTopology;
import org.fog.entities.Tuple;
import org.fog.placement.Controller;
import org.fog.placement.ModuleMapping;
import org.fog.placement.ModulePlacementEdgewards;
import org.fog.placement.ModulePlacementMapping;
import org.fog.utils.JsonToTopology;
import org.fog.entities.FogDevice;
import org.fog.utils.TimeKeeper;

/**
 * Simulation setup for EEG Beam Tractor Game extracting physical topology 
 * @author Ryan Bannon
 *
 */
public class SmartWaste_Topology2 {

	static List<FogDevice> fogDevices = new ArrayList<FogDevice>();
	private static boolean CLOUD = false;

	public static void main(String[] args) {

		Log.printLine("Starting Smart Waste Management System...");

		try {
			Log.disable();
			int num_user = 1; // number of cloud users
			Calendar calendar = Calendar.getInstance();
			boolean trace_flag = false; // mean trace events

			CloudSim.init(num_user, calendar, trace_flag);

			String appId = "swms";
			
			FogBroker broker = new FogBroker("broker");
			
			Application application = createApplication(appId, broker.getId());
			application.setUserId(broker.getId());
			
			/*
			 * Creating the physical topology from specified JSON file
			 */
			PhysicalTopology physicalTopology = JsonToTopology.getPhysicalTopology(broker.getId(), appId, "topologies/SmartWaste_Topology2");
			
			fogDevices = physicalTopology.getFogDevices();

			ModuleMapping moduleMapping = ModuleMapping.createModuleMapping(); // initializing a module mapping
			for(FogDevice device : fogDevices){
				if(device.getName().startsWith("B")){ // names of all Smart Bins start with 'b' 
					moduleMapping.addModuleToDevice("waste-info-module", device.getName());  // fixing 1 instance of the waste info module to each Smart Bin
				}
			}
			moduleMapping.addModuleToDevice("user_interface", "cloud"); // fixing instances of User Interface module in the Cloud
			if(CLOUD){ // if the mode of deployment is cloud-based
				moduleMapping.addModuleToDevice("master-module", "cloud"); // placing all instances of master module in the Cloud
			}

			Controller controller = new Controller("master-controller", physicalTopology.getFogDevices(), physicalTopology.getSensors(), 
					physicalTopology.getActuators());

			controller.submitApplication(application, 
					(CLOUD)?(new ModulePlacementMapping(physicalTopology.getFogDevices(), application, moduleMapping))
							:(new ModulePlacementEdgewards(physicalTopology.getFogDevices(), physicalTopology.getSensors(), physicalTopology.getActuators(), application, moduleMapping)));
			
			TimeKeeper.getInstance().setSimulationStartTime(Calendar.getInstance().getTimeInMillis());
			
			CloudSim.startSimulation();

			CloudSim.stopSimulation();

			Log.printLine("Waste Management simulation is finished!");
		} catch (Exception e) {
			e.printStackTrace();
			Log.printLine("Unwanted errors happen");
		}
	}
	
	@SuppressWarnings({ "serial" })
	private static Application createApplication(String appId, int userId){
		
		Application application = Application.createApplication(appId, userId);
		application.addAppModule("master-module", 10);
		application.addAppModule("waste-info-module", 10);
		application.addAppModule("user_interface", 10);
		
		application.addTupleMapping("waste-info-module", "ULTRASONIC", "THRESHOLD_REACHED", new FractionalSelectivity(1.0)); // 1.0 tuples of type THRESHOLD_REACHED are emitted by Motion Detector module per incoming tuple of type ULTRASONIC
		application.addTupleMapping("master-module", "THRESHOLD_REACHED", "ACT_PARAMS", new FractionalSelectivity(1.0)); // 1.0 tuples of type OBJECT_LOCATION are emitted by Object Detector module per incoming tuple of type THRESHOLD_REACHED
		application.addTupleMapping("master-module", "THRESHOLD_REACHED", "REQUEST_COLLECTION", new FractionalSelectivity(0.05)); // 0.05 tuples of type THRESHOLD_REACHED are emitted by Object Detector module per incoming tuple of type THRESHOLD_REACHED
	
		application.addAppEdge("ULTRASONIC", "waste-info-module", 1000, 20000, "ULTRASONIC", Tuple.UP, AppEdge.SENSOR); // adding edge from ULTRASONIC (sensor) to Motion Detector module carrying tuples of type ULTRASONIC
		application.addAppEdge("waste-info-module", "master-module", 2000, 2000, "THRESHOLD_REACHED", Tuple.UP, AppEdge.MODULE); // adding edge from Motion Detector to Object Detector module carrying tuples of type THRESHOLD_REACHED
		application.addAppEdge("master-module", "user_interface", 500, 2000, "REQUEST_COLLECTION", Tuple.UP, AppEdge.MODULE); // adding edge from Object Detector to User Interface module carrying tuples of type REQUEST_COLLECTION
		application.addAppEdge("master-module", "SWITCH", 100, 28, 100, "ACT_PARAMS", Tuple.DOWN, AppEdge.ACTUATOR); // adding edge from Object Tracker to ACT CONTROL (actuator) carrying tuples of type ACT_PARAMS
		
		final AppLoop loop1 = new AppLoop(new ArrayList<String>(){{add("waste-info-module");add("master-module");add("user_interface");}});
		final AppLoop loop2 = new AppLoop(new ArrayList<String>(){{add("master-module");add("SWITCH");}});
		List<AppLoop> loops = new ArrayList<AppLoop>(){{add(loop1);add(loop2);}};
		
		application.setLoops(loops);
		
		return application;
	}
}