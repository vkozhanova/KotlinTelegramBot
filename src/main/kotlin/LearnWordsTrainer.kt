import java.io.File
import java.io.FileNotFoundException
import java.lang.IllegalStateException

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

class Statistics(
    val learnedCount: Int,
    val totalCount: Int,
    val percent: Int,
)

data class Question(
    val variants: List<Word>,
    val correctAnswer: Word,
)

const val PERCENT_MULTIPLIER = 100
const val LEARNED_ANSWER_COUNT = 3
const val COUNT_OF_QUESTION_WORDS = 4

class LearnWordsTrainer {
    private var question: Question? = null
    private val dictionary = loadDictionary()

    fun getStatistics(): Statistics {
        val learnedCount = dictionary.filter { it.correctAnswersCount >= LEARNED_ANSWER_COUNT }.size
        val totalCount = dictionary.size
        val percent =
            if (totalCount > 0) (learnedCount.toDouble() / totalCount * PERCENT_MULTIPLIER).toInt() else 0
        return Statistics(
            learnedCount,
            totalCount,
            percent
        )
    }

    fun getNextQuestion(): Question? {
        val notLearnedList = dictionary.filter { it.correctAnswersCount < LEARNED_ANSWER_COUNT }
        if (notLearnedList.isEmpty()) return null
        val questionWords = if (notLearnedList.size < COUNT_OF_QUESTION_WORDS) {
            val learnedList = dictionary.filter { it.correctAnswersCount >= LEARNED_ANSWER_COUNT }.shuffled()
            notLearnedList.shuffled().take(COUNT_OF_QUESTION_WORDS) +
                    learnedList.take(COUNT_OF_QUESTION_WORDS - notLearnedList.size)
        } else {
            notLearnedList.shuffled().take(COUNT_OF_QUESTION_WORDS)
        }.shuffled()
        val correctAnswer = questionWords.random()
        question = Question(
            variants = questionWords,
            correctAnswer = correctAnswer,
        )
        return question
    }

    fun checkAnswer(userAnswerIndex: Int?): Boolean {
        return question?.let {
            val correctAnswerId = it.variants.indexOf(it.correctAnswer)
            if (correctAnswerId == userAnswerIndex) {
                it.correctAnswer.correctAnswersCount++
                saveDictionary(dictionary)
                true
            } else {
                false
            }
        } ?: false
    }

    private fun loadDictionary(): MutableList<Word> {
        try {
            val wordsFile: File = File("words.txt")
            if (!wordsFile.exists()) {
                throw FileNotFoundException("Файл ${wordsFile.name} не найден.")
            }
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
        } catch (e: IndexOutOfBoundsException) {
            throw IllegalStateException("Некорректный файл.")
        }
    }

    private fun saveDictionary(dictionary: List<Word>) {
        val wordsFile = File("words.txt")
        wordsFile.printWriter().use { out ->
            for (word in dictionary) {
                wordsFile.appendText("${word.original}|${word.translate}|${word.correctAnswersCount}\n")
            }
        }
    }
}




