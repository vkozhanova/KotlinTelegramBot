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
        .version(HttpClient.Version.HTTP_1_1)
        .build()

    private val lastMessageIds = mutableMapOf<Long, Long>()


    fun sendSticker(json: Json, chatId: Long): String? {
        val stickerIds = listOf(
            "CAACAgIAAxkBAAEN5qFnvkJsTvE6oG8pnt6DksPjQXxvpAACNQADrWW8FPWlcVzFMOXgNgQ",
            "CAACAgIAAxkBAAEOLR1n5tj0_Yoh1Vzm9Q2FWw7Bh7LwXQACmwADO2AkFOBQ1OzxDlN9NgQ",
            "CAACAgIAAxkBAAEOLR9n5tkqIDrzjRhOYPSoV-7MEfaPhgACHQADwDZPE17YptxBPd5INgQ",
            "CAACAgIAAxkBAAEOLSFn5tpM8tPteR0uIEU9G0r5omJbOwACTgADrWW8FCFszl8rK9s8NgQ",
            "CAACAgIAAxkBAAEN5pFnvkJIBazom_GqZC8J7FhU-uGCIQACKQADJHFiGiKockiM5SMwNgQ",
            "CAACAgIAAxkBAAEOLSVn5trCTbVoW-sMwYX6CDHn7dCz6gAC9wAD9wLID9CX3j-K0TwONgQ",
            "CAACAgIAAxkBAAEOLSdn5tvN5hwjcZzo-xqX2vLI9JyCVgACQAEAAlKJkSM2vVjaS6yQ6DYE",
            "CAACAgIAAxkBAAEOLSln5tvvC9LvXZfol_SXtiHcBOUGqgACDwMAArrAlQVTx-VZjNIcsjYE",
            "CAACAgIAAxkBAAEOLS5n5tyIWWEwr79l4f-ibS-7i3WmtgAC5gUAAj-VzAoNpAABeLxMTco2BA",
            "CAACAgIAAxkBAAEOLTBn5t0EdA7FlkuYZ8HYRnADGfNHUQAC3BEAAtfmiUprmtcmqgFCFDYE",
            "CAACAgIAAxkBAAEOLTJn5t0mvOczpLwEucpe8fUTBFHOyAACIBQAAlqGcUn4IS5DiFwyKTYE",
            "CAACAgIAAxkBAAEOLTRn5t1nEzX61-U-101EsdBbCLCcQAAC0zEAAvG5cUrosxy_mrtczjYE",
            "CAACAgIAAxkBAAEOLTZn5t2Kq_XdY997-cUC9qozU5nqIwACShIAAidQAUqpgnF_W9HvnDYE",
            "CAACAgIAAxkBAAEOLThn5t34dCZSGGygYI9OlnbwK6qUhAACmQUAAj-VzArz4mcjdKvvczYE",
            "CAACAgIAAxkBAAEOLTpn5t4jgFtie983E3wCsuI1K72DTwACDxUAAhcU0EkmuyEN8xzCCTYE",
        )

        val sticker = stickerIds.random()

        val url = "$BASE_URL$botToken/sendSticker"

        return try {
            val requestBody = SendStickerRequest(
                chatId = chatId,
                sticker = sticker
            )
            val request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(json.encodeToString(requestBody)))
                .build()

            client.send(request, HttpResponse.BodyHandlers.ofString()).body()
        } catch (e: Exception) {
            println("Ошибка отправки стикера: ${e.message}")
            null
        }
    }

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

    fun sendMessage(json: Json, chatId: Long, message: String): String? {

        val sendMessage = "$BASE_URL$botToken/sendMessage"
        val requestBody = SendMessageRequest(
            chatId = chatId,
            text = message,
        )

        return try {
            val requestBodyString = json.encodeToString(requestBody)
            val request: HttpRequest = HttpRequest.newBuilder().uri(URI.create(sendMessage))
                .header("Content-type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(requestBodyString))
                .build()
            val response: HttpResponse<String> = client.send(request, HttpResponse.BodyHandlers.ofString())
            val responseBody = response.body()

            json.decodeFromString<SendMessageResponse>(responseBody).result?.let {
                lastMessageIds[chatId] = it.messageId
            }

            responseBody
        } catch (e: Exception) {
            println("Ошибка при отправки сообщения: ${e.message}")
            null
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

    fun sendQuestion(json: Json, chatId: Long, question: Question, editMessageId: Long? = null): String? {
        val buttonsInColumn = question.variants.mapIndexed { index, word ->
            listOf(
                InlineKeyBoard(
                    text = word.translate,
                    callbackData = "$CALLBACK_DATA_ANSWER_PREFIX$index"
                )
            )
        }

        return if (editMessageId != null) {
            editMessage(
                json = json,
                chatId = chatId,
                messageId = editMessageId,
                text = question.correctAnswer.original,
                replyMarkup = ReplyMarkup(buttonsInColumn)
            ).also {
                lastMessageIds[chatId] = editMessageId
            }
        } else {
            val response = sendNewMessageWithQuestion(json, chatId, question)
            json.decodeFromString<SendMessageResponse>(response).result?.messageId?.let {
                lastMessageIds[chatId] = it
            }
            response
        }
    }

    private fun sendNewMessageWithQuestion(
        json: Json,
        chatId: Long,
        question: Question
    ): String {
        val buttons = question.variants.mapIndexed { index, word ->
            listOf(InlineKeyBoard(
                text = word.translate,
                callbackData = "$CALLBACK_DATA_ANSWER_PREFIX$index"
            ))
        }

        val requestBody = SendMessageRequest(
            chatId = chatId,
            text = question.correctAnswer.original,
            replyMarkup = ReplyMarkup(buttons)
        )

        val requestBodyString = json.encodeToString(requestBody)
        val request = HttpRequest.newBuilder()
            .uri(URI.create("$BASE_URL$botToken/sendMessage"))
            .header("Content-Type", "application/json")
            .POST(HttpRequest.BodyPublishers.ofString(requestBodyString))
            .build()

        return client.send(request, HttpResponse.BodyHandlers.ofString()).body()
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

   fun editMessage(
        json: Json,
        chatId: Long,
        messageId: Long,
        text: String,
        replyMarkup: ReplyMarkup? = null
    ): String? {

        val url = "$BASE_URL$botToken/editMessageText"
        val requestBody = EditMessageTextRequest(chatId, messageId, text, replyMarkup)
        val requestBodyString = json.encodeToString(requestBody)

        val request = HttpRequest.newBuilder()
            .uri(URI.create(url))
            .header("Content-Type", "application/json")
            .POST(HttpRequest.BodyPublishers.ofString(requestBodyString))
            .build()

        return try {
            val response = client.send(request, HttpResponse.BodyHandlers.ofString())
            response.body()
        } catch (e: IOException) {
            println("Ошибка при редактировании сообщения: ${e.message}")
            null
        } catch (e: InterruptedException) {
            println("Прервано редактирование сообщения: ${e.message}")
            null
        }
    }
}