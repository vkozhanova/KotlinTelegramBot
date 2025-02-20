import java.net.URI
import java.net.URLEncoder
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.nio.charset.StandardCharsets

const val BASE_URL = "https://api.telegram.org/bot"
const val CALLBACK_DATA_ANSWER_PREFIX = "answer_"

class TelegramBotService(private val botToken: String) {

    private val client = HttpClient.newBuilder().build()

    fun getUpdates(updateId: Int): String {
        val urlGetUpdates = "$BASE_URL$botToken/getUpdates?offset=$updateId"
        val request: HttpRequest = HttpRequest.newBuilder().uri(URI.create(urlGetUpdates)).build()
        val response: HttpResponse<String> = client.send(request, HttpResponse.BodyHandlers.ofString())

        return response.body()
    }

    fun sendMessage(chatId: Long, message: String) {
        val encoded = URLEncoder.encode(
            message,
            StandardCharsets.UTF_8
        )
        println(encoded)
        val sendMessage = "$BASE_URL$botToken/sendMessage?chat_id=$chatId&text=$encoded"
        val request: HttpRequest = HttpRequest.newBuilder().uri(URI.create(sendMessage)).build()
        client.send(request, HttpResponse.BodyHandlers.ofString())
    }

    fun sendMenu(chatId: Long): String {
        val sendMessage = "$BASE_URL$botToken/sendMessage"
        val sendMenuBody = """
            {
                "chat_id": $chatId,
                "text": "Основное меню",
                "reply_markup": {
                    "inline_keyboard": [
                        [
                            {
                                "text": "Изучить слова",
                                "callback_data": "learn_words_clicked"
                            },
                            {
                                "text": "Статистика",
                                "callback_data": "statistics_clicked"
                            }
                        ]
                    ]
                }
            }
        """.trimIndent()

        val client: HttpClient = HttpClient.newBuilder().build()
        val request: HttpRequest = HttpRequest.newBuilder().uri(URI.create(sendMessage))
            .header("Content-type", "application/json")
            .POST(HttpRequest.BodyPublishers.ofString(sendMenuBody))
            .build()
        val response: HttpResponse<String> = client.send(request, HttpResponse.BodyHandlers.ofString())
        return response.body()
    }

    fun sendQuestion(chatId: Long, question: Question): String? {
        val keyboard = question.variants.mapIndexed { index, word ->
            """
              {
               "text": "${word.translate}" ,
                    "callback_data": "$CALLBACK_DATA_ANSWER_PREFIX$index"
              }"""
        }.joinToString(separator = ",", prefix = "[", postfix = "]")
             val messageBody = """
                 {
            "chat_id": $chatId,
            "text": "Переведите слово: ${question.correctAnswer.original}",
            "reply_markup": {
            "inline_keyboard": [$keyboard]
            }
            } 
             """.trimIndent()
        val request: HttpRequest = HttpRequest.newBuilder()
            .uri(URI.create("$BASE_URL$botToken/sendMessage"))
            .header("Content-type", "application/json")
            .POST(HttpRequest.BodyPublishers.ofString(messageBody))
            .build()
        return client.send(request, HttpResponse.BodyHandlers.ofString()).body()
    }
}