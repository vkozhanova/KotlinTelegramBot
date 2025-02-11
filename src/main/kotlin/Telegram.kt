fun main(args: Array<String>) {

    val botToken = args[0]
    var updateId = 0

    val messageIdRegex = "\"update_id\":\\s*(\\d+)".toRegex()
    val messageChatIdRegex = "\"chat\":\\s*\\{[^}]*\"id\":\\s*(\\d+)".toRegex()
    val messageTextRegex = "\"text\":\\s*\"([^\"]*)\"".toRegex()
    val messageDataRegex = "\"data\":\\s*\"([^\"]*)\"".toRegex()

    val telegramBotService = TelegramBotService(botToken)
    val trainer = LearnWordsTrainer()

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

        if (message != null) {
            if (message.lowercase() == "hello") {
                telegramBotService.sendMessage(chatId, message)
                println(message)
            }
            if (message.lowercase() == "/start") {
                telegramBotService.sendMenu(chatId)
                println(message)
            }
        }

        if (data?.lowercase() == "statistics_clicked") {
            val statistics = trainer.getStatistics()
            telegramBotService.sendMessage(chatId, "Выучено ${statistics.learnedCount} из ${statistics.totalCount} | ${statistics.percent}%")
            println("Statistics button clicked")

        }
        if (data?.lowercase() == "learn_words_clicked") {
            telegramBotService.sendMessage(chatId, "Изучение слов")
            println("Learning button clicked")

        }
    }
}


