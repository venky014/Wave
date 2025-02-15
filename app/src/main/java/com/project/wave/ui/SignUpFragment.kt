package com.project.wave.ui

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.project.wave.R
import com.project.wave.databinding.FragmentSignUpBinding

class SignUpFragment : Fragment() {
    private var _binding: FragmentSignUpBinding? = null
    private val binding get() = _binding!!
    private lateinit var auth: FirebaseAuth
    private lateinit var db: FirebaseFirestore

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentSignUpBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        auth = FirebaseAuth.getInstance()
        db = FirebaseFirestore.getInstance()

        binding.signUpButton.setOnClickListener {
            val email = binding.emailEditText.text.toString()
            val password = binding.passwordEditText.text.toString()
            val confirmPassword = binding.confirmPasswordEditText.text.toString()
            val rollNumber = binding.rollNumberEditText.text.toString()

            if (validateInputs(email, password, confirmPassword, rollNumber)) {
                auth.createUserWithEmailAndPassword(email, password)
                    .addOnCompleteListener { task ->
                        if (task.isSuccessful) {
                            // Save additional user data to Firestore
                            val user = hashMapOf(
                                "email" to email,
                                "rollNumber" to rollNumber
                            )
                            
                            db.collection("users")
                                .document(auth.currentUser!!.uid)
                                .set(user)
                                .addOnSuccessListener {
                                    findNavController().navigate(R.id.action_to_home)
                                }
                                .addOnFailureListener { e ->
                                    Toast.makeText(
                                        context,
                                        "Failed to save user data: ${e.message}",
                                        Toast.LENGTH_SHORT
                                    ).show()
                                }
                        } else {
                            Toast.makeText(
                                context,
                                "Sign up failed: ${task.exception?.message}",
                                Toast.LENGTH_SHORT
                            ).show()
                        }
                    }
            }
        }
    }

    private fun validateInputs(
        email: String,
        password: String,
        confirmPassword: String,
        rollNumber: String
    ): Boolean {
        if (email.isEmpty() || password.isEmpty() || confirmPassword.isEmpty() || rollNumber.isEmpty()) {
            Toast.makeText(context, "Please fill all fields", Toast.LENGTH_SHORT).show()
            return false
        }

        if (password != confirmPassword) {
            Toast.makeText(context, "Passwords do not match", Toast.LENGTH_SHORT).show()
            return false
        }

        if (password.length < 6) {
            Toast.makeText(
                context,
                "Password should be at least 6 characters long",
                Toast.LENGTH_SHORT
            ).show()
            return false
        }

        return true
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
} 