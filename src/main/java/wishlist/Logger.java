package wishlist;

import com.rabbitmq.client.Channel;
import com.rabbitmq.client.Connection;
import com.rabbitmq.client.ConnectionFactory;
import tp1.registry.ServiceType;

import java.io.IOException;
import java.util.Date;
import java.util.Optional;
import java.util.concurrent.TimeoutException;

/**
 * Create logs of events and send them to log queue
 */
public class Logger {

    private final Channel channel;

    public Logger() throws IOException, TimeoutException {
        ConnectionFactory factory = new ConnectionFactory();
        factory.setHost("localhost");
        Connection connection = factory.newConnection();

        channel = connection.createChannel();

        channel.exchangeDeclare("wish_list_exchange", "topic");
    }

    public String error(String message, ServiceType serviceType, String address){
        return log(message, serviceType, address, LogType.ERROR, new Date());
    }

    public String info(String message, ServiceType serviceType, String address){
        return log(message, serviceType, address, LogType.INFO, new Date());
    }

    public String warning(String message, ServiceType serviceType, String address){
        return log(message, serviceType, address, LogType.WARNING, new Date());
    }

    private String log(String message, ServiceType serviceType, String address, LogType logType, Date date) {
        try {
            final String aux = date.toString() + " [" + logType.name() + "] from " + address + ": " + message;
            channel.basicPublish("wish_list_exchange", (serviceType.name() + "." + logType.name()).toLowerCase(), null, aux.getBytes("UTF-8"));
            System.out.println(" [x] Sent '" + aux + "'");
        } catch (final IOException e) { e.printStackTrace(); }
        return message;
    }

    public static Optional<Logger> logger(){
        try {
            return Optional.of(new Logger());
        } catch (IOException | TimeoutException e) { return Optional.empty(); }
    }

    enum LogType { ERROR, WARNING, INFO }
}
