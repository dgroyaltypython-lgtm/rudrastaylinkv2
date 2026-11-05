package com.rudradigital.rudrastaylink.data

import okhttp3.MultipartBody
import okhttp3.RequestBody
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.Multipart
import retrofit2.http.PATCH
import retrofit2.http.POST
import retrofit2.http.Part
import retrofit2.http.Path
import retrofit2.http.Query

interface StayLinkApi {
    @POST("api/v1/auth/token/")
    suspend fun login(@Body body: Map<String, String>): TokenResponse

    @POST("api/v1/auth/token/refresh/")
    suspend fun refresh(@Body body: Map<String, String>): TokenResponse

    @POST("api/v1/auth/logout/")
    suspend fun logout(@Body body: Map<String, String>): ApiMessage

    @GET("api/v1/dashboard/")
    suspend fun dashboard(): DashboardDto

    @GET("api/v1/me/")
    suspend fun me(): OwnerUser

    @GET("api/v1/properties/")
    suspend fun properties(@Query("page_size") pageSize: Int = 100): ApiPage<PropertyDto>

    @POST("api/v1/properties/")
    suspend fun createProperty(@Body body: PropertyRequest): PropertyDto

    @PATCH("api/v1/properties/{id}/")
    suspend fun updateProperty(@Path("id") id: Int, @Body body: PropertyRequest): PropertyDto

    @GET("api/v1/rooms/")
    suspend fun rooms(
        @Query("page_size") pageSize: Int = 100,
        @Query("status") status: String? = null,
        @Query("property") propertyId: Int? = null,
        @Query("q") search: String? = null,
        @Query("include_archived") includeArchived: String? = null
    ): ApiPage<RoomDto>

    @Multipart
    @POST("api/v1/rooms/")
    suspend fun createRoom(
        @Part("property") property: RequestBody,
        @Part("name") name: RequestBody,
        @Part("room_type") roomType: RequestBody,
        @Part("capacity") capacity: RequestBody,
        @Part("rate_per_night") rate: RequestBody,
        @Part("amenities") amenities: RequestBody,
        @Part("notes") notes: RequestBody,
        @Part("status") status: RequestBody,
        @Part photo: MultipartBody.Part?
    ): RoomDto

    @Multipart
    @PATCH("api/v1/rooms/{id}/")
    suspend fun updateRoom(
        @Path("id") id: Int,
        @Part("property") property: RequestBody,
        @Part("name") name: RequestBody,
        @Part("room_type") roomType: RequestBody,
        @Part("capacity") capacity: RequestBody,
        @Part("rate_per_night") rate: RequestBody,
        @Part("amenities") amenities: RequestBody,
        @Part("notes") notes: RequestBody,
        @Part("status") status: RequestBody,
        @Part photo: MultipartBody.Part?
    ): RoomDto

    @PATCH("api/v1/rooms/{id}/availability/")
    suspend fun setAvailability(@Path("id") id: Int, @Body body: AvailabilityRequest): RoomDto

    @DELETE("api/v1/rooms/{id}/")
    suspend fun archiveRoom(@Path("id") id: Int): Response<ApiMessage>

    @POST("api/v1/rooms/{id}/restore/")
    suspend fun restoreRoom(@Path("id") id: Int): ApiMessage

    @GET("api/v1/enquiries/")
    suspend fun enquiries(@Query("page_size") pageSize: Int = 100, @Query("status") status: String? = null): ApiPage<EnquiryDto>

    @PATCH("api/v1/enquiries/{id}/status/")
    suspend fun updateEnquiryStatus(@Path("id") id: Int, @Body body: EnquiryStatusRequest): EnquiryDto

    @GET("api/v1/notifications/")
    suspend fun notifications(@Query("page_size") pageSize: Int = 100, @Query("unread") unread: String? = null): ApiPage<NotificationDto>

    @POST("api/v1/notifications/{id}/read/")
    suspend fun markNotificationRead(@Path("id") id: Int): ApiMessage

    @POST("api/v1/notifications/read-all/")
    suspend fun markAllNotificationsRead(): ApiMessage

    @POST("api/v1/auth/change-password/")
    suspend fun changePassword(@Body body: ChangePasswordRequest): TokenResponse
}
