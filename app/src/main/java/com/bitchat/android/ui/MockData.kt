package com.bitchat.android.ui

enum class ConversationType {
    VERIFIED_BROADCAST,
    LOCAL_RADIUS,
    DIRECT_MESSAGE
}

enum class DeliveryStatus {
    SENT,
    DELIVERED
}

data class MockMessage(
    val sender: String,
    val content: String,
    val timestamp: String,
    val isMine: Boolean,
    val isVerified: Boolean = false,
    val deliveryStatus: DeliveryStatus? = null
)

data class MockConversation(
    val id: String,
    val name: String,
    val type: ConversationType,
    val messages: List<MockMessage>,
    val lastMessage: String = messages.lastOrNull()?.content ?: "",
    val timestamp: String = messages.lastOrNull()?.timestamp ?: "",
    val unreadCount: Int = 0
)

object MockDataProvider {
    val conversations: List<MockConversation> = listOf(
        MockConversation(
            id = "cjp_official",
            name = "\uD83D\uDCE2 CJP Official Updates",
            type = ConversationType.VERIFIED_BROADCAST,
            unreadCount = 2,
            messages = listOf(
                MockMessage(
                    sender = "CJP Official",
                    content = "Do not head towards Main Square. Police blockade ahead. Reroute through 4th street.",
                    timestamp = "10:42 AM",
                    isMine = false,
                    isVerified = true
                ),
                MockMessage(
                    sender = "CJP Official",
                    content = "Internet jammers activated in the north quadrant. Keep your Bluetooth on to maintain the mesh.",
                    timestamp = "11:05 AM",
                    isMine = false,
                    isVerified = true
                )
            )
        ),
        MockConversation(
            id = "local_radius",
            name = "\uD83D\uDCCD Local Radius Chatter",
            type = ConversationType.LOCAL_RADIUS,
            unreadCount = 3,
            messages = listOf(
                MockMessage(
                    sender = "SwiftWolf",
                    content = "Need medics near Gate 4!",
                    timestamp = "10:45 AM",
                    isMine = false
                ),
                MockMessage(
                    sender = "BraveFalcon",
                    content = "I have a medkit, heading your way now.",
                    timestamp = "10:47 AM",
                    isMine = false
                ),
                MockMessage(
                    sender = "SilentBear",
                    content = "Tear gas deployed on 5th ave, cover your faces.",
                    timestamp = "10:50 AM",
                    isMine = false
                )
            )
        ),
        MockConversation(
            id = "ironhawk_dm",
            name = "IronHawk",
            type = ConversationType.DIRECT_MESSAGE,
            unreadCount = 1,
            messages = listOf(
                MockMessage(
                    sender = "IronHawk",
                    content = "Did you make it past the barricade?",
                    timestamp = "11:15 AM",
                    isMine = false
                ),
                MockMessage(
                    sender = "You",
                    content = "Yes, I'm safe in the alley. Keep your location hidden.",
                    timestamp = "11:16 AM",
                    isMine = true,
                    deliveryStatus = DeliveryStatus.DELIVERED
                )
            )
        )
    )
}
