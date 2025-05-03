package services


import play.api.libs.ws.WSClient

import javax.inject.{Inject, Singleton}
import scala.concurrent.{ExecutionContext, Future}
import edu.stanford.nlp.pipeline.*
import edu.stanford.nlp.ling.*
import edu.stanford.nlp.util.*
import models.{EventPoll, PollOption}

import scala.jdk.CollectionConverters.*

@Singleton
class InferUserOptionService @Inject()(ws: WSClient)(implicit ec: ExecutionContext) {

  def inferencePollResponse(eventPoll: EventPoll, options: List[PollOption], response: String): Future[Option[(PollOption, Int)]] = {
    Future.successful(inferPollResponseWithNLP(eventPoll.pollQuestion.toLowerCase, options.map(_.optionText.toLowerCase), response.toLowerCase)).map{ r =>
      println(s"${eventPoll.pollQuestion}[${options.map(_.optionText).mkString(",")}]: $response => $r")
      r
    }.map{
      case Some((responseOption, confidence)) => options.find(_.optionText.equals(responseOption)).map(po => (po, confidence))
      case _ => None
    }
  }

  /**
   * Infers the chosen option and confidence value from a natural language response to a poll
   *
   * @param question The poll question
   * @param options  The list of available options
   * @param response The user's natural language response
   * @return Option containing a tuple of (matched option, confidence value) or None if inference failed
   */
  private def inferPollResponseWithNLP(
                                question: String,
                                options: List[String],
                                response: String
                              ): Option[(String, Int)] = {

    // Initialize Stanford CoreNLP pipeline
    val props = new java.util.Properties()
    props.setProperty("annotators", "tokenize,ssplit,pos,lemma,ner")
    props.setProperty("tokenize.language", "es")
    props.setProperty("ner.applyNumericClassifiers", "true")
    props.setProperty("ner.useSUTime", "false")
    val pipeline = new StanfordCoreNLP(props)

    // Annotate the text
    val annotation = new Annotation(normalizeText(response).mkString(" "))
    pipeline.annotate(annotation)

    // Extract sentences
    val sentences = annotation.get(classOf[CoreAnnotations.SentencesAnnotation]).asScala.toList

    // Prepare normalized versions of options for better matching
    val normalizedOptions = options.map(opt => (opt, normalizeText(opt)))

    // Find the matched option using NLP features
    val matchedOption = findBestMatchingOption(normalizedOptions, sentences)

    // Extract confidence value using NLP
    val confidence = extractConfidenceValue(sentences).flatMap(c => Option.when(c > 0)(c))

    // Return the results if we found both parts
    (matchedOption, confidence) match {
      case (Some(option), Some(conf)) => Some((option, conf))
      case _ => None
    }
  }

  /**
   * Normalizes text by converting to lowercase and removing stop words
   */
  private  def normalizeText(text: String): Set[String] = {
    val a = text.toLowerCase

    val k = (a.zip(a.tail + " ").map { case (current, next) => if ((current.isDigit && !next.isDigit) ||
      (!current.isDigit && next.isDigit)) current.toString + " " else current.toString
    }).foldLeft("")(_ + _)

    val r = k.toLowerCase
      .split("\\W+")
      .toSet

    //println(s"from $text to ${r.mkString("|")}")
    r
  }

  /**
   * Finds the best matching option using NLP features
   */
  private  def findBestMatchingOption(
                              normalizedOptions: List[(String, Set[String])],
                              sentences: List[CoreMap]
                            ): Option[String] = {

    // Extract all tokens from the response
    val tokens = sentences.flatMap(sentence =>
      sentence.get(classOf[CoreAnnotations.TokensAnnotation]).asScala
    )

    // Get lemmas (base forms) of all words, which helps with matching variations
    val lemmas = tokens.map(token =>
      token.get(classOf[CoreAnnotations.LemmaAnnotation]).toLowerCase
    ).toSet

    // Get all content words (nouns, verbs, adjectives, etc.)
    val contentWords = tokens.filter(token => {
      val pos = token.get(classOf[CoreAnnotations.PartOfSpeechAnnotation])
      pos.startsWith("NN") || pos.startsWith("VB") || pos.startsWith("JJ")
    }).map(token =>
      token.get(classOf[CoreAnnotations.LemmaAnnotation]).toLowerCase
    ).toSet

    // Score each option based on word overlap
    val scored = normalizedOptions.map { case (original, normalized) =>
      val contentWordMatches = normalized.intersect(contentWords).size
      val lemmaMatches = normalized.intersect(lemmas).size

      // Weight content words more heavily
      val score = (contentWordMatches * 2) + lemmaMatches
      (original, score)
    }

    // Return the option with the highest score, if any option matches
    val bestMatch = scored.maxByOption(_._2)
    bestMatch match {
      case Some((option, score)) if score > 0 => Some(option)
      case _ => None
    }
  }

  /**
   * Extracts a confidence value from the response using NLP features
   */
  private  def extractConfidenceValue(sentences: List[CoreMap]): Option[Int] = {


    // If no percentages found, look for cardinal numbers
    val numbers = sentences.flatMap(sentence =>
      sentence.get(classOf[CoreAnnotations.TokensAnnotation]).asScala.filter(token =>
        token.get(classOf[CoreAnnotations.NamedEntityTagAnnotation]) == "NUMBER"
      )
    )

    if (numbers.nonEmpty) {
      // Try to convert to integer
      try {
        val numText = numbers.head.get(classOf[CoreAnnotations.TextAnnotation])

        // Handle common text representations of numbers
        numText.toLowerCase match {
          case "uno" => Some(1)
          case "dos" => Some(2)
          case "tres" => Some(3)
          case "cuatro" => Some(4)
          case "cinco" => Some(5)
          // Add more as needed
          case _ => Some(numText.toInt)
        }
      } catch {
        case _: NumberFormatException => None
      }
    } else {
      // Fallback to regex pattern matching
      val numberPattern = "\\b(\\d+)\\b".r

      val all_sentences = sentences.map(_.toString)
      val allText = all_sentences.mkString(" ")
      val allWords = allText.split("\\W+").toSet
      numberPattern.findFirstIn(allText).map(_.toInt) match {
        case Some(x) => Some(x)
        case None => {
          if (allWords.contains("all") || allWords.contains("todos") || allWords.contains("todas"))
            Some(Int.MaxValue)
          else None
        }
      }
    }
  }
}