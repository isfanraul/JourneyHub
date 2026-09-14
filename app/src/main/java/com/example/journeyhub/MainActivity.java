package com.example.journeyhub;

import static android.content.ContentValues.TAG;
import static com.example.journeyhub.BuildConfig.MAPS_API_KEY;

import android.Manifest;
import android.annotation.SuppressLint;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.location.Address;
import android.location.Geocoder;
import android.location.Location;
import android.os.Bundle;
import android.text.SpannableString;
import android.text.SpannableStringBuilder;
import android.text.method.ScrollingMovementMethod;
import android.text.style.ForegroundColorSpan;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.RelativeLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.widget.SearchView;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.FragmentActivity;

import com.google.android.gms.location.FusedLocationProviderClient;
import com.google.android.gms.location.LocationServices;
import com.google.android.gms.maps.CameraUpdateFactory;
import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.OnMapReadyCallback;
import com.google.android.gms.maps.SupportMapFragment;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.maps.model.MarkerOptions;
import com.google.android.gms.maps.model.PolylineOptions;
import com.google.maps.DirectionsApi;
import com.google.maps.GeoApiContext;
import com.google.maps.model.DirectionsLeg;
import com.google.maps.model.DirectionsResult;
import com.google.maps.model.DirectionsRoute;
import com.google.maps.model.DirectionsStep;
import com.google.maps.model.EncodedPolyline;
import com.google.maps.model.TransitDetails;
import com.google.maps.model.TravelMode;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class MainActivity extends FragmentActivity implements OnMapReadyCallback, GoogleMap.OnMapClickListener {

    private static final int LOCATION_PERMISSION_REQUEST = 2;
    private static final int[] ROUTE_COLORS = {Color.RED, Color.GREEN, Color.CYAN, Color.DKGRAY, Color.YELLOW, Color.MAGENTA, Color.GRAY};

    private GoogleMap map;
    private View mapView;
    private TextView instructionsView;
    private SearchView searchView;
    private FusedLocationProviderClient fusedLocationClient;
    private final ExecutorService backgroundExecutor = Executors.newSingleThreadExecutor();

    private String origin;
    private String destination;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        instructionsView = findViewById(R.id.simpleTextView);
        instructionsView.setMovementMethod(new ScrollingMovementMethod());
        searchView = findViewById(R.id.idSearchView);
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this);

        searchView.setOnQueryTextListener(new SearchView.OnQueryTextListener() {
            @Override
            public boolean onQueryTextSubmit(String query) {
                searchForDestination(query == null ? "" : query.trim());
                return true;
            }

            @Override
            public boolean onQueryTextChange(String newText) {
                return false;
            }
        });

        Button directionsButton = findViewById(R.id.btnGetDirections);
        directionsButton.setOnClickListener(view -> requestDirections());

        SupportMapFragment mapFragment = (SupportMapFragment) getSupportFragmentManager()
                .findFragmentById(R.id.map);
        if (mapFragment != null) {
            mapView = mapFragment.getView();
            mapFragment.getMapAsync(this);
        }
    }

    private void searchForDestination(String query) {
        if (query.isEmpty()) {
            searchView.setError(getString(R.string.search_required));
            return;
        }
        if (map == null) {
            showMessage(getString(R.string.map_not_ready));
            return;
        }

        backgroundExecutor.execute(() -> {
            try {
                List<Address> addresses = new Geocoder(this).getFromLocationName(query, 1);
                if (addresses == null || addresses.isEmpty()) {
                    showMessage(getString(R.string.location_not_found));
                    return;
                }
                Address address = addresses.get(0);
                LatLng location = new LatLng(address.getLatitude(), address.getLongitude());
                runOnUiThread(() -> selectDestination(location, query));
            } catch (IOException | RuntimeException exception) {
                Log.e(TAG, "Unable to search for destination", exception);
                showMessage(getString(R.string.location_search_failed));
            }
        });
    }

    private void selectDestination(LatLng location, String title) {
        destination = formatCoordinate(location);
        map.clear();
        map.addMarker(new MarkerOptions().position(location).title(title));
        map.animateCamera(CameraUpdateFactory.newLatLngZoom(location, 15));
    }

    private void requestDirections() {
        if (origin == null) {
            showMessage(getString(R.string.current_location_required));
            return;
        }
        if (destination == null) {
            showMessage(getString(R.string.destination_required));
            return;
        }

        instructionsView.setText(R.string.loading_directions);
        backgroundExecutor.execute(() -> {
            try {
                DirectionsResult result;
                GeoApiContext context = new GeoApiContext.Builder().apiKey(MAPS_API_KEY).build();
                try {
                    result = DirectionsApi.newRequest(context)
                            .origin(origin)
                            .destination(destination)
                            .mode(TravelMode.TRANSIT)
                            .await();
                } finally {
                    context.shutdown();
                }
                renderDirections(result);
            } catch (Exception exception) {
                Log.e(TAG, "Unable to load directions", exception);
                showMessage(getString(R.string.directions_failed));
            }
        });
    }

    private void renderDirections(DirectionsResult result) {
        if (result == null || result.routes == null || result.routes.length == 0) {
            showMessage(getString(R.string.no_route_found));
            return;
        }

        DirectionsRoute route = result.routes[0];
        SpannableStringBuilder instructions = new SpannableStringBuilder(
                getString(R.string.route_instructions) + "\n");
        List<PolylineOptions> polylines = new ArrayList<>();
        int routeColor = 0;
        int instructionColor = 0;

        if (route.legs != null) {
            for (DirectionsLeg leg : route.legs) {
                if (leg.steps == null) {
                    continue;
                }
                for (DirectionsStep step : leg.steps) {
                    appendInstruction(instructions, step, instructionColor);
                    if (step.transitDetails != null) {
                        instructionColor = (instructionColor + 1) % ROUTE_COLORS.length;
                    }
                    List<DirectionsStep> steps = step.steps != null && step.steps.length > 0
                            ? java.util.Arrays.asList(step.steps) : java.util.Collections.singletonList(step);
                    for (DirectionsStep segment : steps) {
                        PolylineOptions polyline = createPolyline(segment, routeColor);
                        if (polyline != null) {
                            polylines.add(polyline);
                            routeColor = (routeColor + 1) % ROUTE_COLORS.length;
                        }
                    }
                }
            }
        }

        runOnUiThread(() -> {
            map.clear();
            for (PolylineOptions polyline : polylines) {
                map.addPolyline(polyline);
            }
            instructionsView.setText(instructions, TextView.BufferType.SPANNABLE);
        });
    }

    private void appendInstruction(SpannableStringBuilder builder, DirectionsStep step, int colorIndex) {
        if (step.travelMode != null && step.travelMode.name().equals("WALKING") && step.distance != null && step.duration != null) {
            String walking = getString(R.string.walk_instruction, step.distance.humanReadable, step.duration.humanReadable);
            builder.append(coloredText(walking, Color.BLUE));
        }
        TransitDetails transit = step.transitDetails;
        if (transit != null && transit.arrivalTime != null) {
            String line = getString(R.string.transit_instruction, transit.line, transit.arrivalTime.getHour(),
                    transit.arrivalTime.getMinute(), transit.numStops);
            builder.append(coloredText(line, ROUTE_COLORS[colorIndex % ROUTE_COLORS.length]));
        }
    }

    private SpannableString coloredText(String value, int color) {
        SpannableString text = new SpannableString(value);
        text.setSpan(new ForegroundColorSpan(color), 0, value.length(), 0);
        return text;
    }

    private PolylineOptions createPolyline(DirectionsStep step, int colorIndex) {
        EncodedPolyline encodedPolyline = step.polyline;
        if (encodedPolyline == null) {
            return null;
        }
        List<LatLng> path = new ArrayList<>();
        for (com.google.maps.model.LatLng point : encodedPolyline.decodePath()) {
            path.add(new LatLng(point.lat, point.lng));
        }
        int color = step.travelMode != null && step.travelMode.name().equals("WALKING")
                ? Color.BLUE : ROUTE_COLORS[colorIndex % ROUTE_COLORS.length];
        return new PolylineOptions().addAll(path).color(color).width(5);
    }

    @SuppressLint("MissingPermission")
    @Override
    public void onMapReady(@NonNull GoogleMap googleMap) {
        map = googleMap;
        map.setOnMapClickListener(this);
        LatLng timisoara = new LatLng(45.760696, 21.226788);
        map.moveCamera(CameraUpdateFactory.newLatLngZoom(timisoara, 15));
        positionLocationButton();
        requestLocationPermissionIfNeeded();
    }

    private void requestLocationPermissionIfNeeded() {
        boolean fineGranted = ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
            == PackageManager.PERMISSION_GRANTED;
        boolean coarseGranted = ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION)
            == PackageManager.PERMISSION_GRANTED;
        if (!fineGranted && !coarseGranted) {
            ActivityCompat.requestPermissions(this,
                    new String[]{Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION},
                    LOCATION_PERMISSION_REQUEST);
            return;
        }
        enableLocationAndLoadOrigin();
    }

    @SuppressLint("MissingPermission")
    private void enableLocationAndLoadOrigin() {
        if (map != null) {
            map.setMyLocationEnabled(true);
        }
        fusedLocationClient.getLastLocation().addOnSuccessListener(this, location -> {
            if (location != null) {
                origin = formatCoordinate(location.getLatitude(), location.getLongitude());
            }
        });
    }

    private void positionLocationButton() {
        if (mapView == null || mapView.findViewById(1) == null) {
            return;
        }
        View locationButton = ((View) mapView.findViewById(1).getParent()).findViewById(2);
        if (locationButton != null && locationButton.getLayoutParams() instanceof RelativeLayout.LayoutParams) {
            RelativeLayout.LayoutParams layoutParams = (RelativeLayout.LayoutParams) locationButton.getLayoutParams();
            layoutParams.addRule(RelativeLayout.ALIGN_PARENT_TOP, 0);
            layoutParams.addRule(RelativeLayout.ALIGN_PARENT_BOTTOM, RelativeLayout.TRUE);
            layoutParams.setMargins(0, 0, 30, 30);
            locationButton.setLayoutParams(layoutParams);
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        boolean locationGranted = false;
        for (int result : grantResults) {
            if (result == PackageManager.PERMISSION_GRANTED) {
                locationGranted = true;
                break;
            }
        }
        if (requestCode == LOCATION_PERMISSION_REQUEST && locationGranted) {
            enableLocationAndLoadOrigin();
        } else {
            showMessage(getString(R.string.location_permission_required));
        }
    }

    @Override
    public void onMapClick(@NonNull LatLng latLng) {
        selectDestination(latLng, getString(R.string.selected_destination));
    }

    private String formatCoordinate(double latitude, double longitude) {
        return String.format(Locale.US, "%f,%f", latitude, longitude);
    }

    private String formatCoordinate(Location location) {
        return formatCoordinate(location.getLatitude(), location.getLongitude());
    }

    private void showMessage(String message) {
        runOnUiThread(() -> Toast.makeText(this, message, Toast.LENGTH_SHORT).show());
    }

    @Override
    protected void onDestroy() {
        backgroundExecutor.shutdownNow();
        super.onDestroy();
    }
}
