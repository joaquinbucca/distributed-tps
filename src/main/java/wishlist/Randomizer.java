package wishlist;

import tp1.registry.Service;
import tp1.wishList.Item;

import java.util.List;
import java.util.Random;

import static java.lang.String.valueOf;
import static tp1.wishList.Item.newBuilder;

/**
 * Randomize descriptions, ids, etc
 */
public class Randomizer {
    public static final int MAX_ITEMS = 7;
    public static final int MAX_CUSTOMERS = 7;

    private Randomizer(){}

    public static String randomCustomerId(){ return valueOf(randomId(MAX_CUSTOMERS)); }

    public static Item randomItem() {
        return newBuilder().setId(randomId(MAX_ITEMS)).setName("Some Name").setDescription("Some Description").build();
    }

    private static long randomId(int max) { return new Random().nextInt(max); }

    public static Service getRandomService(List<Service> services) {
        final int bound = services.size();
        return bound == 0 ? services.get(bound) : services.get(new Random().nextInt(bound));
    }
}