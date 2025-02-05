import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse

fun main(args:Array<String>) {

    val botToken = args[0]
    var updateId = 0

    val messageChatIdRegex = "\"chat\":\\s*\\{[^}]*\"id\":\\s*(\\d+)".toRegex()
    val messageIdRegex = "\"update_id\":\\s*(\\d+)".toRegex()
    val messageFirstNameRegex = "\"first_name\":\\s*\"([\\w]+)\"".toRegex()
    val messageLastNameRegex = "\"last_name\":\\s*\"([\\w]+)\"".toRegex()
    val messageUsernameRegex = "\"username\":*\"([\\w\\d]+)\"".toRegex()
    val messageTextRegex = "\"text\":\\s*\"([^\"]*)\"".toRegex()
    val messageDateRegex = "\"date\":\\s*(\\d+)".toRegex()

    while (true) {
        Thread.sleep(2000)
        val updates: String = getUpdates(botToken, updateId)
        println(updates)

        val matchResult = messageIdRegex.find(updates)
        val groups = matchResult?.groups
        val updateIdResult = groups?.get(1)?.value?.toIntOrNull()?.plus(1) ?: continue

        updateId = updateIdResult
        println("Update Id: $updateIdResult")

        val namesGroups = messageFirstNameRegex.find(updates)?.groups
        val firstName = namesGroups?.get(1)?.value
        println("First name: $firstName")

        val lastNamesGroups = messageLastNameRegex.find(updates)?.groups
        val lastName = lastNamesGroups?.get(1)?.value
        println("Last name: $lastName")

        val usernamesGroups = messageUsernameRegex.find(updates)?.groups
        val username = usernamesGroups?.get(1)?.value
        println("Username: $username")

        val chatIdGroups = messageChatIdRegex.find(updates)?.groups
        val chatId = chatIdGroups?.get(1)?.value
        println("Chat Id: $chatId")

        val textGroups = messageTextRegex.find(updates)?.groups
        val text = textGroups?.get(1)?.value
        println("Text: $text")


        val dataGroups = messageDateRegex.find(updates)?.groups
        val date = dataGroups?.get(1)?.value
        println("Date: $date")
    }
}

fun getUpdates(botToken: String, updateId: Int): String {
    val urlGetUpdates = "https://api.telegram.org/bot$botToken/getUpdates?offset=$updateId"
    val client: HttpClient = HttpClient.newBuilder().build()
    val request: HttpRequest = HttpRequest.newBuilder().uri(URI.create(urlGetUpdates)).build()
    val response: HttpResponse<String> = client.send(request, HttpResponse.BodyHandlers.ofString())

    return response.body()
}
