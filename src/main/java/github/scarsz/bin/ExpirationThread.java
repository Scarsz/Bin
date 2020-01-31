package github.scarsz.bin;

import github.scarsz.bin.exception.BinNotFoundException;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.UUID;

public class ExpirationThread extends Thread {

    @Override
    public void run() {
        // clean up orphaned files
        try {
            Server.getConnection().setAutoCommit(false);

            ResultSet result = Server.getConnection().prepareStatement("select distinct `bin` from `files`").executeQuery();
            while (result.next()) {
                UUID uuid = (UUID) result.getObject("bin");

                try {
                    Bin.retrieve(uuid);
                } catch (BinNotFoundException e) {
                    PreparedStatement statement = Server.getConnection().prepareStatement("delete from `files` where `bin` = ?");
                    statement.setObject(1, uuid);
                    statement.executeUpdate();
                }
            }

            Server.getConnection().setAutoCommit(true);
        } catch (SQLException e) {
            e.printStackTrace();
        }

        while (!isInterrupted()) {
            try {
                PreparedStatement statement = Server.getConnection().prepareStatement(
                        "select id, hits from bins where " +
                                // Condition for expiring x minutes after last access:
                                // expiration > 0 && currentMs >= lastAccess + (expiration * 60000)
                                "(`expiration` > 0 and ? >= `lastAccess` + (expiration * 60000)) or " +
                                // Condition for expiring x minutes after creation:
                                // expiration < 0 && currentMs >= time + (-expiration * 60000)
                                "(`expiration` < 0 and ? >= `time` + (-expiration * 60000))"
                );
                statement.setLong(1, System.currentTimeMillis());
                statement.setLong(2, System.currentTimeMillis());
                ResultSet result = statement.executeQuery();
                if (result.isBeforeFirst()) {
                    Server.getConnection().setAutoCommit(false);
                    PreparedStatement delete = Server.getConnection().prepareStatement("delete from bins where id = ?");
                    while (result.next()) {
                        Server.log("Bin " + result.getObject("id") + " expired, had " + result.getInt("hits") + " hits");
                        delete.setObject(1, result.getObject(1));
                        delete.executeUpdate();
                    }
                    Server.getConnection().setAutoCommit(true);
                }
            } catch (SQLException e) {
                e.printStackTrace();
            }

            try {
                Thread.sleep(Server.getConfig().getInt("Expiration.Cycle") * 1000);
            } catch (InterruptedException e) {
                return;
            }
        }
    }

}
