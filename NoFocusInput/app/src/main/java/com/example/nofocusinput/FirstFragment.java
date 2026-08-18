package com.example.nofocusinput;

import android.content.Intent;
import android.os.Bundle;
import android.provider.Settings;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.fragment.app.Fragment;
import androidx.navigation.fragment.NavHostFragment;

import com.example.nofocusinput.databinding.FragmentFirstBinding;

public class FirstFragment extends Fragment {

    private FragmentFirstBinding binding;

    @Override
    public View onCreateView(
            @NonNull LayoutInflater inflater, ViewGroup container,
            Bundle savedInstanceState
    ) {

        binding = FragmentFirstBinding.inflate(inflater, container, false);
        return binding.getRoot();

    }

    public void onViewCreated(@NonNull View view, Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        binding.buttonFirst.setOnClickListener(v ->
                NavHostFragment.findNavController(FirstFragment.this)
                        .navigate(R.id.action_FirstFragment_to_SecondFragment)
        );

        binding.buttonShowAlert.setOnClickListener(v ->
                startActivity(new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
        );
    }

    @Override
    public void onStart() {
        super.onStart();
        DemoBroadcastReceiver.setListener(text -> {
            if (binding != null) {
                binding.editBroadcast.setText(text);
            }
        });
    }

    @Override
    public void onResume() {
        super.onResume();
        boolean enabled = InputAccessibilityService.isEnabled(requireContext());
        binding.textviewFirst.setText(
                enabled ? R.string.accessibility_status_on : R.string.accessibility_status_off
        );
        String lastText = DemoBroadcastReceiver.getLastText();
        if (lastText != null) {
            binding.editBroadcast.setText(lastText);
        }
    }

    @Override
    public void onDestroyView() {
        DemoBroadcastReceiver.setListener(null);
        super.onDestroyView();
        binding = null;
    }

}