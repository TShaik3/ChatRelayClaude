package client;

public class ClientMain {
    public static void main(String[] args) throws Exception {
        String ip = args.length > 0 ? args[0] : "127.0.0.1";
        String port = args.length > 1 ? args[1] : "5000";
        Client client = new Client(ip, port);
        client.startUp();
    }
}
