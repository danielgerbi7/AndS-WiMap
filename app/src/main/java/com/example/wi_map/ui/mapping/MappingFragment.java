package com.example.wi_map.ui.mapping;

import android.Manifest;
import android.content.Context;
import android.content.pm.PackageManager;
import android.net.wifi.ScanResult;
import android.net.wifi.WifiManager;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.app.ActivityCompat;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;

import com.example.wi_map.R;
import com.example.wi_map.interfaces.INetworkClickListener;
import com.example.wi_map.adapters.NetworkAdapter;
import com.example.wi_map.databinding.FragmentMappingBinding;
import com.google.android.gms.location.FusedLocationProviderClient;
import com.google.android.gms.location.LocationServices;
import com.google.android.gms.maps.CameraUpdateFactory;
import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.OnMapReadyCallback;
import com.google.android.gms.maps.SupportMapFragment;
import com.google.android.gms.maps.model.BitmapDescriptorFactory;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.maps.model.MarkerOptions;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;

public class MappingFragment extends Fragment
        implements OnMapReadyCallback, INetworkClickListener {

    private static final int REQ_PERM_COARSE_LOCATION = 1001;

    private FragmentMappingBinding binding;
    private GoogleMap mMap;
    private NetworkAdapter adapter;
    private final List<ScanResult> networks = new ArrayList<>();
    private WifiManager wifiManager;
    private FusedLocationProviderClient locationClient;
    private LatLng lastKnownLatLng = null;

    private final HashMap<String, LatLng> fingerprintMap = new HashMap<>();

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setHasOptionsMenu(true);
    }


    @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                             ViewGroup container, Bundle savedInstanceState) {
        binding = FragmentMappingBinding.inflate(inflater, container, false);
        View root = binding.getRoot();

        wifiManager = (WifiManager) requireContext()
                .getApplicationContext()
                .getSystemService(Context.WIFI_SERVICE);
        locationClient = LocationServices
                .getFusedLocationProviderClient(requireActivity());


        SupportMapFragment mapFrag = (SupportMapFragment)
                getChildFragmentManager().findFragmentById(R.id.map_fragment);
        assert mapFrag != null;
        mapFrag.getMapAsync(this);


        binding.rvNetworks.setLayoutManager(
                new LinearLayoutManager(requireContext()));
        adapter = new NetworkAdapter(networks,this);
        binding.rvNetworks.setAdapter(adapter);


        return root;
    }

    private void doScan() {
        if (ActivityCompat.checkSelfPermission(requireContext(),
                Manifest.permission.ACCESS_COARSE_LOCATION)
                != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(
                    new String[]{Manifest.permission.ACCESS_COARSE_LOCATION},
                    REQ_PERM_COARSE_LOCATION
            );
            return;
        }

        wifiManager.startScan();
        List<ScanResult> results = wifiManager.getScanResults();

        results.sort(Comparator.comparingInt(r -> -r.level));

        networks.clear();
        networks.addAll(results.subList(0, Math.min(results.size(), 10)));
        adapter.notifyDataSetChanged();

        updateMapMarkers();
    }

    private void updateMapMarkers() {
        if (mMap == null) return;

        mMap.clear();

        if (lastKnownLatLng != null) {
            mMap.addMarker(new MarkerOptions()
                    .position(lastKnownLatLng)
                    .title("You")
                    .icon(BitmapDescriptorFactory.defaultMarker(
                            BitmapDescriptorFactory.HUE_AZURE)));
        }

        for (ScanResult net : networks) {
            LatLng pos = fingerprintMap.get(net.BSSID);
            if (pos == null && lastKnownLatLng != null) {
                pos = new LatLng(
                        lastKnownLatLng.latitude + (Math.random()-0.5)*0.001,
                        lastKnownLatLng.longitude + (Math.random()-0.5)*0.001
                );
            }
            if (pos != null) {
                float hue = net.level > -50 ? BitmapDescriptorFactory.HUE_GREEN
                        : net.level > -70 ? BitmapDescriptorFactory.HUE_YELLOW
                        : BitmapDescriptorFactory.HUE_RED;
                mMap.addMarker(new MarkerOptions()
                        .position(pos)
                        .title(net.SSID)
                        .snippet(net.level + " dBm")
                        .icon(BitmapDescriptorFactory.defaultMarker(hue)));
            }
        }
    }

    @Override
    public void onNetworkClick(ScanResult network) {
        LatLng pos = fingerprintMap.get(network.BSSID);
        if (pos == null && lastKnownLatLng != null) {
            pos = lastKnownLatLng;
        }
        if (mMap != null && pos != null) {
            mMap.animateCamera(CameraUpdateFactory
                    .newLatLngZoom(pos, 18f));
        }
    }

    @Override
    public void onMapReady(@NonNull GoogleMap googleMap) {
        mMap = googleMap;
        mMap.getUiSettings().setZoomControlsEnabled(true);

        if (ActivityCompat.checkSelfPermission(requireContext(),
                Manifest.permission.ACCESS_FINE_LOCATION)
                == PackageManager.PERMISSION_GRANTED) {

            mMap.setMyLocationEnabled(true);
            locationClient.getLastLocation()
                    .addOnSuccessListener(loc -> {
                        if (loc != null) {
                            lastKnownLatLng = new LatLng(
                                    loc.getLatitude(), loc.getLongitude());
                            mMap.moveCamera(CameraUpdateFactory
                                    .newLatLngZoom(lastKnownLatLng, 16f));
                        }
                    });
        } else {
            requestPermissions(
                    new String[]{Manifest.permission.ACCESS_FINE_LOCATION},
                    REQ_PERM_COARSE_LOCATION
            );
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode,
                                           @NonNull String[] permissions,
                                           @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(
                requestCode, permissions, grantResults);

        if (requestCode == REQ_PERM_COARSE_LOCATION
                && grantResults.length > 0
                && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
            doScan();
            if (mMap != null) {
                onMapReady(mMap);
            }
        }
    }

    @Override
    public void onCreateOptionsMenu(@NonNull Menu menu, @NonNull MenuInflater inflater) {
        super.onCreateOptionsMenu(menu, inflater);
        inflater.inflate(R.menu.fragment_mapping_menu, menu);
    }

    @Override
    public boolean onOptionsItemSelected(@NonNull MenuItem item) {
        if (item.getItemId() == R.id.action_refresh) {
            doScan();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }


    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
    }
}
