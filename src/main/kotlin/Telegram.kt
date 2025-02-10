fun main(args: Array<String>) {

    val botToken = args[0]
    var updateId = 0

    val messageIdRegex = "\"update_id\":\\s*(\\d+)".toRegex()
    val messageChatIdRegex = "\"chat\":\\s*\\{[^}]*\"id\":\\s*(\\d+)".toRegex()
    val messageTextRegex = "\"text\":\\s*\"([^\"]*)\"".toRegex()

    val telegramBotService = TelegramBotService(botToken)

    while (true) {
        Thread.sleep(2000)
        val updates: String = telegramBotService.getUpdates(updateId)
        println(updates)

        val matchResult = messageIdRegex.find(updates)
        val groups = matchResult?.groups
        val updateIdResult = groups?.get(1)?.value?.toIntOrNull()?.plus(1) ?: continue

        updateId = updateIdResult

        val chatIdGroups = messageChatIdRegex.find(updates)?.groups
        val chatId = chatIdGroups?.get(1)?.value?.toLongOrNull() ?: continue

        val textGroups = messageTextRegex.find(updates)?.groups
        val text = textGroups?.get(1)?.value

        if (text != null) {
            if (text.lowercase() == "hello") {
                telegramBotService.sendMessage(chatId, text)
                println(text)
            }
        }
    }
}

