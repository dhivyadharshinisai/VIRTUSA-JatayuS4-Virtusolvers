package com.example.safemindwatch.api
import com.example.safemindwatch.AbnormalQueryResponse
import com.example.safemindwatch.ChildDeleteRequest
import com.example.safemindwatch.ChildProfile
import com.example.safemindwatch.ChildProfileResponse
import com.example.safemindwatch.ChildUpdateRequest
import com.example.safemindwatch.DrillDownResponse
import com.example.safemindwatch.GenericResponse
import com.example.safemindwatch.LoginResponse
import com.example.safemindwatch.MentalHealthStatsResponse
import com.example.safemindwatch.PeakHourResponse
import com.example.safemindwatch.PieChartResponse
import com.example.safemindwatch.PredictionSummaryResponse
import com.example.safemindwatch.ProfileImageResponse
import com.example.safemindwatch.ProfileRequest
import com.example.safemindwatch.ProfileResponse
import com.example.safemindwatch.SentimentResponse
import com.example.safemindwatch.StatisticsResponse
import com.example.safemindwatch.UpdateSettingsRequest
import com.example.safemindwatch.User
import com.google.gson.JsonObject
import okhttp3.ResponseBody
import retrofit2.Call
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.HTTP
import retrofit2.http.POST
import retrofit2.http.PUT
import retrofit2.http.Path
import retrofit2.http.Query

data class ApiResponse(
    val message: String,
    val verified: Boolean? = null
)

data class OtpVerifyRequest(val email: String, val otp: String)

data class OtpVerifyResponse(
    val success: Boolean,
    val message: String
)

data class ResetPasswordRequest(
    val email: String,
    val newPassword: String
)

data class SOSAlertResponse(
    val sosActive: Boolean,
    val childName: String?,
    val query : String,
    val timestamp: String?
)

data class SosLog(
    val userId: String,
    val userEmail: String,
    val childId: String,
    val childName: String,
    val query: String,
    val alertTime: String?,
    val createdAt: String?,
    val updatedAt: String?
)

interface ApiService {

   @POST("/signup")
    fun registerUser(@Body userData: HashMap<String, String>): Call<ResponseBody>

    @POST("/google-register")
    fun registerGoogleUser(@Body userData: HashMap<String, String>): Call<ResponseBody>

    @POST("/login")
    fun loginUser(@Body body: HashMap<String, String>): Call<LoginResponse>

    @POST("/getUserByEmail1")
    fun getUserByEmail1(@Body request: Map<String, String>): Call<LoginResponse>

    @POST("/sendOtp")
    fun sendOtp(@Body phoneData: HashMap<String, String>): Call<ResponseBody>

    @GET("api/users/{userId}")
    fun getUserById(
        @Path("userId") userId: String
    ): Call<User>

    @GET("api/users/{userId}/profile-image")
    fun getProfileImageById(
        @Path("userId") userId: String
    ): Call<ProfileImageResponse>

    @POST("api/users/{userId}/profile-image")
    fun uploadProfileImageById(
        @Path("userId") userId: String,
        @Body body: Map<String, String>
    ): Call<GenericResponse>

    @PUT("api/settings/{userId}")
    fun updateSettings(
        @Path("userId") userId: String,
        @Body request: UpdateSettingsRequest
    ): Call<GenericResponse>

    @GET("api/sos-logs")
    fun getSosLogs(
        @Query("userId") userId: String,
        @Query("childName") childName: String
    ): Call<List<SosLog>>

    @POST("/verifyOtp")
    fun verifyOtp(@Body otpData: HashMap<String, String>): Call<ResponseBody>

    @POST("/getUserByEmail")
    fun getUserByEmail(@Body json: JsonObject): Call<User>

    @POST("/getProfileImage")
    fun getProfileImage(@Body json: JsonObject): Call<ProfileImageResponse>

    @POST("/uploadProfileImage")
    fun uploadProfileImage(@Body json: JsonObject): Call<GenericResponse>

    @POST("api/forgot-password")
    fun forgotPassword(@Body request: ForgotPasswordRequest): Call<ForgotPasswordResponse>

    @POST("api/verify-otp")
    fun verifyOtp(@Body request: OtpVerifyRequest): Call<OtpVerifyResponse>

    @POST("api/reset-password")
    fun resetPassword(@Body request: ResetPasswordRequest): Call<GenericResponse>

    @GET("/getChildren")
    fun getChildren(@Query("email") email: String): Call<List<ChildProfile>>

    @POST("/add-child")
    fun addChild(@Body body: Map<String, String>): Call<ChildProfileResponse>

    @PUT("/update-child")
    fun updateChild(@Body request: ChildUpdateRequest): Call<ChildProfileResponse>

    @HTTP(method = "DELETE", path = "/delete-child", hasBody = true)
    fun deleteChild(@Body request: ChildDeleteRequest): Call<ChildProfileResponse>

    @GET("api/sentiment-scaled/{userId}")
    fun getSentimentData(
        @Path("userId") userId: String,
        @Query("mode") mode: String,
        @Query("date") date: String,
        @Query("childName") childName: String
    ): Call<SentimentResponse>

    @GET("api/prediction/{userId}")
    fun getPredictionSummary(
        @Path("userId") userId: String,
        @Query("childName") childName: String,
        @Query("mode") mode: String,
        @Query("date") date: String? = null
    ): Call<PredictionSummaryResponse>

    @GET("api/Statistics/{userId}")
    fun getStatistics(
        @Path("userId") userId: String,
        @Query("mode") mode: String,
        @Query("date") date: String? = null,
        @Query("childName") childName: String
    ): Call<StatisticsResponse>

    @GET("/api/piechart")
    fun getPieChartData(
        @Query("userId") userId: String,
        @Query("mode") mode: String,
        @Query("date") date: String?,
        @Query("childName") childName: String
    ): Call<PieChartResponse>


    @GET("abnormal-queries")
        fun getAbnormalQueries(
        @Query("userId") userId: String,
        @Query("mode") mode: String,
        @Query("date") date: String?,
        @Query("childName") childName: String
        ): Call<AbnormalQueryResponse>

    @GET("api/mental-health/{userId}")
    fun getMentalHealthStats(
        @Path("userId") userId: String,
        @Query("childName") childName: String,
        @Query("mode") mode: String,
        @Query("date") date: String? = null
    ): Call<MentalHealthStatsResponse>

    @POST("/api/drilldown")
    fun getDrillDownData(@Body request: Map<String, String>): Call<DrillDownResponse>

    @POST("/checkGoogleUser")
    fun checkGoogleUser(@Body googleUserData: HashMap<String, String>): Call<ResponseBody>

    @GET("/api/sos-alert/{userId}")
    fun checkSOSAlert(@Path("userId") userId: String): Call<SOSAlertResponse>

    @POST("/api/sos-alert/acknowledge/{userId}")
    fun acknowledgeSOSAlert(@Path("userId") userId: String): Call<Void>

    @POST("/api/send-email-otp")
    fun sendEmailOtp(@Body body: HashMap<String, String>): Call<ApiResponse>

    @POST("/api/verify-email-otp")
    fun verifyEmailOtp(@Body body: HashMap<String, String>): Call<ApiResponse>

    @GET("api/peak-hours/{userId}")
    fun getPeakHours(
        @Path("userId") userId: String,
        @Query("childName") childName: String,
        @Query("mode") mode: String,
        @Query("date") date: String? = null
    ): Call<PeakHourResponse>

    @POST("/api/add-profile")
    fun addProfile(@Body profile: ProfileRequest): Call<ProfileResponse>

}















