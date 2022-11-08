package com.microsocks;

import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.CommandLineParser;
import org.apache.commons.cli.DefaultParser;
import org.apache.commons.cli.HelpFormatter;
import org.apache.commons.cli.Options;
import org.apache.commons.cli.ParseException;

public class MicroSocks {
    public static final String TLS_SERVER = "tls-server";
    public static final String TLS_CLIENT = "tls-client";
    public static final String FORWARD = "forward";
    public static final String HELP = "help";

    // Convenience method so you can run it in your IDE
    public static void main(String[] args) {
        // create the command line parser
        CommandLineParser parser = new DefaultParser();

        // create the Options
        Options options = new Options();
        options.addOption("ts", TLS_SERVER, false, "Server to use TLS");
        options.addOption("tc", TLS_CLIENT, false, "Client to use TLS");
        options.addOption("f", FORWARD, true, "Forward requests to host:port, don't parse");
        options.addOption("?", HELP, false, "Client to use TLS");

        try {
            // parse the command line arguments
            CommandLine line = parser.parse(options, args);

            boolean help = line.hasOption(HELP);
            if (help) {
                HelpFormatter formatter = new HelpFormatter();
                formatter.printHelp("MicroSocks", options);
            } else {
                boolean tlsServer = line.hasOption(TLS_SERVER);
                boolean tlsClient = line.hasOption(TLS_CLIENT);
                String forward = line.getOptionValue(FORWARD);

                run(tlsServer, tlsClient, forward);
            }
        } catch (ParseException pe) {
            System.out.println("Argument parsing error: " + pe.getMessage());

            HelpFormatter formatter = new HelpFormatter();
            formatter.printHelp("MicroSocks", options);
        }
    }

    static void run(boolean tlsServer, boolean tlsClient, String forward) {
        Socks5Server.setTlsServer(tlsServer);
        SimplePumpClient.setTlsClient(tlsClient);
        Socks5Switch.setForward(forward);

        VertxRunner.runVerticle(Socks5Server.class);
    }
}
