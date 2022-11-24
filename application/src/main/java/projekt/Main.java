package projekt;

import com.formdev.flatlaf.FlatDarkLaf;
import projekt.base.EuclideanDistanceCalculator;
import projekt.base.Location;
import projekt.delivery.archetype.*;
import projekt.delivery.rating.InTimeRater;
import projekt.delivery.rating.Rater;
import projekt.delivery.rating.RatingCriteria;
import projekt.delivery.routing.DijkstraPathCalculator;
import projekt.delivery.routing.Region;
import projekt.delivery.routing.VehicleManager;
import projekt.delivery.service.DeliveryService;
import projekt.delivery.service.ProblemSolverDeliveryService;
import projekt.delivery.simulation.BasicDeliverySimulation;
import projekt.delivery.simulation.Simulation;
import projekt.delivery.simulation.SimulationConfig;
import projekt.gui.MainFrame;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class Main {

    public static void main(String[] args) {

        // layer 1 - Region
        Region region = Region.builder()
            .addNeighborhood("Wiesbaden", new Location(-9, -4), 0.75)
            .addNeighborhood("Mainz", new Location(-8, 0), 0.6)
            .addNeighborhood("Frankfurt", new Location(8, -8), 1)
            .addNeighborhood("Darmstadt", new Location(6, 8), 0.5)
            .addNeighborhood("Ruesselsheim", new Location(-2, 0), 0.35)
            .addNeighborhood("Gross-Gerau", new Location(0, 5), 0.2)
            .addNeighborhood("Langen", new Location(6, 0), 0.25)
            .addNeighborhood("Offenbach", new Location(10, -7), 0.35)
            .addRestaurant(new Location(3,-1), Region.Restaurant.LOS_FOPBOTS_HERMANOS)
            .addNode("Mainspitzdreieck", new Location(-5, 0))
            .addNode("Wiesbadener Kreuz", new Location(-4, -5))
            .addNode("Moenchhof-Dreieck", new Location(1, -2))
            .addNode("Frankfurter Kreuz", new Location(4, -4))
            .addNode("Dreieck Mainz", new Location(-10, -1))
            .addEdge("A643", new Location(-10, -1), new Location(-9, -4))
            .addEdge("A60", new Location(-10, -1), new Location(-8, 0))
            .addEdge("A60_1", new Location(-5, 0), new Location(-8, 0))
            .addEdge("A671", new Location(-5, 0), new Location(-9, -4))
            .addEdge("A60_2", new Location(-5, 0), new Location(-2, 0))
            .addEdge("A66", new Location(-4, -5), new Location(-9, -4))
            .addEdge("A66_1", new Location(-4, -5), new Location(8, -8))
            .addEdge("A3", new Location(-4, -5), new Location(1, -2))
            .addEdge("A67", new Location(1, -2), new Location(-2, 0))
            .addEdge("A3_1", new Location(1, -2), new Location(4, -4))
            .addEdge("A5", new Location(4, -4), new Location(8, -8))
            .addEdge("A3_2", new Location(4, -4), new Location(10, -7))
            .addEdge("A5_1", new Location(4, -4), new Location(6, 0))
            .addEdge("A5_2", new Location(6, 0), new Location(6, 8))
            .addEdge("A67_1", new Location(0, 5), new Location(6, 8))
            .addEdge("A67_2", new Location(0, 5), new Location(-2, 0))
            .addEdge("Straße", new Location(3, -1), new Location(1, -2))
            .distanceCalculator(new EuclideanDistanceCalculator()) //TODO braucht man das noch?
            .build();

        // layer 2 - VehicleManager
        VehicleManager vehicleManager = VehicleManager.builder()
            .region(region)
            .pathCalculator(new DijkstraPathCalculator())
            .addVehicle(2, List.of())
            .addVehicle(2, List.of())
            .addVehicle(2, List.of())
            .addVehicle(2, List.of())
            .addVehicle(2, List.of())
            .addVehicle(2, List.of())
            .addVehicle(2, List.of())
            .addVehicle(2, List.of())
            .addVehicle(2, List.of())
            .addVehicle(2, List.of())
            .addVehicle(2, List.of())
            .build();

        final int simulationLength = 480;

        //OrderGenerator
        OrderGenerator.Factory orderGeneratorFactory = new FridayOrderGenerator.FactoryBuilder()
            .setOrderCount(1000)
            .setDeliveryInterval(15)
            .setVariance(0.5)
            .setMaxWeight(0.5)
            .setVehicleManager(vehicleManager)
            .setLastTick(simulationLength)
            .build();

        //ProblemArchetype
        ProblemArchetype problemArchetype = new ProblemArchetypeImpl(orderGeneratorFactory, vehicleManager, RatingCriteria.IN_TIME, 480);

        //layer 3 - DeliveryService
        DeliveryService deliveryService = new ProblemSolverDeliveryService(problemArchetype);

        //Rater
        Map<RatingCriteria, Rater.Factory> raterFactoryMap = new HashMap<>();
        raterFactoryMap.put(RatingCriteria.IN_TIME, new InTimeRater.FactoryBuilder()
            .setIgnoredTicksOff(5)
            .setMaxTicksOff(25)
            .build());

        // SimulationConfig
        SimulationConfig simulationConfig = new SimulationConfig(1000);

        // layer 4 - Simulation
        Simulation simulation = new BasicDeliverySimulation(simulationConfig, raterFactoryMap, deliveryService, orderGeneratorFactory, simulationLength);

        // the lasagna is complete

        // Gui Setup
        FlatDarkLaf.setup();
        MainFrame mainFrame = new MainFrame(region, vehicleManager, simulation);
        mainFrame.setVisible(true);
        simulation.addListener(mainFrame);

        //start simulation
        simulation.runSimulation(problemArchetype.getSimulationLength()); // -> blocks the thread until the simulation is finished.

        System.out.println(simulation.getRatingForCriterion(RatingCriteria.IN_TIME));
    }
}
