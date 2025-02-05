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
        println(updates)

        val messageIdRegex = "\"update_id\":\\s*(\\d+)".toRegex()
        val matchResult = messageIdRegex.find(updates)
        val groups = matchResult?.groups
        val updateIdResult = groups?.get(1)?.value?.toIntOrNull()

        if (updateIdResult != null) {
            updateId = updateIdResult + 1
            println("Update Id: $updateIdResult")
        } else {
            println("нет ответа")
            continue
        }

        val messageFirstNameRegex = "\"first_name\":\\s*\"([\\w]+)\"".toRegex()
        val matchResultNames = messageFirstNameRegex.find(updates)
        val namesGroups = matchResultNames?.groups
        val firstName = namesGroups?.get(1)?.value
        println("First name: $firstName")

        val messageLastNameRegex = "\"last_name\":\\s*\"([\\w]+)\"".toRegex()
        val matchResultLastNames = messageLastNameRegex.find(updates)
        val lastNamesGroups = matchResultLastNames?.groups
        val lastName = lastNamesGroups?.get(1)?.value
        println("Last name: $lastName")

        val messageUsernameRegex = "\"username\":*\"([\\w\\d]+)\"".toRegex()
        val matchResultUsernames = messageUsernameRegex.find(updates)
        val usernamesGroups = matchResultUsernames?.groups
        val username = usernamesGroups?.get(1)?.value
        println("Username: $username")

        val messageChatIdRegex = "\"chat\":\\s*\\{[^}]*\"id\":\\s*(\\d+)".toRegex()
        val matchResultChatId = messageChatIdRegex.find(updates)
        val chatIdGroups = matchResultChatId?.groups
        val chatId = chatIdGroups?.get(1)?.value
        println("Chat Id: $chatId")

        val messageTextRegex = "\"text\":\\s*\"([^\"]*)\"".toRegex()
        val matchResultText = messageTextRegex.find(updates)
        val textGroups = matchResultText?.groups
        val text = textGroups?.get(1)?.value
        println("Text: $text")

        val messageDateRegex = "\"date\":\\s*(\\d+)".toRegex()
        val matchResultDate = messageDateRegex.find(updates)
        val dataGroups = matchResultDate?.groups
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
