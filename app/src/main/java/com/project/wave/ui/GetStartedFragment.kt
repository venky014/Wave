package com.project.wave.ui

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import com.project.wave.R
import com.project.wave.databinding.FragmentGetStartedBinding

class GetStartedFragment : Fragment() {
    private var _binding: FragmentGetStartedBinding? = null
    private val binding get() = _binding!!

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentGetStartedBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        binding.getStartedButton.setOnClickListener {
            findNavController().navigate(R.id.action_to_signIn)
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
} 