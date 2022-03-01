package projekt;

import com.formdev.flatlaf.FlatDarkLaf;
import projekt.base.EuclideanDistanceCalculator;
import projekt.base.Location;
import projekt.delivery.DeliveryService;
import projekt.delivery.Simulation;
import projekt.delivery.SimulationConfig;
import projekt.delivery.rating.LinearRater;
import projekt.delivery.routing.DijkstraPathCalculator;
import projekt.delivery.routing.Region;
import projekt.delivery.routing.VehicleManager;
import projekt.gui.MainFrame;
import projekt.pizzeria.Pizzeria;

import javax.swing.*;
import java.time.Duration;
import java.time.LocalDateTime;

import com.formdev.flatlaf.FlatDarkLaf;

public class Main {

    public static void main(String[] args) {

        // // layer 1

         Region region = Region.builder()
             .addNode("origin", new Location(0, 0))
             .addNeighborhood("nodeA", new Location(-2, -2), 1.5)
             .addNode("nodeB", new Location(-2, 2))
             .addNeighborhood("nodeC", new Location(2, 2), 0.5)
             .addNode("nodeD", new Location(20, -20))
             .addEdge("edge1", new Location(0, 0), new Location(20, -20), Duration.ofMinutes(1))
             .addEdge("edge2", new Location(0, 0), new Location(2, 2), Duration.ofMinutes(1))
//             .addEdge("edge2", new Location(-2, 2), new Location(-2, 2), Duration.ofMinutes(1))
//             .addEdge("edge3", new Location(-2, -2), new Location(-2, 2), Duration.ofMinutes(1))
//             .addEdge("edge4", new Location(-2, -2), new Location(-2, 2), Duration.ofMinutes(1))
             .build();



        // // layer 2

         VehicleManager vehicleManager = VehicleManager.builder()
            .time(LocalDateTime.now())
            .region(region)
            .distanceCalculator(new EuclideanDistanceCalculator())
            .pathCalculator(new DijkstraPathCalculator())
            .warehouse(region.getNode(new Location(-2, -2)))
            .build();

        Region.Edge e = region.getEdge(new Location(2, 2), new Location(1, 3));
        VehicleManager.Occupied<Region.Edge> o = vehicleManager.getOccupied(e);

        // // layer 3

         DeliveryService deliveryService = DeliveryService.SIMPLE.create(vehicleManager, new LinearRater(), new Simulation() {
             @Override
             public void onStateUpdated() {

             }
         }, new SimulationConfig(50));

        // // layer 4

         Pizzeria pizzeria = Pizzeria.LOS_FOPBOTS_HERMANOS.create(deliveryService);

        // the lasagna is complete

        // Gui Setup
        FlatDarkLaf.setup();
        SwingUtilities.invokeLater(()->{
//            new MainFrame(null, null, null, null).setVisible(true); // -> starts GUI thread
             new MainFrame(region, vehicleManager, deliveryService, pizzeria).setVisible(true); // -> starts GUI thread

        });

        // deliveryService.runSimulation(); // -> blocks the thread until the simulation is finished.
    }
}
