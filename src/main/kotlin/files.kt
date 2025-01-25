import java.io.File

data class Word(
    val original: String,
    val translate: String,
    val correctAnswersCount: Int = 0
)

const val SCORE_LIMIT = 3
const val PERCENT_MULTIPLIER = 100
const val QUESTIONS_OPTIONS = 4

fun main() {

    val dictionary = loadDictionary()

    while (true) {
        println("Меню:")
        println("1 - Учить слова")
        println("2 - Статистика")
        println("0 - Выход")

        val userChoice = readln()
        when (userChoice) {
            "1" -> {
                println("Учить слова")
                learnWords(dictionary)
            }

            "2" -> {
                println("Статистика")
                showStatistics(dictionary)
            }

            "0" -> {
                println("Выход")
                return
            }

            else -> {
                println("Некорректный ввод. Введите число 1, 2 или 0")
                continue
            }
        }
    }
}

fun learnWords(dictionary: MutableList<Word>) {
    while (true) {

        val notLearnedList = dictionary.filter { it.correctAnswersCount < SCORE_LIMIT }
        if (notLearnedList.isEmpty()) {
            println("Все слова в словаре выучены")
            return
        }

        val questionWords = notLearnedList.shuffled().take(QUESTIONS_OPTIONS)

        val correctAnswer = questionWords.random()
        val incorrectAnswers = notLearnedList.filter { it != correctAnswer }.shuffled().take(3)
        val variants = (incorrectAnswers + correctAnswer).shuffled()

        println("${correctAnswer.original}:")
        variants.forEachIndexed { index, word ->
            println("${index + 1} - ${word.translate}")
        }
        println("----------")
        println("0 - Меню")
        println()

        val userAnswerInput = readln().toIntOrNull()
        if (userAnswerInput == null) {
            println("Некорректный ввод. Введите число от 0 до ${variants.size}")
            continue
        }


        when (userAnswerInput) {
            0 -> return
            in 1..variants.size -> {
                val selectedWord = variants[userAnswerInput - 1]
                if (selectedWord.original == correctAnswer.original) {
                    println("Правильно!")
                    dictionary[dictionary.indexOf(correctAnswer)] =
                        correctAnswer.copy(correctAnswersCount = correctAnswer.correctAnswersCount + 1)
                    saveDictionary(dictionary)
                } else {
                    println("Неправильно! ${correctAnswer.original} – это ${correctAnswer.translate}")
                }
            }
            else -> {
                println("Некорректный ввод. Введите число от 0 до ${variants.size}")
            }
        }

    }
}

fun showStatistics(dictionary: List<Word>) {
    val learnedCount = dictionary.filter { it.correctAnswersCount >= SCORE_LIMIT }.size
    val totalCount = dictionary.size
    val percent =
        if (totalCount > 0) (learnedCount.toDouble() / totalCount * PERCENT_MULTIPLIER).toInt() else 0

    println("Выучено $learnedCount из $totalCount | $percent%")
    println()
}

fun loadDictionary(): MutableList<Word> {
    val wordsFile: File = File("words.txt")
    val dictionary = mutableListOf<Word>()

    wordsFile.forEachLine { line ->
        val parts = line.split("|")
        val word = Word(
            original = parts[0],
            translate = parts[1],
            correctAnswersCount = parts.getOrNull(2)?.toIntOrNull() ?: 0
        )
        dictionary.add(word)
    }
    return dictionary
}

fun saveDictionary(dictionary: List<Word>) {
    val wordsFile = File("words.txt")
    wordsFile.printWriter().use { out ->
        dictionary.forEach { word ->
            out.println("${word.original}|${word.translate}")
        }
    }
}