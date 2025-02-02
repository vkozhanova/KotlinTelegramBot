import java.io.FileNotFoundException

data class Word(
    val original: String,
    val translate: String,
    var correctAnswersCount: Int = 0
)

fun Question.asConsoleString(): String {
    val variants = this.variants
        .mapIndexed { index: Int, word: Word -> "${index + 1} - ${word.translate}" }
        .joinToString(separator = "\n")
    return "${this.correctAnswer.original}\n$variants\n0 - Выйти в меню"
}

fun main() {

    val trainer = try {
        LearnWordsTrainer()
    } catch (e: FileNotFoundException) {
        println("Невозможно загрузить файл: файл не найден")
        return
    } catch (e: Exception) {
        println("Невозможно загрузить словарь: ${e.message}")
        return
    }

    while (true) {
        println("Меню:")
        println("1 - Учить слова")
        println("2 - Статистика")
        println("0 - Выход")

        when (readln().toIntOrNull()) {
            1 -> {
                println("Учить слова")
                while (true) {
                    val question = trainer.getNextQuestion()
                    if (question == null) {
                        println("Все слова в словаре выучены")
                        break
                    } else {
                        println(question.asConsoleString())

                        val userAnswerInput = readln().toIntOrNull()
                        if (userAnswerInput == 0) break

                        if (userAnswerInput == null || userAnswerInput < 0 || userAnswerInput > question.variants.size) {
                            println("Некорректный ввод. Выберите значение от 1 до ${question.variants.size}")
                            continue
                        }

                        if (trainer.checkAnswer(userAnswerInput.minus(1))) {
                            println("Правильно!")
                        } else {
                            println("Неправильно! ${question.correctAnswer.original} - это ${question.correctAnswer.translate}")
                        }

                    }
                }
            }

            2 -> {
                println("Статистика")
                val statistics = trainer.getStatistics()

                println("Выучено ${statistics.learnedCount} из ${statistics.totalCount} | ${statistics.percent}%")
                println()
            }

            0 -> {
                println("Выход")
                break
            }

            else -> {
                println("Некорректный ввод. Введите число 1, 2 или 0")
                continue
            }
        }
    }

}
