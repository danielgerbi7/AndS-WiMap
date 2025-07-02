package com.example.wi_map.ui.history;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.view.MenuProvider;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.Lifecycle;
import androidx.recyclerview.widget.LinearLayoutManager;

import com.example.wi_map.R;
import com.example.wi_map.adapters.HistoryAdapter;
import com.example.wi_map.data.HistoryStorage;
import com.example.wi_map.databinding.FragmentHistoryBinding;
import com.example.wi_map.models.HistoryEntry;
import com.google.android.material.snackbar.Snackbar;

import java.util.List;

public class HistoryFragment extends Fragment {
    private FragmentHistoryBinding binding;
    private HistoryAdapter adapter;
    private HistoryStorage storage;

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                             ViewGroup container,
                             Bundle savedInstanceState) {
        binding = FragmentHistoryBinding.inflate(inflater, container, false);
        storage = new HistoryStorage(requireContext());
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view,
                              @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        binding.rvHistory.setLayoutManager(
                new LinearLayoutManager(requireContext()));
        adapter = new HistoryAdapter();
        binding.rvHistory.setAdapter(adapter);

        loadAndShow();

        requireActivity().addMenuProvider(new MenuProvider() {
            @Override
            public void onCreateMenu(@NonNull Menu menu,
                                     @NonNull MenuInflater inflater) {
                inflater.inflate(R.menu.menu_history, menu);
            }
            @Override
            public boolean onMenuItemSelected(@NonNull MenuItem item) {
                if (item.getItemId() == R.id.action_clear_history) {
                    storage.clear();
                    loadAndShow();
                    Snackbar.make(binding.getRoot(),
                                    "History cleared",
                                    Snackbar.LENGTH_SHORT)
                            .show();
                    return true;
                }
                return false;
            }
        }, getViewLifecycleOwner(), Lifecycle.State.RESUMED);
    }


    private void loadAndShow() {
        List<HistoryEntry> list = storage.load();
        adapter.submitList(list);
    }
    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
    }
}
