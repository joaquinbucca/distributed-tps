package wishlist;

import com.rabbitmq.client.*;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.StandardOpenOption;
import java.util.Arrays;
import java.util.concurrent.TimeoutException;

/**
 * Consume logs from a queue
 */
public class LogConsumer {
    public LogConsumer(final String exchange, boolean alertError, String[] bindingKeys) throws IOException, TimeoutException {
        ConnectionFactory factory = new ConnectionFactory();
        factory.setHost("localhost");
        Connection connection = factory.newConnection();
        Channel channel = connection.createChannel();

        channel.exchangeDeclare(exchange, "topic");
        String queueName = channel.queueDeclare().getQueue();

        for (final String bindingKey : bindingKeys) channel.queueBind(queueName, exchange, bindingKey);

        System.out.println(" [*] Waiting for messages. To exit press CTRL+C");

        final Consumer consumer = new DefaultConsumer(channel) {
            @Override public void handleDelivery(String consumerTag, Envelope envelope,
                                                 AMQP.BasicProperties properties, byte[] body) throws IOException {
                String message = new String(body, "UTF-8");
                if(alertError && envelope.getRoutingKey().contains("error")) error(message);
                System.out.println(" [x] Received '" + envelope.getRoutingKey() + "':'" + message + "'");
                saveToDisk(envelope.getRoutingKey(), message);
            }
        };
        channel.basicConsume(queueName, true, consumer);
    }

    @SuppressWarnings("ResultOfMethodCallIgnored")
    private void saveToDisk(String routingKey, String message) {
        File desktop = new File(System.getProperty("user.home"), "Desktop");

        try {
            final File routingFile = new File(desktop, routingKey.replaceAll("\\.", "_"));
            if(!routingFile.exists()) routingFile.createNewFile();
            Files.write(routingFile.toPath(), (message + "\n").getBytes(), StandardOpenOption.APPEND);
        } catch (IOException ignored) { }
    }

    private void error(final String message) {
        System.out.println("AAAAAAAAAAAAAAAALEEEEEEEEEERRRRTTTTT ERROOOOOORRRRRRR: " + message);
    }

    public static void main(String[] args) throws IOException, TimeoutException {
        if (args.length < 2) {
            System.err.println("Usage: LogConsumer [alert_error: Boolean] [binding_keys]...");
            System.exit(1);
        }

        new LogConsumer("wish_list_exchange", Boolean.valueOf(args[0]), Arrays.copyOfRange(args, 1, args.length));
    }
}
