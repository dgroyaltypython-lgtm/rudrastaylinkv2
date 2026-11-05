package com.rudradigital.rudrastaylink.data

import com.google.gson.annotations.SerializedName

 data class TokenResponse(
    val access: String,
    val refresh: String,
    val user: OwnerUser? = null
)

data class OwnerUser(
    val id: Int = 0,
    val username: String = "",
    @SerializedName("first_name") val firstName: String = "",
    @SerializedName("last_name") val lastName: String = "",
    val role: String = "OWNER"
)

data class ApiPage<T>(
    val count: Int = 0,
    val next: String? = null,
    val previous: String? = null,
    val results: List<T> = emptyList()
)

data class PropertyDto(
    val id: Int,
    val name: String,
    val area: String = "",
    val address: String = "",
    @SerializedName("contact_person") val contactPerson: String = "",
    val phone: String = "",
    @SerializedName("whatsapp_number") val whatsappNumber: String = "",
    val description: String = "",
    @SerializedName("is_active") val isActive: Boolean = true,
    @SerializedName("rooms_count") val roomsCount: Int = 0
)

data class RoomDto(
    val id: Int,
    val property: Int,
    @SerializedName("property_name") val propertyName: String = "",
    val name: String,
    @SerializedName("room_type") val roomType: String = "Standard",
    val capacity: Int = 2,
    @SerializedName("rate_per_night") val ratePerNight: String = "0",
    val status: String = "AVAILABLE",
    val amenities: String = "",
    val notes: String = "",
    @SerializedName("occupied_guest") val occupiedGuest: String = "",
    @SerializedName("expected_checkout") val expectedCheckout: String? = null,
    @SerializedName("photo_url") val photoUrl: String? = null,
    @SerializedName("is_active") val isActive: Boolean = true
)

data class RoomSummary(
    val total: Int = 0,
    val available: Int = 0,
    val occupied: Int = 0,
    val maintenance: Int = 0
)

data class EnquirySummary(
    val total: Int = 0,
    val new: Int = 0
)

data class EnquiryDto(
    val id: Int,
    @SerializedName("property_name") val propertyName: String = "",
    @SerializedName("room_name") val roomName: String = "",
    @SerializedName("nightly_rate") val nightlyRate: String = "0",
    @SerializedName("customer_name") val customerName: String = "",
    @SerializedName("customer_phone") val customerPhone: String = "",
    @SerializedName("guest_count") val guestCount: Int = 2,
    @SerializedName("check_in") val checkIn: String = "",
    @SerializedName("check_out") val checkOut: String = "",
    val message: String = "",
    val status: String = "NEW",
    @SerializedName("created_at") val createdAt: String = ""
)

data class DashboardDto(
    @SerializedName("owner_name") val ownerName: String = "",
    @SerializedName("properties_count") val propertiesCount: Int = 0,
    val rooms: RoomSummary = RoomSummary(),
    val enquiries: EnquirySummary = EnquirySummary(),
    @SerializedName("unread_notifications") val unreadNotifications: Int = 0,
    val properties: List<PropertyDto> = emptyList(),
    @SerializedName("recent_enquiries") val recentEnquiries: List<EnquiryDto> = emptyList()
)

data class NotificationDto(
    val id: Int,
    val title: String,
    val message: String,
    @SerializedName("is_read") val isRead: Boolean = false,
    @SerializedName("created_at") val createdAt: String = "",
    val enquiry: EnquiryDto? = null
)

data class PropertyRequest(
    val name: String,
    val area: String,
    val address: String = "",
    @SerializedName("contact_person") val contactPerson: String = "",
    val phone: String = "",
    @SerializedName("whatsapp_number") val whatsappNumber: String = "",
    val description: String = ""
)

data class AvailabilityRequest(
    val status: String,
    @SerializedName("occupied_guest") val occupiedGuest: String = "",
    @SerializedName("expected_checkout") val expectedCheckout: String? = null
)

data class EnquiryStatusRequest(val status: String)

data class ChangePasswordRequest(
    @SerializedName("current_password") val currentPassword: String,
    @SerializedName("new_password") val newPassword: String
)

data class ApiMessage(val detail: String = "")
