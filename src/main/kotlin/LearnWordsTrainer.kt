import org.example.Word
import java.io.File

class Statistics(
    val learnedCount: Int,
    val totalCount: Int,
    val percent: Int,
)

data class Question(
    val variants: List<Word>,
    val correctAnswer: Word,
)

const val SCORE_LIMIT = 3
const val PERCENT_MULTIPLIER = 100
const val QUESTIONS_OPTIONS = 4

class LearnWordsTrainer {
    private var question: Question? = null
    private val dictionary = loadDictionary()

    fun getStatistics(): Statistics {
        val learnedCount = dictionary.filter { it.correctAnswersCount >= SCORE_LIMIT }.size
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
        val notLearnedList = dictionary.filter { it.correctAnswersCount < SCORE_LIMIT }
        if (notLearnedList.isEmpty()) return null
        val questionWords = notLearnedList.take(QUESTIONS_OPTIONS).shuffled()
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

    private fun saveDictionary(dictionary: List<Word>) {
        val wordsFile = File("words.txt")
        wordsFile.printWriter().use { out ->
            for (word in dictionary) {
                wordsFile.appendText("${word.original}|${word.translate}|${word.correctAnswersCount}\n")
            }
        }
    }
}




