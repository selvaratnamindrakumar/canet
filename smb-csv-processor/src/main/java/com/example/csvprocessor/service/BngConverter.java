package com.example.csvprocessor.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * Converts British National Grid (BNG / OSGB36) Easting + Northing coordinates
 * to WGS84 latitude and longitude.
 *
 * <p><b>Algorithm</b>:
 * <ol>
 *   <li>Apply the inverse Transverse Mercator projection (Airy 1830 ellipsoid) to
 *       obtain OSGB36 geodetic latitude and longitude.</li>
 *   <li>Convert OSGB36 Cartesian XYZ from the Airy 1830 ellipsoid.</li>
 *   <li>Apply the 7-parameter Helmert transformation (Ordnance Survey parameters)
 *       to WGS84 Cartesian XYZ.</li>
 *   <li>Convert WGS84 Cartesian XYZ back to geodetic latitude and longitude.</li>
 * </ol>
 *
 * <p><b>Reference</b>: Ordnance Survey "A Guide to coordinate systems in Great
 * Britain" (document D00659), Appendix C.
 *
 * <p>Accuracy: typically better than 1 metre for mainland Great Britain.
 */
@Service
public class BngConverter {

    private static final Logger log = LoggerFactory.getLogger(BngConverter.class);

    // ---- Airy 1830 ellipsoid (OSGB36) ----------------------------------------
    private static final double AIRY_A  = 6_377_563.396;   // semi-major axis (m)
    private static final double AIRY_B  = 6_356_256.909;   // semi-minor axis (m)

    // ---- GRS80 / WGS84 ellipsoid ----------------------------------------------
    private static final double WGS84_A = 6_378_137.000;
    private static final double WGS84_B = 6_356_752.3141;

    // ---- National Grid projection parameters ----------------------------------
    private static final double K0     = 0.9996012717;      // central meridian scale
    private static final double E0     = 400_000.0;          // false easting  (m)
    private static final double N0     = -100_000.0;         // false northing (m)
    private static final double LAT0   = Math.toRadians(49); // true origin lat
    private static final double LON0   = Math.toRadians(-2); // true origin lon (2°W)

    // ---- Helmert OSGB36 → WGS84 (OS "best estimate" parameters) ---------------
    // Translation (metres)
    private static final double TX =  446.448;
    private static final double TY = -125.157;
    private static final double TZ =  542.060;
    // Rotation (radians) — converted from arc-seconds
    private static final double RX = Math.toRadians(-0.1502  / 3600.0);
    private static final double RY = Math.toRadians(-0.2470  / 3600.0);
    private static final double RZ = Math.toRadians(-0.8421  / 3600.0);
    // Scale factor (dimensionless) — converted from ppm
    private static final double S  = 1.0 + (-20.4894 * 1e-6);

    // ---- Result holder --------------------------------------------------------

    public static final class LatLon {
        public final double latitude;
        public final double longitude;
        public LatLon(double latitude, double longitude) {
            this.latitude  = latitude;
            this.longitude = longitude;
        }
        @Override public String toString() {
            return latitude + "," + longitude;
        }
    }

    // ---- Public API -----------------------------------------------------------

    /**
     * Converts OSGB36 BNG easting/northing to WGS84 latitude/longitude.
     *
     * @param easting  BNG easting in metres
     * @param northing BNG northing in metres
     * @return WGS84 {@link LatLon}, or {@code null} if the input is out of range
     */
    public LatLon convert(double easting, double northing) {
        if (easting < 0 || easting > 800_000 || northing < -100_000 || northing > 1_400_000) {
            log.warn("BNG coordinates out of expected range: E={} N={}", easting, northing);
            return null;
        }
        double[] osgb36LatLon = bngToOsgb36(easting, northing);
        double[] xyz          = geodeticToCartesian(osgb36LatLon[0], osgb36LatLon[1], 0,
                                                     AIRY_A, AIRY_B);
        double[] wgsXyz       = helmertOsgb36ToWgs84(xyz);
        double[] wgs84        = cartesianToGeodetic(wgsXyz, WGS84_A, WGS84_B);
        return new LatLon(Math.toDegrees(wgs84[0]), Math.toDegrees(wgs84[1]));
    }

    // ---- Step 1: inverse Transverse Mercator (BNG → OSGB36 lat/lon) -----------

    private double[] bngToOsgb36(double E, double N) {
        double a  = AIRY_A;
        double b  = AIRY_B;
        double e2 = 1.0 - (b * b) / (a * a);
        double n  = (a - b) / (a + b);

        double lat = LAT0 + (N - N0) / (a * K0);

        // Iterative convergence to find latitude
        for (int i = 0; i < 100; i++) {
            double m  = meridianArc(lat, LAT0, a, b, n);
            double newLat = lat + (N - N0 - K0 * m) / (a * K0);
            if (Math.abs(newLat - lat) < 1e-12) { lat = newLat; break; }
            lat = newLat;
        }

        double sinLat = Math.sin(lat);
        double cosLat = Math.cos(lat);
        double tanLat = Math.tan(lat);

        double nu   = a * K0 / Math.sqrt(1.0 - e2 * sinLat * sinLat);
        double rho  = a * K0 * (1.0 - e2) / Math.pow(1.0 - e2 * sinLat * sinLat, 1.5);
        double eta2 = nu / rho - 1.0;

        double VII  = tanLat / (2.0 * rho * nu);
        double VIII = tanLat / (24.0 * rho * Math.pow(nu, 3))
                      * (5.0 + 3.0 * tanLat * tanLat + eta2 - 9.0 * tanLat * tanLat * eta2);
        double IX   = tanLat / (720.0 * rho * Math.pow(nu, 5))
                      * (61.0 + 90.0 * tanLat * tanLat + 45.0 * Math.pow(tanLat, 4));
        double X    = 1.0 / (cosLat * nu);
        double XI   = 1.0 / (6.0 * cosLat * Math.pow(nu, 3))
                      * (nu / rho + 2.0 * tanLat * tanLat);
        double XII  = 1.0 / (120.0 * cosLat * Math.pow(nu, 5))
                      * (5.0 + 28.0 * tanLat * tanLat + 24.0 * Math.pow(tanLat, 4));
        double XIIA = 1.0 / (5040.0 * cosLat * Math.pow(nu, 7))
                      * (61.0 + 662.0 * tanLat * tanLat + 1320.0 * Math.pow(tanLat, 4)
                         + 720.0 * Math.pow(tanLat, 6));

        double dE = E - E0;
        double latResult = lat
                - VII  * (dE * dE)
                + VIII * Math.pow(dE, 4)
                - IX   * Math.pow(dE, 6);
        double lonResult = LON0
                + X    * dE
                - XI   * Math.pow(dE, 3)
                + XII  * Math.pow(dE, 5)
                - XIIA * Math.pow(dE, 7);

        return new double[]{ latResult, lonResult };
    }

    private double meridianArc(double lat, double lat0, double a, double b, double n) {
        double n2 = n * n, n3 = n * n * n;
        return b * K0 * (
            (1.0 + n + 5.0/4.0 * n2 + 5.0/4.0 * n3) * (lat - lat0)
            - (3*n + 3*n2 + 21.0/8.0 * n3) * Math.sin(lat - lat0) * Math.cos(lat + lat0)
            + (15.0/8.0 * n2 + 15.0/8.0 * n3) * Math.sin(2*(lat - lat0)) * Math.cos(2*(lat + lat0))
            - (35.0/24.0 * n3) * Math.sin(3*(lat - lat0)) * Math.cos(3*(lat + lat0))
        );
    }

    // ---- Step 2: geodetic (lat/lon/h) → Cartesian XYZ ------------------------

    private double[] geodeticToCartesian(double lat, double lon, double h, double a, double b) {
        double e2  = 1.0 - (b * b) / (a * a);
        double nu  = a / Math.sqrt(1.0 - e2 * Math.sin(lat) * Math.sin(lat));
        double x   = (nu + h) * Math.cos(lat) * Math.cos(lon);
        double y   = (nu + h) * Math.cos(lat) * Math.sin(lon);
        double z   = (nu * (1.0 - e2) + h) * Math.sin(lat);
        return new double[]{ x, y, z };
    }

    // ---- Step 3: Helmert OSGB36 → WGS84 cartesian ----------------------------

    private double[] helmertOsgb36ToWgs84(double[] xyz) {
        double x = xyz[0], y = xyz[1], z = xyz[2];
        return new double[]{
            TX + S * (x      - RZ * y + RY * z),
            TY + S * (RZ * x + y      - RX * z),
            TZ + S * (-RY * x + RX * y + z)
        };
    }

    // ---- Step 4: Cartesian XYZ → geodetic (Bowring iterative) ----------------

    private double[] cartesianToGeodetic(double[] xyz, double a, double b) {
        double x = xyz[0], y = xyz[1], z = xyz[2];
        double e2  = 1.0 - (b * b) / (a * a);
        double ep2 = (a * a - b * b) / (b * b);
        double p   = Math.sqrt(x * x + y * y);
        double lon = Math.atan2(y, x);

        // Initial approximation
        double theta = Math.atan2(z * a, p * b);
        double lat   = Math.atan2(
                z + ep2 * b * Math.pow(Math.sin(theta), 3),
                p - e2 * a * Math.pow(Math.cos(theta), 3));

        // Iterate
        for (int i = 0; i < 10; i++) {
            double nu    = a / Math.sqrt(1.0 - e2 * Math.sin(lat) * Math.sin(lat));
            double newLat = Math.atan2(z + e2 * nu * Math.sin(lat), p);
            if (Math.abs(newLat - lat) < 1e-12) { lat = newLat; break; }
            lat = newLat;
        }

        return new double[]{ lat, lon };
    }
}
