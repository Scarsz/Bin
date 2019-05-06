package github.scarsz.bin.main;

import github.scarsz.bin.Server;

import java.sql.SQLException;

public class Main {

    public static void main(String[] args) throws SQLException {
        new Server(args.length >= 1 ? Integer.parseInt(args[0]) : 6122);
    }

}
