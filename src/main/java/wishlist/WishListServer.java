package wishlist;

import org.apache.avro.AvroRemoteException;
import org.apache.avro.ipc.NettyServer;
import org.apache.avro.ipc.NettyTransceiver;
import org.apache.avro.ipc.specific.SpecificRequestor;
import org.apache.avro.ipc.specific.SpecificResponder;
import tp1.registry.RegistryService;
import tp1.registry.Service;
import tp1.registry.ServiceType;
import tp1.wishList.WishListService;

import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.util.List;
import java.util.Optional;
import java.util.Timer;
import java.util.TimerTask;

import static java.lang.Integer.parseInt;
import static tp1.registry.ServiceType.WISH_LIST;

/**
 * WishList server that keeps wishList service implementation and receives requests for it
 */
@SuppressWarnings("NestedTryStatement")
public class WishListServer {
    public static final int TIME = 10000;
    private static RegistryService registry = null;
    private static final Optional<Logger> logger = Logger.logger();

    public static void main(String[] args) throws IOException {
        final String ip = InetAddress.getLocalHost().getHostAddress();
        final int port = parseInt(args[0]);
        final String registryIp = args[1];
        final int registryPort = 65111;

        initRegistry(registryIp, registryPort);

        final SpecificResponder responder = new SpecificResponder(WishListService.class, new WishListServiceImpl(ip, port, registry));
        final NettyServer server = new NettyServer(responder, new InetSocketAddress(port));
        server.start();

        System.out.println("WishList server started");
        publishWishList(ip, port);
        keepServiceAlive(ip, port);
    }

    private static void initRegistry(String registryIp, int registryPort) throws IOException {
        final NettyTransceiver client = new NettyTransceiver(new InetSocketAddress(registryIp, registryPort));
        registry = SpecificRequestor.getClient(RegistryService.class, client);
    }

    private static void keepServiceAlive(final String ip, final int port){
        final Timer timer = new Timer();
        timer.schedule(new TimerTask() {
            @Override
            public void run() {
                try {
                    registry.keepAlive(WISH_LIST, ip, port);
                } catch (final AvroRemoteException ignored) { }
            }
        }, TIME / 2, TIME / 2);
    }

    private static void publishWishList(String ip, int port) throws AvroRemoteException {
        final List<Service> allButMe = registry.getAllServicesButMe(WISH_LIST, ip, port);
        rebuildWishList(allButMe, ip, port);
        registry.publishService(WISH_LIST, ip, TIME, port);
    }

    private static void rebuildWishList(List<Service> allButMe, String ip, int port) {
        if (!allButMe.isEmpty()) {
            final Service service = Randomizer.getRandomService(allButMe);
            try {
                final NettyTransceiver transceiver = new NettyTransceiver(new InetSocketAddress(service.getIp().toString(), service.getPort()));
                final WishListService wishList = SpecificRequestor.getClient(WishListService.class, transceiver);

                try {
                    final NettyTransceiver trans = new NettyTransceiver(new InetSocketAddress(ip, port));

                    final WishListService newWishList = SpecificRequestor.getClient(WishListService.class, trans);

                    newWishList.rebuildWishList(wishList.getAllWishLists());
                } catch (final IOException ex) {
                    logger.map(l -> l.warning("Service unavailable", ServiceType.WISH_LIST, ip + ":" + port));
                }

                transceiver.close(true);
            } catch (final IOException ex){
                allButMe.remove(service);
                logger.map(l -> l.warning("Service unavailable", ServiceType.WISH_LIST, service.getIp().toString() + ":" + service.getPort()));
                rebuildWishList(allButMe, ip, port);
            }
        }
    }
}