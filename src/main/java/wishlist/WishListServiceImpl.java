package wishlist;

import org.apache.avro.AvroRemoteException;
import org.apache.avro.ipc.NettyTransceiver;
import org.apache.avro.ipc.specific.SpecificRequestor;
import tp1.registry.RegistryService;
import tp1.registry.Service;
import tp1.registry.ServiceType;
import tp1.wishList.*;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.util.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import static java.util.Collections.emptyList;
import static tp1.wishList.Event.ADD;
import static tp1.wishList.Event.REMOVE;

/**
 * WishList service implementation
 */
public class WishListServiceImpl implements WishListService {
    private final Map<CharSequence, WishList> wishLists;
    private final String address;
    private final int port;
    private final RegistryService registry;
    private final Optional<Logger> logger = Logger.logger();

    public WishListServiceImpl(String address, int port, RegistryService registry) {
        this.address = address;
        this.port = port;
        this.registry = registry;
        wishLists = new HashMap<>();
    }

    @Override public WishList addItem(Item item, CharSequence customerId, boolean isReplication) {
        WishList wishList = wishLists.get(customerId);
        if(wishList == null){
            wishList = WishList.newBuilder().setEvents(new ArrayList<>()).setItems(new ArrayList<>()).build();
            wishLists.put(customerId, wishList);
        }
        wishList.getEvents().add(new WishListEvent(item, ADD));
        wishList.getItems().add(item);
        logger.map(l -> l.info("Add Item " + item.getId() + " to consumer " + customerId, ServiceType.WISH_LIST, address + ":" + port));
        if(!isReplication) replicate(item, customerId, ADD);
        return wishList;
    }

    private void replicate(final Item item, final CharSequence customerId, Event event) {
        logger.map(l -> l.info(event.name() + ": Replicating Item " + item.getId() + " to consumer " + customerId, ServiceType.WISH_LIST, address + ":" + port));
        final ExecutorService executor = Executors.newSingleThreadExecutor();

        executor.execute(() -> getOtherWishLists().stream().forEach(service -> {
            try {
                if(event == ADD) getClient(service).addItem(item, customerId, true);
                else if(event == REMOVE) getClient(service).deleteItem(item, customerId, true);
            } catch (final IOException e) {
                logger.map(l -> l.warning(event.name() + ": Can't replicate item " + item.getId() + " to consumer " + customerId, ServiceType.WISH_LIST, service.getIp() + ":" + service.getPort()));
            }
        }));
    }

    public WishListService getClient(Service service) throws IOException {
        return SpecificRequestor.getClient(WishListService.class,
                new NettyTransceiver(new InetSocketAddress(service.getIp().toString(), service.getPort())));
    }

    @SuppressWarnings("unchecked")
    private List<Service> getOtherWishLists() {
        try {
            return registry.getAllServicesButMe(ServiceType.WISH_LIST, address, port);
        } catch (final AvroRemoteException e) { e.printStackTrace(); }
        return emptyList();
    }

    @Override public WishList getWishList(CharSequence customerId) throws AvroRemoteException { return wishLists.get(customerId); }

    @Override public Map<CharSequence, WishList> getAllWishLists() throws AvroRemoteException { return wishLists; }

    @Override public boolean rebuildWishList(Map<CharSequence, WishList> wishList) throws AvroRemoteException {
        wishList.entrySet().forEach(entry -> {
            wishLists.put(entry.getKey(), entry.getValue()); //todo: i should rebuild from when this service went down
        });
        return false;
    }

    @Override public WishList deleteItem(Item item, CharSequence customerId, boolean isReplication) throws AvroRemoteException {
        final WishList wishList = wishLists.get(customerId);
        final List<WishListEvent> events = wishList.getEvents();
        events.add(new WishListEvent(item, Event.REMOVE));
        if (!wishList.getItems().remove(item)) {
            logger.map(l -> l.error("Item " + item.getId() + " from consumer " + customerId + " not contained in wish list", ServiceType.WISH_LIST, address + ":" + port));
            System.out.println("Item not contained in wish list");
        } else {
            logger.map(l -> l.info("Remove Item " + item.getId() + " from consumer " + customerId, ServiceType.WISH_LIST, address + ":" + port));
            System.out.println("Delete item");
            if(!isReplication) replicate(item, customerId, Event.REMOVE);
        }
        return wishList;
    }
}
