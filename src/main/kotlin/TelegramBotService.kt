import kotlinx.serialization.json.Json
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse

const val BASE_URL = "https://api.telegram.org/bot"
const val CALLBACK_DATA_ANSWER_PREFIX = "answer_"
const val STATISTICS_BUTTON = "statistics_clicked"
const val LEARNING_BUTTON = "learn_words_clicked"
const val RESET_BUTTON = "reset_clicked"

class TelegramBotService(private val botToken: String) {

    private val client = HttpClient.newHttpClient()

    fun getUpdates(updateId: Long): String {

        val urlGetUpdates = "$BASE_URL$botToken/getUpdates?offset=$updateId"
        val request: HttpRequest = HttpRequest.newBuilder().uri(URI.create(urlGetUpdates)).build()
        val response: HttpResponse<String> = client.send(request, HttpResponse.BodyHandlers.ofString())

        return response.body()
    }

    fun sendMessage(json: Json, chatId: Long, message: String): String {

        val sendMessage = "$BASE_URL$botToken/sendMessage"
        val requestBody = SendMessageRequest(
            chatId = chatId,
            text = message,
        )
        val requestBodyString = json.encodeToString(requestBody)
        val request: HttpRequest = HttpRequest.newBuilder().uri(URI.create(sendMessage))
            .header("Content-type", "application/json")
            .POST(HttpRequest.BodyPublishers.ofString(requestBodyString))
            .build()
        val response: HttpResponse<String> = client.send(request, HttpResponse.BodyHandlers.ofString())
        return response.body()
    }

    fun sendMenu(json: Json, chatId: Long): String {
        val sendMessage = "$BASE_URL$botToken/sendMessage"
        val requestBody = SendMessageRequest(
            chatId = chatId,
            text = "Основное меню",
            replyMarkup = ReplyMarkup(
                listOf(
                    listOf(
                        InlineKeyBoard(text = "Изучить слова", callbackData = LEARNING_BUTTON),
                        InlineKeyBoard(text = "Статистика", callbackData = STATISTICS_BUTTON),
                    ),
                    listOf(
                        InlineKeyBoard(text = "Сбросить прогресс", callbackData = RESET_BUTTON),
                    )
                )
            )
        )
        val requestBodyString = json.encodeToString(requestBody)
        val request: HttpRequest = HttpRequest.newBuilder().uri(URI.create(sendMessage))
            .header("Content-type", "application/json")
            .POST(HttpRequest.BodyPublishers.ofString(requestBodyString))
            .build()
        val response: HttpResponse<String> = client.send(request, HttpResponse.BodyHandlers.ofString())
        return response.body()
    }

    fun sendQuestion(json: Json, chatId: Long, question: Question): String? {
        val buttonsInColumn = question.variants.mapIndexed{ index, word ->
            listOf(
                InlineKeyBoard(
                    text = word.translate,
                    callbackData = "$CALLBACK_DATA_ANSWER_PREFIX$index"
                )
            )
        }

        val requestBody = SendMessageRequest(
            chatId = chatId,
            text = question.correctAnswer.original,
            replyMarkup = ReplyMarkup(buttonsInColumn)
        )

        val requestBodyString = json.encodeToString(requestBody)
        val request: HttpRequest = HttpRequest.newBuilder()
            .uri(URI.create("$BASE_URL$botToken/sendMessage"))
            .header("Content-type", "application/json")
            .POST(HttpRequest.BodyPublishers.ofString(requestBodyString))
            .build()
        return client.send(request, HttpResponse.BodyHandlers.ofString()).body()
    }
}