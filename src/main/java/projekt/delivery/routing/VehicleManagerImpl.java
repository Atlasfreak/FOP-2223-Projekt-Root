package projekt.delivery.routing;

import org.jetbrains.annotations.Nullable;
import projekt.base.DistanceCalculator;
import projekt.delivery.event.Event;
import projekt.delivery.event.EventBus;
import projekt.delivery.event.SpawnEvent;
import projekt.food.FoodType;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.stream.Collectors;

class VehicleManagerImpl implements VehicleManager {

    private LocalDateTime currentTime;
    private final Region region;
    final Map<Region.Node, OccupiedNodeImpl<? extends Region.Node>> occupiedNodes;
    final Map<Region.Edge, OccupiedEdgeImpl> occupiedEdges;
    private final DistanceCalculator distanceCalculator;
    private final PathCalculator pathCalculator;
    private final OccupiedWarehouseImpl warehouse;
    private final List<VehicleImpl> vehiclesToSpawn = new ArrayList<>();
    private final List<VehicleImpl> vehicles = new ArrayList<>();
    private final Collection<Vehicle> unmodifiableVehicles = Collections.unmodifiableCollection(vehicles);
    private final Set<AbstractOccupied<?>> allOccupied;
    private final EventBus eventBus = new EventBus();

    VehicleManagerImpl(
        LocalDateTime currentTime,
        Region region,
        DistanceCalculator distanceCalculator,
        PathCalculator pathCalculator,
        Region.Node warehouse
    ) {
        this.currentTime = currentTime;
        this.region = region;
        this.distanceCalculator = distanceCalculator;
        this.pathCalculator = pathCalculator;
        this.warehouse = new OccupiedWarehouseImpl(warehouse, this);
        occupiedNodes = toOccupiedNodes(region.getNodes());
        occupiedEdges = toOccupiedEdges(region.getEdges());
        allOccupied = getAllOccupied();
    }

    private OccupiedNodeImpl<? extends Region.Node> toOccupied(Region.Node node) {
        return node.equals(warehouse.getComponent())
            ? warehouse
            : node instanceof Region.Neighborhood
            ? new OccupiedNeighborhoodImpl((Region.Neighborhood) node, this)
            : new OccupiedNodeImpl<>(node, this);
    }

    private Map<Region.Node, OccupiedNodeImpl<? extends Region.Node>> toOccupiedNodes(Collection<Region.Node> nodes) {
        return nodes.stream()
            .map(this::toOccupied)
            .collect(Collectors.toUnmodifiableMap(Occupied::getComponent, Function.identity()));
    }

    private Map<Region.Edge, OccupiedEdgeImpl> toOccupiedEdges(Collection<Region.Edge> edges) {
        return edges.stream()
            .map(edge -> new OccupiedEdgeImpl(edge, this))
            .collect(Collectors.toUnmodifiableMap(Occupied::getComponent, Function.identity()));
    }

    private Set<AbstractOccupied<?>> getAllOccupied() {
        final Set<AbstractOccupied<?>> result = new HashSet<>();
        result.addAll(occupiedNodes.values());
        result.addAll(occupiedEdges.values());
        return Collections.unmodifiableSet(result);
    }

    private OccupiedNodeImpl<?> findNode(Predicate<? super Occupied<? extends Region.Node>> nodePredicate) {
        return occupiedNodes.values().stream()
            .filter(nodePredicate)
            .findFirst()
            .orElseThrow(() -> new IllegalArgumentException("Could not find node with given predicate"));
    }

    @Override
    public Region getRegion() {
        return region;
    }

    @Override
    public DistanceCalculator getDistanceCalculator() {
        return distanceCalculator;
    }

    @Override
    public Collection<Vehicle> getVehicles() {
        return unmodifiableVehicles;
    }

    @Override
    public OccupiedWarehouseImpl getWarehouse() {
        return warehouse;
    }

    Vehicle addVehicle(
        double capacity,
        Collection<FoodType<?, ?>> compatibleFoodTypes,
        @Nullable Predicate<? super Occupied<? extends Region.Node>> nodePredicate
    ) {
        final OccupiedNodeImpl<?> occupied;
        if (nodePredicate == null) {
            occupied = warehouse;
        } else {
            occupied = findNode(nodePredicate);
        }
        final VehicleImpl vehicle = new VehicleImpl(
            vehicles.size() + vehiclesToSpawn.size(),
            capacity,
            compatibleFoodTypes,
            occupied,
            this
        );
        vehiclesToSpawn.add(vehicle);
        vehicle.setOccupied(occupied);
        return vehicle;
    }

    @Override
    @SuppressWarnings("unchecked")
    public <C extends Region.Component<C>> AbstractOccupied<C> getOccupied(C component) {
        Objects.requireNonNull(component, "component");
        if (component instanceof Region.Node) {
            final @Nullable AbstractOccupied<C> result = (AbstractOccupied<C>) occupiedNodes.get(component);
            if (result == null) {
                throw new IllegalArgumentException("Could not find occupied node for " + component);
            }
            return result;
        } else if (component instanceof Region.Edge) {
            final @Nullable AbstractOccupied<C> result = (AbstractOccupied<C>) occupiedEdges.get(component);
            if (result == null) {
                throw new IllegalArgumentException("Could not find occupied edge for " + component);
            }
            return result;
        }
        throw new IllegalArgumentException("Component is not of recognized subtype: " + component.getClass().getName());
    }

    @Override
    public OccupiedNeighborhood getOccupiedNeighborhood(Region.Node component) {
        Objects.requireNonNull(component, "component");
        final @Nullable OccupiedNodeImpl<?> node = occupiedNodes.get(component);
        if (node instanceof OccupiedNeighborhood) {
            return (OccupiedNeighborhood) node;
        } else {
            throw new IllegalArgumentException("Component " + component + " is not a neighborhood");
        }
    }

    @Override
    public Collection<Occupied<? extends Region.Node>> getOccupiedNodes() {
        return Collections.unmodifiableCollection(occupiedNodes.values());
    }

    @Override
    public Collection<Occupied<? extends Region.Edge>> getOccupiedEdges() {
        return Collections.unmodifiableCollection(occupiedEdges.values());
    }

    @Override
    public EventBus getEventBus() {
        return eventBus;
    }

    @Override
    public LocalDateTime getCurrentTime() {
        return currentTime;
    }

    private void spawnVehicle(VehicleImpl vehicle) {
        vehicles.add(vehicle);
        OccupiedWarehouseImpl warehouse = (OccupiedWarehouseImpl) vehicle.getOccupied();
        warehouse.vehicles.put(vehicle, new AbstractOccupied.VehicleStats(currentTime, null));
        getEventBus().queuePost(SpawnEvent.of(currentTime, vehicle, warehouse.getComponent()));
    }

    @Override
    public List<Event> tick() {
        for (VehicleImpl vehicle : vehiclesToSpawn) {
            spawnVehicle(vehicle);
        }
        vehiclesToSpawn.clear();
        currentTime = currentTime.plusMinutes(1);
        // It is important that nodes are ticked before edges
        // This only works because edge ticking is idempotent
        // Otherwise, there may be two state changes in a single tick.
        // For example, a node tick may move a vehicle onto an edge.
        // Ticking this edge afterwards does not move the vehicle further along the edge
        // compared to a vehicle already on the edge.
        occupiedNodes.values().forEach(AbstractOccupied::tick);
        occupiedEdges.values().forEach(AbstractOccupied::tick);
        return eventBus.popEvents(currentTime);
    }

    @Override
    public PathCalculator getPathCalculator() {
        return pathCalculator;
    }
}
