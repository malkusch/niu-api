package de.malkusch.niu;

import static java.util.Arrays.stream;

import java.io.IOException;
import java.time.Duration;
import java.time.Instant;

public class Niu {

    private final Client client;
    private final Authentication authentication;

    public static final class Builder {
        private static final Duration DEFAULT_TIMEOUT = Duration.ofSeconds(10);

        private Duration timeout = DEFAULT_TIMEOUT;
        private Duration expirationWindow = timeout.multipliedBy(3);
        private final String account;
        private final String password;
        private final String countryCode;

        public Builder(String account, String password, String countryCode) {
            this.account = account;
            this.password = password;
            this.countryCode = countryCode;
        }

        public Niu build() throws IOException {
            var client = new Client(timeout);
            var authentication = new Authentication(account, password, countryCode, expirationWindow, client);
            return new Niu(client, authentication);
        }
    }

    private Niu(Client client, Authentication authentication) {
        this.client = client;
        this.authentication = authentication;
    }

    private static final String VEHICLES_URI = "https://app-api-fk.niu.com/motoinfo/list";

    public Vehicle[] vehicles() throws IOException {
        record Response(Data[] data) {
            record Data(String sn, String name) {
            }
        }
        var response = client.post(Response.class, VEHICLES_URI, authentication.token());
        return stream(response.data).map(it -> new Vehicle(it.sn, it.name)).toArray(Vehicle[]::new);
    }

    public record Vehicle(String serialNumber, String name) {
    }

    private static final String BATTERY_INFO_URI = "https://app-api-fk.niu.com/v3/motor_data/battery_info";

    public BatteryInfo batteryInfo(String serialNumber) throws IOException {
        var uri = BATTERY_INFO_URI + "?sn=" + serialNumber;
        record Response(Data data, int status) {
            record Data(Batteries batteries, boolean isCharging) {
                record Batteries(Battery compartmentA) {
                    record Battery(int batteryCharging, int temperature, double gradeBattery) {
                    }
                }
            }
        }
        var response = client.get(Response.class, uri, authentication.token());
        return new BatteryInfo(response.data.isCharging, response.data.batteries.compartmentA.batteryCharging,
                response.data.batteries.compartmentA.temperature, response.data.batteries.compartmentA.gradeBattery,
                response.status);
    }

    public record BatteryInfo(boolean isCharging, int charge, int temperature, double grade, int status) {
    }

    private static final String INFO_URI = "https://app-api-fk.niu.com/v3/motor_data/index_info";

    public VehicleInfo vehicle(String serialNumber) throws IOException {
        var uri = INFO_URI + "?sn=" + serialNumber;
        record Response(Data data, int status) {
            record Data(Batteries batteries, boolean isCharging, int nowSpeed, int shakingValue, Position postion,
                    int centreCtrlBattery, int gps, long gpsTimestamp, int gsm, long infoTimestamp, double leftTime,
                    boolean isConnected, int estimatedMileage, int ss_online_sta) {
                record Position(double lat, double lng) {
                }

                record Batteries(Battery compartmentA) {
                    record Battery(int batteryCharging, double gradeBattery) {
                    }
                }
            }
        }
        var response = client.get(Response.class, uri, authentication.token());
        return new VehicleInfo(
                new VehicleInfo.Battery(response.data.isCharging, response.data.batteries.compartmentA.batteryCharging,
                        response.data.batteries.compartmentA.gradeBattery),
                new VehicleInfo.Position(response.data.postion.lat, response.data.postion.lng), response.data.nowSpeed,
                response.data.shakingValue, response.data.centreCtrlBattery, response.data.gps,
                timestamp(response.data.gpsTimestamp), response.data.gsm, timestamp(response.data.infoTimestamp),
                response.status, response.data.leftTime, response.data.isConnected, response.data.estimatedMileage,
                response.data.ss_online_sta);
    }

    private static Instant timestamp(long timestamp) {
        return Instant.ofEpochMilli(timestamp);
    }

    public static record VehicleInfo(Battery battery, Position position, int nowSpeed, int shakingValue,
            int ecuBatteryCharge, int gps, Instant gpsTimestamp, int gsm, Instant gsmTimestamp, int status,
            double leftTime, boolean isConnected, int estimatedMileage, int ss_online_sta) {

        public record Battery(boolean isCharging, int charge, double grade) {
        }

        public record Position(double lat, double lng) {
        }
    }
}
