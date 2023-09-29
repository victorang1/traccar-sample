package id.trsp.traccarsample

import id.trsp.traccarsample.model.Command
import id.trsp.traccarsample.model.CommandType
import id.trsp.traccarsample.model.Device
import id.trsp.traccarsample.model.User
import retrofit2.Call
import retrofit2.http.Body
import retrofit2.http.Field
import retrofit2.http.FormUrlEncoded
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Query

interface WebService {
    @FormUrlEncoded
    @POST("/api/session")
    fun addSession(
        @Field("email") email: String,
        @Field("password") password: String
    ): Call<User>

    @GET("/api/devices")
    fun getDevices(): Call<List<Device>>

    @GET("/api/commandtypes")
    fun getCommandTypes(@Query("deviceId") deviceId: Long): Call<List<CommandType>>

    @POST("/api/commands")
    fun sendCommand(@Body command: Command?): Call<Command>
}
