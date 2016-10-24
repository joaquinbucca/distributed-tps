package wishlist;

import org.apache.avro.AvroRemoteException;
import org.apache.avro.ipc.NettyServer;
import org.apache.avro.ipc.specific.SpecificResponder;
import tp1.registry.RegistryService;
import tp1.registry.Service;
import tp1.registry.ServiceMapper;
import tp1.registry.ServiceType;

import java.net.InetSocketAddress;
import java.util.*;

import static java.util.Collections.emptyList;
import static java.util.stream.Collectors.toList;

/**
 * Registry implementation
 */
@SuppressWarnings("ArraysAsListWithZeroOrOneArgument")
public class RegistryImpl implements RegistryService {
    private final Map<ServiceType, ServiceMapper> servicesMap = new HashMap<>();
    private final Map<String, Timer> timers = new HashMap<>();
    private final Optional<Logger> logger = Logger.logger();

    @Override
    public CharSequence publishService(ServiceType type, CharSequence ip, int time, int port) throws AvroRemoteException {
        logger.map(l -> l.info("Publish service " + type.name().toLowerCase() + " with address " + getAddress(ip, port), ServiceType.REGISTRY, "registry address"));
        final String address = getAddress(ip, port);
        if (servicesMap.containsKey(type)) {
            final ServiceMapper serviceMapper = servicesMap.get(type);
            final Service service = getService(serviceMapper, address);
            if (service != null) {
                service.setTimeToLive(time);
                resetTimer(serviceMapper, time, address);
            } else serviceMapper.getServices().put(address, new Service(ip, port, time));
        } else {
            final Map<CharSequence, Service> map = new HashMap<>();
            map.put(address, new Service(ip, port, time));
            servicesMap.put(type, new ServiceMapper(map));
        }
        return ip;
    }

    private Service getService(ServiceMapper serviceList, String address) { return serviceList.getServices().get(address); }

    private String getAddress(CharSequence ip, int port) { return ip + ":" + port; }

    @Override public CharSequence keepAlive(ServiceType type, CharSequence ip, int port) throws AvroRemoteException {
        logger.map(l -> l.info("Keep alive " + type.name().toLowerCase() + " with address " + getAddress(ip, port), ServiceType.REGISTRY, "registry address"));
        if (servicesMap.containsKey(type)){
            final ServiceMapper mapper = servicesMap.get(type);
            final String address = getAddress(ip, port);
            final Service service = getService(mapper, address);
            resetTimer(mapper, service.getTimeToLive(), address);
        }
        return "";
    }

    private void resetTimer(final ServiceMapper mapper, Integer time, final String address) {
        if (timers.containsKey(address)) timers.get(address).cancel();
        final Timer timer = new Timer();
        timer.schedule(new TimerTask() {
            @Override public void run() {
                logger.map(l -> l.warning("Killed service with address " + address, ServiceType.REGISTRY, "registry address"));
                mapper.getServices().remove(address);
            }
        }, time);
        timers.put(address, timer);
    }

    @Override
    public List<Service> getAllServicesButMe(ServiceType serviceType, CharSequence ip, int port) throws AvroRemoteException {
        if (servicesMap.containsKey(serviceType)){
            final Collection<Service> values = servicesMap.get(serviceType).getServices().values();
            return values.stream()
                    .filter(s -> !(getAddress(s.getIp(), s.getPort()))
                            .equals(getAddress(ip, port))).collect(toList());
        }
        return emptyList();
    }

    @Override public List<Service> getAvailableServices(ServiceType serviceType) throws AvroRemoteException {
        if (servicesMap.containsKey(serviceType)) return new ArrayList<>(servicesMap.get(serviceType).getServices().values());
        return emptyList();
    }

    public static void main(String[] args) {
        final SpecificResponder responder = new SpecificResponder(RegistryService.class, new RegistryImpl());
        final int port = 65111;
        System.out.println("Starting registry");
        final NettyServer server = new NettyServer(responder, new InetSocketAddress(port));
        server.start();
        System.out.println("Registry started");
    }
}
