package com.project.wave.ui

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.widget.addTextChangedListener
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.project.wave.R
import com.project.wave.databinding.FragmentSearchBinding
import com.project.wave.model.User
import com.project.wave.ui.adapter.UserAdapter

class SearchFragment : Fragment() {
    private var _binding: FragmentSearchBinding? = null
    private val binding get() = _binding!!
    private lateinit var auth: FirebaseAuth
    private lateinit var db: FirebaseFirestore
    private lateinit var userAdapter: UserAdapter

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentSearchBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        auth = FirebaseAuth.getInstance()
        db = FirebaseFirestore.getInstance()

        setupRecyclerView()
        setupSearch()
    }

    private fun setupRecyclerView() {
        userAdapter = UserAdapter { user ->
            val action = SearchFragmentDirections.actionToChat(
                userId = user.id,
                userEmail = user.email,
                userRollNumber = user.rollNumber,
                userAvatarId = user.avatarId
            )
            findNavController().navigate(action)
        }
        
        binding.searchResults.apply {
            layoutManager = LinearLayoutManager(requireContext())
            adapter = userAdapter
        }
    }

    private fun setupSearch() {
        binding.searchInput.addTextChangedListener { text ->
            val query = text.toString().trim()
            if (query.isNotEmpty()) {
                searchUsers(query)
            } else {
                userAdapter.submitList(emptyList())
            }
        }
    }

    private fun searchUsers(query: String) {
        val currentUserId = auth.currentUser?.uid ?: return
        
        db.collection("users")
            .whereGreaterThanOrEqualTo("email", query)
            .whereLessThanOrEqualTo("email", query + '\uf8ff')
            .get()
            .addOnSuccessListener { documents ->
                val users = documents.mapNotNull { doc ->
                    doc.toObject(User::class.java).takeIf { 
                        it.id != currentUserId 
                    }
                }
                userAdapter.submitList(users)
            }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
} 