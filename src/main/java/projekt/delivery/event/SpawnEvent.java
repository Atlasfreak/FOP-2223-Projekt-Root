package projekt.delivery.event;

import projekt.delivery.routing.Region;
import projekt.delivery.routing.Vehicle;

import java.time.LocalDateTime;

public interface SpawnEvent extends Event {

    Region.Node getNode();

    static SpawnEvent of(
        LocalDateTime time,
        Vehicle vehicle,
        Region.Node node
    ) {
        return new SpawnEventImpl(time, vehicle, node);
    }
}
