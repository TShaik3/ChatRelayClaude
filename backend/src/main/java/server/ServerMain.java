package server;

import server.support.DataSources;

public class ServerMain {
    public static void main(String[] args) throws Exception {
        int port = args.length > 0 ? Integer.parseInt(args[0]) : 5000;
        String ip = args.length > 1 ? args[1] : "127.0.0.1";
        Server server = new Server(port, ip, DataSources.devDataSource());
        server.connect();
    }
}
