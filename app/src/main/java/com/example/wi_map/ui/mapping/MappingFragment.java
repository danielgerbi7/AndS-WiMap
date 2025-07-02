package com.example.wi_map.ui.mapping;

import android.Manifest;
import android.annotation.SuppressLint;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.location.Location;
import android.net.ConnectivityManager;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.net.NetworkRequest;
import android.net.wifi.ScanResult;
import android.net.wifi.WifiConfiguration;
import android.net.wifi.WifiManager;
import android.net.wifi.WifiNetworkSpecifier;
import android.os.Build;
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
import com.example.wi_map.data.MappingStorage;
import com.example.wi_map.models.HistoryEntry;
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
        implements OnMapReadyCallback,
                   INetworkClickListener,
                   MenuProvider,
                   SharedPreferences.OnSharedPreferenceChangeListener {

    private static final String CHANNEL_ID = "wifi_alerts";

    private FragmentMappingBinding binding;
    private GoogleMap mMap;
    private NetworkAdapter adapter;
    private final List<WifiEntry> networks = new ArrayList<>();
    private List<ScanResult> lastScanResults = new ArrayList<>();
    private WifiManager wifiManager;
    private FusedLocationProviderClient locationClient;
    private LatLng lastKnownLatLng = null;
    private MappingStorage storage;
    private boolean finePermissionGranted = false;
    private final HashMap<String, LatLng> fingerprintMap = new HashMap<>();
    private final HashMap<WifiEntry, Marker> markerMap = new HashMap<>();

    private int scanIntervalMs;
    private String distanceUnit;
    private boolean notifyThreshold;
    private int thresholdDbm;
    private boolean autoConnect;

    private final Handler scanHandler = new Handler(Looper.getMainLooper());
    private final Runnable scanRunnable = this::doScan;

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
        storage = new MappingStorage(requireContext());
    }

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                             ViewGroup container, Bundle savedInstanceState) {
        binding = FragmentMappingBinding.inflate(inflater, container, false);
        View root = binding.getRoot();

        SharedPreferences prefs = PreferenceManager
                .getDefaultSharedPreferences(requireContext());
        prefs.registerOnSharedPreferenceChangeListener(this);
        updateSettingsFromPrefs(prefs);

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
        adapter = new NetworkAdapter(networks, this, distanceUnit);
        binding.rvNetworks.setAdapter(adapter);

        return root;
    }

    @SuppressLint("NotifyDataSetChanged")
    private void doScan() {
        if (!checkCoarsePermission())
            return;
        locationClient.getLastLocation().addOnSuccessListener(loc -> {
            if (loc == null)
                return;
            lastKnownLatLng = new LatLng(loc.getLatitude(), loc.getLongitude());
            List<ScanResult> results = startWifiScan();
            lastScanResults = results;
            networks.clear();
            markerMap.clear();
            for (ScanResult sr : results) {
                WifiEntry entry = buildEntry(sr);
                networks.add(entry);
                notifyIfNeeded(sr);
            }
            adapter.notifyDataSetChanged();
            updateMapMarkers();

            if (autoConnect && !results.isEmpty()) {
                ScanResult strongest = results.get(0);
                boolean isOpen = !strongest.capabilities.contains("WEP")
                        && !strongest.capabilities.contains("WPA");

                if (isOpen) {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                        connectToOpenNetworkQ(strongest);
                    } else {
                        connectToOpenNetworkLegacy(strongest);
                    }
                } else {
                    Toast.makeText(requireContext(),
                            "This network is secured — can’t connect automatically.",
                            Toast.LENGTH_SHORT).show();
                }
            }

            if (scanIntervalMs>0)
                scanHandler.postDelayed(scanRunnable, scanIntervalMs);
        });
    }

    private boolean checkCoarsePermission() {
        if (ActivityCompat.checkSelfPermission(requireContext(),
                Manifest.permission.ACCESS_COARSE_LOCATION)
                != PackageManager.PERMISSION_GRANTED) {
            requestPermissionLauncher.launch(
                    new String[]{Manifest.permission.ACCESS_COARSE_LOCATION});
            return false;
        }
        return true;
    }

    private List<ScanResult> startWifiScan() {
        wifiManager.startScan();
        @SuppressLint("MissingPermission") List<ScanResult> results = wifiManager.getScanResults();
        results.sort(Comparator.comparingInt(r -> -r.level));
        return results.subList(0, Math.min(results.size(), 10));
    }

    private WifiEntry buildEntry(ScanResult sr) {
        fingerprintMap.putIfAbsent(sr.BSSID, lastKnownLatLng);
        float dist = calculateDistance(sr);
        if ("ft".equals(distanceUnit)) dist *= 3.28084f;

        return new WifiEntry(sr.SSID, sr.level, dist, sr.BSSID);
    }

    private float calculateDistance(ScanResult sr) {
        LatLng fp = fingerprintMap.get(sr.BSSID);
        if (fp==null || lastKnownLatLng==null)
            return 0f;
        float[] d = new float[1];
        Location.distanceBetween(
                lastKnownLatLng.latitude,
                lastKnownLatLng.longitude,
                fp.latitude,
                fp.longitude,
                d
        );
        return d[0];
    }

    private void notifyIfNeeded(ScanResult sr) {
        if (!notifyThreshold || sr.level < thresholdDbm) return;
        sendNotification("Strong network: " + sr.SSID,
                "Signal " + sr.level + " dBm");
        Toast.makeText(requireContext(),
                        "Threshold passed on " + sr.SSID +
                                " (" + sr.level + " dBm)",
                        Toast.LENGTH_SHORT)
                .show();
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
        LatLng pos = fingerprintMap.get(entry.fingerprintLocation);
        if (pos != null) {
            mMap.animateCamera(CameraUpdateFactory.newLatLngZoom(pos, 18f));
        }

        ScanResult sr = null;
        for (ScanResult candidate : lastScanResults) {
            if (candidate.BSSID.equals(entry.fingerprintLocation)) {
                sr = candidate;
                break;
            }
        }
        if (sr == null) {
            Toast.makeText(requireContext(),
                            "Network not found in last scan", Toast.LENGTH_SHORT)
                    .show();
            return;
        }
        boolean isOpen = !sr.capabilities.contains("WEP")
                && !sr.capabilities.contains("WPA");
        if (!isOpen) {
            Toast.makeText(requireContext(),
                    "This network is secured — can’t connect automatically.",
                    Toast.LENGTH_SHORT).show();
            return;
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            connectToOpenNetworkQ(sr);
        } else {
            connectToOpenNetworkLegacy(sr);
        }
    }

    @SuppressWarnings("deprecation")
    private void connectToOpenNetworkLegacy(ScanResult sr) {
        String ssid = null;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ssid = Objects.requireNonNull(sr.getWifiSsid()).toString();
        }

        WifiConfiguration config = new WifiConfiguration();
        config.SSID = "\"" + ssid + "\"";
        config.allowedKeyManagement.set(WifiConfiguration.KeyMgmt.NONE);

        int netId = wifiManager.addNetwork(config);
        if (netId != -1) {
            wifiManager.disconnect();
            wifiManager.enableNetwork(netId, true);
            wifiManager.reconnect();
            onConnectedToOpenNetwork(sr);
        } else {
            Toast.makeText(requireContext(),
                    "Failed to add network " + ssid,
                    Toast.LENGTH_SHORT).show();
        }
    }

    @androidx.annotation.RequiresApi(29)
    private void connectToOpenNetworkQ(ScanResult sr) {
        WifiNetworkSpecifier spec = new WifiNetworkSpecifier.Builder()
                .setSsid(sr.SSID)
                .build();

        NetworkRequest req = new NetworkRequest.Builder()
                .addTransportType(NetworkCapabilities.TRANSPORT_WIFI)
                .setNetworkSpecifier(spec)
                .build();

        ConnectivityManager cm =
                (ConnectivityManager) requireContext()
                        .getSystemService(Context.CONNECTIVITY_SERVICE);

        cm.requestNetwork(req, new ConnectivityManager.NetworkCallback() {
            @Override
            public void onAvailable(@NonNull Network network) {
                cm.bindProcessToNetwork(network);
                requireActivity().runOnUiThread(() -> {
                    Toast.makeText(requireContext(),
                            "Connected to " + sr.SSID,
                            Toast.LENGTH_SHORT).show();
                    onConnectedToOpenNetwork(sr);
                });
            }
            @Override
            public void onUnavailable() {
                requireActivity().runOnUiThread(() ->
                        Toast.makeText(requireContext(),
                                "Failed to connect to " + sr.SSID,
                                Toast.LENGTH_SHORT).show());
            }
        });
    }

    @SuppressLint("NotifyDataSetChanged")
    private void onConnectedToOpenNetwork(ScanResult sr) {
        float dist = calculateDistance(sr);

        HistoryEntry h = null;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            h = new HistoryEntry(
                    System.currentTimeMillis(),
                    Objects.requireNonNull(sr.getWifiSsid()).toString(),
                    sr.level,
                    dist,
                    distanceUnit,
                    lastKnownLatLng.latitude,
                    lastKnownLatLng.longitude
            );
        }
        storage.addEntry(h);
        adapter.notifyDataSetChanged();
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

        finePermissionGranted = true;
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

    @SuppressLint("NotifyDataSetChanged")
    @Override
    public void onSharedPreferenceChanged(SharedPreferences prefs, String key) {
        switch (Objects.requireNonNull(key)) {
            case "pref_notify_threshold":
            case "pref_notify_level":
                notifyThreshold = prefs.getBoolean("pref_notify_threshold", false);
                thresholdDbm = Integer.parseInt(
                        prefs.getString("pref_notify_level", "-60"));
                doScan();
                Toast.makeText(requireContext(),
                        "Notify threshold set to " + thresholdDbm + " dBm",
                        Toast.LENGTH_SHORT).show();
                break;

            case "pref_distance_unit":
                distanceUnit = prefs.getString("pref_distance_unit", "m");
                adapter.notifyDataSetChanged();
                break;

            default:
                updateSettingsFromPrefs(prefs);
                break;
        }
    }



    private void updateSettingsFromPrefs(SharedPreferences prefs) {
        boolean autoScan = prefs.getBoolean("pref_auto_scan", false);
        scanIntervalMs = Integer.parseInt(prefs.getString("pref_scan_interval", "5000"));
        distanceUnit = prefs.getString("pref_distance_unit", "m");
        if(adapter != null) {
            adapter.setDistanceUnit(distanceUnit);
        }
        notifyThreshold = prefs.getBoolean("pref_notify_threshold", false);
        thresholdDbm = Integer.parseInt(prefs.getString("pref_notify_level", "-60"));
        autoConnect = prefs.getBoolean("pref_auto_connect", false);

        scanHandler.removeCallbacks(scanRunnable);
        if (autoScan && scanIntervalMs > 0) {
            scanHandler.postDelayed(scanRunnable, scanIntervalMs);
        }
    }

}
