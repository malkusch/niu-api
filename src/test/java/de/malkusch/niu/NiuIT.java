package de.malkusch.niu;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import java.io.IOException;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

public class NiuIT {

    private static final String ACCOUNT = System.getenv("ACCOUNT");
    private static final String PASSWORD = System.getenv("PASSWORD");
    private static final String COUNTRY_CODE = System.getenv("COUNTRY_CODE");
    private static final String SN = System.getenv("SN");

    private static Niu niu;

    @BeforeAll
    static void setupNiu() throws IOException {
        assumeTrue(ACCOUNT != null);
        assumeTrue(PASSWORD != null);
        assumeTrue(COUNTRY_CODE != null);
        assumeTrue(SN != null);
        niu = new Niu.Builder(ACCOUNT, PASSWORD, COUNTRY_CODE).build();
    }

    @Test
    public void shouldListVehicle() throws IOException {
        var sn = niu.vehicles()[0].serialNumber();

        assertEquals(SN, sn);
    }

    @Test
    public void testBatteryInfo() throws IOException {
        var info = niu.batteryInfo(SN);

        assertEquals(0, info.status());
    }

    @Test
    public void testOdometer() throws IOException {
        var odemeter = niu.odometer(SN);

        assertTrue(odemeter.mileage() > 0);
    }

    @Test
    public void testVehicle() throws IOException {
        var vehicle = niu.vehicle(SN);

        assertEquals(0, vehicle.status());
    }
}
