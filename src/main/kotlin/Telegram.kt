import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.IOException

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

    val botToken = args[0]
    var lastUpdateId = 0L

    val json = Json { ignoreUnknownKeys = true }
    val trainers = HashMap<Long, LearnWordsTrainer>()

    val telegramBotService = TelegramBotService(botToken)

    while (true) {
        try {
            Thread.sleep(2000)
            val responseString: String = telegramBotService.getUpdates(lastUpdateId)
            println(responseString)

            val response: Response = json.decodeFromString(responseString)
            if (response.result.isEmpty()) continue
            val sortedUpdates = response.result.sortedBy { it.updateId }
            sortedUpdates.forEach { handleUpdate(it, json, telegramBotService, trainers) }
            lastUpdateId = sortedUpdates.last().updateId + 1
        } catch (e: Exception) {
            println("Ошибка при получении обновлений: ${e.message}. Повторная попытка через 5 секунд.")
            Thread.sleep(5000)
        } catch (e: Exception) {
            println("Произошла ошибка: ${e.message}. Повторная попытка через 5 секунд.")
            Thread.sleep(5000)
        }
    }
}

fun handleUpdate(
    update: Update,
    json: Json,
    telegramBotService: TelegramBotService,
    trainers: HashMap<Long, LearnWordsTrainer>
) {
    val message = update.message?.text
    val chatId = update.message?.chat?.id ?: update.callbackQuery?.message?.chat?.id ?: return
    val data = update.callbackQuery?.data

    val trainer = trainers.getOrPut(chatId) { LearnWordsTrainer("$chatId.txt") }

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

