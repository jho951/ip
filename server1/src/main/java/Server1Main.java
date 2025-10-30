public class Server1Main {
    public static void main(String[] args) throws Exception {
        System.setProperty("java.net.preferIPv4Stack", "true");
    }
//        int port1 = Integer.parseInt(System.getProperty("port",
//                args.length > 0 ? args[0] : "8080"));
//        int port2 = Integer.parseInt(System.getProperty("port",
//                args.length > 0 ? args[0] : "8081"));
//        String instanceId1 = System.getProperty("instance.id", "A");
//        String instanceId2 = System.getProperty("instance.id", "B");
//        HttpServer server1 = HttpServer.create(new InetSocketAddress(port1), 0);
//        HttpServer server2 = HttpServer.create(new InetSocketAddress(port2), 1);
//        server1.createContext("/", new IPHandler(instanceId1, port1));
//        server1.setExecutor(null);
//        server1.start();
//        server2.createContext("/", new IPHandler(instanceId2, port2));
//        server2.setExecutor(null);
//        server2.start();
//
//        System.out.println(System.getProperty("local.server.port","포트"));
//
//        System.out.printf("Started instance %s on http://localhost:%d (pid=%d)%n",
//                instanceId1, port1, ProcessHandle.current().pid());
//
//        System.out.printf("Started instance %s on http://localhost:%d (pid=%d)%n",
//                instanceId2, port2, ProcessHandle.current().pid());
//    }
}
