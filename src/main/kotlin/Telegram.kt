import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.File
import java.sql.DriverManager

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
data class Response(
    @SerialName("result")
    val result: List<Update>,
)

@Serializable
data class Message(
    @SerialName("text")
    val text: String? = null,
    @SerialName("chat")
    val chat: Chat,
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

fun main(args: Array<String>) {
    val connection = DriverManager.getConnection("jdbc:sqlite:data.db?busy_timeout=5000")
    connection.use {
        updateDictionary(File("words.txt"), connection)
        val dictionary = DatabaseUserDictionary(connection)
        val trainer = LearnWordsTrainer(dictionary)

        val botToken = args[0]
        var lastUpdateId = 0L

        val json = Json { ignoreUnknownKeys = true }
        val trainers = HashMap<Long, LearnWordsTrainer>()

        val telegramBotService = TelegramBotService(botToken)

        while (true) {
            Thread.sleep(2000)
            val responseString: String = telegramBotService.getUpdates(lastUpdateId)
            println(responseString)

            val response: Response = json.decodeFromString(responseString)
            if (response.result.isEmpty()) continue
            val sortedUpdates = response.result.sortedBy { it.updateId }
            sortedUpdates.forEach {
                handleUpdate(
                    update = it,
                    json = json,
                    telegramBotService = telegramBotService,
                    trainers = trainers,
                    dictionary = dictionary,

                    )
            }
            lastUpdateId = sortedUpdates.last().updateId + 1
        }
    }
}

fun handleUpdate(
    update: Update,
    json: Json,
    telegramBotService: TelegramBotService,
    trainers: HashMap<Long, LearnWordsTrainer>,
    dictionary: IUserDictionary,
) {
    val message = update.message?.text
    val chatId = update.message?.chat?.id ?: update.callbackQuery?.message?.chat?.id ?: return
    val data = update.callbackQuery?.data

    if(dictionary is DatabaseUserDictionary) {
        dictionary.setCurrentChatId(chatId)
    }

    try {
        dictionary.addUserIfNotExist(chatId)
    } catch (e: Exception) {
        println("Ошибка при добавлении пользователя: ${e.message}")
        return
    }

    val trainer = trainers.getOrPut(chatId) { LearnWordsTrainer(dictionary) }

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
            if (isCorrect) {
                telegramBotService.sendMessage(json, chatId, "Правильно!")
            } else {
                val correctAnswer = trainer.question?.correctAnswer
                telegramBotService.sendMessage(
                    json,
                    chatId,
                    "Неправильно! ${correctAnswer?.original} - это ${correctAnswer?.translate}"
                )
            }
            checkNextQuestionAndSend(json, trainer, telegramBotService, chatId)
        }
    }
}

fun checkNextQuestionAndSend(
    json: Json,
    trainer: LearnWordsTrainer,
    telegramBotService: TelegramBotService,
    chatId: Long
) {
    val nextQuestion = trainer.getNextQuestion()
    if (nextQuestion == null) {
        telegramBotService.sendMessage(json, chatId, "Все слова в словаре выучены.")
    } else {
        telegramBotService.sendQuestion(json, chatId, nextQuestion)
    }
}

