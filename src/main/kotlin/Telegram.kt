import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse

fun main(args:Array<String>) {

    val botToken = args[0]
    var updateId = 0

    while (true) {
        Thread.sleep(2000)
        val updates: String = getUpdates(botToken, updateId)

        val startUpdateId = updates.lastIndexOf("update_id")
        val endUpdateId = updates.lastIndexOf(",\n\"message\"")

        if (startUpdateId == -1 || endUpdateId == -1) continue
        val updateIdString = updates.substring(startUpdateId + 11, endUpdateId).trim()
        updateId = updateIdString.toInt() + 1

        val startMessageIndex = updates.lastIndexOf("message_id\":") + 12
        val endMessageId = updates.indexOf(",", startMessageIndex)
        val messageId = updates.substring(startMessageIndex, endMessageId).toInt()

        val startName = updates.lastIndexOf("first_name\":\"") + 13
        val endName = updates.indexOf("\"", startName)
        val name = updates.substring(startName, endName)

        val startLastName = updates.indexOf("last_name\":") + 12
        val endLastName = updates.indexOf("\"", startLastName)
        val lastName = updates.substring(startLastName, endLastName)

        val startUserName = updates.indexOf("username\":") + 11
        val endUserName = updates.indexOf("\"", startUserName)
        val userName = updates.substring(startUserName, endUserName)

        val startText = updates.lastIndexOf("\"text\":") + 8
        val endText = updates.indexOf("\"", startText)
        val text = updates.substring(startText, endText)

        val startDate = updates.lastIndexOf("date\":") + 6
        val endDate = updates.indexOf(",", startDate)
        val date = updates.substring(startDate, endDate)

        println("First name: $name\n" +
                "Last name: $lastName\n" +
                "User name: $userName\n" +
                "Message text: $text\n" +
                "Message id: $messageId\n" +
                "Date: $date\n" +
               "Update id: $updateId\n")

    }
}

fun getUpdates(botToken: String, updateId: Int): String {
    val urlGetUpdates = "https://api.telegram.org/bot$botToken/getUpdates?offset=$updateId"
    val client: HttpClient = HttpClient.newBuilder().build()
    val request: HttpRequest = HttpRequest.newBuilder().uri(URI.create(urlGetUpdates)).build()
    val response: HttpResponse<String> = client.send(request, HttpResponse.BodyHandlers.ofString())

    return response.body()
}
