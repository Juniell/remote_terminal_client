import okhttp3.*
import retrofit2.Call
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.POST
import retrofit2.http.Query

class WebClient(
    inetAddress: String,
    domain: String,
    port: Int?
) {
    private val port = port ?: 8080
    val host = if (inetAddress == "" && domain == "")
        "10.0.0.101"
    else
        if (inetAddress != "")
            inetAddress
        else
            domain

    private val retrofit: Retrofit = Retrofit.Builder()
        .baseUrl("http://$host:${this.port}/term/")
        .addConverterFactory(GsonConverterFactory.create())
        .build()

    private val api: WebClientService = retrofit.create(WebClientService::class.java)

    fun getApi() = api
}

interface WebClientService {
    @GET("auth")
    fun auth(
        @Query("username") username: String,
        @Query("password") password: String
    ): Call<AuthResponse?>

    @GET("ls")
    fun ls(
        @Header("Authorization") token: String
    ): Call<List<String>>

    // Возвращает путь после смены директории
    @GET("cd")
    fun cd(
        @Query("dir") dir: String?,
        @Header("Authorization") token: String
    ): Call<CdRes?>

    @GET("who")
    fun who(
        @Header("Authorization") token: String
    ): Call<Map<String,String>?>

    @POST("kill")
    fun kill(
        @Query("username") user: String?,
        @Header("Authorization") token: String
    ): Call<ResponseBody>

    @POST("logout")
    fun logout(
        @Header("Authorization") token: String
    ): Call<ResponseBody>

}

data class AuthResponse(
    val token: String,
    val currentDir: String
)

data class CdRes(
    val path: String
)