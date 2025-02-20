const val STATISTICS_BUTTON = "statistics_clicked"
const val LEARNING_BUTTON = "learn_words_clicked"

fun main(args: Array<String>) {

    val botToken = args[0]
    var updateId = 0

    val telegramBotService = TelegramBotService(botToken)
    val trainer = LearnWordsTrainer()

    val messageIdRegex = "\"update_id\":\\s*(\\d+)".toRegex()
    val messageChatIdRegex = "\"chat\":\\s*\\{[^}]*\"id\":\\s*(\\d+)".toRegex()
    val messageTextRegex = "\"text\":\\s*\"([^\"]*)\"".toRegex()
    val messageDataRegex = "\"data\":\\s*\"([^\"]*)\"".toRegex()

    while (true) {
        Thread.sleep(2000)
        val updates: String = telegramBotService.getUpdates(updateId)
        println(updates)

        val matchResult = messageIdRegex.find(updates)
        val groups = matchResult?.groups
        val updateIdResult = groups?.get(1)?.value?.toIntOrNull() ?: continue

        updateId = updateIdResult + 1

        val chatIdGroups = messageChatIdRegex.find(updates)?.groups
        val chatId = chatIdGroups?.get(1)?.value?.toLongOrNull() ?: continue
        val message = messageTextRegex.find(updates)?.groups?.get(1)?.value
        val data = messageDataRegex.find(updates)?.groups?.get(1)?.value

        when {
            message?.lowercase() == "hello" -> {
                telegramBotService.sendMessage(chatId, message)
                println(message)
            }

            message?.lowercase() == "/start" -> {
                telegramBotService.sendMenu(chatId)
                println(message)
            }

            data?.lowercase() == STATISTICS_BUTTON -> {
                val statistics = trainer.getStatistics()
                telegramBotService.sendMessage(
                    chatId,
                    "Выучено ${statistics.learnedCount} из ${statistics.totalCount} | ${statistics.percent}%"
                )
                println("Statistics button clicked")
            }

            data?.lowercase() == LEARNING_BUTTON -> {
                checkNextQuestionAndSend(trainer, telegramBotService, chatId)
                println("Learning button clicked")
            }

            data?.startsWith(CALLBACK_DATA_ANSWER_PREFIX) == true -> {
                val userAnswerIndex = data.substringAfter(CALLBACK_DATA_ANSWER_PREFIX).toIntOrNull()
                val isCorrect = trainer.checkAnswer(userAnswerIndex)
                if (isCorrect) {
                    telegramBotService.sendMessage(chatId, "Правильно!")
                } else {
                    val correctAnswer = trainer.question?.correctAnswer
                    telegramBotService.sendMessage(
                        chatId,
                        "Неправильно! ${correctAnswer?.original} - это ${correctAnswer?.translate}"
                    )
                }
                checkNextQuestionAndSend(trainer, telegramBotService, chatId)
            }
        }
    }
}

fun checkNextQuestionAndSend(
    trainer: LearnWordsTrainer,
    telegramBotService: TelegramBotService,
    chatId: Long
) {
    val nextQuestion = trainer.getNextQuestion()
    if (nextQuestion == null) {
        telegramBotService.sendMessage(chatId, "Все слова в словаре выучены.")
    } else {
        telegramBotService.sendQuestion(chatId, nextQuestion)
    }
}

