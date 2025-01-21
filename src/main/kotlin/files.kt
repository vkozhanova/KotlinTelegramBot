import java.io.File
import java.util.Dictionary

data class Word(
    val original: String,
    val translate: String,
    val correctAnswersCount: Int = 0
)

const val SCORE_LIMIT = 3
const val PERCENT_MULTIPLIER = 100

fun main() {

    val dictionary = loadDictionary()


    while (true) {
        println("Меню:")
        println("1 - Учить слова")
        println("2 - Статистика")
        println("0 - Выход")

        val userChoice = readln()

        when (userChoice) {
            "1" -> { println("Учить слова")
            learnWords(dictionary)}
            "2" -> {
                println("Статистика")
                val learnedCount = dictionary.filter { it.correctAnswersCount >= SCORE_LIMIT }.size
                val totalCount = dictionary.size
                val percent = if (totalCount > 0) (learnedCount.toDouble() / totalCount * PERCENT_MULTIPLIER).toInt() else 0

                println("Выучено $learnedCount из $totalCount | $percent%")
                println()
            }

            "0" -> {
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

fun learnWords(dictionary: List<Word>) {

    val notLearnedList = dictionary.filter { it.correctAnswersCount < SCORE_LIMIT }
    if (notLearnedList.isEmpty()) {
        println("Все слова в словаре выучены")
        return
    }

    val questionWords = notLearnedList.shuffled().take(4)

    val correctAnswer = questionWords.random()
    val incorrectAnswers = notLearnedList.filter { it !=correctAnswer }.shuffled().take(3)
    val variants = (incorrectAnswers + correctAnswer).shuffled()

    println("${correctAnswer.original}:")
    variants.forEachIndexed { index, word ->
        println("${index + 1} - ${word.translate}")
    }
    val userInput = readln()

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
