data class Word(
    val original: String,
    val translate: String,
    var correctAnswersCount: Int = 0
)

class Statistics(
    val learnedCount: Int,
    val totalCount: Int,
    val percent: Int,
)

data class Question(
    val variants: List<Word>,
    val correctAnswer: Word,
    val imageFileId: String? = null
)

const val PERCENT_MULTIPLIER = 100

class LearnWordsTrainer(
    private val userDictionary: IUserDictionary,
    private val countOfQuestionWords: Int = 4,
) {
    var question: Question? = null

    fun getStatistics(): Statistics {
        val learnedCount = userDictionary.getNumOfLearnedWords()
        val totalCount = userDictionary.getSize()
        val percent =
            if (totalCount > 0) (learnedCount.toDouble() / totalCount * PERCENT_MULTIPLIER).toInt() else 0
        return Statistics(
            learnedCount,
            totalCount,
            percent
        )
    }

    fun getNextQuestion(): Question? {
        val notLearnedList = userDictionary.getUnlearnedWords()
        if (notLearnedList.isEmpty()) return null
        val questionWords = if (notLearnedList.size < countOfQuestionWords) {
            val learnedList = userDictionary.getLearnedWords().shuffled()
            if (learnedList.isEmpty() && notLearnedList.size < countOfQuestionWords) {
                return null
            }
            notLearnedList.shuffled().take(countOfQuestionWords) +
                    learnedList.take(countOfQuestionWords - notLearnedList.size)
        } else {
            notLearnedList.shuffled().take(countOfQuestionWords)
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
                userDictionary.setCorrectAnswersCount(
                    it.correctAnswer.original,
                    it.correctAnswer.correctAnswersCount + 1
                )
                true
            } else {
                false
            }
        } ?: false
    }

    fun resetProgress() {
        userDictionary.resetUserProgress()
    }
}




