package com.example.wi_map.ui.mapping;

import android.Manifest;
import android.annotation.SuppressLint;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.location.Location;
import android.net.wifi.ScanResult;
import android.net.wifi.WifiConfiguration;
import android.net.wifi.WifiManager;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.app.ActivityCompat;
import androidx.core.app.NotificationCompat;
import androidx.core.view.MenuProvider;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.Lifecycle;
import androidx.preference.PreferenceManager;
import androidx.recyclerview.widget.LinearLayoutManager;

import com.example.wi_map.R;
import com.example.wi_map.adapters.NetworkAdapter;
import com.example.wi_map.models.WifiEntry;
import com.example.wi_map.interfaces.INetworkClickListener;
import com.example.wi_map.databinding.FragmentMappingBinding;
import com.google.android.gms.location.FusedLocationProviderClient;
import com.google.android.gms.location.LocationServices;
import com.google.android.gms.maps.CameraUpdateFactory;
import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.OnMapReadyCallback;
import com.google.android.gms.maps.SupportMapFragment;
import com.google.android.gms.maps.model.BitmapDescriptorFactory;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.maps.model.Marker;
import com.google.android.gms.maps.model.MarkerOptions;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Objects;

public class MappingFragment extends Fragment
        implements OnMapReadyCallback, INetworkClickListener, MenuProvider,SharedPreferences.OnSharedPreferenceChangeListener {

    private static final String CHANNEL_ID = "wifi_alerts";

    private FragmentMappingBinding binding;
    private GoogleMap mMap;
    private NetworkAdapter adapter;
    private final List<WifiEntry> networks = new ArrayList<>();
    private WifiManager wifiManager;
    private FusedLocationProviderClient locationClient;
    private LatLng lastKnownLatLng = null;

    private boolean finePermissionGranted = false;

    private final HashMap<String, LatLng> fingerprintMap = new HashMap<>();
    private final HashMap<WifiEntry, Marker> markerMap = new HashMap<>();

    private int scanIntervalMs;
    private String distanceUnit;
    private boolean notifyThreshold;
    private int thresholdDbm;
    private boolean autoConnect;

    private final Handler scanHandler = new Handler(Looper.getMainLooper());
    private final Runnable scanRunnable = new Runnable() {
        @Override
        public void run() {
            doScan();
            scanHandler.postDelayed(this, scanIntervalMs);
        }
    };

    private final ActivityResultLauncher<String[]> requestPermissionLauncher =
            registerForActivityResult(
                    new ActivityResultContracts.RequestMultiplePermissions(),
                    results -> {
                        boolean fine = Boolean.TRUE.equals(
                                results.get(Manifest.permission.ACCESS_FINE_LOCATION));
                        boolean coarse = Boolean.TRUE.equals(
                                results.get(Manifest.permission.ACCESS_COARSE_LOCATION));

                        if (fine) {
                            finePermissionGranted = true;
                        }
                        if (coarse) {
                            setupMapAndScan();
                        }
                    }
            );

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        createNotificationChannel();
    }

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                             ViewGroup container, Bundle savedInstanceState) {
        binding = FragmentMappingBinding.inflate(inflater, container, false);
        View root = binding.getRoot();

        requireActivity().addMenuProvider(this,
                getViewLifecycleOwner(),
                Lifecycle.State.RESUMED);

        wifiManager = (WifiManager) requireContext()
                .getApplicationContext()
                .getSystemService(Context.WIFI_SERVICE);
        locationClient = LocationServices
                .getFusedLocationProviderClient(requireActivity());

        SupportMapFragment mapFrag = (SupportMapFragment)
                getChildFragmentManager().findFragmentById(R.id.map_fragment);
        if (mapFrag != null) {
            mapFrag.getMapAsync(this);
        }
        binding.rvNetworks.setLayoutManager(
                new LinearLayoutManager(requireContext()));
        adapter = new NetworkAdapter(networks, this);
        binding.rvNetworks.setAdapter(adapter);

        return root;
    }

    private void doScan() {

        if (ActivityCompat.checkSelfPermission(requireContext(),
                Manifest.permission.ACCESS_COARSE_LOCATION)
                != PackageManager.PERMISSION_GRANTED) {
            requestPermissionLauncher.launch(
                    new String[]{ Manifest.permission.ACCESS_COARSE_LOCATION }
            );
            return;
        }

        locationClient.getLastLocation().addOnSuccessListener(loc -> {
            if (loc == null) {
                return;
            }

            lastKnownLatLng = new LatLng(loc.getLatitude(), loc.getLongitude());

            wifiManager.startScan();
            List<ScanResult> results = wifiManager.getScanResults();
            results.sort(Comparator.comparingInt(r -> -r.level));

            networks.clear();
            markerMap.clear();
            for (int i = 0; i < Math.min(results.size(), 10); i++) {
                ScanResult sr = results.get(i);

                fingerprintMap.putIfAbsent(sr.BSSID, lastKnownLatLng);

                LatLng fpLoc = fingerprintMap.get(sr.BSSID);
                float distanceM = 0f;
                if (fpLoc != null) {
                    float[] dist = new float[1];
                    Location.distanceBetween(
                            lastKnownLatLng.latitude,
                            lastKnownLatLng.longitude,
                            fpLoc.latitude,
                            fpLoc.longitude,
                            dist
                    );
                    distanceM = dist[0];
                }
                if ("ft".equals(distanceUnit)) {
                    distanceM *= 3.28084f;
                }
                WifiEntry entry = new WifiEntry(
                        sr.SSID,
                        sr.level,
                        distanceM,
                        sr.BSSID
                );
                networks.add(entry);
                if (notifyThreshold && sr.level >= thresholdDbm) {
                    sendNotification("Strong network: " + sr.SSID,
                            "Signal " + sr.level + " dBm");
                }
                if (autoConnect) {
                    List<WifiConfiguration> configs = wifiManager.getConfiguredNetworks();
                    String quotedSsid = "\"" + sr.SSID + "\"";
                    for (WifiConfiguration cfg : configs) {
                        if (quotedSsid.equals(cfg.SSID)) {
                            wifiManager.enableNetwork(cfg.networkId, true);
                            break;
                        }
                    }
                }
            }
            adapter.notifyDataSetChanged();
            updateMapMarkers();
        });
    }


    private void updateMapMarkers() {
        if (mMap == null || lastKnownLatLng == null) return;

        mMap.clear();
        markerMap.clear();

        mMap.addMarker(new MarkerOptions()
                .position(lastKnownLatLng)
                .title("You")
                .icon(BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_AZURE)));


        for (WifiEntry entry : networks) {
            LatLng pos = fingerprintMap.get(entry.fingerprintLocation);
            if (pos == null) continue;

            Marker marker = mMap.addMarker(new MarkerOptions()
                    .position(pos)
                    .title(entry.ssid)
                    .snippet(entry.level + " dBm · " +
                            Math.round(entry.distanceM) + " " +
                            (distanceUnit.equals("ft") ? "ft" : "m")));
            markerMap.put(entry, marker);
        }
    }


    @Override
    public void onNetworkClick(WifiEntry entry) {
        Marker marker = markerMap.get(entry);
        if (marker != null) {
            LatLng pos = marker.getPosition();
            mMap.animateCamera(CameraUpdateFactory
                    .newLatLngZoom(pos, 18f));
            marker.showInfoWindow();
        }
    }

    @Override
    public void onMapReady(@NonNull GoogleMap googleMap) {
        mMap = googleMap;
        mMap.getUiSettings().setZoomControlsEnabled(true);

        boolean hasFine = ActivityCompat.checkSelfPermission(requireContext(),
                Manifest.permission.ACCESS_FINE_LOCATION)
                == PackageManager.PERMISSION_GRANTED;
        boolean hasCoarse = ActivityCompat.checkSelfPermission(requireContext(),
                Manifest.permission.ACCESS_COARSE_LOCATION)
                == PackageManager.PERMISSION_GRANTED;

        if (!hasFine || !hasCoarse) {
            requestPermissionLauncher.launch(new String[]{
                    Manifest.permission.ACCESS_FINE_LOCATION,
                    Manifest.permission.ACCESS_COARSE_LOCATION
            });
            return;
        }

        if (ActivityCompat.checkSelfPermission(requireContext(),
                Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED &&
                ActivityCompat.checkSelfPermission(requireContext(),
                        Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
            mMap.setMyLocationEnabled(true);
        }

        setupMapAndScan();
    }

    @SuppressLint("MissingPermission")
    private void setupMapAndScan() {
        if (finePermissionGranted) {
            mMap.setMyLocationEnabled(true);
        }

        locationClient.getLastLocation().addOnSuccessListener(loc -> {
            if (loc == null) return;

            lastKnownLatLng = new LatLng(loc.getLatitude(), loc.getLongitude());
            if (finePermissionGranted) {
                mMap.moveCamera(
                        CameraUpdateFactory.newLatLngZoom(lastKnownLatLng, 16f)
                );
            }
            doScan();
        });
    }




    @Override
    public void onCreateMenu(@NonNull Menu menu, @NonNull MenuInflater menuInflater) {
        menuInflater.inflate(R.menu.fragment_mapping_menu, menu);
    }

    @Override
    public boolean onMenuItemSelected(@NonNull MenuItem menuItem) {
        if (menuItem.getItemId() == R.id.action_refresh) {
            doScan();
            return true;
        }
        return false;
    }

    @Override
    public void onResume() {
        super.onResume();
        SharedPreferences prefs = PreferenceManager
                .getDefaultSharedPreferences(requireContext());

        prefs.registerOnSharedPreferenceChangeListener(this);
        updateSettingsFromPrefs(prefs);
        doScan();
        updateSettingsFromPrefs(prefs);
    }

    @Override
    public void onPause() {
        super.onPause();
        SharedPreferences prefs = PreferenceManager
                .getDefaultSharedPreferences(requireContext());
        prefs.unregisterOnSharedPreferenceChangeListener(this);

        scanHandler.removeCallbacks(scanRunnable);
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
    }

    private void createNotificationChannel() {
        CharSequence name = "Wi-Fi Alerts";
        String description = "Notifications for strong networks";
        int importance = NotificationManager.IMPORTANCE_DEFAULT;
        NotificationChannel channel = new NotificationChannel(
                CHANNEL_ID, name, importance);
        channel.setDescription(description);
        NotificationManager nm = requireContext()
                .getSystemService(NotificationManager.class);
        nm.createNotificationChannel(channel);
    }

    private void sendNotification(String title, String text) {
        NotificationCompat.Builder builder = new NotificationCompat.Builder(
                requireContext(), CHANNEL_ID)
                .setSmallIcon(R.drawable.wifi)
                .setContentTitle(title)
                .setContentText(text)
                .setAutoCancel(true);
        NotificationManager nm = (NotificationManager)
                requireContext().getSystemService(Context.NOTIFICATION_SERVICE);
        nm.notify((int) System.currentTimeMillis(), builder.build());
    }

    @Override
    public void onSharedPreferenceChanged(SharedPreferences prefs, String key) {
        switch(Objects.requireNonNull(key)) {
            case "pref_notify_threshold":
            case "pref_notify_level":
                notifyThreshold = prefs.getBoolean("pref_notify_threshold", false);
                thresholdDbm  = Integer.parseInt(
                        prefs.getString("pref_notify_level", "-60"));
                doScan();
                Toast.makeText(requireContext(),
                        "Notify threshold set to " + thresholdDbm + " dBm",
                        Toast.LENGTH_SHORT).show();
                break;
            case "pref_auto_scan":
            case "pref_scan_interval":
            case "pref_distance_unit":
            case "pref_auto_connect":
                updateSettingsFromPrefs(prefs);
                break;
        }
    }

    private void updateSettingsFromPrefs(SharedPreferences prefs) {
        boolean autoScan = prefs.getBoolean("pref_auto_scan", false);
        scanIntervalMs = Integer.parseInt(prefs.getString("pref_scan_interval", "5000"));
        distanceUnit = prefs.getString("pref_distance_unit", "m");
        notifyThreshold = prefs.getBoolean("pref_notify_threshold", false);
        thresholdDbm = Integer.parseInt(prefs.getString("pref_notify_level", "-60"));
        autoConnect = prefs.getBoolean("pref_auto_connect", false);

        scanHandler.removeCallbacks(scanRunnable);
        if (autoScan && scanIntervalMs > 0) {
            scanHandler.postDelayed(scanRunnable, scanIntervalMs);
        }
    }

}
