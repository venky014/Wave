package com.project.wave.ui

import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.core.widget.addTextChangedListener
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.DividerItemDecoration
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
    private var allUsers = listOf<User>()

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

        setupToolbar()
        setupRecyclerView()
        setupSearch()
        loadUsers()
    }

    private fun setupToolbar() {
        binding.toolbar.setNavigationOnClickListener {
            findNavController().navigateUp()
        }
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
            addItemDecoration(DividerItemDecoration(context, DividerItemDecoration.VERTICAL))
        }
    }

    private fun setupSearch() {
        binding.searchInput.requestFocus()
        
        binding.searchInput.addTextChangedListener { text ->
            filterUsers(text?.toString() ?: "")
        }
    }

    private fun loadUsers() {
        val currentUserId = auth.currentUser?.uid ?: return
        
        binding.progressBar.visibility = View.VISIBLE
        
        db.collection("users")
            .get()
            .addOnSuccessListener { documents ->
                allUsers = documents
                    .mapNotNull { doc ->
                        // Skip current user's document
                        if (doc.id == currentUserId) {
                            null
                        } else {
                            try {
                                User(
                                    id = doc.id,  // Use document ID instead of field
                                    email = doc.getString("email") ?: "",
                                    rollNumber = doc.getString("rollNumber") ?: "",
                                    avatarId = doc.getLong("avatarId")?.toInt() ?: 1
                                )
                            } catch (e: Exception) {
                                Log.e("SearchFragment", "Error mapping user: ${e.message}")
                                null
                            }
                        }
                    }
                    .sortedBy { it.rollNumber }
                
                userAdapter.submitList(allUsers)
                updateVisibility()
            }
            .addOnFailureListener { e ->
                Toast.makeText(context, "Error loading users: ${e.message}", Toast.LENGTH_SHORT).show()
                binding.progressBar.visibility = View.GONE
            }
    }

    private fun filterUsers(query: String) {
        if (query.isEmpty()) {
            userAdapter.submitList(allUsers)
            updateVisibility()
            return
        }

        val filteredUsers = allUsers.filter { user ->
            user.rollNumber.lowercase().contains(query.lowercase()) ||
            user.email.lowercase().contains(query.lowercase())
        }.sortedBy { it.rollNumber }
        
        userAdapter.submitList(filteredUsers)
        updateVisibility()
    }

    private fun updateVisibility() {
        binding.apply {
            if (allUsers.isEmpty()) {
                searchResults.visibility = View.GONE
                noResultsText.visibility = View.VISIBLE
                progressBar.visibility = View.GONE
            } else {
                searchResults.visibility = View.VISIBLE
                noResultsText.visibility = View.GONE
                progressBar.visibility = View.GONE
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
} 