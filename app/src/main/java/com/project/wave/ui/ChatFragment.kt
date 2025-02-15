package com.project.wave.ui

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import androidx.navigation.fragment.navArgs
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import com.google.firebase.storage.FirebaseStorage
import com.project.wave.databinding.FragmentChatBinding
import com.project.wave.model.ChatPermission
import com.project.wave.model.Message
import com.project.wave.model.MessageType
import com.project.wave.model.PermissionStatus
import com.project.wave.model.User
import com.project.wave.ui.adapter.ChatAdapter
import java.util.UUID

class ChatFragment : Fragment() {
    private var _binding: FragmentChatBinding? = null
    private val binding get() = _binding!!
    private lateinit var auth: FirebaseAuth
    private lateinit var db: FirebaseFirestore
    private lateinit var storage: FirebaseStorage
    private lateinit var adapter: ChatAdapter
    
    private val args: ChatFragmentArgs by navArgs()
    private var otherUserId: String? = null
    private var otherUser: User? = null
    private var isFirstMessage = true
    private var permissionStatus: PermissionStatus = PermissionStatus.PENDING
    private val initialMessages = listOf("Hi", "Hello", "I need your help")
    private val STORAGE_PERMISSION_CODE = 1

    private val filePickerLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            result.data?.data?.let { uri ->
                uploadFile(uri)
            }
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentChatBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        
        // Initialize Firebase
        auth = FirebaseAuth.getInstance()
        db = FirebaseFirestore.getInstance()
        storage = FirebaseStorage.getInstance()
        
        // Get user ID from arguments
        otherUserId = args.userId
        
        // Update toolbar with initial data
        binding.toolbar.apply {
            userEmail.text = args.userEmail
            userRollNumber.text = args.userRollNumber
            
            // Set avatar
            val avatarResId = resources.getIdentifier(
                "avatar_${args.userAvatarId}",
                "drawable",
                requireContext().packageName
            )
            userAvatar.setImageResource(avatarResId)
            
            // Setup back button
            backButton.setOnClickListener {
                findNavController().navigateUp()
            }
        }
        
        // Setup RecyclerView
        setupRecyclerView()
        
        // Load messages
        loadMessages()
        
        // Setup message input
        setupMessageInput()
        
        // Hide normal message input initially
        binding.messageInputContainer.visibility = View.GONE
        
        // Setup preset messages
        setupPresetMessages()
    }

    private fun setupRecyclerView() {
        adapter = ChatAdapter(
            currentUserId = auth.currentUser?.uid ?: "",
            onFileClick = { fileUrl ->
                openFile(fileUrl)
            }
        )
        binding.messagesRecyclerView.apply {
            layoutManager = LinearLayoutManager(context).apply {
                stackFromEnd = true
            }
            adapter = this@ChatFragment.adapter
        }
    }

    private fun setupMessageInput() {
        binding.sendButton.setOnClickListener {
            val messageText = binding.messageInput.text.toString().trim()
            if (messageText.isNotEmpty()) {
                sendMessage(messageText)
                binding.messageInput.text?.clear()
            }
        }

        binding.attachButton.setOnClickListener {
            checkStoragePermission()
        }
    }

    private fun loadMessages() {
        val currentUserId = auth.currentUser?.uid ?: return
        otherUserId?.let { recipientId ->
            val chatId = getChatId(currentUserId, recipientId)
            
            db.collection("chats")
                .document(chatId)
                .collection("messages")
                .orderBy("timestamp", Query.Direction.ASCENDING)
                .addSnapshotListener { snapshot, _ ->
                    snapshot?.let { documents ->
                        val messages = documents.mapNotNull { it.toObject(Message::class.java) }
                        adapter.submitList(messages)
                        binding.messagesRecyclerView.scrollToPosition(messages.size - 1)
                    }
                }
        }
    }

    private fun sendMessage(text: String) {
        val currentUserId = auth.currentUser?.uid ?: return
        otherUserId?.let { recipientId ->
            val chatId = getChatId(currentUserId, recipientId)
            
            val message = Message(
                senderId = currentUserId,
                receiverId = recipientId,
                text = text,
                timestamp = System.currentTimeMillis(),
                type = MessageType.TEXT
            )
            
            db.collection("chats")
                .document(chatId)
                .collection("messages")
                .add(message)
        }
    }

    private fun checkStoragePermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(
                    requireContext(),
                    Manifest.permission.READ_MEDIA_IMAGES
                ) != PackageManager.PERMISSION_GRANTED
            ) {
                ActivityCompat.requestPermissions(
                    requireActivity(),
                    arrayOf(Manifest.permission.READ_MEDIA_IMAGES),
                    STORAGE_PERMISSION_CODE
                )
            } else {
                openFilePicker()
            }
        } else {
            if (ContextCompat.checkSelfPermission(
                    requireContext(),
                    Manifest.permission.READ_EXTERNAL_STORAGE
                ) != PackageManager.PERMISSION_GRANTED
            ) {
                ActivityCompat.requestPermissions(
                    requireActivity(),
                    arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE),
                    STORAGE_PERMISSION_CODE
                )
            } else {
                openFilePicker()
            }
        }
    }

    private fun openFilePicker() {
        val intent = Intent(Intent.ACTION_GET_CONTENT).apply {
            type = "*/*"
        }
        filePickerLauncher.launch(intent)
    }

    private fun uploadFile(uri: Uri) {
        val fileName = UUID.randomUUID().toString()
        val fileRef = storage.reference.child("chat_files/$fileName")
        
        fileRef.putFile(uri)
            .addOnSuccessListener {
                fileRef.downloadUrl.addOnSuccessListener { downloadUrl ->
                    val message = Message(
                        id = UUID.randomUUID().toString(),
                        senderId = auth.currentUser?.uid ?: return@addOnSuccessListener,
                        receiverId = otherUserId ?: return@addOnSuccessListener,
                        text = fileName,
                        fileUrl = downloadUrl.toString(),
                        type = MessageType.FILE,
                        timestamp = System.currentTimeMillis()
                    )
                    sendMessage(message.toString())
                }
            }
            .addOnFailureListener {
                Toast.makeText(context, "Failed to upload file", Toast.LENGTH_SHORT).show()
            }
    }

    private fun openFile(fileUrl: String) {
        val intent = Intent(Intent.ACTION_VIEW).apply {
            data = Uri.parse(fileUrl)
        }
        startActivity(intent)
    }

    private fun getChatId(userId1: String, userId2: String): String {
        return if (userId1 < userId2) "${userId1}_${userId2}" else "${userId2}_${userId1}"
    }

    private fun loadOtherUserData() {
        otherUserId?.let { uid ->
            db.collection("users").document(uid).get()
                .addOnSuccessListener { document ->
                    otherUser = document.toObject(User::class.java)
                    updateToolbarInfo()
                }
        }
    }

    private fun updateToolbarInfo() {
        binding.toolbar.apply {
            userEmail.text = otherUser?.email
            userRollNumber.text = otherUser?.rollNumber
            
            // Set avatar
            val avatarResId = resources.getIdentifier(
                "avatar_${otherUser?.avatarId ?: 1}",
                "drawable",
                requireContext().packageName
            )
            userAvatar.setImageResource(avatarResId)
        }
    }

    private fun setupPresetMessages() {
        binding.presetMessagesContainer.visibility = View.VISIBLE
        binding.messageInputContainer.visibility = View.GONE
        binding.permissionDialog.visibility = View.GONE
        
        initialMessages.forEachIndexed { index, message ->
            when (index) {
                0 -> binding.presetMessage1.apply {
                    text = message
                    setOnClickListener { 
                        sendFirstMessage(message)
                    }
                }
                1 -> binding.presetMessage2.apply {
                    text = message
                    setOnClickListener { 
                        sendFirstMessage(message)
                    }
                }
                2 -> binding.presetMessage3.apply {
                    text = message
                    setOnClickListener { 
                        sendFirstMessage(message)
                    }
                }
            }
        }
    }

    private fun sendFirstMessage(messageText: String) {
        val currentUserId = auth.currentUser?.uid ?: return
        otherUserId?.let { recipientId ->
            val chatId = getChatId(currentUserId, recipientId)
            
            val message = Message(
                senderId = currentUserId,
                receiverId = recipientId,
                text = messageText,
                timestamp = System.currentTimeMillis(),
                type = MessageType.TEXT,
                isFirstMessage = true
            )
            
            // Create chat permission document
            val permission = ChatPermission(
                senderId = currentUserId,
                receiverId = recipientId,
                status = PermissionStatus.PENDING
            )
            
            db.collection("chatPermissions")
                .document("${recipientId}_${currentUserId}") // Note: receiver_sender format
                .set(permission)
                .addOnSuccessListener {
                    // After setting permission, send the message
                    db.collection("chats")
                        .document(chatId)
                        .collection("messages")
                        .add(message)
                        .addOnSuccessListener {
                            // Disable preset messages after sending first message
                            binding.presetMessagesContainer.visibility = View.GONE
                            Toast.makeText(context, "Message sent! Waiting for receiver's permission", Toast.LENGTH_LONG).show()
                            // Navigate back to home
                            findNavController().navigateUp()
                        }
                }
        }
    }

    private fun checkChatPermission() {
        val currentUserId = auth.currentUser?.uid ?: return
        otherUserId?.let { uid ->
            db.collection("chatPermissions")
                .document("${currentUserId}_${uid}") // Note: receiver_sender format
                .get()
                .addOnSuccessListener { document ->
                    val permission = document.toObject(ChatPermission::class.java)
                    when (permission?.status) {
                        PermissionStatus.ALLOWED -> {
                            binding.permissionDialog.visibility = View.GONE
                            binding.messageInputContainer.visibility = View.VISIBLE
                            binding.presetMessagesContainer.visibility = View.GONE
                        }
                        PermissionStatus.BLOCKED -> {
                            showBlockedMessage()
                        }
                        PermissionStatus.PENDING -> {
                            if (currentUserId == permission.receiverId) {
                                // Show permission dialog to receiver
                                binding.permissionDialog.visibility = View.VISIBLE
                                binding.messageInputContainer.visibility = View.GONE
                                binding.presetMessagesContainer.visibility = View.GONE
                            } else {
                                // Show waiting message to sender
                                Toast.makeText(context, "Waiting for receiver's permission", Toast.LENGTH_SHORT).show()
                                findNavController().navigateUp()
                            }
                        }
                        null -> {
                            // No permission record exists
                            binding.presetMessagesContainer.visibility = View.VISIBLE
                            binding.messageInputContainer.visibility = View.GONE
                            binding.permissionDialog.visibility = View.GONE
                        }
                    }
                }
        }
    }

    private fun showMessageInput() {
        binding.messageInputContainer.visibility = View.VISIBLE
        binding.presetMessagesContainer.visibility = View.GONE
        binding.permissionDialog.visibility = View.GONE
    }

    private fun showBlockedMessage() {
        Toast.makeText(context, "You cannot send messages to this user", Toast.LENGTH_SHORT).show()
        findNavController().navigateUp()
    }

    private fun showPermissionDialog() {
        binding.permissionDialog.visibility = View.VISIBLE
        
        binding.allowButton.setOnClickListener {
            updatePermissionStatus(PermissionStatus.ALLOWED)
            binding.permissionDialog.visibility = View.GONE
            showMessageInput()
        }
        
        binding.blockButton.setOnClickListener {
            updatePermissionStatus(PermissionStatus.BLOCKED)
            findNavController().navigateUp()
        }
    }

    private fun updatePermissionStatus(status: PermissionStatus) {
        val currentUserId = auth.currentUser?.uid ?: return
        otherUserId?.let { uid ->
            db.collection("chatPermissions")
                .document("${currentUserId}_${uid}")
                .update("status", status)
                .addOnSuccessListener {
                    when (status) {
                        PermissionStatus.ALLOWED -> {
                            binding.permissionDialog.visibility = View.GONE
                            binding.messageInputContainer.visibility = View.VISIBLE
                        }
                        PermissionStatus.BLOCKED -> {
                            showBlockedMessage()
                        }
                        else -> {}
                    }
                }
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        when (requestCode) {
            STORAGE_PERMISSION_CODE -> {
                if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                    openFilePicker()
                } else {
                    Toast.makeText(context, "Permission denied", Toast.LENGTH_SHORT).show()
                }
            }
            else -> super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
} 