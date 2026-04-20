package com.example.ebike

import android.util.Log
import com.google.firebase.database.FirebaseDatabase

class FirebaseCommandSender {

    companion object {
        private const val TAG = "FirebaseCommand"
        private const val COMMAND_PATH = "bike_commands/direction"
    }

    private val commandRef = FirebaseDatabase.getInstance().getReference(COMMAND_PATH)

    fun sendCommand(command: String, onResult: (success: Boolean, errorMessage: String?) -> Unit) {
        commandRef.setValue(command)
            .addOnSuccessListener {
                Log.d(TAG, "Sent command: $command")
                onResult(true, null)
            }
            .addOnFailureListener { exception ->
                Log.e(TAG, "Failed to send command: $command", exception)
                onResult(false, exception.message ?: "Unknown Firebase write error")
            }
    }
}
