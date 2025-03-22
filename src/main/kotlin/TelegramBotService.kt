import kotlinx.serialization.json.Json
import java.io.File
import java.io.IOException
import java.io.InputStream
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse

const val BASE_URL = "https://api.telegram.org/bot"
const val BOT_FILE_URL = "https://api.telegram.org/file/bot"
const val CALLBACK_DATA_ANSWER_PREFIX = "answer_"
const val STATISTICS_BUTTON = "statistics_clicked"
const val LEARNING_BUTTON = "learn_words_clicked"
const val RESET_BUTTON = "reset_clicked"

class TelegramBotService(private val botToken: String) {

    private val client = HttpClient.newBuilder()
        .version(HttpClient.Version.HTTP_1_1) // Используем HTTP/1.1
        .build()

    fun getUpdates(updateId: Long): String {

        val urlGetUpdates = "$BASE_URL$botToken/getUpdates?offset=$updateId"
        val request: HttpRequest = HttpRequest.newBuilder().uri(URI.create(urlGetUpdates)).build()

        return try {
            println("Отправка запроса к API: $urlGetUpdates")
            val response: HttpResponse<String> = client.send(request, HttpResponse.BodyHandlers.ofString())
            response.body()
        } catch (e: IOException) {
            println("Ошибка при получении обновлений: ${e.message}")
            Thread.sleep(5000)
            getUpdates(updateId)
        }
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

        return try {
            println("Отправка сообщения: $message")
            val response: HttpResponse<String> = client.send(request, HttpResponse.BodyHandlers.ofString())
            response.body()
        } catch (e: IOException) {
            println("Ошибка при отправки сообщения: ${e.message}")
            Thread.sleep(5000)
            sendMessage(json, chatId, message)
        }
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

        return try {
            println("Отправка меню")
            val response: HttpResponse<String> = client.send(request, HttpResponse.BodyHandlers.ofString())
            response.body()
        } catch (e: IOException) {
            println("Ошибка при отправке меню: ${e.message}")
            Thread.sleep(5000)
            sendMenu(json, chatId)
        }
    }

    fun sendQuestion(json: Json, chatId: Long, question: Question): String? {
        val buttonsInColumn = question.variants.mapIndexed { index, word ->
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

        return try {
            println("Отправка запроса")
            client.send(request, HttpResponse.BodyHandlers.ofString()).body()
        } catch (e: IOException) {
            println("Ошибка при отправке вопроса: ${e.message}")
            Thread.sleep(5000)
            sendQuestion(json, chatId, question)
        }
    }

    fun getFile(fileId: String, json: Json): String? {
        val urlGetFile = "$BASE_URL$botToken/getFile"
        val requestBody = GetFileRequest(fileId = fileId)
        val requestBodyString = json.encodeToString(requestBody)
        val request: HttpRequest? = HttpRequest.newBuilder()
            .uri(URI.create(urlGetFile))
            .header("Content-type", "application/json")
            .POST(HttpRequest.BodyPublishers.ofString(requestBodyString))
            .build()

        return try {
            println("Отправка запроса на получение файла: $fileId")
            val response: HttpResponse<String> = client.send(request, HttpResponse.BodyHandlers.ofString())
            if (response.statusCode() == 200) {
                response.body()
            } else {
                println("Ошибка при получении файла. Код ответа:${response.statusCode()}, тело ответа: ${response.body()}")
                null
            }
        } catch (e: IOException) {
            println("Ошибка при получении файла: ${e.message}")
            null
        }
    }

    fun downloadFile(filePath: String, fileName: String) {
        val urlGetFile = "$BOT_FILE_URL$botToken/$filePath"
        println("Скачивание файла по URL: $urlGetFile")

        val request = HttpRequest.newBuilder()
            .uri(URI.create(urlGetFile))
            .GET()
            .build()

        val response: HttpResponse<InputStream> = HttpClient.newHttpClient()
            .send(request, HttpResponse.BodyHandlers.ofInputStream())

        println("Статус код: ${response.statusCode()}")
        val body: InputStream = response.body()
        body.copyTo(File(fileName).outputStream(), 16 * 1024)
        println("Файл успешно скачан: $fileName")

    }
}