package wishlist;

import org.apache.avro.AvroRemoteException;
import org.apache.avro.ipc.NettyTransceiver;
import org.apache.avro.ipc.specific.SpecificRequestor;
import tp1.registry.RegistryService;
import tp1.registry.Service;
import tp1.registry.ServiceType;
import tp1.wishList.Item;
import tp1.wishList.WishListService;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.UnknownHostException;
import java.util.List;
import java.util.Optional;
import java.util.Scanner;

import static wishlist.Randomizer.getRandomService;

/**
 * Client that will control it's own wish list
 */
public class Client {
    private static RegistryService registry = null;
    private static final Optional<Logger> logger = Logger.logger();

    public static void main(String[] args) throws IOException {
        final String ip= args[0];
        final int port= 65111;
        final String customerId= args[1];
        final NettyTransceiver client = new NettyTransceiver(new InetSocketAddress(ip, port));
        registry = SpecificRequestor.getClient(RegistryService.class, client);

        final Scanner scanner = new Scanner(System.in);
        System.out.println("Welcome " + customerId + " to WishList.com :");
        String opt = "-1";
        while (!"0".equals(opt)) {
            printMenu();
            opt = scanner.next();
            switch (opt){
                case "0":
                    break;
                case "1":
                    instructionsToAddItem(scanner, customerId);
                    break;
                case "2":
                    instructionsToRemoveItem(scanner, customerId);
                    break;
                case "3":
                    printWishList(customerId);
                    break;
                default:
                    System.out.println("Wrong option : "+opt);
            }
        }

        // cleanup
        client.close();
    }

    private static void printWishList(String customerId) throws AvroRemoteException, UnknownHostException {
        final List<Service> availableServices = registry.getAvailableServices(ServiceType.WISH_LIST);
        showWishList(availableServices, customerId);
    }

    private static void instructionsToRemoveItem(Scanner scanner, String customerId) throws IOException {
        System.out.println("Please enter the desired Item's id that you want to remove");
        final String itemId = scanner.next();
        final Item item= createItem(itemId);
        removeItem(customerId, item);
    }

    private static void instructionsToAddItem(Scanner scanner, String customerId) throws IOException {
        System.out.println("Please enter the desired Item's id that you want to add");
        final String itemId = scanner.next();
        final Item item= createItem(itemId);
        addItem(customerId, item);
    }

    private static Item createItem(String itemId) {
        return new Item(Long.valueOf(itemId), "Item nr "+itemId, "This is the item number "+itemId);
    }

    private static void printMenu() {
        System.out.println("\n\n--------------------------------------------------------\n\n");
        System.out.println("Please enter the number of the option chosen: ");
        System.out.println("1. Add item to your Wishlist");
        System.out.println("2. Remove an item from your Wishlist");
        System.out.println("3. Take a look of your wishlist");
        System.out.println("0. Exit");
        System.out.println("\n\n--------------------------------------------------------\n\n");
    }

    private static void showWishList(List<Service> services, String customerId) throws UnknownHostException {
        final Service service = getRandomService(services);

        try {
            final NettyTransceiver transceiver = new NettyTransceiver(new InetSocketAddress(service.getIp().toString(), service.getPort()));

            final WishListService wishList = SpecificRequestor.getClient(WishListService.class, transceiver);
            System.out.println("\n\n\n----------------------------------------------------------------");
            wishList.getWishList(customerId).getItems().forEach(System.out::println);
            System.out.println("----------------------------------------------------------------");
            transceiver.close(true);
        } catch (final IOException e){
            logger.map(l -> l.warning("Service unavailable", ServiceType.WISH_LIST, service.getIp().toString() + ":" + service.getPort()));
            services.remove(service);
            showWishList(services, customerId);
        }
    }

    private static void removeItem(String customerId, Item item) throws IOException {
        final List<Service> availableServices = registry.getAvailableServices(ServiceType.WISH_LIST);
        removing(availableServices, customerId, item);
    }

    private static void removing(List<Service> services, String customerId, Item item) throws UnknownHostException {
        if (services.isEmpty())
            noServiceAvailable();
        final Service service = getRandomService(services);
        try {
            final NettyTransceiver transceiver = new NettyTransceiver(new InetSocketAddress(service.getIp().toString(), service.getPort()));

            final WishListService wishList = SpecificRequestor.getClient(WishListService.class, transceiver);

            wishList.deleteItem(item, customerId, false);
            transceiver.close(true);
        } catch (final IOException e){
            logger.map(l -> l.warning("Service unavailable", ServiceType.WISH_LIST, service.getIp().toString() + ":" + service.getPort()));
            services.remove(service);
            removing(services, customerId, item);
        }
    }

    private static void noServiceAvailable() throws UnknownHostException {
        logger.map(l -> l.error("No service is available to perform the operation", ServiceType.WISH_LIST, "Client"));
        throw new RuntimeException("There are no Services available");
    }

    private static void addItem(String customerId, Item item) throws IOException {
        final List<Service> availableServices = registry.getAvailableServices(ServiceType.WISH_LIST);
        addition(availableServices, customerId, item);
    }

    private static void addition(List<Service> services, String customerId, Item item) throws UnknownHostException {
        if (services.isEmpty()) noServiceAvailable();
        final Service service = getRandomService(services);
        try {
            final NettyTransceiver transceiver = new NettyTransceiver(new InetSocketAddress(service.getIp().toString(), service.getPort()));

            final WishListService wishList = SpecificRequestor.getClient(WishListService.class, transceiver);

            wishList.addItem(item, customerId, false);
            transceiver.close(true);
        } catch (final IOException e){
            logger.map(l -> l.warning("Service unavailable", ServiceType.WISH_LIST, service.getIp().toString() + ":" + service.getPort()));
            services.remove(service);
            addition(services, customerId, item);
        }
    }
}