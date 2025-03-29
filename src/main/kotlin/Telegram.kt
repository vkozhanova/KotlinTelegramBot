import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.File
import java.io.IOException
import java.sql.Connection
import java.sql.DriverManager
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

@Serializable
data class Update(
    @SerialName("update_id")
    val updateId: Long,
    @SerialName("message")
    val message: Message? = null,
    @SerialName("callback_query")
    val callbackQuery: CallbackQuery? = null,
)


@Serializable
data class EditMessageTextRequest(
    @SerialName("chat_id")
    val chatId: Long,
    @SerialName("message_id")
    val messageId: Long,
    @SerialName("text")
    val text: String,
    @SerialName("reply_markup")
    val replyMarkup: ReplyMarkup? = null,
)

@Serializable
data class Response(
    @SerialName("result")
    val result: List<Update>,
)

@Serializable
data class GetFileRequest(
    @SerialName("file_id")
    val fileId: String,
)

@Serializable
data class GetFileResponse(
    @SerialName("ok")
    val ok: Boolean,
    @SerialName("result")
    val result: TelegramFile? = null,
)

@Serializable
data class TelegramFile(
    @SerialName("file_id")
    val fileId: String,
    @SerialName("file_unique_id")
    val fileUniqueId: String,
    @SerialName("file_size")
    val fileSize: Long,
    @SerialName("file_path")
    val filePath: String,
)

@Serializable
data class Document(
    @SerialName("file_name")
    val fileName: String,
    @SerialName("mime_type")
    val mimeName: String,
    @SerialName("file_id")
    val fileId: String,
    @SerialName("file_unique_id")
    val fileUniqueId: String,
    @SerialName("file_size")
    val fileSize: Long,
)

@Serializable
data class Message(
    @SerialName("message_id")
    val messageId: Long,
    @SerialName("text")
    val text: String? = null,
    @SerialName("chat")
    val chat: Chat,
    @SerialName("document")
    val document: Document? = null,
)

@Serializable
data class CallbackQuery(
    @SerialName("data")
    val data: String? = null,
    @SerialName("message")
    val message: Message? = null,
)

@Serializable
data class Chat(
    @SerialName("id")
    val id: Long,
)

@Serializable
data class SendMessageRequest(
    @SerialName("chat_id")
    val chatId: Long,
    @SerialName("text")
    val text: String,
    @SerialName("reply_markup")
    val replyMarkup: ReplyMarkup? = null,
)

@Serializable
data class SentMessage(
    @SerialName("message_id")
    val messageId: Long,
    @SerialName("chat")
    val chat: Chat,
)

@Serializable
data class SendMessageResponse(
    @SerialName("ok")
    val ok: Boolean,
    @SerialName("result")
    val result: SentMessage? = null
)

@Serializable
data class ReplyMarkup(
    @SerialName("inline_keyboard")
    val inlineKeyboard: List<List<InlineKeyBoard>>,
)

@Serializable
data class InlineKeyBoard(
    @SerialName("callback_data")
    val callbackData: String,
    @SerialName("text")
    val text: String,
)

@Serializable
data class SendStickerRequest(
    @SerialName("chat_id") val chatId: Long,
    @SerialName("sticker") val sticker: String
)

private const val TWELVE_HOURS_MILLIS = 12 * 60 * 60 * 1000L // 12 часов в миллисекундах
private const val MAIN_LOOP_SLEEP_MS = 2000L
private const val MAX_DELETE_ATTEMPTS = 3
private const val DOWNLOADS_DIR = "downloads"
private const val STICKER_COUNT = 1

fun main(args: Array<String>) {
    Class.forName("org.sqlite.JDBC")
    val connection = DriverManager.getConnection("jdbc:sqlite:new_data.db?busy_timeout=5000")
    connection.use {
        initializeDatabase(connection)
        val dictionary = DatabaseUserDictionary(connection)

        val trainer = LearnWordsTrainer(dictionary)
//        dictionary.deleteWordsAndRelatedDataByIdRange(0, 0)

        val scheduler = Executors.newScheduledThreadPool(1)
        scheduler.scheduleAtFixedRate({
            println("Начинаю очистку файлов...")
            val downloadsDir = File(DOWNLOADS_DIR).apply { mkdirs() }
            downloadsDir.listFiles()?.forEach { file ->
                if (file.lastModified() < System.currentTimeMillis() - TWELVE_HOURS_MILLIS) {
                    deleteFileWithRetry(file)
                }
            }
        }, 0, 1, TimeUnit.HOURS)

        val botToken = args[0]
        var lastUpdateId = 0L

        val json = Json { ignoreUnknownKeys = true }
        val trainers = HashMap<Long, LearnWordsTrainer>()

        val telegramBotService = TelegramBotService(botToken)

        while (true) {
            Thread.sleep(MAIN_LOOP_SLEEP_MS)
            try {
                val responseString = telegramBotService.getUpdates(lastUpdateId)
                val response: Response = json.decodeFromString(responseString)

                if (response.result.isEmpty()) continue

                response.result
                    .sortedBy { it.updateId }
                    .forEach { update ->
                        handleUpdate(
                            update = update,
                            json = json,
                            telegramBotService = telegramBotService,
                            trainers = trainers,
                            dictionary = dictionary,
                            connection = connection
                        )
                        lastUpdateId = update.updateId + 1
                    }
            } catch (e: Exception) {
                println("Ошибка обработки обновлений: ${e.message}")
            }
        }
    }
}

fun handleUpdate(
    update: Update,
    json: Json,
    telegramBotService: TelegramBotService,
    trainers: HashMap<Long, LearnWordsTrainer>,
    dictionary: IUserDictionary,
    connection: Connection,
) {
    val message = update.message?.text
    val chatId = update.message?.chat?.id ?: update.callbackQuery?.message?.chat?.id ?: return
    val data = update.callbackQuery?.data
    println("Обработка обновления для chatId: $chatId, message: $message, data: $data")

    if (dictionary is DatabaseUserDictionary) {
        dictionary.setCurrentChatId(chatId)
    }

    try {
        dictionary.addUserIfNotExist(chatId)
    } catch (e: Exception) {
        println("Ошибка при добавлении пользователя: ${e.message}")
        return
    }

    val trainer = trainers.getOrPut(chatId) { LearnWordsTrainer(dictionary) }

    update.message?.document?.let { document ->
        val jsonResponse = telegramBotService.getFile(document.fileId, json)
        jsonResponse?.let {
            val response: GetFileResponse = json.decodeFromString(it)
            response.result?.let { file ->
                val downloadsDir = File("downloads").apply { mkdirs() }
                val fileName = "${downloadsDir.absolutePath}/${file.fileUniqueId}_${document.fileName}"
                telegramBotService.downloadFile(file.filePath, fileName)
                println("Файл сохранен в: $fileName")
                try {
                    val words = File(fileName).bufferedReader().use { it.readLines() }
                    updateDictionary(File(fileName), connection)
                    telegramBotService.sendMessage(json, chatId, "Добавлено ${words.size} слов!")
                } catch (e: Exception) {
                    telegramBotService.sendMessage(json, chatId, "Ошибка обработки файла: ${e.message}")
                } finally {
                    deleteFileWithRetry(File(fileName))
                }

            }
        }
    }

    when {
        message?.lowercase() == "hello" -> {
            telegramBotService.sendMessage(json, chatId, message)
            println(message)
        }

        message?.lowercase() == "/start" -> {
            telegramBotService.sendMenu(json, chatId)
            println(message)
        }

        data?.lowercase() == STATISTICS_BUTTON -> {
            val statistics = trainer.getStatistics()
            telegramBotService.sendMessage(
                json,
                chatId,
                "Выучено ${statistics.learnedCount} из ${statistics.totalCount} | ${statistics.percent}%"
            )
            println("Statistics button clicked")
        }

        data?.lowercase() == RESET_BUTTON -> {
            trainer.resetProgress()
            telegramBotService.sendMessage(json, chatId, "Прогресс сброшен!")
            println("Reset button clicked")
        }

        data?.lowercase() == LEARNING_BUTTON -> {
            checkNextQuestionAndSend(json, trainer, telegramBotService, chatId)
            println("Learning button clicked")
        }

        data?.startsWith(CALLBACK_DATA_ANSWER_PREFIX) == true -> {
            val userAnswerIndex = data.substringAfter(CALLBACK_DATA_ANSWER_PREFIX).toIntOrNull()
            val isCorrect = trainer.checkAnswer(userAnswerIndex)
            val chatId = update.callbackQuery.message?.chat?.id ?: return
            val messageId = update.callbackQuery.message.messageId ?: return

            val responseText = if (isCorrect) {
                "✅ Правильно!"
            } else {
                val correctAnswer = trainer.question?.correctAnswer
                "❌ Неправильно! ${correctAnswer?.original} - это ${correctAnswer?.translate}."
            }

            telegramBotService.editMessage(
                json = json,
                chatId = chatId,
                messageId = messageId,
                text = responseText
            )

            if (isCorrect) {
                val statistics = trainer.getStatistics()
                val currentThreshold = dictionary.getStickerThreshold(chatId)
                if (statistics.learnedCount >= currentThreshold + STICKER_COUNT) {
                    val newThreshold = currentThreshold + STICKER_COUNT
                    dictionary.updateStickerThreshold(chatId, newThreshold)

                    telegramBotService.sendSticker(json, chatId)
                    telegramBotService.sendMessage(json, chatId, "Поздравляю! Вы выучили новое слово!")

                    Thread.sleep(1500)
                    checkNextQuestionAndSend(
                        json = json,
                        trainer = trainer,
                        telegramBotService = telegramBotService,
                        chatId = chatId,
                    )
                    return
                }
            }
            Thread.sleep(2000)
            checkNextQuestionAndSend(
                json = json,
                trainer = trainer,
                telegramBotService = telegramBotService,
                chatId = chatId,
                editMessageId = messageId,
            )
        }
    }
}

fun deleteFileWithRetry(file: File, maxAttempts: Int = MAX_DELETE_ATTEMPTS) {
    var attempts = 0
    while (attempts < maxAttempts) {
        try {
            if (file.delete()) {
                println("Файл ${file.name} успешно удален")
                return
            }
        } catch (e: IOException) {
            println("Ошибка удаления (попытка ${attempts + 1}): ${e.message}")
            Thread.sleep(500)
        }
        attempts++
    }
    println("Ошибка: не удалось удалить файл после $maxAttempts попыток: ${file.absolutePath}")

}

fun checkNextQuestionAndSend(
    json: Json,
    trainer: LearnWordsTrainer,
    telegramBotService: TelegramBotService,
    chatId: Long,
    editMessageId: Long? = null
) {
    val nextQuestion = trainer.getNextQuestion()
    if (nextQuestion == null) {
        telegramBotService.sendMessage(json, chatId, "Все слова в словаре выучены.")
    } else {
        telegramBotService.sendQuestion(
            json = json,
            chatId = chatId,
            question = nextQuestion,
            editMessageId = editMessageId
        )
    }
}

