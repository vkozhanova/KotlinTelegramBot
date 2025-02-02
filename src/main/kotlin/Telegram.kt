import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse

fun main(args:Array<String>) {

    val botToken = args[0]
    val urlGetMe = "https://api.telegram.org/bot$botToken/getMe"
    val urlGetUpdates = "https://api.telegram.org/bot$botToken/getUpdates"


    val client: HttpClient = HttpClient.newBuilder().build()
    val request0: HttpRequest = HttpRequest.newBuilder().uri(URI.create(urlGetMe)).build()
    val request1: HttpRequest = HttpRequest.newBuilder().uri(URI.create(urlGetUpdates)).build()

    val response0: HttpResponse<String> = client.send(request0, HttpResponse.BodyHandlers.ofString())
    val response1: HttpResponse<String> = client.send(request1, HttpResponse.BodyHandlers.ofString())
    println(response0.body())
    println(response1.body())

}