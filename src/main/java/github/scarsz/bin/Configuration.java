package github.scarsz.bin;

import java.util.concurrent.TimeUnit;

public class Configuration {

    private int port;
    private long defaultExpiration;
    private long maximumExpiration;

    public int getPort() {
        return port;
    }
    public Configuration setPort(int port) {
        this.port = port;
        return this;
    }

    public long getDefaultExpiration() {
        return defaultExpiration;
    }
    public Configuration setDefaultExpiration(TimeUnit timeUnit, long amount) {
        this.defaultExpiration = timeUnit.toMinutes(amount);
        return this;
    }

    public long getMaximumExpiration() {
        return maximumExpiration;
    }
    public Configuration setMaximumExpiration(TimeUnit timeUnit, long amount) {
        this.maximumExpiration = Math.abs(timeUnit.toMinutes(amount));
        return this;
    }

}
