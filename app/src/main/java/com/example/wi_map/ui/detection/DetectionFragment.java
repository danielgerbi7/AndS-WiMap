package com.example.wi_map.ui.detection;

import android.Manifest;
import android.annotation.SuppressLint;
import android.app.AlertDialog;
import android.content.Context;
import android.content.pm.PackageManager;
import android.location.Location;
import android.location.LocationManager;
import android.net.wifi.ScanResult;
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
import android.widget.EditText;
import android.widget.Toast;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.app.ActivityCompat;
import androidx.core.view.MenuProvider;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.Lifecycle;
import androidx.navigation.fragment.NavHostFragment;
import androidx.recyclerview.widget.LinearLayoutManager;

import com.example.wi_map.R;
import com.example.wi_map.adapters.DetectionAdapter;
import com.example.wi_map.data.FingerprintStorage;
import com.example.wi_map.data.HistoryStorage;
import com.example.wi_map.databinding.FragmentDetectionBinding;
import com.example.wi_map.models.HistoryEntry;
import com.example.wi_map.models.WifiEntry;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public class DetectionFragment extends Fragment {

    private static final int REQ_PERM_FINE_LOCATION = 123;
    private FragmentDetectionBinding binding;
    private WifiManager wifiManager;
    private final List<ScanResult> scanResults = new ArrayList<>();
    private final Handler scanHandler = new Handler(Looper.getMainLooper());
    private Runnable scanRunnable;
    private boolean isScanning = false;
    private final int scanIntervalMs = 5000;
    private double estimatedLat = 0, estimatedLng = 0;
    private boolean hasValidPosition = false;
    private DetectionAdapter adapter;
    private FingerprintStorage fingerprintStorage;

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        binding = FragmentDetectionBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        wifiManager = (WifiManager) requireContext().getApplicationContext().getSystemService(Context.WIFI_SERVICE);

        fingerprintStorage = new FingerprintStorage(requireContext());

        adapter = new DetectionAdapter(scanResults);
        binding.rvDetection.setLayoutManager(new LinearLayoutManager(requireContext()));
        binding.rvDetection.setAdapter(adapter);

        requireActivity().addMenuProvider(new MenuProvider() {
            @Override
            public void onCreateMenu(@NonNull Menu menu, @NonNull MenuInflater inflater) {
                inflater.inflate(R.menu.menu_detection, menu);
            }

            @Override
            public boolean onMenuItemSelected(@NonNull MenuItem item) {
                if (item.getItemId() == R.id.action_refresh) {
                    performScan();
                    return true;
                } else if (item.getItemId() == R.id.action_map_location) {
                    mapCurrentLocation();
                    return true;
                } else if (item.getItemId() == R.id.action_clear_mapped) {
                    fingerprintStorage.clearAll();
                    showFingerprintCount();
                    Toast.makeText(requireContext(), "All mapped networks cleared", Toast.LENGTH_SHORT).show();
                    return true;
                }
                return false;
            }
        }, getViewLifecycleOwner(), Lifecycle.State.RESUMED);

        scanRunnable = () -> {
            performScan();
            if (isScanning) {
                scanHandler.postDelayed(scanRunnable, scanIntervalMs);
            }
        };

        binding.btnStartScan.setOnClickListener(v -> {
            if (!isScanning) startScanning();
            else stopScanning();
        });

        binding.btnSaveDetection.setOnClickListener(v -> {
            if (!hasValidPosition) {
                Toast.makeText(requireContext(), "Please run a scan first to detect position", Toast.LENGTH_SHORT).show();
                return;
            }
            new HistoryStorage(requireContext()).add(new HistoryEntry(System.currentTimeMillis(), "Detection", estimatedLat, (float) estimatedLng));
            Toast.makeText(requireContext(), R.string.detection_saved, Toast.LENGTH_SHORT).show();
        });

        binding.btnGoMapping.setOnClickListener(v -> NavHostFragment.findNavController(this).navigate(R.id.navigation_mapping));

        showFingerprintCount();
    }

    @SuppressWarnings("deprecation")
    private void mapCurrentLocation() {
        if (ActivityCompat.checkSelfPermission(requireContext(), Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.ACCESS_FINE_LOCATION}, REQ_PERM_FINE_LOCATION);
            return;
        }

        LocationManager lm = (LocationManager) requireContext().getSystemService(Context.LOCATION_SERVICE);
        Location loc = lm.getLastKnownLocation(LocationManager.GPS_PROVIDER);
        if (loc != null) {
            saveCurrentWifiFingerprints(loc.getLatitude(), loc.getLongitude());
        } else {
            showManualLocationDialog();
        }
    }

    private void showManualLocationDialog() {
        AlertDialog.Builder b = new AlertDialog.Builder(requireContext());
        EditText input = new EditText(requireContext());
        input.setHint("lat,lon (e.g., 32.0853,34.7818)");
        b.setView(input).setTitle("Enter Current Location").setPositiveButton("Save", (d, w) -> {
            String[] p = input.getText().toString().split(",");
            try {
                double lat = Double.parseDouble(p[0].trim());
                double lon = Double.parseDouble(p[1].trim());
                saveCurrentWifiFingerprints(lat, lon);
            } catch (Exception e) {
                Toast.makeText(requireContext(), "Invalid format", Toast.LENGTH_SHORT).show();
            }
        }).setNegativeButton("Cancel", null).show();
    }

    @SuppressLint("MissingPermission")
    private void saveCurrentWifiFingerprints(double latitude, double longitude) {
        wifiManager.startScan();
        List<ScanResult> results = wifiManager.getScanResults();
        int saved = 0;
        for (ScanResult sr : results) {
            if (sr.level > -110) {
                WifiEntry e = new WifiEntry(sr.SSID, sr.level, 0f, sr.BSSID, latitude, longitude);
                fingerprintStorage.save(e);
                saved++;
            }
        }
        Toast.makeText(requireContext(), "Saved " + saved + " fingerprints", Toast.LENGTH_LONG).show();
        showFingerprintCount();
    }

    private void showFingerprintCount() {
        Map<String, WifiEntry> all = fingerprintStorage.loadAll();
        binding.tvEstimatedPosition.setText(all.isEmpty() ? "No fingerprints mapped yet" : "Mapped networks: " + all.size());
    }

    private void startScanning() {
        isScanning = true;
        binding.btnStartScan.setText(R.string.stop_scan);
        binding.pbScanning.setVisibility(View.VISIBLE);
        scanHandler.post(scanRunnable);
    }

    private void stopScanning() {
        isScanning = false;
        binding.btnStartScan.setText(R.string.start_scan);
        binding.pbScanning.setVisibility(View.GONE);
        scanHandler.removeCallbacks(scanRunnable);
    }

    @SuppressLint({"MissingPermission", "NotifyDataSetChanged", "StringFormatInvalid"})
    @SuppressWarnings("deprecation")
    private void performScan() {
        if (ActivityCompat.checkSelfPermission(requireContext(), Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.ACCESS_FINE_LOCATION}, REQ_PERM_FINE_LOCATION);
            return;
        }

        wifiManager.startScan();
        List<ScanResult> results = wifiManager.getScanResults();
        results.sort(Comparator.comparingInt(r -> -r.level));

        scanResults.clear();
        scanResults.addAll(results.subList(0, Math.min(results.size(), 10)));
        adapter.notifyDataSetChanged();

        Map<String, WifiEntry> map = fingerprintStorage.loadAll();
        double sumLat = 0, sumLng = 0, sumW = 0;
        int matchedNetworks = 0;

        for (ScanResult sr : scanResults) {
            WifiEntry fp = map.get(sr.BSSID);
            if (fp == null) continue;

            double w = Math.pow(10, sr.level / 10.0);
            sumLat += fp.latitude * w;
            sumLng += fp.longitude * w;
            sumW += w;
            matchedNetworks++;
        }
        if (sumW > 0 && matchedNetworks > 0) {
            estimatedLat = sumLat / sumW;
            estimatedLng = sumLng / sumW;
            hasValidPosition = true;
        } else {
            hasValidPosition = false;
        }

        String positionText;
        if (hasValidPosition) {
            positionText = getString(R.string.detection_estimate, String.format(Locale.getDefault(), "%.5f", estimatedLat), String.format(Locale.getDefault(), "%.5f", estimatedLng), matchedNetworks);
        } else {
            positionText = "No position detected. Found " + scanResults.size() + " networks, but " + matchedNetworks + " are mapped. Use Map Current Location' to add more fingerprints.";
        }

        binding.tvEstimatedPosition.setText(positionText);
    }

    @SuppressWarnings("deprecation")
    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == REQ_PERM_FINE_LOCATION && grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
            performScan();
        } else {
            Toast.makeText(requireContext(), R.string.permission_denied, Toast.LENGTH_SHORT).show();
            stopScanning();
        }
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        stopScanning();
        binding = null;
    }
}